package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `loads the entry's timing, edits and saves it`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, timing = TimingConfig(sets = 5)), testEntry(2)))
        val vm = TimingSettingsViewModel(handle, repo)
        assertEquals(TimingConfig(sets = 5), vm.draft.value)
        vm.update { it.copy(sets = 6) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(6, repo.find(1).timing.sets)
        assertEquals(TimingConfig(), repo.find(2).timing)
    }

    @Test
    fun `over two hours is not saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo)
        vm.update { it.copy(sets = 20, workSec = 3599) }
        assertTrue(Field.TOTAL_DURATION in vm.validation.value.errors)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertEquals(TimingConfig(), repo.find(1).timing)
    }

    @Test
    fun `a missing entry reports missing after loading`() = runTest {
        val vm = TimingSettingsViewModel(handle, FakeEntryRepository())
        runCurrent()
        assertTrue(vm.missing.value)
        assertNull(vm.draft.value)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo)
        repo.delete(1)
        var saved = false
        vm.save { saved = true }
        runCurrent()
        assertFalse(saved)
        assertTrue(vm.missing.value)
    }
}
