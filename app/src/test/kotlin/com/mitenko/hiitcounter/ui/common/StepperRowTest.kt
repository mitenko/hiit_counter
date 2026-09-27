package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.PenaltyDraft
import com.mitenko.hiitcounter.domain.StepRange
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StepperRowTest {
    @get:Rule val compose = createComposeRule()

    private var value by mutableIntStateOf(0)

    private fun showInt(initial: Int, range: StepRange, input: ValueInput, label: String) {
        value = initial
        compose.setContent {
            HiitTheme { Column { IntStepperField(label, value, range, input, onUpdate = { f -> value = f(value) }) } }
        }
    }

    @Test
    fun `plus and minus step and clamp at the hard minimum`() {
        showInt(1, FieldRanges.SETS, ValueInput.WHOLE, "SETS")
        compose.onNodeWithContentDescription("Decrease SETS").performClick()
        assertEquals(1, value)
        compose.onNodeWithContentDescription("Increase SETS").performClick()
        compose.onNodeWithContentDescription("Increase SETS").performClick()
        assertEquals(3, value)
        compose.onNodeWithTag("value_SETS").assertTextEquals("3")
    }

    @Test
    fun `holding a button repeats on the compose clock`() {
        showInt(8, FieldRanges.SETS, ValueInput.WHOLE, "SETS")
        // One step on press, then one every 80 ms after a 400 ms hold (RepeatingIconButton).
        compose.onNodeWithContentDescription("Increase SETS").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        compose.waitForIdle()
        assertTrue("held value was $value", value in 14..20)
    }

    @Test
    fun `tapping the value opens the dialog and OK applies the clamped value`() {
        showInt(10, FieldRanges.PHASE, ValueInput.TIME, "PREPARE")
        compose.onNodeWithTag("value_PREPARE").assertTextEquals("00:10").performClick()
        compose.onNodeWithTag("edit_field").assertTextContains("00:10")
        compose.onNodeWithTag("edit_field").performTextReplacement("90:00")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(3599, value)
        compose.onNodeWithTag("value_PREPARE").assertTextEquals("59:59")
        compose.onNodeWithTag("edit_field").assertDoesNotExist()
    }

    @Test
    fun `bad input keeps OK disabled and the value unchanged`() {
        showInt(10, FieldRanges.PHASE, ValueInput.TIME, "PREPARE")
        compose.onNodeWithTag("value_PREPARE").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("1:60")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
        compose.onNodeWithText("Use m:ss or seconds").assertExists()
        assertEquals(10, value)
    }

    private var penalty by mutableStateOf(PenaltyDraft.of(19.5))

    private fun showPenalty(initial: PenaltyDraft) {
        penalty = initial
        compose.setContent {
            HiitTheme { Column { PenaltyStepperField("PENALTY", penalty, onUpdate = { f -> penalty = f(penalty) }) } }
        }
    }

    @Test
    fun `penalty steps in half hours and keeps an exact dialog value until the next press`() {
        showPenalty(PenaltyDraft.of(19.5))
        compose.onNodeWithContentDescription("Increase PENALTY").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("20")
        compose.onNodeWithTag("value_PENALTY").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("1,3")
        compose.onNodeWithTag("edit_ok").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("1.3")
        assertEquals(1.3, penalty.hours, 0.0)
        compose.onNodeWithContentDescription("Increase PENALTY").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("1.5")
    }

    @Test
    fun `penalty dialog values clamp to the hard range`() {
        showPenalty(PenaltyDraft.of(19.5))
        compose.onNodeWithTag("value_PENALTY").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("0.2")
        compose.onNodeWithTag("edit_ok").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("0.5")
        compose.onNodeWithTag("value_PENALTY").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("5000")
        compose.onNodeWithTag("edit_ok").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("999.5")
    }
}
