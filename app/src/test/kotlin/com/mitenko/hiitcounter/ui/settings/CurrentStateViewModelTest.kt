package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.CheckInPoint
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.common.SaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        assertEquals(CurrentStateViewModel.Draft(65, 24, 4, null), vm.draft.value)
    }

    @Test
    fun `a stepper change overwrites the counter after 400 ms`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(total = 65, best = 24, current = 4) }
        advanceTimeBy(399)
        assertEquals(CounterState(total = 48), repo.find(1).counter)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(CounterState(65, 24, 4, null, 0), repo.find(1).counter)
    }

    @Test
    fun `a streak edit keeps the hold count and a total edit resets it`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 64, holdCount = 2))))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(best = 5) }
        vm.flush()
        runCurrent()
        assertEquals(2, repo.find(1).counter.holdCount)
        vm.update { it.copy(total = 65) }
        vm.flush()
        runCurrent()
        assertEquals(0, repo.find(1).counter.holdCount)
    }

    @Test
    fun `a date pick saves at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        val last = clock.instant.minusSeconds(3600)
        vm.updateNow { it.copy(lastCheckIn = last) }
        runCurrent()
        assertEquals(last, repo.find(1).counter.lastCheckIn)
    }

    @Test
    fun `invalid drafts are never saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(best = 3, current = 4) }
        assertTrue(Field.BEST_STREAK in vm.validation.value.errors)
        vm.update { it.copy(best = 4, lastCheckIn = clock.instant.plusSeconds(60)) }
        assertTrue(Field.LAST_CHECK_IN in vm.validation.value.errors)
        vm.update { it.copy(total = 0, lastCheckIn = null) }
        assertTrue(Field.TOTAL in vm.validation.value.errors)
        assertEquals(SaveStatus.INVALID, vm.status.value)
        advanceTimeBy(1_000)
        vm.flush()
        runCurrent()
        assertEquals(0, repo.counterWrites)
        assertEquals(CounterState(total = 48), repo.find(1).counter)
    }

    @Test
    fun `reset progress applies at once, drops a pending save and refreshes the draft`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(1, counter = CounterState(65, 24, 4, clock.instant, 1)),
                testEntry(2, counter = CounterState(total = 50, bestStreak = 3)),
            ),
        )
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(best = 30) } // pending, and must not land after the reset
        vm.resetProgress(clearHistory = false)
        runCurrent()
        assertEquals(CounterState(total = 48), repo.find(1).counter)
        assertEquals(CurrentStateViewModel.Draft(48, 0, 0, null), vm.draft.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, repo.counterWrites)
        assertEquals(CounterState(total = 50, bestStreak = 3), repo.find(2).counter)
    }

    @Test
    fun `a confirmed reset survives the view model being cleared while it waits on an in-flight write`() = runTest {
        val seedRepo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4))))
        CurrentStateViewModel(handle, seedRepo, clock, backgroundScope) // echoes the store into the saved state handle
        // Not ready so the restored draft's own restore-save genuinely suspends mid-write, holding the AutoSaver mutex.
        val notReady = FakeEntryRepository(
            listOf(testEntry(1, counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4))),
            ready = false,
        )
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { CurrentStateViewModel(handle, notReady, clock, backgroundScope) } }
        val vm = ViewModelProvider(store, factory)[CurrentStateViewModel::class.java]
        advanceTimeBy(400)
        runCurrent() // the restore-save fires and blocks in overwriteCounter, mutex held
        vm.resetProgress(clearHistory = false)
        runCurrent() // the reset starts waiting on the same mutex
        store.clear() // cancels viewModelScope while the reset still waits
        notReady.readiness.complete(Unit)
        runCurrent()
        assertEquals(CounterState(total = 48), notReady.find(1).counter)
    }

    @Test
    fun `the draft follows the stored counter while it has no unsaved edits`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        // A NULL total re-resolving to a new starting total (an edit on Progression) reaches the store like this.
        repo.state.update { list -> list.map { it.copy(counter = it.counter.copy(total = 50)) } }
        assertEquals(50, vm.draft.value?.total)
        vm.update { it.copy(best = 3) } // an unsaved edit
        repo.state.update { list -> list.map { it.copy(counter = it.counter.copy(total = 52)) } }
        assertEquals(CurrentStateViewModel.Draft(50, 3, 0, null), vm.draft.value)
        // An edit back to exactly the stored counter (52, 0, 0), with its save still pending: a store echo mustn't overwrite it.
        vm.update { it.copy(total = 52, best = 0) }
        repo.state.update { list -> list.map { it.copy(counter = it.counter.copy(total = 53)) } }
        assertEquals(CurrentStateViewModel.Draft(52, 0, 0, null), vm.draft.value)
    }

    @Test
    fun `the draft is restored from the saved state handle`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val last = clock.instant.minusSeconds(60)
        CurrentStateViewModel(handle, repo, clock, backgroundScope).update { it.copy(best = 3, current = 4, lastCheckIn = last) } // invalid
        val restored = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        assertEquals(CurrentStateViewModel.Draft(48, 3, 4, last), restored.draft.value)
        assertEquals(SaveStatus.INVALID, restored.status.value)
    }

    @Test
    fun `a restored draft is saved only when it differs from the store`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(1, counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4)),
                testEntry(2, counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4)),
            ),
        )

        // Equal to the store: no re-save.
        CurrentStateViewModel(handle, repo, clock, backgroundScope) // echoes the store into the saved state handle
        CurrentStateViewModel(handle, repo, clock, backgroundScope) // "process death": restores the same values
        advanceTimeBy(400)
        runCurrent()
        assertEquals(0, repo.counterWrites)

        // Differs from the store: still saved.
        val handle2 = SavedStateHandle(mapOf(ENTRY_ID_ARG to 2L))
        val seedStore = ViewModelStore()
        val seedFactory = viewModelFactory { initializer { CurrentStateViewModel(handle2, repo, clock, backgroundScope) } }
        ViewModelProvider(seedStore, seedFactory)[CurrentStateViewModel::class.java] // echoes the store into the saved state handle
        seedStore.clear() // "process death": no longer following the store
        // The store moved on independently (e.g. a Progression edit resolving the NULL total) before the restore.
        repo.state.update { list -> list.map { if (it.id == 2L) it.copy(counter = it.counter.copy(total = 50)) else it } }
        CurrentStateViewModel(handle2, repo, clock, backgroundScope) // restores the now-stale (65, 24, 4)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, repo.counterWrites)
        assertEquals(CounterState(total = 65, bestStreak = 24, currentStreak = 4), repo.find(2).counter)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(best = 5) }
        repo.delete(1)
        vm.flush()
        runCurrent()
        assertTrue(vm.missing.value)
    }

    @Test
    fun `reset progress passes Clear history too through to the repository`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(65, 24, 4, clock.instant, 1))))
        repo.points.value = mapOf(1L to listOf(CheckInPoint(clock.instant, 65)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.resetProgress(clearHistory = true)
        runCurrent()
        assertEquals(listOf(1L to true), repo.resets)
        assertTrue(repo.points.value[1L].isNullOrEmpty())
        assertEquals(CounterState(total = 48), repo.find(1).counter)
    }
}
