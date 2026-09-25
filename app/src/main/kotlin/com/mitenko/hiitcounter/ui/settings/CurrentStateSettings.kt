package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.DateFormats
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class CurrentStateViewModel @Inject constructor(
    private val counter: CounterRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    data class Draft(val total: String, val best: String, val current: String, val lastCheckIn: Instant?)

    private var config = ProgressionConfig()
    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(::validate) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    val zone: ZoneId get() = clock.zone()
    fun now(): Instant = clock.now()

    init {
        viewModelScope.launch {
            config = settings.progression.first()
            val s = counter.state.first()
            _draft.value = Draft(s.total.toString(), s.bestStreak.toString(), s.currentStreak.toString(), s.lastCheckIn)
        }
    }

    fun update(transform: (Draft) -> Draft) {
        _draft.update { it?.let(transform) }
    }

    private fun parsed(d: Draft): Triple<Int, Int, Int>? {
        val t = d.total.trim().toIntOrNull() ?: return null
        val b = d.best.trim().toIntOrNull() ?: return null
        val c = d.current.trim().toIntOrNull() ?: return null
        return Triple(t, b, c)
    }

    private fun validate(d: Draft): ValidationResult {
        val numbers = parsed(d)
        if (numbers == null) {
            val errors = mutableMapOf<Field, String>()
            if (d.total.trim().toIntOrNull() == null) errors[Field.TOTAL] = SettingsValidator.NOT_A_NUMBER
            if (d.best.trim().toIntOrNull() == null) errors[Field.BEST_STREAK] = SettingsValidator.NOT_A_NUMBER
            if (d.current.trim().toIntOrNull() == null) errors[Field.CURRENT_STREAK] = SettingsValidator.NOT_A_NUMBER
            return ValidationResult(errors)
        }
        val (t, b, c) = numbers
        return SettingsValidator.currentState(t, b, c, d.lastCheckIn, clock.now(), config)
    }

    /** Overwrites the counter and resets holdCount (spec §6). */
    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (!validate(d).isValid) return
        val (t, b, c) = parsed(d) ?: return
        viewModelScope.launch {
            counter.overwrite(t, b, c, d.lastCheckIn)
            onSaved()
        }
    }

    fun resetProgress(onDone: () -> Unit) {
        viewModelScope.launch {
            counter.resetProgress()
            onDone()
        }
    }
}

@Composable
fun CurrentStateRoute(onBack: () -> Unit, vm: CurrentStateViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val d = draft ?: return
    var picking by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.settings_current_state),
        onBack = onBack,
        actions = { TextButton(onClick = { vm.save(onBack) }, enabled = validation.isValid) { Text(stringResource(R.string.save)) } },
    ) {
        NumberField(stringResource(R.string.current_total), d.total, { v -> vm.update { it.copy(total = v) } }, validation.errors[Field.TOTAL], hint = validation.hints[Field.TOTAL])
        NumberField(stringResource(R.string.best_streak_field), d.best, { v -> vm.update { it.copy(best = v) } }, validation.errors[Field.BEST_STREAK])
        NumberField(stringResource(R.string.current_streak_field), d.current, { v -> vm.update { it.copy(current = v) } }, validation.errors[Field.CURRENT_STREAK])
        Text(stringResource(R.string.last_check_in_field), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(d.lastCheckIn?.let { DateFormats.dateTime(it, vm.zone) } ?: stringResource(R.string.none), modifier = Modifier.weight(1f))
            TextButton(onClick = { picking = true }) { Text(stringResource(R.string.set)) }
            TextButton(onClick = { vm.update { it.copy(lastCheckIn = null) } }, enabled = d.lastCheckIn != null) {
                Text(stringResource(R.string.clear))
            }
        }
        validation.errors[Field.LAST_CHECK_IN]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 24.dp),
        ) { Text(stringResource(R.string.reset_progress)) }
    }

    if (picking) {
        DateTimePickerDialog(
            initial = d.lastCheckIn ?: vm.now(),
            zone = vm.zone,
            onPicked = { t -> picking = false; vm.update { it.copy(lastCheckIn = t) } },
            onDismiss = { picking = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_progress_title)) },
            text = { Text(stringResource(R.string.reset_progress_body)) },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetProgress(onBack) }) { Text(stringResource(R.string.reset)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(initial: Instant, zone: ZoneId, onPicked: (Instant) -> Unit, onDismiss: () -> Unit) {
    val start = initial.atZone(zone)
    var step by remember { mutableIntStateOf(0) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { step = 1 }) { Text(stringResource(R.string.next)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker reports UTC midnight of the chosen day.
                    val date = Instant.ofEpochMilli(dateState.selectedDateMillis ?: start.toInstant().toEpochMilli())
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    onPicked(date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant())
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = timeState) },
        )
    }
}
