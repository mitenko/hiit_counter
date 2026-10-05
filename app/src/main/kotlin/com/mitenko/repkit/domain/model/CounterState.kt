package com.mitenko.repkit.domain.model

import java.time.Instant

data class CounterState(
    val total: Int,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val lastCheckIn: Instant? = null,
    val holdCount: Int = 0,
    /**
     * True after Start fresh (a progress-mode switch) until the next recorded Counter check-in (plan Spec
     * note 13, user ruling A): that check-in is performed at the start, with no +1 and no penalty.
     * A Timer only check-in keeps it.
     */
    val freshStart: Boolean = false,
)
