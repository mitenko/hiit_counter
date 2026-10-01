# REPKIT — Multiple holds (spec revision 16)

**Date:** 2026-10-01
**Status:** Approved by the user in chat (2026-10-01).
**Amends:** `2026-09-24-hiit-counter-design.md` (v1) §6, the hold rule; `2026-09-28-settings-pager-design.md` (R3) §5, the Hold switch and fields, and §6.3, the hold-count reset; and the Room schema (now v5).

## 1. Purpose

A Counter entry can have **more than one hold**, for example hold at 56 for 3 check-ins, then at 64 for 4. The existing **Hold** switch turns all holds on or off and keeps their values when off. An entry's existing single hold becomes the first item of its list.

## 2. Domain (amends v1 §6 and R3 §5.1)

- `data class Hold(val at: Int, val forCount: Int)`.
- `ProgressionConfig` replaces `holdAt` / `holdFor` with `holds: List<Hold>` (default `[Hold(64, 4)]`) and keeps `hold: Boolean`, the switch. The list keeps the user's order; the model never sorts it.
- A hold is **active** when the switch is on, `forCount > 0` and `floor ≤ at < cap`. `activeHold(total)` returns the active hold whose `at == total`, taking the first match if duplicates somehow exist. It replaces `holdEnabled`, which is removed.
- **RepProgression** keeps the v1 rules, with "total == holdAt" generalised to "`activeHold(total) != null`" and the hold length taken from that hold:
  - `holdCount` stays one Int: the count at the hold equal to the current total. Only one hold can match a total.
  - A first check-in or a miss that lands exactly on an active hold starts (or restarts) that hold with a count of 1.
  - An on-time check-in at an active hold whose count has reached its `forCount` goes +1. The count is then 1 if the new total is itself an active hold (two adjacent holds), otherwise 0. Reaching any later hold starts its count at 1, counting the day it's reached.
  - An on-time check-in at an active hold with a smaller count stays put and adds 1 to the count.

## 3. Validation (amends R3 §5.1)

Whatever the switch says, each hold's hard ranges are checked: `at ≥ 1` ("Must be at least 1") and `forCount ≥ 0` ("Must be 0 or more"), so a saved list can always be read back. (The steppers clamp to these ranges, so they never block a hidden value in practice.)

Only while the switch is on:

- a **duplicate `at` is an error on the later duplicate**: "Already a hold at N", shown as the validator's text like every other error;
- at most **8** holds (`ProgressionConfig.MAX_HOLDS`; "At most 8 holds" under `Field.HOLDS`).

Also only while the switch is on, each hold without an error that isn't active (outside [floor, cap), or a count of 0) gets the hint "Hold disabled". Errors and hints are keyed by the hold's index: `ValidationResult.holdErrors: Map<Int, Map<HoldField, String>>` (`HoldField.AT`, `HoldField.FOR`) and `holdHints: Map<Int, String>`. `isValid` needs both `errors` and `holdErrors` to be empty. `Field.HOLD_AT` and `Field.HOLD_FOR` are removed. With the switch off, only the hard ranges apply.

## 4. Hold-count reset (amends R3 §6.3)

`holdResetNeeded(old, new)` is true when:

- the `holds` lists differ as lists (an added, removed, edited or reordered hold);
- or the switch changes;
- or the set of active holds differs (a floor or cap edit that turns a hold on or off).

Starting-total, window and penalty edits, and floor or cap edits that leave every hold's active state unchanged, keep the count. (R3 kept the count when the switch was toggled with no hold active; any switch change now resets it.)

## 5. Storage (Room v4 → v5)

- New column `entry.holds TEXT NOT NULL DEFAULT ''`, encoded by `data/HoldsCodec` as `"56:3,64:4"` in list order, and `"-"` for an empty list.
- The empty string is the column default and is never written by v5 code (the entity has no Kotlin default for it). On read, `""` means "use the legacy `hold_at` / `hold_for` as a one-item list" (each repaired per field as before).
- Other text is decoded item by item: a malformed item, or one with `at < 1` or `forCount < 0`, is dropped and the rest kept in order; text with no good item reads as the default `[Hold(64, 4)]`. The list is then repaired with `distinctBy { it.at }.take(8)`, so a stored list with duplicates or more than 8 holds never fails the group check and resets the rest of the progression. Any repair is logged.
- `MIGRATION_4_5`, verbatim:
  ```sql
  ALTER TABLE entry ADD COLUMN holds TEXT NOT NULL DEFAULT '';
  UPDATE entry SET holds = hold_at || ':' || hold_for;
  ```
- The legacy `hold_at` / `hold_for` columns **stay** (the table isn't rebuilt, since `check_in` has a foreign key to it). Every write mirrors the first hold into them, or 64 / 4 for an empty list. Nothing reads them except the `""` fallback above.
- `EntryDao.setProgression` writes `holds` and the mirrored legacy columns. Duplicate copies the holds.
- v1 import: a v1 hold imports as a one-item list. A v1 `hold_for = 0` still imports as the switch off with the hold's count back at 4.

## 6. Progression page (amends R3 §5.3)

- With the switch on, the holds show as a list inside the existing `AnimatedVisibility(draft.hold)`. Each hold has:
  - a small heading "Hold 1", "Hold 2"… (`hold_n`) with a 48 dp ✕ IconButton (`ic_close`, content description "Remove hold N", `remove_hold`);
  - then the existing "Hold at" and "Hold for (check-ins)" stepper rows with their ⓘ tags, errors and hints. Their visible labels stay the same, but their spoken names (Increase / Decrease / Edit / About) and test tags use "Hold N at" and "Hold N for" (`hold_n_at`, `hold_n_for`, via the stepper's `a11yLabel`), so TalkBack can tell the holds apart.
- Below the list, **"+ Add hold"** (`add_hold`): a full-width TextButton drawn at least 48 dp tall (its padding sits outside the 48 dp minimum), disabled at 8 holds. The new hold is `Hold(at = (last hold's at, or the floor) + 4, forCount = last hold's forCount, or 4)`. If that `at` is at or above the cap, or already a hold, it takes the first free value in [floor, cap); if none is free it is added anyway, for the validator to flag.
- Saving: the steppers keep the 400 ms debounce, dialog edits save at once, and Add and remove save at once. Invalid drafts never save.
- Removing the last hold leaves an empty list. The switch stays as it is, and the page shows just "+ Add hold".
- The draft carries `holds: List<Hold>`, which is part of its equality (and so the save key) and of the saved-state copy.
- `info_hold`: "When on, your total pauses at each hold for a few check-ins before climbing again."
- The Current page keeps its hold-count behaviour; it shows nothing about a hold value.
