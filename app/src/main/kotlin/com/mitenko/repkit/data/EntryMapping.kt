package com.mitenko.repkit.data

import android.util.Log
import com.mitenko.repkit.data.db.CheckInEntity
import com.mitenko.repkit.data.db.EntryEntity
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.EntryNames
import com.mitenko.repkit.domain.NoOpCrashReporter
import com.mitenko.repkit.domain.NameCheck
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.WeightValidator
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.startLevel
import java.time.Instant

private const val TAG = "EntryMapping"
private const val FALLBACK_NAME = "Workout"

/**
 * Spec rev 30 §4: every read repair also leaves a Crashlytics breadcrumb, not a non-fatal, because
 * repairs can be frequent. The breadcrumb names only the entry id and the repaired field: no values
 * or names, which may be workout data. TelemetryInitializer sets [reporter]; tests keep the no-op.
 */
internal object RepairBreadcrumbs {
    @Volatile var reporter: CrashReporter = NoOpCrashReporter
}

/** Logs a read repair locally in full, and to [RepairBreadcrumbs] without its values. */
private fun logRepair(id: Long?, field: String, detail: String) {
    Log.w(TAG, detail)
    RepairBreadcrumbs.reporter.log(if (id == null) "EntryMapping: repaired $field" else "EntryMapping: entry $id repaired $field")
}

/**
 * A stored total below [min] is invalid and reads as NULL (spec §5.2): [min] is 1 in Reps mode and 0,
 * the lightest level, in a weight mode (spec rev 26 §2, plan Spec note 3).
 */
internal fun validTotal(raw: Int?, min: Int = 1): Int? {
    if (raw == null || raw >= min) return raw
    logRepair(null, "total", "Invalid total=$raw; reading as NULL")
    return null
}

/** Per-field repair (spec §5.2): an invalid value is replaced by its default and logged. */
private inline fun <T> checked(id: Long, column: String, value: T, default: T, ok: (T) -> Boolean): T {
    if (ok(value)) return value
    logRepair(id, column, "Entry $id: invalid $column=$value; using default $default")
    return default
}

internal fun EntryEntity.repairedName(): String = when (val check = EntryNames.validate(name)) {
    is NameCheck.Ok -> check.name
    NameCheck.Empty -> FALLBACK_NAME.also { logRepair(id, "name", "Entry $id: blank name; using $it") }
    NameCheck.TooLong -> name.trim().take(EntryNames.MAX_LENGTH).also { logRepair(id, "name", "Entry $id: name too long; truncated") }
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
    logRepair(id, "timing", "Entry $id: timing inconsistent ($c); using default timing")
    return d
}

/** Per-field repair, then the progression group falls back to defaults only if still inconsistent. */
internal fun EntryEntity.progression(): ProgressionConfig {
    val d = ProgressionConfig()
    val mode = mode()
    val weight = weightConfig(mode)
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
        mode = mode,
        weight = weight,
    )
    if (SettingsValidator.progression(c).isValid) return c
    logRepair(id, "progression", "Entry $id: progression inconsistent ($c); using default progression")
    // Spec rev 26 §5: the weight group is repaired on its own, so a Reps fallback keeps it and the mode.
    return d.copy(mode = mode, weight = weight)
}

/** Spec rev 26 §5: an unknown progress_mode is logged and reads as REPS. */
internal fun EntryEntity.mode(): ProgressMode =
    ProgressMode.entries.firstOrNull { it.name == progressMode }
        ?: ProgressMode.REPS.also { logRepair(id, "progress_mode", "Entry $id: unknown progress_mode=$progressMode; reading as REPS") }

/**
 * Spec rev 26 §5, repaired per field like the progression:
 * - unknown strings read as their defaults;
 * - malformed steps read as the default steps, and a malformed list as empty;
 * - the list is sorted and de-duplicated;
 * - reps per set or a rep range outside 1..100 reads as its default;
 * - a starting point or hold that isn't on the ladder is dropped (in Weight mode, so is a later hold on the same weight).
 *
 * A group that is still invalid falls back to the defaults with the row's unit, on its own, so a bad
 * weight group never resets the Reps progression and the reverse. A weight mode with no unit (only a
 * corrupt row: §9.3 writes one on the first switch) reads as KG (plan Spec note 11).
 */
internal fun EntryEntity.weightConfig(mode: ProgressMode): WeightConfig {
    val d = WeightConfig()
    val storedUnit = weightUnit?.let { s ->
        WeightUnit.entries.firstOrNull { it.name == s } ?: null.also { logRepair(id, "weight_unit", "Entry $id: unknown weight_unit=$s") }
    }
    val unit = storedUnit
        ?: if (mode.usesWeights) WeightUnit.KG.also { logRepair(id, "weight_unit", "Entry $id: no weight_unit in $mode; reading as KG") } else null
    val kind = WeightsKind.entries.firstOrNull { it.name == weightsKind }
        ?: d.kind.also { logRepair(id, "weights_kind", "Entry $id: unknown weights_kind=$weightsKind; reading as ${d.kind}") }
    val steps = checked(id, "weight_steps", WeightCodecs.decodeSteps(weightSteps), d.steps) { it != null }!!
    val list = checked(id, "weight_list", WeightCodecs.decodeList(weightList), d.list) { it != null }!!.sorted().distinct()
    val perSet = checked(id, "reps_per_set", repsPerSet, d.repsPerSet) { it in 1..WeightConfig.MAX_REPS }
    val rangeOk = repMin in 1..WeightConfig.MAX_REPS && repMax in 1..WeightConfig.MAX_REPS && repMin < repMax
    if (!rangeOk) logRepair(id, "rep_range", "Entry $id: invalid rep range $repMin..$repMax; using ${d.repMin}..${d.repMax}")
    val min = if (rangeOk) repMin else d.repMin
    val max = if (rangeOk) repMax else d.repMax
    val ladder = WeightConfig(kind = kind, steps = steps, list = list).weights.toSet()
    val c = WeightConfig(
        unit = unit,
        kind = kind,
        steps = steps,
        list = list,
        repsPerSet = perSet,
        repMin = min,
        repMax = max,
        startWeight = checked(id, "start_weight", startWeight, null) { it == null || it in ladder },
        startReps = checked(id, "start_reps", startReps, null) { it == null || it in min..max },
        holds = storedWeightHolds(ladder, min..max, mode),
    )
    if (WeightValidator.validate(c, mode).isEmpty()) return c
    logRepair(id, "weight", "Entry $id: weight settings inconsistent ($c); using defaults")
    return WeightConfig(unit = unit)
}

/** The weight_holds column, item by item: off-ladder or out-of-range holds dropped, then later collisions, then holds past the 8th. */
private fun EntryEntity.storedWeightHolds(ladder: Set<Int>, reps: IntRange, mode: ProgressMode): List<WeightHold> {
    val repaired = WeightCodecs.decodeHolds(weightHolds)
        .filter { it.weight in ladder && it.reps in reps }
        .distinctBy { if (mode == ProgressMode.WEIGHT) it.weight to 0 else it.weight to it.reps }
        .take(ProgressionConfig.MAX_HOLDS)
    if (WeightCodecs.encodeHolds(repaired) != weightHolds) logRepair(id, "weight_holds", "Entry $id: repaired weight_holds=$weightHolds to $repaired")
    return repaired
}

/**
 * Spec rev 16 §5: the holds column, or for "" (a row v5 code never wrote) the legacy hold_at /
 * hold_for as a one-item list. Bad items are dropped (text with no good item reads as the default
 * hold), then later duplicates of the same kind and value and holds past the 8th, so the list alone
 * never fails the group check and resets the rest of the progression.
 */
private fun EntryEntity.storedHolds(): List<Hold> {
    if (holds.isEmpty()) return listOf(legacyHold())
    val decoded = checked(id, "holds", HoldsCodec.decode(holds), listOf(ProgressionConfig.DEFAULT_HOLD)) { it != null }!!
    // Spec rev 34 §4: duplicates are keyed by (kind, at), so "at 64" and "from 64" both stay.
    val repaired = decoded.distinctBy { it.kind to it.at }.take(ProgressionConfig.MAX_HOLDS)
    if (HoldsCodec.encode(repaired) != holds) logRepair(id, "holds", "Entry $id: repaired holds=$holds to $repaired")
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

/** [minTotal] is 0 in a weight mode (spec rev 26 §2), so level 0 is a real value. */
internal fun EntryEntity.counter(startingTotal: Int, minTotal: Int = 1): CounterState = CounterState(
    total = validTotal(total, minTotal) ?: startingTotal,
    bestStreak = checked(id, "best_streak", bestStreak, 0) { it >= 0 },
    currentStreak = checked(id, "current_streak", currentStreak, 0) { it >= 0 },
    lastCheckIn = lastCheckIn?.let(Instant::ofEpochMilli),
    holdCount = checked(id, "hold_count", holdCount, 0) { it >= 0 },
    // Plan Spec note 13 (user ruling A): a boolean column (default 0); every stored value is valid.
    freshStart = freshStart,
)

/** Read repair (spec R4 §3.2): an unknown type string is logged and reads as a Workout. */
internal fun EntryEntity.entryType(): EntryType =
    EntryType.entries.firstOrNull { it.name == type }
        ?: EntryType.WORKOUT.also { logRepair(id, "type", "Entry $id: unknown type=$type; reading as WORKOUT") }

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
        counter = counter(progression.startLevel(), minTotal = if (progression.mode.usesWeights) 0 else 1),
        type = entryType(),
    )
}

/** A history row as a domain point (spec R6 §3.3, rev 26 §9.3). An unknown unit string reads as null and is logged. */
internal fun CheckInEntity.toPoint(): CheckInPoint = CheckInPoint(
    at = Instant.ofEpochMilli(at),
    total = total,
    weight = weight,
    reps = reps,
    unit = unit?.let { u ->
        WeightUnit.entries.firstOrNull { it.name == u }
            ?: null.also { logRepair(entryId, "check_in.unit", "Check-in $id of entry $entryId: unknown unit=$u; reading as null") }
    },
)

/** The counter group as stored: [total] null means "reads as the starting total". */
internal data class StoredCounter(
    val total: Int? = null,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val holdCount: Int = 0,
    val lastCheckIn: Long? = null,
    /** Plan Spec note 13: Start fresh sets it; every other write leaves it false. */
    val freshStart: Boolean = false,
)

/** Rev 16 §5: the legacy hold_at / hold_for columns mirror the first hold (64 / 4 for an empty list), whatever its kind (spec rev 34 §4). */
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
    freshStart = counter.freshStart,
    progressMode = progression.mode.name,
    weightUnit = progression.weight.unit?.name,
    weightsKind = progression.weight.kind.name,
    weightSteps = WeightCodecs.encodeSteps(progression.weight.steps),
    weightList = WeightCodecs.encodeList(progression.weight.list),
    weightHolds = WeightCodecs.encodeHolds(progression.weight.holds),
    repsPerSet = progression.weight.repsPerSet,
    repMin = progression.weight.repMin,
    repMax = progression.weight.repMax,
    startWeight = progression.weight.startWeight,
    startReps = progression.weight.startReps,
)
