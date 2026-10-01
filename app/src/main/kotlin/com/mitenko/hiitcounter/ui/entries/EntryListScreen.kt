package com.mitenko.hiitcounter.ui.entries

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.ui.common.NameDialog
import com.mitenko.hiitcounter.ui.common.label
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun EntryListRoute(onOpenEntry: (Long) -> Unit, onCreated: (Long) -> Unit, vm: EntryListViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    EntryListScreen(
        state = state,
        onOpenEntry = onOpenEntry,
        onMove = vm::move,
        onCreate = { name, type -> vm.create(name, type, onCreated) },
    )
}

/** The entry list (spec §7.3): Loading disables the FAB, Empty offers the first workout, Items is a keyed LazyColumn. */
@Composable
fun EntryListScreen(
    state: EntryListUiState,
    onOpenEntry: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onCreate: (String, EntryType) -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    val loading = state is EntryListUiState.Loading
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // Spec rev 9 §2: "REPKIT" centred, and nothing else in the bar (R5 removed Reorder).
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("title"))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!loading) naming = true },
                modifier = Modifier.testTag("add").semantics { if (loading) disabled() },
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_workout))
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                EntryListUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("loading"))
                EntryListUiState.Empty ->
                    EmptyState(onAdd = { naming = true }, modifier = Modifier.align(Alignment.Center))
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
    Surface(
        shape = TileShape,
        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (isDragging) 6.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
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
                // the tile graph, so it isn't squeezed by the graph; the graph sits at the end of the
                // second line, 8 dp before the ≡ handle. Decorative: no touch target.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // R6 §3.4's weekCount, shown at 0 too; no rep total or streak.
                    Text(
                        stringResource(R.string.week_count, row.weekCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TileGraphic(row.type, row.id, row.tile, Modifier.padding(start = 8.dp, end = 8.dp))
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
