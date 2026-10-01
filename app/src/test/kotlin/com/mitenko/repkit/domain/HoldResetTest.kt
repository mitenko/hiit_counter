package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressionConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldResetTest {
    private val base = ProgressionConfig()
    private val two = ProgressionConfig(holds = listOf(Hold(56, 3), Hold(64, 4)))

    @Test
    fun `edits that leave the holds unchanged keep the hold count`() {
        assertFalse(holdResetNeeded(base, base))
        assertFalse(holdResetNeeded(base, base.copy(startingTotal = 50)))
        assertFalse(holdResetNeeded(base, base.copy(windowHours = 30)))
        assertFalse(holdResetNeeded(base, base.copy(penaltyHoursPerRep = 12.5)))
        assertFalse(holdResetNeeded(base, base.copy(floor = 40)))
        assertFalse(holdResetNeeded(base, base.copy(cap = 80)))
        assertFalse(holdResetNeeded(two, two.copy(cap = 80, startingTotal = 50, windowHours = 30)))
    }

    @Test
    fun `hold at, hold for and switch edits reset the hold count`() {
        assertTrue(holdResetNeeded(base, base.copy(holds = listOf(Hold(66, 4)))))
        assertTrue(holdResetNeeded(base, base.copy(holds = listOf(Hold(64, 3)))))
        assertTrue(holdResetNeeded(base, base.copy(hold = false)))
        assertTrue(holdResetNeeded(base.copy(hold = false), base))
        // Rev 16: any switch change resets, even when no hold was active (hold for 0).
        val inactive = base.copy(holds = listOf(Hold(64, 0)))
        assertTrue(holdResetNeeded(inactive, inactive.copy(hold = false)))
    }

    @Test
    fun `adding, removing or reordering holds resets the hold count`() {
        assertTrue(holdResetNeeded(base, two))
        assertTrue(holdResetNeeded(two, base))
        assertTrue(holdResetNeeded(two, two.copy(holds = two.holds.reversed())))
        assertTrue(holdResetNeeded(base, base.copy(holds = emptyList())))
        assertTrue(holdResetNeeded(two, two.copy(holds = listOf(Hold(56, 3), Hold(64, 5)))))
    }

    @Test
    fun `a floor or cap edit that switches a hold on or off resets the hold count`() {
        assertTrue(holdResetNeeded(base, base.copy(cap = 64)))                        // at == cap: off
        assertTrue(holdResetNeeded(base, base.copy(floor = 65, startingTotal = 65)))  // at < floor: off
        assertTrue(holdResetNeeded(base.copy(cap = 64), base))                        // back on
        assertTrue(holdResetNeeded(two, two.copy(cap = 60)))                          // only the upper hold goes off
        assertTrue(holdResetNeeded(two, two.copy(floor = 57, startingTotal = 57)))    // only the lower hold goes off
    }

    @Test
    fun `only a total change resets the counter's hold count`() {
        assertFalse(counterHoldReset(64, 64))
        assertTrue(counterHoldReset(64, 65))
        assertTrue(counterHoldReset(64, 48))
    }
}
