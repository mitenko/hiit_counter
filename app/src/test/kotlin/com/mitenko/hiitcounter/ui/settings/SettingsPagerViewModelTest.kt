package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPagerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `exposes the entry name and follows a rename`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, name = "Burpees")))
        val vm = SettingsPagerViewModel(handle, repo)
        assertEquals("Burpees", vm.name.value)
        repo.rename(1, "Lunges")
        assertEquals("Lunges", vm.name.value)
    }

    @Test
    fun `a missing entry reports missing after loading`() = runTest {
        val vm = SettingsPagerViewModel(handle, FakeEntryRepository())
        runCurrent()
        assertTrue(vm.missing.value)
        assertNull(vm.name.value)
    }
}
