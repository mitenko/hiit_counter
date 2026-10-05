package com.mitenko.repkit.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.RunStatus
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.domain.VoicePolicy
import com.mitenko.repkit.domain.WakeLockPolicy
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.TimerState
import com.mitenko.repkit.platform.AndroidCueSpeaker
import com.mitenko.repkit.platform.CueSpeaker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin host (spec §4, §10): keeps the process and CPU alive while TimerController runs,
 * plays cues, shows the notification. No business logic.
 */
@AndroidEntryPoint
class TimerService : Service() {
    @Inject lateinit var controller: TimerController
    @Inject lateinit var reporter: CrashReporter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var cuePlayer: CuePlayer
    private lateinit var notifications: WorkoutNotifications
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    /** The run's voice (spec R4 §5): created for a voice run, shut down when it ends or the service stops. */
    private var speaker: CueSpeaker? = null

    override fun onCreate() {
        super.onCreate()
        cuePlayer = CuePlayer(this, scope)
        notifications = WorkoutNotifications(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stop() // idempotent: no-op when idle or done
            if (!started) stopSelf() // stale notification action after process recreation
            return START_NOT_STICKY
        }
        if (started) {
            // Already foreground, e.g. a new workout started during the DONE grace period.
            // Re-assert startForeground as insurance in case the system demoted it.
            try {
                ServiceCompat.startForeground(
                    this,
                    WorkoutNotifications.NOTIFICATION_ID,
                    notifications.build(controller.state.value, controller.snapshot?.entryName),
                    foregroundServiceType(),
                )
                controller.onServiceStarted()
            } catch (e: Exception) {
                Log.e(TAG, "startForeground failed", e)
                controller.onServiceFailed(e.message ?: e.javaClass.simpleName)
                releaseWakeLock()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        try {
            notifications.ensureChannel()
            ServiceCompat.startForeground(
                this,
                WorkoutNotifications.NOTIFICATION_ID,
                notifications.build(null, controller.snapshot?.entryName),
                foregroundServiceType(),
            )
            started = true
            observe()
            controller.onServiceStarted()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            controller.onServiceFailed(e.message ?: e.javaClass.simpleName)
            releaseWakeLock()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    private fun observe() {
        scope.launch {
            // Cues are live (spec R4 §5, rev 7): liveCues.value while a run exists, the frozen
            // snapshot's cues only as a fallback.
            controller.cues.collect { cue ->
                controller.snapshot?.let { snap -> cuePlayer.play(cue, controller.liveCues.value ?: snap.cues) }
            }
        }
        scope.launch {
            controller.state.collect { state ->
                if (state != null) {
                    notifications.update(state, controller.snapshot?.entryName)
                    updateWakeLock(state)
                }
            }
        }
        scope.launch {
            // collectLatest: a new PREPARING/RUNNING status, or a live cue toggle, cancels a
            // pending DONE-grace stop and re-evaluates the speaker.
            combine(controller.status, controller.liveCues) { status, cues -> status to cues }.collectLatest { (status, cues) ->
                // Spec R4 §5: before any delay below, so DONE and IDLE shut the speaker down at once,
                // and so toggling Voice mid-run creates or tears down the speaker right away.
                syncSpeaker(VoicePolicy.speakerWanted(status, cues))
                if (status == RunStatus.IDLE || status == RunStatus.DONE) {
                    // DONE: wait out the grace period (so the Finished triple tone can still play
                    // with the screen off) before releasing the wake lock; IDLE releases at once.
                    if (status == RunStatus.DONE) delay(FINISH_CUE_GRACE_MS)
                    releaseWakeLock()
                    ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (status == RunStatus.PREPARING) {
                    // A new workout being prepared while a previous run's DONE grace delay was
                    // pending cancels that delay here (collectLatest), so the old wake lock would
                    // otherwise survive with its stale (~old run's) timeout. No lock is needed
                    // while preparing, and releasing it now guarantees the new run's first
                    // ticking state acquires a fresh lock with its own timeout.
                    releaseWakeLock()
                }
            }
        }
    }

    private fun updateWakeLock(state: TimerState) {
        // A DONE-phase state releases via the status collector above, after the grace delay —
        // not here, or the CPU could sleep before the Finished cue finishes playing.
        if (state.phase == Phase.DONE) return
        val timeoutMs = WakeLockPolicy.timeoutMs(state)
        if (timeoutMs != null) acquireWakeLock(timeoutMs) else releaseWakeLock()
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        // Before spec revision 10 a held lock always already covered the remaining active time,
        // since elapsedSec only ever grew between acquisitions; an early return here was enough.
        // A skip back can now move elapsedSec backwards, so the lock's existing timeout (set from
        // a smaller remaining time) could under-run the newly extended one. acquire() on a
        // non-reference-counted lock just resets its release deadline, so re-asserting it from
        // every tick's state keeps the held time in sync after both a forward and a backward jump.
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
        lock.acquire(timeoutMs)
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * Creates the speaker while the run's live cues have Voice on (rev 7: cues follow the timer
     * screen's toggles) and shuts it down otherwise. Initialisation is asynchronous; until it succeeds
     * the speaker reports unavailable and CuePlayer says nothing.
     */
    private fun syncSpeaker(wanted: Boolean) {
        if (wanted && speaker == null) {
            speaker = AndroidCueSpeaker(this, reporter).also { cuePlayer.speaker = it }
        } else if (!wanted) {
            shutdownSpeaker()
        }
    }

    private fun shutdownSpeaker() {
        cuePlayer.speaker = null
        speaker?.shutdown()
        speaker = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running when the app is swiped away; the notification is the way back in.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        shutdownSpeaker()
        cuePlayer.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.mitenko.repkit.action.STOP"
        private const val TAG = "TimerService"
        private const val WAKE_LOCK_TAG = "hiitcounter:workout"
        private const val FINISH_CUE_GRACE_MS = 1_500L
    }
}
