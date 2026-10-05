package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig

/** What one level asks for (spec rev 26 §2). */
sealed interface Prescription {
    /** Reps mode: the rep total, split across the sets by RepDistributor. */
    data class RepTotal(val total: Int) : Prescription

    /** A weight mode: [weight] in hundredths of the workout's unit, [reps] per set. */
    data class Load(val weight: Int, val reps: Int) : Prescription
}

/**
 * Spec rev 26 §2: maps the integer RepProgression moves (CounterState.total; a "level" in code,
 * because Entry.position is the list order) to a prescription and back. Pure.
 */
sealed interface ProgressionScale {
    val minLevel: Int
    val maxLevel: Int

    /** The lowest level a miss from [start] (already clamped) may land on (spec rev 26 §9.1 step 3). */
    fun missFloor(start: Int): Int

    fun prescription(level: Int): Prescription

    /** Today's Reps mode: the level is the rep total, floor..cap. */
    data class Reps(val floor: Int, val cap: Int) : ProgressionScale {
        override val minLevel: Int get() = floor
        override val maxLevel: Int get() = cap

        /** No floor of its own (plan Spec note 1), so a Reps miss is exactly today's. */
        override fun missFloor(start: Int): Int = Int.MIN_VALUE

        override fun prescription(level: Int): Prescription.RepTotal = Prescription.RepTotal(level)
    }

    /** The two weight modes. Level 0 is the lightest weight (× the lowest reps); [weights] is ascending and non-empty. */
    sealed interface Ladder : ProgressionScale {
        val weights: List<Int>
        override val minLevel: Int get() = 0

        override fun prescription(level: Int): Prescription.Load

        /** Spec rev 26 §9.2 steps 1–3: the level of [weight] (by weightIndexOf) × [reps] clamped into the range. */
        fun levelOf(weight: Int, reps: Int): Int

        /** §9.2 step 1: the largest index whose weight is ≤ [weight], or 0 (the lightest) when none is. */
        fun weightIndexOf(weight: Int): Int = weights.indexOfLast { it <= weight }.coerceAtLeast(0)
    }

    /** Weight: one level per weight; the reps per set are fixed. */
    data class Weight(override val weights: List<Int>, val repsPerSet: Int) : Ladder {
        init {
            require(weights.isNotEmpty()) { "A ladder needs at least one weight" }
        }

        override val maxLevel: Int get() = weights.lastIndex

        /** At most one lighter weight (spec rev 26 §2): index − 1, never below 0. */
        override fun missFloor(start: Int): Int = (start.coerceIn(minLevel, maxLevel) - 1).coerceAtLeast(0)

        override fun prescription(level: Int): Prescription.Load =
            Prescription.Load(weights[level.coerceIn(minLevel, maxLevel)], repsPerSet)

        override fun levelOf(weight: Int, reps: Int): Int = weightIndexOf(weight)
    }

    /** Reps then weight: [span] levels per weight; weightIndex = level / span, reps = repMin + level % span. */
    data class RepsThenWeight(override val weights: List<Int>, val repMin: Int, val repMax: Int) : Ladder {
        init {
            require(weights.isNotEmpty()) { "A ladder needs at least one weight" }
            require(repMin in 1..repMax) { "Invalid rep range $repMin..$repMax" }
        }

        val span: Int get() = repMax - repMin + 1
        override val maxLevel: Int get() = weights.size * span - 1

        /** The first level of the next lighter weight (spec rev 26 §2): (weightIndex − 1) × span, never below 0. */
        override fun missFloor(start: Int): Int = ((start.coerceIn(minLevel, maxLevel) / span - 1) * span).coerceAtLeast(0)

        override fun prescription(level: Int): Prescription.Load {
            val l = level.coerceIn(minLevel, maxLevel)
            return Prescription.Load(weights[l / span], repMin + l % span)
        }

        override fun levelOf(weight: Int, reps: Int): Int =
            weightIndexOf(weight) * span + (reps.coerceIn(repMin, repMax) - repMin)
    }
}

/** The ladder of weight mode [mode] over [weight]'s weights (spec rev 26 §2). Reps mode has none. */
fun ladderOf(mode: ProgressMode, weight: WeightConfig): ProgressionScale.Ladder = when (mode) {
    ProgressMode.WEIGHT -> ProgressionScale.Weight(weight.weights, weight.repsPerSet)
    ProgressMode.REPS_THEN_WEIGHT -> ProgressionScale.RepsThenWeight(weight.weights, weight.repMin, weight.repMax)
    ProgressMode.REPS -> throw IllegalArgumentException("Reps mode has no ladder")
}

/** This config's scale (spec rev 26 §2). A weight mode needs a non-empty, valid weight group (EntryMapping guarantees one). */
fun ProgressionConfig.scale(): ProgressionScale =
    if (mode == ProgressMode.REPS) ProgressionScale.Reps(floor, cap) else ladderOf(mode, weight)

/** Where an untouched counter is (spec rev 26 §2 Starting point): startingTotal, or the starting weight × starting reps. */
fun ProgressionConfig.startLevel(): Int = when (val s = scale()) {
    is ProgressionScale.Reps -> startingTotal
    is ProgressionScale.Ladder -> s.levelOf(weight.startWeight ?: s.weights.first(), weight.startReps ?: weight.repMin)
}

/**
 * The config RepProgression runs on (spec rev 26 §2): itself in Reps mode. In a weight mode it is the
 * ladder's levels as floor..cap, the start level as the starting total and the weight holds as level
 * holds; the window, penalty and Hold switch are kept.
 */
fun ProgressionConfig.engineConfig(): ProgressionConfig = when (val s = scale()) {
    is ProgressionScale.Reps -> this
    is ProgressionScale.Ladder -> copy(
        startingTotal = startLevel(),
        floor = s.minLevel,
        cap = s.maxLevel,
        holds = weight.holds.map { Hold(s.levelOf(it.weight, it.reps), it.forCount) },
    )
}

/** The load at [level] in a weight mode (spec rev 26 §9.3), or null in Reps mode. */
fun ProgressionConfig.loadAt(level: Int): Prescription.Load? = (scale() as? ProgressionScale.Ladder)?.prescription(level)
