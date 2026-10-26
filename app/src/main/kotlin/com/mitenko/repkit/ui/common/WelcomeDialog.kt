package com.mitenko.repkit.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
 * The welcome dialog (spec rev 43): what REPKIT is and its three uses (Reps, Weights, Timer Only) as
 * headings, in the ⓘ dialogs' style. The entry list shows it once on a first launch and reopens it from
 * the ⓘ in its top bar. The headings reuse the New dialog's `new_kind_*` labels (rev 44), so they always match.
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
                Heading(stringResource(R.string.new_kind_reps))
                Text(stringResource(R.string.welcome_reps))
                Heading(stringResource(R.string.new_kind_weights))
                Text(stringResource(R.string.welcome_weights))
                Heading(stringResource(R.string.new_kind_timer))
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

/** One of the three ways to use REPKIT, in the primary colour so it stands out from the body text. */
@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp).testTag("welcome_heading_$text"),
    )
}
