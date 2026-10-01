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

private val DarkScheme = darkColorScheme(
    primary = HiitColors.Work,
    onPrimary = Color.Black,
    secondary = HiitColors.SetRing,
    background = Color(0xFF12181B),
    surface = Color(0xFF12181B),
    error = Color(0xFFEF5350),
)

/**
 * Light palette (spec rev 14 §2): darker Work/SetRing tones keep at least 4.5:1 text contrast on a
 * near-white background, and the darker green stays easy to tell apart from the unchanged amber
 * Rest colour (timer screen only, which stays on [DarkScheme] regardless - see [HiitTheme]).
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF4C7A29),
    onPrimary = Color.White,
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
