package com.mitenko.hiitcounter.platform

import android.os.SystemClock
import com.mitenko.hiitcounter.domain.Clock
import java.time.Instant
import java.time.ZoneId

/** The only place that reads real time. */
object AndroidClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
