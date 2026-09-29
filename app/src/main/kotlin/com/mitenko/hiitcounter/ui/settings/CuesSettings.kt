package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.SettingsPageLayout
import com.mitenko.hiitcounter.ui.common.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class CuesSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val cues: StateFlow<CueConfig> = repo.entry(entryId).filterNotNull().map { it.cues }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CueConfig())

    /** Serialises the read-modify-write below so two quick toggles can't overwrite each other. */
    private val mutex = Mutex()

    fun setSound(on: Boolean) = edit { it.copy(sound = on) }

    fun setVibration(on: Boolean) = edit { it.copy(vibration = on) }

    /** Cues save immediately (as in v1). A toggle racing a delete pops to the list (spec §7.5). */
    private fun edit(transform: (CueConfig) -> CueConfig) {
        viewModelScope.launch {
            try {
                mutex.withLock {
                    val current = repo.entry(entryId).first()?.cues ?: return@withLock markMissing()
                    repo.setCues(entryId, transform(current))
                }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }
}

/** The Cues page inside the pager (spec R3 §4). Each switch saves at once, as before, so there's no status line. */
@Composable
fun CuesPage(vm: CuesSettingsViewModel) {
    val cues by vm.cues.collectAsStateWithLifecycle()
    SettingsPageLayout {
        SwitchRow(stringResource(R.string.sound), cues.sound, vm::setSound, info = stringResource(R.string.info_sound))
        SwitchRow(stringResource(R.string.vibration), cues.vibration, vm::setVibration, info = stringResource(R.string.info_vibration))
    }
}
