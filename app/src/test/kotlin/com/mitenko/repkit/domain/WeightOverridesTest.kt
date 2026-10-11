package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WeightOverridesTest {
    private val rtw = ProgressMode.REPS_THEN_WEIGHT
    private val steps = WeightConfig(steps = WeightSteps(2000, 250, 3000))

    private fun edit(before: WeightConfig, field: WeightField?, mode: ProgressMode = rtw, transform: (WeightConfig) -> WeightConfig) =
        resolveWeightEdit(mode, before, transform(before), field)

    @Test
    fun `a start raised past the top raises the top one step above it`() {
        val r = edit(steps, WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = 3250)) }
        assertEquals(WeightSteps(3250, 250, 3500), r.config.steps)
        assertEquals(listOf(WeightMove.TopRaised(3500)), r.moves)
    }

    @Test
    fun `a start off the step grid lowers the top to whole steps`() {
        val r = edit(WeightConfig(steps = WeightSteps(2000, 250, 6000)), WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = 2100)) }
        assertEquals(WeightSteps(2100, 250, 5850), r.config.steps)
        assertEquals(listOf(WeightMove.TopLowered(5850)), r.moves)
    }

    @Test
    fun `a step that leaves the top unreachable lowers it`() {
        val r = edit(WeightConfig(steps = WeightSteps(2000, 250, 2750)), WeightField.STEPS_STEP) { it.copy(steps = it.steps.copy(step = 500)) }
        assertEquals(WeightSteps(2000, 500, 2500), r.config.steps)
        assertEquals(listOf(WeightMove.TopLowered(2500)), r.moves)
    }

    @Test
    fun `a top lowered to the start lowers the start one step`() {
        val r = edit(steps, WeightField.STEPS_TOP) { it.copy(steps = it.steps.copy(top = 2000)) }
        assertEquals(WeightSteps(1750, 250, 2000), r.config.steps)
        assertEquals(listOf(WeightMove.StartLowered(1750)), r.moves)
    }

    @Test
    fun `nothing moves when it can't be made valid`() {
        // The start would go below 0.01.
        val tiny = WeightConfig(steps = WeightSteps(100, 250, 350))
        assertEquals(emptyList<WeightMove>(), edit(tiny, WeightField.STEPS_TOP) { it.copy(steps = it.steps.copy(top = 200)) }.moves)
        // The top would pass 999.75.
        val high = WeightConfig(steps = WeightSteps(99_000, 500, 99_500))
        assertEquals(emptyList<WeightMove>(), edit(high, WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = 99_750)) }.moves)
    }

    @Test
    fun `a minimum at the maximum raises the maximum, then explicit starting reps follow`() {
        val c = WeightConfig(repMin = 8, repMax = 12, startReps = 9)
        val r = edit(c, WeightField.REP_MIN) { it.copy(repMin = 12) }
        assertEquals(listOf(12, 13, 12), listOf(r.config.repMin, r.config.repMax, r.config.startReps))
        assertEquals(listOf(WeightMove.RepMaxRaised(13), WeightMove.StartRepsRaised(12)), r.moves)
    }

    @Test
    fun `a maximum at the minimum lowers the minimum, then explicit starting reps follow`() {
        val c = WeightConfig(repMin = 8, repMax = 12, startReps = 11)
        val r = edit(c, WeightField.REP_MAX) { it.copy(repMax = 8) }
        assertEquals(listOf(7, 8, 8), listOf(r.config.repMin, r.config.repMax, r.config.startReps))
        assertEquals(listOf(WeightMove.RepMinLowered(7), WeightMove.StartRepsLowered(8)), r.moves)
    }

    @Test
    fun `starting reps outside the range move the range`() {
        val c = WeightConfig(repMin = 8, repMax = 12)
        assertEquals(listOf(WeightMove.RepMaxRaised(14)), edit(c, WeightField.START_REPS) { it.copy(startReps = 14) }.moves)
        val low = edit(c, WeightField.START_REPS) { it.copy(startReps = 6) }
        assertEquals(6, low.config.repMin)
        assertEquals(listOf(WeightMove.RepMinLowered(6)), low.moves)
    }

    @Test
    fun `untouched starting reps follow the minimum without a note`() {
        assertEquals(emptyList<WeightMove>(), edit(WeightConfig(), WeightField.REP_MIN) { it.copy(repMin = 10) }.moves)
    }

    @Test
    fun `without an edited field nothing moves`() {
        val bad = WeightConfig(repMin = 12, repMax = 12)
        val r = resolveWeightEdit(rtw, bad, bad, null)
        assertSame(bad, r.config)
        assertEquals(emptyList<WeightMove>(), r.moves)
    }

    @Test
    fun `removing the starting weight moves it down and drops a hold on a removed weight`() {
        val c = WeightConfig(
            kind = WeightsKind.LIST, list = listOf(800, 1200, 1600), startWeight = 1200,
            holds = listOf(WeightHold(1200, 8, 2), WeightHold(1600, 8, 2)),
        )
        val r = edit(c, WeightField.LIST) { it.withoutListWeight(1) }
        assertEquals(800, r.config.startWeight)
        assertEquals(listOf(WeightHold(1600, 8, 2)), r.config.holds)
        assertEquals(listOf(WeightMove.StartWeightMoved(800), WeightMove.HoldsRemoved(1)), r.moves)
    }

    @Test
    fun `an untouched starting weight is never noted`() {
        val c = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200, 1600))
        val r = edit(c, WeightField.LIST) { it.withoutListWeight(0) }
        assertNull(r.config.startWeight)
        assertEquals(emptyList<WeightMove>(), r.moves)
    }

    @Test
    fun `a list with a duplicate waits for a clean list before remapping`() {
        val c = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200), startWeight = 1200)
        val r = edit(c, WeightField.LIST) { it.withListWeight(1, 800) }
        assertEquals(1200, r.config.startWeight)
        assertEquals(emptyList<WeightMove>(), r.moves)
    }

    @Test
    fun `in Weight mode two holds that land on one weight keep the first`() {
        val c = WeightConfig(
            kind = WeightsKind.LIST, list = listOf(800, 1200, 1600),
            holds = listOf(WeightHold(1200, 8, 2), WeightHold(1200, 9, 2)),
        )
        val r = edit(c, WeightField.LIST, ProgressMode.WEIGHT) { it.withNewListWeight() }
        assertEquals(listOf(WeightHold(1200, 8, 2)), r.config.holds)
        assertEquals(listOf(WeightMove.HoldsRemoved(1)), r.moves)
    }

    @Test
    fun `a moved current weight is noted by weight in Weight mode and by load in Reps then weight`() {
        val old = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200, 1600))
        val new = old.copy(list = listOf(800, 1600))
        assertEquals(WeightMove.CurrentMoved(800, null), currentLoadMove(ProgressMode.WEIGHT, old, new, oldLevel = 1, newLevel = 0))
        assertNull(currentLoadMove(ProgressMode.WEIGHT, old, old.copy(repsPerSet = 12), 1, 1)) // the reps-per-set edit itself
        val range = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200), repMin = 8, repMax = 12)
        // Level 7 is 1200 × 10; with 8–9 reps it lands on 1200 × 9 (level 3).
        assertEquals(WeightMove.CurrentMoved(1200, 9), currentLoadMove(rtw, range, range.copy(repMax = 9), oldLevel = 7, newLevel = 3))
        assertNull(currentLoadMove(rtw, range, range, 7, 7))
    }
}
