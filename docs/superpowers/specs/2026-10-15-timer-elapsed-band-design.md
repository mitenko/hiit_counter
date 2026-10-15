# REPKIT — Timer elapsed band (spec revision 38)

Status: requested by the user on 2026-10-15, chosen from local HTML mockups ("F3" for the layout, "F3b2" for the
fill, with the line pieces left of the Sets circle lit as well).

Supersedes rev 37's two stat circles (`2026-10-12-timer-stat-circles-design.md` §2.2). Keeps the rest of rev 37:
the name under the cue toggles, mm:ss Elapsed with no hours, the ring at 0.85 / 0.3 / displaySmall and the
`RingCentre` layout. TalkBack and the strings are unchanged.

## 1. Why
The user wasn't sure about two equal circles for Sets and Elapsed. From seven mockups and then twelve variations
they picked the Sets circle sitting across a band of two full-width lines, with Elapsed large between the lines,
and asked for a bar behind Elapsed that slowly fills. They also pointed out the large gap between the stats and
the ring, which `Arrangement.SpaceEvenly` left.

## 2. The change (`ui/timer/TimerScreen.kt`, `TimerUiMapper.kt`)

### 2.1 Stats band (`StatBand`)
Sizes are fractions of the content row's width W (the screen less the column's 24 dp margins):
- **Sets circle:** diameter `c = 0.28 W`, at the row's left end, black-filled with the 2 dp outline in
  `HiitColors.Neutral` at 60% alpha; the value "6/8" is `0.3 c` (`Dp.toSp()`, tight line height).
- **Band:** two 1.5 dp lines across the whole row, `0.84 c` apart and centred on the circle, so the circle sits
  across both lines and hides them inside it.
- **Elapsed:** mm:ss at `0.6 c` (`Dp.toSp()`, tight line height), white, centred in the space right of the circle.
- **TalkBack:** the circle reads "Sets 6 of 8" and Elapsed "Elapsed 3 minutes 33 seconds" (rev 37's
  descriptions); neither has a visible label.

### 2.2 Fill
- **Amount:** `TimerUiState.elapsedProgress` = elapsed ÷ the planned workout length (`totalDurationSec`),
  clamped to 0..1, and 1 at DONE (0 when the planned length is 0). The engine's elapsed time holds while
  paused and jumps with a skip forward or back, so the fill does the same.
- **Where:** from `0.86 c` (just inside the circle's right edge, so the first second already shows) to
  `0.86 c + (W − 0.86 c) × progress`.
- **Look:** a horizontal gradient of `HiitColors.SetRing` (the outer set ring's blue) from 8% to 25% alpha,
  with a solid 2 dp edge at its leading end.
- **Lines:** blue (`SetRing`) from the row's start up to the fill's edge, so the pieces left of the circle are
  always lit; grey (`Neutral` at 53% alpha) ahead of it.
- **Motion:** `animateFloatAsState` with a 1 s linear tween, so the fill glides between the once-a-second
  updates. With Remove animations on (`reducedMotionEnabled()`, moved from `EntryScreen` to `ui/common`) it
  snaps instead.

### 2.3 Spacing
The column under the name is no longer `SpaceEvenly`: the band, 16 dp, the ring, then a weighted spacer, then the
controls. The ring sits right under the band and the spare room falls above Back / Pause / Skip.

## 3. Tests (Robolectric, sdk 34)
- `TimerUiMapperTest`: `elapsedProgress` is 0 at the start, 50/240 mid-run and while paused, 1 at the end of
  cooldown and at DONE, clamped past the end, and 0 for a zero planned length.
- `TimerScreenTest`, at `w320dp-h640dp` and at 411 dp: the band spans the row; the circle is round, 27–29% of
  the row, at its left end and taller than the band; "99/99" fits inside the circle; "75:12" fits between the
  lines right of the circle.
- The fill: empty at the start; at half the planned length its right edge is half way from its start to the
  band's end; full at DONE; it starts inside the circle's right half.
- The ring is at most 20 dp under the stats, and the space above the controls is at least that gap.
- Unchanged: no "Sets" / "Elapsed" text on screen, the TalkBack descriptions, the name under the toggles.
