package com.mitenko.repkit.ui.entries

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.FreeLimits
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.domain.canAddEntry
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.WeightUnit
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val entitlements: Entitlements,
    private val limits: FreeLimits,
    private val proUpgrade: ProUpgrade,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    private val _limitDialog = MutableStateFlow(false)

    /** Spec revision 18 §3: the free tier's limit dialog, shown instead of creating anything. */
    val limitDialog: StateFlow<Boolean> = _limitDialog.asStateFlow()

    /** The free tier's entry limit, for the dialog's text. */
    val maxEntries: Int get() = limits.maxEntries

    /** The Appearance choice (spec rev 14 §5), for the ⚙ dialog in the top bar. */
    val themeMode: StateFlow<ThemeMode> =
        preferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /** Spec rev 30 §3: the "Share crash reports and usage" switch in the same dialog; on by default. */
    val crashReportsEnabled: StateFlow<Boolean> =
        preferences.crashReportsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Saved at once; TelemetryInitializer applies it to Crashlytics and Analytics. */
    fun setCrashReportsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setCrashReportsEnabled(enabled) }
    }

    /** ⚙ › Units (spec rev 26 §3, plan Spec note 36): the unit new weight workouts start in. KG stands in until it loads. */
    val weightUnitDefault: StateFlow<WeightUnit> =
        preferences.weightUnitDefault.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    fun setWeightUnitDefault(unit: WeightUnit) {
        viewModelScope.launch { preferences.setWeightUnitDefault(unit) }
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

    /** In-flight guard for [create]; touched only on Main, like EntrySettingsViewModel's `duplicating`. */
    private var creating = false

    /**
     * Spec revision 18 §3: called before the New dialog opens. True lets it open; false shows the
     * limit dialog instead. It counts the rows on screen; [create] re-checks against the repository.
     */
    fun requestAdd(): Boolean {
        val count = (uiState.value as? EntryListUiState.Items)?.rows?.size ?: 0
        return allowAdd(count)
    }

    /** Go Pro (spec revision 18 §3): starts the upgrade, then closes the dialog. */
    fun goPro() {
        proUpgrade.start()
        _limitDialog.value = false
    }

    /** Not now (spec revision 18 §3). */
    fun dismissLimit() {
        _limitDialog.value = false
    }

    private fun allowAdd(entryCount: Int): Boolean =
        canAddEntry(entitlements.tier.value, entryCount, limits).also { if (!it) _limitDialog.value = true }

    /**
     * Creates with defaults and the chosen [type] (spec R4 §4.4) and reports the new id for
     * navigation (spec §7.3). The name dialog already blocks invalid names.
     */
    fun create(name: String, type: EntryType = EntryType.WORKOUT, onCreated: (Long) -> Unit) {
        // A double submit is ignored while a create is in flight, so two calls can't both pass the
        // limit check before either has written (spec revision 18 §3).
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                // Spec revision 18 §3: at the free limit nothing is created; the limit dialog shows instead.
                if (!allowAdd(repo.entries.first().size)) return@launch
                val id = try {
                    repo.create(name, type)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Create rejected: ${e.message}")
                    return@launch
                }
                onCreated(id)
            } finally {
                creating = false
            }
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
