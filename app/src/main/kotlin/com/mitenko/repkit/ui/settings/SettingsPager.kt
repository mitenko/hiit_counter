package com.mitenko.repkit.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.R
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.ui.common.EntryScopedViewModel
import com.mitenko.repkit.ui.common.SettingsTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The pager's own state: the entry name for the "<name> settings" title (spec R3 §4) and the
 * entry type, which decides the visible tabs (R4 §4.6). Both are null until loaded.
 */
@HiltViewModel
class SettingsPagerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val name: StateFlow<String?> = repo.entry(entryId).filterNotNull().map { it.name }
        .stateIn<String?>(viewModelScope, SharingStarted.Eagerly, null)

    val type: StateFlow<EntryType?> = repo.entry(entryId).filterNotNull().map { it.type }
        .stateIn<EntryType?>(viewModelScope, SharingStarted.Eagerly, null)
}

/** Spec R4 §4.6: where this page opens among the visible [pages]; a hidden page opens the first tab. */
internal fun SettingsPage.tabIndex(pages: List<SettingsPage>): Int = pages.indexOf(this).coerceAtLeast(0)

/**
 * An entry's settings on one screen (spec R3 §4): ← and "<name> settings", tabs over a
 * HorizontalPager. Each page keeps its own ViewModel, keyed on this back-stack entry, so each has
 * its own saved-state draft and AutoSaver (see the R3 plan's layout decision). This route only hosts
 * them. It flushes the three auto-saving pages on every page change and on every exit: back, ←,
 * ON_STOP and an onEntryGone pop (§6.2). Leaving Timing after a Counter's Sets changed asks
 * whether to reset progress (spec revision 32): over the new page, or before an exit continues. Every entry shows all four tabs (spec revision 8); a
 * Timer only entry's Progression stays window-only and Current stays total-less (R4 §4.6). The
 * Progression page has a second ViewModel for the weight group (spec rev 26 PR 2, plan Spec note 25),
 * flushed and noted like the others.
 */
@Composable
fun SettingsPagerRoute(
    initialPage: SettingsPage,
    onBack: () -> Unit,
    onEntryGone: () -> Unit,
    pagerVm: SettingsPagerViewModel = hiltViewModel(),
    timingVm: TimingSettingsViewModel = hiltViewModel(key = "timing"),
    progressionVm: ProgressionSettingsViewModel = hiltViewModel(key = "progression"),
    currentVm: CurrentStateViewModel = hiltViewModel(key = "current"),
    cuesVm: CuesSettingsViewModel = hiltViewModel(key = "cues"),
    weightVm: WeightSettingsViewModel = hiltViewModel(key = "weight"),
) {
    val name by pagerVm.name.collectAsStateWithLifecycle()
    val type by pagerVm.type.collectAsStateWithLifecycle()
    // Collected unconditionally (no short-circuit), so the composition shape never changes.
    val pagerGone by pagerVm.missing.collectAsStateWithLifecycle()
    val timingGone by timingVm.missing.collectAsStateWithLifecycle()
    val progressionGone by progressionVm.missing.collectAsStateWithLifecycle()
    val currentGone by currentVm.missing.collectAsStateWithLifecycle()
    val cuesGone by cuesVm.missing.collectAsStateWithLifecycle()
    val weightGone by weightVm.missing.collectAsStateWithLifecycle()
    val missing = pagerGone || timingGone || progressionGone || currentGone || cuesGone || weightGone

    val flushAll = {
        timingVm.flush()
        progressionVm.flush()
        currentVm.flush()
        weightVm.flush()
    }
    val leave = {
        flushAll()
        timingVm.exit(onBack)
    }
    LaunchedEffect(missing) {
        if (missing) {
            flushAll()
            onEntryGone()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { flushAll() }
    BackHandler(onBack = leave)

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Until the name has loaded, the existing "Settings" string stands in, so there's never a bare " settings".
            val title = name?.let { stringResource(R.string.entry_settings_title, it) } ?: stringResource(R.string.settings)
            SettingsTopBar(title, onBack = leave)
            // The visible tabs depend on the type, so they wait until the entry has loaded (spec R4 §4.6).
            // Spec revisions 27 and 28: a page change also hides the Current and Progression notes.
            val onPageChange = {
                flushAll()
                currentVm.clearRangeNote()
                currentVm.clearStreakNote()
                progressionVm.clearNote()
                weightVm.clearNote()
            }
            type?.let { SettingsTabs(it, initialPage, onPageChange = onPageChange, timingVm, progressionVm, currentVm, cuesVm, weightVm) }
        }
    }
    val setsPrompt by timingVm.setsPrompt.collectAsStateWithLifecycle()
    setsPrompt?.let { SetsChangedDialog(it, onReset = timingVm::resetProgress, onKeep = timingVm::keepProgress) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.SettingsTabs(
    type: EntryType,
    initialPage: SettingsPage,
    onPageChange: () -> Unit,
    timingVm: TimingSettingsViewModel,
    progressionVm: ProgressionSettingsViewModel,
    currentVm: CurrentStateViewModel,
    cuesVm: CuesSettingsViewModel,
    weightVm: WeightSettingsViewModel,
) {
    val pages = SettingsPage.visibleFor(type)
    val checkInOnly = type == EntryType.CHECK_IN
    // rememberPagerState is saveable, so the page survives rotation and process recreation (R3 §4).
    val pagerState = rememberPagerState(initialPage = initialPage.tabIndex(pages)) { pages.size }
    val latestOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pagerState) {
        // Tab taps and swipes alike: leaving a page writes what it had pending, then Timing learns
        // whether it's showing, for its Sets baseline and check (spec revision 32).
        snapshotFlow { pagerState.currentPage }.collectIndexed { i, page ->
            if (i > 0) latestOnPageChange()
            timingVm.pageShown(timing = pages[page] == SettingsPage.TIMING)
        }
    }
    val scope = rememberCoroutineScope()
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        pages.forEachIndexed { index, page ->
            // Spec rev 9 §4: the icon alone, named by its content description; a 48 dp minimum
            // target; the selected tab in the primary colour, with the indicator unchanged.
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                icon = { Icon(painterResource(page.icon), contentDescription = stringResource(page.tab)) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 48.dp).testTag("tab_${page.name}"),
            )
        }
    }
    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).testTag("settings_pager")) { index ->
        when (pages[index]) {
            SettingsPage.TIMING -> TimingPage(timingVm)
            SettingsPage.PROGRESSION -> ProgressionPage(progressionVm, weightVm, windowOnly = checkInOnly)
            SettingsPage.CURRENT -> CurrentStatePage(currentVm, showTotal = !checkInOnly)
            SettingsPage.CUES -> CuesPage(cuesVm)
        }
    }
}
