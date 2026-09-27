package com.mitenko.hiitcounter.ui.common

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
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.PenaltyDraft
import com.mitenko.hiitcounter.domain.StepRange
import com.mitenko.hiitcounter.domain.ValueFormat

/**
 * The shared stepper row (spec §8.1): the label on top, then 48 dp −/+ buttons (tap = one step,
 * hold = repeat) around a large value that opens the edit dialog when tapped, and an inline
 * error or hint beneath.
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
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RepeatingIconButton(onMinus, R.drawable.ic_remove, stringResource(R.string.decrease, label))
            Text(
                valueText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (error != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .clickable(onClickLabel = stringResource(R.string.edit_value, label), onClick = onValueTap)
                    .testTag("value_$label"),
            )
            RepeatingIconButton(onPlus, R.drawable.ic_add, stringResource(R.string.increase, label))
        }
        (error ?: hint)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).testTag("support_$label"),
            )
        }
    }
}

/**
 * An integer field on a [StepRange] (spec §8.1). ± clamps at the hard edges and a dialog value
 * is clamped into the range. Cross-field rules are the screen's validation and are not clamped
 * here. [input] is [ValueInput.TIME] (shown as mm:ss) or [ValueInput.WHOLE]. [onUpdate] receives
 * a transform, so repeated steps always apply to the latest draft.
 */
@Composable
fun IntStepperField(
    label: String,
    value: Int,
    range: StepRange,
    input: ValueInput,
    onUpdate: ((Int) -> Int) -> Unit,
    error: String? = null,
    hint: String? = null,
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
    )
    if (editing) {
        EditValueDialog<Int>(
            title = label,
            initialText = text,
            input = input,
            parse = if (time) ValueFormat::parseSeconds else ValueFormat::parseInt,
            onConfirm = { parsed ->
                editing = false
                onUpdate { range.clamp(parsed) }
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * The penalty rate (spec §8.1): ± steps of 0.5 on integer half-hours within 0.5 – 999.5. A dialog
 * value is clamped to that range and kept exactly, even if it isn't a multiple of 0.5; the next
 * ± press snaps it (see [PenaltyDraft]).
 */
@Composable
fun PenaltyStepperField(
    label: String,
    value: PenaltyDraft,
    onUpdate: ((PenaltyDraft) -> PenaltyDraft) -> Unit,
    error: String? = null,
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
    )
    if (editing) {
        EditValueDialog(
            title = label,
            initialText = text,
            input = ValueInput.DECIMAL,
            parse = ValueFormat::parseDecimal,
            onConfirm = { hours ->
                editing = false
                onUpdate { PenaltyDraft.of(hours.coerceIn(PenaltyDraft.MIN_HOURS, PenaltyDraft.MAX_HOURS)) }
            },
            onDismiss = { editing = false },
        )
    }
}
