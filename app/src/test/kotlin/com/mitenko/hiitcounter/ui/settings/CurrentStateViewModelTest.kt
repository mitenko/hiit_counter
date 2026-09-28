package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentStateViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock()
    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `loads the entry's counter into a typed draft`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4))))
        val vm = CurrentStateViewModel(handle, repo, clock)
        assertEquals(CurrentStateViewModel.Draft(65, 24, 4, null), vm.draft.value)
    }

    @Test
    fun `save overwrites the entry's counter`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock)
        val last = clock.instant.minusSeconds(3600)
        vm.update { it.copy(total = 65, best = 24, current = 4, lastCheckIn = last) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(CounterState(65, 24, 4, last, 0), repo.find(1).counter)
    }

    @Test
    fun `invalid drafts are rejected`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock)
        vm.update { it.copy(best = 3, current = 4) }
        assertTrue(Field.BEST_STREAK in vm.validation.value.errors)
        vm.update { it.copy(best = 4, lastCheckIn = clock.instant.plusSeconds(60)) }
        assertTrue(Field.LAST_CHECK_IN in vm.validation.value.errors)
        vm.update { it.copy(total = 0, lastCheckIn = null) }
        assertTrue(Field.TOTAL in vm.validation.value.errors)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertEquals(CounterState(total = 48), repo.find(1).counter)
    }

    @Test
    fun `reset progress resets this entry only`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(1, counter = CounterState(65, 24, 4, clock.instant, 1)),
                testEntry(2, counter = CounterState(total = 50, bestStreak = 3)),
            ),
        )
        val vm = CurrentStateViewModel(handle, repo, clock)
        var done = false
        vm.resetProgress { done = true }
        assertTrue(done)
        assertEquals(CounterState(total = 48), repo.find(1).counter)
        assertEquals(CounterState(total = 50, bestStreak = 3), repo.find(2).counter)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock)
        repo.delete(1)
        vm.save { }
        runCurrent()
        assertTrue(vm.missing.value)
    }
}
