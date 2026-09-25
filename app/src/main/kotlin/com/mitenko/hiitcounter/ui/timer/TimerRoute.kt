package com.mitenko.hiitcounter.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.RunStatus

@Composable
fun TimerRoute(onExit: () -> Unit, vm: TimerViewModel = hiltViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }

    LaunchedEffect(status) { if (status == RunStatus.IDLE) onExit() }
    KeepScreenOn(enabled = status != RunStatus.DONE)
    NotificationPermissionRequest(vm)

    // Running: confirm before stopping. DONE: leave without confirmation (spec §9.2).
    val onClose: () -> Unit = {
        if (status == RunStatus.DONE) {
            vm.leaveDone()
        } else {
            confirmStop = true
        }
    }
    BackHandler(onBack = onClose)

    ui?.let { TimerScreen(it, onTogglePause = vm::togglePause, onClose = onClose) }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.stop_workout_title)) },
            text = { Text(stringResource(R.string.stop_workout_body)) },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; vm.stop() }) { Text(stringResource(R.string.stop)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Asks for POST_NOTIFICATIONS once, after the workout has started (spec §4 step 6). */
@Composable
private fun NotificationPermissionRequest(vm: TimerViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val asked by vm.notificationPermissionAsked.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(asked) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!asked && !granted) {
            vm.onNotificationPermissionAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
