package com.mitenko.hiitcounter.ui.entries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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

@Composable
fun EntryListRoute(onOpenEntry: (Long) -> Unit, onCreated: (Long) -> Unit, vm: EntryListViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val reorderMode by vm.reorderMode.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    EntryListScreen(
        state = state,
        reorderMode = reorderMode,
        onOpenEntry = onOpenEntry,
        onToggleReorder = vm::toggleReorder,
        onMoveUp = vm::moveUp,
        onMoveDown = vm::moveDown,
        onCreate = { name, type -> vm.create(name, type, onCreated) },
    )
}

/** The entry list (spec §7.3): Loading disables the FAB, Empty offers the first workout, Items is a keyed LazyColumn. */
@Composable
fun EntryListScreen(
    state: EntryListUiState,
    reorderMode: Boolean,
    onOpenEntry: (Long) -> Unit,
    onToggleReorder: () -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onCreate: (String, EntryType) -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    val loading = state is EntryListUiState.Loading
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (state is EntryListUiState.Items) {
                    TextButton(onClick = onToggleReorder, modifier = Modifier.testTag("reorder")) {
                        Text(stringResource(if (reorderMode) R.string.done else R.string.reorder))
                    }
                }
            }
        },
        floatingActionButton = {
            if (!reorderMode) {
                FloatingActionButton(
                    onClick = { if (!loading) naming = true },
                    modifier = Modifier.testTag("add").semantics { if (loading) disabled() },
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_workout))
                }
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
                    EntryList(state.rows, reorderMode, onOpenEntry, onMoveUp, onMoveDown)
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
    reorderMode: Boolean,
    onOpen: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    var movedId by remember { mutableStateOf<Long?>(null) }
    // Keep the moved row in view (spec §7.3): wait one frame for the reordered layout, then scroll just enough.
    LaunchedEffect(rows, movedId) {
        val id = movedId ?: return@LaunchedEffect
        withFrameNanos { }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == id }
        when {
            item == null -> rows.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { listState.animateScrollToItem(it) }
            item.offset < info.viewportStartOffset ->
                listState.animateScrollBy((item.offset - info.viewportStartOffset).toFloat())
            item.offset + item.size > info.viewportEndOffset ->
                listState.animateScrollBy((item.offset + item.size - info.viewportEndOffset).toFloat())
            else -> Unit
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            EntryRowItem(
                row = row,
                reorderMode = reorderMode,
                canMoveUp = index > 0,
                canMoveDown = index < rows.lastIndex,
                onOpen = { onOpen(row.id) },
                onMoveUp = {
                    movedId = row.id
                    onMoveUp(row.id)
                },
                onMoveDown = {
                    movedId = row.id
                    onMoveDown(row.id)
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EntryRowItem(
    row: EntryRow,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val checkedDescription = stringResource(R.string.checked_in_today)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !reorderMode, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("entry_${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                // Spec R4 §4.3: a Workout reads "Reps N", a check-in-only entry "Streak N".
                if (row.type == EntryType.CHECK_IN) stringResource(R.string.streak_n, row.streak) else stringResource(R.string.reps_n, row.reps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.checkedInToday) {
            Text(
                "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = checkedDescription },
            )
        }
        if (reorderMode) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.move_up, row.name))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = stringResource(R.string.move_down, row.name))
            }
        }
    }
}

/** Workout | Check-in only (spec R4 §4.4). Each segment is tagged `type_<TYPE>`. */
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
