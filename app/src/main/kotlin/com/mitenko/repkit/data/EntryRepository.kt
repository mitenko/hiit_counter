package com.mitenko.repkit.data

import android.util.Log
import androidx.room.withTransaction
import com.mitenko.repkit.data.db.CheckInEntity
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.domain.CheckInResult
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.EntryNames
import com.mitenko.repkit.domain.InvalidEntryName
import com.mitenko.repkit.domain.NameCheck
import com.mitenko.repkit.domain.Outcome
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.RepProgression
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.counterHoldReset
import com.mitenko.repkit.domain.holdResetNeeded
import com.mitenko.repkit.domain.rangeChange
import com.mitenko.repkit.domain.widenedFor
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
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

    /** One transaction: holdCount is reset only if holdResetNeeded says so (R3 §6.3, rev 16 §4). */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)

    suspend fun setCues(id: Long, cues: CueConfig)

    /** Spec R4 §3.3: one UPDATE of the type; counter, timing, progression and cues are untouched. */
    suspend fun setType(id: Long, type: EntryType)

    /**
     * One transaction using the row's own progression and type: concurrent calls on one entry record
     * exactly one check-in. A Timer only entry keeps its total and hold count (spec R4 §3.1).
     * Unless the outcome is AlreadyToday, the same transaction logs one history point (spec R6 §3.2).
     */
    suspend fun checkIn(id: Long, clock: Clock): CheckInResult

    /**
     * One transaction: holdCount is reset only if the total changed; a stored NULL counts as the
     * starting total (R3 §6.3). Spec revision 27: a Counter entry's total outside floor..cap first
     * widens the stored progression to include it ([widenedFor]), with the hold count following
     * [holdResetNeeded]. Returns the limit that moved, or null.
     */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?): RangeChange?

    /**
     * total NULL, streaks 0, lastCheckIn NULL, holdCount 0. With [clearHistory], the entry's history
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

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        db.withTransaction {
            // Compared with the row's effective (repaired) progression, the one checkIn uses.
            val old = dao.get(id)?.progression() ?: throw EntryNotFound(id)
            writeProgression(id, old, progression)
        }
    }

    /** Writes [new] over [old] (the row's effective progression); returns whether the hold count was reset. */
    private suspend fun writeProgression(id: Long, old: ProgressionConfig, new: ProgressionConfig): Boolean {
        val reset = holdResetNeeded(old, new)
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
            val result = RepProgression.checkIn(entry.counter, entry.progression, now, clock.zone(), countsReps)
            if (result.outcome != Outcome.AlreadyToday) {
                val s = result.state
                // A Timer only entry never touches its total column, so a NULL total stays NULL (plan Spec note 6).
                val total = if (countsReps) s.total else row.total
                dao.setCounter(id, total, s.bestStreak, s.currentStreak, s.holdCount, s.lastCheckIn?.toEpochMilli())
                // Spec R6 §3.2: at is the new lastCheckIn (now); a Timer only point has no total.
                checkIns.insert(CheckInEntity(entryId = id, at = now.toEpochMilli(), total = if (countsReps) s.total else null))
            }
            result
        }
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?): RangeChange? {
        val check = SettingsValidator.currentState(total, bestStreak, currentStreak, lastCheckIn, validationClock.now())
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        return db.withTransaction {
            // The resolved total (NULL reads as the starting total) is what the Current page showed.
            val entry = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            val old = entry.counter
            // Spec revision 27: only a Counter entry shows (and so sets) its total.
            val progression = entry.progression
            val widened = if (entry.type == EntryType.WORKOUT) progression.widenedFor(total) else progression
            val progressionReset = widened != progression && writeProgression(id, progression, widened)
            val holdCount = if (progressionReset || counterHoldReset(old.total, total)) 0 else old.holdCount
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount, lastCheckIn?.toEpochMilli())
            rangeChange(progression, widened)
        }
    }

    override suspend fun resetProgress(id: Long, clearHistory: Boolean) {
        gate.awaitReady()
        db.withTransaction {
            found(id, dao.setCounter(id, total = null, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null))
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
