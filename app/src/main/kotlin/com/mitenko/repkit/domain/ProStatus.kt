package com.mitenko.repkit.domain

/** The Play product that unlocks Pro (spec rev 41 §1.1): a one-time, non-consumable in-app product. */
const val PRO_PRODUCT_ID = "repkit_pro"

/** A purchase's state as the app needs it (spec rev 41 §1.2); Play's UNSPECIFIED and anything new map to OTHER. */
enum class PurchaseState { PURCHASED, PENDING, OTHER }

/** One purchase Play reports as owned. [token] is only for acknowledging and is never logged (rev 30). */
data class OwnedPurchase(val productId: String, val state: PurchaseState, val acknowledged: Boolean, val token: String)

/** The result of asking Play what the user owns (spec rev 41 §1.3). */
sealed interface PurchaseQuery {
    data class Ok(val owned: List<OwnedPurchase>) : PurchaseQuery
    data object Failed : PurchaseQuery
}

/**
 * Spec rev 41 §1.3: PRO when Play reports [PRO_PRODUCT_ID] purchased, FREE when an answer lacks it (a refund or
 * revocation), and [previous] when the query failed: an error never downgrades.
 */
fun tierAfter(query: PurchaseQuery, previous: Tier): Tier = when (query) {
    PurchaseQuery.Failed -> previous
    is PurchaseQuery.Ok ->
        if (query.owned.any { it.productId == PRO_PRODUCT_ID && it.state == PurchaseState.PURCHASED }) Tier.PRO else Tier.FREE
}

/** Spec rev 41 §1.2: a Pro purchase waiting on a slow payment method ("Purchase pending"). */
fun isPending(query: PurchaseQuery): Boolean =
    query is PurchaseQuery.Ok && query.owned.any { it.productId == PRO_PRODUCT_ID && it.state == PurchaseState.PENDING }
