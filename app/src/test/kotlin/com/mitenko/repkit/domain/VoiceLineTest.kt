package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Cue
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceLineTest {
    private val kg16 = SetWeight(1600, WeightUnit.KG)

    @Test
    fun `Reps mode and Timer only say the bare number, as before`() {
        assertEquals(VoiceLine.Count(12), VoiceLine.of(Cue.PhaseStart(Phase.WORK, reps = 12)))
    }

    @Test
    fun `a weight run's first set says the weight and the reps, later sets just the reps`() {
        assertEquals(VoiceLine.Load(1600, WeightUnit.KG, 10), VoiceLine.of(Cue.PhaseStart(Phase.WORK, 10, kg16, newWeight = true)))
        assertEquals(VoiceLine.Reps(10), VoiceLine.of(Cue.PhaseStart(Phase.WORK, 10, kg16, newWeight = false)))
        assertEquals(10, VoiceLine.Load(1600, WeightUnit.KG, 10).number)
    }

    @Test
    fun `nothing is said without reps or outside a work start`() {
        assertNull(VoiceLine.of(Cue.PhaseStart(Phase.WORK)))
        assertNull(VoiceLine.of(Cue.PhaseStart(Phase.REST)))
        assertNull(VoiceLine.of(Cue.Countdown(3)))
        assertNull(VoiceLine.of(Cue.Finished))
    }
}
