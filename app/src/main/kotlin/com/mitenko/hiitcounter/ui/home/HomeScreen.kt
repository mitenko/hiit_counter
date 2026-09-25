package com.mitenko.hiitcounter.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R

@Composable
fun HomeRoute(onOpenSettings: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    HomeScreen(state, onStart = vm::onStart, onOpenSettings = onOpenSettings, onDismissError = vm::dismissError)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStart: () -> Unit,
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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings")) {
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
            Button(
                onClick = onStart,
                enabled = !state.starting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("start"),
            ) {
                Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RepTable(state: HomeUiState) {
    val line = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().border(1.dp, line)) {
        state.reps.forEachIndexed { index, reps ->
            Text(
                "$reps",
                modifier = Modifier.fillMaxWidth().border(0.5.dp, line).padding(vertical = 8.dp).testTag("rep_$index"),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        TableRow(R.string.total_reps, "${state.total}")
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
