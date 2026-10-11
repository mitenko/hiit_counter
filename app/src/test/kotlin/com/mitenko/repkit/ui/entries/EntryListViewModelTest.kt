package com.mitenko.repkit.ui.entries

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.domain.FreeLimits
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.FakeEntitlements
import com.mitenko.repkit.testutil.FakeProUpgrade
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.theme.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EntryListViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val clock = FakeClock(instant = Instant.parse("2026-09-24T15:00:00Z")) // 08:00 PDT
    private val checkedInThisMorning = Instant.parse("2026-09-24T14:00:00Z")    // 07:00 PDT

    private var preferencesFileCount = 0

    private fun TestScope.preferences() = AppPreferences(
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "app${preferencesFileCount++}.preferences_pb") },
        ),
    )

    private val proUpgrade = FakeProUpgrade()

    private fun TestScope.vm(
        repo: FakeEntryRepository,
        preferences: AppPreferences = preferences(),
        tier: Tier = Tier.PRO,
    ) =
        EntryListViewModel(repo, clock, preferences, FakeEntitlements(tier), FreeLimits(), proUpgrade).also { vm ->
            backgroundScope.launch { vm.uiState.collect {} }
            backgroundScope.launch { vm.themeMode.collect {} }
            backgroundScope.launch { vm.crashReportsEnabled.collect {} }
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
    fun `a Timer only row carries its type and current streak`() = runTest {
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

    // The class clock is Thu 24 Sep 08:00 PDT: the week starts Mon 21 Sep 00:00 PDT, the tile window Fri 28 Aug.
    private val p0 = CheckInPoint(Instant.parse("2026-08-30T16:00:00Z"), 58) // Sun 30 Aug: tile day 2
    private val p1 = CheckInPoint(Instant.parse("2026-09-21T06:30:00Z"), 60) // Sun 20 Sep 23:30 PDT: last week, day 23
    private val p2 = CheckInPoint(Instant.parse("2026-09-21T07:00:00Z"), 61) // Mon 21 Sep 00:00 PDT: this week, day 24
    private val p3 = CheckInPoint(Instant.parse("2026-09-23T12:00:00Z"), 62) // Wed 23 Sep: this week, day 26

    private fun withHistory() = FakeEntryRepository(listOf(testEntry(1, "Burpees"))).apply {
        points.value = mapOf(1L to listOf(p0, p1, p2, p3))
    }

    private fun row(vm: EntryListViewModel) = (vm.uiState.value as EntryListUiState.Items).rows.single()

    @Test
    fun `a row counts this week's check-ins from Monday midnight`() = runTest {
        assertEquals(2, row(vm(withHistory())).weekCount)
    }

    @Test
    fun `a row carries the tile window's count, this week's days and sparkline`() = runTest {
        val tile = row(vm(withHistory())).tile
        assertEquals(4, tile.count)
        // Mon (p2) and Wed (p3) are this week; p0 and p1 (last week and before) are not.
        assertEquals(listOf(0, 2), tile.week.indices.filter { tile.week[it] })
        assertEquals(listOf(SparkPoint(2, 58), SparkPoint(23, 60), SparkPoint(24, 61), SparkPoint(26, 62)), tile.spark)
    }

    @Test
    fun `resume moves the week and the tile window`() = runTest {
        val vm = vm(withHistory())
        clock.instant = Instant.parse("2026-09-28T15:00:00Z") // Mon 28 Sep 08:00 PDT
        vm.onResume()
        runCurrent()
        val r = row(vm)
        assertEquals(0, r.weekCount)
        assertEquals(3, r.tile.count) // 30 Aug has left the window (1 Sep – 28 Sep)
        assertEquals(TileData.NO_WEEK, r.tile.week) // the new week has no check-ins yet
    }

    @Test
    fun `a check-in updates the row's count and tile at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, "Burpees")))
        val vm = vm(repo)
        repo.checkIn(1, clock)
        runCurrent()
        val r = row(vm)
        assertEquals(1, r.weekCount)
        assertEquals(TileData(count = 1, week = List(7) { it == 3 }, spark = listOf(SparkPoint(27, 48))), r.tile)
    }

    @Test
    fun `themeMode defaults to SYSTEM`() = runTest {
        assertEquals(ThemeMode.SYSTEM, vm(FakeEntryRepository()).themeMode.value)
    }

    @Test
    fun `setThemeMode writes through to preferences and updates themeMode`() = runTest {
        val preferences = preferences()
        val vm = vm(FakeEntryRepository(), preferences)
        vm.setThemeMode(ThemeMode.DARK)
        runCurrent()
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
        assertEquals(ThemeMode.DARK, preferences.themeMode.first())
    }

    @Test
    fun `crashReportsEnabled defaults to true`() = runTest {
        assertEquals(true, vm(FakeEntryRepository()).crashReportsEnabled.value)
    }

    @Test
    fun `setCrashReportsEnabled writes through to preferences and updates the flow`() = runTest {
        val preferences = preferences()
        val vm = vm(FakeEntryRepository(), preferences)
        vm.setCrashReportsEnabled(false)
        runCurrent()
        assertEquals(false, vm.crashReportsEnabled.value)
        assertEquals(false, preferences.crashReportsEnabled.first())
    }

    @Test
    fun `setWeightUnitDefault writes through to preferences and updates the default`() = runTest {
        val preferences = preferences()
        val vm = vm(FakeEntryRepository(), preferences)
        backgroundScope.launch { vm.weightUnitDefault.collect {} }
        vm.setWeightUnitDefault(WeightUnit.LB)
        runCurrent()
        assertEquals(WeightUnit.LB, preferences.weightUnitDefault.first())
        assertEquals(WeightUnit.LB, vm.weightUnitDefault.first { it == WeightUnit.LB })
    }

    // Spec revision 18 §3: the free tier's entry limit.

    private fun entries(n: Int) = FakeEntryRepository(List(n) { testEntry(it + 1L) })

    @Test
    fun `free at 2 entries can add and create proceeds`() = runTest {
        val repo = entries(2)
        val vm = vm(repo, tier = Tier.FREE)
        assertTrue(vm.requestAdd())
        var created: Long? = null
        vm.create("Burpees") { created = it }
        runCurrent()
        assertEquals(3, repo.state.value.size)
        assertEquals(repo.state.value.last().id, created)
        assertFalse(vm.limitDialog.value)
    }

    @Test
    fun `free at 3 entries shows the limit dialog instead of the name dialog`() = runTest {
        val vm = vm(entries(3), tier = Tier.FREE)
        assertFalse(vm.requestAdd())
        assertTrue(vm.limitDialog.value)
    }

    @Test
    fun `free at 3 entries creates nothing and shows the limit dialog`() = runTest {
        val repo = entries(3)
        val vm = vm(repo, tier = Tier.FREE)
        var created: Long? = null
        vm.create("Burpees") { created = it }
        runCurrent()
        assertNull(created)
        assertEquals(3, repo.state.value.size)
        assertTrue(vm.limitDialog.value)
    }

    @Test
    fun `a free user over the limit keeps every entry but can't add one`() = runTest {
        val repo = entries(5)
        val vm = vm(repo, tier = Tier.FREE)
        assertEquals(5, (vm.uiState.value as EntryListUiState.Items).rows.size)
        assertFalse(vm.requestAdd())
    }

    @Test
    fun `pro at 10 entries can add and create proceeds`() = runTest {
        val repo = entries(10)
        val vm = vm(repo, tier = Tier.PRO)
        assertTrue(vm.requestAdd())
        vm.create("Burpees") {}
        runCurrent()
        assertEquals(11, repo.state.value.size)
        assertFalse(vm.limitDialog.value)
    }

    @Test
    fun `Go Pro starts the upgrade and closes the dialog`() = runTest {
        val vm = vm(entries(3), tier = Tier.FREE)
        vm.requestAdd()
        vm.goPro()
        assertEquals(1, proUpgrade.starts)
        assertFalse(vm.limitDialog.value)
    }

    @Test
    fun `Not now closes the dialog without starting the upgrade`() = runTest {
        val vm = vm(entries(3), tier = Tier.FREE)
        vm.requestAdd()
        vm.dismissLimit()
        assertEquals(0, proUpgrade.starts)
        assertFalse(vm.limitDialog.value)
    }

    @Test
    fun `maxEntries comes from FreeLimits`() = runTest {
        assertEquals(3, vm(FakeEntryRepository()).maxEntries)
    }

    @Test
    fun `a double submit at 2 free entries creates exactly one`() = runTest {
        // The gate holds the first create() inside repo.create(), after its limit check passed,
        // so the second lands while the first is still in flight.
        val repo = entries(2)
        val gate = CompletableDeferred<Unit>()
        repo.createGate = gate
        val vm = vm(repo, tier = Tier.FREE)
        var created = 0
        vm.create("Burpees") { created++ }
        vm.create("Burpees") { created++ }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, created)
        assertEquals(3, repo.state.value.size)
    }

    @Test
    fun `pro creates again once the previous create has finished`() = runTest {
        val repo = entries(3)
        val vm = vm(repo, tier = Tier.PRO)
        val ids = mutableListOf<Long>()
        vm.create("Burpees") { ids += it }
        runCurrent()
        vm.create("Lunges") { ids += it }
        runCurrent()
        assertEquals(2, ids.size)
        assertEquals(5, repo.state.value.size)
    }
}
