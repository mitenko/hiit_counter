package com.mitenko.repkit.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.HistoryRange
import com.mitenko.repkit.domain.Prescription
import com.mitenko.repkit.domain.RunLog
import com.mitenko.repkit.domain.RunStatus
import com.mitenko.repkit.domain.RunSummary
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.domain.WorkoutSnapshot
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.HoldKind
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.FakeServiceStarter
import com.mitenko.repkit.testutil.FakeServiceStarter.Behavior
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.testutil.rawText
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.UiText
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import com.mitenko.repkit.testutil.fixedWallNow

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(instant = Instant.parse("2026-09-24T15:00:00Z")) // 08:00 PDT
    private val repo = FakeEntryRepository(
        listOf(
            testEntry(
                1, "Burpees",
                counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
            ),
            testEntry(2, "Lunges"),
        ),
    )

    private class Harness(val vm: EntryViewModel, val controller: TimerController, val starter: FakeServiceStarter)

    private fun TestScope.harness(
        behavior: Behavior = Behavior.SUCCEED,
        repository: FakeEntryRepository = repo,
        runLog: RunLog = RunLog {},
    ): Harness {
        val controller = TimerController(backgroundScope, wallNow = fixedWallNow, runLog = runLog) { testScheduler.currentTime }
        val starter = FakeServiceStarter(controller, behavior)
        val vm = EntryViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repository, controller, starter, clock)
        backgroundScope.launch { vm.uiState.collect {} }
        return Harness(vm, controller, starter)
    }

    @Test
    fun `the state shows the entry's distributed reps, total and streaks`() = runTest {
        val h = harness()
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals("Burpees", s.name)
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), s.reps)
        assertEquals(65, s.total)
        assertEquals(24, s.bestStreak)
        assertEquals(4, s.currentStreak)
        assertFalse(s.checkedInToday)
    }

    @Test
    fun `reps follow the entry's own number of sets`() = runTest {
        val h = harness()
        repo.state.update { list -> list.map { if (it.id == 1L) it.copy(timing = it.timing.copy(sets = 5)) else it } }
        runCurrent()
        assertEquals(listOf(13, 13, 13, 13, 13), h.vm.uiState.value.reps)
    }

    @Test
    fun `a missing entry reports missing only after loading`() = runTest {
        val notReady = FakeEntryRepository(emptyList(), ready = false)
        val h = harness(repository = notReady)
        runCurrent()
        assertFalse(h.vm.missing.value)
        notReady.readiness.complete(Unit)
        runCurrent()
        assertTrue(h.vm.missing.value)
    }

    @Test
    fun `a successful start freezes the snapshot and checks in this entry only`() = runTest {
        val h = harness()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertEquals(1, repo.checkInCalls)
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        assertEquals(WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig()), h.controller.snapshot)
        assertEquals(66, repo.find(1).counter.total)
        assertNull(repo.find(2).counter.lastCheckIn)
        assertTrue(h.vm.uiState.value.checkedInToday)
        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `a service start exception leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.THROW_ON_START)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.rawText().contains("not allowed"))
    }

    @Test
    fun `a foreground failure inside the service leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.FAIL_IN_SERVICE)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.rawText().contains("boom"))
    }

    @Test
    fun `no service response times out without checking in`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        advanceTimeBy(EntryViewModel.SERVICE_START_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertEquals(
            UiText.Res(R.string.error_start, listOf(UiText.Res(R.string.error_service_no_response))),
            h.vm.uiState.value.error,
        )
    }

    @Test
    fun `start is debounced and reports starting while in flight`() = runTest {
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
        repo.checkInError = IOException("disk full")
        h.vm.onStart()
        runCurrent()
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.rawText().contains("disk full"))
    }

    @Test
    fun `checkIn throwing EntryNotFound during PREPARING returns to idle with an error`() = runTest {
        val h = harness()
        repo.checkInError = EntryNotFound(1)
        h.vm.onStart()
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.rawText().contains("Entry 1 not found"))
        assertFalse(h.vm.uiState.value.starting)
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

    @Test
    fun `check in updates the counter and then reads as checked in today`() = runTest {
        val h = harness()
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertEquals(66, h.vm.uiState.value.total)
        assertTrue(h.vm.uiState.value.checkedInToday)
        assertFalse(h.vm.uiState.value.checkingIn)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
    }

    @Test
    fun `check in ignores taps on either button while its call is in flight`() = runTest {
        val h = harness()
        val gate = CompletableDeferred<Unit>()
        repo.checkInGate = gate
        h.vm.onCheckIn()
        h.vm.onCheckIn()
        h.vm.onStart()
        runCurrent()
        assertTrue(h.vm.uiState.value.checkingIn)
        assertEquals(0, h.starter.calls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertFalse(h.vm.uiState.value.checkingIn)
        assertEquals(66, repo.find(1).counter.total)
    }

    @Test
    fun `start after a check-in starts with no second check-in`() = runTest {
        val h = harness()
        h.vm.onCheckIn()
        runCurrent()
        h.vm.onStart()
        runCurrent()
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        // Start still calls checkIn; the second call is AlreadyToday and writes nothing.
        assertEquals(2, repo.checkInCalls)
        assertEquals(66, repo.find(1).counter.total)
        assertEquals(66, h.controller.state.value!!.totalReps)
    }

    @Test
    fun `Start on a Timer only entry runs the timer with countsReps false and checks in streak-only`() = runTest {
        val habit = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Stretch", type = EntryType.CHECK_IN,
                    counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
                ),
            ),
        )
        val h = harness(repository = habit)
        runCurrent()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertEquals(1, habit.checkInCalls)
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        assertEquals(WorkoutSnapshot(1L, "Stretch", TimingConfig(), CueConfig(), countsReps = false), h.controller.snapshot)
        // Streak-only: the total never moves for a Timer only check-in.
        assertEquals(65, habit.find(1).counter.total)
        assertEquals(5, habit.find(1).counter.currentStreak)
    }

    @Test
    fun `a Timer only entry checks in without changing its total`() = runTest {
        val habit = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Stretch", type = EntryType.CHECK_IN,
                    counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
                ),
            ),
        )
        val h = harness(repository = habit)
        runCurrent()
        assertEquals(EntryType.CHECK_IN, h.vm.uiState.value.type)
        h.vm.onCheckIn()
        runCurrent()
        val counter = habit.find(1).counter
        assertEquals(65, counter.total)
        assertEquals(5, counter.currentStreak)
        assertEquals(clock.instant, counter.lastCheckIn)
        assertTrue(h.vm.uiState.value.checkedInToday)
    }

    @Test
    fun `a failed check-in shows an error, and a deleted entry pops`() = runTest {
        val h = harness()
        repo.checkInError = IOException("disk full")
        h.vm.onCheckIn()
        runCurrent()
        assertTrue(h.vm.uiState.value.error!!.rawText().contains("disk full"))
        assertFalse(h.vm.uiState.value.checkingIn)
        assertFalse(h.vm.missing.value)
        repo.checkInError = EntryNotFound(1)
        h.vm.onCheckIn()
        runCurrent()
        assertTrue(h.vm.missing.value)
    }

    private val older = CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 60)
    private val newer = CheckInPoint(Instant.parse("2026-09-23T12:55:00Z"), 65)

    @Test
    fun `the state carries every point oldest first, with now and the zone`() = runTest {
        repo.points.value = mapOf(1L to listOf(newer, older))
        val h = harness()
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals(listOf(older, newer), s.points)
        assertEquals(clock.instant, s.now)
        assertEquals(clock.zoneId, s.zone)
    }

    @Test
    fun `a check-in adds its point at once`() = runTest {
        val h = harness()
        runCurrent()
        assertTrue(h.vm.uiState.value.points.isEmpty())
        h.vm.onCheckIn()
        runCurrent()
        assertEquals(listOf(CheckInPoint(clock.instant, 66)), h.vm.uiState.value.points)
    }

    @Test
    fun `resume re-reads now for the range maths`() = runTest {
        val h = harness()
        runCurrent()
        clock.instant = Instant.parse("2026-09-25T15:00:00Z")
        h.vm.onResume()
        runCurrent()
        assertEquals(clock.instant, h.vm.uiState.value.now)
    }

    @Test
    fun `check in on a Counter entry emits a highlight with the right index and UP`() = runTest {
        val h = harness()
        runCurrent()
        assertNull(h.vm.highlight.value)
        h.vm.onCheckIn()
        runCurrent()
        // 65 -> 66 reps across 8 sets: [9,8,8,8,8,8,8,8] -> [9,9,8,8,8,8,8,8], only index 1 gains.
        assertEquals(mapOf(1 to RepsColumnLayout.Change.UP), h.vm.highlight.value?.changes)
    }

    private fun TestScope.holdHarness(total: Int, holdCount: Int): Harness {
        val onHold = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Pushups",
                    counter = CounterState(
                        total = total, bestStreak = 4, currentStreak = 4,
                        lastCheckIn = Instant.parse("2026-09-23T12:55:00Z"), holdCount = holdCount,
                    ),
                ),
            ),
        )
        return harness(repository = onHold)
    }

    @Test
    fun `a check-in that stays on a hold flashes every cell neutral with the hold day`() = runTest {
        // Default holds are [64 x 4]: day 1 was yesterday, so today is day 2 and the total stays 64.
        val h = holdHarness(total = 64, holdCount = 1)
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        val highlight = h.vm.highlight.value
        assertEquals((0 until 8).associateWith { RepsColumnLayout.Change.HOLD }, highlight?.changes)
        assertEquals(HoldStatus(at = 64, day = 2, of = 4), highlight?.hold)
    }

    @Test
    fun `finishing a hold climbs, so it highlights UP rather than the hold`() = runTest {
        val h = holdHarness(total = 64, holdCount = 4)
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        val highlight = h.vm.highlight.value
        assertEquals(mapOf(0 to RepsColumnLayout.Change.UP), highlight?.changes)
        assertNull(highlight?.hold)
    }

    @Test
    fun `start on a hold also flashes the hold`() = runTest {
        val h = holdHarness(total = 64, holdCount = 1)
        runCurrent()
        h.vm.onStart()
        runCurrent()
        assertEquals(HoldStatus(at = 64, day = 2, of = 4), h.vm.highlight.value?.hold)
    }

    @Test
    fun `inside a From range the hold announcement names the total`() = runTest {
        // Spec rev 34 §6: from 64 for 2; 66 reached yesterday (day 1), so today is day 2 at 66.
        val repo = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Pushups",
                    progression = ProgressionConfig(holds = listOf(Hold(64, 2, HoldKind.FROM))),
                    counter = CounterState(
                        total = 66, bestStreak = 4, currentStreak = 4,
                        lastCheckIn = Instant.parse("2026-09-23T12:55:00Z"), holdCount = 1,
                    ),
                ),
            ),
        )
        val h = harness(repository = repo)
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        val highlight = h.vm.highlight.value
        assertEquals((0 until 8).associateWith { RepsColumnLayout.Change.HOLD }, highlight?.changes)
        assertEquals(HoldStatus(at = 66, day = 2, of = 2), highlight?.hold)
    }

    @Test
    fun `a check-in at the cap with no hold still shows nothing`() = runTest {
        val h = holdHarness(total = 72, holdCount = 0)
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        assertNull(h.vm.highlight.value)
    }

    @Test
    fun `a check-in after a miss emits DOWN for the right indices`() = runTest {
        val h = harness()
        runCurrent()
        // ~80h after the last check-in: a miss with penalty 2, so 65 -> 63.
        clock.instant = Instant.parse("2026-09-26T20:55:00Z")
        h.vm.onCheckIn()
        runCurrent()
        // [9,8,8,8,8,8,8,8] -> [8,8,8,8,8,8,8,7]: the first and last sets drop.
        assertEquals(mapOf(0 to RepsColumnLayout.Change.DOWN, 7 to RepsColumnLayout.Change.DOWN), h.vm.highlight.value?.changes)
    }

    @Test
    fun `AlreadyToday emits nothing`() = runTest {
        val h = harness()
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        assertNotNull(h.vm.highlight.value)
        h.vm.highlightShown()
        h.vm.onCheckIn() // same day: AlreadyToday, writes nothing
        runCurrent()
        assertNull(h.vm.highlight.value)
    }

    @Test
    fun `highlightShown clears it`() = runTest {
        val h = harness()
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        assertNotNull(h.vm.highlight.value)
        h.vm.highlightShown()
        assertNull(h.vm.highlight.value)
    }

    @Test
    fun `opening or resuming the screen emits no highlight`() = runTest {
        val h = harness()
        runCurrent()
        assertNull(h.vm.highlight.value)
        h.vm.onResume()
        runCurrent()
        assertNull(h.vm.highlight.value)
    }

    @Test
    fun `start also emits a highlight for the check-in it performs`() = runTest {
        val h = harness()
        runCurrent()
        h.vm.onStart()
        runCurrent()
        assertEquals(mapOf(1 to RepsColumnLayout.Change.UP), h.vm.highlight.value?.changes)
    }

    @Test
    fun `a sets change mid check-in bails without a highlight`() = runTest {
        val h = harness()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repo.checkInGate = gate
        h.vm.onCheckIn() // captures "before" with 8 sets
        runCurrent()
        // The set count changes while the check-in is still in flight.
        repo.state.update { list -> list.map { if (it.id == 1L) it.copy(timing = it.timing.copy(sets = 5)) else it } }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(h.vm.highlight.value)
    }

    /** The curls ladder (8 / 10 / 12 / 14 / 16 kg) in Weight mode, 10 reps per set; level 3 is 14 kg. */
    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600), repsPerSet = 10)
    private val yesterday = Instant.parse("2026-09-23T12:55:00Z")

    private fun weightRepo(
        total: Int = 3,
        holdCount: Int = 0,
        holds: List<WeightHold> = emptyList(),
        lastCheckIn: Instant? = yesterday,
        mode: ProgressMode = ProgressMode.WEIGHT,
    ) = FakeEntryRepository(
        listOf(
            testEntry(
                1, "Curls",
                progression = ProgressionConfig(mode = mode, weight = curls.copy(holds = holds)),
                counter = CounterState(total = total, bestStreak = 4, currentStreak = 4, lastCheckIn = lastCheckIn, holdCount = holdCount),
            ),
        ),
    )

    @Test
    fun `a Reps entry's state has no load and reads as Reps`() = runTest {
        val h = harness()
        runCurrent()
        assertNull(h.vm.uiState.value.load)
        assertEquals(ProgressMode.REPS, h.vm.uiState.value.mode)
    }

    @Test
    fun `a weight mode's state carries the mode, the unit, the load and the reps per set`() = runTest {
        val h = harness(repository = weightRepo())
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals(ProgressMode.WEIGHT, s.mode)
        assertEquals(WeightUnit.KG, s.unit)
        assertEquals(Prescription.Load(1400, 10), s.load)
        assertEquals(List(8) { 10 }, s.reps)
    }

    @Test
    fun `a weight-mode check-in flashes the card up, not the reps column`() = runTest {
        val h = harness(repository = weightRepo())
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        val highlight = h.vm.highlight.value
        assertEquals(RepsColumnLayout.Change.UP, highlight?.card)
        assertEquals(emptyMap<Int, RepsColumnLayout.Change>(), highlight?.changes)
        assertNull(highlight?.hold)
    }

    @Test
    fun `a weight-mode miss flashes the card down`() = runTest {
        val h = harness(repository = weightRepo())
        runCurrent()
        // ~80 h after the last check-in: penalty 2, held to one weight lighter (level 2, 12 kg).
        clock.instant = Instant.parse("2026-09-26T20:55:00Z")
        h.vm.onCheckIn()
        runCurrent()
        assertEquals(RepsColumnLayout.Change.DOWN, h.vm.highlight.value?.card)
    }

    @Test
    fun `a check-in that stays on a weight hold flashes the card neutral with the hold day`() = runTest {
        // A weight hold on 14 kg for 4 (level 3); day 1 was yesterday. The Reps holds can't see it.
        val h = harness(repository = weightRepo(total = 3, holdCount = 1, holds = listOf(WeightHold(1400, 10, 4))))
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        val highlight = h.vm.highlight.value
        assertEquals(RepsColumnLayout.Change.HOLD, highlight?.card)
        assertEquals(HoldStatus(at = 3, day = 2, of = 4), highlight?.hold)
        assertEquals(emptyMap<Int, RepsColumnLayout.Change>(), highlight?.changes)
    }

    @Test
    fun `a Reps-then-weight hold is found by its level from weight and reps, not weight alone`() = runTest {
        // Curls Reps-then-weight, span 5: level 16 is 14 kg x 9 reps (weightIndex 3 x 5 + (9 - 8)).
        // Level 15 is also 14 kg (x 8 reps), so matching on weight alone would land on the wrong level.
        val h = harness(
            repository = weightRepo(
                total = 16, holdCount = 1, mode = ProgressMode.REPS_THEN_WEIGHT,
                holds = listOf(WeightHold(1400, 9, 4)),
            ),
        )
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        val highlight = h.vm.highlight.value
        assertEquals(RepsColumnLayout.Change.HOLD, highlight?.card)
        assertEquals(HoldStatus(at = 16, day = 2, of = 4), highlight?.hold)
    }

    @Test
    fun `a weight-mode check-in at the top of the ladder with no change sets no highlight`() = runTest {
        val h = harness(repository = weightRepo(total = 4)) // already at the heaviest weight
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        assertNull(h.vm.highlight.value)
    }

    @Test
    fun `a weight-mode entry's first check-in shows no highlight`() = runTest {
        val h = harness(repository = weightRepo(total = 3, lastCheckIn = null)) // never checked in before
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        assertNull(h.vm.highlight.value)
    }

    @Test
    fun `a kg to lb race while a check-in is in flight sets no highlight`() = runTest {
        val wr = weightRepo()
        val h = harness(repository = wr)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        wr.checkInGate = gate
        h.vm.onCheckIn() // captures beforeLoad and beforeUnit (KG)
        runCurrent()
        // The unit switches to lb while the check-in is still in flight.
        wr.setWeightConfig(1, curls.copy(unit = WeightUnit.LB))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(h.vm.highlight.value)
    }

    @Test
    fun `a ladder edit while a check-in is in flight sets no highlight (M2)`() = runTest {
        val wr = weightRepo()
        val h = harness(repository = wr)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        wr.checkInGate = gate
        h.vm.onCheckIn() // captures beforeLoad and the curls ladder's rungs (800..1600)
        runCurrent()
        // The ladder's rungs change (same mode, same unit) while the check-in is still in flight.
        wr.setWeightConfig(1, curls.copy(list = curls.list + 1800))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(h.vm.highlight.value)
    }

    @Test
    fun `a ladder edit racing Start's check-in runs the new ladder's load, with the snapshot's own set count (M1)`() = runTest {
        val wr = weightRepo()
        val h = harness(repository = wr)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        wr.checkInGate = gate
        h.vm.onStart() // captures the pre-check-in entry: 8 sets, 10 reps per set, from the curls ladder
        runCurrent()
        // Both the ladder's reps-per-set and the entry's own sets change while Start's check-in is
        // still in flight.
        wr.setWeightConfig(1, curls.copy(repsPerSet = 12))
        wr.state.update { list -> list.map { if (it.id == 1L) it.copy(timing = it.timing.copy(sets = 5)) else it } }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        // The check-in moved level 3 (14 kg) to level 4 (16 kg); the run follows the new ladder's 12
        // reps per set (not the stale 10 captured before Start), but still the snapshot's frozen 8 sets
        // (not the changed 5) — TabataEngine's repsPerSet.size == timing.sets check would otherwise throw.
        assertEquals(SetWeight(1600, WeightUnit.KG), h.controller.weight)
        advanceTimeBy(31_000) // prepare 10 s + set 1's 20 s of work: set 1 is done
        runCurrent()
        assertEquals(8, h.controller.state.value?.sets)
        assertEquals(12, h.controller.state.value?.repsThisSet)
    }

    @Test
    fun `a kg to lb race during Start's check-in sets no highlight, but the run still follows the fresh progression (M5)`() = runTest {
        val wr = weightRepo()
        val h = harness(repository = wr)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        wr.checkInGate = gate
        h.vm.onStart() // captures beforeLoad, beforeMode (WEIGHT) and beforeUnit (KG)
        runCurrent()
        // The unit switches to lb while Start's check-in is still in flight.
        wr.setWeightConfig(1, curls.copy(unit = WeightUnit.LB))
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(h.vm.highlight.value)
        // The run itself still starts, following the fresh (lb) progression's load (spec rev 26 §4).
        assertEquals(WeightUnit.LB, h.controller.weight?.unit)
    }

    @Test
    fun `Start in a weight mode runs the load's reps with its weight, and the session counts sets times reps`() = runTest {
        val summaries = mutableListOf<RunSummary>()
        val h = harness(repository = weightRepo(), runLog = RunLog { summaries += it })
        runCurrent()
        h.vm.onStart()
        runCurrent()
        // The check-in moved 14 kg to 16 kg; the timer runs 16 kg × 10, not a split of level 4.
        assertEquals(SetWeight(1600, WeightUnit.KG), h.controller.weight)
        assertEquals(RepsColumnLayout.Change.UP, h.vm.highlight.value?.card)
        advanceTimeBy(31_000) // prepare 10 s + set 1's 20 s of work: set 1 is done
        runCurrent()
        assertEquals(10, h.controller.state.value?.repsThisSet)
        h.controller.stop()
        assertEquals(10, summaries.single().repsDone) // 1 completed set × 10 reps
    }

    @Test
    fun `after a switch the chart keeps only the new mode's points, and switching back restores the rep line`() = runTest {
        val h = harness()
        runCurrent()
        h.vm.onCheckIn() // 24 Sep: 65 → 66
        runCurrent()
        clock.instant = Instant.parse("2026-09-25T15:00:00Z")
        h.vm.onCheckIn() // 25 Sep: 67
        runCurrent()
        repo.switchMode(1, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG)
        clock.instant = Instant.parse("2026-09-26T15:00:00Z")
        h.vm.onCheckIn() // fresh start: performed at 20 kg × 8 (§10 note 13)
        runCurrent()
        clock.instant = Instant.parse("2026-09-27T15:00:00Z")
        h.vm.onCheckIn() // 20 kg × 9
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals(4, s.points.size)
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, s.mode)
        val view = HistoryLayout.rangeView(s.points, HistoryRange.ALL, s.now, s.zone)
        assertEquals(listOf(2000 to 8, 2000 to 9), HistoryLayout.shownPoints(view, s.type, s.mode).map { it.weight to it.reps })
        repo.switchMode(1, ProgressMode.REPS, WeightUnit.KG)
        runCurrent()
        val back = h.vm.uiState.value
        val repsView = HistoryLayout.rangeView(back.points, HistoryRange.ALL, back.now, back.zone)
        assertEquals(listOf(66, 67), HistoryLayout.shownPoints(repsView, back.type, back.mode).map { it.total })
    }
}
