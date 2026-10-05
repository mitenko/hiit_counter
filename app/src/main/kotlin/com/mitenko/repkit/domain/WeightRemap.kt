package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold

/** What [remapWeights] returns (spec rev 26 §9.2). */
data class WeightRemapResult(
    /** The new settings with the starting point and holds remapped: holds on removed weights dropped, collisions keeping the first. */
    val config: WeightConfig,
    /** The current level on the new ladder, or null when it was null (an untouched counter follows the start). */
    val level: Int?,
    /** §9.2 step 5: the current load (weight value, reps per set) changed. */
    val currentChanged: Boolean,
)

/**
 * Spec rev 26 §9.2: the one remap for the current level, the starting point and every hold, by exact
 * value in hundredths. [mode] is a weight mode. [old] and [new] are in the same unit: after a unit
 * change, convert [old] first with WeightConversion.convert. [new]'s weights must be non-empty and
 * ascending, with 1 ≤ repMin ≤ repMax. WeightValidator runs on the result.
 */
fun remapWeights(mode: ProgressMode, old: WeightConfig, new: WeightConfig, oldLevel: Int?): WeightRemapResult {
    val to = ladderOf(mode, new)
    fun reps(r: Int) = r.coerceIn(new.repMin, new.repMax)
    val holds = new.holds
        .filter { it.weight in to.weights }
        .map { it.copy(reps = reps(it.reps)) }
        .distinctBy { to.levelOf(it.weight, it.reps) }
    val config = new.copy(
        startWeight = new.startWeight?.let { to.weights[to.weightIndexOf(it)] },
        startReps = new.startReps?.let(::reps),
        holds = holds,
    )
    if (oldLevel == null) return WeightRemapResult(config, level = null, currentChanged = false)
    val before = ladderOf(mode, old).prescription(oldLevel)
    val level = to.levelOf(before.weight, before.reps)
    return WeightRemapResult(config, level, currentChanged = to.prescription(level) != before)
}

/**
 * The hold count after a weight save (spec rev 26 §9.2 step 5, with rev 16 §4 applied by value, plan
 * Spec note 9): it resets when the current load changed, when the hold list changed (as a list, so
 * order counts), or when the set of active holds changed.
 */
fun weightHoldResetNeeded(mode: ProgressMode, old: WeightConfig, remap: WeightRemapResult): Boolean =
    remap.currentChanged || old.holds != remap.config.holds || activeHolds(mode, old) != activeHolds(mode, remap.config)

/** The holds that can apply: held for more than 0 and below the top level (RepProgression never holds at the cap). */
private fun activeHolds(mode: ProgressMode, c: WeightConfig): Set<WeightHold> {
    val ladder = ladderOf(mode, c)
    return c.holds.filter { it.forCount > 0 && ladder.levelOf(it.weight, it.reps) < ladder.maxLevel }.toSet()
}
