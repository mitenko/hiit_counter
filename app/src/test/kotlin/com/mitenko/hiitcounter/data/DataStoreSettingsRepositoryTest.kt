package com.mitenko.hiitcounter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "settings.preferences_pb") })

    @Test
    fun `empty store yields defaults`() = runTest {
        val repo = DataStoreSettingsRepository(store())
        assertEquals(TimingConfig(), repo.timing.first())
        assertEquals(ProgressionConfig(), repo.progression.first())
        assertEquals(CueConfig(), repo.cues.first())
        assertFalse(repo.notificationPermissionAsked.first())
    }

    @Test
    fun `values round-trip`() = runTest {
        val repo = DataStoreSettingsRepository(store())
        val timing = TimingConfig(prepareSec = 5, sets = 6, workSec = 30, restSec = 15, cooldownSec = 60)
        val progression = ProgressionConfig(startingTotal = 50, floor = 40, cap = 80, holdAt = 70, holdFor = 3, windowHours = 30, penaltyHoursPerRep = 12.5)
        repo.setTiming(timing)
        repo.setProgression(progression)
        repo.setCues(CueConfig(sound = false, vibration = true))
        repo.markNotificationPermissionAsked()
        assertEquals(timing, repo.timing.first())
        assertEquals(progression, repo.progression.first())
        assertEquals(CueConfig(sound = false, vibration = true), repo.cues.first())
        assertTrue(repo.notificationPermissionAsked.first())
    }

    @Test
    fun `invalid single timing value falls back per key`() = runTest {
        val s = store()
        s.edit { it[intPreferencesKey("sets")] = 99; it[intPreferencesKey("work_sec")] = 30 }
        val t = DataStoreSettingsRepository(s).timing.first()
        assertEquals(8, t.sets)
        assertEquals(30, t.workSec)
    }

    @Test
    fun `inconsistent progression falls back to defaults`() = runTest {
        val s = store()
        s.edit { it[intPreferencesKey("floor")] = 80; it[intPreferencesKey("cap")] = 60 }
        assertEquals(ProgressionConfig(), DataStoreSettingsRepository(s).progression.first())
    }

    @Test
    fun `non-finite penalty falls back`() = runTest {
        val s = store()
        s.edit { it[doublePreferencesKey("penalty_hours_per_rep")] = Double.NaN }
        assertEquals(19.5, DataStoreSettingsRepository(s).progression.first().penaltyHoursPerRep, 0.0)
    }
}
