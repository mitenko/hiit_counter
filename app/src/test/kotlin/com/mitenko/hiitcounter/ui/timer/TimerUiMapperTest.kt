package com.mitenko.hiitcounter.ui.timer

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerUiMapperTest {
    private fun state(phase: Phase, set: Int, left: Int, duration: Int, reps: Int = 8) = TimerState(
        phase = phase, set = set, sets = 8, phaseSecondsLeft = left, phaseDurationSec = duration,
        elapsedSec = 50, totalDurationSec = 240, repsThisSet = reps, totalReps = 65, paused = false,
    )

    @Test
    fun `work shows bright reps without a label`() {
        val ui = TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20))
        assertNull(ui.label)
        assertEquals(8, ui.centerNumber)
        assertFalse(ui.centerDimmed)
        assertEquals(PhaseTone.WORK, ui.tone)
        assertEquals("00:15", ui.countdownText)
        assertEquals("2/8", ui.setsText)
        assertEquals("00:00:50", ui.elapsedText)
        assertEquals(0.75f, ui.innerProgress, 1e-6f)
        assertEquals((1 + 5f / 20f) / 8f, ui.outerProgress, 1e-6f)
        assertEquals("Work, set 2 of 8, 8 reps", ui.description)
    }

    @Test
    fun `rest shows label and dimmed upcoming reps`() {
        val ui = TimerUiMapper.map(state(Phase.REST, set = 3, left = 10, duration = 10, reps = 9))
        assertEquals("REST", ui.label)
        assertEquals(9, ui.centerNumber)
        assertTrue(ui.centerDimmed)
        assertEquals(PhaseTone.REST, ui.tone)
        assertEquals(2f / 8f, ui.outerProgress, 1e-6f)
    }

    @Test
    fun `prepare cooldown and done`() {
        val prep = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10, reps = 9))
        assertEquals("GET READY", prep.label)
        assertEquals(9, prep.centerNumber)
        assertTrue(prep.centerDimmed)
        assertEquals(0f, prep.outerProgress, 1e-6f)

        val cool = TimerUiMapper.map(state(Phase.COOLDOWN, set = 8, left = 5, duration = 30, reps = 0))
        assertEquals("COOLDOWN", cool.label)
        assertNull(cool.centerNumber)
        assertEquals(1f, cool.outerProgress, 1e-6f)

        val done = TimerUiMapper.map(state(Phase.DONE, set = 8, left = 0, duration = 0, reps = 0))
        assertEquals("DONE", done.label)
        assertEquals(65, done.centerNumber)
        assertTrue(done.done)
        assertEquals("", done.countdownText)
    }
}
