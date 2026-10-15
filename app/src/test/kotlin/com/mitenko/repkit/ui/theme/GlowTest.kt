package com.mitenko.repkit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Spec rev 36 §4, rev 39 §10: the glow exists only in the glowing themes; Light and Dark get the very same modifiers and colours. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GlowTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(16.dp)

    private fun assertUntouched(theme: AppTheme) {
        var panel: Modifier? = null
        var edge: Modifier? = null
        var color = Color.Unspecified
        compose.setContent {
            HiitTheme(theme) {
                panel = Modifier.glowPanel(shape)
                edge = Modifier.edgeGlow()
                color = panelColor(Color.Red)
            }
        }
        assertSame(Modifier, panel)
        assertSame(Modifier, edge)
        assertEquals(Color.Red, color)
    }

    @Test
    fun `Light panels are untouched`() = assertUntouched(AppTheme.LIGHT)

    @Test
    fun `Dark panels are untouched`() = assertUntouched(AppTheme.DARK)

    @Test
    fun `Sonar panels glow and let the gradient show through`() {
        var panel: Modifier? = null
        var edge: Modifier? = null
        var color = Color.Unspecified
        compose.setContent {
            HiitTheme(AppTheme.SONAR) {
                panel = Modifier.glowPanel(shape)
                edge = Modifier.edgeGlow()
                color = panelColor(Color.Red)
            }
        }
        assertNotSame(Modifier, panel)
        assertNotSame(Modifier, edge)
        assertEquals(Color.Transparent, color)
    }

    @Test
    fun `panels keep their own shape unless the theme sets a corner`() {
        val shapes = mutableMapOf<AppTheme, androidx.compose.ui.graphics.Shape>()
        compose.setContent {
            listOf(AppTheme.LIGHT, AppTheme.SONAR, AppTheme.GROVE, AppTheme.BLOSSOM, AppTheme.FOUNDRY).forEach { t ->
                HiitTheme(t) { shapes[t] = panelShape(shape) }
            }
        }
        compose.waitForIdle()
        assertSame(shape, shapes[AppTheme.LIGHT])
        assertSame(shape, shapes[AppTheme.SONAR])
        assertSame(shape, shapes[AppTheme.GROVE])
        assertEquals(RoundedCornerShape(22.dp), shapes[AppTheme.BLOSSOM])
        assertEquals(RoundedCornerShape(6.dp), shapes[AppTheme.FOUNDRY])
    }

    @Test
    fun `every glowing theme glows`() {
        val panels = mutableMapOf<AppTheme, Modifier>()
        compose.setContent {
            listOf(AppTheme.BLOSSOM, AppTheme.GROVE, AppTheme.FOUNDRY).forEach { t ->
                HiitTheme(t) { panels[t] = Modifier.glowPanel(shape) }
            }
        }
        compose.waitForIdle()
        assertEquals(3, panels.size)
        panels.forEach { (t, m) -> assertNotSame("$t", Modifier, m) }
    }
}
