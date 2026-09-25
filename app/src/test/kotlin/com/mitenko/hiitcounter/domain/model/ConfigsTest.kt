package com.mitenko.hiitcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigsTest {
    @Test
    fun `default timing totals four minutes with no rest after the last set`() {
        assertEquals(240, TimingConfig().totalDurationSec)
        assertEquals(10 + 20 + 5, TimingConfig(sets = 1, cooldownSec = 5).totalDurationSec)
    }

    @Test
    fun `hold is enabled only for a positive count and holdAt within floor to cap exclusive`() {
        assertTrue(ProgressionConfig().holdEnabled)
        assertFalse(ProgressionConfig(holdFor = 0).holdEnabled)
        assertFalse(ProgressionConfig(holdAt = 72).holdEnabled)
        assertFalse(ProgressionConfig(holdAt = 40).holdEnabled)
        assertTrue(ProgressionConfig(holdAt = 48).holdEnabled)
    }
}
