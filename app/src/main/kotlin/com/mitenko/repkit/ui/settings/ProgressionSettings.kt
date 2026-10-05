package com.mitenko.repkit.ui.settings

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.R
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.Field
import com.mitenko.repkit.domain.FieldRanges
import com.mitenko.repkit.domain.HoldField
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.PenaltyDraft
import com.mitenko.repkit.domain.ProgressionField
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.ValidationResult
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.newHold
import com.mitenko.repkit.domain.resolveFor
import com.mitenko.repkit.ui.common.resolve
import com.mitenko.repkit.ui.common.AutoSaver
import com.mitenko.repkit.ui.common.EntryScopedViewModel
import com.mitenko.repkit.ui.common.IntStepperField
import com.mitenko.repkit.ui.common.MoveNote
import com.mitenko.repkit.ui.common.PenaltyStepperField
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.common.SaveStatusLine
import com.mitenko.repkit.ui.common.SettingsPageLayout
import com.mitenko.repkit.ui.common.SwitchRow
import com.mitenko.repkit.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Typed draft (spec R2 §8.1, R3 §5.3, rev 16 §6): one value per field, the penalty in integer
 * half-hours, the Hold switch and the list of holds (part of equality, so of the save key).
 */
data class ProgressionDraft(
    val startingTotal: Int,
    val floor: Int,
    val cap: Int,
    val holds: List<Hold>,
    val windowHours: Int,
    val penalty: PenaltyDraft,
    val hold: Boolean,
) {
    fun toConfig() = ProgressionConfig(
        startingTotal = startingTotal, floor = floor, cap = cap, holds = holds,
        windowHours = windowHours, penaltyHoursPerRep = penalty.hours, hold = hold,
    )

    /** Replaces the hold at [index] with [transform] of it. */
    fun updateHold(index: Int, transform: (Hold) -> Hold) =
        copy(holds = holds.mapIndexed { i, h -> if (i == index) transform(h) else h })

    /** "+ Add hold" (spec rev 16 §6); the page disables it at [ProgressionConfig.MAX_HOLDS]. */
    fun withNewHold() = copy(holds = holds + newHold(holds, floor, cap))

    /** ✕ on hold [index]. Removing the last one leaves an empty list; the switch is untouched. */
    fun withoutHold(index: Int) = copy(holds = holds.filterIndexed { i, _ -> i != index })

    /** Takes the reps fields from [c] (an override's result, spec revision 28); the penalty keeps its draft form. */
    fun withRange(c: ProgressionConfig) = copy(startingTotal = c.startingTotal, floor = c.floor, cap = c.cap)

    companion object {
        fun from(c: ProgressionConfig) = ProgressionDraft(
            c.startingTotal, c.floor, c.cap, c.holds, c.windowHours, PenaltyDraft.of(c.penaltyHoursPerRep), c.hold,
        )
    }
}

/**
 * What a Progression edit moved (spec revision 28), shown under [at]: the field the user edited,
 * or Reset to defaults when null. [moves] are the draft's own moves first, then a current total the
 * save moved.
 */
data class ProgressionNote(val at: ProgressionField?, val moves: List<Move>)

/**
 * A draft edit from the page. [field] is the field the user changed when it can push another
 * (spec revision 28), else null.
 */
typealias ProgressionEdit = (field: ProgressionField?, transform: (ProgressionDraft) -> ProgressionDraft) -> Unit

/**
 * The Progression page (spec R3 §5.3, §6). It uses the same draft and save pipeline as Timing. The
 * Hold switch and Reset to defaults save at once; setProgression keeps the hold count unless the
 * hold itself changes (§6.3). While the pager is open, a draft without unsaved edits follows the
 * stored progression (spec revision 27). An edit that conflicts with another field wins: the other
 * values move to fit, and [note] says what moved (spec revision 28).
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

    private val _note = MutableStateFlow<ProgressionNote?>(null)

    /**
     * Spec revision 28: what the last edit moved, until the next edit or a page change
     * ([clearNote]). A current total moved by the save is added when the save lands.
     */
    val note: StateFlow<ProgressionNote?> = _note.asStateFlow()

    /**
     * Advanced by [clearNote]. Each save carries the value from when it was queued, and only adds to
     * the note if this hasn't moved since: a page change's flush-then-clear drops it even when the
     * write itself starts later.
     */
    private var noteGeneration = 0L

    /** A queued progression write and the note generation it was queued in. */
    private data class Save(val config: ProgressionConfig, val generation: Long)

    private fun save(config: ProgressionConfig) = Save(config, noteGeneration)

    /** Where a total moved by a save is noted: the last edited field that can push another, or null after Reset. */
    private var noteAnchor: ProgressionField? = null

    private val saver = AutoSaver<Save>(viewModelScope) { (config, generation) ->
        try {
            val moved = repo.setProgression(entryId, config)
            if (moved != null && noteGeneration == generation) {
                _note.value = ProgressionNote(noteAnchor, _note.value?.moves.orEmpty() + moved)
            }
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Progression $config rejected", e)
            failed.value = true
        }
    }

    /** The progression as last stored. A draft whose config equals it, with no save pending, has no unsaved edits. */
    private var stored: ProgressionConfig? = null

    init {
        val restored = _draft.value
        // A valid draft restored after process death may never have been written; the write is idempotent if it was.
        if (restored != null && SettingsValidator.progression(restored.toConfig()).isValid) saver.schedule(save(restored.toConfig()))
        viewModelScope.launch {
            // Spec revision 27: the pager keeps this page alive while the Current page can widen
            // floor..cap, so a draft without unsaved edits follows the store. Compared as configs,
            // so the penalty's draft form can't make a clean draft look edited.
            repo.entry(entryId).filterNotNull().collect { e ->
                val latest = e.progression
                val current = _draft.value
                if (current == null || (current.toConfig() == stored && !saver.hasPending)) setDraft(ProgressionDraft.from(latest))
                stored = latest
            }
        }
    }

    /** A stepper change: saved 400 ms after the last one. */
    fun update(transform: (ProgressionDraft) -> ProgressionDraft) = edit(null, transform, now = false)

    /** A stepper change to [field]; the values it conflicts with move to fit (spec revision 28). */
    fun update(field: ProgressionField?, transform: (ProgressionDraft) -> ProgressionDraft) = edit(field, transform, now = false)

    /** A dialog OK or the Hold switch: saved at once. */
    fun updateNow(transform: (ProgressionDraft) -> ProgressionDraft) = edit(null, transform, now = true)

    /** A dialog OK on [field]: saved at once, with the same override as [update]. */
    fun updateNow(field: ProgressionField?, transform: (ProgressionDraft) -> ProgressionDraft) = edit(field, transform, now = true)

    /** "+ Add hold": saved at once (spec rev 16 §6). */
    fun addHold() = updateNow { it.withNewHold() }

    /** ✕ on hold [index]: saved at once (spec rev 16 §6). */
    fun removeHold(index: Int) = updateNow { it.withoutHold(index) }

    /** After the confirmation (spec R3 §6.4): the draft becomes the defaults and saves at once. */
    fun resetToDefaults() {
        noteAnchor = null
        updateNow { ProgressionDraft.from(ProgressionConfig()) }
    }

    fun flush() = saver.flush()

    /** The next edit or a page change hides the note (spec revision 28). */
    fun clearNote() {
        noteGeneration++
        _note.value = null
    }

    override fun onCleared() {
        saver.flushIn(appScope)
    }

    private fun edit(field: ProgressionField?, transform: (ProgressionDraft) -> ProgressionDraft, now: Boolean) {
        val raw = _draft.value?.let(transform) ?: return
        // Spec revision 28: the edited field wins, and the draft shows the moved values at once.
        val resolution = raw.toConfig().resolveFor(field)
        val d = if (resolution.moves.isEmpty()) raw else raw.withRange(resolution.config)
        clearNote()
        if (field != null) noteAnchor = field
        if (resolution.moves.isNotEmpty()) _note.value = ProgressionNote(field, resolution.moves)
        setDraft(d)
        val config = d.toConfig()
        when {
            !SettingsValidator.progression(config).isValid -> saver.cancel()
            now -> saver.saveNow(save(config))
            else -> saver.schedule(save(config))
        }
    }

    private fun setDraft(d: ProgressionDraft) {
        _draft.value = d
        savedStateHandle[DRAFT_KEY] = intArrayOf(
            d.startingTotal, d.floor, d.cap, d.windowHours, d.penalty.halfHours, if (d.hold) 1 else 0,
        )
        savedStateHandle[HOLDS_KEY] = d.holds.flatMap { listOf(it.at, it.forCount) }.toIntArray()
        savedStateHandle[EXACT_KEY] = d.penalty.exact
    }

    private companion object {
        const val TAG = "ProgressionSettings"
        const val DRAFT_KEY = "progression_draft"
        const val EXACT_KEY = "progression_penalty_exact"
        const val HOLDS_KEY = "progression_holds"

        fun SavedStateHandle.restoredDraft(): ProgressionDraft? {
            val a = get<IntArray>(DRAFT_KEY) ?: return null
            val holds = get<IntArray>(HOLDS_KEY)?.toList()?.chunked(2) { (at, forCount) -> Hold(at, forCount) } ?: return null
            return ProgressionDraft(a[0], a[1], a[2], holds, a[3], PenaltyDraft(a[4], get<Double>(EXACT_KEY)), a[5] == 1)
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
    val note by vm.note.collectAsStateWithLifecycle()
    draft?.let {
        ProgressionPageContent(
            it, validation, status,
            onChange = { field, transform -> vm.update(field, transform) },
            onChangeNow = { field, transform -> vm.updateNow(field, transform) },
            onReset = vm::resetToDefaults,
            windowOnly = windowOnly, note = note,
        )
    }
}

@Composable
fun ProgressionPageContent(
    draft: ProgressionDraft,
    validation: ValidationResult,
    status: SaveStatus,
    onChange: ProgressionEdit,
    onChangeNow: ProgressionEdit,
    onReset: () -> Unit,
    windowOnly: Boolean = false,
    note: ProgressionNote? = null,
) {
    val errors = validation.errors
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        if (!windowOnly) {
            IntStepperField(
                stringResource(R.string.starting_total), draft.startingTotal, FieldRanges.REPS, ValueInput.WHOLE,
                onUpdate = { f -> onChange(ProgressionField.STARTING_TOTAL) { it.copy(startingTotal = f(it.startingTotal)) } },
                onDialogUpdate = { f -> onChangeNow(ProgressionField.STARTING_TOTAL) { it.copy(startingTotal = f(it.startingTotal)) } },
                error = errors[Field.STARTING_TOTAL].resolve(), info = stringResource(R.string.info_starting_total),
            )
            NoteUnder(note, ProgressionField.STARTING_TOTAL)
            IntStepperField(
                stringResource(R.string.floor), draft.floor, FieldRanges.REPS, ValueInput.WHOLE,
                onUpdate = { f -> onChange(ProgressionField.FLOOR) { it.copy(floor = f(it.floor)) } },
                onDialogUpdate = { f -> onChangeNow(ProgressionField.FLOOR) { it.copy(floor = f(it.floor)) } },
                error = errors[Field.FLOOR].resolve(), info = stringResource(R.string.info_floor),
            )
            NoteUnder(note, ProgressionField.FLOOR)
            IntStepperField(
                stringResource(R.string.cap), draft.cap, FieldRanges.REPS, ValueInput.WHOLE,
                onUpdate = { f -> onChange(ProgressionField.CAP) { it.copy(cap = f(it.cap)) } },
                onDialogUpdate = { f -> onChangeNow(ProgressionField.CAP) { it.copy(cap = f(it.cap)) } },
                error = errors[Field.CAP].resolve(), info = stringResource(R.string.info_cap),
            )
            NoteUnder(note, ProgressionField.CAP)
            // Spec R3 §5.3, rev 16 §6: the switch sits directly above the holds; off hides the list but keeps its values.
            SwitchRow(
                stringResource(R.string.hold), draft.hold,
                onChange = { on -> onChangeNow(null) { it.copy(hold = on) } }, info = stringResource(R.string.info_hold),
            )
            AnimatedVisibility(visible = draft.hold) {
                Column {
                    draft.holds.forEachIndexed { i, hold ->
                        val holdErrors = validation.holdErrors[i].orEmpty()
                        HoldHeader(number = i + 1, onRemove = { onChangeNow(null) { it.withoutHold(i) } })
                        IntStepperField(
                            stringResource(R.string.hold_at), hold.at, FieldRanges.REPS, ValueInput.WHOLE,
                            onUpdate = { f -> onChange(null) { it.updateHold(i) { h -> h.copy(at = f(h.at)) } } },
                            onDialogUpdate = { f -> onChangeNow(null) { it.updateHold(i) { h -> h.copy(at = f(h.at)) } } },
                            error = holdErrors[HoldField.AT].resolve(), hint = validation.holdHints[i].resolve(), info = stringResource(R.string.info_hold_at),
                            a11yLabel = stringResource(R.string.hold_n_at, i + 1),
                        )
                        IntStepperField(
                            stringResource(R.string.hold_for), hold.forCount, FieldRanges.HOLD_FOR, ValueInput.WHOLE,
                            onUpdate = { f -> onChange(null) { it.updateHold(i) { h -> h.copy(forCount = f(h.forCount)) } } },
                            onDialogUpdate = { f -> onChangeNow(null) { it.updateHold(i) { h -> h.copy(forCount = f(h.forCount)) } } },
                            error = holdErrors[HoldField.FOR].resolve(), info = stringResource(R.string.info_hold_for),
                            a11yLabel = stringResource(R.string.hold_n_for, i + 1),
                        )
                    }
                    AddHoldButton(
                        enabled = draft.holds.size < ProgressionConfig.MAX_HOLDS,
                        onClick = { onChangeNow(null) { it.withNewHold() } },
                    )
                }
            }
        }
        IntStepperField(
            stringResource(R.string.window_hours), draft.windowHours, FieldRanges.WINDOW_HOURS, ValueInput.WHOLE,
            onUpdate = { f -> onChange(null) { it.copy(windowHours = f(it.windowHours)) } },
            onDialogUpdate = { f -> onChangeNow(null) { it.copy(windowHours = f(it.windowHours)) } },
            error = errors[Field.WINDOW_HOURS].resolve(), info = stringResource(R.string.info_window),
        )
        if (!windowOnly) {
            PenaltyStepperField(
                stringResource(R.string.penalty_rate), draft.penalty,
                onUpdate = { f -> onChange(null) { it.copy(penalty = f(it.penalty)) } },
                onDialogUpdate = { f -> onChangeNow(null) { it.copy(penalty = f(it.penalty)) } },
                error = errors[Field.PENALTY_RATE].resolve(), info = stringResource(R.string.info_penalty_rate),
            )
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 16.dp).testTag("reset_defaults")) {
                Text(stringResource(R.string.reset_defaults))
            }
            NoteUnder(note, null)
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

/** "Hold N" with its 48 dp ✕ (spec rev 16 §6). */
@Composable
private fun HoldHeader(number: Int, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.hold_n, number),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.remove_hold, number))
        }
    }
}

/** "+ Add hold", at least 48 dp tall (spec rev 16 §6). */
@Composable
private fun AddHoldButton(enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 48.dp).testTag("add_hold"),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.add_hold), modifier = Modifier.padding(start = 8.dp))
    }
}

/** The note under the field [at], if the last edit was there (spec revision 28). */
@Composable
private fun NoteUnder(note: ProgressionNote?, at: ProgressionField?) {
    if (note != null && note.at == at) MoveNote(note.moves, tag = "progression_note")
}
