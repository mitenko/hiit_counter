package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec rev 41 §1.3: billing results to a tier; an error never downgrades. */
class ProStatusTest {
    private fun owned(state: PurchaseState, id: String = PRO_PRODUCT_ID) = OwnedPurchase(id, state, acknowledged = true, token = "t")

    @Test fun `a purchased Pro makes PRO`() =
        assertEquals(Tier.PRO, tierAfter(PurchaseQuery.Ok(listOf(owned(PurchaseState.PURCHASED))), Tier.FREE))

    @Test fun `no purchase makes FREE, which is how a refund lands`() =
        assertEquals(Tier.FREE, tierAfter(PurchaseQuery.Ok(emptyList()), Tier.PRO))

    @Test fun `a pending purchase stays FREE`() =
        assertEquals(Tier.FREE, tierAfter(PurchaseQuery.Ok(listOf(owned(PurchaseState.PENDING))), Tier.FREE))

    @Test fun `another product doesn't count`() =
        assertEquals(Tier.FREE, tierAfter(PurchaseQuery.Ok(listOf(owned(PurchaseState.PURCHASED, "other"))), Tier.FREE))

    @Test fun `a failed query keeps the previous tier either way`() {
        assertEquals(Tier.PRO, tierAfter(PurchaseQuery.Failed, Tier.PRO))
        assertEquals(Tier.FREE, tierAfter(PurchaseQuery.Failed, Tier.FREE))
    }

    @Test fun `pending is reported only for a pending Pro`() {
        assertTrue(isPending(PurchaseQuery.Ok(listOf(owned(PurchaseState.PENDING)))))
        assertFalse(isPending(PurchaseQuery.Ok(listOf(owned(PurchaseState.PURCHASED)))))
        assertFalse(isPending(PurchaseQuery.Failed))
    }
}
