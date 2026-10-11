package com.mitenko.repkit.ui.entry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.nearestPoint
import com.mitenko.repkit.ui.common.DateFormats
import com.mitenko.repkit.ui.common.PlotPoint
import com.mitenko.repkit.ui.common.resolve
import com.mitenko.repkit.ui.common.stepVertices
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** The chart's fixed geometry (spec rev 9 §3). Tests use it to find a point on screen. */
internal object ChartInsets {
    /** Room for the y labels. */
    val left = 40.dp
    val right = 12.dp

    /** Room for a point label above the highest point. */
    val top = 32.dp

    /** Room for the date labels. */
    val bottom = 24.dp

    /** The centre row's height: the chart, the calendar and the reps column (rev 9 §3: about 240 dp). */
    val height = 240.dp

    /** Spec R6 §4.2 / rev 9 §3: a tap within this distance of a point selects it. */
    val touch = 24.dp
    val labelGap = 8.dp
}

/**
 * The Counter chart (spec R6 §4.2, placed by rev 9 §3): [series] plotted by `at` from [start] to [end].
 * The y labels come from the series' axis and the x labels are dates, both in the theme's colours; the
 * line and dots are primary. Rep totals (Reps mode) are straight segments, as before; weights are a step
 * line in the workout's unit (spec rev 26 §4). Tapping within 24 dp of a point labels it ("12 Sep · 62",
 * or "12 Sep · 16 kg × 10"); tapping anywhere else on the chart clears the label. [HistoryLayout] and
 * [ChartSeries] do the maths.
 */
@Composable
fun WorkoutChart(series: ChartSeries, start: Instant, end: Instant, zone: ZoneId, modifier: Modifier = Modifier) {
    val points = series.points
    val values = series.values
    val axis = remember(series) { series.axis() }
    val ticks = remember(start, end, zone) { HistoryLayout.dateTicks(start, end, zone) }
    // Plan Spec note 8: the range's first value to its last one.
    val description = when (series) {
        is ChartSeries.Reps -> pluralStringResource(R.plurals.chart_desc, points.size, points.size, values.first(), values.last())
        is ChartSeries.Weights -> pluralStringResource(
            R.plurals.chart_desc_weight, points.size, points.size,
            series.valueText(values.first()).resolve(), series.valueText(values.last()).resolve(),
        )
    }
    val density = LocalDensity.current
    val left = with(density) { ChartInsets.left.toPx() }
    val right = with(density) { ChartInsets.right.toPx() }
    val top = with(density) { ChartInsets.top.toPx() }
    val bottom = with(density) { ChartInsets.bottom.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val xs = remember(points, start, end, canvasSize) {
        points.map { HistoryLayout.x(it.at, start, end, left, canvasSize.width - right) }
    }
    val ys = remember(values, axis, canvasSize) { values.map { HistoryLayout.y(it, axis, top, canvasSize.height - bottom) } }
    // Spec rev 26 §4: weights step from one point to the next; rep totals keep their straight segments.
    val path = remember(xs, ys, series.stepped) {
        val plotted = xs.indices.map { PlotPoint(xs[it], ys[it]) }
        if (series.stepped) stepVertices(plotted) else plotted
    }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Box(modifier.fillMaxWidth().height(ChartInsets.height).semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .testTag("chart")
                .pointerInput(xs) {
                    detectTapGestures { tap -> selected = nearestPoint(tap.x, xs, ChartInsets.touch.toPx()) }
                },
        ) {
            val plotBottom = size.height - bottom
            val plotRight = size.width - right
            axis.ticks.forEach { tick ->
                val y = HistoryLayout.y(tick, axis, top, plotBottom)
                drawLine(grid, Offset(left, y), Offset(plotRight, y))
                val text = measurer.measure(series.tickLabel(tick), labelStyle)
                drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y - text.size.height / 2f))
            }
            ticks.forEach { (fraction, label) ->
                val text = measurer.measure(label, labelStyle)
                val x = left + (plotRight - left) * fraction
                val textLeft = (x - text.size.width / 2f).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                drawText(text, topLeft = Offset(textLeft, plotBottom + 4.dp.toPx()))
            }
            for (i in 1 until path.size) {
                drawLine(line, Offset(path[i - 1].x, path[i - 1].y), Offset(path[i].x, path[i].y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            xs.indices.forEach { drawCircle(line, 4.dp.toPx(), Offset(xs[it], ys[it])) }
            selected?.let { drawCircle(line, 7.dp.toPx(), Offset(xs[it], ys[it]), style = Stroke(2.dp.toPx())) }
        }
        selected?.let { i ->
            val label = when (series) {
                is ChartSeries.Reps -> HistoryLayout.pointLabel(points[i], zone)
                // Plan Spec note 54: the recorded reps with the weight in the workout's unit.
                is ChartSeries.Weights -> "${DateFormats.dayMonth(points[i].at, zone)} · ${series.load(i).resolve()}"
            }
            PointLabel(label, xs[i], ys[i], canvasSize.width)
        }
    }
}

/** The tapped point's label, centred above it and kept inside the chart. */
@Composable
private fun PointLabel(text: String, x: Float, y: Float, maxWidth: Int) {
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .offset {
                IntOffset(
                    (x - labelSize.width / 2f).roundToInt().coerceIn(0, (maxWidth - labelSize.width).coerceAtLeast(0)),
                    (y - labelSize.height - ChartInsets.labelGap.toPx()).roundToInt().coerceAtLeast(0),
                )
            }
            .onSizeChanged { labelSize = it }
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("point_label"),
    )
}
