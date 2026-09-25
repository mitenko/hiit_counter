package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerTextTest {
    private val work = TimerState(Phase.WORK, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20,
        elapsedSec = 50, totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false)

    @Test
    fun `formats times`() {
        assertEquals("00:15", TimerText.formatMmSs(15))
        assertEquals("01:15", TimerText.formatMmSs(75))
        assertEquals("00:00:50", TimerText.formatHms(50))
        assertEquals("01:02:05", TimerText.formatHms(3725))
        assertEquals("04:00", TimerText.formatDuration(240))
        assertEquals("2:00:00", TimerText.formatDuration(7200))
    }

    @Test
    fun `notification text`() {
        assertEquals("Work · Set 2/8", TimerText.notificationTitle(work))
        assertEquals("00:15 left", TimerText.notificationBody(work))
        assertEquals("Paused · 00:15 left", TimerText.notificationBody(work.copy(paused = true)))
        assertEquals("Workout complete", TimerText.notificationBody(work.copy(phase = Phase.DONE)))
        assertEquals("Get ready", TimerText.phaseName(Phase.PREPARE))
    }
}
