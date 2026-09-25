package com.mitenko.hiitcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerStateTest {
    @Test
    fun `derives completed sets and remaining time`() {
        val work = TimerState(Phase.WORK, set = 3, sets = 8, phaseSecondsLeft = 10, phaseDurationSec = 20,
            elapsedSec = 100, totalDurationSec = 240, repsThisSet = 8, totalReps = 64, paused = false)
        assertEquals(2, work.completedWorkSets)
        assertEquals(140, work.remainingSec)
        assertEquals(8, work.copy(phase = Phase.COOLDOWN).completedWorkSets)
        assertEquals(8, work.copy(phase = Phase.DONE).completedWorkSets)
        assertEquals(0, work.copy(elapsedSec = 300).remainingSec)
    }
}
