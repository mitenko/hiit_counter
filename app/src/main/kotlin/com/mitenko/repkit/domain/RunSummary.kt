package com.mitenko.repkit.domain

import java.time.Instant

/**
 * One timer run, from start() to DONE or stop() (spec revision 17 §2). The wall times come from the
 * injected clock; [activeSec] is the engine's running time, so pauses are left out. [setsCompleted]
 * counts distinct work sets that ended (by time or by skip forward); [repsDone] sums their reps and
 * is null for a Timer only run.
 */
data class RunSummary(
    val entryId: Long,
    val startedAt: Instant,
    val endedAt: Instant,
    val activeSec: Int,
    val plannedSec: Int,
    val setsPlanned: Int,
    val setsCompleted: Int,
    val repsDone: Int?,
    val completed: Boolean,
)

/**
 * Receives each [RunSummary] as the run ends, synchronously from [TimerController] (spec revision 17
 * §3). A direct call rather than a flow, so no summary can be missed for want of a collector.
 */
fun interface RunLog {
    fun record(summary: RunSummary)
}
