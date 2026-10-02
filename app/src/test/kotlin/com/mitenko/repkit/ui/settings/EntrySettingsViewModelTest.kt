package com.mitenko.repkit.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.repkit.domain.FreeLimits
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.domain.WorkoutSnapshot
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.testutil.FakeEntitlements
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.FakeProUpgrade
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import com.mitenko.repkit.testutil.fixedWallNow

@OptIn(ExperimentalCoroutinesApi::class)
class EntrySettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeEntryRepository(listOf(testEntry(1, "Burpees"), testEntry(2, "Lunges")))
    private val snapshot = WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig())

    private class Harness(val vm: EntrySettingsViewModel, val controller: TimerController)

    private val proUpgrade = FakeProUpgrade()

    private fun TestScope.harness(repository: FakeEntryRepository = repo, tier: Tier = Tier.PRO): Harness {
        val controller = TimerController(backgroundScope, wallNow = fixedWallNow) { testScheduler.currentTime }
        val vm = EntrySettingsViewModel(
            SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repository, controller, FakeEntitlements(tier), FreeLimits(), proUpgrade,
        )
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

    @Test
    fun `setType saves at once, even while busy, and keeps every other value`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot)
        runCurrent()
        assertTrue(h.vm.uiState.value.busy)
        val before = repo.find(1)
        h.vm.setType(EntryType.CHECK_IN)
        runCurrent()
        assertEquals(before.copy(type = EntryType.CHECK_IN), repo.find(1))
        assertEquals(EntryType.CHECK_IN, h.vm.uiState.value.type)
        assertEquals(1, repo.typeWrites)
    }

    @Test
    fun `setType racing a delete reports missing`() = runTest {
        val h = harness()
        repo.writeError = EntryNotFound(1)
        h.vm.setType(EntryType.CHECK_IN)
        runCurrent()
        assertTrue(h.vm.missing.value)
        assertEquals(EntryType.WORKOUT, repo.find(1).type)
    }

    // Spec revision 18 §3: Duplicate respects the free tier's entry limit.

    private fun entries(n: Int) = FakeEntryRepository(List(n) { testEntry(it + 1L) })

    @Test
    fun `free at 3 entries duplicates nothing and shows the limit dialog`() = runTest {
        val three = entries(3)
        val h = harness(three, tier = Tier.FREE)
        var copy: Long? = null
        h.vm.duplicate { copy = it }
        runCurrent()
        assertNull(copy)
        assertEquals(3, three.state.value.size)
        assertTrue(h.vm.uiState.value.limitDialog)
    }

    @Test
    fun `free at 2 entries duplicates`() = runTest {
        val two = entries(2)
        val h = harness(two, tier = Tier.FREE)
        var copy: Long? = null
        h.vm.duplicate { copy = it }
        runCurrent()
        assertEquals(3, two.state.value.size)
        assertEquals("Entry 1 copy", two.find(copy!!).name)
        assertFalse(h.vm.uiState.value.limitDialog)
    }

    @Test
    fun `pro at 10 entries duplicates`() = runTest {
        val ten = entries(10)
        val h = harness(ten, tier = Tier.PRO)
        var copy: Long? = null
        h.vm.duplicate { copy = it }
        runCurrent()
        assertEquals(11, ten.state.value.size)
        assertEquals("Entry 1 copy", ten.find(copy!!).name)
    }

    @Test
    fun `Go Pro starts the upgrade and closes the dialog`() = runTest {
        val h = harness(entries(3), tier = Tier.FREE)
        h.vm.duplicate {}
        runCurrent()
        h.vm.goPro()
        runCurrent()
        assertEquals(1, proUpgrade.starts)
        assertFalse(h.vm.uiState.value.limitDialog)
    }

    @Test
    fun `Not now closes the dialog without starting the upgrade`() = runTest {
        val h = harness(entries(3), tier = Tier.FREE)
        h.vm.duplicate {}
        runCurrent()
        h.vm.dismissLimit()
        runCurrent()
        assertEquals(0, proUpgrade.starts)
        assertFalse(h.vm.uiState.value.limitDialog)
    }
}
