package com.mitenko.hiitcounter.domain.model

data class ProgressionConfig(
    val startingTotal: Int = 48,
    val floor: Int = 48,
    val cap: Int = 72,
    val holdAt: Int = 64,
    val holdFor: Int = 4,
    val windowHours: Int = 36,
    val penaltyHoursPerRep: Double = 19.5,
) {
    val holdEnabled: Boolean
        get() = holdFor > 0 && holdAt >= floor && holdAt < cap
}
