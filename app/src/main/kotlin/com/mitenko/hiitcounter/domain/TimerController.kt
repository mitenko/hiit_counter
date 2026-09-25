package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
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

data class WorkoutSnapshot(val timing: TimingConfig, val cues: CueConfig)

enum class RunStatus { IDLE, PREPARING, RUNNING, DONE }

sealed interface ServiceStatus {
    data object Pending : ServiceStatus
    data object Started : ServiceStatus
    data class Failed(val reason: String) : ServiceStatus
}

/**
 * Owns the single running workout (spec §4, §8). Commands are idempotent. Must be used
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

    private val _serviceStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Pending)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    var snapshot: WorkoutSnapshot? = null
        private set

    private var engine: TabataEngine? = null
    private var runJob: Job? = null
    private var pauseTimeoutJob: Job? = null

    fun prepare(snapshot: WorkoutSnapshot): Boolean {
        if (_status.value == RunStatus.PREPARING || _status.value == RunStatus.RUNNING) return false
        clearRun() // clears the previous snapshot; assign the new one after
        this.snapshot = snapshot
        _serviceStatus.value = ServiceStatus.Pending
        _status.value = RunStatus.PREPARING
        return true
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
        // The timer only runs under a foreground service (spec §4).
        if (_status.value != RunStatus.PREPARING || _serviceStatus.value != ServiceStatus.Started) return false
        val e = TabataEngine(
            timing = snap.timing,
            repsPerSet = repsPerSet,
            nowMs = nowMs,
            onState = { _state.value = it },
            onCue = { _cues.tryEmit(it) },
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

    private fun clearRun() {
        runJob?.cancel()
        runJob = null
        pauseTimeoutJob?.cancel()
        pauseTimeoutJob = null
        engine = null
        snapshot = null
        _state.value = null
    }

    companion object {
        const val MAX_PAUSE_MS = 30 * 60 * 1000L
    }
}
