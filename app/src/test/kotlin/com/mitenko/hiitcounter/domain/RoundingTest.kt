package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundingTest {
    @Test
    fun `rounds half up like JavaScript Math round`() {
        assertEquals(1, roundHalfUp(0.5))
        assertEquals(1, roundHalfUp(1.4999))
        assertEquals(3, roundHalfUp(2.5))
        assertEquals(1, roundHalfUp(0.667))
        assertEquals(0, roundHalfUp(-0.5))
        assertEquals(-1, roundHalfUp(-0.51))
    }
}
