package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PenaltyDraftTest {
    @Test
    fun `whole multiples of a half hour become half-hours`() {
        assertEquals(PenaltyDraft(39), PenaltyDraft.of(19.5))
        assertEquals(19.5, PenaltyDraft.of(19.5).hours, 0.0)
        assertEquals(PenaltyDraft(2), PenaltyDraft.of(1.0))
        assertNull(PenaltyDraft.of(1.0).exact)
    }

    @Test
    fun `a non-multiple is kept exactly`() {
        val d = PenaltyDraft.of(0.3)
        assertEquals(0.3, d.hours, 0.0)
        assertEquals(0.3, d.exact!!, 0.0)
    }

    @Test
    fun `plus and minus snap a non-multiple to the neighbouring multiple`() {
        assertEquals(PenaltyDraft(1), PenaltyDraft.of(0.3).plus())
        assertEquals(PenaltyDraft(1), PenaltyDraft.of(0.3).minus())
        assertEquals(1.5, PenaltyDraft.of(1.3).plus().hours, 0.0)
        assertEquals(1.0, PenaltyDraft.of(1.3).minus().hours, 0.0)
    }

    @Test
    fun `steps clamp at 0_5 and 999_5`() {
        assertEquals(PenaltyDraft(1), PenaltyDraft(1).minus())
        assertEquals(PenaltyDraft(1999), PenaltyDraft(1999).plus())
        assertEquals(0.5, PenaltyDraft(PenaltyDraft.MIN_HALF_HOURS).hours, 0.0)
        assertEquals(999.5, PenaltyDraft(PenaltyDraft.MAX_HALF_HOURS).hours, 0.0)
    }

    @Test
    fun `hold-to-repeat never drifts`() {
        var d = PenaltyDraft.of(19.5)
        repeat(1000) { d = d.plus() }
        assertEquals(519.5, d.hours, 0.0)
        repeat(1000) { d = d.minus() }
        assertEquals(PenaltyDraft(39), d)
    }
}
