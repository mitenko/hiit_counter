package com.mitenko.hiitcounter.ui.timer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimerScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun ui(phase: Phase) = TimerUiMapper.map(
        TimerState(phase, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20, elapsedSec = 50,
            totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false),
        entryName = "Kettlebell Lunges",
    )

    private fun setScreen(
        phase: Phase = Phase.WORK,
        cues: CueConfig = CueConfig(),
        onTogglePause: () -> Unit = {},
        onClose: () -> Unit = {},
        onToggleSound: () -> Unit = {},
        onToggleVibration: () -> Unit = {},
        onToggleVoice: () -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                TimerScreen(
                    ui(phase), cues,
                    onTogglePause = onTogglePause, onClose = onClose,
                    onToggleSound = onToggleSound, onToggleVibration = onToggleVibration, onToggleVoice = onToggleVoice,
                )
            }
        }
    }

    @Test
    fun `work shows reps and no phase label`() {
        setScreen(Phase.WORK)
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertTextEquals("8")
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertTextEquals("00:15")
    }

    @Test
    fun `rest shows label and upcoming reps`() {
        setScreen(Phase.REST)
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertTextEquals("REST")
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertTextEquals("8")
    }

    @Test
    fun `buttons invoke callbacks`() {
        var toggles = 0
        var closes = 0
        setScreen(Phase.WORK, onTogglePause = { toggles++ }, onClose = { closes++ })
        compose.onNodeWithTag("pause").performClick()
        compose.onNodeWithTag("close").performClick()
        assertEquals(1, toggles)
        assertEquals(1, closes)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun `centre and controls stay visible at 200 percent font scale`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                HiitTheme {
                    TimerScreen(
                        ui(Phase.REST), CueConfig(),
                        onTogglePause = {}, onClose = {}, onToggleSound = {}, onToggleVibration = {}, onToggleVoice = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("pause").assertIsDisplayed()
    }

    @Test
    fun `shows the frozen entry name above the stats`() {
        setScreen(Phase.WORK)
        compose.onNodeWithTag("entry_name").assertTextEquals("Kettlebell Lunges")
    }

    @Test
    fun `cue toggle buttons exist and are at least 48dp`() {
        setScreen(Phase.WORK, cues = CueConfig(sound = true, vibration = true, voice = false))
        compose.onNodeWithTag("cue_sound", useUnmergedTree = true).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithTag("cue_vibration", useUnmergedTree = true).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithTag("cue_voice", useUnmergedTree = true).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun `cue toggle content descriptions reflect state`() {
        setScreen(Phase.WORK, cues = CueConfig(sound = true, vibration = false, voice = true))
        val sound = compose.onNodeWithTag("cue_sound", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Sound on", sound.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
        val vibration = compose.onNodeWithTag("cue_vibration", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Vibration off", vibration.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
        val voice = compose.onNodeWithTag("cue_voice", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Voice on", voice.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
    }

    @Test
    fun `a click on a cue toggle invokes its callback`() {
        var sound = 0
        var vibration = 0
        var voice = 0
        setScreen(Phase.WORK, onToggleSound = { sound++ }, onToggleVibration = { vibration++ }, onToggleVoice = { voice++ })
        compose.onNodeWithTag("cue_sound", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("cue_vibration", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("cue_voice", useUnmergedTree = true).performClick()
        assertEquals(1, sound)
        assertEquals(1, vibration)
        assertEquals(1, voice)
    }

    @Test
    fun `cue toggles are hidden at done`() {
        setScreen(Phase.DONE)
        compose.onNodeWithTag("cue_sound", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cue_vibration", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("cue_voice", useUnmergedTree = true).assertDoesNotExist()
    }
}
