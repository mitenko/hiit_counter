package com.mitenko.repkit.monetisation

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.billingclient.api.BillingClient
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.OwnedPurchase
import com.mitenko.repkit.domain.PRO_PRODUCT_ID
import com.mitenko.repkit.domain.PurchaseQuery
import com.mitenko.repkit.domain.PurchaseState
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.platform.billing.BillingGateway
import com.mitenko.repkit.platform.billing.ITEM_ALREADY_OWNED
import com.mitenko.repkit.platform.billing.LaunchResult
import com.mitenko.repkit.platform.billing.PurchaseUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.IOException

class FakeBillingGateway : BillingGateway {
    var query: PurchaseQuery = PurchaseQuery.Ok(emptyList())
    var price: String? = "\$3.99"
    var launch: LaunchResult = LaunchResult.Started
    val acknowledged = mutableListOf<String>()
    var queries = 0
    /** The next this-many acknowledge calls return false / throw. */
    var failAcks = 0
    var throwAcks = 0
    var priceCalls = 0
    /** When set, the next query waits for it and answers with its value. */
    var queryGate: CompletableDeferred<PurchaseQuery>? = null
    /** When set, the next acknowledge waits for it before answering. */
    var ackGate: CompletableDeferred<Unit>? = null
    override val updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)
    override suspend fun queryOwned(): PurchaseQuery {
        queries++
        queryGate?.let { gate -> queryGate = null; return gate.await() }
        return query
    }
    override suspend fun acknowledge(token: String): Boolean {
        acknowledged += token
        ackGate?.also { ackGate = null }?.await()
        if (throwAcks > 0) { throwAcks--; throw IOException("ack boom") }
        if (failAcks > 0) { failAcks--; return false }
        return true
    }
    override suspend fun proPrice(): String? { priceCalls++; return price }
    override suspend fun launchPurchase(activity: Activity): LaunchResult = launch
}

class RecordingCrash : CrashReporter {
    val logs = mutableListOf<String>()
    override fun setEnabled(enabled: Boolean) = Unit
    override fun recordNonFatal(t: Throwable, context: String) { logs += context + ":" + t.message }
    override fun log(message: String) { logs += message }
}

/**
 * An in-memory DataStore, so everything runs on the test scheduler and runCurrent is exact.
 * [readGate] holds back the next read until completed; [failWrites] makes that many writes throw.
 */
class FakeStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    var readGate: CompletableDeferred<Unit>? = null
    var failWrites = 0
    override val data: Flow<Preferences> = flow {
        readGate?.also { readGate = null }?.await()
        emit(state.value)
    }
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            if (failWrites > 0) { failWrites--; throw IOException("write boom") }
            transform(state.value).also { state.value = it }
        }
}

private fun pro(state: PurchaseState, acked: Boolean = true, token: String = "tok") =
    OwnedPurchase(PRO_PRODUCT_ID, state, acked, token)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillingEntitlementsTest {
    private val gateway = FakeBillingGateway()
    private val crash = RecordingCrash()
    private val store = FakeStore()
    private val prefs = AppPreferences(store)
    private var activity: Activity? = null

    // runCurrent, not advanceUntilIdle: the latter ignores backgroundScope tasks (the collectors under test).
    private fun TestScope.subject(): BillingEntitlements {
        val s = BillingEntitlements(gateway, prefs, crash, currentActivity = { activity }, scope = backgroundScope)
        runCurrent()
        return s
    }

    private fun TestScope.collectEvents(s: BillingEntitlements): List<ProEvent> {
        val list = mutableListOf<ProEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { s.events.collect { list += it } }
        return list
    }

    @Test
    fun `a cached PRO is PRO before the first query answers`() = runTest {
        prefs.setProCached(Tier.PRO)
        gateway.query = PurchaseQuery.Failed
        val s = subject()
        assertEquals(1, gateway.queries)
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(Tier.PRO, prefs.proCached.first())
    }

    @Test
    fun `an Ok purchase makes PRO and writes the cache`() = runTest {
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED)))
        val s = subject()
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(Tier.PRO, prefs.proCached.first())
    }

    @Test
    fun `a refund (Ok without Pro) makes FREE and writes the cache at once`() = runTest {
        prefs.setProCached(Tier.PRO)
        gateway.query = PurchaseQuery.Ok(emptyList())
        val s = subject()
        assertEquals(Tier.FREE, s.tier.value)
        assertEquals(Tier.FREE, prefs.proCached.first())
    }

    @Test
    fun `a failed query changes neither the tier nor the cache`() = runTest {
        prefs.setProCached(Tier.PRO)
        gateway.query = PurchaseQuery.Failed
        val s = subject()
        assertTrue(s.cacheLoaded.value)
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(Tier.PRO, prefs.proCached.first())
    }

    @Test
    fun `a failed query with a cached FREE stays FREE`() = runTest {
        gateway.query = PurchaseQuery.Failed
        val s = subject()
        assertEquals(1, gateway.queries)
        assertTrue(s.cacheLoaded.value)
        assertEquals(Tier.FREE, s.tier.value)
        assertEquals(Tier.FREE, prefs.proCached.first())
    }

    @Test
    fun `an unacknowledged purchase is acknowledged once`() = runTest {
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED, acked = false, token = "a")))
        val s = subject()
        s.refresh()
        runCurrent()
        s.refresh()
        runCurrent()
        assertEquals(3, gateway.queries)
        assertEquals(listOf("a"), gateway.acknowledged)
    }

    @Test
    fun `a failed acknowledge is retried by the next refresh`() = runTest {
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED, acked = false, token = "a")))
        gateway.failAcks = 1
        val s = subject()
        assertEquals(listOf("a"), gateway.acknowledged)
        s.refresh()
        runCurrent()
        assertEquals(listOf("a", "a"), gateway.acknowledged)
        s.refresh()
        runCurrent()
        assertEquals(listOf("a", "a"), gateway.acknowledged)
    }

    @Test
    fun `a throwing acknowledge is retried too`() = runTest {
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED, acked = false, token = "a")))
        gateway.throwAcks = 1
        val s = subject()
        assertEquals(Tier.PRO, s.tier.value)
        s.refresh()
        runCurrent()
        assertEquals(listOf("a", "a"), gateway.acknowledged)
    }

    @Test
    fun `a pending purchase isn't acknowledged and sets pending`() = runTest {
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PENDING, acked = false)))
        val s = subject()
        assertEquals(1, gateway.queries)
        assertTrue(gateway.acknowledged.isEmpty())
        assertTrue(s.pending.value)
        assertEquals(Tier.FREE, s.tier.value)
    }

    @Test
    fun `a pending purchase completing via updates flips to PRO`() = runTest {
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PENDING, acked = false)))
        val s = subject()
        gateway.updates.emit(PurchaseUpdate.Purchases(listOf(pro(PurchaseState.PURCHASED, acked = false, token = "p"))))
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(listOf("p"), gateway.acknowledged)
        assertFalse(s.pending.value)
    }

    @Test
    fun `an empty Purchases update re-queries Play instead of caching FREE`() = runTest {
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED)))
        val s = subject()
        assertEquals(Tier.PRO, s.tier.value)
        val before = gateway.queries
        gateway.updates.emit(PurchaseUpdate.Purchases(emptyList()))
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(Tier.PRO, prefs.proCached.first())
        assertTrue(gateway.queries > before)
    }

    @Test
    fun `a cancel emits nothing`() = runTest {
        val s = subject()
        val events = collectEvents(s)
        gateway.updates.emit(PurchaseUpdate.Cancelled)
        runCurrent()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a purchase error emits BILLING_UNAVAILABLE and records the code only`() = runTest {
        val s = subject()
        val events = collectEvents(s)
        gateway.updates.emit(PurchaseUpdate.Error(3))
        runCurrent()
        assertEquals(listOf(ProEvent.BILLING_UNAVAILABLE), events)
        assertTrue(crash.logs.any { "3" in it })
        assertFalse(crash.logs.any { "tok" in it })
    }

    @Test
    fun `Go Pro with no activity or a failed launch emits BILLING_UNAVAILABLE`() = runTest {
        val s = subject()
        val events = collectEvents(s)
        s.start()
        runCurrent()
        assertEquals(listOf(ProEvent.BILLING_UNAVAILABLE), events)
        activity = Robolectric.buildActivity(Activity::class.java).get()
        gateway.launch = LaunchResult.Failed(2)
        s.start()
        runCurrent()
        assertEquals(listOf(ProEvent.BILLING_UNAVAILABLE, ProEvent.BILLING_UNAVAILABLE), events)
    }

    @Test
    fun `restore reports RESTORED or NOTHING_TO_RESTORE`() = runTest {
        val s = subject()
        val events = collectEvents(s)
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED)))
        s.restore()
        runCurrent()
        gateway.query = PurchaseQuery.Ok(emptyList())
        s.restore()
        runCurrent()
        gateway.query = PurchaseQuery.Failed
        s.restore()
        runCurrent()
        assertEquals(
            listOf(ProEvent.RESTORED, ProEvent.NOTHING_TO_RESTORE, ProEvent.BILLING_UNAVAILABLE),
            events,
        )
    }

    @Test
    fun `cacheLoaded turns true after the cache is read`() = runTest {
        prefs.setProCached(Tier.PRO)
        gateway.query = PurchaseQuery.Failed
        val s = subject()
        assertTrue(s.cacheLoaded.value)
        assertEquals(Tier.PRO, s.tier.value)
    }

    @Test
    fun `the price comes from the gateway`() = runTest {
        assertEquals("\$3.99", subject().price.value)
        gateway.price = null
        assertNull(subject().price.value)
    }

    @Test
    fun `a throwing acknowledge doesn't kill the updates collector`() = runTest {
        gateway.throwAcks = 1
        val s = subject()
        gateway.updates.emit(PurchaseUpdate.Purchases(listOf(pro(PurchaseState.PURCHASED, acked = false, token = "secret-9"))))
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        assertTrue(crash.logs.any { it.startsWith("billing: acknowledge") })
        assertFalse(crash.logs.any { "secret-9" in it })
        gateway.updates.emit(PurchaseUpdate.Purchases(emptyList()))
        runCurrent()
        assertEquals(Tier.FREE, s.tier.value)
    }

    @Test
    fun `a failing cache write doesn't kill the updates collector`() = runTest {
        val s = subject()
        store.failWrites = 1
        gateway.updates.emit(PurchaseUpdate.Purchases(listOf(pro(PurchaseState.PURCHASED))))
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        assertTrue(crash.logs.any { it.startsWith("billing: cache write") })
        gateway.updates.emit(PurchaseUpdate.Purchases(emptyList()))
        runCurrent()
        assertEquals(Tier.FREE, s.tier.value)
        assertEquals(Tier.FREE, prefs.proCached.first())
    }

    @Test
    fun `a cache read finishing after an Ok answer doesn't overwrite it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        store.readGate = gate
        gateway.query = PurchaseQuery.Failed
        val s = subject()
        assertFalse(s.cacheLoaded.value)
        gateway.updates.emit(PurchaseUpdate.Purchases(listOf(pro(PurchaseState.PURCHASED))))
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        gate.complete(Unit)
        runCurrent()
        assertTrue(s.cacheLoaded.value)
        assertEquals(Tier.PRO, s.tier.value)
    }

    @Test
    fun `ITEM_ALREADY_OWNED is Play's code`() {
        assertEquals(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED, ITEM_ALREADY_OWNED)
    }

    @Test
    fun `ITEM_ALREADY_OWNED from the launch re-queries Play and restores`() = runTest {
        val s = subject()
        val events = collectEvents(s)
        activity = Robolectric.buildActivity(Activity::class.java).get()
        gateway.launch = LaunchResult.Failed(ITEM_ALREADY_OWNED)
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED)))
        val before = gateway.queries
        s.start()
        runCurrent()
        assertEquals(before + 1, gateway.queries)
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(Tier.PRO, prefs.proCached.first())
        assertEquals(listOf(ProEvent.RESTORED), events)
    }

    @Test
    fun `ITEM_ALREADY_OWNED from a purchase update re-queries Play and restores`() = runTest {
        val s = subject()
        val events = collectEvents(s)
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED)))
        gateway.updates.emit(PurchaseUpdate.Error(ITEM_ALREADY_OWNED))
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(listOf(ProEvent.RESTORED), events)
    }

    @Test
    fun `ITEM_ALREADY_OWNED without Pro in Play's answer reports BILLING_UNAVAILABLE`() = runTest {
        val s = subject()
        val events = collectEvents(s)
        gateway.updates.emit(PurchaseUpdate.Error(ITEM_ALREADY_OWNED))
        runCurrent()
        gateway.query = PurchaseQuery.Failed
        activity = Robolectric.buildActivity(Activity::class.java).get()
        gateway.launch = LaunchResult.Failed(ITEM_ALREADY_OWNED)
        s.start()
        runCurrent()
        assertEquals(Tier.FREE, s.tier.value)
        assertEquals(listOf(ProEvent.BILLING_UNAVAILABLE, ProEvent.BILLING_UNAVAILABLE), events)
    }

    @Test
    fun `a stale query landing after a newer update doesn't overwrite it`() = runTest {
        val s = subject()
        val stale = CompletableDeferred<PurchaseQuery>()
        gateway.queryGate = stale
        s.refresh()
        runCurrent()
        gateway.updates.emit(PurchaseUpdate.Purchases(listOf(pro(PurchaseState.PURCHASED))))
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        stale.complete(PurchaseQuery.Ok(emptyList()))
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(Tier.PRO, prefs.proCached.first())
    }

    @Test
    fun `a purchase is granted before Play's acknowledge answers`() = runTest {
        val ack = CompletableDeferred<Unit>()
        gateway.ackGate = ack
        gateway.query = PurchaseQuery.Ok(listOf(pro(PurchaseState.PURCHASED, acked = false, token = "a")))
        val s = subject()
        assertEquals(listOf("a"), gateway.acknowledged)
        assertEquals(Tier.PRO, s.tier.value)
        assertEquals(Tier.PRO, prefs.proCached.first())
        ack.complete(Unit)
        runCurrent()
        assertEquals(Tier.PRO, s.tier.value)
        s.refresh()
        runCurrent()
        assertEquals(listOf("a"), gateway.acknowledged)
    }

    @Test
    fun `refresh fetches the price while it is still missing, then stops asking`() = runTest {
        gateway.price = null
        val s = subject()
        assertNull(s.price.value)
        gateway.price = "\$3.99"
        s.refresh()
        runCurrent()
        assertEquals("\$3.99", s.price.value)
        val calls = gateway.priceCalls
        s.refresh()
        runCurrent()
        assertEquals(calls, gateway.priceCalls)
    }
}
