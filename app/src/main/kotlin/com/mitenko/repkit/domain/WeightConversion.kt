package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind

/** kg ↔ lb (spec rev 26 §2 Units), on integer hundredths. Pure. */
object WeightConversion {
    const val LB_PER_KG = 2.20462
    private const val QUARTER = 25

    /** [hundredths] from [from] to [to], to the nearest 0.25 (half up), kept within 0.25..999.75. The same unit returns it as is. */
    fun convert(hundredths: Int, from: WeightUnit, to: WeightUnit): Int {
        if (from == to) return hundredths
        val raw = if (to == WeightUnit.LB) hundredths * LB_PER_KG else hundredths / LB_PER_KG
        return (roundHalfUp(raw / QUARTER) * QUARTER).coerceIn(QUARTER, WeightConfig.MAX_WEIGHT)
    }

    /**
     * [config] in [to] (spec rev 26 §2 Units, §9.2): the weights, the starting weight and every hold
     * convert with the same rounding, so the remap that follows compares exact values. Steps become
     * My weights, because a converted step isn't one of the step choices (plan Spec note 8). Weights that
     * round to one value collapse, keeping the first. A config without a unit only gains [to].
     */
    fun convert(config: WeightConfig, to: WeightUnit): WeightConfig {
        val from = config.unit ?: return config.copy(unit = to)
        if (from == to) return config
        fun c(v: Int) = convert(v, from, to)
        return config.copy(
            unit = to,
            kind = WeightsKind.LIST,
            list = config.weights.map(::c).distinct(),
            startWeight = config.startWeight?.let(::c),
            holds = config.holds.map { it.copy(weight = c(it.weight)) },
        )
    }
}
