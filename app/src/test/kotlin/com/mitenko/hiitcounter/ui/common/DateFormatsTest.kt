package com.mitenko.hiitcounter.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DateFormatsTest {
    @Test
    fun `formats like the sheet in the given zone`() {
        val t = Instant.parse("2026-09-24T12:55:00Z")
        val zone = ZoneId.of("America/Los_Angeles")
        assertEquals("24 Sep 2026, 05:55", DateFormats.dateTime(t, zone))
        assertEquals("24 Sep 2026", DateFormats.date(t, zone))
    }
}
