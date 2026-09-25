package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.Phase.COOLDOWN
import com.mitenko.hiitcounter.domain.model.Phase.DONE
import com.mitenko.hiitcounter.domain.model.Phase.PREPARE
import com.mitenko.hiitcounter.domain.model.Phase.REST
import com.mitenko.hiitcounter.domain.model.Phase.WORK
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
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
}
