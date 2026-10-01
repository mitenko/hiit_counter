package com.mitenko.repkit.domain.model

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

    @Test
    fun `a new entry is a workout unless told otherwise`() {
        val entry = Entry(1, "Burpees", 0, TimingConfig(), ProgressionConfig(), CueConfig(), CounterState(total = 48))
        assertEquals(EntryType.WORKOUT, entry.type)
        assertEquals(EntryType.CHECK_IN, entry.copy(type = EntryType.CHECK_IN).type)
        assertEquals(listOf(EntryType.WORKOUT, EntryType.CHECK_IN), EntryType.entries.toList())
    }
}
