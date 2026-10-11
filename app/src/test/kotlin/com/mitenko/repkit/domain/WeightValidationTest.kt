package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightValidationTest {
    private val rtw = ProgressMode.REPS_THEN_WEIGHT

    @Test
    fun `the defaults are valid`() {
        assertTrue(weightValidation(WeightConfig(), ProgressMode.WEIGHT).isValid)
        assertEquals(WeightValidation(), weightValidation(WeightConfig(), rtw))
    }

    @Test
    fun `list problems go on their rows, and the count on the list`() {
        val v = weightValidation(WeightConfig(kind = WeightsKind.LIST, list = listOf(0, 800, 800)), rtw)
        assertEquals(mapOf(0 to WeightProblem.WeightOutOfRange(0), 2 to WeightProblem.DuplicateWeight(2)), v.rowErrors)
        assertFalse(v.isValid)
        val one = weightValidation(WeightConfig(kind = WeightsKind.LIST, list = listOf(800)), rtw)
        assertEquals(WeightProblem.TooFewWeights, one.errors[WeightField.LIST])
    }

    @Test
    fun `steps problems go on start, step and top, and too many steps on top`() {
        val v = weightValidation(WeightConfig(steps = WeightSteps(0, 300, 0)), rtw)
        assertEquals(WeightProblem.StepsStart, v.errors[WeightField.STEPS_START])
        assertEquals(WeightProblem.StepsStep, v.errors[WeightField.STEPS_STEP])
        assertEquals(WeightProblem.StepsTop, v.errors[WeightField.STEPS_TOP])
        val many = weightValidation(WeightConfig(steps = WeightSteps(100, 100, 5000)), rtw)
        assertEquals(WeightProblem.TooManyWeights, many.errors[WeightField.STEPS_TOP])
    }

    @Test
    fun `rep, start and hold problems go on their fields`() {
        val c = WeightConfig(
            repsPerSet = 0, repMin = 12, repMax = 12, startWeight = 2100, startReps = 20,
            holds = listOf(WeightHold(2100, 8, -1), WeightHold(2000, 30, 2)),
        )
        val v = weightValidation(c, rtw)
        assertEquals(WeightProblem.RepsPerSet, v.errors[WeightField.REPS_PER_SET])
        assertEquals(WeightProblem.RepMax, v.errors[WeightField.REP_MAX])
        assertEquals(WeightProblem.StartWeight, v.errors[WeightField.START_WEIGHT])
        assertEquals(WeightProblem.StartReps, v.errors[WeightField.START_REPS])
        assertEquals(WeightProblem.HoldWeight(0), v.holdErrors[0]!![WeightHoldField.WEIGHT])
        assertEquals(WeightProblem.HoldFor(0), v.holdErrors[0]!![WeightHoldField.FOR])
        assertEquals(WeightProblem.HoldReps(1), v.holdErrors[1]!![WeightHoldField.REPS])
    }

    @Test
    fun `a duplicate hold and too many holds are flagged`() {
        val dup = WeightConfig(holds = listOf(WeightHold(2000, 8, 2), WeightHold(2000, 9, 2)))
        assertEquals(WeightProblem.DuplicateHold(1), weightValidation(dup, ProgressMode.WEIGHT).holdErrors[1]!![WeightHoldField.WEIGHT])
        assertTrue(weightValidation(dup, rtw).isValid) // different positions in Reps then weight (§10 note 10)
        val nine = WeightConfig(holds = List(9) { WeightHold(2000 + it * 250, 8, 2) })
        assertEquals(WeightProblem.TooManyHolds, weightValidation(nine, rtw).errors[WeightField.HOLDS])
    }

    @Test
    fun `a hold held for 0 or on the top level is hinted, not an error`() {
        val c = WeightConfig(steps = WeightSteps(2000, 250, 2500), holds = listOf(WeightHold(2000, 8, 0), WeightHold(2500, 12, 3), WeightHold(2250, 8, 3)))
        val v = weightValidation(c, rtw)
        assertTrue(v.isValid)
        assertEquals(setOf(0, 1), v.holdHints)
        // In Weight mode the top level is the heaviest weight, whatever the reps.
        assertEquals(setOf(0, 1), weightValidation(c.copy(holds = c.holds.map { it.copy(reps = 8) }), ProgressMode.WEIGHT).holdHints)
    }
}
