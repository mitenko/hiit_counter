package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerTextTest {
    @Test
    fun `formats times`() {
        assertEquals("00:15", TimerText.formatMmSs(15))
        assertEquals("01:15", TimerText.formatMmSs(75))
        assertEquals("00:00:50", TimerText.formatHms(50))
        assertEquals("01:02:05", TimerText.formatHms(3725))
        assertEquals("04:00", TimerText.formatDuration(240))
        assertEquals("2:00:00", TimerText.formatDuration(7200))
    }
}
