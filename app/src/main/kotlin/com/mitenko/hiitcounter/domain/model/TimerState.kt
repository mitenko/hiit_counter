package com.mitenko.hiitcounter.domain.model

enum class Phase { PREPARE, WORK, REST, COOLDOWN, DONE }

/**
 * Snapshot of a running workout. [set] is the current work set during WORK, and the
 * upcoming work set during PREPARE and REST.
 */
data class TimerState(
    val phase: Phase,
    val set: Int,
    val sets: Int,
    val phaseSecondsLeft: Int,
    val phaseDurationSec: Int,
    val elapsedSec: Int,
    val totalDurationSec: Int,
    val repsThisSet: Int,
    val totalReps: Int,
    val paused: Boolean,
) {
    val completedWorkSets: Int
        get() = when (phase) {
            Phase.COOLDOWN, Phase.DONE -> sets
            else -> set - 1
        }

    val remainingSec: Int
        get() = (totalDurationSec - elapsedSec).coerceAtLeast(0)
}
