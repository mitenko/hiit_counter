package com.mitenko.repkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HiitColors {
    val Work = Color(0xFF8BC34A)
    val Rest = Color(0xFFFFB300)
    val Neutral = Color(0xFF78909C)
    val SetRing = Color(0xFF4FC3F7)
    val Track = Color(0xFF1E2A30)
}

/**
 * Dark palette. The app's accent is teal (user, 2026-10-01: the lime Work green didn't sit well
 * on the blue-slate background); the timer keeps [HiitColors.Work] for its work phase so work and
 * rest still read green and amber. The containers are set too, so selected chips and buttons are a
 * teal tint instead of Material's default lavender. 0xFF4DB6AC against black text is about 8.6:1.
 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFA7F3EA),
    secondaryContainer = Color(0xFF1E3B3A),
    onSecondaryContainer = Color(0xFFB2DFDB),
    secondary = HiitColors.SetRing,
    background = Color(0xFF12181B),
    surface = Color(0xFF12181B),
    error = Color(0xFFEF5350),
)

/**
 * Light palette (spec rev 14 §2): a darker teal and SetRing blue keep at least 4.5:1 text contrast
 * on a near-white background (0xFF00796B against white text is about 5.3:1). The timer screen
 * stays on [DarkScheme] regardless - see [HiitTheme].
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201D),
    secondaryContainer = Color(0xFFC8E6E2),
    onSecondaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF0277BD),
    background = Color(0xFFF7F9F8),
    surface = Color(0xFFF7F9F8),
    surfaceContainer = Color(0xFFE9EEEC),
    surfaceContainerHighest = Color(0xFFDCE3E0),
    error = Color(0xFFC62828),
)

/** Spec rev 14 §1: [darkTheme] is driven by the user's Appearance choice, defaulting to the phone's setting. */
@Composable
fun HiitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
}
