package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.RepProgression
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * In-memory [EntryRepository] with the same contract as RoomEntryRepository: both flows and every
 * suspend call wait for [readiness], missing ids throw EntryNotFound, invalid names throw
 * IllegalArgumentException, positions stay contiguous. Settings validation is left to the
 * ViewModels under test.
 */
class FakeEntryRepository(initial: List<Entry> = emptyList(), ready: Boolean = true) : EntryRepository {
    val readiness = CompletableDeferred<Unit>().apply { if (ready) complete(Unit) }
    val state = MutableStateFlow(initial.sortedWith(compareBy<Entry>({ it.position }, { it.id })))
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    var checkInCalls = 0
    var checkInError: Throwable? = null
    val moves = mutableListOf<Pair<Long, Int>>()
    var deleteCalls = 0

    override val entries: Flow<List<Entry>> = flow {
        readiness.await()
        emitAll(state)
    }

    override fun entry(id: Long): Flow<Entry?> = flow {
        readiness.await()
        emitAll(state.map { list -> list.firstOrNull { it.id == id } })
    }

    override suspend fun create(name: String): Long {
        val valid = validName(name)
        readiness.await()
        val id = nextId++
        val p = ProgressionConfig()
        state.update { it + Entry(id, valid, it.size, TimingConfig(), p, CueConfig(), CounterState(total = p.startingTotal)) }
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = validName(name)
        edit(id) { it.copy(name = valid) }
    }

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

    override suspend fun setTiming(id: Long, timing: TimingConfig) = edit(id) { it.copy(timing = timing) }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) =
        edit(id) { it.copy(progression = progression, counter = it.counter.copy(holdCount = 0)) }

    override suspend fun setCues(id: Long, cues: CueConfig) = edit(id) { it.copy(cues = cues) }

    override suspend fun checkIn(id: Long, clock: Clock): CheckInResult {
        readiness.await()
        checkInCalls++
        checkInError?.let { throw it }
        val e = find(id)
        val result = RepProgression.checkIn(e.counter, e.progression, clock.now(), clock.zone())
        if (result.outcome != Outcome.AlreadyToday) edit(id) { it.copy(counter = result.state) }
        return result
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) =
        edit(id) { it.copy(counter = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount = 0)) }

    override suspend fun resetProgress(id: Long) = edit(id) { it.copy(counter = CounterState(total = it.progression.startingTotal)) }

    fun find(id: Long): Entry = state.value.firstOrNull { it.id == id } ?: throw EntryNotFound(id)

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
