package com.mitenko.repkit.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.WeightProblem
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit

/** "kg" / "lb". */
@get:StringRes
val WeightUnit.shortLabel: Int
    get() = when (this) {
        WeightUnit.KG -> R.string.unit_kg
        WeightUnit.LB -> R.string.unit_lb
    }

/** "Kilograms (kg)" / "Pounds (lb)", for ⚙ › Units. */
@get:StringRes
val WeightUnit.longLabel: Int
    get() = when (this) {
        WeightUnit.KG -> R.string.unit_kg_long
        WeightUnit.LB -> R.string.unit_lb_long
    }

/** A Progress by segment (spec rev 26 §3). */
@get:StringRes
val ProgressMode.label: Int
    get() = when (this) {
        ProgressMode.REPS -> R.string.progress_reps
        ProgressMode.WEIGHT -> R.string.progress_weight
        ProgressMode.REPS_THEN_WEIGHT -> R.string.progress_reps_then_weight
    }

/** "22.5 kg" (plan Spec note 27); just the number while the workout has no unit. */
fun weightText(hundredths: Int, unit: WeightUnit?): UiText =
    if (unit == null) {
        UiText.Raw(WeightFormat.format(hundredths))
    } else {
        UiText.Res(R.string.weight_value, listOf(WeightFormat.format(hundredths), UiText.Res(unit.shortLabel)))
    }

/** "Start (kg)": a weight row's label with the workout's unit (plan Spec note 27); the bare label while there's no unit. */
@Composable
fun unitLabel(@StringRes label: Int, unit: WeightUnit?): String =
    if (unit == null) stringResource(label) else stringResource(R.string.label_with_unit, stringResource(label), stringResource(unit.shortLabel))

/** A weight field's error (spec rev 26 §3.1). */
fun WeightProblem.uiText(): UiText = when (this) {
    WeightProblem.TooFewWeights -> UiText.Plural(R.plurals.error_too_few_weights, WeightConfig.MIN_WEIGHTS)
    WeightProblem.TooManyWeights -> UiText.Plural(R.plurals.error_too_many_weights, WeightConfig.MAX_WEIGHTS)
    is WeightProblem.WeightOutOfRange, WeightProblem.StepsStart -> UiText.Res(R.string.error_weight_range)
    is WeightProblem.DuplicateWeight -> UiText.Res(R.string.error_duplicate_weight)
    WeightProblem.StepsStep -> UiText.Res(R.string.error_steps_step)
    WeightProblem.StepsTop -> UiText.Res(R.string.error_steps_top)
    WeightProblem.RepsPerSet, WeightProblem.RepMin -> UiText.Res(R.string.error_reps_range)
    WeightProblem.RepMax -> UiText.Res(R.string.error_rep_max)
    WeightProblem.StartWeight, is WeightProblem.HoldWeight -> UiText.Res(R.string.error_not_a_weight)
    WeightProblem.StartReps, is WeightProblem.HoldReps -> UiText.Res(R.string.error_outside_rep_range)
    WeightProblem.TooManyHolds -> UiText.Plural(R.plurals.error_too_many_holds, ProgressionConfig.MAX_HOLDS)
    is WeightProblem.HoldFor -> UiText.Res(R.string.error_zero_or_more)
    is WeightProblem.DuplicateHold -> UiText.Res(R.string.error_duplicate_weight_hold)
}

/** One weight-mode note (plan Spec notes 30–31), weights in [unit]. */
fun WeightMove.uiText(unit: WeightUnit?): UiText = when (this) {
    is WeightMove.TopRaised -> UiText.Res(R.string.moved_top_raised, listOf(weightText(to, unit)))
    is WeightMove.TopLowered -> UiText.Res(R.string.moved_top_lowered, listOf(weightText(to, unit)))
    is WeightMove.StartLowered -> UiText.Res(R.string.moved_steps_start_lowered, listOf(weightText(to, unit)))
    is WeightMove.RepMaxRaised -> UiText.Res(R.string.moved_rep_max_raised, listOf(to))
    is WeightMove.RepMinLowered -> UiText.Res(R.string.moved_rep_min_lowered, listOf(to))
    is WeightMove.StartRepsRaised -> UiText.Res(R.string.moved_start_reps_raised, listOf(to))
    is WeightMove.StartRepsLowered -> UiText.Res(R.string.moved_start_reps_lowered, listOf(to))
    is WeightMove.StartWeightMoved -> UiText.Res(R.string.moved_start_weight, listOf(weightText(to, unit)))
    is WeightMove.HoldsRemoved -> UiText.Plural(R.plurals.moved_holds_removed, count)
    is WeightMove.CurrentMoved ->
        if (reps == null) {
            UiText.Res(R.string.moved_current_weight, listOf(weightText(weight, unit)))
        } else {
            UiText.Res(R.string.moved_current_load, listOf(weightText(weight, unit), reps))
        }
}

/** Every move of one edit on one line (revision 28's separator). */
@Composable
fun weightNoteText(moves: List<WeightMove>, unit: WeightUnit?): String = moves.map { it.uiText(unit).resolve() }.joinToString(MOVE_SEPARATOR)
