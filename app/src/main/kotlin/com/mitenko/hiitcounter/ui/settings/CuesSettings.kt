package com.mitenko.hiitcounter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuesSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val cues: StateFlow<CueConfig> = settings.cues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CueConfig())

    fun setSound(on: Boolean) {
        viewModelScope.launch { settings.setCues(settings.cues.first().copy(sound = on)) }
    }

    fun setVibration(on: Boolean) {
        viewModelScope.launch { settings.setCues(settings.cues.first().copy(vibration = on)) }
    }
}

@Composable
fun CuesSettingsRoute(onBack: () -> Unit, vm: CuesSettingsViewModel = hiltViewModel()) {
    val cues by vm.cues.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.settings_cues), onBack = onBack) {
        SwitchRow(R.string.sound, cues.sound, vm::setSound)
        SwitchRow(R.string.vibration, cues.vibration, vm::setVibration)
    }
}

@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
