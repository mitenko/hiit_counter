package com.mitenko.hiitcounter.domain

import java.time.Instant
import java.time.ZoneId

/** Wall-clock + monotonic time. The only real implementation is platform/AndroidClock. */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
    fun elapsedRealtimeMs(): Long
}
