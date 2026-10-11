package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Cue
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.WeightUnit

/**
 * What the Voice cue says at a WORK start (spec R4 §5, rev 26 §4, plan Spec note 57). Pure; the platform
 * speaker turns it into words in the voice's language.
 */
sealed interface VoiceLine {
    /** The number every line says: the reps, or for Timer only the sets to go. */
    val number: Int

    /** Reps mode and Timer only, as before: the bare number ("12"). */
    data class Count(override val number: Int) : VoiceLine

    /** A weight run's later sets: "10 reps". */
    data class Reps(override val number: Int) : VoiceLine

    /** A weight run's first set: "16 kilos, 10 reps". [weight] is in hundredths of [unit]. */
    data class Load(val weight: Int, val unit: WeightUnit, override val number: Int) : VoiceLine

    companion object {
        /** The line for [cue], or null when nothing is said: not a WORK start, or no reps on it. */
        fun of(cue: Cue): VoiceLine? {
            if (cue !is Cue.PhaseStart || cue.phase != Phase.WORK) return null
            val reps = cue.reps ?: return null
            val weight = cue.weight ?: return Count(reps)
            return if (cue.newWeight) Load(weight.weight, weight.unit, reps) else Reps(reps)
        }
    }
}
