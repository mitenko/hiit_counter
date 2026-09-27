package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class CurrentStateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val clock: Clock,
) : EntryScopedViewModel(savedStateHandle, repo) {
    /** Typed draft (spec §8.1). */
    data class Draft(val total: Int, val best: Int, val current: Int, val lastCheckIn: Instant?)

    /** The entry's own progression, used only for the "outside floor–cap" hint. */
    private val config = MutableStateFlow(ProgressionConfig())
    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = combine(_draft, config) { d, c -> d?.let { validate(it, c) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    val zone: ZoneId get() = clock.zone()

    fun now(): Instant = clock.now()

    init {
        viewModelScope.launch {
            repo.entry(entryId).first()?.let { e ->
                config.value = e.progression
                _draft.value = Draft(e.counter.total, e.counter.bestStreak, e.counter.currentStreak, e.counter.lastCheckIn)
            }
        }
    }

    fun update(transform: (Draft) -> Draft) {
        _draft.update { it?.let(transform) }
    }

    /** Overwrites the counter; the same UPDATE resets holdCount (spec §5.3). */
    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (!validate(d, config.value).isValid) return
        viewModelScope.launch {
            try {
                repo.overwriteCounter(entryId, d.total, d.best, d.current, d.lastCheckIn)
                onSaved()
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    fun resetProgress(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.resetProgress(entryId)
                onDone()
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    private fun validate(d: Draft, c: ProgressionConfig): ValidationResult =
        SettingsValidator.currentState(d.total, d.best, d.current, d.lastCheckIn, clock.now(), c)
}

@Composable
fun CurrentStateRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: CurrentStateViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    val d = draft ?: return
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val lastLabel = stringResource(R.string.last_check_in_field)

    SettingsScaffold(
        title = stringResource(R.string.settings_current_state),
        onBack = onBack,
        actions = {
            TextButton(onClick = { vm.save(onBack) }, enabled = validation.isValid, modifier = Modifier.testTag("save")) {
                Text(stringResource(R.string.save))
            }
        },
    ) {
        IntStepperField(
            stringResource(R.string.current_total), d.total, FieldRanges.TOTAL, ValueInput.WHOLE,
            onUpdate = { f -> vm.update { it.copy(total = f(it.total)) } },
            error = validation.errors[Field.TOTAL], hint = validation.hints[Field.TOTAL],
        )
        IntStepperField(
            stringResource(R.string.best_streak_field), d.best, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> vm.update { it.copy(best = f(it.best)) } }, error = validation.errors[Field.BEST_STREAK],
        )
        IntStepperField(
            stringResource(R.string.current_streak_field), d.current, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> vm.update { it.copy(current = f(it.current)) } }, error = validation.errors[Field.CURRENT_STREAK],
        )
        Text(lastLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tapping the date text opens the date and time pickers (spec §8.1).
            Text(
                d.lastCheckIn?.let { DateFormats.dateTime(it, vm.zone) } ?: stringResource(R.string.none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.edit_value, lastLabel)) { picking = true }
                    .padding(vertical = 12.dp)
                    .testTag("last_check_in"),
            )
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
            onPicked = { t ->
                picking = false
                vm.update { it.copy(lastCheckIn = t) }
            },
            onDismiss = { picking = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_progress_title)) },
            text = { Text(stringResource(R.string.reset_progress_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetProgress(onBack)
                }) { Text(stringResource(R.string.reset)) }
            },
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
