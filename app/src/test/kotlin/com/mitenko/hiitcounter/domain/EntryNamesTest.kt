package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("Burpees copy", EntryNames.duplicateName("Burpees"))
    }

    @Test
    fun `long names are cut so the suffix survives`() {
        val dup = EntryNames.duplicateName("x".repeat(40))
        assertEquals("x".repeat(35) + " copy", dup)
        assertEquals(40, dup.length)
        assertTrue(dup.endsWith(EntryNames.COPY_SUFFIX))
        assertEquals(NameCheck.Ok(dup), EntryNames.validate(dup))
    }

    @Test
    fun `error messages explain the failure`() {
        assertEquals("Enter a name", EntryNames.errorMessage(NameCheck.Empty))
        assertEquals("Use at most 40 characters", EntryNames.errorMessage(NameCheck.TooLong))
        assertNull(EntryNames.errorMessage(NameCheck.Ok("Burpees")))
    }
}
