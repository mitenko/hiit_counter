package com.mitenko.repkit.platform.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.mitenko.repkit.domain.OwnedPurchase
import com.mitenko.repkit.domain.PRO_PRODUCT_ID
import com.mitenko.repkit.domain.PurchaseQuery
import com.mitenko.repkit.domain.PurchaseState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Play Billing 8 behind [BillingGateway] (spec rev 41 §1.2). Connects lazily and reconnects on demand. */
@Singleton
class PlayBillingGateway @Inject constructor(@ApplicationContext context: Context) : BillingGateway {
    private val _updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override val updates: SharedFlow<PurchaseUpdate> = _updates

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            val update = when (result.responseCode) {
                BillingResponseCode.OK -> PurchaseUpdate.Purchases(purchases.orEmpty().flatMap(::toOwned))
                BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Cancelled
                else -> PurchaseUpdate.Error(result.responseCode)
            }
            _updates.tryEmit(update)
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val connectLock = Mutex()

    /** True once connected; a failure or a 10 s wait returns false (the caller reports it as Failed). */
    private suspend fun ready(): Boolean = connectLock.withLock {
        if (client.isReady) return@withLock true
        val done = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { done.complete(result.responseCode == BillingResponseCode.OK) }
            override fun onBillingServiceDisconnected() { done.complete(false) }
        })
        withTimeoutOrNull(10_000) { done.await() } ?: false
    }

    override suspend fun queryOwned(): PurchaseQuery {
        if (!ready()) return PurchaseQuery.Failed
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build())
        if (result.billingResult.responseCode != BillingResponseCode.OK) return PurchaseQuery.Failed
        return PurchaseQuery.Ok(result.purchasesList.flatMap(::toOwned))
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!ready()) return false
        val result = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
        return result.responseCode == BillingResponseCode.OK
    }

    private suspend fun proDetails(): ProductDetails? {
        if (!ready()) return null
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRO_PRODUCT_ID).setProductType(ProductType.INAPP).build()),
        ).build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) return null
        return result.productDetailsList?.firstOrNull()
    }

    override suspend fun proPrice(): String? = proDetails()?.oneTimePurchaseOfferDetails?.formattedPrice

    override suspend fun launchPurchase(activity: Activity): LaunchResult {
        val details = proDetails() ?: return LaunchResult.Failed(BillingResponseCode.ITEM_UNAVAILABLE)
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
        ).build()
        val result = client.launchBillingFlow(activity, params)
        return if (result.responseCode == BillingResponseCode.OK) LaunchResult.Started else LaunchResult.Failed(result.responseCode)
    }

    private fun toOwned(p: Purchase): List<OwnedPurchase> = p.products.map { id ->
        OwnedPurchase(
            productId = id,
            state = when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
                Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
                else -> PurchaseState.OTHER
            },
            acknowledged = p.isAcknowledged,
            token = p.purchaseToken,
        )
    }
}
