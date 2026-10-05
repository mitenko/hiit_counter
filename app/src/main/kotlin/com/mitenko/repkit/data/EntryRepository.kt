package com.mitenko.repkit.data

import android.util.Log
import androidx.room.withTransaction
import com.mitenko.repkit.data.db.CheckInEntity
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.domain.CheckInResult
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.EntryNames
import com.mitenko.repkit.domain.InvalidEntryName
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.NameCheck
import com.mitenko.repkit.domain.Outcome
import com.mitenko.repkit.domain.ProgressionScale
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.RepProgression
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.WeightConversion
import com.mitenko.repkit.domain.WeightValidator
import com.mitenko.repkit.domain.counterHoldReset
import com.mitenko.repkit.domain.holdResetNeeded
import com.mitenko.repkit.domain.progressionHoldReset
import com.mitenko.repkit.domain.loadAt
import com.mitenko.repkit.domain.rangeChange
import com.mitenko.repkit.domain.totalMove
import com.mitenko.repkit.domain.widenedFor
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.remapWeights
import com.mitenko.repkit.domain.scale
import com.mitenko.repkit.domain.weightHoldResetNeeded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private const val TAG = "EntryRepository"

/**
 * Per-entry persistence (spec §5.3). Every method and both flows await the migration gate first.
 * Missing ids throw [EntryNotFound]; invalid input throws IllegalArgumentException and writes nothing.
 */
interface EntryRepository {
    /** Ordered by position, then id. */
    val entries: Flow<List<Entry>>

    /** Emits null once loaded if the entry does not exist. */
    fun entry(id: Long): Flow<Entry?>

    /** A new entry with default settings, appended at the end. `create(name)` makes a Workout (spec R4 §4.4). */
    suspend fun create(name: String, type: EntryType = EntryType.WORKOUT): Long

    suspend fun rename(id: Long, name: String)

    /** Copies the config with a fresh counter and the §5.5 name, appended at the end. */
    suspend fun duplicate(id: Long): Long

    /** Shifts every later row down by one and deletes the entry's history and workout sessions (spec R6 §3.2, rev 17 §4). */
    suspend fun delete(id: Long)

    /** Target = (position + delta) clamped to the list bounds; a no-op when it equals the current position. */
    suspend fun moveBy(id: Long, delta: Int)

    suspend fun setTiming(id: Long, timing: TimingConfig)

    /**
     * One transaction: holdCount is reset only if progressionHoldReset says so (R3 §6.3, rev 16 §4; in a
     * weight mode only a Hold switch change resets it, spec rev 26 §10 note 21).
     * Spec revision 28 rule 4: a Counter entry's stored total outside the new floor..cap moves to
     * the nearer limit in the same transaction, resetting the hold count ([counterHoldReset]), and
     * that move is returned. A NULL total stays NULL (it follows the starting total); a Timer only
     * entry's total is never moved, and neither is a weight-mode level (floor..cap are rep totals).
     * Returns null when the total didn't move. The mode and the weight group are never written (plan Spec note 4).
     */
    suspend fun setProgression(id: Long, progression: ProgressionConfig): Move?

    suspend fun setCues(id: Long, cues: CueConfig)

    /** Spec R4 §3.3: one UPDATE of the type; counter, timing, progression and cues are untouched. */
    suspend fun setType(id: Long, type: EntryType)

    /**
     * Spec rev 26 §2, §9.2, §3.1, plan Spec note 4: writes the weight group in one transaction.
     * - [weight]'s list is sorted, and a null unit keeps the stored one.
     * - The stored group is converted to [weight]'s unit first, if that changed.
     * - The starting point, the holds and, in a weight mode, the current level are remapped by value.
     *   An untouched (NULL) counter stays NULL.
     * - The result must pass WeightValidator, or IllegalArgumentException is thrown and nothing is written.
     * - In a weight mode, the hold count resets only as weightHoldResetNeeded says.
     *
     * Neither the mode nor the Reps fields are written.
     */
    suspend fun setWeightConfig(id: Long, weight: WeightConfig)

    /**
     * Spec rev 26 §2 "Start fresh", §9.3, plan Spec note 5: sets [mode] and returns the counter to the
     * mode's start (total NULL), with hold count 0. Streaks, the last check-in, the history and every
     * mode's settings are kept. Entering a weight mode writes [defaultUnit] (the app default) only if
     * the row has no unit yet. Switching to the current mode is a no-op. Plan Spec note 13: it sets the
     * fresh-start flag, so the next Counter check-in is performed at the start (no +1, no penalty).
     */
    suspend fun switchMode(id: Long, mode: ProgressMode, defaultUnit: WeightUnit)

    /**
     * One transaction using the row's own progression and type: concurrent calls on one entry record
     * exactly one check-in. A Timer only entry keeps its total and hold count (spec R4 §3.1).
     * Unless the outcome is AlreadyToday, the same transaction logs one history point (spec R6 §3.2)
     * and writes the engine's fresh-start flag (plan Spec note 13).
     */
    suspend fun checkIn(id: Long, clock: Clock): CheckInResult

    /**
     * One transaction: holdCount is reset only if the total changed; a stored NULL counts as the start
     * (R3 §6.3). The total is ≥ 1 in Reps mode and a level in 0..top in a weight mode (spec rev 26 §2).
     * Spec revision 27: in Reps mode, a Counter entry's total outside floor..cap first widens the
     * stored progression to include it ([widenedFor]), with the hold count following [holdResetNeeded].
     * In a weight mode nothing widens: a level outside 0..top is rejected. Returns the limit that
     * moved, or null. An explicit counter clears the fresh-start flag (plan Spec note 13).
     */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?): RangeChange?

    /**
     * total NULL, streaks 0, lastCheckIn NULL, holdCount 0, fresh start off. With [clearHistory], the entry's history
     * and workout sessions are deleted in the same transaction (spec R6 §3.2, rev 17 §4).
     */
    suspend fun resetProgress(id: Long, clearHistory: Boolean)

    /** Spec R6 §3.3: the entry's points at or after [since] (null means all of them), oldest first. */
    fun history(id: Long, since: Instant?): Flow<List<CheckInPoint>>

    /**
     * Spec R6 §3.3: every entry's points at or after [since], keyed by entry id, from one query.
     * An entry without points is absent from the map.
     */
    fun recentCheckIns(since: Instant): Flow<Map<Long, List<CheckInPoint>>>
}

/** Completes once the v1 import has run (spec §6). */
interface MigrationGate {
    suspend fun awaitReady()
}

/**
 * Room-backed [EntryRepository]. Suspend calls return on the caller's dispatcher: ViewModels call
 * it from viewModelScope (Main) and keep calling TimerController on Main — never wrap it in
 * withContext(Dispatchers.IO) around controller calls (spec §5.3 threading).
 * [validationClock] is only used to reject a last check-in in the future. [copySuffix] is the
 * duplicate name's suffix from resources (" copy" in English, spec revision 24), read per call so it
 * follows the current locale.
 */
class RoomEntryRepository(
    private val db: HiitDatabase,
    private val gate: MigrationGate,
    private val validationClock: Clock,
    private val copySuffix: () -> String,
) : EntryRepository {
    private val dao = db.entryDao()
    private val checkIns = db.checkInDao()
    private val sessions = db.workoutSessionDao()

    override val entries: Flow<List<Entry>> = flow {
        gate.awaitReady()
        emitAll(dao.observeAll())
    }.map { rows -> rows.map { it.toDomain() } }

    override fun entry(id: Long): Flow<Entry?> = flow {
        gate.awaitReady()
        emitAll(dao.observe(id))
    }.map { it?.toDomain() }

    override suspend fun create(name: String, type: EntryType): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count(), type = type)) }
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = requireName(name)
        gate.awaitReady()
        found(id, dao.rename(id, valid))
    }

    override suspend fun duplicate(id: Long): Long {
        gate.awaitReady()
        return db.withTransaction {
            val source = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            dao.insert(
                entryEntity(
                    name = EntryNames.duplicateName(source.name, copySuffix()),
                    position = dao.count(),
                    timing = source.timing,
                    progression = source.progression,
                    cues = source.cues,
                    type = source.type,
                ),
            )
        }
    }

    override suspend fun delete(id: Long) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            // Spec R6 §3.2: explicit, so deletion never depends on PRAGMA foreign_keys (the cascade covers it too).
            checkIns.deleteForEntry(id)
            sessions.deleteForEntry(id)
            dao.delete(id)
            dao.shiftPositions(low = row.position + 1, high = Int.MAX_VALUE, delta = -1)
        }
    }

    override suspend fun moveBy(id: Long, delta: Int) {
        gate.awaitReady()
        db.withTransaction {
            // Computed inside the transaction, so rapid repeated taps never act on a stale list.
            val row = dao.get(id) ?: throw EntryNotFound(id)
            val last = dao.count() - 1
            val target = (row.position.toLong() + delta).coerceIn(0L, last.toLong()).toInt()
            if (target != row.position) {
                if (target < row.position) {
                    dao.shiftPositions(low = target, high = row.position - 1, delta = 1)
                } else {
                    dao.shiftPositions(low = row.position + 1, high = target, delta = -1)
                }
                dao.setPosition(id, target)
            }
        }
    }

    override suspend fun setTiming(id: Long, timing: TimingConfig) {
        require(SettingsValidator.timing(timing).isValid) { "Invalid timing: $timing" }
        gate.awaitReady()
        found(id, with(timing) { dao.setTiming(id, prepareSec, sets, workSec, restSec, cooldownSec) })
    }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig): Move? {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        return db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            // Compared with the row's effective (repaired) progression, the one checkIn uses.
            val entry = row.toDomain()
            val progressionReset = writeProgression(id, entry.progression, progression)
            // Floor..cap are rep totals: a weight mode's level never moves with them.
            val repsCounter = entry.type == EntryType.WORKOUT && !entry.progression.mode.usesWeights
            // Spec revision 28 rule 4: only a stored total moves; NULL keeps following the starting total.
            // Read only for a Reps counter, so a weight mode's level 0 is never reported as an invalid total.
            val stored = if (repsCounter) validTotal(row.total) else null
            val move = if (repsCounter && stored != null) progression.totalMove(stored) else null
            if (move != null && stored != null) {
                val c = entry.counter
                // As in overwriteCounter: the hold count restarts when the progression or the total says so.
                val holdCount = if (progressionReset || counterHoldReset(stored, move.to)) 0 else c.holdCount
                dao.setCounter(id, move.to, c.bestStreak, c.currentStreak, holdCount, c.lastCheckIn?.toEpochMilli())
            }
            move
        }
    }

    /** Writes [new] over [old] (the row's effective progression); returns whether the hold count was reset. */
    private suspend fun writeProgression(id: Long, old: ProgressionConfig, new: ProgressionConfig): Boolean {
        val reset = progressionHoldReset(old, new)
        with(new) {
            dao.setProgression(
                id, startingTotal, floor, cap, HoldsCodec.encode(holds), legacyHold.at, legacyHold.forCount, hold,
                windowHours, penaltyHoursPerRep,
                resetHoldCount = reset,
            )
        }
        return reset
    }

    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration, cues.voice))
    }

    override suspend fun setType(id: Long, type: EntryType) {
        gate.awaitReady()
        found(id, dao.setType(id, type.name))
    }

    override suspend fun setWeightConfig(id: Long, weight: WeightConfig) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            val stored = row.progression()
            val mode = stored.mode
            // §3.1: sorted on save. §9.2: a unit change converts the stored group first, so values compare exactly.
            val draft = weight.copy(list = weight.list.sorted(), unit = weight.unit ?: stored.weight.unit)
            val old = draft.unit?.let { WeightConversion.convert(stored.weight, it) } ?: stored.weight
            // Reps mode has no level to move, but the starting point and holds still remap, as Reps then weight.
            val remapMode = if (mode.usesWeights) mode else ProgressMode.REPS_THEN_WEIGHT
            val oldLevel = if (mode.usesWeights) validTotal(row.total, min = 0) else null
            val remap = if (draft.weights.isNotEmpty() && draft.repMin in 1..draft.repMax) {
                remapWeights(remapMode, old, draft, oldLevel)
            } else {
                null // the validator rejects this draft below
            }
            val config = remap?.config ?: draft
            val problems = WeightValidator.validate(config, mode)
            require(problems.isEmpty()) { "Invalid weights: $problems" }
            val resetHoldCount = mode.usesWeights && remap != null && weightHoldResetNeeded(mode, old, remap)
            val total = if (mode.usesWeights) remap?.level else row.total
            with(config) {
                dao.setWeightConfig(
                    id, unit?.name, kind.name, WeightCodecs.encodeSteps(steps), WeightCodecs.encodeList(list),
                    WeightCodecs.encodeHolds(holds), repsPerSet, repMin, repMax, startWeight, startReps, total, resetHoldCount,
                )
            }
        }
    }

    override suspend fun switchMode(id: Long, mode: ProgressMode, defaultUnit: WeightUnit) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            if (row.mode() == mode) return@withTransaction
            dao.switchMode(id, mode.name, if (mode.usesWeights) defaultUnit.name else null)
        }
    }

    override suspend fun checkIn(id: Long, clock: Clock): CheckInResult {
        gate.awaitReady()
        return db.withTransaction {
            // The row's progression and type are the single source of truth (spec §5.3, R4 §3.3).
            val row = dao.get(id) ?: throw EntryNotFound(id)
            val entry = row.toDomain()
            val now = clock.now()
            entry.counter.lastCheckIn?.let { last ->
                if (now.isBefore(last)) Log.w(TAG, "Clock moved backwards: now=$now last=$last")
            }
            val countsReps = entry.type == EntryType.WORKOUT
            val result = RepProgression.checkInByMode(entry.counter, entry.progression, now, clock.zone(), countsReps)
            if (result.outcome != Outcome.AlreadyToday) {
                val s = result.state
                // A Timer only entry never touches its total column, so a NULL total stays NULL (plan Spec note 6).
                val total = if (countsReps) s.total else row.total
                dao.setCounter(id, total, s.bestStreak, s.currentStreak, s.holdCount, s.lastCheckIn?.toEpochMilli())
                // Plan Spec note 13: a Counter check-in clears the flag; a Timer only one keeps it (batch 1 ruling).
                dao.setFreshStart(id, s.freshStart)
                // Spec R6 §3.2: at is the new lastCheckIn (now); a Timer only point has no total. Spec rev 26 §9.3:
                // a weight-mode Counter point also records its load and unit, so history never depends on later edits.
                val load = if (countsReps) entry.progression.loadAt(s.total) else null
                checkIns.insert(
                    CheckInEntity(
                        entryId = id,
                        at = now.toEpochMilli(),
                        total = if (countsReps) s.total else null,
                        weight = load?.weight,
                        reps = load?.reps,
                        unit = load?.let { entry.progression.weight.unit?.name },
                    ),
                )
            }
            result
        }
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?): RangeChange? {
        // The total's range depends on the mode (a weight level may be 0), so it's checked in the transaction (plan Spec note 16).
        val check = SettingsValidator.currentState(total.coerceAtLeast(1), bestStreak, currentStreak, lastCheckIn, validationClock.now())
        require(check.isValid && total >= 0) { "Invalid counter: ${check.errors}, total=$total" }
        gate.awaitReady()
        return db.withTransaction {
            // The resolved total (NULL reads as the start) is what the Current page showed.
            val entry = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            val old = entry.counter
            val progression = entry.progression
            val scale = progression.scale()
            val ladder = scale is ProgressionScale.Ladder
            val range = if (scale is ProgressionScale.Ladder) scale.minLevel..scale.maxLevel else 1..Int.MAX_VALUE
            require(total in range) { "Invalid total $total for ${progression.mode}" }
            // Spec revision 27: only a Counter entry shows (and so sets) its total. Floor..cap are rep totals,
            // so a weight mode (where the total is a level on the ladder) never widens them.
            val widened = if (entry.type == EntryType.WORKOUT && !ladder) progression.widenedFor(total) else progression
            val progressionReset = widened != progression && writeProgression(id, progression, widened)
            val holdCount = if (progressionReset || counterHoldReset(old.total, total)) 0 else old.holdCount
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount, lastCheckIn?.toEpochMilli())
            // Plan Spec note 13: an explicit Current edit means the user chose the position.
            dao.setFreshStart(id, false)
            rangeChange(progression, widened)
        }
    }

    override suspend fun resetProgress(id: Long, clearHistory: Boolean) {
        gate.awaitReady()
        db.withTransaction {
            found(id, dao.setCounter(id, total = null, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null))
            dao.setFreshStart(id, false) // plan Spec note 13: no last check-in, so the First rule applies
            if (clearHistory) {
                checkIns.deleteForEntry(id)
                sessions.deleteForEntry(id)
            }
        }
    }

    override fun history(id: Long, since: Instant?): Flow<List<CheckInPoint>> = flow {
        gate.awaitReady()
        emitAll(checkIns.observeForEntry(id, since?.toEpochMilli() ?: Long.MIN_VALUE))
    }.map { rows -> rows.map { it.toPoint() } }

    override fun recentCheckIns(since: Instant): Flow<Map<Long, List<CheckInPoint>>> = flow {
        gate.awaitReady()
        emitAll(checkIns.observeSince(since.toEpochMilli()))
    }.map { rows -> rows.groupBy({ it.entryId }, { it.toPoint() }) }

    private fun requireName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw InvalidEntryName(check)
    }

    private fun found(id: Long, updatedRows: Int) {
        if (updatedRows == 0) throw EntryNotFound(id)
    }
}
