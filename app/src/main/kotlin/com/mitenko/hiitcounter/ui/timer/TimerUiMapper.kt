package com.mitenko.hiitcounter.ui.timer

import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState

enum class PhaseTone { WORK, REST, NEUTRAL }

data class TimerUiState(
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
    fun map(s: TimerState): TimerUiState {
        val inner = if (s.phaseDurationSec > 0) s.phaseSecondsLeft.toFloat() / s.phaseDurationSec else 0f
        val workFraction = if (s.phase == Phase.WORK && s.phaseDurationSec > 0) {
            (s.phaseDurationSec - s.phaseSecondsLeft).toFloat() / s.phaseDurationSec
        } else {
            0f
        }
        val base = TimerUiState(
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
                centerNumber = s.repsThisSet, tone = PhaseTone.WORK,
                description = "Work, set ${s.set} of ${s.sets}, ${s.repsThisSet} reps",
            )
            Phase.REST -> base.copy(
                label = "REST", centerNumber = s.repsThisSet, centerDimmed = true, tone = PhaseTone.REST,
                description = "Rest, next set ${s.set} of ${s.sets}, ${s.repsThisSet} reps",
            )
            Phase.PREPARE -> base.copy(
                label = "GET READY", centerNumber = s.repsThisSet, centerDimmed = true,
                description = "Get ready, first set ${s.repsThisSet} reps",
            )
            Phase.COOLDOWN -> base.copy(label = "COOLDOWN", description = "Cooldown")
            Phase.DONE -> base.copy(
                label = "DONE", centerNumber = s.totalReps, countdownText = "", innerProgress = 0f,
                outerProgress = 1f, done = true, description = "Done, ${s.totalReps} reps",
            )
        }
    }
}
