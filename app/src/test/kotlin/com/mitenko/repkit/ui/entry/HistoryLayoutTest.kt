package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.domain.Axis
import com.mitenko.repkit.domain.HistoryRange
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.rangeStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HistoryLayoutTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val now = Instant.parse("2026-09-24T15:00:00Z") // Thu 24 Sep 08:00 PDT
    private val a = CheckInPoint(Instant.parse("2026-06-10T16:00:00Z"), 50) // All only
    private val b = CheckInPoint(Instant.parse("2026-08-10T16:00:00Z"), 55) // 3 months
    private val c = CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 60) // 4 weeks
    private val d = CheckInPoint(Instant.parse("2026-09-23T16:00:00Z"), 62) // 4 weeks
    private val all = listOf(a, b, c, d)

    @Test
    fun `the chart axis starts at the first shown point, not the empty start of the range`() {
        // Spec rev 21: 4 weeks starts on 27 Aug, but the first point is c (10 Sep), so the axis starts there.
        val view = HistoryLayout.rangeView(all, HistoryRange.FOUR_WEEKS, now, la)
        assertEquals(c.at, HistoryLayout.chartStart(view, listOf(c, d)))
        // No points shown: the range start stands.
        assertEquals(view.start, HistoryLayout.chartStart(view, emptyList()))
        // All already starts at the first point.
        val allView = HistoryLayout.rangeView(all, HistoryRange.ALL, now, la)
        assertEquals(a.at, HistoryLayout.chartStart(allView, all))
    }

    @Test
    fun `four weeks and three months start at the range start and keep only its points`() {
        assertEquals(
            RangeView(Instant.parse("2026-08-28T07:00:00Z"), now, listOf(c, d)),
            HistoryLayout.rangeView(all, HistoryRange.FOUR_WEEKS, now, la),
        )
        assertEquals(
            RangeView(Instant.parse("2026-06-24T07:00:00Z"), now, listOf(b, c, d)),
            HistoryLayout.rangeView(all, HistoryRange.THREE_MONTHS, now, la),
        )
    }

    @Test
    fun `All runs from the first point to today`() {
        assertEquals(RangeView(a.at, now, all), HistoryLayout.rangeView(all, HistoryRange.ALL, now, la))
        assertEquals(RangeView(now, now, emptyList()), HistoryLayout.rangeView(emptyList(), HistoryRange.ALL, now, la))
    }

    @Test
    fun `the empty state is no check-ins yet or none in this range, and a workout shows only points with a total`() {
        assertEquals(HistoryEmpty.NO_CHECK_INS, HistoryLayout.empty(emptyList(), emptyList()))
        val old = HistoryLayout.rangeView(listOf(a), HistoryRange.FOUR_WEEKS, now, la)
        assertEquals(HistoryEmpty.NONE_IN_RANGE, HistoryLayout.empty(listOf(a), HistoryLayout.shownPoints(old, EntryType.WORKOUT)))
        val untotalled = CheckInPoint(Instant.parse("2026-09-12T16:00:00Z"), null)
        val view = HistoryLayout.rangeView(listOf(c, untotalled), HistoryRange.FOUR_WEEKS, now, la)
        assertEquals(listOf(c), HistoryLayout.shownPoints(view, EntryType.WORKOUT))
        assertEquals(listOf(c, untotalled), HistoryLayout.shownPoints(view, EntryType.CHECK_IN))
        assertNull(HistoryLayout.empty(listOf(c), listOf(c)))
    }

    @Test
    fun `x maps the axis ends to the plot edges, clamps outside points and centres a zero-length axis`() {
        val start = Instant.parse("2026-09-01T00:00:00Z")
        val end = Instant.parse("2026-09-11T00:00:00Z")
        assertEquals(40f, HistoryLayout.x(start, start, end, 40f, 240f), 0.001f)
        assertEquals(240f, HistoryLayout.x(end, start, end, 40f, 240f), 0.001f)
        assertEquals(140f, HistoryLayout.x(Instant.parse("2026-09-06T00:00:00Z"), start, end, 40f, 240f), 0.001f)
        assertEquals(40f, HistoryLayout.x(Instant.parse("2026-08-20T00:00:00Z"), start, end, 40f, 240f), 0.001f)
        assertEquals(240f, HistoryLayout.x(Instant.parse("2026-09-20T00:00:00Z"), start, end, 40f, 240f), 0.001f)
        assertEquals(140f, HistoryLayout.x(start, start, start, 40f, 240f), 0.001f)
        assertEquals(140f, HistoryLayout.x(start, start, start.plusSeconds(30), 40f, 240f), 0.001f) // under a minute counts as zero-length
    }

    @Test
    fun `y puts the axis bottom at the plot bottom and its top at the plot top`() {
        val axis = Axis(45, 65, 5)
        assertEquals(196f, HistoryLayout.y(45, axis, 28f, 196f), 0.001f)
        assertEquals(28f, HistoryLayout.y(65, axis, 28f, 196f), 0.001f)
        assertEquals(112f, HistoryLayout.y(55, axis, 28f, 196f), 0.001f)
    }

    @Test
    fun `four weeks get four date labels spread across the axis`() {
        val ticks = HistoryLayout.dateTicks(rangeStart(HistoryRange.FOUR_WEEKS, now, la)!!, now, la)
        assertEquals(listOf("28 Aug", "6 Sep", "15 Sep", "24 Sep"), ticks.map { it.second })
        assertEquals(listOf(0f, 1f / 3, 2f / 3, 1f), ticks.map { it.first })
    }

    @Test
    fun `a short axis drops repeated dates and a zero-length one has a single centred label`() {
        val midnight = Instant.parse("2026-09-24T07:00:00Z") // 24 Sep 00:00 PDT
        assertEquals(listOf(0f to "24 Sep"), HistoryLayout.dateTicks(midnight, now, la))
        assertEquals(listOf(0.5f to "24 Sep"), HistoryLayout.dateTicks(now, now, la))
        assertEquals(listOf(0.5f to "24 Sep"), HistoryLayout.dateTicks(now.minusSeconds(30), now, la))
    }

    @Test
    fun `the point label reads day month and reps`() {
        assertEquals("23 Sep · 62", HistoryLayout.pointLabel(d, la))
    }
}
