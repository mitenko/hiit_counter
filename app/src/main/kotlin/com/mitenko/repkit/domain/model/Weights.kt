package com.mitenko.repkit.domain.model

/** What a Counter check-in moves (spec rev 26 §1). Stored by name in `entry.progress_mode`. */
enum class ProgressMode {
    REPS,
    WEIGHT,
    REPS_THEN_WEIGHT,
    ;

    /** True for the two modes whose level is a rung on a weight ladder (spec rev 26 §2). */
    val usesWeights: Boolean get() = this != REPS
}

/** A workout's unit (spec rev 26 §2 Units). Stored by name in `entry.weight_unit` and `check_in.unit`. */
enum class WeightUnit { KG, LB }

/** Where a workout's weights come from (spec rev 26 §2 Weights): Steps or My weights. */
enum class WeightsKind { STEPS, LIST }

/**
 * Steps (spec rev 26 §2): [start], [step] and [top] in integer hundredths of the workout's unit, so
 * 2000 / 250 / 6000 is 20 / 2.5 / 60. WeightValidator checks the values; [expand] never throws.
 */
data class WeightSteps(val start: Int, val step: Int, val top: Int) {
    /** start, start + step, … while ≤ top. Empty when nothing can be generated (start or step ≤ 0, top < start). */
    fun expand(): List<Int> {
        if (step <= 0 || start <= 0 || top < start) return emptyList()
        return (start..top).step(step).toList()
    }

    companion object {
        /** What `entry.weight_steps` '' reads as (spec rev 26 §5): 20 / 2.5 / 60. */
        val DEFAULT = WeightSteps(2000, 250, 6000)
    }
}

/**
 * A weight-mode hold (spec rev 26 §2 Holds), stored by value in `entry.weight_holds` (plan Spec
 * note 2): [weight] in hundredths, [reps] (Reps then weight; Weight mode ignores it) and [forCount]
 * check-ins, counting the day it's reached.
 */
data class WeightHold(val weight: Int, val reps: Int, val forCount: Int)

/**
 * A Counter workout's weight settings (spec rev 26 §2, §5). Every mode keeps them, so switching away
 * and back restores them. [unit] is null only while the workout has never been in a weight mode
 * (§9.3). [startWeight] null means the lightest weight; [startReps] null means [repMin].
 */
data class WeightConfig(
    val unit: WeightUnit? = null,
    val kind: WeightsKind = WeightsKind.STEPS,
    val steps: WeightSteps = WeightSteps.DEFAULT,
    val list: List<Int> = emptyList(),
    val repsPerSet: Int = 10,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val startWeight: Int? = null,
    val startReps: Int? = null,
    val holds: List<WeightHold> = emptyList(),
) {
    /** The ladder in hundredths: the expanded steps, or the list as stored (sorted on save). */
    val weights: List<Int>
        get() = when (kind) {
            WeightsKind.STEPS -> steps.expand()
            WeightsKind.LIST -> list
        }

    companion object {
        const val MIN_WEIGHTS = 2
        const val MAX_WEIGHTS = 40

        /** 999.75 in the unit (spec rev 26 §2 Limits). */
        const val MAX_WEIGHT = 99_975
        const val MAX_REPS = 100
    }
}
