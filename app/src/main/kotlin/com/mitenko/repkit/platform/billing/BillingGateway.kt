package com.mitenko.repkit.platform.billing

import android.app.Activity
import com.mitenko.repkit.domain.OwnedPurchase
import com.mitenko.repkit.domain.PurchaseQuery
import kotlinx.coroutines.flow.Flow

/** A thin, testable face on Play Billing (spec rev 41 §1.2). [PlayBillingGateway] is the real one. */
interface BillingGateway {
    /** What the user owns now; [PurchaseQuery.Failed] when Play couldn't answer. */
    suspend fun queryOwned(): PurchaseQuery

    /** Acknowledges a purchase so Play doesn't refund it after 3 days; true on success. */
    suspend fun acknowledge(token: String): Boolean

    /** Play's formatted price for Pro ("$3.99"), or null when it isn't available. */
    suspend fun proPrice(): String?

    /** Opens Play's purchase sheet for Pro on [activity]. */
    suspend fun launchPurchase(activity: Activity): LaunchResult

    /** Purchase results Play pushes after [launchPurchase] (and pending purchases completing later). */
    val updates: Flow<PurchaseUpdate>
}

/** Play's BillingResponseCode.ITEM_ALREADY_OWNED: the user owns Pro already, so the answer is a restore. */
const val ITEM_ALREADY_OWNED = 7

sealed interface PurchaseUpdate {
    data class Purchases(val owned: List<OwnedPurchase>) : PurchaseUpdate
    data object Cancelled : PurchaseUpdate
    /** Play's BillingResponseCode; only the code is ever logged. */
    data class Error(val code: Int) : PurchaseUpdate
}

sealed interface LaunchResult {
    data object Started : LaunchResult
    data class Failed(val code: Int) : LaunchResult
}
