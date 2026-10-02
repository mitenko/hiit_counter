package com.mitenko.repkit.ui.entry

/**
 * The entry screen's reps column (spec rev 9 §3, plan Spec note 18): one cell per set, and
 * [VISIBLE_ROWS] cells fill the chart's height, so the column scrolls only past that. Kept pure so
 * it's tested without Compose.
 */
object RepsColumnLayout {
    /** Cells that fit the column (the chart's 240 dp, 24 dp each) before it scrolls. */
    const val VISIBLE_ROWS = 10

    fun scrolls(sets: Int): Boolean = sets > VISIBLE_ROWS

    /** What screen readers hear after "Reps per set: " (spec rev 9 §3). */
    fun spoken(reps: List<Int>): String = reps.joinToString(", ")

    /**
     * A per-set rep change after a check-in (spec revision 12 §3): up on a gain, down on a drop.
     * HOLD (spec revision 20) marks every cell when a check-in kept the total on a hold, so the
     * column still answers the check-in with a neutral flash although no value changed.
     */
    enum class Change { UP, DOWN, HOLD }

    /**
     * Every index whose value differs between [before] and [after] (spec revision 12 §3). A size
     * mismatch (the set count changed) reports no change at all, rather than a misaligned diff.
     */
    fun changedSets(before: List<Int>, after: List<Int>): Map<Int, Change> {
        if (before.size != after.size) return emptyMap()
        return buildMap {
            for (i in before.indices) {
                when {
                    after[i] > before[i] -> put(i, Change.UP)
                    after[i] < before[i] -> put(i, Change.DOWN)
                }
            }
        }
    }
}
