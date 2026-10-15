package com.mitenko.repkit.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `SYSTEM follows the phone`() {
        assertTrue(isDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(isDark(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun `LIGHT is always light`() {
        assertFalse(isDark(ThemeMode.LIGHT, systemDark = true))
        assertFalse(isDark(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun `DARK is always dark`() {
        assertTrue(isDark(ThemeMode.DARK, systemDark = true))
        assertTrue(isDark(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun `the glowing themes are dark whatever the phone says`() {
        listOf(ThemeMode.SONAR, ThemeMode.BLOSSOM, ThemeMode.GROVE, ThemeMode.FOUNDRY).forEach { mode ->
            assertTrue(isDark(mode, systemDark = true))
            assertTrue(isDark(mode, systemDark = false))
        }
    }

    @Test
    fun `effectiveTheme resolves SYSTEM against the phone and keeps the rest`() {
        assertEquals(AppTheme.DARK, effectiveTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(AppTheme.LIGHT, effectiveTheme(ThemeMode.SYSTEM, systemDark = false))
        listOf(true, false).forEach { systemDark ->
            assertEquals(AppTheme.LIGHT, effectiveTheme(ThemeMode.LIGHT, systemDark))
            assertEquals(AppTheme.DARK, effectiveTheme(ThemeMode.DARK, systemDark))
            assertEquals(AppTheme.SONAR, effectiveTheme(ThemeMode.SONAR, systemDark))
            assertEquals(AppTheme.BLOSSOM, effectiveTheme(ThemeMode.BLOSSOM, systemDark))
            assertEquals(AppTheme.GROVE, effectiveTheme(ThemeMode.GROVE, systemDark))
            assertEquals(AppTheme.FOUNDRY, effectiveTheme(ThemeMode.FOUNDRY, systemDark))
        }
    }

    @Test
    fun `the timer is dark in Light and Dark and keeps each glowing theme`() {
        assertEquals(AppTheme.DARK, timerTheme(AppTheme.LIGHT))
        assertEquals(AppTheme.DARK, timerTheme(AppTheme.DARK))
        listOf(AppTheme.SONAR, AppTheme.BLOSSOM, AppTheme.GROVE, AppTheme.FOUNDRY).forEach { assertEquals(it, timerTheme(it)) }
    }
}
