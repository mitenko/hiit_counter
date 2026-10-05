package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.WeightProblem.*
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightValidatorTest {
    private val rtw = ProgressMode.REPS_THEN_WEIGHT
    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600))

    private fun problems(c: WeightConfig, mode: ProgressMode = rtw) = WeightValidator.validate(c, mode)

    @Test
    fun `the defaults and the curls ladder are valid in every mode`() {
        for (mode in ProgressMode.entries) {
            assertEquals(mode.name, emptyList<WeightProblem>(), problems(WeightConfig(), mode))
            assertEquals(mode.name, emptyList<WeightProblem>(), problems(curls, mode))
        }
    }

    @Test
    fun `My weights needs 2 to 40 weights in range with no duplicates`() {
        assertEquals(listOf(TooFewWeights), problems(curls.copy(list = listOf(800))))
        assertEquals(listOf(TooManyWeights), problems(curls.copy(list = (1..41).map { it * 100 })))
        assertEquals(listOf(WeightOutOfRange(1)), problems(curls.copy(list = listOf(800, 0))))
        assertEquals(listOf(WeightOutOfRange(1)), problems(curls.copy(list = listOf(800, 100_000))))
        assertEquals(listOf(DuplicateWeight(2)), problems(curls.copy(list = listOf(800, 1200, 800))))
    }

    @Test
    fun `Steps need a positive start, an offered step and a top the step divides`() {
        fun steps(start: Int, step: Int, top: Int) = problems(WeightConfig(steps = WeightSteps(start, step, top)))
        assertEquals(listOf(StepsStart), steps(0, 250, 6000))
        assertEquals(listOf(StepsStep), steps(2000, 300, 6000))
        assertEquals(listOf(StepsTop), steps(2000, 250, 6100))
        assertEquals(listOf(StepsTop), steps(2000, 250, 2000))
        assertEquals(listOf(StepsTop), steps(2000, 250, 100_000))
        assertEquals(listOf(TooManyWeights), steps(50, 50, 5000))
        WeightValidator.STEP_CHOICES.forEach { assertEquals(emptyList<WeightProblem>(), steps(1000, it, 1000 + 4 * it)) }
    }

    @Test
    fun `reps per set and the rep range are 1 to 100 with min below max`() {
        assertEquals(listOf(RepsPerSet), problems(curls.copy(repsPerSet = 0)))
        assertEquals(listOf(RepsPerSet), problems(curls.copy(repsPerSet = 101)))
        assertEquals(listOf(RepMin), problems(curls.copy(repMin = 0)))
        assertEquals(listOf(RepMax), problems(curls.copy(repMin = 12, repMax = 12)))
        assertEquals(listOf(RepMax), problems(curls.copy(repMax = 101)))
    }

    @Test
    fun `the starting weight is on the ladder and the starting reps in the range`() {
        assertEquals(listOf(StartWeight), problems(curls.copy(startWeight = 900)))
        assertEquals(listOf(StartReps), problems(curls.copy(startReps = 13)))
        assertEquals(emptyList<WeightProblem>(), problems(curls.copy(startWeight = 1200, startReps = 12)))
    }

    @Test
    fun `each hold is on the ladder, in the range, held for 0 or more, and in its own place`() {
        assertEquals(listOf(HoldWeight(0)), problems(curls.copy(holds = listOf(WeightHold(900, 8, 4)))))
        assertEquals(listOf(HoldReps(0)), problems(curls.copy(holds = listOf(WeightHold(1200, 13, 4)))))
        assertEquals(listOf(HoldFor(0)), problems(curls.copy(holds = listOf(WeightHold(1200, 8, -1)))))
        val sameWeight = curls.copy(holds = listOf(WeightHold(1200, 8, 4), WeightHold(1200, 10, 2)))
        assertEquals(emptyList<WeightProblem>(), problems(sameWeight, rtw))
        assertEquals(listOf(DuplicateHold(1)), problems(sameWeight, ProgressMode.WEIGHT))
        val nine = curls.copy(holds = (0 until 9).map { WeightHold(curls.list[it % 5], 8 + it / 5, 1) })
        assertEquals(listOf(TooManyHolds), problems(nine))
    }

    @Test
    fun `a draft with one bad row fails, and fixing that row passes`() {
        val draft = curls.copy(list = listOf(800, 1000, 1000, 1600))
        assertEquals(listOf(DuplicateWeight(2)), problems(draft))
        assertEquals(emptyList<WeightProblem>(), problems(draft.copy(list = listOf(800, 1000, 1200, 1600))))
    }
}
