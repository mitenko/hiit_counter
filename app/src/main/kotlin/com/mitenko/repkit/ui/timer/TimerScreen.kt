package com.mitenko.repkit.ui.timer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.ui.common.resolve
import com.mitenko.repkit.ui.theme.HiitColors
import com.mitenko.repkit.ui.theme.HiitTheme

/**
 * Spec rev 14 §3 (deliberate, overturnable): the timer screen stays dark regardless of the
 * Appearance choice, for visibility mid-workout. It already hard-codes black/white for its own
 * text and icons; this wrap only matters for anything that reads [MaterialTheme]'s colour scheme
 * (currently just the cue toggle row's "on" fill), so that it's still the dark scheme's colours
 * even when the rest of the app is light.
 */
@Composable
fun TimerScreen(
    ui: TimerUiState,
    cues: CueConfig,
    onTogglePause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onClose: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleVoice: () -> Unit,
) = HiitTheme(darkTheme = true) {
    val color = when (ui.tone) {
        PhaseTone.WORK -> HiitColors.Work
        PhaseTone.REST -> HiitColors.Rest
        PhaseTone.NEUTRAL -> HiitColors.Neutral
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        IconButton(onClick = onClose, modifier = Modifier.padding(8.dp).testTag("close")) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.stop), tint = Color.White)
        }
        // The run's cues are live (spec revision 7): hidden once the run is DONE, matching the pause
        // button. Spec revision 22: they have their own row under ✕, spread across the width.
        if (!ui.done) {
            CueToggleRow(
                cues = cues,
                onToggleSound = onToggleSound,
                onToggleVibration = onToggleVibration,
                onToggleVoice = onToggleVoice,
                modifier = Modifier.padding(horizontal = 48.dp).testTag("cue_toggle_row"),
            )
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The name frozen at Start (spec §7.6), above the Sets/Elapsed row.
                Text(
                    ui.entryName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("entry_name"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Stat(R.string.sets_label, ui.setsText, testTag = "stat_sets")
                    Stat(R.string.elapsed_label, ui.elapsedText, testTag = "stat_elapsed")
                }
            }
            val description = ui.description.resolve()
            BoxWithConstraints(Modifier.fillMaxWidth(0.85f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                // Dp.toSp() cancels the user's font scale, so the digits always fit the ring.
                val numberSize = with(LocalDensity.current) { (maxWidth * 0.3f).toSp() }
                DualRing(ui.innerProgress, ui.outerProgress, innerColor = color, outerColor = HiitColors.SetRing, modifier = Modifier.fillMaxSize())
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
                ) {
                    ui.label?.let {
                        Text(it.resolve(), color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("phase_label"))
                    }
                    ui.centerNumber?.let {
                        Text(
                            "$it",
                            color = if (ui.centerDimmed) color.copy(alpha = 0.45f) else color,
                            fontSize = numberSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("center_number"),
                        )
                    }
                    Text(ui.countdownText, color = Color.White, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("countdown"))
                }
            }
            if (!ui.done) {
                // Skip back and forward sit either side of Pause (spec revision 10 §5); both work while
                // paused. Spec revision 22: 56 dp apart, so they're harder to hit by mistake.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                    IconButton(onClick = onSkipBack, modifier = Modifier.size(48.dp).testTag("skip_back")) {
                        Icon(painterResource(R.drawable.ic_skip_back), contentDescription = stringResource(R.string.skip_back), tint = Color.White)
                    }
                    FilledIconButton(
                        onClick = onTogglePause,
                        modifier = Modifier.size(72.dp).testTag("pause"),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
                    ) {
                        Icon(
                            painterResource(if (ui.paused) R.drawable.ic_play else R.drawable.ic_pause),
                            contentDescription = stringResource(if (ui.paused) R.string.resume else R.string.pause),
                            tint = Color.Black,
                        )
                    }
                    IconButton(onClick = onSkipForward, modifier = Modifier.size(48.dp).testTag("skip_forward")) {
                        Icon(painterResource(R.drawable.ic_skip_forward), contentDescription = stringResource(R.string.skip_forward), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(@StringRes label: Int, value: String, testTag: String) {
    // Spec revision 33: larger than before (titleSmall/headlineMedium, up from labelLarge/titleLarge).
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag(testTag)) {
        Text(stringResource(label), color = Color.LightGray, style = MaterialTheme.typography.titleSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineMedium)
    }
}

/** Round toggle buttons for Sound, Vibration and Voice (spec revision 7): live, applied at once. */
@Composable
private fun CueToggleRow(
    cues: CueConfig,
    onToggleSound: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CueToggleButton(
            on = cues.sound, icon = R.drawable.ic_cue_sound,
            onDescription = stringResource(R.string.cue_sound_on), offDescription = stringResource(R.string.cue_sound_off),
            onClick = onToggleSound, testTag = "cue_sound",
        )
        CueToggleButton(
            on = cues.vibration, icon = R.drawable.ic_cue_vibration,
            onDescription = stringResource(R.string.cue_vibration_on), offDescription = stringResource(R.string.cue_vibration_off),
            onClick = onToggleVibration, testTag = "cue_vibration",
        )
        CueToggleButton(
            on = cues.voice, icon = R.drawable.ic_cue_voice,
            onDescription = stringResource(R.string.cue_voice_on), offDescription = stringResource(R.string.cue_voice_off),
            onClick = onToggleVoice, testTag = "cue_voice",
        )
    }
}

/**
 * A 48 dp round toggle: on is a filled primary circle, off is an outlined circle with the icon at
 * reduced alpha and a diagonal slash across it.
 */
@Composable
private fun CueToggleButton(
    on: Boolean,
    @DrawableRes icon: Int,
    onDescription: String,
    offDescription: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = Color.White.copy(alpha = 0.5f)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (on) Modifier.background(primary) else Modifier.border(1.dp, outline, CircleShape))
            .toggleable(value = on, onValueChange = { onClick() }, role = Role.Switch)
            .semantics { contentDescription = if (on) onDescription else offDescription }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (on) Color.Black else outline,
            modifier = Modifier.size(24.dp).let { base ->
                if (on) {
                    base
                } else {
                    base.drawWithContent {
                        drawContent()
                        drawLine(color = outline, start = Offset(0f, size.height), end = Offset(size.width, 0f), strokeWidth = 2.dp.toPx())
                    }
                }
            },
        )
    }
}
