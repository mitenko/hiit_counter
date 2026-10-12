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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.TimerState
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.ui.theme.HiitTheme
import kotlin.math.sqrt
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

    /** The longest realistic stat values (spec rev 37): "99/99" sets and 75:12 elapsed (no hours). */
    private val longStatsUi = TimerUiMapper.map(
        TimerState(Phase.WORK, set = 99, sets = 99, phaseSecondsLeft = 15, phaseDurationSec = 20, elapsedSec = 4512,
            totalDurationSec = 7200, repsThisSet = 8, totalReps = 65, paused = false),
        entryName = "Kettlebell Lunges",
    )

    /**
     * Spec revision 37: Sets and Elapsed are two circles side by side, at least 24 dp apart, each about
     * 34% of the screen width (shrunk only to keep the gap), with its value inside the circle.
     */
    private fun assertStatCirclesFit(screenWidth: Float) {
        assertEquals("99/99", longStatsUi.setsText)
        assertEquals("75:12", longStatsUi.elapsedText)
        showUi(longStatsUi)
        compose.onNodeWithTag("stat_sets_value", useUnmergedTree = true).assertTextEquals("99/99")
        compose.onNodeWithTag("stat_elapsed_value", useUnmergedTree = true).assertTextEquals("75:12")
        val sets = bounds("stat_sets")
        val elapsed = bounds("stat_elapsed")
        // Side by side on one row, spaced apart, inside the screen.
        assertEquals(sets.top.value, elapsed.top.value, 0.5f)
        assertTrue("gap ${elapsed.left - sets.right}", elapsed.left - sets.right >= 23.5.dp)
        assertTrue(sets.left >= 0.dp)
        assertTrue(elapsed.right <= screenWidth.dp)
        // Round, and about a third of the screen wide.
        listOf(sets, elapsed).forEach { c ->
            assertEquals(c.width.value, (c.bottom - c.top).value, 0.5f)
            assertTrue("circle ${c.width}", c.width.value >= screenWidth * 0.30f && c.width.value <= screenWidth * 0.34f + 0.5f)
        }
        // Each value fits inside its own circle (every corner within the radius, less the outline).
        assertInsideCircle(sets, bounds("stat_sets_value"))
        assertInsideCircle(elapsed, bounds("stat_elapsed_value"))
    }

    private fun assertInsideCircle(circle: DpRect, node: DpRect) {
        val radius = circle.width.value / 2f - 2f
        val cx = ((circle.left + circle.right) / 2f).value
        val cy = ((circle.top + circle.bottom) / 2f).value
        listOf(node.left to node.top, node.right to node.top, node.left to node.bottom, node.right to node.bottom).forEach { (x, y) ->
            val distance = sqrt((x.value - cx) * (x.value - cx) + (y.value - cy) * (y.value - cy))
            assertTrue("corner (${x.value}, ${y.value}) of $node outside circle radius $radius", distance <= radius)
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `the sets and elapsed circles sit apart and hold their values at 320dp`() = assertStatCirclesFit(320f)

    @Test
    fun `the sets and elapsed circles sit apart and hold their values at 411dp`() = assertStatCirclesFit(411f)

    @Test
    fun `the stat circles have no visible labels and read their values to TalkBack`() {
        val ui = TimerUiMapper.map(
            TimerState(Phase.REST, set = 8, sets = 8, phaseSecondsLeft = 7, phaseDurationSec = 10, elapsedSec = 213,
                totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false),
            entryName = "Pushups",
        )
        showUi(ui)
        compose.onNodeWithText("Sets").assertDoesNotExist()
        compose.onNodeWithText("Elapsed").assertDoesNotExist()
        val sets = compose.onNodeWithTag("stat_sets").fetchSemanticsNode()
        assertEquals("Sets 8 of 8", sets.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
        val elapsed = compose.onNodeWithTag("stat_elapsed").fetchSemanticsNode()
        assertEquals("Elapsed 3 minutes 33 seconds", elapsed.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull())
        compose.onNodeWithTag("stat_elapsed_value", useUnmergedTree = true).assertTextEquals("03:33")
        compose.onNodeWithTag("stat_sets_value", useUnmergedTree = true).assertTextEquals("8/8")
    }

    @Test
    fun `the entry name sits close under the cue toggles`() {
        // Spec revision 37: the user asked for the name to move up, nearer the sound buttons.
        setScreen(Phase.WORK)
        val toggles = bounds("cue_toggle_row")
        val name = bounds("entry_name")
        val circles = bounds("stat_sets")
        assertTrue(name.top >= toggles.bottom)
        assertTrue("gap ${name.top - toggles.bottom}", name.top - toggles.bottom <= 16.dp)
        assertTrue(name.bottom <= circles.top)
    }

    @Test
    fun `at done the final count sits at the ring's centre with DONE above it`() {
        setScreen(Phase.DONE)
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertTextEquals("65")
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertTextEquals("DONE")
        val ring = bounds("ring")
        val number = bounds("center_number")
        val label = bounds("phase_label")
        assertEquals(((ring.left + ring.right) / 2f).value, ((number.left + number.right) / 2f).value, 1f)
        assertEquals(((ring.top + ring.bottom) / 2f).value, ((number.top + number.bottom) / 2f).value, 1f)
        assertTrue(label.bottom <= number.top)
        assertInsideRing(ring, label, number)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `label, number and countdown fit inside the ring without overlapping at 320dp`() = assertRingCentreFits()

    @Test
    fun `label, number and countdown fit inside the ring without overlapping at 411dp`() = assertRingCentreFits()

    private fun assertRingCentreFits() {
        // Spec revision 37 (rev 35's sizes reverted): "88" reps, "00:59" countdown and the REST label
        // fit inside the ring's open middle, stacked without overlapping each other.
        val bigUi = TimerUiMapper.map(
            TimerState(Phase.REST, set = 2, sets = 8, phaseSecondsLeft = 59, phaseDurationSec = 60, elapsedSec = 50,
                totalDurationSec = 240, repsThisSet = 88, totalReps = 88, paused = false),
            entryName = "Kettlebell Lunges",
        )
        assertEquals(88, bigUi.centerNumber)
        assertEquals("00:59", bigUi.countdownText)
        compose.setContent {
            HiitTheme {
                TimerScreen(
                    bigUi, CueConfig(),
                    onTogglePause = {}, onSkipBack = {}, onSkipForward = {}, onClose = {},
                    onToggleSound = {}, onToggleVibration = {}, onToggleVoice = {},
                )
            }
        }
        val label = compose.onNodeWithTag("phase_label", useUnmergedTree = true).getBoundsInRoot()
        val number = compose.onNodeWithTag("center_number", useUnmergedTree = true).getBoundsInRoot()
        val countdown = compose.onNodeWithTag("countdown", useUnmergedTree = true).getBoundsInRoot()
        val ring = compose.onNodeWithTag("ring", useUnmergedTree = true).getBoundsInRoot()

        // Not overlapping: the Column stacks them top to bottom with no extra arrangement.
        assertTrue(label.bottom <= number.top)
        assertTrue(number.bottom <= countdown.top)

        // Inside the inner ring's open centre. DualRing.kt: outerStroke = d*0.02, a d*0.03 gap,
        // innerStroke = d*0.07 — the inner ring's open middle has diameter
        // d * (1 - 2*(0.02 + 0.03 + 0.07)) = d * 0.76, centred in the (square) ring box.
        val d = ring.width
        val openDiameterRatio = 1f - 2f * (0.02f + 0.03f + 0.07f)
        val radius = (d * openDiameterRatio / 2f).value
        val centerX = ((ring.left + ring.right) / 2f).value
        val centerY = ((ring.top + ring.bottom) / 2f).value
        listOf(label, number, countdown).forEach { bounds ->
            listOf(
                bounds.left.value to bounds.top.value,
                bounds.right.value to bounds.top.value,
                bounds.left.value to bounds.bottom.value,
                bounds.right.value to bounds.bottom.value,
            ).forEach { (x, y) ->
                val distance = sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
                assertTrue("corner ($x, $y) of $bounds outside radius $radius", distance <= radius)
            }
        }
    }

    private fun showUi(ui: TimerUiState) {
        compose.setContent {
            HiitTheme {
                TimerScreen(
                    ui, CueConfig(),
                    onTogglePause = {}, onSkipBack = {}, onSkipForward = {}, onClose = {},
                    onToggleSound = {}, onToggleVibration = {}, onToggleVoice = {},
                )
            }
        }
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    /** Every corner of each of [nodes] lies inside the inner ring's open centre (rev 37: open diameter d × 0.76). */
    private fun assertInsideRing(ring: DpRect, vararg nodes: DpRect) {
        val radius = (ring.width * (1f - 2f * (0.02f + 0.03f + 0.07f)) / 2f).value
        val cx = ((ring.left + ring.right) / 2f).value
        val cy = ((ring.top + ring.bottom) / 2f).value
        nodes.forEach { b ->
            listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom).forEach { (x, y) ->
                val distance = sqrt((x.value - cx) * (x.value - cx) + (y.value - cy) * (y.value - cy))
                assertTrue("corner (${x.value}, ${y.value}) of $b outside radius $radius", distance <= radius)
            }
        }
    }

    /** The longest realistic centre (rev 37) plus a long weight: "88" reps, "00:59", "112.5 lb". */
    private fun weightUi(phase: Phase) = TimerUiMapper.map(
        TimerState(phase, set = 2, sets = 8, phaseSecondsLeft = 59, phaseDurationSec = 60, elapsedSec = 50,
            totalDurationSec = 240, repsThisSet = 88, totalReps = 88, paused = false),
        entryName = "Kettlebell Lunges",
        weight = SetWeight(11_250, WeightUnit.LB),
    )

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `a long weight fits over the reps inside the ring during work at 320dp`() {
        showUi(weightUi(Phase.WORK))
        compose.onNodeWithTag("center_weight", useUnmergedTree = true).assertTextEquals("112.5 lb")
        val weight = bounds("center_weight")
        val number = bounds("center_number")
        val countdown = bounds("countdown")
        assertTrue(weight.bottom <= number.top)
        assertTrue(number.bottom <= countdown.top)
        assertInsideRing(bounds("ring"), weight, number, countdown)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `a long weight shares the rest label's line inside the ring at 320dp`() = assertRestWeightFits()

    @Test
    fun `a long weight shares the rest label's line inside the ring at 411dp`() = assertRestWeightFits()

    private fun assertRestWeightFits() {
        showUi(weightUi(Phase.REST))
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertTextEquals("REST")
        compose.onNodeWithTag("center_weight", useUnmergedTree = true).assertTextEquals("112.5 lb")
        val label = bounds("phase_label")
        val weight = bounds("center_weight")
        val number = bounds("center_number")
        val countdown = bounds("countdown")
        assertTrue(label.right <= weight.left)
        assertTrue(label.bottom <= number.top && weight.bottom <= number.top)
        assertTrue(number.bottom <= countdown.top)
        assertInsideRing(bounds("ring"), label, weight, number, countdown)
    }

    @Test
    fun `a Reps run shows no weight`() {
        setScreen(Phase.WORK)
        compose.onNodeWithTag("center_weight", useUnmergedTree = true).assertDoesNotExist()
    }
}
