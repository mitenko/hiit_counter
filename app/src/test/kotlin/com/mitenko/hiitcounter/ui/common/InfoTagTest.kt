package com.mitenko.hiitcounter.ui.common

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InfoTagTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tapping the tag opens its title, text and OK`() {
        compose.setContent { HiitTheme { InfoTag(title = "Floor (min)", text = "The lowest your rep total can fall to.") } }
        compose.onNodeWithTag("info_text").assertDoesNotExist()
        compose.onNodeWithContentDescription("About Floor (min)").performClick()
        compose.onNodeWithTag("info_title").assertTextEquals("Floor (min)")
        compose.onNodeWithTag("info_text").assertTextEquals("The lowest your rep total can fall to.")
        compose.onNodeWithTag("info_ok").performClick()
        compose.onNodeWithTag("info_text").assertDoesNotExist()
    }

    @Test
    fun `the tag is a 48 dp target`() {
        compose.setContent { HiitTheme { InfoTag(title = "SETS", text = "How many work intervals the workout has.") } }
        compose.onNodeWithContentDescription("About SETS").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
}
