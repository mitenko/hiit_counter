package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntrySettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeEntryRepository(listOf(testEntry(1, "Burpees"), testEntry(2, "Lunges")))
    private val snapshot = WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig())

    private class Harness(val vm: EntrySettingsViewModel, val controller: TimerController)

    private fun TestScope.harness(repository: FakeEntryRepository = repo): Harness {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = EntrySettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repository, controller)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return Harness(vm, controller)
    }

    @Test
    fun `ui state shows the name and follows the controller's busy rule`() = runTest {
        val h = harness()
        assertEquals(EntrySettingsUiState(name = "Burpees", busy = false), h.vm.uiState.value)
        h.controller.prepare(snapshot)
        runCurrent()
        assertTrue(h.vm.uiState.value.busy)
        h.controller.cancelPrepare()
        runCurrent()
        assertFalse(h.vm.uiState.value.busy)
    }

    @Test
    fun `rename updates the entry`() = runTest {
        val h = harness()
        h.vm.rename("  Kettlebell Lunges ")
        runCurrent()
        assertEquals("Kettlebell Lunges", repo.find(1).name)
        assertEquals("Kettlebell Lunges", h.vm.uiState.value.name)
    }

    @Test
    fun `a rename during an active run leaves the frozen snapshot name`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot)
        h.controller.onServiceStarted()
        h.controller.start(List(8) { 6 })
        runCurrent()
        h.vm.rename("Kettlebell Lunges")
        runCurrent()
        assertEquals("Kettlebell Lunges", repo.find(1).name)
        assertEquals("Burpees", h.controller.snapshot!!.entryName)
    }

    @Test
    fun `duplicate reports the copy's id`() = runTest {
        val h = harness()
        var copy: Long? = null
        h.vm.duplicate { copy = it }
        runCurrent()
        assertEquals("Burpees copy", repo.find(copy!!).name)
    }

    @Test
    fun `a second duplicate call is ignored while one is in flight`() = runTest {
        // Not ready so repo.duplicate() genuinely suspends, keeping the first call in flight
        // long enough for a second, quick call to land while the guard is still set.
        val notReady = FakeEntryRepository(listOf(testEntry(1, "Burpees")), ready = false)
        val h = harness(notReady)
        var calls = 0
        h.vm.duplicate { calls++ }
        h.vm.duplicate { calls++ }
        notReady.readiness.complete(Unit)
        runCurrent()
        assertEquals(1, calls)
        assertEquals(1, notReady.state.value.count { it.name == "Burpees copy" })
    }

    @Test
    fun `deleting a busy entry fails with EntryBusy and deletes nothing`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot)
        runCurrent()
        var deleted = false
        h.vm.delete { deleted = true }
        runCurrent()
        assertFalse(deleted)
        assertEquals(0, repo.deleteCalls)
        assertEquals(EntrySettingsViewModel.BUSY_HINT, h.vm.uiState.value.error)
    }

    @Test
    fun `deleting an idle entry removes it and reports back`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot.copy(entryId = 2L)) // another entry's run doesn't block this delete
        var deleted = false
        h.vm.delete { deleted = true }
        runCurrent()
        assertTrue(deleted)
        assertEquals(listOf(2L), repo.state.value.map { it.id })
        assertTrue(h.vm.missing.value)
    }

    @Test
    fun `a missing entry reports missing after loading`() = runTest {
        val notReady = FakeEntryRepository(emptyList(), ready = false)
        val h = harness(notReady)
        assertFalse(h.vm.missing.value)
        notReady.readiness.complete(Unit)
        runCurrent()
        assertTrue(h.vm.missing.value)
    }

    @Test
    fun `a resolved error is cleared by the next action`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot)
        runCurrent()
        h.vm.delete {}
        runCurrent()
        assertEquals(EntrySettingsViewModel.BUSY_HINT, h.vm.uiState.value.error)
        h.controller.cancelPrepare()
        runCurrent()
        h.vm.rename("Kettlebell Lunges")
        runCurrent()
        assertEquals(null, h.vm.uiState.value.error)
    }
}
