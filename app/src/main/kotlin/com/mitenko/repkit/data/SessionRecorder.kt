package com.mitenko.repkit.data

import android.util.Log
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.data.db.WorkoutSessionDao
import com.mitenko.repkit.data.db.WorkoutSessionEntity
import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.RunLog
import com.mitenko.repkit.domain.RunSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionRecorder"

/**
 * Stores each ended run as one workout_session row (spec revision 17 §3). It is TimerController's
 * [RunLog], passed in when AppModule builds the controller, so it exists before any run can start and
 * receives every summary by a direct call: there is no subscription that could start late or be
 * missing. The insert runs on the application scope, so leaving the timer screen or the service
 * stopping never cancels it. A failed save is logged, reported as a non-fatal (spec rev 30 §4) and
 * dropped: it must never crash the app.
 */
@Singleton
class SessionRecorder internal constructor(
    private val dao: WorkoutSessionDao,
    private val scope: CoroutineScope,
    private val reporter: CrashReporter,
) : RunLog {
    @Inject constructor(db: HiitDatabase, @ApplicationScope scope: CoroutineScope, reporter: CrashReporter) :
        this(db.workoutSessionDao(), scope, reporter)

    override fun record(summary: RunSummary) {
        scope.launch {
            try {
                dao.insert(summary.toEntity())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // E.g. the entry was deleted while the row was being written (its foreign key fails).
                Log.e(TAG, "Could not record the run for entry ${summary.entryId}", e)
                reporter.recordNonFatal(e, "SessionRecorder: could not record a run")
            }
        }
    }

    private fun RunSummary.toEntity() = WorkoutSessionEntity(
        entryId = entryId,
        startedAt = startedAt.toEpochMilli(),
        endedAt = endedAt.toEpochMilli(),
        activeSec = activeSec,
        plannedSec = plannedSec,
        setsPlanned = setsPlanned,
        setsCompleted = setsCompleted,
        repsDone = repsDone,
        completed = completed,
    )
}
