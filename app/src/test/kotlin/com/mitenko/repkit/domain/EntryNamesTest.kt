package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryNamesTest {
    @Test
    fun `trims and accepts 1 to 40 characters`() {
        assertEquals(NameCheck.Ok("Burpees"), EntryNames.validate("  Burpees "))
        assertEquals(NameCheck.Ok("a"), EntryNames.validate("a"))
        assertEquals(NameCheck.Ok("x".repeat(40)), EntryNames.validate("x".repeat(40)))
    }

    @Test
    fun `blank is empty`() {
        assertEquals(NameCheck.Empty, EntryNames.validate(""))
        assertEquals(NameCheck.Empty, EntryNames.validate("   "))
    }

    @Test
    fun `more than 40 characters after trimming is too long`() {
        assertEquals(NameCheck.TooLong, EntryNames.validate("x".repeat(41)))
        assertEquals(NameCheck.Ok("x".repeat(40)), EntryNames.validate(" " + "x".repeat(40) + " "))
    }

    @Test
    fun `length counts UTF-16 units`() {
        val flexed = "💪" // one emoji, two UTF-16 units
        assertEquals(NameCheck.Ok(flexed.repeat(20)), EntryNames.validate(flexed.repeat(20)))
        assertEquals(NameCheck.TooLong, EntryNames.validate(flexed.repeat(20) + "x"))
    }

    @Test
    fun `duplicate appends copy`() {
        assertEquals("Burpees copy", EntryNames.duplicateName("Burpees", " copy"))
    }

    @Test
    fun `long names are cut so the suffix survives`() {
        val dup = EntryNames.duplicateName("x".repeat(40), " copy")
        assertEquals("x".repeat(35) + " copy", dup)
        assertEquals(40, dup.length)
        assertTrue(dup.endsWith(" copy"))
        assertEquals(NameCheck.Ok(dup), EntryNames.validate(dup))
    }

    @Test
    fun `a suffix longer than the limit never cuts below an empty base`() {
        assertEquals("y".repeat(41), EntryNames.duplicateName("Burpees", "y".repeat(41)))
    }

    @Test
    fun `an invalid name exception carries the typed check`() {
        assertEquals(NameCheck.TooLong, InvalidEntryName(NameCheck.TooLong).check)
    }
}
