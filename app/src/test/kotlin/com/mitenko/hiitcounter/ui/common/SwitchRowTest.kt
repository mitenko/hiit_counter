package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwitchRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a switch row toggles and its info tag opens its text`() {
        var on by mutableStateOf(true)
        compose.setContent {
            HiitTheme { Column { SwitchRow("Sound", on, onChange = { on = it }, info = "Beeps for the countdown and at each phase change.") } }
        }
        compose.onNodeWithTag("switch_Sound").performClick()
        assertFalse(on)
        compose.onNodeWithContentDescription("About Sound").performClick()
        compose.onNodeWithTag("info_text").assertTextEquals("Beeps for the countdown and at each phase change.")
    }

    @Test
    fun `the switch row is one rounded card holding its label, info tag and switch`() {
        compose.setContent {
            HiitTheme { Column { SwitchRow("Sound", true, onChange = {}, info = "Beeps for the countdown and at each phase change.") } }
        }
        val card = compose.onNodeWithTag("card_Sound").fetchSemanticsNode().boundsInRoot
        listOf(
            compose.onNodeWithText("Sound"),
            compose.onNodeWithContentDescription("About Sound"),
            compose.onNodeWithTag("switch_Sound"),
        ).forEach { node ->
            val b = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$b outside $card", b.left >= card.left && b.top >= card.top && b.right <= card.right && b.bottom <= card.bottom)
        }
    }
}
