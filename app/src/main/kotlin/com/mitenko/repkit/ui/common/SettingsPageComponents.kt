package com.mitenko.repkit.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.ValidationResult

/** What a page's status line says (spec R3 §6.2). */
enum class SaveStatus {
    /** The draft is valid: stored, or about to be (a debounce may still be pending, and every exit flushes it). */
    SAVED,

    /** The draft is invalid: nothing is saved, and the stored values stay at the last valid state. */
    INVALID,

    /** The repository rejected a valid draft (logged). This shouldn't happen. */
    FAILED;

    companion object {
        fun of(validation: ValidationResult, failed: Boolean): SaveStatus = when {
            !validation.isValid -> INVALID
            failed -> FAILED
            else -> SAVED
        }
    }
}

/** "Saved", "Not saved: fix the highlighted field" or "Not saved" (spec R3 §6.2). */
@Composable
fun SaveStatusLine(status: SaveStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        SaveStatus.SAVED -> R.string.saved
        SaveStatus.INVALID -> R.string.not_saved_invalid
        SaveStatus.FAILED -> R.string.not_saved
    }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = if (status == SaveStatus.SAVED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("save_status"),
    )
}

/** ← and a title, as on the other settings screens (spec R3 §4). */
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
}

/** One settings page: its rows scroll above a fixed [footer] (the status line). There's no Save button (spec R3 §6). */
@Composable
fun SettingsPageLayout(footer: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), content = content)
        footer()
    }
}
