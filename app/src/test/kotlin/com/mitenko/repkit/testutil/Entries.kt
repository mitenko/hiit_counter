package com.mitenko.repkit.testutil

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig

/** A domain entry with defaults and an untouched counter; the counter total is already resolved. */
fun testEntry(
    id: Long,
    name: String = "Entry $id",
    position: Int = (id - 1).toInt(),
    timing: TimingConfig = TimingConfig(),
    progression: ProgressionConfig = ProgressionConfig(),
    cues: CueConfig = CueConfig(),
    counter: CounterState = CounterState(total = progression.startingTotal),
    type: EntryType = EntryType.WORKOUT,
) = Entry(id, name, position, timing, progression, cues, counter, type)
