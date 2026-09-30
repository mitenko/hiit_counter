package com.mitenko.hiitcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Row-level access. Ordering invariants live in RoomEntryRepository, which wraps these in transactions. */
@Dao
interface EntryDao {
    @Query("SELECT * FROM entry ORDER BY position, id")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry WHERE id = :id")
    fun observe(id: Long): Flow<EntryEntity?>

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun get(id: Long): EntryEntity?

    @Query("SELECT * FROM entry ORDER BY position, id")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT COUNT(*) FROM entry")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE entry SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String): Int

    @Query("UPDATE entry SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int): Int

    /** Adds [delta] to every position in [low]..[high] (inclusive). */
    @Query("UPDATE entry SET position = position + :delta WHERE position BETWEEN :low AND :high")
    suspend fun shiftPositions(low: Int, high: Int, delta: Int)

    @Query(
        "UPDATE entry SET prepare_sec = :prepareSec, sets = :sets, work_sec = :workSec, rest_sec = :restSec, " +
            "cooldown_sec = :cooldownSec WHERE id = :id",
    )
    suspend fun setTiming(id: Long, prepareSec: Int, sets: Int, workSec: Int, restSec: Int, cooldownSec: Int): Int

    /**
     * Writes the progression group, resetting hold_count only when [resetHoldCount] is true. The
     * repository decides that with holdResetNeeded inside the same transaction (spec R3 §6.3).
     */
    @Query(
        "UPDATE entry SET starting_total = :startingTotal, floor = :floor, cap = :cap, hold_at = :holdAt, " +
            "hold_for = :holdFor, hold_enabled = :holdEnabled, window_hours = :windowHours, " +
            "penalty_hours_per_rep = :penaltyHoursPerRep, " +
            "hold_count = CASE WHEN :resetHoldCount THEN 0 ELSE hold_count END WHERE id = :id",
    )
    suspend fun setProgression(
        id: Long,
        startingTotal: Int,
        floor: Int,
        cap: Int,
        holdAt: Int,
        holdFor: Int,
        holdEnabled: Boolean,
        windowHours: Int,
        penaltyHoursPerRep: Double,
        resetHoldCount: Boolean,
    ): Int

    @Query("UPDATE entry SET cue_sound = :sound, cue_vibration = :vibration, cue_voice = :voice WHERE id = :id")
    suspend fun setCues(id: Long, sound: Boolean, vibration: Boolean, voice: Boolean): Int

    /** Spec R4 §3.3: the type alone. Every other column is kept, so switching back restores everything. */
    @Query("UPDATE entry SET type = :type WHERE id = :id")
    suspend fun setType(id: Long, type: String): Int

    /** Writes the whole counter group in one UPDATE. */
    @Query(
        "UPDATE entry SET total = :total, best_streak = :bestStreak, current_streak = :currentStreak, " +
            "hold_count = :holdCount, last_check_in = :lastCheckIn WHERE id = :id",
    )
    suspend fun setCounter(id: Long, total: Int?, bestStreak: Int, currentStreak: Int, holdCount: Int, lastCheckIn: Long?): Int
}
