package com.mitenko.repkit.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.R
import com.mitenko.repkit.data.db.CheckInEntity
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.data.db.WorkoutSessionEntity
import com.mitenko.repkit.domain.InvalidEntryName
import com.mitenko.repkit.domain.NameCheck
import com.mitenko.repkit.domain.Outcome
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.expectThrows
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
import java.time.Instant
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

    private fun repo(gate: MigrationGate = open) = RoomEntryRepository(db, gate, clock) {
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.copy_suffix)
    }

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
        assertEquals(NameCheck.Empty, expectThrows<InvalidEntryName> { r.create("   ") }.check)
        assertEquals(NameCheck.TooLong, expectThrows<InvalidEntryName> { r.create("x".repeat(41)) }.check)
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
        r.setProgression(a, ProgressionConfig(holds = listOf(Hold(64, 2))))
        val e = r.entry(a).first()!!
        assertEquals(0, e.counter.holdCount)
        assertEquals(64, e.counter.total)
        assertEquals(listOf(Hold(64, 2)), e.progression.holds)
    }

    @Test
    fun `invalid settings are rejected and nothing is written`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        expectThrows<IllegalArgumentException> { r.setTiming(a, TimingConfig(sets = 0)) }
        expectThrows<IllegalArgumentException> { r.setTiming(a, TimingConfig(sets = 20, workSec = 3599)) }
        expectThrows<IllegalArgumentException> { r.setProgression(a, ProgressionConfig(floor = 80, cap = 60)) }
        // Rev 16 §3: a hold's hard ranges apply with the switch off too, so the stored list always decodes.
        expectThrows<IllegalArgumentException> { r.setProgression(a, ProgressionConfig(holds = listOf(Hold(0, 4)), hold = false)) }
        expectThrows<IllegalArgumentException> { r.setProgression(a, ProgressionConfig(holds = listOf(Hold(64, -1)), hold = false)) }
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
        r.resetProgress(a, clearHistory = false)
        assertNull(db.entryDao().get(a)!!.total)
        assertEquals(CounterState(total = 48), r.entry(a).first()!!.counter)
        r.setProgression(a, ProgressionConfig(startingTotal = 55))
        assertEquals(55, r.entry(a).first()!!.counter.total)
    }

    @Test
    fun `counter writes on missing ids throw EntryNotFound`() = runTest {
        val r = repo()
        expectThrows<EntryNotFound> { r.overwriteCounter(99, 60, 0, 0, null) }
        expectThrows<EntryNotFound> { r.resetProgress(99, clearHistory = true) }
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
        r.setProgression(a, ProgressionConfig(startingTotal = 64, holds = listOf(Hold(64, 2))))
        val e = r.entry(a).first()!!
        assertEquals(0, e.counter.holdCount)
        assertEquals(64, e.counter.total)
        assertEquals(clock.instant, e.counter.lastCheckIn)
        assertEquals(listOf(Hold(64, 2)), e.progression.holds)
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
        assertEquals(0, holdCountAfter(ProgressionConfig(holds = listOf(Hold(66, 4)))))
        assertEquals(0, holdCountAfter(ProgressionConfig(holds = listOf(Hold(66, 3)))))
        assertEquals(0, holdCountAfter(ProgressionConfig(holds = listOf(Hold(66, 3)), hold = false)))
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
    fun `overwriteCounter above the cap raises the cap in the same write and reports it`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val p = ProgressionConfig(startingTotal = 50, floor = 48, cap = 72, holds = listOf(Hold(64, 4)), windowHours = 30)
        r.setProgression(a, p)
        assertEquals(RangeChange.RaisedMax(80), r.overwriteCounter(a, total = 80, bestStreak = 2, currentStreak = 1, lastCheckIn = null))
        val e = r.entry(a).first()!!
        assertEquals(p.copy(cap = 80), e.progression)
        assertEquals(CounterState(80, 2, 1, null, 0), e.counter)
        assertEquals(Hold(64, 4).at, db.entryDao().get(a)!!.holdAt) // the legacy columns still mirror the first hold
    }

    @Test
    fun `overwriteCounter below the floor lowers the floor`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        assertEquals(RangeChange.LoweredMin(40), r.overwriteCounter(a, total = 40, bestStreak = 0, currentStreak = 0, lastCheckIn = null))
        val e = r.entry(a).first()!!
        assertEquals(ProgressionConfig(floor = 40), e.progression)
        assertEquals(48, e.progression.startingTotal)
        assertEquals(40, e.counter.total)
    }

    @Test
    fun `overwriteCounter inside the range leaves the progression alone`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        for (total in listOf(48, 60, 72)) {
            assertNull(r.overwriteCounter(a, total, 0, 0, null))
            assertEquals(ProgressionConfig(), r.entry(a).first()!!.progression)
        }
    }

    @Test
    fun `a widening overwrite resets the hold count only as holdResetNeeded says`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        // A hold at the cap is inactive; raising the cap activates it, which resets the hold count.
        r.setProgression(a, ProgressionConfig(holds = listOf(Hold(72, 4))))
        db.entryDao().setCounter(a, total = 80, bestStreak = 1, currentStreak = 1, holdCount = 2, lastCheckIn = null)
        r.overwriteCounter(a, total = 80, bestStreak = 3, currentStreak = 1, lastCheckIn = null)
        assertEquals(0, r.entry(a).first()!!.counter.holdCount)
        assertEquals(80, r.entry(a).first()!!.progression.cap)
        // The default hold (64) stays active when the cap rises, so an unchanged total keeps the count.
        val b = r.create("Lunges")
        db.entryDao().setCounter(b, total = 90, bestStreak = 1, currentStreak = 1, holdCount = 2, lastCheckIn = null)
        assertEquals(RangeChange.RaisedMax(90), r.overwriteCounter(b, total = 90, bestStreak = 4, currentStreak = 1, lastCheckIn = null))
        assertEquals(2, r.entry(b).first()!!.counter.holdCount)
        assertEquals(ProgressionConfig(cap = 90), r.entry(b).first()!!.progression)
    }

    @Test
    fun `a Timer only entry's overwrite never widens its range`() = runTest {
        val r = repo()
        val a = r.create("Stretch", EntryType.CHECK_IN)
        assertNull(r.overwriteCounter(a, total = 80, bestStreak = 1, currentStreak = 1, lastCheckIn = null))
        assertEquals(ProgressionConfig(), r.entry(a).first()!!.progression)
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

    @Test
    fun `create stores the chosen type and create(name) makes a workout`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Stretch", EntryType.CHECK_IN)
        assertEquals(EntryType.WORKOUT, r.entry(a).first()!!.type)
        assertEquals(EntryType.CHECK_IN, r.entry(b).first()!!.type)
        assertEquals("CHECK_IN", db.entryDao().get(b)!!.type)
    }

    @Test
    fun `setType changes only the type, and switching back restores everything`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setTiming(a, TimingConfig(sets = 6))
        r.setProgression(a, ProgressionConfig(cap = 80, hold = false))
        r.setCues(a, CueConfig(sound = false, voice = true))
        r.overwriteCounter(a, 65, 24, 4, clock.instant.minusSeconds(60))
        val before = db.entryDao().get(a)!!
        r.setType(a, EntryType.CHECK_IN)
        assertEquals(before.copy(type = "CHECK_IN"), db.entryDao().get(a)!!)
        r.setType(a, EntryType.WORKOUT)
        assertEquals(before, db.entryDao().get(a)!!)
    }

    @Test
    fun `setType waits for the migration gate and throws EntryNotFound for a missing id`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val r = repo(object : MigrationGate {
            override suspend fun awaitReady() = gate.await()
        })
        val write = async { runCatching { r.setType(99, EntryType.CHECK_IN) } }
        runCurrent()
        assertFalse(write.isCompleted)
        gate.complete(Unit)
        assertTrue(write.await().exceptionOrNull() is EntryNotFound)
    }

    @Test
    fun `setCues writes the voice`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setCues(a, CueConfig(voice = true))
        assertEquals(CueConfig(voice = true), r.entry(a).first()!!.cues)
        assertTrue(db.entryDao().get(a)!!.cueVoice)
    }

    @Test
    fun `duplicate copies the type and the voice`() = runTest {
        val r = repo()
        val a = r.create("Stretch", EntryType.CHECK_IN)
        r.setCues(a, CueConfig(voice = true))
        val copy = r.entry(r.duplicate(a)).first()!!
        assertEquals(EntryType.CHECK_IN, copy.type)
        assertTrue(copy.cues.voice)
    }

    @Test
    fun `a Timer only check-in moves the day and streaks and never touches the total`() = runTest {
        val r = repo()
        val a = r.create("Stretch", EntryType.CHECK_IN)
        db.entryDao().setCounter(a, total = null, bestStreak = 0, currentStreak = 0, holdCount = 2, lastCheckIn = null)
        assertEquals(Outcome.First, r.checkIn(a, clock).outcome)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(24 * 3600)
        assertEquals(Outcome.OnTime, r.checkIn(a, clock).outcome)
        val row = db.entryDao().get(a)!!
        assertNull(row.total)
        assertEquals(2, row.currentStreak)
        assertEquals(2, row.bestStreak)
        assertEquals(2, row.holdCount)
        assertEquals(clock.instant.toEpochMilli(), row.lastCheckIn)
        assertEquals(48, r.entry(a).first()!!.counter.total)
    }

    @Test
    fun `a check-in logs one point with the new total and a same-day repeat logs none`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.checkIn(a, clock)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(600)
        assertEquals(Outcome.AlreadyToday, r.checkIn(a, clock).outcome)
        clock.instant = day1.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        assertEquals(listOf(CheckInPoint(day1, 48), CheckInPoint(clock.instant, 49)), r.history(a, null).first())
    }

    @Test
    fun `a Timer only check-in logs a point without a total`() = runTest {
        val r = repo()
        val a = r.create("Stretch", EntryType.CHECK_IN)
        r.checkIn(a, clock)
        assertEquals(listOf(CheckInPoint(clock.instant, null)), r.history(a, null).first())
    }

    @Test
    fun `resetProgress without clearing keeps the history`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.checkIn(a, clock)
        r.resetProgress(a, clearHistory = false)
        assertEquals(CounterState(total = 48), r.entry(a).first()!!.counter)
        // Spec R6 §3.2: nothing but checkIn writes history, so Current-tab edits and a type switch add no point.
        r.overwriteCounter(a, total = 60, bestStreak = 1, currentStreak = 1, lastCheckIn = null)
        r.setType(a, EntryType.CHECK_IN)
        assertEquals(listOf(CheckInPoint(clock.instant, 48)), r.history(a, null).first())
    }

    @Test
    fun `resetProgress with clearHistory deletes only that entry's history`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        r.checkIn(a, clock)
        r.checkIn(b, clock)
        r.resetProgress(a, clearHistory = true)
        assertEquals(CounterState(total = 48), r.entry(a).first()!!.counter)
        assertTrue(r.history(a, null).first().isEmpty())
        assertEquals(listOf(CheckInPoint(clock.instant, 48)), r.history(b, null).first())
    }

    @Test
    fun `delete removes the entry's history and a duplicate starts with none`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.checkIn(a, clock)
        val copy = r.duplicate(a)
        assertTrue(r.history(copy, null).first().isEmpty())
        r.delete(a)
        assertTrue(db.checkInDao().getForEntry(a).isEmpty())
        assertTrue(r.recentCheckIns(Instant.EPOCH).first().isEmpty())
    }

    @Test
    fun `delete removes the entry's workout sessions and a duplicate copies none`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        db.workoutSessionDao().insert(session(a))
        db.workoutSessionDao().insert(session(b))
        val copy = r.duplicate(a)
        assertTrue(db.workoutSessionDao().getForEntry(copy).isEmpty())
        r.delete(a)
        assertTrue(db.workoutSessionDao().getForEntry(a).isEmpty())
        assertEquals(1, db.workoutSessionDao().getForEntry(b).size)
    }

    @Test
    fun `resetProgress clears the workout sessions only with clearHistory`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        db.workoutSessionDao().insert(session(a))
        db.workoutSessionDao().insert(session(b))
        r.resetProgress(a, clearHistory = false)
        assertEquals(1, db.workoutSessionDao().getForEntry(a).size)
        r.resetProgress(a, clearHistory = true)
        assertTrue(db.workoutSessionDao().getForEntry(a).isEmpty())
        assertEquals(1, db.workoutSessionDao().getForEntry(b).size)
    }

    private fun session(entryId: Long) = WorkoutSessionEntity(
        entryId = entryId, startedAt = 1_000, endedAt = 241_000, activeSec = 240, plannedSec = 240,
        setsPlanned = 8, setsCompleted = 8, repsDone = 64, completed = true,
    )

    @Test
    fun `history returns points oldest first from since, or all of them for null`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val days = (0L..2L).map { clock.instant.plusSeconds(it * 24 * 3600) }
        // Inserted newest first, so the order has to come from the query.
        days.reversed().forEachIndexed { i, at ->
            db.checkInDao().insert(CheckInEntity(entryId = a, at = at.toEpochMilli(), total = 50 - i))
        }
        assertEquals(
            listOf(CheckInPoint(days[0], 48), CheckInPoint(days[1], 49), CheckInPoint(days[2], 50)),
            r.history(a, null).first(),
        )
        assertEquals(listOf(CheckInPoint(days[1], 49), CheckInPoint(days[2], 50)), r.history(a, days[1]).first())
    }

    @Test
    fun `recentCheckIns groups every entry's points since the given instant`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Stretch", EntryType.CHECK_IN)
        r.create("Lunges") // no points: absent from the map
        val old = clock.instant.minusSeconds(40L * 24 * 3600)
        db.checkInDao().insert(CheckInEntity(entryId = a, at = old.toEpochMilli(), total = 47))
        r.checkIn(a, clock)
        r.checkIn(b, clock)
        assertEquals(
            mapOf(a to listOf(CheckInPoint(clock.instant, 48)), b to listOf(CheckInPoint(clock.instant, null))),
            r.recentCheckIns(clock.instant.minusSeconds(3600)).first(),
        )
    }

    @Test
    fun `history and recentCheckIns wait for the migration gate`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val r = repo(object : MigrationGate {
            override suspend fun awaitReady() = gate.await()
        })
        val history = async { r.history(1, null).first() }
        val recent = async { r.recentCheckIns(Instant.EPOCH).first() }
        runCurrent()
        assertFalse(history.isCompleted)
        assertFalse(recent.isCompleted)
        gate.complete(Unit)
        assertTrue(history.await().isEmpty())
        assertTrue(recent.await().isEmpty())
    }
    @Test
    fun `saving holds round trips and mirrors the first hold into the legacy columns`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        assertEquals("64:4", db.entryDao().get(a)!!.holds)
        val holds = listOf(Hold(64, 4), Hold(56, 3))
        r.setProgression(a, ProgressionConfig(holds = holds))
        assertEquals(holds, r.entry(a).first()!!.progression.holds)
        val row = db.entryDao().get(a)!!
        assertEquals(Triple("64:4,56:3", 64, 4), Triple(row.holds, row.holdAt, row.holdFor))
        r.setProgression(a, ProgressionConfig(holds = emptyList()))
        assertEquals(emptyList<Hold>(), r.entry(a).first()!!.progression.holds)
        assertEquals(Triple("-", 64, 4), db.entryDao().get(a)!!.let { Triple(it.holds, it.holdAt, it.holdFor) })
    }

    @Test
    fun `setProgression resets the hold count only as holdResetNeeded says`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val two = ProgressionConfig(holds = listOf(Hold(56, 3), Hold(64, 4)))
        suspend fun holdCountAfter(p: ProgressionConfig): Int {
            db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
            r.setProgression(a, p)
            return r.entry(a).first()!!.counter.holdCount
        }
        assertEquals(0, holdCountAfter(two))                                      // a hold added
        assertEquals(3, holdCountAfter(two.copy(cap = 80, windowHours = 30)))     // same active holds
        assertEquals(0, holdCountAfter(two.copy(cap = 60, windowHours = 30)))     // 64 switched off by the cap
        assertEquals(0, holdCountAfter(two.copy(holds = two.holds.reversed())))   // reordered
        assertEquals(0, holdCountAfter(two.copy(holds = listOf(Hold(64, 4)))))    // a hold removed
    }

    @Test
    fun `duplicate copies the holds`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val holds = listOf(Hold(56, 3), Hold(64, 4))
        r.setProgression(a, ProgressionConfig(holds = holds, hold = false))
        val copy = r.duplicate(a)
        assertEquals(holds, r.entry(copy).first()!!.progression.holds)
        assertEquals("56:3,64:4", db.entryDao().get(copy)!!.holds)
    }
}
