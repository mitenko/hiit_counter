package com.mitenko.repkit.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.FieldRanges
import com.mitenko.repkit.domain.StepRange
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.WeightHoldField
import com.mitenko.repkit.domain.WeightValidation
import com.mitenko.repkit.domain.WeightValidator
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.pickable
import com.mitenko.repkit.domain.snapTop
import com.mitenko.repkit.domain.stepAlong
import com.mitenko.repkit.domain.stepWeight
import com.mitenko.repkit.domain.withKind
import com.mitenko.repkit.domain.withListWeight
import com.mitenko.repkit.domain.withNewListWeight
import com.mitenko.repkit.domain.withNewWeightHold
import com.mitenko.repkit.domain.withWeightHold
import com.mitenko.repkit.domain.withoutListWeight
import com.mitenko.repkit.domain.withoutWeightHold
import com.mitenko.repkit.ui.common.ChoiceRow
import com.mitenko.repkit.ui.common.EditValueDialog
import com.mitenko.repkit.ui.common.InfoTag
import com.mitenko.repkit.ui.common.IntStepperField
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.common.SettingsCard
import com.mitenko.repkit.ui.common.ValueInput
import com.mitenko.repkit.ui.common.WeightMoveNote
import com.mitenko.repkit.ui.common.WeightPickerField
import com.mitenko.repkit.ui.common.WeightStepperField
import com.mitenko.repkit.ui.common.label
import com.mitenko.repkit.ui.common.resolve
import com.mitenko.repkit.ui.common.shortLabel
import com.mitenko.repkit.ui.common.uiText
import com.mitenko.repkit.ui.common.unitLabel
import com.mitenko.repkit.ui.common.weightText

/** A weight-mode draft edit; [field] is the field the user changed (revision 28 §6), or null. */
typealias WeightEdit = (field: WeightField?, transform: (WeightConfig) -> WeightConfig) -> Unit

/** The weight half of the Progression page (spec rev 26 §3, plan Spec note 25): the mode, the weight draft and its callbacks. */
class WeightPage(
    val mode: ProgressMode,
    val draft: WeightConfig,
    val validation: WeightValidation,
    val status: SaveStatus,
    val note: WeightNote?,
    val onChange: WeightEdit,
    val onChangeNow: WeightEdit,
    val onRequestMode: (ProgressMode) -> Unit,
    val onRequestUnit: (WeightUnit) -> Unit,
)

/** Progress by: Reps | Weight | Reps then weight (spec rev 26 §3). Another mode is requested; the confirm comes from the ViewModel. */
@Composable
internal fun ProgressByRow(mode: ProgressMode, onRequest: (ProgressMode) -> Unit) {
    ChoiceRow(
        stringResource(R.string.progress_by), ProgressMode.entries, mode,
        optionLabel = { stringResource(it.label) }, key = { it.name }, tag = "mode",
        onSelect = { if (it != mode) onRequest(it) }, info = stringResource(R.string.info_progress_by),
    )
}

/**
 * Rows 1–4 of a weight-mode Progression page (spec rev 26 §3): Unit, Weights (Steps or My weights),
 * Reps per set or the rep range, and the starting point. Each note shows under the field that caused it.
 */
@Composable
internal fun WeightSetupRows(w: WeightPage) {
    val d = w.draft
    val unit = d.unit
    val errors = w.validation.errors
    ChoiceRow(
        stringResource(R.string.weight_unit), WeightUnit.entries, unit,
        optionLabel = { stringResource(it.shortLabel) }, key = { it.name }, tag = "unit",
        onSelect = { if (it != unit) w.onRequestUnit(it) }, info = stringResource(R.string.info_weight_unit),
    )
    WeightNoteUnder(w, WeightField.UNIT)
    ChoiceRow(
        stringResource(R.string.weights_source), WeightsKind.entries, d.kind,
        optionLabel = { stringResource(if (it == WeightsKind.STEPS) R.string.weights_steps else R.string.weights_list) },
        key = { it.name }, tag = "weights",
        onSelect = { kind -> w.onChangeNow(WeightField.KIND) { it.withKind(kind) } }, info = stringResource(R.string.info_weights_source),
    )
    WeightNoteUnder(w, WeightField.KIND)
    when (d.kind) {
        WeightsKind.STEPS -> StepsRows(w)
        WeightsKind.LIST -> MyWeightsRows(w)
    }
    if (w.mode == ProgressMode.WEIGHT) {
        IntStepperField(
            stringResource(R.string.reps_per_set), d.repsPerSet, FieldRanges.REPS_PER_SET, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.REPS_PER_SET) { it.copy(repsPerSet = f(it.repsPerSet)) } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.REPS_PER_SET) { it.copy(repsPerSet = f(it.repsPerSet)) } },
            error = errors[WeightField.REPS_PER_SET]?.uiText()?.resolve(), info = stringResource(R.string.info_reps_per_set),
        )
    } else {
        IntStepperField(
            stringResource(R.string.rep_range_min), d.repMin, FieldRanges.REPS_PER_SET, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.REP_MIN) { it.copy(repMin = f(it.repMin)) } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.REP_MIN) { it.copy(repMin = f(it.repMin)) } },
            error = errors[WeightField.REP_MIN]?.uiText()?.resolve(), info = stringResource(R.string.info_rep_range_min),
        )
        WeightNoteUnder(w, WeightField.REP_MIN)
        IntStepperField(
            stringResource(R.string.rep_range_max), d.repMax, FieldRanges.REPS_PER_SET, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.REP_MAX) { it.copy(repMax = f(it.repMax)) } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.REP_MAX) { it.copy(repMax = f(it.repMax)) } },
            error = errors[WeightField.REP_MAX]?.uiText()?.resolve(), info = stringResource(R.string.info_rep_range_max),
        )
        WeightNoteUnder(w, WeightField.REP_MAX)
    }
    val options = d.pickable
    WeightPickerField(
        unitLabel(R.string.start_weight, unit), options, d.startWeight ?: options.firstOrNull(), unit,
        onStep = { up -> w.onChange(WeightField.START_WEIGHT) { c -> c.copy(startWeight = stepAlong(c.pickable, c.startWeight ?: c.pickable.firstOrNull(), up)) } },
        onPick = { v -> w.onChangeNow(WeightField.START_WEIGHT) { it.copy(startWeight = v) } },
        error = errors[WeightField.START_WEIGHT]?.uiText()?.resolve(), info = stringResource(R.string.info_start_weight),
        a11yLabel = stringResource(R.string.start_weight),
    )
    WeightNoteUnder(w, WeightField.START_WEIGHT)
    if (w.mode == ProgressMode.REPS_THEN_WEIGHT) {
        IntStepperField(
            stringResource(R.string.start_reps), d.startReps ?: d.repMin, FieldRanges.REPS_PER_SET, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.START_REPS) { it.copy(startReps = f(it.startReps ?: it.repMin)) } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.START_REPS) { it.copy(startReps = f(it.startReps ?: it.repMin)) } },
            error = errors[WeightField.START_REPS]?.uiText()?.resolve(), info = stringResource(R.string.info_start_reps),
        )
        WeightNoteUnder(w, WeightField.START_REPS)
    }
}

/** Steps: Start and Top are free values stepped by Step; Step is picked from the choices (plan Spec note 28). */
@Composable
private fun StepsRows(w: WeightPage) {
    val s = w.draft.steps
    val unit = w.draft.unit
    val errors = w.validation.errors
    WeightStepperField(
        unitLabel(R.string.steps_start, unit), s.start,
        onStep = { up -> w.onChange(WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = stepWeight(it.steps.start, it.steps.step, up))) } },
        onDialogValue = { v -> w.onChangeNow(WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = v)) } },
        error = errors[WeightField.STEPS_START]?.uiText()?.resolve(), info = stringResource(R.string.info_steps_start),
        a11yLabel = stringResource(R.string.steps_start),
    )
    WeightNoteUnder(w, WeightField.STEPS_START)
    WeightPickerField(
        unitLabel(R.string.steps_step, unit), WeightValidator.STEP_CHOICES, s.step, unit,
        onStep = { up -> w.onChange(WeightField.STEPS_STEP) { it.copy(steps = it.steps.copy(step = stepAlong(WeightValidator.STEP_CHOICES, it.steps.step, up) ?: it.steps.step)) } },
        onPick = { v -> w.onChangeNow(WeightField.STEPS_STEP) { it.copy(steps = it.steps.copy(step = v)) } },
        error = errors[WeightField.STEPS_STEP]?.uiText()?.resolve(), info = stringResource(R.string.info_steps_step),
        a11yLabel = stringResource(R.string.steps_step),
    )
    WeightNoteUnder(w, WeightField.STEPS_STEP)
    WeightStepperField(
        unitLabel(R.string.steps_top, unit), s.top,
        onStep = { up -> w.onChange(WeightField.STEPS_TOP) { it.copy(steps = it.steps.copy(top = stepWeight(it.steps.top, it.steps.step, up))) } },
        onDialogValue = { v -> w.onChangeNow(WeightField.STEPS_TOP) { it.copy(steps = it.steps.copy(top = snapTop(v, it.steps.start, it.steps.step))) } },
        error = errors[WeightField.STEPS_TOP]?.uiText()?.resolve(), info = stringResource(R.string.info_steps_top),
        a11yLabel = stringResource(R.string.steps_top),
    )
    WeightNoteUnder(w, WeightField.STEPS_TOP)
}

/** My weights: a heading with its ⓘ, a row per weight (edit, ✕), the list's own error and "+ Add weight" (plan Spec note 29). */
@Composable
private fun MyWeightsRows(w: WeightPage) {
    val d = w.draft
    val heading = unitLabel(R.string.weights_list, d.unit)
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        InfoTag(heading, stringResource(R.string.info_my_weights))
    }
    d.list.forEachIndexed { i, value ->
        WeightListRow(
            index = i, value = value, unit = d.unit, errorText = w.validation.rowErrors[i]?.uiText()?.resolve(),
            onEdit = { v -> w.onChangeNow(WeightField.LIST) { it.withListWeight(i, v) } },
            onRemove = { w.onChangeNow(WeightField.LIST) { it.withoutListWeight(i) } },
        )
    }
    w.validation.errors[WeightField.LIST]?.let {
        Text(
            it.uiText().resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp).testTag("support_weights"),
        )
    }
    TextButton(
        onClick = { w.onChangeNow(WeightField.LIST) { it.withNewListWeight() } },
        enabled = d.list.size < WeightConfig.MAX_WEIGHTS,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 48.dp).testTag("add_weight"),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.add_weight), modifier = Modifier.padding(start = 8.dp))
    }
    WeightNoteUnder(w, WeightField.LIST)
}

/** One My weights row: "20 kg" opens the edit dialog, ✕ removes it; both are 48 dp targets. */
@Composable
private fun WeightListRow(index: Int, value: Int, unit: WeightUnit?, errorText: String?, onEdit: (Int) -> Unit, onRemove: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val rowLabel = stringResource(R.string.weight_n, index + 1)
    val shown = weightText(value, unit).resolve()
    SettingsCard(Modifier.padding(vertical = 4.dp).testTag("weight_row_$index")) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shown,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (errorText != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = stringResource(R.string.edit_value, rowLabel)) { editing = true }
                        .wrapContentHeight(Alignment.CenterVertically)
                        .testTag("weight_value_$index"),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp).testTag("remove_weight_$index")) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.remove_weight, shown))
                }
            }
            errorText?.let {
                Text(
                    it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("support_weight_$index"),
                )
            }
        }
    }
    if (editing) {
        EditValueDialog(
            title = rowLabel,
            initialText = WeightFormat.format(value),
            input = ValueInput.WEIGHT,
            parse = WeightFormat::parse,
            onConfirm = { v ->
                editing = false
                onEdit(v)
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * The weight-mode hold list under the shared Hold switch (spec rev 26 §3 row 5, plan Spec note 32):
 * per hold a header with ✕, its weight, its reps (Reps then weight) and "for", then "+ Add hold".
 * Unlike the Reps page's holds (spec rev 34 §10), a weight hold stays At only: no At | From choice.
 */
@Composable
internal fun WeightHoldRows(w: WeightPage) {
    val d = w.draft
    val v = w.validation
    val options = d.pickable
    val reps = d.repStepRange()
    d.holds.forEachIndexed { i, hold ->
        val errs = v.holdErrors[i].orEmpty()
        HoldHeader(number = i + 1, onRemove = { w.onChangeNow(WeightField.HOLDS) { it.withoutWeightHold(i) } })
        WeightPickerField(
            unitLabel(R.string.hold_at_weight, d.unit), options, hold.weight, d.unit,
            onStep = { up -> w.onChange(WeightField.HOLDS) { c -> c.withWeightHold(i) { h -> h.copy(weight = stepAlong(c.pickable, h.weight, up) ?: h.weight) } } },
            onPick = { x -> w.onChangeNow(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(weight = x) } } },
            error = errs[WeightHoldField.WEIGHT]?.uiText()?.resolve(),
            hint = if (i in v.holdHints) stringResource(R.string.hint_hold_disabled) else null,
            info = stringResource(R.string.info_hold_at_weight),
            a11yLabel = stringResource(R.string.hold_n_weight, i + 1),
        )
        if (w.mode == ProgressMode.REPS_THEN_WEIGHT) {
            IntStepperField(
                stringResource(R.string.hold_at_reps), hold.reps, reps, ValueInput.WHOLE,
                onUpdate = { f -> w.onChange(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(reps = f(h.reps)) } } },
                onDialogUpdate = { f -> w.onChangeNow(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(reps = f(h.reps)) } } },
                error = errs[WeightHoldField.REPS]?.uiText()?.resolve(), info = stringResource(R.string.info_hold_at_reps),
                a11yLabel = stringResource(R.string.hold_n_reps, i + 1),
            )
        }
        IntStepperField(
            stringResource(R.string.hold_for), hold.forCount, FieldRanges.HOLD_FOR, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(forCount = f(h.forCount)) } } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(forCount = f(h.forCount)) } } },
            error = errs[WeightHoldField.FOR]?.uiText()?.resolve(), info = stringResource(R.string.info_hold_for),
            a11yLabel = stringResource(R.string.hold_n_for, i + 1),
        )
    }
    v.errors[WeightField.HOLDS]?.let {
        Text(
            it.uiText().resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp).testTag("support_holds"),
        )
    }
    AddHoldButton(
        enabled = d.holds.size < ProgressionConfig.MAX_HOLDS,
        onClick = { w.onChangeNow(WeightField.HOLDS) { it.withNewWeightHold(w.mode) } },
    )
    WeightNoteUnder(w, WeightField.HOLDS)
}

/** A hold's reps stepper range: the rep range, kept a valid StepRange while the draft's range is invalid. */
private fun WeightConfig.repStepRange(): StepRange {
    val low = minOf(repMin, repMax).coerceIn(1, WeightConfig.MAX_REPS)
    val high = maxOf(repMin, repMax).coerceIn(low, WeightConfig.MAX_REPS)
    return StepRange(low, high, 1)
}

/** The note under [at] (null: under Reset to defaults), if the last edit was there. */
@Composable
internal fun WeightNoteUnder(w: WeightPage, at: WeightField?) {
    val note = w.note
    if (note != null && note.at == at) WeightMoveNote(note.moves, w.draft.unit, tag = "weight_note")
}

/** "Start fresh?" (spec rev 26 §2 Switching mode, plan Spec note 33); the body names the starting weight, or the starting reps for Reps. */
@Composable
internal fun StartFreshDialog(to: ProgressMode, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.start_fresh_title)) },
        text = { Text(stringResource(if (to.usesWeights) R.string.start_fresh_body_weight else R.string.start_fresh_body_reps)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_start_fresh")) { Text(stringResource(R.string.start_fresh)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Switch to lb?" (spec rev 26 §2 Units, plan Spec note 34). */
@Composable
internal fun ChangeUnitDialog(to: WeightUnit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = stringResource(to.shortLabel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_unit_title, name)) },
        text = { Text(stringResource(R.string.change_unit_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_change_unit")) { Text(stringResource(R.string.convert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
