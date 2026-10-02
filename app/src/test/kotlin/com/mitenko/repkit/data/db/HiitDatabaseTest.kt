package com.mitenko.repkit.data.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitenko.repkit.testutil.testEntity
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
        val json = File("schemas/com.mitenko.repkit.data.db.HiitDatabase/1.json").readText()
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
        val json = File("schemas/com.mitenko.repkit.data.db.HiitDatabase/2.json").readText()
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

    @Test
    fun `schema v3 is exported with the type and cue_voice defaults`() {
        val json = File("schemas/com.mitenko.repkit.data.db.HiitDatabase/3.json").readText()
        assertTrue(Regex("\"version\"\\s*:\\s*3").containsMatchIn(json))
        assertTrue(json.contains("`type` TEXT NOT NULL DEFAULT 'WORKOUT'"))
        assertTrue(json.contains("`cue_voice` INTEGER NOT NULL DEFAULT 0"))
    }

    @Test
    fun `the 2 to 3 migration SQL makes every row a workout with the voice off`() {
        // Runs everywhere (no file-based helper), so Windows also covers the §3.2 SQL.
        val raw = SQLiteDatabase.create(null)
        try {
            raw.execSQL(V2_ENTRY_TABLE)
            raw.execSQL(v2Row(1, holdEnabled = 1, total = "65"))
            raw.execSQL(v2Row(2, holdEnabled = 0, total = "NULL"))
            HiitDatabase.MIGRATION_2_3_SQL.forEach { raw.execSQL(it) }
            assertEquals(MIGRATED_V3_ROWS, raw.rawQuery(TYPE_QUERY, null).typeColumns())
        } finally {
            raw.close()
        }
    }

    @Test
    fun `migration 2 to 3 validates through MigrationTestHelper`() {
        // Same Windows guard as the checks above: androidx.sqlite 2.6.1 mishandles backslash paths. CI runs it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase(MIGRATION_DB_3, 2).use { db ->
            db.execSQL(v2Row(1, holdEnabled = 1, total = "65"))
            db.execSQL(v2Row(2, holdEnabled = 0, total = "NULL"))
        }
        helper.runMigrationsAndValidate(MIGRATION_DB_3, 3, true, HiitDatabase.MIGRATION_2_3).use { db ->
            assertEquals(MIGRATED_V3_ROWS, db.query(TYPE_QUERY).typeColumns())
        }
    }

    /** A v2 row with the default settings, the given hold switch and a raw SQL total ("65" or "NULL"). */
    private fun v2Row(id: Long, holdEnabled: Int, total: String) =
        "INSERT INTO entry (id, name, position, prepare_sec, sets, work_sec, rest_sec, cooldown_sec, starting_total, floor, cap, " +
            "hold_at, hold_for, hold_enabled, window_hours, penalty_hours_per_rep, cue_sound, cue_vibration, total, best_streak, " +
            "current_streak, hold_count, last_check_in) " +
            "VALUES ($id, 'Workout', ${id - 1}, 10, 8, 20, 10, 0, 48, 48, 72, 64, 4, $holdEnabled, 36, 19.5, 1, 1, $total, 0, 0, 0, NULL)"

    /** (id, type, cue_voice, hold_enabled, total) per row, ordered by id. */
    private fun Cursor.typeColumns(): List<List<Any?>> = use {
        buildList {
            while (moveToNext()) add(listOf(getLong(0), getString(1), getInt(2), getInt(3), if (isNull(4)) null else getInt(4)))
        }
    }

    @Test
    fun `schema v4 exports check_in exactly as the 3 to 4 migration creates it`() {
        val json = File("schemas/com.mitenko.repkit.data.db.HiitDatabase/4.json").readText()
        assertTrue(Regex("\"version\"\\s*:\\s*4").containsMatchIn(json))
        // Room writes the table name as a placeholder. §3.1: the migration must create exactly what the entity exports.
        HiitDatabase.MIGRATION_3_4_SQL.take(2).forEach { sql ->
            assertTrue(sql, json.contains(sql.replace("`check_in`", "`\${TABLE_NAME}`")))
        }
    }

    @Test
    fun `the 3 to 4 migration SQL seeds one point per checked-in entry`() {
        // Runs everywhere (no file-based helper), so Windows also covers the §3.1 SQL.
        val raw = SQLiteDatabase.create(null)
        try {
            raw.execSQL(V3_ENTRY_TABLE)
            v3Rows.forEach { raw.execSQL(it) }
            HiitDatabase.MIGRATION_3_4_SQL.forEach { raw.execSQL(it) }
            assertEquals(SEEDED_POINTS, raw.rawQuery(POINT_QUERY, null).pointColumns())
        } finally {
            raw.close()
        }
    }

    @Test
    fun `migration 3 to 4 validates through MigrationTestHelper`() {
        // Same Windows guard as the checks above: androidx.sqlite 2.6.1 mishandles backslash paths. CI runs it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase(MIGRATION_DB_4, 3).use { db -> v3Rows.forEach { db.execSQL(it) } }
        helper.runMigrationsAndValidate(MIGRATION_DB_4, 4, true, HiitDatabase.MIGRATION_3_4).use { db ->
            assertEquals(SEEDED_POINTS, db.query(POINT_QUERY).pointColumns())
        }
    }

    @Test
    fun `deleting an entry row cascades to its check-ins`() = runTest {
        // Straight through the DAO, bypassing the repository's explicit delete: this proves Room turns on
        // PRAGMA foreign_keys, so ON DELETE CASCADE is enforced (§3.1, plan Spec note 6).
        val a = db.entryDao().insert(testEntity(name = "A", position = 0))
        val b = db.entryDao().insert(testEntity(name = "B", position = 1))
        db.checkInDao().insert(CheckInEntity(entryId = a, at = 1_000, total = 48))
        val kept = db.checkInDao().insert(CheckInEntity(entryId = b, at = 2_000, total = null))
        db.entryDao().delete(a)
        assertTrue(db.checkInDao().getForEntry(a).isEmpty())
        assertEquals(listOf(CheckInEntity(kept, b, 2_000, null)), db.checkInDao().getForEntry(b))
    }

    /** A v3 row with the default settings (starting total 50), the given type, raw SQL total and last check-in. */
    private fun v3Row(id: Long, type: String, total: String, lastCheckIn: String) =
        "INSERT INTO entry (id, name, position, type, prepare_sec, sets, work_sec, rest_sec, cooldown_sec, starting_total, " +
            "floor, cap, hold_at, hold_for, hold_enabled, window_hours, penalty_hours_per_rep, cue_sound, cue_vibration, " +
            "cue_voice, total, best_streak, current_streak, hold_count, last_check_in) " +
            "VALUES ($id, 'Workout', ${id - 1}, '$type', 10, 8, 20, 10, 0, 50, 48, 72, 64, 4, 1, 36, 19.5, 1, 1, 0, " +
            "$total, 0, 0, 0, $lastCheckIn)"

    /** A Workout with a total, a Workout with a NULL total, a Timer only entry (stored as CHECK_IN), and one never checked in. */
    private val v3Rows = listOf(
        v3Row(1, "WORKOUT", total = "65", lastCheckIn = "1790000000000"),
        v3Row(2, "WORKOUT", total = "NULL", lastCheckIn = "1790000100000"),
        v3Row(3, "CHECK_IN", total = "60", lastCheckIn = "1790000200000"),
        v3Row(4, "WORKOUT", total = "55", lastCheckIn = "NULL"),
    )

    /** (entry_id, at, total) per check_in row, ordered by entry_id. */
    private fun Cursor.pointColumns(): List<List<Any?>> = use {
        buildList { while (moveToNext()) add(listOf(getLong(0), getLong(1), if (isNull(2)) null else getInt(2))) }
    }

    @Test
    fun `schema v5 is exported with holds defaulting to the empty string`() {
        val json = File("schemas/com.mitenko.repkit.data.db.HiitDatabase/5.json").readText()
        assertTrue(Regex("\"version\"\\s*:\\s*5").containsMatchIn(json))
        assertTrue(json.contains("`holds` TEXT NOT NULL DEFAULT ''"))
        // The legacy columns stay (rev 16 §5): check_in's foreign key rules out a table rebuild.
        assertTrue(json.contains("`hold_at` INTEGER NOT NULL, `hold_for` INTEGER NOT NULL"))
    }

    @Test
    fun `the 4 to 5 migration SQL copies each row's hold into holds and keeps every other column`() {
        // Runs everywhere (no file-based helper), so Windows also covers the rev 16 §5 SQL.
        val raw = SQLiteDatabase.create(null)
        try {
            raw.execSQL(V3_ENTRY_TABLE) // v4 didn't change the entry table
            v4Rows.forEach { raw.execSQL(it) }
            val before = raw.rawQuery(ALL_QUERY, null).allColumns()
            HiitDatabase.MIGRATION_4_5_SQL.forEach { raw.execSQL(it) }
            assertEquals(MIGRATED_HOLDS, raw.rawQuery(HOLDS_QUERY, null).holdsColumn())
            assertEquals(before, raw.rawQuery(ALL_QUERY, null).allColumns(except = "holds"))
            // What Room's schema check compares with 5.json: type, NOT NULL and the '' default.
            val info = raw.rawQuery("PRAGMA table_info(entry)", null).allColumns().single { it["name"] == "holds" }
            assertEquals(listOf<Any?>("TEXT", 1L, "''"), listOf(info["type"], info["notnull"], info["dflt_value"]))
        } finally {
            raw.close()
        }
    }

    @Test
    fun `migration 4 to 5 validates through MigrationTestHelper`() {
        // Same Windows guard as the checks above: androidx.sqlite 2.6.1 mishandles backslash paths. CI runs it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase(MIGRATION_DB_5, 4).use { db -> v4Rows.forEach { db.execSQL(it) } }
        helper.runMigrationsAndValidate(MIGRATION_DB_5, 5, true, HiitDatabase.MIGRATION_4_5).use { db ->
            assertEquals(MIGRATED_HOLDS, db.query(HOLDS_QUERY).holdsColumn())
        }
    }

    /** v4 rows: the default hold, a custom hold with the switch off, and a hold for 0. */
    private val v4Rows = listOf(
        v3Row(1, "WORKOUT", total = "65", lastCheckIn = "1790000000000"),
        v3Row(2, "CHECK_IN", total = "NULL", lastCheckIn = "NULL").replace(", 64, 4, 1, 36,", ", 56, 3, 0, 36,"),
        v3Row(3, "WORKOUT", total = "60", lastCheckIn = "NULL").replace(", 64, 4, 1, 36,", ", 70, 0, 1, 36,"),
    )

    private fun Cursor.holdsColumn(): List<String> = use { buildList { while (moveToNext()) add(getString(0)) } }

    /** Every column of every row, by name, optionally leaving one out. */
    private fun Cursor.allColumns(except: String? = null): List<Map<String, Any?>> = use {
        buildList {
            while (moveToNext()) {
                add(
                    columnNames.withIndex().filter { it.value != except }.associate { (i, name) ->
                        name to when (getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                            else -> getString(i)
                        }
                    },
                )
            }
        }
    }

    @Test
    fun `workout sessions round-trip including a null reps count`() = runTest {
        val a = db.entryDao().insert(testEntity(name = "A", position = 0))
        val counter = WorkoutSessionEntity(
            entryId = a, startedAt = 1_000, endedAt = 250_000, activeSec = 240, plannedSec = 240,
            setsPlanned = 8, setsCompleted = 8, repsDone = 36, completed = true,
        )
        val timerOnly = counter.copy(startedAt = 300_000, endedAt = 320_000, setsCompleted = 0, repsDone = null, completed = false)
        val first = db.workoutSessionDao().insert(counter)
        val second = db.workoutSessionDao().insert(timerOnly)
        assertEquals(listOf(counter.copy(id = first), timerOnly.copy(id = second)), db.workoutSessionDao().getForEntry(a))
        assertEquals(2, db.workoutSessionDao().deleteForEntry(a))
        assertTrue(db.workoutSessionDao().getForEntry(a).isEmpty())
    }

    @Test
    fun `deleting an entry row cascades to its workout sessions`() = runTest {
        val a = db.entryDao().insert(testEntity(name = "A", position = 0))
        val b = db.entryDao().insert(testEntity(name = "B", position = 1))
        db.workoutSessionDao().insert(session(a))
        val kept = db.workoutSessionDao().insert(session(b))
        db.entryDao().delete(a)
        assertTrue(db.workoutSessionDao().getForEntry(a).isEmpty())
        assertEquals(listOf(session(b).copy(id = kept)), db.workoutSessionDao().getForEntry(b))
    }

    @Test
    fun `schema v6 exports workout_session exactly as the 5 to 6 migration creates it`() {
        val json = File("schemas/com.mitenko.repkit.data.db.HiitDatabase/6.json").readText()
        assertTrue(Regex("\"version\"\\s*:\\s*6").containsMatchIn(json))
        // Room writes the table name as a placeholder; rev 17 §1: the migration creates exactly what the entity exports.
        assertEquals(2, HiitDatabase.MIGRATION_5_6_SQL.size)
        HiitDatabase.MIGRATION_5_6_SQL.forEach { sql ->
            assertTrue(sql, json.contains(sql.replace("`workout_session`", "`\${TABLE_NAME}`")))
        }
    }

    @Test
    fun `the 5 to 6 migration SQL adds a cascading, indexed workout_session and keeps every existing row`() {
        // Runs everywhere (no file-based helper), so Windows also covers the rev 17 §1 SQL.
        val raw = SQLiteDatabase.create(null)
        try {
            v5Schema(raw)
            val entriesBefore = raw.rawQuery(ALL_QUERY, null).allColumns()
            val checkInsBefore = raw.rawQuery(CHECK_IN_QUERY, null).allColumns()
            HiitDatabase.MIGRATION_5_6_SQL.forEach { raw.execSQL(it) }
            assertEquals(entriesBefore, raw.rawQuery(ALL_QUERY, null).allColumns())
            assertEquals(checkInsBefore, raw.rawQuery(CHECK_IN_QUERY, null).allColumns())
            assertEquals(
                listOf("index_workout_session_entry_id"),
                raw.rawQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'workout_session'", null).holdsColumn(),
            )
            raw.execSQL("PRAGMA foreign_keys = ON")
            raw.execSQL(SESSION_ROW.format(1))
            raw.execSQL(SESSION_ROW.format(2))
            raw.execSQL("DELETE FROM check_in WHERE entry_id = 1")
            raw.execSQL("DELETE FROM entry WHERE id = 1")
            assertEquals(listOf("2"), raw.rawQuery("SELECT entry_id FROM workout_session", null).holdsColumn())
        } finally {
            raw.close()
        }
    }

    @Test
    fun `migration 5 to 6 validates through MigrationTestHelper`() {
        // Same Windows guard as the checks above: androidx.sqlite 2.6.1 mishandles backslash paths. CI runs it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase(MIGRATION_DB_6, 5).use { db ->
            v4Rows.forEach { db.execSQL(it) }
            db.execSQL("UPDATE entry SET holds = hold_at || ':' || hold_for")
        }
        helper.runMigrationsAndValidate(MIGRATION_DB_6, 6, true, HiitDatabase.MIGRATION_5_6).use { db ->
            assertEquals(MIGRATED_HOLDS, db.query(HOLDS_QUERY).holdsColumn())
            db.execSQL(SESSION_ROW.format(1))
            assertEquals(listOf("1"), db.query("SELECT entry_id FROM workout_session").holdsColumn())
        }
    }

    /** The v5 tables (entry and check_in) as the migrations up to 5 leave them, with the v4 rows and one check-in each. */
    private fun v5Schema(raw: SQLiteDatabase) {
        raw.execSQL(V3_ENTRY_TABLE)
        v4Rows.forEach { raw.execSQL(it) }
        HiitDatabase.MIGRATION_3_4_SQL.forEach { raw.execSQL(it) }
        HiitDatabase.MIGRATION_4_5_SQL.forEach { raw.execSQL(it) }
    }

    private fun session(entryId: Long) = WorkoutSessionEntity(
        entryId = entryId, startedAt = 1_000, endedAt = 61_000, activeSec = 60, plannedSec = 240,
        setsPlanned = 8, setsCompleted = 2, repsDone = null, completed = false,
    )

    private companion object {
        const val MIGRATION_DB_5 = "migration-4-5"
        const val MIGRATION_DB_6 = "migration-5-6"
        const val CHECK_IN_QUERY = "SELECT * FROM check_in ORDER BY id"
        const val SESSION_ROW = "INSERT INTO workout_session (entry_id, started_at, ended_at, active_sec, planned_sec, " +
            "sets_planned, sets_completed, reps_done, completed) VALUES (%d, 1000, 61000, 60, 240, 8, 2, 3, 0)"
        const val ALL_QUERY = "SELECT * FROM entry ORDER BY id"
        const val HOLDS_QUERY = "SELECT holds FROM entry ORDER BY id"
        val MIGRATED_HOLDS = listOf("64:4", "56:3", "70:0")
        const val MIGRATION_DB = "migration-1-2"
        const val HOLD_QUERY = "SELECT id, hold_enabled, hold_for FROM entry ORDER BY id"
        const val MIGRATION_DB_3 = "migration-2-3"
        const val TYPE_QUERY = "SELECT id, type, cue_voice, hold_enabled, total FROM entry ORDER BY id"
        const val MIGRATION_DB_4 = "migration-3-4"
        const val POINT_QUERY = "SELECT entry_id, at, total FROM check_in ORDER BY entry_id"

        /**
         * The seed (§3.1): the Workout's own total, COALESCE to the starting total (50) for a NULL total,
         * NULL for the Timer only entry, and no row for the entry that was never checked in.
         */
        val SEEDED_POINTS = listOf(
            listOf<Any?>(1L, 1_790_000_000_000L, 65),
            listOf<Any?>(2L, 1_790_000_100_000L, 50),
            listOf<Any?>(3L, 1_790_000_200_000L, null),
        )

        /** Both v2 rows after 2 → 3: Workouts with the voice off, every other column kept. */
        val MIGRATED_V3_ROWS = listOf(listOf<Any?>(1L, "WORKOUT", 0, 1, 65), listOf<Any?>(2L, "WORKOUT", 0, 0, null))

        /** The v1 `entry` table exactly as 1.json creates it. */
        const val V1_ENTRY_TABLE = "CREATE TABLE IF NOT EXISTS `entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `prepare_sec` INTEGER NOT NULL, `sets` INTEGER NOT NULL, " +
            "`work_sec` INTEGER NOT NULL, `rest_sec` INTEGER NOT NULL, `cooldown_sec` INTEGER NOT NULL, " +
            "`starting_total` INTEGER NOT NULL, `floor` INTEGER NOT NULL, `cap` INTEGER NOT NULL, `hold_at` INTEGER NOT NULL, " +
            "`hold_for` INTEGER NOT NULL, `window_hours` INTEGER NOT NULL, `penalty_hours_per_rep` REAL NOT NULL, " +
            "`cue_sound` INTEGER NOT NULL, `cue_vibration` INTEGER NOT NULL, `total` INTEGER, `best_streak` INTEGER NOT NULL, " +
            "`current_streak` INTEGER NOT NULL, `hold_count` INTEGER NOT NULL, `last_check_in` INTEGER)"

        /** The v2 `entry` table exactly as 2.json creates it. */
        const val V2_ENTRY_TABLE = "CREATE TABLE IF NOT EXISTS `entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `prepare_sec` INTEGER NOT NULL, `sets` INTEGER NOT NULL, " +
            "`work_sec` INTEGER NOT NULL, `rest_sec` INTEGER NOT NULL, `cooldown_sec` INTEGER NOT NULL, " +
            "`starting_total` INTEGER NOT NULL, `floor` INTEGER NOT NULL, `cap` INTEGER NOT NULL, `hold_at` INTEGER NOT NULL, " +
            "`hold_for` INTEGER NOT NULL, `hold_enabled` INTEGER NOT NULL DEFAULT 1, `window_hours` INTEGER NOT NULL, " +
            "`penalty_hours_per_rep` REAL NOT NULL, `cue_sound` INTEGER NOT NULL, `cue_vibration` INTEGER NOT NULL, " +
            "`total` INTEGER, `best_streak` INTEGER NOT NULL, `current_streak` INTEGER NOT NULL, " +
            "`hold_count` INTEGER NOT NULL, `last_check_in` INTEGER)"

        /** The v3 `entry` table exactly as 3.json creates it. */
        const val V3_ENTRY_TABLE = "CREATE TABLE IF NOT EXISTS `entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `type` TEXT NOT NULL DEFAULT 'WORKOUT', " +
            "`prepare_sec` INTEGER NOT NULL, `sets` INTEGER NOT NULL, `work_sec` INTEGER NOT NULL, `rest_sec` INTEGER NOT NULL, " +
            "`cooldown_sec` INTEGER NOT NULL, `starting_total` INTEGER NOT NULL, `floor` INTEGER NOT NULL, `cap` INTEGER NOT NULL, " +
            "`hold_at` INTEGER NOT NULL, `hold_for` INTEGER NOT NULL, `hold_enabled` INTEGER NOT NULL DEFAULT 1, " +
            "`window_hours` INTEGER NOT NULL, `penalty_hours_per_rep` REAL NOT NULL, `cue_sound` INTEGER NOT NULL, " +
            "`cue_vibration` INTEGER NOT NULL, `cue_voice` INTEGER NOT NULL DEFAULT 0, `total` INTEGER, " +
            "`best_streak` INTEGER NOT NULL, `current_streak` INTEGER NOT NULL, `hold_count` INTEGER NOT NULL, " +
            "`last_check_in` INTEGER)"
    }
}
