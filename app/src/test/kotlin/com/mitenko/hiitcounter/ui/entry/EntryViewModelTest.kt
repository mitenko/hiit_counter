package com.mitenko.hiitcounter.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.FakeServiceStarter
import com.mitenko.hiitcounter.testutil.FakeServiceStarter.Behavior
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(instant = Instant.parse("2026-09-24T15:00:00Z")) // 08:00 PDT
    private val repo = FakeEntryRepository(
        listOf(
            testEntry(
                1, "Burpees",
                counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
            ),
            testEntry(2, "Lunges"),
        ),
    )

    private class Harness(val vm: EntryViewModel, val controller: TimerController, val starter: FakeServiceStarter)

    private fun TestScope.harness(
        behavior: Behavior = Behavior.SUCCEED,
        repository: FakeEntryRepository = repo,
    ): Harness {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val starter = FakeServiceStarter(controller, behavior)
        val vm = EntryViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repository, controller, starter, clock)
        backgroundScope.launch { vm.uiState.collect {} }
        return Harness(vm, controller, starter)
    }

    @Test
    fun `table shows the entry's distributed reps and sheet-style fields`() = runTest {
        val h = harness()
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals("Burpees", s.name)
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), s.reps)
        assertEquals(65, s.total)
        assertEquals("23 Sep 2026, 05:55", s.lastCheckIn)
        assertEquals(24, s.bestStreak)
        assertEquals(4, s.currentStreak)
        assertEquals("24 Sep 2026", s.today)
        assertFalse(s.checkedInToday)
    }

    @Test
    fun `reps follow the entry's own number of sets`() = runTest {
        val h = harness()
        repo.state.update { list -> list.map { if (it.id == 1L) it.copy(timing = it.timing.copy(sets = 5)) else it } }
        runCurrent()
        assertEquals(listOf(13, 13, 13, 13, 13), h.vm.uiState.value.reps)
    }

    @Test
    fun `a missing entry reports missing only after loading`() = runTest {
        val notReady = FakeEntryRepository(emptyList(), ready = false)
        val h = harness(repository = notReady)
        runCurrent()
        assertFalse(h.vm.missing.value)
        notReady.readiness.complete(Unit)
        runCurrent()
        assertTrue(h.vm.missing.value)
    }

    @Test
    fun `a successful start freezes the snapshot and checks in this entry only`() = runTest {
        val h = harness()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertEquals(1, repo.checkInCalls)
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        assertEquals(WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig()), h.controller.snapshot)
        assertEquals(66, repo.find(1).counter.total)
        assertNull(repo.find(2).counter.lastCheckIn)
        assertTrue(h.vm.uiState.value.checkedInToday)
        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `a service start exception leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.THROW_ON_START)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("not allowed"))
    }

    @Test
    fun `a foreground failure inside the service leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.FAIL_IN_SERVICE)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("boom"))
    }

    @Test
    fun `no service response times out without checking in`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        advanceTimeBy(EntryViewModel.SERVICE_START_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error != null)
    }

    @Test
    fun `start is debounced and reports starting while in flight`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertTrue(h.vm.uiState.value.starting)
    }

    @Test
    fun `checkIn throwing returns the controller to idle without crashing`() = runTest {
        val h = harness()
        repo.checkInError = IOException("disk full")
        h.vm.onStart()
        runCurrent()
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("disk full"))
    }

    @Test
    fun `checkIn throwing EntryNotFound during PREPARING returns to idle with an error`() = runTest {
        val h = harness()
        repo.checkInError = EntryNotFound(1)
        h.vm.onStart()
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("Entry 1 not found"))
        assertFalse(h.vm.uiState.value.starting)
    }

    @Test
    fun `cancelling mid-start returns the controller to idle`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        runCurrent()
        h.vm.viewModelScope.cancel()
        runCurrent()
        assertEquals(RunStatus.IDLE, h.controller.status.value)
    }

    @Test
    fun `check in updates the counter and then reads as checked in today`() = runTest {
        val h = harness()
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertEquals(66, h.vm.uiState.value.total)
        assertTrue(h.vm.uiState.value.checkedInToday)
        assertFalse(h.vm.uiState.value.checkingIn)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
    }

    @Test
    fun `check in ignores taps on either button while its call is in flight`() = runTest {
        val h = harness()
        val gate = CompletableDeferred<Unit>()
        repo.checkInGate = gate
        h.vm.onCheckIn()
        h.vm.onCheckIn()
        h.vm.onStart()
        runCurrent()
        assertTrue(h.vm.uiState.value.checkingIn)
        assertEquals(0, h.starter.calls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertFalse(h.vm.uiState.value.checkingIn)
        assertEquals(66, repo.find(1).counter.total)
    }

    @Test
    fun `start after a check-in starts with no second check-in`() = runTest {
        val h = harness()
        h.vm.onCheckIn()
        runCurrent()
        h.vm.onStart()
        runCurrent()
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        // Start still calls checkIn; the second call is AlreadyToday and writes nothing.
        assertEquals(2, repo.checkInCalls)
        assertEquals(66, repo.find(1).counter.total)
        assertEquals(66, h.controller.state.value!!.totalReps)
    }

    @Test
    fun `Start on a Timer only entry runs the timer with countsReps false and checks in streak-only`() = runTest {
        val habit = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Stretch", type = EntryType.CHECK_IN,
                    counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
                ),
            ),
        )
        val h = harness(repository = habit)
        runCurrent()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertEquals(1, habit.checkInCalls)
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        assertEquals(WorkoutSnapshot(1L, "Stretch", TimingConfig(), CueConfig(), countsReps = false), h.controller.snapshot)
        // Streak-only: the total never moves for a Timer only check-in.
        assertEquals(65, habit.find(1).counter.total)
        assertEquals(5, habit.find(1).counter.currentStreak)
    }

    @Test
    fun `a Timer only entry checks in without changing its total`() = runTest {
        val habit = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Stretch", type = EntryType.CHECK_IN,
                    counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
                ),
            ),
        )
        val h = harness(repository = habit)
        runCurrent()
        assertEquals(EntryType.CHECK_IN, h.vm.uiState.value.type)
        h.vm.onCheckIn()
        runCurrent()
        val counter = habit.find(1).counter
        assertEquals(65, counter.total)
        assertEquals(5, counter.currentStreak)
        assertEquals(clock.instant, counter.lastCheckIn)
        assertTrue(h.vm.uiState.value.checkedInToday)
    }

    @Test
    fun `a failed check-in shows an error, and a deleted entry pops`() = runTest {
        val h = harness()
        repo.checkInError = IOException("disk full")
        h.vm.onCheckIn()
        runCurrent()
        assertTrue(h.vm.uiState.value.error!!.contains("disk full"))
        assertFalse(h.vm.uiState.value.checkingIn)
        assertFalse(h.vm.missing.value)
        repo.checkInError = EntryNotFound(1)
        h.vm.onCheckIn()
        runCurrent()
        assertTrue(h.vm.missing.value)
    }
}
