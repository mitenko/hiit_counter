package com.mitenko.hiitcounter.ui.settings

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.di.ApplicationScope
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.PenaltyDraft
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.AutoSaver
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.PenaltyStepperField
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.common.SaveStatusLine
import com.mitenko.hiitcounter.ui.common.SettingsPageLayout
import com.mitenko.hiitcounter.ui.common.SwitchRow
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

/** Typed draft (spec R2 §8.1, R3 §5.3): one value per field, the penalty in integer half-hours, and the Hold switch. */
data class ProgressionDraft(
    val startingTotal: Int,
    val floor: Int,
    val cap: Int,
    val holdAt: Int,
    val holdFor: Int,
    val windowHours: Int,
    val penalty: PenaltyDraft,
    val hold: Boolean,
) {
    fun toConfig() = ProgressionConfig(startingTotal, floor, cap, holdAt, holdFor, windowHours, penalty.hours, hold)

    companion object {
        fun from(c: ProgressionConfig) = ProgressionDraft(
            c.startingTotal, c.floor, c.cap, c.holdAt, c.holdFor, c.windowHours, PenaltyDraft.of(c.penaltyHoursPerRep), c.hold,
        )
    }
}

/**
 * The Progression page (spec R3 §5.3, §6). It uses the same draft and save pipeline as Timing. The
 * Hold switch and Reset to defaults save at once; setProgression keeps the hold count unless the
 * hold itself changes (§6.3).
 */
@HiltViewModel
class ProgressionSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _draft = MutableStateFlow(savedStateHandle.restoredDraft())
    val draft: StateFlow<ProgressionDraft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { d -> d?.let { SettingsValidator.progression(it.toConfig()) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    private val saver = AutoSaver<ProgressionConfig>(viewModelScope) { config ->
        try {
            repo.setProgression(entryId, config)
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Progression $config rejected", e)
            failed.value = true
        }
    }

    init {
        val restored = _draft.value
        when {
            restored == null ->
                viewModelScope.launch { repo.entry(entryId).first()?.let { setDraft(ProgressionDraft.from(it.progression)) } }
            // A valid draft restored after process death may never have been written; the write is idempotent if it was.
            SettingsValidator.progression(restored.toConfig()).isValid -> saver.schedule(restored.toConfig())
        }
    }

    /** A stepper change: saved 400 ms after the last one. */
    fun update(transform: (ProgressionDraft) -> ProgressionDraft) = edit(transform, now = false)

    /** A dialog OK or the Hold switch: saved at once. */
    fun updateNow(transform: (ProgressionDraft) -> ProgressionDraft) = edit(transform, now = true)

    /** After the confirmation (spec R3 §6.4): the draft becomes the defaults and saves at once. */
    fun resetToDefaults() = updateNow { ProgressionDraft.from(ProgressionConfig()) }

    fun flush() = saver.flush()

    override fun onCleared() {
        saver.flushIn(appScope)
    }

    private fun edit(transform: (ProgressionDraft) -> ProgressionDraft, now: Boolean) {
        val d = _draft.value?.let(transform) ?: return
        setDraft(d)
        val config = d.toConfig()
        when {
            !SettingsValidator.progression(config).isValid -> saver.cancel()
            now -> saver.saveNow(config)
            else -> saver.schedule(config)
        }
    }

    private fun setDraft(d: ProgressionDraft) {
        _draft.value = d
        savedStateHandle[DRAFT_KEY] = intArrayOf(
            d.startingTotal, d.floor, d.cap, d.holdAt, d.holdFor, d.windowHours, d.penalty.halfHours, if (d.hold) 1 else 0,
        )
        savedStateHandle[EXACT_KEY] = d.penalty.exact
    }

    private companion object {
        const val TAG = "ProgressionSettings"
        const val DRAFT_KEY = "progression_draft"
        const val EXACT_KEY = "progression_penalty_exact"

        fun SavedStateHandle.restoredDraft(): ProgressionDraft? {
            val a = get<IntArray>(DRAFT_KEY) ?: return null
            return ProgressionDraft(a[0], a[1], a[2], a[3], a[4], a[5], PenaltyDraft(a[6], get<Double>(EXACT_KEY)), a[7] == 1)
        }
    }
}

/**
 * The Progression page inside the pager (spec R3 §4). [windowOnly] is a Timer only entry
 * (R4 §4.6): only the check-in window shows. The hidden fields keep their stored values and stay in
 * the draft that is validated and saved. Reset to defaults is hidden too, since it would reset them
 * (plan Spec note 7).
 */
@Composable
fun ProgressionPage(vm: ProgressionSettingsViewModel, windowOnly: Boolean = false) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    draft?.let {
        ProgressionPageContent(
            it, validation, status, onChange = vm::update, onChangeNow = vm::updateNow, onReset = vm::resetToDefaults,
            windowOnly = windowOnly,
        )
    }
}

@Composable
fun ProgressionPageContent(
    draft: ProgressionDraft,
    validation: ValidationResult,
    status: SaveStatus,
    onChange: ((ProgressionDraft) -> ProgressionDraft) -> Unit,
    onChangeNow: ((ProgressionDraft) -> ProgressionDraft) -> Unit,
    onReset: () -> Unit,
    windowOnly: Boolean = false,
) {
    val errors = validation.errors
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        if (!windowOnly) {
            IntStepperField(
                stringResource(R.string.starting_total), draft.startingTotal, FieldRanges.REPS, ValueInput.WHOLE,
                onUpdate = { f -> onChange { it.copy(startingTotal = f(it.startingTotal)) } },
                onDialogUpdate = { f -> onChangeNow { it.copy(startingTotal = f(it.startingTotal)) } },
                error = errors[Field.STARTING_TOTAL], info = stringResource(R.string.info_starting_total),
            )
            IntStepperField(
                stringResource(R.string.floor), draft.floor, FieldRanges.REPS, ValueInput.WHOLE,
                onUpdate = { f -> onChange { it.copy(floor = f(it.floor)) } },
                onDialogUpdate = { f -> onChangeNow { it.copy(floor = f(it.floor)) } },
                error = errors[Field.FLOOR], info = stringResource(R.string.info_floor),
            )
            IntStepperField(
                stringResource(R.string.cap), draft.cap, FieldRanges.REPS, ValueInput.WHOLE,
                onUpdate = { f -> onChange { it.copy(cap = f(it.cap)) } },
                onDialogUpdate = { f -> onChangeNow { it.copy(cap = f(it.cap)) } },
                error = errors[Field.CAP], info = stringResource(R.string.info_cap),
            )
            // Spec R3 §5.3: the switch sits directly above Hold at; off hides both rows but keeps their values.
            SwitchRow(
                stringResource(R.string.hold), draft.hold,
                onChange = { on -> onChangeNow { it.copy(hold = on) } }, info = stringResource(R.string.info_hold),
            )
            AnimatedVisibility(visible = draft.hold) {
                Column {
                    IntStepperField(
                        stringResource(R.string.hold_at), draft.holdAt, FieldRanges.REPS, ValueInput.WHOLE,
                        onUpdate = { f -> onChange { it.copy(holdAt = f(it.holdAt)) } },
                        onDialogUpdate = { f -> onChangeNow { it.copy(holdAt = f(it.holdAt)) } },
                        error = errors[Field.HOLD_AT], hint = validation.hints[Field.HOLD_AT], info = stringResource(R.string.info_hold_at),
                    )
                    IntStepperField(
                        stringResource(R.string.hold_for), draft.holdFor, FieldRanges.HOLD_FOR, ValueInput.WHOLE,
                        onUpdate = { f -> onChange { it.copy(holdFor = f(it.holdFor)) } },
                        onDialogUpdate = { f -> onChangeNow { it.copy(holdFor = f(it.holdFor)) } },
                        error = errors[Field.HOLD_FOR], info = stringResource(R.string.info_hold_for),
                    )
                }
            }
        }
        IntStepperField(
            stringResource(R.string.window_hours), draft.windowHours, FieldRanges.WINDOW_HOURS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(windowHours = f(it.windowHours)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(windowHours = f(it.windowHours)) } },
            error = errors[Field.WINDOW_HOURS], info = stringResource(R.string.info_window),
        )
        if (!windowOnly) {
            PenaltyStepperField(
                stringResource(R.string.penalty_rate), draft.penalty,
                onUpdate = { f -> onChange { it.copy(penalty = f(it.penalty)) } },
                onDialogUpdate = { f -> onChangeNow { it.copy(penalty = f(it.penalty)) } },
                error = errors[Field.PENALTY_RATE], info = stringResource(R.string.info_penalty_rate),
            )
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 16.dp).testTag("reset_defaults")) {
                Text(stringResource(R.string.reset_defaults))
            }
        }
    }
    // Spec R3 §6.4: confirmed, then applied at once.
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_defaults_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    },
                    modifier = Modifier.testTag("confirm_reset_defaults"),
                ) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
