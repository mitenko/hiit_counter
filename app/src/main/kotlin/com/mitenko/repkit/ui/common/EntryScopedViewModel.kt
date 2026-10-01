package com.mitenko.repkit.ui.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.data.EntryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The `{id}` route argument of every per-entry screen (spec §7.2). */
const val ENTRY_ID_ARG = "id"

/**
 * Base for ViewModels bound to one entry. [missing] turns true only once the entry flow has
 * loaded and emitted null (deleted elsewhere, stale saved state, bad id), or when a write hit
 * EntryNotFound. The initial loading value never triggers it (spec §7.2).
 */
abstract class EntryScopedViewModel(
    savedStateHandle: SavedStateHandle,
    protected val repo: EntryRepository,
) : ViewModel() {
    protected val entryId: Long =
        checkNotNull(savedStateHandle.get<Long>(ENTRY_ID_ARG)) { "Missing route argument '$ENTRY_ID_ARG'" }

    private val gone = MutableStateFlow(false)

    val missing: StateFlow<Boolean> = combine(repo.entry(entryId).map { it == null }, gone) { absent, lost -> absent || lost }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A write raced a delete (spec §7.5): the screen pops to the list instead of crashing. */
    protected fun markMissing() {
        gone.value = true
    }
}
