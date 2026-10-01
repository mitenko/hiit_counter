package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs one Tabata workout (spec §8, amended by spec revision 10 §3 for skip forward/back). Ticks
 * are scheduled against absolute active-time targets measured with [nowMs] (monotonic), so they
 * never drift and pausing keeps the sub-second remainder. A skip rebases the target step's timing
 * from the active time at the moment of the jump, so there is no drift after one either. Not
 * thread-safe: call [run], [pause], [resume], [skipForward] and [skipBack] from one thread.
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

    /** The target step index of a pending skip, consumed by the run loop (spec revision 10 §3). */
    private val jumpTarget = MutableStateFlow<Int?>(null)
    private var activeBaseMs = 0L
    private var runningSinceMs: Long? = null
    private var current: TimerState? = null

    /** True for the duration of [run]; guards [skipForward] and [skipBack] (spec revision 10 §3). */
    private var isRunning = false

    /** The step [run] is currently on, and the active-ms/elapsed-sec at which it began. */
    private var stepIndex = 0
    private var stepStartMs = 0L
    private var stepStartSec = 0

    val isPaused: Boolean get() = paused.value

    suspend fun run() {
        isRunning = true
        runningSinceMs = nowMs()
        stepIndex = 0
        stepStartMs = 0L
        stepStartSec = 0
        while (stepIndex < steps.size) {
            val step = steps[stepIndex]
            emit(step, secondsLeft = step.durationSec, elapsedSec = stepStartSec)
            if (step.phase != Phase.PREPARE) onCue(Cue.PhaseStart(step.phase))
            var jumpedTo: Int? = null
            for (k in 1..step.durationSec) {
                val jump = awaitActiveMsOrJump(stepStartMs + k * 1000L)
                if (jump != null) {
                    jumpedTo = jump
                    break
                }
                if (k < step.durationSec) {
                    val left = step.durationSec - k
                    emit(step, secondsLeft = left, elapsedSec = stepStartSec + k)
                    if (left <= 3) onCue(Cue.Countdown(left))
                }
            }
            if (jumpedTo != null) {
                stepIndex = jumpedTo
                if (stepIndex >= steps.size) break
                // Spec revision 10 §8: the target step's timing rebases from the active time at
                // the moment of the jump, so there is no drift; elapsedSec jumps to the sum of
                // the steps before it, reflecting the new position rather than real time elapsed.
                stepStartMs = activeMs()
                stepStartSec = steps.take(stepIndex).sumOf { it.durationSec }
                continue
            }
            stepStartMs += step.durationSec * 1000L
            stepStartSec += step.durationSec
            stepIndex++
        }
        isRunning = false
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

    /**
     * Ends the current step at once and starts the next with its normal cues (spec revision 10
     * §8). Past the last step this finishes the workout, same as its natural end. A no-op before
     * [run] starts or after DONE, and while paused (the run stays paused on the new step).
     */
    fun skipForward() {
        if (!isRunning) return
        jumpTarget.value = stepIndex + 1
    }

    /**
     * Restarts the current step from its beginning, unless less than 2 s of active time has
     * passed in it and there is a previous step, in which case it jumps to the start of that one
     * instead (spec revision 10 §3). A no-op before [run] starts or after DONE.
     */
    fun skipBack() {
        if (!isRunning) return
        val elapsedInStep = activeMs() - stepStartMs
        jumpTarget.value = if (elapsedInStep < 2000L && stepIndex > 0) stepIndex - 1 else stepIndex
    }

    private fun activeMs(): Long = activeBaseMs + (runningSinceMs?.let { nowMs() - it } ?: 0L)

    /**
     * As before spec revision 10, but a pending [jumpTarget] also interrupts the wait and is
     * returned (and consumed) in place of a null, instead of waiting for [targetMs].
     */
    private suspend fun awaitActiveMsOrJump(targetMs: Long): Int? {
        while (true) {
            combine(paused, jumpTarget) { p, j -> !p || j != null }.first { it }
            takeJump()?.let { return it }
            val remaining = targetMs - activeMs()
            if (remaining <= 0) return null
            withTimeoutOrNull(remaining) {
                combine(paused, jumpTarget) { p, j -> p || j != null }.first { it }
            }
            takeJump()?.let { return it }
        }
    }

    private fun takeJump(): Int? {
        val target = jumpTarget.value ?: return null
        jumpTarget.value = null
        return target
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
