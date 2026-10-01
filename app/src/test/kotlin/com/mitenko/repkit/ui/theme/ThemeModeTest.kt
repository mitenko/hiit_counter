package com.mitenko.repkit.ui.theme

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
}
