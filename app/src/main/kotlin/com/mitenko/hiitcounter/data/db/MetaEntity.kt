package com.mitenko.hiitcounter.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key/value markers, e.g. `v1_migrated = "true"` (spec §5.2, §6). */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
