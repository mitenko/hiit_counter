# HIIT Counter — Settings Pager, Auto-Save, Info Tags and Hold Switch (spec revision 3)

**Date:** 2026-09-28
**Status:** Approved by the user (2026-09-28).
**Amends:** `2026-09-24-hiit-counter-design.md` (v1) and `2026-09-25-multi-entry-design.md` (revision 2). Everything in those still holds unless this document replaces it. "R2 §8.1" points into revision 2.

## 1. Purpose

This revision makes an entry's settings faster to use:
- the four settings pages become one screen you swipe through;
- every change saves itself;
- each setting explains itself through an ⓘ tag;
- the hold gets an explicit on/off switch that remembers its values.

## 2. Scope

**In scope**
- A pager with tabs for Timing, Progression, Current and Cues, replacing the four separate settings routes.
- Auto-save on Timing, Progression and Current. Cues already saves immediately.
- An ⓘ info tag and dialog for every labelled setting.
- A **Hold** switch on Progression, stored in a new `hold_enabled` column (Room version 1 → 2).
- A hold count that is reset only by edits that affect the hold.

**Out of scope**
- The check-in history and graph. It has its own spec later and adds the next Room migration.
- The "Keep history / Clear history" choice on Reset progress, which arrives with the graph.
- Changes to entry list, entry screen, timer, notification or progression maths.

## 3. Delta from revision 2

| Area | Revision 2 | Revision 3 |
|---|---|---|
| Settings sub-screens | four routes, each with its own screen and Save button | one pager route with tabs and swipe, and no Save buttons |
| Saving | explicit Save; invalid drafts disable Save | auto-save of valid drafts; invalid drafts show errors and are not saved |
| Hold on/off | implicit: `holdFor = 0` or `holdAt` outside `[floor, cap)` | explicit **Hold** switch, plus the implicit conditions (§5.1) |
| Hold count reset | every `setProgression` and every `overwriteCounter` | only when the hold-relevant values or the total change (§6.3) |
| Info | none | ⓘ tag and dialog on every labelled setting |
| Room schema | version 1 | version 2: `entry.hold_enabled` |
| Reset to defaults / Reset progress | draft-only / confirmed | both confirmed and applied immediately |

## 4. Navigation (amends R2 §7.2, §7.5)

- **New route.** `entry/{id}/settings/pages?page={page}` replaces `…/settings/timing`, `/progression`, `/current` and `/cues`.
  - `page` is an optional int argument, 0–3 (Timing, Progression, Current, Cues), with a default of 0.
  - The `Routes.settingsPage(id, page)` helper keeps its signature and now builds this route.
- **Entry Settings** keeps its four page rows. Each row opens the pager at that page. This deviates on purpose from the design discussion, which proposed a single "Settings" row: keeping the rows keeps the direct jump to a page and keeps the Entry Settings screen and its tests unchanged. Rename, Duplicate and Delete are unchanged.
- **Pager screen.**
  - A top bar with ← and the title "<entry name> settings".
  - A `PrimaryTabRow` with the tabs **Timing · Progression · Current · Cues**, over a `HorizontalPager` with 4 pages.
  - Tapping a tab animates to that page, and swiping updates the selected tab. The current page survives rotation and process recreation through `rememberPagerState` + `rememberSaveable`.
- **Leaving the pager.** Back from any page returns to Entry Settings. Every exit flushes any pending save first (§6.2). This covers back, the ← button, swiping away from a page, the app going to the background (`ON_STOP`), and an `onEntryGone` pop.
- **Unchanged behaviour.** Active-run routing, the `dropUnlessResumed` tap guards and the `EntryNotFound → popToEntries` behaviour all stay as in R2.

## 5. Hold switch

### 5.1 Domain

`ProgressionConfig` gains `hold: Boolean = true`. The derived rule becomes:

```kotlin
val holdEnabled: Boolean
    get() = hold && holdFor > 0 && holdAt >= floor && holdAt < cap
```

- `RepProgression` is unchanged, because it already reads only `holdEnabled`.
- `SettingsValidator.progression` behaves as follows:
  - When `hold` is off, it skips the `HOLD_AT` and `HOLD_FOR` checks and gives no "Hold disabled" hint. The hidden values cannot block a save.
  - When `hold` is on, the checks and the hint behave as today.

### 5.2 Room migration 1 → 2

```sql
ALTER TABLE entry ADD COLUMN hold_enabled INTEGER NOT NULL DEFAULT 1;
UPDATE entry SET hold_enabled = 0, hold_for = 4 WHERE hold_for = 0;
```

- A stored `hold_for = 0` meant "hold off". It becomes switch off, with `hold_for` restored to the default 4, so switching the hold back on gives a working hold. Check-in behaviour is identical before and after the migration.
- `HiitDatabase` moves to `version = 2`, with the migration registered in the builder. The schema `2.json` is exported and committed. There is still no destructive fallback.
- The per-field read repair treats `hold_enabled` as a boolean column with a default of `true`.
- `V1Migrator` applies the same mapping: a v1 `hold_for` of 0 imports as `hold = false, holdFor = 4`.
- Create uses the defaults (`hold = true`). Duplicate copies `hold_enabled`.

### 5.3 UI

- A **Hold** switch row sits in Progression, directly above *Hold at*.
- When the switch is off, the *Hold at* and *Hold for* rows are hidden with `AnimatedVisibility`. Their draft and stored values are kept.
- Turning the switch on shows the rows with their previous values.

## 6. Auto-save (replaces the Save buttons of R2 §7.5 and §8.1)

### 6.1 Draft and validation

- Each page keeps its typed draft in the pager ViewModel, as in R2 §8.1. The drafts survive swipes and recreation through `SavedStateHandle`.
- Every change runs the existing validator on the whole draft. A change is a stepper ±, a dialog OK, a switch toggle, a date or time pick, Clear, or a reset.
- Cross-field rules stay unclamped (R2 §8.1): floor ≤ start ≤ cap, best ≥ current, no future check-in, and TOTAL ≤ 2:00:00.

### 6.2 Save pipeline (one per page)

- **Valid draft.** The save is scheduled.
  - Stepper changes are **debounced by 400 ms** after the last change, so hold-to-repeat writes once.
  - Dialog OK, switch toggles, date and time picks and resets save immediately, cancelling any pending debounce.
- **Invalid draft.** No save is scheduled, and any pending one is cancelled. The field shows its error, and the stored values stay at the last valid state.
- **Flush.** The pending save is written immediately. A flush happens when you change page, leave the pager, or the app is stopped. If a stepper change is pending when the ViewModel is cleared, the write is launched in the `@ApplicationScope` so it survives the ViewModel.
- **Ordering.** Saves for one page are serialised with a `Mutex`, so a later draft is never overwritten by an earlier write.
- **Errors.**
  - `EntryNotFound` → `markMissing()` → pop to the list, as in R2.
  - `IllegalArgumentException` from repository validation cannot happen for a valid draft. If it does, it is logged and the page shows "Not saved".
- **Status line.** The page footer shows **Saved**, or **Not saved: fix the highlighted field** when the draft is invalid. Timing keeps its TOTAL footer, which is red above 2:00:00.

### 6.3 Hold-count rules

- **`setProgression`** now runs in one transaction. It reads the row and writes the new progression. It resets `hold_count` to 0 **only if** `holdAt`, `holdFor` or the effective `holdEnabled` changed. Otherwise `hold_count` is kept. Floor and cap are included through `holdEnabled`, because they can switch the hold on or off.
- **`overwriteCounter`** (the Current page) resets `hold_count` to 0 only if the total changed. Edits to the streaks or the last check-in keep it. This also runs in one transaction.
- **Reset progress** is unchanged: the hold count goes to 0.

### 6.4 Resets

- **Reset to defaults** (Progression) shows a confirmation, "Reset progression to defaults?", with Reset and Cancel. On confirm, the draft becomes the defaults and saves immediately.
- **Reset progress** (Current) keeps its confirmation and applies immediately, as it does today.

## 7. Info tags

### 7.1 Component

- `InfoTag(title: String, text: String)` is a 48 dp `IconButton` showing `Icons.Outlined.Info`. Its content description is "About <title>".
- It sits after the label in `StepperRow`, in the switch rows, and next to the date row. It is a separate touch target from the value's tap-to-edit.
- Tapping it opens an `AlertDialog`: the title, the text, and an OK button.
- The texts are string resources named `info_<field>`. A unit test checks that every row on the four pages has a non-blank info text.

### 7.2 Texts (draft; the user may edit them)

| Setting | Text |
|---|---|
| Prepare | Countdown before the first set, so you can get into position. 0 skips it. |
| Sets | How many work intervals the workout has. A rest follows every set except the last. |
| Work | Length of each work interval. The rep table shows how many reps to do in each set. |
| Rest | Break between sets. There's no rest after the last set. |
| Cooldown | Countdown after the last set. 0 skips it. |
| Total | The workout's full length: prepare, all sets and rests, and cooldown. It can be at most 2:00:00. |
| Starting total | The rep total you do on your first check-in, and again after Reset progress. It must be between the floor and the cap. |
| Floor | The lowest your rep total can fall to, however long you're away. |
| Cap | The highest your rep total can climb to. At the cap, on-time check-ins keep the total where it is. |
| Hold | When on, your total pauses at *hold at* for a few check-ins before climbing again. |
| Hold at | The rep total where the hold happens. It needs to be at or above the floor and below the cap. |
| Hold for | How many check-ins you stay at *hold at* before going up again. The day you first reach it counts. 0 skips the hold. |
| Window | Check in within this many hours of your last check-in to count as on time: your total goes up by one and your streak continues. |
| Penalty rate | After a missed window, you lose reps for the time away: one rep for roughly every this-many hours past the first day, less one. The total never drops below the floor. |
| Total reps | Your current rep total, split across the sets in the rep table. Changing it here takes effect immediately. |
| Best streak | Your longest run of on-time check-ins. It must be at least your current streak. |
| Current streak | On-time check-ins in a row, including the last one. A missed window restarts it at 1. |
| Last check-in | When you last pressed Start. The window and penalty are measured from this. Clear it to make the next check-in count as your first. |
| Sound | Beeps for the countdown and at each phase change. |
| Vibration | Vibrates at each phase change, including with the screen off. |

## 8. Testing (amends R2 §9)

- **Domain (pure, test-first).**
  - `holdEnabled` with the switch on and off.
  - The validator skips the hold checks and hint when the switch is off.
  - A pure `holdResetNeeded(old, new)` rule for the progression hold count.
  - `counterHoldReset(oldTotal, newTotal)` for `overwriteCounter`.
- **Room.**
  - A `MigrationTestHelper` test of 1 → 2 covering `hold_for` 4 → enabled and `hold_for` 0 → disabled with `hold_for` 4. It is skipped on Windows (R2 ruling) and runs on CI.
  - Repository tests: `setProgression` keeps the hold count for a floor, penalty or window edit and resets it for a hold-at, hold-for or switch edit. `overwriteCounter` keeps the hold count for a streak or date edit and resets it for a total edit.
  - Duplicate copies `hold_enabled`.
  - `V1Migrator` maps a v1 `hold_for` of 0.
- **ViewModel.**
  - A valid stepper change saves once after 400 ms, including a burst of 10 changes.
  - An invalid draft never saves and cancels a pending save.
  - A dialog OK saves at once.
  - Changing page, and clearing the ViewModel, flush a pending save.
  - `EntryNotFound` → `missing`.
  - Reset to defaults saves after confirmation.
- **Compose (Robolectric).**
  - Tapping a tab and swiping both change the page.
  - `page=2` opens on Current.
  - The Hold switch hides and restores the rows and their values.
  - ⓘ opens the right title and text on each page.
  - Every ⓘ is at least 48 dp.
  - There is no Save button on any page.
  - The status line reads "Saved" or "Not saved…".
- **Device (Pixel 9a).**
  - Back up the app's data (`run-as … tar`) before installing.
  - Install over the existing build.
  - Verify the entries, totals and hold values survive the migration, and that edits persist after a force-stop.

## 9. Project conventions

These are unchanged: the mitenko identity, no AI attribution, squash to one commit and open a PR, ask before pushing, TDD subtasks, and a phone backup before every install.

## 10. Implementation notes (from the plan, confirmed with the user)

The layout and Reset progress bullets below are deliberate divergences from §6.1 and R2. The others resolve gaps.

- **Layout (diverges from §6.1).** §6.1 describes all drafts in the pager ViewModel. Instead, each page keeps its own ViewModel, keyed on the pager's back-stack entry, with its own `SavedStateHandle` draft and `AutoSaver`. `SettingsPagerViewModel` holds only the title.
- **Reset progress stays on the page (diverges from R2).** Current is a tab now, so Reset progress applies at once and the page stays open, showing the reset counter.
- **ⓘ icon.** `Icons.Outlined.Info` isn't on the classpath: Material 3 1.4 no longer brings in material-icons. The tag uses `res/drawable/ic_info.xml`, which is the same glyph.
- **Current draft.** While the pager is open, a Current draft with no unsaved edits follows the stored counter. For example, a NULL total re-resolves after a starting-total change. The floor–cap hint follows the stored progression.
- **§6.3 switch edits.** The rule is applied literally: a Hold toggle that leaves the effective hold unchanged (e.g. with hold for 0) keeps the count.
- **§6.3 total.** `overwriteCounter` compares against the resolved total: a stored NULL counts as the starting total.
- **§7.2 texts.** They are stored without the Markdown emphasis on "hold at".
- **Tabs.** The Current tab reads "Current", while the Entry Settings row keeps "Current State".
- **Status line.** It reads "Saved" for any valid draft (a debounce may still be pending; every exit flushes). Cues has no status line.
- **Flush on clear.** A value stays pending until its write completes, and a started write runs to completion. So a write that is queued when the ViewModel is cleared is re-issued in the `@ApplicationScope`.
  - Accepted race: a queued write followed by `cancel()` (the draft turned invalid) and then a clear is lost. The stored values stay at the earlier valid state.
  - A valid draft restored from `SavedStateHandle` is scheduled for saving.
- **Current draft echo guard.** The draft follows the store only while no save is pending, so an edit back to the stored value can't be overwritten by a store echo.
- **Title.** "Settings" shows until the entry name has loaded.
