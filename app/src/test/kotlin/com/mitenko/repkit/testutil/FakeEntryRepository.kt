package com.mitenko.repkit.testutil

import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.domain.CheckInResult
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.EntryNames
import com.mitenko.repkit.domain.InvalidEntryName
import com.mitenko.repkit.domain.Move
import com.mitenko.repkit.domain.NameCheck
import com.mitenko.repkit.domain.Outcome
import com.mitenko.repkit.domain.ProgressionScale
import com.mitenko.repkit.domain.RangeChange
import com.mitenko.repkit.domain.RepProgression
import com.mitenko.repkit.domain.WeightConversion
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.counterHoldReset
import com.mitenko.repkit.domain.currentLoadMove
import com.mitenko.repkit.domain.holdResetNeeded
import com.mitenko.repkit.domain.ladderOf
import com.mitenko.repkit.domain.loadAt
import com.mitenko.repkit.domain.progressionHoldReset
import com.mitenko.repkit.domain.rangeChange
import com.mitenko.repkit.domain.remapWeights
import com.mitenko.repkit.domain.scale
import com.mitenko.repkit.domain.startLevel
import com.mitenko.repkit.domain.totalMove
import com.mitenko.repkit.domain.weightHoldResetNeeded
import com.mitenko.repkit.domain.widenedFor
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * In-memory [EntryRepository] with the same contract as RoomEntryRepository: every flow and every
 * suspend call waits for [readiness], missing ids throw EntryNotFound, invalid names throw
 * IllegalArgumentException, positions stay contiguous, the hold count follows the R3 §6.3 rules,
 * a Timer only entry's check-in keeps its total (R4 §3.1), and a recorded check-in logs one
 * point in [points] (R6 §3.2), a Counter total outside floor..cap widens it (rev 27), and a Progression save moves a Counter total into the new floor..cap (rev 28). Settings validation is left to the ViewModels under test. The write
 * counters, [writeError] and [checkInGate] let the tests count, fail and hold individual calls.
 */
class FakeEntryRepository(initial: List<Entry> = emptyList(), ready: Boolean = true) : EntryRepository {
    val readiness = CompletableDeferred<Unit>().apply { if (ready) complete(Unit) }
    val state = MutableStateFlow(initial.sortedWith(compareBy<Entry>({ it.position }, { it.id })))
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    /** Each entry's history (spec R6 §3.2). Tests may seed it directly; reads sort each list by `at`. */
    val points = MutableStateFlow<Map<Long, List<CheckInPoint>>>(emptyMap())

    var checkInCalls = 0
    var checkInError: Throwable? = null

    /** When set, checkIn suspends on it after counting the call, so a test can hold a check-in in flight. */
    var checkInGate: CompletableDeferred<Unit>? = null
    val moves = mutableListOf<Pair<Long, Int>>()

    /** When set, overwriteCounter suspends on it after counting the call, so a test can hold a counter save in flight. */
    var counterGate: CompletableDeferred<Unit>? = null

    /** When set, setProgression suspends on it after counting the call, so a test can hold a progression save in flight. */
    var progressionGate: CompletableDeferred<Unit>? = null

    /** When set, setWeightConfig suspends on it after counting the call, so a test can hold a weight save in flight. */
    var weightGate: CompletableDeferred<Unit>? = null

    /** When set, create suspends on it after validating the name, so a test can hold a create in flight. */
    var createGate: CompletableDeferred<Unit>? = null
    var deleteCalls = 0

    /** Every resetProgress call as (id, clearHistory), including failed ones. */
    val resets = mutableListOf<Pair<Long, Boolean>>()

    /** setTiming / setProgression / overwriteCounter / setType calls so far, including failed ones. */
    var timingWrites = 0
    var progressionWrites = 0
    var counterWrites = 0
    var typeWrites = 0
    var weightWrites = 0

    /** Every switchMode call as (id, mode), including failed ones. */
    val modeSwitches = mutableListOf<Pair<Long, ProgressMode>>()

    /** Thrown once by the next setTiming, setProgression, setWeightConfig, switchMode, overwriteCounter or setType (a repository-side rejection). */
    var writeError: Throwable? = null

    override val entries: Flow<List<Entry>> = flow {
        readiness.await()
        emitAll(state)
    }

    override fun entry(id: Long): Flow<Entry?> = flow {
        readiness.await()
        emitAll(state.map { list -> list.firstOrNull { it.id == id } })
    }

    override suspend fun create(name: String, type: EntryType): Long {
        val valid = validName(name)
        readiness.await()
        createGate?.await()
        val id = nextId++
        val p = ProgressionConfig()
        state.update {
            it + Entry(id, valid, it.size, TimingConfig(), p, CueConfig(), CounterState(total = p.startingTotal), type)
        }
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = validName(name)
        edit(id) { it.copy(name = valid) }
    }

    /** Copies the config, the type and the cues (voice included) with a fresh counter and no history, as Room does. */
    override suspend fun duplicate(id: Long): Long {
        readiness.await()
        val source = find(id)
        val newId = nextId++
        state.update {
            it + source.copy(
                id = newId,
                name = EntryNames.duplicateName(source.name, " copy"),
                position = it.size,
                counter = CounterState(total = source.progression.startLevel()),
            )
        }
        return newId
    }

    override suspend fun delete(id: Long) {
        readiness.await()
        deleteCalls++
        find(id)
        state.update { list -> list.filter { it.id != id }.mapIndexed { i, e -> e.copy(position = i) } }
        points.update { it - id }
    }

    override suspend fun moveBy(id: Long, delta: Int) {
        readiness.await()
        moves += id to delta
        val list = state.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) throw EntryNotFound(id)
        val to = (from + delta).coerceIn(0, list.size - 1)
        list.add(to, list.removeAt(from))
        state.value = list.mapIndexed { i, e -> e.copy(position = i) }
    }

    override suspend fun setTiming(id: Long, timing: TimingConfig) {
        timingWrites++
        failIfAsked()
        edit(id) { it.copy(timing = timing) }
    }

    /** Moves a Counter entry's total into the new floor..cap as Room does (spec revision 28 rule 4). */
    override suspend fun setProgression(id: Long, progression: ProgressionConfig): Move? {
        progressionWrites++
        progressionGate?.await()
        failIfAsked()
        var move: Move? = null
        edit(id) {
            // Like Room, setProgression never writes the mode or the weight group (plan Spec note 4).
            val merged = progression.copy(mode = it.progression.mode, weight = it.progression.weight)
            // Like Room, only a Reps-mode Counter total moves into the new floor..cap (a weight level never does).
            move = if (it.type == EntryType.WORKOUT && !merged.mode.usesWeights) merged.totalMove(it.counter.total) else null
            val reset = progressionHoldReset(it.progression, merged) || move != null
            val holdCount = if (reset) 0 else it.counter.holdCount
            it.copy(progression = merged, counter = it.counter.copy(total = move?.to ?: it.counter.total, holdCount = holdCount))
        }
        return move
    }

    override suspend fun setCues(id: Long, cues: CueConfig) = edit(id) { it.copy(cues = cues) }

    override suspend fun setType(id: Long, type: EntryType) {
        typeWrites++
        failIfAsked()
        edit(id) { it.copy(type = type) }
    }

    /** Room's remap without its validation (settings validation is left to the ViewModels under test). */
    override suspend fun setWeightConfig(id: Long, weight: WeightConfig): WeightMove.CurrentMoved? {
        weightWrites++
        weightGate?.await()
        failIfAsked()
        var moved: WeightMove.CurrentMoved? = null
        edit(id) {
            val stored = it.progression
            val mode = stored.mode
            val draft = weight.copy(list = weight.list.sorted(), unit = weight.unit ?: stored.weight.unit)
            val old = draft.unit?.let { u -> WeightConversion.convert(stored.weight, u) } ?: stored.weight
            val remapMode = if (mode.usesWeights) mode else ProgressMode.REPS_THEN_WEIGHT
            val storedLevel = if (mode.usesWeights) it.counter.total else null
            // Bug fix (batch 1 review, 2026-10-10): storedLevel indexes the stored, unconverted ladder,
            // but `old` above is already converted. See EntryRepository.setWeightConfig for the reason.
            val oldLevel = storedLevel?.let { lvl ->
                val current = ladderOf(mode, stored.weight).prescription(lvl)
                val fromUnit = stored.weight.unit
                val toUnit = draft.unit
                val convertedWeight = if (fromUnit != null && toUnit != null) {
                    WeightConversion.convert(current.weight, fromUnit, toUnit)
                } else {
                    current.weight
                }
                ladderOf(mode, old).levelOf(convertedWeight, current.reps)
            }
            val remap = remapWeights(remapMode, old, draft, oldLevel)
            val newLevel = remap.level
            if (mode.usesWeights && oldLevel != null && newLevel != null) moved = currentLoadMove(mode, old, remap.config, oldLevel, newLevel)
            val holdCount = if (mode.usesWeights && weightHoldResetNeeded(mode, old, remap)) 0 else it.counter.holdCount
            it.copy(
                progression = stored.copy(weight = remap.config),
                counter = it.counter.copy(total = newLevel ?: it.counter.total, holdCount = holdCount),
            )
        }
        return moved
    }

    /** Like Room: Start fresh, including the fresh-start flag (plan Spec note 13). */
    override suspend fun switchMode(id: Long, mode: ProgressMode, defaultUnit: WeightUnit) {
        modeSwitches += id to mode
        failIfAsked()
        edit(id) {
            if (it.progression.mode == mode) return@edit it
            val unit = it.progression.weight.unit ?: defaultUnit.takeIf { mode.usesWeights }
            val progression = it.progression.copy(mode = mode, weight = it.progression.weight.copy(unit = unit))
            it.copy(
                progression = progression,
                counter = it.counter.copy(total = progression.startLevel(), holdCount = 0, freshStart = true),
            )
        }
    }

    override suspend fun checkIn(id: Long, clock: Clock): CheckInResult {
        readiness.await()
        checkInCalls++
        checkInGate?.await()
        checkInError?.let { throw it }
        val e = find(id)
        val countsReps = e.type == EntryType.WORKOUT
        val result = RepProgression.checkInByMode(e.counter, e.progression, clock.now(), clock.zone(), countsReps)
        if (result.outcome != Outcome.AlreadyToday) {
            edit(id) { it.copy(counter = result.state) }
            // Spec R6 §3.2: one point per recorded check-in; a Timer only point has no total. Rev 26 §9.3: weight modes add the load.
            val load = if (countsReps) e.progression.loadAt(result.state.total) else null
            val point = CheckInPoint(
                clock.now(), if (countsReps) result.state.total else null, load?.weight, load?.reps,
                load?.let { e.progression.weight.unit },
            )
            points.update { all -> all + (id to (all[id].orEmpty() + point)) }
        }
        return result
    }

    /** Widens a Counter entry's floor..cap to include [total] as Room does (spec revision 27). */
    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?): RangeChange? {
        counterWrites++
        counterGate?.await()
        failIfAsked()
        var change: RangeChange? = null
        edit(id) {
            // Like Room: a weight-mode level must lie in 0..top, and floor..cap (rep totals) never widen for it.
            val scale = it.progression.scale()
            if (scale is ProgressionScale.Ladder) require(total in scale.minLevel..scale.maxLevel) { "Invalid level $total" }
            val reps = scale !is ProgressionScale.Ladder
            val widened = if (it.type == EntryType.WORKOUT && reps) it.progression.widenedFor(total) else it.progression
            change = rangeChange(it.progression, widened)
            val reset = holdResetNeeded(it.progression, widened) || counterHoldReset(it.counter.total, total)
            val holdCount = if (reset) 0 else it.counter.holdCount
            // §10 note 13, amended by notes 40 and 49: a level change clears fresh start; keeping the level
            // keeps it. Room additionally keeps a NULL total NULL here (note 49) so a later
            // setProgression/setWeightConfig follows a start that moves afterwards instead of remapping a
            // frozen value; the fake has no separate NULL state for `total` (see the class doc above — it
            // always stores the resolved level directly), so when the level doesn't move there is no
            // distinct "untouched" value to preserve: `total` already equals `it.counter.total` here.
            val fresh = it.counter.freshStart && total == it.counter.total
            it.copy(progression = widened, counter = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount, fresh))
        }
        return change
    }

    /** Room stores a NULL total, which resolves to the start level; the fake stores the start level directly. */
    override suspend fun resetProgress(id: Long, clearHistory: Boolean) {
        resets += id to clearHistory
        edit(id) { it.copy(counter = CounterState(total = it.progression.startLevel())) }
        if (clearHistory) points.update { it - id }
    }

    override fun history(id: Long, since: Instant?): Flow<List<CheckInPoint>> = flow {
        readiness.await()
        emitAll(points.map { all -> all[id].orEmpty().filter { since == null || !it.at.isBefore(since) }.sortedBy { it.at } })
    }

    override fun recentCheckIns(since: Instant): Flow<Map<Long, List<CheckInPoint>>> = flow {
        readiness.await()
        emitAll(
            points.map { all ->
                all.mapValues { (_, list) -> list.filter { !it.at.isBefore(since) }.sortedBy { it.at } }
                    .filterValues { it.isNotEmpty() }
            },
        )
    }

    fun find(id: Long): Entry = state.value.firstOrNull { it.id == id } ?: throw EntryNotFound(id)

    private fun failIfAsked() {
        writeError?.let { error ->
            writeError = null
            throw error
        }
    }

    private suspend fun edit(id: Long, transform: (Entry) -> Entry) {
        readiness.await()
        find(id)
        state.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun validName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw InvalidEntryName(check)
    }
}
