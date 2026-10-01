package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CheckInPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val utc = ZoneOffset.UTC
    private val thursday = Instant.parse("2026-09-24T15:00:00Z") // Thu 24 Sep 08:00 PDT
    private val mondayMidnight = Instant.parse("2026-09-21T07:00:00Z") // Mon 21 Sep 00:00 PDT

    private fun at(iso: String): Instant = Instant.parse(iso)

    private fun point(iso: String, total: Int? = 50) = CheckInPoint(at(iso), total)

    @Test
    fun `weekStart on a Monday is that Monday's midnight`() {
        assertEquals(mondayMidnight, weekStart(mondayMidnight, la))
        assertEquals(mondayMidnight, weekStart(at("2026-09-21T17:00:00Z"), la)) // Mon 10:00 PDT
    }

    @Test
    fun `weekStart late on Sunday night is the previous Monday`() {
        assertEquals(mondayMidnight, weekStart(at("2026-09-28T06:59:59Z"), la)) // Sun 27 Sep 23:59:59 PDT
        assertEquals(mondayMidnight, weekStart(thursday, la))
    }

    @Test
    fun `weekStart follows the zone when the zones disagree about the day`() {
        val instant = at("2026-09-28T03:00:00Z") // Monday in UTC, Sunday 20:00 in Los Angeles
        assertEquals(at("2026-09-28T00:00:00Z"), weekStart(instant, utc))
        assertEquals(mondayMidnight, weekStart(instant, la))
    }

    @Test
    fun `weekStart in the week DST ends is local midnight on both sides`() {
        // That week's Monday is PDT (UTC-7); the next Monday is PST (UTC-8).
        assertEquals(at("2026-10-26T07:00:00Z"), weekStart(at("2026-11-02T07:30:00Z"), la)) // Sun 1 Nov 23:30 PST
        assertEquals(at("2026-11-02T08:00:00Z"), weekStart(at("2026-11-02T08:00:00Z"), la)) // Mon 2 Nov 00:00 PST
    }

    @Test
    fun `weekCount counts from Monday midnight, inclusive`() {
        val points = listOf(
            point("2026-09-21T06:59:00Z"), // Sun 20 Sep 23:59 PDT: last week
            point("2026-09-21T07:00:00Z"), // Mon 21 Sep 00:00 PDT
            point("2026-09-23T12:00:00Z"), // Wed 23 Sep
        )
        assertEquals(2, weekCount(points, thursday, la))
        assertEquals(0, weekCount(emptyList(), thursday, la))
    }

    @Test
    fun `rangeStart is 27 days or 3 months before today's start, or null for All`() {
        assertEquals(at("2026-08-28T07:00:00Z"), rangeStart(HistoryRange.FOUR_WEEKS, thursday, la)) // the tile window's start
        assertEquals(at("2026-06-24T07:00:00Z"), rangeStart(HistoryRange.THREE_MONTHS, thursday, la))
        assertNull(rangeStart(HistoryRange.ALL, thursday, la))
    }

    @Test
    fun `three months before the last day of a month lands on the shorter month's last day`() {
        val may31 = at("2026-05-31T12:00:00Z")
        assertEquals(at("2026-02-28T00:00:00Z"), rangeStart(HistoryRange.THREE_MONTHS, may31, utc))
        assertEquals(at("2026-05-04T00:00:00Z"), rangeStart(HistoryRange.FOUR_WEEKS, may31, utc))
    }

    @Test
    fun `the tile window starts 27 days before today, so it covers 28 days`() {
        assertEquals(at("2026-08-28T07:00:00Z"), tileWindowStart(thursday, la)) // Fri 28 Aug 00:00 PDT
        assertEquals(28, TILE_DAYS)
    }

    @Test
    fun `weekDays on a Monday marks only today, ignoring a future point in the same week`() {
        val points = listOf(
            point("2026-09-21T07:00:00Z"), // Mon 21 Sep 00:00 PDT: today
            point("2026-09-23T12:00:00Z"), // Wed 23 Sep: later this week - must be forced false
        )
        assertEquals(listOf(true, false, false, false, false, false, false), weekDays(points, mondayMidnight, la))
    }

    @Test
    fun `weekDays on a Sunday can mark every day of the week`() {
        val sunday = at("2026-09-27T20:00:00Z") // Sun 27 Sep 13:00 PDT
        val points = listOf(
            point("2026-09-21T07:00:00Z"), // Mon
            point("2026-09-24T14:00:00Z"), // Thu
            point("2026-09-27T19:00:00Z"), // Sun (today) 12:00 PDT
        )
        assertEquals(listOf(true, false, false, true, false, false, true), weekDays(points, sunday, la))
    }

    @Test
    fun `weekDays ignores a check-in from last week`() {
        val points = listOf(point("2026-09-21T06:59:00Z")) // Sun 20 Sep 23:59 PDT: last week
        assertTrue(weekDays(points, thursday, la).none { it })
    }

    @Test
    fun `weekDays counts two points on the same day once`() {
        val points = listOf(
            point("2026-09-23T12:00:00Z"), // Wed 23 Sep
            point("2026-09-23T20:00:00Z"), // the same day again
        )
        assertEquals(listOf(false, false, true, false, false, false, false), weekDays(points, thursday, la))
    }

    @Test
    fun `weekDays in the week DST ends treats the 25-hour day as one calendar day`() {
        val sundayNoonPst = at("2026-11-01T20:00:00Z") // Sun 1 Nov 12:00 PST: the week's last day
        val points = listOf(
            point("2026-10-26T07:00:00Z"), // Mon 26 Oct 00:00 PDT
            point("2026-11-01T08:30:00Z"), // Sun 1 Nov 01:30 PDT: before the 2 am fallback
            point("2026-11-01T10:00:00Z"), // Sun 1 Nov 02:00 PST: after the fallback, same calendar day
        )
        assertEquals(listOf(true, false, false, false, false, false, true), weekDays(points, sundayNoonPst, la))
    }

    @Test
    fun `niceAxis pads equal values by 2 either side, never below 0`() {
        assertEquals(Axis(46, 50, 1), niceAxis(48, 48))
        assertEquals(Axis(0, 3, 1), niceAxis(1, 1))
        assertEquals(Axis(0, 2, 1), niceAxis(0, 0))
    }

    @Test
    fun `niceAxis keeps small spans at 3 to 5 ticks`() {
        assertEquals(Axis(48, 50, 1), niceAxis(48, 49))
        assertEquals(Axis(48, 52, 1), niceAxis(48, 52))
        assertEquals(Axis(48, 54, 2), niceAxis(48, 53))
        assertEquals(listOf(48, 50, 52, 54), niceAxis(48, 53).ticks)
    }

    @Test
    fun `niceAxis rounds large spans to nice steps`() {
        assertEquals(Axis(45, 65, 5), niceAxis(48, 62))
        assertEquals(Axis(0, 1000, 500), niceAxis(10, 1000))
        assertTrue(niceAxis(3, 997).ticks.size in 3..5)
    }

    @Test
    fun `nearestPoint finds the closest x within the threshold, or nothing`() {
        val xs = listOf(10f, 60f, 200f)
        assertEquals(1, nearestPoint(70f, xs, 24f))
        assertEquals(2, nearestPoint(224f, xs, 24f)) // exactly on the threshold
        assertNull(nearestPoint(120f, xs, 24f))
        assertNull(nearestPoint(10f, emptyList(), 24f))
    }

    @Test
    fun `nearestPoint breaks a tie toward the older point`() {
        assertEquals(0, nearestPoint(35f, listOf(10f, 60f), 30f))
    }

    @Test
    fun `calendarMonths lays each month out in Monday-first weeks`() {
        val sep = calendarMonths(emptyList(), at("2026-09-01T00:00:00Z"), at("2026-09-30T12:00:00Z"), utc).single()
        assertEquals(YearMonth.of(2026, 9), sep.month)
        assertEquals(5, sep.weeks.size)
        assertTrue(sep.weeks.all { it.size == 7 })
        assertNull(sep.weeks[0][0]) // 1 Sep 2026 is a Tuesday
        assertEquals(LocalDate.of(2026, 9, 1), sep.weeks[0][1]?.date)
        assertEquals(listOf(28, 29, 30), sep.weeks[4].filterNotNull().map { it.date.dayOfMonth })
        assertTrue(sep.weeks.all { week -> week[0] == null || week[0]!!.date.dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun `calendarMonths marks checked-in days and the days inside the range`() {
        val points = listOf(point("2026-09-23T12:00:00Z"), point("2026-09-25T12:00:00Z"), point("2026-10-02T08:00:00Z"))
        val months = calendarMonths(points, at("2026-09-24T10:00:00Z"), at("2026-10-02T09:00:00Z"), utc)
        fun day(month: Int, day: Int) =
            months.flatMap { it.weeks.flatten() }.filterNotNull().single { it.date == LocalDate.of(2026, month, day) }
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), months.map { it.month })
        assertEquals(CalendarDay(LocalDate.of(2026, 9, 23), checkedIn = false, inRange = false), day(9, 23)) // a point, but before the range
        assertEquals(CalendarDay(LocalDate.of(2026, 9, 24), checkedIn = false, inRange = true), day(9, 24))
        assertEquals(CalendarDay(LocalDate.of(2026, 9, 25), checkedIn = true, inRange = true), day(9, 25))
        assertEquals(CalendarDay(LocalDate.of(2026, 10, 2), checkedIn = true, inRange = true), day(10, 2))
        assertEquals(CalendarDay(LocalDate.of(2026, 10, 3), checkedIn = false, inRange = false), day(10, 3))
    }

    @Test
    fun `calendarMonths runs oldest month first in the given zone`() {
        val months = calendarMonths(emptyList(), at("2026-06-10T16:00:00Z"), thursday, la)
        assertEquals((6..9).map { YearMonth.of(2026, it) }, months.map { it.month })
        assertTrue(calendarMonths(emptyList(), thursday, at("2026-09-01T00:00:00Z"), la).isEmpty())
    }
}
