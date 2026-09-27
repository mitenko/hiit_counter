package com.mitenko.hiitcounter.data

import android.util.Log
import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.RepProgression
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
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

    /** A new entry with default settings, appended at the end. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    /** Copies the config with a fresh counter and the §5.5 name, appended at the end. */
    suspend fun duplicate(id: Long): Long

    /** Shifts every later row down by one. */
    suspend fun delete(id: Long)

    /** Target = (position + delta) clamped to the list bounds; a no-op when it equals the current position. */
    suspend fun moveBy(id: Long, delta: Int)

    suspend fun setTiming(id: Long, timing: TimingConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)

    suspend fun setCues(id: Long, cues: CueConfig)

    /** One transaction using the row's own progression: concurrent calls on one entry record exactly one check-in. */
    suspend fun checkIn(id: Long, clock: Clock): CheckInResult

    /** The same UPDATE resets holdCount. */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)

    /** total NULL, streaks 0, lastCheckIn NULL, holdCount 0. */
    suspend fun resetProgress(id: Long)
}

/** Completes once the v1 import has run (spec §6). */
interface MigrationGate {
    suspend fun awaitReady()
}

/**
 * Room-backed [EntryRepository]. Suspend calls return on the caller's dispatcher: ViewModels call
 * it from viewModelScope (Main) and keep calling TimerController on Main — never wrap it in
 * withContext(Dispatchers.IO) around controller calls (spec §5.3 threading).
 * [validationClock] is only used to reject a last check-in in the future.
 */
class RoomEntryRepository(
    private val db: HiitDatabase,
    private val gate: MigrationGate,
    private val validationClock: Clock,
) : EntryRepository {
    private val dao = db.entryDao()

    override val entries: Flow<List<Entry>> = flow {
        gate.awaitReady()
        emitAll(dao.observeAll())
    }.map { rows -> rows.map { it.toDomain() } }

    override fun entry(id: Long): Flow<Entry?> = flow {
        gate.awaitReady()
        emitAll(dao.observe(id))
    }.map { it?.toDomain() }

    override suspend fun create(name: String): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count())) }
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
                    name = EntryNames.duplicateName(source.name),
                    position = dao.count(),
                    timing = source.timing,
                    progression = source.progression,
                    cues = source.cues,
                ),
            )
        }
    }

    override suspend fun delete(id: Long) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
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
        found(
            id,
            with(progression) { dao.setProgression(id, startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep) },
        )
    }

    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration))
    }

    override suspend fun checkIn(id: Long, clock: Clock): CheckInResult {
        gate.awaitReady()
        return db.withTransaction {
            // The row's progression is the single source of truth (spec §5.3).
            val entry = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            val now = clock.now()
            entry.counter.lastCheckIn?.let { last ->
                if (now.isBefore(last)) Log.w(TAG, "Clock moved backwards: now=$now last=$last")
            }
            val result = RepProgression.checkIn(entry.counter, entry.progression, now, clock.zone())
            if (result.outcome != Outcome.AlreadyToday) {
                val s = result.state
                dao.setCounter(id, s.total, s.bestStreak, s.currentStreak, s.holdCount, s.lastCheckIn?.toEpochMilli())
            }
            result
        }
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        // Only currentState's hints depend on the progression, so the defaults are enough to decide validity.
        val check = SettingsValidator.currentState(
            total, bestStreak, currentStreak, lastCheckIn, validationClock.now(), ProgressionConfig(),
        )
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        found(
            id,
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount = 0, lastCheckIn = lastCheckIn?.toEpochMilli()),
        )
    }

    override suspend fun resetProgress(id: Long) {
        gate.awaitReady()
        found(id, dao.setCounter(id, total = null, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null))
    }

    private fun requireName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }

    private fun found(id: Long, updatedRows: Int) {
        if (updatedRows == 0) throw EntryNotFound(id)
    }
}
