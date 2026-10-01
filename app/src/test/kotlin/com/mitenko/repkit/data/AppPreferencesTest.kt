package com.mitenko.repkit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mitenko.repkit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppPreferencesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "app.preferences_pb") })

    @Test
    fun `the flag reads false when absent`() = runTest {
        assertFalse(AppPreferences(store()).notificationPermissionAsked.first())
    }

    @Test
    fun `marking the flag is sticky`() = runTest {
        val prefs = AppPreferences(store())
        prefs.markNotificationPermissionAsked()
        prefs.markNotificationPermissionAsked()
        assertTrue(prefs.notificationPermissionAsked.first())
    }

    @Test
    fun `themeMode defaults to SYSTEM when absent`() = runTest {
        assertEquals(ThemeMode.SYSTEM, AppPreferences(store()).themeMode.first())
    }

    @Test
    fun `setThemeMode round trips`() = runTest {
        val prefs = AppPreferences(store())
        prefs.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, prefs.themeMode.first())
        prefs.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, prefs.themeMode.first())
    }

    @Test
    fun `an unknown stored value reads as SYSTEM`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("theme_mode")] = "GARBAGE" }
        assertEquals(ThemeMode.SYSTEM, AppPreferences(store).themeMode.first())
    }
}
