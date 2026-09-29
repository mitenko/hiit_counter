package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.ProgressionConfig

/**
 * Spec R3 §6.3: a progression save resets the hold count only when the hold itself changes:
 * holdAt, holdFor or the effective [ProgressionConfig.holdEnabled]. Floor, cap and the switch
 * feed into holdEnabled. Starting-total, window and penalty edits keep the count.
 */
fun holdResetNeeded(old: ProgressionConfig, new: ProgressionConfig): Boolean =
    old.holdAt != new.holdAt || old.holdFor != new.holdFor || old.holdEnabled != new.holdEnabled

/** Spec R3 §6.3: overwriting the counter (Current page) resets the hold count only when the total changes. */
fun counterHoldReset(oldTotal: Int, newTotal: Int): Boolean = oldTotal != newTotal
