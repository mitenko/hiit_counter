package com.mitenko.repkit.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.domain.CheckInResult
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.Outcome
import com.mitenko.repkit.domain.RepDistributor
import com.mitenko.repkit.domain.RunStatus
import com.mitenko.repkit.domain.ServiceStatus
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.domain.WorkoutSnapshot
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.service.WorkoutServiceStarter
import com.mitenko.repkit.ui.common.EntryScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

data class EntryUiState(
    val name: String = "",
    /** Spec rev 9 §3: a Workout shows the chart and the reps column, a Timer only entry the calendar. Both show the streak line, Check in and Start. */
    val type: EntryType = EntryType.WORKOUT,
    val reps: List<Int> = emptyList(),
    val total: Int = 0,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val checkedInToday: Boolean = false,
    val starting: Boolean = false,
    /** A Check in call is in flight (spec R4 §4.1): both buttons are disabled until it returns. */
    val checkingIn: Boolean = false,
    val error: String? = null,
    /** True once the entry has emitted at least once. The action buttons and Workout-only rows wait for this. */
    val loaded: Boolean = false,
    /**
     * Every point the entry has, oldest first (spec R6 §3.3): the screen filters by range, so one
     * query gives both empty states (plan Spec note 3). [now] and [zone] are "today" for the range
     * maths, re-read on resume.
     */
    val points: List<CheckInPoint> = emptyList(),
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneOffset.UTC,
)

/**
 * A one-shot check-in highlight (spec revision 12 §3): [id] increases on every event, so the UI's
 * `LaunchedEffect` keys replay even when the changed indices repeat.
 */
data class Highlight(val id: Int, val changes: Map<Int, RepsColumnLayout.Change>, val hold: HoldStatus? = null)

/** Spec revision 20: the hold a check-in stayed on, for the announcement ("Holding at 64, 2 of 4"). */
data class HoldStatus(val at: Int, val day: Int, val of: Int)

@HiltViewModel
class EntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val controller: TimerController,
    private val starter: WorkoutServiceStarter,
    private val clock: Clock,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private data class Transient(val starting: Boolean = false, val checkingIn: Boolean = false, val error: String? = null)

    private val transient = MutableStateFlow(Transient())
    private val refresh = MutableStateFlow(0)

    private val _highlight = MutableStateFlow<Highlight?>(null)

    /** Set after a check-in that changed reps (spec revision 12 §3); consumed once via [highlightShown]. */
    val highlight: StateFlow<Highlight?> = _highlight
    private var highlightSeq = 0

    val uiState: StateFlow<EntryUiState> =
        combine(repo.entry(entryId).filterNotNull(), repo.history(entryId, since = null), transient, refresh) { entry, points, tr, _ ->
            val now = clock.now()
            val zone = clock.zone()
            val counter = entry.counter
            EntryUiState(
                name = entry.name,
                type = entry.type,
                reps = RepDistributor.distribute(counter.total, entry.timing.sets),
                total = counter.total,
                bestStreak = counter.bestStreak,
                currentStreak = counter.currentStreak,
                checkedInToday = counter.lastCheckIn?.atZone(zone)?.toLocalDate() == now.atZone(zone).toLocalDate(),
                starting = tr.starting,
                checkingIn = tr.checkingIn,
                error = tr.error,
                loaded = true,
                points = points,
                now = now,
                zone = zone,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState())

    /** Re-evaluates "Checked in today" and "now" (the range maths) when the screen resumes, e.g. after midnight. */
    fun onResume() {
        refresh.update { it + 1 }
    }

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }

    /** Consumes the pending highlight once the UI has started playing it. */
    fun highlightShown() {
        _highlight.value = null
    }

    /**
     * Spec R4 §4.1: Check in on its own. Ignored while a check-in or a start is in flight. The
     * repository call runs NonCancellable, so leaving the screen can't drop a check-in that has
     * started. A missing entry pops to the list; any other failure shows a message.
     */
    fun onCheckIn() {
        val t = transient.value
        if (t.checkingIn || t.starting) return
        val type = uiState.value.type
        val before = uiState.value.reps
        transient.update { it.copy(checkingIn = true, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(NonCancellable) { repo.checkIn(entryId, clock) }
                applyHighlight(type, before, result, repo.entry(entryId).first()?.progression)
            } catch (e: CancellationException) {
                throw e
            } catch (e: EntryNotFound) {
                markMissing()
            } catch (e: Exception) {
                transient.update { it.copy(error = "Couldn't check in: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                transient.update { it.copy(checkingIn = false) }
            }
        }
    }

    /**
     * v1 spec §4: the check-in is committed only after the foreground service has started. After a
     * manual Check in, that call returns AlreadyToday and writes nothing (R4 §4.1).
     */
    fun onStart() {
        val t = transient.value
        if (t.starting || t.checkingIn) return
        if (controller.status.value == RunStatus.RUNNING) return
        transient.value = Transient(starting = true)
        viewModelScope.launch {
            try {
                startWorkout()
            } catch (e: CancellationException) {
                controller.cancelPrepare()
                throw e
            } catch (e: Exception) {
                fail("Couldn't start the workout: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                transient.update { it.copy(starting = false) }
            }
        }
    }

    private suspend fun startWorkout() {
        val entry = repo.entry(entryId).first() ?: throw EntryNotFound(entryId)
        // Frozen at Start (spec §7.1): the run never reads the entry again. A Timer only entry
        // (spec revision 8) runs the same flow with countsReps = false: no reps are counted or shown.
        if (!controller.prepare(WorkoutSnapshot(entry.id, entry.name, entry.timing, entry.cues, countsReps = entry.type == EntryType.WORKOUT))) {
            transient.update { it.copy(error = "A workout is already starting") }
            return
        }

        starter.start().onFailure { e ->
            fail("Couldn't start the workout: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        val status = withTimeoutOrNull(SERVICE_START_TIMEOUT_MS) {
            controller.serviceStatus.first { it != ServiceStatus.Pending }
        }
        if (status != ServiceStatus.Started) {
            val reason = (status as? ServiceStatus.Failed)?.reason ?: "the timer service didn't respond"
            fail("Couldn't start the workout: $reason")
            return
        }
        // Uses the row's own progression; throwing (incl. EntryNotFound) takes the fail() path above.
        val before = RepDistributor.distribute(entry.counter.total, entry.timing.sets)
        val result = repo.checkIn(entryId, clock)
        // Starting navigates to the timer right away, so this may only ever play once the user
        // comes back here: collectAsStateWithLifecycle pauses collection while the screen isn't
        // started, so the event just waits on the StateFlow. If this screen (and this ViewModel)
        // is gone by the time this runs, there's nobody left to observe it, and it's discarded
        // along with the ViewModel — no extra "has the user left" check is needed.
        applyHighlight(entry.type, before, result, entry.progression)
        controller.start(RepDistributor.distribute(result.state.total, entry.timing.sets))
    }

    /**
     * Spec revision 12 §3: nothing highlights for a Timer only entry or an AlreadyToday no-op. If
     * the set count changed while this check-in was in flight (e.g. a Settings edit raced it),
     * `before` no longer lines up with the live column, so this bails rather than diff against a
     * stale size and highlight the wrong cells.
     */
    private fun applyHighlight(type: EntryType, before: List<Int>, result: CheckInResult, progression: ProgressionConfig?) {
        if (type != EntryType.WORKOUT || result.outcome == Outcome.AlreadyToday) return
        if (before.size != uiState.value.reps.size) return
        val after = RepDistributor.distribute(result.state.total, before.size)
        val changes = RepsColumnLayout.changedSets(before, after)
        if (changes.isNotEmpty()) {
            _highlight.value = Highlight(++highlightSeq, changes)
            return
        }
        // Spec revision 20: nothing moved because the check-in stayed on a hold. A positive hold
        // count means the total sits on an active hold; flash every cell neutral and say the day.
        val hold = progression?.activeHold(result.state.total)
        if (hold != null && result.state.holdCount > 0) {
            val all = before.indices.associateWith { RepsColumnLayout.Change.HOLD }
            _highlight.value = Highlight(++highlightSeq, all, HoldStatus(hold.at, result.state.holdCount, hold.forCount))
        }
    }

    private fun fail(message: String) {
        controller.cancelPrepare()
        transient.update { it.copy(error = message) }
    }

    companion object {
        const val SERVICE_START_TIMEOUT_MS = 5_000L
    }
}
