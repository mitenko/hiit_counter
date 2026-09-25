package com.mitenko.hiitcounter.domain.model

data class TimingConfig(
    val prepareSec: Int = 10,
    val sets: Int = 8,
    val workSec: Int = 20,
    val restSec: Int = 10,
    val cooldownSec: Int = 0,
) {
    /** No rest after the final set. */
    val totalDurationSec: Int
        get() = prepareSec + sets * workSec + (sets - 1).coerceAtLeast(0) * restSec + cooldownSec
}
