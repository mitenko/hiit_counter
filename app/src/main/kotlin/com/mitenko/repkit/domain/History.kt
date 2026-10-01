package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CheckInPoint
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** The entry screen's range switch (spec R6 §3.4, rev 9 §3). */
enum class HistoryRange { FOUR_WEEKS, THREE_MONTHS, ALL }

/** Days in the entry list's tile window, today included (spec R6 §3.4, §4.1). */
const val TILE_DAYS = 28

private fun today(now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate()

/** Local midnight, or the first valid instant of the day where midnight doesn't exist. */
private fun LocalDate.startIn(zone: ZoneId): Instant = atStartOfDay(zone).toInstant()

/** Local Monday 00:00 of the week containing [now] (spec R6 §3.4). */
fun weekStart(now: Instant, zone: ZoneId): Instant =
    today(now, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).startIn(zone)

/** "X× this week" (spec rev 9 §2): the points at or after [weekStart]. */
fun weekCount(points: List<CheckInPoint>, now: Instant, zone: ZoneId): Int {
    val start = weekStart(now, zone)
    return points.count { !it.at.isBefore(start) }
}

/**
 * The first instant a [range] shows (spec R6 §3.4): the tile window's start (27 local days before
 * today's start, spec R6 §3.4) or 3 calendar months before today's start, or null for ALL.
 * A month-end date clamps to the shorter month's last day.
 */
fun rangeStart(range: HistoryRange, now: Instant, zone: ZoneId): Instant? = when (range) {
    HistoryRange.FOUR_WEEKS -> today(now, zone).minusDays(TILE_DAYS - 1L).startIn(zone)
    HistoryRange.THREE_MONTHS -> today(now, zone).minusMonths(3).startIn(zone)
    HistoryRange.ALL -> null
}

/** The start of the day 27 days before today: the 28-day tile window, today included (spec R6 §3.4). */
fun tileWindowStart(now: Instant, zone: ZoneId): Instant =
    today(now, zone).minusDays(TILE_DAYS - 1L).startIn(zone)

/**
 * 7 values, Monday first: true when that local day in the calendar week containing [now] has at
 * least one point (spec rev 11 §2). A day later in the week than today is simply false, even if a
 * (shouldn't-happen) future point is passed for it.
 */
fun weekDays(points: List<CheckInPoint>, now: Instant, zone: ZoneId): List<Boolean> {
    val start = weekStart(now, zone).atZone(zone).toLocalDate()
    val todayDate = today(now, zone)
    val days = points.mapTo(HashSet()) { it.at.atZone(zone).toLocalDate() }
    return List(7) { i ->
        val date = start.plusDays(i.toLong())
        !date.isAfter(todayDate) && date in days
    }
}

/** A y axis from [lo] to [hi] in steps of [step] (spec R6 §3.4). */
data class Axis(val lo: Int, val hi: Int, val step: Int) {
    val ticks: List<Int> get() = (lo..hi).step(step).toList()
}

private val NICE_MANTISSAS = intArrayOf(1, 2, 5)
private const val MIN_TICKS = 3
private const val MAX_TICKS = 5

/**
 * Rounded bounds with 3–5 ticks (spec R6 §3.4): the smallest step of 1, 2, 5, 10, 20, 50, … whose
 * rounded-out bounds need at most 5 ticks, extended upwards to at least 3. Equal values pad to
 * [min] − 2 .. [max] + 2, never below 0. Rep totals are never negative.
 */
fun niceAxis(min: Int, max: Int): Axis {
    require(min <= max) { "min $min > max $max" }
    val lo = if (min == max) maxOf(0, min - 2) else min
    val hi = if (min == max) max + 2 else max
    var magnitude = 1
    while (true) {
        for (mantissa in NICE_MANTISSAS) {
            val step = mantissa * magnitude
            val a = Math.floorDiv(lo, step) * step
            var b = -Math.floorDiv(-hi, step) * step
            if ((b - a) / step + 1 > MAX_TICKS) continue
            while ((b - a) / step + 1 < MIN_TICKS) b += step
            return Axis(a, b, step)
        }
        magnitude *= 10
    }
}

/**
 * The index of the x in [xs] nearest [tapX], if it's within [thresholdPx]; otherwise null
 * (spec R6 §3.4). On a tie the older point, the lower index, wins (plan Spec note 11).
 */
fun nearestPoint(tapX: Float, xs: List<Float>, thresholdPx: Float): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    xs.forEachIndexed { index, x ->
        val distance = abs(x - tapX)
        if (distance <= thresholdPx && distance < bestDistance) {
            best = index
            bestDistance = distance
        }
    }
    return best
}

/**
 * One calendar day (spec R6 §4.2, rev 9 §3): [inRange] when it lies between the range's first and last local
 * days, [checkedIn] when it's in range and has at least one point.
 */
data class CalendarDay(val date: LocalDate, val checkedIn: Boolean, val inRange: Boolean)

/**
 * One calendar row, Monday to Sunday (spec rev 15): weeks run on unbroken across month ends.
 * [month] is the label shown above this row: the first row's month, then the month of any 1st
 * that falls inside the range in this row; null for every other row.
 */
data class CalendarWeek(val days: List<CalendarDay>, val month: YearMonth?)

/**
 * Continuous Monday-first weeks covering [start]..[end], oldest first, in [zone] (spec R6 §3.4,
 * amended rev 15). Days outside the range are kept (inRange = false) so every row has 7 cells.
 * Empty when [end] falls on a day before [start].
 */
fun calendarWeeks(points: List<CheckInPoint>, start: Instant, end: Instant, zone: ZoneId): List<CalendarWeek> {
    val first = start.atZone(zone).toLocalDate()
    val last = end.atZone(zone).toLocalDate()
    if (last.isBefore(first)) return emptyList()
    val checked = points.mapTo(HashSet()) { it.at.atZone(zone).toLocalDate() }
    val monday = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return generateSequence(monday) { it.plusDays(1) }
        .takeWhile { !it.isAfter(sunday) }
        .map { date ->
            val inRange = !date.isBefore(first) && !date.isAfter(last)
            CalendarDay(date, checkedIn = inRange && date in checked, inRange = inRange)
        }
        .chunked(7)
        .mapIndexed { index, days ->
            val label = if (index == 0) {
                YearMonth.from(first)
            } else {
                days.firstOrNull { it.inRange && it.date.dayOfMonth == 1 }?.let { YearMonth.from(it.date) }
            }
            CalendarWeek(days, label)
        }
        .toList()
}
