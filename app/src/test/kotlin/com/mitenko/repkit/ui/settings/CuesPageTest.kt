package com.mitenko.repkit.ui.settings

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.FakeVoiceAvailability
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CuesPageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the switches save this entry's cues at once and each has an info tag`() {
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2)))
        val vm = CuesSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo, FakeVoiceAvailability())
        compose.setContent { HiitTheme { CuesPage(vm) } }
        compose.onNodeWithTag("switch_Sound").performClick()
        compose.waitForIdle()
        assertEquals(CueConfig(sound = false), repo.find(1).cues)
        assertEquals(CueConfig(), repo.find(2).cues)
        compose.onNodeWithContentDescription("About Sound").assertExists()
        compose.onNodeWithContentDescription("About Vibration").assertExists()
    }

    @Test
    fun `the voice switch saves at once and says when the device has no voice`() {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CuesSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo, FakeVoiceAvailability(available = false))
        compose.setContent { HiitTheme { CuesPage(vm) } }
        compose.onNodeWithTag("support_Voice").assertTextEquals("Voice cues aren't available on this device")
        compose.onNodeWithTag("switch_Voice").performClick()
        compose.waitForIdle()
        assertEquals(CueConfig(voice = true), repo.find(1).cues)
        compose.onNodeWithContentDescription("About Voice").assertExists()
    }
}
