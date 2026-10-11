package com.mitenko.repkit.ui.common

/** A point in a Canvas's pixel space, returned by the pure tile and chart layout helpers. */
data class PlotPoint(val x: Float, val y: Float)

/**
 * A step line through [points] (spec rev 26 §4): flat from each point to the next one's x, then
 * straight up or down to it. Fewer than two points are returned as they are.
 */
fun stepVertices(points: List<PlotPoint>): List<PlotPoint> =
    if (points.size < 2) {
        points
    } else {
        buildList {
            add(points.first())
            for (i in 1 until points.size) {
                add(PlotPoint(points[i].x, points[i - 1].y))
                add(points[i])
            }
        }
    }
