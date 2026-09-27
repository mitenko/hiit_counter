package com.mitenko.hiitcounter.data.v1

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mitenko.hiitcounter.data.StoredCounter
import com.mitenko.hiitcounter.data.entryEntity
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V1ReadersTest {
    private fun prefs(block: (MutablePreferences) -> Unit): Preferences = mutablePreferencesOf().apply(block)

    @Test
    fun `empty v1 files give the default Workout entry with a null total`() {
        assertEquals(entryEntity("Workout", 0), v1Entry(emptyPreferences(), emptyPreferences()))
    }

    @Test
    fun `non-default v1 values carry across`() {
        val settings = prefs {
            it[V1Keys.PREPARE_SEC] = 5
            it[V1Keys.SETS] = 6
            it[V1Keys.WORK_SEC] = 30
            it[V1Keys.REST_SEC] = 15
            it[V1Keys.COOLDOWN_SEC] = 60
            it[V1Keys.STARTING_TOTAL] = 50
            it[V1Keys.FLOOR] = 40
            it[V1Keys.CAP] = 80
            it[V1Keys.HOLD_AT] = 70
            it[V1Keys.HOLD_FOR] = 3
            it[V1Keys.WINDOW_HOURS] = 30
            it[V1Keys.PENALTY_HOURS_PER_REP] = 12.5
            it[V1Keys.CUE_SOUND] = false
            it[V1Keys.CUE_VIBRATION] = true
        }
        val counter = prefs {
            it[V1Keys.TOTAL] = 65
            it[V1Keys.BEST_STREAK] = 24
            it[V1Keys.CURRENT_STREAK] = 4
            it[V1Keys.HOLD_COUNT] = 2
            it[V1Keys.LAST_CHECK_IN] = 1_790_000_000_123L
        }
        assertEquals(
            entryEntity(
                "Workout", 0,
                TimingConfig(5, 6, 30, 15, 60),
                ProgressionConfig(50, 40, 80, 70, 3, 30, 12.5),
                CueConfig(sound = false, vibration = true),
                StoredCounter(total = 65, bestStreak = 24, currentStreak = 4, holdCount = 2, lastCheckIn = 1_790_000_000_123L),
            ),
            v1Entry(settings, counter),
        )
    }

    @Test
    fun `absent and invalid v1 totals become null`() {
        assertNull(prefs { it[V1Keys.BEST_STREAK] = 3 }.readV1Total())
        assertNull(prefs { it[V1Keys.TOTAL] = 0 }.readV1Total())
        assertNull(prefs { it[V1Keys.TOTAL] = -5 }.readV1Total())
        assertEquals(1, prefs { it[V1Keys.TOTAL] = 1 }.readV1Total())
    }

    @Test
    fun `invalid v1 values fall back per key`() {
        val timing = prefs { it[V1Keys.SETS] = 99; it[V1Keys.WORK_SEC] = 30 }.readV1Timing()
        assertEquals(8, timing.sets)
        assertEquals(30, timing.workSec)
        val counter = prefs { it[V1Keys.CURRENT_STREAK] = -1; it[V1Keys.BEST_STREAK] = 7 }.readV1Counter()
        assertEquals(0, counter.currentStreak)
        assertEquals(7, counter.bestStreak)
    }
}
