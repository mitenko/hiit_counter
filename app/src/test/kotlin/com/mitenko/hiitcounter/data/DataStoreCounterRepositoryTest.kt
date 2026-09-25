package com.mitenko.hiitcounter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class DataStoreCounterRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val clock = FakeClock()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "counter.preferences_pb") })

    @Test
    fun `absent total follows the live starting total`() = runTest {
        val settings = FakeSettingsRepository()
        val repo = DataStoreCounterRepository(store(), settings)
        assertEquals(CounterState(total = 48), repo.state.first())
        settings.progressionFlow.value = ProgressionConfig(startingTotal = 55)
        assertEquals(55, repo.state.first().total)
    }

    @Test
    fun `overwrite and resetHoldCount clear the hold`() = runTest {
        val s = store()
        val repo = DataStoreCounterRepository(s, FakeSettingsRepository())
        s.edit { it[intPreferencesKey("hold_count")] = 3 }
        assertEquals(3, repo.state.first().holdCount)
        repo.resetHoldCount()
        assertEquals(0, repo.state.first().holdCount)
        s.edit { it[intPreferencesKey("hold_count")] = 2 }
        repo.overwrite(total = 64, bestStreak = 5, currentStreak = 3, lastCheckIn = null)
        assertEquals(CounterState(total = 64, bestStreak = 5, currentStreak = 3, lastCheckIn = null, holdCount = 0), repo.state.first())
    }

    @Test
    fun `last check-in round-trips as epoch millis`() = runTest {
        val repo = DataStoreCounterRepository(store(), FakeSettingsRepository())
        val t = Instant.ofEpochMilli(1_790_000_000_123)
        repo.overwrite(60, 3, 2, t)
        assertEquals(t, repo.state.first().lastCheckIn)
        repo.overwrite(60, 3, 2, null)
        assertNull(repo.state.first().lastCheckIn)
    }

    @Test
    fun `reset progress returns to the fresh-install state`() = runTest {
        val repo = DataStoreCounterRepository(store(), FakeSettingsRepository())
        repo.overwrite(65, 24, 4, Instant.ofEpochMilli(1_000))
        repo.resetProgress()
        assertEquals(CounterState(total = 48), repo.state.first())
    }

    @Test
    fun `reset progress uses the live starting total`() = runTest {
        val settings = FakeSettingsRepository(progression = ProgressionConfig(startingTotal = 55))
        val repo = DataStoreCounterRepository(store(), settings)
        repo.overwrite(65, 24, 4, Instant.ofEpochMilli(1_000))
        repo.resetProgress()
        assertEquals(CounterState(total = 55), repo.state.first())
    }

    @Test
    fun `invalid stored values fall back per key`() = runTest {
        val s = store()
        s.edit {
            it[intPreferencesKey("total")] = -5
            it[intPreferencesKey("current_streak")] = -1
            it[intPreferencesKey("best_streak")] = 7
        }
        val st = DataStoreCounterRepository(s, FakeSettingsRepository()).state.first()
        assertEquals(48, st.total)
        assertEquals(0, st.currentStreak)
        assertEquals(7, st.bestStreak)
    }

    @Test
    fun `check-in is persisted`() = runTest {
        val repo = DataStoreCounterRepository(store(), FakeSettingsRepository())
        val r = repo.checkIn(ProgressionConfig(), clock)
        assertEquals(Outcome.First, r.outcome)
        assertEquals(r.state, repo.state.first())
        assertEquals(clock.instant, repo.state.first().lastCheckIn)
    }

    @Test
    fun `concurrent check-ins record exactly one`() = runTest {
        val repo = DataStoreCounterRepository(store(), FakeSettingsRepository())
        val results = List(5) { async { repo.checkIn(ProgressionConfig(), clock) } }.awaitAll()
        assertEquals(1, results.count { it.outcome != Outcome.AlreadyToday })
    }

    @Test
    fun `first check-in at the hold value persists the hold count`() = runTest {
        val settings = FakeSettingsRepository(progression = ProgressionConfig(startingTotal = 64))
        val repo = DataStoreCounterRepository(store(), settings)
        repo.checkIn(settings.progressionFlow.value, clock)
        assertEquals(64 to 1, repo.state.first().let { it.total to it.holdCount })
    }
}
