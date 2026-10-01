package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
import java.time.Instant

enum class Field {
    PREPARE, SETS, WORK, REST, COOLDOWN, TOTAL_DURATION,
    STARTING_TOTAL, FLOOR, CAP, HOLDS, WINDOW_HOURS, PENALTY_RATE,
    TOTAL, BEST_STREAK, CURRENT_STREAK, LAST_CHECK_IN,
}

/** A field of one hold in [ProgressionConfig.holds] (spec rev 16 §3). */
enum class HoldField { AT, FOR }

/**
 * [holdErrors] and [holdHints] are keyed by the hold's index in [ProgressionConfig.holds]; a hint
 * belongs to the hold's Hold at row (spec rev 16 §3).
 */
data class ValidationResult(
    val errors: Map<Field, String> = emptyMap(),
    val hints: Map<Field, String> = emptyMap(),
    val holdErrors: Map<Int, Map<HoldField, String>> = emptyMap(),
    val holdHints: Map<Int, String> = emptyMap(),
) {
    val isValid: Boolean get() = errors.isEmpty() && holdErrors.isEmpty()
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
        if (c.windowHours < 1) e[Field.WINDOW_HOURS] = "Must be at least 1"
        if (!(c.penaltyHoursPerRep > 0.0) || !c.penaltyHoursPerRep.isFinite()) e[Field.PENALTY_RATE] = "Must be greater than 0"
        // Rev 16 §3: each hold's hard ranges hold whatever the switch says, so a stored list always decodes.
        val holdErrors = mutableMapOf<Int, MutableMap<HoldField, String>>()
        c.holds.forEachIndexed { i, h ->
            if (h.at < 1) holdErrors.getOrPut(i, ::mutableMapOf)[HoldField.AT] = "Must be at least 1"
            if (h.forCount < 0) holdErrors.getOrPut(i, ::mutableMapOf)[HoldField.FOR] = "Must be 0 or more"
        }
        // Spec R3 §5.1: with the Hold switch off, the hidden hold values can't otherwise block a save.
        if (!c.hold) return ValidationResult(e, holdErrors = holdErrors)
        if (c.holds.size > ProgressionConfig.MAX_HOLDS) e[Field.HOLDS] = "At most ${ProgressionConfig.MAX_HOLDS} holds"
        val holdHints = mutableMapOf<Int, String>()
        val seen = mutableSetOf<Int>()
        c.holds.forEachIndexed { i, h ->
            // Spec rev 16 §3: the later duplicate carries the error.
            if (h.at >= 1 && !seen.add(h.at)) holdErrors.getOrPut(i, ::mutableMapOf)[HoldField.AT] = "Already a hold at ${h.at}"
            if (i !in holdErrors && !c.isActive(h)) holdHints[i] = HOLD_DISABLED
        }
        return ValidationResult(e, holdErrors = holdErrors, holdHints = holdHints)
    }

    const val HOLD_DISABLED = "Hold disabled"

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
