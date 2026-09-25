package com.mitenko.hiitcounter.domain.model

sealed interface Cue {
    data class Countdown(val secondsLeft: Int) : Cue
    data class PhaseStart(val phase: Phase) : Cue
    data object Finished : Cue
}
