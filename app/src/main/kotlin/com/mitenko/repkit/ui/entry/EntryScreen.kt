package com.mitenko.repkit.ui.entry

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.HistoryRange
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.ui.ads.AdPlacement
import com.mitenko.repkit.ui.ads.AdSlot
import kotlinx.coroutines.launch

@Composable
fun EntryRoute(onBack: () -> Unit, onOpenSettings: () -> Unit, onEntryGone: () -> Unit, vm: EntryViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    // Paused while this screen isn't started (spec revision 12 §3): a highlight set while away
    // (e.g. Start navigated to the timer) just waits here and plays once collection resumes.
    val highlight by vm.highlight.collectAsStateWithLifecycle()
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
        highlight = highlight, onHighlightShown = vm::highlightShown,
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
    highlight: Highlight? = null,
    onHighlightShown: () -> Unit = {},
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
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // Spec revision 18 §4: the ad slot sits at the bottom; it composes nothing for Pro (all of v1).
        bottomBar = { AdSlot(AdPlacement.ENTRY_SCREEN) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
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
                IconButton(onClick = onOpenSettings, enabled = !state.starting && !state.checkingIn, modifier = Modifier.testTag("settings")) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                }
            }
            // Everything below waits for the entry's first emission (R4's final fix, plan Spec note 16),
            // so there's nothing to tap, and no Workout layout to flash, before its type is known.
            if (state.loaded) {
                Spacer(Modifier.height(12.dp))
                RangeSwitch(range, onSelect = { range = it })
                Spacer(Modifier.height(16.dp))
                CentreRow(state, range, highlight, onHighlightShown)
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
private fun CentreRow(state: EntryUiState, range: HistoryRange, highlight: Highlight?, onHighlightShown: () -> Unit) {
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
                    EntryType.WORKOUT -> WorkoutChart(shown, HistoryLayout.chartStart(view, shown), view.end, state.zone)
                    // key(range): a new range gets a fresh scroll state, so the calendar reopens at today.
                    EntryType.CHECK_IN -> key(range) { CheckInCalendar(shown, view.start, view.end, state.zone) }
                }
            }
        }
        if (state.type == EntryType.WORKOUT) RepsColumn(state.reps, highlight, onHighlightShown)
    }
}

/**
 * Spec rev 9 §3 and plan Spec note 18: a Workout's per-set reps, top to bottom, in the old table
 * cells' style. It scrolls only past [RepsColumnLayout.VISIBLE_ROWS] sets, and it's one node for
 * screen readers: "Reps per set: 9, 8, 8, …".
 *
 * Spec revision 12 §3: after a check-in, the cells whose value changed pop and flash. [highlight]
 * is a one-shot event — [onHighlightShown] consumes it right away so a later recomposition with a
 * cleared (null) highlight can't drop the animation; a locally remembered copy of the event is what
 * the cells and the announcement actually key off, and it survives that clearing.
 */
@Composable
private fun RepsColumn(reps: List<Int>, highlight: Highlight?, onHighlightShown: () -> Unit) {
    val line = MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.reps_per_set_desc, RepsColumnLayout.spoken(reps))
    val scrollState = rememberScrollState()

    var active by remember { mutableStateOf<Highlight?>(null) }
    LaunchedEffect(highlight?.id) {
        if (highlight != null) {
            active = highlight
            onHighlightShown()
        }
    }

    Box {
        Column(
            Modifier
                .width(REPS_COLUMN_WIDTH)
                .height(ChartInsets.height)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .then(if (RepsColumnLayout.scrolls(reps.size)) Modifier.verticalScroll(scrollState) else Modifier)
                .testTag("reps_column"),
        ) {
            reps.forEachIndexed { index, value ->
                RepCell(index, value, active?.changes?.get(index), active?.id, line)
            }
        }
        ChangeAnnouncement(active, reps)
    }
}

/**
 * One cell's pop-and-fade (spec revision 12 §3): primary on a gain, error on a drop. Keyed on
 * [highlightId] rather than [change] itself, so the same cell changing the same way twice in a
 * row (e.g. gaining a rep two check-ins running) still replays the animation.
 */
@Composable
private fun RepCell(index: Int, value: Int, change: RepsColumnLayout.Change?, highlightId: Int?, borderColor: Color) {
    val reducedMotion = reducedMotionEnabled()
    val scale = remember { Animatable(1f) }
    val colorFraction = remember { Animatable(0f) }
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(highlightId) {
        if (change == null) return@LaunchedEffect
        active = true
        try {
            // Reduced motion (spec revision 12 §3): skip the scale pop, keep only the colour fade.
            // A hold (spec revision 20) never pops: no value changed, so it only flashes.
            if (!reducedMotion && change != RepsColumnLayout.Change.HOLD) {
                launch {
                    scale.animateTo(1.15f, tween(POP_HALF_MS))
                    scale.animateTo(1f, tween(POP_HALF_MS))
                }
            }
            colorFraction.snapTo(1f)
            colorFraction.animateTo(0f, tween(FADE_MS))
        } finally {
            // A cancelled animation (e.g. a new highlight arriving, or leaving the screen) must
            // still clear "increased"/"decreased" rather than stranding it on this cell.
            active = false
        }
    }
    val tint: Color? = when (change) {
        RepsColumnLayout.Change.UP -> MaterialTheme.colorScheme.primary
        RepsColumnLayout.Change.DOWN -> MaterialTheme.colorScheme.error
        RepsColumnLayout.Change.HOLD -> MaterialTheme.colorScheme.onSurfaceVariant
        null -> null
    }
    Text(
        "$value",
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartInsets.height / RepsColumnLayout.VISIBLE_ROWS)
            .scale(scale.value)
            .then(if (tint != null) Modifier.background(tint.copy(alpha = HIGHLIGHT_ALPHA * colorFraction.value)) else Modifier)
            .border(0.5.dp, borderColor)
            .wrapContentHeight(Alignment.CenterVertically)
            .testTag("rep_$index")
            .then(
                if (active) {
                    Modifier.semantics {
                        stateDescription = when (change) {
                            RepsColumnLayout.Change.UP -> "increased"
                            RepsColumnLayout.Change.DOWN -> "decreased"
                            else -> "holding"
                        }
                    }
                } else {
                    Modifier
                },
            ),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleSmall,
    )
}

/**
 * TalkBack announcement (spec revision 12 §3): a polite live region that names the single changed
 * set, or counts them when several changed (e.g. after a miss). Invisible — it exists for
 * accessibility services, not sighted users.
 */
@Composable
private fun ChangeAnnouncement(active: Highlight?, reps: List<Int>) {
    val message = when {
        active == null -> ""
        active.hold != null -> stringResource(R.string.reps_holding, active.hold.at, active.hold.day, active.hold.of)
        active.changes.size == 1 -> {
            val (index, _) = active.changes.entries.first()
            stringResource(R.string.reps_changed_one, index + 1, reps.getOrElse(index) { 0 })
        }
        else -> stringResource(R.string.reps_changed_many, active.changes.size)
    }
    Text(
        message,
        modifier = Modifier
            .size(1.dp)
            .testTag("reps_announcement")
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Settings → Accessibility → Remove animations (spec revision 12 §3): read once per composition. */
@Composable
private fun reducedMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

private val REPS_COLUMN_WIDTH = 56.dp
private const val POP_HALF_MS = 150
private const val FADE_MS = 1200
private const val HIGHLIGHT_ALPHA = 0.35f

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
