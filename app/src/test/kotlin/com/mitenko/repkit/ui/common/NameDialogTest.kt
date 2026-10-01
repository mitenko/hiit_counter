package com.mitenko.repkit.ui.common

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NameDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `an empty name disables OK and says why`() {
        compose.setContent { HiitTheme { NameDialog("New workout", "", onConfirm = {}, onDismiss = {}) } }
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
        compose.onNodeWithText("Enter a name").assertExists()
    }

    @Test
    fun `41 characters disables OK and says why`() {
        var confirmed: String? = null
        compose.setContent { HiitTheme { NameDialog("New workout", "", onConfirm = { confirmed = it }, onDismiss = {}) } }
        compose.onNodeWithTag("name_field").performTextReplacement("x".repeat(41))
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
        compose.onNodeWithText("Use at most 40 characters").assertExists()
        compose.onNodeWithTag("name_field").performTextReplacement("Burpees")
        compose.onNodeWithTag("name_ok").assertIsEnabled().performClick()
        assertEquals("Burpees", confirmed)
    }

    @Test
    fun `rename is prefilled and returns the trimmed name`() {
        var confirmed: String? = null
        compose.setContent { HiitTheme { NameDialog("Rename", "Burpees", onConfirm = { confirmed = it }, onDismiss = {}) } }
        compose.onNodeWithTag("name_field").assertTextContains("Burpees")
        compose.onNodeWithTag("name_field").performTextReplacement("  Kettlebell Lunges  ")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals("Kettlebell Lunges", confirmed)
    }
}
