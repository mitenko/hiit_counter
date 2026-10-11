package com.mitenko.repkit.domain.model

sealed interface Cue {
    data class Countdown(val secondsLeft: Int) : Cue

    /**
     * [reps] is the starting set's reps for a WORK start, filled in by TimerController from the
     * list given to start() (spec R4 §5). It is null for every other phase. In a weight run, [weight]
     * is the run's weight on every WORK start and [newWeight] is true on set 1's, so the voice says the
     * weight once (spec rev 26 §4, plan Spec note 57).
     */
    data class PhaseStart(val phase: Phase, val reps: Int? = null, val weight: SetWeight? = null, val newWeight: Boolean = false) : Cue
    data object Finished : Cue
}
