package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
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

    fun checkIn(state: CounterState, config: ProgressionConfig, now: Instant, zone: ZoneId): CheckInResult {
        val last = state.lastCheckIn

        // Rule 1: already checked in today.
        if (last != null && last.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            return CheckInResult(state, Outcome.AlreadyToday)
        }

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

        val hours = roundHalfUp((now.toEpochMilli() - last.toEpochMilli()) / MS_PER_HOUR)

        // Rule 3: missed. A miss that leaves the total on holdAt restarts the hold.
        if (hours > config.windowHours) {
            val penalty = max(0, roundHalfUp((hours - 24) / config.penaltyHoursPerRep) - 1)
            val newTotal = max(config.floor, total - penalty)
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
        if (config.holdEnabled && total == config.holdAt) {
            if (state.holdCount >= config.holdFor) {
                newTotal = total + 1
                holdCount = 0
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

    /** 1 when this check-in is the first performed at the hold value, else 0. */
    private fun startingHoldCount(total: Int, config: ProgressionConfig): Int =
        if (config.holdEnabled && total == config.holdAt) 1 else 0
}
