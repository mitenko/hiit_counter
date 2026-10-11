package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.R
import com.mitenko.repkit.domain.Axis
import com.mitenko.repkit.domain.HistoryRange
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.niceAxis
import com.mitenko.repkit.domain.rangeStart
import com.mitenko.repkit.testutil.DefaultLocaleRule
import com.mitenko.repkit.ui.common.UiText
import com.mitenko.repkit.ui.common.weightText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HistoryLayoutTest {
    @get:Rule val locale = DefaultLocaleRule()

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

    private val w14 = CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 3, weight = 1400, reps = 10, unit = WeightUnit.KG)
    private val w16 = CheckInPoint(Instant.parse("2026-09-23T16:00:00Z"), 4, weight = 1600, reps = 10, unit = WeightUnit.KG)

    @Test
    fun `a Counter shows the points of its mode family, and Timer only shows every point`() {
        val view = HistoryLayout.rangeView(listOf(c, w14, w16), HistoryRange.FOUR_WEEKS, now, la)
        assertEquals(listOf(c), HistoryLayout.shownPoints(view, EntryType.WORKOUT, ProgressMode.REPS))
        assertEquals(listOf(w14, w16), HistoryLayout.shownPoints(view, EntryType.WORKOUT, ProgressMode.WEIGHT))
        assertEquals(listOf(c, w14, w16), HistoryLayout.shownPoints(view, EntryType.CHECK_IN, ProgressMode.WEIGHT))
    }

    @Test
    fun `no points in the current mode family says so, but a Reps history without weights is unchanged`() {
        // Right after a switch into a weight mode: only rep points so far.
        val repsOnly = listOf(c, d)
        val view = HistoryLayout.rangeView(repsOnly, HistoryRange.FOUR_WEEKS, now, la)
        val shownWeight = HistoryLayout.shownPoints(view, EntryType.WORKOUT, ProgressMode.WEIGHT)
        assertEquals(HistoryEmpty.NONE_IN_MODE, HistoryLayout.empty(repsOnly, shownWeight, ProgressMode.WEIGHT))
        // Switched back to Reps with only weight points recorded.
        val weightsOnly = listOf(w14, w16)
        val back = HistoryLayout.rangeView(weightsOnly, HistoryRange.FOUR_WEEKS, now, la)
        val shownReps = HistoryLayout.shownPoints(back, EntryType.WORKOUT, ProgressMode.REPS)
        assertEquals(HistoryEmpty.NONE_IN_MODE, HistoryLayout.empty(weightsOnly, shownReps, ProgressMode.REPS))
        // A Reps Counter with only Timer only points (no weight anywhere) still says "none in this range", as before.
        val untotalled = listOf(CheckInPoint(Instant.parse("2026-09-12T16:00:00Z"), null))
        val old = HistoryLayout.rangeView(untotalled, HistoryRange.FOUR_WEEKS, now, la)
        val shownOld = HistoryLayout.shownPoints(old, EntryType.WORKOUT, ProgressMode.REPS)
        assertEquals(HistoryEmpty.NONE_IN_RANGE, HistoryLayout.empty(untotalled, shownOld, ProgressMode.REPS))
        // Weight points that exist but fall outside the range: none in this range.
        val longAgo = listOf(w14.copy(at = Instant.parse("2026-06-10T16:00:00Z")))
        val far = HistoryLayout.rangeView(longAgo, HistoryRange.FOUR_WEEKS, now, la)
        val shownFar = HistoryLayout.shownPoints(far, EntryType.WORKOUT, ProgressMode.WEIGHT)
        assertEquals(HistoryEmpty.NONE_IN_RANGE, HistoryLayout.empty(longAgo, shownFar, ProgressMode.WEIGHT))
        assertNull(HistoryLayout.empty(listOf(w16), listOf(w16), ProgressMode.WEIGHT))
    }

    @Test
    fun `the series is rep totals in Reps mode and weights in the workout's unit in a weight mode`() {
        val reps = HistoryLayout.series(listOf(c, d), ProgressMode.REPS, null)
        assertEquals(ChartSeries.Reps(listOf(c, d)), reps)
        assertEquals(listOf(60, 62), reps.values)
        assertEquals(niceAxis(60, 62), reps.axis())
        assertEquals("62", reps.tickLabel(62))
        assertFalse(reps.stepped)

        val lb = w16.copy(weight = 3500, unit = WeightUnit.LB) // 35 lb = 16 kg
        val weights = HistoryLayout.series(listOf(w14, lb), ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG) as ChartSeries.Weights
        assertEquals(listOf(1400, 1600), weights.values)
        assertTrue(weights.stepped)
        assertEquals(Axis(1400, 1600, 50), weights.axis())
        assertEquals("15.5", weights.tickLabel(1550))
        assertEquals(UiText.Res(R.string.load_value, listOf(weightText(1600, WeightUnit.KG), 10)), weights.load(1))
        assertEquals(weightText(1400, WeightUnit.KG), weights.valueText(1400))
    }

    @Test
    fun `an empty series is rejected rather than crashing on an empty axis`() {
        assertThrows(IllegalArgumentException::class.java) { ChartSeries.Reps(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ChartSeries.Weights(emptyList(), WeightUnit.KG) }
    }
}
