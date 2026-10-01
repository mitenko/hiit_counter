package com.mitenko.hiitcounter.ui.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.di.ApplicationScope
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.AutoSaver
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.InfoTag
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.common.SaveStatusLine
import com.mitenko.hiitcounter.ui.common.SettingsPageLayout
import com.mitenko.hiitcounter.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * The Current page (spec R3 §6). It uses the same draft and save pipeline as Timing. overwriteCounter
 * keeps the hold count unless the total changes (§6.3). While the pager is open, a draft without
 * unsaved edits follows the stored counter, and the floor–cap hint follows the stored progression
 * (plan Spec note 2).
 */
@HiltViewModel
class CurrentStateViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : EntryScopedViewModel(savedStateHandle, repo) {
    /** Typed draft (spec R2 §8.1). */
    data class Draft(val total: Int, val best: Int, val current: Int, val lastCheckIn: Instant?)

    /** The entry's own progression, used only for the "outside floor–cap" hint. */
    private val config = MutableStateFlow(ProgressionConfig())
    private val _draft = MutableStateFlow(savedStateHandle.get<LongArray>(DRAFT_KEY)?.toDraft())
    val draft: StateFlow<Draft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = combine(_draft, config) { d, c -> d?.let { validate(it, c) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    /** The counter as last stored. A draft equal to it, with no save pending, has no unsaved edits. */
    private var stored: Draft? = null

    private val saver = AutoSaver<Draft>(viewModelScope) { d ->
        try {
            repo.overwriteCounter(entryId, d.total, d.best, d.current, d.lastCheckIn)
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Counter $d rejected", e)
            failed.value = true
        }
    }

    val zone: ZoneId get() = clock.zone()

    fun now(): Instant = clock.now()

    init {
        // A valid draft restored after process death may never have been written; the write is idempotent if it was.
        val restored = _draft.value?.takeIf { validate(it, config.value).isValid }
        restored?.let(saver::schedule)
        viewModelScope.launch {
            repo.entry(entryId).filterNotNull().collect { e ->
                config.value = e.progression
                val latest = e.counter.toDraft()
                val current = _draft.value
                // The first store emission after a restore: a restored draft already matching the
                // store needs no write (Minor 3); a restored draft that differs still saves.
                if (stored == null && restored == latest) saver.cancel()
                // Follow the store only without unsaved edits. An edit back to the stored value
                // whose save is still pending counts as unsaved, so an echo can't overwrite it.
                if (current == null || (current == stored && !saver.hasPending)) setDraft(latest)
                stored = latest
            }
        }
    }

    /** A stepper change: saved 400 ms after the last one. */
    fun update(transform: (Draft) -> Draft) = edit(transform, now = false)

    /** A dialog OK, a date or time pick, or Clear: saved at once. */
    fun updateNow(transform: (Draft) -> Draft) = edit(transform, now = true)

    fun flush() = saver.flush()

    override fun onCleared() {
        saver.flushIn(appScope)
    }

    /**
     * Confirmed on the page and applied at once (spec R3 §6.4). A pending counter save is dropped,
     * and an in-flight one lands first. Run in [appScope]: leaving the page (viewModelScope
     * cancelled) can't drop the reset while it waits on the mutex or during the Room call. The
     * post-reset re-read that republishes the draft stays in viewModelScope; it's fine to lose it
     * once the page is gone. [clearHistory] is the dialog's Clear history too (spec R6 §4.3).
     */
    fun resetProgress(clearHistory: Boolean) {
        saver.cancel()
        appScope.launch {
            try {
                saver.exclusive { repo.resetProgress(entryId, clearHistory) }
                viewModelScope.launch {
                    repo.entry(entryId).first()?.let { e ->
                        val reset = e.counter.toDraft()
                        stored = reset
                        setDraft(reset)
                    }
                }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    private fun edit(transform: (Draft) -> Draft, now: Boolean) {
        val d = _draft.value?.let(transform) ?: return
        setDraft(d)
        when {
            !validate(d, config.value).isValid -> saver.cancel()
            now -> saver.saveNow(d)
            else -> saver.schedule(d)
        }
    }

    private fun setDraft(d: Draft) {
        _draft.value = d
        savedStateHandle[DRAFT_KEY] = longArrayOf(
            d.total.toLong(), d.best.toLong(), d.current.toLong(),
            if (d.lastCheckIn != null) 1L else 0L, d.lastCheckIn?.toEpochMilli() ?: 0L,
        )
    }

    private fun validate(d: Draft, c: ProgressionConfig): ValidationResult =
        SettingsValidator.currentState(d.total, d.best, d.current, d.lastCheckIn, clock.now(), c)

    private companion object {
        const val TAG = "CurrentState"
        const val DRAFT_KEY = "current_draft"

        fun CounterState.toDraft() = Draft(total, bestStreak, currentStreak, lastCheckIn)

        fun LongArray.toDraft() =
            Draft(this[0].toInt(), this[1].toInt(), this[2].toInt(), if (this[3] == 1L) Instant.ofEpochMilli(this[4]) else null)
    }
}

/**
 * The Current page inside the pager (spec R3 §4). Without [showTotal] (a Timer only entry,
 * R4 §4.6) the total row is hidden; the draft keeps the stored total, so saves write it back
 * unchanged and the hold count is kept (plan Spec note 8).
 */
@Composable
fun CurrentStatePage(vm: CurrentStateViewModel, showTotal: Boolean = true) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    draft?.let {
        CurrentStatePageContent(
            it, validation, status, vm.zone, vm::now,
            onChange = vm::update, onChangeNow = vm::updateNow, onResetProgress = vm::resetProgress,
            showTotal = showTotal,
        )
    }
}

@Composable
fun CurrentStatePageContent(
    draft: CurrentStateViewModel.Draft,
    validation: ValidationResult,
    status: SaveStatus,
    zone: ZoneId,
    now: () -> Instant,
    onChange: ((CurrentStateViewModel.Draft) -> CurrentStateViewModel.Draft) -> Unit,
    onChangeNow: ((CurrentStateViewModel.Draft) -> CurrentStateViewModel.Draft) -> Unit,
    onResetProgress: (clearHistory: Boolean) -> Unit,
    showTotal: Boolean = true,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val lastLabel = stringResource(R.string.last_check_in_field)

    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        if (showTotal) {
            IntStepperField(
                stringResource(R.string.current_total), draft.total, FieldRanges.TOTAL, ValueInput.WHOLE,
                onUpdate = { f -> onChange { it.copy(total = f(it.total)) } },
                onDialogUpdate = { f -> onChangeNow { it.copy(total = f(it.total)) } },
                error = validation.errors[Field.TOTAL], hint = validation.hints[Field.TOTAL],
                info = stringResource(R.string.info_total_reps),
            )
        }
        IntStepperField(
            stringResource(R.string.best_streak_field), draft.best, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(best = f(it.best)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(best = f(it.best)) } },
            error = validation.errors[Field.BEST_STREAK], info = stringResource(R.string.info_best_streak),
        )
        IntStepperField(
            stringResource(R.string.current_streak_field), draft.current, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(current = f(it.current)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(current = f(it.current)) } },
            error = validation.errors[Field.CURRENT_STREAK], info = stringResource(R.string.info_current_streak),
        )
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lastLabel, style = MaterialTheme.typography.labelLarge)
            InfoTag(lastLabel, stringResource(R.string.info_last_check_in))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tapping the date text opens the date and time pickers (spec R2 §8.1).
            Text(
                draft.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: stringResource(R.string.none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.edit_value, lastLabel)) { picking = true }
                    .padding(vertical = 12.dp)
                    .testTag("last_check_in"),
            )
            TextButton(
                onClick = { onChangeNow { it.copy(lastCheckIn = null) } },
                enabled = draft.lastCheckIn != null,
                modifier = Modifier.testTag("clear_last_check_in"),
            ) {
                Text(stringResource(R.string.clear))
            }
        }
        validation.errors[Field.LAST_CHECK_IN]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 24.dp).testTag("reset_progress"),
        ) { Text(stringResource(R.string.reset_progress)) }
    }

    if (picking) {
        DateTimePickerDialog(
            initial = draft.lastCheckIn ?: now(),
            zone = zone,
            onPicked = { t ->
                picking = false
                onChangeNow { it.copy(lastCheckIn = t) }
            },
            onDismiss = { picking = false },
        )
    }
    // Spec R3 §6.4: Reset progress keeps its confirmation and applies at once; the page stays open.
    if (confirmReset) {
        // Spec R6 §4.3: unchecked each time the dialog opens, kept across rotation while it's open (plan Spec note 24).
        var clearHistory by rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_progress_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.reset_progress_body))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(top = 8.dp)
                            .toggleable(value = clearHistory, role = Role.Checkbox, onValueChange = { clearHistory = it })
                            .testTag("clear_history"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = clearHistory, onCheckedChange = null)
                        Text(stringResource(R.string.clear_history_too), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetProgress(clearHistory)
                    },
                    modifier = Modifier.testTag("confirm_reset_progress"),
                ) { Text(stringResource(R.string.reset)) }
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
