package com.mitenko.repkit.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mitenko.repkit.domain.Field
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.common.SaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // Spec revision 32: the reset prompt on a Sets change.

    /** Opens on Timing with total 60, so a reset visibly moves it back to the starting total. */
    private fun TestScope.openOnTiming(type: EntryType = EntryType.WORKOUT): Pair<FakeEntryRepository, TimingSettingsViewModel> {
        val repo = FakeEntryRepository(listOf(testEntry(1, type = type, counter = CounterState(total = 60))))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.pageShown(timing = true)
        runCurrent()
        return repo to vm
    }

    private fun TestScope.leaveTiming(vm: TimingSettingsViewModel) {
        vm.flush()
        vm.pageShown(timing = false)
        runCurrent()
    }

    @Test
    fun `a sets change then leaving Timing prompts with from and to`() = runTest {
        val (_, vm) = openOnTiming()
        vm.update { it.copy(sets = 9) }
        vm.update { it.copy(sets = 10) }
        assertNull(vm.setsPrompt.value)
        leaveTiming(vm)
        assertEquals(TimingSettingsViewModel.SetsChange(from = 8, to = 10), vm.setsPrompt.value)
    }

    @Test
    fun `a weight-mode entry is never prompted, since its reps are per set`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(
            1,
            progression = ProgressionConfig(mode = ProgressMode.WEIGHT, weight = WeightConfig(unit = WeightUnit.KG)),
            counter = CounterState(total = 3),
        )))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.pageShown(timing = true)
        runCurrent()
        vm.update { it.copy(sets = 9) }
        leaveTiming(vm)
        assertNull(vm.setsPrompt.value)
    }

    @Test
    fun `a Timer only entry is never prompted`() = runTest {
        val (_, vm) = openOnTiming(EntryType.CHECK_IN)
        vm.update { it.copy(sets = 9) }
        leaveTiming(vm)
        assertNull(vm.setsPrompt.value)
    }

    @Test
    fun `sets back at the baseline give no prompt`() = runTest {
        val (_, vm) = openOnTiming()
        vm.updateNow { it.copy(sets = 10) }
        runCurrent()
        vm.updateNow { it.copy(sets = 8) }
        leaveTiming(vm)
        assertNull(vm.setsPrompt.value)
    }

    @Test
    fun `an answered change is never prompted again`() = runTest {
        val (_, vm) = openOnTiming()
        vm.update { it.copy(sets = 9) }
        leaveTiming(vm)
        vm.keepProgress()
        assertNull(vm.setsPrompt.value)
        var left = false
        vm.exit { left = true }
        runCurrent()
        assertTrue(left)
        assertNull(vm.setsPrompt.value)
        vm.pageShown(timing = true)
        runCurrent()
        leaveTiming(vm)
        assertNull(vm.setsPrompt.value)
    }

    @Test
    fun `Reset progress resets with the checkbox value`() = runTest {
        val (repo, vm) = openOnTiming()
        vm.update { it.copy(sets = 9) }
        leaveTiming(vm)
        vm.resetProgress(clearHistory = true)
        runCurrent()
        assertNull(vm.setsPrompt.value)
        assertEquals(listOf(1L to true), repo.resets)
        assertEquals(48, repo.find(1).counter.total)
    }

    @Test
    fun `Keep progress changes nothing`() = runTest {
        val (repo, vm) = openOnTiming()
        vm.update { it.copy(sets = 9) }
        leaveTiming(vm)
        vm.keepProgress()
        runCurrent()
        assertNull(vm.setsPrompt.value)
        assertTrue(repo.resets.isEmpty())
        assertEquals(60, repo.find(1).counter.total)
        assertEquals(9, repo.find(1).timing.sets)
    }

    @Test
    fun `exiting with a sets change waits for the answer, and without one leaves at once`() = runTest {
        val (_, vm) = openOnTiming()
        var left = 0
        vm.exit { left++ }
        runCurrent()
        assertEquals(1, left)
        assertNull(vm.setsPrompt.value)

        vm.update { it.copy(sets = 9) }
        vm.flush()
        vm.exit { left++ }
        runCurrent()
        assertEquals(TimingSettingsViewModel.SetsChange(from = 8, to = 9), vm.setsPrompt.value)
        assertEquals(1, left)
        vm.keepProgress()
        assertEquals(2, left)
    }

    @Test
    fun `exiting from another page after leaving Timing unanswered never prompts twice`() = runTest {
        val (_, vm) = openOnTiming()
        vm.update { it.copy(sets = 9) }
        leaveTiming(vm)
        assertFalse(vm.setsPrompt.value == null)
        vm.resetProgress(clearHistory = false)
        var left = false
        vm.exit { left = true }
        runCurrent()
        assertTrue(left)
        assertNull(vm.setsPrompt.value)
    }
}
