package com.mitenko.repkit.data

import android.util.Log
import com.mitenko.repkit.data.db.CheckInEntity
import com.mitenko.repkit.data.db.EntryEntity
import com.mitenko.repkit.domain.EntryNames
import com.mitenko.repkit.domain.NameCheck
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
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
        holds = storedHolds(),
        windowHours = checked(id, "window_hours", windowHours, d.windowHours) { it >= 1 },
        penaltyHoursPerRep = checked(id, "penalty_hours_per_rep", penaltyHoursPerRep, d.penaltyHoursPerRep) {
            it > 0.0 && it.isFinite()
        },
        // Spec R3 §5.2: a boolean column (default true); every stored value is valid, and the group fallback gives true.
        hold = holdEnabled,
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "Entry $id: progression inconsistent ($c); using default progression")
    return d
}

/**
 * Spec rev 16 §5: the holds column, or for "" (a row v5 code never wrote) the legacy hold_at /
 * hold_for as a one-item list. Bad items are dropped (text with no good item reads as the default
 * hold), then later duplicates and holds past the 8th, so the list alone never fails the group check
 * and resets the rest of the progression.
 */
private fun EntryEntity.storedHolds(): List<Hold> {
    if (holds.isEmpty()) return listOf(legacyHold())
    val decoded = checked(id, "holds", HoldsCodec.decode(holds), listOf(ProgressionConfig.DEFAULT_HOLD)) { it != null }!!
    val repaired = decoded.distinctBy { it.at }.take(ProgressionConfig.MAX_HOLDS)
    if (HoldsCodec.encode(repaired) != holds) Log.w(TAG, "Entry $id: repaired holds=$holds to $repaired")
    return repaired
}

/** The single hold of the legacy hold_at / hold_for columns, repaired per field. */
private fun EntryEntity.legacyHold(): Hold {
    val d = ProgressionConfig.DEFAULT_HOLD
    return Hold(
        at = checked(id, "hold_at", holdAt, d.at) { it >= 1 },
        forCount = checked(id, "hold_for", holdFor, d.forCount) { it >= 0 },
    )
}

internal fun EntryEntity.counter(startingTotal: Int): CounterState = CounterState(
    total = validTotal(total) ?: startingTotal,
    bestStreak = checked(id, "best_streak", bestStreak, 0) { it >= 0 },
    currentStreak = checked(id, "current_streak", currentStreak, 0) { it >= 0 },
    lastCheckIn = lastCheckIn?.let(Instant::ofEpochMilli),
    holdCount = checked(id, "hold_count", holdCount, 0) { it >= 0 },
)

/** Read repair (spec R4 §3.2): an unknown type string is logged and reads as a Workout. */
internal fun EntryEntity.entryType(): EntryType =
    EntryType.entries.firstOrNull { it.name == type }
        ?: EntryType.WORKOUT.also { Log.w(TAG, "Entry $id: unknown type=$type; reading as WORKOUT") }

/** Spec §5.1 invariant: a NULL total is resolved to the (repaired) starting total, so the UI never sees null. */
internal fun EntryEntity.toDomain(): Entry {
    val progression = progression()
    return Entry(
        id = id,
        name = repairedName(),
        position = position,
        timing = timing(),
        progression = progression,
        cues = CueConfig(cueSound, cueVibration, cueVoice),
        counter = counter(progression.startingTotal),
        type = entryType(),
    )
}

/** A history row as a domain point (spec R6 §3.3). */
internal fun CheckInEntity.toPoint(): CheckInPoint = CheckInPoint(Instant.ofEpochMilli(at), total)

/** The counter group as stored: [total] null means "reads as the starting total". */
internal data class StoredCounter(
    val total: Int? = null,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val holdCount: Int = 0,
    val lastCheckIn: Long? = null,
)

/** Rev 16 §5: the legacy hold_at / hold_for columns mirror the first hold (64 / 4 for an empty list). */
internal val ProgressionConfig.legacyHold: Hold
    get() = holds.firstOrNull() ?: ProgressionConfig.DEFAULT_HOLD

/** A row for insertion (id assigned by Room). The defaults give a fresh entry with an untouched counter. */
internal fun entryEntity(
    name: String,
    position: Int,
    timing: TimingConfig = TimingConfig(),
    progression: ProgressionConfig = ProgressionConfig(),
    cues: CueConfig = CueConfig(),
    counter: StoredCounter = StoredCounter(),
    type: EntryType = EntryType.WORKOUT,
): EntryEntity = EntryEntity(
    name = name,
    position = position,
    type = type.name,
    prepareSec = timing.prepareSec,
    sets = timing.sets,
    workSec = timing.workSec,
    restSec = timing.restSec,
    cooldownSec = timing.cooldownSec,
    startingTotal = progression.startingTotal,
    floor = progression.floor,
    cap = progression.cap,
    holds = HoldsCodec.encode(progression.holds),
    holdAt = progression.legacyHold.at,
    holdFor = progression.legacyHold.forCount,
    holdEnabled = progression.hold,
    windowHours = progression.windowHours,
    penaltyHoursPerRep = progression.penaltyHoursPerRep,
    cueSound = cues.sound,
    cueVibration = cues.vibration,
    cueVoice = cues.voice,
    total = counter.total,
    bestStreak = counter.bestStreak,
    currentStreak = counter.currentStreak,
    holdCount = counter.holdCount,
    lastCheckIn = counter.lastCheckIn,
)
