package com.mitenko.repkit.ui.entries

import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.ui.common.PlotPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TileLayoutTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val now = Instant.parse("2026-09-24T15:00:00Z") // Thu 24 Sep 08:00 PDT; the window starts Fri 28 Aug

    private fun assertPoint(expected: PlotPoint, actual: PlotPoint) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }

    @Test
    fun `tile counts the window's points, marks this week's days and indexes the sparkline`() {
        val points = listOf(
            CheckInPoint(Instant.parse("2026-08-27T20:00:00Z"), 47), // before the window
            CheckInPoint(Instant.parse("2026-08-28T07:00:00Z"), 48), // day 0, last week
            CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 55), // day 13, last week
            CheckInPoint(Instant.parse("2026-09-24T14:00:00Z"), null), // today (Thu), this week, without a total
        )
        val tile = TileLayout.tile(points, now, la)
        assertEquals(3, tile.count)
        assertEquals(listOf(3), tile.week.indices.filter { tile.week[it] }) // Thu is index 3, Monday first
        assertEquals(listOf(SparkPoint(0, 48), SparkPoint(13, 55)), tile.spark)
        assertEquals(TileData(), TileLayout.tile(emptyList(), now, la))
    }

    @Test
    fun `the sparkline spans the box with the highest total at the top`() {
        val line = TileLayout.sparkline(listOf(SparkPoint(0, 48), SparkPoint(9, 53), SparkPoint(27, 58)), 96f, 32f, inset = 2f)
        assertPoint(PlotPoint(2f, 30f), line[0])
        assertPoint(PlotPoint(32.666668f, 16f), line[1])
        assertPoint(PlotPoint(94f, 2f), line[2])
    }

    @Test
    fun `the sparkline starts at its first point and ends at today`() {
        // Spec rev 21: points on days 24..26 of 28 stretch from the left edge, with today (27) on the right.
        val line = TileLayout.sparkline(listOf(SparkPoint(24, 61), SparkPoint(25, 62), SparkPoint(26, 63)), 96f, 32f, inset = 2f)
        assertPoint(PlotPoint(2f, 30f), line[0])
        assertPoint(PlotPoint(32.666668f, 16f), line[1])
        assertPoint(PlotPoint(63.333336f, 2f), line[2])
    }

    @Test
    fun `one point or a flat line sits mid-height, and no points draw nothing`() {
        // A lone point today has no span to stretch over, so it sits in the middle.
        assertPoint(PlotPoint(48f, 16f), TileLayout.sparkline(listOf(SparkPoint(27, 50)), 96f, 32f, inset = 2f).single())
        assertPoint(PlotPoint(2f, 16f), TileLayout.sparkline(listOf(SparkPoint(5, 50)), 96f, 32f, inset = 2f).single())
        assertTrue(TileLayout.sparkline(listOf(SparkPoint(0, 50), SparkPoint(27, 50)), 96f, 32f, inset = 2f).all { it.y == 16f })
        assertTrue(TileLayout.sparkline(emptyList(), 96f, 32f, inset = 2f).isEmpty())
    }
}
