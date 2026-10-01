package com.mitenko.hiitcounter.ui.entries

import com.mitenko.hiitcounter.domain.model.CheckInPoint
import com.mitenko.hiitcounter.ui.common.PlotPoint
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
    fun `tile counts the window's points, marks their days and indexes the sparkline`() {
        val points = listOf(
            CheckInPoint(Instant.parse("2026-08-27T20:00:00Z"), 47), // before the window
            CheckInPoint(Instant.parse("2026-08-28T07:00:00Z"), 48), // day 0
            CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 55), // day 13
            CheckInPoint(Instant.parse("2026-09-24T14:00:00Z"), null), // today, without a total
        )
        val tile = TileLayout.tile(points, now, la)
        assertEquals(3, tile.count)
        assertEquals(listOf(0, 13, 27), tile.days.indices.filter { tile.days[it] })
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
    fun `one point or a flat line sits mid-height, no points draw nothing, and day-dots are evenly spaced`() {
        assertPoint(PlotPoint(19.037037f, 16f), TileLayout.sparkline(listOf(SparkPoint(5, 50)), 96f, 32f, inset = 2f).single())
        assertTrue(TileLayout.sparkline(listOf(SparkPoint(0, 50), SparkPoint(27, 50)), 96f, 32f, inset = 2f).all { it.y == 16f })
        assertTrue(TileLayout.sparkline(emptyList(), 96f, 32f, inset = 2f).isEmpty())
        assertEquals(1.7142857f, TileLayout.dotX(0, 96f), 0.001f)
        assertEquals(94.28571f, TileLayout.dotX(27, 96f), 0.001f)
    }
}
