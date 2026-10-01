package com.mitenko.repkit.ui.theme

/** The user's Appearance choice (spec rev 14 §1): SYSTEM follows the phone; LIGHT/DARK override it. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Pure: whether the effective theme is dark, given the user's [mode] and the phone's [systemDark] setting. */
fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
