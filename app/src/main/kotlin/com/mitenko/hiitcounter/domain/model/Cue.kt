package com.mitenko.hiitcounter.domain.model

sealed interface Cue {
    data class Countdown(val secondsLeft: Int) : Cue

    /**
     * [reps] is the starting set's reps for a WORK start, filled in by TimerController from the
     * list given to start() (spec R4 §5). It is null for every other phase.
     */
    data class PhaseStart(val phase: Phase, val reps: Int? = null) : Cue
    data object Finished : Cue
}
