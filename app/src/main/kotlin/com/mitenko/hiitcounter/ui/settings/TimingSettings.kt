package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.common.ValueInput
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

/** Typed draft (spec §8.1): one TimingConfig held in the ViewModel, so it survives configuration changes. */
@HiltViewModel
class TimingSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _draft = MutableStateFlow<TimingConfig?>(null)
    val draft: StateFlow<TimingConfig?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(SettingsValidator::timing) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    init {
        viewModelScope.launch { repo.entry(entryId).first()?.let { _draft.value = it.timing } }
    }

    fun update(transform: (TimingConfig) -> TimingConfig) {
        _draft.update { it?.let(transform) }
    }

    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (!SettingsValidator.timing(d).isValid) return
        viewModelScope.launch {
            try {
                repo.setTiming(entryId, d)
                onSaved()
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }
}

@Composable
fun TimingSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: TimingSettingsViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
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
    val errors = validation.errors
    val totalError = errors[Field.TOTAL_DURATION]
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
        IntStepperField(
            stringResource(R.string.prepare), draft.prepareSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(prepareSec = f(it.prepareSec)) } }, error = errors[Field.PREPARE],
        )
        IntStepperField(
            stringResource(R.string.sets), draft.sets, FieldRanges.SETS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(sets = f(it.sets)) } }, error = errors[Field.SETS],
        )
        IntStepperField(
            stringResource(R.string.work), draft.workSec, FieldRanges.WORK, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(workSec = f(it.workSec)) } }, error = errors[Field.WORK],
        )
        IntStepperField(
            stringResource(R.string.rest), draft.restSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(restSec = f(it.restSec)) } }, error = errors[Field.REST],
        )
        IntStepperField(
            stringResource(R.string.cooldown), draft.cooldownSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(cooldownSec = f(it.cooldownSec)) } }, error = errors[Field.COOLDOWN],
        )
        totalError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}
