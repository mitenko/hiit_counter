package com.mitenko.repkit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.data.db.WorkoutSessionDao
import com.mitenko.repkit.data.db.WorkoutSessionEntity
import com.mitenko.repkit.domain.RunSummary
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.domain.WorkoutSnapshot
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.testutil.testEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionRecorderTest {
    private lateinit var db: HiitDatabase
    private val start = Instant.parse("2026-10-01T17:00:00Z")

    @Before
    fun open() {
        // Inline executors, so the recorder's insert finishes inside runCurrent().
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
    }

    @After
    fun close() {
        db.close()
    }

    private fun summary(entryId: Long, repsDone: Int?) = RunSummary(
        entryId = entryId, startedAt = start, endedAt = start.plusSeconds(250), activeSec = 240, plannedSec = 240,
        setsPlanned = 8, setsCompleted = 8, repsDone = repsDone, completed = true,
    )

    @Test
    fun `a recorded summary becomes one row`() = runTest {
        val a = db.entryDao().insert(testEntity(name = "A", position = 0))
        val recorder = SessionRecorder(db, backgroundScope)
        recorder.record(summary(a, repsDone = 64))
        recorder.record(summary(a, repsDone = null).copy(completed = false, startedAt = start.plusSeconds(600)))
        advanceTimeBy(1)
        runCurrent()
        val rows = db.workoutSessionDao().getForEntry(a)
        assertEquals(
            listOf(
                WorkoutSessionEntity(
                    id = rows[0].id, entryId = a, startedAt = start.toEpochMilli(), endedAt = start.plusSeconds(250).toEpochMilli(),
                    activeSec = 240, plannedSec = 240, setsPlanned = 8, setsCompleted = 8, repsDone = 64, completed = true,
                ),
                WorkoutSessionEntity(
                    id = rows[1].id, entryId = a, startedAt = start.plusSeconds(600).toEpochMilli(),
                    endedAt = start.plusSeconds(250).toEpochMilli(), activeSec = 240, plannedSec = 240, setsPlanned = 8,
                    setsCompleted = 8, repsDone = null, completed = false,
                ),
            ),
            rows,
        )
    }

    @Test
    fun `a summary for an entry that no longer exists is dropped without crashing`() = runTest {
        val recorder = SessionRecorder(db, backgroundScope)
        recorder.record(summary(entryId = 99, repsDone = 64))
        advanceTimeBy(1)
        runCurrent()
        assertTrue(db.workoutSessionDao().getForEntry(99).isEmpty())
        // The recorder still works afterwards.
        val a = db.entryDao().insert(testEntity(name = "A", position = 0))
        recorder.record(summary(a, repsDone = 64))
        runCurrent()
        assertEquals(1, db.workoutSessionDao().getForEntry(a).size)
    }

    @Test
    fun `a non-SQLite failure from the DAO is swallowed and later saves still run`() = runTest {
        val saved = mutableListOf<WorkoutSessionEntity>()
        var fail = true
        val dao = object : WorkoutSessionDao {
            override suspend fun insert(session: WorkoutSessionEntity): Long {
                if (fail) throw IllegalStateException("disk on fire")
                saved += session
                return saved.size.toLong()
            }
            override suspend fun getForEntry(entryId: Long) = saved.filter { it.entryId == entryId }
            override suspend fun deleteForEntry(entryId: Long) = 0
        }
        val recorder = SessionRecorder(dao, backgroundScope)
        recorder.record(summary(entryId = 1, repsDone = 64))
        advanceTimeBy(1)
        runCurrent() // an uncaught exception here would fail the test when runTest ends
        assertTrue(saved.isEmpty())
        fail = false
        recorder.record(summary(entryId = 1, repsDone = 64))
        runCurrent()
        assertEquals(1, saved.size)
    }

    @Test
    fun `a stopped run on a controller wired to the recorder is stored`() = runTest {
        val a = db.entryDao().insert(testEntity(name = "A", position = 0))
        val recorder = SessionRecorder(db, backgroundScope)
        val c = TimerController(backgroundScope, wallNow = { start.plusMillis(testScheduler.currentTime) }, runLog = recorder) {
            testScheduler.currentTime
        }
        c.prepare(WorkoutSnapshot(a, "A", TimingConfig(), CueConfig()))
        c.onServiceStarted()
        c.start(List(8) { 5 })
        runCurrent()
        advanceTimeBy(45_000) // 5 s into WORK 2
        runCurrent()
        c.stop()
        runCurrent()
        val row = db.workoutSessionDao().getForEntry(a).single()
        assertEquals(listOf(45, 1, 5, 0), listOf(row.activeSec, row.setsCompleted, row.repsDone, if (row.completed) 1 else 0))
        assertEquals(start.toEpochMilli(), row.startedAt)
        assertEquals(start.plusSeconds(45).toEpochMilli(), row.endedAt)
    }
}
