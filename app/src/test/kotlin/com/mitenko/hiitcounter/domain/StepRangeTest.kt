package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StepRangeTest {
    @Test
    fun `steps clamp at the hard edges`() {
        assertEquals(0, FieldRanges.PHASE.minus(0))
        assertEquals(0, FieldRanges.PHASE.minus(3))
        assertEquals(3599, FieldRanges.PHASE.plus(3595))
        assertEquals(1, FieldRanges.WORK.minus(5))
        assertEquals(1, FieldRanges.WORK.minus(1))
        assertEquals(6, FieldRanges.WORK.plus(1))
        assertEquals(20, FieldRanges.SETS.plus(20))
        assertEquals(0, FieldRanges.STREAK.minus(0))
        assertEquals(9999, FieldRanges.REPS.plus(Int.MAX_VALUE))
    }

    @Test
    fun `clamp pulls dialog values into range`() {
        assertEquals(1, FieldRanges.REPS.clamp(0))
        assertEquals(9999, FieldRanges.REPS.clamp(12_000))
        assertEquals(0, FieldRanges.HOLD_FOR.clamp(-3))
        assertEquals(500, FieldRanges.WINDOW_HOURS.clamp(500))
    }

    @Test
    fun `ranges match the spec table`() {
        assertEquals(StepRange(0, 3599, 5), FieldRanges.PHASE)
        assertEquals(StepRange(1, 3599, 5), FieldRanges.WORK)
        assertEquals(StepRange(1, 20, 1), FieldRanges.SETS)
        assertEquals(StepRange(1, 9999, 1), FieldRanges.REPS)
        assertEquals(StepRange(0, 999, 1), FieldRanges.HOLD_FOR)
        assertEquals(StepRange(1, 999, 1), FieldRanges.WINDOW_HOURS)
        assertEquals(StepRange(1, 9999, 1), FieldRanges.TOTAL)
        assertEquals(StepRange(0, 99999, 1), FieldRanges.STREAK)
    }
}
