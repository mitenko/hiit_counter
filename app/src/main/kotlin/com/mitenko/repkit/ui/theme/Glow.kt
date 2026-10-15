package com.mitenko.repkit.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A glowing panel (spec rev 36 §4): a soft accent glow outside (coloured shadows, API 28+, at the theme's
 * panel halo strength), a radial gradient from the panel centre to the panel colour, and a 1.5 dp outline.
 * Returns the receiver unchanged in Light and Dark. Pair it with [panelColor] for the panel's own fill, and
 * pass the [panelShape] of the panel's own shape.
 */
@Composable
fun Modifier.glowPanel(shape: Shape): Modifier {
    val glow = LocalGlow.current ?: return this
    val halo = glow.accent.copy(alpha = glow.panelHalo)
    val shadow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Modifier.shadow(12.dp, shape, clip = false, ambientColor = halo, spotColor = halo)
    } else {
        Modifier
    }
    return this
        .then(shadow)
        .background(Brush.radialGradient(listOf(glow.panelCentre, glow.panel)), shape)
        .border(1.5.dp, glow.outline, shape)
}

/** A panel's shape: [default], or the glowing theme's own corner radius (spec rev 39 §10: Blossom rounder, Foundry squarer). */
@Composable
fun panelShape(default: Shape): Shape {
    val corner = LocalGlow.current?.panelCorner ?: return default
    return remember(corner) { RoundedCornerShape(corner) }
}

/** A panel's own fill: [default] in Light and Dark; transparent in a glowing theme, so [glowPanel]'s gradient shows. */
@Composable
fun panelColor(default: Color): Color = if (LocalGlow.current == null) default else Color.Transparent

/** A glowing theme's faint inner glow along the screen edge (spec rev 36 §4): the accent (12% in Sonar) fading in over 24 dp. */
@Composable
fun Modifier.edgeGlow(): Modifier {
    val glow = LocalGlow.current ?: return this
    return drawWithCache {
        val edge = glow.accent.copy(alpha = glow.edgeHalo)
        val clear = Color.Transparent
        val w = 24.dp.toPx()
        val top = Brush.verticalGradient(listOf(edge, clear), startY = 0f, endY = w)
        val bottom = Brush.verticalGradient(listOf(clear, edge), startY = size.height - w, endY = size.height)
        val left = Brush.horizontalGradient(listOf(edge, clear), startX = 0f, endX = w)
        val right = Brush.horizontalGradient(listOf(clear, edge), startX = size.width - w, endX = size.width)
        onDrawWithContent {
            drawContent()
            drawRect(top, size = Size(size.width, w))
            drawRect(bottom, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
            drawRect(left, size = Size(w, size.height))
            drawRect(right, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
        }
    }
}
