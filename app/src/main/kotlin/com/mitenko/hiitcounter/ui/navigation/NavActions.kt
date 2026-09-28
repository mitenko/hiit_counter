package com.mitenko.hiitcounter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG

/** Pops everything above the entry list (the start destination). */
fun NavController.popToEntries() {
    popBackStack(Routes.ENTRIES, inclusive = false)
}

/** After create (spec §7.3) and duplicate (§7.5): open the entry with only the list beneath it. */
fun NavController.openEntryOverList(id: Long) {
    navigate(Routes.entry(id)) {
        popUpTo(Routes.ENTRIES)
        launchSingleTop = true
    }
}

/**
 * Leaving the timer (spec §7.2): pop to the `entry/{id}` whose id is the run's [entryId]
 * (TimerController.lastEntryId). If that entry isn't on the back stack (e.g. after the activity
 * was recreated), pop to the list. At most one `entry/{id}` is ever on the stack, so the topmost
 * one is the only candidate.
 */
fun NavController.exitTimer(entryId: Long?) {
    val topEntry = runCatching { getBackStackEntry(Routes.ENTRY) }.getOrNull()
    if (entryId != null && topEntry != null && topEntry.arguments?.getLong(ENTRY_ID_ARG) == entryId) {
        popBackStack(topEntry.destination.id, inclusive = false)
    } else {
        popToEntries()
    }
}

/**
 * The double-tap guard for callbacks that carry an argument: dropUnlessResumed
 * (lifecycle-runtime-compose 2.9.4) only has a zero-arg overload.
 */
@Composable
fun <T> dropUnlessResumedWith(block: (T) -> Unit): (T) -> Unit {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(block)
    return remember(owner) {
        { value: T -> if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) latest(value) }
    }
}
