package com.mitenko.repkit.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.model.WeightUnit

/**
 * A free weight (plan Spec note 28: Steps' Start and Top) on the shared stepper row: ± is one step
 * ([onStep], debounced by the page), and the value opens the edit dialog, which takes up to 2
 * decimals ([onDialogValue], saved at once). [value] is hundredths.
 */
@Composable
fun WeightStepperField(
    label: String,
    value: Int,
    onStep: (up: Boolean) -> Unit,
    onDialogValue: (Int) -> Unit,
    error: String? = null,
    info: String? = null,
    a11yLabel: String = label,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val text = WeightFormat.format(value)
    StepperRow(
        label = label,
        valueText = text,
        onMinus = { onStep(false) },
        onPlus = { onStep(true) },
        onValueTap = { editing = true },
        error = error,
        info = info,
        a11yLabel = a11yLabel,
    )
    if (editing) {
        EditValueDialog(
            title = label,
            initialText = text,
            input = ValueInput.WEIGHT,
            parse = WeightFormat::parse,
            onConfirm = { v ->
                editing = false
                onDialogValue(v)
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * A weight picked from [options] (plan Spec note 28: Step, Starting weight, a hold's weight, Current
 * weight): ± moves along the list ([onStep]), and the value opens a radio list ([onPick], saved at
 * once). A null [value] shows "—"; with no options the list doesn't open.
 */
@Composable
fun WeightPickerField(
    label: String,
    options: List<Int>,
    value: Int?,
    unit: WeightUnit?,
    onStep: (up: Boolean) -> Unit,
    onPick: (Int) -> Unit,
    error: String? = null,
    hint: String? = null,
    info: String? = null,
    a11yLabel: String = label,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    StepperRow(
        label = label,
        valueText = value?.let(WeightFormat::format) ?: stringResource(R.string.none),
        onMinus = { onStep(false) },
        onPlus = { onStep(true) },
        onValueTap = { if (options.isNotEmpty()) picking = true },
        error = error,
        hint = hint,
        info = info,
        a11yLabel = a11yLabel,
    )
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(label) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    options.forEach { w ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = w == value, role = Role.RadioButton, onClick = {
                                    picking = false
                                    onPick(w)
                                })
                                .testTag("option_${a11yLabel}_$w"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = w == value, onClick = null)
                            Text(weightText(w, unit).resolve(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * A labelled single choice (plan Spec note 37: Progress by, Unit, Weights): the label and its ⓘ on top,
 * then a segmented row. Segments are at least 48 dp tall, have no check icon and wrap to two lines,
 * so three fit at 320 dp. The card is tagged `card_<label>`, each segment `<tag>_<key(option)>`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    key: (T) -> String,
    tag: String,
    onSelect: (T) -> Unit,
    info: String? = null,
) {
    SettingsCard(Modifier.padding(vertical = 4.dp).testTag("card_$label")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                info?.let { InfoTag(title = label, text = it) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                options.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                        icon = {},
                        modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_${key(option)}"),
                    ) {
                        Text(optionLabel(option), maxLines = 2, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
