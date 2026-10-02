package com.mitenko.repkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Run log rows (spec revision 17). SessionRecorder inserts them; RoomEntryRepository deletes them. */
@Dao
interface WorkoutSessionDao {
    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    /** One entry's sessions, oldest first. */
    @Query("SELECT * FROM workout_session WHERE entry_id = :entryId ORDER BY started_at, id")
    suspend fun getForEntry(entryId: Long): List<WorkoutSessionEntity>

    @Query("DELETE FROM workout_session WHERE entry_id = :entryId")
    suspend fun deleteForEntry(entryId: Long): Int
}
