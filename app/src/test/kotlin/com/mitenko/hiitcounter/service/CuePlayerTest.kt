package com.mitenko.hiitcounter.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.CuePatterns
import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.platform.CueSpeaker
import com.mitenko.hiitcounter.testutil.FakeCueSpeaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CuePlayerTest {
    private class FakeFocus : CueFocus {
        var requests = 0
        var abandons = 0

        override fun request(): Boolean {
            requests++
            return true
        }

        override fun abandon() {
            abandons++
        }
    }

    private val focus = FakeFocus()

    /** Beep and voice, no vibration (the vibrator isn't under test). */
    private val voiceOn = CueConfig(vibration = false, voice = true)

    /** Voice only: no beep, so speech starts at once. */
    private val voiceOnly = CueConfig(sound = false, vibration = false, voice = true)

    private fun TestScope.player(speaker: CueSpeaker?) =
        CuePlayer(ApplicationProvider.getApplicationContext(), backgroundScope, focus).also { it.speaker = speaker }

    @Test
    fun `a work start with the voice on says its reps after the beep`() = runTest {
        val speaker = FakeCueSpeaker()
        player(speaker).play(Cue.PhaseStart(Phase.WORK, reps = 12), voiceOn)
        runCurrent()
        assertTrue(speaker.spoken.isEmpty()) // the 600 ms WORK tone plays first
        advanceTimeBy(CuePatterns.LONG_MS)
        runCurrent()
        assertEquals(listOf(12), speaker.spoken)
    }

    @Test
    fun `the voice speaks only at a work start with the voice switched on`() = runTest {
        val speaker = FakeCueSpeaker()
        val p = player(speaker)
        p.play(Cue.PhaseStart(Phase.REST), voiceOnly)
        p.play(Cue.PhaseStart(Phase.COOLDOWN), voiceOnly)
        p.play(Cue.Countdown(3), voiceOnly)
        p.play(Cue.Finished, voiceOnly)
        p.play(Cue.PhaseStart(Phase.WORK, reps = 9), voiceOnly.copy(voice = false))
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(speaker.spoken.isEmpty())
        p.play(Cue.PhaseStart(Phase.WORK, reps = 9), voiceOnly)
        runCurrent()
        assertEquals(listOf(9), speaker.spoken)
    }

    @Test
    fun `an unavailable or missing speaker never speaks and takes no focus`() = runTest {
        val unavailable = FakeCueSpeaker(available = false)
        player(unavailable).play(Cue.PhaseStart(Phase.WORK, reps = 9), voiceOnly)
        player(null).play(Cue.PhaseStart(Phase.WORK, reps = 9), voiceOnly)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(unavailable.spoken.isEmpty())
        assertEquals(0, focus.requests)
    }

    @Test
    fun `focus is held through the utterance and released when it ends`() = runTest {
        val speaker = FakeCueSpeaker()
        player(speaker).play(Cue.PhaseStart(Phase.WORK, reps = 12), voiceOn)
        advanceTimeBy(CuePatterns.LONG_MS + 500) // the tone and its 100 ms hold are over; the voice is still talking
        runCurrent()
        assertEquals(1, focus.requests)
        assertEquals(0, focus.abandons)
        speaker.finishUtterance()
        runCurrent()
        assertEquals(1, focus.abandons)
    }

    @Test
    fun `focus is released after 3 s if the utterance never ends`() = runTest {
        player(FakeCueSpeaker()).play(Cue.PhaseStart(Phase.WORK, reps = 12), voiceOnly)
        advanceTimeBy(CuePlayer.SPEECH_TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(0, focus.abandons)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, focus.abandons)
    }
}
