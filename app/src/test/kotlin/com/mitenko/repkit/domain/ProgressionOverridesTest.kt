package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec revision 28: the field you edit wins, and the value it pushes against moves to fit. */
class ProgressionOverridesTest {
    private val base = ProgressionConfig(startingTotal = 50, floor = 48, cap = 72, holds = listOf(Hold(64, 4)), windowHours = 30)

    private fun ProgressionConfig.valid() = SettingsValidator.progression(this).isValid

    // Rule 1: starting reps outside floor..cap widen the range.

    @Test
    fun `starting reps above the maximum raise the maximum`() {
        val r = base.copy(startingTotal = 80).resolveFor(ProgressionField.STARTING_TOTAL)
        assertEquals(base.copy(startingTotal = 80, cap = 80), r.config)
        assertEquals(listOf(RangeChange.RaisedMax(80)), r.moves)
        assertTrue(r.config.valid())
    }

    @Test
    fun `starting reps below the minimum lower the minimum`() {
        val r = base.copy(startingTotal = 40).resolveFor(ProgressionField.STARTING_TOTAL)
        assertEquals(base.copy(startingTotal = 40, floor = 40), r.config)
        assertEquals(listOf(RangeChange.LoweredMin(40)), r.moves)
        assertTrue(r.config.valid())
    }

    // Rule 2: the minimum pushes starting reps up.

    @Test
    fun `a minimum raised above starting reps raises starting reps`() {
        val r = base.copy(floor = 55).resolveFor(ProgressionField.FLOOR)
        assertEquals(base.copy(floor = 55, startingTotal = 55), r.config)
        assertEquals(listOf(Move.StartingRaised(55)), r.moves)
        assertTrue(r.config.valid())
    }

    @Test
    fun `a minimum lowered below starting reps moves nothing`() {
        val edited = base.copy(floor = 30)
        val r = edited.resolveFor(ProgressionField.FLOOR)
        assertSame(edited, r.config)
        assertTrue(r.moves.isEmpty())
    }

    @Test
    fun `a minimum raised past the maximum raises starting reps and then the maximum`() {
        val r = base.copy(floor = 90).resolveFor(ProgressionField.FLOOR)
        assertEquals(base.copy(floor = 90, startingTotal = 90, cap = 90), r.config)
        assertEquals(listOf(Move.StartingRaised(90), RangeChange.RaisedMax(90)), r.moves)
        assertTrue(r.config.valid())
    }

    // Rule 3: the maximum pushes starting reps down.

    @Test
    fun `a maximum lowered below starting reps lowers starting reps`() {
        val r = base.copy(cap = 49).resolveFor(ProgressionField.CAP)
        assertEquals(base.copy(cap = 49, startingTotal = 49), r.config)
        assertEquals(listOf(Move.StartingLowered(49)), r.moves)
        assertTrue(r.config.valid())
    }

    @Test
    fun `a maximum raised above starting reps moves nothing`() {
        val edited = base.copy(cap = 90)
        val r = edited.resolveFor(ProgressionField.CAP)
        assertSame(edited, r.config)
        assertTrue(r.moves.isEmpty())
    }

    @Test
    fun `a maximum lowered past the minimum lowers starting reps and then the minimum`() {
        val r = base.copy(cap = 40).resolveFor(ProgressionField.CAP)
        assertEquals(base.copy(cap = 40, startingTotal = 40, floor = 40), r.config)
        assertEquals(listOf(Move.StartingLowered(40), RangeChange.LoweredMin(40)), r.moves)
        assertTrue(r.config.valid())
    }

    @Test
    fun `the edited field wins - the same values edited through different fields resolve differently`() {
        // starting 50 and maximum 49 together: which one the user just set decides which moves.
        val conflict = base.copy(cap = 49)
        val capWins = conflict.resolveFor(ProgressionField.CAP)
        assertEquals(49, capWins.config.cap)
        assertEquals(49, capWins.config.startingTotal)
        assertEquals(listOf(Move.StartingLowered(49)), capWins.moves)
        val startingWins = conflict.resolveFor(ProgressionField.STARTING_TOTAL)
        assertEquals(50, startingWins.config.startingTotal)
        assertEquals(50, startingWins.config.cap)
        assertEquals(listOf(RangeChange.RaisedMax(50)), startingWins.moves)
    }

    @Test
    fun `no edited field resolves nothing, so the old errors stay`() {
        val edited = base.copy(startingTotal = 40)
        val r = edited.resolveFor(null)
        assertSame(edited, r.config)
        assertTrue(r.moves.isEmpty())
        assertEquals(FieldMessage.AtLeastFloor, SettingsValidator.progression(r.config).errors[Field.STARTING_TOTAL])
    }

    @Test
    fun `a minimum below 1 stays an error and pushes nothing`() {
        val edited = base.copy(floor = 0)
        val r = edited.resolveFor(ProgressionField.FLOOR)
        assertSame(edited, r.config)
        assertTrue(r.moves.isEmpty())
        assertEquals(FieldMessage.AtLeastOne, SettingsValidator.progression(r.config).errors[Field.FLOOR])
        // A maximum below 1 would drag the minimum below 1 too, so it is left alone as well.
        val cap = base.copy(cap = 0)
        assertSame(cap, cap.resolveFor(ProgressionField.CAP).config)
        assertFalse(cap.resolveFor(ProgressionField.CAP).config.valid())
    }

    @Test
    fun `values already in order move nothing`() {
        for (field in ProgressionField.entries) {
            val r = base.resolveFor(field)
            assertSame(base, r.config)
            assertTrue(r.moves.isEmpty())
        }
    }

    // Rule 4: the stored total follows floor..cap.

    @Test
    fun `a total above the new maximum is lowered, below the new minimum raised, inside it kept`() {
        assertEquals(Move.CurrentLowered(60), base.copy(cap = 60).totalMove(66))
        assertEquals(Move.CurrentRaised(55), base.copy(floor = 55, startingTotal = 55).totalMove(50))
        assertNull(base.totalMove(48))
        assertNull(base.totalMove(72))
        assertEquals(60, Move.CurrentLowered(60).to)
    }

    @Test
    fun `the cascade of a raised minimum moves starting reps in the draft and the current total in the store`() {
        val r = base.copy(floor = 52).resolveFor(ProgressionField.FLOOR)
        assertEquals(listOf(Move.StartingRaised(52)), r.moves)
        assertEquals(Move.CurrentRaised(52), r.config.totalMove(49))
    }

    // Rule 5: current streak pushes best streak up.

    @Test
    fun `a current streak raised above the best streak raises the best streak`() {
        val r = resolveStreaks(best = 4, current = 6, edited = StreakField.CURRENT)
        assertEquals(StreakResolution(best = 6, current = 6, moves = listOf(Move.BestStreakRaised(6))), r)
        assertTrue(SettingsValidator.currentState(50, r.best, r.current, null, java.time.Instant.EPOCH).isValid)
    }

    @Test
    fun `a current streak lowered moves nothing`() {
        assertEquals(StreakResolution(4, 2, emptyList()), resolveStreaks(best = 4, current = 2, edited = StreakField.CURRENT))
    }

    @Test
    fun `a best streak below the current streak stays an error (best streak is read-only, spec rev 29)`() {
        // Best streak has no edit path any more (spec revision 29), so the only way the domain sees
        // best < current is with no streak field edited; the validator still catches it as a safety net.
        val r = resolveStreaks(best = 3, current = 4, edited = null)
        assertEquals(StreakResolution(3, 4, emptyList()), r)
        assertEquals(
            FieldMessage.AtLeastCurrentStreak,
            SettingsValidator.currentState(50, r.best, r.current, null, java.time.Instant.EPOCH).errors[Field.BEST_STREAK],
        )
    }

    // Rule 6: the hint says why a hold is outside the range.

    @Test
    fun `a hold above the maximum or below the minimum says which bound`() {
        val above = SettingsValidator.progression(base.copy(holds = listOf(Hold(80, 4))))
        assertEquals(mapOf(0 to FieldMessage.HoldOutsideRange(at = 80, bound = 72, isAbove = true)), above.holdHints)
        val below = SettingsValidator.progression(base.copy(holds = listOf(Hold(40, 4))))
        assertEquals(mapOf(0 to FieldMessage.HoldOutsideRange(at = 40, bound = 48, isAbove = false)), below.holdHints)
        assertTrue(above.isValid)
        assertTrue(below.isValid)
    }

    @Test
    fun `a hold at the maximum or held for 0 keeps the plain hint`() {
        assertEquals(mapOf(0 to FieldMessage.HoldDisabled), SettingsValidator.progression(base.copy(holds = listOf(Hold(72, 4)))).holdHints)
        assertEquals(mapOf(0 to FieldMessage.HoldDisabled), SettingsValidator.progression(base.copy(holds = listOf(Hold(64, 0)))).holdHints)
        // Outside the range wins over a count of 0: the bound is the more useful reason.
        assertEquals(
            mapOf(0 to FieldMessage.HoldOutsideRange(90, 72, isAbove = true)),
            SettingsValidator.progression(base.copy(holds = listOf(Hold(90, 0)))).holdHints,
        )
    }
}
