package com.mitenko.repkit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightConfigTest {
    @Test
    fun `the default steps expand to 20 to 60 in 2_5 steps`() {
        val w = WeightSteps.DEFAULT.expand()
        assertEquals(17, w.size)
        assertEquals(listOf(2000, 2250, 2500), w.take(3))
        assertEquals(6000, w.last())
    }

    @Test
    fun `steps that don't reach the top stop below it`() {
        assertEquals(listOf(2000, 2300, 2600), WeightSteps(2000, 300, 2800).expand())
    }

    @Test
    fun `steps that can't generate expand to nothing`() {
        listOf(WeightSteps(2000, 0, 6000), WeightSteps(0, 250, 6000), WeightSteps(6000, 250, 2000), WeightSteps(2000, -250, 6000))
            .forEach { assertEquals(it.toString(), emptyList<Int>(), it.expand()) }
    }

    @Test
    fun `weights come from the steps or from the list`() {
        val list = listOf(800, 1200, 1600)
        assertEquals(WeightSteps.DEFAULT.expand(), WeightConfig(list = list).weights)
        assertEquals(list, WeightConfig(kind = WeightsKind.LIST, list = list).weights)
    }

    @Test
    fun `a default progression is in Reps mode with the default weight settings`() {
        val p = ProgressionConfig()
        assertEquals(ProgressMode.REPS, p.mode)
        assertEquals(WeightConfig(), p.weight)
        val w = p.weight
        assertNull(w.unit)
        assertEquals(WeightsKind.STEPS, w.kind)
        assertEquals(listOf(10, 8, 12), listOf(w.repsPerSet, w.repMin, w.repMax))
        assertNull(w.startWeight)
        assertNull(w.startReps)
        assertTrue(w.holds.isEmpty())
    }

    @Test
    fun `only the two weight modes use weights`() {
        assertFalse(ProgressMode.REPS.usesWeights)
        assertTrue(ProgressMode.WEIGHT.usesWeights)
        assertTrue(ProgressMode.REPS_THEN_WEIGHT.usesWeights)
    }
}
