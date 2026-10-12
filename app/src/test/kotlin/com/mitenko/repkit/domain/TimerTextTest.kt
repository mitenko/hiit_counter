package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerTextTest {
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
