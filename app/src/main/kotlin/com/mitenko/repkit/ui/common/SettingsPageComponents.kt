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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.ValidationResult
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.model.WeightUnit

/** What a page's status line says (spec R3 §6.2). */
enum class SaveStatus {
    /** The draft is valid: stored, or about to be (a debounce may still be pending, and every exit flushes it). */
    SAVED,

    /** The draft is invalid: nothing is saved, and the stored values stay at the last valid state. */
    INVALID,

    /** The repository rejected a valid draft (logged). This shouldn't happen. */
    FAILED;

    companion object {
        fun of(validation: ValidationResult, failed: Boolean): SaveStatus = of(validation.isValid, failed)

        fun of(valid: Boolean, failed: Boolean): SaveStatus = when {
            !valid -> INVALID
            failed -> FAILED
            else -> SAVED
        }

        /** One status line for two drafts (plan Spec note 25): invalid first, then a failed save, else Saved. */
        fun worst(a: SaveStatus, b: SaveStatus): SaveStatus = when {
            a == INVALID || b == INVALID -> INVALID
            a == FAILED || b == FAILED -> FAILED
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

/**
 * What an edit moved (spec revisions 27 and 28), under the field that caused it: the moves joined
 * on one line, announced politely to TalkBack.
 */
@Composable
fun MoveNote(moves: List<Move>, tag: String) = NoteText(noteText(moves), tag)

/** A weight-mode note (plan Spec notes 30–31), with the same look. */
@Composable
fun WeightMoveNote(moves: List<WeightMove>, unit: WeightUnit?, tag: String) = NoteText(weightNoteText(moves, unit), tag)

@Composable
private fun NoteText(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
    )
}
