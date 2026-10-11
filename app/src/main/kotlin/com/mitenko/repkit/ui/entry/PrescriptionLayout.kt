package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.domain.Prescription

/** The prescription card's maths (spec rev 26 §4), kept pure so it's tested without Compose. */
object PrescriptionLayout {
    /**
     * How a check-in moved the load (plan Spec note 51): the weight decides first, so 16 kg × 12 → 20 kg × 8
     * is UP; at the same weight the reps decide. Null when the load is the same (a hold, or a fresh start).
     */
    fun change(before: Prescription.Load, after: Prescription.Load): RepsColumnLayout.Change? = when {
        after.weight > before.weight -> RepsColumnLayout.Change.UP
        after.weight < before.weight -> RepsColumnLayout.Change.DOWN
        after.reps > before.reps -> RepsColumnLayout.Change.UP
        after.reps < before.reps -> RepsColumnLayout.Change.DOWN
        else -> null
    }
}
