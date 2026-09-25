package com.mitenko.hiitcounter.ui.home

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
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val state = HomeUiState(
        reps = listOf(9, 8, 8, 8, 8, 8, 8, 8), total = 65, lastCheckIn = "24 Sep 2026, 05:55",
        bestStreak = 24, currentStreak = 4, today = "24 Sep 2026", checkedInToday = true,
    )

    @Test
    fun `renders the sheet table`() {
        compose.setContent { HiitTheme { HomeScreen(state, onStart = {}, onOpenSettings = {}, onDismissError = {}) } }
        compose.onNodeWithTag("rep_0").assertTextEquals("9")
        compose.onNodeWithTag("rep_7").assertTextEquals("8")
        compose.onNodeWithText("Total Reps").assertExists()
        compose.onNodeWithText("65").assertExists()
        compose.onNodeWithText("24 Sep 2026, 05:55").assertExists()
        compose.onNodeWithText("Best CI Streak").assertExists()
        compose.onNodeWithText("Checked in today").assertExists()
    }

    @Test
    fun `start button invokes the callback`() {
        var clicks = 0
        compose.setContent { HiitTheme { HomeScreen(state, onStart = { clicks++ }, onOpenSettings = {}, onDismissError = {}) } }
        compose.onNodeWithTag("start").performScrollTo().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `start button is disabled while starting`() {
        compose.setContent { HiitTheme { HomeScreen(state.copy(starting = true), {}, {}, {}) } }
        compose.onNodeWithTag("start").assertIsNotEnabled()
    }
}
