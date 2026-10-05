# REPKIT — Current reps widen the min/max (spec revision 27)

Status: requested by the user in chat (2026-10-05): "If current reps gets set to be more than Max, auto adjust Max reps. Same with minimum."

Amends R3 §6 (the Current page and its save) and rev 24 (the `OutsideFloorCap` message is removed).

## 1. Scope
- The **Current tab only**, for **Counter** entries. A Timer only entry hides its total, so its saves never widen the range.
- The Progression tab's own validation is unchanged: there, Starting reps must still be ≤ Maximum reps and ≥ Minimum reps.

## 2. Rule
- `ProgressionConfig.widenedFor(total)` (pure, `domain/RangeWidening.kt`) sets `floor = min(floor, total)` and `cap = max(cap, total)`; nothing else changes. A total inside floor..cap returns the same config.
- The starting total stays inside the widened range automatically.

## 3. Save
- `EntryRepository.overwriteCounter` does it in **one transaction** with the counter write:
  1. it reads the stored (effective) progression;
  2. it computes the widened config;
  3. if that differs, it writes it the same way as `setProgression` (holds codec, legacy hold columns mirroring the first hold), with `resetHoldCount = holdResetNeeded(old, new)`;
  4. then it writes the counter as before. The hold count is 0 when step 3 reset it or when `counterHoldReset` says so; otherwise it is kept.
- A raised cap can make a hold active (a hold at the old cap). `holdResetNeeded` then resets the hold count, as for any Progression edit.
- It returns a `RangeChange?`: `RaisedMax(to)`, `LoweredMin(to)` or null.

## 4. Current page
- The "Outside floor–cap; clamped at the next check-in" hint is gone (`FieldMessage.OutsideFloorCap` and `hint_outside_floor_cap` are removed). `SettingsValidator.currentState` no longer takes the progression.
- After a save that moved a limit, a note shows under Current reps:
  - "Maximum reps raised to %1$d" (`range_raised_max`);
  - "Minimum reps lowered to %1$d" (`range_lowered_min`).
- The note stays until the total is edited again or the pager changes page. Streak and date edits keep it.
- It is a polite live region, so TalkBack reads it.
- The note is only set when the saved total is still the one on screen.

## 5. Progression page sync
- The pager keeps the Progression page's ViewModel alive. Its draft now follows the stored progression while it has no unsaved edits: the draft's config equals the last stored config and no save is pending (the same rule as the Current page).
- So a cap raised on Current shows on Progression at once. A clean draft never writes its stale cap back, and a later Progression edit saves on top of the new cap.
- A draft with unsaved edits (including an invalid one) is not replaced. If the user later makes it valid, its values are saved as edited.

## 6. Translations
- The two new strings are in `values-es`, `values-zh-rCN` and `values-hi`. `hint_outside_floor_cap` is removed from all four.
