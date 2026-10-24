package com.mitenko.repkit.ads

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.CrashReporter
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.monetisation.CurrentActivityProvider
import com.mitenko.repkit.testutil.FakeEntitlements
import com.mitenko.repkit.testutil.FakeProStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

class FakeConsent : ConsentGateway {
    val calls = mutableListOf<String>()
    var state = ConsentState(ok = true, privacyOptionsRequired = false)
    var can = true
    var onForm: () -> Unit = {}
    override suspend fun update(activity: Activity): ConsentState { calls += "update"; return state }
    override suspend fun showFormIfRequired(activity: Activity) { calls += "form"; onForm() }
    override fun canRequestAds(): Boolean { calls += "can"; return can }
    override fun showPrivacyOptions(activity: Activity) { calls += "privacy" }
}

class FakeSdk : AdsSdk {
    var inits = 0
    var failWith: Throwable? = null
    override suspend fun initialize() { failWith?.let { throw it }; inits++ }
}

private class RecordingCrash : CrashReporter {
    val logs = mutableListOf<String>()
    override fun setEnabled(enabled: Boolean) = Unit
    override fun recordNonFatal(t: Throwable, context: String) = Unit
    override fun log(message: String) { logs += message }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AdsControllerTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val consent = FakeConsent()
    private val sdk = FakeSdk()
    private val crash = RecordingCrash()
    private val store = FakeProStore()
    private var current: Activity? = activity

    private fun TestScope.controller(tier: Tier): Pair<AdsController, FakeEntitlements> {
        val ent = FakeEntitlements(tier)
        return AdsController(ent, store, consent, sdk, { current }, crash, backgroundScope) to ent
    }

    private fun TestScope.settle() { runCurrent(); advanceUntilIdle(); runCurrent() }

    @Test
    fun `PRO never touches consent or the SDK`() = runTest {
        val (c, _) = controller(Tier.PRO)
        c.onForeground(); settle()
        assertTrue(consent.calls.isEmpty())
        assertEquals(0, sdk.inits)
        assertFalse(c.ready.value)
    }

    @Test
    fun `FREE runs update, form, can, then initialize, in that order`() = runTest {
        val (c, _) = controller(Tier.FREE)
        c.onForeground(); settle()
        assertEquals(listOf("update", "form", "can"), consent.calls)
        assertEquals(1, sdk.inits)
        assertTrue(c.ready.value)
    }

    @Test
    fun `consent not given means no initialize`() = runTest {
        consent.can = false
        val (c, _) = controller(Tier.FREE)
        c.onForeground(); settle()
        assertEquals(0, sdk.inits)
        assertFalse(c.ready.value)
    }

    @Test
    fun `an update failure stops the sequence and logs`() = runTest {
        consent.state = ConsentState(ok = false, privacyOptionsRequired = false)
        val (c, _) = controller(Tier.FREE)
        c.onForeground(); settle()
        assertEquals(listOf("update"), consent.calls)
        assertEquals(0, sdk.inits)
        assertFalse(c.ready.value)
        assertTrue(crash.logs.isNotEmpty())
    }

    @Test
    fun `initialize runs once across foregrounds, consent refreshes each time`() = runTest {
        val (c, _) = controller(Tier.FREE)
        c.onForeground(); settle()
        c.onForeground(); settle()
        assertEquals(1, sdk.inits)
        assertEquals(2, consent.calls.count { it == "update" })
    }

    @Test
    fun `turning PRO mid-sequence ends not ready and never initializes`() = runTest {
        val (c, ent) = controller(Tier.FREE)
        consent.onForm = { ent.tier.value = Tier.PRO }
        c.onForeground(); settle()
        assertEquals(listOf("update", "form"), consent.calls)
        assertEquals(0, sdk.inits)
        assertFalse(c.ready.value)
    }

    @Test
    fun `becoming PRO after ready drops ready`() = runTest {
        val (c, ent) = controller(Tier.FREE)
        c.onForeground(); settle()
        assertTrue(c.ready.value)
        ent.tier.value = Tier.PRO; settle()
        assertFalse(c.ready.value)
    }

    @Test
    fun `privacy options follow UMP and only on FREE`() = runTest {
        consent.state = ConsentState(ok = true, privacyOptionsRequired = true)
        val (c, ent) = controller(Tier.FREE)
        c.onForeground(); settle()
        assertTrue(c.privacyOptionsRequired.value)
        ent.tier.value = Tier.PRO; settle()
        assertFalse(c.privacyOptionsRequired.value)
    }

    @Test
    fun `nothing runs until the Pro cache is loaded`() = runTest {
        store.cacheLoaded.value = false
        val (c, ent) = controller(Tier.FREE)
        c.onForeground(); settle()
        assertTrue(consent.calls.isEmpty())
        ent.tier.value = Tier.PRO
        store.cacheLoaded.value = true; settle()
        assertTrue(consent.calls.isEmpty())
        assertEquals(0, sdk.inits)
    }

    @Test
    fun `an initialize that throws neither crashes nor becomes ready, and is logged`() = runTest {
        sdk.failWith = IllegalStateException("sdk boom")
        val (c, _) = controller(Tier.FREE)
        c.onForeground(); settle()
        assertFalse(c.ready.value)
        assertTrue(crash.logs.isNotEmpty())
        assertTrue("logs the type, never the message", crash.logs.none { "sdk boom" in it })
        // Only this fake throws, to exercise the controller's guard. The real MobileAdsSdk never throws: it logs and
        // returns, so the controller marks it initialized and ready, and banners just fail to load. Here, where it
        // did throw, nothing was marked initialized, so a later foreground calls initialize again.
        sdk.failWith = null
        c.onForeground(); settle()
        assertEquals(1, sdk.inits)
        assertTrue(c.ready.value)
    }

    @Test
    fun `consent withdrawn after ready drops ready`() = runTest {
        val (c, _) = controller(Tier.FREE)
        c.onForeground(); settle()
        assertTrue(c.ready.value)
        consent.can = false
        c.onForeground(); settle()
        assertFalse(c.ready.value)
    }

    @Test
    fun `showPrivacyOptions uses the current activity, and is a no-op without one`() = runTest {
        val (c, _) = controller(Tier.FREE)
        c.showPrivacyOptions()
        assertEquals("privacy", consent.calls.last())
        val n = consent.calls.size
        current = null
        c.showPrivacyOptions()
        assertEquals(n, consent.calls.size)
    }
}
