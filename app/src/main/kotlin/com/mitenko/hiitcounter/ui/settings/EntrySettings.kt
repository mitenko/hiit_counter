package com.mitenko.hiitcounter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.EntryBusy
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.InfoTag
import com.mitenko.hiitcounter.ui.common.NameDialog
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The four per-entry settings pages (spec R2 §7.5). [label] is the Entry Settings row, and [tab]
 * is the pager tab (R3 §4: "Timing · Progression · Current · Cues"). The ordinal is the route's
 * `page` argument.
 */
enum class SettingsPage(@StringRes val label: Int, @StringRes val tab: Int) {
    TIMING(R.string.settings_timing, R.string.settings_timing),
    PROGRESSION(R.string.settings_progression, R.string.settings_progression),
    CURRENT(R.string.settings_current_state, R.string.tab_current),
    CUES(R.string.settings_cues, R.string.settings_cues);

    companion object {
        /** Spec R4 §4.5–4.6: a check-in-only entry has no timer and no cues, so only Progression and Current remain. */
        fun visibleFor(type: EntryType): List<SettingsPage> = when (type) {
            EntryType.WORKOUT -> entries.toList()
            EntryType.CHECK_IN -> listOf(PROGRESSION, CURRENT)
        }
    }
}

data class EntrySettingsUiState(
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val type: EntryType = EntryType.WORKOUT,
)

@HiltViewModel
class EntrySettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val controller: TimerController,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val error = MutableStateFlow<String?>(null)

    /** In-flight guard; touched only on Main. A double-tap while a duplicate is running is ignored. */
    private var duplicating = false

    /** busy is re-read on every run-status change; the snapshot is set before PREPARING is emitted. */
    val uiState: StateFlow<EntrySettingsUiState> =
        combine(repo.entry(entryId).filterNotNull(), controller.status, error) { entry, _, err ->
            EntrySettingsUiState(name = entry.name, busy = controller.isBusy(entryId), error = err, type = entry.type)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntrySettingsUiState())

    /** Allowed while busy: it only affects future runs, because the snapshot is frozen (spec §7.1). */
    fun rename(name: String) {
        error.value = null
        viewModelScope.launch {
            try {
                repo.rename(entryId, name)
            } catch (e: EntryNotFound) {
                markMissing()
            } catch (e: IllegalArgumentException) {
                error.value = e.message
            }
        }
    }

    /**
     * Spec R4 §4.5: saved at once on the dialog's OK. Allowed while busy, because the run's snapshot
     * is frozen. NonCancellable, so leaving right after OK can't drop a write that has started. A
     * deleted entry pops to the list.
     */
    fun setType(type: EntryType) {
        error.value = null
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { repo.setType(entryId, type) }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    fun duplicate(onCreated: (Long) -> Unit) {
        if (duplicating) return
        duplicating = true
        error.value = null
        viewModelScope.launch {
            try {
                onCreated(repo.duplicate(entryId))
            } catch (e: EntryNotFound) {
                markMissing()
            } finally {
                duplicating = false
            }
        }
    }

    /**
     * Spec §7.1: the busy check reads the singleton controller at the moment of the call, so a
     * stale screen can't bypass it. A busy entry fails with [EntryBusy] and nothing is deleted.
     */
    fun delete(onDeleted: () -> Unit) {
        error.value = null
        viewModelScope.launch {
            try {
                if (controller.isBusy(entryId)) throw EntryBusy(entryId)
                repo.delete(entryId)
                onDeleted()
            } catch (e: EntryBusy) {
                error.value = BUSY_HINT
            } catch (e: EntryNotFound) {
                onDeleted()
            }
        }
    }

    companion object {
        const val BUSY_HINT = "Stop the workout first"
    }
}

@Composable
fun EntrySettingsRoute(
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onDuplicated: (Long) -> Unit,
    onDeleted: () -> Unit,
    onEntryGone: () -> Unit,
    vm: EntrySettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    EntrySettingsScreen(
        state = state,
        onBack = onBack,
        onOpen = onOpen,
        onRename = vm::rename,
        onDuplicate = { vm.duplicate(onDuplicated) },
        onDelete = { vm.delete(onDeleted) },
        onSetType = vm::setType,
    )
}

@Composable
fun EntrySettingsScreen(
    state: EntrySettingsUiState,
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSetType: (EntryType) -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var choosingType by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(title = state.name, onBack = onBack) {
        // Spec R4 §4.5: the Type row sits above the page rows, and a check-in-only entry has no Timing or Cues.
        TypeRow(state.type) { choosingType = true }
        SettingsPage.visibleFor(state.type).forEach { page ->
            ListRow(stringResource(page.label), tag = "page_${page.name}") { onOpen(page) }
        }
        ListRow(stringResource(R.string.rename), tag = "rename") { renaming = true }
        ListRow(stringResource(R.string.duplicate), tag = "duplicate", onClick = onDuplicate)
        // Busy rule (spec §7.1): disabled with a hint; the ViewModel re-checks at the moment of the call.
        ListRow(stringResource(R.string.delete), tag = "delete", enabled = !state.busy, color = MaterialTheme.colorScheme.error) {
            confirmDelete = true
        }
        if (state.busy) {
            Text(
                stringResource(R.string.stop_workout_first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).testTag("busy_hint"),
            )
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.rename),
            initial = state.name,
            onConfirm = { name ->
                renaming = false
                onRename(name)
            },
            onDismiss = { renaming = false },
        )
    }
    if (choosingType) {
        TypeDialog(
            current = state.type,
            onConfirm = { type ->
                choosingType = false
                if (type != state.type) onSetType(type)
            },
            onDismiss = { choosingType = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, state.name)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete"),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "Type" over "Workout" or "Check-in only", with its ⓘ as a separate target (spec R4 §4.5, R3 §7.1). */
@Composable
private fun TypeRow(type: EntryType, onClick: () -> Unit) {
    val title = stringResource(R.string.type)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp)
                .testTag("type"),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(type.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("type_value"),
            )
        }
        InfoTag(title = title, text = stringResource(R.string.info_type))
    }
    HorizontalDivider()
}

/** Two radio options with OK and Cancel (spec R4 §4.5). The pick lives in rememberSaveable until OK. */
@Composable
private fun TypeDialog(current: EntryType, onConfirm: (EntryType) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.type)) },
        text = {
            Column(Modifier.selectableGroup()) {
                EntryType.entries.forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = selected == type, onClick = { selected = type }, role = Role.RadioButton)
                            .testTag("type_option_${type.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == type, onClick = null)
                        Text(stringResource(type.label), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, modifier = Modifier.testTag("type_ok")) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("type_cancel")) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ListRow(
    text: String,
    tag: String,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp)
            .testTag(tag),
    )
    HorizontalDivider()
}
