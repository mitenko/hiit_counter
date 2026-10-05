package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressionConfig

/**
 * A value that moved to fit the field the user edited (spec revision 28); the UI turns each one
 * into a note. [RangeChange] (Maximum raised, Minimum lowered) is the rest of the set. Only the
 * directions an override can cause exist, so every move has its own text.
 */
sealed interface Move {
    val to: Int

    data class StartingRaised(override val to: Int) : Move

    data class StartingLowered(override val to: Int) : Move

    /** The stored total, moved into a new floor..cap. */
    data class CurrentRaised(override val to: Int) : Move

    data class CurrentLowered(override val to: Int) : Move

    data class BestStreakRaised(override val to: Int) : Move
}

/** The Progression fields that can push another one (spec revision 28 rules 1–3). */
enum class ProgressionField { STARTING_TOTAL, FLOOR, CAP }

/** The config after an override, and every value that moved, in the order they moved. */
data class Resolution(val config: ProgressionConfig, val moves: List<Move>)

/**
 * Spec revision 28 rules 1–3: [edited] keeps its value, and the values it conflicts with move to
 * fit it. Starting reps widen floor..cap; a minimum above starting reps raises them (and the
 * maximum, if it now has to); a maximum below starting reps lowers them (and the minimum). With no
 * edited field, or an edited value below 1 (which can't be made valid), nothing moves and the
 * validator's errors stay. A config already in order returns this same instance.
 */
fun ProgressionConfig.resolveFor(edited: ProgressionField?): Resolution {
    val moves = mutableListOf<Move>()
    var c = this
    when (edited) {
        null -> Unit
        ProgressionField.STARTING_TOTAL -> if (startingTotal >= 1) {
            if (startingTotal > cap) {
                c = c.copy(cap = startingTotal)
                moves += RangeChange.RaisedMax(startingTotal)
            } else if (startingTotal < floor) {
                c = c.copy(floor = startingTotal)
                moves += RangeChange.LoweredMin(startingTotal)
            }
        }
        ProgressionField.FLOOR -> if (floor >= 1) {
            if (floor > c.startingTotal) {
                c = c.copy(startingTotal = floor)
                moves += Move.StartingRaised(floor)
            }
            if (c.startingTotal > c.cap) {
                c = c.copy(cap = c.startingTotal)
                moves += RangeChange.RaisedMax(c.cap)
            }
        }
        ProgressionField.CAP -> if (cap >= 1) {
            if (cap < c.startingTotal) {
                c = c.copy(startingTotal = cap)
                moves += Move.StartingLowered(cap)
            }
            if (c.startingTotal < c.floor) {
                c = c.copy(floor = c.startingTotal)
                moves += RangeChange.LoweredMin(c.floor)
            }
        }
    }
    return Resolution(c, moves)
}

/**
 * Spec revision 28 rule 4: where a stored [total] moves when this floor..cap is saved, or null when
 * it's already inside.
 */
fun ProgressionConfig.totalMove(total: Int): Move? = when {
    total > cap -> Move.CurrentLowered(cap)
    total < floor -> Move.CurrentRaised(floor)
    else -> null
}

/** The Current page's streak fields (spec revision 28 rule 5). */
enum class StreakField { BEST, CURRENT }

data class StreakResolution(val best: Int, val current: Int, val moves: List<Move>)

/**
 * Spec revision 28 rule 5: a current streak edited above the best streak raises the best streak to
 * match. A best streak edited below the current streak stays an error (no rule lowers the current
 * streak).
 */
fun resolveStreaks(best: Int, current: Int, edited: StreakField?): StreakResolution =
    if (edited == StreakField.CURRENT && current > best) {
        StreakResolution(current, current, listOf(Move.BestStreakRaised(current)))
    } else {
        StreakResolution(best, current, emptyList())
    }
