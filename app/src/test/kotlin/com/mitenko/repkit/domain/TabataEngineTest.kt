package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Cue
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.Phase.COOLDOWN
import com.mitenko.repkit.domain.model.Phase.DONE
import com.mitenko.repkit.domain.model.Phase.PREPARE
import com.mitenko.repkit.domain.model.Phase.REST
import com.mitenko.repkit.domain.model.Phase.WORK
import com.mitenko.repkit.domain.model.TimerState
import com.mitenko.repkit.domain.model.TimingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabataEngineTest {
    private data class Tick(val atMs: Long, val phase: Phase, val set: Int, val left: Int, val elapsed: Int, val paused: Boolean = false)

    private class Recorder {
        val states = mutableListOf<Pair<Long, TimerState>>()
        val cues = mutableListOf<Cue>()
        val ticks get() = states.map { (t, s) -> Tick(t, s.phase, s.set, s.phaseSecondsLeft, s.elapsedSec, s.paused) }
    }

    private val small = TimingConfig(prepareSec = 2, sets = 2, workSec = 3, restSec = 2, cooldownSec = 1)
    private val single5 = TimingConfig(prepareSec = 0, sets = 1, workSec = 5, restSec = 0, cooldownSec = 0)

    private fun TestScope.engine(timing: TimingConfig, reps: List<Int>, rec: Recorder) = TabataEngine(
        timing = timing,
        repsPerSet = reps,
        nowMs = { testScheduler.currentTime },
        onState = { rec.states += testScheduler.currentTime to it },
        onCue = { rec.cues += it },
    )

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `emits the exact tick sequence`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(
            listOf(
                Tick(0, PREPARE, 1, 2, 0), Tick(1000, PREPARE, 1, 1, 1),
                Tick(2000, WORK, 1, 3, 2), Tick(3000, WORK, 1, 2, 3), Tick(4000, WORK, 1, 1, 4),
                Tick(5000, REST, 2, 2, 5), Tick(6000, REST, 2, 1, 6),
                Tick(7000, WORK, 2, 3, 7), Tick(8000, WORK, 2, 2, 8), Tick(9000, WORK, 2, 1, 9),
                Tick(10000, COOLDOWN, 2, 1, 10),
                Tick(11000, DONE, 2, 0, 11),
            ),
            rec.ticks,
        )
    }

    @Test
    fun `reps follow the current or upcoming set`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(prepareSec = 1, sets = 2, workSec = 1, restSec = 1, cooldownSec = 1), listOf(5, 4), rec)
        launch { e.run() }
        advanceUntilIdle()
        val reps = rec.states.map { (_, s) -> s.phase to s.repsThisSet }
        assertEquals(listOf(PREPARE to 5, WORK to 5, REST to 4, WORK to 4, COOLDOWN to 0, DONE to 0), reps)
        assertEquals(9, rec.states.last().second.totalReps)
    }

    @Test
    fun `zero-duration phases are skipped`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(prepareSec = 0, sets = 2, workSec = 1, restSec = 0, cooldownSec = 0), listOf(3, 3), rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(listOf(Tick(0, WORK, 1, 1, 0), Tick(1000, WORK, 2, 1, 1), Tick(2000, DONE, 2, 0, 2)), rec.ticks)
    }

    @Test
    fun `default workout ends at four minutes without drift`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(), List(8) { 8 }, rec)
        launch { e.run() }
        advanceUntilIdle()
        val last = rec.states.last()
        assertEquals(240_000L, last.first)
        assertEquals(DONE, last.second.phase)
        assertEquals(240, last.second.elapsedSec)
        rec.states.forEach { (t, s) -> assertEquals(s.elapsedSec * 1000L, t) }
    }

    @Test
    fun `emits cues at phase starts and in the final seconds`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(
            listOf(
                Cue.Countdown(1),
                Cue.PhaseStart(WORK), Cue.Countdown(2), Cue.Countdown(1),
                Cue.PhaseStart(REST), Cue.Countdown(1),
                Cue.PhaseStart(WORK), Cue.Countdown(2), Cue.Countdown(1),
                Cue.PhaseStart(COOLDOWN),
                Cue.Finished,
            ),
            rec.cues,
        )
    }

    @Test
    fun `zero-duration phases emit no cues`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(prepareSec = 0, sets = 2, workSec = 1, restSec = 0, cooldownSec = 0), listOf(3, 3), rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(listOf(Cue.PhaseStart(WORK), Cue.PhaseStart(WORK), Cue.Finished), rec.cues)
    }

    @Test
    fun `finished is emitted exactly once`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(), List(8) { 8 }, rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(1, rec.cues.count { it == Cue.Finished })
        assertEquals(Cue.Finished, rec.cues.last())
    }

    @Test
    fun `cancelling the run emits no DONE and no Finished`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(), List(8) { 8 }, rec)
        val job = launch { e.run() }
        advance(15_000)
        job.cancel()
        advanceUntilIdle()
        assertFalse(rec.states.any { it.second.phase == DONE })
        assertFalse(Cue.Finished in rec.cues)
    }

    @Test
    fun `pause mid-second keeps the sub-second remainder`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        launch { e.run() }
        runCurrent()
        advance(1500)
        e.pause()
        advance(10_000)
        e.resume()
        advance(500)
        assertEquals(
            listOf(
                Tick(0, WORK, 1, 5, 0),
                Tick(1000, WORK, 1, 4, 1),
                Tick(1500, WORK, 1, 4, 1, paused = true),
                Tick(11_500, WORK, 1, 4, 1, paused = false),
                Tick(12_000, WORK, 1, 3, 2),
            ),
            rec.ticks,
        )
    }

    @Test
    fun `pause exactly at a boundary resumes a full second later`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        launch { e.run() }
        runCurrent()
        advance(2000)
        e.pause()
        advance(3000)
        e.resume()
        advance(1000)
        assertEquals(Tick(6000, WORK, 1, 2, 3), rec.ticks.last())
    }

    @Test
    fun `pause and resume are idempotent`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        launch { e.run() }
        runCurrent()
        e.resume()
        e.pause()
        e.pause()
        assertTrue(e.isPaused)
        e.resume()
        e.resume()
        assertFalse(e.isPaused)
        assertEquals(listOf(false, true, false), rec.states.map { it.second.paused })
    }

    @Test
    fun `skip calls before run starts or after DONE are no-ops`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        e.skipForward() // before run() starts
        e.skipBack()
        assertTrue(rec.states.isEmpty())

        launch { e.run() }
        advanceUntilIdle()
        val afterDone = rec.ticks
        e.skipForward() // after DONE
        e.skipBack()
        runCurrent()
        assertEquals(afterDone, rec.ticks)
    }

    @Test
    fun `skipForward mid-WORK goes straight to the next REST and fires its PhaseStart`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advance(3000) // 1s into WORK set 1 (Tick(3000, WORK, 1, 2, 3))
        e.skipForward()
        runCurrent()
        // REST set 2 starts at once, elapsedSec jumps to the sum of PREPARE + WORK (2 + 3 = 5).
        assertEquals(Tick(3000, REST, 2, 2, 5), rec.ticks.last())
        assertEquals(Cue.PhaseStart(REST), rec.cues.last())
    }

    @Test
    fun `skipForward goes straight to the next WORK when rest is 0`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(prepareSec = 0, sets = 2, workSec = 3, restSec = 0, cooldownSec = 0), listOf(3, 3), rec)
        launch { e.run() }
        advance(1000) // Tick(1000, WORK, 1, 2, 1)
        e.skipForward()
        runCurrent()
        assertEquals(Tick(1000, WORK, 2, 3, 3), rec.ticks.last())
        assertEquals(Cue.PhaseStart(WORK), rec.cues.last())
    }

    @Test
    fun `skipForward from COOLDOWN gives DONE and Finished`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advance(10_000) // Tick(10000, COOLDOWN, 2, 1, 10)
        e.skipForward()
        runCurrent()
        assertEquals(Tick(10_000, DONE, 2, 0, 11), rec.ticks.last())
        assertEquals(1, rec.cues.count { it == Cue.Finished })
        assertEquals(Cue.Finished, rec.cues.last())
    }

    @Test
    fun `skipForward from the last WORK when cooldown is 0 gives DONE and Finished`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(prepareSec = 0, sets = 1, workSec = 3, restSec = 0, cooldownSec = 0), listOf(5), rec)
        launch { e.run() }
        advance(1000) // Tick(1000, WORK, 1, 2, 1)
        e.skipForward()
        runCurrent()
        assertEquals(Tick(1000, DONE, 1, 0, 3), rec.ticks.last())
        assertEquals(1, rec.cues.count { it == Cue.Finished })
        assertEquals(Cue.Finished, rec.cues.last())
    }

    @Test
    fun `skipBack after 5s into a phase restarts it with full seconds left`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        launch { e.run() }
        advance(3000) // Tick(3000, WORK, 1, 2, 3); 3s into the 5s WORK phase, past the 2s grace
        val phaseStartCues = rec.cues.count { it == Cue.PhaseStart(WORK) }
        e.skipBack()
        runCurrent()
        assertEquals(Tick(3000, WORK, 1, 5, 0), rec.ticks.last())
        assertEquals(phaseStartCues + 1, rec.cues.count { it == Cue.PhaseStart(WORK) })
    }

    @Test
    fun `skipBack within 2s goes to the previous phase`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advance(3000) // Tick(3000, WORK, 1, 2, 3); 1s into WORK set 1, under the 2s grace
        val cuesBefore = rec.cues.toList()
        e.skipBack()
        runCurrent()
        // Back to the start of PREPARE; elapsedSec resets to 0 (nothing precedes it). PREPARE fires no PhaseStart.
        assertEquals(Tick(3000, PREPARE, 1, 2, 0), rec.ticks.last())
        assertEquals(cuesBefore, rec.cues)
    }

    @Test
    fun `skipBack within 2s of the first step restarts it`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advance(500) // still in the first second of PREPARE, no tick yet beyond the initial one
        e.skipBack()
        runCurrent()
        assertEquals(Tick(500, PREPARE, 1, 2, 0), rec.ticks.last())
    }

    @Test
    fun `a jump while paused emits the new step paused with full seconds left, and resume continues it`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advance(3000) // Tick(3000, WORK, 1, 2, 3)
        e.pause()
        e.skipForward()
        runCurrent()
        assertEquals(Tick(3000, REST, 2, 2, 5, paused = true), rec.ticks.last())
        val ticksWhilePaused = rec.ticks.size
        advance(5000) // paused: no active time passes, so no further ticks
        assertEquals(ticksWhilePaused, rec.ticks.size)
        e.resume()
        advance(1000)
        // Wall time keeps advancing while paused (5000 above), so the next tick lands at 9000;
        // what matters is it is exactly 1s of *active* time after the jump.
        assertEquals(Tick(9000, REST, 2, 1, 6, paused = false), rec.ticks.last())
    }

    @Test
    fun `no drift after a mid-second jump, ticks remain exactly 1s apart`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        launch { e.run() }
        advance(2500) // mid-tick, 2.5s into the 5s WORK phase
        e.skipBack() // restarts WORK from here: full 5s left, rebased at 2500
        runCurrent()
        advanceUntilIdle()
        val afterJump = rec.ticks.dropWhile { it.atMs < 2500 }
        assertEquals(
            listOf(
                Tick(2500, WORK, 1, 5, 0),
                Tick(3500, WORK, 1, 4, 1),
                Tick(4500, WORK, 1, 3, 2),
                Tick(5500, WORK, 1, 2, 3),
                Tick(6500, WORK, 1, 1, 4),
                Tick(7500, DONE, 1, 0, 5),
            ),
            afterJump,
        )
    }
}
