package com.mitenko.repkit.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mitenko.repkit.domain.Tier
import com.mitenko.repkit.domain.showAds

/**
 * Where an ad may show (spec revision 18 §4): the bottom of the entry list and of the entry screen.
 * There is deliberately no timer placement: ads never show on the timer screen.
 */
enum class AdPlacement { ENTRY_LIST, ENTRY_SCREEN }

/** Draws the ad for a placement. An ads SDK implements it later; v1's [NoAdRenderer] draws nothing. */
interface AdRenderer {
    @Composable
    fun Render(placement: AdPlacement, modifier: Modifier)
}

/** The v1 renderer (spec revision 18 §4): draws nothing, so the slot has zero size and reserves no space. */
object NoAdRenderer : AdRenderer {
    @Composable
    override fun Render(placement: AdPlacement, modifier: Modifier) = Unit
}

/** Provided in MainActivity from the injected renderer; the default is v1's [NoAdRenderer]. */
val LocalAdRenderer = staticCompositionLocalOf<AdRenderer> { NoAdRenderer }

/** Provided in MainActivity from the injected entitlements; the default is v1's PRO, so no ads. */
val LocalTier = staticCompositionLocalOf { Tier.PRO }

/**
 * An ad slot (spec revision 18 §4). It composes nothing unless [showAds] allows it for the current
 * tier, and then holds whatever [LocalAdRenderer] draws; with the v1 renderer that's nothing at all.
 */
@Composable
fun AdSlot(placement: AdPlacement, modifier: Modifier = Modifier) {
    if (!showAds(LocalTier.current)) return
    Box(modifier.testTag("ad_slot")) { LocalAdRenderer.current.Render(placement, Modifier) }
}
