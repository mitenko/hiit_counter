package com.mitenko.repkit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One glowing dark theme's tokens (spec rev 36 §3.1, rev 39 §10): the colours, how strongly its panels,
 * screen edge and timer rings glow (alphas of the accent / arc colour), and [panelCorner], the radius every
 * panel takes in it (null keeps each panel's own shape).
 */
@Immutable
data class ThemePalette(
    val ground: Color,
    val panel: Color,
    val panelCentre: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val secondaryText: Color,
    val error: Color,
    val work: Color,
    val rest: Color,
    val prepare: Color,
    val setRing: Color,
    val track: Color,
    val panelHalo: Float,
    val edgeHalo: Float,
    val ringHalo: Float,
    val panelCorner: Dp?,
)

/** What the glow modifiers and the timer ring draw with (spec rev 36 §4); provided only in a glowing theme. */
@Immutable
data class GlowTokens(
    val panel: Color,
    val panelCentre: Color,
    val outline: Color,
    val accent: Color,
    val panelHalo: Float,
    val edgeHalo: Float,
    val ringHalo: Float,
    val panelCorner: Dp?,
)

fun ThemePalette.glow() = GlowTokens(panel, panelCentre, outline, accent, panelHalo, edgeHalo, ringHalo, panelCorner)

/** A palette as a Material scheme: chips, buttons, switches and dialogs take it from here (rev 36 plan ruling 3). */
fun ThemePalette.scheme(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = panelCentre,
    onPrimaryContainer = text,
    secondary = setRing,
    onSecondary = onAccent,
    secondaryContainer = panelCentre,
    onSecondaryContainer = text,
    background = ground,
    onBackground = text,
    surface = ground,
    onSurface = text,
    surfaceVariant = panel,
    onSurfaceVariant = secondaryText,
    surfaceContainer = panel,
    surfaceContainerHigh = panel,
    surfaceContainerHighest = panelCentre,
    outline = outline,
    outlineVariant = outline.copy(alpha = 0.5f),
    error = error,
)

/** The timer's colours in a glowing theme (spec rev 36 §3.2): its own ground, phases, ring and band. */
fun ThemePalette.timerColors() = TimerColors(
    ground = ground, text = text, work = work, rest = rest, neutral = prepare, setRing = setRing, track = track,
    bandLine = outline.copy(alpha = 0.53f), circleOutline = outline.copy(alpha = 0.6f),
)

/** Blossom (spec rev 39 §10.1): deep plum, petal pink, rounder panels; gold rest, lavender prepare. */
val BlossomPalette = ThemePalette(
    ground = Color(0xFF1A0E16), panel = Color(0xFF2A1724), panelCentre = Color(0xFF3A1F33),
    outline = Color(0xFFB5578E), accent = Color(0xFFFF8FC8), onAccent = Color(0xFF33081F),
    text = Color(0xFFFBEFF5), secondaryText = Color(0xFFCFA8BE), error = Color(0xFFFF6B6B),
    work = Color(0xFFFF8FC8), rest = Color(0xFFF5C26B), prepare = Color(0xFFB79CFF),
    setRing = Color(0xFFE9A8FF), track = Color(0xFF3A2133),
    panelHalo = 0.40f, edgeHalo = 0.13f, ringHalo = 0.60f, panelCorner = 22.dp,
)

/** Grove (spec rev 39 §10.1): forest greens, a moss accent, a gentler glow; amber rest, lichen prepare. */
val GrovePalette = ThemePalette(
    ground = Color(0xFF0D120C), panel = Color(0xFF18211A), panelCentre = Color(0xFF1F2D21),
    outline = Color(0xFF5C7F4A), accent = Color(0xFF9CCB6B), onAccent = Color(0xFF142008),
    text = Color(0xFFEEF0E6), secondaryText = Color(0xFFA9B49B), error = Color(0xFFE86A5A),
    work = Color(0xFF9CCB6B), rest = Color(0xFFE0A458), prepare = Color(0xFF6FC2A8),
    setRing = Color(0xFFD8C17A), track = Color(0xFF2A2A1E),
    panelHalo = 0.25f, edgeHalo = 0.09f, ringHalo = 0.40f, panelCorner = null,
)

/** Foundry (spec rev 39 §10.1): gunmetal and steel, square panels, almost no glow; safety yellow, signal blue, hazard red. */
val FoundryPalette = ThemePalette(
    ground = Color(0xFF111315), panel = Color(0xFF1C1F22), panelCentre = Color(0xFF262A2E),
    outline = Color(0xFF6B737A), accent = Color(0xFFF5C518), onAccent = Color(0xFF1A1500),
    text = Color(0xFFECEEF0), secondaryText = Color(0xFFA3AAB0), error = Color(0xFFFF5A4E),
    work = Color(0xFFF5C518), rest = Color(0xFF3FA7F0), prepare = Color(0xFFFF5E3A),
    setRing = Color(0xFFC9D1D8), track = Color(0xFF2A2E33),
    panelHalo = 0.12f, edgeHalo = 0.05f, ringHalo = 0.25f, panelCorner = 6.dp,
)

/** Pure: the palette drawn for [theme], or null for Light and Dark (which keep their own schemes). */
fun paletteFor(theme: AppTheme): ThemePalette? = when (theme) {
    AppTheme.LIGHT, AppTheme.DARK -> null
    AppTheme.SONAR -> SonarPalette
    AppTheme.BLOSSOM -> BlossomPalette
    AppTheme.GROVE -> GrovePalette
    AppTheme.FOUNDRY -> FoundryPalette
}
