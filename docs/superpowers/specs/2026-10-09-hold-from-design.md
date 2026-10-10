# REPKIT — Hold from (spec revision 34)

Status: approved by the user, 2026-10-08 and 2026-10-09:
- a hold's number is the first value that holds (option B);
- the most specific hold wins (A);
- Reps mode only for now (A).

Amends `2026-10-01-multi-hold-design.md` (revision 16) §2–§6, and revision 20 §2 (the hold announcement).

## 1. Purpose

Today a hold pauses the total at one number: **Hold at 64, for 4**. A **Hold from** pauses at every number from its value upward. **Hold from 64, for 2** means the total holds at 64 for 2 check-ins, then at 65 for 2, then at 66 for 2, and so on up to the cap. Past that point you gain one rep every 2 check-ins instead of every check-in, which slows the climb near your maximum.

It applies to Counter entries in Reps mode only. Weight holds (revision 26) stay "at" only. A "from" kind for weight holds can be a later change of its own.

## 2. Domain (amends revision 16 §2)

- **The model:** `Hold(at: Int, forCount: Int, kind: HoldKind = HoldKind.AT)`, with `enum class HoldKind { AT, FROM }`.
  - For an AT hold, `at` is the held value.
  - For a FROM hold, `at` is the first value of its range.
  - Existing code that builds `Hold(at, forCount)` keeps meaning "at".
- **Active:** a hold of either kind is active under the revision 16 rule: the switch is on, `forCount > 0`, and `floor ≤ at < cap`.
  - A FROM hold covers `at ..< cap`. The cap itself never holds, because nothing lies above it to climb to.
- **`activeHold(total)`, the most specific hold wins:**
  1. the active AT hold whose `at == total`, if there is one;
  2. otherwise, of the active FROM holds with `at ≤ total < cap`, the one with the greatest `at`;
  3. otherwise none.

  With no FROM holds, this is exactly today's function.
- **RepProgression:** unchanged. It already works from `activeHold(total)` and the returned hold's `forCount`, and every value inside a FROM range is its own hold:
  - An on-time check-in at a held value whose count has reached `forCount` goes +1. The count is then 1 when the new value is held too (inside a FROM range it always is, until the cap), otherwise 0.
  - A check-in with a smaller count stays put and adds 1.
  - A first check-in or a miss that lands on a held value starts (or restarts) its count at 1.
  - Example, from 64 for 2: reach 64 (count 1) → stay (2) → 65 (1) → stay (2) → 66 (1) … A miss that drops 66 to 65 restarts 65 at 1.
- **Hold-count reset** (revision 16 §4, `holdResetNeeded`): unchanged in form. The holds list, the switch and the set of active holds are compared with `kind` included, so changing a hold between At and From resets the count.

## 3. Validation (amends revision 16 §3)

- **Duplicates** are keyed by `(kind, at)`. "At 64" and "from 64" may coexist: "at" wins on 64, and "from" covers 65 upward.
  - A later duplicate AT shows today's error, "Already a hold at N".
  - A later duplicate FROM shows the new error, "Already a hold from N".
- **"Hold disabled":** the same rule as today. With the switch on, a hold without an error that isn't active (outside `[floor, cap)`, or a count of 0) gets the hint. That covers a FROM hold at or above the cap.
- At most 8 holds, of either kind. The hard ranges for `at` and `forCount` are unchanged.

## 4. Storage (amends revision 16 §5; no Room version change)

- **The `entry.holds` text:** an AT item stays `at:for`, and a FROM item is written `at+:for`.
  - Example: `"56:3,60+:2"` is at 56 for 3, then from 60 for 2.
  - Every existing row decodes exactly as before.
- **Decoding:**
  - An item with a `+` after `at` reads as FROM.
  - Any other malformed item is dropped, as today.
  - The repair is `distinctBy { it.kind to it.at }.take(8)`.
- **Legacy columns:** the mirrored `hold_at` / `hold_for` columns take the first hold's `at` and `forCount`, whatever its kind (nothing reads them but the `""` fallback).
- **Older builds:** an app version from before this revision drops a `+` item as malformed. That matters only if a phone goes back to an older build.

## 5. Progression page (amends revision 16 §6)

- **Each hold row** gets a two-segment choice, **At | From**, under its "Hold N" heading and above its value stepper. A new hold (+ Add hold) starts as At, with today's value rules.
- **The value row's label** follows the choice:
  - **"Hold at"**, with today's ⓘ;
  - **"Hold from"**, with a new ⓘ.
- **Spoken names and test tags:**
  - At holds keep `hold_n_at`.
  - From holds use "Hold N from" and `hold_n_from`.
  - The segments are tagged `hold_n_kind_at` / `hold_n_kind_from`, and are each at least 48 dp tall.
- **Saving:**
  - Switching the kind saves at once, like Add and remove.
  - The draft's `holds` carries the kind, so it's part of the draft's equality and saved state.
- **The Current page:** unchanged.

## 6. Hold highlight and announcement (amends revision 20 §2)

The check-in that stays on a hold announces "Holding at N · D of F". **N is now the total**, not `hold.at`, so inside a FROM range it names the value being held ("Holding at 66 · 1 of 2"). For an AT hold the two are the same.

## 7. Strings (en, plus es / zh-rCN / hi through the translations data file)

| Key | English |
| --- | --- |
| `hold_kind_at` | At |
| `hold_kind_from` | From |
| `hold_from` | Hold from |
| `hold_n_from` | Hold %1$d from |
| `info_hold_from` | Every number from this one up holds for the check-in count before you climb again, which slows the climb near your maximum. |
| `error_duplicate_hold_from` | Already a hold from %1$d |

`hold_n_from` is the spoken name, as `hold_n_at` is today: %1$d is the hold's index, not a count.

## 8. Testing

- **Pure Kotlin, test-first:**
  - `activeHold` covers the overlap rule (AT beats FROM; the highest FROM wins; the cap never holds) and an inactive FROM (`at ≥ cap`, below the floor, or a count of 0);
  - RepProgression climbs through a FROM range, and a miss inside one restarts the landed value;
  - `holdResetNeeded` on a kind change;
  - validation of `(kind, at)` duplicates and the FROM "Hold disabled" hint.
- **HoldsCodec:** round trips of `64:4`, `60+:2` and mixed lists; old text decodes unchanged; a malformed `+` item is dropped; the repair keeps "at 64" and "from 64" together.
- **Compose (Robolectric):** the At | From choice switches the label and tags, and saves; a duplicate FROM shows its error.
- **The announcement:** it names the total inside a FROM range.

## 9. Out of scope

- FROM holds for weight modes (revision 26).
- A "hold above" with an exclusive start: option A was declined.
- Any change to misses, penalties, the floor or the cap.

## 10. Order with weight progression

This ships before weight progression PR 2 (the settings UI), which is still waiting for the user's rulings. PR 2's plan reuses the hold row parts (`HoldHeader`, `AddHoldButton`). When PR 2 is built, its weight hold rows keep At only and must not show the At | From choice.

## 11. Implementation notes (plan `2026-10-09-hold-from.md`)

1. An inactive From hold (at or above the cap, below the floor, or a count of 0) always gets the plain "Hold disabled" hint. Revision 28's "Hold at N is above / below …" hints stay for At holds only, since they name the wrong kind and §7 adds no From version.
2. The value row's tags follow its spoken name: `value_Hold 1 from`, `support_Hold 1 from`. The segments are `hold_1_kind_at` / `hold_1_kind_from` (1-based hold number).
3. The codec reads `at` as plain digits with at most one trailing `+`. A leading `+` (`"+64:4"`, which Kotlin's `toIntOrNull` used to accept) is now malformed and dropped; no build ever wrote it.
4. "+ Add hold" (`newHold`) is unchanged: it makes an At hold and avoids every existing value, whatever its kind.
5. `HoldStatus.at` keeps its name and now carries the held total.
6. The page's saved state stores each hold as (at, for, kind).
7. Tapping the segment that is already selected does nothing.
