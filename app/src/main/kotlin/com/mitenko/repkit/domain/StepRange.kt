package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightConfig

/** A stepper field's step and hard range (spec §8.1): ± and dialog values never leave [min]..[max]. */
data class StepRange(val min: Int, val max: Int, val step: Int) {
    init {
        require(min <= max) { "min $min > max $max" }
        require(step > 0) { "step must be positive, was $step" }
    }

    fun clamp(value: Int): Int = value.coerceIn(min, max)

    fun plus(value: Int): Int = (value.toLong() + step).coerceIn(min.toLong(), max.toLong()).toInt()

    fun minus(value: Int): Int = (value.toLong() - step).coerceIn(min.toLong(), max.toLong()).toInt()
}

/** The hard ranges of spec §8.1. The penalty rate is stepped in half-hours by [PenaltyDraft]. */
object FieldRanges {
    /** Prepare, Rest, Cooldown: 0 – 59:59 in 1 s steps (user, 2026-10-01: every ± changes the value by 1). */
    val PHASE = StepRange(0, SettingsValidator.MAX_PHASE_SEC, 1)
    val WORK = StepRange(1, SettingsValidator.MAX_PHASE_SEC, 1)
    val SETS = StepRange(1, SettingsValidator.MAX_SETS, 1)
    /** Starting total, Floor, Cap, Hold at. */
    val REPS = StepRange(1, 9999, 1)
    val HOLD_FOR = StepRange(0, 999, 1)
    val WINDOW_HOURS = StepRange(1, 999, 1)
    /** Current State total. */
    val TOTAL = StepRange(1, 9999, 1)
    /** Best and current streak. */
    val STREAK = StepRange(0, 99999, 1)

    /** Reps per set, the rep range and starting reps per set in a weight mode (spec rev 26 §3.1): 1–100. */
    val REPS_PER_SET = StepRange(1, WeightConfig.MAX_REPS, 1)
}
