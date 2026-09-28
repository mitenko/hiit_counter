package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.PenaltyDraft
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.PenaltyStepperField
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

/** Typed draft (spec §8.1): one domain value per field, with the penalty in integer half-hours. */
data class ProgressionDraft(
    val startingTotal: Int,
    val floor: Int,
    val cap: Int,
    val holdAt: Int,
    val holdFor: Int,
    val windowHours: Int,
    val penalty: PenaltyDraft,
) {
    fun toConfig() = ProgressionConfig(startingTotal, floor, cap, holdAt, holdFor, windowHours, penalty.hours)

    companion object {
        fun from(c: ProgressionConfig) = ProgressionDraft(
            c.startingTotal, c.floor, c.cap, c.holdAt, c.holdFor, c.windowHours, PenaltyDraft.of(c.penaltyHoursPerRep),
        )
    }
}

@HiltViewModel
class ProgressionSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _draft = MutableStateFlow<ProgressionDraft?>(null)
    val draft: StateFlow<ProgressionDraft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { d -> d?.let { SettingsValidator.progression(it.toConfig()) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    init {
        viewModelScope.launch { repo.entry(entryId).first()?.let { _draft.value = ProgressionDraft.from(it.progression) } }
    }

    fun update(transform: (ProgressionDraft) -> ProgressionDraft) {
        _draft.update { it?.let(transform) }
    }

    fun resetToDefaults() {
        _draft.value = ProgressionDraft.from(ProgressionConfig())
    }

    /** setProgression resets holdCount in the same UPDATE (spec §5.3). */
    fun save(onSaved: () -> Unit) {
        val config = _draft.value?.toConfig() ?: return
        if (!SettingsValidator.progression(config).isValid) return
        viewModelScope.launch {
            try {
                repo.setProgression(entryId, config)
                onSaved()
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }
}

@Composable
fun ProgressionSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: ProgressionSettingsViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    draft?.let {
        ProgressionSettingsScreen(
            it, validation, onBack, onChange = vm::update, onSave = { vm.save(onBack) }, onReset = vm::resetToDefaults,
        )
    }
}

@Composable
fun ProgressionSettingsScreen(
    draft: ProgressionDraft,
    validation: ValidationResult,
    onBack: () -> Unit,
    onChange: ((ProgressionDraft) -> ProgressionDraft) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    val errors = validation.errors
    SettingsScaffold(
        title = stringResource(R.string.settings_progression),
        onBack = onBack,
        actions = {
            TextButton(onClick = onSave, enabled = validation.isValid, modifier = Modifier.testTag("save")) {
                Text(stringResource(R.string.save))
            }
        },
    ) {
        IntStepperField(
            stringResource(R.string.starting_total), draft.startingTotal, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(startingTotal = f(it.startingTotal)) } }, error = errors[Field.STARTING_TOTAL],
        )
        IntStepperField(
            stringResource(R.string.floor), draft.floor, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(floor = f(it.floor)) } }, error = errors[Field.FLOOR],
        )
        IntStepperField(
            stringResource(R.string.cap), draft.cap, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(cap = f(it.cap)) } }, error = errors[Field.CAP],
        )
        IntStepperField(
            stringResource(R.string.hold_at), draft.holdAt, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(holdAt = f(it.holdAt)) } },
            error = errors[Field.HOLD_AT], hint = validation.hints[Field.HOLD_AT],
        )
        IntStepperField(
            stringResource(R.string.hold_for), draft.holdFor, FieldRanges.HOLD_FOR, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(holdFor = f(it.holdFor)) } }, error = errors[Field.HOLD_FOR],
        )
        IntStepperField(
            stringResource(R.string.window_hours), draft.windowHours, FieldRanges.WINDOW_HOURS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(windowHours = f(it.windowHours)) } }, error = errors[Field.WINDOW_HOURS],
        )
        PenaltyStepperField(
            stringResource(R.string.penalty_rate), draft.penalty,
            onUpdate = { f -> onChange { it.copy(penalty = f(it.penalty)) } }, error = errors[Field.PENALTY_RATE],
        )
        OutlinedButton(onClick = onReset, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.reset_defaults))
        }
    }
}
