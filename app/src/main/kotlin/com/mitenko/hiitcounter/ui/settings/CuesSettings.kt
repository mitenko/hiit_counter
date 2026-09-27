package com.mitenko.hiitcounter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
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

@Composable
fun CuesSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: CuesSettingsViewModel = hiltViewModel()) {
    val cues by vm.cues.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    SettingsScaffold(title = stringResource(R.string.settings_cues), onBack = onBack) {
        SwitchRow(R.string.sound, cues.sound, vm::setSound)
        SwitchRow(R.string.vibration, cues.vibration, vm::setVibration)
    }
}

/** Restyled with the stepper rows' spacing (spec §8.1). */
@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
