package com.mitenko.hiitcounter.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.RepProgression
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.io.IOException
import java.time.Instant

interface CounterRepository {
    val state: Flow<CounterState>
    suspend fun checkIn(config: ProgressionConfig, clock: Clock): CheckInResult
    suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)
    suspend fun resetHoldCount()
    suspend fun resetProgress()
}

private const val COUNTER_TAG = "CounterRepository"

/** Absent `total` reads as the live starting total (spec §5). */
internal fun Preferences.readCounter(startingTotal: Int) = CounterState(
    total = valid(Keys.TOTAL, startingTotal) { it >= 1 },
    bestStreak = valid(Keys.BEST_STREAK, 0) { it >= 0 },
    currentStreak = valid(Keys.CURRENT_STREAK, 0) { it >= 0 },
    lastCheckIn = this[Keys.LAST_CHECK_IN]?.let(Instant::ofEpochMilli),
    holdCount = valid(Keys.HOLD_COUNT, 0) { it >= 0 },
)

internal fun MutablePreferences.writeCounter(s: CounterState) {
    this[Keys.TOTAL] = s.total
    this[Keys.BEST_STREAK] = s.bestStreak
    this[Keys.CURRENT_STREAK] = s.currentStreak
    this[Keys.HOLD_COUNT] = s.holdCount
    val last = s.lastCheckIn
    if (last == null) remove(Keys.LAST_CHECK_IN) else this[Keys.LAST_CHECK_IN] = last.toEpochMilli()
}

class DataStoreCounterRepository(
    private val store: DataStore<Preferences>,
    settings: SettingsRepository,
) : CounterRepository {
    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) {
            Log.e(COUNTER_TAG, "Counter read failed", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    override val state: Flow<CounterState> =
        combine(prefs, settings.progression) { p, config -> p.readCounter(config.startingTotal) }

    /** Atomic: runs inside one DataStore transaction, so concurrent calls check in once. */
    override suspend fun checkIn(config: ProgressionConfig, clock: Clock): CheckInResult {
        lateinit var result: CheckInResult
        store.edit { p ->
            val before = p.readCounter(config.startingTotal)
            val now = clock.now()
            before.lastCheckIn?.let { last ->
                if (now.isBefore(last)) Log.w(COUNTER_TAG, "Clock moved backwards: now=$now last=$last")
            }
            result = RepProgression.checkIn(before, config, now, clock.zone())
            if (result.outcome != Outcome.AlreadyToday) p.writeCounter(result.state)
        }
        return result
    }

    /** Overwrites everything from Settings → Current State and resets holdCount (spec §6). */
    override suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        store.edit { it.writeCounter(CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount = 0)) }
    }

    override suspend fun resetHoldCount() {
        store.edit { it[Keys.HOLD_COUNT] = 0 }
    }

    override suspend fun resetProgress() {
        store.edit { it.clear() }
    }
}
