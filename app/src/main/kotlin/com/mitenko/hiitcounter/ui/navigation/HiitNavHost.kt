package com.mitenko.hiitcounter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.entries.EntryListRoute
import com.mitenko.hiitcounter.ui.entry.EntryRoute
import com.mitenko.hiitcounter.ui.settings.CuesSettingsRoute
import com.mitenko.hiitcounter.ui.settings.CurrentStateRoute
import com.mitenko.hiitcounter.ui.settings.EntrySettingsRoute
import com.mitenko.hiitcounter.ui.settings.ProgressionSettingsRoute
import com.mitenko.hiitcounter.ui.settings.SettingsPage
import com.mitenko.hiitcounter.ui.settings.TimingSettingsRoute
import com.mitenko.hiitcounter.ui.timer.TimerRoute

@Composable
fun HiitNavHost(controller: TimerController) {
    val nav = rememberNavController()
    val status by controller.status.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = Routes.ENTRIES) {
        val idArg = listOf(navArgument(ENTRY_ID_ARG) { type = NavType.LongType })

        composable(Routes.ENTRIES) {
            val openEntry = dropUnlessResumedWith<Long> { id -> nav.navigate(Routes.entry(id)) { launchSingleTop = true } }
            EntryListRoute(
                onOpenEntry = openEntry,
                // Spec §7.3: the new entry opens with the list beneath it.
                onCreated = { id -> nav.openEntryOverList(id) },
            )
        }
        composable(Routes.ENTRY, arguments = idArg) { entry ->
            val id = entry.entryId()
            EntryRoute(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenSettings = dropUnlessResumed { nav.navigate(Routes.entrySettings(id)) { launchSingleTop = true } },
                onEntryGone = { nav.popToEntries() },
            )
        }
        composable(Routes.ENTRY_SETTINGS, arguments = idArg) { entry ->
            val id = entry.entryId()
            val openPage = dropUnlessResumedWith<SettingsPage> { page ->
                nav.navigate(Routes.settingsPage(id, page)) { launchSingleTop = true }
            }
            EntrySettingsRoute(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpen = openPage,
                // Spec §7.5: the copy opens with the list beneath it.
                onDuplicated = { copy -> nav.openEntryOverList(copy) },
                onDeleted = { nav.popToEntries() },
                onEntryGone = { nav.popToEntries() },
            )
        }
        composable(Routes.ENTRY_TIMING, arguments = idArg) {
            TimingSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popToEntries() })
        }
        composable(Routes.ENTRY_PROGRESSION, arguments = idArg) {
            ProgressionSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popToEntries() })
        }
        composable(Routes.ENTRY_CURRENT, arguments = idArg) {
            CurrentStateRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popToEntries() })
        }
        composable(Routes.ENTRY_CUES, arguments = idArg) {
            CuesSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popToEntries() })
        }
        // Active-run routing relies on TimerRoute's BackHandler blocking back navigation while RUNNING,
        // so onExit only fires once the workout has stopped. lastEntryId outlives clearRun() (spec §7.2).
        composable(Routes.TIMER) { TimerRoute(onExit = { nav.exitTimer(controller.lastEntryId) }) }
    }

    // v1 §4 active-run routing, unchanged.
    LaunchedEffect(status) {
        if (status == RunStatus.RUNNING && nav.currentDestination?.route != Routes.TIMER) {
            nav.navigate(Routes.TIMER) { launchSingleTop = true }
        }
    }
}

private fun NavBackStackEntry.entryId(): Long = requireNotNull(arguments) { "Missing route arguments" }.getLong(ENTRY_ID_ARG)
