package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind

/** The weight-mode Progression fields (spec rev 26 §3): the field an edit came from, for overrides and notes. */
enum class WeightField { UNIT, KIND, STEPS_START, STEPS_STEP, STEPS_TOP, LIST, REPS_PER_SET, REP_MIN, REP_MAX, START_WEIGHT, START_REPS, HOLDS }

/**
 * A value that moved to fit a weight-mode edit (revision 28 §6 applied to rev 26, plan Spec note 30).
 * Weights are hundredths of the workout's unit; the UI turns each move into a note.
 */
sealed interface WeightMove {
    data class TopRaised(val to: Int) : WeightMove

    data class TopLowered(val to: Int) : WeightMove

    /** The Steps start, lowered by a Top edit. */
    data class StartLowered(val to: Int) : WeightMove

    data class RepMaxRaised(val to: Int) : WeightMove

    data class RepMinLowered(val to: Int) : WeightMove

    data class StartRepsRaised(val to: Int) : WeightMove

    data class StartRepsLowered(val to: Int) : WeightMove

    /** An explicit starting weight remapped because its weight left the list (§9.2). */
    data class StartWeightMoved(val to: Int) : WeightMove

    /** Holds dropped because their weight left the list, or collided on one position (§9.2 step 4). */
    data class HoldsRemoved(val count: Int) : WeightMove

    /** The stored current load after a save remapped it (plan Spec note 31); [reps] is null in Weight mode. */
    data class CurrentMoved(val weight: Int, val reps: Int?) : WeightMove
}

data class WeightResolution(val config: WeightConfig, val moves: List<WeightMove>)

/**
 * The field you edit wins (revision 28 §6, plan Spec note 30): [after] is [before] with the user's
 * edit to [edited]. The Steps and the rep range move to fit, then a ladder or range change remaps
 * the starting weight and the holds by value (§9.2). With no edited field only the remap runs. Every
 * value that moved is in the moves, in the order it moved.
 */
fun resolveWeightEdit(mode: ProgressMode, before: WeightConfig, after: WeightConfig, edited: WeightField?): WeightResolution {
    val moves = mutableListOf<WeightMove>()
    var c = after
    if (c.kind == WeightsKind.STEPS) {
        val steps = resolveSteps(c.steps, edited, moves)
        if (steps != c.steps) c = c.copy(steps = steps) // unchanged returns the same instance
    }
    c = resolveReps(c, edited, moves)
    c = remapDraft(mode, before, c, moves)
    return WeightResolution(c, moves)
}

/** Start or Step edited: the top goes to start + whole steps. Top edited at or below the start: the start goes one step under it. */
private fun resolveSteps(s: WeightSteps, edited: WeightField?, moves: MutableList<WeightMove>): WeightSteps {
    if (s.step !in WeightValidator.STEP_CHOICES || s.start !in 1..WeightConfig.MAX_WEIGHT) return s
    return when (edited) {
        WeightField.STEPS_START, WeightField.STEPS_STEP -> {
            val top = s.start + ((s.top - s.start) / s.step).coerceAtLeast(1) * s.step
            when {
                top > WeightConfig.MAX_WEIGHT || top == s.top -> s
                top > s.top -> s.copy(top = top).also { moves += WeightMove.TopRaised(top) }
                else -> s.copy(top = top).also { moves += WeightMove.TopLowered(top) }
            }
        }
        WeightField.STEPS_TOP -> {
            val start = s.top - s.step
            if (s.top > s.start || start < 1) s else s.copy(start = start).also { moves += WeightMove.StartLowered(start) }
        }
        else -> s
    }
}

/** Revision 28 rules 1–3, per set: the edited one of min, max and starting reps wins. Untouched (null) starting reps follow the minimum. */
private fun resolveReps(start: WeightConfig, edited: WeightField?, moves: MutableList<WeightMove>): WeightConfig {
    var c = start
    val max = WeightConfig.MAX_REPS
    when (edited) {
        WeightField.REP_MIN -> if (c.repMin in 1..max) {
            if (c.repMax <= c.repMin && c.repMin < max) {
                c = c.copy(repMax = c.repMin + 1)
                moves += WeightMove.RepMaxRaised(c.repMax)
            }
            val reps = c.startReps
            if (reps != null && reps < c.repMin) {
                c = c.copy(startReps = c.repMin)
                moves += WeightMove.StartRepsRaised(c.repMin)
            }
        }
        WeightField.REP_MAX -> if (c.repMax in 1..max) {
            if (c.repMin >= c.repMax && c.repMax > 1) {
                c = c.copy(repMin = c.repMax - 1)
                moves += WeightMove.RepMinLowered(c.repMin)
            }
            val reps = c.startReps
            if (reps != null && reps > c.repMax) {
                c = c.copy(startReps = c.repMax)
                moves += WeightMove.StartRepsLowered(c.repMax)
            }
        }
        WeightField.START_REPS -> {
            val reps = c.startReps
            if (reps != null && reps in 1..max) {
                if (reps > c.repMax) {
                    c = c.copy(repMax = reps)
                    moves += WeightMove.RepMaxRaised(reps)
                } else if (reps < c.repMin) {
                    c = c.copy(repMin = reps)
                    moves += WeightMove.RepMinLowered(reps)
                }
            }
        }
        else -> Unit
    }
    return c
}

/**
 * §9.2 in the draft: when the ladder or the rep range changed, the starting weight and the holds are
 * remapped by value at once, so the pickers never offer a removed weight. A list that isn't clean yet
 * (empty, or with a duplicate) or an invalid range waits; the validator flags it. Reps mode remaps
 * as Reps then weight, like setWeightConfig.
 */
private fun remapDraft(mode: ProgressMode, before: WeightConfig, c: WeightConfig, moves: MutableList<WeightMove>): WeightConfig {
    val weights = c.weights
    val sameLadder = before.weights == weights && before.repMin == c.repMin && before.repMax == c.repMax
    if (sameLadder || weights.isEmpty() || weights != weights.sorted().distinct() || c.repMin !in 1..c.repMax) return c
    val remapMode = if (mode.usesWeights) mode else ProgressMode.REPS_THEN_WEIGHT
    val remapped = remapWeights(remapMode, before, c, oldLevel = null).config
    val start = remapped.startWeight
    if (c.startWeight != null && start != null && start != c.startWeight) moves += WeightMove.StartWeightMoved(start)
    val removed = c.holds.size - remapped.holds.size
    if (removed > 0) moves += WeightMove.HoldsRemoved(removed)
    return remapped
}

/**
 * Revision 28 rule 4 for a weight save (plan Spec note 31): where a save's remap (§9.2) took the stored
 * current load, or null when it stayed. [mode] is a weight mode; [old] and [new] are in one unit. In
 * Weight mode only the weight counts; in Reps then weight the weight or the reps.
 */
fun currentLoadMove(mode: ProgressMode, old: WeightConfig, new: WeightConfig, oldLevel: Int, newLevel: Int): WeightMove.CurrentMoved? {
    val before = ladderOf(mode, old).prescription(oldLevel)
    val after = ladderOf(mode, new).prescription(newLevel)
    return when {
        mode == ProgressMode.WEIGHT -> if (after.weight != before.weight) WeightMove.CurrentMoved(after.weight, null) else null
        after != before -> WeightMove.CurrentMoved(after.weight, after.reps)
        else -> null
    }
}
