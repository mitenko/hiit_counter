package com.mitenko.repkit.ui.timer

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.ui.common.reducedMotionEnabled
import com.mitenko.repkit.ui.common.resolve
import com.mitenko.repkit.ui.theme.HiitTheme
import com.mitenko.repkit.ui.theme.LocalAppTheme
import com.mitenko.repkit.ui.theme.LocalGlow
import com.mitenko.repkit.ui.theme.LocalTimerColors
import com.mitenko.repkit.ui.theme.timerTheme
import kotlin.math.min
import kotlin.math.roundToInt

/** The timer's own theme (spec rev 14 §3, rev 36 §3): dark from Light or Dark, else the glowing theme's own. */
@Composable
internal fun TimerTheme(content: @Composable () -> Unit) = HiitTheme(timerTheme(LocalAppTheme.current), content)

/**
 * Spec rev 14 §3 / rev 36 §3: the timer screen stays dark in Light and Dark, and takes Sonar's own
 * colours in Sonar. The wrap also sets the Material scheme (the cue toggles' "on" fill) to match.
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
) = TimerTheme {
    val tc = LocalTimerColors.current
    val color = when (ui.tone) {
        PhaseTone.WORK -> tc.work
        PhaseTone.REST -> tc.rest
        PhaseTone.NEUTRAL -> tc.neutral
    }
    Column(Modifier.fillMaxSize().background(tc.ground)) {
        IconButton(onClick = onClose, modifier = Modifier.padding(8.dp).testTag("close")) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.stop), tint = tc.text)
        }
        // The run's cues are live (spec revision 7): hidden once the run is DONE, matching the pause
        // button. Spec revision 22: they have their own row under ✕, spread across the width.
        if (!ui.done) {
            CueToggleRow(
                cues = cues,
                captionColor = tc.text,
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
            color = tc.text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp)
                .testTag("entry_name"),
        )
        // Spec revision 38: the ring sits 32 dp under the stats band and the spare room goes above the
        // controls, instead of SpaceEvenly opening a gap between the stats and the ring.
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatBand(ui)
            Spacer(Modifier.height(32.dp))
            val description = ui.description.resolve()
            // Spec revision 37 reverts rev 35's sizes: a 0.85 ring box, a 0.3 rep number and a
            // displaySmall countdown, all inside the ring's open centre.
            // Spec rev 42 §2: the captions add height, so the ring (not the controls) gives way when the
            // screen is short: a square of at most 0.85 of the width, capped by the height left over.
            // The spare room on a tall screen goes under the ring, above the controls.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                BoxWithConstraints(
                    Modifier.layout { measurable, c ->
                        val side = min((c.maxWidth * 0.85f).roundToInt(), c.maxHeight)
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(side, side) { placeable.place(0, 0) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    // Dp.toSp() cancels the user's font scale, so the digits always fit the ring.
                    val numberSize = with(LocalDensity.current) { (maxWidth * 0.3f).toSp() }
                    val clockSize = with(LocalDensity.current) { (maxWidth * 0.22f).toSp() }
                    DualRing(
                        ui.innerProgress, ui.outerProgress, innerColor = color, outerColor = tc.setRing,
                        trackColor = tc.track, haloAlpha = LocalGlow.current?.ringHalo,
                        modifier = Modifier.fillMaxSize().testTag("ring"),
                    )
                    val weight = ui.weight
                    val label = ui.label
                    val centerNumber = ui.centerNumber
                    val centerCountdown = ui.centerCountdown
                    val nextText = ui.nextText
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
                        number = if (centerCountdown != null) {
                            {
                                // Spec rev 42 §3: PREPARE's countdown is the big number; m:ss is wider, so it's smaller.
                                Text(
                                    centerCountdown,
                                    color = color,
                                    style = tightTextStyle(if (':' in centerCountdown) clockSize else numberSize, FontWeight.Bold),
                                    maxLines = 1,
                                    modifier = Modifier.testTag("center_countdown"),
                                )
                            }
                        } else {
                            centerNumber?.let { n ->
                                {
                                    Text(
                                        "$n",
                                        color = if (ui.centerDimmed) color.copy(alpha = 0.45f) else color,
                                        style = tightTextStyle(numberSize, FontWeight.Bold),
                                        modifier = Modifier.testTag("center_number"),
                                    )
                                }
                            }
                        },
                        bottom = if (nextText != null) {
                            {
                                // Spec rev 42 §3: capped to the ring's opening (about 0.6 of its side this low) on one
                                // line; a long Next ("Next: 88 · 112.5 lb", a wide locale, a big font scale) shrinks to fit.
                                val titleMedium = MaterialTheme.typography.titleMedium
                                BasicText(
                                    nextText.resolve(),
                                    style = titleMedium.copy(color = tc.text.copy(alpha = 0.7f)),
                                    maxLines = 1,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = titleMedium.fontSize),
                                    modifier = Modifier.widthIn(max = maxWidth * 0.6f).testTag("next_text"),
                                )
                            }
                        } else if (ui.countdownText.isEmpty()) {
                            null
                        } else {
                            {
                                Text(
                                    ui.countdownText,
                                    color = tc.text,
                                    style = MaterialTheme.typography.displaySmall,
                                    modifier = Modifier.testTag("countdown"),
                                )
                            }
                        },
                    )
                }
            }
            if (!ui.done) {
                // Skip back and forward sit either side of Pause (spec revision 10 §5); both work while
                // paused. Spec revision 22: 56 dp apart, so they're harder to hit by mistake.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onSkipBack, modifier = Modifier.size(48.dp).testTag("skip_back")) {
                            Icon(painterResource(R.drawable.ic_skip_back), contentDescription = stringResource(R.string.skip_back), tint = tc.text)
                        }
                        ControlCaption(stringResource(R.string.skip_back), tc.text)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        ControlCaption(stringResource(if (ui.paused) R.string.resume else R.string.pause), tc.text)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = onSkipForward, modifier = Modifier.size(48.dp).testTag("skip_forward")) {
                            Icon(painterResource(R.drawable.ic_skip_forward), contentDescription = stringResource(R.string.skip_forward), tint = tc.text)
                        }
                        ControlCaption(stringResource(R.string.skip_label), tc.text)
                    }
                }
            }
        }
    }
}

/** The phase label over the number: WORK, REST, GET READY, COOLDOWN or DONE (spec revision 38 §2.4). */
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
 * Sets and Elapsed (spec revision 38): the Sets circle sits at the left end of a band of two full-width
 * lines, with Elapsed large between the lines to its right. Behind Elapsed, a blue fill grows from just
 * inside the circle's right edge by [TimerUiState.elapsedProgress], with a bright leading edge; the lines
 * are lit blue up to the fill's edge (so the short pieces left of the circle are always lit) and grey
 * ahead of it. The fill glides between the once-a-second updates, or steps with Remove animations on.
 * Neither value has a visible label; each carries its own TalkBack description.
 */
@Composable
private fun StatBand(ui: TimerUiState) {
    val tc = LocalTimerColors.current
    val setsDescription = ui.setsDescription.resolve()
    val elapsedDescription = ui.elapsedDescription.resolve()
    val reducedMotion = reducedMotionEnabled()
    // Read only while drawing and placing, never in composition: the tween runs for the whole workout,
    // and a composition read would recompose the band every frame instead of once a second.
    val progress = animateFloatAsState(
        targetValue = ui.elapsedProgress,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 1000, easing = LinearEasing),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val circle = maxWidth * 0.28f
        val bandHeight = circle * 0.84f
        val fillStart = circle * 0.86f
        val setsSize = with(LocalDensity.current) { (circle * 0.3f).toSp() }
        val elapsedSize = with(LocalDensity.current) { (circle * 0.6f).toSp() }
        Box(Modifier.fillMaxWidth().height(circle), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bandHeight)
                    .drawBehind {
                        val start = fillStart.toPx()
                        val litEnd = start + (size.width - start) * progress.value
                        if (litEnd > start) {
                            drawRect(
                                Brush.horizontalGradient(
                                    listOf(tc.setRing.copy(alpha = 0.08f), tc.setRing.copy(alpha = 0.25f)),
                                    startX = start, endX = litEnd,
                                ),
                                topLeft = Offset(start, 0f), size = Size(litEnd - start, size.height),
                            )
                            val edge = min(2.dp.toPx(), litEnd - start)
                            drawRect(tc.setRing, topLeft = Offset(litEnd - edge, 0f), size = Size(edge, size.height))
                        }
                        val stroke = 1.5.dp.toPx()
                        listOf(stroke / 2, size.height - stroke / 2).forEach { y ->
                            drawLine(tc.bandLine, Offset(litEnd, y), Offset(size.width, y), stroke)
                            drawLine(tc.setRing, Offset(0f, y), Offset(litEnd, y), stroke)
                        }
                    }
                    .testTag("stat_band"),
            ) {
                // An empty node over the drawn fill, so tests can measure it; placed, never composed, per frame.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .layout { measurable, constraints ->
                            val start = fillStart.roundToPx()
                            val width = ((constraints.maxWidth - start) * progress.value).roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(start, 0) }
                        }
                        .testTag("stat_fill"),
                )
            }
            Box(
                Modifier
                    .size(circle)
                    .background(tc.ground, CircleShape)
                    .border(2.dp, tc.circleOutline, CircleShape)
                    .semantics(mergeDescendants = true) { contentDescription = setsDescription }
                    .testTag("stat_sets"),
                contentAlignment = Alignment.Center,
            ) {
                StatValue(ui.setsText, setsSize, tc.text, testTag = "stat_sets_value")
            }
            Box(
                Modifier
                    .padding(start = circle)
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = elapsedDescription }
                    .testTag("stat_elapsed"),
                contentAlignment = Alignment.Center,
            ) {
                StatValue(ui.elapsedText, elapsedSize, tc.text, testTag = "stat_elapsed_value")
            }
        }
    }
}

@Composable
private fun StatValue(value: String, size: TextUnit, color: Color, testTag: String) {
    Text(value, color = color, style = tightTextStyle(size), maxLines = 1, modifier = Modifier.testTag(testTag))
}

/** Round toggle buttons for Sound, Vibration and Voice (spec revision 7): live, applied at once. */
@Composable
private fun CueToggleRow(
    cues: CueConfig,
    onToggleSound: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleVoice: () -> Unit,
    captionColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CueToggleButton(
                on = cues.sound, icon = R.drawable.ic_cue_sound,
                onDescription = stringResource(R.string.cue_sound_on), offDescription = stringResource(R.string.cue_sound_off),
                onClick = onToggleSound, testTag = "cue_sound",
            )
            ControlCaption(stringResource(R.string.cue_label_sound), captionColor)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CueToggleButton(
                on = cues.vibration, icon = R.drawable.ic_cue_vibration,
                onDescription = stringResource(R.string.cue_vibration_on), offDescription = stringResource(R.string.cue_vibration_off),
                onClick = onToggleVibration, testTag = "cue_vibration",
            )
            ControlCaption(stringResource(R.string.cue_label_vibration), captionColor)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CueToggleButton(
                on = cues.voice, icon = R.drawable.ic_cue_voice,
                onDescription = stringResource(R.string.cue_voice_on), offDescription = stringResource(R.string.cue_voice_off),
                onClick = onToggleVoice, testTag = "cue_voice",
            )
            ControlCaption(stringResource(R.string.cue_label_voice), captionColor)
        }
    }
}

/** Spec rev 42 §2: a visible caption under a timer control; screen readers skip it (the control says its state). */
@Composable
private fun ControlCaption(text: String, color: Color) {
    Text(
        text,
        color = color.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp).semantics { hideFromAccessibility() },
    )
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
    val outline = LocalTimerColors.current.text.copy(alpha = 0.5f)
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
