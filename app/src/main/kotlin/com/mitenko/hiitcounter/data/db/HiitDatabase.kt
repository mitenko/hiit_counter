package com.mitenko.hiitcounter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** `hiit.db` (spec §5.2). Schemas are exported to app/schemas and committed. */
@Database(entities = [EntryEntity::class, MetaEntity::class], version = 1, exportSchema = true)
abstract class HiitDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "hiit.db"
        const val KEY_V1_MIGRATED = "v1_migrated"
    }
}
