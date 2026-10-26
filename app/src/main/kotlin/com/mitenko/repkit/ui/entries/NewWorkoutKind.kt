package com.mitenko.repkit.ui.entries

import androidx.annotation.StringRes
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode

/**
 * The New workout dialog's three choices (spec rev 44): shortcuts to a type and a progression mode.
 * Weights is a Counter in reps-then-weight mode; afterwards the type and mode change in Settings as usual.
 */
enum class NewWorkoutKind(@StringRes val label: Int, val type: EntryType, val mode: ProgressMode) {
    REPS(R.string.new_kind_reps, EntryType.WORKOUT, ProgressMode.REPS),
    WEIGHTS(R.string.new_kind_weights, EntryType.WORKOUT, ProgressMode.REPS_THEN_WEIGHT),
    TIMER(R.string.new_kind_timer, EntryType.CHECK_IN, ProgressMode.REPS),
}
