package com.mitenko.hiitcounter.ui.home

import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeCounterRepository
import com.mitenko.hiitcounter.testutil.FakeServiceStarter
import com.mitenko.hiitcounter.testutil.FakeServiceStarter.Behavior
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(instant = Instant.parse("2026-09-24T15:00:00Z")) // 08:00 PDT
    private val counter = FakeCounterRepository(
        CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
    )
    private val settings = FakeSettingsRepository()

    private class Harness(val vm: HomeViewModel, val controller: TimerController, val starter: FakeServiceStarter)

    private fun TestScope.harness(behavior: Behavior = Behavior.SUCCEED): Harness {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val starter = FakeServiceStarter(controller, behavior)
        val vm = HomeViewModel(counter, settings, controller, starter, clock)
        backgroundScope.launch { vm.uiState.collect {} }
        return Harness(vm, controller, starter)
    }

    @Test
    fun `table shows distributed reps and sheet-style fields`() = runTest {
        val h = harness()
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), s.reps)
        assertEquals(65, s.total)
        assertEquals("23 Sep 2026, 05:55", s.lastCheckIn)
        assertEquals(24, s.bestStreak)
        assertEquals(4, s.currentStreak)
        assertEquals("24 Sep 2026", s.today)
        assertFalse(s.checkedInToday)
    }

    @Test
    fun `reps follow the configured number of sets`() = runTest {
        val h = harness()
        settings.timingFlow.value = settings.timingFlow.value.copy(sets = 5)
        runCurrent()
        assertEquals(listOf(13, 13, 13, 13, 13), h.vm.uiState.value.reps)
    }

    @Test
    fun `successful start checks in after the service starts and runs the timer`() = runTest {
        val h = harness()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertEquals(1, counter.checkInCalls)
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        assertEquals(66, counter.stateFlow.value.total)
        assertTrue(h.vm.uiState.value.checkedInToday)
        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `service start exception leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.THROW_ON_START)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, counter.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("not allowed"))
    }

    @Test
    fun `foreground failure inside the service leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.FAIL_IN_SERVICE)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, counter.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("boom"))
    }

    @Test
    fun `no service response times out without checking in`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        advanceTimeBy(HomeViewModel.SERVICE_START_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(0, counter.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error != null)
    }

    @Test
    fun `start is debounced while in flight`() = runTest {
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
        counter.checkInError = IOException("disk full")
        h.vm.onStart()
        runCurrent()
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("disk full"))
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
}
