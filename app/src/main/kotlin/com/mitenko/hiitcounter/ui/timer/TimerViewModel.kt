package com.mitenko.hiitcounter.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val controller: TimerController,
    private val preferences: AppPreferences,
    private val repo: EntryRepository,
) : ViewModel() {
    val uiState: StateFlow<TimerUiState?> = controller.state
        .map { it?.let(::toUi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value?.let(::toUi))

    val status: StateFlow<RunStatus> = controller.status

    /** The run's cues, live (spec R4 §5, rev 7): null when there is no run. */
    val cues: StateFlow<CueConfig?> = controller.liveCues

    /** App-level and sticky (spec §5.4). */
    val notificationPermissionAsked: Flow<Boolean> = preferences.notificationPermissionAsked

    /** Serialises the cue writes below, as the Cues settings page does, so rapid taps are never lost. */
    private val mutex = Mutex()

    fun togglePause() {
        val state = controller.state.value ?: return
        if (state.paused) controller.resume() else controller.pause()
    }

    fun onSkipForward() = controller.skipForward()

    fun onSkipBack() = controller.skipBack()

    fun stop() = controller.stop()

    fun leaveDone() = controller.dismissDone()

    fun toggleSound() = toggleCue { it.copy(sound = !it.sound) }

    fun toggleVibration() = toggleCue { it.copy(vibration = !it.vibration) }

    fun toggleVoice() = toggleCue { it.copy(voice = !it.voice) }

    fun onNotificationPermissionAsked() {
        viewModelScope.launch { preferences.markNotificationPermissionAsked() }
    }

    /**
     * Applies [transform] to the run's live cues at once (spec R4 §5, rev 7), then persists to
     * the entry so the next run keeps it. A missing entry (deleted mid-run) is ignored; the write
     * always finishes, even if it is queued behind another toggle when this ViewModel's scope is
     * cancelled.
     */
    private fun toggleCue(transform: (CueConfig) -> CueConfig) {
        val snap = controller.snapshot ?: return
        val current = controller.liveCues.value ?: snap.cues
        val new = transform(current)
        controller.setCues(new)
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    mutex.withLock { repo.setCues(snap.entryId, new) }
                }
            } catch (e: EntryNotFound) {
                // The entry may have been deleted during the run; nothing to persist.
            }
        }
    }

    /** The name and countsReps come from the frozen snapshot, so a rename during the run never shows here (spec §7.1). */
    private fun toUi(state: TimerState): TimerUiState =
        TimerUiMapper.map(state, controller.snapshot?.entryName.orEmpty(), controller.snapshot?.countsReps ?: true)
}
