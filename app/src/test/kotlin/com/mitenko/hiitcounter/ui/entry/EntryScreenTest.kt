package com.mitenko.hiitcounter.ui.entry

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val state = EntryUiState(
        name = "Burpees", reps = listOf(9, 8, 8, 8, 8, 8, 8, 8), total = 65, lastCheckIn = "24 Sep 2026, 05:55",
        bestStreak = 24, currentStreak = 4, today = "24 Sep 2026", checkedInToday = true,
    )

    private fun show(s: EntryUiState, onBack: () -> Unit = {}, onStart: () -> Unit = {}, onSettings: () -> Unit = {}) {
        compose.setContent {
            HiitTheme { EntryScreen(s, onBack = onBack, onStart = onStart, onOpenSettings = onSettings, onDismissError = {}) }
        }
    }

    @Test
    fun `renders the entry's sheet table and name`() {
        show(state)
        compose.onNodeWithTag("entry_name").assertTextEquals("Burpees")
        compose.onNodeWithTag("rep_0").assertTextEquals("9")
        compose.onNodeWithTag("rep_7").assertTextEquals("8")
        compose.onNodeWithText("Total Reps").assertExists()
        compose.onNodeWithText("65").assertExists()
        compose.onNodeWithText("24 Sep 2026, 05:55").assertExists()
        compose.onNodeWithText("Best CI Streak").assertExists()
        compose.onNodeWithText("Checked in today").assertExists()
    }

    @Test
    fun `start invokes the callback`() {
        var clicks = 0
        show(state, onStart = { clicks++ })
        compose.onNodeWithTag("start").performScrollTo().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `start, back and settings are disabled while starting`() {
        show(state.copy(starting = true))
        compose.onNodeWithTag("start").assertIsNotEnabled()
        compose.onNodeWithTag("back").assertIsNotEnabled()
        compose.onNodeWithTag("settings").assertIsNotEnabled()
    }

    @Test
    fun `back and settings invoke their callbacks`() {
        var backs = 0
        var settings = 0
        show(state, onBack = { backs++ }, onSettings = { settings++ })
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("settings").performClick()
        assertEquals(1, backs)
        assertEquals(1, settings)
    }
}
