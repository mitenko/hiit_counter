package com.mitenko.repkit.ui.timer

import com.mitenko.repkit.R
import com.mitenko.repkit.domain.TimerText
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.TimerState
import com.mitenko.repkit.ui.common.UiText
import com.mitenko.repkit.ui.common.spokenWeight
import com.mitenko.repkit.ui.common.weightText

enum class PhaseTone { WORK, REST, NEUTRAL }

data class TimerUiState(
    /** Frozen in the run's snapshot at Start (spec §7.6). */
    val entryName: String,
    val setsText: String,
    val elapsedText: String,
    /** TalkBack for the Sets circle ("Sets 8 of 8"), which has no visible label (spec revision 37). */
    val setsDescription: UiText,
    /** TalkBack for Elapsed ("Elapsed 3 minutes 33 seconds"), which has no visible label (spec revision 37). */
    val elapsedDescription: UiText,
    /**
     * How far the elapsed band is filled (spec revision 38): elapsed ÷ the planned workout length, 0..1.
     * The engine's elapsed time holds while paused and jumps with a skip, so the fill does too; full at DONE.
     */
    val elapsedProgress: Float,
    /** The phase label over the countdown (none during WORK), resolved by the screen (spec revision 24). */
    val label: UiText?,
    val centerNumber: Int?,
    val centerDimmed: Boolean,
    val countdownText: String,
    val innerProgress: Float,
    val outerProgress: Float,
    val tone: PhaseTone,
    val paused: Boolean,
    val done: Boolean,
    /** What TalkBack says for the centre, resolved by the screen. */
    val description: UiText,
    /** Spec rev 26 §4, plan Spec note 56: a weight run's weight ("16 kg") over the reps in WORK and REST; null otherwise. */
    val weight: UiText? = null,
)

object TimerUiMapper {
    /**
     * [countsReps] is false for a Timer only run (spec revision 8, amended by revision 10): the
     * centre counts the sets down instead of showing reps — `sets - set + 1`, the current (or,
     * during REST/PREPARE, upcoming) set and every one still to come, including it — and no rep
     * value appears anywhere, including on DONE. The Voice cue says the same number
     * (TimerController.withReps). [weight] is a weight run's frozen weight (spec rev 26 §4): shown in
     * WORK and REST, and appended to their descriptions; never for Timer only.
     */
    fun map(s: TimerState, entryName: String, countsReps: Boolean = true, weight: SetWeight? = null): TimerUiState {
        val inner = if (s.phaseDurationSec > 0) s.phaseSecondsLeft.toFloat() / s.phaseDurationSec else 0f
        val workFraction = if (s.phase == Phase.WORK && s.phaseDurationSec > 0) {
            (s.phaseDurationSec - s.phaseSecondsLeft).toFloat() / s.phaseDurationSec
        } else {
            0f
        }
        val base = TimerUiState(
            entryName = entryName,
            setsText = "${s.set}/${s.sets}",
            elapsedText = TimerText.formatElapsed(s.elapsedSec),
            setsDescription = UiText.Res(R.string.timer_desc_sets, listOf(s.set, s.sets)),
            elapsedDescription = UiText.Res(R.string.timer_desc_elapsed, listOf(spokenElapsed(s.elapsedSec))),
            elapsedProgress = if (s.totalDurationSec > 0) (s.elapsedSec.toFloat() / s.totalDurationSec).coerceIn(0f, 1f) else 0f,
            label = null,
            centerNumber = null,
            centerDimmed = false,
            countdownText = TimerText.formatMmSs(s.phaseSecondsLeft),
            innerProgress = inner,
            outerProgress = ((s.completedWorkSets + workFraction) / s.sets).coerceIn(0f, 1f),
            tone = PhaseTone.NEUTRAL,
            paused = s.paused,
            done = false,
            description = UiText.Raw(""),
        )
        val setsToGo = s.sets - s.set + 1
        // Spec revision 24: the text is resource ids plus args; "last set" stays its own string,
        // and every other count goes through a plural.
        val mapped = when (s.phase) {
            Phase.WORK -> base.copy(
                centerNumber = if (countsReps) s.repsThisSet else setsToGo, tone = PhaseTone.WORK,
                description = if (countsReps) {
                    UiText.Plural(R.plurals.timer_desc_work_reps, s.repsThisSet, listOf(s.repsThisSet, s.set, s.sets))
                } else if (setsToGo == 1) {
                    UiText.Res(R.string.timer_desc_work_last_set)
                } else {
                    UiText.Plural(R.plurals.timer_desc_work_sets_to_go, setsToGo)
                },
            )
            Phase.REST -> base.copy(
                label = UiText.Res(R.string.timer_label_rest),
                centerNumber = if (countsReps) s.repsThisSet else setsToGo, centerDimmed = true, tone = PhaseTone.REST,
                description = if (countsReps) {
                    UiText.Plural(R.plurals.timer_desc_rest_reps, s.repsThisSet, listOf(s.repsThisSet, s.set, s.sets))
                } else if (setsToGo == 1) {
                    UiText.Res(R.string.timer_desc_rest_last_set)
                } else {
                    UiText.Plural(R.plurals.timer_desc_rest_sets_to_go, setsToGo)
                },
            )
            Phase.PREPARE -> base.copy(
                label = UiText.Res(R.string.timer_label_get_ready),
                centerNumber = if (countsReps) s.repsThisSet else setsToGo, centerDimmed = true,
                description = if (countsReps) {
                    UiText.Plural(R.plurals.timer_desc_prepare_reps, s.repsThisSet)
                } else {
                    UiText.Plural(R.plurals.timer_desc_prepare_sets, setsToGo)
                },
            )
            Phase.COOLDOWN -> base.copy(
                label = UiText.Res(R.string.timer_label_cooldown),
                description = UiText.Res(R.string.timer_desc_cooldown),
            )
            Phase.DONE -> base.copy(
                label = UiText.Res(R.string.timer_label_done),
                centerNumber = if (countsReps) s.totalReps else null, countdownText = "", innerProgress = 0f,
                outerProgress = 1f, elapsedProgress = 1f, done = true,
                description = if (countsReps) {
                    UiText.Plural(R.plurals.timer_desc_done_reps, s.totalReps)
                } else {
                    UiText.Res(R.string.timer_desc_done)
                },
            )
        }
        // Spec rev 26 §4, plan Spec note 56: a weight run shows its weight in WORK and in the rest preview.
        val w = weight?.takeIf { countsReps && (s.phase == Phase.WORK || s.phase == Phase.REST) } ?: return mapped
        return mapped.copy(
            weight = weightText(w.weight, w.unit),
            description = UiText.Res(R.string.timer_desc_with_weight, listOf(mapped.description, spokenWeight(w.weight, w.unit))),
        )
    }
}

/** "3 minutes 33 seconds" (spec revision 37); a whole minute drops the seconds and under a minute drops the minutes. */
private fun spokenElapsed(sec: Int): UiText {
    val minutes = sec / 60
    val seconds = sec % 60
    val m = UiText.Plural(R.plurals.duration_minutes, minutes)
    val s = UiText.Plural(R.plurals.duration_seconds, seconds)
    return when {
        minutes == 0 -> s
        seconds == 0 -> m
        else -> UiText.Res(R.string.duration_minutes_seconds, listOf(m, s))
    }
}
