package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.FakeVoiceAvailability
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CuesSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `toggles persist immediately for this entry`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2)))
        val vm = CuesSettingsViewModel(handle, repo, FakeVoiceAvailability())
        vm.setSound(false)
        assertEquals(CueConfig(sound = false, vibration = true), repo.find(1).cues)
        vm.setVibration(false)
        assertEquals(CueConfig(sound = false, vibration = false), repo.find(1).cues)
        assertEquals(CueConfig(), repo.find(2).cues)
    }

    @Test
    fun `back-to-back toggles both persist`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CuesSettingsViewModel(handle, repo, FakeVoiceAvailability())
        vm.setSound(false)
        vm.setVibration(false)
        runCurrent()
        assertEquals(CueConfig(sound = false, vibration = false), repo.find(1).cues)
    }

    @Test
    fun `a toggle on a deleted entry reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CuesSettingsViewModel(handle, repo, FakeVoiceAvailability())
        repo.delete(1)
        vm.setSound(false)
        runCurrent()
        assertTrue(vm.missing.value)
    }

    @Test
    fun `the voice switch persists at once and the device check is exposed`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val voice = FakeVoiceAvailability(available = false)
        val vm = CuesSettingsViewModel(handle, repo, voice)
        assertEquals(false, vm.voiceAvailable.value)
        assertEquals(1, voice.checks)
        vm.setVoice(true)
        assertEquals(CueConfig(voice = true), repo.find(1).cues)
    }

    @Test
    fun `the voice availability check also runs for a Timer only entry`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, type = EntryType.CHECK_IN)))
        val voice = FakeVoiceAvailability(available = true)
        val vm = CuesSettingsViewModel(handle, repo, voice)
        assertEquals(true, vm.voiceAvailable.value)
        assertEquals(1, voice.checks)
    }
}
