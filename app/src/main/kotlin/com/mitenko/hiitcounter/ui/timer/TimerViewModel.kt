package com.mitenko.hiitcounter.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val controller: TimerController,
    private val preferences: AppPreferences,
) : ViewModel() {
    val uiState: StateFlow<TimerUiState?> = controller.state
        .map { it?.let(::toUi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value?.let(::toUi))

    val status: StateFlow<RunStatus> = controller.status

    /** App-level and sticky (spec §5.4). */
    val notificationPermissionAsked: Flow<Boolean> = preferences.notificationPermissionAsked

    fun togglePause() {
        val state = controller.state.value ?: return
        if (state.paused) controller.resume() else controller.pause()
    }

    fun stop() = controller.stop()

    fun leaveDone() = controller.dismissDone()

    fun onNotificationPermissionAsked() {
        viewModelScope.launch { preferences.markNotificationPermissionAsked() }
    }

    /** The name comes from the frozen snapshot, so a rename during the run never shows here (spec §7.1). */
    private fun toUi(state: TimerState): TimerUiState = TimerUiMapper.map(state, controller.snapshot?.entryName.orEmpty())
}
