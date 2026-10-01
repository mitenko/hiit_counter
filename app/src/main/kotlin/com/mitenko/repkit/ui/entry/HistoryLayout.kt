package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.domain.Axis
import com.mitenko.repkit.domain.HistoryRange
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.rangeStart
import com.mitenko.repkit.ui.common.DateFormats
import java.time.Instant
import java.time.ZoneId

/** What one range shows (spec rev 9 §3): the axis runs from [start] to [end] (today); [points] are the range's points. */
data class RangeView(val start: Instant, val end: Instant, val points: List<CheckInPoint>)

/** Spec rev 9 §3: "No check-ins yet" or "No check-ins in this range". */
enum class HistoryEmpty { NO_CHECK_INS, NONE_IN_RANGE }

/** The entry screen's chart and calendar maths, kept pure so it's tested without a Canvas. */
object HistoryLayout {
    /** Date labels on the x axis before repeats are dropped (plan Spec note 10). */
    const val DATE_TICKS = 4

    /**
     * An axis shorter than this counts as zero-length (plan Spec note 10): All with one point checked
     * in moments ago would otherwise put the point at the far left.
     */
    const val MIN_SPAN_MS = 60_000L

    /** The range's axis and points: from the range start (for All, the first point) to [now]. */
    fun rangeView(all: List<CheckInPoint>, range: HistoryRange, now: Instant, zone: ZoneId): RangeView {
        val start = rangeStart(range, now, zone) ?: all.firstOrNull()?.at ?: now
        return RangeView(start, now, all.filter { !it.at.isBefore(start) })
    }

    /** A Workout plots only points with a total; a Timer only entry shows every point (plan Spec note 9). */
    fun shownPoints(view: RangeView, type: EntryType): List<CheckInPoint> =
        if (type == EntryType.WORKOUT) view.points.filter { it.total != null } else view.points

    /** Null when there is something to draw. */
    fun empty(all: List<CheckInPoint>, shown: List<CheckInPoint>): HistoryEmpty? = when {
        all.isEmpty() -> HistoryEmpty.NO_CHECK_INS
        shown.isEmpty() -> HistoryEmpty.NONE_IN_RANGE
        else -> null
    }

    /**
     * The x of [at] on an axis from [start] (at [left]) to [end] (at [right]). Points outside the
     * axis are clamped to its ends; a zero-length axis puts every point in the middle.
     */
    fun x(at: Instant, start: Instant, end: Instant, left: Float, right: Float): Float {
        val span = end.toEpochMilli() - start.toEpochMilli()
        if (span < MIN_SPAN_MS) return (left + right) / 2
        val fraction = ((at.toEpochMilli() - start.toEpochMilli()).toDouble() / span).coerceIn(0.0, 1.0)
        return (left + (right - left) * fraction).toFloat()
    }

    /** The y of [total] on [axis]: axis.lo at [bottom], axis.hi at [top]. niceAxis always gives hi > lo. */
    fun y(total: Int, axis: Axis, top: Float, bottom: Float): Float =
        bottom - (bottom - top) * (total - axis.lo) / (axis.hi - axis.lo)

    /**
     * The x-axis labels (spec R6 §4.2): [DATE_TICKS] evenly spaced (fraction 0..1, "12 Sep") pairs,
     * dropping a label that repeats the previous date. A zero-length axis has one centred label.
     */
    fun dateTicks(start: Instant, end: Instant, zone: ZoneId): List<Pair<Float, String>> {
        val span = end.toEpochMilli() - start.toEpochMilli()
        if (span < MIN_SPAN_MS) return listOf(0.5f to DateFormats.dayMonth(end, zone))
        return (0 until DATE_TICKS).map { i ->
            val at = Instant.ofEpochMilli(start.toEpochMilli() + span * i / (DATE_TICKS - 1))
            i.toFloat() / (DATE_TICKS - 1) to DateFormats.dayMonth(at, zone)
        }.distinctBy { it.second }
    }

    /** The tapped point's label (rev 9 §3): "12 Sep · 62". Only called for a point with a total. */
    fun pointLabel(point: CheckInPoint, zone: ZoneId): String = "${DateFormats.dayMonth(point.at, zone)} · ${point.total}"
}
