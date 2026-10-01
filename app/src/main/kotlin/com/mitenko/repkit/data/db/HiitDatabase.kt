package com.mitenko.repkit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `hiit.db` (spec §5.2). Version 2 adds `entry.hold_enabled` (R3 §5.2), version 3 adds
 * `entry.type` and `entry.cue_voice` (R4 §3.2), version 4 adds the `check_in` history table
 * (R6 §3.1), version 5 adds `entry.holds` (rev 16 §5). Schemas are exported to app/schemas and committed. Every migration is registered in
 * the builder (StorageModule), and there is no destructive fallback.
 */
@Database(entities = [EntryEntity::class, MetaEntity::class, CheckInEntity::class], version = 5, exportSchema = true)
abstract class HiitDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun metaDao(): MetaDao
    abstract fun checkInDao(): CheckInDao

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

        /**
         * Spec R6 §3.1. The CREATE TABLE and CREATE INDEX are Room's own text for CheckInEntity (the
         * 4.json createSql, checked by HiitDatabaseTest; plan Spec note 1). The seed is the spec's
         * INSERT … SELECT, verbatim: one point per checked-in entry, a Workout's total falling back
         * to its starting total, NULL for a Timer only entry.
         */
        internal val MIGRATION_3_4_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `check_in` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entry_id` INTEGER NOT NULL, `at` INTEGER NOT NULL, `total` INTEGER, " +
                "FOREIGN KEY(`entry_id`) REFERENCES `entry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_check_in_entry_id_at` ON `check_in` (`entry_id`, `at`)",
            "INSERT INTO check_in (entry_id, at, total) " +
                "SELECT id, last_check_in, CASE WHEN type = 'WORKOUT' THEN COALESCE(total, starting_total) ELSE NULL END " +
                "FROM entry WHERE last_check_in IS NOT NULL",
        )

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach { db.execSQL(it) }
            }
        }

        /**
         * Spec rev 16 §5, verbatim: each row's single hold becomes a one-item list. The legacy
         * hold_at / hold_for columns stay (check_in's foreign key rules out a table rebuild).
         */
        internal val MIGRATION_4_5_SQL = listOf(
            "ALTER TABLE entry ADD COLUMN holds TEXT NOT NULL DEFAULT ''",
            "UPDATE entry SET holds = hold_at || ':' || hold_for",
        )

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
