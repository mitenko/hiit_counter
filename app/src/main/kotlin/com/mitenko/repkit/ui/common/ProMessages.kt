package com.mitenko.repkit.ui.common

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mitenko.repkit.R
import com.mitenko.repkit.monetisation.ProEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Spec rev 41 §3: the one place billing outcomes show, as a snackbar over whichever screen is current (Go Pro
 * can start from the list, the Settings dialog or Entry Settings › Duplicate). A newer event replaces the one
 * on screen, so the store never waits on a slow reader.
 */
@Composable
fun ProMessageHost(events: Flow<ProEvent>, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val texts by rememberUpdatedState(
        mapOf(
            ProEvent.BILLING_UNAVAILABLE to stringResource(R.string.billing_unavailable),
            ProEvent.RESTORED to stringResource(R.string.restore_ok),
            ProEvent.NOTHING_TO_RESTORE to stringResource(R.string.restore_none),
        ),
    )
    LaunchedEffect(events) {
        events.collectLatest { event -> texts[event]?.let { snackbar.showSnackbar(it) } }
    }
    SnackbarHost(snackbar, modifier)
}
