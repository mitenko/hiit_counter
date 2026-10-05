package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Spec revision 27: a Current total outside floor..cap widens the range to include it. */
class RangeWideningTest {
    private val config = ProgressionConfig(startingTotal = 50, floor = 48, cap = 72, holds = listOf(Hold(64, 4)), windowHours = 30)

    @Test
    fun `a total above the cap raises the cap and nothing else`() {
        assertEquals(config.copy(cap = 80), config.widenedFor(80))
        assertEquals(RangeChange.RaisedMax(80), rangeChange(config, config.widenedFor(80)))
    }

    @Test
    fun `a total below the floor lowers the floor and nothing else`() {
        assertEquals(config.copy(floor = 40), config.widenedFor(40))
        assertEquals(RangeChange.LoweredMin(40), rangeChange(config, config.widenedFor(40)))
    }

    @Test
    fun `a total inside the range, edges included, changes nothing`() {
        for (total in listOf(48, 60, 72)) {
            assertSame(config, config.widenedFor(total))
            assertNull(rangeChange(config, config.widenedFor(total)))
        }
    }
}
