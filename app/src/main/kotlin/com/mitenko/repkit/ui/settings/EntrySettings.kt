package com.mitenko.repkit.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import com.mitenko.repkit.R
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.FreeLimits
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.domain.canAddEntry
import com.mitenko.repkit.domain.model.EntryBusy
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.ui.common.EntryLimitDialog
import com.mitenko.repkit.ui.common.EntryScopedViewModel
import com.mitenko.repkit.ui.common.InfoTag
import com.mitenko.repkit.ui.common.NameDialog
import com.mitenko.repkit.ui.common.SettingsCard
import com.mitenko.repkit.ui.common.SettingsCardShape
import com.mitenko.repkit.ui.common.SettingsScaffold
import com.mitenko.repkit.ui.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The four per-entry settings pages (spec R2 §7.5). [label] is the Entry Settings row. The pager
 * tab is [icon] alone, and [tab] is its content description (spec rev 9 §4; R3 §4's names
 * "Timing · Progression · Current · Cues"). The ordinal is the route's `page` argument.
 */
enum class SettingsPage(@StringRes val label: Int, @StringRes val tab: Int, @DrawableRes val icon: Int) {
    TIMING(R.string.settings_timing, R.string.tab_timing, R.drawable.ic_tab_timing),
    PROGRESSION(R.string.settings_progression, R.string.tab_progression, R.drawable.ic_tab_progression),
    CURRENT(R.string.settings_current_state, R.string.tab_current, R.drawable.ic_tab_current),
    CUES(R.string.settings_cues, R.string.tab_cues, R.drawable.ic_tab_cues);

    companion object {
        /** Spec revision 8: every entry, Workout or Timer only, shows all four pages. */
        fun visibleFor(type: EntryType): List<SettingsPage> = entries.toList()
    }
}

data class EntrySettingsUiState(
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val type: EntryType = EntryType.WORKOUT,
    /** Spec revision 18 §3: the free tier's limit dialog, shown instead of duplicating. */
    val limitDialog: Boolean = false,
)

@HiltViewModel
class EntrySettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val controller: TimerController,
    private val entitlements: Entitlements,
    private val limits: FreeLimits,
    private val proUpgrade: ProUpgrade,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val error = MutableStateFlow<String?>(null)
    private val limitDialog = MutableStateFlow(false)

    /** The free tier's entry limit, for the dialog's text. */
    val maxEntries: Int get() = limits.maxEntries

    /** In-flight guard; touched only on Main. A double-tap while a duplicate is running is ignored. */
    private var duplicating = false

    /** busy is re-read on every run-status change; the snapshot is set before PREPARING is emitted. */
    val uiState: StateFlow<EntrySettingsUiState> =
        combine(repo.entry(entryId).filterNotNull(), controller.status, error, limitDialog) { entry, _, err, limit ->
            EntrySettingsUiState(
                name = entry.name, busy = controller.isBusy(entryId), error = err, type = entry.type, limitDialog = limit,
            )
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

    /** Spec revision 18 §3: at the free limit nothing is duplicated; the limit dialog shows instead. */
    fun duplicate(onCreated: (Long) -> Unit) {
        if (duplicating) return
        duplicating = true
        error.value = null
        viewModelScope.launch {
            try {
                if (!canAddEntry(entitlements.tier.value, repo.entries.first().size, limits)) {
                    limitDialog.value = true
                    return@launch
                }
                onCreated(repo.duplicate(entryId))
            } catch (e: EntryNotFound) {
                markMissing()
            } finally {
                duplicating = false
            }
        }
    }

    /** Go Pro (spec revision 18 §3): starts the upgrade, then closes the dialog. */
    fun goPro() {
        proUpgrade.start()
        limitDialog.value = false
    }

    /** Not now (spec revision 18 §3). */
    fun dismissLimit() {
        limitDialog.value = false
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
        maxEntries = vm.maxEntries,
        onGoPro = vm::goPro,
        onDismissLimit = vm::dismissLimit,
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
    maxEntries: Int = FreeLimits().maxEntries,
    onGoPro: () -> Unit = {},
    onDismissLimit: () -> Unit = {},
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var choosingType by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(title = state.name, onBack = onBack) {
        // Spec rev 9 §4: every option is a rounded card, 8 dp apart.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Spec R4 §4.5, amended by spec revision 8: the Type row sits above the page rows; every entry shows all four.
            TypeRow(state.type) { choosingType = true }
            SettingsPage.visibleFor(state.type).forEach { page ->
                ListRow(stringResource(page.label), tag = "page_${page.name}") { onOpen(page) }
            }
            ListRow(stringResource(R.string.rename), tag = "rename") { renaming = true }
            ListRow(stringResource(R.string.duplicate), tag = "duplicate", onClick = onDuplicate)
            // Busy rule (spec §7.1): disabled with a hint; the ViewModel re-checks at the moment of the call.
            // Spec rev 9 §4: Delete is the same card on errorContainer.
            ListRow(stringResource(R.string.delete), tag = "delete", enabled = !state.busy, danger = true) {
                confirmDelete = true
            }
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
    if (state.limitDialog) {
        EntryLimitDialog(maxEntries, onGoPro = onGoPro, onDismiss = onDismissLimit)
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

/**
 * "Type" over "Counter" or "Timer Only", with its ⓘ as a separate target (spec R4 §4.5, R3 §7.1),
 * in a rounded card tagged `card_type` (rev 9 §4).
 */
@Composable
private fun TypeRow(type: EntryType, onClick: () -> Unit) {
    val title = stringResource(R.string.type)
    SettingsCard(Modifier.testTag("card_type")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
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
    }
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

/**
 * One Entry Settings option as a rounded card button (spec rev 9 §4): full width, at least 56 dp,
 * `surfaceContainer`, or `errorContainer` when [danger] (Delete). The tag is on the clickable card.
 */
@Composable
private fun ListRow(
    text: String,
    tag: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = SettingsCardShape,
        color = if (danger) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (danger) colors.onErrorContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Color.Unspecified else colors.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
