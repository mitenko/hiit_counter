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
 * Why a settings field is invalid, or a hint about it (spec revision 24): typed, so `domain/` stays
 * free of English. The UI maps each one to a string resource (`ui/common/FieldMessages.kt`).
 */
sealed interface FieldMessage {
    /** Sets outside 1..[max]: "1–20 sets". */
    data class SetsRange(val max: Int) : FieldMessage

    /** Work outside 1 s..59:59. */
    data object WorkRange : FieldMessage

    /** Prepare, rest or cooldown outside 0..59:59. */
    data object PhaseRange : FieldMessage

    /** The derived total is over [SettingsValidator.MAX_TOTAL_SEC]. */
    data object WorkoutTooLong : FieldMessage

    data object AtLeastOne : FieldMessage

    data object ZeroOrMore : FieldMessage

    data object GreaterThanZero : FieldMessage

    /** Starting total below the floor. */
    data object AtLeastFloor : FieldMessage

    /** Cap below the starting total. */
    data object AtLeastStartingTotal : FieldMessage

    /** Best streak below the current streak. */
    data object AtLeastCurrentStreak : FieldMessage

    data class TooManyHolds(val max: Int) : FieldMessage

    /** A later hold at the same total as an earlier one. */
    data class DuplicateHold(val at: Int) : FieldMessage

    /** Hint: the hold can never apply (outside floor..cap, or held for 0). */
    data object HoldDisabled : FieldMessage

    data object InTheFuture : FieldMessage
}

/**
 * [holdErrors] and [holdHints] are keyed by the hold's index in [ProgressionConfig.holds]; a hint
 * belongs to the hold's Hold at row (spec rev 16 §3).
 */
data class ValidationResult(
    val errors: Map<Field, FieldMessage> = emptyMap(),
    val hints: Map<Field, FieldMessage> = emptyMap(),
    val holdErrors: Map<Int, Map<HoldField, FieldMessage>> = emptyMap(),
    val holdHints: Map<Int, FieldMessage> = emptyMap(),
) {
    val isValid: Boolean get() = errors.isEmpty() && holdErrors.isEmpty()
}

/** Settings validation — spec §10. */
object SettingsValidator {
    const val MAX_PHASE_SEC = 59 * 60 + 59
    const val MAX_SETS = 20
    const val MAX_TOTAL_SEC = 2 * 60 * 60

    fun timing(c: TimingConfig): ValidationResult {
        val e = mutableMapOf<Field, FieldMessage>()
        if (c.sets !in 1..MAX_SETS) e[Field.SETS] = FieldMessage.SetsRange(MAX_SETS)
        if (c.workSec !in 1..MAX_PHASE_SEC) e[Field.WORK] = FieldMessage.WorkRange
        if (c.prepareSec !in 0..MAX_PHASE_SEC) e[Field.PREPARE] = FieldMessage.PhaseRange
        if (c.restSec !in 0..MAX_PHASE_SEC) e[Field.REST] = FieldMessage.PhaseRange
        if (c.cooldownSec !in 0..MAX_PHASE_SEC) e[Field.COOLDOWN] = FieldMessage.PhaseRange
        if (e.isEmpty() && c.totalDurationSec > MAX_TOTAL_SEC) e[Field.TOTAL_DURATION] = FieldMessage.WorkoutTooLong
        return ValidationResult(e)
    }

    fun progression(c: ProgressionConfig): ValidationResult {
        val e = mutableMapOf<Field, FieldMessage>()
        if (c.floor < 1) e[Field.FLOOR] = FieldMessage.AtLeastOne
        if (c.startingTotal < c.floor) e[Field.STARTING_TOTAL] = FieldMessage.AtLeastFloor
        if (c.cap < c.startingTotal) e[Field.CAP] = FieldMessage.AtLeastStartingTotal
        if (c.windowHours < 1) e[Field.WINDOW_HOURS] = FieldMessage.AtLeastOne
        if (!(c.penaltyHoursPerRep > 0.0) || !c.penaltyHoursPerRep.isFinite()) e[Field.PENALTY_RATE] = FieldMessage.GreaterThanZero
        // Rev 16 §3: each hold's hard ranges hold whatever the switch says, so a stored list always decodes.
        val holdErrors = mutableMapOf<Int, MutableMap<HoldField, FieldMessage>>()
        c.holds.forEachIndexed { i, h ->
            if (h.at < 1) holdErrors.getOrPut(i, ::mutableMapOf)[HoldField.AT] = FieldMessage.AtLeastOne
            if (h.forCount < 0) holdErrors.getOrPut(i, ::mutableMapOf)[HoldField.FOR] = FieldMessage.ZeroOrMore
        }
        // Spec R3 §5.1: with the Hold switch off, the hidden hold values can't otherwise block a save.
        if (!c.hold) return ValidationResult(e, holdErrors = holdErrors)
        if (c.holds.size > ProgressionConfig.MAX_HOLDS) e[Field.HOLDS] = FieldMessage.TooManyHolds(ProgressionConfig.MAX_HOLDS)
        val holdHints = mutableMapOf<Int, FieldMessage>()
        val seen = mutableSetOf<Int>()
        c.holds.forEachIndexed { i, h ->
            // Spec rev 16 §3: the later duplicate carries the error.
            if (h.at >= 1 && !seen.add(h.at)) holdErrors.getOrPut(i, ::mutableMapOf)[HoldField.AT] = FieldMessage.DuplicateHold(h.at)
            if (i !in holdErrors && !c.isActive(h)) holdHints[i] = FieldMessage.HoldDisabled
        }
        return ValidationResult(e, holdErrors = holdErrors, holdHints = holdHints)
    }

    fun currentState(
        total: Int,
        bestStreak: Int,
        currentStreak: Int,
        lastCheckIn: Instant?,
        now: Instant,
    ): ValidationResult {
        // Spec revision 27: a total outside floor..cap is fine; saving it widens the range.
        val e = mutableMapOf<Field, FieldMessage>()
        if (total < 1) e[Field.TOTAL] = FieldMessage.AtLeastOne
        if (currentStreak < 0) e[Field.CURRENT_STREAK] = FieldMessage.ZeroOrMore
        if (bestStreak < 0) e[Field.BEST_STREAK] = FieldMessage.ZeroOrMore
        else if (bestStreak < currentStreak) e[Field.BEST_STREAK] = FieldMessage.AtLeastCurrentStreak
        if (lastCheckIn != null && lastCheckIn.isAfter(now)) e[Field.LAST_CHECK_IN] = FieldMessage.InTheFuture
        return ValidationResult(e)
    }
}
