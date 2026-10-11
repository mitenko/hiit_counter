package com.mitenko.repkit.ui.settings

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
import com.mitenko.repkit.R
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.Field
import com.mitenko.repkit.domain.FieldRanges
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.ProgressionScale
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.StepRange
import com.mitenko.repkit.domain.StreakField
import com.mitenko.repkit.domain.ValidationResult
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.resolveStreaks
import com.mitenko.repkit.domain.scale
import com.mitenko.repkit.domain.stepAlong
import com.mitenko.repkit.ui.common.resolve
import com.mitenko.repkit.ui.common.AutoSaver
import com.mitenko.repkit.ui.common.DateFormats
import com.mitenko.repkit.ui.common.EntryScopedViewModel
import com.mitenko.repkit.ui.common.InfoTag
import com.mitenko.repkit.ui.common.IntStepperField
import com.mitenko.repkit.ui.common.MoveNote
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.common.SaveStatusLine
import com.mitenko.repkit.ui.common.SettingsPageLayout
import com.mitenko.repkit.ui.common.ValueInput
import com.mitenko.repkit.ui.common.ValueRow
import com.mitenko.repkit.ui.common.WeightPickerField
import com.mitenko.repkit.ui.common.unitLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * The Current page (spec R3 §6). It uses the same draft and save pipeline as Timing. overwriteCounter
 * keeps the hold count unless the total changes (§6.3). While the pager is open, a draft without
 * unsaved edits follows the stored counter (plan Spec note 2). A saved total outside floor..cap
 * widens the range, and [rangeNote] says which limit moved (spec revision 27). A current streak
 * edited above the best streak raises the best streak, and [streakNote] says so (spec revision 28).
 * Best streak itself is read-only (spec revision 29): the app keeps it current, and only a current
 * streak edit (via [update]/[updateNow] with [StreakField.CURRENT]) can raise it. In a weight mode
 * the total is a level on [ladder] (spec rev 26 §3, plan Spec note 35).
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

    /** The ladder in a weight mode (spec rev 26 §3 Current tab, plan Spec note 35): the draft's total is a level on [scale]. */
    data class LadderView(val scale: ProgressionScale.Ladder, val unit: WeightUnit?)

    private val _draft = MutableStateFlow(savedStateHandle.get<LongArray>(DRAFT_KEY)?.toDraft())
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    private val _ladder = MutableStateFlow<LadderView?>(null)

    /** Null in Reps mode, and until the entry has loaded. */
    val ladder: StateFlow<LadderView?> = _ladder.asStateFlow()

    val validation: StateFlow<ValidationResult> = combine(_draft, _ladder) { d, l -> d?.let { validate(it, l) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    private val _rangeNote = MutableStateFlow<RangeChange?>(null)

    private val _streakNote = MutableStateFlow<List<Move>>(emptyList())

    /**
     * Spec revision 28 rule 5: what the last edit moved (the best streak), shown under Current
     * streak until the next edit or a page change ([clearStreakNote]). It comes from the draft,
     * not a save, so no late write can bring it back.
     */
    val streakNote: StateFlow<List<Move>> = _streakNote.asStateFlow()

    /**
     * Spec revision 27: the limit the last save moved, shown under Current reps until the total is
     * edited again or the page is left ([clearRangeNote]).
     */
    val rangeNote: StateFlow<RangeChange?> = _rangeNote.asStateFlow()

    /**
     * Advanced by [clearRangeNote] (a total edit or a page change). A write only sets the note if
     * this hasn't moved since it started, so a save finishing late never brings back a dismissed note.
     */
    private var noteGeneration = 0L

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    /** The counter as last stored. A draft equal to it, with no save pending, has no unsaved edits. */
    private var stored: Draft? = null

    private val saver = AutoSaver<Draft>(viewModelScope) { d ->
        try {
            val generation = noteGeneration
            val moved = repo.overwriteCounter(entryId, d.total, d.best, d.current, d.lastCheckIn)
            if (moved != null && noteGeneration == generation) _rangeNote.value = moved
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
        // Checked by the Reps rules here, so it saves even before the entry loads (as before).
        // This schedule's debounce window is almost always cancelled by the store's first emission below
        // (lines ~184-191); even on the rare race where it fires first, a draft invalid under the real
        // ladder is rejected by Room's own validation, so no bad value can land either way.
        val restoredRaw = _draft.value
        val restored = restoredRaw?.takeIf { validate(it, ladder = null).isValid }
        restored?.let(saver::schedule)
        viewModelScope.launch {
            repo.entry(entryId).filterNotNull().collect { e ->
                val ladder = (e.progression.scale() as? ProgressionScale.Ladder)?.let { LadderView(it, e.progression.weight.unit) }
                _ladder.value = ladder
                val latest = e.counter.toDraft()
                val current = _draft.value
                if (stored == null) {
                    // A restored draft already matching the store needs no write (Minor 3); one that differs still saves.
                    if (restoredRaw == latest) {
                        saver.cancel()
                    } else if (ladder != null && restoredRaw != null) {
                        // Plan Spec note 35: in a weight mode its validity depends on the ladder (a level can be 0).
                        if (validate(restoredRaw).isValid) saver.schedule(restoredRaw) else saver.cancel()
                    }
                }
                // Follow the store only without unsaved edits. An edit back to the stored value
                // whose save is still pending counts as unsaved, so an echo can't overwrite it.
                if (current == null || (current == stored && !saver.hasPending)) setDraft(latest)
                stored = latest
            }
        }
    }

    /** A stepper change: saved 400 ms after the last one. */
    fun update(transform: (Draft) -> Draft) = edit(null, transform, now = false)

    /** A stepper change to a streak [field]; a current streak above the best raises the best (spec revision 28). */
    fun update(field: StreakField?, transform: (Draft) -> Draft) = edit(field, transform, now = false)

    /** A dialog OK, a date or time pick, or Clear: saved at once. */
    fun updateNow(transform: (Draft) -> Draft) = edit(null, transform, now = true)

    /** A dialog OK on a streak [field]: saved at once, with the same override as [update]. */
    fun updateNow(field: StreakField?, transform: (Draft) -> Draft) = edit(field, transform, now = true)

    fun flush() = saver.flush()

    /** Leaving the page hides the note (spec revision 27). */
    fun clearRangeNote() {
        noteGeneration++
        _rangeNote.value = null
    }

    /** Leaving the page hides the best-streak note (spec revision 28). */
    fun clearStreakNote() {
        _streakNote.value = emptyList()
    }

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

    private fun edit(field: StreakField?, transform: (Draft) -> Draft, now: Boolean) {
        val before = _draft.value ?: return
        val raw = transform(before)
        // Spec revision 28 rule 5: the edited streak wins; the draft shows the raised best at once.
        val streaks = resolveStreaks(raw.best, raw.current, field)
        val d = raw.copy(best = streaks.best, current = streaks.current)
        if (d.total != before.total) clearRangeNote()
        _streakNote.value = streaks.moves
        setDraft(d)
        when {
            !validate(d).isValid -> saver.cancel()
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

    private fun validate(d: Draft, ladder: LadderView? = _ladder.value): ValidationResult =
        SettingsValidator.currentState(
            d.total, d.best, d.current, d.lastCheckIn, clock.now(),
            levels = ladder?.scale?.let { it.minLevel..it.maxLevel },
        )

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
    val rangeNote by vm.rangeNote.collectAsStateWithLifecycle()
    val streakNote by vm.streakNote.collectAsStateWithLifecycle()
    val ladder by vm.ladder.collectAsStateWithLifecycle()
    draft?.let {
        CurrentStatePageContent(
            it, validation, status, vm.zone, vm::now,
            onChange = { field, transform -> vm.update(field, transform) },
            onChangeNow = { field, transform -> vm.updateNow(field, transform) },
            onResetProgress = vm::resetProgress,
            showTotal = showTotal, rangeNote = rangeNote, streakNote = streakNote, ladder = ladder,
        )
    }
}

/** A draft edit from the Current page; [field] is the streak the user changed, else null (spec revision 28). */
typealias CurrentEdit = (field: StreakField?, transform: (CurrentStateViewModel.Draft) -> CurrentStateViewModel.Draft) -> Unit

@Composable
fun CurrentStatePageContent(
    draft: CurrentStateViewModel.Draft,
    validation: ValidationResult,
    status: SaveStatus,
    zone: ZoneId,
    now: () -> Instant,
    onChange: CurrentEdit,
    onChangeNow: CurrentEdit,
    onResetProgress: (clearHistory: Boolean) -> Unit,
    showTotal: Boolean = true,
    rangeNote: RangeChange? = null,
    streakNote: List<Move> = emptyList(),
    ladder: CurrentStateViewModel.LadderView? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val lastLabel = stringResource(R.string.last_check_in_field)

    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        if (showTotal) {
            if (ladder == null) {
                IntStepperField(
                    stringResource(R.string.current_total), draft.total, FieldRanges.TOTAL, ValueInput.WHOLE,
                    onUpdate = { f -> onChange(null) { it.copy(total = f(it.total)) } },
                    onDialogUpdate = { f -> onChangeNow(null) { it.copy(total = f(it.total)) } },
                    error = validation.errors[Field.TOTAL].resolve(), info = stringResource(R.string.info_total_reps),
                )
                // Spec revision 27: which limit the save moved, announced politely to TalkBack.
                rangeNote?.let { MoveNote(listOf(it), tag = "range_note") }
            } else {
                CurrentLoadRows(draft, ladder, validation, onChange, onChangeNow)
            }
        }
        // Best streak is read-only (spec revision 29): the app keeps it current on its own.
        ValueRow(
            stringResource(R.string.best_streak_field), draft.best.toString(),
            info = stringResource(R.string.info_best_streak),
        )
        IntStepperField(
            stringResource(R.string.current_streak_field), draft.current, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> onChange(StreakField.CURRENT) { it.copy(current = f(it.current)) } },
            onDialogUpdate = { f -> onChangeNow(StreakField.CURRENT) { it.copy(current = f(it.current)) } },
            error = validation.errors[Field.CURRENT_STREAK].resolve(), info = stringResource(R.string.info_current_streak),
        )
        // Spec revision 28 rule 5: the best streak this edit raised.
        if (streakNote.isNotEmpty()) MoveNote(streakNote, tag = "streak_note")
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
                onClick = { onChangeNow(null) { it.copy(lastCheckIn = null) } },
                enabled = draft.lastCheckIn != null,
                modifier = Modifier.testTag("clear_last_check_in"),
            ) {
                Text(stringResource(R.string.clear))
            }
        }
        validation.errors[Field.LAST_CHECK_IN].resolve()?.let {
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
                onChangeNow(null) { it.copy(lastCheckIn = t) }
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
                    // A Timer only entry (showTotal false) never shows a total or a load, even when it's
                    // stuck in a weight mode from before it switched type, so it gets the normal wording.
                    Text(stringResource(if (ladder != null && showTotal) R.string.reset_progress_body_weight else R.string.reset_progress_body))
                    ClearHistoryRow(clearHistory, onChange = { clearHistory = it })
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

/**
 * The current load in a weight mode (spec rev 26 §3 Current tab, plan Spec note 35): Current weight
 * moves along the ladder keeping the reps, and in Reps then weight Current reps per set moves within
 * the rep range keeping the weight. Each edit writes the level; nothing widens (§10 note 20).
 */
@Composable
private fun CurrentLoadRows(
    draft: CurrentStateViewModel.Draft,
    ladder: CurrentStateViewModel.LadderView,
    validation: ValidationResult,
    onChange: CurrentEdit,
    onChangeNow: CurrentEdit,
) {
    val scale = ladder.scale
    val load = scale.prescription(draft.total)
    WeightPickerField(
        unitLabel(R.string.current_weight, ladder.unit), scale.weights, load.weight, ladder.unit,
        onStep = { up ->
            onChange(null) { d ->
                val p = scale.prescription(d.total)
                d.copy(total = scale.levelOf(stepAlong(scale.weights, p.weight, up) ?: p.weight, p.reps))
            }
        },
        onPick = { w -> onChangeNow(null) { d -> d.copy(total = scale.levelOf(w, scale.prescription(d.total).reps)) } },
        error = validation.errors[Field.TOTAL].resolve(), info = stringResource(R.string.info_current_weight),
        a11yLabel = stringResource(R.string.current_weight),
    )
    if (scale is ProgressionScale.RepsThenWeight) {
        IntStepperField(
            stringResource(R.string.current_reps_per_set), load.reps, StepRange(scale.repMin, scale.repMax, 1), ValueInput.WHOLE,
            onUpdate = { f -> onChange(null) { d -> val p = scale.prescription(d.total); d.copy(total = scale.levelOf(p.weight, f(p.reps))) } },
            onDialogUpdate = { f -> onChangeNow(null) { d -> val p = scale.prescription(d.total); d.copy(total = scale.levelOf(p.weight, f(p.reps))) } },
            info = stringResource(R.string.info_current_reps_per_set),
        )
    }
}

/** The Reset progress dialogs' "Clear history too" checkbox row (spec R6 §4.3), one 48 dp toggle target. */
@Composable
internal fun ClearHistoryRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(top = 8.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .testTag("clear_history"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(R.string.clear_history_too), modifier = Modifier.padding(start = 8.dp))
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
