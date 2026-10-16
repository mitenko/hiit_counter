package com.mitenko.repkit.ui.theme

import androidx.compose.ui.graphics.Color

/** Sonar's palette (spec rev 36 §3.1; amber rest and sky-blue prepare chosen by the user on 2026-10-16). */
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
    val Rest = Color(0xFFFFB547)
    val Prepare = Color(0xFF5AB4FF)
    val SetRing = Color(0xFF22D3EE)
    val Track = Color(0xFF12302F)
}

val SonarPalette = ThemePalette(
    ground = SonarColors.Ground, panel = SonarColors.Panel, panelCentre = SonarColors.PanelCentre,
    outline = SonarColors.Outline, accent = SonarColors.Accent, onAccent = SonarColors.OnAccent,
    text = SonarColors.Text, secondaryText = SonarColors.SecondaryText, error = SonarColors.Error,
    work = SonarColors.Work, rest = SonarColors.Rest, prepare = SonarColors.Prepare,
    setRing = SonarColors.SetRing, track = SonarColors.Track,
    panelHalo = 0.35f, edgeHalo = 0.12f, ringHalo = 0.55f, panelCorner = null,
)

val SonarGlow = SonarPalette.glow()
