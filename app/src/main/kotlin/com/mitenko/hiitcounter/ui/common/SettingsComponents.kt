package com.mitenko.hiitcounter.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), content = content)
        }
    }
}

/** 48 dp stepper button; tap = one step, hold = repeat. */
@Composable
fun RepeatingIconButton(onClick: () -> Unit, @DrawableRes icon: Int, contentDescription: String, modifier: Modifier = Modifier) {
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    coroutineScope {
                        currentOnClick()
                        val repeat = launch {
                            delay(400)
                            while (true) {
                                currentOnClick()
                                delay(80)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.surface)
    }
}

/** The settings cards' shape (spec rev 9 §4): a 16 dp radius. */
val SettingsCardShape = RoundedCornerShape(16.dp)

/**
 * A full-width rounded card on `surfaceContainer` (spec rev 9 §4). It isn't clickable itself: the
 * row inside keeps its own targets (steppers, tap-to-edit, ⓘ, the switch, the Type value).
 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = SettingsCardShape, color = MaterialTheme.colorScheme.surfaceContainer, content = content)
}

/**
 * A labelled switch with the stepper rows' spacing (spec R2 §8.1) and an optional ⓘ after the
 * label (R3 §7.1), in one rounded card tagged `card_<label>` (rev 9 §4). The switch is tagged
 * `switch_<label>`. [supportingText] goes under the row, tagged `support_<label>` (the Voice
 * switch's "not available", R4 §4.7).
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    info: String? = null,
    supportingText: String? = null,
) {
    // Spec rev 9 §4: 4 dp above and below, so neighbouring cards sit 8 dp apart.
    SettingsCard(modifier.padding(vertical = 4.dp).testTag("card_$label")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                info?.let { InfoTag(title = label, text = it) }
                Spacer(Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("switch_$label"))
            }
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("support_$label"),
                )
            }
        }
    }
}
