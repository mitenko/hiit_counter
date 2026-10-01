package com.mitenko.repkit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
}
