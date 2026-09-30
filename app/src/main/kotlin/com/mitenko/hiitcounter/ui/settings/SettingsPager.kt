package com.mitenko.hiitcounter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.SettingsTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
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
 * ON_STOP and an onEntryGone pop (§6.2). A check-in-only entry shows only Progression · Current (R4 §4.6).
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
) {
    val name by pagerVm.name.collectAsStateWithLifecycle()
    val type by pagerVm.type.collectAsStateWithLifecycle()
    // Collected unconditionally (no short-circuit), so the composition shape never changes.
    val pagerGone by pagerVm.missing.collectAsStateWithLifecycle()
    val timingGone by timingVm.missing.collectAsStateWithLifecycle()
    val progressionGone by progressionVm.missing.collectAsStateWithLifecycle()
    val currentGone by currentVm.missing.collectAsStateWithLifecycle()
    val cuesGone by cuesVm.missing.collectAsStateWithLifecycle()
    val missing = pagerGone || timingGone || progressionGone || currentGone || cuesGone

    val flushAll = {
        timingVm.flush()
        progressionVm.flush()
        currentVm.flush()
    }
    val leave = {
        flushAll()
        onBack()
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
            type?.let { SettingsTabs(it, initialPage, onPageChange = flushAll, timingVm, progressionVm, currentVm, cuesVm) }
        }
    }
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
) {
    val pages = SettingsPage.visibleFor(type)
    val checkInOnly = type == EntryType.CHECK_IN
    // rememberPagerState is saveable, so the page survives rotation and process recreation (R3 §4).
    val pagerState = rememberPagerState(initialPage = initialPage.tabIndex(pages)) { pages.size }
    val latestOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pagerState) {
        // Tab taps and swipes alike: leaving a page writes what it had pending.
        snapshotFlow { pagerState.currentPage }.drop(1).collect { latestOnPageChange() }
    }
    val scope = rememberCoroutineScope()
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        pages.forEachIndexed { index, page ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(stringResource(page.tab)) },
                modifier = Modifier.testTag("tab_${page.name}"),
            )
        }
    }
    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).testTag("settings_pager")) { index ->
        when (pages[index]) {
            SettingsPage.TIMING -> TimingPage(timingVm)
            SettingsPage.PROGRESSION -> ProgressionPage(progressionVm, windowOnly = checkInOnly)
            SettingsPage.CURRENT -> CurrentStatePage(currentVm, showTotal = !checkInOnly)
            SettingsPage.CUES -> CuesPage(cuesVm)
        }
    }
}
