package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.navigation.Routes

@Composable
fun SettingsListScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val entries = listOf(
        R.string.settings_timing to Routes.SETTINGS_TIMING,
        R.string.settings_progression to Routes.SETTINGS_PROGRESSION,
        R.string.settings_current_state to Routes.SETTINGS_CURRENT,
        R.string.settings_cues to Routes.SETTINGS_CUES,
    )
    SettingsScaffold(title = stringResource(R.string.settings), onBack = onBack) {
        entries.forEach { (label, route) ->
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(route) }.padding(vertical = 16.dp).testTag(route),
            )
            HorizontalDivider()
        }
    }
}
