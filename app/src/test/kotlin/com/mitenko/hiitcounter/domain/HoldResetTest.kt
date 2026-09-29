package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldResetTest {
    private val base = ProgressionConfig()

    @Test
    fun `edits that leave the hold unchanged keep the hold count`() {
        assertFalse(holdResetNeeded(base, base))
        assertFalse(holdResetNeeded(base, base.copy(startingTotal = 50)))
        assertFalse(holdResetNeeded(base, base.copy(windowHours = 30)))
        assertFalse(holdResetNeeded(base, base.copy(penaltyHoursPerRep = 12.5)))
        assertFalse(holdResetNeeded(base, base.copy(floor = 40)))
        assertFalse(holdResetNeeded(base, base.copy(cap = 80)))
        // Literal §6.3: a switch toggle that leaves the effective hold off (hold for 0) keeps the count.
        assertFalse(holdResetNeeded(base.copy(holdFor = 0), base.copy(holdFor = 0, hold = false)))
    }

    @Test
    fun `hold at, hold for and switch edits reset the hold count`() {
        assertTrue(holdResetNeeded(base, base.copy(holdAt = 66)))
        assertTrue(holdResetNeeded(base, base.copy(holdFor = 3)))
        assertTrue(holdResetNeeded(base, base.copy(hold = false)))
        assertTrue(holdResetNeeded(base.copy(hold = false), base))
    }

    @Test
    fun `a floor or cap edit that switches the hold on or off resets the hold count`() {
        assertTrue(holdResetNeeded(base, base.copy(cap = 64)))                        // holdAt == cap: off
        assertTrue(holdResetNeeded(base, base.copy(floor = 65, startingTotal = 65)))  // holdAt < floor: off
        assertTrue(holdResetNeeded(base.copy(cap = 64), base))                        // back on
    }

    @Test
    fun `only a total change resets the counter's hold count`() {
        assertFalse(counterHoldReset(64, 64))
        assertTrue(counterHoldReset(64, 65))
        assertTrue(counterHoldReset(64, 48))
    }
}
