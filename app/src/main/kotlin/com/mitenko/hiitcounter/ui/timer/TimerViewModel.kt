package com.mitenko.hiitcounter.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
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
    private val settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<TimerUiState?> = controller.state
        .map { it?.let(TimerUiMapper::map) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value?.let(TimerUiMapper::map))

    val status: StateFlow<RunStatus> = controller.status

    val notificationPermissionAsked: Flow<Boolean> = settings.notificationPermissionAsked

    fun togglePause() {
        val state = controller.state.value ?: return
        if (state.paused) controller.resume() else controller.pause()
    }

    fun stop() = controller.stop()

    fun leaveDone() = controller.dismissDone()

    fun onNotificationPermissionAsked() {
        viewModelScope.launch { settings.markNotificationPermissionAsked() }
    }
}
