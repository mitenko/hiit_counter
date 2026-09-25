package com.mitenko.hiitcounter.ui.timer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    )

    @Test
    fun `work shows reps and no phase label`() {
        compose.setContent { HiitTheme { TimerScreen(ui(Phase.WORK), onTogglePause = {}, onClose = {}) } }
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertTextEquals("8")
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertTextEquals("00:15")
    }

    @Test
    fun `rest shows label and upcoming reps`() {
        compose.setContent { HiitTheme { TimerScreen(ui(Phase.REST), onTogglePause = {}, onClose = {}) } }
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertTextEquals("REST")
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertTextEquals("8")
    }

    @Test
    fun `buttons invoke callbacks`() {
        var toggles = 0
        var closes = 0
        compose.setContent { HiitTheme { TimerScreen(ui(Phase.WORK), onTogglePause = { toggles++ }, onClose = { closes++ }) } }
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
                HiitTheme { TimerScreen(ui(Phase.REST), onTogglePause = {}, onClose = {}) }
            }
        }
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("pause").assertIsDisplayed()
    }
}
