package com.mitenko.hiitcounter.ui.entries

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.model.EntryType

private val TILE_WIDTH = 96.dp
private val TILE_HEIGHT = 32.dp
private val LINE_WIDTH = 1.5.dp
private val POINT_RADIUS = 2.dp
private val DAY_CIRCLE = 18.dp
private val DAY_SPACING = 2.dp
private val DAY_LETTER_SIZE = 10.dp
private val DAY_OUTLINE_WIDTH = 1.dp

/**
 * A list tile's graph (spec rev 9 §2, amended rev 11 §2). A Workout draws a 96 × 32 dp sparkline of
 * its totals over the 28-day window with a dot on each point (one point draws one dot, none draws
 * nothing). A Timer only entry draws a row of 7 lettered circles for the current calendar week
 * (Mon–Sun), filled for a checked-in day and outlined for the rest. Decorative: no touch target,
 * one content description. The positions come from [TileLayout].
 */
@Composable
fun TileGraphic(type: EntryType, entryId: Long, tile: TileData, modifier: Modifier = Modifier) {
    when (type) {
        EntryType.WORKOUT -> {
            val description = pluralStringResource(R.plurals.tile_desc, tile.count, tile.count)
            val filled = MaterialTheme.colorScheme.primary
            Canvas(
                modifier
                    .size(TILE_WIDTH, TILE_HEIGHT)
                    .testTag("tile_$entryId")
                    .semantics { contentDescription = description },
            ) {
                drawSparkline(tile.spark, filled)
            }
        }
        EntryType.CHECK_IN -> WeekRow(entryId, tile.week, modifier)
    }
}

@Composable
private fun WeekRow(entryId: Long, week: List<Boolean>, modifier: Modifier) {
    val letters = stringArrayResource(R.array.week_day_letters)
    val abbrev = stringArrayResource(R.array.week_day_abbrev)
    val filled = MaterialTheme.colorScheme.primary
    val onFilled = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline
    val onHollow = MaterialTheme.colorScheme.onSurfaceVariant
    val checkedDays = week.indices.filter { week[it] }.map { abbrev[it] }
    val description = if (checkedDays.isEmpty()) {
        stringResource(R.string.week_no_check_in_desc)
    } else {
        stringResource(R.string.week_check_in_desc, checkedDays.joinToString(", "))
    }
    val checkedInDesc = stringResource(R.string.day_checked_in)
    val notCheckedInDesc = stringResource(R.string.day_not_checked_in)
    // A size in dp-to-sp, so the letters don't blow the 18 dp circles out at a large font scale.
    val letterSize = with(LocalDensity.current) { DAY_LETTER_SIZE.toSp() }
    Row(
        modifier
            .testTag("week_$entryId")
            // The card is clickable, which merges descendants into one announced node (spec rev
            // 11 §2). clearAndSetSemantics stops the circles' own text and state from bubbling up
            // past the row: only this contentDescription is what a screen reader hears, while the
            // circles (below this boundary) stay fully present for the unmerged tree tests query.
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(DAY_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        week.forEachIndexed { index, checkedIn ->
            Box(
                Modifier
                    .size(DAY_CIRCLE)
                    .then(
                        if (checkedIn) {
                            Modifier.background(filled, CircleShape)
                        } else {
                            Modifier.border(DAY_OUTLINE_WIDTH, outline, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // The tag, text and state live on the Text node itself: a wrapping Box's own
                // semantics wouldn't carry the text up in the unmerged tree the tests query. The
                // Row's clearAndSetSemantics above keeps this from merging any further up.
                Text(
                    letters[index],
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = letterSize,
                    color = if (checkedIn) onFilled else onHollow,
                    modifier = Modifier
                        .testTag("day_${entryId}_$index")
                        .semantics { stateDescription = if (checkedIn) checkedInDesc else notCheckedInDesc },
                )
            }
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
