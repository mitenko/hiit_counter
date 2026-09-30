package com.mitenko.hiitcounter.ui.entries

import org.junit.Assert.assertEquals
import org.junit.Test

class ListReorderTest {
    private val list = listOf("A", "B", "C", "D", "E")

    @Test
    fun `moves an element down`() {
        assertEquals(listOf("A", "C", "D", "B", "E"), list.moved(1, 3))
    }

    @Test
    fun `moves an element up`() {
        assertEquals(listOf("A", "D", "B", "C", "E"), list.moved(3, 1))
    }

    @Test
    fun `a no-op when from equals to`() {
        assertEquals(list, list.moved(2, 2))
    }

    @Test
    fun `first to last`() {
        assertEquals(listOf("B", "C", "D", "E", "A"), list.moved(0, 4))
    }

    @Test
    fun `last to first`() {
        assertEquals(listOf("E", "A", "B", "C", "D"), list.moved(4, 0))
    }
}
