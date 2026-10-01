package com.mitenko.hiitcounter.ui.entry

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
}
