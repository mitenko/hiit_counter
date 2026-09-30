package com.mitenko.hiitcounter.ui.entries

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.TILE_DAYS
import com.mitenko.hiitcounter.domain.model.EntryType
import kotlin.math.min

private val TILE_WIDTH = 96.dp
private val TILE_HEIGHT = 32.dp
private val LINE_WIDTH = 1.5.dp
private val POINT_RADIUS = 2.dp

/**
 * A list tile's graph (spec rev 9 §2), 96 × 32 dp. A Workout draws a sparkline of its totals over
 * the 28-day window with a dot on each point (one point draws one dot, none draws nothing). A Timer
 * only entry draws 28 day-dots, filled for a checked-in day and hollow for a missed one. Decorative:
 * no touch target, one content description. The positions come from [TileLayout].
 */
@Composable
fun TileGraphic(type: EntryType, tile: TileData, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.tile_desc, tile.count, tile.count)
    val filled = MaterialTheme.colorScheme.primary
    val hollow = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(TILE_WIDTH, TILE_HEIGHT).semantics { contentDescription = description }) {
        when (type) {
            EntryType.WORKOUT -> drawSparkline(tile.spark, filled)
            EntryType.CHECK_IN -> drawDayDots(tile.days, filled, hollow)
        }
    }
}

private fun DrawScope.drawSparkline(spark: List<SparkPoint>, color: Color) {
    val radius = POINT_RADIUS.toPx()
    val points = TileLayout.sparkline(spark, size.width, size.height, inset = radius)
    points.zipWithNext { a, b ->
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = LINE_WIDTH.toPx(), cap = StrokeCap.Round)
    }
    points.forEach { drawCircle(color, radius, Offset(it.x, it.y)) }
}

private fun DrawScope.drawDayDots(days: List<Boolean>, filled: Color, hollow: Color) {
    val radius = min(size.width / TILE_DAYS, size.height) * 0.4f
    days.forEachIndexed { index, checkedIn ->
        val center = Offset(TileLayout.dotX(index, size.width), size.height / 2)
        if (checkedIn) {
            drawCircle(filled, radius, center)
        } else {
            drawCircle(hollow, radius, center, style = Stroke(width = radius / 2))
        }
    }
}
