package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig

/** A domain entry with defaults and an untouched counter; the counter total is already resolved. */
fun testEntry(
    id: Long,
    name: String = "Entry $id",
    position: Int = (id - 1).toInt(),
    timing: TimingConfig = TimingConfig(),
    progression: ProgressionConfig = ProgressionConfig(),
    cues: CueConfig = CueConfig(),
    counter: CounterState = CounterState(total = progression.startingTotal),
) = Entry(id, name, position, timing, progression, cues, counter)
