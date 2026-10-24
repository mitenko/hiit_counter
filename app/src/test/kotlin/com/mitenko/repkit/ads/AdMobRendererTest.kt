package com.mitenko.repkit.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.BuildConfig
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.ui.ads.AdPlacement
import com.mitenko.repkit.ui.ads.AdSlot
import com.mitenko.repkit.ui.ads.LocalAdRenderer
import com.mitenko.repkit.ui.ads.LocalTier
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AdMobRendererTest {
    @get:Rule val compose = createComposeRule()
    private val loads = mutableListOf<String>()
    private var load: (() -> Unit)? = null
    private var fail: (() -> Unit)? = null
    private val fake = BannerFactory { unitId, onLoaded, onFailed, modifier ->
        loads += unitId; load = onLoaded; fail = onFailed
        Box(modifier.size(320.dp, 50.dp).testTag("fake_banner"))
    }

    private fun slot(ready: Boolean, placement: AdPlacement = AdPlacement.ENTRY_LIST) {
        val renderer = AdMobRenderer(readyFlow = MutableStateFlow(ready), banner = fake)
        compose.setContent {
            CompositionLocalProvider(LocalTier provides Tier.FREE, LocalAdRenderer provides renderer) {
                AdSlot(placement)
            }
        }
    }

    @Test fun `not ready requests nothing and has zero height`() {
        slot(ready = false)
        assertTrue(loads.isEmpty())
        compose.onNodeWithTag("ad_slot", useUnmergedTree = true).assertHeightIsEqualTo(0.dp)
    }

    @Test fun `ready requests the list unit but stays zero height until loaded`() {
        slot(ready = true)
        assertEquals(listOf(BuildConfig.AD_UNIT_LIST), loads)
        compose.onNodeWithTag("ad_slot", useUnmergedTree = true).assertHeightIsEqualTo(0.dp)
        compose.runOnIdle { load!!() }
        compose.onNodeWithTag("ad_slot", useUnmergedTree = true).assertHeightIsEqualTo(50.dp)
    }

    @Test fun `the entry screen requests the entry unit`() {
        slot(ready = true, placement = AdPlacement.ENTRY_SCREEN)
        assertEquals(listOf(BuildConfig.AD_UNIT_ENTRY), loads)
    }

    @Test fun `a failed load stays zero height`() {
        slot(ready = true)
        compose.runOnIdle { fail!!() }
        compose.onNodeWithTag("ad_slot", useUnmergedTree = true).assertHeightIsEqualTo(0.dp)
    }

    @Test fun `a failed refresh never collapses a banner that has loaded`() {
        slot(ready = true)
        compose.runOnIdle { load!!() }
        compose.runOnIdle { fail!!() }
        compose.onNodeWithTag("ad_slot", useUnmergedTree = true).assertHeightIsEqualTo(50.dp)
    }

    @Test fun `a later visit reserves the height the placement showed before it loads`() {
        val renderer = AdMobRenderer(readyFlow = MutableStateFlow(true), banner = fake)
        val visit = mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalTier provides Tier.FREE, LocalAdRenderer provides renderer) {
                key(visit.intValue) { AdSlot(AdPlacement.ENTRY_LIST) }
            }
        }
        compose.runOnIdle { load!!() }
        compose.onNodeWithTag("ad_slot", useUnmergedTree = true).assertHeightIsEqualTo(50.dp)
        compose.runOnIdle { visit.intValue = 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("ad_slot", useUnmergedTree = true).assertHeightIsEqualTo(50.dp)
    }

    @Test fun `a placement that never loaded reserves nothing on a later visit`() {
        val renderer = AdMobRenderer(readyFlow = MutableStateFlow(true), banner = fake)
        val visit = mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalTier provides Tier.FREE, LocalAdRenderer provides renderer) {
                key(visit.intValue) { AdSlot(AdPlacement.ENTRY_LIST) }
            }
        }
        compose.runOnIdle { fail!!() }
        compose.runOnIdle { visit.intValue = 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("ad_slot", useUnmergedTree = true).assertHeightIsEqualTo(0.dp)
    }
}
