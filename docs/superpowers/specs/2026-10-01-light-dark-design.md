# REPKIT — Light and dark colour schemes (spec revision 14)

**Date:** 2026-10-01
**Status:** Approved by the user in chat (2026-10-01).
**Amends:** `2026-09-24-hiit-counter-design.md` §3's Stack row ("Jetpack Compose, Material 3, dark theme app-wide" becomes "Compose + Material 3 (light and dark, user-selectable)") and `2026-09-30-ui-refresh-design.md` (rev 9) §2's "Top bar: … Nothing else is in the bar" (an Appearance ⚙ is added, at the right).

## 1. Purpose

The app followed the phone's dark theme unconditionally (v1 §3). This adds a light palette and an in-app **Appearance** override (System / Light / Dark), reached from a ⚙ in the entry list's top bar.

## 2. Preference

- `enum class ThemeMode { SYSTEM, LIGHT, DARK }` (`ui/theme/ThemeMode.kt`), pure Kotlin — no Android imports.
- Pure helper `fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean`: SYSTEM follows `systemDark`; LIGHT is always light; DARK is always dark.
- `AppPreferences.themeMode: Flow<ThemeMode>` — a new `stringPreferencesKey("theme_mode")` in the existing `app.preferences_pb`. Absent or unrecognised stored values read as SYSTEM (forward-compatible with any later mode). `suspend fun setThemeMode(mode: ThemeMode)` writes `mode.name`.

## 3. Theme

- `HiitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content)` (`ui/theme/Theme.kt`) picks `DarkScheme` (unchanged) or the new `LightScheme`.
- **LightScheme:**
  - `primary` `0xFF4C7A29` (a darker Work green, about 5.1:1 against white text and 4.8:1 on the light background; `onPrimary` white) — stays visually distinct from the unchanged amber `HiitColors.Rest`, so work/rest reads are still easy to tell apart.
  - `secondary` `0xFF0277BD` (a darker SetRing blue).
  - `background` / `surface` `0xFFF7F9F8` (near-white).
  - `surfaceContainer` `0xFFE9EEEC` (light grey, for cards), `surfaceContainerHighest` `0xFFDCE3E0` (slightly darker, for the dragged-card state).
  - `error` `0xFFC62828`.
- No Material You / dynamic colour: the app keeps its own fixed palettes in both modes.

## 4. Timer screen stays dark (deliberate — overturnable)

The timer screen (`ui/timer/TimerScreen.kt`) keeps hard-coded black background and white/black text and icon colours in **both** app modes, for visibility mid-workout. `TimerScreen` now wraps its body in `HiitTheme(darkTheme = true)` so the one value it reads from `MaterialTheme` — the cue toggle row's "on" fill (`MaterialTheme.colorScheme.primary`) — also stays the dark scheme's green, regardless of the Appearance choice. This is a deliberate choice from this revision; the user can overturn it later (e.g. a light timer screen) without re-litigating the rest of this spec.

## 5. MainActivity and the window background

- `MainActivity` collects `AppPreferences.themeMode` (initial `SYSTEM`) and computes `darkTheme = isDark(mode, isSystemInDarkTheme())`, passed to `HiitTheme`.
- Status and navigation bar icons follow the same `darkTheme`: a `SideEffect` calls `enableEdgeToEdge(statusBarStyle, navigationBarStyle)` with `SystemBarStyle.dark`/`.light` (transparent scrims) on every change, so a manual override flips the icon colour live, not just the phone's own night mode.
- `res/values/themes.xml`'s `windowBackground` no longer hard-codes black: it's now the light background colour (`#FFF7F9F8`), with `res/values-night/themes.xml` keeping the original black for the phone's own dark setting. This avoids a black flash in light mode before Compose starts. **Known limitation (accepted):** a manual in-app Light/Dark override can't affect this pre-Compose window — only the phone's own night mode can, via the resource qualifier — so a user who sets Dark while their phone is in light mode (or vice versa) may see one frame of the "wrong" background before `HiitTheme` takes over.

## 6. Appearance UI

- A ⚙ `IconButton` (48 dp, vector drawable `ic_settings`, already present and used elsewhere for entry settings; here with content description "Appearance") sits at the right of the entry list's top bar (`ui/entries/EntryListScreen.kt`). It's overlaid inside the existing centred `Box`, so the "REPKIT" title — still `Box`-centred, unaffected by a sibling's `Alignment.CenterEnd` — stays exactly centred.
- Tapping it opens an `AlertDialog` titled "Appearance" with three rows: "System default", "Light", "Dark" (`R.string.theme_system/theme_light/theme_dark`). Each row is a 48 dp `Modifier.selectable(role = Role.RadioButton)` with a `RadioButton` + label, grouped under `Modifier.selectableGroup()`.
- Choosing a row calls `EntryListViewModel.setThemeMode` immediately and closes the dialog; the effective theme switches live (recomposition from the `themeMode` `StateFlow`).
- `EntryListViewModel` exposes `themeMode: StateFlow<ThemeMode>` (from `AppPreferences.themeMode`, `stateIn(WhileSubscribed(5_000), ThemeMode.SYSTEM)`) and `fun setThemeMode(mode: ThemeMode)`.

## 7. Hard-coded colours audit

A grep for `Color(0x`, `Color.Black`, `Color.White` and `HiitColors.` across `ui/` (outside `ui/theme/Theme.kt` itself, where the palettes are defined) found **no literal colours to replace**: every other screen already reads `MaterialTheme.colorScheme.*` roles (list tiles, the entry screen's chart/calendar/reps column, settings cards and dialogs, the week circles and tile sparkline). The only literals outside `Theme.kt` are inside `ui/timer/TimerScreen.kt`, which §4 above keeps as-is by design. Vector drawables (`ic_settings`, `ic_add`, etc.) use a white `fillColor` but are always drawn through `Icon(tint = ...)`, which defaults to `LocalContentColor` (theme-aware) everywhere outside the timer screen, so none needed a tint fix.

## 8. Strings

`appearance` "Appearance", `theme_system` "System default", `theme_light` "Light", `theme_dark` "Dark".
