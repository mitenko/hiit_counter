package com.mitenko.repkit.ui.timer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.TimerState
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
// A real phone size (Pixel-class, portrait): Robolectric's 320 x 470 dp default is smaller than any
// phone the app targets, and the timer's controls are laid out for a phone screen (spec rev 22).
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
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
        onSkipBack: () -> Unit = {},
        onSkipForward: () -> Unit = {},
        onClose: () -> Unit = {},
        onToggleSound: () -> Unit = {},
        onToggleVibration: () -> Unit = {},
        onToggleVoice: () -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                TimerScreen(
                    ui(phase), cues,
                    onTogglePause = onTogglePause, onSkipBack = onSkipBack, onSkipForward = onSkipForward, onClose = onClose,
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
                        onTogglePause = {}, onSkipBack = {}, onSkipForward = {}, onClose = {},
                        onToggleSound = {}, onToggleVibration = {}, onToggleVoice = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("pause").assertIsDisplayed()
        compose.onNodeWithTag("skip_back").assertIsDisplayed()
        compose.onNodeWithTag("skip_forward").assertIsDisplayed()
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

    @Test
    fun `skip buttons exist, are at least 48dp and carry their descriptions`() {
        setScreen(Phase.WORK)
        compose.onNodeWithTag("skip_back").assertWidthIsAtLeast(48.dp)
        compose.onNodeWithTag("skip_forward").assertWidthIsAtLeast(48.dp)
        val back = compose.onNodeWithTag("skip_back").fetchSemanticsNode()
        assertEquals("Back", back.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
        val forward = compose.onNodeWithTag("skip_forward").fetchSemanticsNode()
        assertEquals("Skip forward", forward.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
    }

    @Test
    fun `skip buttons sit on either side of pause`() {
        setScreen(Phase.WORK)
        val back = compose.onNodeWithTag("skip_back").getBoundsInRoot()
        val pause = compose.onNodeWithTag("pause").getBoundsInRoot()
        val forward = compose.onNodeWithTag("skip_forward").getBoundsInRoot()
        assertTrue(back.right <= pause.left)
        assertTrue(forward.left >= pause.right)
    }

    @Test
    fun `skip buttons sit at least 48dp from pause`() {
        // User, 2026-10-03: more space between the < pause > buttons.
        setScreen(Phase.WORK)
        val back = compose.onNodeWithTag("skip_back").getBoundsInRoot()
        val pause = compose.onNodeWithTag("pause").getBoundsInRoot()
        val forward = compose.onNodeWithTag("skip_forward").getBoundsInRoot()
        assertTrue(pause.left - back.right >= 47.5.dp)
        assertTrue(forward.left - pause.right >= 47.5.dp)
    }

    @Test
    fun `cue toggles have their own row below close, spread across the width`() {
        // User, 2026-10-03: the sound buttons go on their own row, spaced between.
        setScreen(Phase.WORK)
        val close = compose.onNodeWithTag("close").getBoundsInRoot()
        val sound = compose.onNodeWithTag("cue_sound").getBoundsInRoot()
        val vibration = compose.onNodeWithTag("cue_vibration").getBoundsInRoot()
        val voice = compose.onNodeWithTag("cue_voice").getBoundsInRoot()
        val row = compose.onNodeWithTag("cue_toggle_row").getBoundsInRoot()
        assertTrue(sound.top >= close.bottom)
        // Spread out: the gaps between buttons are wider than a button, and equal.
        val gap1 = vibration.left - sound.right
        val gap2 = voice.left - vibration.right
        assertTrue(gap1 > sound.width)
        assertEquals(gap1.value, gap2.value, 1f)
        // The middle button is centred in the row.
        assertEquals(((row.left + row.right) / 2).value, ((vibration.left + vibration.right) / 2).value, 1f)
    }

    @Test
    fun `skip buttons invoke their callbacks`() {
        var backClicks = 0
        var forwardClicks = 0
        setScreen(Phase.WORK, onSkipBack = { backClicks++ }, onSkipForward = { forwardClicks++ })
        compose.onNodeWithTag("skip_back").performClick()
        compose.onNodeWithTag("skip_forward").performClick()
        assertEquals(1, backClicks)
        assertEquals(1, forwardClicks)
    }

    @Test
    fun `skip buttons are hidden at done`() {
        setScreen(Phase.DONE)
        compose.onNodeWithTag("skip_back", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("skip_forward", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `sets and elapsed stats fit side by side at 320dp with the longest realistic values`() {
        // Spec revision 33: larger stat text (headlineMedium/titleSmall) must still fit at 320dp.
        // "99/99" and the formatHms-produced "01:59:59" are the longest realistic values.
        val longUi = TimerUiMapper.map(
            TimerState(Phase.WORK, set = 99, sets = 99, phaseSecondsLeft = 15, phaseDurationSec = 20, elapsedSec = 7199,
                totalDurationSec = 7200, repsThisSet = 8, totalReps = 65, paused = false),
            entryName = "Kettlebell Lunges",
        )
        assertEquals("99/99", longUi.setsText)
        assertEquals("01:59:59", longUi.elapsedText)
        compose.setContent {
            HiitTheme {
                TimerScreen(
                    longUi, CueConfig(),
                    onTogglePause = {}, onSkipBack = {}, onSkipForward = {}, onClose = {},
                    onToggleSound = {}, onToggleVibration = {}, onToggleVoice = {},
                )
            }
        }
        val sets = compose.onNodeWithTag("stat_sets").getBoundsInRoot()
        val elapsed = compose.onNodeWithTag("stat_elapsed").getBoundsInRoot()
        // Side by side, not overlapping, and neither clipped by the 320dp root.
        assertTrue(sets.right <= elapsed.left)
        assertTrue(sets.left >= 0.dp)
        assertTrue(elapsed.right <= 320.dp)
    }
}
