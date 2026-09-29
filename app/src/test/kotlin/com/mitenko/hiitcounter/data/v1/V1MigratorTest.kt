package com.mitenko.hiitcounter.data.v1

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.data.RoomEntryRepository
import com.mitenko.hiitcounter.data.StoredCounter
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.data.db.MetaEntity
import com.mitenko.hiitcounter.data.entryEntity
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.testEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class V1MigratorTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: HiitDatabase
    private lateinit var dir: File

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dir = tmp.newFolder("datastore")
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Call once per test: two DataStores on app.preferences_pb in one process would conflict. */
    private fun TestScope.appPreferences() = AppPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "app.preferences_pb") }),
    )

    private fun TestScope.migrator(prefs: AppPreferences, deleteFile: (File) -> Boolean = { it.delete() }) =
        V1Migrator(dir, db, prefs, backgroundScope, Dispatchers.IO, deleteFile)

    /** Writes a v1 file the way v1 did, then closes its DataStore so the file is released. */
    private suspend fun seed(fileName: String, block: (MutablePreferences) -> Unit) {
        val job = Job()
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { File(dir, fileName) })
            .edit { block(it) }
        job.cancelAndJoin()
    }

    private suspend fun marker() = db.metaDao().get(HiitDatabase.KEY_V1_MIGRATED)

    private suspend fun rows() = db.entryDao().getAll()

    @Test
    fun `a fresh install writes only the marker`() = runTest {
        migrator(appPreferences()).ready.await()
        assertEquals("true", marker())
        assertTrue(rows().isEmpty())
        assertFalse(File(dir, "app.preferences_pb").exists())
    }

    @Test
    fun `both v1 files become one Workout entry with identical values`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) {
            it[V1Keys.SETS] = 6
            it[V1Keys.WORK_SEC] = 30
            it[V1Keys.CAP] = 80
            it[V1Keys.PENALTY_HOURS_PER_REP] = 12.5
            it[V1Keys.CUE_SOUND] = false
        }
        seed(V1Migrator.COUNTER_FILE) {
            it[V1Keys.TOTAL] = 65
            it[V1Keys.BEST_STREAK] = 24
            it[V1Keys.CURRENT_STREAK] = 4
            it[V1Keys.HOLD_COUNT] = 2
            it[V1Keys.LAST_CHECK_IN] = 1_790_000_000_123L
        }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertEquals(
            entryEntity(
                "Workout", 0,
                TimingConfig(sets = 6, workSec = 30),
                ProgressionConfig(cap = 80, penaltyHoursPerRep = 12.5),
                CueConfig(sound = false),
                StoredCounter(total = 65, bestStreak = 24, currentStreak = 4, holdCount = 2, lastCheckIn = 1_790_000_000_123L),
            ).copy(id = row.id),
            row,
        )
        assertEquals("true", marker())
    }

    @Test
    fun `only the settings file imports settings with a fresh counter`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 5 }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertEquals(5, row.sets)
        assertNull(row.total)
        assertEquals(0, row.bestStreak)
    }

    @Test
    fun `only the counter file imports the counter with default settings`() = runTest {
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 70 }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertEquals(entryEntity("Workout", 0, counter = StoredCounter(total = 70)).copy(id = row.id), row)
    }

    @Test
    fun `re-running is a no-op`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        val prefs = appPreferences()
        migrator(prefs).ready.await()
        migrator(prefs).ready.await()
        assertEquals(1, rows().size)
    }

    @Test
    fun `the v1 files are read without any Hilt DataStore and released afterwards`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        migrator(appPreferences()).ready.await()
        assertEquals(6, rows().single().sets)
        // Throws "There are multiple DataStores active for the same file" if the migration scope leaked.
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, V1Migrator.SETTINGS_FILE) })
            .data.first()
    }

    @Test
    fun `a create racing the migration still yields Workout first and never an empty list`() = runTest {
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        val m = migrator(appPreferences())
        val repo = RoomEntryRepository(db, m, FakeClock())
        val seen = mutableListOf<List<String>>()
        val collector = backgroundScope.launch { repo.entries.collect { list -> seen += list.map { it.name } } }
        repo.create("Burpees")
        assertEquals(listOf("Workout", "Burpees"), repo.entries.first { it.size == 2 }.map { it.name })
        assertTrue("saw $seen", seen.none { it.isEmpty() })
        collector.cancel()
    }

    @Test
    fun `rows that exist without the marker shift up behind Workout`() = runTest {
        db.entryDao().insert(testEntity(name = "Restored", position = 0))
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        migrator(appPreferences()).ready.await()
        assertEquals(listOf("Workout" to 0, "Restored" to 1), rows().map { it.name to it.position })
    }

    // deleteFile = { false } keeps the files past cleanup (added in 7.5), so the bytes can be checked after the import.
    @Test
    fun `a corrupted v1 file falls back to defaults and is never rewritten`() = runTest {
        val corrupt = File(dir, V1Migrator.SETTINGS_FILE).apply { writeText("not a protobuf") }
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        migrator(appPreferences(), deleteFile = { false }).ready.await()
        val row = rows().single()
        assertEquals(entryEntity("Workout", 0, counter = StoredCounter(total = 65)).copy(id = row.id), row)
        assertEquals("not a protobuf", corrupt.readText())
    }

    @Test
    fun `a corrupted counter file yields a fresh counter and is never rewritten`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        val corrupt = File(dir, V1Migrator.COUNTER_FILE).apply { writeText("not a protobuf") }
        migrator(appPreferences(), deleteFile = { false }).ready.await()
        val row = rows().single()
        assertEquals(entryEntity("Workout", 0, TimingConfig(sets = 6)).copy(id = row.id), row)
        assertEquals("not a protobuf", corrupt.readText())
    }

    @Test
    fun `the notification flag is copied when the v1 settings file exists`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.NOTIFICATION_ASKED] = true }
        val prefs = appPreferences()
        migrator(prefs).ready.await()
        assertTrue(prefs.notificationPermissionAsked.first())
    }

    @Test
    fun `the notification flag is not created without a v1 settings file`() = runTest {
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        migrator(appPreferences()).ready.await()
        assertFalse(File(dir, "app.preferences_pb").exists())
    }

    @Test
    fun `v1 files and their tmp siblings are deleted and the marker is written`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        File(dir, "${V1Migrator.SETTINGS_FILE}.tmp").writeText("partial")
        File(dir, "${V1Migrator.COUNTER_FILE}.tmp").writeText("partial")
        migrator(appPreferences()).ready.await()
        assertEquals("true", marker())
        listOf(
            V1Migrator.SETTINGS_FILE, V1Migrator.COUNTER_FILE,
            "${V1Migrator.SETTINGS_FILE}.tmp", "${V1Migrator.COUNTER_FILE}.tmp",
        ).forEach { assertFalse("$it should be deleted", File(dir, it).exists()) }
    }

    @Test
    fun `a non-corruption read failure writes no marker, keeps the files and retries`() = runTest {
        File(dir, V1Migrator.SETTINGS_FILE).mkdir()
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        migrator(appPreferences()).ready.await()
        assertNull(marker())
        assertTrue(rows().isEmpty())
        assertTrue(File(dir, V1Migrator.COUNTER_FILE).exists())

        File(dir, V1Migrator.SETTINGS_FILE).delete()
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertEquals(
            entryEntity("Workout", 0, TimingConfig(sets = 6), counter = StoredCounter(total = 65)).copy(id = row.id),
            row,
        )
        assertEquals("true", marker())
    }

    @Test
    fun `a crash after the commit completes the cleanup without a duplicate entry`() = runTest {
        // The import and the marker committed, then the process died before step 4.
        db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, "true"))
        db.entryDao().insert(testEntity(name = "Workout"))
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.NOTIFICATION_ASKED] = true }
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        val prefs = appPreferences()
        migrator(prefs).ready.await()
        assertEquals(1, rows().size)
        assertTrue(prefs.notificationPermissionAsked.first())
        assertFalse(File(dir, V1Migrator.SETTINGS_FILE).exists())
        assertFalse(File(dir, V1Migrator.COUNTER_FILE).exists())
    }

    @Test
    fun `a v1 hold_for of 0 migrates as the hold switched off`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.HOLD_FOR] = 0 }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertFalse(row.holdEnabled)
        assertEquals(4, row.holdFor)
    }
}
