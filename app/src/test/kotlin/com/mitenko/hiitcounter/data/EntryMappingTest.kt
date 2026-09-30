package com.mitenko.hiitcounter.data

import com.mitenko.hiitcounter.data.db.CheckInEntity
import com.mitenko.hiitcounter.domain.model.CheckInPoint
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.testEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EntryMappingTest {
    @Test
    fun `a null total resolves to the starting total`() {
        assertEquals(55, testEntity(total = null).copy(startingTotal = 55).toDomain().counter.total)
    }

    @Test
    fun `an invalid total reads as null`() {
        assertNull(validTotal(0))
        assertNull(validTotal(-3))
        assertNull(validTotal(null))
        assertEquals(5, validTotal(5))
        assertEquals(48, testEntity(total = 0).toDomain().counter.total)
    }

    @Test
    fun `a valid row maps field by field`() {
        val row = testEntity(id = 7, name = "Burpees", position = 2, total = 65).copy(
            prepareSec = 5, sets = 6, workSec = 30, restSec = 15, cooldownSec = 60,
            startingTotal = 50, floor = 40, cap = 80, holdAt = 70, holdFor = 3, windowHours = 30, penaltyHoursPerRep = 12.5,
            cueSound = false, cueVibration = true,
            bestStreak = 24, currentStreak = 4, holdCount = 2, lastCheckIn = 1_790_000_000_123,
        )
        assertEquals(
            Entry(
                id = 7, name = "Burpees", position = 2,
                timing = TimingConfig(5, 6, 30, 15, 60),
                progression = ProgressionConfig(50, 40, 80, 70, 3, 30, 12.5),
                cues = CueConfig(sound = false, vibration = true),
                counter = CounterState(65, 24, 4, Instant.ofEpochMilli(1_790_000_000_123), 2),
            ),
            row.toDomain(),
        )
    }

    @Test
    fun `one invalid timing field is repaired on its own`() {
        val timing = testEntity().copy(sets = 99, workSec = 30).toDomain().timing
        assertEquals(8, timing.sets)
        assertEquals(30, timing.workSec)
    }

    @Test
    fun `timing still inconsistent after repair falls back as a group`() {
        val entry = testEntity().copy(sets = 20, workSec = 3599, restSec = 3599, cap = 80).toDomain()
        assertEquals(TimingConfig(), entry.timing)
        assertEquals(80, entry.progression.cap)
    }

    @Test
    fun `progression fields are repaired per field`() {
        val p = testEntity().copy(penaltyHoursPerRep = Double.NaN, holdFor = -1, windowHours = 30).toDomain().progression
        assertEquals(19.5, p.penaltyHoursPerRep, 0.0)
        assertEquals(4, p.holdFor)
        assertEquals(30, p.windowHours)
    }

    @Test
    fun `inconsistent progression falls back as a group and timing is kept`() {
        val entry = testEntity().copy(floor = 80, cap = 60, sets = 6).toDomain()
        assertEquals(ProgressionConfig(), entry.progression)
        assertEquals(6, entry.timing.sets)
    }

    @Test
    fun `invalid counter values and names are repaired`() {
        val entry = testEntity().copy(name = "   ", bestStreak = 7, currentStreak = -1, holdCount = -2).toDomain()
        assertEquals("Workout", entry.name)
        assertEquals(7, entry.counter.bestStreak)
        assertEquals(0, entry.counter.currentStreak)
        assertEquals(0, entry.counter.holdCount)
        assertEquals("x".repeat(40), testEntity().copy(name = "x".repeat(45)).toDomain().name)
    }

    @Test
    fun `a new row has default settings and an untouched counter`() {
        val row = entryEntity("Burpees", position = 3)
        assertNull(row.total)
        assertEquals(
            Entry(0, "Burpees", 3, TimingConfig(), ProgressionConfig(), CueConfig(), CounterState(total = 48)),
            row.toDomain(),
        )
    }

    @Test
    fun `stored counter fields are written as given`() {
        val row = entryEntity(
            "Workout", 0,
            counter = StoredCounter(total = 65, bestStreak = 24, currentStreak = 4, holdCount = 1, lastCheckIn = 1_000),
        )
        assertEquals(
            listOf<Any?>(65, 24, 4, 1, 1_000L),
            listOf(row.total, row.bestStreak, row.currentStreak, row.holdCount, row.lastCheckIn),
        )
    }

    @Test
    fun `hold_enabled maps to the hold switch and back`() {
        val off = testEntity().copy(holdEnabled = false).toDomain().progression
        assertEquals(ProgressionConfig(hold = false), off)
        assertFalse(off.holdEnabled)
        assertTrue(testEntity().toDomain().progression.hold)
        assertFalse(entryEntity("Burpees", 0, progression = ProgressionConfig(hold = false)).holdEnabled)
        assertTrue(entryEntity("Burpees", 0).holdEnabled)
    }

    @Test
    fun `an inconsistent progression falls back with the hold switched on`() {
        assertEquals(ProgressionConfig(), testEntity().copy(holdEnabled = false, floor = 80, cap = 60).toDomain().progression)
    }

    @Test
    fun `type and voice map to the domain and back`() {
        val entry = testEntity().copy(type = "CHECK_IN", cueVoice = true).toDomain()
        assertEquals(EntryType.CHECK_IN, entry.type)
        assertEquals(CueConfig(voice = true), entry.cues)
        val row = entryEntity("Stretch", 0, cues = CueConfig(voice = true), type = EntryType.CHECK_IN)
        assertEquals("CHECK_IN", row.type)
        assertTrue(row.cueVoice)
        assertEquals("WORKOUT", entryEntity("Burpees", 0).type)
        assertFalse(entryEntity("Burpees", 0).cueVoice)
    }

    @Test
    fun `an unknown type reads as a workout`() {
        assertEquals(EntryType.WORKOUT, testEntity().copy(type = "HABIT").toDomain().type)
        assertEquals(EntryType.WORKOUT, testEntity().copy(type = "").toDomain().type)
        assertEquals(EntryType.WORKOUT, testEntity().copy(type = "check_in").toDomain().type)
    }

    @Test
    fun `a check-in row maps to a point`() {
        assertEquals(CheckInPoint(Instant.ofEpochMilli(1_790_000_000_123), 62), CheckInEntity(1, 7, 1_790_000_000_123, 62).toPoint())
        assertNull(CheckInEntity(2, 7, 1_000, null).toPoint().total)
    }
}
