package com.mitenko.hiitcounter.ui.entries

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [reps] is the entry's current total, i.e. the next workout's total. [streak] is the current
 * check-in streak, which a check-in-only row shows instead (spec R4 §4.3).
 */
data class EntryRow(
    val id: Long,
    val name: String,
    val reps: Int,
    val checkedInToday: Boolean,
    val type: EntryType = EntryType.WORKOUT,
    val streak: Int = 0,
)

sealed interface EntryListUiState {
    data object Loading : EntryListUiState
    data object Empty : EntryListUiState
    data class Items(val rows: List<EntryRow>) : EntryListUiState
}

@HiltViewModel
class EntryListViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val _reorderMode = MutableStateFlow(false)

    /** Loading until the repository first emits; it waits for the migration, so Empty never races the import (spec §7.3). */
    val uiState: StateFlow<EntryListUiState> = combine(repo.entries, refresh) { entries, _ ->
        if (entries.isEmpty()) {
            EntryListUiState.Empty
        } else {
            val zone = clock.zone()
            val today = clock.now().atZone(zone).toLocalDate()
            EntryListUiState.Items(
                entries.map { e ->
                    EntryRow(
                        id = e.id,
                        name = e.name,
                        reps = e.counter.total,
                        checkedInToday = e.counter.lastCheckIn?.atZone(zone)?.toLocalDate() == today,
                        type = e.type,
                        streak = e.counter.currentStreak,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryListUiState.Loading)

    val reorderMode: StateFlow<Boolean> = _reorderMode.asStateFlow()

    init {
        // Reorder mode ends when the list becomes empty (the toggle disappears with the last row).
        viewModelScope.launch { repo.entries.collect { if (it.isEmpty()) _reorderMode.value = false } }
    }

    /** Re-evaluates "Checked in today" when the list resumes, e.g. after midnight. */
    fun onResume() {
        refresh.update { it + 1 }
    }

    fun toggleReorder() {
        _reorderMode.update { !it }
    }

    fun moveUp(id: Long) = move(id, -1)

    fun moveDown(id: Long) = move(id, +1)

    /**
     * Creates with defaults and the chosen [type] (spec R4 §4.4) and reports the new id for
     * navigation (spec §7.3). The name dialog already blocks invalid names.
     */
    fun create(name: String, type: EntryType = EntryType.WORKOUT, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = try {
                repo.create(name, type)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Create rejected: ${e.message}")
                return@launch
            }
            onCreated(id)
        }
    }

    /** The repository clamps the target inside its transaction, so rapid taps never act on a stale list. */
    private fun move(id: Long, delta: Int) {
        viewModelScope.launch {
            try {
                repo.moveBy(id, delta)
            } catch (e: EntryNotFound) {
                Log.w(TAG, "Move of a deleted entry ignored", e)
            }
        }
    }

    private companion object {
        const val TAG = "EntryListViewModel"
    }
}
