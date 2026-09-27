package com.mitenko.hiitcounter.domain.model

/**
 * One HIIT entry (spec §5.1). Invariant: [counter].total is always a real value — a stored
 * NULL total is resolved to [progression].startingTotal when the row is mapped.
 */
data class Entry(
    val id: Long,
    val name: String,
    val position: Int,
    val timing: TimingConfig,
    val progression: ProgressionConfig,
    val cues: CueConfig,
    val counter: CounterState,
)

class EntryNotFound(val id: Long) : Exception("Entry $id not found")

class EntryBusy(val id: Long) : Exception("Entry $id has an active workout")
