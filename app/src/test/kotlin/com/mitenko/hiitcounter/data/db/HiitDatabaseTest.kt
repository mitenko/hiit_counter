package com.mitenko.hiitcounter.data.db

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
}
