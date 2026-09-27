package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
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
                TimingSettingsScreen(draft, SettingsValidator.timing(draft), onBack = {}, onChange = { draft = it(draft) }, onSave = {})
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
    fun `save disabled when invalid`() {
        val tooLong = TimingConfig(sets = 20, workSec = 3599)
        compose.setContent { HiitTheme { TimingSettingsScreen(tooLong, SettingsValidator.timing(tooLong), {}, {}, {}) } }
        compose.onNodeWithTag("save").assertIsNotEnabled()
    }

    @Test
    fun `work cannot step below one second`() {
        show(TimingConfig(workSec = 5))
        repeat(2) { compose.onNodeWithContentDescription("Decrease WORK").performScrollTo().performClick() }
        compose.onNodeWithTag("value_WORK").assertTextEquals("00:01")
    }

    @Test
    fun `the route pops to the list when its entry loads as missing`() {
        var gone = 0
        val vm = TimingSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), FakeEntryRepository())
        compose.setContent { HiitTheme { TimingSettingsRoute(onBack = {}, onEntryGone = { gone++ }, vm = vm) } }
        compose.waitForIdle()
        assertEquals(1, gone)
    }
}
