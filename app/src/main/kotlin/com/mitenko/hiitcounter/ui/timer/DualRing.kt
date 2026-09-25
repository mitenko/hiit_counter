package com.mitenko.hiitcounter.ui.timer

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
import com.mitenko.hiitcounter.ui.theme.HiitColors

/** Thick inner ring = current phase; thin outer ring = work-set progress (spec §9.2). */
@Composable
fun DualRing(inner: Float, outer: Float, innerColor: Color, outerColor: Color, modifier: Modifier = Modifier) {
    val innerAnim = remember { Animatable(inner) }
    val outerAnim = remember { Animatable(outer) }
    // Sweep smoothly while counting down / filling up; snap on phase or set resets.
    LaunchedEffect(inner) {
        if (inner < innerAnim.value) innerAnim.animateTo(inner, tween(900, easing = LinearEasing)) else innerAnim.snapTo(inner)
    }
    LaunchedEffect(outer) {
        if (outer > outerAnim.value) outerAnim.animateTo(outer, tween(900, easing = LinearEasing)) else outerAnim.snapTo(outer)
    }
    Canvas(modifier) {
        val d = size.minDimension
        val outerStroke = d * 0.02f
        val innerStroke = d * 0.07f
        val outerInset = outerStroke / 2
        val innerInset = outerStroke + d * 0.03f + innerStroke / 2
        ring(HiitColors.Track, 1f, outerInset, outerStroke)
        ring(outerColor, outerAnim.value, outerInset, outerStroke)
        ring(HiitColors.Track, 1f, innerInset, innerStroke)
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
