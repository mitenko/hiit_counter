package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerTextTest {
    @Test
    fun `formatCountdown is whole seconds under a minute, m colon ss from a minute`() {
        assertEquals("0", TimerText.formatCountdown(0))
        assertEquals("7", TimerText.formatCountdown(7))
        assertEquals("59", TimerText.formatCountdown(59))
        assertEquals("1:00", TimerText.formatCountdown(60))
        assertEquals("1:30", TimerText.formatCountdown(90))
        assertEquals("10:00", TimerText.formatCountdown(600))
    }

    @Test
    fun `formats times`() {
        assertEquals("00:15", TimerText.formatMmSs(15))
        assertEquals("01:15", TimerText.formatMmSs(75))
        assertEquals("04:00", TimerText.formatDuration(240))
        assertEquals("2:00:00", TimerText.formatDuration(7200))
    }

    @Test
    fun `elapsed is mm-ss and keeps counting minutes past an hour`() {
        // Spec revision 37: no hours on the timer's Elapsed circle.
        assertEquals("00:00", TimerText.formatElapsed(0))
        assertEquals("03:33", TimerText.formatElapsed(213))
        assertEquals("60:00", TimerText.formatElapsed(3600))
        assertEquals("75:12", TimerText.formatElapsed(4512))
    }
}
