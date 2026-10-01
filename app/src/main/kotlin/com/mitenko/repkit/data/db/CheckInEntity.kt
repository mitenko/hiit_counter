package com.mitenko.repkit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logged check-in (spec R6 §3.1). [at] is epoch ms, like entry.last_check_in; [total] is the rep
 * total after that check-in, NULL for a Timer only entry. Deleting the entry cascades, and
 * RoomEntryRepository also deletes the rows explicitly, so deletion never depends on the pragma.
 */
@Entity(
    tableName = "check_in",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["entry_id", "at"])],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
    val at: Long,
    val total: Int?,
)
