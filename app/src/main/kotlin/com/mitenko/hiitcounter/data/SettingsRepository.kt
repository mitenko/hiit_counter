package com.mitenko.hiitcounter.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface SettingsRepository {
    val timing: Flow<TimingConfig>
    val progression: Flow<ProgressionConfig>
    val cues: Flow<CueConfig>
    val notificationPermissionAsked: Flow<Boolean>
    suspend fun setTiming(config: TimingConfig)
    suspend fun setProgression(config: ProgressionConfig)
    suspend fun setCues(config: CueConfig)
    suspend fun markNotificationPermissionAsked()
}

private const val TAG = "SettingsRepository"

/** Per-key fallback (spec §5): an invalid stored value is replaced by its default and logged. */
internal fun <T> Preferences.valid(key: Preferences.Key<T>, default: T, ok: (T) -> Boolean): T {
    val value = this[key] ?: return default
    if (ok(value)) return value
    Log.w(TAG, "Invalid ${key.name}=$value; using default $default")
    return default
}

internal fun Preferences.readTiming(): TimingConfig {
    val d = TimingConfig()
    val max = SettingsValidator.MAX_PHASE_SEC
    val c = TimingConfig(
        prepareSec = valid(Keys.PREPARE_SEC, d.prepareSec) { it in 0..max },
        sets = valid(Keys.SETS, d.sets) { it in 1..SettingsValidator.MAX_SETS },
        workSec = valid(Keys.WORK_SEC, d.workSec) { it in 1..max },
        restSec = valid(Keys.REST_SEC, d.restSec) { it in 0..max },
        cooldownSec = valid(Keys.COOLDOWN_SEC, d.cooldownSec) { it in 0..max },
    )
    if (SettingsValidator.timing(c).isValid) return c
    Log.w(TAG, "Stored timing inconsistent ($c); using defaults")
    return d
}

internal fun Preferences.readProgression(): ProgressionConfig {
    val d = ProgressionConfig()
    val c = ProgressionConfig(
        startingTotal = valid(Keys.STARTING_TOTAL, d.startingTotal) { it >= 1 },
        floor = valid(Keys.FLOOR, d.floor) { it >= 1 },
        cap = valid(Keys.CAP, d.cap) { it >= 1 },
        holdAt = valid(Keys.HOLD_AT, d.holdAt) { it >= 1 },
        holdFor = valid(Keys.HOLD_FOR, d.holdFor) { it >= 0 },
        windowHours = valid(Keys.WINDOW_HOURS, d.windowHours) { it >= 1 },
        penaltyHoursPerRep = valid(Keys.PENALTY_HOURS_PER_REP, d.penaltyHoursPerRep) { it > 0.0 && it.isFinite() },
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "Stored progression inconsistent ($c); using defaults")
    return d
}

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) {
            Log.e(TAG, "Settings read failed; using defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    override val timing: Flow<TimingConfig> = prefs.map { it.readTiming() }
    override val progression: Flow<ProgressionConfig> = prefs.map { it.readProgression() }
    override val cues: Flow<CueConfig> = prefs.map {
        CueConfig(sound = it[Keys.CUE_SOUND] ?: true, vibration = it[Keys.CUE_VIBRATION] ?: true)
    }
    override val notificationPermissionAsked: Flow<Boolean> = prefs.map { it[Keys.NOTIFICATION_ASKED] ?: false }

    override suspend fun setTiming(config: TimingConfig) {
        store.edit {
            it[Keys.PREPARE_SEC] = config.prepareSec
            it[Keys.SETS] = config.sets
            it[Keys.WORK_SEC] = config.workSec
            it[Keys.REST_SEC] = config.restSec
            it[Keys.COOLDOWN_SEC] = config.cooldownSec
        }
    }

    override suspend fun setProgression(config: ProgressionConfig) {
        store.edit {
            it[Keys.STARTING_TOTAL] = config.startingTotal
            it[Keys.FLOOR] = config.floor
            it[Keys.CAP] = config.cap
            it[Keys.HOLD_AT] = config.holdAt
            it[Keys.HOLD_FOR] = config.holdFor
            it[Keys.WINDOW_HOURS] = config.windowHours
            it[Keys.PENALTY_HOURS_PER_REP] = config.penaltyHoursPerRep
        }
    }

    override suspend fun setCues(config: CueConfig) {
        store.edit {
            it[Keys.CUE_SOUND] = config.sound
            it[Keys.CUE_VIBRATION] = config.vibration
        }
    }

    override suspend fun markNotificationPermissionAsked() {
        store.edit { it[Keys.NOTIFICATION_ASKED] = true }
    }
}
