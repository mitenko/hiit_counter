package com.mitenko.repkit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigsTest {
    @Test
    fun `default timing totals four minutes with no rest after the last set`() {
        assertEquals(240, TimingConfig().totalDurationSec)
        assertEquals(10 + 20 + 5, TimingConfig(sets = 1, cooldownSec = 5).totalDurationSec)
    }

    @Test
    fun `the default is one hold at 64 for 4`() {
        assertEquals(listOf(Hold(64, 4)), ProgressionConfig().holds)
        assertTrue(ProgressionConfig().hold)
    }

    @Test
    fun `the holds keep the user's order`() {
        val unsorted = listOf(Hold(64, 4), Hold(52, 1), Hold(56, 3))
        val c = ProgressionConfig(holds = unsorted)
        assertEquals(unsorted, c.holds)
        assertEquals(unsorted, c.copy(cap = 80).holds)
        assertEquals(unsorted, c.activeHolds)
    }

    @Test
    fun `a hold is active only for a positive count and at within floor to cap exclusive`() {
        assertEquals(Hold(64, 4), ProgressionConfig().activeHold(64))
        assertNull(ProgressionConfig().activeHold(63))
        assertNull(ProgressionConfig(holds = listOf(Hold(64, 0))).activeHold(64))
        assertNull(ProgressionConfig(holds = listOf(Hold(72, 4))).activeHold(72))
        assertNull(ProgressionConfig(holds = listOf(Hold(40, 4))).activeHold(40))
        assertEquals(Hold(48, 4), ProgressionConfig(holds = listOf(Hold(48, 4))).activeHold(48))
        assertNull(ProgressionConfig(holds = emptyList()).activeHold(64))
    }

    @Test
    fun `activeHold finds any hold in the list and takes the first duplicate`() {
        val c = ProgressionConfig(holds = listOf(Hold(56, 3), Hold(64, 4)))
        assertEquals(Hold(56, 3), c.activeHold(56))
        assertEquals(Hold(64, 4), c.activeHold(64))
        assertEquals(Hold(64, 1), ProgressionConfig(holds = listOf(Hold(64, 1), Hold(64, 4))).activeHold(64))
        assertEquals(listOf(Hold(56, 3)), c.copy(cap = 60).activeHolds)
    }

    @Test
    fun `the hold switch turns every hold off whatever the values`() {
        val off = ProgressionConfig(holds = listOf(Hold(56, 3), Hold(64, 4)), hold = false)
        assertNull(off.activeHold(56))
        assertNull(off.activeHold(64))
        assertTrue(off.activeHolds.isEmpty())
        assertEquals(listOf(Hold(56, 3), Hold(64, 4)), off.holds)
    }

    @Test
    fun `the voice cue is off by default`() {
        assertFalse(CueConfig().voice)
        assertTrue(CueConfig(voice = true).voice)
        assertEquals(CueConfig(sound = true, vibration = true, voice = false), CueConfig())
    }
}
