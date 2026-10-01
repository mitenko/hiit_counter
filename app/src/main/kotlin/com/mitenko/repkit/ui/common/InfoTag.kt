package com.mitenko.repkit.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R

/**
 * The ⓘ tag (spec R3 §7.1): a 48 dp button described as "About <title>", separate from the row's
 * own tap target. It opens a dialog with [title], [text] and OK. The glyph is Material's "info
 * outline" as a vector drawable, because material-icons isn't on the classpath.
 */
@Composable
fun InfoTag(title: String, text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.about, title))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, modifier = Modifier.testTag("info_title")) },
            text = { Text(text, modifier = Modifier.testTag("info_text")) },
            confirmButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.testTag("info_ok")) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}
