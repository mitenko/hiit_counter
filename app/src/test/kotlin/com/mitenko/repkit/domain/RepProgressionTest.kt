package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.HoldKind
import com.mitenko.repkit.domain.model.ProgressionConfig
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
        val c = cfg.copy(holds = listOf(Hold(64, 1)))
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
        assertEquals(65 to 0, check(state(64), hoursLater(24.0), cfg.copy(holds = listOf(Hold(64, 0)))).state.totalAndHold())
        assertEquals(72 to 0, check(state(71), hoursLater(24.0), cfg.copy(holds = listOf(Hold(72, 4)))).state.totalAndHold())
        assertEquals(65 to 0, check(state(64), hoursLater(24.0), cfg.copy(holds = listOf(Hold(40, 4)))).state.totalAndHold())
    }

    @Test
    fun `hold at the floor restarts after a miss down to the floor`() {
        assertEquals(48 to 1, check(state(49), hoursLater(84.0), cfg.copy(holds = listOf(Hold(48, 4)))).state.totalAndHold())
    }

    private val twoHolds = cfg.copy(holds = listOf(Hold(56, 3), Hold(64, 4)))

    /** Daily on-time check-ins from [s], returning (total, holdCount) after each. */
    private fun climb(s: CounterState, days: Int, c: ProgressionConfig): List<Pair<Int, Int>> {
        var state = s
        var now = t0
        return List(days) {
            now = now.plusSeconds(24 * 3600)
            state = check(state, now, c).state
            state.totalAndHold()
        }
    }

    @Test
    fun `two holds are climbed in sequence`() {
        val expected = listOf(
            55 to 0, 56 to 1, 56 to 2, 56 to 3, 57 to 0, 58 to 0, 59 to 0, 60 to 0, 61 to 0, 62 to 0, 63 to 0,
            64 to 1, 64 to 2, 64 to 3, 64 to 4, 65 to 0, 66 to 0,
        )
        assertEquals(expected, climb(state(54), expected.size, twoHolds))
    }

    @Test
    fun `adjacent holds start the next hold on the day it is reached`() {
        val c = cfg.copy(holds = listOf(Hold(56, 1), Hold(57, 2)))
        assertEquals(listOf(56 to 1, 57 to 1, 57 to 2, 58 to 0), climb(state(55), 4, c))
    }

    @Test
    fun `a miss landing on the lower hold restarts that hold`() {
        // 84 h away: round((84 - 24) / 19.5) - 1 = 2 reps lost.
        assertEquals(56 to 1, check(state(58), hoursLater(84.0), twoHolds).state.totalAndHold())
        assertEquals(56 to 1, check(state(56, hold = 2), hoursLater(48.0), twoHolds).state.totalAndHold())
        assertEquals(60 to 0, check(state(62), hoursLater(84.0), twoHolds).state.totalAndHold())
    }

    @Test
    fun `a first check-in on any hold starts it`() {
        val c = twoHolds.copy(startingTotal = 56)
        assertEquals(56 to 1, check(CounterState(total = 56), t0, c).state.totalAndHold())
    }

    @Test
    fun `a hold outside floor to cap is ignored`() {
        val c = cfg.copy(holds = listOf(Hold(40, 3), Hold(72, 2), Hold(64, 4)))
        assertEquals(72 to 0, check(state(71), hoursLater(24.0), c).state.totalAndHold())
        assertEquals(72 to 0, check(state(72), hoursLater(24.0), c).state.totalAndHold())
        assertEquals(49 to 0, check(state(48), hoursLater(24.0), c).state.totalAndHold())
        assertEquals(64 to 1, check(state(63), hoursLater(24.0), c).state.totalAndHold())
    }

    @Test
    fun `a hold with a count of 0 is ignored`() {
        val c = cfg.copy(holds = listOf(Hold(56, 0), Hold(64, 4)))
        assertEquals(listOf(56 to 0, 57 to 0), climb(state(55), 2, c))
        assertEquals(64 to 1, check(state(63), hoursLater(24.0), c).state.totalAndHold())
    }

    @Test
    fun `the switch off ignores every hold`() {
        val c = twoHolds.copy(hold = false)
        assertEquals(listOf(56 to 0, 57 to 0), climb(state(55), 2, c))
        assertEquals(listOf(64 to 0, 65 to 0), climb(state(63), 2, c))
        assertEquals(56 to 0, check(state(58), hoursLater(84.0), c).state.totalAndHold())
    }

    @Test
    fun `an empty list holds nowhere`() {
        val c = cfg.copy(holds = emptyList())
        assertEquals(listOf(64 to 0, 65 to 0), climb(state(63), 2, c))
        assertEquals(64 to 0, check(CounterState(total = 64), t0, c.copy(startingTotal = 64)).state.totalAndHold())
    }

    @Test
    fun `duplicate holds use the first match`() {
        val c = cfg.copy(holds = listOf(Hold(64, 1), Hold(64, 4)))
        assertEquals(65 to 0, check(state(64, hold = 1), hoursLater(24.0), c).state.totalAndHold())
    }

    private fun streaksOnly(s: CounterState, now: Instant) = RepProgression.checkIn(s, cfg, now, zone, countsReps = false)

    @Test
    fun `without reps a first check-in starts the streak and keeps the total and hold count`() {
        val now = hoursLater(1.0)
        // Counting reps would clamp 40 up to the floor (48) and reset the hold count.
        val r = streaksOnly(CounterState(total = 40, holdCount = 2), now)
        assertEquals(Outcome.First, r.outcome)
        assertEquals(CounterState(total = 40, bestStreak = 1, currentStreak = 1, lastCheckIn = now, holdCount = 2), r.state)
    }

    @Test
    fun `without reps an on-time check-in extends the streaks and keeps the total`() {
        val now = hoursLater(24.0)
        val r = streaksOnly(state(50, streak = 10, best = 10, hold = 2), now)
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(CounterState(total = 50, bestStreak = 11, currentStreak = 11, lastCheckIn = now, holdCount = 2), r.state)
    }

    @Test
    fun `without reps a missed window restarts the streak with no penalty`() {
        val now = hoursLater(100.0) // counting reps: round((100 − 24) / 19.5) − 1 = 3 reps lost
        val r = streaksOnly(state(60, streak = 5, best = 10, hold = 1), now)
        assertEquals(Outcome.Missed(0), r.outcome)
        assertEquals(CounterState(total = 60, bestStreak = 10, currentStreak = 1, lastCheckIn = now, holdCount = 1), r.state)
    }

    @Test
    fun `without reps a second check-in the same day changes nothing`() {
        val s = state(60)
        val r = streaksOnly(s, hoursLater(10.0))
        assertEquals(Outcome.AlreadyToday, r.outcome)
        assertEquals(s, r.state)
    }

    private val from64 = cfg.copy(holds = listOf(Hold(64, 2, HoldKind.FROM)))

    @Test
    fun `a From hold holds every value from its start for its count`() {
        val expected = listOf(63 to 0, 64 to 1, 64 to 2, 65 to 1, 65 to 2, 66 to 1, 66 to 2, 67 to 1)
        assertEquals(expected, climb(state(62), expected.size, from64))
    }

    @Test
    fun `a From range ends below the cap, which never holds`() {
        assertEquals(listOf(71 to 1, 71 to 2, 72 to 0, 72 to 0), climb(state(70, hold = 2), 4, from64))
    }

    @Test
    fun `a miss inside a From range restarts the value it lands on`() {
        // 60 h away: round((60 - 24) / 19.5) - 1 = 1 rep lost.
        assertEquals(65 to 1, check(state(66, hold = 1), hoursLater(60.0), from64).state.totalAndHold())
        // 84 h away: 2 reps lost, landing on the range's first value.
        assertEquals(64 to 1, check(state(66, hold = 2), hoursLater(84.0), from64).state.totalAndHold())
        // Below the range: no hold.
        assertEquals(63 to 0, check(state(65, hold = 2), hoursLater(84.0), from64).state.totalAndHold())
    }

    @Test
    fun `an At hold on a From's first value runs its own count, then the From range takes over`() {
        val c = cfg.copy(holds = listOf(Hold(64, 4), Hold(64, 2, HoldKind.FROM)))
        val expected = listOf(64 to 1, 64 to 2, 64 to 3, 64 to 4, 65 to 1, 65 to 2, 66 to 1)
        assertEquals(expected, climb(state(63), expected.size, c))
    }

    @Test
    fun `a first check-in inside a From range starts the hold`() {
        val c = from64.copy(startingTotal = 66)
        assertEquals(66 to 1, check(CounterState(total = 66), t0, c).state.totalAndHold())
    }
}
