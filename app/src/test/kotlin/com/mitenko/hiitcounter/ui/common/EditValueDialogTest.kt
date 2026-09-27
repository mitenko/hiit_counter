package com.mitenko.hiitcounter.ui.common

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.ValueFormat
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EditValueDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the prefilled value can be replaced and confirmed`() {
        var confirmed: Int? = null
        compose.setContent {
            HiitTheme {
                EditValueDialog("PREPARE", "00:10", ValueInput.TIME, ValueFormat::parseSeconds, onConfirm = { confirmed = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("edit_field").assertTextContains("00:10")
        compose.onNodeWithTag("edit_ok").assertIsEnabled()
        compose.onNodeWithTag("edit_field").performTextReplacement("1:30")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(90, confirmed)
    }

    @Test
    fun `an unparseable time disables OK and explains why`() {
        compose.setContent {
            HiitTheme { EditValueDialog("PREPARE", "00:10", ValueInput.TIME, ValueFormat::parseSeconds, onConfirm = {}, onDismiss = {}) }
        }
        compose.onNodeWithTag("edit_field").performTextReplacement("1:5")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
        compose.onNodeWithText("Use m:ss or seconds").assertExists()
    }

    @Test
    fun `decimals accept a comma and reject garbage`() {
        var confirmed: Double? = null
        compose.setContent {
            HiitTheme {
                EditValueDialog("PENALTY", "19.5", ValueInput.DECIMAL, ValueFormat::parseDecimal, onConfirm = { confirmed = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("edit_field").performTextReplacement("abc")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
        compose.onNodeWithText("Enter a number").assertExists()
        compose.onNodeWithTag("edit_field").performTextReplacement("12,5")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(12.5, confirmed!!, 0.0)
    }
}
