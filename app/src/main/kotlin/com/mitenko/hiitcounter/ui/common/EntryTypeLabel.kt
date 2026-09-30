package com.mitenko.hiitcounter.ui.common

import androidx.annotation.StringRes
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.model.EntryType

/** An entry type's user-facing name (spec R4 §6): "Workout" or "Check-in only". */
@get:StringRes
val EntryType.label: Int
    get() = when (this) {
        EntryType.WORKOUT -> R.string.type_workout
        EntryType.CHECK_IN -> R.string.type_check_in
    }
