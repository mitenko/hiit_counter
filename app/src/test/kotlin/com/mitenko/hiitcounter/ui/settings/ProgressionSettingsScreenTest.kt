package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProgressionSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `cross-field errors show inline and disable save, and the hold hint shows`() {
        var draft by mutableStateOf(ProgressionDraft.from(ProgressionConfig(startingTotal = 40)))
        compose.setContent {
            HiitTheme {
                ProgressionSettingsScreen(
                    draft, SettingsValidator.progression(draft.toConfig()),
                    onBack = {}, onChange = { draft = it(draft) }, onSave = {}, onReset = {},
                )
            }
        }
        compose.onNodeWithTag("support_Starting total").assertTextEquals("Must be ≥ floor")
        compose.onNodeWithTag("save").assertIsNotEnabled()
        draft = ProgressionDraft.from(ProgressionConfig(holdFor = 0))
        compose.onNodeWithTag("support_Hold at").performScrollTo().assertTextEquals("Hold disabled")
        compose.onNodeWithTag("save").assertIsEnabled()
    }
}
