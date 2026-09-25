package com.mitenko.hiitcounter.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/** Persistence keys — spec §5 table. */
internal object Keys {
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
