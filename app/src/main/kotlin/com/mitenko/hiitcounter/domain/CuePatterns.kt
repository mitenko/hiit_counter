package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase

enum class Tone { SHORT, LONG }

data class ToneAt(val atMs: Long, val tone: Tone)

/** [vibration] is VibrationEffect.createWaveform timings: [delay, on, off, on, …]. */
data class CuePattern(val tones: List<ToneAt>, val vibration: List<Long>?) {
    val durationMs: Long
        get() = tones.maxOfOrNull { it.atMs + CuePatterns.lengthOf(it.tone) } ?: 0L
}

object CuePatterns {
    const val SHORT_MS = 120L
    const val LONG_MS = 600L

    fun lengthOf(tone: Tone): Long = when (tone) {
        Tone.SHORT -> SHORT_MS
        Tone.LONG -> LONG_MS
    }

    fun forCue(cue: Cue): CuePattern = when (cue) {
        is Cue.Countdown -> CuePattern(listOf(ToneAt(0, Tone.SHORT)), vibration = null)
        is Cue.PhaseStart -> when (cue.phase) {
            Phase.WORK -> CuePattern(listOf(ToneAt(0, Tone.LONG)), listOf(0L, 400L))
            Phase.REST, Phase.COOLDOWN ->
                CuePattern(listOf(ToneAt(0, Tone.SHORT), ToneAt(250, Tone.SHORT)), listOf(0L, 150L, 100L, 150L))
            Phase.PREPARE, Phase.DONE -> CuePattern(emptyList(), null)
        }
        Cue.Finished -> CuePattern(
            listOf(ToneAt(0, Tone.SHORT), ToneAt(250, Tone.SHORT), ToneAt(500, Tone.SHORT)),
            listOf(0L, 150L, 100L, 150L, 100L, 150L),
        )
    }
}
