package com.mitenko.hiitcounter.domain

object RepDistributor {
    /** Splits [total] across [sets]; the first `total % sets` sets get one extra rep. */
    fun distribute(total: Int, sets: Int): List<Int> {
        require(sets >= 1) { "sets must be >= 1, was $sets" }
        require(total >= 0) { "total must be >= 0, was $total" }
        val base = total / sets
        val extra = total % sets
        return List(sets) { index -> if (index < extra) base + 1 else base }
    }
}
