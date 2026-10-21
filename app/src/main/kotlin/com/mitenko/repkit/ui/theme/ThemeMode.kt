package com.mitenko.repkit.ui.theme

import com.mitenko.repkit.domain.Tier

/** The user's Appearance choice (spec rev 14 §1, rev 36 §2, rev 39 §10): SYSTEM follows the phone; the rest override it. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, SONAR, BLOSSOM, GROVE, FOUNDRY }

/** The theme actually drawn (spec rev 36 §2): [ThemeMode] with SYSTEM resolved against the phone. */
enum class AppTheme { LIGHT, DARK, SONAR, BLOSSOM, GROVE, FOUNDRY }

/** Spec rev 41 §3.2: the themes that come with Pro. Light, Dark and Sonar stay free. */
fun isProTheme(mode: ThemeMode): Boolean = mode == ThemeMode.BLOSSOM || mode == ThemeMode.GROVE || mode == ThemeMode.FOUNDRY

/** Spec rev 41 §3.2: whether [tier] may draw [mode]. */
fun canUseTheme(tier: Tier, mode: ThemeMode): Boolean = tier == Tier.PRO || !isProTheme(mode)

/**
 * Pure: the theme drawn for the user's [mode], given the phone's [systemDark] setting and the [tier]
 * (spec rev 41 §3.2: a Pro theme on FREE draws as Dark; the stored choice is kept).
 */
fun effectiveTheme(mode: ThemeMode, systemDark: Boolean, tier: Tier): AppTheme {
    if (!canUseTheme(tier, mode)) return AppTheme.DARK
    return when (mode) {
        ThemeMode.SYSTEM -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
        ThemeMode.LIGHT -> AppTheme.LIGHT
        ThemeMode.DARK -> AppTheme.DARK
        ThemeMode.SONAR -> AppTheme.SONAR
        ThemeMode.BLOSSOM -> AppTheme.BLOSSOM
        ThemeMode.GROVE -> AppTheme.GROVE
        ThemeMode.FOUNDRY -> AppTheme.FOUNDRY
    }
}

/** The timer stays dark in Light and Dark (spec rev 14 §3) and takes a glowing theme's own colours in it (rev 36 §3). */
fun timerTheme(theme: AppTheme): AppTheme = if (theme == AppTheme.LIGHT) AppTheme.DARK else theme
