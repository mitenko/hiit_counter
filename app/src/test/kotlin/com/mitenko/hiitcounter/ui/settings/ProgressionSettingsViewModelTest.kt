package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
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
class ProgressionSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `save persists the entry's progression and resets the hold`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 64, holdCount = 2))))
        val vm = ProgressionSettingsViewModel(handle, repo)
        vm.update { it.copy(holdAt = 66, holdFor = 3) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(ProgressionConfig(holdAt = 66, holdFor = 3), repo.find(1).progression)
        assertEquals(0, repo.find(1).counter.holdCount)
    }

    @Test
    fun `invalid drafts are not saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo)
        vm.update { it.copy(cap = 40) }
        assertTrue(Field.CAP in vm.validation.value.errors)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertEquals(ProgressionConfig(), repo.find(1).progression)
    }

    @Test
    fun `reset to defaults fills the draft`() = runTest {
        val vm = ProgressionSettingsViewModel(handle, FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(cap = 90)))))
        assertEquals(90, vm.draft.value?.cap)
        vm.resetToDefaults()
        assertEquals(72, vm.draft.value?.cap)
    }

    @Test
    fun `the penalty steps in half hours and a stored non-multiple is saved exactly`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(penaltyHoursPerRep = 0.3))))
        val vm = ProgressionSettingsViewModel(handle, repo)
        assertEquals(0.3, vm.draft.value!!.penalty.hours, 0.0)
        vm.save { }
        assertEquals(0.3, repo.find(1).progression.penaltyHoursPerRep, 0.0)
        vm.update { it.copy(penalty = it.penalty.plus()) }
        vm.save { }
        assertEquals(0.5, repo.find(1).progression.penaltyHoursPerRep, 0.0)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo)
        repo.delete(1)
        vm.save { }
        runCurrent()
        assertTrue(vm.missing.value)
    }
}
