package com.mitenko.repkit.ui.timer

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * Thick inner ring = current phase; thin outer ring = work-set progress (spec §9.2). In Sonar each arc has
 * a blurred halo under it (rev 36 §4, API 28+).
 */
@Composable
fun DualRing(
    inner: Float,
    outer: Float,
    innerColor: Color,
    outerColor: Color,
    trackColor: Color,
    glow: Boolean,
    modifier: Modifier = Modifier,
) {
    val innerAnim = remember { Animatable(inner) }
    val outerAnim = remember { Animatable(outer) }
    // Sweep smoothly while counting down / filling up; snap on phase or set resets.
    LaunchedEffect(inner) {
        if (inner < innerAnim.value) innerAnim.animateTo(inner, tween(900, easing = LinearEasing)) else innerAnim.snapTo(inner)
    }
    LaunchedEffect(outer) {
        if (outer > outerAnim.value) outerAnim.animateTo(outer, tween(900, easing = LinearEasing)) else outerAnim.snapTo(outer)
    }
    val outerHalo = remember { HaloPaint() }
    val innerHalo = remember { HaloPaint() }
    val haloOn = glow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    Canvas(modifier) {
        val d = size.minDimension
        val outerStroke = d * 0.02f
        val innerStroke = d * 0.07f
        val outerInset = outerStroke / 2
        val innerInset = outerStroke + d * 0.03f + innerStroke / 2
        ring(trackColor, 1f, outerInset, outerStroke)
        if (haloOn) halo(outerHalo, outerColor, outerAnim.value, outerInset, outerStroke)
        ring(outerColor, outerAnim.value, outerInset, outerStroke)
        ring(trackColor, 1f, innerInset, innerStroke)
        if (haloOn) halo(innerHalo, innerColor, innerAnim.value, innerInset, innerStroke)
        ring(innerColor, innerAnim.value, innerInset, innerStroke)
    }
}

private fun DrawScope.ring(color: Color, fraction: Float, inset: Float, stroke: Float) {
    if (fraction <= 0f) return
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * fraction.coerceAtMost(1f),
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

/** One reusable halo paint per ring; its blur filter is rebuilt only when that ring's stroke changes (a resize). */
private class HaloPaint {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    private var blur = -1f

    fun update(color: Color, stroke: Float) {
        paint.strokeWidth = stroke * 1.8f
        paint.color = color.copy(alpha = 0.55f).toArgb()
        if (blur != stroke) {
            blur = stroke
            paint.maskFilter = BlurMaskFilter(stroke, BlurMaskFilter.Blur.NORMAL)
        }
    }
}

private fun DrawScope.halo(halo: HaloPaint, color: Color, fraction: Float, inset: Float, stroke: Float) {
    if (fraction <= 0f) return
    halo.update(color, stroke)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawArc(
            inset, inset, size.width - inset, size.height - inset,
            -90f, 360f * fraction.coerceAtMost(1f), false, halo.paint,
        )
    }
}
