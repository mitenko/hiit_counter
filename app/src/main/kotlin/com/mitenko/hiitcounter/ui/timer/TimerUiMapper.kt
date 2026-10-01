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
     * [countsReps] is false for a Timer only run (spec revision 8, amended by revision 10): the
     * centre counts the sets down instead of showing reps — `sets - set + 1`, the current (or,
     * during REST/PREPARE, upcoming) set and every one still to come, including it — and no rep
     * value appears anywhere, including on DONE. The Voice cue says the same number
     * (TimerController.withReps).
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
        val setsToGo = s.sets - s.set + 1
        return when (s.phase) {
            Phase.WORK -> base.copy(
                centerNumber = if (countsReps) s.repsThisSet else setsToGo, tone = PhaseTone.WORK,
                description = if (countsReps) {
                    "Work, set ${s.set} of ${s.sets}, ${s.repsThisSet} reps"
                } else if (setsToGo == 1) {
                    "Work, last set"
                } else {
                    "Work, $setsToGo sets to go"
                },
            )
            Phase.REST -> base.copy(
                label = "REST", centerNumber = if (countsReps) s.repsThisSet else setsToGo, centerDimmed = true, tone = PhaseTone.REST,
                description = if (countsReps) {
                    "Rest, next set ${s.set} of ${s.sets}, ${s.repsThisSet} reps"
                } else if (setsToGo == 1) {
                    "Rest, last set next"
                } else {
                    "Rest, next set $setsToGo to go"
                },
            )
            Phase.PREPARE -> base.copy(
                label = "GET READY", centerNumber = if (countsReps) s.repsThisSet else setsToGo, centerDimmed = true,
                description = if (countsReps) "Get ready, first set ${s.repsThisSet} reps" else if (setsToGo == 1) "Get ready, 1 set" else "Get ready, $setsToGo sets",
            )
            Phase.COOLDOWN -> base.copy(label = "COOLDOWN", description = "Cooldown")
            Phase.DONE -> base.copy(
                label = "DONE", centerNumber = if (countsReps) s.totalReps else null, countdownText = "", innerProgress = 0f,
                outerProgress = 1f, done = true, description = if (countsReps) "Done, ${s.totalReps} reps" else "Done",
            )
        }
    }
}
