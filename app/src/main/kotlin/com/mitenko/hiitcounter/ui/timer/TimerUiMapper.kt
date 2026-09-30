package com.mitenko.hiitcounter.ui.timer

import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState

enum class PhaseTone { WORK, REST, NEUTRAL }

data class TimerUiState(
    /** Frozen in the run's snapshot at Start (spec §7.6). */
    val entryName: String,
    val setsText: String,
    val elapsedText: String,
    val label: String?,
    val centerNumber: Int?,
    val centerDimmed: Boolean,
    val countdownText: String,
    val innerProgress: Float,
    val outerProgress: Float,
    val tone: PhaseTone,
    val paused: Boolean,
    val done: Boolean,
    val description: String,
)

object TimerUiMapper {
    /**
     * [countsReps] is false for a Timer only run (spec revision 8): the centre shows the current
     * (or, during REST/PREPARE, upcoming) set number in place of the rep count, and no rep value
     * appears anywhere, including on DONE.
     */
    fun map(s: TimerState, entryName: String, countsReps: Boolean = true): TimerUiState {
        val inner = if (s.phaseDurationSec > 0) s.phaseSecondsLeft.toFloat() / s.phaseDurationSec else 0f
        val workFraction = if (s.phase == Phase.WORK && s.phaseDurationSec > 0) {
            (s.phaseDurationSec - s.phaseSecondsLeft).toFloat() / s.phaseDurationSec
        } else {
            0f
        }
        val base = TimerUiState(
            entryName = entryName,
            setsText = "${s.set}/${s.sets}",
            elapsedText = TimerText.formatHms(s.elapsedSec),
            label = null,
            centerNumber = null,
            centerDimmed = false,
            countdownText = TimerText.formatMmSs(s.phaseSecondsLeft),
            innerProgress = inner,
            outerProgress = ((s.completedWorkSets + workFraction) / s.sets).coerceIn(0f, 1f),
            tone = PhaseTone.NEUTRAL,
            paused = s.paused,
            done = false,
            description = "",
        )
        return when (s.phase) {
            Phase.WORK -> base.copy(
                centerNumber = if (countsReps) s.repsThisSet else s.set, tone = PhaseTone.WORK,
                description = if (countsReps) {
                    "Work, set ${s.set} of ${s.sets}, ${s.repsThisSet} reps"
                } else {
                    "Work, set ${s.set} of ${s.sets}"
                },
            )
            Phase.REST -> base.copy(
                label = "REST", centerNumber = if (countsReps) s.repsThisSet else s.set, centerDimmed = true, tone = PhaseTone.REST,
                description = if (countsReps) {
                    "Rest, next set ${s.set} of ${s.sets}, ${s.repsThisSet} reps"
                } else {
                    "Rest, next set ${s.set} of ${s.sets}"
                },
            )
            Phase.PREPARE -> base.copy(
                label = "GET READY", centerNumber = if (countsReps) s.repsThisSet else s.set, centerDimmed = true,
                description = if (countsReps) "Get ready, first set ${s.repsThisSet} reps" else "Get ready, set ${s.set} of ${s.sets}",
            )
            Phase.COOLDOWN -> base.copy(label = "COOLDOWN", description = "Cooldown")
            Phase.DONE -> base.copy(
                label = "DONE", centerNumber = if (countsReps) s.totalReps else null, countdownText = "", innerProgress = 0f,
                outerProgress = 1f, done = true, description = if (countsReps) "Done, ${s.totalReps} reps" else "Done",
            )
        }
    }
}
