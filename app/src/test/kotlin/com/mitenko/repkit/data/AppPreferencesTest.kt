package com.mitenko.repkit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.model.WeightUnit
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Runs on Robolectric (SDK 34). With DataStore 1.1.7 (raised by Firebase), PreferenceDataStoreFactory
 * on Android uses FileStorage, which replaces the file with Files.move(REPLACE_EXISTING) on API 26+
 * but File.renameTo below that. On the plain JVM, SDK_INT reads 0, and renameTo can't overwrite an
 * existing file on Windows. So a second write that changes a value failed with "Unable to rename".
 * One DataStore per test, as before.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
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

    @Test
    fun `crashReportsEnabled defaults to true when absent`() = runTest {
        assertTrue(AppPreferences(store()).crashReportsEnabled.first())
    }

    @Test
    fun `setCrashReportsEnabled round trips`() = runTest {
        val prefs = AppPreferences(store())
        prefs.setCrashReportsEnabled(false)
        assertFalse(prefs.crashReportsEnabled.first())
        prefs.setCrashReportsEnabled(true)
        assertTrue(prefs.crashReportsEnabled.first())
    }

    @Test
    fun `weightUnitDefault follows the locale when absent`() = runTest {
        val store = store()
        listOf("US" to WeightUnit.LB, "LR" to WeightUnit.LB, "MM" to WeightUnit.LB, "GB" to WeightUnit.KG, "" to WeightUnit.KG)
            .forEach { (country, unit) -> assertEquals(country, unit, AppPreferences(store) { country }.weightUnitDefault.first()) }
    }

    @Test
    fun `setWeightUnitDefault round trips and wins over the locale`() = runTest {
        val prefs = AppPreferences(store()) { "US" }
        prefs.setWeightUnitDefault(WeightUnit.KG)
        assertEquals(WeightUnit.KG, prefs.weightUnitDefault.first())
        prefs.setWeightUnitDefault(WeightUnit.LB)
        assertEquals(WeightUnit.LB, prefs.weightUnitDefault.first())
    }

    @Test
    fun `an unknown stored unit reads as the locale default`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("weight_unit_default")] = "STONE" }
        assertEquals(WeightUnit.KG, AppPreferences(store) { "GB" }.weightUnitDefault.first())
    }
}
