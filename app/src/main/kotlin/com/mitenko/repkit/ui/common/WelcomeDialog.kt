package com.mitenko.repkit.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R

/**
 * The welcome dialog (spec rev 43): what REPKIT is and the two kinds of workout, in the ⓘ dialogs'
 * style. The entry list shows it once on a first launch and reopens it from the ⓘ in its top bar.
 * The type headings reuse `type_workout` / `type_check_in`, so they always match the New dialog.
 */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.welcome_title), modifier = Modifier.testTag("welcome_title")) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .semantics(mergeDescendants = true) {}
                    .testTag("welcome_text"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.welcome_intro))
                Text(stringResource(R.string.type_workout), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.welcome_counter))
                Text(stringResource(R.string.type_check_in), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.welcome_timer))
                Text(stringResource(R.string.welcome_start))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("welcome_ok")) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
