package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressionConfig

/** Which limit a Current total moved (spec revision 27); the UI turns it into a note. */
sealed interface RangeChange {
    /** Maximum reps (cap) raised to [to]. */
    data class RaisedMax(val to: Int) : RangeChange

    /** Minimum reps (floor) lowered to [to]. */
    data class LoweredMin(val to: Int) : RangeChange
}

/**
 * Spec revision 27: floor..cap widened just enough to include [total]; nothing else changes. A
 * total already inside the range returns this same config.
 */
fun ProgressionConfig.widenedFor(total: Int): ProgressionConfig = when {
    total > cap -> copy(cap = total)
    total < floor -> copy(floor = total)
    else -> this
}

/** What [widenedFor] moved from [old] to [new], or null when the range is unchanged. */
fun rangeChange(old: ProgressionConfig, new: ProgressionConfig): RangeChange? = when {
    new.cap > old.cap -> RangeChange.RaisedMax(new.cap)
    new.floor < old.floor -> RangeChange.LoweredMin(new.floor)
    else -> null
}
