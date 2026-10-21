package com.mitenko.repkit.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mitenko.repkit.R

/**
 * Spec rev 41 §3: shown when a free user taps a Pro theme. Go Pro starts the purchase and the caller closes
 * the dialog; Not now just closes it.
 */
@Composable
fun ProDialog(onGoPro: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pro_title)) },
        text = { Text(stringResource(R.string.pro_theme_text)) },
        confirmButton = {
            TextButton(onClick = onGoPro, modifier = Modifier.testTag("pro_go")) { Text(stringResource(R.string.go_pro)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("pro_not_now")) { Text(stringResource(R.string.not_now)) }
        },
        modifier = Modifier.testTag("pro_dialog"),
    )
}
