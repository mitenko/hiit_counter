package com.mitenko.repkit.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mitenko.repkit.R

/**
 * Spec revision 18 §3: shown instead of creating or duplicating when the free tier is at its
 * [maxEntries]. Go Pro reports back and the caller closes the dialog; Not now just closes it.
 */
@Composable
fun EntryLimitDialog(maxEntries: Int, onGoPro: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.limit_title)) },
        text = { Text(pluralStringResource(R.plurals.limit_text, maxEntries, maxEntries)) },
        confirmButton = {
            TextButton(onClick = onGoPro, modifier = Modifier.testTag("go_pro")) { Text(stringResource(R.string.go_pro)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("not_now")) { Text(stringResource(R.string.not_now)) }
        },
        modifier = Modifier.testTag("limit_dialog"),
    )
}
