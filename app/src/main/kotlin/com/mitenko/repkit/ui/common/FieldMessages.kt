package com.mitenko.repkit.ui.common

import androidx.compose.runtime.Composable
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.EntryNames
import com.mitenko.repkit.domain.FieldMessage
import com.mitenko.repkit.domain.NameCheck

/** The text for a typed settings message (spec revision 24); `domain/` returns the type, this picks the resource. */
fun FieldMessage.uiText(): UiText = when (this) {
    is FieldMessage.SetsRange -> UiText.Plural(R.plurals.error_sets_range, max)
    FieldMessage.WorkRange -> UiText.Res(R.string.error_work_range)
    FieldMessage.PhaseRange -> UiText.Res(R.string.error_phase_range)
    FieldMessage.WorkoutTooLong -> UiText.Res(R.string.error_workout_too_long)
    FieldMessage.AtLeastOne -> UiText.Res(R.string.error_at_least_one)
    FieldMessage.ZeroOrMore -> UiText.Res(R.string.error_zero_or_more)
    FieldMessage.GreaterThanZero -> UiText.Res(R.string.error_greater_than_zero)
    FieldMessage.AtLeastFloor -> UiText.Res(R.string.error_at_least_floor)
    FieldMessage.AtLeastStartingTotal -> UiText.Res(R.string.error_at_least_starting_total)
    FieldMessage.AtLeastCurrentStreak -> UiText.Res(R.string.error_at_least_current_streak)
    is FieldMessage.TooManyHolds -> UiText.Plural(R.plurals.error_too_many_holds, max)
    is FieldMessage.DuplicateHold -> UiText.Res(R.string.error_duplicate_hold, listOf(at))
    FieldMessage.HoldDisabled -> UiText.Res(R.string.hint_hold_disabled)
    FieldMessage.InTheFuture -> UiText.Res(R.string.error_in_the_future)
}

/** The name dialog's inline explanation (spec §8.3); null when the name is valid. */
fun NameCheck.uiText(): UiText? = when (this) {
    is NameCheck.Ok -> null
    NameCheck.Empty -> UiText.Res(R.string.error_enter_name)
    NameCheck.TooLong -> UiText.Plural(R.plurals.error_name_too_long, EntryNames.MAX_LENGTH)
}

/** A settings row's error or hint text, or null when there is none. */
@Composable
fun FieldMessage?.resolve(): String? = this?.uiText()?.resolve()
