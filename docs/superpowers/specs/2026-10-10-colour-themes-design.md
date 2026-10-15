# REPKIT — Colour themes: Sonar, Blossom, Grove, Foundry (spec revisions 36 and 39)

Status: approved by the user, 2026-10-10; revised 2026-10-15:
- one glowing dark theme, **Sonar**, next to System / Light / Dark ("i love the sonar theme");
- Sonar is modelled on the user's AstraLoc screenshots (`docs/theme/`, local only, not committed);
- a theme changes every screen, the timer included (option A);
- Sonar is free. **Ember and Tide wait for Pro**: the user chose "Sonar only first", since Pro can't be bought
  yet and they would only show a lock (§8). Their draft palettes are kept in §8 for then.
- It ships after 0.2.0, as its own release.
- **Revision 39 (2026-10-15)** adds Blossom, Grove and Foundry (§10), chosen from the mockup
  (`D:\Claude\apps\repkit-mockups\theme-suggestions.html`): the user found Tide "too similar to sonar", asked for
  "a feminine theme and a woodland theme and an industrial theme", then "Blossom Grove and Foundry. do it".

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

- **Ember (Pro), when billing exists.** Tide is dropped (rev 39: too close to Sonar). Ember's draft below
  fails §6's hue rule (work, rest and prepare all orange, under 20° apart); the mockup's fix is rest `#D9579E`
  (rose) and prepare / cooldown `#8FB3C9` (steel blue). Originally **Ember and Tide (Pro)**: They become `ThemeMode` values with a lock for a FREE tier,
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

## 10. Revision 39: Blossom, Grove and Foundry

Three more glowing dark themes join Sonar. They work exactly like Sonar (§2–§6) and differ only in their tokens,
including how strongly they glow and, for two of them, their panel corners.

- **The setting:** `ThemeMode` and `AppTheme` gain `BLOSSOM`, `GROVE` and `FOUNDRY`.
  - The Appearance list reads System · Light · Dark · Sonar · Blossom · Grove · Foundry, each glowing theme
    with its three-dot swatch (ground, accent, rest).
  - `timerTheme` keeps every glowing theme; only Light maps to Dark.
- **Free for now.** Pro can't be bought yet, so a lock would only be a wall. Revisit when billing lands (§8's
  `canUseTheme` idea applies to any of them).
- **Names** are product names, `translatable="false"`, like Sonar (`theme_blossom`, `theme_grove`, `theme_foundry`).
- **Code:** a `ThemePalette` holds each theme's tokens; `paletteFor(AppTheme)` returns it (null for Light and
  Dark), and the scheme, `GlowTokens` and `TimerColors` are built from it once. Sonar's tokens are unchanged.

### 10.1 Palettes

| Token | Blossom (feminine) | Grove (woodland) | Foundry (industrial) |
| --- | --- | --- | --- |
| ground | `#1A0E16` | `#0D120C` | `#111315` |
| panel | `#2A1724` | `#18211A` | `#1C1F22` |
| panel centre | `#3A1F33` | `#1F2D21` | `#262A2E` |
| outline | `#B5578E` | `#5C7F4A` | `#6B737A` |
| accent | `#FF8FC8` | `#9CCB6B` | `#F5C518` |
| on accent | `#33081F` | `#142008` | `#1A1500` |
| text | `#FBEFF5` | `#EEF0E6` | `#ECEEF0` |
| secondary text | `#CFA8BE` | `#A9B49B` | `#A3AAB0` |
| error | `#FF6B6B` | `#E86A5A` | `#FF5A4E` |
| timer: work | `#FF8FC8` (pink) | `#9CCB6B` (moss) | `#F5C518` (safety yellow) |
| timer: rest | `#F5C26B` (gold) | `#E0A458` (amber) | `#3FA7F0` (signal blue) |
| timer: prepare / cooldown | `#B79CFF` (lavender) | `#6FC2A8` (lichen) | `#FF5E3A` (hazard red) |
| timer: set ring | `#E9A8FF` | `#D8C17A` (birch) | `#C9D1D8` (steel) |
| timer: track | `#3A2133` | `#2A2A1E` | `#2A2E33` |

### 10.2 Glow and shape per theme

| | Sonar | Blossom | Grove | Foundry |
| --- | --- | --- | --- | --- |
| panel halo (accent alpha) | 0.35 | 0.40 | 0.25 | 0.12 |
| screen edge (accent alpha) | 0.12 | 0.13 | 0.09 | 0.05 |
| ring halo (arc alpha) | 0.55 | 0.60 | 0.40 | 0.25 |
| panel corner | each panel's own | 22 dp | each panel's own | 6 dp |

`panelShape(default)` gives a panel its theme's corner (list tiles, settings cards, the entry settings action
cards and the prescription card); the danger card keeps its own.

### 10.3 Testing

§6's contrast and hue checks run over every glowing palette. Also: each mode round-trips in `AppPreferences`, is
dark, and keeps its own timer; `HiitTheme` provides each palette's scheme, glow and timer colours; `panelShape`
gives Blossom 22 dp and Foundry 6 dp and leaves the rest alone; the Appearance list shows each theme with its
swatch; the timer renders in each.
