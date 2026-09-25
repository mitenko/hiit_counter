package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.domain.Clock
import java.time.Instant
import java.time.ZoneId

class FakeClock(
    var instant: Instant = Instant.parse("2026-09-24T12:55:00Z"),
    var zoneId: ZoneId = ZoneId.of("America/Los_Angeles"),
    var elapsedMs: Long = 0L,
) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    override fun elapsedRealtimeMs(): Long = elapsedMs
}
