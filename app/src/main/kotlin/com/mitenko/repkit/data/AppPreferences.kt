package com.mitenko.repkit.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mitenko.repkit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * `app.preferences_pb` (spec §5.4): the app-wide values. The notification flag is sticky — set once the
 * Android 13+ prompt has been shown, whatever the answer, and never reset.
 */
class AppPreferences(private val store: DataStore<Preferences>) {
    val notificationPermissionAsked: Flow<Boolean> = store.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "App preferences read failed", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[NOTIFICATION_ASKED] ?: false }

    suspend fun markNotificationPermissionAsked() {
        store.edit { it[NOTIFICATION_ASKED] = true }
    }

    /** Spec rev 14 §1: defaults to SYSTEM when absent, and an unrecognised stored value also reads as SYSTEM. */
    val themeMode: Flow<ThemeMode> = store.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "App preferences read failed", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            prefs[THEME_MODE]?.let { stored ->
                runCatching { ThemeMode.valueOf(stored) }.getOrNull()
            } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    /** Spec rev 30 §3: the "Share crash reports and usage" switch; on when absent. */
    val crashReportsEnabled: Flow<Boolean> = store.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "App preferences read failed", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[CRASH_REPORTS_ENABLED] ?: true }

    suspend fun setCrashReportsEnabled(enabled: Boolean) {
        store.edit { it[CRASH_REPORTS_ENABLED] = enabled }
    }

    companion object {
        /** `app.preferences_pb` via `preferencesDataStoreFile(FILE_NAME)`. */
        const val FILE_NAME = "app"
        val NOTIFICATION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CRASH_REPORTS_ENABLED = booleanPreferencesKey("crash_reports_enabled")
        private const val TAG = "AppPreferences"
    }
}
