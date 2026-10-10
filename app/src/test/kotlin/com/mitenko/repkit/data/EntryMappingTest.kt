package com.mitenko.repkit.data

import com.mitenko.repkit.data.db.CheckInEntity
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.HoldKind
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.NoOpCrashReporter
import com.mitenko.repkit.testutil.RecordingCrashReporter
import com.mitenko.repkit.testutil.testEntity
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
                progression = ProgressionConfig(50, 40, 80, listOf(Hold(70, 3)), 30, 12.5),
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
        assertEquals(listOf(Hold(64, 4)), p.holds)
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
        assertTrue(off.activeHolds.isEmpty())
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
    @Test
    fun `holds read from the holds column, and the empty default reads the legacy columns`() {
        val legacy = testEntity().copy(holdAt = 70, holdFor = 3)
        assertEquals("", legacy.holds)
        assertEquals(listOf(Hold(70, 3)), legacy.toDomain().progression.holds)
        assertEquals(listOf(Hold(56, 3), Hold(64, 4)), legacy.copy(holds = "56:3,64:4").toDomain().progression.holds)
        assertEquals(emptyList<Hold>(), legacy.copy(holds = "-").toDomain().progression.holds)
    }

    @Test
    fun `unparseable holds read as the default hold`() {
        val p = testEntity().copy(holdAt = 70, holdFor = 3, holds = "56:x", cap = 80).toDomain().progression
        assertEquals(listOf(Hold(64, 4)), p.holds)
        assertEquals(80, p.cap)
    }

    @Test
    fun `one garbage item among good ones is dropped and the rest of the progression kept`() {
        val p = testEntity().copy(holds = "56:3,x:1,64:4", cap = 80, windowHours = 30).toDomain().progression
        assertEquals(listOf(Hold(56, 3), Hold(64, 4)), p.holds)
        assertEquals(80, p.cap)
        assertEquals(30, p.windowHours)
    }

    @Test
    fun `a stored duplicate keeps the first and the rest of the progression`() {
        val p = testEntity().copy(holds = "64:4,56:3,64:2", holdEnabled = true, cap = 80).toDomain().progression
        assertEquals(listOf(Hold(64, 4), Hold(56, 3)), p.holds)
        assertTrue(p.hold)
        assertEquals(80, p.cap)
    }

    @Test
    fun `more than eight stored holds keep the first eight and the rest of the progression`() {
        val nine = (0 until 9).map { Hold(50 + it, 1) }
        val p = testEntity().copy(holds = HoldsCodec.encode(nine), cap = 80).toDomain().progression
        assertEquals(nine.take(8), p.holds)
        assertEquals(80, p.cap)
    }

    @Test
    fun `holds are written in order and mirrored into the legacy columns`() {
        val row = entryEntity("Burpees", 0, progression = ProgressionConfig(holds = listOf(Hold(56, 3), Hold(64, 4))))
        assertEquals(Triple("56:3,64:4", 56, 3), Triple(row.holds, row.holdAt, row.holdFor))
        val empty = entryEntity("Burpees", 0, progression = ProgressionConfig(holds = emptyList()))
        assertEquals(Triple("-", 64, 4), Triple(empty.holds, empty.holdAt, empty.holdFor))
        assertEquals(emptyList<Hold>(), empty.toDomain().progression.holds)
    }

    @Test
    fun `the repair keeps At and From on one value and drops later duplicates of the same kind`() {
        val p = testEntity().copy(holds = "64:4,64+:2,64:1,64+:3,60+:1", cap = 80).toDomain().progression
        assertEquals(listOf(Hold(64, 4), Hold(64, 2, HoldKind.FROM), Hold(60, 1, HoldKind.FROM)), p.holds)
        assertEquals(80, p.cap)
    }

    @Test
    fun `a From hold is written with a plus and mirrored into the legacy columns whatever its kind`() {
        val row = entryEntity("Burpees", 0, progression = ProgressionConfig(holds = listOf(Hold(60, 2, HoldKind.FROM), Hold(64, 4))))
        assertEquals(Triple("60+:2,64:4", 60, 2), Triple(row.holds, row.holdAt, row.holdFor))
        assertEquals(listOf(Hold(60, 2, HoldKind.FROM), Hold(64, 4)), row.toDomain().progression.holds)
    }

    @Test
    fun `a repair leaves a breadcrumb without values and the same result`() {
        val row = testEntity(id = 7, name = "Burpees").copy(sets = 99, type = "BOGUS")
        val expected = row.toDomain()
        val reporter = RecordingCrashReporter()
        RepairBreadcrumbs.reporter = reporter
        try {
            assertEquals(expected, row.toDomain())
        } finally {
            RepairBreadcrumbs.reporter = NoOpCrashReporter
        }
        assertEquals(listOf("EntryMapping: entry 7 repaired sets", "EntryMapping: entry 7 repaired type"), reporter.logs)
        assertTrue(reporter.nonFatals.isEmpty())
    }

    @Test
    fun `a valid row leaves no breadcrumb`() {
        val reporter = RecordingCrashReporter()
        RepairBreadcrumbs.reporter = reporter
        try {
            testEntity(id = 7, name = "Burpees").toDomain()
        } finally {
            RepairBreadcrumbs.reporter = NoOpCrashReporter
        }
        assertTrue(reporter.logs.isEmpty())
    }

    /** Spec §6's curls: Reps then weight, 8/10/12/14/16 kg × 8–12. */
    private fun curlsRow(total: Int? = null) = testEntity(total = total).copy(
        progressMode = "REPS_THEN_WEIGHT", weightUnit = "KG", weightsKind = "LIST", weightList = "800,1000,1200,1400,1600",
    )

    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600))

    @Test
    fun `a row as the v7 migration leaves it reads as Reps with the default weight settings`() {
        val e = testEntity(total = 60).toDomain()
        assertEquals(ProgressionConfig(), e.progression)
        assertEquals(60, e.counter.total)
        assertFalse(e.counter.freshStart)
    }

    @Test
    fun `fresh_start maps to and from the counter's freshStart`() {
        assertTrue(testEntity(total = 60).copy(freshStart = true).toDomain().counter.freshStart)
        assertTrue(curlsRow().copy(freshStart = true).toDomain().counter.freshStart)
        assertTrue(entryEntity("Curls", 0, counter = StoredCounter(freshStart = true)).freshStart)
        assertFalse(entryEntity("Curls", 0).freshStart)
    }

    @Test
    fun `a weight row maps field by field`() {
        val row = curlsRow(total = 13).copy(startWeight = 1000, startReps = 9, weightHolds = "1400:8:4", repsPerSet = 6)
        val expected = ProgressionConfig(
            mode = ProgressMode.REPS_THEN_WEIGHT,
            weight = curls.copy(repsPerSet = 6, startWeight = 1000, startReps = 9, holds = listOf(WeightHold(1400, 8, 4))),
        )
        assertEquals(expected, row.toDomain().progression)
        assertEquals(13, row.toDomain().counter.total)
    }

    @Test
    fun `in a weight mode a null total reads as the start level and 0 is a real level`() {
        assertEquals(6, curlsRow().copy(startWeight = 1000, startReps = 9).toDomain().counter.total)
        assertEquals(0, curlsRow(total = 0).copy(startWeight = 1000).toDomain().counter.total)
        assertEquals(5, curlsRow(total = -1).copy(startWeight = 1000).toDomain().counter.total)
        // Reps mode keeps today's rule: 0 is invalid and reads as the starting total.
        assertEquals(48, testEntity(total = 0).toDomain().counter.total)
    }

    @Test
    fun `unknown mode, kind and unit strings are repaired`() {
        assertEquals(ProgressMode.REPS, testEntity().copy(progressMode = "LEGS").toDomain().progression.mode)
        assertEquals(WeightsKind.STEPS, curlsRow().copy(weightsKind = "PLATES").toDomain().progression.weight.kind)
        assertNull(testEntity().copy(weightUnit = "STONE").toDomain().progression.weight.unit)
        // Plan Spec note 11: a weight mode always has a unit.
        assertEquals(WeightUnit.KG, curlsRow().copy(weightUnit = null).toDomain().progression.weight.unit)
        assertEquals(WeightUnit.KG, curlsRow().copy(weightUnit = "STONE").toDomain().progression.weight.unit)
    }

    @Test
    fun `the weight list is sorted and de-duplicated, and bad steps read as the default`() {
        assertEquals(listOf(800, 1200, 1600), curlsRow().copy(weightList = "1200,800,800,1600").toDomain().progression.weight.list)
        assertEquals(WeightSteps.DEFAULT, testEntity().copy(weightSteps = "20:2.5").toDomain().progression.weight.steps)
    }

    @Test
    fun `a starting point or hold that isn't on the ladder is dropped`() {
        val w = curlsRow().copy(startWeight = 900, startReps = 13, weightHolds = "900:8:4,1400:8:4,1400:13:2,x")
            .toDomain().progression.weight
        assertNull(w.startWeight)
        assertNull(w.startReps)
        assertEquals(listOf(WeightHold(1400, 8, 4)), w.holds)
    }

    @Test
    fun `in Weight mode two holds on one weight keep the first`() {
        val w = curlsRow().copy(progressMode = "WEIGHT", weightHolds = "1400:8:4,1400:10:2").toDomain().progression.weight
        assertEquals(listOf(WeightHold(1400, 8, 4)), w.holds)
    }

    @Test
    fun `an inconsistent weight group falls back on its own, keeping the unit and the Reps progression`() {
        val p = curlsRow().copy(weightList = "800", cap = 80).toDomain().progression
        assertEquals(WeightConfig(unit = WeightUnit.KG), p.weight)
        assertEquals(80, p.cap)
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, p.mode)
    }

    @Test
    fun `an inconsistent Reps progression falls back and keeps the mode and the weight group`() {
        val p = curlsRow().copy(floor = 80, cap = 60).toDomain().progression
        assertEquals(ProgressionConfig(mode = ProgressMode.REPS_THEN_WEIGHT, weight = curls), p)
    }

    @Test
    fun `a weight config is written and read back unchanged`() {
        val p = ProgressionConfig(
            mode = ProgressMode.WEIGHT,
            weight = curls.copy(unit = WeightUnit.LB, repsPerSet = 6, startWeight = 1200, holds = listOf(WeightHold(1400, 8, 4))),
        )
        val row = entryEntity("Curls", 0, progression = p)
        assertEquals(
            listOf<Any?>("WEIGHT", "LB", "LIST", "2000:250:6000", "800,1000,1200,1400,1600", "1400:8:4", 6, 8, 12, 1200, null),
            listOf(
                row.progressMode, row.weightUnit, row.weightsKind, row.weightSteps, row.weightList, row.weightHolds,
                row.repsPerSet, row.repMin, row.repMax, row.startWeight, row.startReps,
            ),
        )
        assertEquals(p, row.toDomain().progression)
        assertNull(entryEntity("Burpees", 0).weightUnit)
    }

    @Test
    fun `a weight check-in row maps to a point with its load`() {
        assertEquals(
            CheckInPoint(Instant.ofEpochMilli(1_000), 15, 1400, 8, WeightUnit.KG),
            CheckInEntity(1, 7, 1_000, 15, 1400, 8, "KG").toPoint(),
        )
        val reps = CheckInEntity(2, 7, 1_000, 62).toPoint()
        assertEquals(listOf<Any?>(null, null, null), listOf(reps.weight, reps.reps, reps.unit))
    }
}
