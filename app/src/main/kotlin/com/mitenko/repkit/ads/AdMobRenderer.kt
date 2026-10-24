package com.mitenko.repkit.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.mitenko.repkit.BuildConfig
import com.mitenko.repkit.ui.ads.AdPlacement
import com.mitenko.repkit.ui.ads.AdRenderer
import kotlinx.coroutines.flow.StateFlow

/** One AdView per screen (spec rev 41 §2.1): an anchored adaptive banner sized to the slot's width. */
fun interface BannerFactory {
    @Composable
    fun Banner(unitId: String, onLoaded: () -> Unit, onFailed: () -> Unit, modifier: Modifier)
}

object RealBanner : BannerFactory {
    @Composable
    override fun Banner(unitId: String, onLoaded: () -> Unit, onFailed: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val loaded by rememberUpdatedState(onLoaded)
        val failed by rememberUpdatedState(onFailed)
        BoxWithConstraints(modifier) {
            val widthDp = maxWidth.value.toInt()
            val view = remember(unitId, widthDp) {
                AdView(context).apply {
                    adUnitId = unitId
                    // play-services-ads 25 deprecates every standard-height anchored size in favour of the "large"
                    // ones; spec rev 41 §2.1 keeps the standard anchored banner.
                    @Suppress("DEPRECATION")
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                    adListener = object : AdListener() {
                        override fun onAdLoaded() = loaded()
                        override fun onAdFailedToLoad(error: LoadAdError) = failed()
                    }
                }
            }
            LaunchedEffect(view) { view.loadAd(AdRequest.Builder().build()) }
            DisposableEffect(view) { onDispose { view.destroy() } }
            key(view) { AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

/**
 * Spec rev 41 §2.1–§2.2: draws nothing until [readyFlow] (consent and initialize done), then the banner. A placement
 * that has never shown a banner stays at zero height until one loads, so a failure leaves no gap; once one has
 * shown, a failed refresh never collapses it, and later visits reserve its last height before the new one loads.
 * A @Singleton (MonetisationModule), so those heights last for the process.
 */
class AdMobRenderer(private val readyFlow: StateFlow<Boolean>, private val banner: BannerFactory = RealBanner) : AdRenderer {
    private val shownHeights = mutableStateMapOf<AdPlacement, Dp>()

    @Composable
    override fun Render(placement: AdPlacement, modifier: Modifier) {
        val ready by readyFlow.collectAsStateWithLifecycle()
        if (!ready) return
        var loaded by remember(placement) { mutableStateOf(false) }
        val density = LocalDensity.current
        val unit = when (placement) {
            AdPlacement.ENTRY_LIST -> BuildConfig.AD_UNIT_LIST
            AdPlacement.ENTRY_SCREEN -> BuildConfig.AD_UNIT_ENTRY
        }
        // The map is read only before loading, so recording a loaded banner's height doesn't recompose this slot.
        val height = if (loaded) {
            Modifier.wrapContentHeight().onSizeChanged {
                if (it.height > 0) shownHeights[placement] = with(density) { it.height.toDp() }
            }
        } else {
            Modifier.height(shownHeights[placement] ?: 0.dp)
        }
        banner.Banner(
            unitId = unit,
            onLoaded = { loaded = true },
            // A failure keeps whatever is showing: zero for a banner that never loaded, the old banner otherwise.
            onFailed = {},
            modifier = modifier.fillMaxWidth().then(height),
        )
    }
}
