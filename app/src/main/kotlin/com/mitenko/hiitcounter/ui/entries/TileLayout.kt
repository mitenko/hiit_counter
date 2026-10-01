package com.mitenko.hiitcounter.ui.entries

import com.mitenko.hiitcounter.domain.TILE_DAYS
import com.mitenko.hiitcounter.domain.model.CheckInPoint
import com.mitenko.hiitcounter.domain.tileWindowStart
import com.mitenko.hiitcounter.domain.weekDays
import com.mitenko.hiitcounter.ui.common.PlotPoint
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One sparkline point: [day] 0 (27 days ago) to 27 (today), and the rep [total] (spec rev 9 §2). */
data class SparkPoint(val day: Int, val total: Int)

/**
 * A tile's graph data (spec rev 9 §2, amended rev 11 §2). [count] is the check-ins in the 28-day
 * window, for the Workout content description. [week] are this calendar week's 7 days, Monday
 * first, for the Timer Only week circles. [spark] is the Workout sparkline: the window's points
 * that have a total, oldest first (plan Spec note 9).
 */
data class TileData(
    val count: Int = 0,
    val week: List<Boolean> = NO_WEEK,
    val spark: List<SparkPoint> = emptyList(),
) {
    companion object {
        val NO_WEEK: List<Boolean> = List(7) { false }
    }
}

/** The tile graph's layout maths, kept pure so it's tested without a Canvas (spec rev 9 §2). */
object TileLayout {
    fun tile(points: List<CheckInPoint>, now: Instant, zone: ZoneId): TileData {
        val start = tileWindowStart(now, zone)
        val window = points.filter { !it.at.isBefore(start) }
        val firstDay = start.atZone(zone).toLocalDate()
        return TileData(
            count = window.size,
            week = weekDays(window, now, zone),
            spark = window.mapNotNull { p ->
                val day = ChronoUnit.DAYS.between(firstDay, p.at.atZone(zone).toLocalDate()).toInt()
                p.total?.takeIf { day in 0 until TILE_DAYS }?.let { SparkPoint(day, it) }
            },
        )
    }

    /**
     * The sparkline's vertices in a [width] × [height] box, [inset] from every edge: day 0 on the
     * left, day 27 on the right, the highest total at the top. A single point or a flat line sits
     * at mid-height.
     */
    fun sparkline(spark: List<SparkPoint>, width: Float, height: Float, inset: Float): List<PlotPoint> {
        if (spark.isEmpty()) return emptyList()
        val lo = spark.minOf { it.total }
        val hi = spark.maxOf { it.total }
        val usableWidth = width - 2 * inset
        val usableHeight = height - 2 * inset
        return spark.map { p ->
            val x = inset + usableWidth * p.day / (TILE_DAYS - 1)
            val y = if (hi == lo) height / 2 else inset + usableHeight * (hi - p.total) / (hi - lo)
            PlotPoint(x, y)
        }
    }
}
