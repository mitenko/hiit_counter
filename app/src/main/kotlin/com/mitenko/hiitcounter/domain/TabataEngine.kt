package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs one Tabata workout (spec §8). Ticks are scheduled against absolute active-time
 * targets measured with [nowMs] (monotonic), so they never drift and pausing keeps the
 * sub-second remainder. Not thread-safe: call [run], [pause] and [resume] from one thread.
 */
class TabataEngine(
    private val timing: TimingConfig,
    private val repsPerSet: List<Int>,
    private val nowMs: () -> Long,
    private val onState: (TimerState) -> Unit,
    private val onCue: (Cue) -> Unit,
) {
    private data class Step(val phase: Phase, val set: Int, val durationSec: Int)

    init {
        require(repsPerSet.size == timing.sets) { "repsPerSet has ${repsPerSet.size} entries for ${timing.sets} sets" }
    }

    private val steps: List<Step> = buildList {
        if (timing.prepareSec > 0) add(Step(Phase.PREPARE, 1, timing.prepareSec))
        for (set in 1..timing.sets) {
            add(Step(Phase.WORK, set, timing.workSec))
            if (set < timing.sets && timing.restSec > 0) add(Step(Phase.REST, set + 1, timing.restSec))
        }
        if (timing.cooldownSec > 0) add(Step(Phase.COOLDOWN, timing.sets, timing.cooldownSec))
    }
    private val totalReps = repsPerSet.sum()
    private val totalDurationSec = timing.totalDurationSec

    private val paused = MutableStateFlow(false)
    private var activeBaseMs = 0L
    private var runningSinceMs: Long? = null
    private var current: TimerState? = null

    val isPaused: Boolean get() = paused.value

    suspend fun run() {
        runningSinceMs = nowMs()
        var phaseStartMs = 0L
        var phaseStartSec = 0
        for (step in steps) {
            emit(step, secondsLeft = step.durationSec, elapsedSec = phaseStartSec)
            if (step.phase != Phase.PREPARE) onCue(Cue.PhaseStart(step.phase))
            for (k in 1..step.durationSec) {
                awaitActiveMs(phaseStartMs + k * 1000L)
                if (k < step.durationSec) {
                    val left = step.durationSec - k
                    emit(step, secondsLeft = left, elapsedSec = phaseStartSec + k)
                    if (left <= 3) onCue(Cue.Countdown(left))
                }
            }
            phaseStartMs += step.durationSec * 1000L
            phaseStartSec += step.durationSec
        }
        runningSinceMs = null
        val done = TimerState(
            phase = Phase.DONE, set = timing.sets, sets = timing.sets, phaseSecondsLeft = 0, phaseDurationSec = 0,
            elapsedSec = totalDurationSec, totalDurationSec = totalDurationSec, repsThisSet = 0, totalReps = totalReps,
            paused = false,
        )
        current = done
        onState(done)
        onCue(Cue.Finished)
    }

    fun pause() {
        val since = runningSinceMs ?: return
        if (paused.value) return
        activeBaseMs += nowMs() - since
        runningSinceMs = null
        paused.value = true
        current?.copy(paused = true)?.let { current = it; onState(it) }
    }

    fun resume() {
        if (!paused.value) return
        runningSinceMs = nowMs()
        paused.value = false
        current?.copy(paused = false)?.let { current = it; onState(it) }
    }

    private fun activeMs(): Long = activeBaseMs + (runningSinceMs?.let { nowMs() - it } ?: 0L)

    private suspend fun awaitActiveMs(targetMs: Long) {
        while (true) {
            paused.first { !it }
            val remaining = targetMs - activeMs()
            if (remaining <= 0) return
            withTimeoutOrNull(remaining) { paused.first { it } }
        }
    }

    private fun emit(step: Step, secondsLeft: Int, elapsedSec: Int) {
        val reps = if (step.phase == Phase.COOLDOWN) 0 else repsPerSet[step.set - 1]
        val state = TimerState(
            phase = step.phase, set = step.set, sets = timing.sets, phaseSecondsLeft = secondsLeft,
            phaseDurationSec = step.durationSec, elapsedSec = elapsedSec, totalDurationSec = totalDurationSec,
            repsThisSet = reps, totalReps = totalReps, paused = paused.value,
        )
        current = state
        onState(state)
    }
}
