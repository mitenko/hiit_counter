package com.mitenko.repkit.domain.model

/** Spec rev 34 §2: an At hold holds one value; a From hold holds every value from [Hold.at] up to (not including) the cap. */
enum class HoldKind { AT, FROM }

/**
 * One hold (spec rev 16 §2, rev 34 §2): the total pauses at [at] for [forCount] check-ins, counting
 * the day it's reached. For a [HoldKind.FROM] hold, [at] is the first value of its range and every
 * value from there up to the cap is held the same way.
 */
data class Hold(val at: Int, val forCount: Int, val kind: HoldKind = HoldKind.AT)

data class ProgressionConfig(
    val startingTotal: Int = 48,
    val floor: Int = 48,
    val cap: Int = 72,
    /** In the user's order, never sorted (spec rev 16 §2). */
    val holds: List<Hold> = listOf(DEFAULT_HOLD),
    val windowHours: Int = 36,
    val penaltyHoursPerRep: Double = 19.5,
    /** The Hold switch (spec R3 §5.1, rev 16 §2). Off keeps every hold stored, but unused. All modes share it. */
    val hold: Boolean = true,
    /** Spec rev 26 §1: what a check-in moves. In a weight mode, CounterState.total is the level on the ladder. */
    val mode: ProgressMode = ProgressMode.REPS,
    /** Spec rev 26 §2: the weight settings, kept in every mode. */
    val weight: WeightConfig = WeightConfig(),
) {
    /** A hold of either kind takes effect only with the switch on, a positive count and floor ≤ at < cap (rev 16 §2, rev 34 §2). */
    fun isActive(h: Hold): Boolean = hold && h.forCount > 0 && h.at >= floor && h.at < cap

    /** The holds that take effect, in list order. */
    val activeHolds: List<Hold>
        get() = holds.filter(::isActive)

    /**
     * The hold at [total], or null (spec rev 34 §2, the most specific wins): the active At hold on
     * [total] (the first, for duplicates the validator rejects); otherwise the active From hold with
     * the greatest start at or below [total]. A From hold covers start..<cap, so the cap never holds.
     */
    fun activeHold(total: Int): Hold? =
        holds.firstOrNull { it.kind == HoldKind.AT && it.at == total && isActive(it) }
            ?: holds.filter { it.kind == HoldKind.FROM && it.at <= total && total < cap && isActive(it) }.maxByOrNull { it.at }

    companion object {
        const val MAX_HOLDS = 8
        val DEFAULT_HOLD = Hold(64, 4)
    }
}
