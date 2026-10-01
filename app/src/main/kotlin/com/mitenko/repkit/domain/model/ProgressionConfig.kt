package com.mitenko.repkit.domain.model

data class ProgressionConfig(
    val startingTotal: Int = 48,
    val floor: Int = 48,
    val cap: Int = 72,
    val holdAt: Int = 64,
    val holdFor: Int = 4,
    val windowHours: Int = 36,
    val penaltyHoursPerRep: Double = 19.5,
    /** The Hold switch (spec R3 §5.1). Off keeps [holdAt] and [holdFor] stored, but unused. */
    val hold: Boolean = true,
) {
    /** RepProgression reads only this, so the switch needs no change there (spec R3 §5.1). */
    val holdEnabled: Boolean
        get() = hold && holdFor > 0 && holdAt >= floor && holdAt < cap
}
