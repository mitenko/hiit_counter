package com.mitenko.repkit.ui.timer

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
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
        // The name frozen at Start (spec §7.6). Spec revision 37: it sits just under the cue toggles
        // (12 dp) instead of being floated down by the SpaceEvenly column below.
        Text(
            ui.entryName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp)
                .testTag("entry_name"),
        )
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCircles(ui)
            val description = ui.description.resolve()
            // Spec revision 37 reverts rev 35's sizes: a 0.85 ring box, a 0.3 rep number and a
            // displaySmall countdown, all inside the ring's open centre.
            BoxWithConstraints(Modifier.fillMaxWidth(0.85f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                // Dp.toSp() cancels the user's font scale, so the digits always fit the ring.
                val numberSize = with(LocalDensity.current) { (maxWidth * 0.3f).toSp() }
                DualRing(
                    ui.innerProgress, ui.outerProgress, innerColor = color, outerColor = HiitColors.SetRing,
                    modifier = Modifier.fillMaxSize().testTag("ring"),
                )
                val weight = ui.weight
                val label = ui.label
                val centerNumber = ui.centerNumber
                RingCentre(
                    modifier = Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = description },
                    top = if (label == null && weight == null) {
                        null
                    } else {
                        {
                            // Spec rev 26 §4, plan Spec note 56: the weight takes the label's line (after "REST" in
                            // the rest preview), so the stack is no taller and still fits the ring.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                label?.let { PhaseLabel(it.resolve(), color) }
                                weight?.let {
                                    Text(
                                        it.resolve(),
                                        color = color,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        modifier = Modifier.testTag("center_weight"),
                                    )
                                }
                            }
                        }
                    },
                    number = centerNumber?.let { n ->
                        {
                            Text(
                                "$n",
                                color = if (ui.centerDimmed) color.copy(alpha = 0.45f) else color,
                                style = tightTextStyle(numberSize, FontWeight.Bold),
                                modifier = Modifier.testTag("center_number"),
                            )
                        }
                    },
                    bottom = if (ui.countdownText.isEmpty()) {
                        null
                    } else {
                        {
                            Text(
                                ui.countdownText,
                                color = Color.White,
                                style = MaterialTheme.typography.displaySmall,
                                modifier = Modifier.testTag("countdown"),
                            )
                        }
                    },
                )
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

/** The phase label over the number (WORK has none). */
@Composable
private fun PhaseLabel(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("phase_label"))
}

/**
 * The ring's centre (spec revision 37): the number always sits at the ring's centre, the label line
 * just above it and the countdown just below, so a missing countdown (DONE) never pulls the number
 * off-centre. With no number (COOLDOWN, a Timer only DONE) the label and countdown are centred as a
 * stack. Takes the whole ring box, so each part is centred horizontally on the ring.
 */
@Composable
private fun RingCentre(
    top: (@Composable () -> Unit)?,
    number: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(top ?: {}, number ?: {}, bottom ?: {}), modifier = modifier) { (tops, numbers, bottoms), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val t = tops.firstOrNull()?.measure(loose)
        val n = numbers.firstOrNull()?.measure(loose)
        val b = bottoms.firstOrNull()?.measure(loose)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            val topHeight = t?.height ?: 0
            val numberTop = if (n != null) {
                (height - n.height) / 2
            } else {
                (height - topHeight - (b?.height ?: 0)) / 2 + topHeight
            }
            t?.placeRelative((width - t.width) / 2, numberTop - topHeight)
            n?.placeRelative((width - n.width) / 2, numberTop)
            b?.placeRelative((width - b.width) / 2, numberTop + (n?.height ?: 0))
        }
    }
}

/**
 * A tight line height (no extra font-padding leading), so a large number claims no more vertical
 * space than its glyphs: the ring's rep number (spec revision 35) and the stat circles' values (37).
 */
private fun tightTextStyle(fontSize: TextUnit, fontWeight: FontWeight? = null) = TextStyle(
    fontSize = fontSize,
    lineHeight = fontSize,
    fontWeight = fontWeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
)

/**
 * Sets and Elapsed as two outlined circles side by side (spec revision 37), with no visible labels;
 * each circle carries its own TalkBack description. A circle is 34% of the screen width, shrunk only
 * where that would leave less than 24 dp between and beside them (SpaceEvenly). The value is 0.3 of
 * the circle (about 42 sp on a 411 dp phone), as [Dp] converted to sp so it fits at any font scale.
 */
@Composable
private fun StatCircles(ui: TimerUiState) {
    val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minGap = 24.dp
        val circle = min(screenWidth * 0.34f, (maxWidth - minGap * 3) / 2)
        val valueSize = with(LocalDensity.current) { (circle * 0.3f).toSp() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatCircle(ui.setsText, ui.setsDescription.resolve(), circle, valueSize, testTag = "stat_sets")
            StatCircle(ui.elapsedText, ui.elapsedDescription.resolve(), circle, valueSize, testTag = "stat_elapsed")
        }
    }
}

@Composable
private fun StatCircle(value: String, description: String, size: Dp, valueSize: TextUnit, testTag: String) {
    Box(
        Modifier
            .size(size)
            .border(2.dp, HiitColors.Neutral.copy(alpha = 0.6f), CircleShape)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value,
            color = Color.White,
            style = tightTextStyle(valueSize),
            maxLines = 1,
            modifier = Modifier.testTag("${testTag}_value"),
        )
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
