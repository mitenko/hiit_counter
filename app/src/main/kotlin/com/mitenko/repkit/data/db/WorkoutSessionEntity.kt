package com.mitenko.repkit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One timer run (spec revision 17 §1), written once when the run ends. Times are wall-clock epoch ms;
 * [activeSec] leaves pauses out; [repsDone] is NULL for a Timer only run; [completed] is true when the
 * run reached DONE. Deleting the entry cascades, and RoomEntryRepository also deletes the rows explicitly.
 */
@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["entry_id"])],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "active_sec") val activeSec: Int,
    @ColumnInfo(name = "planned_sec") val plannedSec: Int,
    @ColumnInfo(name = "sets_planned") val setsPlanned: Int,
    @ColumnInfo(name = "sets_completed") val setsCompleted: Int,
    @ColumnInfo(name = "reps_done") val repsDone: Int?,
    val completed: Boolean,
)
