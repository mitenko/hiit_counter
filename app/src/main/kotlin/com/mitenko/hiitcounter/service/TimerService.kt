package com.mitenko.hiitcounter.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WakeLockPolicy
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin host (spec §4, §10): keeps the process and CPU alive while TimerController runs,
 * plays cues, shows the notification. No business logic.
 */
@AndroidEntryPoint
class TimerService : Service() {
    @Inject lateinit var controller: TimerController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var cuePlayer: CuePlayer
    private lateinit var notifications: WorkoutNotifications
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

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
                    notifications.build(controller.state.value),
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
                notifications.build(null),
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
            controller.cues.collect { cue -> controller.snapshot?.let { cuePlayer.play(cue, it.cues) } }
        }
        scope.launch {
            controller.state.collect { state ->
                if (state != null) {
                    notifications.update(state)
                    updateWakeLock(state)
                }
            }
        }
        scope.launch {
            // collectLatest: a new PREPARING/RUNNING status cancels a pending DONE-grace stop.
            controller.status.collectLatest { status ->
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
        // A held lock already covers the remaining active time; pause releases it and
        // resume re-acquires with a fresh timeout (WakeLockPolicy).
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running when the app is swiped away; the notification is the way back in.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        cuePlayer.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.mitenko.hiitcounter.action.STOP"
        private const val TAG = "TimerService"
        private const val WAKE_LOCK_TAG = "hiitcounter:workout"
        private const val FINISH_CUE_GRACE_MS = 1_500L
    }
}
