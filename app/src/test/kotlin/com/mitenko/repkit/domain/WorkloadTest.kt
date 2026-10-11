package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.testutil.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkloadTest {
    /** The curls ladder from the spec's simulation: 8 / 10 / 12 / 14 / 16 kg, 10 reps per set, 8–12 reps. */
    private val curls = WeightConfig(
        unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600),
        repsPerSet = 10, repMin = 8, repMax = 12,
    )
    private val weight = ProgressionConfig(mode = ProgressMode.WEIGHT, weight = curls)
    private val repsThenWeight = ProgressionConfig(mode = ProgressMode.REPS_THEN_WEIGHT, weight = curls)
    private val three = TimingConfig(sets = 3)

    @Test
    fun `Reps mode splits the total exactly as RepDistributor does`() {
        assertEquals(RepDistributor.distribute(65, 8), ProgressionConfig().setReps(65, 8))
        val e = testEntry(1, counter = CounterState(total = 65))
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), e.setReps())
        assertNull(e.load())
        assertNull(e.setWeight())
        assertEquals(65, e.nextWorkoutReps())
        assertEquals(ProgressMode.REPS, e.historyMode)
    }

    @Test
    fun `a weight mode repeats the level's reps per set`() {
        assertEquals(listOf(10, 10, 10), weight.setReps(4, 3))
        // Reps then weight: level 22 is 16 kg (22 / 5 = 4) × 10 (8 + 22 % 5).
        assertEquals(listOf(10, 10, 10), repsThenWeight.setReps(22, 3))
        val e = testEntry(1, timing = three, progression = repsThenWeight, counter = CounterState(total = 22))
        assertEquals(Prescription.Load(1600, 10), e.load())
        assertEquals(SetWeight(1600, WeightUnit.KG), e.setWeight())
        assertEquals(SetWeight(1400, WeightUnit.KG), e.setWeight(level = 15))
        assertEquals(30, e.nextWorkoutReps())
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, e.historyMode)
    }

    @Test
    fun `a weight workout with no unit reads as kg`() {
        val e = testEntry(1, progression = weight.copy(weight = curls.copy(unit = null)), counter = CounterState(total = 0))
        assertEquals(SetWeight(800, WeightUnit.KG), e.setWeight())
    }

    @Test
    fun `a level at the top and above it clamps to the heaviest weight and repMax`() {
        // Batch 1 review finding 4: the top level (4) and anything above it both read as 16 kg x 10.
        assertEquals(Prescription.Load(1600, 10), weight.loadAt(4))
        assertEquals(Prescription.Load(1600, 10), weight.loadAt(10))
        val e = testEntry(1, timing = three, progression = weight, counter = CounterState(total = 10))
        assertEquals(listOf(10, 10, 10), e.setReps())
        assertEquals(SetWeight(1600, WeightUnit.KG), e.setWeight())
        assertEquals(30, e.nextWorkoutReps())
        // Reps then weight clamps to its own top level (weights.size * span - 1 = 24): 16 kg x 12.
        val topLevel = repsThenWeight.scale().maxLevel
        assertEquals(Prescription.Load(1600, 12), repsThenWeight.loadAt(topLevel))
        assertEquals(Prescription.Load(1600, 12), repsThenWeight.loadAt(topLevel + 5))
    }

    @Test
    fun `a Weight-mode entry's next workout reps are sets times the fixed reps per set`() {
        val e = testEntry(1, timing = three, progression = weight, counter = CounterState(total = 4))
        assertEquals(30, e.nextWorkoutReps())
        assertEquals(ProgressMode.WEIGHT, e.historyMode)
    }

    @Test
    fun `setReps, load and setWeight can be built against a different progression than the entry's own (M1)`() {
        // A Reps-then-weight entry, but the run is built against the plain Weight ladder instead.
        val e = testEntry(1, timing = three, progression = repsThenWeight, counter = CounterState(total = 22))
        assertEquals(listOf(10, 10, 10), e.setReps(weight, 4)) // weight's own level 4 reps, not RTW's level 22
        assertEquals(Prescription.Load(1600, 10), e.load(weight, 4))
        assertEquals(SetWeight(1600, WeightUnit.KG), e.setWeight(weight, 4))
        // The set count still comes from the entry's own (frozen) timing, not a param.
        assertEquals(3, e.setReps(weight, 4).size)
    }

    @Test
    fun `Timer only keeps today's split and has no load, whatever its stored mode`() {
        val e = testEntry(1, timing = three, progression = weight, counter = CounterState(total = 4), type = EntryType.CHECK_IN)
        assertEquals(RepDistributor.distribute(4, 3), e.setReps())
        assertNull(e.load())
        assertNull(e.setWeight())
        assertEquals(4, e.nextWorkoutReps())
        assertEquals(ProgressMode.REPS, e.historyMode)
    }
}
