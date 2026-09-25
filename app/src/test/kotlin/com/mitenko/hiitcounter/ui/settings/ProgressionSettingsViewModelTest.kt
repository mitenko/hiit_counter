package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.testutil.FakeCounterRepository
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProgressionSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `save persists and resets hold count`() = runTest {
        val settings = FakeSettingsRepository()
        val counter = FakeCounterRepository()
        val vm = ProgressionSettingsViewModel(settings, counter)
        vm.update { it.copy(holdAt = "66", holdFor = "3") }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(ProgressionConfig(holdAt = 66, holdFor = 3), settings.progressionFlow.value)
        assertEquals(1, counter.resetHoldCountCalls)
    }

    @Test
    fun `unparseable and invalid drafts are not saved`() = runTest {
        val settings = FakeSettingsRepository()
        val counter = FakeCounterRepository()
        val vm = ProgressionSettingsViewModel(settings, counter)
        vm.update { it.copy(cap = "abc") }
        assertTrue(Field.CAP in vm.validation.value.errors)
        vm.save { }
        vm.update { it.copy(cap = "40") }
        assertFalse(vm.validation.value.isValid)
        vm.save { }
        assertEquals(ProgressionConfig(), settings.progressionFlow.value)
        assertEquals(0, counter.resetHoldCountCalls)
    }

    @Test
    fun `reset to defaults fills the draft`() = runTest {
        val vm = ProgressionSettingsViewModel(FakeSettingsRepository(progression = ProgressionConfig(cap = 90)), FakeCounterRepository())
        assertEquals("90", vm.draft.value?.cap)
        vm.resetToDefaults()
        assertEquals("72", vm.draft.value?.cap)
    }
}
