package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightFormatTest {
    @Test
    fun `formats hundredths as the shortest plain decimal`() {
        assertEquals("22.5", WeightFormat.format(2250))
        assertEquals("20", WeightFormat.format(2000))
        assertEquals("1.25", WeightFormat.format(125))
        assertEquals("0.05", WeightFormat.format(5))
        assertEquals("999.75", WeightFormat.format(99_975))
        assertEquals("0", WeightFormat.format(0))
    }

    @Test
    fun `parses a dot or a comma with up to two decimals`() {
        assertEquals(2250, WeightFormat.parse("22.5"))
        assertEquals(2250, WeightFormat.parse(" 22,5 "))
        assertEquals(2000, WeightFormat.parse("20"))
        assertEquals(2000, WeightFormat.parse("20."))
        assertEquals(50, WeightFormat.parse(".5"))
        assertEquals(125, WeightFormat.parse("1.25"))
        assertEquals(0, WeightFormat.parse("0"))
    }

    @Test
    fun `rejects three decimals, signs, letters and blanks`() {
        listOf("1.255", "-5", "+5", "abc", "", " ", ".", "1.2.3", "1e3", "1,2,3").forEach {
            assertNull(it, WeightFormat.parse(it))
        }
    }

    @Test
    fun `rejects a value too large for an Int of hundredths`() {
        assertNull(WeightFormat.parse("99999999999"))
    }

    @Test
    fun `format and parse round trip`() {
        listOf(25, 125, 2250, 99_975).forEach { assertEquals(it, WeightFormat.parse(WeightFormat.format(it))) }
    }
}
