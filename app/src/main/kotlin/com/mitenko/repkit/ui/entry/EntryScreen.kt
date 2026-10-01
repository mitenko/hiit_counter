package com.mitenko.repkit.ui.entry

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.HistoryRange
import com.mitenko.repkit.domain.model.EntryType

@Composable
fun EntryRoute(onBack: () -> Unit, onOpenSettings: () -> Unit, onEntryGone: () -> Unit, vm: EntryViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    // ← and ⚙ are disabled while starting (spec §7.4); system back is held too.
    BackHandler(enabled = state.starting) { }
    EntryScreen(
        state, onBack = onBack, onStart = vm::onStart, onCheckIn = vm::onCheckIn,
        onOpenSettings = onOpenSettings, onDismissError = vm::dismissError,
    )
}

/**
 * The entry screen (spec rev 9 §3): ←, the name and ⚙; then, once the entry has loaded (plan Spec
 * note 16), the 4 weeks · 3 months · All switch, the centre row (the Workout chart or the Timer
 * only calendar, with a Workout's reps column on the right), the streak line and R4's Check in /
 * Start row. There is no chart icon and no History screen.
 */
@Composable
fun EntryScreen(
    state: EntryUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onCheckIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            onDismissError()
        }
    }
    // Spec rev 9 §3: 4 weeks by default, kept across rotation and process death.
    var range by rememberSaveable { mutableStateOf(HistoryRange.FOUR_WEEKS) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !state.starting, modifier = Modifier.testTag("back")) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
                Text(
                    state.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("entry_name"),
                )
                IconButton(onClick = onOpenSettings, enabled = !state.starting, modifier = Modifier.testTag("settings")) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                }
            }
            // Everything below waits for the entry's first emission (R4's final fix, plan Spec note 16),
            // so there's nothing to tap, and no Workout layout to flash, before its type is known.
            if (state.loaded) {
                Spacer(Modifier.height(12.dp))
                RangeSwitch(range, onSelect = { range = it })
                Spacer(Modifier.height(16.dp))
                CentreRow(state, range)
                Spacer(Modifier.height(12.dp))
                // Spec rev 9 §3: replaces the Best/Curr CI Streak rows, for both types.
                Text(
                    stringResource(R.string.streak_line, state.currentStreak, state.bestStreak),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("streak_line"),
                )
                Spacer(Modifier.height(24.dp))
                // Spec R4 §4.1, amended by spec revision 8: every entry has Check in (outlined) and Start
                // (filled), laid out the same way whether it's a Workout or Timer only.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CheckInButton(state, onCheckIn, Modifier.weight(1f))
                    Button(
                        onClick = onStart,
                        enabled = !state.starting && !state.checkingIn,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("start"),
                    ) {
                        Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/**
 * Check in (spec R4 §4.1). It reads "Checked in ✓" and is disabled once checked in today (the
 * screen re-evaluates the date on resume). It's also disabled while its own call or a start is in flight.
 */
@Composable
private fun CheckInButton(state: EntryUiState, onCheckIn: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onCheckIn,
        enabled = !state.checkedInToday && !state.checkingIn && !state.starting,
        modifier = modifier.heightIn(min = 56.dp).testTag("check_in"),
    ) {
        Text(
            stringResource(if (state.checkedInToday) R.string.checked_in else R.string.check_in),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** 4 weeks · 3 months · All (spec rev 9 §3). Each segment is tagged `range_<RANGE>`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSwitch(selected: HistoryRange, onSelect: (HistoryRange) -> Unit) {
    val ranges = HistoryRange.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                selected = selected == range,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
                modifier = Modifier.testTag("range_${range.name}"),
            ) {
                Text(stringResource(range.label))
            }
        }
    }
}

@get:StringRes
private val HistoryRange.label: Int
    get() = when (this) {
        HistoryRange.FOUR_WEEKS -> R.string.range_4w
        HistoryRange.THREE_MONTHS -> R.string.range_3m
        HistoryRange.ALL -> R.string.range_all
    }

/**
 * Spec rev 9 §3: the chart (or calendar) takes the remaining width at [ChartInsets.height], and a
 * Workout's reps column sits on the right. The empty states sit in the chart area (plan Spec note 17).
 */
@Composable
private fun CentreRow(state: EntryUiState, range: HistoryRange) {
    val view = remember(state.points, range, state.now, state.zone) {
        HistoryLayout.rangeView(state.points, range, state.now, state.zone)
    }
    val shown = remember(view, state.type) { HistoryLayout.shownPoints(view, state.type) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(ChartInsets.height), contentAlignment = Alignment.Center) {
            when (HistoryLayout.empty(state.points, shown)) {
                HistoryEmpty.NO_CHECK_INS -> EmptyText(R.string.history_empty)
                HistoryEmpty.NONE_IN_RANGE -> EmptyText(R.string.history_empty_range)
                null -> when (state.type) {
                    EntryType.WORKOUT -> WorkoutChart(shown, view.start, view.end, state.zone)
                    // key(range): a new range gets a fresh scroll state, so the calendar reopens at today.
                    EntryType.CHECK_IN -> key(range) { CheckInCalendar(shown, view.start, view.end, state.zone) }
                }
            }
        }
        if (state.type == EntryType.WORKOUT) RepsColumn(state.reps)
    }
}

/**
 * Spec rev 9 §3 and plan Spec note 18: a Workout's per-set reps, top to bottom, in the old table
 * cells' style. It scrolls only past [RepsColumnLayout.VISIBLE_ROWS] sets, and it's one node for
 * screen readers: "Reps per set: 9, 8, 8, …".
 */
@Composable
private fun RepsColumn(reps: List<Int>) {
    val line = MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.reps_per_set_desc, RepsColumnLayout.spoken(reps))
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .width(REPS_COLUMN_WIDTH)
            .height(ChartInsets.height)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .then(if (RepsColumnLayout.scrolls(reps.size)) Modifier.verticalScroll(scrollState) else Modifier)
            .testTag("reps_column"),
    ) {
        reps.forEachIndexed { index, value ->
            Text(
                "$value",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartInsets.height / RepsColumnLayout.VISIBLE_ROWS)
                    .border(0.5.dp, line)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .testTag("rep_$index"),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

private val REPS_COLUMN_WIDTH = 56.dp

@Composable
private fun EmptyText(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("history_empty"),
    )
}
