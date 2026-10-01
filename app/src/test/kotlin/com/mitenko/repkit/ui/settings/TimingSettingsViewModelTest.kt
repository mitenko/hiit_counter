package com.mitenko.repkit.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mitenko.repkit.domain.Field
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.common.SaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimingSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `loads the entry's timing and a stepper change saves it after 400 ms`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, timing = TimingConfig(sets = 5)), testEntry(2)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(TimingConfig(sets = 5), vm.draft.value)
        vm.update { it.copy(sets = 6) }
        advanceTimeBy(399)
        assertEquals(5, repo.find(1).timing.sets)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(6, repo.find(1).timing.sets)
        assertEquals(TimingConfig(), repo.find(2).timing)
        assertEquals(SaveStatus.SAVED, vm.status.value)
    }

    @Test
    fun `a burst of ten stepper changes writes once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        repeat(10) {
            vm.update { it.copy(sets = it.sets + 1) }
            advanceTimeBy(80) // hold-to-repeat pace
        }
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, repo.timingWrites)
        assertEquals(18, repo.find(1).timing.sets)
    }

    @Test
    fun `an invalid draft never saves and cancels a pending save`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(sets = 9) }
        vm.update { it.copy(sets = 20, workSec = 3599) }
        assertTrue(Field.TOTAL_DURATION in vm.validation.value.errors)
        assertEquals(SaveStatus.INVALID, vm.status.value)
        advanceTimeBy(1_000)
        vm.flush()
        runCurrent()
        assertEquals(0, repo.timingWrites)
        assertEquals(TimingConfig(), repo.find(1).timing)
    }

    @Test
    fun `a dialog OK saves at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(sets = 9) }
        vm.updateNow { it.copy(workSec = 30) }
        runCurrent()
        assertEquals(TimingConfig(sets = 9, workSec = 30), repo.find(1).timing)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, repo.timingWrites)
    }

    @Test
    fun `flush writes a pending stepper change at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(sets = 9) }
        vm.flush()
        runCurrent()
        assertEquals(9, repo.find(1).timing.sets)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, repo.timingWrites)
    }

    @Test
    fun `clearing the view model flushes a pending change through the application scope`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { TimingSettingsViewModel(handle, repo, backgroundScope) } }
        val vm = ViewModelProvider(store, factory)[TimingSettingsViewModel::class.java]
        vm.update { it.copy(sets = 9) }
        store.clear() // cancels viewModelScope (and the debounce), then onCleared
        runCurrent()
        assertEquals(9, repo.find(1).timing.sets)
    }

    @Test
    fun `the typed draft is restored from the saved state handle`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        TimingSettingsViewModel(handle, repo, backgroundScope).update { it.copy(sets = 20, workSec = 3599) } // invalid: never saved
        val restored = TimingSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(TimingConfig(sets = 20, workSec = 3599), restored.draft.value)
        assertEquals(SaveStatus.INVALID, restored.status.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, repo.timingWrites) // only a valid restored draft is scheduled
        assertEquals(TimingConfig(), repo.find(1).timing)
    }

    @Test
    fun `a missing entry reports missing after loading`() = runTest {
        val vm = TimingSettingsViewModel(handle, FakeEntryRepository(), backgroundScope)
        runCurrent()
        assertTrue(vm.missing.value)
        assertNull(vm.draft.value)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(sets = 9) }
        repo.delete(1)
        vm.flush()
        runCurrent()
        assertTrue(vm.missing.value)
    }

    @Test
    fun `a write the repository rejects shows Not saved until the next good write`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        repo.writeError = IllegalArgumentException("rejected")
        vm.updateNow { it.copy(sets = 9) }
        runCurrent()
        assertEquals(SaveStatus.FAILED, vm.status.value)
        vm.updateNow { it.copy(sets = 10) }
        runCurrent()
        assertEquals(SaveStatus.SAVED, vm.status.value)
        assertEquals(10, repo.find(1).timing.sets)
    }
}
