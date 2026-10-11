package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightDraftTest {
    private val bells = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200, 1600))

    @Test
    fun `a new list weight is the last plus the last gap`() {
        assertEquals(2000, nextListWeight(listOf(800, 1200, 1600)))
        assertEquals(2500, nextListWeight(listOf(2250, 2000, 1600))) // unsorted input: 2250 + 250
        assertEquals(1050, nextListWeight(listOf(800))) // one weight: the default step, 2.5
        assertEquals(2000, nextListWeight(emptyList())) // none: the default start, 20
        assertEquals(99_975, nextListWeight(listOf(99_000, 99_900))) // kept at most 999.75
    }

    @Test
    fun `editing a list weight keeps the list ascending`() {
        assertEquals(listOf(1200, 1600, 2050), bells.withListWeight(0, 2050).list)
        assertEquals(listOf(800, 1600), bells.withoutListWeight(1).list)
        assertEquals(listOf(800, 1200, 1600, 2000), bells.withNewListWeight().list)
    }

    @Test
    fun `pickable weights are sorted without duplicates`() {
        assertEquals(listOf(800, 1200), WeightConfig(kind = WeightsKind.LIST, list = listOf(1200, 800, 800)).pickable)
        assertEquals(WeightSteps.DEFAULT.expand(), WeightConfig().pickable)
    }

    @Test
    fun `switching to My weights with an empty list seeds it from the steps`() {
        val steps = WeightConfig(steps = WeightSteps(2000, 500, 3000))
        assertEquals(WeightConfig(steps = steps.steps, kind = WeightsKind.LIST, list = listOf(2000, 2500, 3000)), steps.withKind(WeightsKind.LIST))
        assertEquals(bells.copy(kind = WeightsKind.STEPS), bells.withKind(WeightsKind.STEPS)) // the list is kept
        assertEquals(bells, bells.copy(kind = WeightsKind.STEPS).withKind(WeightsKind.LIST)) // a kept list isn't reseeded
        assertEquals(40, WeightConfig(steps = WeightSteps(100, 100, 9000)).withKind(WeightsKind.LIST).list.size) // at most 40
    }

    @Test
    fun `a new weight hold goes above the last hold, never on the heaviest`() {
        // No holds yet: above the starting weight (the lightest), at the lowest reps, for 4.
        assertEquals(WeightHold(1200, 8, 4), newWeightHold(ProgressMode.WEIGHT, bells))
        val one = bells.copy(holds = listOf(WeightHold(1200, 8, 3)))
        // Above 1200 is only the heaviest, so the first free weight below it: 800.
        assertEquals(WeightHold(800, 8, 3), newWeightHold(ProgressMode.WEIGHT, one))
        val full = bells.copy(holds = listOf(WeightHold(800, 8, 2), WeightHold(1200, 8, 2)))
        assertEquals(WeightHold(800, 8, 2), newWeightHold(ProgressMode.WEIGHT, full)) // none free: added anyway, for the validator
        assertEquals(listOf(WeightHold(1200, 8, 4)), bells.withNewWeightHold(ProgressMode.REPS_THEN_WEIGHT).holds)
    }

    @Test
    fun `a new Reps then weight hold counts a weight at other reps as free`() {
        val held = bells.copy(holds = listOf(WeightHold(1200, 10, 3)))
        // 1200 × 8 is free (the hold is at 10 reps), but nothing free is above the last hold's 1200,
        // so the first free weight wins: 800.
        assertEquals(WeightHold(800, 8, 3), newWeightHold(ProgressMode.REPS_THEN_WEIGHT, held))
        // In Weight mode the same hold takes 1200 whatever its reps, and the answer is the same.
        assertEquals(WeightHold(800, 8, 3), newWeightHold(ProgressMode.WEIGHT, held))
    }

    @Test
    fun `hold edits replace or remove one hold`() {
        val c = bells.copy(holds = listOf(WeightHold(800, 8, 2), WeightHold(1200, 8, 2)))
        assertEquals(listOf(WeightHold(800, 8, 2), WeightHold(1200, 8, 5)), c.withWeightHold(1) { it.copy(forCount = 5) }.holds)
        assertEquals(listOf(WeightHold(1200, 8, 2)), c.withoutWeightHold(0).holds)
    }

    @Test
    fun `stepping Start or Top moves one step and stays inside 0,01 to 999,75`() {
        assertEquals(2250, stepWeight(2000, 250, up = true))
        assertEquals(1750, stepWeight(2000, 250, up = false))
        assertEquals(100, stepWeight(100, 250, up = false)) // would go below 0.01: unchanged
        assertEquals(99_900, stepWeight(99_900, 250, up = true)) // would pass 999.75: unchanged
    }

    @Test
    fun `stepping along a list stops at the ends and snaps a value that isn't on it`() {
        val options = listOf(800, 1200, 1600)
        assertEquals(1600, stepAlong(options, 1200, up = true))
        assertEquals(1600, stepAlong(options, 1600, up = true))
        assertEquals(800, stepAlong(options, 800, up = false))
        assertEquals(1200, stepAlong(options, 1000, up = true))
        assertEquals(800, stepAlong(options, 1000, up = false))
        assertEquals(800, stepAlong(options, null, up = true))
        assertNull(stepAlong(emptyList(), null, up = true))
        assertEquals(500, stepAlong(WeightValidator.STEP_CHOICES, 250, up = true))
        assertEquals(50, stepAlong(WeightValidator.STEP_CHOICES, 50, up = false))
    }

    @Test
    fun `a typed Top snaps down to the start plus whole steps, at least one step up`() {
        assertEquals(5850, snapTop(5900, 2100, 250))
        assertEquals(6000, snapTop(6000, 2000, 250))
        assertEquals(2250, snapTop(2100, 2000, 250)) // less than a step above: one step
        assertEquals(1500, snapTop(1500, 2000, 250)) // not above the start: left for the Top override
        assertEquals(5900, snapTop(5900, 2000, 300)) // not a step choice: left for the validator
    }
}
