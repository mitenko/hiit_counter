package com.mitenko.repkit.di

import com.mitenko.repkit.domain.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonetisationModuleTest {
    @Test fun `debug builds honour FREE and PRO`() {
        assertEquals(Tier.FREE, forcedTier(debug = true, value = "FREE"))
        assertEquals(Tier.PRO, forcedTier(debug = true, value = "pro"))
    }
    @Test fun `release ignores it and blank or junk means billing`() {
        assertNull(forcedTier(debug = false, value = "PRO"))
        assertNull(forcedTier(debug = true, value = ""))
        assertNull(forcedTier(debug = true, value = "gold"))
    }
}
