package com.mitenko.repkit.domain.model

/**
 * One HIIT entry (spec §5.1). Invariant: [counter].total is always a real value — a stored NULL total
 * is resolved to the progression's start level when the row is mapped: startingTotal in Reps mode,
 * the starting weight × reps in a weight mode (spec rev 26 §2).
 */
data class Entry(
    val id: Long,
    val name: String,
    val position: Int,
    val timing: TimingConfig,
    val progression: ProgressionConfig,
    val cues: CueConfig,
    val counter: CounterState,
    /** Spec R4 §3.1: new entries are Workouts. */
    val type: EntryType = EntryType.WORKOUT,
)

class EntryNotFound(val id: Long) : Exception("Entry $id not found")

class EntryBusy(val id: Long) : Exception("Entry $id has an active workout")
