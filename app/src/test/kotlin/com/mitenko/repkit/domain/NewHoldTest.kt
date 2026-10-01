package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import org.junit.Assert.assertEquals
import org.junit.Test

class NewHoldTest {
    @Test
    fun `a new hold sits four above the last one with its count`() {
        assertEquals(Hold(68, 4), newHold(listOf(Hold(64, 4)), floor = 48, cap = 72))
        assertEquals(Hold(60, 3), newHold(listOf(Hold(64, 4), Hold(56, 3)), floor = 48, cap = 72))
    }

    @Test
    fun `with no holds it sits four above the floor for 4`() {
        assertEquals(Hold(52, 4), newHold(emptyList(), floor = 48, cap = 72))
    }

    @Test
    fun `at or above the cap, or a duplicate, takes the first free value from the floor`() {
        assertEquals(Hold(48, 2), newHold(listOf(Hold(70, 2)), floor = 48, cap = 72))
        assertEquals(Hold(49, 2), newHold(listOf(Hold(48, 1), Hold(70, 2)), floor = 48, cap = 72))
        assertEquals(Hold(49, 1), newHold(listOf(Hold(52, 9), Hold(48, 1)), floor = 48, cap = 72))
    }

    @Test
    fun `with no free value it is added anyway for the validator to flag`() {
        assertEquals(Hold(64, 4), newHold(listOf(Hold(60, 4)), floor = 60, cap = 61))
    }
}
