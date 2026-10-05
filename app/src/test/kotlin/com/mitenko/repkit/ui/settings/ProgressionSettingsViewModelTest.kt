package com.mitenko.repkit.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.repkit.domain.Field
import com.mitenko.repkit.domain.FieldMessage
import com.mitenko.repkit.domain.HoldField
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.ProgressionField
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.common.SaveStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `a hold at change auto-saves after 400 ms and resets the hold count`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 64, holdCount = 2))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(holds = listOf(Hold(66, 3))) }
        advanceTimeBy(399)
        assertEquals(ProgressionConfig(), repo.find(1).progression)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(ProgressionConfig(holds = listOf(Hold(66, 3))), repo.find(1).progression)
        assertEquals(0, repo.find(1).counter.holdCount)
    }

    @Test
    fun `a floor change keeps the hold count`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 64, holdCount = 2))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(floor = 40) }
        vm.flush()
        runCurrent()
        assertEquals(40, repo.find(1).progression.floor)
        assertEquals(2, repo.find(1).counter.holdCount)
    }

    @Test
    fun `an invalid draft is never saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(cap = 40) }
        assertTrue(Field.CAP in vm.validation.value.errors)
        assertEquals(SaveStatus.INVALID, vm.status.value)
        advanceTimeBy(1_000)
        vm.flush()
        runCurrent()
        assertEquals(0, repo.progressionWrites)
        assertEquals(ProgressionConfig(), repo.find(1).progression)
    }

    @Test
    fun `reset to defaults saves the defaults at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(cap = 90, hold = false))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(90, vm.draft.value?.cap)
        vm.resetToDefaults()
        runCurrent()
        assertEquals(ProgressionDraft.from(ProgressionConfig()), vm.draft.value)
        assertEquals(ProgressionConfig(), repo.find(1).progression)
    }

    @Test
    fun `the penalty steps in half hours and a stored non-multiple is saved exactly`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(penaltyHoursPerRep = 0.3))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(0.3, vm.draft.value!!.penalty.hours, 0.0)
        vm.update { it.copy(windowHours = 30) }
        vm.flush()
        runCurrent()
        assertEquals(0.3, repo.find(1).progression.penaltyHoursPerRep, 0.0)
        vm.update { it.copy(penalty = it.penalty.plus()) }
        vm.flush()
        runCurrent()
        assertEquals(0.5, repo.find(1).progression.penaltyHoursPerRep, 0.0)
    }

    @Test
    fun `the hold switch saves at once and keeps the hidden values`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(holds = listOf(Hold(66, 3))))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.updateNow { it.copy(hold = false) }
        runCurrent()
        assertEquals(ProgressionConfig(holds = listOf(Hold(66, 3)), hold = false), repo.find(1).progression)
        vm.updateNow { it.copy(hold = true) }
        runCurrent()
        assertEquals(ProgressionConfig(holds = listOf(Hold(66, 3))), repo.find(1).progression)
        assertEquals(2, repo.progressionWrites)
    }

    @Test
    fun `with the hold off the hold checks and hint are skipped`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(holds = listOf(Hold(64, 0))))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(mapOf(0 to FieldMessage.HoldDisabled), vm.validation.value.holdHints)
        vm.updateNow { it.copy(hold = false) }
        assertTrue(vm.validation.value.holdHints.isEmpty())
        vm.update { it.copy(holds = listOf(Hold(80, 0))) } // hidden, and at or above the cap: it would give the hint with the hold on
        assertTrue(vm.validation.value.holdHints.isEmpty())
        vm.flush()
        runCurrent()
        assertEquals(SaveStatus.SAVED, vm.status.value)
        assertEquals(ProgressionConfig(holds = listOf(Hold(80, 0)), hold = false), repo.find(1).progression)
    }

    @Test
    fun `the draft and its switch are restored from the saved state handle`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(penaltyHoursPerRep = 0.3))))
        val first = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        first.update { it.copy(cap = 40, hold = false) } // invalid (cap < starting total): never saved
        val restored = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        val d = restored.draft.value!!
        assertEquals(40, d.cap)
        assertFalse(d.hold)
        assertEquals(0.3, d.penalty.hours, 0.0)
        assertEquals(SaveStatus.INVALID, restored.status.value)
    }

    @Test
    fun `a clean draft follows a progression stored elsewhere, a dirty one keeps its edits`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        runCurrent()
        repo.overwriteCounter(1, total = 80, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // the Current page widens the cap
        runCurrent()
        assertEquals(80, vm.draft.value!!.cap)
        vm.update { it.copy(cap = 40) } // invalid, so never saved, and not overwritten by the store either
        repo.overwriteCounter(1, total = 90, bestStreak = 0, currentStreak = 0, lastCheckIn = null)
        runCurrent()
        assertEquals(40, vm.draft.value!!.cap)
        assertEquals(0, repo.progressionWrites)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(floor = 40) }
        repo.delete(1)
        vm.flush()
        runCurrent()
        assertTrue(vm.missing.value)
    }
    private val two = ProgressionConfig(holds = listOf(Hold(56, 3), Hold(64, 4)))

    @Test
    fun `adding and removing a hold save at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = two)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.addHold()
        runCurrent()
        assertEquals(listOf(Hold(56, 3), Hold(64, 4), Hold(68, 4)), repo.find(1).progression.holds)
        vm.removeHold(0)
        runCurrent()
        assertEquals(listOf(Hold(64, 4), Hold(68, 4)), repo.find(1).progression.holds)
        assertEquals(2, repo.progressionWrites)
    }

    @Test
    fun `a stepper change then a remove within 400 ms is one write with both changes`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = two)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.updateHold(1) { h -> h.copy(forCount = 5) } }
        advanceTimeBy(200)
        vm.removeHold(0)
        runCurrent()
        assertEquals(1, repo.progressionWrites)
        assertEquals(listOf(Hold(64, 5)), repo.find(1).progression.holds)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, repo.progressionWrites)
    }

    @Test
    fun `removing the last hold leaves an empty list and the switch on`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.removeHold(0)
        runCurrent()
        assertEquals(ProgressionConfig(holds = emptyList()), repo.find(1).progression)
    }

    @Test
    fun `a duplicate hold at is never saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = two)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.updateNow { it.updateHold(1) { h -> h.copy(at = 56) } }
        runCurrent()
        assertEquals(mapOf(1 to mapOf(HoldField.AT to FieldMessage.DuplicateHold(56))), vm.validation.value.holdErrors)
        assertEquals(SaveStatus.INVALID, vm.status.value)
        assertEquals(0, repo.progressionWrites)
        assertEquals(two, repo.find(1).progression)
    }

    @Test
    fun `the holds are restored from the saved state handle`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = two)))
        val first = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        first.update { it.copy(cap = 40, holds = listOf(Hold(56, 3), Hold(60, 2), Hold(64, 4))) } // invalid: never saved
        val restored = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(listOf(Hold(56, 3), Hold(60, 2), Hold(64, 4)), restored.draft.value!!.holds)
        assertEquals(SaveStatus.INVALID, restored.status.value)
    }

    // Spec revision 28: the field you edit wins.

    @Test
    fun `a minimum raised above starting reps raises them in the draft at once, then the save moves the current total`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 48, holdCount = 2))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update(ProgressionField.FLOOR) { it.copy(floor = 50) }
        assertEquals(50, vm.draft.value!!.startingTotal) // before any save
        assertEquals(SaveStatus.SAVED, vm.status.value)
        assertEquals(ProgressionNote(ProgressionField.FLOOR, listOf(Move.StartingRaised(50))), vm.note.value)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(ProgressionConfig(startingTotal = 50, floor = 50), repo.find(1).progression)
        assertEquals(CounterState(total = 50, holdCount = 0), repo.find(1).counter)
        assertEquals(ProgressionNote(ProgressionField.FLOOR, listOf(Move.StartingRaised(50), Move.CurrentRaised(50))), vm.note.value)
    }

    @Test
    fun `a maximum lowered below starting reps lowers them and the save lowers the current total`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(startingTotal = 60), counter = CounterState(total = 66))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.updateNow(ProgressionField.CAP) { it.copy(cap = 55) }
        assertEquals(55, vm.draft.value!!.startingTotal)
        runCurrent()
        assertEquals(ProgressionConfig(startingTotal = 55, cap = 55), repo.find(1).progression)
        assertEquals(55, repo.find(1).counter.total)
        assertEquals(ProgressionNote(ProgressionField.CAP, listOf(Move.StartingLowered(55), Move.CurrentLowered(55))), vm.note.value)
    }

    @Test
    fun `starting reps above the maximum raise it and below the minimum lower it`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.updateNow(ProgressionField.STARTING_TOTAL) { it.copy(startingTotal = 80) }
        assertEquals(80, vm.draft.value!!.cap)
        assertEquals(ProgressionNote(ProgressionField.STARTING_TOTAL, listOf(RangeChange.RaisedMax(80))), vm.note.value)
        runCurrent()
        assertEquals(ProgressionConfig(startingTotal = 80, cap = 80), repo.find(1).progression)
        vm.updateNow(ProgressionField.STARTING_TOTAL) { it.copy(startingTotal = 40) }
        assertEquals(40, vm.draft.value!!.floor)
        assertEquals(ProgressionNote(ProgressionField.STARTING_TOTAL, listOf(RangeChange.LoweredMin(40))), vm.note.value)
        runCurrent()
        assertEquals(ProgressionConfig(startingTotal = 40, floor = 40, cap = 80), repo.find(1).progression)
        assertEquals(48, repo.find(1).counter.total) // inside 40..80, so it stays
    }

    @Test
    fun `a field-less edit resolves nothing, so an out-of-order draft stays invalid`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(floor = 50) }
        assertEquals(48, vm.draft.value!!.startingTotal)
        assertEquals(SaveStatus.INVALID, vm.status.value)
        assertNull(vm.note.value)
    }

    @Test
    fun `the note clears on the next edit and on a page change`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update(ProgressionField.FLOOR) { it.copy(floor = 49) }
        assertEquals(ProgressionNote(ProgressionField.FLOOR, listOf(Move.StartingRaised(49))), vm.note.value)
        vm.update { it.copy(windowHours = 30) } // any next edit
        assertNull(vm.note.value)
        vm.update(ProgressionField.FLOOR) { it.copy(floor = 50) }
        assertEquals(ProgressionNote(ProgressionField.FLOOR, listOf(Move.StartingRaised(50))), vm.note.value)
        // A page change flushes the pending save, then hides the note, as the pager does.
        vm.flush()
        vm.clearNote()
        assertNull(vm.note.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(50, repo.find(1).counter.total)
        assertNull(vm.note.value)
    }

    @Test
    fun `a save that finishes after the note was dismissed never brings it back`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 66))))
        val gate = CompletableDeferred<Unit>()
        repo.progressionGate = gate
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.updateNow(ProgressionField.CAP) { it.copy(cap = 60) }
        runCurrent()
        assertEquals(1, repo.progressionWrites) // in flight, held by the gate
        vm.clearNote() // the user changed page
        gate.complete(Unit)
        runCurrent()
        assertEquals(60, repo.find(1).counter.total)
        assertNull(vm.note.value)
    }

    @Test
    fun `a page change right after its flush drops the note even though the queued write moves the total`() = runTest {
        // Production order on a page change: flush, then clearNote, with nothing run in between.
        // A queued (not eager) dispatcher makes the write start only after clearNote.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 66))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        runCurrent() // the draft loads
        vm.update(ProgressionField.CAP) { it.copy(cap = 60) }
        vm.flush()
        vm.clearNote()
        runCurrent()
        assertEquals(1, repo.progressionWrites)
        assertEquals(60, repo.find(1).counter.total) // a real Move happened
        assertNull(vm.note.value)
    }

    @Test
    fun `reset to defaults notes a moved current total under the reset button`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(cap = 90), counter = CounterState(total = 85))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update(ProgressionField.CAP) { it.copy(cap = 95) }
        vm.resetToDefaults()
        runCurrent()
        assertEquals(72, repo.find(1).counter.total)
        assertEquals(ProgressionNote(null, listOf(Move.CurrentLowered(72))), vm.note.value)
    }

    @Test
    fun `a clean draft follows the store when the entry also has a weight group`() = runTest {
        // The draft holds the Reps fields only (setProgression never writes the mode or the weight group),
        // so an entry with a stored weight group must still count as clean.
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(weight = WeightConfig(unit = WeightUnit.KG)))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        runCurrent()
        repo.overwriteCounter(1, total = 80, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // the Current page widens the cap
        runCurrent()
        assertEquals(80, vm.draft.value!!.cap)
        vm.update { it.copy(floor = 40) }
        vm.flush()
        runCurrent()
        // Nothing stale saved: the widened cap and the total survive, and the weight group is untouched.
        assertEquals(40 to 80, repo.find(1).progression.let { it.floor to it.cap })
        assertEquals(80, repo.find(1).counter.total)
        assertEquals(WeightUnit.KG, repo.find(1).progression.weight.unit)
    }
}
