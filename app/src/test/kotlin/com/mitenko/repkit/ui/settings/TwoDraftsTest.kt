package com.mitenko.repkit.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.data.MigrationGate
import com.mitenko.repkit.data.RoomEntryRepository
import com.mitenko.repkit.data.WeightUnitDefaults
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.domain.WeightConversion
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.withNewWeightHold
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TwoDraftsTest {
    @get:Rule val main = MainDispatcherRule()

    private lateinit var db: HiitDatabase

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private val lbDefault = object : WeightUnitDefaults {
        override val weightUnitDefault: Flow<WeightUnit> = flowOf(WeightUnit.LB)
    }

    private class Page(val repo: RoomEntryRepository, val id: Long, val reps: ProgressionSettingsViewModel, val weights: WeightSettingsViewModel)

    /**
     * A real (wall-clock) 5 s cap on [block]: Room's Flow queries dispatch off the test dispatcher onto
     * a real one, so a `withTimeout` left on the ambient (virtual-time) test dispatcher would fire at
     * once, under `runTest`'s idle auto-advance, instead of actually waiting for them.
     */
    private suspend fun <T> real5s(block: suspend () -> T): T = withContext(Dispatchers.Default) { withTimeout(5_000) { block() } }

    /** A Weight-mode entry (kg) with both of the Progression page's ViewModels loaded, sharing one handle as in the pager. */
    private suspend fun TestScope.page(): Page {
        val open = object : MigrationGate {
            override suspend fun awaitReady() = Unit
        }
        val repo = RoomEntryRepository(db, open, FakeClock()) { " copy" }
        val id = repo.create("Curls")
        repo.switchMode(id, ProgressMode.WEIGHT, WeightUnit.KG)
        val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to id))
        val reps = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        val weights = WeightSettingsViewModel(handle, repo, lbDefault, backgroundScope)
        real5s { reps.draft.first { it != null } }
        real5s { weights.draft.first { it != null } }
        return Page(repo, id, reps, weights)
    }

    @Test
    fun `both dirty drafts flushed together keep each other's fields, in either order`() = runTest {
        for (weightsFirst in listOf(false, true)) {
            val p = page()
            p.reps.update { it.copy(windowHours = 40, hold = false) }
            p.weights.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
            if (weightsFirst) {
                p.weights.flush()
                p.reps.flush()
            } else {
                p.reps.flush()
                p.weights.flush()
            }
            val e = real5s { p.repo.entry(p.id).first { it!!.progression.windowHours == 40 && it.progression.weight.repsPerSet == 12 } }!!
            assertEquals(false, e.progression.hold)
            assertEquals(ProgressMode.WEIGHT, e.progression.mode)
        }
    }

    @Test
    fun `both pending drafts survive Start fresh`() = runTest {
        val p = page()
        p.reps.update { it.copy(windowHours = 40) }
        p.weights.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
        p.weights.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        p.reps.flush() // the dialog's beforeSwitch
        p.weights.confirmMode()
        val e = real5s { p.repo.entry(p.id).first { it!!.progression.mode == ProgressMode.REPS_THEN_WEIGHT && it.progression.windowHours == 40 } }!!
        assertEquals(12, e.progression.weight.repsPerSet)
        val row = db.entryDao().get(p.id)!!
        assertNull(row.total)
        assertTrue(row.freshStart)
    }

    @Test
    fun `a unit change saved while a Reps-side edit is pending loses neither`() = runTest {
        val p = page()
        p.reps.update { it.copy(windowHours = 40) }
        p.weights.requestUnit(WeightUnit.LB)
        p.weights.confirmUnit()
        p.reps.flush()
        val e = real5s { p.repo.entry(p.id).first { it!!.progression.weight.unit == WeightUnit.LB && it.progression.windowHours == 40 } }!!
        assertEquals(WeightConversion.convert(WeightConfig(unit = WeightUnit.KG), WeightUnit.LB).list, e.progression.weight.list)
    }

    @Test
    fun `the shared Hold switch and the weight holds save independently`() = runTest {
        val p = page()
        p.reps.updateNow { it.copy(hold = false) }
        p.weights.updateNow(WeightField.HOLDS) { it.withNewWeightHold(ProgressMode.WEIGHT) }
        val e = real5s { p.repo.entry(p.id).first { !it!!.progression.hold && it.progression.weight.holds.size == 1 } }!!
        assertEquals(ProgressMode.WEIGHT, e.progression.mode)
    }
}
