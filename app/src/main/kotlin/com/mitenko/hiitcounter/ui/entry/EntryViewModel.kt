package com.mitenko.hiitcounter.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.RepDistributor
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.ServiceStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.service.WorkoutServiceStarter
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
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
import javax.inject.Inject

data class EntryUiState(
    val name: String = "",
    /** Spec R4 §4.1–4.2: a Workout shows the rep table and both buttons; a check-in-only entry shows the streak rows and Check in. */
    val type: EntryType = EntryType.WORKOUT,
    val reps: List<Int> = emptyList(),
    val total: Int = 0,
    val lastCheckIn: String = "—",
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val today: String = "",
    val checkedInToday: Boolean = false,
    val starting: Boolean = false,
    /** A Check in call is in flight (spec R4 §4.1): both buttons are disabled until it returns. */
    val checkingIn: Boolean = false,
    val error: String? = null,
    /** True once the entry has emitted at least once. The action buttons and Workout-only rows wait for this. */
    val loaded: Boolean = false,
)

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

    val uiState: StateFlow<EntryUiState> =
        combine(repo.entry(entryId).filterNotNull(), transient, refresh) { entry, tr, _ ->
            val now = clock.now()
            val zone = clock.zone()
            val counter = entry.counter
            EntryUiState(
                name = entry.name,
                type = entry.type,
                reps = RepDistributor.distribute(counter.total, entry.timing.sets),
                total = counter.total,
                lastCheckIn = counter.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: "—",
                bestStreak = counter.bestStreak,
                currentStreak = counter.currentStreak,
                today = DateFormats.date(now, zone),
                checkedInToday = counter.lastCheckIn?.atZone(zone)?.toLocalDate() == now.atZone(zone).toLocalDate(),
                starting = tr.starting,
                checkingIn = tr.checkingIn,
                error = tr.error,
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState())

    /** Re-evaluates "Today" and "Checked in today" when the screen resumes (e.g. after midnight). */
    fun onResume() {
        refresh.update { it + 1 }
    }

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }

    /**
     * Spec R4 §4.1: Check in on its own. Ignored while a check-in or a start is in flight. The
     * repository call runs NonCancellable, so leaving the screen can't drop a check-in that has
     * started. A missing entry pops to the list; any other failure shows a message.
     */
    fun onCheckIn() {
        val t = transient.value
        if (t.checkingIn || t.starting) return
        transient.update { it.copy(checkingIn = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { repo.checkIn(entryId, clock) }
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
        // A check-in-only entry has no Start (spec R4 §4.1); guard it here too in case it's ever called anyway.
        if (entry.type != EntryType.WORKOUT) return
        // Frozen at Start (spec §7.1): the run never reads the entry again.
        if (!controller.prepare(WorkoutSnapshot(entry.id, entry.name, entry.timing, entry.cues))) {
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
        val result = repo.checkIn(entryId, clock)
        controller.start(RepDistributor.distribute(result.state.total, entry.timing.sets))
    }

    private fun fail(message: String) {
        controller.cancelPrepare()
        transient.update { it.copy(error = message) }
    }

    companion object {
        const val SERVICE_START_TIMEOUT_MS = 5_000L
    }
}
