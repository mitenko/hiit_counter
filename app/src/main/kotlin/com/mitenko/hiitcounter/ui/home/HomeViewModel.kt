package com.mitenko.hiitcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.RepDistributor
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.ServiceStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.service.WorkoutServiceStarter
import com.mitenko.hiitcounter.ui.common.DateFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class HomeUiState(
    val reps: List<Int> = emptyList(),
    val total: Int = 0,
    val lastCheckIn: String = "—",
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val today: String = "",
    val checkedInToday: Boolean = false,
    val starting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val counter: CounterRepository,
    private val settings: SettingsRepository,
    private val controller: TimerController,
    private val starter: WorkoutServiceStarter,
    private val clock: Clock,
) : ViewModel() {
    private data class Transient(val starting: Boolean = false, val error: String? = null)

    private val transient = MutableStateFlow(Transient())
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> =
        combine(counter.state, settings.timing, transient, refresh) { state, timing, tr, _ ->
            val now = clock.now()
            val zone = clock.zone()
            HomeUiState(
                reps = RepDistributor.distribute(state.total, timing.sets),
                total = state.total,
                lastCheckIn = state.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: "—",
                bestStreak = state.bestStreak,
                currentStreak = state.currentStreak,
                today = DateFormats.date(now, zone),
                checkedInToday = state.lastCheckIn?.atZone(zone)?.toLocalDate() == now.atZone(zone).toLocalDate(),
                starting = tr.starting,
                error = tr.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Re-evaluates "Today" and "Checked in today" when the screen resumes (e.g. after midnight). */
    fun onResume() {
        refresh.update { it + 1 }
    }

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }

    /** Spec §4: the check-in is committed only after the foreground service has started. */
    fun onStart() {
        if (transient.value.starting) return
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
        val timing = settings.timing.first()
        val cues = settings.cues.first()
        val progression = settings.progression.first()
        if (!controller.prepare(WorkoutSnapshot(timing, cues))) {
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
        val result = counter.checkIn(progression, clock)
        controller.start(RepDistributor.distribute(result.state.total, timing.sets))
    }

    private fun fail(message: String) {
        controller.cancelPrepare()
        transient.update { it.copy(error = message) }
    }

    companion object {
        const val SERVICE_START_TIMEOUT_MS = 5_000L
    }
}
