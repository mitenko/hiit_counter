# REPKIT — Timer stat circles (spec revision 37)

Status: requested by the user on 2026-10-12 with annotated screenshots (the REST state and the DONE state).

Supersedes rev 35's sizes (`2026-10-10-timer-bigger-centre-design.md`): the ring and its centre go back to
their pre-rev-35 sizes. Amends rev 33's stats typography (`2026-10-08-timer-exit-stats-design.md`): the
labelled Sets / Elapsed text becomes two unlabelled circles. Keeps weights PR 3's weight on the label line
(rev 26 §4, plan Spec note 56): "16 kg" in WORK, "REST 16 kg" in REST. The controls (Back / Pause / Skip)
and the cue toggles are unchanged.

## 1. Why
On the phone, rev 35's larger centre pushed the phase label into the ring ("REST" was clipped at the top) and
the countdown over the ring's lower edge. The user asked to revert the centre, to make the Sets / Elapsed values
the large thing instead, each in its own circle with no label, to drop the hours from Elapsed, and to move the
workout name up towards the cue toggles. On DONE, the final count sat high in the ring; it should be centred.

## 2. The change (`ui/timer/TimerScreen.kt`)

### 2.1 The workout name
The name now sits directly under the cue toggle row, 12 dp below it, instead of being the first child of the
`SpaceEvenly` column (which floated it down). On DONE, with no toggles, it sits under ✕ the same way.

### 2.2 Sets and Elapsed circles
- The visible "Sets" and "Elapsed" labels are gone. Each value is centred inside its own circle, side by side in
  one row with `Arrangement.SpaceEvenly`.
- **Circle:** 34% of the screen width (`LocalWindowInfo.containerSize`), with a 2 dp outline in
  `HiitColors.Neutral` at 60% alpha. **The circles never touch:** the row keeps at least 24 dp between them
  and beside them; where 34% would break that (the 272 dp row at a 320 dp screen), the circles shrink instead:
  `circle = min(0.34 × screen, (row − 3 × 24 dp) / 2)`. That gives 100 dp circles 24 dp apart at 320 dp, and
  ≈140 dp circles ≈28 dp apart at 411 dp.
- **Value:** `0.3 × circle`, converted with `Dp.toSp()` so the user's font scale can't overflow it (30 sp at
  320 dp, ≈42 sp at 411 dp), white, one line, with the tight line height (§2.4).
- **TalkBack:** each circle is one merged node with a content description: "Sets 8 of 8"
  (`timer_desc_sets`) and "Elapsed 3 minutes 33 seconds" (`timer_desc_elapsed` over the `duration_minutes` /
  `duration_seconds` plurals, joined by `duration_minutes_seconds`; a whole minute says only the minutes,
  under a minute only the seconds). The mapper returns them as `TimerUiState.setsDescription` /
  `elapsedDescription`. All five resources have Spanish, Simplified Chinese and Hindi.
- **Sets text:** unchanged ("8/8").
- **Elapsed format:** `TimerText.formatElapsed` is mm:ss with no hours; past 59:59 the minutes keep counting
  ("60:00", "75:12"), never h:mm:ss. It replaces `formatHms`, which had no other caller.
- `sets_label` / `elapsed_label` stay in the resources (unused) so the generated locale files only gain lines.

### 2.3 The ring (rev 35 reverted)
- The ring box: `fillMaxWidth(0.95f)` → **`fillMaxWidth(0.85f)`**.
- The rep number: `maxWidth × 0.44` → **`maxWidth × 0.3`** (still `Dp.toSp()`, bold).
- The countdown: `maxWidth × 0.21` → **`MaterialTheme.typography.displaySmall`**.
- The phase label and the weight on its line: unchanged (`titleLarge`).

### 2.4 Tight line height
`tightCentreTextStyle` is renamed `tightTextStyle` and kept where it still helps: the rep number and the stat
circles' values. The countdown is back to its theme style.

### 2.5 The number sits at the ring's centre (`RingCentre`)
The centre is no longer a `Column` centred as a whole. A small custom `Layout` fills the ring box and places:
- **the number** at the ring's centre, horizontally and vertically;
- **the label line** (phase label, weight, or both) directly above the number;
- **the countdown** directly below it.

So on DONE, which has no countdown, the final count is centred in the ring with "DONE" above it, instead of
the label-and-number stack being centred. With no number (COOLDOWN, or a Timer only run's DONE) the label line
and the countdown are centred together as a stack. Each part is centred horizontally on the ring. TalkBack is
unchanged: one merged node with the centre's description.

## 3. Tests (Robolectric, sdk 34)
- `TimerTextTest`: `formatElapsed` gives 0 → "00:00", 213 → "03:33", 3600 → "60:00", 4512 → "75:12".
- `TimerUiMapperTest`: "00:50" elapsed; the circles' descriptions ("Sets 8 of 8", "Elapsed 3 minutes 33
  seconds", "Elapsed 1 minute", "Elapsed 1 minute 1 second", "Elapsed 75 minutes 12 seconds"); every language
  resolves them with no format left over.
- `TimerScreenTest`, at `w320dp-h640dp` and at 411 dp:
  - the circles sit side by side, at least 24 dp apart, round, 30–34% of the screen wide, and "99/99" and
    "75:12" each fit inside their circle (every corner within the radius, less the outline);
  - the label, number and countdown fit inside the inner ring without overlapping;
  - "REST 112.5 lb" fits on the label line inside the ring (WORK's "112.5 lb" at 320 dp as before).
- DONE: the number's centre equals the ring's centre within 1 dp, and DONE is above it.
- No "Sets" / "Elapsed" text on screen; the circles' content descriptions.
- The name is at most 16 dp under the cue toggle row.
