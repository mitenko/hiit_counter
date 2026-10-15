package com.mitenko.repkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

/** The theme being drawn (spec rev 36): the timer reads it to pick its own theme ([timerTheme]). */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.LIGHT }

/** Sonar's glow tokens; null in Light and Dark, so the glow modifiers do nothing there (spec rev 36 §4). */
val LocalGlow = staticCompositionLocalOf<GlowTokens?> { null }

/** The timer screen's colours (spec rev 36 §3). */
val LocalTimerColors = staticCompositionLocalOf { DarkTimerColors }

/** Spec rev 14 §1: [darkTheme] is driven by the user's Appearance choice, defaulting to the phone's setting. */
@Composable
fun HiitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) =
    HiitTheme(if (darkTheme) AppTheme.DARK else AppTheme.LIGHT, content)

/** Spec rev 36 §3: the scheme, the glow and the timer colours for [theme]. */
@Composable
fun HiitTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        AppTheme.LIGHT -> LightScheme
        AppTheme.DARK -> DarkScheme
        AppTheme.SONAR -> SonarScheme
    }
    CompositionLocalProvider(
        LocalAppTheme provides theme,
        LocalGlow provides if (theme == AppTheme.SONAR) SonarGlow else null,
        LocalTimerColors provides if (theme == AppTheme.SONAR) SonarTimerColors else DarkTimerColors,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
