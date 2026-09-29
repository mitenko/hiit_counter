package com.mitenko.hiitcounter.data.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitenko.hiitcounter.testutil.testEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HiitDatabaseTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HiitDatabase::class.java)

    private lateinit var db: HiitDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun `entry rows round-trip including nulls`() = runTest {
        val row = testEntity(name = "Burpees", position = 0)
        val id = db.entryDao().insert(row)
        val stored = db.entryDao().get(id)!!
        assertEquals(row.copy(id = id), stored)
        assertNull(stored.total)
        assertNull(stored.lastCheckIn)
    }

    @Test
    fun `meta values are upserted by key`() = runTest {
        db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, "false"))
        db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, "true"))
        assertEquals("true", db.metaDao().get(HiitDatabase.KEY_V1_MIGRATED))
        assertNull(db.metaDao().get("missing"))
    }

    @Test
    fun `schema v1 is exported with a non-unique position index`() {
        // Unit tests run with the module directory (app/) as the working directory.
        val json = File("schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/1.json").readText()
        assertTrue(Regex("\"tableName\"\\s*:\\s*\"entry\"").containsMatchIn(json))
        assertTrue(Regex("\"tableName\"\\s*:\\s*\"meta\"").containsMatchIn(json))
        assertTrue(Regex("\"name\"\\s*:\\s*\"index_entry_position\"\\s*,\\s*\"unique\"\\s*:\\s*false").containsMatchIn(json))
    }

    @Test
    fun `schema v1 opens through MigrationTestHelper`() {
        // androidx.sqlite 2.6.1's SupportSQLiteDriver compares database names with substringAfterLast('/'),
        // which fails on Windows paths (backslashes) for file-based MigrationTestHelper databases.
        // Linux CI runs this test; local Windows runs skip it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase("migration-helper-check", 1).close()
    }

    @Test
    fun `schema v2 is exported with hold_enabled defaulting to 1`() {
        val json = File("schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/2.json").readText()
        assertTrue(Regex("\"version\"\\s*:\\s*2").containsMatchIn(json))
        assertTrue(json.contains("`hold_enabled` INTEGER NOT NULL DEFAULT 1"))
    }

    @Test
    fun `the 1 to 2 migration SQL switches the hold off where hold_for was 0 and restores hold_for 4`() {
        // Runs everywhere (no file-based helper), so Windows also covers the §5.2 SQL.
        val raw = SQLiteDatabase.create(null)
        try {
            raw.execSQL(V1_ENTRY_TABLE)
            raw.execSQL(v1Row(1, holdFor = 4))
            raw.execSQL(v1Row(2, holdFor = 0))
            HiitDatabase.MIGRATION_1_2_SQL.forEach { raw.execSQL(it) }
            assertEquals(listOf(Triple(1L, 1, 4), Triple(2L, 0, 4)), raw.rawQuery(HOLD_QUERY, null).holdColumns())
        } finally {
            raw.close()
        }
    }

    @Test
    fun `migration 1 to 2 validates through MigrationTestHelper`() {
        // Same Windows guard as the v1 check above: androidx.sqlite 2.6.1 mishandles backslash paths. CI runs it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase(MIGRATION_DB, 1).use { db ->
            db.execSQL(v1Row(1, holdFor = 4))
            db.execSQL(v1Row(2, holdFor = 0))
        }
        helper.runMigrationsAndValidate(MIGRATION_DB, 2, true, HiitDatabase.MIGRATION_1_2).use { db ->
            assertEquals(listOf(Triple(1L, 1, 4), Triple(2L, 0, 4)), db.query(HOLD_QUERY).holdColumns())
        }
    }

    /** A v1 row with the default settings and the given hold_for. */
    private fun v1Row(id: Long, holdFor: Int) =
        "INSERT INTO entry (id, name, position, prepare_sec, sets, work_sec, rest_sec, cooldown_sec, starting_total, floor, cap, " +
            "hold_at, hold_for, window_hours, penalty_hours_per_rep, cue_sound, cue_vibration, total, best_streak, " +
            "current_streak, hold_count, last_check_in) " +
            "VALUES ($id, 'Workout', ${id - 1}, 10, 8, 20, 10, 0, 48, 48, 72, 64, $holdFor, 36, 19.5, 1, 1, NULL, 0, 0, 0, NULL)"

    /** (id, hold_enabled, hold_for) per row, ordered by id. */
    private fun Cursor.holdColumns(): List<Triple<Long, Int, Int>> = use {
        buildList { while (moveToNext()) add(Triple(getLong(0), getInt(1), getInt(2))) }
    }

    private companion object {
        const val MIGRATION_DB = "migration-1-2"
        const val HOLD_QUERY = "SELECT id, hold_enabled, hold_for FROM entry ORDER BY id"

        /** The v1 `entry` table exactly as 1.json creates it. */
        const val V1_ENTRY_TABLE = "CREATE TABLE IF NOT EXISTS `entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `prepare_sec` INTEGER NOT NULL, `sets` INTEGER NOT NULL, " +
            "`work_sec` INTEGER NOT NULL, `rest_sec` INTEGER NOT NULL, `cooldown_sec` INTEGER NOT NULL, " +
            "`starting_total` INTEGER NOT NULL, `floor` INTEGER NOT NULL, `cap` INTEGER NOT NULL, `hold_at` INTEGER NOT NULL, " +
            "`hold_for` INTEGER NOT NULL, `window_hours` INTEGER NOT NULL, `penalty_hours_per_rep` REAL NOT NULL, " +
            "`cue_sound` INTEGER NOT NULL, `cue_vibration` INTEGER NOT NULL, `total` INTEGER, `best_streak` INTEGER NOT NULL, " +
            "`current_streak` INTEGER NOT NULL, `hold_count` INTEGER NOT NULL, `last_check_in` INTEGER)"
    }
}
