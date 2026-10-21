package com.mitenko.repkit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.ui.timer.TimerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HiitThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `darkTheme false provides the light background colour`() {
        var background by mutableStateOf(Color.Unspecified)
        compose.setContent {
            HiitTheme(darkTheme = false) { background = MaterialTheme.colorScheme.background }
        }
        assertEquals(Color(0xFFF7F9F8), background)
    }

    @Test
    fun `darkTheme true provides the dark background colour`() {
        var background by mutableStateOf(Color.Unspecified)
        compose.setContent {
            HiitTheme(darkTheme = true) { background = MaterialTheme.colorScheme.background }
        }
        assertEquals(Color(0xFF12181B), background)
    }

    @Test
    fun `Sonar provides its ground, glow and timer colours`() {
        var background by mutableStateOf(Color.Unspecified)
        var glow: GlowTokens? = null
        var timer: TimerColors? = null
        var theme: AppTheme? = null
        compose.setContent {
            HiitTheme(AppTheme.SONAR) {
                background = MaterialTheme.colorScheme.background
                glow = LocalGlow.current
                timer = LocalTimerColors.current
                theme = LocalAppTheme.current
            }
        }
        assertEquals(Color(0xFF071014), background)
        assertEquals(SonarGlow, glow)
        assertEquals(SonarTimerColors, timer)
        assertEquals(AppTheme.SONAR, theme)
    }

    @Test
    fun `Dark has no glow and keeps the HiitColors timer`() {
        var glow: GlowTokens? = SonarGlow
        var timer: TimerColors? = null
        compose.setContent {
            HiitTheme(darkTheme = true) {
                glow = LocalGlow.current
                timer = LocalTimerColors.current
            }
        }
        assertNull(glow)
        assertEquals(DarkTimerColors, timer)
        assertEquals(HiitColors.Work, DarkTimerColors.work)
        assertEquals(Color.Black, DarkTimerColors.ground)
    }

    @Test
    fun `each glowing theme provides its own ground, glow and timer colours`() {
        val themes = listOf(AppTheme.BLOSSOM to BlossomPalette, AppTheme.GROVE to GrovePalette, AppTheme.FOUNDRY to FoundryPalette)
        val seen = mutableMapOf<AppTheme, Triple<Color, GlowTokens?, TimerColors>>()
        compose.setContent {
            themes.forEach { (t, _) ->
                HiitTheme(t) { seen[t] = Triple(MaterialTheme.colorScheme.background, LocalGlow.current, LocalTimerColors.current) }
            }
        }
        compose.waitForIdle()
        themes.forEach { (t, p) ->
            assertEquals("$t", Triple(p.ground, p.glow(), p.timerColors()), seen[t])
        }
    }

    @Test
    fun `a locked Pro theme on FREE draws as Dark, timer included, and as itself on PRO`() {
        var app: AppTheme? = null
        var timer: TimerColors? = null
        var proApp: AppTheme? = null
        var proTimer: TimerColors? = null
        compose.setContent {
            HiitTheme(effectiveTheme(ThemeMode.BLOSSOM, systemDark = false, tier = Tier.FREE)) {
                app = LocalAppTheme.current
                TimerTheme { timer = LocalTimerColors.current }
            }
            HiitTheme(effectiveTheme(ThemeMode.BLOSSOM, systemDark = false, tier = Tier.PRO)) {
                proApp = LocalAppTheme.current
                TimerTheme { proTimer = LocalTimerColors.current }
            }
        }
        compose.waitForIdle()
        assertEquals(AppTheme.DARK, app)
        assertEquals(DarkTimerColors, timer)
        assertEquals(AppTheme.BLOSSOM, proApp)
        assertEquals(BlossomPalette.timerColors(), proTimer)
    }
}
