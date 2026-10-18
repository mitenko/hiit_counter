package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.HoldKind
import com.mitenko.repkit.domain.model.ProgressionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec rev 40: how many more on-time check-ins stay on the current hold ("Hold for x more"). */
class HoldRemainingTest {
    private val cfg = ProgressionConfig(holds = listOf(Hold(64, 4)))

    @Test
    fun `the day a hold is reached counts, so three remain of four`() {
        assertEquals(3, holdRemaining(cfg, CounterState(total = 64, holdCount = 1)))
    }

    @Test
    fun `the last held day leaves none, so nothing shows`() {
        assertEquals(1, holdRemaining(cfg, CounterState(total = 64, holdCount = 3)))
        assertNull(holdRemaining(cfg, CounterState(total = 64, holdCount = 4)))
    }

    @Test
    fun `a hold not yet counted holds for its full length`() {
        assertEquals(4, holdRemaining(cfg, CounterState(total = 64, holdCount = 0)))
    }

    @Test
    fun `off a hold there is nothing to show`() {
        assertNull(holdRemaining(cfg, CounterState(total = 60, holdCount = 0)))
    }

    @Test
    fun `the Hold switch off shows nothing`() {
        assertNull(holdRemaining(cfg.copy(hold = false), CounterState(total = 64, holdCount = 1)))
    }

    @Test
    fun `a From hold counts on every value it covers`() {
        val from = ProgressionConfig(holds = listOf(Hold(60, 2, HoldKind.FROM)))
        assertEquals(1, holdRemaining(from, CounterState(total = 66, holdCount = 1)))
        assertNull(holdRemaining(from, CounterState(total = 72, holdCount = 0)))
    }

    @Test
    fun `a stored total outside the range is clamped like a check-in`() {
        val low = ProgressionConfig(floor = 64, startingTotal = 64, holds = listOf(Hold(64, 4)))
        assertEquals(3, holdRemaining(low, CounterState(total = 50, holdCount = 1)))
    }
}
