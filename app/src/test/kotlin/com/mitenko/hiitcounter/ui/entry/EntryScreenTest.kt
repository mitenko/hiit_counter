package com.mitenko.hiitcounter.ui.entry

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.model.EntryType
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
        bestStreak = 24, currentStreak = 4, today = "24 Sep 2026", checkedInToday = true, loaded = true,
    )

    private fun show(
        s: EntryUiState,
        onBack: () -> Unit = {},
        onStart: () -> Unit = {},
        onSettings: () -> Unit = {},
        onCheckIn: () -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                EntryScreen(
                    s, onBack = onBack, onStart = onStart, onCheckIn = onCheckIn, onOpenSettings = onSettings, onDismissError = {},
                )
            }
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

    @Test
    fun `check in sits next to start and invokes its callback`() {
        var checks = 0
        show(state.copy(checkedInToday = false), onCheckIn = { checks++ })
        compose.onNodeWithTag("check_in").performScrollTo().assertTextEquals("Check in").performClick()
        assertEquals(1, checks)
        compose.onNodeWithTag("start").assertIsEnabled()
    }

    @Test
    fun `once checked in today it reads Checked in and is disabled`() {
        show(state) // checkedInToday = true
        compose.onNodeWithTag("check_in").assertTextEquals("Checked in ✓").assertIsNotEnabled()
        compose.onNodeWithTag("start").assertIsEnabled()
    }

    @Test
    fun `both buttons are disabled while a check-in is in flight`() {
        show(state.copy(checkedInToday = false, checkingIn = true))
        compose.onNodeWithTag("check_in").assertIsNotEnabled()
        compose.onNodeWithTag("start").assertIsNotEnabled()
    }

    @Test
    fun `a check-in-only entry shows the streak rows and one Check in`() {
        show(state.copy(type = EntryType.CHECK_IN, checkedInToday = false))
        compose.onNodeWithTag("rep_0").assertDoesNotExist()
        compose.onNodeWithText("Total Reps").assertDoesNotExist()
        compose.onNodeWithText("Last Check In").assertExists()
        compose.onNodeWithText("Best CI Streak").assertExists()
        compose.onNodeWithText("Curr CI Streak").assertExists()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithTag("start").assertDoesNotExist()
        compose.onNodeWithTag("check_in").assertIsEnabled()
    }

    @Test
    fun `before the entry has loaded there is no start or check-in button`() {
        show(EntryUiState())
        compose.onNodeWithTag("start").assertDoesNotExist()
        compose.onNodeWithTag("check_in").assertDoesNotExist()
        compose.onNodeWithTag("rep_0").assertDoesNotExist()
        compose.onNodeWithText("Total Reps").assertDoesNotExist()
    }

    @Test
    fun `both buttons are at least 48 dp tall`() {
        show(state.copy(checkedInToday = false))
        compose.onNodeWithTag("check_in").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("start").assertHeightIsAtLeast(48.dp)
    }
}
