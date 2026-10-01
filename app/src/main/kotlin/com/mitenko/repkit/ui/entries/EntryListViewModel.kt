package com.mitenko.repkit.ui.entries

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.tileWindowStart
import com.mitenko.repkit.domain.weekCount
import com.mitenko.repkit.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [reps] is the entry's current total, i.e. the next workout's total, and [streak] the current
 * check-in streak (spec R4 §4.3); since rev 9 the tile shows neither (plan Spec note 22). [weekCount]
 * is the check-ins since local Monday 00:00, and [tile] the tile graph's 28-day data (spec rev 9 §2).
 */
data class EntryRow(
    val id: Long,
    val name: String,
    val reps: Int,
    val checkedInToday: Boolean,
    val type: EntryType = EntryType.WORKOUT,
    val streak: Int = 0,
    val weekCount: Int = 0,
    val tile: TileData = TileData(),
)

sealed interface EntryListUiState {
    data object Loading : EntryListUiState
    data object Empty : EntryListUiState
    data class Items(val rows: List<EntryRow>) : EntryListUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntryListViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val clock: Clock,
    private val preferences: AppPreferences,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    /** The Appearance choice (spec rev 14 §5), for the ⚙ dialog in the top bar. */
    val themeMode: StateFlow<ThemeMode> =
        preferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /**
     * Spec R6 §3.3: one query for every row's recent points. It restarts on each resume, so the
     * window follows the date (plan Spec note 15). The 28-day window always contains this week.
     */
    private val recent: Flow<Map<Long, List<CheckInPoint>>> =
        refresh.flatMapLatest { repo.recentCheckIns(tileWindowStart(clock.now(), clock.zone())) }

    /** Loading until the repository first emits; it waits for the migration, so Empty never races the import (spec §7.3). */
    val uiState: StateFlow<EntryListUiState> = combine(repo.entries, recent, refresh) { entries, recentPoints, _ ->
        if (entries.isEmpty()) {
            EntryListUiState.Empty
        } else {
            val now = clock.now()
            val zone = clock.zone()
            val today = now.atZone(zone).toLocalDate()
            EntryListUiState.Items(
                entries.map { e ->
                    val points = recentPoints[e.id].orEmpty()
                    EntryRow(
                        id = e.id,
                        name = e.name,
                        reps = e.counter.total,
                        checkedInToday = e.counter.lastCheckIn?.atZone(zone)?.toLocalDate() == today,
                        type = e.type,
                        streak = e.counter.currentStreak,
                        weekCount = weekCount(points, now, zone),
                        tile = TileLayout.tile(points, now, zone),
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryListUiState.Loading)

    /** Re-evaluates "Checked in today", "this week" and the tile window when the list resumes (spec R6 §4.1). */
    fun onResume() {
        refresh.update { it + 1 }
    }

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

    /**
     * The repository clamps the target inside its transaction, so rapid taps never act on a stale
     * list. Also persists a drag-and-drop reorder; a delta of 0 (dropped where it started) writes
     * nothing. Runs in withContext(NonCancellable), matching checkIn/setType: a drop that lands
     * right as the screen leaves composition still finishes its write.
     */
    fun move(id: Long, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { repo.moveBy(id, delta) }
            } catch (e: EntryNotFound) {
                Log.w(TAG, "Move of a deleted entry ignored", e)
            }
        }
    }

    private companion object {
        const val TAG = "EntryListViewModel"
    }
}
