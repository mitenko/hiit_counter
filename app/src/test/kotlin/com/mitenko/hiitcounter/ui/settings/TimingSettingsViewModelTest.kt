package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimingSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `loads, edits and saves`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = TimingSettingsViewModel(settings)
        assertEquals(TimingConfig(), vm.draft.value)
        vm.update { it.copy(sets = 6) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(6, settings.timingFlow.value.sets)
    }

    @Test
    fun `over two hours is not saved`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = TimingSettingsViewModel(settings)
        vm.update { it.copy(sets = 20, workSec = 3599) }
        assertTrue(Field.TOTAL_DURATION in vm.validation.value.errors)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertEquals(TimingConfig(), settings.timingFlow.value)
    }
}
