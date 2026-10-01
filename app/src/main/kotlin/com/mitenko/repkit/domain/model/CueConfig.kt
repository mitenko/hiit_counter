package com.mitenko.repkit.domain.model

data class CueConfig(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    /** The Voice cue (spec R4 §5): says each set's reps as work starts. Off by default. */
    val voice: Boolean = false,
)
