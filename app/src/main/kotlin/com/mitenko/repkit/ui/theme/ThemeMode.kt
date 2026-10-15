package com.mitenko.repkit.ui.theme

/** The user's Appearance choice (spec rev 14 §1, rev 36 §2, rev 39 §10): SYSTEM follows the phone; the rest override it. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, SONAR, BLOSSOM, GROVE, FOUNDRY }

/** The theme actually drawn (spec rev 36 §2): [ThemeMode] with SYSTEM resolved against the phone. */
enum class AppTheme { LIGHT, DARK, SONAR, BLOSSOM, GROVE, FOUNDRY }

/** Pure: the theme drawn for the user's [mode], given the phone's [systemDark] setting. */
fun effectiveTheme(mode: ThemeMode, systemDark: Boolean): AppTheme = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
    ThemeMode.LIGHT -> AppTheme.LIGHT
    ThemeMode.DARK -> AppTheme.DARK
    ThemeMode.SONAR -> AppTheme.SONAR
    ThemeMode.BLOSSOM -> AppTheme.BLOSSOM
    ThemeMode.GROVE -> AppTheme.GROVE
    ThemeMode.FOUNDRY -> AppTheme.FOUNDRY
}

/** Pure: whether the effective theme is dark (every glowing theme is dark). */
fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = effectiveTheme(mode, systemDark) != AppTheme.LIGHT

/** The timer stays dark in Light and Dark (spec rev 14 §3) and takes a glowing theme's own colours in it (rev 36 §3). */
fun timerTheme(theme: AppTheme): AppTheme = if (theme == AppTheme.LIGHT) AppTheme.DARK else theme
