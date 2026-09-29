package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private var resets = 0

    private fun show() {
        compose.setContent {
            HiitTheme {
                CurrentStatePageContent(
                    draft, ValidationResult(), SaveStatus.SAVED, ZoneOffset.UTC, now = { Instant.parse("2026-09-24T12:00:00Z") },
                    onChange = { draft = it(draft) },
                    onChangeNow = { immediate++; draft = it(draft) },
                    onResetProgress = { resets++ },
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
        assertEquals(0, resets)
        compose.onNodeWithTag("confirm_reset_progress").performClick()
        assertEquals(1, resets)
    }
}
