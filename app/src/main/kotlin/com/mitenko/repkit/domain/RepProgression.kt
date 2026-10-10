package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.ProgressionConfig
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

sealed interface Outcome {
    data object AlreadyToday : Outcome
    data object First : Outcome
    data object OnTime : Outcome
    data class Missed(val penalty: Int) : Outcome
}

data class CheckInResult(val state: CounterState, val outcome: Outcome)

/** Check-in rules — spec §6. Pure; evaluated in order, first match wins. */
object RepProgression {
    private const val MS_PER_HOUR = 3_600_000.0

    /** Reps mode's miss floor: none, so a miss is exactly `max(floor, total − penalty)` (plan Spec note 1). */
    val NO_MISS_FLOOR: (Int) -> Int = { Int.MIN_VALUE }

    /**
     * [countsReps] is false for a Timer only entry (spec R4 §3.1): rules 1–4 still decide the
     * outcome and the streaks, and [CounterState.lastCheckIn] becomes [now], but the total and the
     * hold count are kept exactly (no +1, no penalty, no clamp, no hold). A miss reports a penalty of 0.
     * [missFloor] (spec rev 26 §9.1) is the lowest total a miss from the clamped total may land on.
     *
     * [CounterState.freshStart] (plan Spec note 13): a recorded check-in after Start fresh stays at the
     * clamped start; on time adds no +1, a miss takes no penalty (Missed(0)), and the hold count follows
     * the start. Every recorded Counter check-in clears the flag; AlreadyToday and Timer only
     * check-ins keep it, so it survives a Counter → Timer only → Counter round trip.
     */
    fun checkIn(
        state: CounterState,
        config: ProgressionConfig,
        now: Instant,
        zone: ZoneId,
        countsReps: Boolean = true,
        missFloor: (Int) -> Int = NO_MISS_FLOOR,
    ): CheckInResult {
        val last = state.lastCheckIn

        // Rule 1: already checked in today.
        if (last != null && last.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            return CheckInResult(state, Outcome.AlreadyToday)
        }

        if (!countsReps) return streaksOnly(state, config, now, last)

        // Rules 2–4 start from a total clamped to [floor, cap] (config may have changed).
        val total = state.total.coerceIn(config.floor, config.cap)

        // Rule 2: first ever check-in — perform the starting total on day 1.
        if (last == null) {
            return CheckInResult(
                CounterState(
                    total = total,
                    bestStreak = max(state.bestStreak, 1),
                    currentStreak = 1,
                    lastCheckIn = now,
                    holdCount = startingHoldCount(total, config),
                ),
                Outcome.First,
            )
        }

        val hours = hoursSince(last, now)

        // Rule 3: missed. A miss that leaves the total on an active hold restarts that hold.
        if (hours > config.windowHours) {
            // Plan Spec note 13: after Start fresh the start is the baseline, so no penalty applies.
            val penalty = if (state.freshStart) 0 else max(0, roundHalfUp((hours - 24) / config.penaltyHoursPerRep) - 1)
            // Spec rev 26 §9.1: clamp (above), penalty, the miss floor measured from the clamped start, then the minimum.
            val newTotal = if (state.freshStart) total else maxOf(total - penalty, missFloor(total), config.floor)
            return CheckInResult(
                CounterState(
                    total = newTotal,
                    bestStreak = max(state.bestStreak, 1),
                    currentStreak = 1,
                    lastCheckIn = now,
                    holdCount = startingHoldCount(newTotal, config),
                ),
                Outcome.Missed(penalty),
            )
        }

        // Rule 4: on time (includes a clock that moved backwards: negative hours).
        val streak = state.currentStreak + 1
        val newTotal: Int
        val holdCount: Int
        val hold = config.activeHold(total)
        if (state.freshStart) {
            // Plan Spec note 13: performed at the start; a start on a hold counts this day as day 1.
            newTotal = total
            holdCount = startingHoldCount(total, config)
        } else if (hold != null) {
            if (state.holdCount >= hold.forCount) {
                // Done here (an active hold's total is below the cap, so +1 stays in range). A held value
                // right above (the next one, inside a From range) starts on this day (spec rev 34 §2).
                newTotal = total + 1
                holdCount = startingHoldCount(newTotal, config)
            } else {
                newTotal = total
                holdCount = state.holdCount + 1
            }
        } else {
            newTotal = if (total < config.cap) total + 1 else total
            holdCount = startingHoldCount(newTotal, config)
        }
        return CheckInResult(
            CounterState(
                total = newTotal,
                bestStreak = max(state.bestStreak, streak),
                currentStreak = streak,
                lastCheckIn = now,
                holdCount = holdCount,
            ),
            Outcome.OnTime,
        )
    }

    /**
     * Spec rev 26 §2: [checkIn] on [config]'s levels, using its engine config and its scale's miss floor.
     * In Reps mode this is exactly [checkIn]. Callers with an entry use this, never [checkIn] directly.
     */
    fun checkInByMode(
        state: CounterState,
        config: ProgressionConfig,
        now: Instant,
        zone: ZoneId,
        countsReps: Boolean = true,
    ): CheckInResult {
        val scale = config.scale()
        return checkIn(state, config.engineConfig(), now, zone, countsReps, scale::missFloor)
    }

    /** Rules 2–4 for the streaks and the date only (spec R4 §3.1, Timer only entries); the total and hold count are copied as they are. */
    private fun streaksOnly(state: CounterState, config: ProgressionConfig, now: Instant, last: Instant?): CheckInResult {
        val (streak, outcome) = when {
            last == null -> 1 to Outcome.First
            hoursSince(last, now) > config.windowHours -> 1 to Outcome.Missed(penalty = 0)
            else -> state.currentStreak + 1 to Outcome.OnTime
        }
        return CheckInResult(
            state.copy(bestStreak = max(state.bestStreak, streak), currentStreak = streak, lastCheckIn = now),
            outcome,
        )
    }

    private fun hoursSince(last: Instant, now: Instant): Int =
        roundHalfUp((now.toEpochMilli() - last.toEpochMilli()) / MS_PER_HOUR)

    /** 1 when this check-in is the first performed at an active hold's value, else 0 (spec rev 16 §2). */
    private fun startingHoldCount(total: Int, config: ProgressionConfig): Int =
        if (config.activeHold(total) != null) 1 else 0
}
