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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.domain.Entitlements
import com.mitenko.repkit.domain.TimerController
import com.mitenko.repkit.ui.ads.AdRenderer
import com.mitenko.repkit.ui.ads.LocalAdRenderer
import com.mitenko.repkit.ui.ads.LocalTier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Spec rev 14 §4, rev 36 §2: the Appearance choice (default SYSTEM) decides the theme drawn.
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val appTheme = effectiveTheme(themeMode, isSystemInDarkTheme())
            // Spec revision 18 §4: the ad slots read the tier and renderer from here. v1: PRO, no renderer.
            val tier by entitlements.tier.collectAsStateWithLifecycle()

            // Status/navigation bar icons follow the theme drawn too - not just the phone's own night
            // mode - since a manual choice can disagree with it. Sonar is dark (rev 36 §5).
            LaunchedEffect(appTheme) {
                val style = if (appTheme != AppTheme.LIGHT) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            HiitTheme(appTheme) {
                // Sonar's faint glow along the screen edge (rev 36 §4); nothing in Light and Dark.
                Surface(Modifier.fillMaxSize().edgeGlow(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalTier provides tier, LocalAdRenderer provides adRenderer) {
                        Box(Modifier.safeDrawingPadding()) { HiitNavHost(controller) }
                    }
                }
            }
        }
    }
}
