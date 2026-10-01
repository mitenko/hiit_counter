package com.mitenko.repkit.domain

import kotlin.math.floor

/**
 * Penalty-rate draft (spec §8.1), held as whole half-hours so hold-to-repeat never drifts.
 * A dialog (or stored) value that isn't a multiple of 0.5 is kept exactly in [exact] for
 * display and saving; the next ± press snaps it to the neighbouring multiple (0.3 → + 0.5,
 * − clamps to 0.5).
 */
data class PenaltyDraft(val halfHours: Int, val exact: Double? = null) {
    val hours: Double get() = exact ?: (halfHours / 2.0)

    fun plus(): PenaltyDraft = PenaltyDraft(clampHalf(if (exact != null) floor(exact * 2).toInt() + 1 else halfHours + 1))

    fun minus(): PenaltyDraft = PenaltyDraft(clampHalf(if (exact != null) floor(exact * 2).toInt() else halfHours - 1))

    companion object {
        /** 0.5 h. */
        const val MIN_HALF_HOURS = 1
        /** 999.5 h. */
        const val MAX_HALF_HOURS = 1999
        const val MIN_HOURS = 0.5
        const val MAX_HOURS = 999.5

        fun of(hours: Double): PenaltyDraft {
            val doubled = hours * 2
            return if (doubled == floor(doubled) && doubled <= Int.MAX_VALUE) {
                PenaltyDraft(doubled.toInt())
            } else {
                PenaltyDraft(floor(doubled).toInt(), exact = hours)
            }
        }

        private fun clampHalf(halfHours: Int): Int = halfHours.coerceIn(MIN_HALF_HOURS, MAX_HALF_HOURS)
    }
}
