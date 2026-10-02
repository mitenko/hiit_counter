package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Cue
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.TimingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mitenko.repkit.testutil.fixedWallNow

@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    private val snapshot = WorkoutSnapshot(entryId = 1L, entryName = "Burpees", timing = TimingConfig(), cues = CueConfig())
    private val reps = List(8) { 8 }

    private fun TestScope.controller() = TimerController(backgroundScope, wallNow = fixedWallNow) { testScheduler.currentTime }

    private fun TestScope.running(): TimerController = controller().also {
        it.prepare(snapshot)
        it.onServiceStarted()
        it.start(reps)
        runCurrent()
    }

    @Test
    fun `a WorkoutSnapshot defaults to counting reps, and prepare carries the flag through`() = runTest {
        assertTrue(WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig()).countsReps)
        val c = controller()
        c.prepare(snapshot.copy(countsReps = false))
        assertFalse(c.snapshot!!.countsReps)
    }

    @Test
    fun `prepare only from idle`() = runTest {
        val c = controller()
        assertTrue(c.prepare(snapshot))
        assertEquals(RunStatus.PREPARING, c.status.value)
        assertEquals(ServiceStatus.Pending, c.serviceStatus.value)
        assertEquals(snapshot, c.snapshot)
        assertFalse(c.prepare(snapshot))
    }

    @Test
    fun `start requires prepare and runs once`() = runTest {
        val c = controller()
        assertFalse(c.start(reps))
        c.prepare(snapshot)
        c.onServiceStarted()
        assertTrue(c.start(reps))
        assertFalse(c.start(reps))
    }

    @Test
    fun `start requires the foreground service to have started`() = runTest {
        val pending = controller()
        pending.prepare(snapshot)
        assertFalse(pending.start(reps))
        assertEquals(RunStatus.PREPARING, pending.status.value)
        val failed = controller()
        failed.prepare(snapshot)
        failed.onServiceFailed("boom")
        assertFalse(failed.start(reps))
    }

    @Test
    fun `service status is reported while preparing`() = runTest {
        val a = controller()
        a.prepare(snapshot)
        a.onServiceStarted()
        assertEquals(ServiceStatus.Started, a.serviceStatus.value)
        val b = controller()
        b.prepare(snapshot)
        b.onServiceFailed("boom")
        assertEquals(ServiceStatus.Failed("boom"), b.serviceStatus.value)
    }

    @Test
    fun `cancelPrepare returns to idle`() = runTest {
        val c = controller()
        c.prepare(snapshot)
        c.cancelPrepare()
        assertEquals(RunStatus.IDLE, c.status.value)
        assertNull(c.snapshot)
    }

    @Test
    fun `runs to done and dismisses to idle`() = runTest {
        val c = running()
        assertEquals(RunStatus.RUNNING, c.status.value)
        assertEquals(Phase.PREPARE, c.state.value?.phase)
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
        assertEquals(Phase.DONE, c.state.value?.phase)
        c.dismissDone()
        assertEquals(RunStatus.IDLE, c.status.value)
        assertNull(c.state.value)
        assertTrue(c.prepare(snapshot))
    }

    @Test
    fun `stop goes idle without a finished cue and is idempotent`() = runTest {
        val c = controller()
        val cues = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { cues += it } }
        c.prepare(snapshot)
        c.onServiceStarted()
        c.start(reps)
        advanceTimeBy(15_000)
        runCurrent()
        c.stop()
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(RunStatus.IDLE, c.status.value)
        assertNull(c.state.value)
        assertTrue(cues.isNotEmpty())
        assertFalse(Cue.Finished in cues)
        c.stop()
        assertEquals(RunStatus.IDLE, c.status.value)
    }

    @Test
    fun `cues are one-shot and never replayed`() = runTest {
        val c = controller()
        val first = mutableListOf<Cue>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { first += it } }
        c.prepare(snapshot)
        c.onServiceStarted()
        c.start(reps)
        advanceTimeBy(11_000)
        runCurrent()
        assertEquals(listOf(Cue.Countdown(3), Cue.Countdown(2), Cue.Countdown(1), Cue.PhaseStart(Phase.WORK, reps = 8)), first)
        job.cancel()
        val second = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { second += it } }
        runCurrent()
        assertTrue(second.isEmpty())
    }

    @Test
    fun `each work start carries its own set's reps and other phases carry none`() = runTest {
        val c = controller()
        val cues = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { cues += it } }
        c.prepare(snapshot)
        c.onServiceStarted()
        c.start(listOf(1, 2, 3, 4, 5, 6, 7, 8))
        advanceTimeBy(240_000)
        runCurrent()
        val starts = cues.filterIsInstance<Cue.PhaseStart>()
        assertEquals((1..8).toList(), starts.filter { it.phase == Phase.WORK }.map { it.reps })
        assertEquals(7, starts.count { it.phase == Phase.REST })
        assertTrue(starts.filter { it.phase != Phase.WORK }.all { it.reps == null })
    }

    @Test
    fun `a Timer only run carries the countdown of sets remaining instead of reps, and other phases carry none`() = runTest {
        val c = controller()
        val cues = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { cues += it } }
        c.prepare(snapshot.copy(countsReps = false))
        c.onServiceStarted()
        c.start(listOf(11, 12, 13, 14, 15, 16, 17, 18))
        advanceTimeBy(240_000)
        runCurrent()
        val starts = cues.filterIsInstance<Cue.PhaseStart>()
        // Voice should match the display (spec revision 10): set 1 of 8 says 8, set 8 says 1.
        assertEquals((8 downTo 1).toList(), starts.filter { it.phase == Phase.WORK }.map { it.reps })
        assertTrue(starts.filter { it.phase != Phase.WORK }.all { it.reps == null })
    }

    @Test
    fun `skip calls are no-ops when not RUNNING`() = runTest {
        val idle = controller()
        idle.skipForward() // no snapshot, no engine: must not crash
        idle.skipBack()
        assertEquals(RunStatus.IDLE, idle.status.value)

        val preparing = controller()
        preparing.prepare(snapshot)
        preparing.skipForward() // PREPARING, not yet RUNNING
        preparing.skipBack()
        assertEquals(RunStatus.PREPARING, preparing.status.value)
        assertNull(preparing.state.value)

        val c = running()
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
        val doneState = c.state.value
        c.skipForward() // DONE: must not crash or change state
        c.skipBack()
        assertEquals(doneState, c.state.value)
    }

    @Test
    fun `pausing for the maximum auto-stops`() = runTest {
        val c = running()
        c.pause()
        advanceTimeBy(TimerController.MAX_PAUSE_MS - 1)
        runCurrent()
        assertEquals(RunStatus.RUNNING, c.status.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(RunStatus.IDLE, c.status.value)
    }

    @Test
    fun `resume cancels the pause timeout`() = runTest {
        val c = running()
        c.pause()
        advanceTimeBy(20 * 60_000L)
        c.resume()
        advanceTimeBy(20 * 60_000L)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
    }

    @Test
    fun `pause and resume are idempotent and ignored when not running`() = runTest {
        val idle = controller()
        idle.pause()
        idle.resume()
        assertEquals(RunStatus.IDLE, idle.status.value)

        val c = running()
        c.resume()
        c.pause()
        c.pause()
        assertTrue(c.state.value!!.paused)
        c.resume()
        c.resume()
        assertFalse(c.state.value!!.paused)
    }

    @Test
    fun `a resumed pause's timeout cannot stop a later pause`() = runTest {
        val c = running()
        c.pause()
        advanceTimeBy(TimerController.MAX_PAUSE_MS - 1_000)
        c.resume()
        runCurrent()
        c.pause()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(RunStatus.RUNNING, c.status.value)
        assertTrue(c.state.value!!.paused)
    }

    @Test
    fun `lastEntryId is set by prepare and survives the end of the run`() = runTest {
        val c = controller()
        assertNull(c.lastEntryId)
        c.prepare(snapshot)
        assertEquals(1L, c.lastEntryId)
        c.cancelPrepare()
        assertEquals(1L, c.lastEntryId)
        c.prepare(snapshot.copy(entryId = 2L))
        c.onServiceStarted()
        c.start(reps)
        runCurrent()
        c.stop()
        assertNull(c.snapshot)
        assertEquals(2L, c.lastEntryId)
    }

    @Test
    fun `isBusy follows the run's own entry through its lifecycle`() = runTest {
        val c = controller()
        assertFalse(c.isBusy(1L))
        c.prepare(snapshot)
        assertTrue(c.isBusy(1L))
        c.onServiceStarted()
        c.start(reps)
        runCurrent()
        assertTrue(c.isBusy(1L))
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
        // A leftover DONE is inert; deleting its entry is safe (exitTimer falls back to the list).
        assertFalse(c.isBusy(1L))
        c.dismissDone()
        assertFalse(c.isBusy(1L))
    }

    @Test
    fun `other entries and a stopped run are never busy`() = runTest {
        val c = controller()
        c.prepare(snapshot)
        assertFalse(c.isBusy(2L))
        c.onServiceStarted()
        c.start(reps)
        c.stop()
        assertFalse(c.isBusy(1L))
    }

    @Test
    fun `prepare seeds liveCues from the snapshot`() = runTest {
        val c = controller()
        assertNull(c.liveCues.value)
        c.prepare(snapshot)
        assertEquals(snapshot.cues, c.liveCues.value)
    }

    @Test
    fun `setCues updates liveCues while preparing or running`() = runTest {
        val c = controller()
        c.prepare(snapshot)
        val prepared = CueConfig(sound = false)
        c.setCues(prepared)
        assertEquals(prepared, c.liveCues.value)

        c.onServiceStarted()
        c.start(reps)
        runCurrent()
        val running = CueConfig(vibration = false, voice = true)
        c.setCues(running)
        assertEquals(running, c.liveCues.value)
    }

    @Test
    fun `setCues is ignored when idle`() = runTest {
        val c = controller()
        c.setCues(CueConfig(sound = false))
        assertNull(c.liveCues.value)
    }

    @Test
    fun `clearRun resets liveCues and never mutates the snapshot`() = runTest {
        val c = controller()
        c.prepare(snapshot)
        c.setCues(CueConfig(sound = false, vibration = false, voice = true))
        assertEquals(snapshot, c.snapshot)
        c.stop()
        assertNull(c.liveCues.value)
        assertNull(c.snapshot)
    }
}
