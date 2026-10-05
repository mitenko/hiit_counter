package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressionConfig

/**
 * Spec R3 §6.3 as amended by rev 16 §4: a progression save resets the hold count only when the
 * holds change: the list itself (compared as a list, so order counts), the switch, or the set of
 * active holds (a floor or cap edit that turns a hold on or off). Starting-total, window and
 * penalty edits keep the count.
 */
fun holdResetNeeded(old: ProgressionConfig, new: ProgressionConfig): Boolean =
    old.holds != new.holds || old.hold != new.hold || old.activeHolds.toSet() != new.activeHolds.toSet()

/** Spec R3 §6.3: overwriting the counter (Current page) resets the hold count only when the total changes. */
fun counterHoldReset(oldTotal: Int, newTotal: Int): Boolean = oldTotal != newTotal

/**
 * What a Progression-page save ([new], always built in Reps mode) does to the hold count of a row
 * whose effective progression is [old]. In Reps mode it is [holdResetNeeded]. In a weight mode the
 * Reps holds don't apply, so only the shared Hold switch resets it (spec rev 26 §10 note 21);
 * weight-hold edits reset it through setWeightConfig's own rule (weightHoldResetNeeded).
 */
fun progressionHoldReset(old: ProgressionConfig, new: ProgressionConfig): Boolean =
    if (old.mode.usesWeights) old.hold != new.hold else holdResetNeeded(old, new)
