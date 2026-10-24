package com.mitenko.repkit

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.repkit.ads.AdsController
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.monetisation.ProStore
import com.mitenko.repkit.ui.ads.AdRenderer
import com.mitenko.repkit.ui.ads.LocalAdRenderer
import com.mitenko.repkit.ui.ads.LocalTier
import com.mitenko.repkit.ui.common.ProMessageHost
import com.mitenko.repkit.ui.navigation.HiitNavHost
import com.mitenko.repkit.ui.theme.AppTheme
import com.mitenko.repkit.ui.theme.HiitTheme
import com.mitenko.repkit.ui.theme.ThemeMode
import com.mitenko.repkit.ui.theme.edgeGlow
import com.mitenko.repkit.ui.theme.effectiveTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var controller: TimerController
    @Inject lateinit var preferences: AppPreferences
    @Inject lateinit var entitlements: Entitlements
    @Inject lateinit var adRenderer: AdRenderer
    @Inject lateinit var proStore: ProStore
    @Inject lateinit var adsController: AdsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Spec rev 14 §4, rev 36 §2: the Appearance choice (default SYSTEM) decides the theme drawn.
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            // Spec revision 18 §4: the ad slots read the tier and renderer from here. v1: PRO, no renderer.
            val tier by entitlements.tier.collectAsStateWithLifecycle()
            val appTheme = effectiveTheme(themeMode, isSystemInDarkTheme(), tier)

            // Status/navigation bar icons follow the theme drawn too - not just the phone's own night
            // mode - since a manual choice can disagree with it. Every glowing theme is dark (rev 36 §5).
            LaunchedEffect(appTheme) {
                val style = if (appTheme != AppTheme.LIGHT) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            HiitTheme(appTheme) {
                // A glowing theme's faint glow along the screen edge (rev 36 §4); nothing in Light and Dark.
                Surface(Modifier.fillMaxSize().edgeGlow(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalTier provides tier, LocalAdRenderer provides adRenderer) {
                        Box(Modifier.safeDrawingPadding()) {
                            HiitNavHost(controller)
                            // Spec rev 41 §3: billing outcomes show over whichever screen is current.
                            ProMessageHost(proStore.events, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Spec rev 41 §1.4: ask Play again on every return to the foreground (a refund or a restore elsewhere).
        proStore.refresh()
        // Spec rev 41 §2.2: (re)run consent and the SDK start on FREE; PRO does nothing.
        adsController.onForeground()
    }
}
