package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CuesSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `toggles persist immediately`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = CuesSettingsViewModel(settings)
        vm.setSound(false)
        assertEquals(CueConfig(sound = false, vibration = true), settings.cuesFlow.value)
        vm.setVibration(false)
        assertEquals(CueConfig(sound = false, vibration = false), settings.cuesFlow.value)
    }
}
