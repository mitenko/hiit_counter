package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.ProgressionConfig

/**
 * Spec rev 40: how many more on-time check-ins stay on the hold the workout is on, or null when it isn't on
 * one or the next on-time check-in climbs. The day a hold is reached counts as day 1 (rev 16), so a hold
 * for 4 reached today leaves 3. Uses the engine's holds ([engineConfig]: weight holds in a weight mode) and
 * the clamped total, as a check-in does.
 */
fun holdRemaining(config: ProgressionConfig, state: CounterState): Int? {
    val engine = config.engineConfig()
    val total = state.total.coerceIn(engine.floor, engine.cap)
    val hold = engine.activeHold(total) ?: return null
    return (hold.forCount - state.holdCount).takeIf { it > 0 }
}
