package com.mitenko.hiitcounter.ui.entries

import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EntryListViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(instant = Instant.parse("2026-09-24T15:00:00Z")) // 08:00 PDT
    private val checkedInThisMorning = Instant.parse("2026-09-24T14:00:00Z")    // 07:00 PDT

    private fun TestScope.vm(repo: FakeEntryRepository) = EntryListViewModel(repo, clock).also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `loading until migration readiness, then empty`() = runTest {
        val repo = FakeEntryRepository(ready = false)
        val vm = vm(repo)
        assertEquals(EntryListUiState.Loading, vm.uiState.value)
        repo.readiness.complete(Unit)
        runCurrent()
        assertEquals(EntryListUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `items show the name, the next total and today's check-in`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(1, "Burpees", counter = CounterState(total = 65, lastCheckIn = checkedInThisMorning)),
                testEntry(2, "Lunges"),
            ),
        )
        assertEquals(
            EntryListUiState.Items(listOf(EntryRow(1, "Burpees", 65, true), EntryRow(2, "Lunges", 48, false))),
            vm(repo).uiState.value,
        )
    }

    @Test
    fun `resume re-evaluates today after midnight`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, "Burpees", counter = CounterState(total = 65, lastCheckIn = checkedInThisMorning))))
        val vm = vm(repo)
        assertTrue((vm.uiState.value as EntryListUiState.Items).rows.single().checkedInToday)
        clock.instant = Instant.parse("2026-09-25T08:00:00Z") // 01:00 PDT the next day
        vm.onResume()
        runCurrent()
        assertFalse((vm.uiState.value as EntryListUiState.Items).rows.single().checkedInToday)
    }

    @Test
    fun `move calls moveBy once with the given delta`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2), testEntry(3)))
        val vm = vm(repo)
        vm.move(1, 2)
        runCurrent()
        assertEquals(listOf(1L to 2), repo.moves)
        assertEquals(listOf(2L, 3L, 1L), repo.state.value.map { it.id })
    }

    @Test
    fun `move with a delta of 0 writes nothing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2)))
        val vm = vm(repo)
        vm.move(1, 0)
        runCurrent()
        assertTrue(repo.moves.isEmpty())
        assertEquals(listOf(1L, 2L), repo.state.value.map { it.id })
    }

    @Test
    fun `move on a deleted entry does not throw and the state stays consistent`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2)))
        val vm = vm(repo)
        repo.delete(2)
        runCurrent()
        vm.move(2, 1)
        runCurrent()
        assertEquals(listOf(1L), (vm.uiState.value as EntryListUiState.Items).rows.map { it.id })
    }

    @Test
    fun `move survives the viewModelScope being cancelled mid-write`() = runTest {
        // Not ready: repo.moveBy suspends on readiness.await(), giving a real suspension point to
        // cancel at, matching the checkIn/setType pattern (spec: a user-confirmed write finishes
        // even if its scope is cancelled first).
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2)), ready = false)
        val vm = vm(repo)
        vm.move(1, 1)
        runCurrent()
        vm.viewModelScope.cancel()
        repo.readiness.complete(Unit)
        runCurrent()
        assertEquals(listOf(1L to 1), repo.moves)
    }

    @Test
    fun `create reports the new id`() = runTest {
        val repo = FakeEntryRepository()
        val vm = vm(repo)
        var created: Long? = null
        vm.create(" Burpees ") { created = it }
        runCurrent()
        val entry = repo.state.value.single()
        assertEquals(entry.id, created)
        assertEquals("Burpees", entry.name)
    }

    @Test
    fun `an invalid name creates nothing`() = runTest {
        val repo = FakeEntryRepository()
        val vm = vm(repo)
        var created: Long? = null
        vm.create("   ") { created = it }
        runCurrent()
        assertNull(created)
        assertTrue(repo.state.value.isEmpty())
    }

    @Test
    fun `a check-in-only row carries its type and current streak`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Stretch", type = EntryType.CHECK_IN,
                    counter = CounterState(total = 48, currentStreak = 5, lastCheckIn = checkedInThisMorning),
                ),
            ),
        )
        assertEquals(
            EntryListUiState.Items(listOf(EntryRow(1, "Stretch", 48, true, type = EntryType.CHECK_IN, streak = 5))),
            vm(repo).uiState.value,
        )
    }

    @Test
    fun `create passes the chosen type`() = runTest {
        val repo = FakeEntryRepository()
        val vm = vm(repo)
        vm.create("Stretch", EntryType.CHECK_IN) {}
        vm.create("Burpees") {}
        runCurrent()
        assertEquals(listOf(EntryType.CHECK_IN, EntryType.WORKOUT), repo.state.value.map { it.type })
    }
}
