package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValueFormatTest {
    @Test
    fun `m colon ss is accepted`() {
        assertEquals(90, ValueFormat.parseSeconds("1:30"))
        assertEquals(5, ValueFormat.parseSeconds("0:05"))
        assertEquals(3599, ValueFormat.parseSeconds("59:59"))
        assertEquals(120, ValueFormat.parseSeconds(" 2:00 "))
        assertEquals(10, ValueFormat.parseSeconds("00:10"))
    }

    @Test
    fun `plain whole seconds are accepted`() {
        assertEquals(90, ValueFormat.parseSeconds("90"))
        assertEquals(0, ValueFormat.parseSeconds("0"))
    }

    @Test
    fun `malformed times are rejected`() {
        listOf("1:5", "1:60", "-5", "-1:30", "", "  ", "abc", "1:30:00", "1.5", ":30", "1:", "99999999999").forEach {
            assertNull("'$it' should not parse", ValueFormat.parseSeconds(it))
        }
    }

    @Test
    fun `formats as mm ss`() {
        assertEquals("01:30", ValueFormat.formatSeconds(90))
        assertEquals("00:05", ValueFormat.formatSeconds(5))
        assertEquals("59:59", ValueFormat.formatSeconds(3599))
    }

    @Test
    fun `parseInt accepts non-negative whole numbers`() {
        assertEquals(0, ValueFormat.parseInt("0"))
        assertEquals(42, ValueFormat.parseInt("42"))
        assertEquals(7, ValueFormat.parseInt(" 7 "))
    }

    @Test
    fun `parseInt rejects everything else`() {
        listOf("-1", "1.5", "", "abc", "99999999999", "+3", "1 000").forEach {
            assertNull("'$it' should not parse", ValueFormat.parseInt(it))
        }
    }

    @Test
    fun `parseDecimal accepts a dot or a comma`() {
        assertEquals(19.5, ValueFormat.parseDecimal("19.5")!!, 0.0)
        assertEquals(19.5, ValueFormat.parseDecimal("19,5")!!, 0.0)
        assertEquals(2.0, ValueFormat.parseDecimal("2")!!, 0.0)
        assertEquals(0.5, ValueFormat.parseDecimal(".5")!!, 0.0)
    }

    @Test
    fun `parseDecimal rejects negatives, NaN, infinity and garbage`() {
        listOf("-1", "-0.5", "NaN", "Infinity", "1e5", "", "abc", "1.2.3", "1,2,3").forEach {
            assertNull("'$it' should not parse", ValueFormat.parseDecimal(it))
        }
    }

    @Test
    fun `formatDecimal drops trailing zeros`() {
        assertEquals("19.5", ValueFormat.formatDecimal(19.5))
        assertEquals("20", ValueFormat.formatDecimal(20.0))
        assertEquals("0.3", ValueFormat.formatDecimal(0.3))
    }
}
