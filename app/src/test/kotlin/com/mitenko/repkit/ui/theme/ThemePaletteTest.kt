package com.mitenko.repkit.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec rev 36 §6, rev 39 §10: every glowing theme passes WCAG contrast and its timer phases are told apart by hue. */
class ThemePaletteTest {
    private val palettes = AppTheme.entries.mapNotNull { t -> paletteFor(t)?.let { t to it } }

    private fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
    private fun hue(c: Color): Double {
        val r = c.red.toDouble(); val g = c.green.toDouble(); val b = c.blue.toDouble()
        val mx = maxOf(r, g, b); val d = mx - minOf(r, g, b)
        if (d == 0.0) return 0.0
        val h = when (mx) { r -> ((g - b) / d).mod(6.0); g -> (b - r) / d + 2; else -> (r - g) / d + 4 }
        return h * 60
    }
    private fun hueGap(a: Color, b: Color) = abs(hue(a) - hue(b)).let { min(it, 360 - it) }

    private fun assertContrast(fg: Color, bg: Color, atLeast: Double, what: String) {
        val ratio = contrast(fg, bg)
        assertTrue("$what: $ratio < $atLeast", ratio >= atLeast)
    }

    @Test
    fun `every glowing theme has a palette and Light and Dark have none`() {
        assertEquals(listOf(AppTheme.SONAR, AppTheme.BLOSSOM, AppTheme.GROVE, AppTheme.FOUNDRY), palettes.map { it.first })
        assertNull(paletteFor(AppTheme.LIGHT))
        assertNull(paletteFor(AppTheme.DARK))
        assertEquals(SonarColors.Ground, SonarPalette.ground)
    }

    @Test
    fun `text reads on the ground and on both ends of a panel`() = palettes.forEach { (t, p) ->
        listOf(p.ground, p.panel, p.panelCentre).forEach { bg ->
            assertContrast(p.text, bg, 4.5, "$t text on $bg")
            assertContrast(p.secondaryText, bg, 4.5, "$t secondary text on $bg")
        }
        assertContrast(p.onAccent, p.accent, 4.5, "$t on-accent on accent")
    }

    @Test
    fun `the timer phases stand out from the ground and from each other`() = palettes.forEach { (t, p) ->
        val phases = listOf(p.work, p.rest, p.prepare)
        phases.forEach { assertContrast(it, p.ground, 3.0, "$t phase $it on ground") }
        for (i in phases.indices) for (j in i + 1 until phases.size) {
            val gap = hueGap(phases[i], phases[j])
            assertTrue("$t hue gap ${phases[i]} / ${phases[j]} = $gap", gap >= 30.0)
        }
    }

    @Test
    fun `glow strengths are alphas`() = palettes.forEach { (t, p) ->
        listOf(p.panelHalo, p.edgeHalo, p.ringHalo).forEach { assertTrue("$t glow $it", it in 0f..1f) }
    }
}
