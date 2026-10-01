package com.mitenko.repkit.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimingSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(initial: TimingConfig) {
        compose.setContent {
            HiitTheme {
                var draft by remember { mutableStateOf(initial) }
                val validation = SettingsValidator.timing(draft)
                TimingPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { draft = it(draft) }, onChangeNow = { draft = it(draft) },
                )
            }
        }
    }

    @Test
    fun `steppers update values and total`() {
        show(TimingConfig())
        compose.onNodeWithTag("total").assertTextEquals("TOTAL 04:00")
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.onNodeWithTag("value_SETS").assertTextEquals("9")
        compose.onNodeWithTag("total").assertTextEquals("TOTAL 04:30")
    }

    @Test
    fun `the status line reads Not saved while invalid and Saved once valid`() {
        // 0 + 3 × 59:59 = 2:59:57 is too long; one set fewer, 1:59:58, is fine.
        show(TimingConfig(prepareSec = 0, sets = 3, workSec = 3599, restSec = 0, cooldownSec = 0))
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
        compose.onNodeWithContentDescription("Decrease SETS").performScrollTo().performClick()
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }

    @Test
    fun `work cannot step below one second`() {
        show(TimingConfig(workSec = 5))
        repeat(2) { compose.onNodeWithContentDescription("Decrease WORK").performScrollTo().performClick() }
        compose.onNodeWithTag("value_WORK").assertTextEquals("00:01")
    }
}
