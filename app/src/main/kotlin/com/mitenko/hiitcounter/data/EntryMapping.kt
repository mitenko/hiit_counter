package com.mitenko.hiitcounter.data

import android.util.Log
import com.mitenko.hiitcounter.data.db.EntryEntity
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import java.time.Instant

private const val TAG = "EntryMapping"
private const val FALLBACK_NAME = "Workout"

/** A stored total below 1 is invalid and reads as NULL (spec §5.2). */
internal fun validTotal(raw: Int?): Int? {
    if (raw == null || raw >= 1) return raw
    Log.w(TAG, "Invalid total=$raw; reading as NULL")
    return null
}

/** Per-field repair (spec §5.2): an invalid value is replaced by its default and logged. */
private inline fun <T> checked(id: Long, column: String, value: T, default: T, ok: (T) -> Boolean): T {
    if (ok(value)) return value
    Log.w(TAG, "Entry $id: invalid $column=$value; using default $default")
    return default
}

internal fun EntryEntity.repairedName(): String = when (val check = EntryNames.validate(name)) {
    is NameCheck.Ok -> check.name
    NameCheck.Empty -> FALLBACK_NAME.also { Log.w(TAG, "Entry $id: blank name; using $it") }
    NameCheck.TooLong -> name.trim().take(EntryNames.MAX_LENGTH).also { Log.w(TAG, "Entry $id: name too long; truncated") }
}

/** Per-field repair, then the timing group falls back to defaults only if still inconsistent. */
internal fun EntryEntity.timing(): TimingConfig {
    val d = TimingConfig()
    val max = SettingsValidator.MAX_PHASE_SEC
    val c = TimingConfig(
        prepareSec = checked(id, "prepare_sec", prepareSec, d.prepareSec) { it in 0..max },
        sets = checked(id, "sets", sets, d.sets) { it in 1..SettingsValidator.MAX_SETS },
        workSec = checked(id, "work_sec", workSec, d.workSec) { it in 1..max },
        restSec = checked(id, "rest_sec", restSec, d.restSec) { it in 0..max },
        cooldownSec = checked(id, "cooldown_sec", cooldownSec, d.cooldownSec) { it in 0..max },
    )
    if (SettingsValidator.timing(c).isValid) return c
    Log.w(TAG, "Entry $id: timing inconsistent ($c); using default timing")
    return d
}

/** Per-field repair, then the progression group falls back to defaults only if still inconsistent. */
internal fun EntryEntity.progression(): ProgressionConfig {
    val d = ProgressionConfig()
    val c = ProgressionConfig(
        startingTotal = checked(id, "starting_total", startingTotal, d.startingTotal) { it >= 1 },
        floor = checked(id, "floor", floor, d.floor) { it >= 1 },
        cap = checked(id, "cap", cap, d.cap) { it >= 1 },
        holdAt = checked(id, "hold_at", holdAt, d.holdAt) { it >= 1 },
        holdFor = checked(id, "hold_for", holdFor, d.holdFor) { it >= 0 },
        windowHours = checked(id, "window_hours", windowHours, d.windowHours) { it >= 1 },
        penaltyHoursPerRep = checked(id, "penalty_hours_per_rep", penaltyHoursPerRep, d.penaltyHoursPerRep) {
            it > 0.0 && it.isFinite()
        },
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "Entry $id: progression inconsistent ($c); using default progression")
    return d
}

internal fun EntryEntity.counter(startingTotal: Int): CounterState = CounterState(
    total = validTotal(total) ?: startingTotal,
    bestStreak = checked(id, "best_streak", bestStreak, 0) { it >= 0 },
    currentStreak = checked(id, "current_streak", currentStreak, 0) { it >= 0 },
    lastCheckIn = lastCheckIn?.let(Instant::ofEpochMilli),
    holdCount = checked(id, "hold_count", holdCount, 0) { it >= 0 },
)

/** Spec §5.1 invariant: a NULL total is resolved to the (repaired) starting total, so the UI never sees null. */
internal fun EntryEntity.toDomain(): Entry {
    val progression = progression()
    return Entry(
        id = id,
        name = repairedName(),
        position = position,
        timing = timing(),
        progression = progression,
        cues = CueConfig(cueSound, cueVibration),
        counter = counter(progression.startingTotal),
    )
}

/** The counter group as stored: [total] null means "reads as the starting total". */
internal data class StoredCounter(
    val total: Int? = null,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val holdCount: Int = 0,
    val lastCheckIn: Long? = null,
)

/** A row for insertion (id assigned by Room). The defaults give a fresh entry with an untouched counter. */
internal fun entryEntity(
    name: String,
    position: Int,
    timing: TimingConfig = TimingConfig(),
    progression: ProgressionConfig = ProgressionConfig(),
    cues: CueConfig = CueConfig(),
    counter: StoredCounter = StoredCounter(),
): EntryEntity = EntryEntity(
    name = name,
    position = position,
    prepareSec = timing.prepareSec,
    sets = timing.sets,
    workSec = timing.workSec,
    restSec = timing.restSec,
    cooldownSec = timing.cooldownSec,
    startingTotal = progression.startingTotal,
    floor = progression.floor,
    cap = progression.cap,
    holdAt = progression.holdAt,
    holdFor = progression.holdFor,
    windowHours = progression.windowHours,
    penaltyHoursPerRep = progression.penaltyHoursPerRep,
    cueSound = cues.sound,
    cueVibration = cues.vibration,
    total = counter.total,
    bestStreak = counter.bestStreak,
    currentStreak = counter.currentStreak,
    holdCount = counter.holdCount,
    lastCheckIn = counter.lastCheckIn,
)
