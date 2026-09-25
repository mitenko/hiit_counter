package com.mitenko.hiitcounter.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.navigation.Routes
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `entries open their routes`() {
        val opened = mutableListOf<String>()
        compose.setContent { HiitTheme { SettingsListScreen(onBack = {}, onOpen = { opened += it }) } }
        compose.onNodeWithText("Timing").performClick()
        compose.onNodeWithText("Progression").performClick()
        compose.onNodeWithText("Current State").performClick()
        compose.onNodeWithText("Cues").performClick()
        assertEquals(
            listOf(Routes.SETTINGS_TIMING, Routes.SETTINGS_PROGRESSION, Routes.SETTINGS_CURRENT, Routes.SETTINGS_CUES),
            opened,
        )
    }
}
