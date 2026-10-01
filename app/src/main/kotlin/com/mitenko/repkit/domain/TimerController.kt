package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Cue
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.TimerState
import com.mitenko.repkit.domain.model.TimingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Frozen at Start (spec §7.1): the service, timer screen and notification read only this, so
 * renaming, editing or deleting the source entry never changes an active run.
 */
data class WorkoutSnapshot(
    val entryId: Long,
    val entryName: String,
    val timing: TimingConfig,
    val cues: CueConfig,
    /** False for a Timer only entry (spec revision 8): no reps are counted or shown; the centre and voice count the sets down (spec revision 10). */
    val countsReps: Boolean = true,
)

enum class RunStatus { IDLE, PREPARING, RUNNING, DONE }

sealed interface ServiceStatus {
    data object Pending : ServiceStatus
    data object Started : ServiceStatus
    data class Failed(val reason: String) : ServiceStatus
}

/**
 * Owns the single running workout (v1 spec §4, §8). Commands are idempotent. Must be used
 * from the thread [scope] dispatches on (Main in production).
 */
class TimerController(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {
    private val _status = MutableStateFlow(RunStatus.IDLE)
    val status: StateFlow<RunStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow<TimerState?>(null)
    val state: StateFlow<TimerState?> = _state.asStateFlow()

    private val _cues = MutableSharedFlow<Cue>(replay = 0, extraBufferCapacity = 64)
    val cues: SharedFlow<Cue> = _cues.asSharedFlow()

    /**
     * The run's cues, live (this amends spec R4 §5's "frozen at Start" for cues only, spec
     * revision 7): seeded from the snapshot by [prepare], updatable by [setCues] while a run
     * exists, and cleared by [clearRun]. Name, timing and reps stay frozen in [snapshot].
     */
    private val _liveCues = MutableStateFlow<CueConfig?>(null)
    val liveCues: StateFlow<CueConfig?> = _liveCues.asStateFlow()

    private val _serviceStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Pending)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    var snapshot: WorkoutSnapshot? = null
        private set

    /** The entry of the most recent prepare(); kept after the run ends so leaving the timer returns to it (spec §7.2). */
    var lastEntryId: Long? = null
        private set

    private var engine: TabataEngine? = null
    private var runJob: Job? = null
    private var pauseTimeoutJob: Job? = null

    /**
     * Spec §7.1 busy rule, read at the moment of each destructive action. After process
     * recreation the controller starts IDLE, so nothing is busy. A leftover DONE is inert:
     * deleting its entry is safe because exitTimer falls back to the list.
     */
    fun isBusy(entryId: Long): Boolean =
        (_status.value == RunStatus.PREPARING || _status.value == RunStatus.RUNNING) && snapshot?.entryId == entryId

    fun prepare(snapshot: WorkoutSnapshot): Boolean {
        if (_status.value == RunStatus.PREPARING || _status.value == RunStatus.RUNNING) return false
        clearRun() // clears the previous snapshot; assign the new one after
        this.snapshot = snapshot
        lastEntryId = snapshot.entryId
        _liveCues.value = snapshot.cues
        _serviceStatus.value = ServiceStatus.Pending
        _status.value = RunStatus.PREPARING
        return true
    }

    /** Applies immediately to the running workout while it exists; ignored otherwise (spec R4 §5, rev 7). */
    fun setCues(config: CueConfig) {
        if (snapshot != null) _liveCues.value = config
    }

    fun onServiceStarted() {
        if (_status.value == RunStatus.PREPARING) _serviceStatus.value = ServiceStatus.Started
    }

    fun onServiceFailed(reason: String) {
        if (_status.value == RunStatus.PREPARING) _serviceStatus.value = ServiceStatus.Failed(reason)
    }

    fun cancelPrepare() {
        if (_status.value != RunStatus.PREPARING) return
        clearRun()
        _status.value = RunStatus.IDLE
    }

    fun start(repsPerSet: List<Int>): Boolean {
        val snap = snapshot ?: return false
        // The timer only runs under a foreground service (v1 spec §4).
        if (_status.value != RunStatus.PREPARING || _serviceStatus.value != ServiceStatus.Started) return false
        val e = TabataEngine(
            timing = snap.timing,
            repsPerSet = repsPerSet,
            nowMs = nowMs,
            onState = { _state.value = it },
            onCue = { _cues.tryEmit(withReps(it, repsPerSet)) },
        )
        engine = e
        _status.value = RunStatus.RUNNING
        runJob = scope.launch {
            e.run()
            pauseTimeoutJob?.cancel()
            _status.value = RunStatus.DONE
        }
        return true
    }

    fun pause() {
        val e = engine ?: return
        if (_status.value != RunStatus.RUNNING || e.isPaused) return
        e.pause()
        pauseTimeoutJob = scope.launch {
            delay(MAX_PAUSE_MS)
            stop()
        }
    }

    fun resume() {
        val e = engine ?: return
        if (_status.value != RunStatus.RUNNING || !e.isPaused) return
        pauseTimeoutJob?.cancel()
        e.resume()
    }

    /** Ends the current phase and starts the next, keeping pause (spec revision 10 §4). A no-op unless RUNNING. */
    fun skipForward() {
        val e = engine ?: return
        if (_status.value != RunStatus.RUNNING) return
        e.skipForward()
    }

    /** Restarts the current phase, or jumps to the previous one within its first 2 s (spec revision 10 §4). A no-op unless RUNNING. */
    fun skipBack() {
        val e = engine ?: return
        if (_status.value != RunStatus.RUNNING) return
        e.skipBack()
    }

    /** Ends the workout. The check-in made at Start stands; no DONE, no Finished cue. */
    fun stop() {
        when (_status.value) {
            RunStatus.RUNNING -> {
                clearRun()
                _status.value = RunStatus.IDLE
            }
            RunStatus.PREPARING -> cancelPrepare()
            RunStatus.IDLE, RunStatus.DONE -> Unit
        }
    }

    fun dismissDone() {
        if (_status.value != RunStatus.DONE) return
        clearRun()
        _status.value = RunStatus.IDLE
    }

    /**
     * Spec R4 §5, amended by spec revision 8 and revision 10: a WORK start carries that set's
     * entry of [repsPerSet], or, when the run's snapshot has `countsReps = false` (Timer only),
     * the countdown of sets remaining including this one (`sets - set + 1`), so the voice matches
     * the centre number. The engine publishes the set's first state just before its PhaseStart, so
     * [state] already names the starting set.
     */
    private fun withReps(cue: Cue, repsPerSet: List<Int>): Cue {
        if (cue !is Cue.PhaseStart || cue.phase != Phase.WORK) return cue
        val s = _state.value ?: return cue
        return cue.copy(reps = if (snapshot?.countsReps == false) s.sets - s.set + 1 else repsPerSet.getOrNull(s.set - 1))
    }

    /** Clears the run but deliberately not [lastEntryId]. */
    private fun clearRun() {
        runJob?.cancel()
        runJob = null
        pauseTimeoutJob?.cancel()
        pauseTimeoutJob = null
        engine = null
        snapshot = null
        _liveCues.value = null
        _state.value = null
    }

    companion object {
        const val MAX_PAUSE_MS = 30 * 60 * 1000L
    }
}
