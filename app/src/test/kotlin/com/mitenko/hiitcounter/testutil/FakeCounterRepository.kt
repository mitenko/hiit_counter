package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.RepProgression
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

class FakeCounterRepository(initial: CounterState = CounterState(total = 48)) : CounterRepository {
    val stateFlow = MutableStateFlow(initial)
    var checkInCalls = 0
    var overwriteCalls = 0
    var resetHoldCountCalls = 0
    var resetProgressCalls = 0
    var checkInError: Throwable? = null

    override val state: Flow<CounterState> = stateFlow

    override suspend fun checkIn(config: ProgressionConfig, clock: Clock): CheckInResult {
        checkInCalls++
        checkInError?.let { throw it }
        val r = RepProgression.checkIn(stateFlow.value, config, clock.now(), clock.zone())
        stateFlow.value = r.state
        return r
    }

    override suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        overwriteCalls++
        stateFlow.value = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount = 0)
    }

    override suspend fun resetHoldCount() {
        resetHoldCountCalls++
        stateFlow.update { it.copy(holdCount = 0) }
    }

    override suspend fun resetProgress() {
        resetProgressCalls++
        stateFlow.value = CounterState(total = 48)
    }
}
