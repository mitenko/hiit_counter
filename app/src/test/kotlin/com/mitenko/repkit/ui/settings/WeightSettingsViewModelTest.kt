package com.mitenko.repkit.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.data.MigrationGate
import com.mitenko.repkit.data.RoomEntryRepository
import com.mitenko.repkit.data.WeightUnitDefaults
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.WeightProblem
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.withoutListWeight
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.common.SaveStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeightSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))
    private val kg = WeightUnit.KG

    /** The app default is lb, so a switch into a weight mode visibly takes it. */
    private val lbDefault = object : WeightUnitDefaults {
        override val weightUnitDefault: Flow<WeightUnit> = flowOf(WeightUnit.LB)
    }

    private fun weightEntry(weight: WeightConfig = WeightConfig(unit = kg), mode: ProgressMode = ProgressMode.WEIGHT, level: Int = 0) =
        testEntry(1, progression = ProgressionConfig(mode = mode, weight = weight), counter = CounterState(total = level))

    private fun TestScope.vm(repo: FakeEntryRepository, h: SavedStateHandle = handle) = WeightSettingsViewModel(h, repo, lbDefault, backgroundScope)

    @Test
    fun `loads the mode and the weight group`() = runTest {
        val vm = vm(FakeEntryRepository(listOf(weightEntry())))
        assertEquals(ProgressMode.WEIGHT, vm.mode.value)
        assertEquals(WeightConfig(unit = kg), vm.draft.value)
        assertEquals(SaveStatus.SAVED, vm.status.value)
    }

    @Test
    fun `a reps per set change auto-saves after 400 ms`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        vm.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
        advanceTimeBy(399)
        assertEquals(10, repo.find(1).progression.weight.repsPerSet)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(12, repo.find(1).progression.weight.repsPerSet)
    }

    @Test
    fun `an invalid draft is never saved and shows on its field`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(800, 1200)))))
        val vm = vm(repo)
        vm.updateNow(WeightField.LIST) { it.withoutListWeight(1) }
        assertEquals(WeightProblem.TooFewWeights, vm.validation.value.errors[WeightField.LIST])
        assertEquals(SaveStatus.INVALID, vm.status.value)
        vm.flush()
        runCurrent()
        assertEquals(0, repo.weightWrites)
    }

    @Test
    fun `a step that leaves the top unreachable lowers it, notes it under Step and saves`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = kg, steps = WeightSteps(2000, 250, 2750)))))
        val vm = vm(repo)
        vm.updateNow(WeightField.STEPS_STEP) { it.copy(steps = it.steps.copy(step = 500)) }
        runCurrent()
        assertEquals(WeightSteps(2000, 500, 2500), vm.draft.value!!.steps)
        assertEquals(WeightNote(WeightField.STEPS_STEP, listOf(WeightMove.TopLowered(2500))), vm.note.value)
        assertEquals(WeightSteps(2000, 500, 2500), repo.find(1).progression.weight.steps)
    }

    @Test
    fun `removing the starting weight moves it, and the save adds the moved current weight`() = runTest {
        val bells = WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(800, 1200, 1600), startWeight = 1200)
        val repo = FakeEntryRepository(listOf(weightEntry(bells, level = 1)))
        val vm = vm(repo)
        vm.updateNow(WeightField.LIST) { it.withoutListWeight(1) }
        runCurrent()
        assertEquals(800, vm.draft.value!!.startWeight)
        assertEquals(
            WeightNote(WeightField.LIST, listOf(WeightMove.StartWeightMoved(800), WeightMove.CurrentMoved(800, null))),
            vm.note.value,
        )
        assertEquals(0, repo.find(1).counter.total)
    }

    @Test
    fun `a page change clears the note and a late save doesn't bring it back`() = runTest {
        val bells = WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(800, 1200, 1600))
        val repo = FakeEntryRepository(listOf(weightEntry(bells, level = 1)))
        repo.weightGate = CompletableDeferred()
        val vm = vm(repo)
        vm.updateNow(WeightField.LIST) { it.withoutListWeight(1) }
        runCurrent()
        vm.clearNote()
        repo.weightGate!!.complete(Unit)
        runCurrent()
        assertNull(vm.note.value)
        assertEquals(0, repo.find(1).counter.total)
    }

    @Test
    fun `changing the unit asks first, then converts the draft and saves it`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(1000, 2000)))))
        val vm = vm(repo)
        vm.requestUnit(WeightUnit.LB)
        assertEquals(WeightUnit.LB, vm.unitPrompt.value)
        assertEquals(kg, repo.find(1).progression.weight.unit)
        vm.confirmUnit()
        runCurrent()
        assertNull(vm.unitPrompt.value)
        assertEquals(listOf(2200, 4400), vm.draft.value!!.list) // 22.05 and 44.09 lb, to the nearest 0.25
        assertEquals(WeightUnit.LB, repo.find(1).progression.weight.unit)
        assertEquals(listOf(2200, 4400), repo.find(1).progression.weight.list)
    }

    @Test
    fun `dismissing the unit prompt or asking for the same unit changes nothing`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        vm.requestUnit(kg)
        assertNull(vm.unitPrompt.value)
        vm.requestUnit(WeightUnit.LB)
        vm.dismissUnitPrompt()
        assertNull(vm.unitPrompt.value)
        runCurrent()
        assertEquals(0, repo.weightWrites)
    }

    @Test
    fun `a conversion that leaves an invalid draft never saves, and the store stays in the old unit`() = runTest {
        // 1 and 1.25 lb both round to 0.5 kg: one weight left (plan Spec note 43).
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = WeightUnit.LB, kind = WeightsKind.LIST, list = listOf(100, 125)))))
        val vm = vm(repo)
        vm.requestUnit(kg)
        vm.confirmUnit()
        runCurrent()
        assertEquals(listOf(50), vm.draft.value!!.list)
        assertEquals(WeightProblem.TooFewWeights, vm.validation.value.errors[WeightField.LIST])
        assertEquals(SaveStatus.INVALID, vm.status.value)
        assertEquals(0, repo.weightWrites)
        assertEquals(WeightConfig(unit = WeightUnit.LB, kind = WeightsKind.LIST, list = listOf(100, 125)), repo.find(1).progression.weight)
    }

    @Test
    fun `reset to defaults keeps the unit and saves at once`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = WeightUnit.LB, kind = WeightsKind.LIST, list = listOf(1000, 2000), repsPerSet = 5))))
        val vm = vm(repo)
        vm.resetToDefaults()
        runCurrent()
        assertEquals(WeightConfig(unit = WeightUnit.LB), vm.draft.value)
        assertEquals(WeightConfig(unit = WeightUnit.LB), repo.find(1).progression.weight)
    }

    @Test
    fun `a draft restored from the saved state handle is shown and saved`() = runTest {
        val first = vm(FakeEntryRepository(listOf(weightEntry())))
        first.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 15) }
        val copy = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val restored = vm(repo, copy)
        assertEquals(15, restored.draft.value!!.repsPerSet)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(15, repo.find(1).progression.weight.repsPerSet)
    }

    @Test
    fun `a clean draft follows the store`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        repo.setWeightConfig(1, WeightConfig(unit = kg, repsPerSet = 8))
        runCurrent()
        assertEquals(8, vm.draft.value!!.repsPerSet)
    }

    @Test
    fun `a weight save keeps an untouched counter untouched`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val open = object : MigrationGate {
                override suspend fun awaitReady() = Unit
            }
            val repo = RoomEntryRepository(db, open, FakeClock()) { " copy" }
            val id = repo.create("Curls")
            repo.switchMode(id, ProgressMode.WEIGHT, kg)
            val vm = WeightSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to id)), repo, lbDefault, backgroundScope)
            vm.draft.first { it != null }
            vm.updateNow(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
            repo.entry(id).first { it!!.progression.weight.repsPerSet == 12 }
            assertNull(db.entryDao().get(id)!!.total)
        } finally {
            db.close()
        }
    }

    @Test
    fun `another mode asks first and nothing changes until confirmed`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = vm(repo)
        assertEquals(ProgressMode.REPS, vm.mode.value)
        vm.requestMode(ProgressMode.WEIGHT)
        assertEquals(ProgressMode.WEIGHT, vm.modePrompt.value)
        vm.dismissModePrompt()
        assertNull(vm.modePrompt.value)
        runCurrent()
        assertTrue(repo.modeSwitches.isEmpty())
    }

    @Test
    fun `the current mode doesn't ask`() = runTest {
        val vm = vm(FakeEntryRepository(listOf(weightEntry())))
        vm.requestMode(ProgressMode.WEIGHT)
        assertNull(vm.modePrompt.value)
    }

    @Test
    fun `confirming starts fresh in the new mode with the app default unit`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 60, bestStreak = 5, currentStreak = 3))))
        val vm = vm(repo)
        vm.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        vm.confirmMode()
        runCurrent()
        assertNull(vm.modePrompt.value)
        assertEquals(listOf(1L to ProgressMode.REPS_THEN_WEIGHT), repo.modeSwitches)
        val e = repo.find(1)
        assertEquals(WeightUnit.LB, e.progression.weight.unit)
        assertEquals(listOf<Any>(0, 5, 3, true), listOf(e.counter.total, e.counter.bestStreak, e.counter.currentStreak, e.counter.freshStart))
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, vm.mode.value)
        assertEquals(WeightUnit.LB, vm.draft.value!!.unit)
    }

    @Test
    fun `a workout that already has a unit keeps it when it switches back into a weight mode`() = runTest {
        // Plan Spec note 46: the app default (lb here) only fills a missing unit.
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(weight = WeightConfig(unit = kg)))))
        val vm = vm(repo)
        vm.requestMode(ProgressMode.WEIGHT)
        vm.confirmMode()
        runCurrent()
        assertEquals(kg, repo.find(1).progression.weight.unit)
        assertEquals(kg, vm.draft.value!!.unit)
    }

    @Test
    fun `a pending weight edit lands before the switch`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        vm.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
        vm.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        repo.weightGate = CompletableDeferred()
        vm.confirmMode()
        runCurrent()
        assertTrue(repo.modeSwitches.isEmpty())
        repo.weightGate!!.complete(Unit)
        runCurrent()
        assertEquals(12, repo.find(1).progression.weight.repsPerSet)
        assertEquals(listOf(1L to ProgressMode.REPS_THEN_WEIGHT), repo.modeSwitches)
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, repo.find(1).progression.mode)
    }

    @Test
    fun `confirming drops unsaved invalid edits`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(800, 1200)))))
        val vm = vm(repo)
        vm.updateNow(WeightField.LIST) { it.withoutListWeight(0) } // one weight: invalid, never saved
        vm.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        vm.confirmMode()
        runCurrent()
        assertEquals(listOf(800, 1200), vm.draft.value!!.list)
        assertEquals(SaveStatus.SAVED, vm.status.value)
    }

    @Test
    fun `Start fresh shows Saved after a rejected save, not Not saved`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        repo.writeError = IllegalArgumentException("rejected")
        vm.updateNow(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
        runCurrent()
        assertEquals(SaveStatus.FAILED, vm.status.value)
        vm.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        vm.confirmMode()
        runCurrent()
        assertEquals(SaveStatus.SAVED, vm.status.value)
    }
}
