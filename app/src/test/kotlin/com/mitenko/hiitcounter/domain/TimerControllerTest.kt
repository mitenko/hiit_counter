package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimingConfig
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

@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    private val snapshot = WorkoutSnapshot(TimingConfig(), CueConfig())
    private val reps = List(8) { 8 }

    private fun TestScope.controller() = TimerController(backgroundScope) { testScheduler.currentTime }

    private fun TestScope.running(): TimerController = controller().also {
        it.prepare(snapshot)
        it.onServiceStarted()
        it.start(reps)
        runCurrent()
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
        assertEquals(listOf(Cue.Countdown(3), Cue.Countdown(2), Cue.Countdown(1), Cue.PhaseStart(Phase.WORK)), first)
        job.cancel()
        val second = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { second += it } }
        runCurrent()
        assertTrue(second.isEmpty())
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
}
