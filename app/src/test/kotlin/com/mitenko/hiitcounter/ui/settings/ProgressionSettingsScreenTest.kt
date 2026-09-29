package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProgressionSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private var draft by mutableStateOf(ProgressionDraft.from(ProgressionConfig()))
    private var resets = 0

    private fun show(initial: ProgressionConfig) {
        draft = ProgressionDraft.from(initial)
        compose.setContent {
            HiitTheme {
                val validation = SettingsValidator.progression(draft.toConfig())
                ProgressionPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { draft = it(draft) }, onChangeNow = { draft = it(draft) }, onReset = { resets++ },
                )
            }
        }
    }

    @Test
    fun `cross-field errors show inline with Not saved, and the hold hint shows`() {
        show(ProgressionConfig(startingTotal = 40))
        compose.onNodeWithTag("support_Starting total").assertTextEquals("Must be ≥ floor")
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
        draft = ProgressionDraft.from(ProgressionConfig(holdFor = 0))
        compose.onNodeWithTag("support_Hold at").performScrollTo().assertTextEquals("Hold disabled")
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }

    @Test
    fun `the hold switch hides and restores the rows and their values`() {
        show(ProgressionConfig(holdAt = 66, holdFor = 3))
        compose.onNodeWithTag("value_Hold at").performScrollTo().assertTextEquals("66")
        compose.onNodeWithTag("switch_Hold").performScrollTo().performClick()
        compose.onNodeWithTag("value_Hold at").assertDoesNotExist()
        compose.onNodeWithTag("value_Hold for (check-ins)").assertDoesNotExist()
        assertFalse(draft.hold)
        assertEquals(66, draft.holdAt)
        compose.onNodeWithTag("switch_Hold").performScrollTo().performClick()
        compose.onNodeWithTag("value_Hold at").performScrollTo().assertTextEquals("66")
        compose.onNodeWithTag("value_Hold for (check-ins)").performScrollTo().assertTextEquals("3")
    }

    @Test
    fun `reset to defaults asks for confirmation first`() {
        show(ProgressionConfig(cap = 90))
        compose.onNodeWithTag("reset_defaults").performScrollTo().performClick()
        compose.onNodeWithText("Reset progression to defaults?").assertIsDisplayed()
        assertEquals(0, resets)
        compose.onNodeWithTag("confirm_reset_defaults").performClick()
        assertEquals(1, resets)
    }
}
