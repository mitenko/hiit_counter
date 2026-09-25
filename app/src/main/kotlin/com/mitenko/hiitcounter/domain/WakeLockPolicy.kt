package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState

/** Wake-lock rule for TimerService (spec §10). */
object WakeLockPolicy {
    const val MARGIN_MS = 60_000L

    /** Timeout for a lock held while [state] is ticking, or null when the lock must be released. */
    fun timeoutMs(state: TimerState?): Long? =
        if (state == null || state.phase == Phase.DONE || state.paused) null
        else state.remainingSec * 1000L + MARGIN_MS
}
