package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.TimingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Spec revision 17 §3: every run that started ends with exactly one [RunSummary] on the controller's
 * [RunLog]. Default timing: PREPARE 0–10 s, WORK n at 10 + 30(n−1) s for 20 s, REST 10 s between sets,
 * no cooldown, 240 s in all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunSummaryTest {
    private val snapshot = WorkoutSnapshot(entryId = 7L, entryName = "Burpees", timing = TimingConfig(), cues = CueConfig())
    private val reps = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val base = Instant.parse("2026-10-01T17:00:00Z")
    private val logged = mutableListOf<RunSummary>()

    private fun TestScope.controller() = TimerController(
        scope = backgroundScope,
        nowMs = { testScheduler.currentTime },
        wallNow = { base.plusMillis(testScheduler.currentTime) },
        runLog = { logged += it },
    )

    private fun TestScope.running(snap: WorkoutSnapshot = snapshot): TimerController = controller().also {
        it.prepare(snap)
        it.onServiceStarted()
        advanceTimeBy(3_000) // prepare-to-start time is not part of the run
        it.start(reps)
        runCurrent()
    }

    @Test
    fun `a run to DONE logs one completed summary with every set and the reps summed`() = runTest {
        val c = running()
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
        c.dismissDone()
        assertEquals(
            listOf(
                RunSummary(
                    entryId = 7L, startedAt = base.plusSeconds(3), endedAt = base.plusSeconds(243), activeSec = 240,
                    plannedSec = 240, setsPlanned = 8, setsCompleted = 8, repsDone = 36, completed = true,
                ),
            ),
            logged,
        )
    }

    @Test
    fun `stop mid set 3 logs an early end with two sets done`() = runTest {
        val c = running()
        advanceTimeBy(80_000) // 10 s into WORK 3
        runCurrent()
        assertEquals(Phase.WORK to 3, c.state.value!!.phase to c.state.value!!.set)
        c.stop()
        c.stop() // idempotent: still one summary
        val s = logged.single()
        assertEquals(false, s.completed)
        assertEquals(2, s.setsCompleted)
        assertEquals(3, s.repsDone)
        assertEquals(80, s.activeSec)
        assertEquals(base.plusSeconds(83), s.endedAt)
    }

    @Test
    fun `skipping forward through a work phase counts it`() = runTest {
        val c = running()
        advanceTimeBy(12_000) // 2 s into WORK 1
        runCurrent()
        c.skipForward()
        runCurrent()
        assertEquals(Phase.REST, c.state.value!!.phase)
        c.stop()
        assertEquals(1, logged.single().setsCompleted)
        assertEquals(1, logged.single().repsDone)
        assertEquals(12, logged.single().activeSec)
    }

    @Test
    fun `skipping back over a completed set and running it again counts it once`() = runTest {
        val c = running()
        advanceTimeBy(31_000) // 1 s into REST before set 2: set 1 is done
        runCurrent()
        c.skipBack() // within 2 s: back to WORK 1
        runCurrent()
        assertEquals(Phase.WORK to 1, c.state.value!!.phase to c.state.value!!.set)
        advanceTimeBy(20_000) // WORK 1 again, to its end
        runCurrent()
        assertEquals(Phase.REST, c.state.value!!.phase)
        c.stop()
        assertEquals(1, logged.single().setsCompleted)
        assertEquals(1, logged.single().repsDone)
    }

    @Test
    fun `restarting a work phase does not count it`() = runTest {
        val c = running()
        advanceTimeBy(15_000) // 5 s into WORK 1
        runCurrent()
        c.skipBack() // restarts WORK 1
        runCurrent()
        c.stop()
        assertEquals(0, logged.single().setsCompleted)
        assertEquals(0, logged.single().repsDone)
    }

    @Test
    fun `pauses are left out of the active time but not the wall times`() = runTest {
        val c = running()
        advanceTimeBy(15_000)
        runCurrent()
        c.pause()
        advanceTimeBy(60_000)
        c.resume()
        advanceTimeBy(5_000)
        runCurrent()
        c.stop()
        val s = logged.single()
        assertEquals(20, s.activeSec)
        assertEquals(base.plusSeconds(3), s.startedAt)
        assertEquals(base.plusSeconds(83), s.endedAt)
    }

    @Test
    fun `the pause timeout's auto-stop logs an early end`() = runTest {
        val c = running()
        advanceTimeBy(5_000)
        runCurrent()
        c.pause()
        advanceTimeBy(TimerController.MAX_PAUSE_MS)
        runCurrent()
        assertEquals(RunStatus.IDLE, c.status.value)
        assertEquals(5, logged.single().activeSec)
        assertEquals(false, logged.single().completed)
    }

    @Test
    fun `a cancelled prepare or a failed service logs nothing`() = runTest {
        val cancelled = controller()
        cancelled.prepare(snapshot)
        cancelled.onServiceStarted()
        cancelled.cancelPrepare()
        val stopped = controller()
        stopped.prepare(snapshot)
        stopped.stop() // stop while preparing is a cancel
        val failed = controller()
        failed.prepare(snapshot)
        failed.onServiceFailed("boom")
        failed.start(reps)
        failed.cancelPrepare()
        runCurrent()
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `a Timer only run logs no reps`() = runTest {
        val c = running(snapshot.copy(countsReps = false))
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
        val s = logged.single()
        assertNull(s.repsDone)
        assertEquals(8, s.setsCompleted)
        assertEquals(true, s.completed)
    }

    @Test
    fun `each run logs its own summary`() = runTest {
        val c = running()
        advanceTimeBy(240_000)
        runCurrent()
        c.dismissDone()
        c.prepare(snapshot.copy(entryId = 9L))
        c.onServiceStarted()
        c.start(reps)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        c.stop()
        assertEquals(listOf(7L to true, 9L to false), logged.map { it.entryId to it.completed })
        assertEquals(0, logged[1].setsCompleted)
        assertEquals(1, logged[1].activeSec)
    }
}
