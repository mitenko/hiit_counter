package com.mitenko.repkit.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepsColumnLayoutTest {
    @Test
    fun `ten sets fit and only more than ten scroll`() {
        assertEquals(10, RepsColumnLayout.VISIBLE_ROWS)
        assertFalse(RepsColumnLayout.scrolls(1))
        assertFalse(RepsColumnLayout.scrolls(10))
        assertTrue(RepsColumnLayout.scrolls(11))
    }

    @Test
    fun `the spoken list names every set in order`() {
        assertEquals("8, 8, 8, 8, 8, 7, 7, 7", RepsColumnLayout.spoken(listOf(8, 8, 8, 8, 8, 7, 7, 7)))
        assertEquals("", RepsColumnLayout.spoken(emptyList()))
    }
}
