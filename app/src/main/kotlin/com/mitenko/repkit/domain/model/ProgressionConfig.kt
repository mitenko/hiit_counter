package com.mitenko.repkit.domain.model

/** One hold (spec rev 16 §2): the total pauses at [at] for [forCount] check-ins, counting the day it's reached. */
data class Hold(val at: Int, val forCount: Int)

data class ProgressionConfig(
    val startingTotal: Int = 48,
    val floor: Int = 48,
    val cap: Int = 72,
    /** In the user's order, never sorted (spec rev 16 §2). */
    val holds: List<Hold> = listOf(DEFAULT_HOLD),
    val windowHours: Int = 36,
    val penaltyHoursPerRep: Double = 19.5,
    /** The Hold switch (spec R3 §5.1, rev 16 §2). Off keeps every hold stored, but unused. */
    val hold: Boolean = true,
) {
    /** A hold takes effect only with the switch on, a positive count and floor ≤ at < cap. */
    fun isActive(h: Hold): Boolean = hold && h.forCount > 0 && h.at >= floor && h.at < cap

    /** The holds that take effect, in list order. */
    val activeHolds: List<Hold>
        get() = holds.filter(::isActive)

    /** The active hold at [total], or null. Duplicates (which the validator rejects) take the first match. */
    fun activeHold(total: Int): Hold? = holds.firstOrNull { it.at == total && isActive(it) }

    companion object {
        const val MAX_HOLDS = 8
        val DEFAULT_HOLD = Hold(64, 4)
    }
}
