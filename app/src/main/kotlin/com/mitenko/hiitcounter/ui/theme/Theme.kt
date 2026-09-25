package com.mitenko.hiitcounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun HiitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
