package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.NumberField
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

/** Text-field draft; parsed on validate/save. */
data class ProgressionDraft(
    val startingTotal: String,
    val floor: String,
    val cap: String,
    val holdAt: String,
    val holdFor: String,
    val windowHours: String,
    val penaltyHoursPerRep: String,
) {
    fun parse(): Pair<ProgressionConfig?, Map<Field, String>> {
        val errors = mutableMapOf<Field, String>()
        fun int(text: String, field: Field): Int? =
            text.trim().toIntOrNull().also { if (it == null) errors[field] = SettingsValidator.NOT_A_NUMBER }
        val st = int(startingTotal, Field.STARTING_TOTAL)
        val fl = int(floor, Field.FLOOR)
        val cp = int(cap, Field.CAP)
        val ha = int(holdAt, Field.HOLD_AT)
        val hf = int(holdFor, Field.HOLD_FOR)
        val wh = int(windowHours, Field.WINDOW_HOURS)
        val pr = penaltyHoursPerRep.trim().toDoubleOrNull().also {
            if (it == null) errors[Field.PENALTY_RATE] = SettingsValidator.NOT_A_NUMBER
        }
        if (errors.isNotEmpty()) return null to errors
        return ProgressionConfig(st!!, fl!!, cp!!, ha!!, hf!!, wh!!, pr!!) to errors
    }

    companion object {
        fun from(c: ProgressionConfig) = ProgressionDraft(
            c.startingTotal.toString(), c.floor.toString(), c.cap.toString(), c.holdAt.toString(),
            c.holdFor.toString(), c.windowHours.toString(), c.penaltyHoursPerRep.toString(),
        )

        fun validate(d: ProgressionDraft): ValidationResult {
            val (config, errors) = d.parse()
            return if (config == null) ValidationResult(errors) else SettingsValidator.progression(config)
        }
    }
}

@HiltViewModel
class ProgressionSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val counter: CounterRepository,
) : ViewModel() {
    private val _draft = MutableStateFlow<ProgressionDraft?>(null)
    val draft: StateFlow<ProgressionDraft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(ProgressionDraft::validate) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    init {
        viewModelScope.launch { _draft.value = ProgressionDraft.from(settings.progression.first()) }
    }

    fun update(transform: (ProgressionDraft) -> ProgressionDraft) {
        _draft.update { it?.let(transform) }
    }

    fun resetToDefaults() {
        _draft.value = ProgressionDraft.from(ProgressionConfig())
    }

    /** Saving also resets holdCount (spec §6) since hold-at/hold-for may have changed. */
    fun save(onSaved: () -> Unit) {
        val config = _draft.value?.parse()?.first ?: return
        if (!SettingsValidator.progression(config).isValid) return
        viewModelScope.launch {
            settings.setProgression(config)
            counter.resetHoldCount()
            onSaved()
        }
    }
}

@Composable
fun ProgressionSettingsRoute(onBack: () -> Unit, vm: ProgressionSettingsViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val d = draft ?: return
    val onChange = vm::update
    SettingsScaffold(
        title = stringResource(R.string.settings_progression),
        onBack = onBack,
        actions = { TextButton(onClick = { vm.save(onBack) }, enabled = validation.isValid) { Text(stringResource(R.string.save)) } },
    ) {
        NumberField(stringResource(R.string.starting_total), d.startingTotal, { v -> onChange { it.copy(startingTotal = v) } }, validation.errors[Field.STARTING_TOTAL])
        NumberField(stringResource(R.string.floor), d.floor, { v -> onChange { it.copy(floor = v) } }, validation.errors[Field.FLOOR])
        NumberField(stringResource(R.string.cap), d.cap, { v -> onChange { it.copy(cap = v) } }, validation.errors[Field.CAP])
        NumberField(stringResource(R.string.hold_at), d.holdAt, { v -> onChange { it.copy(holdAt = v) } }, validation.errors[Field.HOLD_AT], hint = validation.hints[Field.HOLD_AT])
        NumberField(stringResource(R.string.hold_for), d.holdFor, { v -> onChange { it.copy(holdFor = v) } }, validation.errors[Field.HOLD_FOR])
        NumberField(stringResource(R.string.window_hours), d.windowHours, { v -> onChange { it.copy(windowHours = v) } }, validation.errors[Field.WINDOW_HOURS])
        NumberField(stringResource(R.string.penalty_rate), d.penaltyHoursPerRep, { v -> onChange { it.copy(penaltyHoursPerRep = v) } }, validation.errors[Field.PENALTY_RATE], decimal = true)
        OutlinedButton(onClick = vm::resetToDefaults, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.reset_defaults))
        }
    }
}
