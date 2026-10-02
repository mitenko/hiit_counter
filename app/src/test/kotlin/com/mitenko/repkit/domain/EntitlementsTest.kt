package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    private val limits = FreeLimits()

    @Test
    fun `the free limit is 3 entries`() {
        assertEquals(3, limits.maxEntries)
    }

    @Test
    fun `pro can always add an entry`() {
        listOf(0, 2, 3, 4, 10, 1_000).forEach { count ->
            assertTrue("PRO at $count", canAddEntry(Tier.PRO, count, limits))
        }
    }

    @Test
    fun `free can add below the limit only`() {
        assertTrue(canAddEntry(Tier.FREE, 2, limits))
        assertFalse(canAddEntry(Tier.FREE, 3, limits))
        assertFalse(canAddEntry(Tier.FREE, 4, limits))
    }

    @Test
    fun `a free user over the limit keeps their entries but can't add one more`() {
        assertFalse(canAddEntry(Tier.FREE, 5, limits))
    }

    @Test
    fun `the limit follows FreeLimits`() {
        assertTrue(canAddEntry(Tier.FREE, 4, FreeLimits(maxEntries = 5)))
        assertFalse(canAddEntry(Tier.FREE, 5, FreeLimits(maxEntries = 5)))
    }

    @Test
    fun `ads show only on the free tier`() {
        assertTrue(showAds(Tier.FREE))
        assertFalse(showAds(Tier.PRO))
    }
}
