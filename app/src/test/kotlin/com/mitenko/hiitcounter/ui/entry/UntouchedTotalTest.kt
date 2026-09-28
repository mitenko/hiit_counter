package com.mitenko.hiitcounter.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.data.MigrationGate
import com.mitenko.hiitcounter.data.RoomEntryRepository
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeServiceStarter
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.entries.EntryListUiState
import com.mitenko.hiitcounter.ui.entries.EntryListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UntouchedTotalTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `an untouched entry shows its starting total on the list and the entry screen`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val clock = FakeClock()
            val open = object : MigrationGate {
                override suspend fun awaitReady() = Unit
            }
            val repo = RoomEntryRepository(db, open, clock)
            val id = repo.create("Burpees")
            assertNull(db.entryDao().get(id)!!.total)
            val controller = TimerController(backgroundScope) { testScheduler.currentTime }
            val entryVm = EntryViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to id)), repo, controller, FakeServiceStarter(controller), clock)
            val listVm = EntryListViewModel(repo, clock)
            backgroundScope.launch { entryVm.uiState.collect {} }
            backgroundScope.launch { listVm.uiState.collect {} }
            val entry = entryVm.uiState.first { it.name == "Burpees" }
            assertEquals(48, entry.total)
            assertEquals(List(8) { 6 }, entry.reps)
            val list = listVm.uiState.first { it is EntryListUiState.Items } as EntryListUiState.Items
            assertEquals(48, list.rows.single().reps)
        } finally {
            db.close()
        }
    }
}
