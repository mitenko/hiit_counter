# REPKIT — Weight progression (spec revision 26)

Status: designed with the user in chat (2026-10-04); reviewed 2026-10-05, with all five review findings resolved in §9. Decisions are marked **(user)**; choices made while writing the spec are marked **(proposed)** and are open to the user's review.

Amends v1 §6 (progression), R3 §5 (Progression page), R6 §3 (history), rev 9 §2–§3 (tile and entry screen), rev 12 (check-in highlight), rev 14 §5 (the ⚙ dialog) and rev 16 (holds). Builds on rev 24/25 (localisation): every new string needs es / zh-rCN / hi.

## 1. What it is

A Counter workout can progress by **weight** as well as by reps. A new setting, **Progress by**, has three values:

| Mode | A check-in moves | Example |
| --- | --- | --- |
| **Reps** (today, the default) | The rep total +1, split across sets | 64 reps → 65 |
| **Weight** | One weight up; reps per set fixed | 20 kg → 22.5 kg, always 3 × 10 |
| **Reps then weight** (double progression) | One rep per set up through a range; at the top, the next weight and the reps reset | 16 kg × 12 → 20 kg × 8 |

It is a variant of Counter, not a third entry type **(user)**. Timer Only entries are unchanged.

## 2. Model: positions on a ladder (approach 1, user)

The check-in engine (`RepProgression`) keeps moving one integer, which today is the rep total. In the weight modes the integer becomes a **position** (level) on a ladder of what you actually do, and a pure translator, `ProgressionScale`, maps the position to a prescription and back:

- **Reps:** position = rep total. Identity; today's behaviour, untouched.
- **Weight:** position = index into the weight list. `0 = weights[0]`, … Reps per set are fixed.
- **Reps then weight:** with `span = maxReps − minReps + 1`, `weightIndex = position / span` and `reps = minReps + position % span`. Position 0 is `weights[0] × minReps`, and the top is `weights.last × maxReps`.

So the existing rules apply unchanged (user): on time +1, the 36 h window, the missed-day formula, the minimum and maximum, holds, and streaks. In weight modes:

- **Minimum** = position 0 (the lightest weight at the bottom of the range) and **maximum** = the top position. They are derived from the ladder, not edited separately **(proposed)**.
- **Holds** sit on a position: a weight (Weight), or a weight × reps (Reps then weight) **(user)**. The hold rules are unchanged: the day it is reached counts, a miss that lands on it restarts it, and finishing it goes +1.
- **Gentler misses (user, option B):** a miss never drops more than one weight. Concretely, after a miss the new position is at least the **first position of the next lighter weight** (Reps then weight: `(weightIndex − 1) × span`; Weight: `index − 1`), never below 0.
  - This is the engine's only change: an optional `missFloor(position): Int` that the scale supplies. It is identity for Reps mode, so today's Reps behaviour is byte-for-byte the same.
  - Example: 16 kg × 8 plus a 6-day absence lands on 14 kg × 8, not 12 kg × 12.
  - The exact order of clamp, penalty, `missFloor` and hold is in §9.1.

### Weights (user)

Each weight workout uses one of two kinds of weight list:

- **Steps:** start, step and top, e.g. 20 / 2.5 / 60 kg. This expands to `20, 22.5, … 60`.
- **My weights:** an explicit ascending list, e.g. 8 / 12 / 16 / 20 / 24 kg (kettlebells). Gaps can be uneven. Add and remove work like holds.

Limits **(proposed)**:
- 2–40 weights;
- each weight > 0 and ≤ 999.75 in the unit;
- at most 2 decimals;
- the list is sorted on save, and duplicates are an error.

### Editing the weights or the rep range keeps you where you are (proposed)

Positions are only meaningful against the current list. So when the weights or the rep range change, the saved position and every hold are **remapped by value**:

- **Weight:** you stay on the same weight. If it was removed, you go to the nearest weight below it, or the lightest.
- **Reps:** reps are clamped into the new range.
- **Holds:** a hold whose weight was removed is dropped when the change is saved.
- **The hold count** resets only if the current position's weight or reps actually changed (the R3 §6.3 rule, applied to values).

The exact algorithm is in §9.2. History is unaffected, because each point carries its own weight, reps and unit.

### Units (user)

- **kg** or **lb** per workout, with an **app-wide default** for new workouts: ⚙ › Units.
- Weights are stored as **integer hundredths of the workout's unit**: 2250 means 22.5 in the workout's unit. This avoids floating-point drift and kg↔lb rounding.
- Changing a workout's unit **converts** its weights (×2.20462 or ÷2.20462), rounded to the nearest 0.25, and asks first **(proposed)**. History points keep the unit they were recorded in and convert for display. The NULL and default rules are in §9.3.

### Switching mode (user, option A)

Changing **Progress by** asks "Start fresh? This workout restarts at its starting weight. Your streaks and history are kept."

- On confirm, the position goes to the mode's starting position and the hold count to 0. Streaks, the last check-in and the history stay.
- Each mode's settings (weights, reps, units) are remembered when you switch away and back. Only the position resets.

### Starting point

The **starting weight** is picked from the list, plus the **starting reps** in Reps then weight. Together they give the starting position. **Reset progress** returns to it, as today.

## 3. Settings screens (user, section 2)

**Progression tab:**

- At the top, a segmented **Progress by: Reps | Weight | Reps then weight**, with the start-fresh confirm on change.
- **Reps:** today's page, unchanged.
- **Weight / Reps then weight**, top to bottom:
  1. **Unit:** kg | lb.
  2. **Weights:** Steps | My weights.
     - *Steps:* three steppers, Start / Step / Top. The step stepper offers 0.5, 1, 1.25, 2, 2.5, 5 **(proposed)**.
     - *My weights:* a list of weight rows, each with ✕ remove, plus "+ Add weight". A new row is the last weight + the last gap.
  3. **Reps per set** (Weight), or a **Rep range** with min and max steppers, 1–100 and min < max (Reps then weight).
  4. **Starting weight** (a picker from the list), plus **Starting reps** (Reps then weight, within the range).
  5. **Hold** switch plus the hold list. Each hold picks a weight (and reps), plus "for N check-ins". This reuses the multi-hold UI from rev 16.
  6. **On-time window** and **Missed-day adjustment**, as today.
- Every row keeps its ⓘ info text, with new info strings for the new rows.
- Auto-save and validation follow R3: valid drafts save, invalid drafts never save.

### 3.1 Validation (weight modes; see §9.5)

| Field | Rule | Error shown on |
| --- | --- | --- |
| Weights (My weights) | 2–40 values; each > 0 and ≤ 999.75; at most 2 decimals; no duplicates (sorted on save) | The offending row, or the list for its count |
| Weights (Steps) | start > 0; step in {0.5, 1, 1.25, 2, 2.5, 5}; top > start; top − start divisible by step; 2–40 weights generated | Start, Step or Top |
| Reps per set (Weight) | 1–100 | Reps per set |
| Rep range (Reps then weight) | 1 ≤ min < max ≤ 100 | Min or Max |
| Starting weight | one of the current weights | Starting weight |
| Starting reps | within the rep range | Starting reps |
| Each hold | its weight is in the list; its reps are in the range; "for" ≥ 0; no two holds on the same position; at most 8 holds | The hold row |
| On-time window, missed-day adjustment | as today (R3) | As today |

A remap (§9.2) runs before validation, so after an edit the starting weight and the holds already point at valid weights, or have been dropped with the save. Tests cover partial edits (a draft with one invalid row doesn't save, and fixing it does), duplicate weights, a step that doesn't divide the range, and a hold on a removed weight.

**Current tab:**
- In weight modes, "Current reps" becomes **Current weight** (a stepper that moves along the list) plus **Current reps** (Reps then weight, within the range).
- Editing them sets the position; the hold count resets only if the position changes, as today.
- Streaks and Last check-in are unchanged.

**App settings:** the list's ⚙ opens **Settings**: Appearance (System / Light / Dark, as today) plus **Units: kg | lb**, the default for new weight workouts. Existing workouts keep their own unit.

## 4. Screens in use (proposed, following the decisions)

- **Entry screen:**
  - The reps column is replaced by a **prescription card**: "16 kg" large, "3 × 10 reps" below it.
  - The check-in highlight flashes the card: primary when it goes up (reps or weight), error when it goes down, and neutral on a hold (rev 12 and rev 20 colours).
  - TalkBack hears "16 kilograms, 3 sets of 10 reps".
- **History chart (user, option A):**
  - Weight is a **step line**, with the y-axis in the unit.
  - Tapping a point shows "16 kg × 10".
  - In Reps then weight the line is flat while reps climb, then steps up.
  - The x-axis follows rev 21 (it starts at the first shown check-in).
  - Which points it shows, and the after-switch empty state, are in §9.4.
- **List tile:** the sparkline plots weight as a step line. The byline is unchanged ("N× this week").
- **Timer:**
  - The centre number is the **reps** for the set, as today.
  - The weight shows above it in smaller text, e.g. "16 kg", during work and in the rest preview.
  - The Sets/Elapsed row is unchanged, and Timer Only is unaffected.
- **Voice (user):** at the start of each work set, it says "16 kilos, 10 reps" (localised plurals, with the unit spoken in full). At the same weight, later sets say just "10 reps".
- **Notification:** "Curls · Work · Set 2/3 · 16 kg".

## 5. Storage (proposed)

**Room v7** (the migration `6 → 7` adds columns with defaults, so no row is rewritten).

`entry` gains:

| Column | Type | Meaning |
| --- | --- | --- |
| `progress_mode` | TEXT NOT NULL DEFAULT 'REPS' | REPS / WEIGHT / REPS_THEN_WEIGHT |
| `weight_unit` | TEXT NULL | KG / LB; NULL = follow the app default |
| `weights_kind` | TEXT NOT NULL DEFAULT 'STEPS' | STEPS / LIST |
| `weight_steps` | TEXT NOT NULL DEFAULT '' | "start:step:top" in hundredths; '' = default 2000:250:6000 |
| `weight_list` | TEXT NOT NULL DEFAULT '' | "800,1200,1600" in hundredths |
| `weight_holds` | TEXT NOT NULL DEFAULT '' | weight-mode holds by value, "weight:reps:for" in hundredths (§10 note 2) |
| `reps_per_set` | INTEGER NOT NULL DEFAULT 10 | Weight mode |
| `rep_min` / `rep_max` | INTEGER NOT NULL DEFAULT 8 / 12 | Reps then weight |
| `start_weight` / `start_reps` | INTEGER NULL | starting point; NULL = the lightest weight / rep_min |
| `fresh_start` | INTEGER NOT NULL DEFAULT 0 | 1 after Start fresh until the next Counter check-in, which is performed at the start (§10 note 13) |

- `holds` keeps the Reps holds. Weight-mode holds are stored by value in `weight_holds` (§10 note 2), and the engine sees them as positions.
- In weight modes, `total` stores the **position**. The legacy `starting_total`, `floor` and `cap` stay for Reps mode only.
- `check_in` gains `weight` (INTEGER NULL, hundredths), `reps` (INTEGER NULL) and `unit` (TEXT NULL). They are written by the check-in transaction in weight modes and stay NULL for Reps and Timer Only, so history never depends on later edits to the weights.
- The app-wide unit default lives in AppPreferences (`weight_unit_default`, KG unless the locale is US/LR/MM, then LB) **(proposed)**.
- The tests follow claude.md's Room rule: a MigrationTestHelper test plus an in-memory SQL test.

## 6. Testing

- **Pure:**
  - `ProgressionScale` round trips for each mode;
  - `missFloor`, including that Reps mode is identity;
  - the RepProgression rules on weight positions, using the simulation in the brainstorm (curls, 8/10/12/14/16 kg, 8–12 reps): the climb, the reset to 8 at each new weight, −1/−3 misses, a hold, the cap, and the gentler-miss case;
  - the weight list from steps, the codecs, and unit conversion with rounding.
- **Storage:** the v6→v7 migration (existing rows become REPS with nothing changed), history rows carry weight, reps and unit, and switching mode keeps the history.
- **UI (Robolectric):**
  - the mode switch and its confirm;
  - each weight-mode page section and its validation errors;
  - the prescription card;
  - the chart step line and its point label;
  - the timer weight label and the voice text;
  - the ⚙ Units choice.
- **Existing tests stay green**: Reps mode must not change.

## 7. Delivery (proposed)

Three PRs, each releasable on its own:

1. **Model and storage:** `ProgressionScale`, `missFloor`, Room v7, history columns, unit preference. Invisible in the UI; all existing behaviour unchanged.
2. **Settings:** the Progress by switch, the weight sections, the Current tab, and ⚙ Settings with Units. PR 2 and PR 3 ship together as 0.2.0 (§10 note 23).
3. **Screens in use:** the prescription card, chart, tile, timer label, voice and notification. Its strings ship with their es / zh-rCN / hi translations; PR 2's ship with PR 2 (§10 note 24).

## 8. Out of scope

- Per-set different weights (pyramids, drop sets).
- Plate math.
- A rest-only timer.
- Logging actual reps done versus prescribed.
- Bodyweight added to the load.
- Importing weights from other apps.

## 9. Review findings (appended 2026-10-05)

This design is strong overall, but a few risks remain before implementation. These are the main issues to resolve in code and tests.

### 1) `missFloor()` ordering is not yet fully specified against holds and the position ladder

The draft states that a miss never drops more than one weight and defines `missFloor(position)` as the only engine change, but it does not say whether `missFloor()` is applied before or after hold evaluation and before or after cap/min checks. That matters in Reps then weight when a miss lands on the same weight but a different rep value, or when a hold sits exactly on the new lower weight.

Suggested resolution: define the exact ordering as `penalty -> missFloor -> hold evaluation -> cap/min clamp -> persistence`, and add a table-driven test for a miss that lands exactly on an active hold, a miss that crosses a weight boundary, and a no-op miss on the lightest rung.

**Resolution (adopted, with one change):** the order follows today's engine, where the clamp comes *first*, not last. A clamp after the hold check could land on a position whose hold count was decided for a different one. On a miss:

1. `start = clamp(position, min, max)`, as `RepProgression` does today. The config may have changed since the last check-in.
2. `raw = start − penalty`, using today's missed-day formula.
3. `next = max(raw, missFloor(start), min)`. `missFloor` is taken from the position **before** the miss, so "at most one lighter weight" is measured from where you were.
4. `holdCount = 1` if `next` is an active hold, else 0. A miss landing on a hold restarts it, as today.
5. Persist `next` and `holdCount`, and log the history point.

Tests (table-driven, using the curls ladder 8/10/12/14/16 kg × 8–12): a miss inside the same weight; a miss crossing one weight boundary; a long miss capped at one weight; a miss landing exactly on an active hold; a miss on position 0 (no-op); and Reps mode unchanged (`missFloor` = identity).

### 2) The remap-by-value rules are under-specified for edited weight lists and rep ranges

The spec says the saved position and every hold are remapped by value when the weight list or rep range changes, but it does not specify a deterministic tie-break when a removed weight is exactly at the current value or when a saved value falls between two adjacent weights after rounding or a step change. This can cause unstable behaviour and different users seeing different “current weight” values after a save.

Suggested resolution: lock the remap contract to a single algorithm: nearest lower valid weight, or lightest if none exists; for Reps then weight, clamp reps into the new range and re-map the weight by the nearest lower valid weight after the clamp; then drop holds whose resolved position no longer exists. Document this rule in the domain model and include tests for removal at the current value, near-boundary edits, and duplicate-value lists.

**Resolution (adopted):** one remap algorithm, used for the current position and for each hold:

1. **Weight:** the new weight index is the largest index whose weight is ≤ the old weight value, compared as exact integer hundredths. An exact match keeps the same weight. If no weight is ≤ the old value, use index 0 (the lightest).
2. **Reps** (Reps then weight): `reps = clamp(oldReps, newMin, newMax)`.
3. **Position** = `weightIndex × span + (reps − newMin)`, or just `weightIndex` in Weight mode.
4. **Holds:** a hold whose exact weight value is no longer in the list is **dropped**. The others remap by steps 1–3. After remapping, holds that land on the same position are de-duplicated, keeping the first.
5. **Hold count:** reset only if the current (weight value, reps) pair changed.

Ties and rounding can't arise. Values are exact integer hundredths, a list can't contain duplicates (the validator rejects them, see §3.1), and a unit change converts the list, the current weight and every hold with the same rounding (nearest 0.25) *before* the remap. Tests: removing the current weight; removing a weight below and above it; a step change that skips the current value; a rep range that shrinks under the current reps; a hold on a removed weight; and two holds colliding after a remap.

### 3) The `NULL` semantics for `entry.weight_unit` and history units are not fully nailed down

The draft says `weight_unit` is `NULL` = follow the app default, while the history rows also carry `weight`, `reps` and `unit`, and the app-wide unit default lives in `AppPreferences`. That is sensible, but it leaves a few edge cases unresolved: what does `NULL` mean for an old migration row, for Timer Only entries, for a row with no weight data, and for history displayed after the workout’s default unit is changed? The expected display and conversion rules must be explicit before the migration is implemented.

Suggested resolution: define one source-of-truth policy: `entry.weight_unit` is nullable only for “follow default,” never for historical rows; `check_in.unit` is always populated for a weight-mode entry and never for Reps/Timer Only; and the display layer converts based on row unit, with app default used only for new entries. Add migration tests covering legacy `NULL` rows and toggling the default unit.

**Resolution (adopted):**

- **`entry.weight_unit`:**
  - It is NULL only for workouts that have **never** been in a weight mode: every Reps and Timer Only row, and every row after the v7 migration. The unit means nothing for them.
  - The first time a workout switches into a weight mode, the **current app default is written** to its row. From then on the workout owns its unit, and changing the app default never changes an existing weight workout.
- **`check_in.unit`:** it is non-NULL **exactly when** `weight` is non-NULL, which means a check-in recorded in a weight mode. It is NULL for every Reps and Timer Only check-in, and for every pre-v7 row.
- **Display:** a point's weight converts from its **own** unit into the **workout's current** unit, for the chart and for the tap label. The app default is used only to seed new weight workouts.
- **Tests:** the migration leaves every unit NULL; switching into a weight mode writes the default; changing the default afterwards leaves the workout alone; and a point recorded in lb displays correctly on a workout now in kg.

### 4) Mode switching currently preserves history but not a single, testable history contract

The design says a mode switch asks “Start fresh? This workout restarts at its starting weight. Your streaks and history are kept,” and that the history is kept regardless of the mode. That is a reasonable UX choice, but the spec does not say exactly how old history is displayed when the mode changes: does the chart show a step line in the current mode only, or every historical point with its own stored unit/weight/reps? Without that contract, the UI, chart labels, and migration logic can deviate.

Suggested resolution: define a single rule in the domain/API layer: history is always displayed as the recorded value at the time of each check-in, regardless of the current mode; the current mode only affects the live position and the default settings. Then add a UI test covering a mode switch after several check-ins, and confirm the chart labels still show the original values rather than remapped ones.

**Resolution (adopted):** history always shows **what was recorded** at each check-in, never remapped by later edits or mode switches.

- **Which points the chart shows:**
  - In a weight mode, the chart plots the **weight points**, those with a non-NULL `weight`, as the step line.
  - In Reps mode, it plots the **rep-total points**: NULL `weight` with a non-NULL `total`, as today.
  - Points from the other mode family stay stored and reappear if you switch back.
- **"N× this week", streaks and the Timer Only calendar** count every check-in, whatever its mode.
- **The empty state:** if the current mode has no points yet, for example right after switching, the chart shows "No check-ins in this mode yet". This is one new string, with translations.
- **Test:** check in several times in Reps mode, switch to Reps then weight, and check in twice. The chart shows only the two weight points, with their recorded labels. Switch back to Reps, and the rep line returns unchanged.

### 5) The weight-mode settings screen needs a clear “invalid draft” and auto-save contract

The spec says valid drafts save and invalid drafts never save, but the weight-mode schema has several cross-field rules (list length, sorted values, duplicate rejection, rep-range ordering, start weight in range, start reps in range, and hold values referencing valid weights). Those rules are spread across sections and not consolidated into one validation matrix. The risk is that a settings change can silently save a partially invalid weight configuration.

Suggested resolution: add one validation table for every field in Weight and Reps then weight — list length, sorting, duplicates, ranges, valid start values, and hold references — and require the UI to show inline errors before the draft can be saved. This should be paired with tests for partial edits, duplicate weight lists, and hold values that no longer exist after a save.

**Resolution (adopted):** the validation matrix is §3.1 below. Every rule shows inline on its field, and a draft with any error never saves (R3).

These items are manageable design tensions, not blockers to the overall direction, but they should be resolved before the model and migration work starts so the implementation is stable and testable.

## 10. Implementation notes (PR 1, 2026-10-05)

Recorded from `docs/superpowers/plans/2026-10-05-weight-model.md`. Note 13 records the user's ruling (option A, 2026-10-05); notes 17–22 were added while the plan was carried out.

1. `missFloor` is "no floor" (`Int.MIN_VALUE`) in Reps mode, not the identity: with the identity, §9.1 step 3 would cancel every Reps penalty.
   - §2 says `missFloor` is "identity for Reps mode", but §9.1 step 3 is `next = max(raw, missFloor(start), min)`. With the identity, `missFloor(start) = start`, so every Reps penalty would be cancelled.
   - Ruling: `ProgressionScale.Reps.missFloor` returns `Int.MIN_VALUE`. Then `max(raw, MIN, floor)` equals today's `max(floor, total − penalty)` exactly.
2. **Weight-mode holds are stored by value in a new column, `entry.weight_holds`** (TEXT NOT NULL DEFAULT ''), as `weight:reps:for` items. This is a deviation from §5, which said "`holds` keeps storing positions". Three reasons:
   - `HoldsCodec` and `SettingsValidator` reject `at < 1`, but level 0 (the lightest weight × min reps) is a valid hold.
   - One shared column would read a Reps hold (64) as level 64 after a switch, and a Weight level as a different Reps-then-weight level. That breaks "each mode's settings are remembered".
   - Stored values make the §9.2 hold remap a plain filter.

   The engine still sees **level** holds: `engineConfig()` converts them. `ProgressionConfig.holds` (Reps) is untouched. The Hold switch (`hold_enabled`) is shared by all modes.
3. **A level can be 0.** `validTotal` reads a stored total below 1 as NULL. It gains a `min` parameter, which is 0 in a weight mode. A NULL total in a weight mode reads as the **start level**, so the existing "NULL = untouched" rule carries over: Reset progress and Start fresh both write NULL.
4. **The remap lives in a new `setWeightConfig(id, WeightConfig)`, not in `setProgression`.**
   - The existing Reps page builds its save from `ProgressionDraft.toConfig()`, which rebuilds a `ProgressionConfig` from the Reps fields only. If `setProgression` wrote the weight group, every Reps-page save would wipe it.
   - So `setProgression` stays byte-for-byte the same, and never writes the mode or the weight columns. `setWeightConfig` writes the weight group, with the §9.2 remap and validation.
5. **`switchMode(id, mode, defaultUnit)` takes the app default as a parameter.** The caller (PR 2's ViewModel) reads `AppPreferences.weightUnitDefault`, which keeps the repository free of DataStore.
   - Start fresh means: total NULL (the new mode's start level), hold count 0, `fresh_start = 1` (note 13), and `weight_unit = COALESCE(weight_unit, defaultUnit)` when entering a weight mode. Streaks, the last check-in and the history are kept.
   - Switching to the current mode is a no-op.
6. **What a weight-mode `check_in` row stores.** Its `total` is the **level**, so a Workout point keeps a non-NULL total, and PR 3 tells the mode families apart by `weight IS NOT NULL` (§9.4). `weight`, `reps` and `unit` are written only for a Counter (`WORKOUT`) check-in in a weight mode. They stay NULL for Reps and for Timer Only, including a Timer Only entry whose stored mode is a weight mode.
7. **Validation is typed and string-free.** `WeightValidator.validate(config, mode): List<WeightProblem>` covers every row of §3.1. PR 2 maps each problem to a field and a string. "At most 2 decimals" is automatic with integer hundredths; parsing input is PR 2's job.
8. **A unit change can merge weights, and Steps become My weights.**
   - §9.2 says ties "can't arise". They can after a unit change: 1.00 and 1.25 lb both round to 0.50 kg. `WeightConversion.convert(config, to)` keeps the first of any merged weights, and clamps every value to 25..99 975.
   - A converted step (2.5 kg = 5.51 lb) isn't one of the step choices, so a converted Steps config becomes a **My weights** list of the converted values. The stored `steps` value is left as it was.
9. **Hold-count reset on a weight save** (§9.2 step 5, read literally): the count resets when the current load (weight value, reps per set) changes. In Weight mode, that includes a reps-per-set edit. It also resets when the weight-hold list changes (order counts, as in rev 16 §4), or when the set of active holds changes (a hold that lands on the top level is inactive, as in Reps).
10. **Two holds in one place.** In Weight mode, two holds on one weight with different reps sit on the same position. The validator reports the later one, and read-repair keeps the first. In Reps then weight they are different positions.
11. **A weight mode with no unit** can only come from a corrupt row, because §9.3 writes the unit on the first switch. Such a row reads as KG and is logged, so check-ins always record a unit. An unknown unit on a `check_in` row reads as NULL and is logged too.
12. **Where things live:**
    - The weight codecs go in `data/WeightCodecs.kt`, next to `HoldsCodec`, following the existing pattern even though they are pure.
    - `defaultWeightUnit(country)` is pure domain code. `AppPreferences` takes a `country: () -> String` parameter, which defaults to `Locale.getDefault().country`.
13. **Ruling (user, 2026-10-05, option A): the first check-in after Start fresh is performed AT the starting point.** It does not go +1, and the streak continues.
    - **Storage:** v7 also adds `entry.fresh_start INTEGER NOT NULL DEFAULT 0`. Every existing row reads 0, so nothing changes. `CounterState.freshStart` maps to and from it.
    - **Engine (`RepProgression.checkIn`):** when `state.freshStart` is true and the outcome is not AlreadyToday:
      - **On time:** the position stays at the start (no +1). The streak goes +1 and `lastCheckIn = now`. The hold count follows `startingHoldCount(position)`, so if the start is itself a hold, that day counts as day 1.
      - **Missed:** the position stays at the start, with no penalty (`Missed(0)`). The streak resets to 1, as for any miss.
      - **First** (no last check-in): unchanged.
      - A recorded Counter check-in returns `freshStart = false`. With the flag false, the engine behaves byte-for-byte as before, in every mode including Reps.
    - **Repository:** `switchMode` writes `fresh_start = 1` with total NULL and hold count 0; `checkIn` writes the engine's returned flag in the same transaction (note 18); `resetProgress` and `overwriteCounter` write 0 (Reset clears the last check-in, so the First rule covers it; an explicit Current edit means the user chose the position); `duplicate` writes 0.
14. **Cosmetic:** §9.5 says "the validation matrix is §3.1 below", but §3.1 is above it.
15. **The brainstorm's simulation isn't in the repo.** The expected positions in the engine and remap tests are computed from §2 and §9.1. They agree with §2's one example: 16 kg × 8 plus a 6-day absence lands on 14 kg × 8. "A 6-day absence" means the check-ins are 168 h apart: the penalty is 6, the raw level is 14 (12 kg × 12), and the floor is 15 (14 kg × 8).
16. **Error precedence in `overwriteCounter`:** a total of exactly 0 is now checked inside the transaction (0 is a valid level in a weight mode). So `overwriteCounter(missingId, total = 0, …)` throws `EntryNotFound` instead of `IllegalArgumentException`. Everything else is unchanged: Reps still rejects 0, and a negative total is still rejected up front.
17. **Timer only keeps `freshStart`** (ruling, batch 1 review). A Timer only check-in moves only the streaks and the date (R4 §3.1), so it also keeps the flag. A Counter → Timer only → Counter round trip after Start fresh still performs the first Counter check-in at the start.
18. **`checkIn` persists the flag** (batch 3 review). The check-in transaction writes the engine's returned `freshStart` (`EntryDao.setFreshStart`) alongside the counter and the history point. Without it, `switchMode` would set the flag and nothing would clear it, so every later check-in would stay at the start.
19. **In-memory migration tests use new query text after an ALTER TABLE.** The framework connection caches each statement's column list, so re-running the same `SELECT *` after `ADD COLUMN` reports the old columns and makes a before/after comparison vacuous. The 4 → 5 and 6 → 7 tests query with distinct text after the migration.
20. **Revisions 27 and 28 apply to Reps mode only** (rebase onto main, 2026-10-05). Floor..cap are rep totals, but in a weight mode the total is a level on the ladder. So in a weight mode `overwriteCounter` never widens floor..cap (rev 27): it accepts a level in 0..top and rejects anything else (note 16). And `setProgression` never moves a weight-mode level into the new floor..cap (rev 28 rule 4). Both still work exactly as on main for a Reps-mode Counter entry.
21. **The hold count on a Progression save in a weight mode** (final review ruling). `setProgression` resets `hold_count` only when the shared Hold switch changes (`old.hold != new.hold`, `progressionHoldReset`). Reps-hold, floor and cap edits don't apply to a level, so they keep the count. Weight-hold edits go through `setWeightConfig`'s own rule (note 9). Reps mode is unchanged (`holdResetNeeded`). The Progression page compares its draft with the stored Reps fields only (mode and weight group normalised away), so an entry with a weight group still follows changes made elsewhere, such as the Current page's widening (rev 27).
22. **Release order** (final review). PR 2 makes `switchMode` reachable, but only PR 3 teaches the timer, the voice, the chart and the tile about levels. So either PR 2 and PR 3 ship in the same release, or PR 2 keeps the Progress by switch hidden until PR 3 lands. Also, `FakeEntryRepository.setWeightConfig` always remaps and skips validation, unlike Room: it never keeps an untouched (NULL) counter untouched, because the fake stores resolved totals. PR 2's ViewModel tests must not rely on it for "untouched stays untouched" or for rejecting invalid drafts; use `RoomEntryRepository` for those.

**PR 2 (settings UI, 2026-10-06)**, recorded from `docs/superpowers/plans/2026-10-06-weight-settings-ui.md`. Notes 38–41 record the user's rulings on that plan's open questions.

23. **Release (user, 2026-10-06):** PR 2 and PR 3 ship together as 0.2.0, so the Progress by switch isn't hidden (resolves note 22). PR 2 is not released alone.
24. **Translations ship with PR 2.** §7 put the translations in PR 3, but lint's MissingTranslation is an error, so every PR 2 string ships with es / zh-rCN / hi. PR 3 translates its own strings.
25. **Two ViewModels on the Progression page.** `ProgressionSettingsViewModel` keeps the Reps fields and the rows every mode shares (Hold switch, window, penalty) and saves through `setProgression`. The new `WeightSettingsViewModel` owns the mode and the weight group and saves through `setWeightConfig` and `switchMode`. Each has its own draft, validation and AutoSaver; the page's status line shows the worse of the two (Not saved: invalid, then Not saved, then Saved).
26. **Weight numbers** show "." as the decimal separator in every locale, like the penalty rate, and the edit dialog accepts "." or ",", with at most 2 decimals (`WeightFormat`). A value with 3 decimals keeps OK disabled with "Enter a number with up to 2 decimals" (`ValueInput.WEIGHT`).
27. **The unit is in the label, not the value:** "Start (kg)", "Starting weight (kg)". At 320 dp the stepper's value has room for "999.75" but not "999.75 kg". Pickers, notes and My weights rows show "22.5 kg" (`weight_value`). Test tags use the label without the unit.
28. **One control picks a weight.** Starting weight, Step, each hold's weight and Current weight are stepper rows whose ± moves along the list and whose value opens a list picker (§3 asks for a picker for the start and a stepper for Current; this does both). Start and Top under Steps are free values: ± is one step, and the value opens the edit dialog.
29. **My weights:** each row's value opens the edit dialog, and the list sorts itself after each OK (§3.1 "sorted on save", done at once so the rows never disagree with the save). "+ Add weight" adds the last weight + the last gap; with fewer than 2 weights the gap is 2.5 (the default step), and an empty list starts at 20. It is disabled at 40. Switching Steps → My weights with an empty list seeds it with the steps' weights (at most 40); switching back keeps both.
30. **The field you edit wins, for weights** (revision 28 §6). In the draft, at once, with a note under the edited field:
    - **Start or Step edited:** Top moves to the start plus a whole number of steps, at or below the old top and at least one step above the start ("Top raised/lowered to …").
    - **Top edited at or below Start:** Start is lowered to Top − step ("Start lowered to …"). A Top typed in the dialog that isn't start + whole steps is snapped down to one (a clamp to the field's own values, not an override).
    - **Rep range and starting reps per set:** revision 28 rules 1–3 per set: a minimum at or above the maximum raises the maximum to minimum + 1; a maximum at or below the minimum lowers the minimum to maximum − 1; starting reps outside the range move the range; a range edit moves explicit starting reps into it.
    - **A ladder or range change** remaps the starting weight and the holds by value at once (§9.2), so the pickers never show a removed weight. It notes "Starting weight moved to …" (only for an explicit starting weight) and "N holds removed". Hold reps clamped into a new range aren't noted; the hold row shows them.
    - **More than 40 weights** stays an error on Top (Steps) or the list; nothing moves to fix it.
31. **`setWeightConfig` returns the moved current load** (`WeightMove.CurrentMoved?`, an API change from PR 1), revision 28 rule 4 for weights. In Weight mode only the weight counts (a reps-per-set edit is the edit itself, not a move); in Reps then weight, the weight or the reps. The page appends it to the note when the save lands, with revision 28's generation rule.
32. **Weight holds** are validated whatever the Hold switch says, because `setWeightConfig` validates them. A hold held for 0, or on the top level, shows "Hold disabled". "+ Add hold" adds the first weight above the last hold's (or the starting weight) that isn't the heaviest and isn't held, at the lowest reps, for the last hold's count (or 4); failing that the first free weight below the heaviest; with none free it's added anyway for the validator. In weight modes the Hold switch's ⓘ talks about weights (`info_hold_weight`).
33. **Start fresh:** choosing another mode asks first ("Start fresh?", with a Reps body when switching to Reps). On confirm, both pages' pending writes are flushed, then `switchMode` runs with `AppPreferences.weightUnitDefault`, then the weight draft takes the stored weight group, dropping unsaved edits. Timer only entries don't show Progress by (their Progression page is window-only, R4 §4.6).
34. **Changing a workout's unit** asks first ("Switch to lb?"), then converts the draft with `WeightConversion.convert` (Steps become My weights, note 8) and saves at once. History is untouched (§9.3).
35. **Current page in a weight mode:** Current weight (a picker along the ladder) and, in Reps then weight, Current reps per set, bounded to the rep range (§3: "within the range"). Neither widens anything (note 20). The level is valid in 0..top (`SettingsValidator.currentState(levels = …)`, `FieldMessage.NotOnLadder`). Reset progress says "Your weight and reps will return to the starting point…".
36. **⚙ › Units:** under Appearance, "Kilograms (kg)" / "Pounds (lb)" radio rows with an ⓘ. A choice saves at once and closes the dialog, like Appearance. `AppPreferences` implements a new `WeightUnitDefaults` interface, which Hilt binds for the settings.
37. **Progress by** is a segmented row whose segments are at least 48 dp tall, drop the check icon and wrap their labels to two lines, so all three fit at 320 dp (checked in a test).
38. **Reset to defaults in a weight mode** (ruled by the user, 2026-10-10: as recommended — Tasks 8 and 10 run as written). In a weight mode, Reset to defaults resets the rows every mode shares (window 36 h, penalty 19.5, Hold switch on) and the weight group to `WeightConfig(unit = <the workout's unit>)`, and keeps the hidden Reps fields, since every mode's settings are remembered (§2).
39. **No Sets-changed prompt in a weight mode** (ruled by the user, 2026-10-10: as recommended — Task 13 runs as written). In a weight mode the reps are per set, so a Sets change never moves them: the prompt never fires.
40. **A Current edit keeps the fresh-start flag unless the level changes** (ruled by the user, 2026-10-10: as recommended; amends note 13 — Task 14 runs as written). `overwriteCounter` clears `fresh_start` only when the stored effective total changes; a streak or last-check-in edit that keeps the level leaves it set, so the next check-in is still performed at the start.
41. **The penalty's label and ⓘ in a weight mode say "per step"** (ruled by the user, 2026-10-10: as recommended — Tasks 6 and 10 run as written). The label reads "Missed-day adjustment (hours per step)", with an ⓘ that explains a step and the one-weight limit.
42. **Two drafts, one row: the write-order contract.** The code already guarantees this; the plan states it and tests it:
    - `setProgression` and `setWeightConfig` each read the row and write it inside one Room transaction, and Room runs transactions one at a time.
    - Their columns are disjoint except `total` and `hold_count`:
      - `setProgression` writes `starting_total`, `floor`, `cap`, `holds`, `hold_at`, `hold_for`, `hold_enabled`, `window_hours` and `penalty_hours_per_rep` (`EntryDao.setProgression`). It moves `total` only for a Reps-mode Counter (`EntryRepository.setProgression`).
      - `setWeightConfig` writes `weight_unit` … `start_reps` (`EntryDao.setWeightConfig`). Its `total` comes from the row it read in its own transaction: the remapped level, or the unchanged Reps total (`EntryRepository.setWeightConfig`).
      - Both write `hold_count` as `CASE WHEN reset THEN 0 ELSE hold_count END`.
      - `switchMode` writes only `progress_mode`, `total`, `hold_count`, `fresh_start` and `weight_unit` (COALESCE).
    - So the two AutoSavers need no order between them. Whichever lands second sees the first's result, and neither can undo the other's fields. Each AutoSaver keeps its own writes in order.
    - Every action that leaves the page or switches flushes both drafts:
      - a page change and an exit, through the pager's `flushAll` (and `onCleared` → `flushIn(appScope)`);
      - Start fresh, through the dialog's `beforeSwitch` (the Reps draft), then `confirmMode`'s own flush before `switchMode`;
      - a unit change, which is just a weight save.
    - Tests: Task 11, `TwoDraftsTest` (both orders of flush, Start fresh, unit change, and the shared Hold switch next to the weight holds).
43. **A unit change is one data contract.**
    - **The draft:** the page converts it with `WeightConversion.convert(draft, to)`. That converts every ladder weight (Steps become a My weights list, note 8), the starting weight and each hold's weight, to the nearest 0.25; merged values are kept once. Reps and every other field are unchanged.
    - **One call, one transaction:** the page saves with a single `setWeightConfig` call (`EntryRepository.setWeightConfig`). Inside it, the stored group is converted the same way first. Then the start, the holds and the current level are remapped by value, validated and written in one UPDATE.
    - **The current level** keeps its weight value, by value (ruled 2026-10-10): the current load is found on the stored ladder before converting it, so a merge that maps it onto an equal post-conversion value is never reported as `CurrentMoved`, even though the level index shifts; only a genuinely different post-conversion value is.
    - **`hold_count`** is kept unless `weightHoldResetNeeded` says otherwise: the current load changed, or a merge dropped a hold.
    - **`fresh_start`** is never written by `setWeightConfig`.
    - **History:** `check_in` rows are never written (§9.3: history keeps its own unit).
    - **An invalid converted draft** (e.g. a merge leaves one weight) follows R3: it shows its error and never saves, so the stored row stays wholly in the old unit. The next valid draft is converted and saved as above. A stored row can't mix units, because the stored group's conversion and the write share one transaction.
    - **Tests:** PR 1's `a unit change converts before the remap and keeps the rung and the hold count` (RoomEntryRepositoryTest:984); Task 5 (fresh start and history untouched); Task 8 (an invalid conversion never saves).
44. **A Current edit in a weight mode.**
    - **The level it writes:** a Current weight `w` with the current reps `r` gives `levelOf(w, r)`. A Current reps `r` with the current weight `w` gives the same. In Weight mode the reps are fixed.
    - **"The level changed"** means the new total ≠ the stored effective total (a NULL total reads as the start level), an integer compare in `overwriteCounter`.
    - **`hold_count`** resets exactly then (`counterHoldReset`, in `EntryRepository.overwriteCounter`).
    - **`fresh_start`** is cleared only when the level changes (note 40, amending note 13): a streak or last-check-in-only edit leaves it set. Note 49 amends this further: when the stored total was NULL and the level didn't change, the write keeps it NULL too, not the resolved value, so it keeps following a starting total or weight that moves afterwards.
    - **No widening:** revision 27's widening never runs in a weight mode (`EntryRepository.overwriteCounter`, the `!ladder` check), and a level outside 0..top is rejected. So `starting_total`, `floor` and `cap` never change from the Current page in a weight mode.
    - **Tests:** Task 12 (page mapping and Room); Task 14 (the fresh-start rule); note 49's final-review fix.
45. **One `setWeightConfig` save, one order** (`EntryRepository.setWeightConfig`):
    1. sort the submitted list;
    2. convert the stored group to the submitted unit;
    3. remap the starting weight, the holds and the stored current level by value against the converted old ladder (§9.2: off-ladder holds dropped, collisions de-duplicated keeping the first);
    4. validate;
    5. decide the hold-count reset;
    6. write the group, the level and the hold count in one UPDATE.

    The stored row depends only on the stored row and the submitted values. The page's draft-side remap (note 30) gives the same answer as step 3. So a draft submitted already remapped and one submitted stale and unsorted store the same row and return the same move. Across saves, each save remaps against the ladder stored at that moment. Tests: Task 5.
46. **When the app default unit is read:**
    - **One place:** `WeightSettingsViewModel.confirmMode`, just before `switchMode`, which writes it only as `COALESCE(weight_unit, :unit)` (EntryDao.kt:122–126).
    - **Nothing else writes it to a workout:** `create` leaves `weight_unit` NULL, `setWeightConfig` keeps the stored unit when the draft has none (EntryRepository.kt:299), and ⚙ › Units never touches existing rows.
    - **A workout keeps its unit:** once it has been in a weight mode, it keeps its unit through any later switch.
    - **Tests:** PR 1's `a new entry has no unit until its first switch into a weight mode` (RoomEntryRepositoryTest:1108); Task 5 (`switchMode keeps a unit the workout already has`); Task 9.
47. **Transition matrix.** Every cell is backed by the named test.

    | Transition (call) | Weight group | Current level | `hold_count` | `fresh_start` | Tests |
    | --- | --- | --- | --- | --- | --- |
    | Switch mode (`switchMode`) | unchanged; unit = COALESCE(unit, app default) | NULL (the new mode's start) | 0 | 1 | PR 1 `switchMode starts fresh…` (RoomEntryRepositoryTest:1025); Task 5 `switchMode keeps a unit…`; Task 9 |
    | Reset to defaults, weight mode (`setWeightConfig(WeightConfig(unit))`, note 38, ruled 2026-10-10 as recommended) | defaults, unit kept | remapped by value (nearest lower, else the lightest), noted | 0 when the load or the holds changed, else kept | unchanged | Task 5 `matrix: reset to defaults…`; Task 8 |
    | Unit change (`setWeightConfig(convert(…))`) | every weight, the start and the holds converted; Steps → My weights | same weight value | kept unless a merge changed the load or holds | unchanged | PR 1 (:984); Task 5 `matrix: a unit change…`; Task 8 |
    | Steps → My weights, same values | kind LIST, list = the steps' weights | same | kept | unchanged | Task 2; Task 5 `matrix: Steps to My weights…` |
    | Removing a weight that isn't current | that weight gone | same value (level renumbered) | kept | unchanged | Task 5 `matrix: removing a weight…` |
    | Removing the current weight | that weight gone; an explicit start on it moves down | nearest lower weight, noted | 0 | unchanged | Task 5 `matrix: removing the current weight…`; Task 3 |
    | A hold dropped by a remap | the hold gone | unchanged | 0 | unchanged | Task 5 `matrix: a hold whose weight is removed…`; Task 3 |
    | Current edit (`overwriteCounter`) | unchanged | the edited level | 0 iff the level changed | cleared iff the level changed (note 40), else kept | Task 12; Task 14 |

48. **The Progress by and unit confirm prompts are not restored after process death.** If the process is killed while "Switch to Weight?" / "Switch to lb?" is open, the dialog simply closes on return and nothing is saved; the draft underneath is whatever was last auto-saved.
49. **`overwriteCounter` keeps a NULL total NULL when the level doesn't change (final review fix, amends note 40).** A streak-only or last-check-in-only Current save was writing the resolved effective total back as a concrete value even when the row was still untouched (NULL): `fresh_start` correctly stayed set (note 40), but the concrete value then got remapped by a later `setWeightConfig`/`setProgression` instead of following a start that moved afterwards (e.g. Start fresh into Weight, a streak-only save, then a changed start weight landed on the *old* start, not the new one). Fixed in both `RoomEntryRepository.overwriteCounter` and `FakeEntryRepository.overwriteCounter`: when the stored total is NULL and the new total equals the resolved effective total, the write keeps it NULL. Reps mode has the same fix in effect through `startingTotal`/`setProgression` (rule 4), since `setProgression` already treats a NULL total as untouched — it only needed `overwriteCounter` to stop clobbering it. Tested in `RoomEntryRepositoryTest` (a streak-only save followed by a start-weight change in Weight mode, the same in Reps mode via `startingTotal`, and a real level change still storing a concrete value and clearing `fresh_start`).
