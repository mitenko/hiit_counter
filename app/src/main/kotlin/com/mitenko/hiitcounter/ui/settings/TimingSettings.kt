package com.mitenko.hiitcounter.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.di.ApplicationScope
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.ui.common.AutoSaver
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Timing page (spec R3 §6). The typed draft is mirrored into the SavedStateHandle, so it
 * survives swipes, rotation and process recreation. Valid drafts auto-save through an
 * [AutoSaver]: a stepper change 400 ms after the last one, a dialog OK at once. Invalid drafts are
 * never saved.
 */
@HiltViewModel
class TimingSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _draft = MutableStateFlow(savedStateHandle.get<IntArray>(DRAFT_KEY)?.toTiming())
    val draft: StateFlow<TimingConfig?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(SettingsValidator::timing) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    private val saver = AutoSaver<TimingConfig>(viewModelScope) { timing ->
        try {
            repo.setTiming(entryId, timing)
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            // A valid draft can't be rejected; if it is, log it and say "Not saved" (spec R3 §6.2).
            Log.e(TAG, "Timing $timing rejected", e)
            failed.value = true
        }
    }

    init {
        val restored = _draft.value
        when {
            restored == null -> viewModelScope.launch { repo.entry(entryId).first()?.let { setDraft(it.timing) } }
            // A valid draft restored after process death may never have been written; the write is idempotent if it was.
            SettingsValidator.timing(restored).isValid -> saver.schedule(restored)
        }
    }

    /** A stepper change: saved 400 ms after the last one, so hold-to-repeat writes once. */
    fun update(transform: (TimingConfig) -> TimingConfig) = edit(transform, now = false)

    /** A dialog OK: saved at once, cancelling any pending debounce. */
    fun updateNow(transform: (TimingConfig) -> TimingConfig) = edit(transform, now = true)

    /** Writes a pending stepper change now (page change, leaving the pager, ON_STOP). */
    fun flush() = saver.flush()

    /** A change still pending is written in the application scope, so it outlives this ViewModel. */
    override fun onCleared() {
        saver.flushIn(appScope)
    }

    private fun edit(transform: (TimingConfig) -> TimingConfig, now: Boolean) {
        val d = _draft.value?.let(transform) ?: return
        setDraft(d)
        when {
            !SettingsValidator.timing(d).isValid -> saver.cancel()
            now -> saver.saveNow(d)
            else -> saver.schedule(d)
        }
    }

    private fun setDraft(d: TimingConfig) {
        _draft.value = d
        savedStateHandle[DRAFT_KEY] = intArrayOf(d.prepareSec, d.sets, d.workSec, d.restSec, d.cooldownSec)
    }

    private companion object {
        const val TAG = "TimingSettings"
        const val DRAFT_KEY = "timing_draft"

        fun IntArray.toTiming() = TimingConfig(this[0], this[1], this[2], this[3], this[4])
    }
}

/** The Timing page inside the pager (spec R3 §4). */
@Composable
fun TimingPage(vm: TimingSettingsViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    draft?.let { TimingPageContent(it, validation, status, onChange = vm::update, onChangeNow = vm::updateNow) }
}

@Composable
fun TimingPageContent(
    draft: TimingConfig,
    validation: ValidationResult,
    status: SaveStatus,
    onChange: ((TimingConfig) -> TimingConfig) -> Unit,
    onChangeNow: ((TimingConfig) -> TimingConfig) -> Unit,
) {
    val errors = validation.errors
    val totalError = errors[Field.TOTAL_DURATION]
    SettingsPageLayout(
        footer = {
            Column {
                SaveStatusLine(status)
                // The TOTAL footer, red above 2:00:00 (spec R2 §8.1, kept by R3 §6.2).
                Surface(color = if (totalError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.total_duration, TimerText.formatDuration(draft.totalDurationSec)),
                            modifier = Modifier.weight(1f).padding(vertical = 16.dp).testTag("total"),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        InfoTag(stringResource(R.string.total_label), stringResource(R.string.info_total))
                    }
                }
            }
        },
    ) {
        IntStepperField(
            stringResource(R.string.prepare), draft.prepareSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(prepareSec = f(it.prepareSec)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(prepareSec = f(it.prepareSec)) } },
            error = errors[Field.PREPARE], info = stringResource(R.string.info_prepare),
        )
        IntStepperField(
            stringResource(R.string.sets), draft.sets, FieldRanges.SETS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(sets = f(it.sets)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(sets = f(it.sets)) } },
            error = errors[Field.SETS], info = stringResource(R.string.info_sets),
        )
        IntStepperField(
            stringResource(R.string.work), draft.workSec, FieldRanges.WORK, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(workSec = f(it.workSec)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(workSec = f(it.workSec)) } },
            error = errors[Field.WORK], info = stringResource(R.string.info_work),
        )
        IntStepperField(
            stringResource(R.string.rest), draft.restSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(restSec = f(it.restSec)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(restSec = f(it.restSec)) } },
            error = errors[Field.REST], info = stringResource(R.string.info_rest),
        )
        IntStepperField(
            stringResource(R.string.cooldown), draft.cooldownSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(cooldownSec = f(it.cooldownSec)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(cooldownSec = f(it.cooldownSec)) } },
            error = errors[Field.COOLDOWN], info = stringResource(R.string.info_cooldown),
        )
        totalError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}
