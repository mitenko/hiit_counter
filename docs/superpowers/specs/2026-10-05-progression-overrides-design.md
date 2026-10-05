# REPKIT — Progression overrides: the field you edit wins (spec revision 28)

Status: Approved by the user in chat (2026-10-05).

Amends R3 §5–§6 (the Progression and Current pages and their saves), rev 16 §3 (the hold hint) and rev 27 (which it extends from Current reps to the cases below).

## 1. Rule
When one value conflicts with another, **the field you're editing wins, and the value it pushes against moves to fit.** A short note says what moved. Counter entries only: a Timer only entry hides these fields.

| # | Where | Edit | Behaviour | Note |
| --- | --- | --- | --- | --- |
| 1 | Progression | Starting reps above the max / below the min | The max is raised / the min lowered to the starting reps, and the draft saves | "Maximum reps raised to N" / "Minimum reps lowered to N" |
| 2 | Progression | Minimum reps raised above starting reps | Starting reps are raised to the new minimum (and the maximum too, if the minimum passed it) | "Starting reps raised to N" (· "Maximum reps raised to N") |
| 3 | Progression | Maximum reps lowered below starting reps | Starting reps are lowered to the new maximum (and the minimum too, if the maximum passed it) | "Starting reps lowered to N" (· "Minimum reps lowered to N") |
| 4 | Progression | Min or max moved past the stored current reps | The current total moves to the nearer limit in the same transaction as the save | "Current reps raised/lowered to N" |
| 5 | Current | Current streak set above the best streak | The best streak is raised to match, and the draft saves | "Best streak raised to N" |
| 6 | Progression | A hold ends up outside min..max | The hint says why; the hold still saves and stays inactive while outside | "Hold at 64 is above the maximum (60)" / "Hold at 40 is below the minimum (48)" |

**Cascades:** one edit can move several values; the note lists every move on one line, joined with " · " (a separator in code, not translated): "Starting reps raised to 50 · Current reps raised to 50".

**Unchanged errors:** a workout longer than 2:00:00, a last check-in in the future, minimum < 1, every hold rule (duplicates, more than 8 holds, ranges), and a **best streak lowered below the current streak** (no rule lowers the current streak, so that stays "Must be ≥ current streak").

## 2. Domain (`domain/ProgressionOverrides.kt`, pure)
- `Move`: a sealed set of the moves an override can cause — `StartingRaised`, `StartingLowered`, `CurrentRaised`, `CurrentLowered`, `BestStreakRaised`, plus rev 27's `RangeChange.RaisedMax` / `LoweredMin` (now `Move`s too). Only possible directions exist, so each has its own string.
- `ProgressionConfig.resolveFor(edited: ProgressionField?): Resolution(config, moves)` — rules 1–3. `ProgressionField` is `STARTING_TOTAL`, `FLOOR` or `CAP`: the field the user just changed, threaded from its stepper or dialog. With no edited field, or an edited value below 1, nothing moves and the validator's errors stay (`AtLeastFloor`, `AtLeastStartingTotal` and `AtLeastOne` remain for drafts that arrive out of order, such as a restored one).
- The same final values reached through different fields resolve differently: starting 50 with maximum 49 lowers starting reps to 49 if the maximum was edited, and raises the maximum to 50 if starting reps were.
- `ProgressionConfig.totalMove(total)` — rule 4: `CurrentLowered(cap)` above the cap, `CurrentRaised(floor)` below the floor, else null.
- `resolveStreaks(best, current, edited: StreakField?)` — rule 5.
- `FieldMessage.HoldOutsideRange(at, bound, isAbove)` — rule 6, for a hold above the cap or below the floor. `HoldDisabled` ("Hold disabled") stays for a hold held for 0 or at the cap itself (inactive, but neither above nor below the range). Outside the range takes precedence.

## 3. Repository
- `EntryRepository.setProgression` now returns `Move?`. In the same transaction as the progression write, a Counter entry's **stored** total outside the new floor..cap moves to the nearer limit; the hold count is then reset (`counterHoldReset`: the total changed), on top of `holdResetNeeded`.
- A NULL total (untouched) stays NULL: it follows the starting total, which rules 2 and 3 already keep inside the range.
- A **Timer only** entry's stored total is never moved (it isn't shown or used, and switching the type back keeps every value). Its Progression page shows only the window anyway.

## 4. UI
- **Progression page.** `ProgressionPageContent` callbacks take the edited field: `(ProgressionField?, transform)`. The ViewModel resolves the draft at once (the Starting reps stepper shows 50 as soon as the minimum goes to 50) and auto-saves the resolved config.
  - `note: ProgressionNote(at, moves)` shows under the edited field (`progression_note`); after Reset to defaults (`at` null) it shows under that button. The draft's moves show at once; a current total the save moved is appended when the save lands.
  - The note clears on the next edit (any field) and on a page change. Like rev 27's range note, a save only adds to it if no edit or page change happened since the save was queued (generation token, captured with the queued value, so a page change's flush-then-clear drops it even if the write starts later), so a late save can't bring a dismissed note back.
- **Current page.** The callbacks take `(StreakField?, transform)`. A current streak above the best raises the best in the draft at once; `streakNote` shows "Best streak raised to N" under Current streak (`streak_note`) until the next edit or a page change. It comes from the draft, not the save, so no late write can revive it.
- Both notes use rev 27's look: a centred `bodySmall` line in `onSurfaceVariant`, a polite live region (`MoveNote`).

## 5. Strings
New in `values/` and `values-es`, `values-zh-rCN`, `values-hi`: `moved_starting_raised`, `moved_starting_lowered`, `moved_current_raised`, `moved_current_lowered`, `moved_best_streak_raised`, `hint_hold_above_max`, `hint_hold_below_min`. Rev 27's `range_raised_max` / `range_lowered_min` are reused for the max and min moves.

## 6. Weight modes
Weight progression (rev 26, not built yet) must follow the same rule in its PR 2: the field you edit wins, the value it pushes against moves to fit, and a note says what moved.

## 7. Known edges
- If a write that moved the current total is superseded by a newer edit before it lands, the total's move isn't noted (the newer save finds the total already inside the range).
