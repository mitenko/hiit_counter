package com.mitenko.repkit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The timer screen's colours (spec rev 36 §3, §3.2): today's black / white / [HiitColors] in Light and
 * Dark (rev 14 §3), a glowing theme's own in it (rev 39 §10). [bandLine] and [circleOutline] are the elapsed band's grey
 * lines ahead of the fill and the Sets circle's outline (rev 38).
 */
@Immutable
data class TimerColors(
    val ground: Color,
    val text: Color,
    val work: Color,
    val rest: Color,
    val neutral: Color,
    val setRing: Color,
    val track: Color,
    val bandLine: Color,
    val circleOutline: Color,
)

val DarkTimerColors = TimerColors(
    ground = Color.Black, text = Color.White, work = HiitColors.Work, rest = HiitColors.Rest, neutral = HiitColors.Neutral,
    setRing = HiitColors.SetRing, track = HiitColors.Track,
    bandLine = HiitColors.Neutral.copy(alpha = 0.53f), circleOutline = HiitColors.Neutral.copy(alpha = 0.6f),
)

val SonarTimerColors = SonarPalette.timerColors()
