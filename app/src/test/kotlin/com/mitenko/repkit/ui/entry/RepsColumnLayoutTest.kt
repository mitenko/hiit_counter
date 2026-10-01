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

    @Test
    fun `one set gaining a rep reports that index as UP`() {
        val before = listOf(9, 8, 8, 8, 8, 8, 8, 8)
        val after = listOf(9, 9, 8, 8, 8, 8, 8, 8)
        assertEquals(mapOf(1 to RepsColumnLayout.Change.UP), RepsColumnLayout.changedSets(before, after))
    }

    @Test
    fun `several sets losing a rep after a miss report those indices as DOWN`() {
        val before = listOf(9, 8, 8, 8, 8, 8, 8, 8)
        val after = listOf(8, 8, 8, 8, 8, 8, 8, 7)
        assertEquals(mapOf(0 to RepsColumnLayout.Change.DOWN, 7 to RepsColumnLayout.Change.DOWN), RepsColumnLayout.changedSets(before, after))
    }

    @Test
    fun `no change reports nothing`() {
        assertTrue(RepsColumnLayout.changedSets(listOf(8, 8, 8), listOf(8, 8, 8)).isEmpty())
    }

    @Test
    fun `a set-count mismatch reports nothing`() {
        assertTrue(RepsColumnLayout.changedSets(listOf(8, 8, 8), listOf(8, 8, 8, 8)).isEmpty())
    }
}
