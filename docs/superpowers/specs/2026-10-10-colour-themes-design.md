# REPKIT — Colour themes: Sonar (spec revision 36)

Status: approved by the user, 2026-10-10; revised 2026-10-15:
- one glowing dark theme, **Sonar**, next to System / Light / Dark ("i love the sonar theme");
- Sonar is modelled on the user's AstraLoc screenshots (`docs/theme/`, local only, not committed);
- a theme changes every screen, the timer included (option A);
- Sonar is free. **Ember and Tide wait for Pro**: the user chose "Sonar only first", since Pro can't be bought
  yet and they would only show a lock (§8). Their draft palettes are kept in §8 for then.
- It ships after 0.2.0, as its own release.

Amends revision 14 (light and dark: the Appearance choice and the always-dark timer). Covers the timer as it
stands after revision 38 (the elapsed band).

## 1. Purpose

Light and Dark stay exactly as they are. Sonar adds a distinct look: a dark ground, panels lit from within,
thin glowing outlines, and a faint glow around the screen edge. It has its own work and rest colours on the
timer.

## 2. Choosing a theme (amends revision 14 §1)

- **The setting:** `ThemeMode` gains `SONAR` (`enum class ThemeMode { SYSTEM, LIGHT, DARK, SONAR }`).
  - It's stored as before, by name, in `app.preferences_pb` `theme_mode`.
  - An unknown name still reads as SYSTEM, so a downgrade falls back safely.
- **The ⚙ Settings → Appearance list** reads System · Light · Dark · Sonar, as radio rows.
  - The Sonar row shows a small swatch: three dots for the ground, accent and rest colours.
  - Choosing it applies at once and closes the dialog, like today.
- **Pure rule, in `ui/theme/ThemeMode.kt`** (where `ThemeMode` lives; pure Kotlin): `effectiveTheme(mode, systemDark)` returns an `AppTheme` (LIGHT, DARK, SONAR), and `timerTheme(theme)` gives the timer's (DARK for Light and Dark, SONAR for Sonar).

## 3. What a theme changes

**All screens**, through the `GlowTokens` (`LocalGlow`, Sonar only) and `TimerColors` (`LocalTimerColors`) sets provided by
`HiitTheme`, and a Material 3 `ColorScheme` built from the palette:
- the list tiles and their graphs;
- the prescription card; the entry chart and calendar take Sonar's colours but no panel (they have none in Light or Dark);
- buttons, chips and switches;
- the settings cards, steppers and dialogs;
- the timer.

Light and Dark map to today's schemes, and their timer still uses today's dark scheme and `HiitColors`
(revision 14 unchanged). In Sonar, the timer uses Sonar's own ground, ring and phase colours.

### 3.1 The Sonar palette

| Token | Sonar |
| --- | --- |
| ground (background) | `#071014` |
| panel | `#0F292E` |
| panel centre (the glow inside) | `#123B3D` |
| outline | `#23A492` |
| accent (primary) | `#2EE6C8` |
| on accent | `#04201C` |
| text | `#E8F4F2` |
| secondary text | `#8FB3AE` |
| error | `#F0605D` |
| timer: work | `#2EE6C8` |
| timer: rest | `#F08A6B` |
| timer: prepare / cooldown | `#6A9FE0` |
| timer: set ring | `#22D3EE` |
| timer: track | `#12302F` |

The prepare / cooldown colour moved from `#5FA9C9`, which was 28° of hue from work, under §6's 30° rule.

These are starting values. The tests in §6 decide the final ones: a value that fails a contrast check is
adjusted, and the spec is updated to match.

### 3.2 The timer's stats band (revision 38) in Sonar

- **The fill and the lit lines** use the set ring colour, as they use `HiitColors.SetRing` today.
- **The lines ahead of the fill** use the outline colour at 53% alpha.
- **The Sets circle** is filled with the ground and outlined in the outline colour at 60% alpha.
- **The values** use the text colour.

## 4. The glow

- **Panels** get a `Modifier.glowPanel()`. These are the list tiles, the prescription card and the settings
  cards.
  - **Outline:** 1.5 dp in the outline colour.
  - **Outer glow:** a soft blur in the accent at about 35% alpha, roughly 12 dp. It uses coloured shadows
    (`spotColor` / `ambientColor`) on API 28+.
  - **Fill:** a radial gradient from the panel centre to the panel colour.
- **Chips and buttons:** Sonar's accent and outline through the colour scheme; no separate glow.
- **The timer ring:** each ring arc is drawn twice, once as a blurred wider stroke underneath (the glow) and
  once as the sharp stroke. The blur uses `BlurMaskFilter` on API 28+.
- **The screen edge:** themed screens draw a faint inner glow, the accent at about 12% alpha fading inwards
  over about 24 dp. That covers the list, the entry screen and the timer.
- **API 26–27:** no blur. Outlines and gradients only, with the same colours.
- **Reduced motion:** the glow is static, so no change is needed.
- **Light and Dark** have no glow.

## 5. System bars

Themed screens draw edge to edge with light status and navigation bar icons, as Dark does today.

## 6. Testing

- **Pure:**
  - `effectiveTheme` across every mode × system setting;
  - an unknown stored name reads as SYSTEM.
- **Contrast** (a unit test over Sonar's tokens, WCAG relative luminance):
  - text and on-accent text at least 4.5:1 against the ground, the panel and the accent respectively;
  - the secondary text at least 4.5:1 against the panel;
  - the timer's work, rest and prepare colours at least 3:1 against the ground and clearly distinct from each
    other (a hue gap of at least 30°).
- **AppPreferences:** SONAR round-trips.
- **Compose (Robolectric):**
  - the Appearance list shows all four options, with Sonar's swatch;
  - choosing Sonar applies it;
  - the timer uses Sonar's work, rest and set ring colours in Sonar, and `HiitColors` in Dark.
- **The existing Light and Dark tests** stay green and unchanged.

## 7. Strings

| Key | English |
| --- | --- |
| `theme_sonar` | Sonar |

The name stays "Sonar" in every language, as a product name, so it goes in `values/strings.xml` only
(`translatable="false"`); no translation rows are needed.

## 8. Later and out of scope

- **Ember and Tide (Pro), when billing exists.** They become `ThemeMode` values with a lock for a FREE tier,
  tapping one opens Go Pro (`isProTheme`, `canUseTheme(tier, mode)`, a Pro on FREE reading as DARK with the
  choice kept). Draft palettes, to re-preview on the then-current screens first:

  | Token | Ember | Tide |
  | --- | --- | --- |
  | ground | `#140C08` | `#0A1424` |
  | panel | `#24150D` | `#12233A` |
  | panel centre | `#331D10` | `#183150` |
  | outline | `#C2571F` | `#2F8FA8` |
  | accent | `#FF7A2F` | `#5ED6E0` |
  | on accent | `#2A0E00` | `#032A30` |
  | text | `#F7ECE4` | `#E6F0F8` |
  | secondary text | `#C2A592` | `#93A9C2` |
  | error | `#FF5A4E` | `#FF6B6B` |
  | timer: work | `#FF7A2F` | `#5ED6E0` |
  | timer: rest | `#E0453A` | `#E2C08D` |
  | timer: prepare / cooldown | `#D9A441` | `#8FA8E8` |
  | timer: set ring | `#FFB25C` | `#7FB2FF` |
  | timer: track | `#3A2216` | `#1A2D45` |

- Light versions of the themes.
- User-made colours and Material You dynamic colour.
- Per-workout themes.
- Any change to the Light and Dark looks.

## 9. Order

Built after 0.2.0 (weights and the elapsed band) is uploaded, and shipped as its own release with a version
bump. The user approved Sonar from the preview (`D:\Claude\apps\repkit-mockups\theme-preview.html`).
