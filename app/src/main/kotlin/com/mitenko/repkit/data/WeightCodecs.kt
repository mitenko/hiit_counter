package com.mitenko.repkit.data

import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps

/**
 * The text columns of the weight group (spec rev 26 §5, plan Spec note 2), all in integer hundredths:
 * `weight_steps` "start:step:top", `weight_list` "800,1200,1600" and `weight_holds`
 * "weight:reps:for,…". Each column's '' default means "nothing written yet": the default steps, an
 * empty list, no holds. Values are range-checked by EntryMapping / WeightValidator, not here.
 */
internal object WeightCodecs {
    fun encodeSteps(s: WeightSteps): String = "${s.start}:${s.step}:${s.top}"

    /** "" reads as [WeightSteps.DEFAULT]; anything but three whole numbers is null. */
    fun decodeSteps(text: String): WeightSteps? {
        if (text.isEmpty()) return WeightSteps.DEFAULT
        val parts = text.split(":")
        if (parts.size != 3) return null
        val (start, step, top) = parts.map { it.toIntOrNull() ?: return null }
        return WeightSteps(start, step, top)
    }

    fun encodeList(list: List<Int>): String = list.joinToString(",")

    /** "" is the empty list; one malformed item makes the whole list null. */
    fun decodeList(text: String): List<Int>? =
        if (text.isEmpty()) emptyList() else text.split(",").map { it.toIntOrNull() ?: return null }

    fun encodeHolds(holds: List<WeightHold>): String = holds.joinToString(",") { "${it.weight}:${it.reps}:${it.forCount}" }

    /** Item by item, like HoldsCodec: a malformed item, or one with weight < 1, reps < 1 or for < 0, is dropped. */
    fun decodeHolds(text: String): List<WeightHold> =
        if (text.isEmpty()) emptyList() else text.split(",").mapNotNull(::decodeHold)

    private fun decodeHold(item: String): WeightHold? {
        val parts = item.split(":")
        if (parts.size != 3) return null
        val weight = parts[0].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val reps = parts[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val forCount = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return WeightHold(weight, reps, forCount)
    }
}
