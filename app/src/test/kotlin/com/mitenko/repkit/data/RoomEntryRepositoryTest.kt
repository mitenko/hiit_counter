package com.mitenko.repkit.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.R
import com.mitenko.repkit.data.db.CheckInEntity
import com.mitenko.repkit.data.db.EntryEntity
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.data.db.WorkoutSessionEntity
import com.mitenko.repkit.domain.InvalidEntryName
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.NameCheck
import com.mitenko.repkit.domain.Outcome
import com.mitenko.repkit.domain.Prescription
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.WeightConversion
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.loadAt
import com.mitenko.repkit.domain.resolveWeightEdit
import com.mitenko.repkit.domain.startLevel
import com.mitenko.repkit.domain.withKind
import com.mitenko.repkit.domain.withoutListWeight
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.expectThrows
import com.mitenko.repkit.testutil.testEntity
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
import org.robolectric.shadows.ShadowLog
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
    fun `setProgression lowers a stored total above the new cap in the same write and reports it`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 66, bestStreak = 3, currentStreak = 2, holdCount = 2, lastCheckIn = null)
        assertEquals(Move.CurrentLowered(60), r.setProgression(a, ProgressionConfig(cap = 60, holds = listOf(Hold(56, 4)))))
        val e = r.entry(a).first()!!
        assertEquals(60, e.counter.total)
        assertEquals(CounterState(60, 3, 2, null, 0), e.counter)
        assertEquals(60, e.progression.cap)
    }

    @Test
    fun `setProgression raises a stored total below the new floor and resets the hold count`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 50, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        // A floor and starting edit alone keeps the holds, so only the total change resets the count.
        assertEquals(Move.CurrentRaised(55), r.setProgression(a, ProgressionConfig(startingTotal = 55, floor = 55)))
        assertEquals(CounterState(55, 1, 1, null, 0), r.entry(a).first()!!.counter)
    }

    @Test
    fun `setProgression keeps a total inside the range and returns no move`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 60, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        assertNull(r.setProgression(a, ProgressionConfig(floor = 50, startingTotal = 50, cap = 70)))
        assertEquals(CounterState(60, 1, 1, null, 3), r.entry(a).first()!!.counter)
    }

    @Test
    fun `setProgression leaves a NULL total NULL, following the starting total`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        assertNull(r.setProgression(a, ProgressionConfig(startingTotal = 55, floor = 55)))
        assertNull(db.entryDao().get(a)!!.total)
        assertEquals(55, r.entry(a).first()!!.counter.total)
        assertNull(r.setProgression(a, ProgressionConfig(startingTotal = 40, floor = 40, cap = 40)))
        assertNull(db.entryDao().get(a)!!.total)
        assertEquals(40, r.entry(a).first()!!.counter.total)
    }

    @Test
    fun `a Timer only entry's stored total is never clamped`() = runTest {
        val r = repo()
        val a = r.create("Stretch", EntryType.CHECK_IN)
        db.entryDao().setCounter(a, total = 66, bestStreak = 1, currentStreak = 1, holdCount = 2, lastCheckIn = null)
        assertNull(r.setProgression(a, ProgressionConfig(cap = 60, holds = listOf(Hold(56, 4)))))
        assertEquals(66, db.entryDao().get(a)!!.total)
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

    /** Spec §6's curls: Reps then weight, 8/10/12/14/16 kg × 8–12 in kg, appended at the end; [edit] varies the row. */
    private suspend fun curlsRow(total: Int? = null, edit: (EntryEntity) -> EntryEntity = { it }): Long =
        db.entryDao().insert(
            edit(
                testEntity(name = "Curls", position = db.entryDao().count(), total = total).copy(
                    progressMode = "REPS_THEN_WEIGHT", weightUnit = "KG", weightsKind = "LIST",
                    weightList = "800,1000,1200,1400,1600",
                ),
            ),
        )

    @Test
    fun `a Reps then weight check-in logs the weight, the reps and the unit`() = runTest {
        val r = repo()
        val a = curlsRow()
        r.checkIn(a, clock)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        assertEquals(
            listOf(CheckInPoint(day1, 0, 800, 8, WeightUnit.KG), CheckInPoint(clock.instant, 1, 800, 9, WeightUnit.KG)),
            r.history(a, null).first(),
        )
        assertEquals(1, db.entryDao().get(a)!!.total)
        assertEquals(listOf("KG", "KG"), db.checkInDao().getForEntry(a).map { it.unit })
    }

    @Test
    fun `a Weight check-in logs the fixed reps per set`() = runTest {
        val r = repo()
        val a = curlsRow { it.copy(progressMode = "WEIGHT", repsPerSet = 6) }
        r.checkIn(a, clock)
        assertEquals(listOf(CheckInPoint(clock.instant, 0, 800, 6, WeightUnit.KG)), r.history(a, null).first())
    }

    @Test
    fun `a long miss in a weight mode stops one weight lighter`() = runTest {
        val r = repo()
        val a = curlsRow()
        db.entryDao().setCounter(
            a, total = 20, bestStreak = 3, currentStreak = 3, holdCount = 0,
            lastCheckIn = clock.instant.minusSeconds(168 * 3600L).toEpochMilli(),
        ) // 16 kg × 8, a week ago
        val result = r.checkIn(a, clock)
        assertEquals(Outcome.Missed(6), result.outcome)
        assertEquals(15, result.state.total)
        assertEquals(CheckInPoint(clock.instant, 15, 1400, 8, WeightUnit.KG), r.history(a, null).first().single())
    }

    @Test
    fun `Reps and Timer only points leave the weight, reps and unit NULL`() = runTest {
        val r = repo()
        val reps = r.create("Burpees")
        val timerOnly = curlsRow { it.copy(type = "CHECK_IN") }
        r.checkIn(reps, clock)
        r.checkIn(timerOnly, clock)
        for (id in listOf(reps, timerOnly)) {
            val row = db.checkInDao().getForEntry(id).single()
            assertEquals(listOf<Any?>(null, null, null), listOf(row.weight, row.reps, row.unit))
        }
        assertNull(db.entryDao().get(timerOnly)!!.total)
        assertEquals(listOf(CheckInPoint(clock.instant, null)), r.history(timerOnly, null).first())
    }

    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600))

    @Test
    fun `setWeightConfig keeps you on the same weight and resets the hold count only when it moves`() = runTest {
        val r = repo()
        val below = curlsRow(total = 12) { it.copy(holdCount = 2) } // 12 kg × 10
        r.setWeightConfig(below, curls.copy(list = listOf(1000, 1200, 1400, 1600)))
        assertEquals(7 to 2, db.entryDao().get(below)!!.let { it.total to it.holdCount })
        assertEquals(Prescription.Load(1200, 10), r.entry(below).first()!!.progression.loadAt(7))

        val current = curlsRow(total = 12) { it.copy(holdCount = 2) }
        r.setWeightConfig(current, curls.copy(list = listOf(800, 1000, 1400, 1600)))
        assertEquals(7 to 0, db.entryDao().get(current)!!.let { it.total to it.holdCount })
        assertEquals(Prescription.Load(1000, 10), r.entry(current).first()!!.progression.loadAt(7))
    }

    @Test
    fun `setWeightConfig sorts the list and clamps the reps into a shrunk range`() = runTest {
        val r = repo()
        val a = curlsRow(total = 14) // 12 kg × 12
        r.setWeightConfig(a, curls.copy(list = listOf(1600, 800, 1200, 1000, 1400), repMax = 10))
        val row = db.entryDao().get(a)!!
        assertEquals(listOf<Any?>("800,1000,1200,1400,1600", 10, 8), listOf(row.weightList, row.repMax, row.total))
        assertEquals(Prescription.Load(1200, 10), r.entry(a).first()!!.progression.loadAt(8))
    }

    @Test
    fun `setWeightConfig drops a hold on a removed weight`() = runTest {
        val r = repo()
        val a = curlsRow(total = 0) { it.copy(weightHolds = "1200:8:4,1400:8:4") }
        val holds = listOf(WeightHold(1200, 8, 4), WeightHold(1400, 8, 4))
        r.setWeightConfig(a, curls.copy(list = listOf(800, 1000, 1400, 1600), holds = holds))
        assertEquals("1400:8:4", db.entryDao().get(a)!!.weightHolds)
    }

    @Test
    fun `an invalid weight draft writes nothing and the fixed draft saves`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12)
        val before = db.entryDao().get(a)
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, curls.copy(list = listOf(800, 1000, 1000, 1600))) }
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, WeightConfig(unit = WeightUnit.KG, steps = WeightSteps(2000, 250, 6100))) }
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, curls.copy(list = listOf(800))) }
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, curls.copy(repMin = 12, repMax = 8)) }
        // Nine holds on nine different positions, so the remap's de-duplication can't hide the excess.
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, curls.copy(holds = List(9) { WeightHold(curls.list[it % 5], 8 + it / 5, 1) })) }
        assertEquals(before, db.entryDao().get(a))
        r.setWeightConfig(a, curls.copy(list = listOf(800, 1000, 1200, 1600)))
        assertEquals("800,1000,1200,1600", db.entryDao().get(a)!!.weightList)
    }

    @Test
    fun `setWeightConfig leaves an untouched counter untouched and moves the starting weight down`() = runTest {
        val r = repo()
        val a = curlsRow { it.copy(startWeight = 1200) }
        r.setWeightConfig(a, curls.copy(list = listOf(800, 1000, 1400, 1600), startWeight = 1200))
        val row = db.entryDao().get(a)!!
        assertNull(row.total)
        assertEquals(1000, row.startWeight)
        assertEquals(5, r.entry(a).first()!!.counter.total) // 10 kg × 8
    }

    @Test
    fun `a unit change converts before the remap and keeps the rung and the hold count`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12) { it.copy(holdCount = 2) }
        r.setWeightConfig(a, WeightConversion.convert(r.entry(a).first()!!.progression.weight, WeightUnit.LB))
        val row = db.entryDao().get(a)!!
        assertEquals(listOf<Any?>("LB", "1775,2200,2650,3075,3525", 12, 2), listOf(row.weightUnit, row.weightList, row.total, row.holdCount))
    }

    @Test
    fun `setWeightConfig keeps the stored unit when the draft has none`() = runTest {
        val r = repo()
        val a = curlsRow(total = 3)
        r.setWeightConfig(a, curls.copy(unit = null))
        assertEquals("KG", db.entryDao().get(a)!!.weightUnit)
    }

    @Test
    fun `in Reps mode the weight group saves without touching the total or the mode`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.overwriteCounter(a, 60, 1, 1, null)
        r.setWeightConfig(a, curls)
        val e = r.entry(a).first()!!
        assertEquals(60, e.counter.total)
        assertEquals(curls, e.progression.weight)
        assertEquals(ProgressMode.REPS, e.progression.mode)
    }

    @Test
    fun `setProgression never touches the mode or the weight group`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12)
        r.setProgression(a, ProgressionConfig(cap = 80))
        val p = r.entry(a).first()!!.progression
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, p.mode)
        assertEquals(curls, p.weight)
        assertEquals(80, p.cap)
        assertEquals(12, db.entryDao().get(a)!!.total)
    }

    @Test
    fun `switchMode starts fresh and keeps the streaks, the last check-in and the history`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.checkIn(a, clock)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        val day2 = clock.instant
        val before = db.entryDao().get(a)!!
        db.entryDao().setCounter(a, before.total, before.bestStreak, before.currentStreak, holdCount = 1, lastCheckIn = before.lastCheckIn)

        r.switchMode(a, ProgressMode.REPS_THEN_WEIGHT, defaultUnit = WeightUnit.LB)
        val row = db.entryDao().get(a)!!
        assertEquals(listOf<Any?>("REPS_THEN_WEIGHT", null, 0, "LB", true), listOf(row.progressMode, row.total, row.holdCount, row.weightUnit, row.freshStart))
        assertEquals(listOf<Any?>(2, 2, before.lastCheckIn), listOf(row.currentStreak, row.bestStreak, row.lastCheckIn))
        assertEquals(0, r.entry(a).first()!!.counter.total) // 20 lb × 8, the default steps' start
        assertEquals(listOf(CheckInPoint(day1, 48), CheckInPoint(day2, 49)), r.history(a, null).first())

        // Plan Spec note 13 (ruling A): the first check-in after Start fresh is AT the start, the streak goes on,
        // and the flag is cleared in the same transaction, so the next on-time check-in moves +1.
        clock.instant = day2.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        assertEquals(CheckInPoint(clock.instant, 0, 2000, 8, WeightUnit.LB), r.history(a, null).first().last())
        assertEquals(listOf<Any?>(3, false), db.entryDao().get(a)!!.let { listOf(it.currentStreak, it.freshStart) })
        clock.instant = clock.instant.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        assertEquals(CheckInPoint(clock.instant, 1, 2000, 9, WeightUnit.LB), r.history(a, null).first().last())

        r.switchMode(a, ProgressMode.REPS, WeightUnit.KG)
        assertEquals(48, r.entry(a).first()!!.counter.total)
        assertEquals(4, r.history(a, null).first().size)
        // §9.3: once set, the workout owns its unit; a later default doesn't change it.
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        assertEquals("LB", db.entryDao().get(a)!!.weightUnit)
    }

    @Test
    fun `a missed check-in after Start fresh stays at the start with the streak reset`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.checkIn(a, clock)
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        clock.instant = clock.instant.plusSeconds(168 * 3600)
        val result = r.checkIn(a, clock)
        assertEquals(Outcome.Missed(0), result.outcome)
        val row = db.entryDao().get(a)!!
        assertEquals(listOf<Any?>(0, 1, false), listOf(row.total, row.currentStreak, row.freshStart))
    }

    @Test
    fun `a Timer only check-in keeps the fresh start for the first Counter check-in`() = runTest {
        val r = repo()
        val a = r.create("Curls", EntryType.CHECK_IN)
        r.checkIn(a, clock)
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        clock.instant = clock.instant.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        assertEquals(listOf<Any?>(null, 2, true), db.entryDao().get(a)!!.let { listOf(it.total, it.currentStreak, it.freshStart) })

        r.setType(a, EntryType.WORKOUT)
        clock.instant = clock.instant.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        assertEquals(listOf<Any?>(0, 3, false), db.entryDao().get(a)!!.let { listOf(it.total, it.currentStreak, it.freshStart) })
    }

    @Test
    fun `an explicit counter, a reset and a duplicate all clear the fresh start`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        val copy = r.duplicate(a)
        assertEquals(listOf(true, false), listOf(a, copy).map { db.entryDao().get(it)!!.freshStart })
        r.overwriteCounter(a, total = 2, bestStreak = 1, currentStreak = 1, lastCheckIn = null)
        assertFalse(db.entryDao().get(a)!!.freshStart)

        r.switchMode(a, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG)
        assertTrue(db.entryDao().get(a)!!.freshStart)
        r.resetProgress(a, clearHistory = false)
        assertFalse(db.entryDao().get(a)!!.freshStart)
        assertFalse(r.entry(a).first()!!.counter.freshStart)
    }

    @Test
    fun `a new entry has no unit until its first switch into a weight mode`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        assertNull(db.entryDao().get(a)!!.weightUnit)
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        assertEquals("KG", db.entryDao().get(a)!!.weightUnit)
    }

    @Test
    fun `switching to the current mode changes nothing`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.overwriteCounter(a, 60, 3, 3, null)
        r.switchMode(a, ProgressMode.REPS, WeightUnit.KG)
        assertEquals(CounterState(60, 3, 3, null, 0), r.entry(a).first()!!.counter)
        assertNull(db.entryDao().get(a)!!.weightUnit)
    }

    @Test
    fun `weight writes on missing ids throw EntryNotFound`() = runTest {
        val r = repo()
        expectThrows<EntryNotFound> { r.setWeightConfig(99, curls) }
        expectThrows<EntryNotFound> { r.switchMode(99, ProgressMode.WEIGHT, WeightUnit.KG) }
    }

    @Test
    fun `overwriteCounter takes a level from 0 to the top in a weight mode`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12)
        r.overwriteCounter(a, total = 0, bestStreak = 1, currentStreak = 1, lastCheckIn = null)
        assertEquals(0, db.entryDao().get(a)!!.total)
        assertEquals(0, r.entry(a).first()!!.counter.total)
        r.overwriteCounter(a, total = 24, bestStreak = 1, currentStreak = 1, lastCheckIn = null)
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 25, 1, 1, null) }
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, -1, 1, 1, null) }
        assertEquals(24, db.entryDao().get(a)!!.total)
    }

    @Test
    fun `duplicate copies the mode and the weight group with a fresh counter`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12) { it.copy(weightHolds = "1400:8:4", startWeight = 1000, startReps = 9) }
        val copy = r.duplicate(a)
        val e = r.entry(copy).first()!!
        assertEquals(r.entry(a).first()!!.progression, e.progression)
        assertNull(db.entryDao().get(copy)!!.total)
        assertEquals(6, e.counter.total) // the start: 10 kg × 9
        assertEquals("KG", db.entryDao().get(copy)!!.weightUnit)
    }

    @Test
    fun `in a weight mode the Reps floor and cap never widen for a level and never move it`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12) // Reps then weight: levels 0..24
        assertNull(r.overwriteCounter(a, total = 24, bestStreak = 1, currentStreak = 1, lastCheckIn = null))
        // In Reps mode a cap of 20 would pull a total of 24 down to 20 (spec revision 28 rule 4).
        assertNull(r.setProgression(a, ProgressionConfig(startingTotal = 5, floor = 5, cap = 20)))
        assertEquals(24, db.entryDao().get(a)!!.total)
        // In Reps mode a total of 23 would raise the cap to 23 (spec revision 27).
        assertNull(r.overwriteCounter(a, total = 23, bestStreak = 1, currentStreak = 1, lastCheckIn = null))
        assertEquals(5 to 20, r.entry(a).first()!!.progression.let { it.floor to it.cap })
        // Past the top level is rejected, not widened.
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 25, 1, 1, null) }
        assertEquals(23, db.entryDao().get(a)!!.total)
        assertEquals(20, r.entry(a).first()!!.progression.cap)
    }

    @Test
    fun `an unknown unit on a history row reads as null and is logged`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        db.checkInDao().insert(CheckInEntity(entryId = a, at = 1_000, total = 15, weight = 1400, reps = 8, unit = "STONE"))
        ShadowLog.clear()
        assertNull(r.history(a, null).first().single().unit)
        assertTrue(ShadowLog.getLogsForTag("EntryMapping").any { it.type == android.util.Log.WARN && "STONE" in it.msg })
    }

    @Test
    fun `in a weight mode setProgression resets the hold count only for the Hold switch and logs no level as invalid`() = runTest {
        val r = repo()
        val a = curlsRow(total = 0) { it.copy(holdCount = 2) } // level 0: 8 kg × 8
        ShadowLog.clear()
        r.setProgression(a, ProgressionConfig(holds = listOf(Hold(66, 3))))
        r.setProgression(a, ProgressionConfig(holds = listOf(Hold(66, 3)), cap = 60))
        assertEquals(0 to 2, db.entryDao().get(a)!!.let { it.total to it.holdCount })
        assertFalse(ShadowLog.getLogsForTag("EntryMapping").any { "Invalid total" in it.msg })
        r.setProgression(a, ProgressionConfig(holds = listOf(Hold(66, 3)), cap = 60, hold = false))
        assertEquals(0 to 0, db.entryDao().get(a)!!.let { it.total to it.holdCount })
    }

    @Test
    fun `setWeightConfig returns where it moved the current weight, and null when it stayed`() = runTest {
        val r = repo()
        val id = r.create("Curls")
        r.switchMode(id, ProgressMode.WEIGHT, WeightUnit.KG)
        r.overwriteCounter(id, total = 2, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // 25 kg on 20 / 2.5 / 60
        val list = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(2000, 2250, 3000))
        assertEquals(WeightMove.CurrentMoved(2250, null), r.setWeightConfig(id, list))
        assertEquals(1, db.entryDao().get(id)!!.total)
        assertNull(r.setWeightConfig(id, list.copy(repsPerSet = 12)))
    }

    @Test
    fun `setWeightConfig notes moved reps in Reps then weight, and nothing for an untouched counter`() = runTest {
        val r = repo()
        val id = r.create("Curls")
        r.switchMode(id, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG)
        assertNull(r.setWeightConfig(id, WeightConfig(unit = WeightUnit.KG, repMin = 8, repMax = 10))) // NULL total: nothing to move
        r.overwriteCounter(id, total = 2, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // 20 kg × 10
        assertEquals(WeightMove.CurrentMoved(2000, 9), r.setWeightConfig(id, WeightConfig(unit = WeightUnit.KG, repMin = 8, repMax = 9)))
    }

    @Test
    fun `setWeightConfig in Reps mode never notes a move`() = runTest {
        val r = repo()
        val id = r.create("Curls")
        assertNull(r.setWeightConfig(id, WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1200))))
    }

    // Plan Spec notes 43, 45, 46 and the transition matrix (note 47). Most of these pin behaviour PR 1
    // already has; they fail only if a later change breaks a contract.

    /** Weight mode on 10 / 5 / 25 kg (10, 15, 20, 25), a hold on 20 for 3, at 15 kg (level 1), hold count 2, fresh start still set. */
    private suspend fun matrixRow(r: RoomEntryRepository): Long {
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        r.setWeightConfig(a, WeightConfig(unit = WeightUnit.KG, steps = WeightSteps(1000, 500, 2500), holds = listOf(WeightHold(2000, 8, 3))))
        val row = db.entryDao().get(a)!!
        db.entryDao().setCounter(a, 1, row.bestStreak, row.currentStreak, holdCount = 2, lastCheckIn = row.lastCheckIn)
        return a
    }

    private suspend fun weightsOf(r: RoomEntryRepository, a: Long) = r.entry(a).first()!!.progression.weight

    /** total, hold_count, fresh_start as stored. */
    private suspend fun counterOf(a: Long) = db.entryDao().get(a)!!.let { listOf<Any?>(it.total, it.holdCount, it.freshStart) }

    @Test
    fun `matrix - Steps to My weights with the same values keeps the level, the hold count and the fresh start`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertNull(r.setWeightConfig(a, weightsOf(r, a).withKind(WeightsKind.LIST)))
        assertEquals("1000,1500,2000,2500", db.entryDao().get(a)!!.weightList)
        assertEquals(listOf<Any?>(1, 2, true), counterOf(a))
    }

    @Test
    fun `matrix - a unit change converts in one save and keeps the level, the hold count, the fresh start and the history`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        db.checkInDao().insert(CheckInEntity(entryId = a, at = 1L, total = 1, weight = 1500, reps = 10, unit = "KG"))
        val history = db.checkInDao().getForEntry(a)
        val lb = WeightConversion.convert(weightsOf(r, a), WeightUnit.LB)
        assertNull(r.setWeightConfig(a, lb))
        assertEquals(lb, weightsOf(r, a))
        assertEquals(listOf<Any?>(1, 2, true), counterOf(a))
        assertEquals(history, db.checkInDao().getForEntry(a))
    }

    @Test
    fun `matrix - removing a weight renumbers the level but keeps its value and the hold count`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertNull(r.setWeightConfig(a, weightsOf(r, a).withKind(WeightsKind.LIST).withoutListWeight(0))) // 10 kg gone
        assertEquals(listOf<Any?>(0, 2, true), counterOf(a)) // 15 kg is now level 0
    }

    @Test
    fun `matrix - removing the current weight moves it down, notes it and resets the hold count`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertEquals(WeightMove.CurrentMoved(1000, null), r.setWeightConfig(a, weightsOf(r, a).withKind(WeightsKind.LIST).withoutListWeight(1)))
        assertEquals(listOf<Any?>(0, 0, true), counterOf(a))
    }

    @Test
    fun `matrix - a hold whose weight is removed is dropped and resets the hold count`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertNull(r.setWeightConfig(a, weightsOf(r, a).withKind(WeightsKind.LIST).withoutListWeight(2))) // 20 kg gone; its hold is still in the draft
        assertEquals(emptyList<WeightHold>(), weightsOf(r, a).holds)
        assertEquals(listOf<Any?>(1, 0, true), counterOf(a))
    }

    @Test
    fun `matrix - reset to defaults in a weight mode moves to the lightest default and resets the hold count`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertEquals(WeightMove.CurrentMoved(2000, null), r.setWeightConfig(a, WeightConfig(unit = WeightUnit.KG))) // nothing ≤ 15 kg: the lightest, 20
        assertEquals(WeightConfig(unit = WeightUnit.KG), weightsOf(r, a))
        assertEquals(listOf<Any?>(0, 0, true), counterOf(a))
    }

    @Test
    fun `the stored row doesn't depend on whether the draft was remapped or sorted first`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        val b = matrixRow(r)
        val before = weightsOf(r, a).withKind(WeightsKind.LIST).copy(startWeight = 1500)
        r.setWeightConfig(a, before)
        r.setWeightConfig(b, before)
        // One edit removing 15 kg, the start and current weight. a: stale and unsorted; b: remapped by the page first.
        val stale = before.copy(list = listOf(2500, 1000, 2000))
        val remapped = resolveWeightEdit(ProgressMode.WEIGHT, before, before.withoutListWeight(1), WeightField.LIST).config
        assertEquals(1000, remapped.startWeight)
        assertEquals(WeightMove.CurrentMoved(1000, null), r.setWeightConfig(a, stale))
        assertEquals(WeightMove.CurrentMoved(1000, null), r.setWeightConfig(b, remapped))
        assertEquals(db.entryDao().get(a)!!.copy(id = 0, position = 0), db.entryDao().get(b)!!.copy(id = 0, position = 0))
    }

    @Test
    fun `switchMode keeps a unit the workout already has`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.LB)
        r.switchMode(a, ProgressMode.REPS, WeightUnit.KG)
        r.switchMode(a, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG) // the app default is now kg
        assertEquals("LB", db.entryDao().get(a)!!.weightUnit)
    }

    // Bug fix (batch 1 review, 2026-10-10): the stored level indexes the stored, unconverted ladder,
    // but a unit change's `old` is already converted. WeightConversion.convert's distinct() can merge
    // weights, shifting indexes, so the current load must be found on the stored ladder first and only
    // then converted and re-found on the converted ladder.

    @Test
    fun `a unit change finds the current load on the stored ladder before converting it (Weight mode)`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.LB)
        r.setWeightConfig(a, WeightConfig(unit = WeightUnit.LB, kind = WeightsKind.LIST, list = listOf(1300, 1350, 1400)))
        r.overwriteCounter(a, total = 1, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // level 1: 13.5 lb
        val row = db.entryDao().get(a)!!
        db.entryDao().setCounter(a, row.total, row.bestStreak, row.currentStreak, holdCount = 2, lastCheckIn = row.lastCheckIn)
        val kg = WeightConversion.convert(r.entry(a).first()!!.progression.weight, WeightUnit.KG)
        assertEquals(listOf(600, 625), kg.list) // the merge: 13 and 13.5 lb both round to 6.0 kg
        assertNull(r.setWeightConfig(a, kg)) // kept by value: not a move, even though the level index shifts
        assertEquals(0, db.entryDao().get(a)!!.total) // 6.0 kg (index 0), not the buggy 6.25 kg (index 1)
        assertEquals(2, db.entryDao().get(a)!!.holdCount) // unchanged
    }

    @Test
    fun `a unit change in Reps then weight keeps the reps and finds the load on the stored ladder`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.LB)
        r.setWeightConfig(a, WeightConfig(unit = WeightUnit.LB, kind = WeightsKind.LIST, list = listOf(1300, 1350, 1400), repMin = 8, repMax = 10))
        r.overwriteCounter(a, total = 4, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // 13.5 lb × 9 reps (level 1×3 + 1)
        val row = db.entryDao().get(a)!!
        db.entryDao().setCounter(a, row.total, row.bestStreak, row.currentStreak, holdCount = 2, lastCheckIn = row.lastCheckIn)
        val kg = WeightConversion.convert(r.entry(a).first()!!.progression.weight, WeightUnit.KG)
        assertEquals(listOf(600, 625), kg.list) // the same merge: 13 and 13.5 lb both round to 6.0 kg
        assertNull(r.setWeightConfig(a, kg))
        assertEquals(1, db.entryDao().get(a)!!.total) // 6.0 kg × 9 reps (level 0×3 + 1), not the buggy 6.25 kg
        assertEquals(Prescription.Load(600, 9), r.entry(a).first()!!.progression.loadAt(1))
        assertEquals(2, db.entryDao().get(a)!!.holdCount) // unchanged
    }

    @Test
    fun `a Reps then weight Current save resets the hold count only when the level changes, and never widens`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG) // 20 / 2.5 / 60 kg × 8–12: 17 weights × span 5, levels 0..84
        r.overwriteCounter(a, total = 7, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // 22.5 kg × 10
        val before = db.entryDao().get(a)!!
        db.entryDao().setCounter(a, 7, 0, 0, holdCount = 2, lastCheckIn = null)
        assertNull(r.overwriteCounter(a, total = 7, bestStreak = 3, currentStreak = 3, lastCheckIn = null)) // streaks only: same level
        assertEquals(2, db.entryDao().get(a)!!.holdCount)
        assertNull(r.overwriteCounter(a, total = 2, bestStreak = 3, currentStreak = 3, lastCheckIn = null)) // weight only: 20 kg × 10
        assertEquals(0, db.entryDao().get(a)!!.holdCount)
        assertNull(r.overwriteCounter(a, total = 84, bestStreak = 3, currentStreak = 3, lastCheckIn = null)) // the top: 60 kg × 12
        val after = db.entryDao().get(a)!!
        assertEquals(listOf(before.startingTotal, before.floor, before.cap), listOf(after.startingTotal, after.floor, after.cap))
        assertTrue(runCatching { r.overwriteCounter(a, total = 85, bestStreak = 3, currentStreak = 3, lastCheckIn = null) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(84, db.entryDao().get(a)!!.total)
    }

    @Test
    fun `a Current edit that keeps the level keeps the fresh start, and moving the level clears it`() = runTest {
        val r = repo()
        val id = r.create("Curls")
        r.switchMode(id, ProgressMode.WEIGHT, WeightUnit.KG)
        r.overwriteCounter(id, total = 0, bestStreak = 5, currentStreak = 5, lastCheckIn = null) // the start level, streaks edited
        assertEquals(true, db.entryDao().get(id)!!.freshStart)
        r.overwriteCounter(id, total = 3, bestStreak = 5, currentStreak = 5, lastCheckIn = null)
        assertEquals(false, db.entryDao().get(id)!!.freshStart)
    }

    // §10 note 49: a streak-only Current save on an untouched (NULL) counter must keep it NULL, not
    // freeze it as the resolved start value — otherwise a later setWeightConfig/setProgression remaps
    // that frozen value instead of following the new start.
    @Test
    fun `a streak-only Current save on an untouched weight level keeps it NULL, so a later start-weight change still lands there`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        assertNull(db.entryDao().get(a)!!.total)
        assertEquals(0, r.entry(a).first()!!.counter.total) // the lightest weight (20 kg), the resolved start
        r.overwriteCounter(a, total = 0, bestStreak = 5, currentStreak = 5, lastCheckIn = null) // streak-only: same level
        assertNull(db.entryDao().get(a)!!.total) // stays untouched, not frozen at 0
        assertTrue(db.entryDao().get(a)!!.freshStart)
        val weight = r.entry(a).first()!!.progression.weight
        assertNull(r.setWeightConfig(a, weight.copy(startWeight = 3000))) // move the start to 30 kg; no move reported
        assertNull(db.entryDao().get(a)!!.total) // still untouched
        val entry = r.entry(a).first()!!
        assertEquals(entry.progression.startLevel(), entry.counter.total)
        assertEquals(Prescription.Load(3000, 10), entry.progression.loadAt(entry.counter.total))
        val result = r.checkIn(a, clock) // the next check-in lands on the new start, not the old one
        assertEquals(Prescription.Load(3000, 10), entry.progression.loadAt(result.state.total))
    }

    @Test
    fun `a streak-only Current save on an untouched Reps total keeps it NULL, so a later starting-total change still lands there`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        assertNull(db.entryDao().get(a)!!.total)
        assertEquals(48, r.entry(a).first()!!.counter.total) // the default starting total, the resolved start
        r.overwriteCounter(a, total = 48, bestStreak = 5, currentStreak = 5, lastCheckIn = null) // streak-only: same level
        assertNull(db.entryDao().get(a)!!.total) // stays untouched, not frozen at 48
        assertNull(r.setProgression(a, ProgressionConfig(startingTotal = 60, floor = 55))) // no move: still untouched
        assertNull(db.entryDao().get(a)!!.total) // still untouched
        assertEquals(60, r.entry(a).first()!!.counter.total) // follows the new starting total
    }

    @Test
    fun `overwriteCounter with a real level change still stores it and clears the fresh start`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        assertNull(db.entryDao().get(a)!!.total)
        r.overwriteCounter(a, total = 3, bestStreak = 1, currentStreak = 1, lastCheckIn = null) // a real level, not the start
        val row = db.entryDao().get(a)!!
        assertEquals(3, row.total)
        assertFalse(row.freshStart)
    }
}
