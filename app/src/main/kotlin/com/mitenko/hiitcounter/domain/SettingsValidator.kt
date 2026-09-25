package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import java.time.Instant

enum class Field {
    PREPARE, SETS, WORK, REST, COOLDOWN, TOTAL_DURATION,
    STARTING_TOTAL, FLOOR, CAP, HOLD_AT, HOLD_FOR, WINDOW_HOURS, PENALTY_RATE,
    TOTAL, BEST_STREAK, CURRENT_STREAK, LAST_CHECK_IN,
}

data class ValidationResult(
    val errors: Map<Field, String> = emptyMap(),
    val hints: Map<Field, String> = emptyMap(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Settings validation — spec §10. */
object SettingsValidator {
    const val MAX_PHASE_SEC = 59 * 60 + 59
    const val MAX_SETS = 20
    const val MAX_TOTAL_SEC = 2 * 60 * 60
    const val NOT_A_NUMBER = "Enter a number"

    fun timing(c: TimingConfig): ValidationResult {
        val e = mutableMapOf<Field, String>()
        if (c.sets !in 1..MAX_SETS) e[Field.SETS] = "1–$MAX_SETS sets"
        if (c.workSec !in 1..MAX_PHASE_SEC) e[Field.WORK] = "1 s – 59:59"
        if (c.prepareSec !in 0..MAX_PHASE_SEC) e[Field.PREPARE] = "0 – 59:59"
        if (c.restSec !in 0..MAX_PHASE_SEC) e[Field.REST] = "0 – 59:59"
        if (c.cooldownSec !in 0..MAX_PHASE_SEC) e[Field.COOLDOWN] = "0 – 59:59"
        if (e.isEmpty() && c.totalDurationSec > MAX_TOTAL_SEC) e[Field.TOTAL_DURATION] = "Workout longer than 2:00:00"
        return ValidationResult(e)
    }

    fun progression(c: ProgressionConfig): ValidationResult {
        val e = mutableMapOf<Field, String>()
        if (c.floor < 1) e[Field.FLOOR] = "Must be at least 1"
        if (c.startingTotal < c.floor) e[Field.STARTING_TOTAL] = "Must be ≥ floor"
        if (c.cap < c.startingTotal) e[Field.CAP] = "Must be ≥ starting total"
        if (c.holdAt < 1) e[Field.HOLD_AT] = "Must be at least 1"
        if (c.holdFor < 0) e[Field.HOLD_FOR] = "Must be 0 or more"
        if (c.windowHours < 1) e[Field.WINDOW_HOURS] = "Must be at least 1"
        if (!(c.penaltyHoursPerRep > 0.0) || !c.penaltyHoursPerRep.isFinite()) e[Field.PENALTY_RATE] = "Must be greater than 0"
        val hints = if (Field.HOLD_AT !in e && !c.holdEnabled) mapOf(Field.HOLD_AT to "Hold disabled") else emptyMap()
        return ValidationResult(e, hints)
    }

    fun currentState(
        total: Int,
        bestStreak: Int,
        currentStreak: Int,
        lastCheckIn: Instant?,
        now: Instant,
        config: ProgressionConfig,
    ): ValidationResult {
        val e = mutableMapOf<Field, String>()
        val hints = mutableMapOf<Field, String>()
        if (total < 1) e[Field.TOTAL] = "Must be at least 1"
        else if (total !in config.floor..config.cap) hints[Field.TOTAL] = "Outside floor–cap; clamped at the next check-in"
        if (currentStreak < 0) e[Field.CURRENT_STREAK] = "Must be 0 or more"
        if (bestStreak < 0) e[Field.BEST_STREAK] = "Must be 0 or more"
        else if (bestStreak < currentStreak) e[Field.BEST_STREAK] = "Must be ≥ current streak"
        if (lastCheckIn != null && lastCheckIn.isAfter(now)) e[Field.LAST_CHECK_IN] = "Can't be in the future"
        return ValidationResult(e, hints)
    }
}
