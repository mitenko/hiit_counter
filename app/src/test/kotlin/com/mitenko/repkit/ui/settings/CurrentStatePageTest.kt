package com.mitenko.repkit.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.ValidationResult
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CurrentStatePageTest {
    @get:Rule val compose = createComposeRule()

    private var draft by mutableStateOf(CurrentStateViewModel.Draft(65, 24, 4, Instant.parse("2026-09-23T12:00:00Z")))
    private var immediate = 0

    /** Each reset's Clear history too value, in order. */
    private val resets = mutableListOf<Boolean>()

    private fun show(showTotal: Boolean = true) {
        compose.setContent {
            HiitTheme {
                CurrentStatePageContent(
                    draft, ValidationResult(), SaveStatus.SAVED, ZoneOffset.UTC, now = { Instant.parse("2026-09-24T12:00:00Z") },
                    onChange = { draft = it(draft) },
                    onChangeNow = { immediate++; draft = it(draft) },
                    onResetProgress = { resets += it },
                    showTotal = showTotal,
                )
            }
        }
    }

    @Test
    fun `Clear saves at once`() {
        show()
        compose.onNodeWithTag("clear_last_check_in").performScrollTo().performClick()
        assertEquals(1, immediate)
        assertNull(draft.lastCheckIn)
    }

    @Test
    fun `reset progress asks for confirmation first`() {
        show()
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithText("Reset progress?").assertIsDisplayed()
        assertTrue(resets.isEmpty())
        compose.onNodeWithTag("confirm_reset_progress").performClick()
        assertEquals(listOf(false), resets)
    }

    @Test
    fun `Clear history too starts unchecked under the unchanged body, so a reset keeps the history`() {
        show()
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithText("Your reps will return to the starting total, your streaks will reset to 0, and your last check-in will be cleared.").assertIsDisplayed()
        compose.onNodeWithTag("clear_history").assertIsOff()
        compose.onNodeWithTag("confirm_reset_progress").performClick()
        assertEquals(listOf(false), resets)
    }

    @Test
    fun `checking Clear history too passes it to the reset, and the next dialog starts unchecked again`() {
        show()
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithTag("clear_history").performClick().assertIsOn()
        compose.onNodeWithTag("confirm_reset_progress").performClick()
        assertEquals(listOf(true), resets)
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithTag("clear_history").assertIsOff()
    }

    @Test
    fun `without the total the page shows the streaks, the date and Reset progress`() {
        show(showTotal = false)
        compose.onNodeWithTag("value_Current reps").assertDoesNotExist()
        compose.onNodeWithTag("value_Best streak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("value_Current streak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("last_check_in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reset_progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase Best streak").performScrollTo().performClick()
        assertEquals(25, draft.best)
        assertEquals(65, draft.total) // a streak edit keeps the stored total in the draft, so the save writes it back unchanged
    }
}
