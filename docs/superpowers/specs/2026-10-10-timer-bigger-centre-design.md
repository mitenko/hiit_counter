# REPKIT — Bigger timer centre (spec revision 35)

Status: requested by the user 2026-10-10 ("bigger still").

Amends the timer layout from rev 22's controls-spacing spec (`2026-10-03-timer-controls-spacing-design.md`)
and rev 33's larger-stats spec (`2026-10-08-timer-exit-stats-design.md`); both left the ring and its centre
text unchanged.

## 1. Why
The ring and its centre (rep number, countdown) were still small relative to the screen even after rev 33
enlarged the Sets/Elapsed stats. The user asked for the ring and its centre text bigger still.

## 2. The change (`ui/timer/TimerScreen.kt`)
- **Ring:** the ring box `fillMaxWidth(0.85f)` → `fillMaxWidth(0.95f)`, still square (`aspectRatio(1f)`).
- **Rep number:** `numberSize` (derived from the ring box's `maxWidth`, so it scales with the ring and
  ignores the user's font scale via `Dp.toSp()`) goes from `maxWidth * 0.3f` → **`maxWidth * 0.44f`**.
- **Countdown:** was `MaterialTheme.typography.displaySmall` (a fixed, theme-driven size); now sized the
  same way as the rep number, **`maxWidth * 0.21f`**. Colour (`Color.White`) and test tag (`countdown`)
  are unchanged.
- **Tight line height:** both the rep number and the countdown use a private `tightCentreTextStyle(fontSize,
  fontWeight)` helper — `lineHeight = fontSize`, `platformStyle = PlatformTextStyle(includeFontPadding =
  false)`, `lineHeightStyle = LineHeightStyle(alignment = Center, trim = Both)` — so the larger text doesn't
  claim extra vertical space beyond its glyphs (Compose's default line height otherwise adds font-metric
  padding that would push the three centre elements further apart than their actual pixel heights need).
- **Phase label** (WORK / REST / …): unchanged (`MaterialTheme.typography.titleLarge`, bold).

## 3. Fit check
The three centre elements (phase label, rep number, countdown) are laid out in a `Column` inside the ring,
and must fit inside the ring's open middle without overlapping each other. `ui/timer/DualRing.kt` draws the
inner (phase) ring with `outerStroke = d * 0.02`, a `d * 0.03` gap, and `innerStroke = d * 0.07` (`d` = the
ring box's side, since it's square), so the inner ring's open centre has
`diameter = d * (1 - 2 * (0.02 + 0.03 + 0.07)) = d * 0.76`, centred in the box.

At `0.95` (ring) / `0.44` (number) / `0.21` (countdown) all three fit inside that circle without overlapping,
verified at 320 dp width (the smallest phone width the app targets) with the longest realistic centre
content — reps `"88"`, countdown `"00:59"`, and the REST label — so no reduction from the spec's requested
factors was needed.

**Final factors: ring 0.95, number 0.44, countdown 0.21.**

## 4. Testing
- `ui/timer/TimerScreenTest.kt`, new test `label, number and countdown fit inside the ring without
  overlapping at 320dp` (`@Config(qualifiers = "w320dp-h640dp")`): builds a REST-phase `TimerUiState` with
  `repsThisSet = 88` (→ centre `"88"`) and `phaseSecondsLeft = 59` (→ countdown `"00:59"`), asserts the
  label/number/countdown bounds don't overlap vertically, and asserts every corner of each node's bounds
  lies within the inner ring's open circle (computed from the `DualRing.kt` stroke/gap fractions above,
  centred on the `ring`-tagged `DualRing` node's bounds).
- The existing 411 dp `TimerScreenTest` suite (buttons, cue toggles, skip spacing, font-scale visibility,
  etc.) is unaffected and still passes.
