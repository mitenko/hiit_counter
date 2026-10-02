package com.mitenko.repkit.monetisation

import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.domain.canAddEntry
import com.mitenko.repkit.domain.FreeLimits
import com.mitenko.repkit.domain.showAds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockedEntitlementsTest {
    @Test
    fun `v1 is always pro, so nothing is limited and no ads show`() {
        val tier = UnlockedEntitlements().tier.value
        assertEquals(Tier.PRO, tier)
        assertTrue(canAddEntry(tier, 100, FreeLimits()))
        assertFalse(showAds(tier))
    }

    @Test
    fun `v1's Go Pro does nothing`() {
        NoOpProUpgrade().start()
    }
}
