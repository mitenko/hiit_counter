package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuePatternsTest {
    @Test
    fun `countdown is a single short beep without vibration`() {
        val p = CuePatterns.forCue(Cue.Countdown(3))
        assertEquals(listOf(ToneAt(0, Tone.SHORT)), p.tones)
        assertNull(p.vibration)
    }

    @Test
    fun `work start is a long tone with a pulse`() {
        val p = CuePatterns.forCue(Cue.PhaseStart(Phase.WORK))
        assertEquals(listOf(ToneAt(0, Tone.LONG)), p.tones)
        assertEquals(listOf(0L, 400L), p.vibration)
        assertEquals(CuePatterns.LONG_MS, p.durationMs)
    }

    @Test
    fun `rest and cooldown start are double beeps`() {
        for (phase in listOf(Phase.REST, Phase.COOLDOWN)) {
            val p = CuePatterns.forCue(Cue.PhaseStart(phase))
            assertEquals(listOf(ToneAt(0, Tone.SHORT), ToneAt(250, Tone.SHORT)), p.tones)
            assertEquals(listOf(0L, 150L, 100L, 150L), p.vibration)
        }
    }

    @Test
    fun `finished is a triple beep`() {
        val p = CuePatterns.forCue(Cue.Finished)
        assertEquals(listOf(ToneAt(0, Tone.SHORT), ToneAt(250, Tone.SHORT), ToneAt(500, Tone.SHORT)), p.tones)
        assertEquals(listOf(0L, 150L, 100L, 150L, 100L, 150L), p.vibration)
        assertEquals(500 + CuePatterns.SHORT_MS, p.durationMs)
    }
}
