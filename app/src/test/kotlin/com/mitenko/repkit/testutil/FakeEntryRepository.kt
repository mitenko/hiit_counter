package com.mitenko.repkit.testutil

import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.domain.CheckInResult
import com.mitenko.repkit.domain.Clock
import com.mitenko.repkit.domain.EntryNames
import com.mitenko.repkit.domain.NameCheck
import com.mitenko.repkit.domain.Outcome
import com.mitenko.repkit.domain.RepProgression
import com.mitenko.repkit.domain.counterHoldReset
import com.mitenko.repkit.domain.holdResetNeeded
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
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
 * point in [points] (R6 §3.2). Settings validation is left to the ViewModels under test. The write
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
    var deleteCalls = 0

    /** Every resetProgress call as (id, clearHistory), including failed ones. */
    val resets = mutableListOf<Pair<Long, Boolean>>()

    /** setTiming / setProgression / overwriteCounter / setType calls so far, including failed ones. */
    var timingWrites = 0
    var progressionWrites = 0
    var counterWrites = 0
    var typeWrites = 0

    /** Thrown once by the next setTiming, setProgression, overwriteCounter or setType (a repository-side rejection). */
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
                name = EntryNames.duplicateName(source.name),
                position = it.size,
                counter = CounterState(total = source.progression.startingTotal),
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

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        progressionWrites++
        failIfAsked()
        edit(id) {
            val holdCount = if (holdResetNeeded(it.progression, progression)) 0 else it.counter.holdCount
            it.copy(progression = progression, counter = it.counter.copy(holdCount = holdCount))
        }
    }

    override suspend fun setCues(id: Long, cues: CueConfig) = edit(id) { it.copy(cues = cues) }

    override suspend fun setType(id: Long, type: EntryType) {
        typeWrites++
        failIfAsked()
        edit(id) { it.copy(type = type) }
    }

    override suspend fun checkIn(id: Long, clock: Clock): CheckInResult {
        readiness.await()
        checkInCalls++
        checkInGate?.await()
        checkInError?.let { throw it }
        val e = find(id)
        val countsReps = e.type == EntryType.WORKOUT
        val result = RepProgression.checkIn(e.counter, e.progression, clock.now(), clock.zone(), countsReps)
        if (result.outcome != Outcome.AlreadyToday) {
            edit(id) { it.copy(counter = result.state) }
            // Spec R6 §3.2: one point per recorded check-in; a Timer only point has no total.
            val point = CheckInPoint(clock.now(), if (countsReps) result.state.total else null)
            points.update { all -> all + (id to (all[id].orEmpty() + point)) }
        }
        return result
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        counterWrites++
        failIfAsked()
        edit(id) {
            val holdCount = if (counterHoldReset(it.counter.total, total)) 0 else it.counter.holdCount
            it.copy(counter = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount))
        }
    }

    /** Room stores a NULL total, which resolves to startingTotal; the fake stores startingTotal directly. */
    override suspend fun resetProgress(id: Long, clearHistory: Boolean) {
        resets += id to clearHistory
        edit(id) { it.copy(counter = CounterState(total = it.progression.startingTotal)) }
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
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }
}
