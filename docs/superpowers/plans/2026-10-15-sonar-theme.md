# Sonar Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fourth Appearance option, Sonar: a glowing dark theme that restyles every screen, the timer included.

**Architecture:** `ThemeMode` gains `SONAR`; a pure `effectiveTheme()` resolves the stored mode into an `AppTheme` (LIGHT, DARK, SONAR). `HiitTheme(theme: AppTheme)` provides the Material `ColorScheme` plus three composition locals: `LocalAppTheme`, `LocalGlow` (Sonar's glow tokens, null in Light/Dark) and `LocalTimerColors`. Glow is drawn by small modifiers (`glowPanel`, `edgeGlow`) that do nothing when `LocalGlow` is null, so Light and Dark are untouched. The timer reads `LocalTimerColors` instead of `HiitColors` / black / white.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Robolectric (sdk 34) for Compose tests, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-10-colour-themes-design.md` (revision 36, revised 2026-10-15: Sonar only). Also read `docs/superpowers/specs/2026-10-01-light-dark-design.md` (rev 14) and `docs/superpowers/specs/2026-10-15-timer-elapsed-band-design.md` (rev 38).

## Rulings (spec amendments this plan makes; Task 6 writes them into the spec)

1. `ThemeMode` / `effectiveTheme` stay in `ui/theme/ThemeMode.kt`, where `ThemeMode` already lives, not `domain/Themes.kt`. The functions are pure Kotlin all the same.
2. Sonar's timer prepare / cooldown colour moves from `#5FA9C9` to **`#6A9FE0`**: `#5FA9C9` is 28° of hue from the work colour `#2EE6C8`, under the spec's own 30° rule (§6).
3. Chips and buttons take Sonar through the `ColorScheme` only (accent, outline); no per-button glow. There are about 90 button / chip call sites, and per-site glow would touch most of the UI for a small effect.
4. The entry chart and calendar get no panel: they have no container in Light or Dark, and adding one only in Sonar would change the layout. Panels that exist (list tiles, settings cards, the entry settings action cards, the prescription card) glow.

## Global Constraints

- Light and Dark must look exactly as today: every existing test stays green, unchanged.
- The timer stays dark in Light and Dark (rev 14 §3) and uses Sonar's own colours in Sonar.
- `domain/` stays free of Android imports; `ui/theme/ThemeMode.kt` stays pure Kotlin (no Compose).
- All user-facing text from resources; `HardCodedTextGuardTest` must pass (test tags go through `testTag`).
- New string: `<string name="theme_sonar" translatable="false">Sonar</string>` in `values/strings.xml` only.
- Blur (`BlurMaskFilter`, coloured shadows) only on API 28+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.P`); API 26–27 get outlines and gradients only. minSdk is 26.
- Build / test only through rgradle, from the worktree root (Git Bash):
  `pwsh -NoProfile -Command "\$env:RGRADLE_WORKERS='miten@192.168.1.86,miten@192.168.1.89'; & D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests '*Theme*'"`
  Exit 2 = no worker: stop and report, never run `./gradlew` locally.
- Commits as `mitenko <mitenko@gmail.com>`, no AI attribution lines.
- Sonar palette (after ruling 2):

  | Token | Value |
  | --- | --- |
  | ground | `#071014` |
  | panel | `#0F292E` |
  | panel centre | `#123B3D` |
  | outline | `#23A492` |
  | accent | `#2EE6C8` |
  | on accent | `#04201C` |
  | text | `#E8F4F2` |
  | secondary text | `#8FB3AE` |
  | error | `#F0605D` |
  | timer work | `#2EE6C8` |
  | timer rest | `#F08A6B` |
  | timer prepare / cooldown | `#6A9FE0` |
  | timer set ring | `#22D3EE` |
  | timer track | `#12302F` |

## Review Focus

1. **A stored "SONAR" read by an older build** (downgrade): `ThemeMode.valueOf` fails and must fall back to SYSTEM. Covered by the existing GARBAGE test; Task 1 adds a SONAR round-trip so the new name is proven to be stored by name.
2. **The timer opened from a Light app** must still be dark (rev 14), and from Sonar must be Sonar, not Dark. Task 1 pins `timerTheme()`; Task 5 renders the timer in all three.
3. **System bar icons in Sonar** must be light (dark bars), or the status bar is unreadable on `#071014`. Task 4 routes the bars through `AppTheme`, with `isDark` covering SONAR (Task 1 test).
4. **Panel text contrast**: Sonar's panels become a gradient under transparent surfaces; text must stay readable on both gradient ends. Task 2's contrast test checks text and secondary text against the panel *and* the panel centre.
5. **Blur on old APIs / Robolectric**: the ring glow and shadows must not crash where blur is unsupported. Task 5 renders the timer in Sonar under Robolectric; the API gate is in code review.

---

### Task 1: Theme model (`SONAR`, `AppTheme`, `effectiveTheme`, `timerTheme`)

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/theme/ThemeMode.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/theme/ThemeModeTest.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/data/AppPreferencesTest.kt`

**Interfaces:**
- Produces: `enum class ThemeMode { SYSTEM, LIGHT, DARK, SONAR }`; `enum class AppTheme { LIGHT, DARK, SONAR }`; `fun effectiveTheme(mode: ThemeMode, systemDark: Boolean): AppTheme`; `fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean` (kept, now true for SONAR); `fun timerTheme(theme: AppTheme): AppTheme`.

- [ ] **Step 1: Write the failing tests.** Append to `ThemeModeTest`:

```kotlin
    @Test
    fun `SONAR is dark whatever the phone says`() {
        assertTrue(isDark(ThemeMode.SONAR, systemDark = true))
        assertTrue(isDark(ThemeMode.SONAR, systemDark = false))
    }

    @Test
    fun `effectiveTheme resolves SYSTEM against the phone and keeps the rest`() {
        assertEquals(AppTheme.DARK, effectiveTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(AppTheme.LIGHT, effectiveTheme(ThemeMode.SYSTEM, systemDark = false))
        listOf(true, false).forEach { systemDark ->
            assertEquals(AppTheme.LIGHT, effectiveTheme(ThemeMode.LIGHT, systemDark))
            assertEquals(AppTheme.DARK, effectiveTheme(ThemeMode.DARK, systemDark))
            assertEquals(AppTheme.SONAR, effectiveTheme(ThemeMode.SONAR, systemDark))
        }
    }

    @Test
    fun `the timer is dark in Light and Dark and Sonar in Sonar`() {
        assertEquals(AppTheme.DARK, timerTheme(AppTheme.LIGHT))
        assertEquals(AppTheme.DARK, timerTheme(AppTheme.DARK))
        assertEquals(AppTheme.SONAR, timerTheme(AppTheme.SONAR))
    }
```

Add `import org.junit.Assert.assertEquals`. Append to `AppPreferencesTest` (next to `setThemeMode round trips`):

```kotlin
    @Test
    fun `setThemeMode round trips SONAR`() = runTest {
        val prefs = AppPreferences(store())
        prefs.setThemeMode(ThemeMode.SONAR)
        assertEquals(ThemeMode.SONAR, prefs.themeMode.first())
    }
```

- [ ] **Step 2: Run to verify they fail.** rgradle `testDebugUnitTest --tests '*ThemeModeTest*' --tests '*AppPreferencesTest*'`. Expected: compile errors (`SONAR`, `AppTheme`, `effectiveTheme`, `timerTheme` unresolved).

- [ ] **Step 3: Implement.** Replace `ThemeMode.kt` with:

```kotlin
package com.mitenko.repkit.ui.theme

/** The user's Appearance choice (spec rev 14 §1, rev 36 §2): SYSTEM follows the phone; the rest override it. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, SONAR }

/** The theme actually drawn (spec rev 36 §2): [ThemeMode] with SYSTEM resolved against the phone. */
enum class AppTheme { LIGHT, DARK, SONAR }

/** Pure: the theme drawn for the user's [mode], given the phone's [systemDark] setting. */
fun effectiveTheme(mode: ThemeMode, systemDark: Boolean): AppTheme = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
    ThemeMode.LIGHT -> AppTheme.LIGHT
    ThemeMode.DARK -> AppTheme.DARK
    ThemeMode.SONAR -> AppTheme.SONAR
}

/** Pure: whether the effective theme is dark (Sonar is a dark theme). */
fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = effectiveTheme(mode, systemDark) != AppTheme.LIGHT

/** The timer stays dark in Light and Dark (spec rev 14 §3) and takes Sonar's own colours in Sonar (rev 36 §3). */
fun timerTheme(theme: AppTheme): AppTheme = if (theme == AppTheme.SONAR) AppTheme.SONAR else AppTheme.DARK
```

- [ ] **Step 4: Run to verify they pass.** Same command. Expected: all pass (`AppPreferences` needs no change: it stores `mode.name` and reads `ThemeMode.valueOf`).

- [ ] **Step 5: Commit.** `git add` the three files; `git commit -m "Theme model: SONAR, AppTheme, effectiveTheme, timerTheme"`.

---

### Task 2: Sonar palette, `HiitTheme(theme)`, composition locals

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/ui/theme/Sonar.kt`
- Create: `app/src/main/kotlin/com/mitenko/repkit/ui/theme/TimerColors.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/theme/Theme.kt` (the `HiitTheme` function at the end)
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/theme/SonarPaletteTest.kt` (create, pure JUnit)
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/theme/HiitThemeTest.kt` (append)

**Interfaces:**
- Consumes: `AppTheme` (Task 1).
- Produces:
  - `object SonarColors { Ground, Panel, PanelCentre, Outline, Accent, OnAccent, Text, SecondaryText, Error, Work, Rest, Prepare, SetRing, Track }` (all `Color`);
  - `@Immutable data class GlowTokens(val panel: Color, val panelCentre: Color, val outline: Color, val accent: Color)`; `val SonarGlow: GlowTokens`;
  - `@Immutable data class TimerColors(val ground: Color, val text: Color, val work: Color, val rest: Color, val neutral: Color, val setRing: Color, val track: Color, val bandLine: Color, val circleOutline: Color)`; `val DarkTimerColors`, `val SonarTimerColors`;
  - `val LocalAppTheme: ProvidableCompositionLocal<AppTheme>` (default `AppTheme.LIGHT`), `val LocalGlow: ProvidableCompositionLocal<GlowTokens?>` (default null), `val LocalTimerColors: ProvidableCompositionLocal<TimerColors>` (default `DarkTimerColors`);
  - `@Composable fun HiitTheme(theme: AppTheme, content: @Composable () -> Unit)`; the existing `HiitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content)` stays and delegates.

- [ ] **Step 1: Write the failing tests.** Create `SonarPaletteTest.kt`:

```kotlin
package com.mitenko.repkit.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec rev 36 §6: Sonar's colours pass WCAG contrast and the timer phases are told apart by hue. */
class SonarPaletteTest {
    private fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
    private fun hue(c: Color): Double {
        val r = c.red.toDouble(); val g = c.green.toDouble(); val b = c.blue.toDouble()
        val mx = maxOf(r, g, b); val d = mx - minOf(r, g, b)
        if (d == 0.0) return 0.0
        val h = when (mx) { r -> ((g - b) / d).mod(6.0); g -> (b - r) / d + 2; else -> (r - g) / d + 4 }
        return h * 60
    }
    private fun hueGap(a: Color, b: Color) = abs(hue(a) - hue(b)).let { min(it, 360 - it) }

    private fun assertContrast(fg: Color, bg: Color, atLeast: Double, what: String) {
        val ratio = contrast(fg, bg)
        assertTrue("$what: $ratio < $atLeast", ratio >= atLeast)
    }

    @Test
    fun `text reads on the ground and on both ends of a panel`() {
        listOf(SonarColors.Ground, SonarColors.Panel, SonarColors.PanelCentre).forEach { bg ->
            assertContrast(SonarColors.Text, bg, 4.5, "text on $bg")
            assertContrast(SonarColors.SecondaryText, bg, 4.5, "secondary text on $bg")
        }
        assertContrast(SonarColors.OnAccent, SonarColors.Accent, 4.5, "on-accent on accent")
    }

    @Test
    fun `the timer phases stand out from the ground and from each other`() {
        val phases = listOf(SonarColors.Work, SonarColors.Rest, SonarColors.Prepare)
        phases.forEach { assertContrast(it, SonarColors.Ground, 3.0, "phase $it on ground") }
        for (i in phases.indices) for (j in i + 1 until phases.size) {
            val gap = hueGap(phases[i], phases[j])
            assertTrue("hue gap ${phases[i]} / ${phases[j]} = $gap", gap >= 30.0)
        }
    }
}
```

Append to `HiitThemeTest` (add `import org.junit.Assert.assertNull`):

```kotlin
    @Test
    fun `Sonar provides its ground, glow and timer colours`() {
        var background by mutableStateOf(Color.Unspecified)
        var glow: GlowTokens? = null
        var timer: TimerColors? = null
        var theme: AppTheme? = null
        compose.setContent {
            HiitTheme(AppTheme.SONAR) {
                background = MaterialTheme.colorScheme.background
                glow = LocalGlow.current
                timer = LocalTimerColors.current
                theme = LocalAppTheme.current
            }
        }
        assertEquals(Color(0xFF071014), background)
        assertEquals(SonarGlow, glow)
        assertEquals(SonarTimerColors, timer)
        assertEquals(AppTheme.SONAR, theme)
    }

    @Test
    fun `Dark has no glow and keeps the HiitColors timer`() {
        var glow: GlowTokens? = SonarGlow
        var timer: TimerColors? = null
        compose.setContent {
            HiitTheme(darkTheme = true) {
                glow = LocalGlow.current
                timer = LocalTimerColors.current
            }
        }
        assertNull(glow)
        assertEquals(DarkTimerColors, timer)
        assertEquals(HiitColors.Work, DarkTimerColors.work)
        assertEquals(Color.Black, DarkTimerColors.ground)
    }
```

- [ ] **Step 2: Run to verify they fail.** rgradle `testDebugUnitTest --tests '*SonarPaletteTest*' --tests '*HiitThemeTest*'`. Expected: compile errors (unresolved `SonarColors`, `HiitTheme(AppTheme)`, …).

- [ ] **Step 3: Implement.** Create `Sonar.kt`:

```kotlin
package com.mitenko.repkit.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Sonar's palette (spec rev 36 §3.1; prepare moved to #6A9FE0 for the 30° hue rule, plan ruling 2). */
object SonarColors {
    val Ground = Color(0xFF071014)
    val Panel = Color(0xFF0F292E)
    val PanelCentre = Color(0xFF123B3D)
    val Outline = Color(0xFF23A492)
    val Accent = Color(0xFF2EE6C8)
    val OnAccent = Color(0xFF04201C)
    val Text = Color(0xFFE8F4F2)
    val SecondaryText = Color(0xFF8FB3AE)
    val Error = Color(0xFFF0605D)
    val Work = Color(0xFF2EE6C8)
    val Rest = Color(0xFFF08A6B)
    val Prepare = Color(0xFF6A9FE0)
    val SetRing = Color(0xFF22D3EE)
    val Track = Color(0xFF12302F)
}

/** What the glow modifiers draw with (spec rev 36 §4); provided only in Sonar. */
@Immutable
data class GlowTokens(val panel: Color, val panelCentre: Color, val outline: Color, val accent: Color)

val SonarGlow = GlowTokens(
    panel = SonarColors.Panel, panelCentre = SonarColors.PanelCentre, outline = SonarColors.Outline, accent = SonarColors.Accent,
)

/** Sonar as a Material scheme: chips, buttons, switches and dialogs take it from here (plan ruling 3). */
internal val SonarScheme = darkColorScheme(
    primary = SonarColors.Accent,
    onPrimary = SonarColors.OnAccent,
    primaryContainer = SonarColors.PanelCentre,
    onPrimaryContainer = SonarColors.Text,
    secondary = SonarColors.SetRing,
    onSecondary = SonarColors.OnAccent,
    secondaryContainer = SonarColors.PanelCentre,
    onSecondaryContainer = SonarColors.Text,
    background = SonarColors.Ground,
    onBackground = SonarColors.Text,
    surface = SonarColors.Ground,
    onSurface = SonarColors.Text,
    surfaceVariant = SonarColors.Panel,
    onSurfaceVariant = SonarColors.SecondaryText,
    surfaceContainer = SonarColors.Panel,
    surfaceContainerHigh = SonarColors.Panel,
    surfaceContainerHighest = SonarColors.PanelCentre,
    outline = SonarColors.Outline,
    outlineVariant = SonarColors.Outline.copy(alpha = 0.5f),
    error = SonarColors.Error,
)
```

Create `TimerColors.kt`:

```kotlin
package com.mitenko.repkit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The timer screen's colours (spec rev 36 §3, §3.2): today's black / white / [HiitColors] in Light and
 * Dark (rev 14 §3), Sonar's own in Sonar. [bandLine] and [circleOutline] are the elapsed band's grey
 * lines ahead of the fill and the Sets circle's outline (rev 38).
 */
@Immutable
data class TimerColors(
    val ground: Color,
    val text: Color,
    val work: Color,
    val rest: Color,
    val neutral: Color,
    val setRing: Color,
    val track: Color,
    val bandLine: Color,
    val circleOutline: Color,
)

val DarkTimerColors = TimerColors(
    ground = Color.Black, text = Color.White, work = HiitColors.Work, rest = HiitColors.Rest, neutral = HiitColors.Neutral,
    setRing = HiitColors.SetRing, track = HiitColors.Track,
    bandLine = HiitColors.Neutral.copy(alpha = 0.53f), circleOutline = HiitColors.Neutral.copy(alpha = 0.6f),
)

val SonarTimerColors = TimerColors(
    ground = SonarColors.Ground, text = SonarColors.Text, work = SonarColors.Work, rest = SonarColors.Rest,
    neutral = SonarColors.Prepare, setRing = SonarColors.SetRing, track = SonarColors.Track,
    bandLine = SonarColors.Outline.copy(alpha = 0.53f), circleOutline = SonarColors.Outline.copy(alpha = 0.6f),
)
```

In `Theme.kt`, replace the `HiitTheme` function (and its KDoc) with the following, and add imports `androidx.compose.runtime.CompositionLocalProvider` and `androidx.compose.runtime.staticCompositionLocalOf`:

```kotlin
/** The theme being drawn (spec rev 36): the timer reads it to pick its own theme ([timerTheme]). */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.LIGHT }

/** Sonar's glow tokens; null in Light and Dark, so the glow modifiers do nothing there (spec rev 36 §4). */
val LocalGlow = staticCompositionLocalOf<GlowTokens?> { null }

/** The timer screen's colours (spec rev 36 §3). */
val LocalTimerColors = staticCompositionLocalOf { DarkTimerColors }

/** Spec rev 14 §1: [darkTheme] is driven by the user's Appearance choice, defaulting to the phone's setting. */
@Composable
fun HiitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) =
    HiitTheme(if (darkTheme) AppTheme.DARK else AppTheme.LIGHT, content)

/** Spec rev 36 §3: the scheme, the glow and the timer colours for [theme]. */
@Composable
fun HiitTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        AppTheme.LIGHT -> LightScheme
        AppTheme.DARK -> DarkScheme
        AppTheme.SONAR -> SonarScheme
    }
    CompositionLocalProvider(
        LocalAppTheme provides theme,
        LocalGlow provides if (theme == AppTheme.SONAR) SonarGlow else null,
        LocalTimerColors provides if (theme == AppTheme.SONAR) SonarTimerColors else DarkTimerColors,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
```

- [ ] **Step 4: Run to verify they pass.** Same command, then the whole theme package: `--tests '*ui.theme*'`. Expected: all pass, including the two existing `HiitThemeTest` tests unchanged.

- [ ] **Step 5: Commit.** `git commit -m "Sonar palette, HiitTheme(AppTheme), glow and timer colour locals"`.

---

### Task 3: Glow modifiers on the panels

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/ui/theme/Glow.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/common/SettingsComponents.kt:111-113` (`SettingsCard`)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/entries/EntryListScreen.kt:440-445` (the tile `Surface`)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/settings/EntrySettings.kt:398-406` (the action card `Surface`)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/entry/EntryScreen.kt` (`PrescriptionCard`, the `.background(MaterialTheme.colorScheme.surfaceVariant, shape)` line)
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/theme/GlowTest.kt` (create, Robolectric)

**Interfaces:**
- Consumes: `LocalGlow`, `GlowTokens`, `HiitTheme(AppTheme)` (Task 2).
- Produces: `@Composable fun Modifier.glowPanel(shape: Shape): Modifier`; `@Composable fun panelColor(default: Color): Color`; `@Composable fun Modifier.edgeGlow(): Modifier` (used by Task 4).

- [ ] **Step 1: Write the failing test.** Create `GlowTest.kt`:

```kotlin
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

/** Spec rev 36 §4: the glow exists only in Sonar; Light and Dark get the very same modifiers and colours. */
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
}
```


- [ ] **Step 2: Run to verify it fails.** rgradle `testDebugUnitTest --tests '*GlowTest*'`. Expected: compile error (`glowPanel` unresolved).

- [ ] **Step 3: Implement `Glow.kt`.**

```kotlin
package com.mitenko.repkit.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A glowing panel in Sonar (spec rev 36 §4): a soft accent glow outside (coloured shadows, API 28+), a
 * radial gradient from the panel centre to the panel colour, and a 1.5 dp outline. Returns the receiver
 * unchanged in Light and Dark. Pair it with [panelColor] for the panel's own fill.
 */
@Composable
fun Modifier.glowPanel(shape: Shape): Modifier {
    val glow = LocalGlow.current ?: return this
    val halo = glow.accent.copy(alpha = 0.35f)
    val shadow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Modifier.shadow(12.dp, shape, clip = false, ambientColor = halo, spotColor = halo)
    } else {
        Modifier
    }
    return this
        .then(shadow)
        .background(Brush.radialGradient(listOf(glow.panelCentre, glow.panel)), shape)
        .border(1.5.dp, glow.outline, shape)
}

/** A panel's own fill: [default] in Light and Dark; transparent in Sonar, so [glowPanel]'s gradient shows. */
@Composable
fun panelColor(default: Color): Color = if (LocalGlow.current == null) default else Color.Transparent

/** Sonar's faint inner glow along the screen edge (spec rev 36 §4): the accent at 12% fading in over 24 dp. */
@Composable
fun Modifier.edgeGlow(): Modifier {
    val glow = LocalGlow.current ?: return this
    val edge = glow.accent.copy(alpha = 0.12f)
    return drawWithContent {
        drawContent()
        val w = 24.dp.toPx()
        val clear = Color.Transparent
        drawRect(Brush.verticalGradient(listOf(edge, clear), startY = 0f, endY = w), size = Size(size.width, w))
        drawRect(
            Brush.verticalGradient(listOf(clear, edge), startY = size.height - w, endY = size.height),
            topLeft = Offset(0f, size.height - w), size = Size(size.width, w),
        )
        drawRect(Brush.horizontalGradient(listOf(edge, clear), startX = 0f, endX = w), size = Size(w, size.height))
        drawRect(
            Brush.horizontalGradient(listOf(clear, edge), startX = size.width - w, endX = size.width),
            topLeft = Offset(size.width - w, 0f), size = Size(w, size.height),
        )
    }
}
```

- [ ] **Step 4: Apply to the panels.** Each needs `import com.mitenko.repkit.ui.theme.glowPanel` and `import com.mitenko.repkit.ui.theme.panelColor`.
  - `SettingsCard` becomes:
    ```kotlin
    fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        Surface(
            modifier.fillMaxWidth().glowPanel(SettingsCardShape),
            shape = SettingsCardShape,
            color = panelColor(MaterialTheme.colorScheme.surfaceContainer),
            content = content,
        )
    }
    ```
  - The list tile: `color = panelColor(if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)` and `modifier = Modifier.fillMaxWidth().glowPanel(TileShape)`.
  - The entry settings action card: `color = if (danger) colors.errorContainer else panelColor(colors.surfaceContainer)` and `modifier = Modifier.fillMaxWidth().then(if (danger) Modifier else Modifier.glowPanel(SettingsCardShape)).heightIn(min = 56.dp).testTag(tag)` (Delete keeps its error colour, no glow).
  - `PrescriptionCard`: replace `.background(MaterialTheme.colorScheme.surfaceVariant, shape)` with `.glowPanel(shape).background(panelColor(MaterialTheme.colorScheme.surfaceVariant), shape)` (keep it after `.scale(...)`, so the glow scales with the card's pop).

- [ ] **Step 5: Run to verify.** rgradle `testDebugUnitTest --tests '*GlowTest*' --tests '*Settings*' --tests '*EntryListScreenTest*' --tests '*EntryScreenTest*'`. Expected: all pass (Light/Dark unchanged: the modifiers return the receiver and `panelColor` returns the default).

- [ ] **Step 6: Commit.** `git commit -m "Glow modifiers on the panels (Sonar only)"`.

---

### Task 4: Appearance option, swatch, app-level theme and edge glow

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (after `theme_dark`)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/entries/EntryListScreen.kt` (`SettingsDialog`, `ThemeOptionRow`)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/MainActivity.kt:43-62`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/entries/EntryListScreenTest.kt` (append)

**Interfaces:**
- Consumes: `ThemeMode.SONAR`, `AppTheme`, `effectiveTheme` (Task 1); `HiitTheme(AppTheme)`, `SonarColors` (Task 2); `Modifier.edgeGlow()` (Task 3).

- [ ] **Step 1: Write the failing tests.** Append to `EntryListScreenTest`:

```kotlin
    @Test
    fun `the Settings dialog offers Sonar with its swatch`() {
        show(EntryListUiState.Items(rows), themeMode = ThemeMode.SONAR)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Sonar").assertExists()
        compose.onNodeWithTag("theme_SONAR").assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("theme_swatch_SONAR", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("theme_swatch_DARK", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `choosing Sonar calls the setter`() {
        var chosen: ThemeMode? = null
        show(EntryListUiState.Items(rows), themeMode = ThemeMode.SYSTEM, onSetThemeMode = { chosen = it })
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("theme_SONAR").performClick()
        assertEquals(ThemeMode.SONAR, chosen)
    }
```

- [ ] **Step 2: Run to verify they fail.** rgradle `testDebugUnitTest --tests '*EntryListScreenTest*'`. Expected: the two new tests fail (no "Sonar" row).

- [ ] **Step 3: Implement.**
  - `strings.xml`, after `theme_dark`: `<string name="theme_sonar" translatable="false">Sonar</string>` (a product name, the same in every language; spec rev 36 §7).
  - `SettingsDialog`: add `ThemeOptionRow(ThemeMode.SONAR, R.string.theme_sonar, current, onSelect)` after the DARK row.
  - `ThemeOptionRow`: after the label `Text`, add the swatch for themes that have one:
    ```kotlin
            if (mode == ThemeMode.SONAR) {
                Spacer(Modifier.weight(1f))
                ThemeSwatch(listOf(SonarColors.Ground, SonarColors.Accent, SonarColors.Rest), Modifier.testTag("theme_swatch_${mode.name}"))
            }
    ```
    and add:
    ```kotlin
    /** Three dots for a theme's ground, accent and rest colours (spec rev 36 §2); decorative, so no semantics. */
    @Composable
    private fun ThemeSwatch(colors: List<Color>, modifier: Modifier = Modifier) {
        Row(modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            colors.forEach { c ->
                Box(Modifier.size(12.dp).background(c, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
        }
    }
    ```
    (add any missing imports: `Spacer`, `size`, `background`, `border`, `CircleShape`, `Color`, `Arrangement`, `SonarColors`).
  - `MainActivity.onCreate` `setContent` block becomes:
    ```kotlin
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
    ```
    Replace the `isDark` import with `AppTheme`, `effectiveTheme` and `edgeGlow` imports.

- [ ] **Step 4: Run to verify they pass.** rgradle `testDebugUnitTest --tests '*EntryListScreenTest*' --tests '*HardCodedTextGuardTest*'`. Expected: all pass.

- [ ] **Step 5: Commit.** `git commit -m "Appearance: Sonar option with swatch; app draws the effective theme with an edge glow"`.

---

### Task 5: The timer in Sonar

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/timer/TimerScreen.kt` (the lines listed in Step 3)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/timer/DualRing.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/timer/TimerScreenTest.kt` (append)

**Interfaces:**
- Consumes: `LocalAppTheme`, `LocalTimerColors`, `LocalGlow`, `TimerColors` (Task 2); `timerTheme` (Task 1).
- Produces: `fun DualRing(inner: Float, outer: Float, innerColor: Color, outerColor: Color, trackColor: Color, glow: Boolean, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Write the failing tests.** Append to `TimerScreenTest` (add imports `com.mitenko.repkit.ui.theme.AppTheme`, `com.mitenko.repkit.ui.theme.LocalTimerColors`, `com.mitenko.repkit.ui.theme.SonarTimerColors`, `com.mitenko.repkit.ui.theme.DarkTimerColors`, `com.mitenko.repkit.ui.theme.TimerColors`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`):

```kotlin
    private fun timerIn(theme: AppTheme) {
        compose.setContent {
            HiitTheme(theme) {
                TimerScreen(
                    ui(Phase.REST), CueConfig(),
                    onTogglePause = {}, onSkipBack = {}, onSkipForward = {}, onClose = {},
                    onToggleSound = {}, onToggleVibration = {}, onToggleVoice = {},
                )
            }
        }
    }

    @Test
    fun `the timer renders in Sonar with its ring, band and controls`() {
        // Spec rev 36 §3: Sonar's own timer, ring glow included; must not crash where blur is drawn.
        timerIn(AppTheme.SONAR)
        compose.onNodeWithTag("ring", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("stat_band", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pause").assertIsDisplayed()
    }

    @Test
    fun `the timer renders in Light, staying dark`() {
        timerIn(AppTheme.LIGHT)
        compose.onNodeWithTag("pause").assertIsDisplayed()
    }

    @Test
    fun `the timer's colours follow timerTheme`() {
        var sonar: TimerColors? = null
        var light: TimerColors? = null
        compose.setContent {
            HiitTheme(AppTheme.SONAR) { TimerColorsProbe { sonar = it } }
            HiitTheme(AppTheme.LIGHT) { TimerColorsProbe { light = it } }
        }
        assertEquals(SonarTimerColors, sonar)
        assertEquals(DarkTimerColors, light)
    }
```

and, in the same test file, outside the class:

```kotlin
/** Reads the timer colours the way TimerScreen does: inside the timer's own theme. */
@androidx.compose.runtime.Composable
private fun TimerColorsProbe(onColors: (TimerColors) -> Unit) = TimerTheme { onColors(LocalTimerColors.current) }
```

- [ ] **Step 2: Run to verify they fail.** rgradle `testDebugUnitTest --tests '*TimerScreenTest*'`. Expected: compile error (`TimerTheme` unresolved).

- [ ] **Step 3: Implement.**
  - In `TimerScreen.kt`, add above `TimerScreen`:
    ```kotlin
    /** The timer's own theme (spec rev 14 §3, rev 36 §3): dark from Light or Dark, Sonar from Sonar. */
    @Composable
    internal fun TimerTheme(content: @Composable () -> Unit) = HiitTheme(timerTheme(LocalAppTheme.current), content)
    ```
  - `TimerScreen(...) = HiitTheme(darkTheme = true) {` → `TimerScreen(...) = TimerTheme {`, and update its KDoc's first sentence to: "Spec rev 14 §3 / rev 36 §3: the timer screen stays dark in Light and Dark, and takes Sonar's own colours in Sonar."
  - At the top of the body: `val tc = LocalTimerColors.current`; the `when (ui.tone)` uses `tc.work`, `tc.rest`, `tc.neutral`.
  - `background(Color.Black)` → `background(tc.ground)`; every `Color.White` (close icon tint, entry name, countdown, skip icon tints, `StatValue`) → `tc.text`. `StatValue` gains a `color: Color` parameter, passed `tc.text`.
  - `DualRing(...)`: `outerColor = tc.setRing, trackColor = tc.track, glow = LocalGlow.current != null`.
  - `StatBand`: read `val tc = LocalTimerColors.current` at its top; `HiitColors.SetRing` → `tc.setRing` (gradient, edge, lit line), `BandLine` → `tc.bandLine` (delete the `BandLine` val), the circle's `background(Color.Black, CircleShape)` → `background(tc.ground, CircleShape)` and its border colour → `tc.circleOutline`.
  - Leave the pause icon's `Color.Black` tint (it sits on the phase colour) and `CueToggleButton`'s colours (they read the Material scheme) as they are.
  - Remove the now-unused `HiitColors` import; add imports for `LocalAppTheme`, `LocalGlow`, `LocalTimerColors`, `timerTheme`.
  - `DualRing.kt`: add parameters `trackColor: Color, glow: Boolean`; use `trackColor` for both tracks; when `glow` and `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P`, draw each coloured arc first as a blurred halo, then the sharp arc:
    ```kotlin
    private fun DrawScope.halo(color: Color, fraction: Float, inset: Float, stroke: Float) {
        if (fraction <= 0f) return
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = stroke * 1.8f
                strokeCap = android.graphics.Paint.Cap.ROUND
                this.color = color.copy(alpha = 0.55f).toArgb()
                maskFilter = BlurMaskFilter(stroke, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawArc(
                inset, inset, size.width - inset, size.height - inset,
                -90f, 360f * fraction.coerceAtMost(1f), false, paint,
            )
        }
    }
    ```
    In the `Canvas` block: `ring(trackColor, 1f, outerInset, outerStroke)`, then `if (haloOn) halo(outerColor, outerAnim.value, outerInset, outerStroke)`, then `ring(outerColor, …)`; the same for the inner ring. `val haloOn = glow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` above the `Canvas`. Imports: `android.graphics.BlurMaskFilter`, `android.os.Build`, `androidx.compose.ui.graphics.Paint`, `androidx.compose.ui.graphics.drawscope.drawIntoCanvas`, `androidx.compose.ui.graphics.nativeCanvas`, `androidx.compose.ui.graphics.toArgb`; drop the `HiitColors` import. Update the KDoc: "Thick inner ring = current phase; thin outer ring = work-set progress (spec §9.2). In Sonar each arc has a blurred halo under it (rev 36 §4, API 28+)."

- [ ] **Step 4: Run to verify they pass.** rgradle `testDebugUnitTest --tests '*Timer*'`. Expected: all pass, the existing timer tests unchanged.

- [ ] **Step 5: Commit.** `git commit -m "Timer in Sonar: theme colours, ring halo, band colours"`.

---

### Task 6: Spec rulings, CLAUDE.md, full verification

**Files:**
- Modify: `docs/superpowers/specs/2026-10-10-colour-themes-design.md`
- Modify: `CLAUDE.md` (git tracks it as `claude.md`)

- [ ] **Step 1: Write the rulings into the spec.**
  - §2 last bullet: "**Pure rule, in `ui/theme/ThemeMode.kt`** (where `ThemeMode` lives; pure Kotlin): `effectiveTheme(mode, systemDark)` returns an `AppTheme` (LIGHT, DARK, SONAR), and `timerTheme(theme)` gives the timer's (DARK for Light and Dark, SONAR for Sonar)."
  - §3.1: timer prepare / cooldown `#5FA9C9` → `#6A9FE0`, with a note: "(moved from #5FA9C9, which was 28° of hue from work, under §6's 30°)".
  - §3 list: change "the entry chart and calendar, and the prescription card" to "the prescription card; the entry chart and calendar take Sonar's colours but no panel (they have none in Light or Dark)".
  - §4 "Chips and primary buttons" bullet → "**Chips and buttons:** Sonar's accent and outline through the colour scheme; no separate glow."
  - §4 "Panels" bullet: drop "the entry chart / calendar panel".
- [ ] **Step 2: CLAUDE.md.** Add, before the `- **References:**` line:
  `- **Colour themes: Sonar (approved, rev 36, revised 2026-10-15, amends rev 14):** \`docs/superpowers/specs/2026-10-10-colour-themes-design.md\` — Appearance adds Sonar (free) after System / Light / Dark: \`effectiveTheme\` → \`AppTheme\`; \`HiitTheme(AppTheme)\` provides the scheme, \`LocalGlow\` (null outside Sonar) and \`LocalTimerColors\`; \`glowPanel\` / \`panelColor\` / \`edgeGlow\` do nothing in Light and Dark; the timer uses \`TimerTheme\` (dark from Light/Dark, Sonar from Sonar) with a blurred ring halo on API 28+. Ember and Tide wait for Pro (spec §8).`
- [ ] **Step 3: Full verification.** rgradle `assembleDebug testDebugUnitTest lintDebug`. Expected: BUILD OK, 0 failed, lint with no new errors.
- [ ] **Step 4: Commit.** `git commit -m "Spec and CLAUDE.md: Sonar rulings"`.
