package com.mitenko.repkit.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PlotPointTest {
    @Test
    fun `a step line runs flat to each next point's x, then up or down to it`() {
        val a = PlotPoint(0f, 10f)
        val b = PlotPoint(5f, 4f)
        val c = PlotPoint(9f, 4f)
        assertEquals(listOf(a, PlotPoint(5f, 10f), b, PlotPoint(9f, 4f), c), stepVertices(listOf(a, b, c)))
        assertEquals(listOf(a), stepVertices(listOf(a)))
        assertEquals(emptyList<PlotPoint>(), stepVertices(emptyList()))
    }

    @Test
    fun `a flat run of equal y values still steps between the x values`() {
        val a = PlotPoint(0f, 10f)
        val b = PlotPoint(5f, 10f)
        val c = PlotPoint(9f, 10f)
        assertEquals(listOf(a, PlotPoint(5f, 10f), b, PlotPoint(9f, 10f), c), stepVertices(listOf(a, b, c)))
    }
}
