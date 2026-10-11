# REPKIT Weight progression, PR 2 of 3: Settings UI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make weight progression editable: the **Progress by** switch (Reps / Weight / Reps then weight) with its Start fresh confirm, the weight setup on the Progression page (unit, Steps or My weights, reps per set or the rep range, the starting point, weight holds), the Current page in weight modes, and ⚙ › Units, all following the "field you edit wins" rule, with every string in en / es / zh-rCN / hi.

**Architecture:**
- **Domain (pure, test-first):** small helpers on top of PR 1's model: `WeightFormat` (hundredths ↔ text), draft edits (`WeightDraft.kt`), the weight-mode overrides and their notes (`WeightOverrides.kt`, revision 28 §6), and `weightValidation`, which places `WeightValidator`'s problems on the page's fields.
- **Data:** `setWeightConfig` now returns the stored current load it moved (`WeightMove.CurrentMoved?`), so the page can note it (revision 28 rule 4). A `WeightUnitDefaults` interface hands the app default unit to the settings without DataStore in tests.
- **UI:** the Progression page gets a second ViewModel, `WeightSettingsViewModel` (mode, weight group, `setWeightConfig`, `switchMode`), next to today's `ProgressionSettingsViewModel` (Reps fields plus the shared Hold switch, window and penalty, `setProgression`). `ProgressionPageContent` shows the Reps rows or the weight rows by mode. `CurrentStateViewModel` learns the ladder. The ⚙ dialog gains Units.

**Tech Stack:** Kotlin 2.2.10, Compose Material 3, Hilt, Room 2.8.1, DataStore Preferences, coroutines, JUnit 4.13.2, Robolectric 4.16 (`@Config(sdk = [34])`). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-04-weight-progression-design.md` (revision 26), especially §2, §3, §3.1, §9.2–§9.5 and §10 notes 1–22. Also read `docs/superpowers/specs/2026-10-05-progression-overrides-design.md` (revision 28, "the field you edit wins"; its §6 binds this PR), `docs/superpowers/specs/2026-10-05-current-widens-range-design.md` (revision 27), `docs/superpowers/specs/2026-10-05-best-streak-readonly-design.md` (revision 29), `docs/superpowers/specs/2026-10-06-sets-reset-prompt-design.md` (revision 32), `docs/superpowers/plans/2026-10-05-weight-model.md` (PR 1, the APIs used here) and `CLAUDE.md`. In this plan, `§n` points into revision 26.

**Decision (user, 2026-10-06): PR 2 and PR 3 ship together as 0.2.0.** §10 note 22 says PR 2 makes `switchMode` reachable before PR 3 teaches the timer, voice, chart and tile about levels. Because both PRs go out in the same release, PR 2 does **not** hide the Progress by switch. PR 2 must not be released on its own, and it doesn't bump `versionName`. PR 2 stays on the settings: no entry screen, chart, tile, timer, voice or notification change.

## Global Constraints

- **Paths:** main code under `app/src/main/kotlin/com/mitenko/repkit/`, tests under `app/src/test/kotlin/com/mitenko/repkit/`, resources under `app/src/main/res/`. Package `com.mitenko.repkit`.
- **Worktree and branch:** work in a git worktree made with superpowers:using-git-worktrees, on branch `feat/weight-settings` from `main` (0e8d6d2). `<worktree>` below is its absolute path.
- **Builds run remotely, never local gradlew.** Run every Gradle command through rgradle with both workers, from PowerShell:
  `pwsh -NoProfile -Command "$env:RGRADLE_WORKERS='miten@192.168.1.86,miten@192.168.1.89'; Set-Location <worktree>; & D:\Dev\scripts\rgradle.ps1 <tasks>"`
  From Git Bash, single-quote the whole `-Command` string so bash leaves `$env` alone. `<tasks>` is e.g. `testDebugUnitTest --tests "*WeightFormatTest*"`. Output is tagged `CLAUDE-RGRADLE-<id>`. Announce each run first (targeted runs take about 1–2 minutes; the gate about 4). Exit code 2 or 3 means no worker: stop and ask the user, don't fall back to a local build. Never run two builds at once.
- **Gate:** `assembleDebug testDebugUnitTest lintDebug`, BUILD SUCCESSFUL with 0 lint errors (Task 16).
- **Commits:** author `mitenko <mitenko@gmail.com>` (check `git config user.email` once in the worktree before the first commit). Plain messages, **no trailers** (no Co-Authored-By, no "Generated with"). Explicit `git add <paths>`, never `-A` or `.`. Don't push; the PR protocol in CLAUDE.md runs when the user asks.
- **`domain/` is pure Kotlin:** no `android.*` imports, developed test-first (red, then green).
- **User text comes from resources only.** `HardCodedTextGuardTest` fails on any string literal with three or more letters in `ui/` or `domain/` unless the line matches its exemptions (`testTag`, `tag = `, `Log.x`, `require`, `check`, `error(`, `Exception(`, `const val …TAG|KEY|ARG`, `hiltViewModel(key`). So pass test-tag prefixes as a parameter **named `tag`**, and name saved-state keys `…_KEY`.
- **Lint MissingTranslation is an error:** every new English string needs es, zh-rCN and hi in the same commit. The `values-es`, `values-zh-rCN` and `values-hi` files are **generated**: add the translations to `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py` and run its generator (Task 6). Never hand-edit those three files.
- **Clock:** time only from the injected `Clock`.
- **Reps mode doesn't change.** Existing tests keep their assertions. The only existing-test edits allowed are the ones a task names (constructor or helper signatures that gain a parameter).
- **Storage units:** weights are integer hundredths of the workout's unit (2250 = 22.5). Limits from PR 1: 2–40 weights, each 1..99 975, steps 50 / 100 / 125 / 200 / 250 / 500, reps 1..100 with min < max, at most 8 holds.
- **Compose tests** are Robolectric: `@RunWith(AndroidJUnit4::class)`, `@Config(sdk = [34])`, `createComposeRule()`, content wrapped in `HiitTheme`.
- **Repository rule (§10 note 22):** `FakeEntryRepository.setWeightConfig` remaps but doesn't validate, and stores resolved totals. Tests of "an untouched (NULL) counter stays untouched" use `RoomEntryRepository` on an in-memory database; "an invalid draft never saves" is checked through the fake's `weightWrites` count. The fake's `CounterState.total` is never NULL (it's always a concrete level), so the fake reports `CurrentMoved` for an untouched counter where Room correctly reports null: Task 8's NULL-counter note tests must use Room, not the fake.

## Open questions (each has a recommended answer; the plan implements the recommendation)

**Ruled by the user on 2026-10-10: all four as recommended.** Tasks 8, 10, 13 and 14 run as written; notes 38–41 are decided.

1. **Reset to defaults in a weight mode.** Revision 26 doesn't say what the Progression page's Reset to defaults does in Weight / Reps then weight. Today it resets the whole Reps progression. **Recommendation:** in a weight mode it resets the shared rows (window 36 h, penalty 19.5, Hold switch on) and the weight group to `WeightConfig(unit = <the workout's unit>)`, and keeps the hidden Reps fields, since every mode's settings are remembered (§2). Implemented in Tasks 8 and 10.
2. **The Sets-changed prompt (revision 32) in a weight mode.** That prompt exists because a rep *total* is spread over the sets. In a weight mode the reps are per set, so a Sets change doesn't change them. **Recommendation:** never prompt in a weight mode. Implemented in Task 13.
3. **A Current-page edit and the fresh-start flag.** §10 note 13 makes every `overwriteCounter` clear `fresh_start`. But the Current page writes the counter on *any* edit, so after Start fresh a streak or last-check-in edit would make the next check-in go +1 instead of being performed at the start. **Recommendation:** `overwriteCounter` clears the flag only when the level actually changes (the stored effective total ≠ the new total). Implemented in Task 14 (it amends §10 note 13).
4. **The missed-day adjustment's label in a weight mode.** Today's label is "Missed-day adjustment (hours per rep)", and its ⓘ talks about the rep total. In a weight mode the penalty counts levels. **Recommendation:** in a weight mode show "Missed-day adjustment (hours per step)" with an ⓘ that explains a step and the one-weight limit. Implemented in Tasks 6 and 10.

## Spec notes (decisions made while reading the real code)

Task 16 appends these to the spec's §10 as notes 23–47, under a "PR 2 (settings UI, 2026-10-06)" line. Each is binding for this plan.

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
38. **Reset to defaults in a weight mode** (open question 1, ruled 2026-10-10: as recommended).
39. **No Sets-changed prompt in a weight mode** (open question 2, ruled 2026-10-10: as recommended).
40. **A Current edit keeps the fresh-start flag unless the level changes** (open question 3, ruled 2026-10-10: as recommended; amends note 13).
41. **The penalty's label and ⓘ in a weight mode say "per step"** (open question 4, ruled 2026-10-10: as recommended).
42. **Two drafts, one row: the write-order contract.** The code already guarantees this; the plan states it and tests it:
    - `setProgression` and `setWeightConfig` each read the row and write it inside one Room transaction, and Room runs transactions one at a time.
    - Their columns are disjoint except `total` and `hold_count`:
      - `setProgression` writes `starting_total`, `floor`, `cap`, `holds`, `hold_at`, `hold_for`, `hold_enabled`, `window_hours` and `penalty_hours_per_rep` (`EntryDao.setProgression`). It moves `total` only for a Reps-mode Counter (EntryRepository.kt:253–264).
      - `setWeightConfig` writes `weight_unit` … `start_reps` (`EntryDao.setWeightConfig`). Its `total` comes from the row it read in its own transaction: the remapped level, or the unchanged Reps total (EntryRepository.kt:313).
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
    - **One call, one transaction:** the page saves with a single `setWeightConfig` call (EntryRepository.kt:292–321). Inside it, the stored group is converted the same way first (line 300). Then the start, the holds and the current level are remapped by value (304–305), validated (310–311) and written in one UPDATE.
    - **The current level** keeps its weight value, by value (ruled 2026-10-10): the current load is found on the stored ladder before converting it, so a merge that maps it onto an equal post-conversion value is never reported as `CurrentMoved`, even though the level index shifts; only a genuinely different post-conversion value is.
    - **`hold_count`** is kept unless `weightHoldResetNeeded` says otherwise: the current load changed, or a merge dropped a hold.
    - **`fresh_start`** is never written by `setWeightConfig`.
    - **History:** `check_in` rows are never written (§9.3: history keeps its own unit).
    - **An invalid converted draft** (e.g. a merge leaves one weight) follows R3: it shows its error and never saves, so the stored row stays wholly in the old unit. The next valid draft is converted and saved as above. A stored row can't mix units, because the stored group's conversion and the write share one transaction.
    - **Tests:** PR 1's `a unit change converts before the remap and keeps the rung and the hold count` (RoomEntryRepositoryTest:984); Task 5 (fresh start and history untouched); Task 8 (an invalid conversion never saves).
44. **A Current edit in a weight mode.**
    - **The level it writes:** a Current weight `w` with the current reps `r` gives `levelOf(w, r)`. A Current reps `r` with the current weight `w` gives the same. In Weight mode the reps are fixed.
    - **"The level changed"** means the new total ≠ the stored effective total (a NULL total reads as the start level), an integer compare in `overwriteCounter`.
    - **`hold_count`** resets exactly then (`counterHoldReset`, EntryRepository.kt:387).
    - **`fresh_start`** is cleared on every Current save (note 13), or, if open question 3 is ruled as recommended, only when the level changes (note 40, Task 14).
    - **No widening:** revision 27's widening never runs in a weight mode (EntryRepository.kt:385, `!ladder`), and a level outside 0..top is rejected. So `starting_total`, `floor` and `cap` never change from the Current page in a weight mode.
    - **Tests:** Task 12 (page mapping and Room).
45. **One `setWeightConfig` save, one order** (EntryRepository.kt:292–321):
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
    | Reset to defaults, weight mode (`setWeightConfig(WeightConfig(unit))`, open question 1) | defaults, unit kept | remapped by value (nearest lower, else the lightest), noted | 0 when the load or the holds changed, else kept | unchanged | Task 5 `matrix: reset to defaults…`; Task 8 |
    | Unit change (`setWeightConfig(convert(…))`) | every weight, the start and the holds converted; Steps → My weights | same weight value | kept unless a merge changed the load or holds | unchanged | PR 1 (:984); Task 5 `matrix: a unit change…`; Task 8 |
    | Steps → My weights, same values | kind LIST, list = the steps' weights | same | kept | unchanged | Task 2; Task 5 `matrix: Steps to My weights…` |
    | Removing a weight that isn't current | that weight gone | same value (level renumbered) | kept | unchanged | Task 5 `matrix: removing a weight…` |
    | Removing the current weight | that weight gone; an explicit start on it moves down | nearest lower weight, noted | 0 | unchanged | Task 5 `matrix: removing the current weight…`; Task 3 |
    | A hold dropped by a remap | the hold gone | unchanged | 0 | unchanged | Task 5 `matrix: a hold whose weight is removed…`; Task 3 |
    | Current edit (`overwriteCounter`) | unchanged | the edited level | 0 iff the level changed | cleared (note 13), or only iff the level changed (note 40) | Task 12; Task 14 |

## File structure

| File | Status | Responsibility |
| --- | --- | --- |
| `domain/WeightFormat.kt` | create | hundredths ↔ "22.5" |
| `domain/StepRange.kt` | modify | `FieldRanges.REPS_PER_SET` |
| `domain/WeightDraft.kt` | create | draft edits: My weights rows, kind, holds, `newWeightHold`, `stepWeight`, `stepAlong`, `snapTop`, `pickable` |
| `domain/WeightOverrides.kt` | create | `WeightField`, `WeightMove`, `resolveWeightEdit`, `currentLoadMove` |
| `domain/WeightValidation.kt` | create | `WeightHoldField`, `WeightValidation`, `weightValidation` |
| `domain/SettingsValidator.kt` | modify | `FieldMessage.NotOnLadder`; `currentState(levels)` |
| `data/EntryRepository.kt` | modify | `setWeightConfig` returns `WeightMove.CurrentMoved?` |
| `data/AppPreferences.kt` | modify | `WeightUnitDefaults` |
| `di/StorageModule.kt` | modify | provides `WeightUnitDefaults` |
| `res/values/strings.xml` (+ generated `values-es`, `values-zh-rCN`, `values-hi`) | modify | the new strings |
| `ui/common/WeightTexts.kt` | create | unit and mode labels, `weightText`, `unitLabel`, problem and move texts |
| `ui/common/FieldMessages.kt`, `EditValueDialog.kt`, `SettingsPageComponents.kt` | modify | `NotOnLadder`; `ValueInput.WEIGHT`; `SaveStatus.of(Boolean)`/`worst`, `WeightMoveNote` |
| `ui/common/WeightFields.kt` | create | `WeightStepperField`, `WeightPickerField`, `ChoiceRow` |
| `ui/settings/WeightSettings.kt` | create | `WeightNote`, `WeightSettingsViewModel` |
| `ui/settings/WeightSections.kt` | create | `WeightPage`, the weight-mode rows, the two confirm dialogs |
| `ui/settings/ProgressionSettings.kt` | modify | weight-mode page, `resetSharedToDefaults`, dialogs |
| `ui/settings/SettingsPager.kt` | modify | the weight ViewModel: flush, notes, missing |
| `ui/settings/CurrentStateSettings.kt` | modify | weight-mode Current page |
| `ui/settings/TimingSettings.kt` | modify | no Sets prompt in weight modes |
| `ui/entries/EntryListViewModel.kt`, `EntryListScreen.kt` | modify | ⚙ › Units |
| `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py` | modify | the translations |
| `docs/superpowers/specs/2026-10-04-weight-progression-design.md`, `CLAUDE.md` | modify | §10 notes 23–47, project notes |

Tests: create `domain/WeightFormatTest`, `WeightDraftTest`, `WeightOverridesTest`, `WeightValidationTest`, `ui/common/WeightTextsTest`, `WeightFieldsTest`, `ui/settings/WeightSettingsViewModelTest`, `WeightProgressionPageTest`, `TwoDraftsTest`; modify `domain/StepRangeTest`, `SettingsValidatorTest`, `data/RoomEntryRepositoryTest`, `testutil/FakeEntryRepository`, `ui/settings/ProgressionSettingsViewModelTest`, `SettingsPagerTest`, `CurrentStateViewModelTest`, `CurrentStatePageTest`, `TimingSettingsViewModelTest`, `ui/entries/EntryListViewModelTest`, `EntryListScreenTest`.

---

### Task 1: WeightFormat and the reps-per-set range

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/WeightFormat.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/domain/StepRange.kt` (`FieldRanges`)
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightFormatTest.kt`, `app/src/test/kotlin/com/mitenko/repkit/domain/StepRangeTest.kt`

**Interfaces:**
- Consumes: `WeightConfig.MAX_REPS` (PR 1).
- Produces: `object WeightFormat { fun format(hundredths: Int): String; fun parse(text: String): Int? }`; `FieldRanges.REPS_PER_SET: StepRange` (1..100, step 1).

- [ ] **Step 1: Write the failing tests**

`WeightFormatTest.kt`:

```kotlin
package com.mitenko.repkit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightFormatTest {
    @Test
    fun `formats hundredths as the shortest plain decimal`() {
        assertEquals("22.5", WeightFormat.format(2250))
        assertEquals("20", WeightFormat.format(2000))
        assertEquals("1.25", WeightFormat.format(125))
        assertEquals("0.05", WeightFormat.format(5))
        assertEquals("999.75", WeightFormat.format(99_975))
        assertEquals("0", WeightFormat.format(0))
    }

    @Test
    fun `parses a dot or a comma with up to two decimals`() {
        assertEquals(2250, WeightFormat.parse("22.5"))
        assertEquals(2250, WeightFormat.parse(" 22,5 "))
        assertEquals(2000, WeightFormat.parse("20"))
        assertEquals(2000, WeightFormat.parse("20."))
        assertEquals(50, WeightFormat.parse(".5"))
        assertEquals(125, WeightFormat.parse("1.25"))
        assertEquals(0, WeightFormat.parse("0"))
    }

    @Test
    fun `rejects three decimals, signs, letters and blanks`() {
        listOf("1.255", "-5", "+5", "abc", "", " ", ".", "1.2.3", "1e3", "1,2,3").forEach {
            assertNull(it, WeightFormat.parse(it))
        }
    }

    @Test
    fun `rejects a value too large for an Int of hundredths`() {
        assertNull(WeightFormat.parse("99999999999"))
    }

    @Test
    fun `format and parse round trip`() {
        listOf(25, 125, 2250, 99_975).forEach { assertEquals(it, WeightFormat.parse(WeightFormat.format(it))) }
    }
}
```

Append to `StepRangeTest.kt` (inside the class):

```kotlin
    @Test
    fun `reps per set step by one within 1 to 100`() {
        assertEquals(StepRange(1, 100, 1), FieldRanges.REPS_PER_SET)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run (Global Constraints form): `testDebugUnitTest --tests "*WeightFormatTest*" --tests "*StepRangeTest*"`
Expected: compilation fails: `WeightFormat` and `REPS_PER_SET` are unresolved.

- [ ] **Step 3: Implement**

`WeightFormat.kt`:

```kotlin
package com.mitenko.repkit.domain

import java.math.BigDecimal

/**
 * Weights as text (spec rev 26 §2, plan Spec note 26): integer hundredths shown as the shortest plain
 * decimal with "." in every locale, like the penalty rate, and read back with "." or ",". Pure.
 */
object WeightFormat {
    private val WEIGHT = Regex("""\d+([.,]\d{0,2})?|[.,]\d{1,2}""")
    private val MAX = BigDecimal.valueOf(Int.MAX_VALUE.toLong())

    /** 2250 → "22.5", 2000 → "20", 125 → "1.25". */
    fun format(hundredths: Int): String = BigDecimal.valueOf(hundredths.toLong(), 2).stripTrailingZeros().toPlainString()

    /** "22.5" or "22,5" → 2250. Null for 3+ decimals, a sign, letters, a blank, or more than Int.MAX_VALUE hundredths. */
    fun parse(text: String): Int? {
        val t = text.trim()
        if (!WEIGHT.matches(t)) return null
        val hundredths = BigDecimal(t.replace(',', '.')).movePointRight(2)
        return if (hundredths > MAX) null else hundredths.toInt()
    }
}
```

In `StepRange.kt`, add `import com.mitenko.repkit.domain.model.WeightConfig` and, inside `object FieldRanges` after `HOLD_FOR`:

```kotlin
    /** Reps per set, the rep range and starting reps per set in a weight mode (spec rev 26 §3.1): 1–100. */
    val REPS_PER_SET = StepRange(1, WeightConfig.MAX_REPS, 1)
```

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*WeightFormatTest*" --tests "*StepRangeTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/WeightFormat.kt app/src/main/kotlin/com/mitenko/repkit/domain/StepRange.kt app/src/test/kotlin/com/mitenko/repkit/domain/WeightFormatTest.kt app/src/test/kotlin/com/mitenko/repkit/domain/StepRangeTest.kt
git commit -m "Add weight text formatting and the reps-per-set range"
```

---

### Task 2: Weight draft edits

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/WeightDraft.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightDraftTest.kt`

**Interfaces:**
- Consumes: `WeightConfig`, `WeightSteps`, `WeightsKind`, `WeightHold`, `ProgressMode`, `ProgressionConfig.DEFAULT_HOLD`, `WeightValidator.STEP_CHOICES` (PR 1).
- Produces (all top-level in `com.mitenko.repkit.domain`):
  - `fun nextListWeight(list: List<Int>): Int`
  - `val WeightConfig.pickable: List<Int>` (the weights sorted, without duplicates)
  - `fun WeightConfig.withListWeight(index: Int, value: Int): WeightConfig`, `withoutListWeight(index: Int)`, `withNewListWeight()`
  - `fun WeightConfig.withKind(kind: WeightsKind): WeightConfig`
  - `fun WeightConfig.withWeightHold(index: Int, transform: (WeightHold) -> WeightHold): WeightConfig`, `withoutWeightHold(index: Int)`, `withNewWeightHold(mode: ProgressMode)`
  - `fun newWeightHold(mode: ProgressMode, c: WeightConfig): WeightHold`
  - `fun stepWeight(value: Int, step: Int, up: Boolean): Int`
  - `fun stepAlong(options: List<Int>, value: Int?, up: Boolean): Int?`
  - `fun snapTop(top: Int, start: Int, step: Int): Int`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightDraftTest {
    private val bells = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200, 1600))

    @Test
    fun `a new list weight is the last plus the last gap`() {
        assertEquals(2000, nextListWeight(listOf(800, 1200, 1600)))
        assertEquals(2500, nextListWeight(listOf(2250, 2000, 1600))) // unsorted input: 2250 + 250
        assertEquals(1050, nextListWeight(listOf(800))) // one weight: the default step, 2.5
        assertEquals(2000, nextListWeight(emptyList())) // none: the default start, 20
        assertEquals(99_975, nextListWeight(listOf(99_000, 99_900))) // kept at most 999.75
    }

    @Test
    fun `editing a list weight keeps the list ascending`() {
        assertEquals(listOf(1200, 1600, 2050), bells.withListWeight(0, 2050).list)
        assertEquals(listOf(800, 1600), bells.withoutListWeight(1).list)
        assertEquals(listOf(800, 1200, 1600, 2000), bells.withNewListWeight().list)
    }

    @Test
    fun `pickable weights are sorted without duplicates`() {
        assertEquals(listOf(800, 1200), WeightConfig(kind = WeightsKind.LIST, list = listOf(1200, 800, 800)).pickable)
        assertEquals(WeightSteps.DEFAULT.expand(), WeightConfig().pickable)
    }

    @Test
    fun `switching to My weights with an empty list seeds it from the steps`() {
        val steps = WeightConfig(steps = WeightSteps(2000, 500, 3000))
        assertEquals(WeightConfig(steps = steps.steps, kind = WeightsKind.LIST, list = listOf(2000, 2500, 3000)), steps.withKind(WeightsKind.LIST))
        assertEquals(bells.copy(kind = WeightsKind.STEPS), bells.withKind(WeightsKind.STEPS)) // the list is kept
        assertEquals(bells, bells.copy(kind = WeightsKind.STEPS).withKind(WeightsKind.LIST)) // a kept list isn't reseeded
        assertEquals(40, WeightConfig(steps = WeightSteps(100, 100, 9000)).withKind(WeightsKind.LIST).list.size) // at most 40
    }

    @Test
    fun `a new weight hold goes above the last hold, never on the heaviest`() {
        // No holds yet: above the starting weight (the lightest), at the lowest reps, for 4.
        assertEquals(WeightHold(1200, 8, 4), newWeightHold(ProgressMode.WEIGHT, bells))
        val one = bells.copy(holds = listOf(WeightHold(1200, 8, 3)))
        // Above 1200 is only the heaviest, so the first free weight below it: 800.
        assertEquals(WeightHold(800, 8, 3), newWeightHold(ProgressMode.WEIGHT, one))
        val full = bells.copy(holds = listOf(WeightHold(800, 8, 2), WeightHold(1200, 8, 2)))
        assertEquals(WeightHold(800, 8, 2), newWeightHold(ProgressMode.WEIGHT, full)) // none free: added anyway, for the validator
        assertEquals(listOf(WeightHold(1200, 8, 4)), bells.withNewWeightHold(ProgressMode.REPS_THEN_WEIGHT).holds)
    }

    @Test
    fun `a new Reps then weight hold counts a weight at other reps as free`() {
        val held = bells.copy(holds = listOf(WeightHold(1200, 10, 3)))
        // 1200 × 8 is free (the hold is at 10 reps), but nothing free is above the last hold's 1200,
        // so the first free weight wins: 800.
        assertEquals(WeightHold(800, 8, 3), newWeightHold(ProgressMode.REPS_THEN_WEIGHT, held))
        // In Weight mode the same hold takes 1200 whatever its reps, and the answer is the same.
        assertEquals(WeightHold(800, 8, 3), newWeightHold(ProgressMode.WEIGHT, held))
    }

    @Test
    fun `hold edits replace or remove one hold`() {
        val c = bells.copy(holds = listOf(WeightHold(800, 8, 2), WeightHold(1200, 8, 2)))
        assertEquals(listOf(WeightHold(800, 8, 2), WeightHold(1200, 8, 5)), c.withWeightHold(1) { it.copy(forCount = 5) }.holds)
        assertEquals(listOf(WeightHold(1200, 8, 2)), c.withoutWeightHold(0).holds)
    }

    @Test
    fun `stepping Start or Top moves one step and stays inside 0,01 to 999,75`() {
        assertEquals(2250, stepWeight(2000, 250, up = true))
        assertEquals(1750, stepWeight(2000, 250, up = false))
        assertEquals(100, stepWeight(100, 250, up = false)) // would go below 0.01: unchanged
        assertEquals(99_900, stepWeight(99_900, 250, up = true)) // would pass 999.75: unchanged
    }

    @Test
    fun `stepping along a list stops at the ends and snaps a value that isn't on it`() {
        val options = listOf(800, 1200, 1600)
        assertEquals(1600, stepAlong(options, 1200, up = true))
        assertEquals(1600, stepAlong(options, 1600, up = true))
        assertEquals(800, stepAlong(options, 800, up = false))
        assertEquals(1200, stepAlong(options, 1000, up = true))
        assertEquals(800, stepAlong(options, 1000, up = false))
        assertEquals(800, stepAlong(options, null, up = true))
        assertNull(stepAlong(emptyList(), null, up = true))
        assertEquals(500, stepAlong(WeightValidator.STEP_CHOICES, 250, up = true))
        assertEquals(50, stepAlong(WeightValidator.STEP_CHOICES, 50, up = false))
    }

    @Test
    fun `a typed Top snaps down to the start plus whole steps, at least one step up`() {
        assertEquals(5850, snapTop(5900, 2100, 250))
        assertEquals(6000, snapTop(6000, 2000, 250))
        assertEquals(2250, snapTop(2100, 2000, 250)) // less than a step above: one step
        assertEquals(1500, snapTop(1500, 2000, 250)) // not above the start: left for the Top override
        assertEquals(5900, snapTop(5900, 2000, 300)) // not a step choice: left for the validator
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*WeightDraftTest*"`
Expected: compilation fails on the unresolved functions.

- [ ] **Step 3: Implement**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind

/**
 * "+ Add weight" (spec rev 26 §3, plan Spec note 29): the last weight + the last gap. With fewer than
 * two weights the gap is the default step (2.5), and an empty list starts at the default start (20).
 * Kept at most 999.75; a value already listed is added anyway, for the validator to flag.
 */
fun nextListWeight(list: List<Int>): Int {
    val sorted = list.sorted()
    val last = sorted.lastOrNull() ?: return WeightSteps.DEFAULT.start
    val gap = if (sorted.size >= 2) last - sorted[sorted.size - 2] else WeightSteps.DEFAULT.step
    return (last + gap.coerceAtLeast(1)).coerceAtMost(WeightConfig.MAX_WEIGHT)
}

/** The weights a picker offers: ascending, each once (a My weights draft can hold a duplicate the validator flags). */
val WeightConfig.pickable: List<Int>
    get() = weights.sorted().distinct()

/** My weights row [index] becomes [value]; the list sorts at once (plan Spec note 29). */
fun WeightConfig.withListWeight(index: Int, value: Int): WeightConfig =
    copy(list = list.mapIndexed { i, w -> if (i == index) value else w }.sorted())

/** ✕ on My weights row [index]. Going below two weights is allowed; the validator flags it. */
fun WeightConfig.withoutListWeight(index: Int): WeightConfig = copy(list = list.filterIndexed { i, _ -> i != index })

fun WeightConfig.withNewListWeight(): WeightConfig = copy(list = (list + nextListWeight(list)).sorted())

/**
 * Steps | My weights. Both are kept when switching. Switching to My weights with an empty list seeds
 * it from the steps' weights (at most 40), so the user edits from where they were (plan Spec note 29).
 */
fun WeightConfig.withKind(kind: WeightsKind): WeightConfig = when {
    kind == this.kind -> this
    kind == WeightsKind.LIST && list.isEmpty() -> copy(kind = kind, list = steps.expand().take(WeightConfig.MAX_WEIGHTS))
    else -> copy(kind = kind)
}

fun WeightConfig.withWeightHold(index: Int, transform: (WeightHold) -> WeightHold): WeightConfig =
    copy(holds = holds.mapIndexed { i, h -> if (i == index) transform(h) else h })

fun WeightConfig.withoutWeightHold(index: Int): WeightConfig = copy(holds = holds.filterIndexed { i, _ -> i != index })

fun WeightConfig.withNewWeightHold(mode: ProgressMode): WeightConfig = copy(holds = holds + newWeightHold(mode, this))

/**
 * The hold "+ Add hold" appends in a weight mode (plan Spec note 32, like rev 16 §6): the first weight
 * above the last hold's weight (or the starting weight) that isn't the heaviest and isn't held, at
 * the lowest reps, for the last hold's count (or 4). Failing that, the first free weight below the
 * heaviest; with none free it's added anyway, for the validator to flag. In Weight mode a hold sits
 * on its weight alone (§10 note 10), so any hold on a weight takes it.
 */
fun newWeightHold(mode: ProgressMode, c: WeightConfig): WeightHold {
    val forCount = c.holds.lastOrNull()?.forCount ?: ProgressionConfig.DEFAULT_HOLD.forCount
    val weights = c.pickable
    val lightest = weights.firstOrNull() ?: return WeightHold(WeightSteps.DEFAULT.start, c.repMin, forCount)
    fun position(weight: Int, reps: Int) = if (mode == ProgressMode.WEIGHT) weight to 0 else weight to reps
    val taken = c.holds.map { position(it.weight, it.reps) }.toSet()
    val free = weights.dropLast(1).filter { position(it, c.repMin) !in taken }
    val from = c.holds.lastOrNull()?.weight ?: c.startWeight ?: lightest
    val weight = free.firstOrNull { it > from } ?: free.firstOrNull() ?: lightest
    return WeightHold(weight, c.repMin, forCount)
}

/** ± on Start or Top (spec rev 26 §3): one [step] up or down; a move that would leave 0.01..999.75 does nothing. */
fun stepWeight(value: Int, step: Int, up: Boolean): Int {
    val next = if (up) value + step else value - step
    return if (next in 1..WeightConfig.MAX_WEIGHT) next else value
}

/**
 * ± on a picker (plan Spec note 28): the next or previous of [options] (ascending), stopping at the
 * ends. A [value] not in the list goes to the nearest entry in that direction; null goes to the first.
 * Null only when there are no options.
 */
fun stepAlong(options: List<Int>, value: Int?, up: Boolean): Int? {
    if (options.isEmpty()) return null
    if (value == null) return options.first()
    return if (up) options.firstOrNull { it > value } ?: options.last() else options.lastOrNull { it < value } ?: options.first()
}

/**
 * A Top typed in the dialog (plan Spec note 30): snapped down to [start] + a whole number of [step]s, at
 * least one step above the start. Left as typed when it isn't above the start (the Top override then
 * lowers the start) or the step isn't a choice (the validator flags it).
 */
fun snapTop(top: Int, start: Int, step: Int): Int {
    if (step !in WeightValidator.STEP_CHOICES || top <= start) return top
    return start + ((top - start) / step).coerceAtLeast(1) * step
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*WeightDraftTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/WeightDraft.kt app/src/test/kotlin/com/mitenko/repkit/domain/WeightDraftTest.kt
git commit -m "Add the weight draft edits: My weights rows, kind, holds and steppers"
```

---

### Task 3: Weight overrides and their moves

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/WeightOverrides.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightOverridesTest.kt`

**Interfaces:**
- Consumes: `remapWeights`, `ladderOf`, `WeightValidator.STEP_CHOICES` (PR 1).
- Produces:
  - `enum class WeightField { UNIT, KIND, STEPS_START, STEPS_STEP, STEPS_TOP, LIST, REPS_PER_SET, REP_MIN, REP_MAX, START_WEIGHT, START_REPS, HOLDS }`
  - `sealed interface WeightMove` with `TopRaised(to)`, `TopLowered(to)`, `StartLowered(to)`, `RepMaxRaised(to)`, `RepMinLowered(to)`, `StartRepsRaised(to)`, `StartRepsLowered(to)`, `StartWeightMoved(to)`, `HoldsRemoved(count)`, `CurrentMoved(weight: Int, reps: Int?)` (weights in hundredths)
  - `data class WeightResolution(val config: WeightConfig, val moves: List<WeightMove>)`
  - `fun resolveWeightEdit(mode: ProgressMode, before: WeightConfig, after: WeightConfig, edited: WeightField?): WeightResolution`
  - `fun currentLoadMove(mode: ProgressMode, old: WeightConfig, new: WeightConfig, oldLevel: Int, newLevel: Int): WeightMove.CurrentMoved?`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WeightOverridesTest {
    private val rtw = ProgressMode.REPS_THEN_WEIGHT
    private val steps = WeightConfig(steps = WeightSteps(2000, 250, 3000))

    private fun edit(before: WeightConfig, field: WeightField?, mode: ProgressMode = rtw, transform: (WeightConfig) -> WeightConfig) =
        resolveWeightEdit(mode, before, transform(before), field)

    @Test
    fun `a start raised past the top raises the top one step above it`() {
        val r = edit(steps, WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = 3250)) }
        assertEquals(WeightSteps(3250, 250, 3500), r.config.steps)
        assertEquals(listOf(WeightMove.TopRaised(3500)), r.moves)
    }

    @Test
    fun `a start off the step grid lowers the top to whole steps`() {
        val r = edit(WeightConfig(steps = WeightSteps(2000, 250, 6000)), WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = 2100)) }
        assertEquals(WeightSteps(2100, 250, 5850), r.config.steps)
        assertEquals(listOf(WeightMove.TopLowered(5850)), r.moves)
    }

    @Test
    fun `a step that leaves the top unreachable lowers it`() {
        val r = edit(WeightConfig(steps = WeightSteps(2000, 250, 2750)), WeightField.STEPS_STEP) { it.copy(steps = it.steps.copy(step = 500)) }
        assertEquals(WeightSteps(2000, 500, 2500), r.config.steps)
        assertEquals(listOf(WeightMove.TopLowered(2500)), r.moves)
    }

    @Test
    fun `a top lowered to the start lowers the start one step`() {
        val r = edit(steps, WeightField.STEPS_TOP) { it.copy(steps = it.steps.copy(top = 2000)) }
        assertEquals(WeightSteps(1750, 250, 2000), r.config.steps)
        assertEquals(listOf(WeightMove.StartLowered(1750)), r.moves)
    }

    @Test
    fun `nothing moves when it can't be made valid`() {
        // The start would go below 0.01.
        val tiny = WeightConfig(steps = WeightSteps(100, 250, 350))
        assertEquals(emptyList<WeightMove>(), edit(tiny, WeightField.STEPS_TOP) { it.copy(steps = it.steps.copy(top = 200)) }.moves)
        // The top would pass 999.75.
        val high = WeightConfig(steps = WeightSteps(99_000, 500, 99_500))
        assertEquals(emptyList<WeightMove>(), edit(high, WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = 99_750)) }.moves)
    }

    @Test
    fun `a minimum at the maximum raises the maximum, then explicit starting reps follow`() {
        val c = WeightConfig(repMin = 8, repMax = 12, startReps = 9)
        val r = edit(c, WeightField.REP_MIN) { it.copy(repMin = 12) }
        assertEquals(listOf(12, 13, 12), listOf(r.config.repMin, r.config.repMax, r.config.startReps))
        assertEquals(listOf(WeightMove.RepMaxRaised(13), WeightMove.StartRepsRaised(12)), r.moves)
    }

    @Test
    fun `a maximum at the minimum lowers the minimum, then explicit starting reps follow`() {
        val c = WeightConfig(repMin = 8, repMax = 12, startReps = 11)
        val r = edit(c, WeightField.REP_MAX) { it.copy(repMax = 8) }
        assertEquals(listOf(7, 8, 8), listOf(r.config.repMin, r.config.repMax, r.config.startReps))
        assertEquals(listOf(WeightMove.RepMinLowered(7), WeightMove.StartRepsLowered(8)), r.moves)
    }

    @Test
    fun `starting reps outside the range move the range`() {
        val c = WeightConfig(repMin = 8, repMax = 12)
        assertEquals(listOf(WeightMove.RepMaxRaised(14)), edit(c, WeightField.START_REPS) { it.copy(startReps = 14) }.moves)
        val low = edit(c, WeightField.START_REPS) { it.copy(startReps = 6) }
        assertEquals(6, low.config.repMin)
        assertEquals(listOf(WeightMove.RepMinLowered(6)), low.moves)
    }

    @Test
    fun `untouched starting reps follow the minimum without a note`() {
        assertEquals(emptyList<WeightMove>(), edit(WeightConfig(), WeightField.REP_MIN) { it.copy(repMin = 10) }.moves)
    }

    @Test
    fun `without an edited field nothing moves`() {
        val bad = WeightConfig(repMin = 12, repMax = 12)
        val r = resolveWeightEdit(rtw, bad, bad, null)
        assertSame(bad, r.config)
        assertEquals(emptyList<WeightMove>(), r.moves)
    }

    @Test
    fun `removing the starting weight moves it down and drops a hold on a removed weight`() {
        val c = WeightConfig(
            kind = WeightsKind.LIST, list = listOf(800, 1200, 1600), startWeight = 1200,
            holds = listOf(WeightHold(1200, 8, 2), WeightHold(1600, 8, 2)),
        )
        val r = edit(c, WeightField.LIST) { it.withoutListWeight(1) }
        assertEquals(800, r.config.startWeight)
        assertEquals(listOf(WeightHold(1600, 8, 2)), r.config.holds)
        assertEquals(listOf(WeightMove.StartWeightMoved(800), WeightMove.HoldsRemoved(1)), r.moves)
    }

    @Test
    fun `an untouched starting weight is never noted`() {
        val c = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200, 1600))
        val r = edit(c, WeightField.LIST) { it.withoutListWeight(0) }
        assertNull(r.config.startWeight)
        assertEquals(emptyList<WeightMove>(), r.moves)
    }

    @Test
    fun `a list with a duplicate waits for a clean list before remapping`() {
        val c = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200), startWeight = 1200)
        val r = edit(c, WeightField.LIST) { it.withListWeight(1, 800) }
        assertEquals(1200, r.config.startWeight)
        assertEquals(emptyList<WeightMove>(), r.moves)
    }

    @Test
    fun `in Weight mode two holds that land on one weight keep the first`() {
        val c = WeightConfig(
            kind = WeightsKind.LIST, list = listOf(800, 1200, 1600),
            holds = listOf(WeightHold(1200, 8, 2), WeightHold(1200, 9, 2)),
        )
        val r = edit(c, WeightField.LIST, ProgressMode.WEIGHT) { it.withNewListWeight() }
        assertEquals(listOf(WeightHold(1200, 8, 2)), r.config.holds)
        assertEquals(listOf(WeightMove.HoldsRemoved(1)), r.moves)
    }

    @Test
    fun `a moved current weight is noted by weight in Weight mode and by load in Reps then weight`() {
        val old = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200, 1600))
        val new = old.copy(list = listOf(800, 1600))
        assertEquals(WeightMove.CurrentMoved(800, null), currentLoadMove(ProgressMode.WEIGHT, old, new, oldLevel = 1, newLevel = 0))
        assertNull(currentLoadMove(ProgressMode.WEIGHT, old, old.copy(repsPerSet = 12), 1, 1)) // the reps-per-set edit itself
        val range = WeightConfig(kind = WeightsKind.LIST, list = listOf(800, 1200), repMin = 8, repMax = 12)
        // Level 7 is 1200 × 10; with 8–9 reps it lands on 1200 × 9 (level 3).
        assertEquals(WeightMove.CurrentMoved(1200, 9), currentLoadMove(rtw, range, range.copy(repMax = 9), oldLevel = 7, newLevel = 3))
        assertNull(currentLoadMove(rtw, range, range, 7, 7))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*WeightOverridesTest*"`
Expected: compilation fails on `resolveWeightEdit`, `WeightField`, `WeightMove`, `currentLoadMove`.

- [ ] **Step 3: Implement**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind

/** The weight-mode Progression fields (spec rev 26 §3): the field an edit came from, for overrides and notes. */
enum class WeightField { UNIT, KIND, STEPS_START, STEPS_STEP, STEPS_TOP, LIST, REPS_PER_SET, REP_MIN, REP_MAX, START_WEIGHT, START_REPS, HOLDS }

/**
 * A value that moved to fit a weight-mode edit (revision 28 §6 applied to rev 26, plan Spec note 30).
 * Weights are hundredths of the workout's unit; the UI turns each move into a note.
 */
sealed interface WeightMove {
    data class TopRaised(val to: Int) : WeightMove

    data class TopLowered(val to: Int) : WeightMove

    /** The Steps start, lowered by a Top edit. */
    data class StartLowered(val to: Int) : WeightMove

    data class RepMaxRaised(val to: Int) : WeightMove

    data class RepMinLowered(val to: Int) : WeightMove

    data class StartRepsRaised(val to: Int) : WeightMove

    data class StartRepsLowered(val to: Int) : WeightMove

    /** An explicit starting weight remapped because its weight left the list (§9.2). */
    data class StartWeightMoved(val to: Int) : WeightMove

    /** Holds dropped because their weight left the list, or collided on one position (§9.2 step 4). */
    data class HoldsRemoved(val count: Int) : WeightMove

    /** The stored current load after a save remapped it (plan Spec note 31); [reps] is null in Weight mode. */
    data class CurrentMoved(val weight: Int, val reps: Int?) : WeightMove
}

data class WeightResolution(val config: WeightConfig, val moves: List<WeightMove>)

/**
 * The field you edit wins (revision 28 §6, plan Spec note 30): [after] is [before] with the user's
 * edit to [edited]. The Steps and the rep range move to fit, then a ladder or range change remaps
 * the starting weight and the holds by value (§9.2). With no edited field only the remap runs. Every
 * value that moved is in the moves, in the order it moved.
 */
fun resolveWeightEdit(mode: ProgressMode, before: WeightConfig, after: WeightConfig, edited: WeightField?): WeightResolution {
    val moves = mutableListOf<WeightMove>()
    var c = after
    if (c.kind == WeightsKind.STEPS) {
        val steps = resolveSteps(c.steps, edited, moves)
        if (steps != c.steps) c = c.copy(steps = steps) // unchanged returns the same instance
    }
    c = resolveReps(c, edited, moves)
    c = remapDraft(mode, before, c, moves)
    return WeightResolution(c, moves)
}

/** Start or Step edited: the top goes to start + whole steps. Top edited at or below the start: the start goes one step under it. */
private fun resolveSteps(s: WeightSteps, edited: WeightField?, moves: MutableList<WeightMove>): WeightSteps {
    if (s.step !in WeightValidator.STEP_CHOICES || s.start !in 1..WeightConfig.MAX_WEIGHT) return s
    return when (edited) {
        WeightField.STEPS_START, WeightField.STEPS_STEP -> {
            val top = s.start + ((s.top - s.start) / s.step).coerceAtLeast(1) * s.step
            when {
                top > WeightConfig.MAX_WEIGHT || top == s.top -> s
                top > s.top -> s.copy(top = top).also { moves += WeightMove.TopRaised(top) }
                else -> s.copy(top = top).also { moves += WeightMove.TopLowered(top) }
            }
        }
        WeightField.STEPS_TOP -> {
            val start = s.top - s.step
            if (s.top > s.start || start < 1) s else s.copy(start = start).also { moves += WeightMove.StartLowered(start) }
        }
        else -> s
    }
}

/** Revision 28 rules 1–3, per set: the edited one of min, max and starting reps wins. Untouched (null) starting reps follow the minimum. */
private fun resolveReps(start: WeightConfig, edited: WeightField?, moves: MutableList<WeightMove>): WeightConfig {
    var c = start
    val max = WeightConfig.MAX_REPS
    when (edited) {
        WeightField.REP_MIN -> if (c.repMin in 1..max) {
            if (c.repMax <= c.repMin && c.repMin < max) {
                c = c.copy(repMax = c.repMin + 1)
                moves += WeightMove.RepMaxRaised(c.repMax)
            }
            val reps = c.startReps
            if (reps != null && reps < c.repMin) {
                c = c.copy(startReps = c.repMin)
                moves += WeightMove.StartRepsRaised(c.repMin)
            }
        }
        WeightField.REP_MAX -> if (c.repMax in 1..max) {
            if (c.repMin >= c.repMax && c.repMax > 1) {
                c = c.copy(repMin = c.repMax - 1)
                moves += WeightMove.RepMinLowered(c.repMin)
            }
            val reps = c.startReps
            if (reps != null && reps > c.repMax) {
                c = c.copy(startReps = c.repMax)
                moves += WeightMove.StartRepsLowered(c.repMax)
            }
        }
        WeightField.START_REPS -> {
            val reps = c.startReps
            if (reps != null && reps in 1..max) {
                if (reps > c.repMax) {
                    c = c.copy(repMax = reps)
                    moves += WeightMove.RepMaxRaised(reps)
                } else if (reps < c.repMin) {
                    c = c.copy(repMin = reps)
                    moves += WeightMove.RepMinLowered(reps)
                }
            }
        }
        else -> Unit
    }
    return c
}

/**
 * §9.2 in the draft: when the ladder or the rep range changed, the starting weight and the holds are
 * remapped by value at once, so the pickers never offer a removed weight. A list that isn't clean yet
 * (empty, or with a duplicate) or an invalid range waits; the validator flags it. Reps mode remaps
 * as Reps then weight, like setWeightConfig.
 */
private fun remapDraft(mode: ProgressMode, before: WeightConfig, c: WeightConfig, moves: MutableList<WeightMove>): WeightConfig {
    val weights = c.weights
    val sameLadder = before.weights == weights && before.repMin == c.repMin && before.repMax == c.repMax
    if (sameLadder || weights.isEmpty() || weights != weights.sorted().distinct() || c.repMin !in 1..c.repMax) return c
    val remapMode = if (mode.usesWeights) mode else ProgressMode.REPS_THEN_WEIGHT
    val remapped = remapWeights(remapMode, before, c, oldLevel = null).config
    val start = remapped.startWeight
    if (c.startWeight != null && start != null && start != c.startWeight) moves += WeightMove.StartWeightMoved(start)
    val removed = c.holds.size - remapped.holds.size
    if (removed > 0) moves += WeightMove.HoldsRemoved(removed)
    return remapped
}

/**
 * Revision 28 rule 4 for a weight save (plan Spec note 31): where a save's remap (§9.2) took the stored
 * current load, or null when it stayed. [mode] is a weight mode; [old] and [new] are in one unit. In
 * Weight mode only the weight counts; in Reps then weight the weight or the reps.
 */
fun currentLoadMove(mode: ProgressMode, old: WeightConfig, new: WeightConfig, oldLevel: Int, newLevel: Int): WeightMove.CurrentMoved? {
    val before = ladderOf(mode, old).prescription(oldLevel)
    val after = ladderOf(mode, new).prescription(newLevel)
    return when {
        mode == ProgressMode.WEIGHT -> if (after.weight != before.weight) WeightMove.CurrentMoved(after.weight, null) else null
        after != before -> WeightMove.CurrentMoved(after.weight, after.reps)
        else -> null
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*WeightOverridesTest*"`
Expected: PASS. If `a list with a duplicate…` fails because `withListWeight` sorted `[800, 800]`, check `remapDraft`'s clean-list guard (the duplicate must skip the remap).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/WeightOverrides.kt app/src/test/kotlin/com/mitenko/repkit/domain/WeightOverridesTest.kt
git commit -m "Resolve weight-mode edits: the edited field wins and the rest moves to fit"
```

---

### Task 4: Weight validation on the page's fields

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/WeightValidation.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightValidationTest.kt`

**Interfaces:**
- Consumes: `WeightValidator.validate`, `WeightProblem` (PR 1); `WeightField` (Task 3).
- Produces:
  - `enum class WeightHoldField { WEIGHT, REPS, FOR }`
  - `data class WeightValidation(errors: Map<WeightField, WeightProblem>, rowErrors: Map<Int, WeightProblem>, holdErrors: Map<Int, Map<WeightHoldField, WeightProblem>>, holdHints: Set<Int>)` with `isValid`
  - `fun weightValidation(c: WeightConfig, mode: ProgressMode): WeightValidation`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightValidationTest {
    private val rtw = ProgressMode.REPS_THEN_WEIGHT

    @Test
    fun `the defaults are valid`() {
        assertTrue(weightValidation(WeightConfig(), ProgressMode.WEIGHT).isValid)
        assertEquals(WeightValidation(), weightValidation(WeightConfig(), rtw))
    }

    @Test
    fun `list problems go on their rows, and the count on the list`() {
        val v = weightValidation(WeightConfig(kind = WeightsKind.LIST, list = listOf(0, 800, 800)), rtw)
        assertEquals(mapOf(0 to WeightProblem.WeightOutOfRange(0), 2 to WeightProblem.DuplicateWeight(2)), v.rowErrors)
        assertFalse(v.isValid)
        val one = weightValidation(WeightConfig(kind = WeightsKind.LIST, list = listOf(800)), rtw)
        assertEquals(WeightProblem.TooFewWeights, one.errors[WeightField.LIST])
    }

    @Test
    fun `steps problems go on start, step and top, and too many steps on top`() {
        val v = weightValidation(WeightConfig(steps = WeightSteps(0, 300, 0)), rtw)
        assertEquals(WeightProblem.StepsStart, v.errors[WeightField.STEPS_START])
        assertEquals(WeightProblem.StepsStep, v.errors[WeightField.STEPS_STEP])
        assertEquals(WeightProblem.StepsTop, v.errors[WeightField.STEPS_TOP])
        val many = weightValidation(WeightConfig(steps = WeightSteps(100, 100, 5000)), rtw)
        assertEquals(WeightProblem.TooManyWeights, many.errors[WeightField.STEPS_TOP])
    }

    @Test
    fun `rep, start and hold problems go on their fields`() {
        val c = WeightConfig(
            repsPerSet = 0, repMin = 12, repMax = 12, startWeight = 2100, startReps = 20,
            holds = listOf(WeightHold(2100, 8, -1), WeightHold(2000, 30, 2)),
        )
        val v = weightValidation(c, rtw)
        assertEquals(WeightProblem.RepsPerSet, v.errors[WeightField.REPS_PER_SET])
        assertEquals(WeightProblem.RepMax, v.errors[WeightField.REP_MAX])
        assertEquals(WeightProblem.StartWeight, v.errors[WeightField.START_WEIGHT])
        assertEquals(WeightProblem.StartReps, v.errors[WeightField.START_REPS])
        assertEquals(WeightProblem.HoldWeight(0), v.holdErrors[0]!![WeightHoldField.WEIGHT])
        assertEquals(WeightProblem.HoldFor(0), v.holdErrors[0]!![WeightHoldField.FOR])
        assertEquals(WeightProblem.HoldReps(1), v.holdErrors[1]!![WeightHoldField.REPS])
    }

    @Test
    fun `a duplicate hold and too many holds are flagged`() {
        val dup = WeightConfig(holds = listOf(WeightHold(2000, 8, 2), WeightHold(2000, 9, 2)))
        assertEquals(WeightProblem.DuplicateHold(1), weightValidation(dup, ProgressMode.WEIGHT).holdErrors[1]!![WeightHoldField.WEIGHT])
        assertTrue(weightValidation(dup, rtw).isValid) // different positions in Reps then weight (§10 note 10)
        val nine = WeightConfig(holds = List(9) { WeightHold(2000 + it * 250, 8, 2) })
        assertEquals(WeightProblem.TooManyHolds, weightValidation(nine, rtw).errors[WeightField.HOLDS])
    }

    @Test
    fun `a hold held for 0 or on the top level is hinted, not an error`() {
        val c = WeightConfig(steps = WeightSteps(2000, 250, 2500), holds = listOf(WeightHold(2000, 8, 0), WeightHold(2500, 12, 3), WeightHold(2250, 8, 3)))
        val v = weightValidation(c, rtw)
        assertTrue(v.isValid)
        assertEquals(setOf(0, 1), v.holdHints)
        // In Weight mode the top level is the heaviest weight, whatever the reps.
        assertEquals(setOf(0, 1), weightValidation(c.copy(holds = c.holds.map { it.copy(reps = 8) }), ProgressMode.WEIGHT).holdHints)
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*WeightValidationTest*"`
Expected: compilation fails on `weightValidation`.

- [ ] **Step 3: Implement**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightsKind

/** A field of one weight-mode hold (spec rev 26 §3.1). */
enum class WeightHoldField { WEIGHT, REPS, FOR }

/**
 * WeightValidator's problems placed on the Progression page's fields (spec rev 26 §3.1, §10 note 7):
 * [errors] by field, [rowErrors] by My weights row, [holdErrors] by hold, and [holdHints] the holds
 * without errors that can never apply ("Hold disabled"). A draft with any error never saves (R3).
 */
data class WeightValidation(
    val errors: Map<WeightField, WeightProblem> = emptyMap(),
    val rowErrors: Map<Int, WeightProblem> = emptyMap(),
    val holdErrors: Map<Int, Map<WeightHoldField, WeightProblem>> = emptyMap(),
    val holdHints: Set<Int> = emptySet(),
) {
    val isValid: Boolean get() = errors.isEmpty() && rowErrors.isEmpty() && holdErrors.isEmpty()
}

/**
 * Validates [c] for [mode] and places each problem on its field; the first problem on a field wins.
 * Holds are checked whatever the Hold switch says, because setWeightConfig checks them (plan Spec note 32).
 */
fun weightValidation(c: WeightConfig, mode: ProgressMode): WeightValidation {
    val errors = mutableMapOf<WeightField, WeightProblem>()
    val rows = mutableMapOf<Int, WeightProblem>()
    val holds = mutableMapOf<Int, MutableMap<WeightHoldField, WeightProblem>>()
    fun hold(index: Int, field: WeightHoldField, p: WeightProblem) {
        holds.getOrPut(index, ::mutableMapOf).putIfAbsent(field, p)
    }
    WeightValidator.validate(c, mode).forEach { p ->
        when (p) {
            WeightProblem.TooFewWeights -> errors.putIfAbsent(WeightField.LIST, p)
            WeightProblem.TooManyWeights -> errors.putIfAbsent(if (c.kind == WeightsKind.STEPS) WeightField.STEPS_TOP else WeightField.LIST, p)
            is WeightProblem.WeightOutOfRange -> rows.putIfAbsent(p.index, p)
            is WeightProblem.DuplicateWeight -> rows.putIfAbsent(p.index, p)
            WeightProblem.StepsStart -> errors.putIfAbsent(WeightField.STEPS_START, p)
            WeightProblem.StepsStep -> errors.putIfAbsent(WeightField.STEPS_STEP, p)
            WeightProblem.StepsTop -> errors.putIfAbsent(WeightField.STEPS_TOP, p)
            WeightProblem.RepsPerSet -> errors.putIfAbsent(WeightField.REPS_PER_SET, p)
            WeightProblem.RepMin -> errors.putIfAbsent(WeightField.REP_MIN, p)
            WeightProblem.RepMax -> errors.putIfAbsent(WeightField.REP_MAX, p)
            WeightProblem.StartWeight -> errors.putIfAbsent(WeightField.START_WEIGHT, p)
            WeightProblem.StartReps -> errors.putIfAbsent(WeightField.START_REPS, p)
            WeightProblem.TooManyHolds -> errors.putIfAbsent(WeightField.HOLDS, p)
            is WeightProblem.HoldWeight -> hold(p.index, WeightHoldField.WEIGHT, p)
            is WeightProblem.DuplicateHold -> hold(p.index, WeightHoldField.WEIGHT, p)
            is WeightProblem.HoldReps -> hold(p.index, WeightHoldField.REPS, p)
            is WeightProblem.HoldFor -> hold(p.index, WeightHoldField.FOR, p)
        }
    }
    return WeightValidation(errors, rows, holds, inactiveHolds(c, mode, holds.keys))
}

/** Holds without errors that can never apply: held for 0, or on the top level (RepProgression never holds at the cap). */
private fun inactiveHolds(c: WeightConfig, mode: ProgressMode, withErrors: Set<Int>): Set<Int> {
    val weights = c.weights
    if (!mode.usesWeights || weights.isEmpty() || weights != weights.sorted().distinct() || c.repMin !in 1..c.repMax) return emptySet()
    val ladder = ladderOf(mode, c)
    return c.holds.indices.filter { i ->
        val h = c.holds[i]
        i !in withErrors && (h.forCount == 0 || ladder.levelOf(h.weight, h.reps) >= ladder.maxLevel)
    }.toSet()
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*WeightValidationTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/WeightValidation.kt app/src/test/kotlin/com/mitenko/repkit/domain/WeightValidationTest.kt
git commit -m "Place weight validation problems on the settings fields"
```

---

### Task 5: The repository notes a moved current load; the default-unit seam

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/EntryRepository.kt` (interface KDoc and signature, `RoomEntryRepository.setWeightConfig`)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/AppPreferences.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/di/StorageModule.kt`
- Modify: `app/src/test/kotlin/com/mitenko/repkit/testutil/FakeEntryRepository.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt`

**Interfaces:**
- Consumes: `currentLoadMove`, `WeightMove` (Task 3).
- Produces:
  - `suspend fun setWeightConfig(id: Long, weight: WeightConfig): WeightMove.CurrentMoved?` on `EntryRepository`, Room and the fake.
  - `interface WeightUnitDefaults { val weightUnitDefault: Flow<WeightUnit> }` in `com.mitenko.repkit.data`; `AppPreferences : WeightUnitDefaults`; Hilt provides it.
  - `FakeEntryRepository.weightGate: CompletableDeferred<Unit>?` (holds a weight save in flight).

- [ ] **Step 1: Write the failing tests**

Append to `RoomEntryRepositoryTest` (add imports `com.mitenko.repkit.domain.WeightMove`, `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightUnit`, `com.mitenko.repkit.domain.model.WeightsKind` if missing):

```kotlin
    @Test
    fun `setWeightConfig returns where it moved the current weight, and null when it stayed`() = runTest {
        val r = repo()
        val id = r.create("Curls")
        r.switchMode(id, ProgressMode.WEIGHT, WeightUnit.KG)
        r.overwriteCounter(id, total = 2, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // 25 kg on 20 / 2.5 / 60
        val list = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(2000, 2250, 3000))
        assertEquals(WeightMove.CurrentMoved(2250, null), r.setWeightConfig(id, list))
        assertEquals(1, db.entryDao().get(id)!!.total)
        assertNull(r.setWeightConfig(id, list.copy(repsPerSet = 12)))
    }

    @Test
    fun `setWeightConfig notes moved reps in Reps then weight, and nothing for an untouched counter`() = runTest {
        val r = repo()
        val id = r.create("Curls")
        r.switchMode(id, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG)
        assertNull(r.setWeightConfig(id, WeightConfig(unit = WeightUnit.KG, repMin = 8, repMax = 10))) // NULL total: nothing to move
        r.overwriteCounter(id, total = 2, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // 20 kg × 10
        assertEquals(WeightMove.CurrentMoved(2000, 9), r.setWeightConfig(id, WeightConfig(unit = WeightUnit.KG, repMin = 8, repMax = 9)))
    }

    @Test
    fun `setWeightConfig in Reps mode never notes a move`() = runTest {
        val r = repo()
        val id = r.create("Curls")
        assertNull(r.setWeightConfig(id, WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1200))))
    }

    // Plan Spec notes 43, 45, 46 and the transition matrix (note 47). Most of these pin behaviour PR 1
    // already has; they fail only if a later change breaks a contract.

    /** Weight mode on 10 / 5 / 25 kg (10, 15, 20, 25), a hold on 20 for 3, at 15 kg (level 1), hold count 2, fresh start still set. */
    private suspend fun matrixRow(r: RoomEntryRepository): Long {
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        r.setWeightConfig(a, WeightConfig(unit = WeightUnit.KG, steps = WeightSteps(1000, 500, 2500), holds = listOf(WeightHold(2000, 8, 3))))
        val row = db.entryDao().get(a)!!
        db.entryDao().setCounter(a, 1, row.bestStreak, row.currentStreak, holdCount = 2, lastCheckIn = row.lastCheckIn)
        return a
    }

    private suspend fun weightsOf(r: RoomEntryRepository, a: Long) = r.entry(a).first()!!.progression.weight

    /** total, hold_count, fresh_start as stored. */
    private suspend fun counterOf(a: Long) = db.entryDao().get(a)!!.let { listOf<Any?>(it.total, it.holdCount, it.freshStart) }

    @Test
    fun `matrix - Steps to My weights with the same values keeps the level, the hold count and the fresh start`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertNull(r.setWeightConfig(a, weightsOf(r, a).withKind(WeightsKind.LIST)))
        assertEquals("1000,1500,2000,2500", db.entryDao().get(a)!!.weightList)
        assertEquals(listOf<Any?>(1, 2, true), counterOf(a))
    }

    @Test
    fun `matrix - a unit change converts in one save and keeps the level, the hold count, the fresh start and the history`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        db.checkInDao().insert(CheckInEntity(entryId = a, at = 1L, total = 1, weight = 1500, reps = 10, unit = "KG"))
        val history = db.checkInDao().getForEntry(a)
        val lb = WeightConversion.convert(weightsOf(r, a), WeightUnit.LB)
        assertNull(r.setWeightConfig(a, lb))
        assertEquals(lb, weightsOf(r, a))
        assertEquals(listOf<Any?>(1, 2, true), counterOf(a))
        assertEquals(history, db.checkInDao().getForEntry(a))
    }

    @Test
    fun `matrix - removing a weight renumbers the level but keeps its value and the hold count`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertNull(r.setWeightConfig(a, weightsOf(r, a).withKind(WeightsKind.LIST).withoutListWeight(0))) // 10 kg gone
        assertEquals(listOf<Any?>(0, 2, true), counterOf(a)) // 15 kg is now level 0
    }

    @Test
    fun `matrix - removing the current weight moves it down, notes it and resets the hold count`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertEquals(WeightMove.CurrentMoved(1000, null), r.setWeightConfig(a, weightsOf(r, a).withKind(WeightsKind.LIST).withoutListWeight(1)))
        assertEquals(listOf<Any?>(0, 0, true), counterOf(a))
    }

    @Test
    fun `matrix - a hold whose weight is removed is dropped and resets the hold count`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertNull(r.setWeightConfig(a, weightsOf(r, a).withKind(WeightsKind.LIST).withoutListWeight(2))) // 20 kg gone; its hold is still in the draft
        assertEquals(emptyList<WeightHold>(), weightsOf(r, a).holds)
        assertEquals(listOf<Any?>(1, 0, true), counterOf(a))
    }

    @Test
    fun `matrix - reset to defaults in a weight mode moves to the lightest default and resets the hold count`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        assertEquals(WeightMove.CurrentMoved(2000, null), r.setWeightConfig(a, WeightConfig(unit = WeightUnit.KG))) // nothing ≤ 15 kg: the lightest, 20
        assertEquals(WeightConfig(unit = WeightUnit.KG), weightsOf(r, a))
        assertEquals(listOf<Any?>(0, 0, true), counterOf(a))
    }

    @Test
    fun `the stored row doesn't depend on whether the draft was remapped or sorted first`() = runTest {
        val r = repo()
        val a = matrixRow(r)
        val b = matrixRow(r)
        val before = weightsOf(r, a).withKind(WeightsKind.LIST).copy(startWeight = 1500)
        r.setWeightConfig(a, before)
        r.setWeightConfig(b, before)
        // One edit removing 15 kg, the start and current weight. a: stale and unsorted; b: remapped by the page first.
        val stale = before.copy(list = listOf(2500, 1000, 2000))
        val remapped = resolveWeightEdit(ProgressMode.WEIGHT, before, before.withoutListWeight(1), WeightField.LIST).config
        assertEquals(1000, remapped.startWeight)
        assertEquals(WeightMove.CurrentMoved(1000, null), r.setWeightConfig(a, stale))
        assertEquals(WeightMove.CurrentMoved(1000, null), r.setWeightConfig(b, remapped))
        assertEquals(db.entryDao().get(a)!!.copy(id = 0, position = 0), db.entryDao().get(b)!!.copy(id = 0, position = 0))
    }

    @Test
    fun `switchMode keeps a unit the workout already has`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.LB)
        r.switchMode(a, ProgressMode.REPS, WeightUnit.KG)
        r.switchMode(a, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG) // the app default is now kg
        assertEquals("LB", db.entryDao().get(a)!!.weightUnit)
    }
```

(Further imports for these: `com.mitenko.repkit.domain.WeightField`, `com.mitenko.repkit.domain.resolveWeightEdit`, `com.mitenko.repkit.domain.withKind`, `com.mitenko.repkit.domain.withoutListWeight`, `com.mitenko.repkit.domain.model.WeightHold`, if missing. `CheckInEntity`, `WeightConversion` and `WeightSteps` are already imported. `EntryEntity` has `id` and `position`; both rows are named "Curls".)

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*RoomEntryRepositoryTest*"`
Expected: compilation fails: `setWeightConfig` returns `Unit`.

- [ ] **Step 3: Implement**

In `EntryRepository.kt`, change the interface method and add a KDoc bullet before "Neither the mode…":

```kotlin
     * - Returns where the stored current load moved, or null (plan Spec note 31, revision 28 rule 4):
     *   only in a weight mode with a stored (non-NULL) level, by weight in Weight mode and by load in
     *   Reps then weight.
     ...
    suspend fun setWeightConfig(id: Long, weight: WeightConfig): WeightMove.CurrentMoved?
```

In `RoomEntryRepository.setWeightConfig`, make it return the transaction's value and end the transaction block with the move:

```kotlin
    override suspend fun setWeightConfig(id: Long, weight: WeightConfig): WeightMove.CurrentMoved? {
        gate.awaitReady()
        return db.withTransaction {
            // … every existing line unchanged, through `with(config) { dao.setWeightConfig(…) }` …
            val newLevel = remap?.level
            if (mode.usesWeights && oldLevel != null && newLevel != null) currentLoadMove(mode, old, config, oldLevel, newLevel) else null
        }
    }
```

Add imports `com.mitenko.repkit.domain.WeightMove` and `com.mitenko.repkit.domain.currentLoadMove`.

In `AppPreferences.kt`, add above the class:

```kotlin
/**
 * The unit new weight workouts start in (spec rev 26 §5), as the settings need it (plan Spec note 36).
 * [AppPreferences] is the real one; ViewModel tests pass a fixed flow.
 */
interface WeightUnitDefaults {
    val weightUnitDefault: Flow<WeightUnit>
}
```

Make the class `class AppPreferences(…) : WeightUnitDefaults` and mark the property `override val weightUnitDefault: Flow<WeightUnit> = …` (body unchanged).

In `di/StorageModule.kt`, add after `appPreferences` (import `com.mitenko.repkit.data.WeightUnitDefaults`):

```kotlin
    /** Plan Spec note 36: the settings read the app default unit through this seam. */
    @Provides
    fun weightUnitDefaults(preferences: AppPreferences): WeightUnitDefaults = preferences
```

In `FakeEntryRepository.kt`, add next to `progressionGate`:

```kotlin
    /** When set, setWeightConfig suspends on it after counting the call, so a test can hold a weight save in flight. */
    var weightGate: CompletableDeferred<Unit>? = null
```

and replace `setWeightConfig`:

```kotlin
    /** Room's remap without its validation (settings validation is left to the ViewModels under test). */
    override suspend fun setWeightConfig(id: Long, weight: WeightConfig): WeightMove.CurrentMoved? {
        weightWrites++
        weightGate?.await()
        failIfAsked()
        var moved: WeightMove.CurrentMoved? = null
        edit(id) {
            val stored = it.progression
            val mode = stored.mode
            val draft = weight.copy(list = weight.list.sorted(), unit = weight.unit ?: stored.weight.unit)
            val old = draft.unit?.let { u -> WeightConversion.convert(stored.weight, u) } ?: stored.weight
            val remapMode = if (mode.usesWeights) mode else ProgressMode.REPS_THEN_WEIGHT
            val remap = remapWeights(remapMode, old, draft, if (mode.usesWeights) it.counter.total else null)
            val newLevel = remap.level
            if (mode.usesWeights && newLevel != null) moved = currentLoadMove(mode, old, remap.config, it.counter.total, newLevel)
            val holdCount = if (mode.usesWeights && weightHoldResetNeeded(mode, old, remap)) 0 else it.counter.holdCount
            it.copy(
                progression = stored.copy(weight = remap.config),
                counter = it.counter.copy(total = newLevel ?: it.counter.total, holdCount = holdCount),
            )
        }
        return moved
    }
```

(imports `com.mitenko.repkit.domain.WeightMove`, `com.mitenko.repkit.domain.currentLoadMove`).

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*RoomEntryRepositoryTest*" --tests "*AppPreferencesTest*"`
Expected: PASS (existing tests unchanged). The `matrix -`, `the stored row doesn't depend…` and `switchMode keeps a unit…` tests are contract tests (notes 43, 45–47): they compile only after Step 3 and should pass with it. If one fails, the code breaks a stated contract; stop and report it rather than editing the test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/data/EntryRepository.kt app/src/main/kotlin/com/mitenko/repkit/data/AppPreferences.kt app/src/main/kotlin/com/mitenko/repkit/di/StorageModule.kt app/src/test/kotlin/com/mitenko/repkit/testutil/FakeEntryRepository.kt app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt
git commit -m "Return the moved current load from setWeightConfig; add the default-unit seam"
```

---

### Task 6: Strings, translations and the weight texts

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify (generated, never by hand): `app/src/main/res/values-es/strings.xml`, `values-zh-rCN/strings.xml`, `values-hi/strings.xml`
- Modify: `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py` (outside the repo)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/domain/SettingsValidator.kt` (`FieldMessage.NotOnLadder`, `currentState(levels)`)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/common/FieldMessages.kt`, `app/src/main/kotlin/com/mitenko/repkit/ui/common/EditValueDialog.kt`
- Create: `app/src/main/kotlin/com/mitenko/repkit/ui/common/WeightTexts.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/SettingsValidatorTest.kt`, `app/src/test/kotlin/com/mitenko/repkit/ui/common/WeightTextsTest.kt`

**Interfaces:**
- Consumes: `WeightFormat` (Task 1), `WeightMove` (Task 3), `WeightProblem` (PR 1).
- Produces:
  - every `R.string` / `R.plurals` below;
  - `FieldMessage.NotOnLadder`; `SettingsValidator.currentState(total, bestStreak, currentStreak, lastCheckIn, now, levels: IntRange? = null)`;
  - `ValueInput.WEIGHT`;
  - in `ui/common/WeightTexts.kt`: `@get:StringRes val WeightUnit.shortLabel: Int`, `val WeightUnit.longLabel: Int`, `val ProgressMode.label: Int`, `fun weightText(hundredths: Int, unit: WeightUnit?): UiText`, `@Composable fun unitLabel(@StringRes label: Int, unit: WeightUnit?): String`, `fun WeightProblem.uiText(): UiText`, `fun WeightMove.uiText(unit: WeightUnit?): UiText`, `@Composable fun weightNoteText(moves: List<WeightMove>, unit: WeightUnit?): String`.

- [ ] **Step 1: Write the failing tests**

Append to `SettingsValidatorTest` (inside the class):

```kotlin
    @Test
    fun `in a weight mode the total is a level: 0 is valid and past the top isn't`() {
        val now = Instant.parse("2026-10-06T12:00:00Z")
        assertTrue(SettingsValidator.currentState(0, 0, 0, null, now, levels = 0..16).isValid)
        assertEquals(FieldMessage.NotOnLadder, SettingsValidator.currentState(17, 0, 0, null, now, levels = 0..16).errors[Field.TOTAL])
        assertEquals(FieldMessage.AtLeastOne, SettingsValidator.currentState(0, 0, 0, null, now).errors[Field.TOTAL])
    }
```

Create `ui/common/WeightTextsTest.kt`:

```kotlin
package com.mitenko.repkit.ui.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.WeightProblem
import com.mitenko.repkit.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeightTextsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun res(tag: String): Resources =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }).resources

    private val en = res("en")

    @Test
    fun `weights read with their unit`() {
        assertEquals("22.5 kg", weightText(2250, WeightUnit.KG).resolve(en))
        assertEquals("20 lb", weightText(2000, WeightUnit.LB).resolve(en))
        assertEquals("20", weightText(2000, null).resolve(en))
    }

    @Test
    fun `moves read as notes`() {
        assertEquals("Top raised to 35 lb", WeightMove.TopRaised(3500).uiText(WeightUnit.LB).resolve(en))
        assertEquals("Starting weight moved to 8 kg", WeightMove.StartWeightMoved(800).uiText(WeightUnit.KG).resolve(en))
        assertEquals("2 holds removed (their weights are gone)", WeightMove.HoldsRemoved(2).uiText(WeightUnit.KG).resolve(en))
        assertEquals("Current weight moved to 12 kg × 9", WeightMove.CurrentMoved(1200, 9).uiText(WeightUnit.KG).resolve(en))
        assertEquals("Maximum reps per set raised to 13", WeightMove.RepMaxRaised(13).uiText(WeightUnit.KG).resolve(en))
    }

    @Test
    fun `problems read as field errors`() {
        assertEquals("Add at least 2 weights", WeightProblem.TooFewWeights.uiText().resolve(en))
        assertEquals("At most 40 weights", WeightProblem.TooManyWeights.uiText().resolve(en))
        assertEquals("Already in the list", WeightProblem.DuplicateWeight(1).uiText().resolve(en))
        assertEquals("Pick one of your weights", WeightProblem.HoldWeight(0).uiText().resolve(en))
    }

    @Test
    fun `every problem and move resolves in every language`() {
        val problems = listOf(
            WeightProblem.TooFewWeights, WeightProblem.TooManyWeights, WeightProblem.WeightOutOfRange(0), WeightProblem.DuplicateWeight(1),
            WeightProblem.StepsStart, WeightProblem.StepsStep, WeightProblem.StepsTop, WeightProblem.RepsPerSet, WeightProblem.RepMin,
            WeightProblem.RepMax, WeightProblem.StartWeight, WeightProblem.StartReps, WeightProblem.TooManyHolds, WeightProblem.HoldWeight(0),
            WeightProblem.HoldReps(0), WeightProblem.HoldFor(0), WeightProblem.DuplicateHold(1),
        )
        val moves = listOf(
            WeightMove.TopRaised(3500), WeightMove.TopLowered(2500), WeightMove.StartLowered(1750), WeightMove.RepMaxRaised(13),
            WeightMove.RepMinLowered(7), WeightMove.StartRepsRaised(12), WeightMove.StartRepsLowered(8), WeightMove.StartWeightMoved(800),
            WeightMove.HoldsRemoved(1), WeightMove.HoldsRemoved(2), WeightMove.CurrentMoved(800, null), WeightMove.CurrentMoved(1200, 9),
        )
        listOf("en", "es", "zh-CN", "hi").map(::res).forEach { r ->
            problems.forEach { assertTrue("$it", it.uiText().resolve(r).isNotBlank()) }
            moves.forEach {
                val text = it.uiText(WeightUnit.KG).resolve(r)
                assertTrue(text, text.isNotBlank())
                assertFalse(text, text.contains('%'))
            }
        }
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*SettingsValidatorTest*" --tests "*WeightTextsTest*"`
Expected: compilation fails (`levels`, `NotOnLadder`, `weightText`, `uiText` unresolved).

- [ ] **Step 3: Add the English strings**

Append before `</resources>` in `app/src/main/res/values/strings.xml`:

```xml

    <!-- Weight progression settings (spec rev 26 §3, PR 2) -->
    <string name="progress_by">Progress by</string>
    <string name="progress_reps">Reps</string>
    <string name="progress_weight">Weight</string>
    <string name="progress_reps_then_weight">Reps then weight</string>
    <string name="info_progress_by">What goes up when you check in on time. Reps adds one rep to your total. Weight moves you to the next weight, with the same reps per set. Reps then weight adds a rep to every set until the top of your range, then moves to the next weight and starts the reps again.</string>
    <string name="start_fresh_title">Start fresh?</string>
    <string name="start_fresh_body_weight">This workout restarts at its starting weight. Your streaks and history are kept.</string>
    <string name="start_fresh_body_reps">This workout restarts at its starting reps. Your streaks and history are kept.</string>
    <string name="start_fresh">Start fresh</string>
    <string name="weight_unit">Unit</string>
    <string name="unit_kg">kg</string>
    <string name="unit_lb">lb</string>
    <string name="unit_kg_long">Kilograms (kg)</string>
    <string name="unit_lb_long">Pounds (lb)</string>
    <string name="info_weight_unit">The unit for this workout\'s weights. Changing it converts every weight, rounded to the nearest 0.25.</string>
    <!-- %1$s is the new unit, e.g. lb -->
    <string name="change_unit_title">Switch to %1$s?</string>
    <string name="change_unit_body">Your weights, starting weight and holds will convert to %1$s, rounded to the nearest 0.25. Your history keeps the unit it was recorded in.</string>
    <string name="convert">Convert</string>
    <!-- %1$s is a weight such as 22.5, %2$s its unit -->
    <string name="weight_value">%1$s %2$s</string>
    <!-- A field label with the workout's unit: %1$s the label, %2$s the unit -->
    <string name="label_with_unit">%1$s (%2$s)</string>
    <string name="weights_source">Weights</string>
    <string name="weights_steps">Steps</string>
    <string name="weights_list">My weights</string>
    <string name="info_weights_source">Steps makes evenly spaced weights from a start, a step and a top, like 20 to 60 by 2.5. My weights is your own list, for equipment with uneven gaps such as kettlebells.</string>
    <string name="steps_start">Start</string>
    <string name="steps_step">Step</string>
    <string name="steps_top">Top</string>
    <string name="info_steps_start">The lightest weight.</string>
    <string name="info_steps_step">The gap between one weight and the next.</string>
    <string name="info_steps_top">The heaviest weight. It must be the start plus a whole number of steps.</string>
    <string name="weight_n">Weight %1$d</string>
    <!-- %1$s is a weight with its unit, e.g. 20 kg -->
    <string name="remove_weight">Remove %1$s</string>
    <string name="add_weight">Add weight</string>
    <string name="info_my_weights">The weights you own, lightest to heaviest. Tap a weight to change it; the list keeps itself in order.</string>
    <string name="reps_per_set">Reps per set</string>
    <string name="info_reps_per_set">How many reps you do in every set. They stay the same as the weight goes up.</string>
    <string name="rep_range_min">Minimum reps per set</string>
    <string name="rep_range_max">Maximum reps per set</string>
    <string name="info_rep_range_min">Where your reps start at each new weight.</string>
    <string name="info_rep_range_max">The reps per set to reach before you move to the next weight.</string>
    <string name="start_weight">Starting weight</string>
    <string name="info_start_weight">The weight you start at, and return to after you reset your progress.</string>
    <string name="start_reps">Starting reps per set</string>
    <string name="info_start_reps">The reps per set you start at. Setting them outside the range moves the range to fit.</string>
    <string name="hold_at_weight">Hold at weight</string>
    <string name="hold_at_reps">Hold at reps per set</string>
    <string name="hold_n_weight">Hold %1$d weight</string>
    <string name="hold_n_reps" tools:ignore="PluralsCandidate">Hold %1$d reps</string>
    <string name="info_hold_at_weight">The weight where progression pauses. Pick one of your weights.</string>
    <string name="info_hold_at_reps">The reps per set where progression pauses, within your rep range.</string>
    <string name="info_hold_weight">Want to stay at a weight for a while? A hold pauses your progression there for a number of check-ins before you climb again. You can set multiple holds.</string>
    <string name="penalty_rate_steps">Missed-day adjustment (hours per step)</string>
    <string name="info_penalty_rate_steps">After you miss the on-time window, you drop one step for every this many hours you are away. A step is one weight (Weight) or one rep per set (Reps then weight), and a miss never drops you more than one weight.</string>
    <!-- Notes: %1$s is a weight with its unit, %1$d a number of reps -->
    <string name="moved_top_raised">Top raised to %1$s</string>
    <string name="moved_top_lowered">Top lowered to %1$s</string>
    <string name="moved_steps_start_lowered">Start lowered to %1$s</string>
    <string name="moved_rep_max_raised">Maximum reps per set raised to %1$d</string>
    <string name="moved_rep_min_lowered">Minimum reps per set lowered to %1$d</string>
    <string name="moved_start_reps_raised">Starting reps per set raised to %1$d</string>
    <string name="moved_start_reps_lowered">Starting reps per set lowered to %1$d</string>
    <string name="moved_start_weight">Starting weight moved to %1$s</string>
    <plurals name="moved_holds_removed">
        <item quantity="one">%1$d hold removed (its weight is gone)</item>
        <item quantity="other">%1$d holds removed (their weights are gone)</item>
    </plurals>
    <string name="moved_current_weight">Current weight moved to %1$s</string>
    <!-- %1$s is a weight with its unit, %2$d the reps per set -->
    <string name="moved_current_load">Current weight moved to %1$s × %2$d</string>
    <string name="current_weight">Current weight</string>
    <string name="info_current_weight">The weight for your next workout. Changes take effect immediately.</string>
    <string name="current_reps_per_set">Current reps per set</string>
    <string name="info_current_reps_per_set">The reps per set for your next workout, within your rep range.</string>
    <string name="reset_progress_body_weight">Your weight and reps will return to the starting point, your streaks will reset to 0, and your last check-in will be cleared.</string>
    <string name="units">Units</string>
    <string name="info_units">The unit new weight workouts start in. Workouts that already use weights keep their own unit.</string>
    <plurals name="error_too_few_weights">
        <item quantity="one">Add at least %1$d weight</item>
        <item quantity="other">Add at least %1$d weights</item>
    </plurals>
    <plurals name="error_too_many_weights">
        <item quantity="one">At most %1$d weight</item>
        <item quantity="other">At most %1$d weights</item>
    </plurals>
    <string name="error_weight_range">Must be more than 0 and at most 999.75</string>
    <string name="error_duplicate_weight">Already in the list</string>
    <string name="error_steps_step">Pick one of the step sizes</string>
    <string name="error_steps_top">Must be above the start, in whole steps</string>
    <string name="error_reps_range">Must be 1–100</string>
    <string name="error_rep_max">Must be above the minimum, up to 100</string>
    <string name="error_not_a_weight">Pick one of your weights</string>
    <string name="error_outside_rep_range">Must be within the rep range</string>
    <string name="error_duplicate_weight_hold">Another hold is already here</string>
    <string name="error_enter_weight">Enter a number with up to 2 decimals</string>
```

(`strings.xml` already declares `xmlns:tools`, which `hold_n_for` uses.)

- [ ] **Step 4: Add the translations and generate the three locale files**

Append to `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py`, at the very end (after the Sets-prompt block). The wording follows the first review round's glossary in that file: Spanish "mantener / mantenimiento" for a hold (never "pausa") and "ventana de tiempo"; Chinese "漏打卡"; Hindi "छूटे चेक-इन" and "प्रगति होल्ड होती है". This block runs after the file's Hindi replace loop, so it's already in the final wording. `P(es_one, es_other, zh, hi_one, hi_other)` is the file's plural helper.

```python
# --- Weight progression settings (spec rev 26 PR 2, feat/weight-settings, 2026-10-06) ---
T.update({
 "progress_by": ("Progresar por", "进阶方式", "प्रगति का तरीका"),
 "progress_reps": ("Repeticiones", "次数", "रेप्स"),
 "progress_weight": ("Peso", "重量", "वज़न"),
 "progress_reps_then_weight": ("Repeticiones y luego peso", "先次数后重量", "पहले रेप्स, फिर वज़न"),
 "info_progress_by": ("Lo que sube cuando registras a tiempo. Repeticiones suma una repetición a tu total. Peso te pasa al siguiente peso, con las mismas repeticiones por serie. Repeticiones y luego peso suma una repetición a cada serie hasta el tope de tu rango y luego pasa al siguiente peso y vuelve a empezar las repeticiones.",
                      "准时打卡时增加的内容。次数：总次数加 1。重量：换到下一个重量，每组次数不变。先次数后重量：每组加 1 次，直到达到范围上限，然后换到下一个重量，次数重新开始。",
                      "समय पर चेक-इन करने पर क्या बढ़ता है। रेप्स: आपके कुल रेप्स में एक रेप जुड़ता है। वज़न: आप अगले वज़न पर जाते हैं, हर सेट के रेप्स वही रहते हैं। पहले रेप्स, फिर वज़न: हर सेट में एक रेप जुड़ता है जब तक आप अपनी रेंज के ऊपर तक नहीं पहुँचते, फिर आप अगले वज़न पर जाते हैं और रेप्स फिर से शुरू होते हैं।"),
 "start_fresh_title": ("¿Empezar de nuevo?", "重新开始？", "नए सिरे से शुरू करें?"),
 "start_fresh_body_weight": ("Este entrenamiento vuelve a empezar en su peso inicial. Tus rachas y tu historial se conservan.", "此训练将从初始重量重新开始。你的连续记录和历史会保留。", "यह वर्कआउट अपने शुरुआती वज़न से फिर शुरू होगा। आपकी स्ट्रीक और इतिहास बने रहेंगे।"),
 "start_fresh_body_reps": ("Este entrenamiento vuelve a empezar en sus repeticiones iniciales. Tus rachas y tu historial se conservan.", "此训练将从初始次数重新开始。你的连续记录和历史会保留。", "यह वर्कआउट अपने शुरुआती रेप्स से फिर शुरू होगा। आपकी स्ट्रीक और इतिहास बने रहेंगे।"),
 "start_fresh": ("Empezar de nuevo", "重新开始", "नए सिरे से शुरू करें"),
 "weight_unit": ("Unidad", "单位", "इकाई"),
 "unit_kg": ("kg", "公斤", "किलो"),
 "unit_lb": ("lb", "磅", "पाउंड"),
 "unit_kg_long": ("Kilogramos (kg)", "公斤 (kg)", "किलोग्राम (kg)"),
 "unit_lb_long": ("Libras (lb)", "磅 (lb)", "पाउंड (lb)"),
 "info_weight_unit": ("La unidad de los pesos de este entrenamiento. Al cambiarla, cada peso se convierte y se redondea al 0.25 más cercano.", "此训练所用重量的单位。更改后，所有重量都会换算并四舍五入到最接近的 0.25。", "इस वर्कआउट के वज़न की इकाई। इसे बदलने पर हर वज़न बदल जाता है और सबसे पास के 0.25 तक गोल हो जाता है।"),
 "change_unit_title": ("¿Cambiar a %1$s?", "切换为%1$s？", "%1$s पर बदलें?"),
 "change_unit_body": ("Tus pesos, tu peso inicial y tus mantenimientos se convertirán a %1$s, redondeados al 0.25 más cercano. Tu historial conserva la unidad con la que se registró.", "你的重量、初始重量和保持都将换算为%1$s，并四舍五入到最接近的 0.25。历史记录保留记录时的单位。", "आपके वज़न, शुरुआती वज़न और होल्ड %1$s में बदल जाएँगे, सबसे पास के 0.25 तक गोल करके। आपका इतिहास उसी इकाई में रहेगा जिसमें वह दर्ज हुआ था।"),
 "convert": ("Convertir", "换算", "बदलें"),
 "weight_value": ("%1$s %2$s", "%1$s %2$s", "%1$s %2$s"),
 "label_with_unit": ("%1$s (%2$s)", "%1$s（%2$s）", "%1$s (%2$s)"),
 "weights_source": ("Pesos", "重量", "वज़न"),
 "weights_steps": ("Incrementos", "按步长", "स्टेप्स"),
 "weights_list": ("Mis pesos", "我的重量", "मेरे वज़न"),
 "info_weights_source": ("Incrementos crea pesos espaciados por igual a partir de un inicio, un incremento y un tope, como de 20 a 60 de 2.5 en 2.5. Mis pesos es tu propia lista, para equipo con saltos desiguales como las pesas rusas.", "按步长：根据起点、步长和上限生成等间距的重量，例如从 20 到 60，每次加 2.5。我的重量：你自己的重量列表，适合壶铃等间距不均的器材。", "स्टेप्स: शुरुआत, स्टेप और ऊपरी सीमा से बराबर दूरी वाले वज़न बनाता है, जैसे 20 से 60 तक 2.5 के अंतर पर। मेरे वज़न: आपकी अपनी सूची, केटलबेल जैसे उपकरणों के लिए जिनमें अंतर बराबर नहीं होता।"),
 "steps_start": ("Inicio", "起点", "शुरुआत"),
 "steps_step": ("Incremento", "步长", "स्टेप"),
 "steps_top": ("Tope", "上限", "ऊपरी सीमा"),
 "info_steps_start": ("El peso más ligero.", "最轻的重量。", "सबसे हल्का वज़न।"),
 "info_steps_step": ("La diferencia entre un peso y el siguiente.", "相邻两个重量之间的差值。", "एक वज़न और अगले वज़न के बीच का अंतर।"),
 "info_steps_top": ("El peso más pesado. Debe ser el inicio más un número entero de incrementos.", "最重的重量。必须等于起点加上整数个步长。", "सबसे भारी वज़न। यह शुरुआत में पूरे-पूरे स्टेप जोड़कर बनना चाहिए।"),
 "weight_n": ("Peso %1$d", "重量 %1$d", "वज़न %1$d"),
 "remove_weight": ("Quitar %1$s", "删除 %1$s", "%1$s हटाएँ"),
 "add_weight": ("Añadir peso", "添加重量", "वज़न जोड़ें"),
 "info_my_weights": ("Los pesos que tienes, del más ligero al más pesado. Toca un peso para cambiarlo; la lista se ordena sola.", "你拥有的重量，从轻到重。点按某个重量即可修改，列表会自动排序。", "आपके पास मौजूद वज़न, हल्के से भारी तक। किसी वज़न को बदलने के लिए उस पर टैप करें; सूची अपने आप क्रम में रहती है।"),
 "reps_per_set": ("Repeticiones por serie", "每组次数", "प्रति सेट रेप्स"),
 "info_reps_per_set": ("Cuántas repeticiones haces en cada serie. No cambian cuando sube el peso.", "每组做多少次。重量增加时保持不变。", "हर सेट में आप कितने रेप्स करते हैं। वज़न बढ़ने पर ये वही रहते हैं।"),
 "rep_range_min": ("Repeticiones mínimas por serie", "每组最少次数", "प्रति सेट न्यूनतम रेप्स"),
 "rep_range_max": ("Repeticiones máximas por serie", "每组最多次数", "प्रति सेट अधिकतम रेप्स"),
 "info_rep_range_min": ("Donde empiezan tus repeticiones con cada peso nuevo.", "每换一个新重量时，次数从这里开始。", "हर नए वज़न पर आपके रेप्स यहीं से शुरू होते हैं।"),
 "info_rep_range_max": ("Las repeticiones por serie que debes alcanzar antes de pasar al siguiente peso.", "换到下一个重量之前，每组需要达到的次数。", "अगले वज़न पर जाने से पहले हर सेट में इतने रेप्स तक पहुँचना है।"),
 "start_weight": ("Peso inicial", "初始重量", "शुरुआती वज़न"),
 "info_start_weight": ("El peso con el que empiezas y al que vuelves si restableces tu progreso.", "开始时的重量，重置进度后也会回到这个重量。", "जिस वज़न से आप शुरू करते हैं, और प्रगति रीसेट करने पर जिस पर लौटते हैं।"),
 "start_reps": ("Repeticiones iniciales por serie", "每组初始次数", "प्रति सेट शुरुआती रेप्स"),
 "info_start_reps": ("Las repeticiones por serie con las que empiezas. Si las pones fuera del rango, el rango se ajusta.", "开始时每组的次数。如果设在范围之外，范围会随之调整。", "प्रति सेट जितने रेप्स से आप शुरू करते हैं। इन्हें रेंज से बाहर रखने पर रेंज अपने आप बदल जाती है।"),
 "hold_at_weight": ("Mantener en el peso", "保持在重量", "इस वज़न पर होल्ड करें"),
 "hold_at_reps": ("Mantener en repeticiones por serie", "保持在每组次数", "प्रति सेट इतने रेप्स पर होल्ड करें"),
 "hold_n_weight": ("Mantener %1$d: peso", "保持 %1$d 的重量", "होल्ड %1$d का वज़न"),
 "hold_n_reps": ("Mantener %1$d: repeticiones", "保持 %1$d 的次数", "होल्ड %1$d के रेप्स"),
 "info_hold_at_weight": ("El peso en el que se detiene la progresión. Elige uno de tus pesos.", "进阶暂停的重量。从你的重量中选择一个。", "वह वज़न जहाँ प्रगति होल्ड होती है। अपने वज़नों में से एक चुनें।"),
 "info_hold_at_reps": ("Las repeticiones por serie en las que se detiene la progresión, dentro de tu rango.", "进阶暂停时的每组次数，需在你的次数范围内。", "प्रति सेट वे रेप्स जहाँ प्रगति होल्ड होती है, आपकी रेप रेंज के अंदर।"),
 "info_hold_weight": ("¿Quieres quedarte en un peso un tiempo? Un mantenimiento detiene ahí tu progresión durante varios registros antes de volver a subir. Puedes configurar varios mantenimientos.", "想在某个重量上多练一阵？保持会让你的进阶在那里暂停若干次打卡，然后再继续提升。你可以设置多个保持。", "किसी वज़न पर कुछ समय रहना चाहते हैं? होल्ड आपकी प्रगति को वहाँ कुछ चेक-इन तक रोक देता है, फिर आप आगे बढ़ते हैं। आप कई होल्ड लगा सकते हैं।"),
 "penalty_rate_steps": ("Ajuste por días perdidos (horas por escalón)", "漏打卡调整（每级的小时数）", "छूटे चेक-इन का समायोजन (प्रति स्टेप घंटे)"),
 "info_penalty_rate_steps": ("Si te pasas de la ventana de tiempo, bajas un escalón por cada tantas horas que estés fuera. Un escalón es un peso (Peso) o una repetición por serie (Repeticiones y luego peso), y un día perdido nunca te baja más de un peso.", "错过准时窗口后，你离开期间每满这么多小时就下降一级。一级是一个重量（重量）或每组一次（先次数后重量），一次漏打卡最多只会让你下降一个重量。", "समय पर चेक-इन की सीमा छूटने के बाद, आप जितने घंटे दूर रहते हैं, हर इतने घंटे पर एक स्टेप नीचे आते हैं। एक स्टेप एक वज़न (वज़न) या प्रति सेट एक रेप (पहले रेप्स, फिर वज़न) है, और छूटा चेक-इन आपको कभी एक वज़न से ज़्यादा नीचे नहीं लाता।"),
 "moved_top_raised": ("Tope aumentado a %1$s", "上限已提高到 %1$s", "ऊपरी सीमा बढ़ाकर %1$s की गई"),
 "moved_top_lowered": ("Tope reducido a %1$s", "上限已降低到 %1$s", "ऊपरी सीमा घटाकर %1$s की गई"),
 "moved_steps_start_lowered": ("Inicio reducido a %1$s", "起点已降低到 %1$s", "शुरुआत घटाकर %1$s की गई"),
 "moved_rep_max_raised": ("Repeticiones máximas por serie aumentadas a %1$d", "每组最多次数已提高到 %1$d", "प्रति सेट अधिकतम रेप्स बढ़ाकर %1$d किए गए"),
 "moved_rep_min_lowered": ("Repeticiones mínimas por serie reducidas a %1$d", "每组最少次数已降低到 %1$d", "प्रति सेट न्यूनतम रेप्स घटाकर %1$d किए गए"),
 "moved_start_reps_raised": ("Repeticiones iniciales por serie aumentadas a %1$d", "每组初始次数已提高到 %1$d", "प्रति सेट शुरुआती रेप्स बढ़ाकर %1$d किए गए"),
 "moved_start_reps_lowered": ("Repeticiones iniciales por serie reducidas a %1$d", "每组初始次数已降低到 %1$d", "प्रति सेट शुरुआती रेप्स घटाकर %1$d किए गए"),
 "moved_start_weight": ("Peso inicial cambiado a %1$s", "初始重量已改为 %1$s", "शुरुआती वज़न बदलकर %1$s किया गया"),
 "moved_holds_removed": P("%1$d mantenimiento eliminado (su peso ya no está)", "%1$d mantenimientos eliminados (sus pesos ya no están)", "已删除 %1$d 个保持（对应的重量已不存在）", "%1$d होल्ड हटाया गया (उसका वज़न अब नहीं है)", "%1$d होल्ड हटाए गए (उनके वज़न अब नहीं हैं)"),
 "moved_current_weight": ("Peso actual cambiado a %1$s", "当前重量已改为 %1$s", "वर्तमान वज़न बदलकर %1$s किया गया"),
 "moved_current_load": ("Peso actual cambiado a %1$s × %2$d", "当前重量已改为 %1$s × %2$d", "वर्तमान वज़न बदलकर %1$s × %2$d किया गया"),
 "current_weight": ("Peso actual", "当前重量", "वर्तमान वज़न"),
 "info_current_weight": ("El peso de tu próximo entrenamiento. Los cambios se aplican de inmediato.", "下次训练的重量。更改立即生效。", "आपके अगले वर्कआउट का वज़न। बदलाव तुरंत लागू होते हैं।"),
 "current_reps_per_set": ("Repeticiones actuales por serie", "当前每组次数", "वर्तमान प्रति सेट रेप्स"),
 "info_current_reps_per_set": ("Las repeticiones por serie de tu próximo entrenamiento, dentro de tu rango.", "下次训练的每组次数，需在你的次数范围内。", "आपके अगले वर्कआउट के प्रति सेट रेप्स, आपकी रेप रेंज के अंदर।"),
 "reset_progress_body_weight": ("Tu peso y tus repeticiones volverán al punto de partida, tus rachas se pondrán en 0 y se borrará tu último registro.", "你的重量和次数将回到起点，连续记录将归零，上次打卡也会被清除。", "आपका वज़न और रेप्स शुरुआती बिंदु पर लौट आएँगे, स्ट्रीक 0 हो जाएगी और पिछला चेक-इन हट जाएगा।"),
 "units": ("Unidades", "单位", "इकाइयाँ"),
 "info_units": ("La unidad con la que empiezan los entrenamientos de peso nuevos. Los que ya usan pesos conservan su unidad.", "新的重量训练默认使用的单位。已在使用重量的训练保留各自的单位。", "नए वज़न वाले वर्कआउट इसी इकाई से शुरू होते हैं। जो वर्कआउट पहले से वज़न इस्तेमाल करते हैं, उनकी इकाई वही रहती है।"),
 "error_too_few_weights": P("Añade al menos %1$d peso", "Añade al menos %1$d pesos", "至少添加 %1$d 个重量", "कम से कम %1$d वज़न जोड़ें", "कम से कम %1$d वज़न जोड़ें"),
 "error_too_many_weights": P("Como máximo %1$d peso", "Como máximo %1$d pesos", "最多 %1$d 个重量", "अधिकतम %1$d वज़न", "अधिकतम %1$d वज़न"),
 "error_weight_range": ("Debe ser mayor que 0 y como máximo 999.75", "必须大于 0 且不超过 999.75", "0 से ज़्यादा और अधिकतम 999.75 होना चाहिए"),
 "error_duplicate_weight": ("Ya está en la lista", "已在列表中", "पहले से सूची में है"),
 "error_steps_step": ("Elige uno de los incrementos", "请选择一个步长", "कोई एक स्टेप चुनें"),
 "error_steps_top": ("Debe estar por encima del inicio, en incrementos completos", "必须高于起点，且为整数个步长", "शुरुआत से ऊपर और पूरे स्टेप में होना चाहिए"),
 "error_reps_range": ("Debe estar entre 1 y 100", "必须在 1–100 之间", "1–100 के बीच होना चाहिए"),
 "error_rep_max": ("Debe ser mayor que el mínimo, hasta 100", "必须大于最少次数，最多 100", "न्यूनतम से ज़्यादा, अधिकतम 100 होना चाहिए"),
 "error_not_a_weight": ("Elige uno de tus pesos", "请从你的重量中选择", "अपने वज़नों में से एक चुनें"),
 "error_outside_rep_range": ("Debe estar dentro del rango de repeticiones", "必须在次数范围内", "रेप रेंज के अंदर होना चाहिए"),
 "error_duplicate_weight_hold": ("Ya hay otro mantenimiento aquí", "这里已有另一个保持", "यहाँ पहले से एक होल्ड है"),
 "error_enter_weight": ("Escribe un número con hasta 2 decimales", "请输入最多两位小数的数字", "अधिकतम 2 दशमलव वाली संख्या दर्ज करें"),
})
```

Then, from `<worktree>` in Git Bash (the generator reads `app/src/main/res/values/strings.xml` relative to the current directory):

```bash
python '//DEVMONSTER/ethor/claude_share/repkit-l10n/claude_l10n_gen.py' '//DEVMONSTER/ethor/claude_share/repkit-l10n' "<scratchpad>/repkit-translations-review.csv"
```

Expected: three `wrote app/src/main/res/values-…/strings.xml` lines and `wrote <scratchpad>/repkit-translations-review.csv`. An `untranslated keys: …` exit means a key above is missing or misspelt; fix the data file and rerun.

Check that only lines were added (a changed existing line means the share's data drifted from `main`; stop and ask the user):

```bash
git diff -U0 app/src/main/res/values-es app/src/main/res/values-zh-rCN app/src/main/res/values-hi | grep '^-' | grep -v '^---'   # prints nothing
```

Then compare the scratchpad CSV with `\\DEVMONSTER\ethor\claude_share\repkit-l10n\repkit-translations-review.csv`. If the only differences are the new keys' rows, copy the new CSV over the share's, so the reviewers see them; otherwise leave the share's CSV alone and tell the user it needs a merge.

- [ ] **Step 5: Implement the typed messages and the texts**

In `domain/SettingsValidator.kt`, add to `FieldMessage` (after `InTheFuture`):

```kotlin
    /** A weight-mode level outside the ladder (spec rev 26 §3 Current tab, plan Spec note 35). */
    data object NotOnLadder : FieldMessage
```

and change `currentState`'s signature and its first check (the streak and last-check-in lines stay as they are):

```kotlin
    /**
     * [levels] is the ladder's 0..top in a weight mode (plan Spec note 35), where the total is a level
     * and 0 is valid; null in Reps mode, where the total is ≥ 1.
     */
    fun currentState(
        total: Int,
        bestStreak: Int,
        currentStreak: Int,
        lastCheckIn: Instant?,
        now: Instant,
        levels: IntRange? = null,
    ): ValidationResult {
        // Spec revision 27: a total outside floor..cap is fine; saving it widens the range.
        val e = mutableMapOf<Field, FieldMessage>()
        if (levels == null) {
            if (total < 1) e[Field.TOTAL] = FieldMessage.AtLeastOne
        } else if (total !in levels) {
            e[Field.TOTAL] = FieldMessage.NotOnLadder
        }
        if (currentStreak < 0) e[Field.CURRENT_STREAK] = FieldMessage.ZeroOrMore
        if (bestStreak < 0) e[Field.BEST_STREAK] = FieldMessage.ZeroOrMore
        else if (bestStreak < currentStreak) e[Field.BEST_STREAK] = FieldMessage.AtLeastCurrentStreak
        if (lastCheckIn != null && lastCheckIn.isAfter(now)) e[Field.LAST_CHECK_IN] = FieldMessage.InTheFuture
        return ValidationResult(e)
    }
```

In `ui/common/FieldMessages.kt`, add to `FieldMessage.uiText()`:

```kotlin
    FieldMessage.NotOnLadder -> UiText.Res(R.string.error_not_a_weight)
```

In `ui/common/EditValueDialog.kt`, add to `ValueInput` after `DECIMAL`:

```kotlin
    /** A weight: a decimal with at most 2 places (plan Spec note 26). */
    WEIGHT(KeyboardType.Decimal, R.string.error_enter_weight),
```

Create `ui/common/WeightTexts.kt`:

```kotlin
package com.mitenko.repkit.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.WeightProblem
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit

/** "kg" / "lb". */
@get:StringRes
val WeightUnit.shortLabel: Int
    get() = when (this) {
        WeightUnit.KG -> R.string.unit_kg
        WeightUnit.LB -> R.string.unit_lb
    }

/** "Kilograms (kg)" / "Pounds (lb)", for ⚙ › Units. */
@get:StringRes
val WeightUnit.longLabel: Int
    get() = when (this) {
        WeightUnit.KG -> R.string.unit_kg_long
        WeightUnit.LB -> R.string.unit_lb_long
    }

/** A Progress by segment (spec rev 26 §3). */
@get:StringRes
val ProgressMode.label: Int
    get() = when (this) {
        ProgressMode.REPS -> R.string.progress_reps
        ProgressMode.WEIGHT -> R.string.progress_weight
        ProgressMode.REPS_THEN_WEIGHT -> R.string.progress_reps_then_weight
    }

/** "22.5 kg" (plan Spec note 27); just the number while the workout has no unit. */
fun weightText(hundredths: Int, unit: WeightUnit?): UiText =
    if (unit == null) {
        UiText.Raw(WeightFormat.format(hundredths))
    } else {
        UiText.Res(R.string.weight_value, listOf(WeightFormat.format(hundredths), UiText.Res(unit.shortLabel)))
    }

/** "Start (kg)": a weight row's label with the workout's unit (plan Spec note 27); the bare label while there's no unit. */
@Composable
fun unitLabel(@StringRes label: Int, unit: WeightUnit?): String =
    if (unit == null) stringResource(label) else stringResource(R.string.label_with_unit, stringResource(label), stringResource(unit.shortLabel))

/** A weight field's error (spec rev 26 §3.1). */
fun WeightProblem.uiText(): UiText = when (this) {
    WeightProblem.TooFewWeights -> UiText.Plural(R.plurals.error_too_few_weights, WeightConfig.MIN_WEIGHTS)
    WeightProblem.TooManyWeights -> UiText.Plural(R.plurals.error_too_many_weights, WeightConfig.MAX_WEIGHTS)
    is WeightProblem.WeightOutOfRange, WeightProblem.StepsStart -> UiText.Res(R.string.error_weight_range)
    is WeightProblem.DuplicateWeight -> UiText.Res(R.string.error_duplicate_weight)
    WeightProblem.StepsStep -> UiText.Res(R.string.error_steps_step)
    WeightProblem.StepsTop -> UiText.Res(R.string.error_steps_top)
    WeightProblem.RepsPerSet, WeightProblem.RepMin -> UiText.Res(R.string.error_reps_range)
    WeightProblem.RepMax -> UiText.Res(R.string.error_rep_max)
    WeightProblem.StartWeight, is WeightProblem.HoldWeight -> UiText.Res(R.string.error_not_a_weight)
    WeightProblem.StartReps, is WeightProblem.HoldReps -> UiText.Res(R.string.error_outside_rep_range)
    WeightProblem.TooManyHolds -> UiText.Plural(R.plurals.error_too_many_holds, ProgressionConfig.MAX_HOLDS)
    is WeightProblem.HoldFor -> UiText.Res(R.string.error_zero_or_more)
    is WeightProblem.DuplicateHold -> UiText.Res(R.string.error_duplicate_weight_hold)
}

/** One weight-mode note (plan Spec notes 30–31), weights in [unit]. */
fun WeightMove.uiText(unit: WeightUnit?): UiText = when (this) {
    is WeightMove.TopRaised -> UiText.Res(R.string.moved_top_raised, listOf(weightText(to, unit)))
    is WeightMove.TopLowered -> UiText.Res(R.string.moved_top_lowered, listOf(weightText(to, unit)))
    is WeightMove.StartLowered -> UiText.Res(R.string.moved_steps_start_lowered, listOf(weightText(to, unit)))
    is WeightMove.RepMaxRaised -> UiText.Res(R.string.moved_rep_max_raised, listOf(to))
    is WeightMove.RepMinLowered -> UiText.Res(R.string.moved_rep_min_lowered, listOf(to))
    is WeightMove.StartRepsRaised -> UiText.Res(R.string.moved_start_reps_raised, listOf(to))
    is WeightMove.StartRepsLowered -> UiText.Res(R.string.moved_start_reps_lowered, listOf(to))
    is WeightMove.StartWeightMoved -> UiText.Res(R.string.moved_start_weight, listOf(weightText(to, unit)))
    is WeightMove.HoldsRemoved -> UiText.Plural(R.plurals.moved_holds_removed, count)
    is WeightMove.CurrentMoved ->
        if (reps == null) {
            UiText.Res(R.string.moved_current_weight, listOf(weightText(weight, unit)))
        } else {
            UiText.Res(R.string.moved_current_load, listOf(weightText(weight, unit), reps))
        }
}

/** Every move of one edit on one line (revision 28's separator). */
@Composable
fun weightNoteText(moves: List<WeightMove>, unit: WeightUnit?): String = moves.map { it.uiText(unit).resolve() }.joinToString(MOVE_SEPARATOR)
```

- [ ] **Step 6: Run the tests and the guard**

Run: `testDebugUnitTest --tests "*SettingsValidatorTest*" --tests "*WeightTextsTest*" --tests "*HardCodedTextGuardTest*" --tests "*CurrentStateViewModelTest*"`
Expected: PASS.

- [ ] **Step 7: Run lint on the workers before moving on**

Run: `lintDebug` (Global Constraints form).
Expected: BUILD SUCCESSFUL (lint has `abortOnError = true`, so any MissingTranslation error fails the build). Also open the copied lint report (`lint-results-debug.txt` under the rgradle results folder, `%TEMP%
gradle` by default) and check it lists **0 MissingTranslation** issues and no `ExtraTranslation` / `StringFormatInvalid` / `StringFormatMatches` issues for the new keys. Don't start Task 7 until it does: a missing key goes into the data file, then regenerate (Step 4) and rerun.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-hi/strings.xml app/src/main/kotlin/com/mitenko/repkit/domain/SettingsValidator.kt app/src/main/kotlin/com/mitenko/repkit/ui/common/FieldMessages.kt app/src/main/kotlin/com/mitenko/repkit/ui/common/EditValueDialog.kt app/src/main/kotlin/com/mitenko/repkit/ui/common/WeightTexts.kt app/src/test/kotlin/com/mitenko/repkit/domain/SettingsValidatorTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/common/WeightTextsTest.kt
git commit -m "Add the weight settings strings in four languages and their typed texts"
```

(The data file lives on the share, outside the repo; it is saved in place.)

---

### Task 7: Weight fields, the choice row and the page status

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/ui/common/WeightFields.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/common/SettingsPageComponents.kt` (`SaveStatus`, `WeightMoveNote`)
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/common/WeightFieldsTest.kt`

**Interfaces:**
- Consumes: `StepperRow`, `EditValueDialog`, `SettingsCard`, `InfoTag` (existing); `WeightFormat` (Task 1); `weightText`, `weightNoteText`, `ProgressMode.label`, `ValueInput.WEIGHT` (Task 6).
- Produces:
  - `@Composable fun WeightStepperField(label: String, value: Int, onStep: (up: Boolean) -> Unit, onDialogValue: (Int) -> Unit, error: String? = null, info: String? = null, a11yLabel: String = label)`
  - `@Composable fun WeightPickerField(label: String, options: List<Int>, value: Int?, unit: WeightUnit?, onStep: (up: Boolean) -> Unit, onPick: (Int) -> Unit, error: String? = null, hint: String? = null, info: String? = null, a11yLabel: String = label)`; options tagged `option_<a11yLabel>_<hundredths>`
  - `@Composable fun <T> ChoiceRow(label: String, options: List<T>, selected: T?, optionLabel: @Composable (T) -> String, key: (T) -> String, tag: String, onSelect: (T) -> Unit, info: String? = null)`; segments tagged `<tag>_<key>`
  - `SaveStatus.of(valid: Boolean, failed: Boolean)`, `SaveStatus.worst(a: SaveStatus, b: SaveStatus)`
  - `@Composable fun WeightMoveNote(moves: List<WeightMove>, unit: WeightUnit?, tag: String)`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mitenko.repkit.ui.common

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeightFieldsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a picker steps along its list and picks from a list of weights`() {
        val steps = mutableListOf<Boolean>()
        val picks = mutableListOf<Int>()
        compose.setContent {
            HiitTheme {
                WeightPickerField(
                    "Starting weight (kg)", listOf(800, 1200, 1600), 1200, WeightUnit.KG,
                    onStep = { steps += it }, onPick = { picks += it }, a11yLabel = "Starting weight",
                )
            }
        }
        compose.onNodeWithTag("value_Starting weight").assertTextEquals("12")
        compose.onNodeWithContentDescription("Increase Starting weight").performClick()
        compose.onNodeWithContentDescription("Decrease Starting weight").performClick()
        assertEquals(listOf(true, false), steps)
        compose.onNodeWithTag("value_Starting weight").performClick()
        compose.onNodeWithText("16 kg").assertExists()
        compose.onNodeWithTag("option_Starting weight_1200").assertIsSelected()
        compose.onNodeWithTag("option_Starting weight_1600").performClick()
        assertEquals(listOf(1600), picks)
        compose.onNodeWithText("16 kg").assertDoesNotExist()
    }

    @Test
    fun `a picker with nothing to pick shows a dash and opens nothing`() {
        compose.setContent { HiitTheme { WeightPickerField("Step", emptyList(), null, WeightUnit.KG, onStep = {}, onPick = {}) } }
        compose.onNodeWithTag("value_Step").assertTextEquals("—").performClick()
        compose.onNodeWithTag("option_Step_250").assertDoesNotExist()
    }

    @Test
    fun `a weight stepper takes a comma and refuses three decimals`() {
        val values = mutableListOf<Int>()
        compose.setContent {
            HiitTheme { WeightStepperField("Start (kg)", 2000, onStep = {}, onDialogValue = { values += it }, a11yLabel = "Start") }
        }
        compose.onNodeWithTag("value_Start").assertTextEquals("20").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("20.125")
        compose.onNodeWithText("Enter a number with up to 2 decimals").assertExists()
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
        compose.onNodeWithTag("edit_field").performTextReplacement("22,5")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(listOf(2250), values)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `three choices fit at 320 dp with 48 dp segments`() {
        val chosen = mutableListOf<ProgressMode>()
        compose.setContent {
            HiitTheme {
                ChoiceRow(
                    "Progress by", ProgressMode.entries, ProgressMode.REPS,
                    optionLabel = { stringResource(it.label) }, key = { it.name }, tag = "mode", onSelect = { chosen += it },
                )
            }
        }
        ProgressMode.entries.forEach { compose.onNodeWithTag("mode_${it.name}").assertHeightIsAtLeast(48.dp) }
        compose.onNodeWithTag("mode_REPS").assertIsSelected()
        compose.onNodeWithText("Reps then weight").assertExists()
        compose.onNodeWithTag("mode_REPS_THEN_WEIGHT").performClick()
        assertEquals(listOf(ProgressMode.REPS_THEN_WEIGHT), chosen)
    }

    @Test
    fun `the page status is the worse of two`() {
        assertEquals(SaveStatus.INVALID, SaveStatus.worst(SaveStatus.FAILED, SaveStatus.INVALID))
        assertEquals(SaveStatus.FAILED, SaveStatus.worst(SaveStatus.SAVED, SaveStatus.FAILED))
        assertEquals(SaveStatus.SAVED, SaveStatus.worst(SaveStatus.SAVED, SaveStatus.SAVED))
        assertEquals(SaveStatus.INVALID, SaveStatus.of(valid = false, failed = true))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*WeightFieldsTest*"`
Expected: compilation fails on the new composables and `SaveStatus.worst`.

- [ ] **Step 3: Implement**

In `SettingsPageComponents.kt`, replace the `SaveStatus` companion (imports `com.mitenko.repkit.domain.WeightMove`, `com.mitenko.repkit.domain.model.WeightUnit`):

```kotlin
    companion object {
        fun of(validation: ValidationResult, failed: Boolean): SaveStatus = of(validation.isValid, failed)

        fun of(valid: Boolean, failed: Boolean): SaveStatus = when {
            !valid -> INVALID
            failed -> FAILED
            else -> SAVED
        }

        /** One status line for two drafts (plan Spec note 25): invalid first, then a failed save, else Saved. */
        fun worst(a: SaveStatus, b: SaveStatus): SaveStatus = when {
            a == INVALID || b == INVALID -> INVALID
            a == FAILED || b == FAILED -> FAILED
            else -> SAVED
        }
    }
```

and replace `MoveNote` so both kinds of note share revision 28's look:

```kotlin
/**
 * What an edit moved (spec revisions 27 and 28), under the field that caused it: the moves joined
 * on one line, announced politely to TalkBack.
 */
@Composable
fun MoveNote(moves: List<Move>, tag: String) = NoteText(noteText(moves), tag)

/** A weight-mode note (plan Spec notes 30–31), with the same look. */
@Composable
fun WeightMoveNote(moves: List<WeightMove>, unit: WeightUnit?, tag: String) = NoteText(weightNoteText(moves, unit), tag)

@Composable
private fun NoteText(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
    )
}
```

Create `WeightFields.kt`:

```kotlin
package com.mitenko.repkit.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.model.WeightUnit

/**
 * A free weight (plan Spec note 28: Steps' Start and Top) on the shared stepper row: ± is one step
 * ([onStep], debounced by the page), and the value opens the edit dialog, which takes up to 2
 * decimals ([onDialogValue], saved at once). [value] is hundredths.
 */
@Composable
fun WeightStepperField(
    label: String,
    value: Int,
    onStep: (up: Boolean) -> Unit,
    onDialogValue: (Int) -> Unit,
    error: String? = null,
    info: String? = null,
    a11yLabel: String = label,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val text = WeightFormat.format(value)
    StepperRow(
        label = label,
        valueText = text,
        onMinus = { onStep(false) },
        onPlus = { onStep(true) },
        onValueTap = { editing = true },
        error = error,
        info = info,
        a11yLabel = a11yLabel,
    )
    if (editing) {
        EditValueDialog(
            title = label,
            initialText = text,
            input = ValueInput.WEIGHT,
            parse = WeightFormat::parse,
            onConfirm = { v ->
                editing = false
                onDialogValue(v)
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * A weight picked from [options] (plan Spec note 28: Step, Starting weight, a hold's weight, Current
 * weight): ± moves along the list ([onStep]), and the value opens a radio list ([onPick], saved at
 * once). A null [value] shows "—"; with no options the list doesn't open.
 */
@Composable
fun WeightPickerField(
    label: String,
    options: List<Int>,
    value: Int?,
    unit: WeightUnit?,
    onStep: (up: Boolean) -> Unit,
    onPick: (Int) -> Unit,
    error: String? = null,
    hint: String? = null,
    info: String? = null,
    a11yLabel: String = label,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    StepperRow(
        label = label,
        valueText = value?.let(WeightFormat::format) ?: stringResource(R.string.none),
        onMinus = { onStep(false) },
        onPlus = { onStep(true) },
        onValueTap = { if (options.isNotEmpty()) picking = true },
        error = error,
        hint = hint,
        info = info,
        a11yLabel = a11yLabel,
    )
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(label) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    options.forEach { w ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = w == value, role = Role.RadioButton, onClick = {
                                    picking = false
                                    onPick(w)
                                })
                                .testTag("option_${a11yLabel}_$w"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = w == value, onClick = null)
                            Text(weightText(w, unit).resolve(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * A labelled single choice (plan Spec note 37: Progress by, Unit, Weights): the label and its ⓘ on top,
 * then a segmented row. Segments are at least 48 dp tall, have no check icon and wrap to two lines,
 * so three fit at 320 dp. The card is tagged `card_<label>`, each segment `<tag>_<key(option)>`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    key: (T) -> String,
    tag: String,
    onSelect: (T) -> Unit,
    info: String? = null,
) {
    SettingsCard(Modifier.padding(vertical = 4.dp).testTag("card_$label")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                info?.let { InfoTag(title = label, text = it) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                options.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                        icon = {},
                        modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_${key(option)}"),
                    ) {
                        Text(optionLabel(option), maxLines = 2, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*WeightFieldsTest*" --tests "*ProgressionSettingsScreenTest*" --tests "*CurrentStatePageTest*" --tests "*HardCodedTextGuardTest*"`
Expected: PASS (`MoveNote` keeps its look and tags).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/common/WeightFields.kt app/src/main/kotlin/com/mitenko/repkit/ui/common/SettingsPageComponents.kt app/src/test/kotlin/com/mitenko/repkit/ui/common/WeightFieldsTest.kt
git commit -m "Add the weight stepper, weight picker and choice row"
```

---

### Task 8: WeightSettingsViewModel: draft, saves, notes, unit and reset

**Depends on ruling:** open question 1 (`resetToDefaults` implements the recommendation; if the ruling differs, change it and its test first).

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/ui/settings/WeightSettings.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/settings/WeightSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `EntryScopedViewModel`, `AutoSaver`, `SaveStatus.of(Boolean, Boolean)` (Task 7); `resolveWeightEdit`, `WeightField`, `WeightMove` (Task 3); `weightValidation`, `WeightValidation` (Task 4); `WeightUnitDefaults`, `setWeightConfig(): WeightMove.CurrentMoved?`, `FakeEntryRepository.weightGate` (Task 5); `WeightConversion.convert`, `WeightCodecs` (PR 1).
- Produces:
  - `data class WeightNote(val at: WeightField?, val moves: List<WeightMove>)`
  - `class WeightSettingsViewModel(savedStateHandle, repo, unitDefaults: WeightUnitDefaults, @ApplicationScope appScope)` with `mode: StateFlow<ProgressMode?>`, `draft: StateFlow<WeightConfig?>`, `validation: StateFlow<WeightValidation>`, `status: StateFlow<SaveStatus>`, `note: StateFlow<WeightNote?>`, `unitPrompt: StateFlow<WeightUnit?>`, `update(field: WeightField?, transform: (WeightConfig) -> WeightConfig)`, `updateNow(field, transform)`, `requestUnit(unit)`, `confirmUnit()`, `dismissUnitPrompt()`, `resetToDefaults()`, `flush()`, `clearNote()`, and `missing` (inherited).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.mitenko.repkit.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.data.MigrationGate
import com.mitenko.repkit.data.RoomEntryRepository
import com.mitenko.repkit.data.WeightUnitDefaults
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.WeightProblem
import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.withoutListWeight
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.common.SaveStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeightSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))
    private val kg = WeightUnit.KG

    /** The app default is lb, so a switch into a weight mode visibly takes it. */
    private val lbDefault = object : WeightUnitDefaults {
        override val weightUnitDefault: Flow<WeightUnit> = flowOf(WeightUnit.LB)
    }

    private fun weightEntry(weight: WeightConfig = WeightConfig(unit = kg), mode: ProgressMode = ProgressMode.WEIGHT, level: Int = 0) =
        testEntry(1, progression = ProgressionConfig(mode = mode, weight = weight), counter = CounterState(total = level))

    private fun TestScope.vm(repo: FakeEntryRepository, h: SavedStateHandle = handle) = WeightSettingsViewModel(h, repo, lbDefault, backgroundScope)

    @Test
    fun `loads the mode and the weight group`() = runTest {
        val vm = vm(FakeEntryRepository(listOf(weightEntry())))
        assertEquals(ProgressMode.WEIGHT, vm.mode.value)
        assertEquals(WeightConfig(unit = kg), vm.draft.value)
        assertEquals(SaveStatus.SAVED, vm.status.value)
    }

    @Test
    fun `a reps per set change auto-saves after 400 ms`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        vm.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
        advanceTimeBy(399)
        assertEquals(10, repo.find(1).progression.weight.repsPerSet)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(12, repo.find(1).progression.weight.repsPerSet)
    }

    @Test
    fun `an invalid draft is never saved and shows on its field`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(800, 1200)))))
        val vm = vm(repo)
        vm.updateNow(WeightField.LIST) { it.withoutListWeight(1) }
        assertEquals(WeightProblem.TooFewWeights, vm.validation.value.errors[WeightField.LIST])
        assertEquals(SaveStatus.INVALID, vm.status.value)
        vm.flush()
        runCurrent()
        assertEquals(0, repo.weightWrites)
    }

    @Test
    fun `a step that leaves the top unreachable lowers it, notes it under Step and saves`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = kg, steps = WeightSteps(2000, 250, 2750)))))
        val vm = vm(repo)
        vm.updateNow(WeightField.STEPS_STEP) { it.copy(steps = it.steps.copy(step = 500)) }
        runCurrent()
        assertEquals(WeightSteps(2000, 500, 2500), vm.draft.value!!.steps)
        assertEquals(WeightNote(WeightField.STEPS_STEP, listOf(WeightMove.TopLowered(2500))), vm.note.value)
        assertEquals(WeightSteps(2000, 500, 2500), repo.find(1).progression.weight.steps)
    }

    @Test
    fun `removing the starting weight moves it, and the save adds the moved current weight`() = runTest {
        val bells = WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(800, 1200, 1600), startWeight = 1200)
        val repo = FakeEntryRepository(listOf(weightEntry(bells, level = 1)))
        val vm = vm(repo)
        vm.updateNow(WeightField.LIST) { it.withoutListWeight(1) }
        runCurrent()
        assertEquals(800, vm.draft.value!!.startWeight)
        assertEquals(
            WeightNote(WeightField.LIST, listOf(WeightMove.StartWeightMoved(800), WeightMove.CurrentMoved(800, null))),
            vm.note.value,
        )
        assertEquals(0, repo.find(1).counter.total)
    }

    @Test
    fun `a page change clears the note and a late save doesn't bring it back`() = runTest {
        val bells = WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(800, 1200, 1600))
        val repo = FakeEntryRepository(listOf(weightEntry(bells, level = 1)))
        repo.weightGate = CompletableDeferred()
        val vm = vm(repo)
        vm.updateNow(WeightField.LIST) { it.withoutListWeight(1) }
        runCurrent()
        vm.clearNote()
        repo.weightGate!!.complete(Unit)
        runCurrent()
        assertNull(vm.note.value)
        assertEquals(0, repo.find(1).counter.total)
    }

    @Test
    fun `changing the unit asks first, then converts the draft and saves it`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(1000, 2000)))))
        val vm = vm(repo)
        vm.requestUnit(WeightUnit.LB)
        assertEquals(WeightUnit.LB, vm.unitPrompt.value)
        assertEquals(kg, repo.find(1).progression.weight.unit)
        vm.confirmUnit()
        runCurrent()
        assertNull(vm.unitPrompt.value)
        assertEquals(listOf(2200, 4400), vm.draft.value!!.list) // 22.05 and 44.09 lb, to the nearest 0.25
        assertEquals(WeightUnit.LB, repo.find(1).progression.weight.unit)
        assertEquals(listOf(2200, 4400), repo.find(1).progression.weight.list)
    }

    @Test
    fun `dismissing the unit prompt or asking for the same unit changes nothing`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        vm.requestUnit(kg)
        assertNull(vm.unitPrompt.value)
        vm.requestUnit(WeightUnit.LB)
        vm.dismissUnitPrompt()
        assertNull(vm.unitPrompt.value)
        runCurrent()
        assertEquals(0, repo.weightWrites)
    }

    @Test
    fun `a conversion that leaves an invalid draft never saves, and the store stays in the old unit`() = runTest {
        // 1 and 1.25 lb both round to 0.5 kg: one weight left (plan Spec note 43).
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = WeightUnit.LB, kind = WeightsKind.LIST, list = listOf(100, 125)))))
        val vm = vm(repo)
        vm.requestUnit(kg)
        vm.confirmUnit()
        runCurrent()
        assertEquals(listOf(50), vm.draft.value!!.list)
        assertEquals(WeightProblem.TooFewWeights, vm.validation.value.errors[WeightField.LIST])
        assertEquals(SaveStatus.INVALID, vm.status.value)
        assertEquals(0, repo.weightWrites)
        assertEquals(WeightConfig(unit = WeightUnit.LB, kind = WeightsKind.LIST, list = listOf(100, 125)), repo.find(1).progression.weight)
    }

    @Test
    fun `reset to defaults keeps the unit and saves at once`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = WeightUnit.LB, kind = WeightsKind.LIST, list = listOf(1000, 2000), repsPerSet = 5))))
        val vm = vm(repo)
        vm.resetToDefaults()
        runCurrent()
        assertEquals(WeightConfig(unit = WeightUnit.LB), vm.draft.value)
        assertEquals(WeightConfig(unit = WeightUnit.LB), repo.find(1).progression.weight)
    }

    @Test
    fun `a draft restored from the saved state handle is shown and saved`() = runTest {
        val first = vm(FakeEntryRepository(listOf(weightEntry())))
        first.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 15) }
        val copy = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val restored = vm(repo, copy)
        assertEquals(15, restored.draft.value!!.repsPerSet)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(15, repo.find(1).progression.weight.repsPerSet)
    }

    @Test
    fun `a clean draft follows the store`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        repo.setWeightConfig(1, WeightConfig(unit = kg, repsPerSet = 8))
        runCurrent()
        assertEquals(8, vm.draft.value!!.repsPerSet)
    }

    @Test
    fun `a weight save keeps an untouched counter untouched`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val open = object : MigrationGate {
                override suspend fun awaitReady() = Unit
            }
            val repo = RoomEntryRepository(db, open, FakeClock()) { " copy" }
            val id = repo.create("Curls")
            repo.switchMode(id, ProgressMode.WEIGHT, kg)
            val vm = WeightSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to id)), repo, lbDefault, backgroundScope)
            vm.draft.first { it != null }
            vm.updateNow(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
            repo.entry(id).first { it!!.progression.weight.repsPerSet == 12 }
            assertNull(db.entryDao().get(id)!!.total)
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*WeightSettingsViewModelTest*"`
Expected: compilation fails: `WeightSettingsViewModel` and `WeightNote` don't exist.

- [ ] **Step 3: Implement**

```kotlin
package com.mitenko.repkit.ui.settings

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.repkit.data.EntryRepository
import com.mitenko.repkit.data.WeightCodecs
import com.mitenko.repkit.data.WeightUnitDefaults
import com.mitenko.repkit.di.ApplicationScope
import com.mitenko.repkit.domain.WeightConversion
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.WeightValidation
import com.mitenko.repkit.domain.model.EntryNotFound
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.resolveWeightEdit
import com.mitenko.repkit.domain.weightValidation
import com.mitenko.repkit.ui.common.AutoSaver
import com.mitenko.repkit.ui.common.EntryScopedViewModel
import com.mitenko.repkit.ui.common.SaveStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a weight-mode edit moved (plan Spec notes 30–31), shown under [at]; null is Reset to defaults. */
data class WeightNote(val at: WeightField?, val moves: List<WeightMove>)

/**
 * The weight half of the Progression page (spec rev 26 §3, plan Spec note 25): the mode and the weight
 * group, saved through setWeightConfig with the R3 pipeline (steppers 400 ms after the last change,
 * everything else at once, invalid drafts never). An edit wins and the values it pushes move to fit
 * (revision 28 §6); [note] says what moved, and a current load the save moved is appended when it lands.
 * While the pager is open, a draft without unsaved edits follows the store.
 */
@HiltViewModel
class WeightSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val unitDefaults: WeightUnitDefaults,
    @ApplicationScope private val appScope: CoroutineScope,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _mode = MutableStateFlow<ProgressMode?>(null)

    /** The stored Progress by mode; null until loaded. */
    val mode: StateFlow<ProgressMode?> = _mode.asStateFlow()

    private val _draft = MutableStateFlow(savedStateHandle.restoredWeightDraft())
    val draft: StateFlow<WeightConfig?> = _draft.asStateFlow()

    val validation: StateFlow<WeightValidation> = combine(_draft, _mode) { d, m ->
        if (d == null || m == null) WeightValidation() else weightValidation(d, m)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeightValidation())

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v.isValid, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    private val _note = MutableStateFlow<WeightNote?>(null)

    /** What the last edit moved, until the next edit or a page change ([clearNote]). */
    val note: StateFlow<WeightNote?> = _note.asStateFlow()

    /** Advanced by [clearNote]; a save only adds to the note if this hasn't moved since it was queued (revision 28 §4). */
    private var noteGeneration = 0L

    /** Where a current load moved by a save is noted: the last edited field, or null after Reset to defaults. */
    private var noteAnchor: WeightField? = null

    private val _unitPrompt = MutableStateFlow<WeightUnit?>(null)

    /** The unit the user asked to switch to, waiting for the confirm (plan Spec note 34). */
    val unitPrompt: StateFlow<WeightUnit?> = _unitPrompt.asStateFlow()

    /** A queued weight write and the note generation it was queued in. */
    private data class Save(val config: WeightConfig, val generation: Long)

    private val saver = AutoSaver<Save>(viewModelScope) { (config, generation) ->
        try {
            val moved = repo.setWeightConfig(entryId, config)
            if (moved != null && noteGeneration == generation) {
                _note.value = WeightNote(noteAnchor, _note.value?.moves.orEmpty() + moved)
            }
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Weights $config rejected", e)
            failed.value = true
        }
    }

    /** The weight group as last stored. A draft equal to it, with no save pending, has no unsaved edits. */
    private var stored: WeightConfig? = null

    init {
        val restored = _draft.value
        viewModelScope.launch {
            repo.entry(entryId).filterNotNull().collect { e ->
                val mode = e.progression.mode
                val latest = e.progression.weight
                _mode.value = mode
                // A valid draft restored after process death may never have been written. Its validity
                // depends on the mode, so it's checked on the first emission; the write is idempotent.
                if (stored == null && restored != null && restored != latest && weightValidation(restored, mode).isValid) {
                    saver.schedule(Save(restored, noteGeneration))
                }
                val current = _draft.value
                if (current == null || (current == stored && !saver.hasPending)) setDraft(latest)
                stored = latest
            }
        }
    }

    /** A stepper change to [field]: saved 400 ms after the last one. */
    fun update(field: WeightField?, transform: (WeightConfig) -> WeightConfig) = edit(field, transform, now = false)

    /** A dialog OK, a pick, a choice, ✕ or "+ Add": saved at once. */
    fun updateNow(field: WeightField?, transform: (WeightConfig) -> WeightConfig) = edit(field, transform, now = true)

    /** kg | lb: a different unit asks first (plan Spec note 34). */
    fun requestUnit(unit: WeightUnit) {
        if (unit != _draft.value?.unit) _unitPrompt.value = unit
    }

    /** Convert: the draft converts (Steps become My weights, §10 note 8) and saves at once. */
    fun confirmUnit() {
        val to = _unitPrompt.value ?: return
        _unitPrompt.value = null
        updateNow(WeightField.UNIT) { WeightConversion.convert(it, to) }
    }

    fun dismissUnitPrompt() {
        _unitPrompt.value = null
    }

    /** Reset to defaults in a weight mode (open question 1): the weight group's defaults, keeping the unit; saved at once. */
    fun resetToDefaults() {
        noteAnchor = null
        updateNow(null) { WeightConfig(unit = it.unit) }
    }

    fun flush() = saver.flush()

    /** The next edit or a page change hides the note. */
    fun clearNote() {
        noteGeneration++
        _note.value = null
    }

    override fun onCleared() {
        saver.flushIn(appScope)
    }

    private fun edit(field: WeightField?, transform: (WeightConfig) -> WeightConfig, now: Boolean) {
        val before = _draft.value ?: return
        val mode = _mode.value ?: return
        val resolution = resolveWeightEdit(mode, before, transform(before), field)
        clearNote()
        if (field != null) noteAnchor = field
        if (resolution.moves.isNotEmpty()) _note.value = WeightNote(field, resolution.moves)
        val config = resolution.config
        setDraft(config)
        when {
            !weightValidation(config, mode).isValid -> saver.cancel()
            now -> saver.saveNow(Save(config, noteGeneration))
            else -> saver.schedule(Save(config, noteGeneration))
        }
    }

    private fun setDraft(d: WeightConfig) {
        _draft.value = d
        savedStateHandle[INTS_KEY] = intArrayOf(
            d.unit?.ordinal ?: -1, d.kind.ordinal, d.repsPerSet, d.repMin, d.repMax, d.startWeight ?: -1, d.startReps ?: -1,
        )
        savedStateHandle[STEPS_KEY] = WeightCodecs.encodeSteps(d.steps)
        savedStateHandle[LIST_KEY] = WeightCodecs.encodeList(d.list)
        savedStateHandle[HOLDS_KEY] = WeightCodecs.encodeHolds(d.holds)
    }

    private companion object {
        const val TAG = "WeightSettings"
        const val INTS_KEY = "weight_draft"
        const val STEPS_KEY = "weight_draft_steps"
        const val LIST_KEY = "weight_draft_list"
        const val HOLDS_KEY = "weight_draft_holds"

        fun SavedStateHandle.restoredWeightDraft(): WeightConfig? {
            val a = get<IntArray>(INTS_KEY) ?: return null
            val steps = get<String>(STEPS_KEY)?.let(WeightCodecs::decodeSteps) ?: return null
            val list = get<String>(LIST_KEY)?.let(WeightCodecs::decodeList) ?: return null
            val holds = get<String>(HOLDS_KEY)?.let(WeightCodecs::decodeHolds) ?: return null
            return WeightConfig(
                unit = WeightUnit.entries.getOrNull(a[0]),
                kind = WeightsKind.entries[a[1]],
                steps = steps,
                list = list,
                repsPerSet = a[2],
                repMin = a[3],
                repMax = a[4],
                startWeight = a[5].takeIf { it >= 0 },
                startReps = a[6].takeIf { it >= 0 },
                holds = holds,
            )
        }
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*WeightSettingsViewModelTest*" --tests "*HardCodedTextGuardTest*"`
Expected: PASS. If the Room test times out, check that the save went through `updateNow` (not the debounced `update`) and that `repo.entry(id).first { … }` waits on the Room flow.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/settings/WeightSettings.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/WeightSettingsViewModelTest.kt
git commit -m "Add the weight settings ViewModel: draft, saves, notes, unit change and reset"
```

---

### Task 9: Start fresh: the mode switch in the ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/settings/WeightSettings.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/settings/WeightSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `EntryRepository.switchMode(id, mode, defaultUnit)` (PR 1), `WeightUnitDefaults` (Task 5).
- Produces: on `WeightSettingsViewModel`: `modePrompt: StateFlow<ProgressMode?>`, `requestMode(to: ProgressMode)`, `confirmMode()`, `dismissModePrompt()`.

- [ ] **Step 1: Write the failing tests**

Append to `WeightSettingsViewModelTest` (add `import org.junit.Assert.assertTrue`):

```kotlin
    @Test
    fun `another mode asks first and nothing changes until confirmed`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = vm(repo)
        assertEquals(ProgressMode.REPS, vm.mode.value)
        vm.requestMode(ProgressMode.WEIGHT)
        assertEquals(ProgressMode.WEIGHT, vm.modePrompt.value)
        vm.dismissModePrompt()
        assertNull(vm.modePrompt.value)
        runCurrent()
        assertTrue(repo.modeSwitches.isEmpty())
    }

    @Test
    fun `the current mode doesn't ask`() = runTest {
        val vm = vm(FakeEntryRepository(listOf(weightEntry())))
        vm.requestMode(ProgressMode.WEIGHT)
        assertNull(vm.modePrompt.value)
    }

    @Test
    fun `confirming starts fresh in the new mode with the app default unit`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 60, bestStreak = 5, currentStreak = 3))))
        val vm = vm(repo)
        vm.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        vm.confirmMode()
        runCurrent()
        assertNull(vm.modePrompt.value)
        assertEquals(listOf(1L to ProgressMode.REPS_THEN_WEIGHT), repo.modeSwitches)
        val e = repo.find(1)
        assertEquals(WeightUnit.LB, e.progression.weight.unit)
        assertEquals(listOf<Any>(0, 5, 3, true), listOf(e.counter.total, e.counter.bestStreak, e.counter.currentStreak, e.counter.freshStart))
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, vm.mode.value)
        assertEquals(WeightUnit.LB, vm.draft.value!!.unit)
    }

    @Test
    fun `a workout that already has a unit keeps it when it switches back into a weight mode`() = runTest {
        // Plan Spec note 46: the app default (lb here) only fills a missing unit.
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(weight = WeightConfig(unit = kg)))))
        val vm = vm(repo)
        vm.requestMode(ProgressMode.WEIGHT)
        vm.confirmMode()
        runCurrent()
        assertEquals(kg, repo.find(1).progression.weight.unit)
        assertEquals(kg, vm.draft.value!!.unit)
    }

    @Test
    fun `a pending weight edit lands before the switch`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry()))
        val vm = vm(repo)
        vm.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
        vm.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        vm.confirmMode()
        runCurrent()
        assertEquals(12, repo.find(1).progression.weight.repsPerSet)
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, repo.find(1).progression.mode)
    }

    @Test
    fun `confirming drops unsaved invalid edits`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(WeightConfig(unit = kg, kind = WeightsKind.LIST, list = listOf(800, 1200)))))
        val vm = vm(repo)
        vm.updateNow(WeightField.LIST) { it.withoutListWeight(0) } // one weight: invalid, never saved
        vm.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        vm.confirmMode()
        runCurrent()
        assertEquals(listOf(800, 1200), vm.draft.value!!.list)
        assertEquals(SaveStatus.SAVED, vm.status.value)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*WeightSettingsViewModelTest*"`
Expected: compilation fails on `requestMode`, `modePrompt`, `confirmMode`, `dismissModePrompt`.

- [ ] **Step 3: Implement**

In `WeightSettings.kt`, add `import kotlinx.coroutines.flow.first` and, after `unitPrompt`:

```kotlin
    private val _modePrompt = MutableStateFlow<ProgressMode?>(null)

    /** The mode the user picked under Progress by, waiting for the Start fresh confirm (spec rev 26 §2). */
    val modePrompt: StateFlow<ProgressMode?> = _modePrompt.asStateFlow()
```

and after `dismissUnitPrompt()`:

```kotlin
    /** Progress by: a different mode asks "Start fresh?" first (spec rev 26 §2 Switching mode). */
    fun requestMode(to: ProgressMode) {
        if (to != _mode.value) _modePrompt.value = to
    }

    fun dismissModePrompt() {
        _modePrompt.value = null
    }

    /**
     * Start fresh (plan Spec note 33, §10 notes 5 and 13): the pending weight write lands first, then
     * switchMode runs with the app default unit, then the draft takes the stored weight group, dropping
     * unsaved edits. The caller flushes the Progression page's own draft before this. Run in [appScope],
     * like Reset progress, so leaving the page can't drop the switch.
     */
    fun confirmMode() {
        val to = _modePrompt.value ?: return
        _modePrompt.value = null
        clearNote()
        saver.flush()
        appScope.launch {
            try {
                val unit = unitDefaults.weightUnitDefault.first()
                saver.exclusive { repo.switchMode(entryId, to, unit) }
                viewModelScope.launch {
                    repo.entry(entryId).first()?.let { e ->
                        _mode.value = e.progression.mode
                        stored = e.progression.weight
                        setDraft(e.progression.weight)
                    }
                }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }
```

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*WeightSettingsViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/settings/WeightSettings.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/WeightSettingsViewModelTest.kt
git commit -m "Switch Progress by mode with a Start fresh confirm"
```

---

### Task 10: The weight-mode Progression page

**Depends on ruling:** open question 1 (`resetSharedToDefaults` and the weight-mode Reset wiring) and open question 4 (the penalty label and ⓘ). Change those parts first if a ruling differs.

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/ui/settings/WeightSections.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/settings/ProgressionSettings.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/settings/WeightProgressionPageTest.kt`, `app/src/test/kotlin/com/mitenko/repkit/ui/settings/ProgressionSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 2–4 (draft edits, `WeightField`, `WeightValidation`, `WeightHoldField`), Task 6 (texts), Task 7 (fields, `SaveStatus.worst`, `WeightMoveNote`), Task 8–9 (`WeightSettingsViewModel`, `WeightNote`).
- Produces:
  - `typealias WeightEdit = (field: WeightField?, transform: (WeightConfig) -> WeightConfig) -> Unit`
  - `class WeightPage(mode, draft, validation, status, note, onChange: WeightEdit, onChangeNow: WeightEdit, onRequestMode: (ProgressMode) -> Unit, onRequestUnit: (WeightUnit) -> Unit)`
  - `ProgressionPageContent(…, weight: WeightPage? = null)` (new last parameter; null keeps today's page exactly)
  - `@Composable fun ProgressionPage(vm: ProgressionSettingsViewModel, weightVm: WeightSettingsViewModel? = null, windowOnly: Boolean = false)`
  - `ProgressionSettingsViewModel.resetSharedToDefaults()`
  - `internal` `HoldHeader`, `AddHoldButton` (were private), `StartFreshDialog`, `ChangeUnitDialog`
  - Test tags: segments `mode_<MODE>`, `unit_<UNIT>`, `weights_<KIND>`; My weights `weight_row_<i>`, `weight_value_<i>`, `remove_weight_<i>`, `support_weight_<i>`, `support_weights`, `add_weight`; notes `weight_note`; dialogs `confirm_start_fresh`, `confirm_change_unit`.

- [ ] **Step 1: Write the failing tests**

Append to `ProgressionSettingsViewModelTest`:

```kotlin
    @Test
    fun `a shared reset restores the window, the penalty and the Hold switch and keeps the Reps fields`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(cap = 90, windowHours = 40, penaltyHoursPerRep = 10.0, hold = false))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.resetSharedToDefaults()
        runCurrent()
        assertEquals(ProgressionConfig(cap = 90), repo.find(1).progression)
    }
```

Create `WeightProgressionPageTest.kt`:

```kotlin
package com.mitenko.repkit.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.SettingsValidator
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.weightValidation
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeightProgressionPageTest {
    @get:Rule val compose = createComposeRule()

    private var draft by mutableStateOf(ProgressionDraft.from(ProgressionConfig()))
    private var weight by mutableStateOf(WeightConfig(unit = WeightUnit.KG))
    private var note by mutableStateOf<WeightNote?>(null)
    private val fields = mutableListOf<WeightField?>()
    private var nowEdits = 0
    private val modes = mutableListOf<ProgressMode>()
    private val units = mutableListOf<WeightUnit>()

    private fun show(mode: ProgressMode, initial: WeightConfig = WeightConfig(unit = WeightUnit.KG), windowOnly: Boolean = false) {
        weight = initial
        compose.setContent {
            HiitTheme {
                val validation = SettingsValidator.progression(draft.toConfig())
                val wv = weightValidation(weight, mode)
                ProgressionPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { _, f -> draft = f(draft) },
                    onChangeNow = { _, f -> draft = f(draft) },
                    onReset = {},
                    windowOnly = windowOnly,
                    weight = WeightPage(
                        mode, weight, wv, SaveStatus.of(wv.isValid, failed = false), note,
                        onChange = { field, f -> fields += field; weight = f(weight) },
                        onChangeNow = { field, f -> fields += field; nowEdits++; weight = f(weight) },
                        onRequestMode = { modes += it },
                        onRequestUnit = { units += it },
                    ),
                )
            }
        }
    }

    private fun card(label: String) = compose.onNodeWithTag("card_$label")

    @Test
    fun `Reps mode shows Progress by over today's rows`() {
        show(ProgressMode.REPS)
        compose.onNodeWithTag("mode_REPS").assertIsSelected()
        card("Starting reps").assertExists()
        card("Unit").assertDoesNotExist()
        card("Missed-day adjustment (hours per rep)").assertExists()
    }

    @Test
    fun `Weight mode shows the weight rows and hides the Reps rows`() {
        show(ProgressMode.WEIGHT)
        listOf("Unit", "Weights", "Start", "Step", "Top", "Reps per set", "Starting weight", "Hold", "Missed-day adjustment (hours per step)").forEach {
            card(it).assertExists()
        }
        listOf("Starting reps", "Minimum reps", "Minimum reps per set", "Starting reps per set").forEach { card(it).assertDoesNotExist() }
        compose.onNodeWithText("Start (kg)").assertExists()
        compose.onNodeWithTag("unit_KG").assertIsSelected()
    }

    @Test
    fun `Reps then weight shows the rep range and starting reps per set instead of reps per set`() {
        show(ProgressMode.REPS_THEN_WEIGHT)
        listOf("Minimum reps per set", "Maximum reps per set", "Starting reps per set").forEach { card(it).assertExists() }
        card("Reps per set").assertDoesNotExist()
    }

    @Test
    fun `Timer only shows neither Progress by nor the weight rows`() {
        show(ProgressMode.WEIGHT, windowOnly = true)
        card("Progress by").assertDoesNotExist()
        card("Unit").assertDoesNotExist()
        card("On-time window (hours)").assertExists()
    }

    @Test
    fun `another mode or unit is requested, the current one isn't`() {
        show(ProgressMode.WEIGHT)
        compose.onNodeWithTag("mode_WEIGHT").performClick()
        compose.onNodeWithTag("mode_REPS_THEN_WEIGHT").performClick()
        assertEquals(listOf(ProgressMode.REPS_THEN_WEIGHT), modes)
        compose.onNodeWithTag("unit_KG").performScrollTo().performClick()
        compose.onNodeWithTag("unit_LB").performClick()
        assertEquals(listOf(WeightUnit.LB), units)
    }

    @Test
    fun `the Step stepper cycles the step choices`() {
        show(ProgressMode.WEIGHT)
        compose.onNodeWithContentDescription("Increase Step").performScrollTo().performClick()
        assertEquals(500, weight.steps.step)
        assertEquals(WeightField.STEPS_STEP, fields.last())
    }

    @Test
    fun `the starting weight is picked from the list and saved at once`() {
        show(ProgressMode.WEIGHT)
        compose.onNodeWithTag("value_Starting weight").performScrollTo().assertTextEquals("20").performClick()
        compose.onNodeWithTag("option_Starting weight_2250").performClick()
        assertEquals(2250, weight.startWeight)
        assertEquals(WeightField.START_WEIGHT, fields.last())
        assertEquals(1, nowEdits)
    }

    @Test
    fun `My weights lists each weight with remove and add`() {
        show(ProgressMode.WEIGHT, WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1200, 1600)))
        compose.onNodeWithTag("weight_value_0").performScrollTo().assertTextEquals("8 kg")
        compose.onNodeWithTag("remove_weight_1").performScrollTo().performClick()
        assertEquals(listOf(800, 1600), weight.list)
        compose.onNodeWithTag("add_weight").performScrollTo().performClick()
        assertEquals(listOf(800, 1600, 2400), weight.list)
        assertEquals(listOf<WeightField?>(WeightField.LIST, WeightField.LIST), fields)
    }

    @Test
    fun `a My weights row edited with a comma keeps the list sorted`() {
        show(ProgressMode.WEIGHT, WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1200, 1600)))
        compose.onNodeWithTag("weight_value_0").performScrollTo().performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("20,5")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(listOf(1200, 1600, 2050), weight.list)
    }

    @Test
    fun `a duplicate weight shows on its row and the page isn't saved`() {
        show(ProgressMode.WEIGHT, WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 800)))
        compose.onNodeWithTag("support_weight_1").performScrollTo().assertTextEquals("Already in the list")
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
    }

    @Test
    fun `weight holds pick a weight, reps in Reps then weight, and say Hold disabled on the top`() {
        show(ProgressMode.REPS_THEN_WEIGHT, WeightConfig(unit = WeightUnit.KG, steps = WeightSteps(2000, 250, 2500), holds = listOf(WeightHold(2500, 12, 3))))
        compose.onNodeWithTag("support_Hold 1 weight").performScrollTo().assertTextEquals("Hold disabled")
        card("Hold 1 reps").assertExists()
        card("Hold 1 for").assertExists()
        compose.onNodeWithTag("add_hold").performScrollTo().performClick()
        assertEquals(2, weight.holds.size)
        assertEquals(WeightField.HOLDS, fields.last())
    }

    @Test
    fun `Weight mode holds have no reps row`() {
        show(ProgressMode.WEIGHT, WeightConfig(unit = WeightUnit.KG, holds = listOf(WeightHold(2500, 8, 3))))
        card("Hold 1 weight").assertExists()
        card("Hold 1 reps").assertDoesNotExist()
    }

    @Test
    fun `a note shows under the field that caused it`() {
        note = WeightNote(WeightField.STEPS_TOP, listOf(WeightMove.TopLowered(2500)))
        show(ProgressMode.WEIGHT)
        compose.onNodeWithTag("weight_note").performScrollTo().assertTextEquals("Top lowered to 25 kg")
    }

    @Test
    fun `the Start fresh dialog names the starting weight, or the starting reps when going back to Reps`() {
        var confirmed = 0
        var to by mutableStateOf(ProgressMode.WEIGHT)
        compose.setContent { HiitTheme { StartFreshDialog(to, onConfirm = { confirmed++ }, onDismiss = {}) } }
        compose.onNodeWithText("This workout restarts at its starting weight. Your streaks and history are kept.").assertExists()
        to = ProgressMode.REPS
        compose.onNodeWithText("This workout restarts at its starting reps. Your streaks and history are kept.").assertExists()
        compose.onNodeWithTag("confirm_start_fresh").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `the unit dialog names the new unit`() {
        var converted = false
        compose.setContent { HiitTheme { ChangeUnitDialog(WeightUnit.LB, onConfirm = { converted = true }, onDismiss = {}) } }
        compose.onNodeWithText("Switch to lb?").assertExists()
        compose.onNodeWithTag("confirm_change_unit").performClick()
        assertTrue(converted)
    }
}
```

(The card labels are the English strings: `window_hours` is "On-time window (hours)", `penalty_rate` "Missed-day adjustment (hours per rep)".)

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*WeightProgressionPageTest*" --tests "*ProgressionSettingsViewModelTest*"`
Expected: compilation fails (`WeightPage`, `weight =`, `resetSharedToDefaults`, the dialogs).

- [ ] **Step 3: Write `WeightSections.kt`**

```kotlin
package com.mitenko.repkit.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.FieldRanges
import com.mitenko.repkit.domain.StepRange
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.WeightHoldField
import com.mitenko.repkit.domain.WeightValidation
import com.mitenko.repkit.domain.WeightValidator
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.domain.pickable
import com.mitenko.repkit.domain.snapTop
import com.mitenko.repkit.domain.stepAlong
import com.mitenko.repkit.domain.stepWeight
import com.mitenko.repkit.domain.withKind
import com.mitenko.repkit.domain.withListWeight
import com.mitenko.repkit.domain.withNewListWeight
import com.mitenko.repkit.domain.withNewWeightHold
import com.mitenko.repkit.domain.withWeightHold
import com.mitenko.repkit.domain.withoutListWeight
import com.mitenko.repkit.domain.withoutWeightHold
import com.mitenko.repkit.ui.common.ChoiceRow
import com.mitenko.repkit.ui.common.EditValueDialog
import com.mitenko.repkit.ui.common.InfoTag
import com.mitenko.repkit.ui.common.IntStepperField
import com.mitenko.repkit.ui.common.SaveStatus
import com.mitenko.repkit.ui.common.SettingsCard
import com.mitenko.repkit.ui.common.ValueInput
import com.mitenko.repkit.ui.common.WeightMoveNote
import com.mitenko.repkit.ui.common.WeightPickerField
import com.mitenko.repkit.ui.common.WeightStepperField
import com.mitenko.repkit.ui.common.label
import com.mitenko.repkit.ui.common.resolve
import com.mitenko.repkit.ui.common.shortLabel
import com.mitenko.repkit.ui.common.uiText
import com.mitenko.repkit.ui.common.unitLabel
import com.mitenko.repkit.ui.common.weightText

/** A weight-mode draft edit; [field] is the field the user changed (revision 28 §6), or null. */
typealias WeightEdit = (field: WeightField?, transform: (WeightConfig) -> WeightConfig) -> Unit

/** The weight half of the Progression page (spec rev 26 §3, plan Spec note 25): the mode, the weight draft and its callbacks. */
class WeightPage(
    val mode: ProgressMode,
    val draft: WeightConfig,
    val validation: WeightValidation,
    val status: SaveStatus,
    val note: WeightNote?,
    val onChange: WeightEdit,
    val onChangeNow: WeightEdit,
    val onRequestMode: (ProgressMode) -> Unit,
    val onRequestUnit: (WeightUnit) -> Unit,
)

/** Progress by: Reps | Weight | Reps then weight (spec rev 26 §3). Another mode is requested; the confirm comes from the ViewModel. */
@Composable
internal fun ProgressByRow(mode: ProgressMode, onRequest: (ProgressMode) -> Unit) {
    ChoiceRow(
        stringResource(R.string.progress_by), ProgressMode.entries, mode,
        optionLabel = { stringResource(it.label) }, key = { it.name }, tag = "mode",
        onSelect = { if (it != mode) onRequest(it) }, info = stringResource(R.string.info_progress_by),
    )
}

/**
 * Rows 1–4 of a weight-mode Progression page (spec rev 26 §3): Unit, Weights (Steps or My weights),
 * Reps per set or the rep range, and the starting point. Each note shows under the field that caused it.
 */
@Composable
internal fun WeightSetupRows(w: WeightPage) {
    val d = w.draft
    val unit = d.unit
    val errors = w.validation.errors
    ChoiceRow(
        stringResource(R.string.weight_unit), WeightUnit.entries, unit,
        optionLabel = { stringResource(it.shortLabel) }, key = { it.name }, tag = "unit",
        onSelect = { if (it != unit) w.onRequestUnit(it) }, info = stringResource(R.string.info_weight_unit),
    )
    WeightNoteUnder(w, WeightField.UNIT)
    ChoiceRow(
        stringResource(R.string.weights_source), WeightsKind.entries, d.kind,
        optionLabel = { stringResource(if (it == WeightsKind.STEPS) R.string.weights_steps else R.string.weights_list) },
        key = { it.name }, tag = "weights",
        onSelect = { kind -> w.onChangeNow(WeightField.KIND) { it.withKind(kind) } }, info = stringResource(R.string.info_weights_source),
    )
    WeightNoteUnder(w, WeightField.KIND)
    when (d.kind) {
        WeightsKind.STEPS -> StepsRows(w)
        WeightsKind.LIST -> MyWeightsRows(w)
    }
    if (w.mode == ProgressMode.WEIGHT) {
        IntStepperField(
            stringResource(R.string.reps_per_set), d.repsPerSet, FieldRanges.REPS_PER_SET, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.REPS_PER_SET) { it.copy(repsPerSet = f(it.repsPerSet)) } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.REPS_PER_SET) { it.copy(repsPerSet = f(it.repsPerSet)) } },
            error = errors[WeightField.REPS_PER_SET]?.uiText()?.resolve(), info = stringResource(R.string.info_reps_per_set),
        )
    } else {
        IntStepperField(
            stringResource(R.string.rep_range_min), d.repMin, FieldRanges.REPS_PER_SET, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.REP_MIN) { it.copy(repMin = f(it.repMin)) } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.REP_MIN) { it.copy(repMin = f(it.repMin)) } },
            error = errors[WeightField.REP_MIN]?.uiText()?.resolve(), info = stringResource(R.string.info_rep_range_min),
        )
        WeightNoteUnder(w, WeightField.REP_MIN)
        IntStepperField(
            stringResource(R.string.rep_range_max), d.repMax, FieldRanges.REPS_PER_SET, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.REP_MAX) { it.copy(repMax = f(it.repMax)) } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.REP_MAX) { it.copy(repMax = f(it.repMax)) } },
            error = errors[WeightField.REP_MAX]?.uiText()?.resolve(), info = stringResource(R.string.info_rep_range_max),
        )
        WeightNoteUnder(w, WeightField.REP_MAX)
    }
    val options = d.pickable
    WeightPickerField(
        unitLabel(R.string.start_weight, unit), options, d.startWeight ?: options.firstOrNull(), unit,
        onStep = { up -> w.onChange(WeightField.START_WEIGHT) { c -> c.copy(startWeight = stepAlong(c.pickable, c.startWeight ?: c.pickable.firstOrNull(), up)) } },
        onPick = { v -> w.onChangeNow(WeightField.START_WEIGHT) { it.copy(startWeight = v) } },
        error = errors[WeightField.START_WEIGHT]?.uiText()?.resolve(), info = stringResource(R.string.info_start_weight),
        a11yLabel = stringResource(R.string.start_weight),
    )
    WeightNoteUnder(w, WeightField.START_WEIGHT)
    if (w.mode == ProgressMode.REPS_THEN_WEIGHT) {
        IntStepperField(
            stringResource(R.string.start_reps), d.startReps ?: d.repMin, FieldRanges.REPS_PER_SET, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.START_REPS) { it.copy(startReps = f(it.startReps ?: it.repMin)) } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.START_REPS) { it.copy(startReps = f(it.startReps ?: it.repMin)) } },
            error = errors[WeightField.START_REPS]?.uiText()?.resolve(), info = stringResource(R.string.info_start_reps),
        )
        WeightNoteUnder(w, WeightField.START_REPS)
    }
}

/** Steps: Start and Top are free values stepped by Step; Step is picked from the choices (plan Spec note 28). */
@Composable
private fun StepsRows(w: WeightPage) {
    val s = w.draft.steps
    val unit = w.draft.unit
    val errors = w.validation.errors
    WeightStepperField(
        unitLabel(R.string.steps_start, unit), s.start,
        onStep = { up -> w.onChange(WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = stepWeight(it.steps.start, it.steps.step, up))) } },
        onDialogValue = { v -> w.onChangeNow(WeightField.STEPS_START) { it.copy(steps = it.steps.copy(start = v)) } },
        error = errors[WeightField.STEPS_START]?.uiText()?.resolve(), info = stringResource(R.string.info_steps_start),
        a11yLabel = stringResource(R.string.steps_start),
    )
    WeightNoteUnder(w, WeightField.STEPS_START)
    WeightPickerField(
        unitLabel(R.string.steps_step, unit), WeightValidator.STEP_CHOICES, s.step, unit,
        onStep = { up -> w.onChange(WeightField.STEPS_STEP) { it.copy(steps = it.steps.copy(step = stepAlong(WeightValidator.STEP_CHOICES, it.steps.step, up) ?: it.steps.step)) } },
        onPick = { v -> w.onChangeNow(WeightField.STEPS_STEP) { it.copy(steps = it.steps.copy(step = v)) } },
        error = errors[WeightField.STEPS_STEP]?.uiText()?.resolve(), info = stringResource(R.string.info_steps_step),
        a11yLabel = stringResource(R.string.steps_step),
    )
    WeightNoteUnder(w, WeightField.STEPS_STEP)
    WeightStepperField(
        unitLabel(R.string.steps_top, unit), s.top,
        onStep = { up -> w.onChange(WeightField.STEPS_TOP) { it.copy(steps = it.steps.copy(top = stepWeight(it.steps.top, it.steps.step, up))) } },
        onDialogValue = { v -> w.onChangeNow(WeightField.STEPS_TOP) { it.copy(steps = it.steps.copy(top = snapTop(v, it.steps.start, it.steps.step))) } },
        error = errors[WeightField.STEPS_TOP]?.uiText()?.resolve(), info = stringResource(R.string.info_steps_top),
        a11yLabel = stringResource(R.string.steps_top),
    )
    WeightNoteUnder(w, WeightField.STEPS_TOP)
}

/** My weights: a heading with its ⓘ, a row per weight (edit, ✕), the list's own error and "+ Add weight" (plan Spec note 29). */
@Composable
private fun MyWeightsRows(w: WeightPage) {
    val d = w.draft
    val heading = unitLabel(R.string.weights_list, d.unit)
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        InfoTag(heading, stringResource(R.string.info_my_weights))
    }
    d.list.forEachIndexed { i, value ->
        WeightListRow(
            index = i, value = value, unit = d.unit, errorText = w.validation.rowErrors[i]?.uiText()?.resolve(),
            onEdit = { v -> w.onChangeNow(WeightField.LIST) { it.withListWeight(i, v) } },
            onRemove = { w.onChangeNow(WeightField.LIST) { it.withoutListWeight(i) } },
        )
    }
    w.validation.errors[WeightField.LIST]?.let {
        Text(
            it.uiText().resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp).testTag("support_weights"),
        )
    }
    TextButton(
        onClick = { w.onChangeNow(WeightField.LIST) { it.withNewListWeight() } },
        enabled = d.list.size < WeightConfig.MAX_WEIGHTS,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 48.dp).testTag("add_weight"),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.add_weight), modifier = Modifier.padding(start = 8.dp))
    }
    WeightNoteUnder(w, WeightField.LIST)
}

/** One My weights row: "20 kg" opens the edit dialog, ✕ removes it; both are 48 dp targets. */
@Composable
private fun WeightListRow(index: Int, value: Int, unit: WeightUnit?, errorText: String?, onEdit: (Int) -> Unit, onRemove: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val rowLabel = stringResource(R.string.weight_n, index + 1)
    val shown = weightText(value, unit).resolve()
    SettingsCard(Modifier.padding(vertical = 4.dp).testTag("weight_row_$index")) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shown,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (errorText != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = stringResource(R.string.edit_value, rowLabel)) { editing = true }
                        .wrapContentHeight(Alignment.CenterVertically)
                        .testTag("weight_value_$index"),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp).testTag("remove_weight_$index")) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.remove_weight, shown))
                }
            }
            errorText?.let {
                Text(
                    it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("support_weight_$index"),
                )
            }
        }
    }
    if (editing) {
        EditValueDialog(
            title = rowLabel,
            initialText = WeightFormat.format(value),
            input = ValueInput.WEIGHT,
            parse = WeightFormat::parse,
            onConfirm = { v ->
                editing = false
                onEdit(v)
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * The weight-mode hold list under the shared Hold switch (spec rev 26 §3 row 5, plan Spec note 32):
 * per hold a header with ✕, its weight, its reps (Reps then weight) and "for", then "+ Add hold".
 */
@Composable
internal fun WeightHoldRows(w: WeightPage) {
    val d = w.draft
    val v = w.validation
    val options = d.pickable
    val reps = d.repStepRange()
    d.holds.forEachIndexed { i, hold ->
        val errs = v.holdErrors[i].orEmpty()
        HoldHeader(number = i + 1, onRemove = { w.onChangeNow(WeightField.HOLDS) { it.withoutWeightHold(i) } })
        WeightPickerField(
            unitLabel(R.string.hold_at_weight, d.unit), options, hold.weight, d.unit,
            onStep = { up -> w.onChange(WeightField.HOLDS) { c -> c.withWeightHold(i) { h -> h.copy(weight = stepAlong(c.pickable, h.weight, up) ?: h.weight) } } },
            onPick = { x -> w.onChangeNow(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(weight = x) } } },
            error = errs[WeightHoldField.WEIGHT]?.uiText()?.resolve(),
            hint = if (i in v.holdHints) stringResource(R.string.hint_hold_disabled) else null,
            info = stringResource(R.string.info_hold_at_weight),
            a11yLabel = stringResource(R.string.hold_n_weight, i + 1),
        )
        if (w.mode == ProgressMode.REPS_THEN_WEIGHT) {
            IntStepperField(
                stringResource(R.string.hold_at_reps), hold.reps, reps, ValueInput.WHOLE,
                onUpdate = { f -> w.onChange(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(reps = f(h.reps)) } } },
                onDialogUpdate = { f -> w.onChangeNow(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(reps = f(h.reps)) } } },
                error = errs[WeightHoldField.REPS]?.uiText()?.resolve(), info = stringResource(R.string.info_hold_at_reps),
                a11yLabel = stringResource(R.string.hold_n_reps, i + 1),
            )
        }
        IntStepperField(
            stringResource(R.string.hold_for), hold.forCount, FieldRanges.HOLD_FOR, ValueInput.WHOLE,
            onUpdate = { f -> w.onChange(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(forCount = f(h.forCount)) } } },
            onDialogUpdate = { f -> w.onChangeNow(WeightField.HOLDS) { it.withWeightHold(i) { h -> h.copy(forCount = f(h.forCount)) } } },
            error = errs[WeightHoldField.FOR]?.uiText()?.resolve(), info = stringResource(R.string.info_hold_for),
            a11yLabel = stringResource(R.string.hold_n_for, i + 1),
        )
    }
    v.errors[WeightField.HOLDS]?.let {
        Text(
            it.uiText().resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp).testTag("support_holds"),
        )
    }
    AddHoldButton(
        enabled = d.holds.size < ProgressionConfig.MAX_HOLDS,
        onClick = { w.onChangeNow(WeightField.HOLDS) { it.withNewWeightHold(w.mode) } },
    )
    WeightNoteUnder(w, WeightField.HOLDS)
}

/** A hold's reps stepper range: the rep range, kept a valid StepRange while the draft's range is invalid. */
private fun WeightConfig.repStepRange(): StepRange {
    val low = minOf(repMin, repMax).coerceIn(1, WeightConfig.MAX_REPS)
    val high = maxOf(repMin, repMax).coerceIn(low, WeightConfig.MAX_REPS)
    return StepRange(low, high, 1)
}

/** The note under [at] (null: under Reset to defaults), if the last edit was there. */
@Composable
internal fun WeightNoteUnder(w: WeightPage, at: WeightField?) {
    val note = w.note
    if (note != null && note.at == at) WeightMoveNote(note.moves, w.draft.unit, tag = "weight_note")
}

/** "Start fresh?" (spec rev 26 §2 Switching mode, plan Spec note 33); the body names the starting weight, or the starting reps for Reps. */
@Composable
internal fun StartFreshDialog(to: ProgressMode, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.start_fresh_title)) },
        text = { Text(stringResource(if (to.usesWeights) R.string.start_fresh_body_weight else R.string.start_fresh_body_reps)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_start_fresh")) { Text(stringResource(R.string.start_fresh)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Switch to lb?" (spec rev 26 §2 Units, plan Spec note 34). */
@Composable
internal fun ChangeUnitDialog(to: WeightUnit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = stringResource(to.shortLabel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_unit_title, name)) },
        text = { Text(stringResource(R.string.change_unit_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_change_unit")) { Text(stringResource(R.string.convert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
```

- [ ] **Step 4: Change `ProgressionSettings.kt`**

1. Make `HoldHeader` and `AddHoldButton` `internal` instead of `private` (their bodies don't change).

2. In `ProgressionSettingsViewModel`, after `resetToDefaults()`:

```kotlin
    /**
     * Reset to defaults in a weight mode (open question 1): only the rows every mode shares go back to
     * their defaults (the window, the penalty, the Hold switch); the hidden Reps fields keep their
     * values. Saved at once; the weight group is reset by WeightSettingsViewModel.
     */
    fun resetSharedToDefaults() {
        noteAnchor = null
        val d = ProgressionConfig()
        updateNow { it.copy(windowHours = d.windowHours, penalty = PenaltyDraft.of(d.penaltyHoursPerRep), hold = d.hold) }
    }
```

3. Replace `ProgressionPage` (add imports `androidx.compose.runtime.getValue` is already there; add `com.mitenko.repkit.domain.model.ProgressMode` if missing, `com.mitenko.repkit.ui.common.WeightMoveNote` isn't needed here):

```kotlin
/**
 * The Progression page inside the pager (spec R3 §4). [windowOnly] is a Timer only entry
 * (R4 §4.6): only the check-in window shows. The hidden fields keep their stored values and stay in
 * the draft that is validated and saved. Reset to defaults is hidden too, since it would reset them
 * (plan Spec note 7). With [weightVm], the page shows Progress by and, in a weight mode, the weight
 * rows (spec rev 26 §3, plan Spec note 25); Reset to defaults then resets the shared rows and the weight
 * group (open question 1), and Start fresh flushes this page's draft before the switch (note 33).
 */
@Composable
fun ProgressionPage(vm: ProgressionSettingsViewModel, weightVm: WeightSettingsViewModel? = null, windowOnly: Boolean = false) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val weight = weightVm?.let { weightPageOf(it) }
    val weightMode = weight?.mode?.usesWeights == true
    draft?.let {
        ProgressionPageContent(
            it, validation, status,
            onChange = { field, transform -> vm.update(field, transform) },
            onChangeNow = { field, transform -> vm.updateNow(field, transform) },
            onReset = if (weightMode && weightVm != null) {
                {
                    vm.resetSharedToDefaults()
                    weightVm.resetToDefaults()
                }
            } else {
                vm::resetToDefaults
            },
            windowOnly = windowOnly, note = note, weight = weight,
        )
    }
    weightVm?.let { WeightDialogs(it, beforeSwitch = vm::flush) }
}

/** The weight ViewModel as the page's [WeightPage]; null until it has loaded. Every flow is collected first, so the composition's shape never changes. */
@Composable
private fun weightPageOf(vm: WeightSettingsViewModel): WeightPage? {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val m = mode ?: return null
    val d = draft ?: return null
    return WeightPage(
        m, d, validation, status, note,
        onChange = { field, transform -> vm.update(field, transform) },
        onChangeNow = { field, transform -> vm.updateNow(field, transform) },
        onRequestMode = vm::requestMode,
        onRequestUnit = vm::requestUnit,
    )
}

/** The Start fresh and unit confirms (plan Spec notes 33–34). */
@Composable
private fun WeightDialogs(vm: WeightSettingsViewModel, beforeSwitch: () -> Unit) {
    val modePrompt by vm.modePrompt.collectAsStateWithLifecycle()
    val unitPrompt by vm.unitPrompt.collectAsStateWithLifecycle()
    modePrompt?.let {
        StartFreshDialog(
            it,
            onConfirm = {
                beforeSwitch()
                vm.confirmMode()
            },
            onDismiss = vm::dismissModePrompt,
        )
    }
    unitPrompt?.let { ChangeUnitDialog(it, onConfirm = vm::confirmUnit, onDismiss = vm::dismissUnitPrompt) }
}
```

4. Replace `ProgressionPageContent` with this version (same parameters plus `weight` last; the Reps branch is today's rows, unchanged):

```kotlin
@Composable
fun ProgressionPageContent(
    draft: ProgressionDraft,
    validation: ValidationResult,
    status: SaveStatus,
    onChange: ProgressionEdit,
    onChangeNow: ProgressionEdit,
    onReset: () -> Unit,
    windowOnly: Boolean = false,
    note: ProgressionNote? = null,
    weight: WeightPage? = null,
) {
    val errors = validation.errors
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // Spec rev 26 §3: a weight mode swaps the Reps rows for the weight rows; the window, the penalty and the Hold switch stay.
    val weightRows = weight?.takeIf { it.mode.usesWeights }
    val pageStatus = if (weightRows != null) SaveStatus.worst(status, weightRows.status) else status
    SettingsPageLayout(footer = { SaveStatusLine(pageStatus) }) {
        if (!windowOnly) {
            weight?.let { ProgressByRow(it.mode, it.onRequestMode) }
            if (weightRows != null) {
                WeightSetupRows(weightRows)
                SwitchRow(
                    stringResource(R.string.hold), draft.hold,
                    onChange = { on -> onChangeNow(null) { it.copy(hold = on) } }, info = stringResource(R.string.info_hold_weight),
                )
                AnimatedVisibility(visible = draft.hold) {
                    Column { WeightHoldRows(weightRows) }
                }
            } else {
                IntStepperField(
                    stringResource(R.string.starting_total), draft.startingTotal, FieldRanges.REPS, ValueInput.WHOLE,
                    onUpdate = { f -> onChange(ProgressionField.STARTING_TOTAL) { it.copy(startingTotal = f(it.startingTotal)) } },
                    onDialogUpdate = { f -> onChangeNow(ProgressionField.STARTING_TOTAL) { it.copy(startingTotal = f(it.startingTotal)) } },
                    error = errors[Field.STARTING_TOTAL].resolve(), info = stringResource(R.string.info_starting_total),
                )
                NoteUnder(note, ProgressionField.STARTING_TOTAL)
                IntStepperField(
                    stringResource(R.string.floor), draft.floor, FieldRanges.REPS, ValueInput.WHOLE,
                    onUpdate = { f -> onChange(ProgressionField.FLOOR) { it.copy(floor = f(it.floor)) } },
                    onDialogUpdate = { f -> onChangeNow(ProgressionField.FLOOR) { it.copy(floor = f(it.floor)) } },
                    error = errors[Field.FLOOR].resolve(), info = stringResource(R.string.info_floor),
                )
                NoteUnder(note, ProgressionField.FLOOR)
                IntStepperField(
                    stringResource(R.string.cap), draft.cap, FieldRanges.REPS, ValueInput.WHOLE,
                    onUpdate = { f -> onChange(ProgressionField.CAP) { it.copy(cap = f(it.cap)) } },
                    onDialogUpdate = { f -> onChangeNow(ProgressionField.CAP) { it.copy(cap = f(it.cap)) } },
                    error = errors[Field.CAP].resolve(), info = stringResource(R.string.info_cap),
                )
                NoteUnder(note, ProgressionField.CAP)
                // Spec R3 §5.3, rev 16 §6: the switch sits directly above the holds; off hides the list but keeps its values.
                SwitchRow(
                    stringResource(R.string.hold), draft.hold,
                    onChange = { on -> onChangeNow(null) { it.copy(hold = on) } }, info = stringResource(R.string.info_hold),
                )
                AnimatedVisibility(visible = draft.hold) {
                    Column {
                        draft.holds.forEachIndexed { i, hold ->
                            val holdErrors = validation.holdErrors[i].orEmpty()
                            HoldHeader(number = i + 1, onRemove = { onChangeNow(null) { it.withoutHold(i) } })
                            IntStepperField(
                                stringResource(R.string.hold_at), hold.at, FieldRanges.REPS, ValueInput.WHOLE,
                                onUpdate = { f -> onChange(null) { it.updateHold(i) { h -> h.copy(at = f(h.at)) } } },
                                onDialogUpdate = { f -> onChangeNow(null) { it.updateHold(i) { h -> h.copy(at = f(h.at)) } } },
                                error = holdErrors[HoldField.AT].resolve(), hint = validation.holdHints[i].resolve(), info = stringResource(R.string.info_hold_at),
                                a11yLabel = stringResource(R.string.hold_n_at, i + 1),
                            )
                            IntStepperField(
                                stringResource(R.string.hold_for), hold.forCount, FieldRanges.HOLD_FOR, ValueInput.WHOLE,
                                onUpdate = { f -> onChange(null) { it.updateHold(i) { h -> h.copy(forCount = f(h.forCount)) } } },
                                onDialogUpdate = { f -> onChangeNow(null) { it.updateHold(i) { h -> h.copy(forCount = f(h.forCount)) } } },
                                error = holdErrors[HoldField.FOR].resolve(), info = stringResource(R.string.info_hold_for),
                                a11yLabel = stringResource(R.string.hold_n_for, i + 1),
                            )
                        }
                        AddHoldButton(
                            enabled = draft.holds.size < ProgressionConfig.MAX_HOLDS,
                            onClick = { onChangeNow(null) { it.withNewHold() } },
                        )
                    }
                }
            }
        }
        IntStepperField(
            stringResource(R.string.window_hours), draft.windowHours, FieldRanges.WINDOW_HOURS, ValueInput.WHOLE,
            onUpdate = { f -> onChange(null) { it.copy(windowHours = f(it.windowHours)) } },
            onDialogUpdate = { f -> onChangeNow(null) { it.copy(windowHours = f(it.windowHours)) } },
            error = errors[Field.WINDOW_HOURS].resolve(), info = stringResource(R.string.info_window),
        )
        if (!windowOnly) {
            // Open question 4: in a weight mode the penalty counts steps on the ladder.
            PenaltyStepperField(
                stringResource(if (weightRows != null) R.string.penalty_rate_steps else R.string.penalty_rate), draft.penalty,
                onUpdate = { f -> onChange(null) { it.copy(penalty = f(it.penalty)) } },
                onDialogUpdate = { f -> onChangeNow(null) { it.copy(penalty = f(it.penalty)) } },
                error = errors[Field.PENALTY_RATE].resolve(),
                info = stringResource(if (weightRows != null) R.string.info_penalty_rate_steps else R.string.info_penalty_rate),
            )
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 16.dp).testTag("reset_defaults")) {
                Text(stringResource(R.string.reset_defaults))
            }
            if (weightRows != null) WeightNoteUnder(weightRows, null) else NoteUnder(note, null)
        }
    }
    // Spec R3 §6.4: confirmed, then applied at once.
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_defaults_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    },
                    modifier = Modifier.testTag("confirm_reset_defaults"),
                ) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `testDebugUnitTest --tests "*WeightProgressionPageTest*" --tests "*ProgressionSettingsScreenTest*" --tests "*ProgressionSettingsViewModelTest*" --tests "*HardCodedTextGuardTest*"`
Expected: PASS. `ProgressionSettingsScreenTest` passes unchanged, since `weight = null` keeps today's page.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/settings/WeightSections.kt app/src/main/kotlin/com/mitenko/repkit/ui/settings/ProgressionSettings.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/WeightProgressionPageTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/ProgressionSettingsViewModelTest.kt
git commit -m "Show Progress by and the weight setup on the Progression page"
```

---

### Task 11: Wire the weight ViewModel into the settings pager

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/settings/SettingsPager.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/settings/SettingsPagerTest.kt`
- Test (create): `app/src/test/kotlin/com/mitenko/repkit/ui/settings/TwoDraftsTest.kt` (plan Spec note 42)

**Interfaces:**
- Consumes: `WeightSettingsViewModel` (Tasks 8–9), `ProgressionPage(vm, weightVm, windowOnly)` (Task 10).
- Produces: `SettingsPagerRoute(…, weightVm: WeightSettingsViewModel = hiltViewModel(key = "weight"))` as the last parameter; the pager flushes it on every page change and exit, clears its note on a page change, and pops when it reports `missing`.

- [ ] **Step 1: Update the test helper and write the failing tests**

In `SettingsPagerTest`, add imports `com.mitenko.repkit.data.WeightUnitDefaults`, `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.ProgressionConfig`, `com.mitenko.repkit.domain.model.WeightConfig`, `com.mitenko.repkit.domain.model.WeightUnit`, `com.mitenko.repkit.domain.model.CounterState`, `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.flowOf`, `androidx.compose.ui.test.assertIsSelected` (each only if missing). Add a field and change `show()` so every test passes a weight ViewModel (without it the default `hiltViewModel()` can't run in Robolectric):

```kotlin
    private val lbDefault = object : WeightUnitDefaults {
        override val weightUnitDefault: Flow<WeightUnit> = flowOf(WeightUnit.LB)
    }
```

In `show()`, after `val cuesVm = …`:

```kotlin
        val weightVm = WeightSettingsViewModel(handle(), repo, lbDefault, appScope)
```

and pass `weightVm = weightVm` to `SettingsPagerRoute(…)`.

Append the tests:

```kotlin
    @Test
    fun `switching to Weight asks Start fresh, then shows the weight rows in the app default unit`() {
        show(SettingsPage.PROGRESSION)
        compose.onNodeWithTag("mode_WEIGHT").performClick()
        compose.onNodeWithText("Start fresh?").assertExists()
        compose.onNodeWithTag("confirm_start_fresh").performClick()
        compose.waitUntil(5_000) { repo.find(1).progression.mode == ProgressMode.WEIGHT }
        compose.onNodeWithTag("card_Unit").assertExists()
        compose.onNodeWithTag("unit_LB").assertIsSelected()
        assertEquals(true, repo.find(1).counter.freshStart)
    }

    @Test
    fun `a pending weight edit is saved when the page changes`() {
        val weightRepo = FakeEntryRepository(listOf(testEntry(
            1, name = "Curls",
            progression = ProgressionConfig(mode = ProgressMode.WEIGHT, weight = WeightConfig(unit = WeightUnit.KG)),
            counter = CounterState(total = 0),
        )))
        show(SettingsPage.PROGRESSION, weightRepo)
        compose.onNodeWithContentDescription("Increase Reps per set").performScrollTo().performClick()
        tab(SettingsPage.CURRENT).performClick()
        compose.waitUntil(5_000) { weightRepo.find(1).progression.weight.repsPerSet == 11 }
    }

    @Test
    fun `a Timer only entry has no Progress by row`() {
        show(SettingsPage.PROGRESSION, checkInRepo())
        compose.onNodeWithTag("card_Progress by").assertDoesNotExist()
    }
```

Create `TwoDraftsTest.kt`: both Progression-page ViewModels on one Room entry, with both drafts dirty, as every flushing action leaves them (plan Spec note 42). The pager's page change and exit run exactly `flushAll` (`onCleared` runs the same flush in the app scope), so the first test covers both.

```kotlin
package com.mitenko.repkit.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.data.MigrationGate
import com.mitenko.repkit.data.RoomEntryRepository
import com.mitenko.repkit.data.WeightUnitDefaults
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.domain.WeightConversion
import com.mitenko.repkit.domain.WeightField
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.withNewWeightHold
import com.mitenko.repkit.testutil.FakeClock
import com.mitenko.repkit.testutil.MainDispatcherRule
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TwoDraftsTest {
    @get:Rule val main = MainDispatcherRule()

    private lateinit var db: HiitDatabase

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private val lbDefault = object : WeightUnitDefaults {
        override val weightUnitDefault: Flow<WeightUnit> = flowOf(WeightUnit.LB)
    }

    private class Page(val repo: RoomEntryRepository, val id: Long, val reps: ProgressionSettingsViewModel, val weights: WeightSettingsViewModel)

    /** A Weight-mode entry (kg) with both of the Progression page's ViewModels loaded, sharing one handle as in the pager. */
    private suspend fun TestScope.page(): Page {
        val open = object : MigrationGate {
            override suspend fun awaitReady() = Unit
        }
        val repo = RoomEntryRepository(db, open, FakeClock()) { " copy" }
        val id = repo.create("Curls")
        repo.switchMode(id, ProgressMode.WEIGHT, WeightUnit.KG)
        val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to id))
        val reps = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        val weights = WeightSettingsViewModel(handle, repo, lbDefault, backgroundScope)
        reps.draft.first { it != null }
        weights.draft.first { it != null }
        return Page(repo, id, reps, weights)
    }

    @Test
    fun `both dirty drafts flushed together keep each other's fields, in either order`() = runTest {
        for (weightsFirst in listOf(false, true)) {
            val p = page()
            p.reps.update { it.copy(windowHours = 40, hold = false) }
            p.weights.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
            if (weightsFirst) {
                p.weights.flush()
                p.reps.flush()
            } else {
                p.reps.flush()
                p.weights.flush()
            }
            val e = p.repo.entry(p.id).first { it!!.progression.windowHours == 40 && it.progression.weight.repsPerSet == 12 }!!
            assertEquals(false, e.progression.hold)
            assertEquals(ProgressMode.WEIGHT, e.progression.mode)
        }
    }

    @Test
    fun `Start fresh lands both pending drafts before the switch`() = runTest {
        val p = page()
        p.reps.update { it.copy(windowHours = 40) }
        p.weights.update(WeightField.REPS_PER_SET) { it.copy(repsPerSet = 12) }
        p.weights.requestMode(ProgressMode.REPS_THEN_WEIGHT)
        p.reps.flush() // the dialog's beforeSwitch
        p.weights.confirmMode()
        val e = p.repo.entry(p.id).first { it!!.progression.mode == ProgressMode.REPS_THEN_WEIGHT && it.progression.windowHours == 40 }!!
        assertEquals(12, e.progression.weight.repsPerSet)
        val row = db.entryDao().get(p.id)!!
        assertNull(row.total)
        assertTrue(row.freshStart)
    }

    @Test
    fun `a unit change saved while a Reps-side edit is pending loses neither`() = runTest {
        val p = page()
        p.reps.update { it.copy(windowHours = 40) }
        p.weights.requestUnit(WeightUnit.LB)
        p.weights.confirmUnit()
        p.reps.flush()
        val e = p.repo.entry(p.id).first { it!!.progression.weight.unit == WeightUnit.LB && it.progression.windowHours == 40 }!!
        assertEquals(WeightConversion.convert(WeightConfig(unit = WeightUnit.KG), WeightUnit.LB).list, e.progression.weight.list)
    }

    @Test
    fun `the shared Hold switch and the weight holds save independently`() = runTest {
        val p = page()
        p.reps.updateNow { it.copy(hold = false) }
        p.weights.updateNow(WeightField.HOLDS) { it.withNewWeightHold(ProgressMode.WEIGHT) }
        val e = p.repo.entry(p.id).first { !it!!.progression.hold && it.progression.weight.holds.size == 1 }!!
        assertEquals(ProgressMode.WEIGHT, e.progression.mode)
    }
}
```

A lost write shows up as the `first { … }` never matching, so the test times out (runTest's 60 s) instead of passing.

`TwoDraftsTest` needs only Tasks 8–10, so it passes before Step 3: it pins note 42's contract rather than driving new code. If it fails, stop and report it; don't change the test.

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*SettingsPagerTest*"`
Expected: compilation fails: `SettingsPagerRoute` has no `weightVm` parameter.

- [ ] **Step 3: Implement**

In `SettingsPager.kt`:
- add the parameter after `cuesVm`: `weightVm: WeightSettingsViewModel = hiltViewModel(key = "weight"),`;
- collect `val weightGone by weightVm.missing.collectAsStateWithLifecycle()` next to the others and add `|| weightGone` to `missing`;
- add `weightVm.flush()` to `flushAll`;
- add `weightVm.clearNote()` to `onPageChange`;
- pass `weightVm` through `SettingsTabs` (new parameter after `cuesVm`) and call `SettingsPage.PROGRESSION -> ProgressionPage(progressionVm, weightVm, windowOnly = checkInOnly)`;
- extend the route's KDoc: "The Progression page has a second ViewModel for the weight group (spec rev 26 PR 2, plan Spec note 25), flushed and noted like the others."

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*SettingsPagerTest*" --tests "*TwoDraftsTest*" --tests "*SettingsPagesTest*" --tests "*EntrySettingsScreenTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/settings/SettingsPager.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/SettingsPagerTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/TwoDraftsTest.kt
git commit -m "Host the weight settings ViewModel in the settings pager"
```

---

### Task 12: The Current page in weight modes

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/settings/CurrentStateSettings.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/settings/CurrentStateViewModelTest.kt`, `app/src/test/kotlin/com/mitenko/repkit/ui/settings/CurrentStatePageTest.kt`, `app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt` (plan Spec note 44)

**Interfaces:**
- Consumes: `ProgressionScale.Ladder`, `ProgressionConfig.scale()` (PR 1); `stepAlong` (Task 2); `currentState(levels)`, `unitLabel` (Task 6); `WeightPickerField` (Task 7).
- Produces: `CurrentStateViewModel.LadderView(scale: ProgressionScale.Ladder, unit: WeightUnit?)`, `CurrentStateViewModel.ladder: StateFlow<LadderView?>`; `CurrentStatePageContent(…, ladder: CurrentStateViewModel.LadderView? = null)` (new last parameter). Tags: `card_Current weight`, `value_Current weight`, `option_Current weight_<hundredths>`, `card_Current reps per set`.

- [ ] **Step 1: Write the failing tests**

Append to `CurrentStateViewModelTest` (imports as needed: `com.mitenko.repkit.domain.FieldMessage`, `com.mitenko.repkit.domain.Field`, `com.mitenko.repkit.domain.ProgressionScale`, `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.ProgressionConfig`, `com.mitenko.repkit.domain.model.WeightConfig`, `com.mitenko.repkit.domain.model.WeightSteps`, `com.mitenko.repkit.domain.model.WeightUnit`, `org.junit.Assert.assertNull`, `org.junit.Assert.assertTrue`):

```kotlin
    private fun weightEntry(level: Int) = testEntry(
        1,
        progression = ProgressionConfig(mode = ProgressMode.WEIGHT, weight = WeightConfig(unit = WeightUnit.KG)),
        counter = CounterState(total = level),
    )

    @Test
    fun `in a weight mode the ladder is exposed and level 0 saves`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(3)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        assertEquals(ProgressionScale.Weight(WeightSteps.DEFAULT.expand(), 10), vm.ladder.value!!.scale)
        assertEquals(WeightUnit.KG, vm.ladder.value!!.unit)
        vm.updateNow { it.copy(total = 0) }
        runCurrent()
        assertTrue(vm.validation.value.isValid)
        assertEquals(0, repo.find(1).counter.total)
    }

    @Test
    fun `a level past the top is invalid and never saved`() = runTest {
        val repo = FakeEntryRepository(listOf(weightEntry(3)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.updateNow { it.copy(total = 17) } // 17 weights on 20 / 2.5 / 60: levels 0..16
        assertEquals(FieldMessage.NotOnLadder, vm.validation.value.errors[Field.TOTAL])
        runCurrent()
        assertEquals(0, repo.counterWrites)
    }

    @Test
    fun `Reps mode has no ladder`() = runTest {
        val vm = CurrentStateViewModel(handle, FakeEntryRepository(listOf(testEntry(1))), clock, backgroundScope)
        assertNull(vm.ladder.value)
    }
```

Append to `RoomEntryRepositoryTest` (plan Spec note 44: what a Current save does in Reps then weight; a contract test that passes on arrival, since PR 1's `overwriteCounter` already behaves so):

```kotlin
    @Test
    fun `a Reps then weight Current save resets the hold count only when the level changes, and never widens`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.switchMode(a, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG) // 20 / 2.5 / 60 kg × 8–12: 17 weights × span 5, levels 0..84
        r.overwriteCounter(a, total = 7, bestStreak = 0, currentStreak = 0, lastCheckIn = null) // 22.5 kg × 10
        val before = db.entryDao().get(a)!!
        db.entryDao().setCounter(a, 7, 0, 0, holdCount = 2, lastCheckIn = null)
        assertNull(r.overwriteCounter(a, total = 7, bestStreak = 3, currentStreak = 3, lastCheckIn = null)) // streaks only: same level
        assertEquals(2, db.entryDao().get(a)!!.holdCount)
        assertNull(r.overwriteCounter(a, total = 2, bestStreak = 3, currentStreak = 3, lastCheckIn = null)) // weight only: 20 kg × 10
        assertEquals(0, db.entryDao().get(a)!!.holdCount)
        assertNull(r.overwriteCounter(a, total = 84, bestStreak = 3, currentStreak = 3, lastCheckIn = null)) // the top: 60 kg × 12
        val after = db.entryDao().get(a)!!
        assertEquals(listOf(before.startingTotal, before.floor, before.cap), listOf(after.startingTotal, after.floor, after.cap))
        assertTrue(runCatching { r.overwriteCounter(a, total = 85, bestStreak = 3, currentStreak = 3, lastCheckIn = null) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(84, db.entryDao().get(a)!!.total)
    }
```

The weight-only and reps-only mapping onto the level (`levelOf(w, current reps)` / `levelOf(current weight, r)`) is tested on the page below.

Append to `CurrentStatePageTest` (imports `com.mitenko.repkit.domain.ProgressionScale`, `com.mitenko.repkit.domain.model.WeightUnit`, `androidx.compose.ui.test.assertTextEquals`, `androidx.compose.ui.test.onNodeWithContentDescription` if missing):

```kotlin
    private fun showLadder(ladder: CurrentStateViewModel.LadderView) {
        compose.setContent {
            HiitTheme {
                CurrentStatePageContent(
                    draft, ValidationResult(), SaveStatus.SAVED, ZoneOffset.UTC, now = { Instant.parse("2026-09-24T12:00:00Z") },
                    onChange = { field, f -> fields += field; draft = f(draft) },
                    onChangeNow = { field, f -> fields += field; immediate++; draft = f(draft) },
                    onResetProgress = { resets += it },
                    ladder = ladder,
                )
            }
        }
    }

    @Test
    fun `a Weight-mode Current page shows Current weight and moves along the ladder`() {
        draft = draft.copy(total = 1)
        showLadder(CurrentStateViewModel.LadderView(ProgressionScale.Weight(listOf(2000, 2250, 2500), 10), WeightUnit.KG))
        compose.onNodeWithText("Current weight (kg)").assertExists()
        compose.onNodeWithTag("value_Current weight").assertTextEquals("22.5")
        compose.onNodeWithTag("card_Current reps").assertDoesNotExist()
        compose.onNodeWithTag("card_Current reps per set").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase Current weight").performClick()
        assertEquals(2, draft.total)
        compose.onNodeWithTag("value_Current weight").performClick()
        compose.onNodeWithTag("option_Current weight_2000").performClick()
        assertEquals(0, draft.total)
        assertEquals(1, immediate)
    }

    @Test
    fun `Reps then weight shows Current reps per set within the range`() {
        draft = draft.copy(total = 7) // 22.5 × 10 on 20 / 22.5 × 8–12: weight 1 × span 5 + 2
        showLadder(CurrentStateViewModel.LadderView(ProgressionScale.RepsThenWeight(listOf(2000, 2250), 8, 12), WeightUnit.KG))
        compose.onNodeWithTag("value_Current reps per set").assertTextEquals("10")
        compose.onNodeWithContentDescription("Increase Current reps per set").performScrollTo().performClick()
        assertEquals(8, draft.total)
        compose.onNodeWithTag("value_Current weight").performScrollTo().performClick()
        compose.onNodeWithTag("option_Current weight_2000").performClick()
        assertEquals(3, draft.total) // 20 × 11: the reps are kept
    }

    @Test
    fun `the reset dialog talks about weight in a weight mode`() {
        showLadder(CurrentStateViewModel.LadderView(ProgressionScale.Weight(listOf(2000, 2250), 10), WeightUnit.KG))
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithText("Your weight and reps will return to the starting point, your streaks will reset to 0, and your last check-in will be cleared.").assertIsDisplayed()
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*CurrentStateViewModelTest*" --tests "*CurrentStatePageTest*"`
Expected: compilation fails (`ladder`, `LadderView`).

- [ ] **Step 3: Implement in `CurrentStateSettings.kt`**

Imports to add: `com.mitenko.repkit.domain.ProgressionScale`, `com.mitenko.repkit.domain.StepRange`, `com.mitenko.repkit.domain.scale`, `com.mitenko.repkit.domain.stepAlong`, `com.mitenko.repkit.domain.model.WeightUnit`, `com.mitenko.repkit.ui.common.WeightPickerField`, `com.mitenko.repkit.ui.common.unitLabel`.

In `CurrentStateViewModel`:

1. After `data class Draft(…)` add:

```kotlin
    /** The ladder in a weight mode (spec rev 26 §3 Current tab, plan Spec note 35): the draft's total is a level on [scale]. */
    data class LadderView(val scale: ProgressionScale.Ladder, val unit: WeightUnit?)
```

2. Right after `_draft`, add (it must come before `validation`, which reads it):

```kotlin
    private val _ladder = MutableStateFlow<LadderView?>(null)

    /** Null in Reps mode, and until the entry has loaded. */
    val ladder: StateFlow<LadderView?> = _ladder.asStateFlow()
```

3. Replace `validation`:

```kotlin
    val validation: StateFlow<ValidationResult> = combine(_draft, _ladder) { d, l -> d?.let { validate(it, l) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())
```

4. Replace `init`:

```kotlin
    init {
        // A valid draft restored after process death may never have been written; the write is idempotent if it was.
        // Checked by the Reps rules here, so it saves even before the entry loads (as before).
        val restoredRaw = _draft.value
        val restored = restoredRaw?.takeIf { validate(it, ladder = null).isValid }
        restored?.let(saver::schedule)
        viewModelScope.launch {
            repo.entry(entryId).filterNotNull().collect { e ->
                val ladder = (e.progression.scale() as? ProgressionScale.Ladder)?.let { LadderView(it, e.progression.weight.unit) }
                _ladder.value = ladder
                val latest = e.counter.toDraft()
                val current = _draft.value
                if (stored == null) {
                    // A restored draft already matching the store needs no write (Minor 3); one that differs still saves.
                    if (restoredRaw == latest) {
                        saver.cancel()
                    } else if (ladder != null && restoredRaw != null) {
                        // Plan Spec note 35: in a weight mode its validity depends on the ladder (a level can be 0).
                        if (validate(restoredRaw).isValid) saver.schedule(restoredRaw) else saver.cancel()
                    }
                }
                // Follow the store only without unsaved edits. An edit back to the stored value
                // whose save is still pending counts as unsaved, so an echo can't overwrite it.
                if (current == null || (current == stored && !saver.hasPending)) setDraft(latest)
                stored = latest
            }
        }
    }
```

5. Replace `validate`:

```kotlin
    private fun validate(d: Draft, ladder: LadderView? = _ladder.value): ValidationResult =
        SettingsValidator.currentState(
            d.total, d.best, d.current, d.lastCheckIn, clock.now(),
            levels = ladder?.scale?.let { it.minLevel..it.maxLevel },
        )
```

In `CurrentStatePage`, collect `val ladder by vm.ladder.collectAsStateWithLifecycle()` and pass `ladder = ladder`.

In `CurrentStatePageContent`, add the last parameter `ladder: CurrentStateViewModel.LadderView? = null`, and replace the `if (showTotal) { … }` block with:

```kotlin
        if (showTotal) {
            if (ladder == null) {
                IntStepperField(
                    stringResource(R.string.current_total), draft.total, FieldRanges.TOTAL, ValueInput.WHOLE,
                    onUpdate = { f -> onChange(null) { it.copy(total = f(it.total)) } },
                    onDialogUpdate = { f -> onChangeNow(null) { it.copy(total = f(it.total)) } },
                    error = validation.errors[Field.TOTAL].resolve(), info = stringResource(R.string.info_total_reps),
                )
                // Spec revision 27: which limit the save moved, announced politely to TalkBack.
                rangeNote?.let { MoveNote(listOf(it), tag = "range_note") }
            } else {
                CurrentLoadRows(draft, ladder, validation, onChange, onChangeNow)
            }
        }
```

and in the Reset progress dialog use `Text(stringResource(if (ladder != null) R.string.reset_progress_body_weight else R.string.reset_progress_body))`.

Add, below `CurrentStatePageContent`:

```kotlin
/**
 * The current load in a weight mode (spec rev 26 §3 Current tab, plan Spec note 35): Current weight
 * moves along the ladder keeping the reps, and in Reps then weight Current reps per set moves within
 * the rep range keeping the weight. Each edit writes the level; nothing widens (§10 note 20).
 */
@Composable
private fun CurrentLoadRows(
    draft: CurrentStateViewModel.Draft,
    ladder: CurrentStateViewModel.LadderView,
    validation: ValidationResult,
    onChange: CurrentEdit,
    onChangeNow: CurrentEdit,
) {
    val scale = ladder.scale
    val load = scale.prescription(draft.total)
    WeightPickerField(
        unitLabel(R.string.current_weight, ladder.unit), scale.weights, load.weight, ladder.unit,
        onStep = { up ->
            onChange(null) { d ->
                val p = scale.prescription(d.total)
                d.copy(total = scale.levelOf(stepAlong(scale.weights, p.weight, up) ?: p.weight, p.reps))
            }
        },
        onPick = { w -> onChangeNow(null) { d -> d.copy(total = scale.levelOf(w, scale.prescription(d.total).reps)) } },
        error = validation.errors[Field.TOTAL].resolve(), info = stringResource(R.string.info_current_weight),
        a11yLabel = stringResource(R.string.current_weight),
    )
    if (scale is ProgressionScale.RepsThenWeight) {
        IntStepperField(
            stringResource(R.string.current_reps_per_set), load.reps, StepRange(scale.repMin, scale.repMax, 1), ValueInput.WHOLE,
            onUpdate = { f -> onChange(null) { d -> val p = scale.prescription(d.total); d.copy(total = scale.levelOf(p.weight, f(p.reps))) } },
            onDialogUpdate = { f -> onChangeNow(null) { d -> val p = scale.prescription(d.total); d.copy(total = scale.levelOf(p.weight, f(p.reps))) } },
            info = stringResource(R.string.info_current_reps_per_set),
        )
    }
}
```

Update the class KDoc of `CurrentStateViewModel` with one sentence: "In a weight mode the total is a level on [ladder] (spec rev 26 §3, plan Spec note 35)."

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*CurrentStateViewModelTest*" --tests "*CurrentStatePageTest*" --tests "*SettingsPagerTest*" --tests "*RoomEntryRepositoryTest*"`
Expected: PASS, including every existing restore test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/settings/CurrentStateSettings.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/CurrentStateViewModelTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/CurrentStatePageTest.kt app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt
git commit -m "Edit the current weight and reps on the Current page in weight modes"
```

---

### Task 13: No Sets-changed prompt in weight modes (open question 2)

**Depends on ruling:** open question 2. Skip this task if the user rules that the prompt should stay in weight modes.

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/settings/TimingSettings.kt` (`checkSets`)
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/settings/TimingSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `ProgressMode.usesWeights` (PR 1).
- Produces: nothing new; `setsPrompt` stays null for a weight-mode entry.

- [ ] **Step 1: Write the failing test**

Append to `TimingSettingsViewModelTest` (imports `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.ProgressionConfig`, `com.mitenko.repkit.domain.model.WeightConfig`, `com.mitenko.repkit.domain.model.WeightUnit` if missing):

```kotlin
    @Test
    fun `a weight-mode entry is never prompted, since its reps are per set`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(
            1,
            progression = ProgressionConfig(mode = ProgressMode.WEIGHT, weight = WeightConfig(unit = WeightUnit.KG)),
            counter = CounterState(total = 3),
        )))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.pageShown(timing = true)
        runCurrent()
        vm.update { it.copy(sets = 9) }
        leaveTiming(vm)
        assertNull(vm.setsPrompt.value)
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `testDebugUnitTest --tests "*TimingSettingsViewModelTest*"`
Expected: FAIL: the prompt is `SetsChange(8, 9)`.

- [ ] **Step 3: Implement**

In `checkSets`, extend the prompting branch's condition:

```kotlin
                entry != null && entry.type != EntryType.CHECK_IN && !entry.progression.mode.usesWeights &&
                    from != null && entry.timing.sets != from -> {
```

and add to the class KDoc: "A weight-mode entry is never asked: its reps are per set, so Sets doesn't change them (plan Spec note 39)."

- [ ] **Step 4: Run it to see it pass**

Run: `testDebugUnitTest --tests "*TimingSettingsViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/settings/TimingSettings.kt app/src/test/kotlin/com/mitenko/repkit/ui/settings/TimingSettingsViewModelTest.kt
git commit -m "Skip the Sets-changed prompt in weight modes"
```

---

### Task 14: A Current edit keeps the fresh start unless the level changes (open question 3)

**Depends on ruling:** open question 3. Skip this task if the user rules that every Current edit should clear the flag (today's §10 note 13); the transition matrix's Current-edit row then reads "cleared".

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/EntryRepository.kt` (`overwriteCounter` and its KDoc)
- Modify: `app/src/test/kotlin/com/mitenko/repkit/testutil/FakeEntryRepository.kt` (`overwriteCounter`)
- Test: `app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `overwriteCounter` writes `fresh_start = 0` only when the new total differs from the stored effective total.

- [ ] **Step 1: Write the failing test**

Append to `RoomEntryRepositoryTest`:

```kotlin
    @Test
    fun `a Current edit that keeps the level keeps the fresh start, and moving the level clears it`() = runTest {
        val r = repo()
        val id = r.create("Curls")
        r.switchMode(id, ProgressMode.WEIGHT, WeightUnit.KG)
        r.overwriteCounter(id, total = 0, bestStreak = 5, currentStreak = 5, lastCheckIn = null) // the start level, streaks edited
        assertEquals(true, db.entryDao().get(id)!!.freshStart)
        r.overwriteCounter(id, total = 3, bestStreak = 5, currentStreak = 5, lastCheckIn = null)
        assertEquals(false, db.entryDao().get(id)!!.freshStart)
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `testDebugUnitTest --tests "*RoomEntryRepositoryTest*"`
Expected: FAIL at the first `assertEquals(true, …)`.

- [ ] **Step 3: Implement**

In `RoomEntryRepository.overwriteCounter`, replace

```kotlin
            // Plan Spec note 13: an explicit Current edit means the user chose the position.
            dao.setFreshStart(id, false)
```

with

```kotlin
            // §10 note 13, amended by note 40: choosing another level clears the fresh start; a streak or
            // date edit that keeps the level doesn't, so the next check-in is still performed at the start.
            if (total != old.total) dao.setFreshStart(id, false)
```

and change the interface KDoc's last sentence to: "A counter whose total changes clears the fresh-start flag (§10 notes 13 and 40)."

In `FakeEntryRepository.overwriteCounter`, keep the flag the same way:

```kotlin
            val fresh = it.counter.freshStart && total == it.counter.total
            it.copy(progression = widened, counter = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount, fresh))
```

- [ ] **Step 4: Run it to see it pass**

Run: `testDebugUnitTest --tests "*RoomEntryRepositoryTest*" --tests "*CurrentStateViewModelTest*"`
Expected: PASS (the existing switchMode / check-in tests are unchanged).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/data/EntryRepository.kt app/src/test/kotlin/com/mitenko/repkit/testutil/FakeEntryRepository.kt app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt
git commit -m "Keep the fresh start through Current edits that don't move the level"
```

---

### Task 15: ⚙ › Units

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/ui/entries/EntryListViewModel.kt`, `app/src/main/kotlin/com/mitenko/repkit/ui/entries/EntryListScreen.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/ui/entries/EntryListViewModelTest.kt`, `app/src/test/kotlin/com/mitenko/repkit/ui/entries/EntryListScreenTest.kt`

**Interfaces:**
- Consumes: `AppPreferences.weightUnitDefault` / `setWeightUnitDefault` (PR 1); `WeightUnit.longLabel` (Task 6).
- Produces: `EntryListViewModel.weightUnitDefault: StateFlow<WeightUnit>`, `setWeightUnitDefault(unit)`; `EntryListScreen(…, weightUnit: WeightUnit = WeightUnit.KG, onSetWeightUnit: (WeightUnit) -> Unit = {})`. Tags: `units_heading`, `unit_default_<UNIT>`.

- [ ] **Step 1: Write the failing tests**

Append to `EntryListViewModelTest` (imports `com.mitenko.repkit.domain.model.WeightUnit`, `kotlinx.coroutines.flow.first` if missing):

```kotlin
    @Test
    fun `setWeightUnitDefault writes through to preferences and updates the default`() = runTest {
        val preferences = preferences()
        val vm = vm(FakeEntryRepository(), preferences)
        backgroundScope.launch { vm.weightUnitDefault.collect {} }
        vm.setWeightUnitDefault(WeightUnit.LB)
        runCurrent()
        assertEquals(WeightUnit.LB, preferences.weightUnitDefault.first())
        assertEquals(WeightUnit.LB, vm.weightUnitDefault.first { it == WeightUnit.LB })
    }
```

In `EntryListScreenTest`, add `weightUnit: WeightUnit = WeightUnit.KG, onSetWeightUnit: (WeightUnit) -> Unit = {}` to `show()`'s parameters and pass `weightUnit = weightUnit, onSetWeightUnit = onSetWeightUnit` to `EntryListScreen`. Then append (imports `com.mitenko.repkit.domain.model.WeightUnit`, `androidx.compose.ui.test.assertIsNotSelected`, `androidx.compose.ui.test.performScrollTo` if missing):

```kotlin
    @Test
    fun `the Settings dialog offers Units with the stored default selected`() {
        show(EntryListUiState.Items(rows), weightUnit = WeightUnit.LB)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("units_heading").performScrollTo().assertTextEquals("Units")
        compose.onNodeWithTag("unit_default_LB").performScrollTo().assertIsSelected().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("unit_default_KG").assertIsNotSelected()
        compose.onNodeWithText("Kilograms (kg)").assertExists()
    }

    @Test
    fun `choosing a unit calls the setter and closes the dialog`() {
        var chosen: WeightUnit? = null
        show(EntryListUiState.Items(rows), onSetWeightUnit = { chosen = it })
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("unit_default_LB").performScrollTo().performClick()
        assertEquals(WeightUnit.LB, chosen)
        compose.onNodeWithText("Pounds (lb)").assertDoesNotExist()
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `testDebugUnitTest --tests "*EntryListViewModelTest*" --tests "*EntryListScreenTest*"`
Expected: compilation fails.

- [ ] **Step 3: Implement**

In `EntryListViewModel` (import `com.mitenko.repkit.domain.model.WeightUnit`), after `setCrashReportsEnabled`:

```kotlin
    /** ⚙ › Units (spec rev 26 §3, plan Spec note 36): the unit new weight workouts start in. KG stands in until it loads. */
    val weightUnitDefault: StateFlow<WeightUnit> =
        preferences.weightUnitDefault.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    fun setWeightUnitDefault(unit: WeightUnit) {
        viewModelScope.launch { preferences.setWeightUnitDefault(unit) }
    }
```

In `EntryListScreen.kt` (imports `com.mitenko.repkit.domain.model.WeightUnit`, `com.mitenko.repkit.ui.common.longLabel`, `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.verticalScroll` if missing):

- `EntryListRoute`: `val weightUnit by vm.weightUnitDefault.collectAsStateWithLifecycle()`, and pass `weightUnit = weightUnit, onSetWeightUnit = vm::setWeightUnitDefault`.
- `EntryListScreen`: add `weightUnit: WeightUnit = WeightUnit.KG, onSetWeightUnit: (WeightUnit) -> Unit = {},` after `onSetCrashReportsEnabled`, and pass to the dialog:

```kotlin
            unit = weightUnit,
            onSelectUnit = { unit ->
                showSettings = false
                onSetWeightUnit(unit)
            },
```

- `SettingsDialog`: add parameters `unit: WeightUnit, onSelectUnit: (WeightUnit) -> Unit`; make its text `Column(Modifier.verticalScroll(rememberScrollState()))`; and between the Appearance group and the divider add:

```kotlin
                // Spec rev 26 §3, plan Spec note 36: the unit new weight workouts start in; saved at once, closing like Appearance.
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.units),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }.testTag("units_heading"),
                    )
                    InfoTag(title = stringResource(R.string.units), text = stringResource(R.string.info_units))
                }
                Column(Modifier.selectableGroup()) {
                    WeightUnit.entries.forEach { UnitOptionRow(it, unit, onSelectUnit) }
                }
```

- Add next to `ThemeOptionRow`:

```kotlin
@Composable
private fun UnitOptionRow(option: WeightUnit, current: WeightUnit, onSelect: (WeightUnit) -> Unit) {
    val selected = option == current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = { onSelect(option) }, role = Role.RadioButton)
            .testTag("unit_default_${option.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(option.longLabel), modifier = Modifier.padding(start = 8.dp))
    }
}
```

Update `SettingsDialog`'s KDoc: "Under Appearance, Units (spec rev 26 §3): kg or lb for new weight workouts, saved at once, closing the dialog."

- [ ] **Step 4: Run them to see them pass**

Run: `testDebugUnitTest --tests "*EntryListViewModelTest*" --tests "*EntryListScreenTest*" --tests "*UntouchedTotalTest*"`
Expected: PASS (the existing Appearance and crash-report tests are unchanged).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/entries/EntryListViewModel.kt app/src/main/kotlin/com/mitenko/repkit/ui/entries/EntryListScreen.kt app/src/test/kotlin/com/mitenko/repkit/ui/entries/EntryListViewModelTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/entries/EntryListScreenTest.kt
git commit -m "Add Units to the app Settings dialog"
```

---

### Task 16: Spec notes, project notes and the full gate

**Files:**
- Modify: `docs/superpowers/specs/2026-10-04-weight-progression-design.md` (§7 and §10)
- Modify: `docs/superpowers/specs/2026-10-05-progression-overrides-design.md` (§6)
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the recorded decisions and a green gate.

- [ ] **Step 1: Record the decisions in the weight spec**

In §7, item 2, append: " PR 2 and PR 3 ship together as 0.2.0 (§10 note 23)." In item 3, replace "Plus the es / zh-rCN / hi translations of every new string (the reviewer sheet regenerated)." with "Its strings ship with their es / zh-rCN / hi translations; PR 2's ship with PR 2 (§10 note 24)."

At the end of §10, after note 22, add:

```markdown

**PR 2 (settings UI, 2026-10-06)**, recorded from `docs/superpowers/plans/2026-10-06-weight-settings-ui.md`. Notes 38–41 record the user's rulings on that plan's open questions.

23. **Release (user, 2026-10-06):** …
```

Write notes 23–47 in full, copied from this plan's "Spec notes" with "plan Spec note" changed to "§10 note" and "open question N" changed to the user's ruling. Don't leave a "…". If the user ruled against a recommendation, write the ruling, and say which task was skipped or changed.

- [ ] **Step 2: Point the overrides spec at the weight rules**

In `2026-10-05-progression-overrides-design.md` §6, append: "Built in weight progression PR 2: see the weight progression spec, §10 notes 30 and 31."

- [ ] **Step 3: Update CLAUDE.md**

- Replace the Weight progression bullet's heading text "(approved, rev 26; PR 1 model and storage built, no UI yet)" with "(approved, rev 26; PR 1 model and storage, PR 2 settings UI; PR 2 and PR 3 ship together as 0.2.0)", and its last sentence with "Its §10 records the PR 1 and PR 2 implementation notes."
- In the Progression overrides bullet, replace "Weight modes (rev 26) must follow the same rule in their PR 2." with "Weight modes follow it too (rev 26 §10 note 30)."
- In "Plans", append: ", `docs/superpowers/plans/2026-10-06-weight-settings-ui.md` (weight progression PR 2: settings UI)".

- [ ] **Step 4: Run the full gate**

Announce it first: the gate runs on a remote worker and takes about 4 minutes.

Run: `assembleDebug testDebugUnitTest lintDebug` (Global Constraints form).
Expected: BUILD SUCCESSFUL, every test green (the file-based `MigrationTestHelper` checks are skipped on Windows), lint at 0 errors and no new warnings in the files this PR touched. MissingTranslation errors mean a key is missing from the data file: add it and regenerate (Task 6 Step 4). A `HardCodedTextGuardTest` failure names the file and line: move the text to a resource, or rename a test-tag argument to `tag`.

Confirm nothing outside the plan changed:

```bash
git status --short     # clean (the main checkout's untracked files aren't in the worktree)
git diff --stat main -- app/src/main/kotlin/com/mitenko/repkit/ui/entry app/src/main/kotlin/com/mitenko/repkit/ui/timer app/src/main/kotlin/com/mitenko/repkit/service app/schemas   # empty: no screen-in-use, timer or schema change
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-04-weight-progression-design.md docs/superpowers/specs/2026-10-05-progression-overrides-design.md CLAUDE.md
git commit -m "Record the weight settings implementation notes in the specs and project notes"
```

Don't push. The PR (squash, rebase onto `main`, `gh pr create`) follows CLAUDE.md's protocol when the user asks. Its description says PR 2 must ship together with PR 3 as 0.2.0.

---

## Self-review

**Spec coverage (§3, §3.1, §6 UI and PR 2's share of §7):**
- Progress by (segmented, three modes) with the Start fresh confirm: Tasks 7, 9, 10, 11.
- Reps mode unchanged: Task 10 (`weight = null` and the Reps branch are today's rows; `ProgressionSettingsScreenTest` unchanged).
- Unit kg | lb with the convert confirm: Tasks 8, 10.
- Weights: Steps (Start / Step / Top, steps 0.5–5) and My weights (rows with ✕, "+ Add weight" = last + last gap): Tasks 2, 7, 10.
- Reps per set (Weight) and the rep range 1–100, min < max (Reps then weight): Tasks 1, 3, 10.
- Starting weight (a picker) and starting reps (Reps then weight): Tasks 7, 10.
- Hold switch and weight holds (weight, reps, for; the rev 16 UI): Tasks 2, 4, 10.
- On-time window and missed-day adjustment, as today: Task 10 (label per open question 4).
- ⓘ on every new row: Task 6 strings, used in Tasks 10, 12, 15.
- Auto-save and validation (valid drafts save, invalid never): Task 8, the §3.1 matrix placed in Task 4; partial edits, duplicates, an unreachable top and a hold on a removed weight: Tasks 3, 4, 8.
- Remap before validation (§3.1 last paragraph): Task 3 (`remapDraft`) and PR 1's `setWeightConfig`.
- The overrides rule (revision 28 §6): Task 3 (draft), Task 5 (the stored current load), Task 8 (notes).
- Current tab: Current weight and Current reps, hold count by position (PR 1's `overwriteCounter`): Task 12.
- App settings ⚙ › Units: Task 15; the default reaches `switchMode`: Tasks 5, 9.
- Strings in en / es / zh-rCN / hi and the data file: Task 6.
- UI tests (§6): the mode switch and its confirm (Tasks 7, 10, 11), each weight-mode section and its errors (Task 10), the ⚙ Units choice (Task 15). The prescription card, chart, timer label and voice are PR 3.

**Placeholder scan:** no TBD or "similar to Task N". Task 16 Step 1 asks the executor to copy notes 23–47 from this plan, whose full text is above.

**Review contracts (notes 42–47):** each has a test in the task that owns it: write order in Task 11 (`TwoDraftsTest`); the unit change in Tasks 5 and 8; the Current edit in Task 12; the single save order in Task 5; the default unit in Tasks 5 and 9; every transition-matrix cell in Tasks 2, 3, 5, 8, 9, 12 and 14.

**Type consistency:** `WeightField`, `WeightMove`, `WeightNote`, `WeightPage`, `WeightEdit`, `WeightValidation`, `WeightHoldField`, `LadderView` and `WeightUnitDefaults` keep one name and shape from the task that produces them to every task that consumes them. `setWeightConfig` returns `WeightMove.CurrentMoved?` in the interface, Room and the fake (Task 5).

**Rough size:** 16 tasks; 8 new main files, 15 changed main files (plus 3 generated locale files and the share's data file), 9 new test files, 11 changed test files, 3 docs.

## Review response (2026-10-06)

Each finding was checked against the code (`EntryDao`, `RoomEntryRepository.setProgression` / `setWeightConfig` / `switchMode` / `overwriteCounter`, `WeightConversion`, `WeightRemap`, `AutoSaver`).

1. **Open questions:** kept open, as the user hasn't ruled. The header now says no task runs until all four are ruled, and Tasks 8, 10, 13 and 14 each name the ruling they depend on.
2. **Write order of the two drafts:** already safe in the code. Each write is a read-modify-write in its own Room transaction (`EntryRepository.setProgression` and `EntryRepository.setWeightConfig`). The column sets are disjoint except `total` and `hold_count`, which are computed from the row read in that transaction (EntryDao.kt:53–58, 95–99). Stated as note 42; tested in Task 11 `TwoDraftsTest` (flush in either order, which is page change and exit; Start fresh; unit change; shared Hold switch).
3. **Unit change:** mostly already handled. One `setWeightConfig` call converts the stored group, remaps and writes in one transaction (`EntryRepository.setWeightConfig`); PR 1 tests the rung and hold count (RoomEntryRepositoryTest:984). The plan didn't state `fresh_start`, history or the invalid-conversion case. Now note 43, with tests in Task 5 (fresh start and history untouched) and Task 8 (an invalid conversion never saves; the store stays in the old unit).
4. **The Current page in Reps then weight:** the code already counts a level change as an integer compare (`counterHoldReset`, in `EntryRepository.overwriteCounter`) and never widens in a weight mode (the same function's `!ladder` check). The level mapping and "the level changed" are now stated as note 44; tested in Task 12 (page mapping plus a Room contract test). `fresh_start` follows note 13, or note 40 if open question 3 is ruled as recommended (and note 49, a final-review fix on top of note 40).
5. **Persisted state after one edit:** already deterministic in the code: sort, convert, remap by value against the old ladder, validate, one UPDATE (`EntryRepository.setWeightConfig`). Now stated as note 45; tested in Task 5 (a stale, unsorted draft and a remapped one store identical rows and return the same move).
6. **When the default unit is read:** already handled. It is read only in `confirmMode` and written only through `switchMode`'s COALESCE (EntryDao.kt:122–126); PR 1 tests the first write (RoomEntryRepositoryTest:1108). The plan now states this as note 46; tested in Task 5 (a unit already set is kept through later switches) and Task 9 (the same through the ViewModel).
7. **Transition matrix:** added as note 47. Every cell names its test; the new `matrix -` Room tests are in Task 5.
8. **Translation guardrail:** Task 6 now ends with a `lintDebug` run on the workers that must show 0 MissingTranslation before Task 7 starts (Step 7).
