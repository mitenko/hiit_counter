package com.mitenko.hiitcounter.ui.timer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.theme.HiitColors

@Composable
fun TimerScreen(ui: TimerUiState, onTogglePause: () -> Unit, onClose: () -> Unit) {
    val color = when (ui.tone) {
        PhaseTone.WORK -> HiitColors.Work
        PhaseTone.REST -> HiitColors.Rest
        PhaseTone.NEUTRAL -> HiitColors.Neutral
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).testTag("close")) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.stop), tint = Color.White)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Stat(R.string.sets_label, ui.setsText)
                Stat(R.string.elapsed_label, ui.elapsedText)
            }
            BoxWithConstraints(Modifier.fillMaxWidth(0.85f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                // Dp.toSp() cancels the user's font scale, so the digits always fit the ring.
                val numberSize = with(LocalDensity.current) { (maxWidth * 0.3f).toSp() }
                DualRing(ui.innerProgress, ui.outerProgress, innerColor = color, outerColor = HiitColors.SetRing, modifier = Modifier.fillMaxSize())
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = ui.description },
                ) {
                    ui.label?.let {
                        Text(it, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("phase_label"))
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
            }
        }
    }
}

@Composable
private fun Stat(@StringRes label: Int, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(label), color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}
