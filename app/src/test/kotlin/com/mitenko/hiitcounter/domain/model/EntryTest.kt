package com.mitenko.hiitcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryTest {
    @Test
    fun `errors carry the id and a readable message`() {
        val notFound = EntryNotFound(7)
        assertEquals(7L, notFound.id)
        assertEquals("Entry 7 not found", notFound.message)
        val busy = EntryBusy(3)
        assertEquals(3L, busy.id)
        assertEquals("Entry 3 has an active workout", busy.message)
    }
}
