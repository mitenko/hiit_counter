package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RepProgressionTest {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val cfg = ProgressionConfig()
    private val t0: Instant = ZonedDateTime.of(2026, 9, 1, 7, 0, 0, 0, zone).toInstant()

    private fun state(total: Int, streak: Int = 5, best: Int = 10, last: Instant? = t0, hold: Int = 0) =
        CounterState(total = total, bestStreak = best, currentStreak = streak, lastCheckIn = last, holdCount = hold)

    private fun hoursLater(h: Double): Instant = t0.plusMillis((h * 3_600_000).toLong())

    private fun check(s: CounterState, now: Instant, c: ProgressionConfig = cfg) =
        RepProgression.checkIn(s, c, now, zone)

    private fun CounterState.totalAndHold() = total to holdCount

    @Test
    fun `same local day is a no-op`() {
        val s = state(60)
        val r = check(s, hoursLater(10.0))
        assertEquals(Outcome.AlreadyToday, r.outcome)
        assertEquals(s, r.state)
    }

    @Test
    fun `same local day across a DST change is a no-op`() {
        val last = ZonedDateTime.of(2026, 3, 8, 0, 30, 0, 0, zone).toInstant()
        val now = ZonedDateTime.of(2026, 3, 8, 23, 30, 0, 0, zone).toInstant()
        assertEquals(Outcome.AlreadyToday, check(state(60, last = last), now).outcome)
    }

    @Test
    fun `just after midnight is a new day`() {
        val last = ZonedDateTime.of(2026, 9, 1, 23, 30, 0, 0, zone).toInstant()
        val now = ZonedDateTime.of(2026, 9, 2, 0, 10, 0, 0, zone).toInstant()
        val r = check(state(60, last = last), now)
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(61, r.state.total)
    }

    @Test
    fun `on time adds one rep and extends the streak`() {
        val now = hoursLater(24.0)
        val r = check(state(50, streak = 5, best = 10), now)
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(CounterState(total = 51, bestStreak = 10, currentStreak = 6, lastCheckIn = now, holdCount = 0), r.state)
    }

    @Test
    fun `on time raises the best streak`() {
        val r = check(state(50, streak = 10, best = 10), hoursLater(24.0))
        assertEquals(11, r.state.currentStreak)
        assertEquals(11, r.state.bestStreak)
    }

    @Test
    fun `on time never exceeds the cap`() {
        assertEquals(72, check(state(72), hoursLater(24.0)).state.total)
    }

    @Test
    fun `first check-in keeps the starting total and starts the streak`() {
        val now = hoursLater(1.0)
        val r = check(CounterState(total = 48), now)
        assertEquals(Outcome.First, r.outcome)
        assertEquals(CounterState(total = 48, bestStreak = 1, currentStreak = 1, lastCheckIn = now, holdCount = 0), r.state)
    }

    @Test
    fun `first check-in clamps to the floor`() {
        assertEquals(48, check(CounterState(total = 40), t0).state.total)
    }

    @Test
    fun `total above the cap is clamped`() {
        assertEquals(72, check(state(80), hoursLater(24.0)).state.total)
    }

    @Test
    fun `total below the floor is clamped then incremented`() {
        assertEquals(49, check(state(40), hoursLater(24.0)).state.total)
    }

    @Test
    fun `window boundary uses half-up hour rounding`() {
        assertEquals(Outcome.OnTime, check(state(60), hoursLater(36.49)).outcome)
        assertEquals(Outcome.Missed(0), check(state(60), hoursLater(36.5)).outcome)
    }

    @Test
    fun `penalty matches the sheet formula`() {
        val expected = mapOf(37.0 to 0, 48.0 to 0, 53.0 to 0, 54.0 to 1, 60.0 to 1, 72.0 to 1, 73.0 to 2, 84.0 to 2)
        for ((hours, penalty) in expected) {
            val now = hoursLater(hours)
            val r = check(state(60, streak = 5, best = 10), now)
            assertEquals("hours=$hours", Outcome.Missed(penalty), r.outcome)
            assertEquals(
                "hours=$hours",
                CounterState(total = 60 - penalty, bestStreak = 10, currentStreak = 1, lastCheckIn = now, holdCount = 0),
                r.state,
            )
        }
    }

    @Test
    fun `penalty never drops below the floor`() {
        assertEquals(48, check(state(49), hoursLater(84.0)).state.total)
    }

    @Test
    fun `cap equal to floor pins the total`() {
        val c = ProgressionConfig(startingTotal = 60, floor = 60, cap = 60)
        assertEquals(60, check(state(60), hoursLater(24.0), c).state.total)
        assertEquals(60, check(state(60), hoursLater(84.0), c).state.total)
    }

    @Test
    fun `clock moved backwards is treated as on time`() {
        val r = check(state(60), t0.minusSeconds(30 * 3600))
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(61, r.state.total)
    }

    @Test
    fun `first check-in at the hold value starts the hold`() {
        val c = cfg.copy(startingTotal = 64)
        assertEquals(64 to 1, check(CounterState(total = 64), t0, c).state.totalAndHold())
    }

    @Test
    fun `hold performs the hold value on holdFor check-ins then advances`() {
        var s = state(63)
        var now = t0
        val performed = mutableListOf<Pair<Int, Int>>()
        repeat(5) {
            now = now.plusSeconds(24 * 3600)
            s = check(s, now).state
            performed += s.totalAndHold()
        }
        assertEquals(listOf(64 to 1, 64 to 2, 64 to 3, 64 to 4, 65 to 0), performed)
    }

    @Test
    fun `holdFor of one holds for a single check-in`() {
        val c = cfg.copy(holdFor = 1)
        val first = check(state(63), hoursLater(24.0), c).state
        val second = check(first, hoursLater(47.0), c).state
        assertEquals(listOf(64 to 1, 65 to 0), listOf(first.totalAndHold(), second.totalAndHold()))
    }

    @Test
    fun `hold after a miss restarts when the total lands on the hold value`() {
        data class Case(val total: Int, val hold: Int, val hours: Double, val expected: Pair<Int, Int>)
        val cases = listOf(
            Case(64, 3, 48.0, 64 to 1),
            Case(64, 4, 48.0, 64 to 1),
            Case(66, 0, 84.0, 64 to 1),
            Case(65, 0, 84.0, 63 to 0),
            Case(64, 2, 60.0, 63 to 0),
        )
        for (c in cases) {
            val r = check(state(c.total, hold = c.hold), hoursLater(c.hours))
            assertEquals("$c", c.expected, r.state.totalAndHold())
        }
    }

    @Test
    fun `disabled hold configurations advance normally`() {
        assertEquals(65 to 0, check(state(64), hoursLater(24.0), cfg.copy(holdFor = 0)).state.totalAndHold())
        assertEquals(72 to 0, check(state(71), hoursLater(24.0), cfg.copy(holdAt = 72)).state.totalAndHold())
        assertEquals(65 to 0, check(state(64), hoursLater(24.0), cfg.copy(holdAt = 40)).state.totalAndHold())
    }

    @Test
    fun `hold at the floor restarts after a miss down to the floor`() {
        assertEquals(48 to 1, check(state(49), hoursLater(84.0), cfg.copy(holdAt = 48)).state.totalAndHold())
    }
}
