package com.mitenko.repkit.ui.theme

import com.mitenko.repkit.domain.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `effectiveTheme resolves SYSTEM against the phone and keeps the rest`() {
        assertEquals(AppTheme.DARK, effectiveTheme(ThemeMode.SYSTEM, systemDark = true, tier = Tier.PRO))
        assertEquals(AppTheme.LIGHT, effectiveTheme(ThemeMode.SYSTEM, systemDark = false, tier = Tier.PRO))
        listOf(true, false).forEach { systemDark ->
            assertEquals(AppTheme.LIGHT, effectiveTheme(ThemeMode.LIGHT, systemDark, Tier.PRO))
            assertEquals(AppTheme.DARK, effectiveTheme(ThemeMode.DARK, systemDark, Tier.PRO))
            assertEquals(AppTheme.SONAR, effectiveTheme(ThemeMode.SONAR, systemDark, Tier.PRO))
            assertEquals(AppTheme.BLOSSOM, effectiveTheme(ThemeMode.BLOSSOM, systemDark, Tier.PRO))
            assertEquals(AppTheme.GROVE, effectiveTheme(ThemeMode.GROVE, systemDark, Tier.PRO))
            assertEquals(AppTheme.FOUNDRY, effectiveTheme(ThemeMode.FOUNDRY, systemDark, Tier.PRO))
        }
    }

    @Test
    fun `only Blossom, Grove and Foundry are Pro themes`() {
        assertEquals(
            setOf(ThemeMode.BLOSSOM, ThemeMode.GROVE, ThemeMode.FOUNDRY),
            ThemeMode.entries.filter(::isProTheme).toSet(),
        )
    }

    @Test
    fun `FREE can use every theme but the Pro ones, PRO can use all`() {
        ThemeMode.entries.forEach { mode ->
            assertTrue(canUseTheme(Tier.PRO, mode))
            assertEquals(!isProTheme(mode), canUseTheme(Tier.FREE, mode))
        }
    }

    @Test
    fun `a Pro theme on FREE draws as Dark, and as itself on PRO`() {
        listOf(ThemeMode.BLOSSOM, ThemeMode.GROVE, ThemeMode.FOUNDRY).forEach { mode ->
            listOf(true, false).forEach { systemDark ->
                assertEquals(AppTheme.DARK, effectiveTheme(mode, systemDark, Tier.FREE))
            }
        }
        assertEquals(AppTheme.BLOSSOM, effectiveTheme(ThemeMode.BLOSSOM, systemDark = false, tier = Tier.PRO))
        assertEquals(AppTheme.SONAR, effectiveTheme(ThemeMode.SONAR, systemDark = false, tier = Tier.FREE))
        assertEquals(AppTheme.LIGHT, effectiveTheme(ThemeMode.SYSTEM, systemDark = false, tier = Tier.FREE))
    }

    @Test
    fun `the timer is dark in Light and Dark and keeps each glowing theme`() {
        assertEquals(AppTheme.DARK, timerTheme(AppTheme.LIGHT))
        assertEquals(AppTheme.DARK, timerTheme(AppTheme.DARK))
        listOf(AppTheme.SONAR, AppTheme.BLOSSOM, AppTheme.GROVE, AppTheme.FOUNDRY).forEach { assertEquals(it, timerTheme(it)) }
    }
}
