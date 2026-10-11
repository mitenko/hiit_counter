package com.mitenko.repkit.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mitenko.repkit.domain.defaultWeightUnit
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

/**
 * The unit new weight workouts start in (spec rev 26 §5), as the settings need it (plan Spec note 36).
 * [AppPreferences] is the real one; ViewModel tests pass a fixed flow.
 */
interface WeightUnitDefaults {
    val weightUnitDefault: Flow<WeightUnit>
}

/**
 * `app.preferences_pb` (spec §5.4): the app-wide values. The notification flag is sticky — set once
 * the Android 13+ prompt has been shown, whatever the answer, and never reset. [country] gives the
 * locale's region for the default unit (spec rev 26 §5); tests pass their own.
 */
class AppPreferences(
    private val store: DataStore<Preferences>,
    private val country: () -> String = { Locale.getDefault().country },
) : WeightUnitDefaults {
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

    /**
     * Spec rev 26 §5: the unit new weight workouts start in (⚙ › Units in PR 2). When it is absent or
     * unrecognised, it follows the locale (defaultWeightUnit). A workout copies it on its first switch
     * into a weight mode (§9.3), so changing it never changes an existing workout.
     */
    override val weightUnitDefault: Flow<WeightUnit> = store.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "App preferences read failed", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            prefs[WEIGHT_UNIT_DEFAULT]?.let { stored -> WeightUnit.entries.firstOrNull { it.name == stored } }
                ?: defaultWeightUnit(country())
        }

    suspend fun setWeightUnitDefault(unit: WeightUnit) {
        store.edit { it[WEIGHT_UNIT_DEFAULT] = unit.name }
    }

    companion object {
        /** `app.preferences_pb` via `preferencesDataStoreFile(FILE_NAME)`. */
        const val FILE_NAME = "app"
        val NOTIFICATION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CRASH_REPORTS_ENABLED = booleanPreferencesKey("crash_reports_enabled")
        val WEIGHT_UNIT_DEFAULT = stringPreferencesKey("weight_unit_default")
        private const val TAG = "AppPreferences"
    }
}
