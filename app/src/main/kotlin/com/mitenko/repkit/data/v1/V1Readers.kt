package com.mitenko.repkit.data.v1

import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.mitenko.repkit.data.StoredCounter
import com.mitenko.repkit.data.db.EntryEntity
import com.mitenko.repkit.data.entryEntity
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig

private const val TAG = "V1Readers"

/** The name the imported v1 data gets (spec §6 step 3). */
internal const val V1_ENTRY_NAME = "Workout"

/** v1 DataStore keys (v1 spec §5). Read only by the one-time migration. */
internal object V1Keys {
    val PREPARE_SEC = intPreferencesKey("prepare_sec")
    val SETS = intPreferencesKey("sets")
    val WORK_SEC = intPreferencesKey("work_sec")
    val REST_SEC = intPreferencesKey("rest_sec")
    val COOLDOWN_SEC = intPreferencesKey("cooldown_sec")
    val STARTING_TOTAL = intPreferencesKey("starting_total")
    val FLOOR = intPreferencesKey("floor")
    val CAP = intPreferencesKey("cap")
    val HOLD_AT = intPreferencesKey("hold_at")
    val HOLD_FOR = intPreferencesKey("hold_for")
    val WINDOW_HOURS = intPreferencesKey("window_hours")
    val PENALTY_HOURS_PER_REP = doublePreferencesKey("penalty_hours_per_rep")
    val CUE_SOUND = booleanPreferencesKey("cue_sound")
    val CUE_VIBRATION = booleanPreferencesKey("cue_vibration")
    val NOTIFICATION_ASKED = booleanPreferencesKey("notification_permission_asked")

    val TOTAL = intPreferencesKey("total")
    val BEST_STREAK = intPreferencesKey("best_streak")
    val CURRENT_STREAK = intPreferencesKey("current_streak")
    val HOLD_COUNT = intPreferencesKey("hold_count")
    val LAST_CHECK_IN = longPreferencesKey("last_check_in")
}

/** v1 per-key fallback: an invalid stored value is replaced by its default and logged. */
private fun <T> Preferences.valid(key: Preferences.Key<T>, default: T, ok: (T) -> Boolean): T {
    val value = this[key] ?: return default
    if (ok(value)) return value
    Log.w(TAG, "Invalid v1 ${key.name}=$value; using default $default")
    return default
}

internal fun Preferences.readV1Timing(): TimingConfig {
    val d = TimingConfig()
    val max = SettingsValidator.MAX_PHASE_SEC
    val c = TimingConfig(
        prepareSec = valid(V1Keys.PREPARE_SEC, d.prepareSec) { it in 0..max },
        sets = valid(V1Keys.SETS, d.sets) { it in 1..SettingsValidator.MAX_SETS },
        workSec = valid(V1Keys.WORK_SEC, d.workSec) { it in 1..max },
        restSec = valid(V1Keys.REST_SEC, d.restSec) { it in 0..max },
        cooldownSec = valid(V1Keys.COOLDOWN_SEC, d.cooldownSec) { it in 0..max },
    )
    if (SettingsValidator.timing(c).isValid) return c
    Log.w(TAG, "v1 timing inconsistent ($c); using defaults")
    return d
}

internal fun Preferences.readV1Progression(): ProgressionConfig {
    val d = ProgressionConfig()
    val c = ProgressionConfig(
        startingTotal = valid(V1Keys.STARTING_TOTAL, d.startingTotal) { it >= 1 },
        floor = valid(V1Keys.FLOOR, d.floor) { it >= 1 },
        cap = valid(V1Keys.CAP, d.cap) { it >= 1 },
        holdAt = valid(V1Keys.HOLD_AT, d.holdAt) { it >= 1 },
        holdFor = valid(V1Keys.HOLD_FOR, d.holdFor) { it >= 0 },
        windowHours = valid(V1Keys.WINDOW_HOURS, d.windowHours) { it >= 1 },
        penaltyHoursPerRep = valid(V1Keys.PENALTY_HOURS_PER_REP, d.penaltyHoursPerRep) { it > 0.0 && it.isFinite() },
    )
    val valid = if (SettingsValidator.progression(c).isValid) {
        c
    } else {
        Log.w(TAG, "v1 progression inconsistent ($c); using defaults")
        d
    }
    // Spec R3 §5.2: v1's hold_for = 0 meant "hold off"; it imports as the switch off with hold_for back at 4.
    return if (valid.holdFor == 0) valid.copy(hold = false, holdFor = d.holdFor) else valid
}

internal fun Preferences.readV1Cues(): CueConfig =
    CueConfig(sound = this[V1Keys.CUE_SOUND] ?: true, vibration = this[V1Keys.CUE_VIBRATION] ?: true)

/** `total` is read raw (spec §6 step 2): absent or invalid (< 1) becomes NULL. */
internal fun Preferences.readV1Total(): Int? {
    val raw = this[V1Keys.TOTAL] ?: return null
    if (raw >= 1) return raw
    Log.w(TAG, "Invalid v1 total=$raw; importing as NULL")
    return null
}

/** Streaks, hold count and last check-in follow the v1 reader rules. */
internal fun Preferences.readV1Counter(): StoredCounter = StoredCounter(
    total = readV1Total(),
    bestStreak = valid(V1Keys.BEST_STREAK, 0) { it >= 0 },
    currentStreak = valid(V1Keys.CURRENT_STREAK, 0) { it >= 0 },
    holdCount = valid(V1Keys.HOLD_COUNT, 0) { it >= 0 },
    lastCheckIn = this[V1Keys.LAST_CHECK_IN],
)

/** The "Workout" row built from the two v1 files; either may be empty (missing or corrupted). */
internal fun v1Entry(settings: Preferences, counter: Preferences): EntryEntity = entryEntity(
    name = V1_ENTRY_NAME,
    position = 0,
    timing = settings.readV1Timing(),
    progression = settings.readV1Progression(),
    cues = settings.readV1Cues(),
    counter = counter.readV1Counter(),
)
