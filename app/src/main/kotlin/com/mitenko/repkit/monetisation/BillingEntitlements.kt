package com.mitenko.repkit.monetisation

import android.app.Activity
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.PRO_PRODUCT_ID
import com.mitenko.repkit.domain.ProUpgrade
import com.mitenko.repkit.domain.PurchaseQuery
import com.mitenko.repkit.domain.PurchaseState
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.domain.isPending
import com.mitenko.repkit.domain.tierAfter
import com.mitenko.repkit.platform.billing.BillingGateway
import com.mitenko.repkit.platform.billing.ITEM_ALREADY_OWNED
import com.mitenko.repkit.platform.billing.LaunchResult
import com.mitenko.repkit.platform.billing.PurchaseUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** What the Settings row and snackbars need beyond the tier (spec rev 41 §1.2, §1.5, §3.3). */
interface ProStore {
    /** True once the cached tier has been read (spec rev 41 §1.4); before that a Pro user still reads FREE. */
    val cacheLoaded: StateFlow<Boolean>
    val pending: StateFlow<Boolean>
    val price: StateFlow<String?>
    val events: Flow<ProEvent>
    /** Re-asks Play what the user owns (app start and every return to the foreground). */
    fun refresh()
    /** Settings → Restore purchase: like [refresh], then reports the outcome. */
    fun restore()
}

enum class ProEvent { BILLING_UNAVAILABLE, RESTORED, NOTHING_TO_RESTORE }

/**
 * Spec rev 41 §1.4: the tier starts from the DataStore cache and follows Play. Every Ok answer writes the cache at
 * once (a refund included); a failed answer never touches it.
 */
@Singleton
class BillingEntitlements @Inject constructor(
    private val gateway: BillingGateway,
    private val prefs: AppPreferences,
    private val crash: CrashReporter,
    private val currentActivity: CurrentActivityProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) : Entitlements, ProUpgrade, ProStore {
    private val _tier = MutableStateFlow(Tier.FREE)
    override val tier: StateFlow<Tier> = _tier.asStateFlow()
    private val _cacheLoaded = MutableStateFlow(false)
    override val cacheLoaded: StateFlow<Boolean> = _cacheLoaded.asStateFlow()
    private val _pending = MutableStateFlow(false)
    override val pending: StateFlow<Boolean> = _pending.asStateFlow()
    private val _price = MutableStateFlow<String?>(null)
    override val price: StateFlow<String?> = _price.asStateFlow()
    private val _events = MutableSharedFlow<ProEvent>(extraBufferCapacity = 4)
    override val events: Flow<ProEvent> = _events.asSharedFlow()

    private val lock = Mutex()
    private val acknowledgedTokens = mutableSetOf<String>()

    /** True once an Ok answer from Play has been applied; the cached tier must not overwrite it. */
    private var okApplied = false

    /** Each query or update takes the next number when it starts; [apply] drops an answer older than [newestApplied]. */
    private val sequence = AtomicLong()
    private var newestApplied = 0L

    init {
        scope.launch {
            val cached = try {
                prefs.proCached.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crash.recordNonFatal(e, "billing: cache read")
                null
            }
            lock.withLock { if (cached != null && !okApplied) _tier.value = cached }
            _cacheLoaded.value = true
            queryAndApply()
            fetchPrice()
        }
        scope.launch {
            gateway.updates.collect { update ->
                // Guarded per update, so one bad update can't end the collector.
                guarded("updates") {
                    when (update) {
                        is PurchaseUpdate.Purchases ->
                            // An update is not a full ownership snapshot: without the Pro purchase in it, ask Play.
                            if (update.owned.any { it.productId == PRO_PRODUCT_ID }) {
                                apply(sequence.incrementAndGet(), PurchaseQuery.Ok(update.owned))
                            } else {
                                queryAndApply()
                            }
                        PurchaseUpdate.Cancelled -> Unit
                        is PurchaseUpdate.Error ->
                            if (update.code == ITEM_ALREADY_OWNED) alreadyOwned("purchase update") else fail("purchase update", update.code)
                    }
                }
            }
        }
    }

    override fun refresh() {
        scope.launch {
            queryAndApply()
            // Play may not have had the price at start (offline); ask again until it does.
            if (_price.value == null) fetchPrice()
        }
    }

    override fun restore() {
        scope.launch {
            when (queryAndApply()) {
                PurchaseQuery.Failed -> fail("restore", null)
                is PurchaseQuery.Ok ->
                    _events.emit(if (_tier.value == Tier.PRO) ProEvent.RESTORED else ProEvent.NOTHING_TO_RESTORE)
            }
        }
    }

    override fun start() {
        scope.launch {
            val activity = currentActivity.get() ?: return@launch fail("launch: no activity", null)
            val result = try {
                gateway.launchPurchase(activity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crash.recordNonFatal(e, "billing: launch")
                return@launch _events.emit(ProEvent.BILLING_UNAVAILABLE)
            }
            if (result is LaunchResult.Failed) {
                if (result.code == ITEM_ALREADY_OWNED) alreadyOwned("launch") else fail("launch", result.code)
            }
        }
    }

    /**
     * Play says the user owns Pro already (a purchase on another phone, or one this install hasn't seen yet):
     * ask Play and apply its answer like a restore. Pro → RESTORED; anything else → BILLING_UNAVAILABLE.
     */
    private suspend fun alreadyOwned(where: String) {
        crash.log("billing: $where: already owned")
        val q = queryAndApply()
        _events.emit(if (q is PurchaseQuery.Ok && _tier.value == Tier.PRO) ProEvent.RESTORED else ProEvent.BILLING_UNAVAILABLE)
    }

    private suspend fun fetchPrice() = guarded("price") { _price.value = gateway.proPrice() }

    /** Asks Play and applies the answer, numbered when the query starts so a slower, older answer can't win. */
    private suspend fun queryAndApply(): PurchaseQuery {
        val seq = sequence.incrementAndGet()
        return queryOwned().also { apply(seq, it) }
    }

    /** Nothing in here may throw out of a coroutine on the app scope (spec rev 41 §1.4): a throw becomes a non-fatal. */
    private suspend fun guarded(where: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            crash.recordNonFatal(e, "billing: $where")
        }
    }

    /** Play's answer, or [PurchaseQuery.Failed] when the call threw. */
    private suspend fun queryOwned(): PurchaseQuery = try {
        gateway.queryOwned()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        crash.recordNonFatal(e, "billing: query")
        PurchaseQuery.Failed
    }

    /** Acknowledges once per token; a failure (false or a throw) forgets the token so the next answer retries. */
    private suspend fun acknowledge(token: String) {
        if (!acknowledgedTokens.add(token)) return
        val ok = try {
            gateway.acknowledge(token)
        } catch (e: CancellationException) {
            acknowledgedTokens.remove(token)
            throw e
        } catch (e: Exception) {
            crash.recordNonFatal(e, "billing: acknowledge")
            false
        }
        if (!ok) {
            acknowledgedTokens.remove(token)
            crash.log("billing: acknowledge failed")
        }
    }

    /**
     * One answer from Play, numbered [seq]: the tier, pending and the cache first, then acknowledge, so a buyer
     * isn't shown Free while acknowledge waits on Play (spec rev 41 §1.2–§1.4). An answer older than the newest
     * applied one is dropped.
     */
    private suspend fun apply(seq: Long, query: PurchaseQuery) = lock.withLock {
        if (query !is PurchaseQuery.Ok) {
            crash.log("billing: query failed")
            return@withLock
        }
        if (seq < newestApplied) {
            crash.log("billing: stale answer dropped")
            return@withLock
        }
        newestApplied = seq
        _pending.value = isPending(query)
        val tier = tierAfter(query, _tier.value)
        _tier.value = tier
        okApplied = true
        guarded("cache write") { prefs.setProCached(tier) }
        query.owned
            .filter { it.productId == PRO_PRODUCT_ID && it.state == PurchaseState.PURCHASED && !it.acknowledged }
            .forEach { acknowledge(it.token) }
    }

    private suspend fun fail(where: String, code: Int?) {
        crash.log("billing: $where failed" + (code?.let { " (code $it)" } ?: ""))
        _events.emit(ProEvent.BILLING_UNAVAILABLE)
    }
}

/** The foreground Activity, for Play's purchase sheet (spec rev 41 §1.5); null when none is resumed. */
fun interface CurrentActivityProvider {
    fun get(): Activity?
}
