package com.mitenko.hiitcounter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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

/** The pager's own state: the entry name for the "<name> settings" title (spec R3 §4), null until loaded. */
@HiltViewModel
class SettingsPagerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val name: StateFlow<String?> = repo.entry(entryId).filterNotNull().map { it.name }
        .stateIn<String?>(viewModelScope, SharingStarted.Eagerly, null)
}

/**
 * An entry's settings on one screen (spec R3 §4): ← and "<name> settings", tabs over a
 * HorizontalPager. Each page keeps its own ViewModel, keyed on this back-stack entry, so each has
 * its own saved-state draft and AutoSaver (see the plan's layout decision). This route only hosts
 * them. It flushes the three auto-saving pages on every page change and on every exit: back, ←,
 * ON_STOP and an onEntryGone pop (§6.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // rememberPagerState is saveable, so the page survives rotation and process recreation (§4).
    val pagerState = rememberPagerState(initialPage = initialPage.ordinal) { SettingsPage.entries.size }
    LaunchedEffect(pagerState) {
        // Tab taps and swipes alike: leaving a page writes what it had pending.
        snapshotFlow { pagerState.currentPage }.drop(1).collect { flushAll() }
    }
    val scope = rememberCoroutineScope()

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Until the name has loaded, the existing "Settings" string stands in, so there's never a bare " settings".
            val title = name?.let { stringResource(R.string.entry_settings_title, it) } ?: stringResource(R.string.settings)
            SettingsTopBar(title, onBack = leave)
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                SettingsPage.entries.forEach { page ->
                    Tab(
                        selected = pagerState.currentPage == page.ordinal,
                        onClick = { scope.launch { pagerState.animateScrollToPage(page.ordinal) } },
                        text = { Text(stringResource(page.tab)) },
                        modifier = Modifier.testTag("tab_${page.name}"),
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).testTag("settings_pager")) { index ->
                when (SettingsPage.entries[index]) {
                    SettingsPage.TIMING -> TimingPage(timingVm)
                    SettingsPage.PROGRESSION -> ProgressionPage(progressionVm)
                    SettingsPage.CURRENT -> CurrentStatePage(currentVm)
                    SettingsPage.CUES -> CuesPage(cuesVm)
                }
            }
        }
    }
}
