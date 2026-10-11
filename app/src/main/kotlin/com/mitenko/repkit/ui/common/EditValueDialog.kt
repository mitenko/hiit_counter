package com.mitenko.repkit.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.mitenko.repkit.R

/** How the edit dialog reads its text (spec §8.2, §8.3). */
enum class ValueInput(val keyboardType: KeyboardType, @param:StringRes val invalidMessage: Int) {
    /** m:ss or plain seconds; Ascii so the keyboard has ':'. */
    TIME(KeyboardType.Ascii, R.string.error_time_format),
    WHOLE(KeyboardType.Number, R.string.error_enter_number),
    DECIMAL(KeyboardType.Decimal, R.string.error_enter_number),

    /** A weight: a decimal with at most 2 places (plan Spec note 26). */
    WEIGHT(KeyboardType.Decimal, R.string.error_enter_weight),
}

/**
 * Edit-value dialog (spec §8.3). The title is the field label and the text starts selected.
 * OK is disabled while [parse] returns null, with an inline explanation. The text lives in
 * rememberSaveable, so it survives rotation. The caller clamps the confirmed value to the
 * field's hard range; cross-field rules are left to the screen's validation.
 */
@Composable
fun <T : Any> EditValueDialog(
    title: String,
    initialText: String,
    input: ValueInput,
    parse: (String) -> T?,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val parsed = parse(text.text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val focus = remember { FocusRequester() }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = parsed == null,
                supportingText = if (parsed == null) {
                    { Text(stringResource(input.invalidMessage)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = input.keyboardType, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("edit_field"),
            )
            // Inside the dialog's own composition, so the requester is attached when this runs.
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null, modifier = Modifier.testTag("edit_ok")) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
