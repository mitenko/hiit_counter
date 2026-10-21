package com.mitenko.repkit.ui.entries

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.FreeLimits
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.ui.ads.AdPlacement
import com.mitenko.repkit.ui.ads.AdSlot
import com.mitenko.repkit.ui.common.EntryLimitDialog
import com.mitenko.repkit.ui.common.InfoTag
import com.mitenko.repkit.ui.common.NameDialog
import com.mitenko.repkit.ui.common.ProDialog
import com.mitenko.repkit.ui.common.label
import com.mitenko.repkit.ui.common.longLabel
import com.mitenko.repkit.ui.theme.ThemeMode
import com.mitenko.repkit.ui.theme.canUseTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.mitenko.repkit.ui.theme.effectiveTheme
import com.mitenko.repkit.ui.theme.glowPanel
import com.mitenko.repkit.ui.theme.paletteFor
import com.mitenko.repkit.ui.theme.panelShape
import com.mitenko.repkit.ui.theme.panelColor

@Composable
fun EntryListRoute(onOpenEntry: (Long) -> Unit, onCreated: (Long) -> Unit, vm: EntryListViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val crashReports by vm.crashReportsEnabled.collectAsStateWithLifecycle()
    val weightUnit by vm.weightUnitDefault.collectAsStateWithLifecycle()
    val limitDialog by vm.limitDialog.collectAsStateWithLifecycle()
    val tier by vm.tier.collectAsStateWithLifecycle()
    val proPending by vm.proPending.collectAsStateWithLifecycle()
    val proPrice by vm.proPrice.collectAsStateWithLifecycle()
    val proDialog by vm.proDialog.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    EntryListScreen(
        state = state,
        onOpenEntry = onOpenEntry,
        onMove = vm::move,
        onCreate = { name, type -> vm.create(name, type, onCreated) },
        themeMode = themeMode,
        onSetThemeMode = vm::setThemeMode,
        crashReportsEnabled = crashReports,
        onSetCrashReportsEnabled = vm::setCrashReportsEnabled,
        weightUnit = weightUnit,
        onSetWeightUnit = vm::setWeightUnitDefault,
        onRequestAdd = vm::requestAdd,
        limitDialog = limitDialog,
        maxEntries = vm.maxEntries,
        onGoPro = vm::goPro,
        onDismissLimit = vm::dismissLimit,
        tier = tier,
        proPending = proPending,
        proPrice = proPrice,
        onRestore = vm::restore,
        proDialog = proDialog,
        onDismissPro = vm::dismissPro,
        onLockedTheme = vm::lockedTheme,
    )
}

/**
 * The entry list (spec §7.3): Loading disables the FAB, Empty offers the first workout, Items is a
 * keyed LazyColumn. Spec revision 18 §3: [onRequestAdd] decides whether the New dialog opens; when
 * it doesn't, the ViewModel raises [limitDialog] instead.
 */
@Composable
fun EntryListScreen(
    state: EntryListUiState,
    onOpenEntry: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onCreate: (String, EntryType) -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    crashReportsEnabled: Boolean = true,
    onSetCrashReportsEnabled: (Boolean) -> Unit = {},
    weightUnit: WeightUnit = WeightUnit.KG,
    onSetWeightUnit: (WeightUnit) -> Unit = {},
    onRequestAdd: () -> Boolean = { true },
    limitDialog: Boolean = false,
    maxEntries: Int = FreeLimits().maxEntries,
    onGoPro: () -> Unit = {},
    onDismissLimit: () -> Unit = {},
    tier: Tier = Tier.PRO,
    proPending: Boolean = false,
    proPrice: String? = null,
    onRestore: () -> Unit = {},
    proDialog: Boolean = false,
    onDismissPro: () -> Unit = {},
    onLockedTheme: () -> Unit = {},
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    val requestAdd = { if (onRequestAdd()) naming = true }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val loading = state is EntryListUiState.Loading
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // Spec rev 9 §2: "REPKIT" centred. Spec rev 14 §5 adds the ⚙ at the right (Settings since rev 30);
            // it's overlaid rather than laid out in a Row, so the title (plain Box-centred) stays
            // exactly centred regardless of the icon.
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("title"))
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(48.dp).testTag("settings"),
                ) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!loading) requestAdd() },
                modifier = Modifier.testTag("add").semantics { if (loading) disabled() },
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_workout))
            }
        },
        // Spec revision 18 §4: the ad slot sits at the bottom; it composes nothing for Pro (all of v1).
        bottomBar = { AdSlot(AdPlacement.ENTRY_LIST) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                EntryListUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("loading"))
                EntryListUiState.Empty ->
                    EmptyState(onAdd = requestAdd, modifier = Modifier.align(Alignment.Center))
                is EntryListUiState.Items ->
                    EntryList(state.rows, onOpenEntry, onMove)
            }
        }
    }
    if (naming) {
        // Spec R4 §4.4: Workout by default, every time the dialog opens; kept across rotation.
        var type by rememberSaveable { mutableStateOf(EntryType.WORKOUT) }
        NameDialog(
            title = stringResource(R.string.new_workout),
            initial = "",
            onConfirm = { name ->
                naming = false
                onCreate(name, type)
            },
            onDismiss = { naming = false },
            extra = { EntryTypeChoice(type, onSelect = { type = it }) },
        )
    }
    if (limitDialog) {
        EntryLimitDialog(maxEntries, onGoPro = onGoPro, onDismiss = onDismissLimit)
    }
    if (proDialog) {
        ProDialog(onGoPro = onGoPro, onDismiss = onDismissPro)
    }
    if (showSettings) {
        SettingsDialog(
            tier = tier,
            proPending = proPending,
            proPrice = proPrice,
            onGoPro = {
                showSettings = false
                onGoPro()
            },
            onRestore = {
                showSettings = false
                onRestore()
            },
            onLockedTheme = {
                showSettings = false
                onLockedTheme()
            },
            current = themeMode,
            onSelect = { mode ->
                showSettings = false
                onSetThemeMode(mode)
            },
            crashReports = crashReportsEnabled,
            onCrashReportsChange = onSetCrashReportsEnabled,
            unit = weightUnit,
            onSelectUnit = { unit ->
                showSettings = false
                onSetWeightUnit(unit)
            },
            onDismiss = { showSettings = false },
        )
    }
}

/**
 * The ⚙ Settings dialog (spec rev 30 §3). Under an Appearance heading, spec rev 14 §5's System /
 * Light / Dark options save at once and close the dialog. Under Appearance, Units (spec rev 26 §3):
 * kg or lb for new weight workouts, saved at once, closing the dialog. Under them, the "Share crash
 * reports and usage" switch applies at once and leaves the dialog open.
 */
@Composable
private fun SettingsDialog(
    tier: Tier,
    proPending: Boolean,
    proPrice: String?,
    onGoPro: () -> Unit,
    onRestore: () -> Unit,
    onLockedTheme: () -> Unit,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    crashReports: Boolean,
    onCrashReportsChange: (Boolean) -> Unit,
    unit: WeightUnit,
    onSelectUnit: (WeightUnit) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings), modifier = Modifier.testTag("settings_title")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProRow(tier, proPending, proPrice, onGoPro, onRestore)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.appearance),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }.testTag("appearance_heading"),
                )
                Column(Modifier.selectableGroup()) {
                    ThemeOptionRow(ThemeMode.SYSTEM, R.string.theme_system, current, onSelect, tier, onLockedTheme)
                    ThemeOptionRow(ThemeMode.LIGHT, R.string.theme_light, current, onSelect, tier, onLockedTheme)
                    ThemeOptionRow(ThemeMode.DARK, R.string.theme_dark, current, onSelect, tier, onLockedTheme)
                    ThemeOptionRow(ThemeMode.SONAR, R.string.theme_sonar, current, onSelect, tier, onLockedTheme)
                    ThemeOptionRow(ThemeMode.BLOSSOM, R.string.theme_blossom, current, onSelect, tier, onLockedTheme)
                    ThemeOptionRow(ThemeMode.GROVE, R.string.theme_grove, current, onSelect, tier, onLockedTheme)
                    ThemeOptionRow(ThemeMode.FOUNDRY, R.string.theme_foundry, current, onSelect, tier, onLockedTheme)
                }
                // Spec rev 26 §3, plan Spec note 36: the unit new weight workouts start in; saved at once, closing like Appearance.
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.units),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }.testTag("units_heading"),
                    )
                    InfoTag(title = stringResource(R.string.units), text = stringResource(R.string.info_units))
                }
                Column(Modifier.selectableGroup()) {
                    WeightUnit.entries.forEach { UnitOptionRow(it, unit, onSelectUnit) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                CrashReportsRow(crashReports, onCrashReportsChange)
            }
        },
        confirmButton = {},
    )
}

/**
 * Spec rev 41 §3: the Pro row at the top of Settings. Free shows "Go Pro · price" (just "Go Pro" until Play's
 * price is known) with Restore purchase under it, a pending purchase says so, and Pro says thanks.
 */
@Composable
private fun ProRow(tier: Tier, pending: Boolean, price: String?, onGoPro: () -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("pro_row_group")) {
        when {
            pending -> Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("pro_row"), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.purchase_pending), style = MaterialTheme.typography.titleMedium)
            }
            tier == Tier.PRO -> Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("pro_row"), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.pro_active), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pro_subtitle_pro), style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClick = onGoPro)
                        .testTag("pro_row"),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (price != null) stringResource(R.string.go_pro_price, price) else stringResource(R.string.go_pro),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.pro_subtitle_free), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onRestore, modifier = Modifier.testTag("restore_purchase")) {
                    Text(stringResource(R.string.restore_purchase))
                }
            }
        }
    }
}

/** Spec rev 30 §3: the label, its ⓘ and the switch (tagged `crash_reports`). */
@Composable
private fun CrashReportsRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(R.string.crash_reports)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        InfoTag(title = label, text = stringResource(R.string.info_crash_reports))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("crash_reports"))
    }
}

@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    label: Int,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    tier: Tier,
    onLockedTheme: () -> Unit,
) {
    val selected = mode == current
    // Spec rev 41 §3: a Pro theme on the free tier shows a lock and opens the Pro dialog instead of applying.
    val locked = !canUseTheme(tier, mode)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = { if (locked) onLockedTheme() else onSelect(mode) }, role = Role.RadioButton)
            .testTag("theme_${mode.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
        // A glowing theme shows its swatch (spec rev 36 §2); the phone setting doesn't matter for those.
        paletteFor(effectiveTheme(mode, systemDark = true, tier = Tier.PRO))?.let { p ->
            Spacer(Modifier.weight(1f))
            ThemeSwatch(listOf(p.ground, p.accent, p.rest), Modifier.testTag("theme_swatch_${mode.name}"))
        }
        if (locked) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = stringResource(R.string.pro_lock),
                modifier = Modifier.size(16.dp).testTag("theme_lock_${mode.name}"),
            )
        }
    }
}

/** Three dots for a theme's ground, accent and rest colours (spec rev 36 §2); decorative, so no semantics. */
@Composable
private fun ThemeSwatch(colors: List<Color>, modifier: Modifier = Modifier) {
    Row(modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { c ->
            Box(Modifier.size(12.dp).background(c, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
}

@Composable
private fun UnitOptionRow(option: WeightUnit, current: WeightUnit, onSelect: (WeightUnit) -> Unit) {
    val selected = option == current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = { onSelect(option) }, role = Role.RadioButton)
            .testTag("unit_default_${option.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(option.longLabel), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.no_workouts), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAdd, modifier = Modifier.testTag("add_first")) { Text(stringResource(R.string.add_first_workout)) }
    }
}

@Composable
private fun EntryList(
    rows: List<EntryRow>,
    onOpen: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentRows by rememberUpdatedState(rows)

    // Drag-to-reorder (PR B). While dragging, the list reorders locally only. It is NOT resynced
    // from [rows] the instant the drag ends: the store hasn't re-emitted the new order yet at
    // that point, so resyncing then would snap the row back to the old order and then forward
    // again once the real emission finally arrives. Instead, on drop:
    //  - a no-op drop (delta == 0), or [rows] having changed while the drag was in progress (e.g.
    //    a delete elsewhere made the local guess stale) resyncs immediately from the latest [rows];
    //  - otherwise the locally-reordered list (already correct) is kept, and the next real
    //    emission - the confirmation of this move, or a later one - resyncs it via the
    //    LaunchedEffect below. A change to [rows] that arrives while a drag is active is never
    //    applied mid-drag (it would fight the live drag), but it is not dropped either: it's simply
    //    picked up by that same resync once the drag ends.
    var localRows by remember { mutableStateOf(rows) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var rowsAtDragStart by remember { mutableStateOf<List<EntryRow>?>(null) }
    LaunchedEffect(rows) {
        if (draggingId == null) localRows = rows
    }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        localRows = localRows.moved(from.index, to.index)
    }

    // Spec rev 9 §2: cards 16 dp in from the sides and 8 dp apart; the bottom padding keeps the FAB clear.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(localRows, key = { _, row -> row.id }) { index, row ->
            ReorderableItem(reorderableState, key = row.id) { isDragging ->
                // Spec rev 9 §2: no divider (R5's is removed); the card itself lifts while it's dragged.
                Column {
                    EntryRowItem(
                        row = row,
                        isDragging = isDragging,
                        canMoveUp = index > 0,
                        canMoveDown = index < localRows.lastIndex,
                        onOpen = { onOpen(row.id) },
                        onMoveUp = { onMove(row.id, -1) },
                        onMoveDown = { onMove(row.id, +1) },
                        dragHandle = {
                            val reorderDescription = stringResource(R.string.reorder_entry, row.name)
                            // An IconButton (PR B review, matching the reorderable library's own
                            // pattern for a handle beside clickable row content): its clickable is
                            // what reliably keeps a plain tap here from bubbling to the row's
                            // onOpen, exactly as the library's docs pair a draggableHandle with.
                            // clearAndSetSemantics then replaces its default Role.Button + onClick
                            // semantics (which would read as a dead "button, does nothing" in
                            // TalkBack) with just the description; Move up/down are reached via
                            // the row's own custom actions.
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("drag_${row.id}")
                                    .draggableHandle(
                                        onDragStarted = {
                                            draggingId = row.id
                                            rowsAtDragStart = currentRows
                                            dragStartIndex = localRows.indexOfFirst { it.id == row.id }
                                        },
                                        onDragStopped = {
                                            val id = draggingId
                                            val startRows = rowsAtDragStart
                                            draggingId = null
                                            rowsAtDragStart = null
                                            if (id != null) {
                                                val finalIndex = localRows.indexOfFirst { it.id == id }
                                                val delta = finalIndex - dragStartIndex
                                                // Only an insert or delete during the drag invalidates it: a
                                                // re-emission that just reorders or updates a field (the
                                                // previous drag's own confirmation landing, or an unrelated
                                                // change) keeps the same id set and must not discard this drag.
                                                val changedDuringDrag = startRows != null &&
                                                    startRows.map { it.id }.toSet() != currentRows.map { it.id }.toSet()
                                                if (delta == 0 || changedDuringDrag) {
                                                    localRows = currentRows
                                                } else {
                                                    onMove(id, delta)
                                                }
                                            }
                                        },
                                    )
                                    .clearAndSetSemantics { contentDescription = reorderDescription },
                            ) {
                                Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRowItem(
    row: EntryRow,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val checkedDescription = stringResource(R.string.checked_in_today)
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    // Spec rev 9 §2: a rounded surfaceContainer card; the whole card lifts while it's dragged (R5).
    val shape = panelShape(TileShape)
    Surface(
        shape = shape,
        color = panelColor(if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer),
        shadowElevation = if (isDragging) 6.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().glowPanel(shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
                .testTag("entry_${row.id}")
                .semantics {
                    // Accessibility reorder actions (PR B): neither edge offers the wrong direction.
                    customActions = listOfNotNull(
                        if (canMoveUp) CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true } else null,
                        if (canMoveDown) CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true } else null,
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // Spec rev 9 §2: the name, with the ✓ right after it when checked in today.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.checkedInToday) {
                        Text(
                            "✓",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = checkedDescription },
                        )
                    }
                }
                // Spec rev 9 §2 (amended rev 13): the name spans the full width above "X× this week" and
                // the tile graph, so it isn't squeezed by the graph. The graph fills the rest of the
                // second line after "X× this week", ending 8 dp before the ≡ handle (user, 2026-10-02:
                // the graphs expand horizontally to fill the width). Decorative: no touch target.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // R6 §3.4's weekCount, shown at 0 too; no rep total or streak.
                    Text(
                        stringResource(R.string.week_count, row.weekCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TileGraphic(row.type, row.id, row.tile, Modifier.weight(1f).padding(start = 12.dp, end = 8.dp))
                }
            }
            dragHandle()
        }
    }
}

private val TileShape = RoundedCornerShape(16.dp)

/** Workout | Timer only (spec R4 §4.4). Each segment is tagged `type_<TYPE>`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTypeChoice(selected: EntryType, onSelect: (EntryType) -> Unit) {
    val types = EntryType.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                modifier = Modifier.testTag("type_${type.name}"),
            ) {
                Text(stringResource(type.label))
            }
        }
    }
}
