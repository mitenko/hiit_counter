package com.mitenko.repkit.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.R
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.platform.VoiceAvailability
import com.mitenko.repkit.ui.common.EntryScopedViewModel
import com.mitenko.repkit.ui.common.SettingsPageLayout
import com.mitenko.repkit.ui.common.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    voice: VoiceAvailability,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val cues: StateFlow<CueConfig> = repo.entry(entryId).filterNotNull().map { it.cues }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CueConfig())

    /** Whether this device can say the Voice cue; null while the check runs (spec R4 §4.7, plan Spec note 4). */
    private val _voiceAvailable = MutableStateFlow<Boolean?>(null)
    val voiceAvailable: StateFlow<Boolean?> = _voiceAvailable.asStateFlow()

    /** Serialises the read-modify-write below so two quick toggles can't overwrite each other. */
    private val mutex = Mutex()

    init {
        // Spec revision 8: every entry has a Cues tab, so the throwaway engine check always runs.
        viewModelScope.launch { _voiceAvailable.value = voice.check() }
    }

    fun setSound(on: Boolean) = edit { it.copy(sound = on) }

    fun setVibration(on: Boolean) = edit { it.copy(vibration = on) }

    /** Spec R4 §4.7: saved at once, like Sound and Vibration, whether or not the device has a voice. */
    fun setVoice(on: Boolean) = edit { it.copy(voice = on) }

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
    val voiceAvailable by vm.voiceAvailable.collectAsStateWithLifecycle()
    SettingsPageLayout {
        SwitchRow(stringResource(R.string.sound), cues.sound, vm::setSound, info = stringResource(R.string.info_sound))
        SwitchRow(stringResource(R.string.vibration), cues.vibration, vm::setVibration, info = stringResource(R.string.info_vibration))
        // Spec R4 §4.7: the switch still saves without a usable voice; the text says why nothing will be heard.
        SwitchRow(
            stringResource(R.string.voice), cues.voice, vm::setVoice, info = stringResource(R.string.info_voice),
            supportingText = if (voiceAvailable == false) stringResource(R.string.voice_unavailable) else null,
        )
    }
}
