package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.ui.common.RepeatingIconButton
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimingSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    private val _draft = MutableStateFlow<TimingConfig?>(null)
    val draft: StateFlow<TimingConfig?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(SettingsValidator::timing) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    init {
        viewModelScope.launch { _draft.value = settings.timing.first() }
    }

    fun update(transform: (TimingConfig) -> TimingConfig) {
        _draft.update { it?.let(transform) }
    }

    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (!SettingsValidator.timing(d).isValid) return
        viewModelScope.launch {
            settings.setTiming(d)
            onSaved()
        }
    }
}

@Composable
fun TimingSettingsRoute(onBack: () -> Unit, vm: TimingSettingsViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    draft?.let { TimingSettingsScreen(it, validation, onBack, onChange = vm::update, onSave = { vm.save(onBack) }) }
}

@Composable
fun TimingSettingsScreen(
    draft: TimingConfig,
    validation: ValidationResult,
    onBack: () -> Unit,
    onChange: ((TimingConfig) -> TimingConfig) -> Unit,
    onSave: () -> Unit,
) {
    val max = SettingsValidator.MAX_PHASE_SEC
    val totalError = validation.errors[Field.TOTAL_DURATION]
    SettingsScaffold(
        title = stringResource(R.string.settings_timing),
        onBack = onBack,
        actions = {
            TextButton(onClick = onSave, enabled = validation.isValid, modifier = Modifier.testTag("save")) {
                Text(stringResource(R.string.save))
            }
        },
        bottomBar = {
            Surface(color = if (totalError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    stringResource(R.string.total_duration, TimerText.formatDuration(draft.totalDurationSec)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("total"),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
    ) {
        StepperRow(stringResource(R.string.prepare), TimerText.formatMmSs(draft.prepareSec),
            onMinus = { onChange { it.copy(prepareSec = (it.prepareSec - 5).coerceAtLeast(0)) } },
            onPlus = { onChange { it.copy(prepareSec = (it.prepareSec + 5).coerceAtMost(max)) } })
        StepperRow(stringResource(R.string.sets), "${draft.sets}",
            onMinus = { onChange { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } },
            onPlus = { onChange { it.copy(sets = (it.sets + 1).coerceAtMost(SettingsValidator.MAX_SETS)) } })
        StepperRow(stringResource(R.string.work), TimerText.formatMmSs(draft.workSec),
            onMinus = { onChange { it.copy(workSec = (it.workSec - 5).coerceAtLeast(5)) } },
            onPlus = { onChange { it.copy(workSec = (it.workSec + 5).coerceAtMost(max)) } })
        StepperRow(stringResource(R.string.rest), TimerText.formatMmSs(draft.restSec),
            onMinus = { onChange { it.copy(restSec = (it.restSec - 5).coerceAtLeast(0)) } },
            onPlus = { onChange { it.copy(restSec = (it.restSec + 5).coerceAtMost(max)) } })
        StepperRow(stringResource(R.string.cooldown), TimerText.formatMmSs(draft.cooldownSec),
            onMinus = { onChange { it.copy(cooldownSec = (it.cooldownSec - 5).coerceAtLeast(0)) } },
            onPlus = { onChange { it.copy(cooldownSec = (it.cooldownSec + 5).coerceAtMost(max)) } })
        totalError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RepeatingIconButton(onMinus, R.drawable.ic_remove, stringResource(R.string.decrease, label))
            Text(
                value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 140.dp).testTag("value_$label"),
            )
            RepeatingIconButton(onPlus, R.drawable.ic_add, stringResource(R.string.increase, label))
        }
    }
}
