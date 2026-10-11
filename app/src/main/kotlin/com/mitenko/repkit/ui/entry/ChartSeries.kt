package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.domain.Axis
import com.mitenko.repkit.domain.Prescription
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.niceAxis
import com.mitenko.repkit.domain.weightIn
import com.mitenko.repkit.ui.common.UiText
import com.mitenko.repkit.ui.common.loadText
import com.mitenko.repkit.ui.common.weightText

/** One unit either side of a flat weight line, in hundredths (plan Spec note 54). */
private const val WEIGHT_AXIS_PAD = 100

/** The smallest weight axis step, 0.1 in hundredths, so a tick label has at most one decimal. */
private const val WEIGHT_AXIS_MIN_STEP = 10

/**
 * What the Counter chart plots (spec R6 §4.2, rev 26 §4 and §9.4): [points] (oldest first, all of one
 * mode family) as [values] on the y axis. Kept pure so it's tested without a Canvas.
 */
sealed interface ChartSeries {
    val points: List<CheckInPoint>
    val values: List<Int>

    /** True for a step line (weights), false for today's straight segments (rep totals). */
    val stepped: Boolean

    fun axis(): Axis

    fun tickLabel(tick: Int): String

    /** Reps mode, exactly as before: rep totals on a straight line. */
    data class Reps(override val points: List<CheckInPoint>) : ChartSeries {
        init {
            require(points.isNotEmpty()) { "ChartSeries needs at least one point" }
        }
        override val values: List<Int> = points.map { requireNotNull(it.total) { "A chart point needs a total" } }
        override val stepped: Boolean get() = false

        override fun axis(): Axis = niceAxis(values.min(), values.max())

        override fun tickLabel(tick: Int): String = "$tick"
    }

    /** A weight mode: each point's weight in the workout's [unit] (§9.3, plan Spec note 54), as a step line. */
    data class Weights(override val points: List<CheckInPoint>, val unit: WeightUnit) : ChartSeries {
        init {
            require(points.isNotEmpty()) { "ChartSeries needs at least one point" }
        }
        override val values: List<Int> = points.map { requireNotNull(it.weightIn(unit)) { "A weight chart point needs a weight" } }
        override val stepped: Boolean get() = true

        override fun axis(): Axis = niceAxis(values.min(), values.max(), pad = WEIGHT_AXIS_PAD, minMagnitude = WEIGHT_AXIS_MIN_STEP)

        override fun tickLabel(tick: Int): String = WeightFormat.format(tick)

        /** "16 kg × 10": point [index]'s converted weight with the reps it recorded. */
        fun load(index: Int): UiText =
            loadText(Prescription.Load(values[index], requireNotNull(points[index].reps) { "A weight point needs reps" }), unit)

        /** "16 kg", for the chart's description. */
        fun valueText(hundredths: Int): UiText = weightText(hundredths, unit)
    }
}
