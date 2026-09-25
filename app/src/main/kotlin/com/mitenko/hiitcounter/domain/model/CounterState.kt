package com.mitenko.hiitcounter.domain.model

import java.time.Instant

data class CounterState(
    val total: Int,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val lastCheckIn: Instant? = null,
    val holdCount: Int = 0,
)
