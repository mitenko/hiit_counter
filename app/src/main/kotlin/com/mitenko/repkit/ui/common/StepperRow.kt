package com.mitenko.repkit.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.PenaltyDraft
import com.mitenko.repkit.domain.StepRange
import com.mitenko.repkit.domain.ValueFormat

/**
 * The shared stepper row (spec R2 §8.1): the label on top, with its ⓘ tag when [info] is given
 * (R3 §7.1). Below it, 48 dp −/+ buttons (tap = one step, hold = repeat) sit around a large value
 * that opens the edit dialog when tapped, and an inline error or hint goes beneath. The whole row
 * is one rounded card tagged `card_<label>` (spec rev 9 §4). [a11yLabel] replaces [label] in the
 * spoken names (Increase / Decrease / Edit / About) and the test tags, while [label] stays the
 * visible text; the holds list uses it to tell its rows apart (spec rev 16 §6).
 */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueTap: () -> Unit,
    error: String? = null,
    hint: String? = null,
    info: String? = null,
    a11yLabel: String = label,
) {
    // Spec rev 9 §4: 4 dp above and below, so neighbouring cards sit 8 dp apart. No horizontal
    // padding inside: the −/value/+ row needs the full 288 dp at the 320 dp minimum width.
    SettingsCard(Modifier.padding(vertical = 4.dp).testTag("card_$a11yLabel")) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // The ⓘ is its own 48 dp target, separate from the value's tap-to-edit (spec R3 §7.1).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                info?.let { InfoTag(title = label, text = it, describedAs = a11yLabel) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RepeatingIconButton(onMinus, R.drawable.ic_remove, stringResource(R.string.decrease, a11yLabel))
                Text(
                    valueText,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (error != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier
                        .widthIn(min = 140.dp)
                        .clickable(onClickLabel = stringResource(R.string.edit_value, a11yLabel), onClick = onValueTap)
                        .testTag("value_$a11yLabel"),
                )
                RepeatingIconButton(onPlus, R.drawable.ic_add, stringResource(R.string.increase, a11yLabel))
            }
            (error ?: hint)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).testTag("support_$a11yLabel"),
                )
            }
        }
    }
}

/**
 * A read-only value row (spec rev 29): the same card and label/ⓘ layout as [StepperRow], but the
 * value is just displayed — no −/+ buttons and no tap-to-edit dialog. Best streak uses this: the
 * app keeps it current on its own (spec R3 §6), so it isn't a field the user edits.
 */
@Composable
fun ValueRow(
    label: String,
    valueText: String,
    info: String? = null,
    a11yLabel: String = label,
) {
    SettingsCard(Modifier.padding(vertical = 4.dp).testTag("card_$a11yLabel")) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                info?.let { InfoTag(title = label, text = it, describedAs = a11yLabel) }
            }
            Text(
                valueText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp).testTag("value_$a11yLabel"),
            )
        }
    }
}

/**
 * An integer field on a [StepRange] (spec R2 §8.1). ± clamps at the hard edges and a dialog value
 * is clamped into the range. Cross-field rules are the screen's validation and are not clamped
 * here. [input] is [ValueInput.TIME] (shown as mm:ss) or [ValueInput.WHOLE]. Updates are
 * transforms, so repeated steps always apply to the latest draft. Steps go to [onUpdate] (the
 * pages debounce them), and a dialog OK goes to [onDialogUpdate] (the pages save it at once,
 * spec R3 §6.2).
 */
@Composable
fun IntStepperField(
    label: String,
    value: Int,
    range: StepRange,
    input: ValueInput,
    onUpdate: ((Int) -> Int) -> Unit,
    onDialogUpdate: ((Int) -> Int) -> Unit = onUpdate,
    error: String? = null,
    hint: String? = null,
    info: String? = null,
    a11yLabel: String = label,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val time = input == ValueInput.TIME
    val text = if (time) ValueFormat.formatSeconds(value) else value.toString()
    StepperRow(
        label = label,
        valueText = text,
        onMinus = { onUpdate(range::minus) },
        onPlus = { onUpdate(range::plus) },
        onValueTap = { editing = true },
        error = error,
        hint = hint,
        info = info,
        a11yLabel = a11yLabel,
    )
    if (editing) {
        EditValueDialog<Int>(
            title = label,
            initialText = text,
            input = input,
            parse = if (time) ValueFormat::parseSeconds else ValueFormat::parseInt,
            onConfirm = { parsed ->
                editing = false
                onDialogUpdate { range.clamp(parsed) }
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * The penalty rate (spec R2 §8.1): ± steps of 0.5 on integer half-hours within 0.5 – 999.5. A
 * dialog value is clamped to that range and kept exactly, even if it isn't a multiple of 0.5; the
 * next ± press snaps it (see [PenaltyDraft]). Steps go to [onUpdate], a dialog OK to [onDialogUpdate].
 */
@Composable
fun PenaltyStepperField(
    label: String,
    value: PenaltyDraft,
    onUpdate: ((PenaltyDraft) -> PenaltyDraft) -> Unit,
    onDialogUpdate: ((PenaltyDraft) -> PenaltyDraft) -> Unit = onUpdate,
    error: String? = null,
    info: String? = null,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val text = ValueFormat.formatDecimal(value.hours)
    StepperRow(
        label = label,
        valueText = text,
        onMinus = { onUpdate(PenaltyDraft::minus) },
        onPlus = { onUpdate(PenaltyDraft::plus) },
        onValueTap = { editing = true },
        error = error,
        info = info,
    )
    if (editing) {
        EditValueDialog(
            title = label,
            initialText = text,
            input = ValueInput.DECIMAL,
            parse = ValueFormat::parseDecimal,
            onConfirm = { hours ->
                editing = false
                onDialogUpdate { PenaltyDraft.of(hours.coerceIn(PenaltyDraft.MIN_HOURS, PenaltyDraft.MAX_HOURS)) }
            },
            onDismiss = { editing = false },
        )
    }
}
