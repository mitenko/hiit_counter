package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SettingsValidatorTest {
    private fun ValidationResult.errorFields() = errors.keys

    @Test
    fun `default timing is valid`() {
        assertTrue(SettingsValidator.timing(TimingConfig()).isValid)
    }

    @Test
    fun `timing field ranges`() {
        assertEquals(setOf(Field.SETS), SettingsValidator.timing(TimingConfig(sets = 0)).errorFields())
        assertEquals(setOf(Field.SETS), SettingsValidator.timing(TimingConfig(sets = 21)).errorFields())
        assertEquals(setOf(Field.WORK), SettingsValidator.timing(TimingConfig(workSec = 0)).errorFields())
        assertEquals(setOf(Field.REST), SettingsValidator.timing(TimingConfig(restSec = -1)).errorFields())
        assertEquals(setOf(Field.PREPARE), SettingsValidator.timing(TimingConfig(prepareSec = 3600)).errorFields())
        assertTrue(SettingsValidator.timing(TimingConfig(prepareSec = 0, restSec = 0, cooldownSec = 0)).isValid)
    }

    @Test
    fun `derived total above two hours is rejected`() {
        val nearly = TimingConfig(prepareSec = 0, sets = 20, workSec = 300, restSec = 60, cooldownSec = 0) // 7140 s
        assertTrue(SettingsValidator.timing(nearly).isValid)
        assertEquals(setOf(Field.TOTAL_DURATION), SettingsValidator.timing(nearly.copy(cooldownSec = 61)).errorFields())
    }

    @Test
    fun `progression ordering rules`() {
        assertTrue(SettingsValidator.progression(ProgressionConfig()).isValid)
        assertEquals(setOf(Field.FLOOR), SettingsValidator.progression(ProgressionConfig(floor = 0)).errorFields())
        assertEquals(setOf(Field.STARTING_TOTAL), SettingsValidator.progression(ProgressionConfig(startingTotal = 40)).errorFields())
        assertEquals(setOf(Field.CAP), SettingsValidator.progression(ProgressionConfig(cap = 47)).errorFields())
        assertEquals(setOf(Field.HOLD_FOR), SettingsValidator.progression(ProgressionConfig(holdFor = -1)).errorFields())
        assertEquals(setOf(Field.WINDOW_HOURS), SettingsValidator.progression(ProgressionConfig(windowHours = 0)).errorFields())
        assertEquals(setOf(Field.PENALTY_RATE), SettingsValidator.progression(ProgressionConfig(penaltyHoursPerRep = 0.0)).errorFields())
        assertEquals(setOf(Field.PENALTY_RATE), SettingsValidator.progression(ProgressionConfig(penaltyHoursPerRep = Double.NaN)).errorFields())
    }

    @Test
    fun `hold outside floor to cap is allowed with a hint`() {
        val r = SettingsValidator.progression(ProgressionConfig(holdAt = 80))
        assertTrue(r.isValid)
        assertTrue(Field.HOLD_AT in r.hints)
        assertTrue(Field.HOLD_AT in SettingsValidator.progression(ProgressionConfig(holdFor = 0)).hints)
        assertFalse(Field.HOLD_AT in SettingsValidator.progression(ProgressionConfig()).hints)
    }

    @Test
    fun `current state rules`() {
        val now = Instant.parse("2026-09-24T12:00:00Z")
        val cfg = ProgressionConfig()
        assertTrue(SettingsValidator.currentState(65, 24, 4, now.minusSeconds(60), now, cfg).isValid)
        assertEquals(setOf(Field.TOTAL), SettingsValidator.currentState(0, 0, 0, null, now, cfg).errorFields())
        assertEquals(setOf(Field.BEST_STREAK), SettingsValidator.currentState(65, 3, 4, null, now, cfg).errorFields())
        assertEquals(setOf(Field.BEST_STREAK, Field.CURRENT_STREAK), SettingsValidator.currentState(65, -1, -2, null, now, cfg).errorFields())
        assertEquals(setOf(Field.LAST_CHECK_IN), SettingsValidator.currentState(65, 24, 4, now.plusSeconds(60), now, cfg).errorFields())
        val outside = SettingsValidator.currentState(80, 24, 4, null, now, cfg)
        assertTrue(outside.isValid)
        assertTrue(Field.TOTAL in outside.hints)
    }
}
