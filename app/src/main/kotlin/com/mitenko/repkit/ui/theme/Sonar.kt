package com.mitenko.repkit.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Sonar's palette (spec rev 36 §3.1; prepare moved to #6A9FE0 for the 30° hue rule, plan ruling 2). */
object SonarColors {
    val Ground = Color(0xFF071014)
    val Panel = Color(0xFF0F292E)
    val PanelCentre = Color(0xFF123B3D)
    val Outline = Color(0xFF23A492)
    val Accent = Color(0xFF2EE6C8)
    val OnAccent = Color(0xFF04201C)
    val Text = Color(0xFFE8F4F2)
    val SecondaryText = Color(0xFF8FB3AE)
    val Error = Color(0xFFF0605D)
    val Work = Color(0xFF2EE6C8)
    val Rest = Color(0xFFF08A6B)
    val Prepare = Color(0xFF6A9FE0)
    val SetRing = Color(0xFF22D3EE)
    val Track = Color(0xFF12302F)
}

/** What the glow modifiers draw with (spec rev 36 §4); provided only in Sonar. */
@Immutable
data class GlowTokens(val panel: Color, val panelCentre: Color, val outline: Color, val accent: Color)

val SonarGlow = GlowTokens(
    panel = SonarColors.Panel, panelCentre = SonarColors.PanelCentre, outline = SonarColors.Outline, accent = SonarColors.Accent,
)

/** Sonar as a Material scheme: chips, buttons, switches and dialogs take it from here (plan ruling 3). */
internal val SonarScheme = darkColorScheme(
    primary = SonarColors.Accent,
    onPrimary = SonarColors.OnAccent,
    primaryContainer = SonarColors.PanelCentre,
    onPrimaryContainer = SonarColors.Text,
    secondary = SonarColors.SetRing,
    onSecondary = SonarColors.OnAccent,
    secondaryContainer = SonarColors.PanelCentre,
    onSecondaryContainer = SonarColors.Text,
    background = SonarColors.Ground,
    onBackground = SonarColors.Text,
    surface = SonarColors.Ground,
    onSurface = SonarColors.Text,
    surfaceVariant = SonarColors.Panel,
    onSurfaceVariant = SonarColors.SecondaryText,
    surfaceContainer = SonarColors.Panel,
    surfaceContainerHigh = SonarColors.Panel,
    surfaceContainerHighest = SonarColors.PanelCentre,
    outline = SonarColors.Outline,
    outlineVariant = SonarColors.Outline.copy(alpha = 0.5f),
    error = SonarColors.Error,
)
