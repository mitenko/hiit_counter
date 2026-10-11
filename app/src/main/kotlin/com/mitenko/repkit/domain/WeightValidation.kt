package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightsKind

/** A field of one weight-mode hold (spec rev 26 §3.1). */
enum class WeightHoldField { WEIGHT, REPS, FOR }

/**
 * WeightValidator's problems placed on the Progression page's fields (spec rev 26 §3.1, §10 note 7):
 * [errors] by field, [rowErrors] by My weights row, [holdErrors] by hold, and [holdHints] the holds
 * without errors that can never apply ("Hold disabled"). A draft with any error never saves (R3).
 */
data class WeightValidation(
    val errors: Map<WeightField, WeightProblem> = emptyMap(),
    val rowErrors: Map<Int, WeightProblem> = emptyMap(),
    val holdErrors: Map<Int, Map<WeightHoldField, WeightProblem>> = emptyMap(),
    val holdHints: Set<Int> = emptySet(),
) {
    val isValid: Boolean get() = errors.isEmpty() && rowErrors.isEmpty() && holdErrors.isEmpty()
}

/**
 * Validates [c] for [mode] and places each problem on its field; the first problem on a field wins.
 * Holds are checked whatever the Hold switch says, because setWeightConfig checks them (plan Spec note 32).
 */
fun weightValidation(c: WeightConfig, mode: ProgressMode): WeightValidation {
    val errors = mutableMapOf<WeightField, WeightProblem>()
    val rows = mutableMapOf<Int, WeightProblem>()
    val holds = mutableMapOf<Int, MutableMap<WeightHoldField, WeightProblem>>()
    fun hold(index: Int, field: WeightHoldField, p: WeightProblem) {
        holds.getOrPut(index, ::mutableMapOf).putIfAbsent(field, p)
    }
    WeightValidator.validate(c, mode).forEach { p ->
        when (p) {
            WeightProblem.TooFewWeights -> errors.putIfAbsent(WeightField.LIST, p)
            WeightProblem.TooManyWeights -> errors.putIfAbsent(if (c.kind == WeightsKind.STEPS) WeightField.STEPS_TOP else WeightField.LIST, p)
            is WeightProblem.WeightOutOfRange -> rows.putIfAbsent(p.index, p)
            is WeightProblem.DuplicateWeight -> rows.putIfAbsent(p.index, p)
            WeightProblem.StepsStart -> errors.putIfAbsent(WeightField.STEPS_START, p)
            WeightProblem.StepsStep -> errors.putIfAbsent(WeightField.STEPS_STEP, p)
            WeightProblem.StepsTop -> errors.putIfAbsent(WeightField.STEPS_TOP, p)
            WeightProblem.RepsPerSet -> errors.putIfAbsent(WeightField.REPS_PER_SET, p)
            WeightProblem.RepMin -> errors.putIfAbsent(WeightField.REP_MIN, p)
            WeightProblem.RepMax -> errors.putIfAbsent(WeightField.REP_MAX, p)
            WeightProblem.StartWeight -> errors.putIfAbsent(WeightField.START_WEIGHT, p)
            WeightProblem.StartReps -> errors.putIfAbsent(WeightField.START_REPS, p)
            WeightProblem.TooManyHolds -> errors.putIfAbsent(WeightField.HOLDS, p)
            is WeightProblem.HoldWeight -> hold(p.index, WeightHoldField.WEIGHT, p)
            is WeightProblem.DuplicateHold -> hold(p.index, WeightHoldField.WEIGHT, p)
            is WeightProblem.HoldReps -> hold(p.index, WeightHoldField.REPS, p)
            is WeightProblem.HoldFor -> hold(p.index, WeightHoldField.FOR, p)
        }
    }
    return WeightValidation(errors, rows, holds, inactiveHolds(c, mode, holds.keys))
}

/** Holds without errors that can never apply: held for 0, or on the top level (RepProgression never holds at the cap). */
private fun inactiveHolds(c: WeightConfig, mode: ProgressMode, withErrors: Set<Int>): Set<Int> {
    val weights = c.weights
    if (!mode.usesWeights || weights.isEmpty() || weights != weights.sorted().distinct() || c.repMin !in 1..c.repMax) return emptySet()
    val ladder = ladderOf(mode, c)
    return c.holds.indices.filter { i ->
        val h = c.holds[i]
        i !in withErrors && (h.forCount == 0 || ladder.levelOf(h.weight, h.reps) >= ladder.maxLevel)
    }.toSet()
}
