package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeLockPolicyTest {
    private val running = TimerState(Phase.WORK, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20,
        elapsedSec = 50, totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false)

    @Test
    fun `ticking holds the lock for the remaining active time plus a margin`() {
        assertEquals(190_000L + WakeLockPolicy.MARGIN_MS, WakeLockPolicy.timeoutMs(running))
    }

    @Test
    fun `paused, done and idle release the lock`() {
        assertNull(WakeLockPolicy.timeoutMs(running.copy(paused = true)))
        assertNull(WakeLockPolicy.timeoutMs(running.copy(phase = Phase.DONE)))
        assertNull(WakeLockPolicy.timeoutMs(null))
    }

    @Test
    fun `timeout always outlasts the remaining active time`() {
        for (elapsed in 0..240) {
            val timeout = WakeLockPolicy.timeoutMs(running.copy(elapsedSec = elapsed))!!
            assertTrue(timeout > (240 - elapsed) * 1000L)
        }
    }
}
