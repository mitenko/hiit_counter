package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.domain.Prescription
import com.mitenko.repkit.ui.entry.RepsColumnLayout.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrescriptionLayoutTest {
    private fun load(weight: Int, reps: Int) = Prescription.Load(weight, reps)

    @Test
    fun `the weight decides first, then the reps`() {
        // Reps then weight at the top of the range: 16 kg × 12 → 20 kg × 8 is a step up.
        assertEquals(Change.UP, PrescriptionLayout.change(load(1600, 12), load(2000, 8)))
        assertEquals(Change.DOWN, PrescriptionLayout.change(load(1600, 8), load(1400, 12)))
        assertEquals(Change.UP, PrescriptionLayout.change(load(1600, 9), load(1600, 10)))
        assertEquals(Change.DOWN, PrescriptionLayout.change(load(1600, 10), load(1600, 8)))
        assertNull(PrescriptionLayout.change(load(1600, 10), load(1600, 10)))
    }
}
