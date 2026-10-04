package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.TimingConfig
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
        assertEquals(setOf(Field.WINDOW_HOURS), SettingsValidator.progression(ProgressionConfig(windowHours = 0)).errorFields())
        assertEquals(setOf(Field.PENALTY_RATE), SettingsValidator.progression(ProgressionConfig(penaltyHoursPerRep = 0.0)).errorFields())
        assertEquals(setOf(Field.PENALTY_RATE), SettingsValidator.progression(ProgressionConfig(penaltyHoursPerRep = Double.NaN)).errorFields())
    }

    private fun holds(vararg h: Hold, hold: Boolean = true) = ProgressionConfig(holds = h.toList(), hold = hold)

    @Test
    fun `hold outside floor to cap is allowed with a hint`() {
        val r = SettingsValidator.progression(holds(Hold(80, 4)))
        assertTrue(r.isValid)
        assertEquals(mapOf(0 to FieldMessage.HoldDisabled), r.holdHints)
        assertEquals(mapOf(0 to FieldMessage.HoldDisabled), SettingsValidator.progression(holds(Hold(64, 0))).holdHints)
        assertTrue(SettingsValidator.progression(ProgressionConfig()).holdHints.isEmpty())
    }

    @Test
    fun `hold errors and hints are per hold`() {
        val r = SettingsValidator.progression(holds(Hold(56, 3), Hold(0, -1), Hold(40, 2), Hold(64, -1)))
        assertFalse(r.isValid)
        assertTrue(r.errors.isEmpty())
        assertEquals(
            mapOf(
                1 to mapOf(HoldField.AT to FieldMessage.AtLeastOne, HoldField.FOR to FieldMessage.ZeroOrMore),
                3 to mapOf(HoldField.FOR to FieldMessage.ZeroOrMore),
            ),
            r.holdErrors,
        )
        assertEquals(mapOf(2 to FieldMessage.HoldDisabled), r.holdHints)
    }

    @Test
    fun `a duplicate hold at is an error on the later duplicate`() {
        val r = SettingsValidator.progression(holds(Hold(64, 4), Hold(56, 3), Hold(64, 2)))
        assertFalse(r.isValid)
        assertEquals(mapOf(2 to mapOf(HoldField.AT to FieldMessage.DuplicateHold(64))), r.holdErrors)
        assertTrue(SettingsValidator.progression(holds(Hold(64, 4), Hold(64, 2), hold = false)).isValid)
    }

    @Test
    fun `each hold's hard ranges are checked whatever the switch says`() {
        val expected = mapOf(0 to mapOf(HoldField.AT to FieldMessage.AtLeastOne, HoldField.FOR to FieldMessage.ZeroOrMore))
        val off = SettingsValidator.progression(holds(Hold(0, -1), Hold(64, 4), hold = false))
        assertFalse(off.isValid)
        assertEquals(expected, off.holdErrors)
        assertEquals(expected, SettingsValidator.progression(holds(Hold(0, -1), Hold(64, 4))).holdErrors)
    }

    @Test
    fun `at most eight holds`() {
        val eight = (0 until 8).map { Hold(50 + it, 1) }.toTypedArray()
        assertTrue(SettingsValidator.progression(holds(*eight)).isValid)
        val nine = holds(*eight, Hold(60, 1))
        assertEquals(setOf(Field.HOLDS), SettingsValidator.progression(nine).errorFields())
        assertTrue(SettingsValidator.progression(nine.copy(hold = false)).isValid)
    }

    @Test
    fun `an empty hold list is valid`() {
        val r = SettingsValidator.progression(holds())
        assertTrue(r.isValid)
        assertTrue(r.holdHints.isEmpty())
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

    @Test
    fun `with the hold switched off the duplicate, count and hint checks are skipped`() {
        val off = SettingsValidator.progression(holds(Hold(64, 4), Hold(64, 2), Hold(80, 0), *Array(6) { Hold(50 + it, 1) }, hold = false))
        assertTrue(off.isValid)
        assertTrue(off.hints.isEmpty())
        assertTrue(off.holdHints.isEmpty())
        assertTrue(SettingsValidator.progression(holds(Hold(64, 0), hold = false)).holdHints.isEmpty())
        // Switched on, the checks and the hint behave as before.
        assertEquals(
            mapOf(0 to mapOf(HoldField.AT to FieldMessage.AtLeastOne, HoldField.FOR to FieldMessage.ZeroOrMore)),
            SettingsValidator.progression(holds(Hold(0, -1))).holdErrors,
        )
        assertEquals(mapOf(0 to FieldMessage.HoldDisabled), SettingsValidator.progression(holds(Hold(64, 0))).holdHints)
    }

    @Test
    fun `every message is typed, with its numbers as values`() {
        assertEquals(mapOf(Field.SETS to FieldMessage.SetsRange(20)), SettingsValidator.timing(TimingConfig(sets = 21)).errors)
        assertEquals(FieldMessage.WorkRange, SettingsValidator.timing(TimingConfig(workSec = 0)).errors[Field.WORK])
        assertEquals(FieldMessage.PhaseRange, SettingsValidator.timing(TimingConfig(restSec = -1)).errors[Field.REST])
        val tooLong = TimingConfig(prepareSec = 0, sets = 20, workSec = 300, restSec = 60, cooldownSec = 61)
        assertEquals(FieldMessage.WorkoutTooLong, SettingsValidator.timing(tooLong).errors[Field.TOTAL_DURATION])

        assertEquals(FieldMessage.AtLeastOne, SettingsValidator.progression(ProgressionConfig(floor = 0)).errors[Field.FLOOR])
        assertEquals(FieldMessage.AtLeastFloor, SettingsValidator.progression(ProgressionConfig(startingTotal = 40)).errors[Field.STARTING_TOTAL])
        assertEquals(FieldMessage.AtLeastStartingTotal, SettingsValidator.progression(ProgressionConfig(cap = 47)).errors[Field.CAP])
        assertEquals(FieldMessage.AtLeastOne, SettingsValidator.progression(ProgressionConfig(windowHours = 0)).errors[Field.WINDOW_HOURS])
        assertEquals(FieldMessage.GreaterThanZero, SettingsValidator.progression(ProgressionConfig(penaltyHoursPerRep = 0.0)).errors[Field.PENALTY_RATE])
        val nine = holds(*Array(9) { Hold(50 + it, 1) })
        assertEquals(FieldMessage.TooManyHolds(ProgressionConfig.MAX_HOLDS), SettingsValidator.progression(nine).errors[Field.HOLDS])

        val now = Instant.parse("2026-09-24T12:00:00Z")
        val cfg = ProgressionConfig()
        assertEquals(FieldMessage.AtLeastOne, SettingsValidator.currentState(0, 0, 0, null, now, cfg).errors[Field.TOTAL])
        assertEquals(FieldMessage.OutsideFloorCap, SettingsValidator.currentState(80, 24, 4, null, now, cfg).hints[Field.TOTAL])
        assertEquals(FieldMessage.AtLeastCurrentStreak, SettingsValidator.currentState(65, 3, 4, null, now, cfg).errors[Field.BEST_STREAK])
        assertEquals(FieldMessage.ZeroOrMore, SettingsValidator.currentState(65, 0, -1, null, now, cfg).errors[Field.CURRENT_STREAK])
        assertEquals(FieldMessage.InTheFuture, SettingsValidator.currentState(65, 24, 4, now.plusSeconds(60), now, cfg).errors[Field.LAST_CHECK_IN])
    }
}
