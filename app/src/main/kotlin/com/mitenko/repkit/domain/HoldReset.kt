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
