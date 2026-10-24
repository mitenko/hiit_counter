package com.mitenko.repkit.ui.common

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Spec rev 42 §1: the empty-name error waits for the first edit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NameDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun show(initial: String, onConfirm: (String) -> Unit = {}) = compose.setContent {
        HiitTheme { NameDialog(title = "New workout", initial = initial, onConfirm = onConfirm, onDismiss = {}) }
    }

    @Test fun `a new empty dialog shows the counter, not the error, and OK is disabled`() {
        show("")
        compose.onNodeWithText("0/40").assertExists()
        compose.onNodeWithText("Enter a name").assertDoesNotExist()
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
    }

    @Test fun `moving the cursor in an empty dialog does not show the error`() {
        show("")
        compose.onNodeWithTag("name_field").performTouchInput { click(center) }
        compose.onNodeWithText("Enter a name").assertDoesNotExist()
        compose.onNodeWithText("0/40").assertExists()
    }

    @Test fun `typing then clearing shows the error`() {
        show("")
        compose.onNodeWithTag("name_field").performTextInput("Push")
        compose.onNodeWithTag("name_field").performTextClearance()
        compose.onNodeWithText("Enter a name").assertExists()
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
    }

    @Test fun `a value replaced in one action counts as an edit`() {
        show("")
        compose.onNodeWithTag("name_field").performTextReplacement("   ")
        compose.onNodeWithText("Enter a name").assertExists()
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
    }

    @Test fun `rename opens without an error and shows it once cleared`() {
        show("Squats")
        compose.onNodeWithText("Enter a name").assertDoesNotExist()
        compose.onNodeWithTag("name_field").performTextClearance()
        compose.onNodeWithText("Enter a name").assertExists()
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
    }

    // Kept from the earlier suite: the over-length message still appears after an edit.
    @Test fun `41 characters disables OK and says why`() {
        var confirmed: String? = null
        show("") { confirmed = it }
        compose.onNodeWithTag("name_field").performTextReplacement("x".repeat(41))
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
        compose.onNodeWithText("Use at most 40 characters").assertExists()
        compose.onNodeWithTag("name_field").performTextReplacement("Burpees")
        compose.onNodeWithTag("name_ok").assertIsEnabled().performClick()
        assertEquals("Burpees", confirmed)
    }

    // Kept from the earlier suite: a rename is prefilled and returns the trimmed name.
    @Test fun `rename is prefilled and returns the trimmed name`() {
        var confirmed: String? = null
        show("Burpees") { confirmed = it }
        compose.onNodeWithTag("name_field").assertTextContains("Burpees")
        compose.onNodeWithTag("name_field").performTextReplacement("  Kettlebell Lunges  ")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals("Kettlebell Lunges", confirmed)
    }
}
