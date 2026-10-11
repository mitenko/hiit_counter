package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind

/**
 * "+ Add weight" (spec rev 26 §3, plan Spec note 29): the last weight + the last gap. With fewer than
 * two weights the gap is the default step (2.5), and an empty list starts at the default start (20).
 * Kept at most 999.75; a value already listed is added anyway, for the validator to flag.
 */
fun nextListWeight(list: List<Int>): Int {
    val sorted = list.sorted()
    val last = sorted.lastOrNull() ?: return WeightSteps.DEFAULT.start
    val gap = if (sorted.size >= 2) last - sorted[sorted.size - 2] else WeightSteps.DEFAULT.step
    return (last + gap.coerceAtLeast(1)).coerceAtMost(WeightConfig.MAX_WEIGHT)
}

/** The weights a picker offers: ascending, each once (a My weights draft can hold a duplicate the validator flags). */
val WeightConfig.pickable: List<Int>
    get() = weights.sorted().distinct()

/** My weights row [index] becomes [value]; the list sorts at once (plan Spec note 29). */
fun WeightConfig.withListWeight(index: Int, value: Int): WeightConfig =
    copy(list = list.mapIndexed { i, w -> if (i == index) value else w }.sorted())

/** ✕ on My weights row [index]. Going below two weights is allowed; the validator flags it. */
fun WeightConfig.withoutListWeight(index: Int): WeightConfig = copy(list = list.filterIndexed { i, _ -> i != index })

fun WeightConfig.withNewListWeight(): WeightConfig = copy(list = (list + nextListWeight(list)).sorted())

/**
 * Steps | My weights. Both are kept when switching. Switching to My weights with an empty list seeds
 * it from the steps' weights (at most 40), so the user edits from where they were (plan Spec note 29).
 */
fun WeightConfig.withKind(kind: WeightsKind): WeightConfig = when {
    kind == this.kind -> this
    kind == WeightsKind.LIST && list.isEmpty() -> copy(kind = kind, list = steps.expand().take(WeightConfig.MAX_WEIGHTS))
    else -> copy(kind = kind)
}

fun WeightConfig.withWeightHold(index: Int, transform: (WeightHold) -> WeightHold): WeightConfig =
    copy(holds = holds.mapIndexed { i, h -> if (i == index) transform(h) else h })

fun WeightConfig.withoutWeightHold(index: Int): WeightConfig = copy(holds = holds.filterIndexed { i, _ -> i != index })

fun WeightConfig.withNewWeightHold(mode: ProgressMode): WeightConfig = copy(holds = holds + newWeightHold(mode, this))

/**
 * The hold "+ Add hold" appends in a weight mode (plan Spec note 32, like rev 16 §6): the first weight
 * above the last hold's weight (or the starting weight) that isn't the heaviest and isn't held, at
 * the lowest reps, for the last hold's count (or 4). Failing that, the first free weight below the
 * heaviest; with none free it's added anyway, for the validator to flag. In Weight mode a hold sits
 * on its weight alone (§10 note 10), so any hold on a weight takes it.
 */
fun newWeightHold(mode: ProgressMode, c: WeightConfig): WeightHold {
    val forCount = c.holds.lastOrNull()?.forCount ?: ProgressionConfig.DEFAULT_HOLD.forCount
    val weights = c.pickable
    val lightest = weights.firstOrNull() ?: return WeightHold(WeightSteps.DEFAULT.start, c.repMin, forCount)
    fun position(weight: Int, reps: Int) = if (mode == ProgressMode.WEIGHT) weight to 0 else weight to reps
    val taken = c.holds.map { position(it.weight, it.reps) }.toSet()
    val free = weights.dropLast(1).filter { position(it, c.repMin) !in taken }
    val from = c.holds.lastOrNull()?.weight ?: c.startWeight ?: lightest
    val weight = free.firstOrNull { it > from } ?: free.firstOrNull() ?: lightest
    return WeightHold(weight, c.repMin, forCount)
}

/** ± on Start or Top (spec rev 26 §3): one [step] up or down; a move that would leave 0.01..999.75 does nothing. */
fun stepWeight(value: Int, step: Int, up: Boolean): Int {
    val next = if (up) value + step else value - step
    return if (next in 1..WeightConfig.MAX_WEIGHT) next else value
}

/**
 * ± on a picker (plan Spec note 28): the next or previous of [options] (ascending), stopping at the
 * ends. A [value] not in the list goes to the nearest entry in that direction; null goes to the first.
 * Null only when there are no options.
 */
fun stepAlong(options: List<Int>, value: Int?, up: Boolean): Int? {
    if (options.isEmpty()) return null
    if (value == null) return options.first()
    return if (up) options.firstOrNull { it > value } ?: options.last() else options.lastOrNull { it < value } ?: options.first()
}

/**
 * A Top typed in the dialog (plan Spec note 30): snapped down to [start] + a whole number of [step]s, at
 * least one step above the start. Left as typed when it isn't above the start (the Top override then
 * lowers the start) or the step isn't a choice (the validator flags it).
 */
fun snapTop(top: Int, start: Int, step: Int): Int {
    if (step !in WeightValidator.STEP_CHOICES || top <= start) return top
    return start + ((top - start) / step).coerceAtLeast(1) * step
}
