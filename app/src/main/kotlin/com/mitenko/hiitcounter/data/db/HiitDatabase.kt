package com.mitenko.hiitcounter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `hiit.db` (spec §5.2). Version 2 adds `entry.hold_enabled` (R3 §5.2), version 3 adds
 * `entry.type` and `entry.cue_voice` (R4 §3.2). Schemas are exported to app/schemas and committed.
 * Every migration is registered in the builder (StorageModule), and there is no destructive fallback.
 */
@Database(entities = [EntryEntity::class, MetaEntity::class], version = 3, exportSchema = true)
abstract class HiitDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "hiit.db"
        const val KEY_V1_MIGRATED = "v1_migrated"

        /**
         * Spec R3 §5.2, verbatim. A stored hold_for = 0 meant "hold off"; it becomes the switch off
         * with hold_for back at the default 4, so switching the hold on again gives a working hold.
         */
        internal val MIGRATION_1_2_SQL = listOf(
            "ALTER TABLE entry ADD COLUMN hold_enabled INTEGER NOT NULL DEFAULT 1",
            "UPDATE entry SET hold_enabled = 0, hold_for = 4 WHERE hold_for = 0",
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
            }
        }

        /** Spec R4 §3.2, verbatim: existing entries become Workouts with the voice off. */
        internal val MIGRATION_2_3_SQL = listOf(
            "ALTER TABLE entry ADD COLUMN type TEXT NOT NULL DEFAULT 'WORKOUT'",
            "ALTER TABLE entry ADD COLUMN cue_voice INTEGER NOT NULL DEFAULT 0",
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
