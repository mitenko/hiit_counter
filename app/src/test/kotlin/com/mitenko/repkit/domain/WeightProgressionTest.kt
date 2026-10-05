package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Spec rev 26 §6 and §9.1, on the curls ladder: 8/10/12/14/16 kg × 8–12 reps, 3 sets. */
class WeightProgressionTest {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val t0: Instant = ZonedDateTime.of(2026, 9, 1, 7, 0, 0, 0, zone).toInstant()
    private val curlsWeights = listOf(800, 1000, 1200, 1400, 1600)

    private fun curls(holds: List<WeightHold> = emptyList(), mode: ProgressMode = ProgressMode.REPS_THEN_WEIGHT) =
        ProgressionConfig(
            mode = mode,
            weight = WeightConfig(
                unit = WeightUnit.KG, kind = WeightsKind.LIST, list = curlsWeights,
                repsPerSet = 10, repMin = 8, repMax = 12, holds = holds,
            ),
        )

    private fun ladder(c: ProgressionConfig) = c.scale() as ProgressionScale.Ladder

    private fun level(c: ProgressionConfig, weight: Int, reps: Int) = ladder(c).levelOf(weight, reps)

    private fun load(c: ProgressionConfig, level: Int) = ladder(c).prescription(level).let { it.weight to it.reps }

    private fun hoursLater(h: Long): Instant = t0.plusSeconds(h * 3600)

    /** At [weight] × [reps], last checked in at t0, streak 5. */
    private fun at(c: ProgressionConfig, weight: Int, reps: Int, holdCount: Int = 0) =
        CounterState(total = level(c, weight, reps), bestStreak = 5, currentStreak = 5, lastCheckIn = t0, holdCount = holdCount)

    @Test
    fun `the climb adds a rep per set, resets to 8 at each new weight and stops at the top`() {
        val c = curls()
        var state = CounterState(total = c.startLevel())
        val seen = (0..25).map { day ->
            state = RepProgression.checkInByMode(state, c, t0.plusSeconds(day * 24L * 3600), zone).state
            load(c, state.total)
        }
        val climb = curlsWeights.flatMap { w -> (8..12).map { r -> w to r } }
        assertEquals(climb + (1600 to 12), seen)
        assertEquals(1000 to 8, seen[5])
        assertEquals(1200 to 8, seen[10])
    }

    private data class Miss(
        val name: String,
        val from: Pair<Int, Int>,
        val hours: Long,
        val penalty: Int,
        val to: Pair<Int, Int>,
        val holdCount: Int = 0,
        val holds: List<WeightHold> = emptyList(),
        val mode: ProgressMode = ProgressMode.REPS_THEN_WEIGHT,
    )

    @Test
    fun `misses clamp, take the penalty, stop at the miss floor, then check the hold (spec 9_1)`() {
        val cases = listOf(
            Miss("inside the same weight (−1)", 1200 to 11, 60, 1, 1200 to 10),
            Miss("across one weight boundary (−3)", 1200 to 9, 96, 3, 1000 to 11),
            Miss("a long miss stops at the next lighter weight", 1600 to 8, 168, 6, 1400 to 8),
            Miss("landing on an active hold restarts it", 1600 to 8, 168, 6, 1400 to 8, holdCount = 1, holds = listOf(WeightHold(1400, 8, 4))),
            Miss("position 0 is a no-op", 800 to 8, 96, 3, 800 to 8),
            Miss("from the top", 1600 to 12, 96, 3, 1600 to 9),
            Miss("Weight: at most one lighter weight", 1600 to 10, 168, 6, 1400 to 10, mode = ProgressMode.WEIGHT),
            Miss("Weight: the lightest is a no-op", 800 to 10, 96, 3, 800 to 10, mode = ProgressMode.WEIGHT),
        )
        for (m in cases) {
            val c = curls(m.holds, m.mode)
            val r = RepProgression.checkInByMode(at(c, m.from.first, m.from.second), c, hoursLater(m.hours), zone)
            assertEquals(m.name, Outcome.Missed(m.penalty), r.outcome)
            assertEquals(m.name, m.to, load(c, r.state.total))
            assertEquals(m.name, m.holdCount, r.state.holdCount)
            assertEquals(m.name, 1, r.state.currentStreak)
        }
    }

    @Test
    fun `without the miss floor the same long miss would drop to 12 kg x 12`() {
        val c = curls()
        val r = RepProgression.checkIn(at(c, 1600, 8), c.engineConfig(), hoursLater(168), zone)
        assertEquals(1200 to 12, load(c, r.state.total))
    }

    @Test
    fun `a hold on 12 kg x 8 holds for its count, counting the day it's reached`() {
        val c = curls(holds = listOf(WeightHold(1200, 8, 2)))
        var s = at(c, 1000, 12)
        val seen = (1..3).map { day ->
            s = RepProgression.checkInByMode(s, c, hoursLater(24L * day), zone).state
            load(c, s.total) to s.holdCount
        }
        assertEquals(listOf((1200 to 8) to 1, (1200 to 8) to 2, (1200 to 9) to 0), seen)
    }

    @Test
    fun `Reps mode is exactly checkIn without a miss floor`() {
        val c = ProgressionConfig(holds = listOf(Hold(56, 2), Hold(64, 4)))
        for (total in listOf(40, 48, 56, 60, 64, 72, 80)) {
            for (hours in listOf(10L, 24L, 37L, 60L, 96L, 168L, 400L)) {
                for (holdCount in 0..4) {
                    val s = CounterState(total, 9, 5, t0, holdCount)
                    assertEquals(
                        "$total/$hours/$holdCount",
                        RepProgression.checkIn(s, c, hoursLater(hours), zone),
                        RepProgression.checkInByMode(s, c, hoursLater(hours), zone),
                    )
                }
            }
        }
        val miss = RepProgression.checkInByMode(CounterState(60, 9, 5, t0), ProgressionConfig(), hoursLater(168), zone)
        assertEquals(Outcome.Missed(6) to 54, miss.outcome to miss.state.total)
        assertEquals(48, RepProgression.checkInByMode(CounterState(50, 9, 5, t0), ProgressionConfig(), hoursLater(168), zone).state.total)
    }

    // Plan Spec note 13 (user ruling A): the first check-in after Start fresh is performed at the start.

    /** At the start level [start] after Start fresh, last checked in at t0, streak 5. */
    private fun fresh(start: Int) =
        CounterState(total = start, bestStreak = 5, currentStreak = 5, lastCheckIn = t0, freshStart = true)

    @Test
    fun `on time after a fresh start stays at the start, the streak goes on and the flag clears`() {
        val c = curls()
        val r = RepProgression.checkInByMode(fresh(level(c, 1200, 8)), c, hoursLater(24), zone)
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(1200 to 8, load(c, r.state.total))
        assertEquals(6, r.state.currentStreak)
        assertEquals(hoursLater(24), r.state.lastCheckIn)
        assertFalse(r.state.freshStart)
    }

    @Test
    fun `a miss after a fresh start stays at the start with no penalty, streak 1 and the flag cleared`() {
        val c = curls()
        val r = RepProgression.checkInByMode(fresh(level(c, 1200, 8)), c, hoursLater(168), zone)
        assertEquals(Outcome.Missed(0), r.outcome)
        assertEquals(1200 to 8, load(c, r.state.total))
        assertEquals(1, r.state.currentStreak)
        assertFalse(r.state.freshStart)
    }

    @Test
    fun `the next on-time check-in after a fresh start goes up one`() {
        val c = curls()
        var s = fresh(level(c, 1200, 8))
        val seen = (1..3).map { day ->
            s = RepProgression.checkInByMode(s, c, hoursLater(24L * day), zone).state
            load(c, s.total)
        }
        assertEquals(listOf(1200 to 8, 1200 to 9, 1200 to 10), seen)
        assertEquals(8, s.currentStreak)
    }

    @Test
    fun `a fresh start on a hold counts that day as day 1`() {
        val c = curls(holds = listOf(WeightHold(1200, 8, 2)))
        var s = fresh(level(c, 1200, 8))
        val seen = (1..3).map { day ->
            s = RepProgression.checkInByMode(s, c, hoursLater(24L * day), zone).state
            load(c, s.total) to s.holdCount
        }
        assertEquals(listOf((1200 to 8) to 1, (1200 to 8) to 2, (1200 to 9) to 0), seen)
    }

    @Test
    fun `a first check-in after a fresh start is unchanged, and already today keeps the flag`() {
        val c = curls()
        val first = RepProgression.checkInByMode(CounterState(total = c.startLevel(), freshStart = true), c, t0, zone)
        assertEquals(Outcome.First, first.outcome)
        assertEquals(CounterState(c.startLevel(), 1, 1, t0, 0), first.state)
        val today = fresh(level(c, 1200, 8))
        assertEquals(CheckInResult(today, Outcome.AlreadyToday), RepProgression.checkInByMode(today, c, hoursLater(1), zone))
    }

    @Test
    fun `a fresh start in Reps mode stays put and clears the flag, and Timer only keeps the flag`() {
        val reps = RepProgression.checkInByMode(fresh(60), ProgressionConfig(), hoursLater(24), zone)
        assertEquals(60 to false, reps.state.total to reps.state.freshStart)
        val timerOnly = RepProgression.checkInByMode(fresh(60), ProgressionConfig(), hoursLater(24), zone, countsReps = false)
        assertEquals(CounterState(60, 6, 6, hoursLater(24), 0, freshStart = true), timerOnly.state)
    }

    @Test
    fun `a fresh start survives a Timer only check-in, so the next Counter check-in stays at the start`() {
        val c = curls()
        val start = level(c, 1200, 8)
        // Start fresh, then the entry is Timer only for a day.
        val timerOnly = RepProgression.checkInByMode(fresh(start), c, hoursLater(24), zone, countsReps = false).state
        assertEquals(start to true, timerOnly.total to timerOnly.freshStart)
        // Back to Counter: the first on-time check-in is performed at the start.
        val counter = RepProgression.checkInByMode(timerOnly, c, hoursLater(48), zone)
        assertEquals(Outcome.OnTime, counter.outcome)
        assertEquals(1200 to 8, load(c, counter.state.total))
        assertEquals(7, counter.state.currentStreak)
        assertFalse(counter.state.freshStart)
        // And the one after that goes up one.
        assertEquals(1200 to 9, load(c, RepProgression.checkInByMode(counter.state, c, hoursLater(72), zone).state.total))
    }

    @Test
    fun `with the flag false nothing changes`() {
        val c = curls()
        val onTime = RepProgression.checkInByMode(at(c, 1200, 8), c, hoursLater(24), zone)
        assertEquals(1200 to 9, load(c, onTime.state.total))
        val miss = RepProgression.checkInByMode(at(c, 1200, 9), c, hoursLater(96), zone)
        assertEquals(Outcome.Missed(3) to (1000 to 11), miss.outcome to load(c, miss.state.total))
        assertFalse(onTime.state.freshStart || miss.state.freshStart)
    }
}
