package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec rev 26 §9.2 on the curls ladder, 8/10/12/14/16 kg × 8–12. */
class WeightRemapTest {
    private val rtw = ProgressMode.REPS_THEN_WEIGHT
    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600))

    private fun without(vararg weights: Int) = curls.copy(list = curls.list - weights.toSet())

    private fun levelOf(c: WeightConfig, weight: Int, reps: Int, mode: ProgressMode = rtw) = ladderOf(mode, c).levelOf(weight, reps)

    private fun load(c: WeightConfig, level: Int, mode: ProgressMode = rtw) = ladderOf(mode, c).prescription(level)

    @Test
    fun `removing the current weight moves to the nearest lower weight`() {
        val r = remapWeights(rtw, curls, without(1200), oldLevel = levelOf(curls, 1200, 10))
        assertEquals(7, r.level)
        assertEquals(Prescription.Load(1000, 10), load(r.config, r.level!!))
        assertTrue(r.currentChanged)
    }

    @Test
    fun `removing a weight below or above keeps the current weight`() {
        val at = levelOf(curls, 1200, 10)
        val below = remapWeights(rtw, curls, without(800), at)
        assertEquals(7 to false, below.level to below.currentChanged)
        assertEquals(Prescription.Load(1200, 10), load(below.config, 7))
        val above = remapWeights(rtw, curls, without(1600), at)
        assertEquals(12 to false, above.level to above.currentChanged)
    }

    @Test
    fun `removing the lightest while on it moves to the new lightest`() {
        val r = remapWeights(rtw, curls, without(800), oldLevel = 0)
        assertEquals(0, r.level)
        assertEquals(Prescription.Load(1000, 8), load(r.config, 0))
        assertTrue(r.currentChanged)
    }

    @Test
    fun `a step change that skips the current value goes to the nearest lower weight`() {
        val old = WeightConfig(unit = WeightUnit.KG)
        val new = old.copy(steps = WeightSteps(2000, 500, 6000))
        val r = remapWeights(rtw, old, new, levelOf(old, 2250, 9))
        assertEquals(Prescription.Load(2000, 9), load(new, r.level!!))
        assertTrue(r.currentChanged)
    }

    @Test
    fun `a shrinking rep range clamps the reps`() {
        val top = remapWeights(rtw, curls, curls.copy(repMax = 10), levelOf(curls, 1200, 12))
        assertEquals(8, top.level)
        assertEquals(Prescription.Load(1200, 10), load(curls.copy(repMax = 10), 8))
        assertTrue(top.currentChanged)
        val bottom = remapWeights(rtw, curls, curls.copy(repMin = 9), levelOf(curls, 1200, 8))
        assertEquals(Prescription.Load(1200, 9), load(curls.copy(repMin = 9), bottom.level!!))
    }

    @Test
    fun `an exact match changes nothing`() {
        val r = remapWeights(rtw, curls, curls, 12)
        assertEquals(WeightRemapResult(curls, 12, currentChanged = false), r)
    }

    @Test
    fun `a hold on a removed weight is dropped and the others are kept`() {
        val draft = without(1200).copy(holds = listOf(WeightHold(1200, 8, 4), WeightHold(1400, 8, 4)))
        assertEquals(listOf(WeightHold(1400, 8, 4)), remapWeights(rtw, curls, draft, null).config.holds)
    }

    @Test
    fun `two holds that collide after a remap keep the first`() {
        val draft = curls.copy(repMax = 10, holds = listOf(WeightHold(1200, 11, 2), WeightHold(1200, 12, 3)))
        assertEquals(listOf(WeightHold(1200, 10, 2)), remapWeights(rtw, curls, draft, null).config.holds)
    }

    @Test
    fun `Weight mode remaps by weight alone`() {
        val weight = ProgressMode.WEIGHT
        val r = remapWeights(weight, curls, without(1200), oldLevel = 2)
        assertEquals(1, r.level)
        assertEquals(Prescription.Load(1000, 10), load(r.config, 1, weight))
        val holds = curls.copy(holds = listOf(WeightHold(1400, 8, 4), WeightHold(1400, 10, 2)))
        assertEquals(listOf(WeightHold(1400, 8, 4)), remapWeights(weight, curls, holds, null).config.holds)
    }

    @Test
    fun `an untouched counter stays untouched and the starting point follows its weight`() {
        val draft = without(1200).copy(startWeight = 1200, startReps = 12, repMax = 10)
        val r = remapWeights(rtw, curls, draft, oldLevel = null)
        assertNull(r.level)
        assertFalse(r.currentChanged)
        assertEquals(1000 to 10, r.config.startWeight to r.config.startReps)
    }

    @Test
    fun `a unit change converted first keeps the same rung`() {
        val lb = WeightConversion.convert(curls, WeightUnit.LB)
        assertEquals(listOf(1775, 2200, 2650, 3075, 3525), lb.list)
        val r = remapWeights(rtw, lb, lb, 12)
        assertEquals(12 to false, r.level to r.currentChanged)
        assertEquals(Prescription.Load(2650, 10), load(lb, 12))
    }

    @Test
    fun `the hold count resets only when the load, the holds or the active holds change`() {
        val held = curls.copy(holds = listOf(WeightHold(1400, 8, 4)))
        val at = levelOf(curls, 1200, 10)
        // Same everything.
        assertFalse(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, held, at)))
        // A lighter weight removed: same load, same holds, still active.
        val lighter = held.copy(list = held.list - 800)
        assertFalse(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, lighter, at)))
        // The current weight removed.
        assertTrue(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, held.copy(list = held.list - 1200), at)))
        // A hold added.
        val added = held.copy(holds = held.holds + WeightHold(1600, 8, 2))
        assertTrue(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, added, at)))
        // A hold dropped with its weight.
        assertTrue(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, held.copy(list = held.list - 1400), 0)))
        // The heaviest removed: 14 kg × 12 becomes the top level, where a hold can't apply.
        val topHold = curls.copy(holds = listOf(WeightHold(1400, 12, 4)))
        assertTrue(weightHoldResetNeeded(rtw, topHold, remapWeights(rtw, topHold, topHold.copy(list = topHold.list - 1600), 0)))
    }
}
