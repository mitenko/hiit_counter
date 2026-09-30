package com.mitenko.hiitcounter.ui.entry

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.model.EntryType

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
            Spacer(Modifier.height(16.dp))
            RepTable(state)
            if (state.checkedInToday) {
                Text(
                    stringResource(R.string.checked_in_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            // Spec R4 §4.1–4.2: a Workout has Check in (outlined) and Start (filled); a check-in-only entry has one Check in.
            // Buttons wait for the entry's first emission, so there's nothing to tap before its type is known.
            if (state.loaded) {
                when (state.type) {
                    EntryType.WORKOUT -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CheckInButton(state, onCheckIn, Modifier.weight(1f))
                        Button(
                            onClick = onStart,
                            enabled = !state.starting && !state.checkingIn,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("start"),
                        ) {
                            Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    EntryType.CHECK_IN -> CheckInButton(state, onCheckIn, Modifier.fillMaxWidth())
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

@Composable
private fun RepTable(state: EntryUiState) {
    val line = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().border(1.dp, line)) {
        // Spec R4 §4.2: a check-in-only entry has no rep rows and no Total Reps row. These also wait for
        // the entry's first emission, so a fresh WORKOUT-default state doesn't flash an empty table.
        if (state.loaded && state.type == EntryType.WORKOUT) {
            state.reps.forEachIndexed { index, reps ->
                Text(
                    "$reps",
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, line).padding(vertical = 8.dp).testTag("rep_$index"),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TableRow(R.string.total_reps, "${state.total}")
        }
        TableRow(R.string.last_check_in, state.lastCheckIn)
        TableRow(R.string.best_streak, "${state.bestStreak}", boldValue = true)
        TableRow(R.string.current_streak, "${state.currentStreak}")
        TableRow(R.string.today, state.today)
    }
}

@Composable
private fun TableRow(@StringRes label: Int, value: String, boldValue: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(
            value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = if (boldValue) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
