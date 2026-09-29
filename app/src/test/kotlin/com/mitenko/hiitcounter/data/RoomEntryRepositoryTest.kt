package com.mitenko.hiitcounter.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.expectThrows
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomEntryRepositoryTest {
    private lateinit var db: HiitDatabase
    private val clock = FakeClock()
    private val open = object : MigrationGate {
        override suspend fun awaitReady() = Unit
    }

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun repo(gate: MigrationGate = open) = RoomEntryRepository(db, gate, clock)

    private suspend fun order() = db.entryDao().getAll().map { it.name to it.position }

    @Test
    fun `create appends entries with defaults in order`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("  Lunges ")
        assertNotEquals(a, b)
        assertEquals(listOf("Burpees" to 0, "Lunges" to 1), order())
        assertEquals(listOf(a, b), r.entries.first().map { it.id })
        val entry = r.entry(a).first()!!
        assertEquals(TimingConfig(), entry.timing)
        assertEquals(ProgressionConfig(), entry.progression)
        assertEquals(CueConfig(), entry.cues)
        assertEquals(CounterState(total = 48), entry.counter)
    }

    @Test
    fun `entry emits null for an unknown id`() = runTest {
        assertNull(repo().entry(99).first())
    }

    @Test
    fun `reads and writes wait for the migration gate`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val r = repo(object : MigrationGate {
            override suspend fun awaitReady() = gate.await()
        })
        val list = async { r.entries.first() }
        val created = async { r.create("Burpees") }
        runCurrent()
        assertFalse(list.isCompleted)
        assertFalse(created.isCompleted)
        assertEquals(0, db.entryDao().count())
        gate.complete(Unit)
        assertTrue(created.await() > 0)
        list.await()
    }

    @Test
    fun `create rejects invalid names and writes nothing`() = runTest {
        val r = repo()
        assertEquals("Enter a name", expectThrows<IllegalArgumentException> { r.create("   ") }.message)
        assertEquals("Use at most 40 characters", expectThrows<IllegalArgumentException> { r.create("x".repeat(41)) }.message)
        assertEquals(0, db.entryDao().count())
    }

    @Test
    fun `rename trims and validates`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.rename(a, "  Kettlebell Lunges  ")
        assertEquals("Kettlebell Lunges", r.entry(a).first()!!.name)
        expectThrows<IllegalArgumentException> { r.rename(a, "") }
        assertEquals("Kettlebell Lunges", r.entry(a).first()!!.name)
    }

    @Test
    fun `settings writes persist per entry`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        r.setTiming(a, TimingConfig(sets = 6))
        r.setProgression(a, ProgressionConfig(cap = 80))
        r.setCues(a, CueConfig(sound = false))
        val ea = r.entry(a).first()!!
        assertEquals(TimingConfig(sets = 6), ea.timing)
        assertEquals(ProgressionConfig(cap = 80), ea.progression)
        assertEquals(CueConfig(sound = false), ea.cues)
        val eb = r.entry(b).first()!!
        assertEquals(TimingConfig(), eb.timing)
        assertEquals(ProgressionConfig(), eb.progression)
        assertEquals(CueConfig(), eb.cues)
    }

    @Test
    fun `setProgression resets holdCount in the same update`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        r.setProgression(a, ProgressionConfig(holdFor = 2))
        val e = r.entry(a).first()!!
        assertEquals(0, e.counter.holdCount)
        assertEquals(64, e.counter.total)
        assertEquals(2, e.progression.holdFor)
    }

    @Test
    fun `invalid settings are rejected and nothing is written`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        expectThrows<IllegalArgumentException> { r.setTiming(a, TimingConfig(sets = 0)) }
        expectThrows<IllegalArgumentException> { r.setTiming(a, TimingConfig(sets = 20, workSec = 3599)) }
        expectThrows<IllegalArgumentException> { r.setProgression(a, ProgressionConfig(floor = 80, cap = 60)) }
        val e = r.entry(a).first()!!
        assertEquals(TimingConfig(), e.timing)
        assertEquals(ProgressionConfig(), e.progression)
    }

    @Test
    fun `settings writes on missing ids throw EntryNotFound`() = runTest {
        val r = repo()
        assertEquals(99L, expectThrows<EntryNotFound> { r.rename(99, "Burpees") }.id)
        expectThrows<EntryNotFound> { r.setTiming(99, TimingConfig()) }
        expectThrows<EntryNotFound> { r.setProgression(99, ProgressionConfig()) }
        expectThrows<EntryNotFound> { r.setCues(99, CueConfig()) }
    }

    @Test
    fun `overwriteCounter writes every field and resets holdCount`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        val last = clock.instant.minusSeconds(3600)
        r.overwriteCounter(a, total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = last)
        assertEquals(CounterState(65, 24, 4, last, 0), r.entry(a).first()!!.counter)
    }

    @Test
    fun `overwriteCounter validates like Current State`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 60, bestStreak = 3, currentStreak = 4, lastCheckIn = null) }
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 60, 5, 4, clock.instant.plusSeconds(60)) }
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 0, 0, 0, null) }
        assertEquals(CounterState(total = 48), r.entry(a).first()!!.counter)
    }

    @Test
    fun `resetProgress returns the counter to its untouched state`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.overwriteCounter(a, 65, 24, 4, clock.instant.minusSeconds(60))
        r.resetProgress(a)
        assertNull(db.entryDao().get(a)!!.total)
        assertEquals(CounterState(total = 48), r.entry(a).first()!!.counter)
        r.setProgression(a, ProgressionConfig(startingTotal = 55))
        assertEquals(55, r.entry(a).first()!!.counter.total)
    }

    @Test
    fun `counter writes on missing ids throw EntryNotFound`() = runTest {
        val r = repo()
        expectThrows<EntryNotFound> { r.overwriteCounter(99, 60, 0, 0, null) }
        expectThrows<EntryNotFound> { r.resetProgress(99) }
    }

    @Test
    fun `delete compacts later positions`() = runTest {
        val r = repo()
        r.create("A")
        val b = r.create("B")
        r.create("C")
        r.create("D")
        r.delete(b)
        assertEquals(listOf("A" to 0, "C" to 1, "D" to 2), order())
        assertEquals(listOf("A", "C", "D"), r.entries.first().map { it.name })
    }

    @Test
    fun `duplicate appends a copy with the config and a fresh counter`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setTiming(a, TimingConfig(sets = 6))
        r.setProgression(a, ProgressionConfig(startingTotal = 50, floor = 40))
        r.setCues(a, CueConfig(vibration = false))
        val last = clock.instant.minusSeconds(60)
        r.overwriteCounter(a, 65, 24, 4, last)
        r.create("Lunges")
        val copy = r.duplicate(a)
        val e = r.entry(copy).first()!!
        assertEquals("Burpees copy", e.name)
        assertEquals(2, e.position)
        assertEquals(TimingConfig(sets = 6), e.timing)
        assertEquals(ProgressionConfig(startingTotal = 50, floor = 40), e.progression)
        assertEquals(CueConfig(vibration = false), e.cues)
        assertEquals(CounterState(total = 50), e.counter)
        assertNull(db.entryDao().get(copy)!!.total)
        assertEquals(CounterState(65, 24, 4, last, 0), r.entry(a).first()!!.counter)
    }

    @Test
    fun `duplicate keeps the suffix on a 40-character name`() = runTest {
        val r = repo()
        val a = r.create("x".repeat(40))
        assertEquals("x".repeat(35) + " copy", r.entry(r.duplicate(a)).first()!!.name)
    }

    @Test
    fun `delete and duplicate throw EntryNotFound for missing ids`() = runTest {
        val r = repo()
        expectThrows<EntryNotFound> { r.delete(99) }
        expectThrows<EntryNotFound> { r.duplicate(99) }
    }

    @Test
    fun `moveBy moves one step and shifts the neighbour`() = runTest {
        val r = repo()
        val a = r.create("A")
        r.create("B")
        val c = r.create("C")
        r.moveBy(c, -1)
        assertEquals(listOf("A" to 0, "C" to 1, "B" to 2), order())
        r.moveBy(a, +1)
        assertEquals(listOf("C" to 0, "A" to 1, "B" to 2), order())
    }

    @Test
    fun `first up and last down are no-ops`() = runTest {
        val r = repo()
        val a = r.create("A")
        r.create("B")
        val c = r.create("C")
        r.moveBy(a, -1)
        r.moveBy(c, +1)
        assertEquals(listOf("A" to 0, "B" to 1, "C" to 2), order())
    }

    @Test
    fun `large deltas clamp to the list bounds`() = runTest {
        val r = repo()
        val a = r.create("A")
        r.create("B")
        r.create("C")
        r.moveBy(a, +10)
        assertEquals(listOf("B" to 0, "C" to 1, "A" to 2), order())
        r.moveBy(a, Int.MIN_VALUE)
        assertEquals(listOf("A" to 0, "B" to 1, "C" to 2), order())
    }

    @Test
    fun `rapid repeated moves act on the current order`() = runTest {
        val r = repo()
        val first = r.create("E0")
        repeat(4) { r.create("E${it + 1}") }
        List(6) { async { r.moveBy(first, +1) } }.awaitAll()
        assertEquals(listOf("E1", "E2", "E3", "E4", "E0"), order().map { it.first })
        assertEquals((0..4).toList(), order().map { it.second })
    }

    @Test
    fun `a hundred entries stay contiguous and ordered through random moves`() = runTest {
        val r = repo()
        val model = MutableList(100) { r.create("E$it") }
        val random = Random(42)
        repeat(300) {
            val id = model[random.nextInt(model.size)]
            val delta = random.nextInt(-5, 6)
            r.moveBy(id, delta)
            val from = model.indexOf(id)
            model.removeAt(from)
            model.add((from + delta).coerceIn(0, model.size), id)
        }
        val rows = db.entryDao().getAll()
        assertEquals(model, rows.map { it.id })
        assertEquals((0 until 100).toList(), rows.map { it.position })
    }

    @Test
    fun `moveBy throws EntryNotFound for a missing id`() = runTest {
        expectThrows<EntryNotFound> { repo().moveBy(99, 1) }
    }

    @Test
    fun `check-in uses the row's own progression`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        r.setProgression(a, ProgressionConfig(startingTotal = 20, floor = 20, cap = 30))
        val result = r.checkIn(a, clock)
        assertEquals(Outcome.First, result.outcome)
        assertEquals(20, result.state.total)
        assertEquals(CounterState(total = 48), r.entry(b).first()!!.counter)
    }

    @Test
    fun `a null total resolves to the starting total and is written on the first check-in`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.checkIn(a, clock)
        val row = db.entryDao().get(a)!!
        assertEquals(48, row.total)
        assertEquals(clock.instant.toEpochMilli(), row.lastCheckIn)
        assertEquals(1, row.currentStreak)
    }

    @Test
    fun `a second check-in the same day writes nothing`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val first = r.checkIn(a, clock)
        clock.instant = clock.instant.plusSeconds(600)
        val second = r.checkIn(a, clock)
        assertEquals(Outcome.AlreadyToday, second.outcome)
        assertEquals(first.state, r.entry(a).first()!!.counter)
    }

    @Test
    fun `concurrent check-ins on one entry record exactly one`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val results = List(5) { async { r.checkIn(a, clock) } }.awaitAll()
        assertEquals(1, results.count { it.outcome != Outcome.AlreadyToday })
    }

    @Test
    fun `two entries check in independently`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        assertEquals(Outcome.First, r.checkIn(a, clock).outcome)
        assertEquals(Outcome.First, r.checkIn(b, clock).outcome)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(24 * 3600)
        assertEquals(Outcome.OnTime, r.checkIn(a, clock).outcome)
        assertEquals(49, r.entry(a).first()!!.counter.total)
        assertEquals(CounterState(48, 1, 1, day1, 0), r.entry(b).first()!!.counter)
    }

    @Test
    fun `a settings save before the check-in applies the new rules`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setProgression(a, ProgressionConfig(startingTotal = 30, floor = 30, cap = 40))
        assertEquals(30, r.checkIn(a, clock).state.total)
    }

    @Test
    fun `a settings save after the check-in keeps both and resets the hold`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setProgression(a, ProgressionConfig(startingTotal = 64))
        assertEquals(1, r.checkIn(a, clock).state.holdCount)
        r.setProgression(a, ProgressionConfig(startingTotal = 64, holdFor = 2))
        val e = r.entry(a).first()!!
        assertEquals(0, e.counter.holdCount)
        assertEquals(64, e.counter.total)
        assertEquals(clock.instant, e.counter.lastCheckIn)
        assertEquals(2, e.progression.holdFor)
    }

    @Test
    fun `checkIn throws EntryNotFound for a missing id`() = runTest {
        expectThrows<EntryNotFound> { repo().checkIn(99, clock) }
    }

    @Test
    fun `setProgression keeps the hold count for floor, penalty and window edits`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        r.setProgression(a, ProgressionConfig(floor = 40))
        r.setProgression(a, ProgressionConfig(floor = 40, penaltyHoursPerRep = 12.5))
        r.setProgression(a, ProgressionConfig(floor = 40, penaltyHoursPerRep = 12.5, windowHours = 30))
        assertEquals(3, r.entry(a).first()!!.counter.holdCount)
    }

    @Test
    fun `setProgression resets the hold count for hold at, hold for and switch edits`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        suspend fun holdCountAfter(p: ProgressionConfig): Int {
            db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
            r.setProgression(a, p)
            return r.entry(a).first()!!.counter.holdCount
        }
        assertEquals(0, holdCountAfter(ProgressionConfig(holdAt = 66)))
        assertEquals(0, holdCountAfter(ProgressionConfig(holdAt = 66, holdFor = 3)))
        assertEquals(0, holdCountAfter(ProgressionConfig(holdAt = 66, holdFor = 3, hold = false)))
        assertFalse(r.entry(a).first()!!.progression.hold)
    }

    @Test
    fun `overwriteCounter keeps the hold count for streak and date edits`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        val last = clock.instant.minusSeconds(3600)
        r.overwriteCounter(a, total = 64, bestStreak = 5, currentStreak = 2, lastCheckIn = last)
        assertEquals(CounterState(64, 5, 2, last, 3), r.entry(a).first()!!.counter)
    }

    @Test
    fun `overwriteCounter resets the hold count for a total edit, a stored null total counting as the starting total`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = null, bestStreak = 0, currentStreak = 0, holdCount = 2, lastCheckIn = null)
        r.overwriteCounter(a, total = 48, bestStreak = 1, currentStreak = 0, lastCheckIn = null)
        assertEquals(2, r.entry(a).first()!!.counter.holdCount)
        r.overwriteCounter(a, total = 49, bestStreak = 1, currentStreak = 0, lastCheckIn = null)
        assertEquals(0, r.entry(a).first()!!.counter.holdCount)
    }

    @Test
    fun `duplicate copies the hold switch`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setProgression(a, ProgressionConfig(hold = false))
        val copy = r.duplicate(a)
        assertFalse(r.entry(copy).first()!!.progression.hold)
        assertFalse(db.entryDao().get(copy)!!.holdEnabled)
    }
}
