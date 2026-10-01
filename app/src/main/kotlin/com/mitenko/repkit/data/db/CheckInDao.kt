package com.mitenko.repkit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** History rows (spec R6 §3). RoomEntryRepository writes them inside its own transactions. */
@Dao
interface CheckInDao {
    @Insert
    suspend fun insert(checkIn: CheckInEntity): Long

    /** One entry's rows at or after [since] (epoch ms), oldest first. */
    @Query("SELECT * FROM check_in WHERE entry_id = :entryId AND at >= :since ORDER BY at, id")
    fun observeForEntry(entryId: Long, since: Long): Flow<List<CheckInEntity>>

    /** Every entry's rows at or after [since] (epoch ms), grouped by entry and oldest first within each. */
    @Query("SELECT * FROM check_in WHERE at >= :since ORDER BY entry_id, at, id")
    fun observeSince(since: Long): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_in WHERE entry_id = :entryId ORDER BY at, id")
    suspend fun getForEntry(entryId: Long): List<CheckInEntity>

    @Query("DELETE FROM check_in WHERE entry_id = :entryId")
    suspend fun deleteForEntry(entryId: Long): Int
}
