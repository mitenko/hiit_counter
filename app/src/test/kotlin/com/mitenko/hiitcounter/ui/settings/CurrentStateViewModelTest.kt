package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeCounterRepository
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CurrentStateViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = FakeClock()

    @Test
    fun `loads the current counter into the draft`() = runTest {
        val counter = FakeCounterRepository(CounterState(total = 65, bestStreak = 24, currentStreak = 4))
        val vm = CurrentStateViewModel(counter, FakeSettingsRepository(), clock)
        assertEquals(CurrentStateViewModel.Draft("65", "24", "4", null), vm.draft.value)
    }

    @Test
    fun `save overwrites the counter`() = runTest {
        val counter = FakeCounterRepository()
        val vm = CurrentStateViewModel(counter, FakeSettingsRepository(), clock)
        val last = clock.instant.minusSeconds(3600)
        vm.update { it.copy(total = "65", best = "24", current = "4", lastCheckIn = last) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(CounterState(65, 24, 4, last, 0), counter.stateFlow.value)
    }

    @Test
    fun `invalid drafts are rejected`() = runTest {
        val counter = FakeCounterRepository()
        val vm = CurrentStateViewModel(counter, FakeSettingsRepository(), clock)
        vm.update { it.copy(best = "3", current = "4") }
        assertTrue(Field.BEST_STREAK in vm.validation.value.errors)
        vm.update { it.copy(best = "4", lastCheckIn = clock.instant.plusSeconds(60)) }
        assertTrue(Field.LAST_CHECK_IN in vm.validation.value.errors)
        vm.update { it.copy(total = "x", lastCheckIn = null) }
        assertTrue(Field.TOTAL in vm.validation.value.errors)
        vm.save { }
        assertEquals(0, counter.overwriteCalls)
    }

    @Test
    fun `reset progress delegates to the repository`() = runTest {
        val counter = FakeCounterRepository()
        val vm = CurrentStateViewModel(counter, FakeSettingsRepository(), clock)
        var done = false
        vm.resetProgress { done = true }
        assertTrue(done)
        assertEquals(1, counter.resetProgressCalls)
    }
}
