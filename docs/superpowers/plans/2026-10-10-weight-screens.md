# REPKIT Weight progression, PR 3 of 3: the screens in use — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a weight-mode Counter look and sound right everywhere it's used: the entry screen's prescription card (with the check-in highlight and TalkBack), the history chart and list tile as weight step lines (only the current mode's points, §9.4), the weight on the timer, the voice ("16 kilos, 10 reps"), and the notification. Also fix the five PR 2 review findings. Every new string ships in en / es / zh-rCN / hi.

**Architecture:**
- **Domain (pure, test-first):** `Workload.kt` gives every screen one answer to "what does the next workout ask for": `setReps` (RepDistributor in Reps mode, the ladder's reps per set in a weight mode), `load`, `setWeight`, `historyMode` and `nextWorkoutReps`. `History.kt` gains the §9.4 mode families (`pointsInMode`), a point's weight in the workout's unit (`weightIn`), and a padded `niceAxis` for hundredths. `TimerController.start` takes the run's `SetWeight`, puts it on each WORK `Cue.PhaseStart` (with `newWeight` on set 1), and `VoiceLine` turns a cue into what the voice says.
- **UI:** `ChartSeries` (Reps or Weights) drives `WorkoutChart`, which draws a step line for weights. `PrescriptionCard` replaces the reps column in weight modes and plays the rev 12 / rev 20 flash. The tile draws steps. The timer shows the weight on the label line. `NotificationText` appends the weight.
- **Fixes:** the highlight's hold lookup uses `engineConfig()` (finding 1); the chart and tile use the mode family (finding 2); `controller.start` gets the weight-mode reps, so `workout_session.repsDone` is sets × reps per set (finding 3); `EntryRow.reps` uses `nextWorkoutReps` (finding 4); Reps and Timer only take the same code paths as today (finding 5, with the proving tests listed below).

**Tech Stack:** Kotlin 2.2.10, Compose Material 3, Hilt, Room 2.8.1 (no schema change: still v7), coroutines, JUnit 4.13.2, Robolectric 4.16 (`@Config(sdk = [34])`). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-04-weight-progression-design.md` (revision 26): §4 (screens in use), §9.3 (display units), §9.4 (which points show; the empty state after a switch), and all of §10, notes 1–49. Notes 22/23: PR 2 and PR 3 ship together as 0.2.0. Also read `2026-10-01-checkin-highlight-design.md` (rev 12), `2026-10-02-hold-highlight-design.md` (rev 20), `2026-10-02-chart-fit-data-design.md` (rev 21), `2026-10-09-hold-from-design.md` (rev 34: weight holds are At only), `2026-10-08-timer-exit-stats-design.md` (rev 33) and `2026-10-10-timer-bigger-centre-design.md` (rev 35: ring 0.95, number 0.44, countdown 0.21), `2026-10-05-crashlytics-design.md` (rev 30: breadcrumbs never carry workout data), the PR 1 and PR 2 plans (`docs/superpowers/plans/2026-10-05-weight-model.md`, `2026-10-06-weight-settings-ui.md`) and `CLAUDE.md`. In this plan `§n` points into revision 26.

## Global Constraints

- **Paths:** main code under `app/src/main/kotlin/com/mitenko/repkit/`, tests under `app/src/test/kotlin/com/mitenko/repkit/`, resources under `app/src/main/res/`. Package `com.mitenko.repkit`. In the task file lists, paths are relative to those roots unless they start with `app/`, `docs/` or `\\`.
- **Worktree and branch:** `D:\Claude\apps\hiit_weightscreens`, branch `feat/weight-screens`, from `main` 7b1d548 (PR 1 and PR 2 merged). Work only there.
- **Builds run remotely, never local gradlew.** Every Gradle run, from Git Bash (note the escaped `\$env:`; quote `--tests` patterns with single quotes):
  `pwsh -NoProfile -Command "\$env:RGRADLE_WORKERS='miten@192.168.1.86,miten@192.168.1.89'; Set-Location D:\Claude\apps\hiit_weightscreens; & D:\Dev\scripts\rgradle.ps1 <tasks>"`
  Below, "Run: `RG <tasks>`" means exactly that command with `<tasks>`, e.g. `RG testDebugUnitTest --tests '*WorkloadTest*'`. Output is tagged `CLAUDE-RGRADLE-<id>`. Announce each run first (targeted runs take about 1–2 minutes, the gate about 4). **Exit code 2 or 3 means no worker: stop and ask the user;** never fall back to a local build. Never run two builds at once.
- **Gate:** `RG assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL, 0 lint errors, 0 MissingTranslation (Task 13).
- **Commits:** author `mitenko <mitenko@gmail.com>` (check `git config user.email` once before the first commit). Plain messages, **no trailers** (no Co-Authored-By, no "Generated with"). Explicit `git add <paths>`, never `-A` or `.`. Don't push.
- **`domain/` is pure Kotlin** (no `android.*`), developed test-first (red, then green).
- **User text comes from resources only.** `HardCodedTextGuardTest` fails on a string literal with three or more letters in `ui/` or `domain/` unless the line is a `testTag(…)`, a log, a `require`/`check`/`error(`/exception message, or a `const val …TAG|KEY|ARG`. Literals like `" · "` and `"$tick"` have no word and pass.
- **Lint MissingTranslation is an error.** Every new English string gets es / zh-rCN / hi in the same commit. The three locale files are **generated** from `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py` by `claude_l10n_gen.py` (Task 3, following PR 2's Task 6). Never hand-edit them.
- **Clock:** time only from the injected `Clock`. **Crash reporting (rev 30):** no new breadcrumb or log may contain a weight, reps or a name.
- **Reps mode and Timer only don't change.** No existing test's assertions change. The only existing-test edits allowed are the ones a task names: `EntryViewModelTest.harness` gains a `runLog` parameter (Task 8) and `FakeCueSpeaker` records `VoiceLine`s while keeping `spoken: List<Int>` (Task 11).
- **Compose tests** are Robolectric: `@RunWith(AndroidJUnit4::class)`, `@Config(sdk = [34])`, `createComposeRule()`, content in `HiitTheme`. Robolectric's default screen is 320 × 470 dp.
- **Kotlin:** backtick test names can't contain `:`.
- **Test source set:** Gradle compiles all of it, so each task's tests reference only code that exists once that task is done. Never leave a later task's red test on disk while running an earlier task.
- **Storage units:** weights are integer hundredths of a unit (2250 = 22.5). A weight-mode `check_in` row has `total` = the level and non-NULL `weight` / `reps` / `unit` (§10 note 6). No Room change in this PR.

## Open questions (each has a recommended answer; the plan implements the recommendation)

**Ruled by the user on 2026-10-11: all five as recommended.**

1. **Where the prescription card goes.** §4 says it replaces the reps column, but the column is 56 dp wide and "112.5 lb" in a large style doesn't fit there. **Recommendation:** in a weight mode the chart takes the row's full width and the card sits full width under it, "16 kg" (headlineLarge) over "8 × 10 reps" (titleMedium). Task 6. (Note 50.)
2. **The timer weight in REST.** Rev 35 fills the ring's open centre: the REST label, the 0.44 number and the 0.21 countdown already use its whole height, so a separate weight line can't fit at those sizes. **Recommendation:** the weight takes the label's line. In WORK, which has no label, it sits alone above the number. In REST it shares the line with "REST" ("REST  16 kg"). Both use titleLarge, so the stack is no taller than today's REST. It isn't shown in PREPARE (§4 names work and the rest preview only), COOLDOWN or DONE. Task 10. (Note 56.)
3. **"Later sets at the same weight".** A run has one weight (per-set weights are out of scope, §8). **Recommendation:** set 1's WORK start says "16 kilos, 10 reps" and sets 2+ say "10 reps". Skipping back into set 1 says the weight again. Task 7. (Note 57.)
4. **`workout_session.repsDone` in a weight mode** (PR 2 finding 3). §10 doesn't cover it. Revision 17 defines it as the sum of the completed sets' reps. **Recommendation:** keep that definition. A weight run's reps are per set, so the value is completed sets × reps per set. No weight column is added (no Room change). Task 8. (Note 59.)
5. **The version.** Notes 22/23 ship PR 2 and PR 3 together as 0.2.0, and PR 2 didn't bump it. **Recommendation:** PR 3 sets `versionName = "0.2.0"` and `versionCode = 2`. Task 13. (Note 64.)

## Spec notes (decisions made while reading the code)

Task 13 appends these to the spec's §10 as notes 50–64, under a "PR 3 (screens in use, 2026-10-10)" line. Each one is binding for this plan.

50. **The prescription card** (open question 1) sits full width under the chart, which takes the full width in a weight mode. It shows "16 kg" (`weight_value`) over "8 × 10 reps" (`prescription_sets_reps`, by reps). TalkBack hears one node, "16 kilograms, 8 sets of 10 reps" (`prescription_desc`). Its tags are `prescription_card`, `prescription_weight` and `prescription_sets`. With no history it still shows, as the reps column does.
51. **The card's highlight** (rev 12 / rev 20 applied to the card). `PrescriptionLayout.change(before, after)` decides on the weight first, then the reps: a heavier weight is UP even with fewer reps (16 kg × 12 → 20 kg × 8), and a lighter one is DOWN. The same load with a positive hold count on an active weight hold is HOLD. `Highlight.card` carries it, and `Highlight.changes` stays empty. The card flashes primary / error / onSurfaceVariant at the rev 12 alpha and fade. It pops to 1.05 (not 1.15, since it's a full-width card) except on HOLD or with reduced motion. While flashing it reports "increased" / "decreased" / "holding". The live region (`load_announcement`) says "Now 16 kilograms, 8 sets of 10 reps" (`load_now`) or "Holding at 16 kg × 10 · 2 of 4" (`load_holding`). A batch 2 review fix, completed in the final review (M2): the card's before-load is captured together with the mode, the unit and the ladder rungs that produced it, and compared against the progression after the check-in; a race (a Weight ↔ Reps-then-weight switch, a kg ↔ lb switch, or a ladder edit) bails without a flash or an announcement.
52. **The hold lookup in a weight mode** (PR 2 finding 1) is `progression.engineConfig().activeHold(level)`, so the weight holds count, as levels, and the Reps holds never do. The announcement names the load as weight × reps in both weight modes; in Weight mode the reps are the fixed reps per set. The Reps-mode path (`progression.activeHold(total)`, the reps column) is unchanged.
53. **Mode families** (§9.4, PR 2 finding 2), in `pointsInMode`. A weight mode shows the points with a weight. Reps mode shows the points with a total and **no** weight: after a switch back, the level-valued weight points no longer join the rep line. The chart's "No check-ins in this mode yet" (`HistoryEmpty.NONE_IN_MODE`) shows when the current family has no points at all. In Reps mode it shows only if some weight point exists, so a Reps history without weights behaves exactly as before (a Counter with only Timer only points still says "No check-ins in this period"). Timer only ignores the mode. "N× this week", the streaks and the tile's check-in count still count every check-in.
54. **The weight chart** plots each point's weight converted from its own unit into the workout's current unit, to the nearest 0.25 (`weightIn`, §9.3). A point without a unit reads as the workout's (§10 note 11). The y axis is `niceAxis(min, max, pad = 100, minMagnitude = 10)` in hundredths: a flat line pads by one unit, the steps are 0.1, 0.2, 0.5, 1, … and the labels use `WeightFormat` (at most one decimal). The line is a step line (flat to the next point's x, then vertical). The tap label reads "23 Sep · 16 kg × 10", the recorded reps with the converted weight. The description reads "2 check-ins, from 14 kg to 16 kg" (`chart_desc_weight`). The x axis keeps rev 21.
55. **The tile** uses the same family, conversion and step line. Its byline and content description (`tile_desc`, a count of every check-in) are unchanged. A Timer only tile reads its mode as REPS (`historyMode`), so its data is as before.
56. **The timer weight** (open question 2). It is frozen by `TimerController.start(reps, weight)` from the check-in's resulting level, because the snapshot is frozen before the check-in. It is null for Timer only and Reps. It is shown in WORK (alone on the label's line) and REST (on the label's line, after "REST"), in titleLarge and the phase colour, tagged `center_weight`. TalkBack appends the spoken weight: "Work, set 2 of 8, 10 reps, 16 kilograms" (`timer_desc_with_weight`). The rev 35 factors are unchanged, and a 320 dp test checks "112.5 lb" in both phases.
57. **The voice** (open question 3, `VoiceLine`). Reps and Timer only say the bare number, as today (`voice_count`). In a weight run, set 1 says "16 kilos, 10 reps" (`voice_load`, `voice_weight_kg` / `voice_weight_lb`, `reps_count`) and later sets say "10 reps". The text is resolved in the voice's own locale, with the English fallback (rev 24). The number uses `WeightFormat`'s "." in every locale, as on screen. The load is spoken only with set 1's WORK cue (or on skipping back into it); if that line is dropped (TTS not ready yet, or Voice switched on after set 1), later sets say only the reps — a possible follow-up is to let `CuePlayer` upgrade the first spoken line.
58. **The notification** appends " · 16 kg" (`notification_with_weight`) in PREPARE, WORK and REST, the phases with a set to do. COOLDOWN and DONE keep today's text.
59. **`workout_session.repsDone`** (open question 4, PR 2 finding 3) is still "the completed sets' reps summed" (rev 17). `EntryViewModel.startWorkout` now passes `Entry.setReps(level)` to `controller.start`, so a weight run sums sets × reps per set instead of a split of the level. `workout_session` gains no weight column.
60. **`EntryRow.reps`** (PR 2 finding 4) is `Entry.nextWorkoutReps()`: the total in Reps mode and for Timer only (as before), and sets × reps per set in a weight mode. No screen shows it (rev 9).
61. **Timer only is untouched.** `Entry.setReps` keeps `RepDistributor` for Timer only whatever its stored mode, `load` / `setWeight` are null for it, and `historyMode` is REPS.
62. **Plural counts for decimal weights.** Android plurals take an `Int`, so `WeightFormat.pluralCount` passes a whole weight as itself, a fraction below 1 as 0 and any other fraction as 2. For en / es / zh / hi that picks CLDR's category for the decimal: "1 kilo", "1.5 kilos", and Hindi "0.5 किलो" (one).
63. **`info_voice` is unchanged** ("…says the number of reps to do…"). It's still true. A weight-specific ⓘ waits for the user's planned rewrite of the info texts.
64. **Version** (open question 5): `versionName` 0.2.0, `versionCode` 2.

## Reps mode and Timer only are unchanged (PR 2 finding 5)

The code keeps the old path for both. Reps mode goes through `RepDistributor.distribute`, `progression.activeHold(total)`, the reps column, `ChartSeries.Reps` (the same line, axis and label) and a `Cue.PhaseStart` with no weight. Timer only goes through `setReps` → `RepDistributor`, a null load and REPS as its history mode. These existing tests, with their assertions untouched, prove it. Each must stay green at the gate.

- `ui/entry/EntryViewModelTest`: every test, especially `the state shows the entry's distributed reps…`, `a successful start freezes the snapshot…` (the snapshot is unchanged), `Start on a Timer only entry runs the timer with countsReps false…`, `a Timer only entry checks in without changing its total`, and all the highlight tests (`check in on a Counter entry emits a highlight…` through `a sets change mid check-in bails…`, including `inside a From range the hold announcement names the total`).
- `ui/entry/EntryScreenTest`: the reps column, highlight and announcement tests, `tapping a point shows its date and reps…` ("23 Sep · 62"), `the range switch changes the point set`, `the empty states say…`, and both Timer only tests.
- `ui/entry/HistoryLayoutTest` (all, including `the point label reads day month and reps` and `…a workout shows only points with a total`), `ui/entry/RepsColumnLayoutTest`, `domain/HistoryTest` (the three `niceAxis …` tests).
- `ui/entries/TileLayoutTest` (all) and `ui/entries/EntryListViewModelTest` (`items show the name, the next total…` with `EntryRow(1, "Burpees", 65, true)`, `a Timer only row…`, `a row carries the tile window's…`, and `a check-in updates the row's count and tile at once` with `TileData(count = 1, …)`).
- `ui/timer/TimerUiMapperTest`, `ui/timer/TimerScreenTest` (including rev 35's `label, number and countdown fit inside the ring…` and rev 33's 320 dp stats test), `ui/timer/TimerViewModelTest`.
- `domain/TimerControllerTest` (`cues are one-shot…` compares `Cue.PhaseStart(Phase.WORK, reps = 8)`; `each work start carries its own set's reps…`; `a Timer only run carries the countdown…`), `domain/RunSummaryTest`, `data/SessionRecorderTest`.
- `service/CuePlayerTest` (`listOf(12)` and `listOf(9)` through `FakeCueSpeaker.spoken`), `platform/VoiceLanguageTest`, `service/NotificationTextTest`.

## File structure

| File | Status | Responsibility |
| --- | --- | --- |
| `domain/model/Weights.kt` | modify | `SetWeight(weight, unit)` |
| `domain/Workload.kt` | create | `setReps`, `Entry.setReps`, `Entry.load`, `Entry.setWeight`, `Entry.historyMode`, `Entry.nextWorkoutReps` |
| `domain/WeightFormat.kt` | modify | `pluralCount` |
| `domain/History.kt` | modify | `pointsInMode`, `CheckInPoint.weightIn`, `niceAxis(pad, minMagnitude)` |
| `domain/model/Cue.kt` | modify | `PhaseStart.weight`, `PhaseStart.newWeight` |
| `domain/TimerController.kt` | modify | `weight`, `start(reps, weight)`, the weight on WORK cues |
| `domain/VoiceLine.kt` | create | `VoiceLine` (Count / Reps / Load), `VoiceLine.of(cue)` |
| `res/values/strings.xml` (+ generated es / zh-rCN / hi) | modify | 16 new strings and plurals |
| `ui/common/WeightTexts.kt` | modify | `loadText`, `spokenWeight`, `prescriptionDescription` |
| `ui/common/PlotPoint.kt` | modify | `stepVertices` |
| `ui/entry/ChartSeries.kt` | create | `ChartSeries.Reps` / `.Weights`: values, axis, tick labels, the load label |
| `ui/entry/HistoryLayout.kt` | modify | `shownPoints(…, mode)`, `empty(…, mode)`, `NONE_IN_MODE`, `series` |
| `ui/entry/WorkoutChart.kt` | modify | draws a `ChartSeries`: step line, weight ticks, weight label and description |
| `ui/entry/PrescriptionLayout.kt` | create | `PrescriptionLayout.change` |
| `ui/entry/EntryScreen.kt` | modify | `ChartArea`, `PrescriptionCard`, the card's announcement |
| `ui/entry/EntryViewModel.kt` | modify | `EntryUiState.mode/unit/load`, `Highlight.card`, the weight-mode highlight, Start with the load |
| `ui/entries/TileLayout.kt`, `TileGraphic.kt`, `EntryListViewModel.kt` | modify | the weight family and steps; `EntryRow.reps` |
| `ui/timer/TimerUiMapper.kt`, `TimerViewModel.kt`, `TimerScreen.kt` | modify | `TimerUiState.weight`, the label line |
| `platform/CueSpeaker.kt`, `VoiceLanguage.kt`, `AndroidCueSpeaker.kt`, `service/CuePlayer.kt` | modify | speak a `VoiceLine` |
| `service/NotificationText.kt`, `WorkoutNotifications.kt`, `TimerService.kt` | modify | the weight in the title |
| `app/build.gradle.kts` | modify | 0.2.0 / 2 |
| `docs/superpowers/specs/2026-10-04-weight-progression-design.md`, `CLAUDE.md` | modify | notes 50–64, project notes |

---

### Task 1: What the next workout asks for (domain)

**Files:**
- Modify: `domain/model/Weights.kt`, `domain/WeightFormat.kt`
- Create: `domain/Workload.kt`
- Test: create `domain/WorkloadTest.kt`; modify `domain/WeightFormatTest.kt`

**Interfaces:**
- Consumes: PR 1's `ProgressionConfig.scale()`, `loadAt(level)`, `Prescription.Load`, `RepDistributor`.
- Produces:
  - `data class SetWeight(val weight: Int, val unit: WeightUnit)` in `domain.model`;
  - in `domain`: `fun ProgressionConfig.setReps(level: Int, sets: Int): List<Int>`, `fun Entry.setReps(level: Int = counter.total): List<Int>`, `fun Entry.load(level: Int = counter.total): Prescription.Load?`, `fun Entry.setWeight(level: Int = counter.total): SetWeight?`, `val Entry.historyMode: ProgressMode`, `fun Entry.nextWorkoutReps(): Int`;
  - `WeightFormat.pluralCount(hundredths: Int): Int`.

- [ ] **Step 1: Write the failing tests**

Create `domain/WorkloadTest.kt`:

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.TimingConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.testutil.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkloadTest {
    /** The curls ladder from the spec's simulation: 8 / 10 / 12 / 14 / 16 kg, 10 reps per set, 8–12 reps. */
    private val curls = WeightConfig(
        unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600),
        repsPerSet = 10, repMin = 8, repMax = 12,
    )
    private val weight = ProgressionConfig(mode = ProgressMode.WEIGHT, weight = curls)
    private val repsThenWeight = ProgressionConfig(mode = ProgressMode.REPS_THEN_WEIGHT, weight = curls)
    private val three = TimingConfig(sets = 3)

    @Test
    fun `Reps mode splits the total exactly as RepDistributor does`() {
        assertEquals(RepDistributor.distribute(65, 8), ProgressionConfig().setReps(65, 8))
        val e = testEntry(1, counter = CounterState(total = 65))
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), e.setReps())
        assertNull(e.load())
        assertNull(e.setWeight())
        assertEquals(65, e.nextWorkoutReps())
        assertEquals(ProgressMode.REPS, e.historyMode)
    }

    @Test
    fun `a weight mode repeats the level's reps per set`() {
        assertEquals(listOf(10, 10, 10), weight.setReps(4, 3))
        // Reps then weight: level 22 is 16 kg (22 / 5 = 4) × 10 (8 + 22 % 5).
        assertEquals(listOf(10, 10, 10), repsThenWeight.setReps(22, 3))
        val e = testEntry(1, timing = three, progression = repsThenWeight, counter = CounterState(total = 22))
        assertEquals(Prescription.Load(1600, 10), e.load())
        assertEquals(SetWeight(1600, WeightUnit.KG), e.setWeight())
        assertEquals(SetWeight(1400, WeightUnit.KG), e.setWeight(level = 15))
        assertEquals(30, e.nextWorkoutReps())
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, e.historyMode)
    }

    @Test
    fun `a weight workout with no unit reads as kg`() {
        val e = testEntry(1, progression = weight.copy(weight = curls.copy(unit = null)), counter = CounterState(total = 0))
        assertEquals(SetWeight(800, WeightUnit.KG), e.setWeight())
    }

    @Test
    fun `Timer only keeps today's split and has no load, whatever its stored mode`() {
        val e = testEntry(1, timing = three, progression = weight, counter = CounterState(total = 4), type = EntryType.CHECK_IN)
        assertEquals(RepDistributor.distribute(4, 3), e.setReps())
        assertNull(e.load())
        assertNull(e.setWeight())
        assertEquals(4, e.nextWorkoutReps())
        assertEquals(ProgressMode.REPS, e.historyMode)
    }
}
```

Append inside the class in `domain/WeightFormatTest.kt`:

```kotlin
    @Test
    fun `a weight's plural count is the whole number, 0 below 1 and 2 for any other fraction`() {
        assertEquals(1, WeightFormat.pluralCount(100))
        assertEquals(16, WeightFormat.pluralCount(1600))
        assertEquals(0, WeightFormat.pluralCount(50))
        assertEquals(2, WeightFormat.pluralCount(150))
        assertEquals(2, WeightFormat.pluralCount(2250))
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*WorkloadTest*' --tests '*WeightFormatTest*'`
Expected: compilation fails (`SetWeight`, `setReps`, `pluralCount` unresolved).

- [ ] **Step 3: Implement**

Append to `domain/model/Weights.kt`:

```kotlin

/**
 * A run's weight (spec rev 26 §4): [weight] in hundredths of [unit]. TimerController holds it for the
 * timer, the voice and the notification; it is frozen from the load the Start check-in left.
 */
data class SetWeight(val weight: Int, val unit: WeightUnit)
```

Add to `object WeightFormat` in `domain/WeightFormat.kt`, after `parse`:

```kotlin

    /**
     * The count a weight's plural takes (plan Spec note 62). Android plurals take an Int, so a whole
     * weight is its own count, a fraction below 1 is 0 and any other fraction is 2. For en / es / zh / hi
     * that picks exactly CLDR's category for the decimal: "1 kilo", "1.5 kilos", Hindi "0.5" one.
     */
    fun pluralCount(hundredths: Int): Int = when {
        hundredths % 100 == 0 -> hundredths / 100
        hundredths < 100 -> 0
        else -> 2
    }
```

Create `domain/Workload.kt`:

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.WeightUnit

/*
 * What the next workout asks for (spec rev 26 §4), one answer for the entry screen, the list, the timer
 * and the session log. Pure.
 */

/** The reps of each of [sets] sets at [level]: Reps mode splits the total (RepDistributor), a weight mode repeats the level's reps per set. */
fun ProgressionConfig.setReps(level: Int, sets: Int): List<Int> = when (val s = scale()) {
    is ProgressionScale.Reps -> RepDistributor.distribute(level, sets)
    is ProgressionScale.Ladder -> List(sets) { s.prescription(level).reps }
}

/**
 * This entry's sets at [level]. A Counter follows its mode ([setReps]); Timer only keeps today's split,
 * which nothing shows (plan Spec note 61).
 */
fun Entry.setReps(level: Int = counter.total): List<Int> =
    if (type == EntryType.WORKOUT) progression.setReps(level, timing.sets) else RepDistributor.distribute(level, timing.sets)

/** A Counter's load at [level] in a weight mode; null in Reps mode and for Timer only. */
fun Entry.load(level: Int = counter.total): Prescription.Load? = if (type == EntryType.WORKOUT) progression.loadAt(level) else null

/** The run's weight at [level] (spec rev 26 §4) in the workout's unit; KG stands in for a missing unit (§10 note 11). */
fun Entry.setWeight(level: Int = counter.total): SetWeight? =
    load(level)?.let { SetWeight(it.weight, progression.weight.unit ?: WeightUnit.KG) }

/** The history family the chart and the tile show (spec rev 26 §9.4): a Counter's mode; Timer only reads as REPS. */
val Entry.historyMode: ProgressMode
    get() = if (type == EntryType.WORKOUT) progression.mode else ProgressMode.REPS

/** The next workout's reps (EntryRow.reps, plan Spec note 60): the total in Reps mode and for Timer only; sets × reps per set in a weight mode. */
fun Entry.nextWorkoutReps(): Int = if (load() != null) setReps().sum() else counter.total
```

- [ ] **Step 4: Run them to see them pass**

Run: `RG testDebugUnitTest --tests '*WorkloadTest*' --tests '*WeightFormatTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/model/Weights.kt app/src/main/kotlin/com/mitenko/repkit/domain/WeightFormat.kt app/src/main/kotlin/com/mitenko/repkit/domain/Workload.kt app/src/test/kotlin/com/mitenko/repkit/domain/WorkloadTest.kt app/src/test/kotlin/com/mitenko/repkit/domain/WeightFormatTest.kt
git commit -m "Add the next workout's reps, load and weight helpers"
```

---

### Task 2: Mode families, point weights and a weight axis (domain)

**Files:**
- Modify: `domain/History.kt`
- Test: modify `domain/HistoryTest.kt`

**Interfaces:**
- Consumes: `WeightConversion.convert(hundredths, from, to)` (PR 1).
- Produces: `fun pointsInMode(points: List<CheckInPoint>, mode: ProgressMode): List<CheckInPoint>`, `fun CheckInPoint.weightIn(to: WeightUnit): Int?`, `fun niceAxis(min: Int, max: Int, pad: Int = 2, minMagnitude: Int = 1): Axis`.

- [ ] **Step 1: Write the failing tests**

Add these imports to `domain/HistoryTest.kt` if they're missing: `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightUnit`, `org.junit.Assert.assertNull`. Append inside the class:

```kotlin
    @Test
    fun `pointsInMode keeps the weight points in a weight mode and the weightless totals in Reps mode`() {
        val at = Instant.parse("2026-09-20T16:00:00Z")
        val reps = CheckInPoint(at, 60)
        val timerOnly = CheckInPoint(at, null)
        val load = CheckInPoint(at, 3, weight = 1400, reps = 10, unit = WeightUnit.KG)
        val all = listOf(reps, timerOnly, load)
        assertEquals(listOf(reps), pointsInMode(all, ProgressMode.REPS))
        assertEquals(listOf(load), pointsInMode(all, ProgressMode.WEIGHT))
        assertEquals(listOf(load), pointsInMode(all, ProgressMode.REPS_THEN_WEIGHT))
    }

    @Test
    fun `a point's weight converts from its own unit to the workout's, to the nearest quarter`() {
        val at = Instant.parse("2026-09-20T16:00:00Z")
        assertEquals(1600, CheckInPoint(at, 4, 1600, 10, WeightUnit.KG).weightIn(WeightUnit.KG))
        assertEquals(1600, CheckInPoint(at, 4, 3500, 10, WeightUnit.LB).weightIn(WeightUnit.KG)) // 35 lb = 15.88 kg
        assertEquals(3525, CheckInPoint(at, 4, 1600, 10, WeightUnit.KG).weightIn(WeightUnit.LB)) // 16 kg = 35.27 lb
        assertEquals(1600, CheckInPoint(at, 4, 1600, 10, null).weightIn(WeightUnit.LB)) // no recorded unit: the workout's
        assertNull(CheckInPoint(at, 60).weightIn(WeightUnit.KG))
    }

    @Test
    fun `niceAxis takes a padding and a smallest step for weights in hundredths`() {
        assertEquals(Axis(1900, 2100, 50), niceAxis(2000, 2000, pad = 100, minMagnitude = 10))
        assertEquals(Axis(800, 1600, 200), niceAxis(800, 1600, pad = 100, minMagnitude = 10))
        assertEquals(Axis(2000, 2300, 100), niceAxis(2000, 2250, pad = 100, minMagnitude = 10))
        assertEquals(Axis(0, 150, 50), niceAxis(50, 50, pad = 100, minMagnitude = 10))
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*domain.HistoryTest*'`
Expected: compilation fails (`pointsInMode`, `weightIn`, the `pad` parameter unresolved).

- [ ] **Step 3: Implement**

In `domain/History.kt`, add the imports `com.mitenko.repkit.domain.model.ProgressMode` and `com.mitenko.repkit.domain.model.WeightUnit`. Replace `niceAxis` (its KDoc and body) with:

```kotlin
/**
 * Rounded bounds with 3–5 ticks (spec R6 §3.4): the smallest step of [minMagnitude] × 1, 2, 5, 10, 20,
 * 50, … whose rounded-out bounds need at most 5 ticks, extended upwards to at least 3. Equal values pad
 * to [min] − [pad] .. [max] + [pad], never below 0. Rep totals use the defaults; weights in hundredths
 * pad by one unit, with steps from 0.1 (plan Spec note 54).
 */
fun niceAxis(min: Int, max: Int, pad: Int = 2, minMagnitude: Int = 1): Axis {
    require(min <= max) { "min $min > max $max" }
    val lo = if (min == max) maxOf(0, min - pad) else min
    val hi = if (min == max) max + pad else max
    var magnitude = minMagnitude
    while (true) {
        for (mantissa in NICE_MANTISSAS) {
            val step = mantissa * magnitude
            val a = Math.floorDiv(lo, step) * step
            var b = -Math.floorDiv(-hi, step) * step
            if ((b - a) / step + 1 > MAX_TICKS) continue
            while ((b - a) / step + 1 < MIN_TICKS) b += step
            return Axis(a, b, step)
        }
        magnitude *= 10
    }
}
```

Append to the file:

```kotlin

/**
 * A Counter's points in its mode family (spec rev 26 §9.4, plan Spec note 53): a weight mode keeps the
 * points with a weight; Reps mode keeps the points with a total and no weight. The other family stays
 * stored and comes back on a switch back.
 */
fun pointsInMode(points: List<CheckInPoint>, mode: ProgressMode): List<CheckInPoint> =
    if (mode.usesWeights) points.filter { it.weight != null } else points.filter { it.total != null && it.weight == null }

/**
 * This point's weight in [to] (spec rev 26 §9.3): converted from the unit it was recorded in, to the
 * nearest 0.25. A weight with no recorded unit reads as [to] (§10 note 11). Null without a weight.
 */
fun CheckInPoint.weightIn(to: WeightUnit): Int? = weight?.let { WeightConversion.convert(it, unit ?: to, to) }
```

- [ ] **Step 4: Run them to see them pass**

Run: `RG testDebugUnitTest --tests '*domain.HistoryTest*'`
Expected: PASS, including the three existing `niceAxis` tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/History.kt app/src/test/kotlin/com/mitenko/repkit/domain/HistoryTest.kt
git commit -m "Add history mode families, point weights in a unit and a padded weight axis"
```

---

### Task 3: Strings, translations and the weight screen texts

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify (generated, never by hand): `app/src/main/res/values-es/strings.xml`, `values-zh-rCN/strings.xml`, `values-hi/strings.xml`
- Modify: `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py` (outside the repo)
- Modify: `ui/common/WeightTexts.kt`
- Test: modify `ui/common/WeightTextsTest.kt`

**Interfaces:**
- Consumes: `WeightFormat.pluralCount` (Task 1), `weightText` (PR 2), `Prescription.Load`.
- Produces:
  - the resources `history_empty_mode`, `chart_desc_weight` (plurals), `load_value`, `prescription_sets_reps` (plurals), `reps_count` (plurals), `sets_of_reps` (plurals), `weight_spoken_kg` / `weight_spoken_lb` (plurals), `prescription_desc`, `load_now`, `load_holding`, `timer_desc_with_weight`, `notification_with_weight`, `voice_weight_kg` / `voice_weight_lb` (plurals), `voice_load`;
  - in `ui/common/WeightTexts.kt`: `fun loadText(load: Prescription.Load, unit: WeightUnit?): UiText`, `fun spokenWeight(hundredths: Int, unit: WeightUnit): UiText`, `fun prescriptionDescription(load: Prescription.Load, sets: Int, unit: WeightUnit?): UiText`.

- [ ] **Step 1: Write the failing tests**

Add the import `com.mitenko.repkit.domain.Prescription` to `ui/common/WeightTextsTest.kt` and append inside the class:

```kotlin
    @Test
    fun `loads, spoken weights and the prescription read in English`() {
        assertEquals("16 kg × 10", loadText(Prescription.Load(1600, 10), WeightUnit.KG).resolve(en))
        assertEquals("16 kilograms", spokenWeight(1600, WeightUnit.KG).resolve(en))
        assertEquals("1 kilogram", spokenWeight(100, WeightUnit.KG).resolve(en))
        assertEquals("22.5 pounds", spokenWeight(2250, WeightUnit.LB).resolve(en))
        assertEquals("16 kilograms, 3 sets of 10 reps", prescriptionDescription(Prescription.Load(1600, 10), 3, WeightUnit.KG).resolve(en))
        assertEquals("1 pound, 1 set of 1 rep", prescriptionDescription(Prescription.Load(100, 1), 1, WeightUnit.LB).resolve(en))
    }

    @Test
    fun `the prescription is translated`() {
        val load = Prescription.Load(1600, 10)
        assertEquals("16 kilogramos, 3 series de 10 repeticiones", prescriptionDescription(load, 3, WeightUnit.KG).resolve(res("es")))
        assertEquals("16 公斤，3 组，每组 10 次", prescriptionDescription(load, 3, WeightUnit.KG).resolve(res("zh-CN")))
        assertEquals("16 किलोग्राम, 3 सेट, हर सेट में 10 रेप्स", prescriptionDescription(load, 3, WeightUnit.KG).resolve(res("hi")))
    }

    @Test
    fun `the screen texts resolve in every language with no format left over`() {
        listOf("en", "es", "zh-CN", "hi").map(::res).forEach { r ->
            listOf(
                loadText(Prescription.Load(2250, 8), WeightUnit.LB),
                spokenWeight(50, WeightUnit.KG),
                spokenWeight(1600, WeightUnit.LB),
                prescriptionDescription(Prescription.Load(1600, 1), 1, WeightUnit.KG),
            ).forEach {
                val text = it.resolve(r)
                assertTrue(text, text.isNotBlank())
                assertFalse(text, text.contains('%'))
            }
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*WeightTextsTest*'`
Expected: compilation fails (`loadText`, `spokenWeight`, `prescriptionDescription` unresolved).

- [ ] **Step 3: Add the English strings**

Append before `</resources>` in `app/src/main/res/values/strings.xml`:

```xml

    <!-- Weight progression in use (spec rev 26 §4, PR 3) -->
    <string name="history_empty_mode">No check-ins in this mode yet</string>
    <!-- %1$d check-ins; %2$s and %3$s the first and last weights with their unit, e.g. 14 kg -->
    <plurals name="chart_desc_weight">
        <item quantity="one">%1$d check-in, from %2$s to %3$s</item>
        <item quantity="other">%1$d check-ins, from %2$s to %3$s</item>
    </plurals>
    <!-- A load: %1$s is a weight with its unit, %2$d the reps per set, e.g. 16 kg × 10 -->
    <string name="load_value">%1$s × %2$d</string>
    <!-- The prescription card's second line: %1$d sets × %2$d reps per set; the quantity is the reps -->
    <plurals name="prescription_sets_reps">
        <item quantity="one">%1$d × %2$d rep</item>
        <item quantity="other">%1$d × %2$d reps</item>
    </plurals>
    <!-- A number of reps, for TalkBack and the Voice cue -->
    <plurals name="reps_count">
        <item quantity="one">%1$d rep</item>
        <item quantity="other">%1$d reps</item>
    </plurals>
    <!-- %1$d sets; %2$s is reps_count, e.g. 10 reps -->
    <plurals name="sets_of_reps">
        <item quantity="one">%1$d set of %2$s</item>
        <item quantity="other">%1$d sets of %2$s</item>
    </plurals>
    <!-- A weight for TalkBack; %1$s is a number such as 22.5 -->
    <plurals name="weight_spoken_kg">
        <item quantity="one">%1$s kilogram</item>
        <item quantity="other">%1$s kilograms</item>
    </plurals>
    <plurals name="weight_spoken_lb">
        <item quantity="one">%1$s pound</item>
        <item quantity="other">%1$s pounds</item>
    </plurals>
    <!-- What TalkBack reads for the prescription card: %1$s the spoken weight, %2$s sets_of_reps -->
    <string name="prescription_desc">%1$s, %2$s</string>
    <!-- After a check-in moves the load: %1$s is prescription_desc -->
    <string name="load_now">Now %1$s</string>
    <!-- After a check-in that stays on a weight hold: %1$s is load_value; %2$d of %3$d the hold's check-ins -->
    <string name="load_holding">Holding at %1$s · %2$d of %3$d</string>
    <!-- Timer TalkBack: %1$s is the centre's description, %2$s the spoken weight -->
    <string name="timer_desc_with_weight">%1$s, %2$s</string>
    <!-- Notification title with the run's weight: %1$s the title, %2$s the weight with its unit -->
    <string name="notification_with_weight">%1$s · %2$s</string>
    <!-- What the Voice cue says for a weight; %1$s is a number such as 22.5 -->
    <plurals name="voice_weight_kg">
        <item quantity="one">%1$s kilo</item>
        <item quantity="other">%1$s kilos</item>
    </plurals>
    <plurals name="voice_weight_lb">
        <item quantity="one">%1$s pound</item>
        <item quantity="other">%1$s pounds</item>
    </plurals>
    <!-- The Voice cue at a weight run's first set: %1$s voice_weight_*, %2$s reps_count, e.g. 16 kilos, 10 reps -->
    <string name="voice_load">%1$s, %2$s</string>
```

- [ ] **Step 4: Add the translations and generate the three locale files**

Append to `\\DEVMONSTER\ethor\claude_share\repkit-l10n\claude_l10n_data.py`, at the very end (after PR 2's weight-settings block). The wording follows the file's glossary and its reviewed `reps_holding` forms:
- **Spanish:** "mantener / manteniendo" for a hold (never "pausa"), "registro" for a check-in, "repeticiones" / "series".
- **Chinese:** "打卡", "次", "组", and "保持在 … · 第 N/M 次打卡".
- **Hindi:** "रेप / रेप्स", "सेट", "चेक-इन", and "… पर होल्ड · N/M".

The block runs after the Hindi replace loop, so its text is already final. `P(es_one, es_other, zh, hi_one, hi_other)` is the file's plural helper; the generator adds Spanish "many".

```python
# --- Weight progression in use (spec rev 26 PR 3, feat/weight-screens, 2026-10-10) ---
T.update({
 "history_empty_mode": ("Aún no hay registros en este modo", "此模式下还没有打卡", "इस मोड में अभी कोई चेक-इन नहीं"),
 "chart_desc_weight": P("%1$d registro, de %2$s a %3$s", "%1$d registros, de %2$s a %3$s", "%1$d 次打卡，从 %2$s 到 %3$s", "%1$d चेक-इन, %2$s से %3$s तक", "%1$d चेक-इन, %2$s से %3$s तक"),
 "load_value": ("%1$s × %2$d", "%1$s × %2$d", "%1$s × %2$d"),
 "prescription_sets_reps": P("%1$d × %2$d repetición", "%1$d × %2$d repeticiones", "%1$d × %2$d 次", "%1$d × %2$d रेप", "%1$d × %2$d रेप्स"),
 "reps_count": P("%1$d repetición", "%1$d repeticiones", "%1$d 次", "%1$d रेप", "%1$d रेप्स"),
 "sets_of_reps": P("%1$d serie de %2$s", "%1$d series de %2$s", "%1$d 组，每组 %2$s", "%1$d सेट, हर सेट में %2$s", "%1$d सेट, हर सेट में %2$s"),
 "weight_spoken_kg": P("%1$s kilogramo", "%1$s kilogramos", "%1$s 公斤", "%1$s किलोग्राम", "%1$s किलोग्राम"),
 "weight_spoken_lb": P("%1$s libra", "%1$s libras", "%1$s 磅", "%1$s पाउंड", "%1$s पाउंड"),
 "prescription_desc": ("%1$s, %2$s", "%1$s，%2$s", "%1$s, %2$s"),
 "load_now": ("Ahora %1$s", "现在：%1$s", "अब %1$s"),
 "load_holding": ("Manteniendo en %1$s · %2$d de %3$d", "保持在 %1$s · 第 %2$d/%3$d 次打卡", "%1$s पर होल्ड · %2$d/%3$d"),
 "timer_desc_with_weight": ("%1$s, %2$s", "%1$s，%2$s", "%1$s, %2$s"),
 "notification_with_weight": ("%1$s · %2$s", "%1$s · %2$s", "%1$s · %2$s"),
 "voice_weight_kg": P("%1$s kilo", "%1$s kilos", "%1$s 公斤", "%1$s किलो", "%1$s किलो"),
 "voice_weight_lb": P("%1$s libra", "%1$s libras", "%1$s 磅", "%1$s पाउंड", "%1$s पाउंड"),
 "voice_load": ("%1$s, %2$s", "%1$s，%2$s", "%1$s, %2$s"),
})
```

Then, in Git Bash (the generator reads `app/src/main/res/values/strings.xml` relative to the current directory; write the CSV to your session scratchpad):

```bash
cd /d/Claude/apps/hiit_weightscreens
python '//DEVMONSTER/ethor/claude_share/repkit-l10n/claude_l10n_gen.py' '//DEVMONSTER/ethor/claude_share/repkit-l10n' "<your session scratchpad>/repkit-translations-review.csv"
```

Expected: three `wrote app/src/main/res/values-…/strings.xml` lines and a `wrote …csv` line. An `untranslated keys: …` exit means a key above is missing or misspelt: fix the data file and rerun.

Check that only lines were added. If an existing line changed or was removed, the share's data has drifted from `main`: stop and ask the user.

```bash
git diff -U0 app/src/main/res/values-es app/src/main/res/values-zh-rCN app/src/main/res/values-hi | grep '^-' | grep -v '^---'   # prints nothing
```

Compare the scratchpad CSV with `\\DEVMONSTER\ethor\claude_share\repkit-l10n\repkit-translations-review.csv`:
- **Only the new keys' rows differ:** first copy the share's CSV to `repkit-translations-review.before-weightscreens.csv` beside it. Then copy the new CSV over the share's, so the reviewers see the new rows.
- **Anything else differs:** leave the share's CSV alone and tell the user it needs a merge.

- [ ] **Step 5: Implement the texts**

Add the import `com.mitenko.repkit.domain.Prescription` to `ui/common/WeightTexts.kt` and append:

```kotlin

/** "16 kg × 10" (spec rev 26 §4): a load as the chart's tap label and the hold announcement show it. */
fun loadText(load: Prescription.Load, unit: WeightUnit?): UiText =
    UiText.Res(R.string.load_value, listOf(weightText(load.weight, unit), load.reps))

/** "16 kilograms" for TalkBack (spec rev 26 §4); the count follows plan Spec note 62. */
fun spokenWeight(hundredths: Int, unit: WeightUnit): UiText = UiText.Plural(
    when (unit) {
        WeightUnit.KG -> R.plurals.weight_spoken_kg
        WeightUnit.LB -> R.plurals.weight_spoken_lb
    },
    WeightFormat.pluralCount(hundredths),
    listOf(WeightFormat.format(hundredths)),
)

/** What TalkBack reads for the prescription card (spec rev 26 §4): "16 kilograms, 3 sets of 10 reps". */
fun prescriptionDescription(load: Prescription.Load, sets: Int, unit: WeightUnit?): UiText = UiText.Res(
    R.string.prescription_desc,
    listOf(
        spokenWeight(load.weight, unit ?: WeightUnit.KG),
        UiText.Plural(R.plurals.sets_of_reps, sets, listOf(sets, UiText.Plural(R.plurals.reps_count, load.reps))),
    ),
)
```

- [ ] **Step 6: Run the tests and lint**

Run: `RG testDebugUnitTest --tests '*WeightTextsTest*' --tests '*HardCodedTextGuardTest*' lintDebug`
Expected: PASS; lint reports 0 errors and no MissingTranslation.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-hi/strings.xml app/src/main/kotlin/com/mitenko/repkit/ui/common/WeightTexts.kt app/src/test/kotlin/com/mitenko/repkit/ui/common/WeightTextsTest.kt
git commit -m "Add the weight screen strings in four languages and their texts"
```

The data file and the reviewer CSV live on the share, outside the repo, so they aren't committed.

---

### Task 4: A step line, the chart series and the mode families on the entry screen (pure)

**Files:**
- Modify: `ui/common/PlotPoint.kt`, `ui/entry/HistoryLayout.kt`
- Create: `ui/entry/ChartSeries.kt`
- Test: create `ui/common/PlotPointTest.kt`; modify `ui/entry/HistoryLayoutTest.kt`

**Interfaces:**
- Consumes: `pointsInMode`, `weightIn`, `niceAxis(pad, minMagnitude)` (Task 2), `loadText` (Task 3).
- Produces:
  - `fun stepVertices(points: List<PlotPoint>): List<PlotPoint>`;
  - `sealed interface ChartSeries { val points: List<CheckInPoint>; val values: List<Int>; val stepped: Boolean; fun axis(): Axis; fun tickLabel(tick: Int): String }`, with `data class Reps(points)` and `data class Weights(points, unit: WeightUnit)`; `Weights` adds `fun load(index: Int): UiText` and `fun valueText(hundredths: Int): UiText`;
  - `HistoryEmpty.NONE_IN_MODE`; `HistoryLayout.shownPoints(view, type, mode: ProgressMode = ProgressMode.REPS)`; `HistoryLayout.empty(all, shown, mode: ProgressMode? = null)`; `HistoryLayout.series(points, mode, unit: WeightUnit?): ChartSeries`.

- [ ] **Step 1: Write the failing tests**

Create `ui/common/PlotPointTest.kt`:

```kotlin
package com.mitenko.repkit.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PlotPointTest {
    @Test
    fun `a step line runs flat to each next point's x, then up or down to it`() {
        val a = PlotPoint(0f, 10f)
        val b = PlotPoint(5f, 4f)
        val c = PlotPoint(9f, 4f)
        assertEquals(listOf(a, PlotPoint(5f, 10f), b, PlotPoint(9f, 4f), c), stepVertices(listOf(a, b, c)))
        assertEquals(listOf(a), stepVertices(listOf(a)))
        assertEquals(emptyList<PlotPoint>(), stepVertices(emptyList()))
    }
}
```

In `ui/entry/HistoryLayoutTest.kt`, add the imports `com.mitenko.repkit.R`, `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightUnit`, `com.mitenko.repkit.domain.niceAxis`, `com.mitenko.repkit.ui.common.UiText`, `com.mitenko.repkit.ui.common.weightText`, `org.junit.Assert.assertFalse` and `org.junit.Assert.assertTrue`, and append inside the class:

```kotlin
    private val w14 = CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 3, weight = 1400, reps = 10, unit = WeightUnit.KG)
    private val w16 = CheckInPoint(Instant.parse("2026-09-23T16:00:00Z"), 4, weight = 1600, reps = 10, unit = WeightUnit.KG)

    @Test
    fun `a Counter shows the points of its mode family, and Timer only shows every point`() {
        val view = HistoryLayout.rangeView(listOf(c, w14, w16), HistoryRange.FOUR_WEEKS, now, la)
        assertEquals(listOf(c), HistoryLayout.shownPoints(view, EntryType.WORKOUT, ProgressMode.REPS))
        assertEquals(listOf(w14, w16), HistoryLayout.shownPoints(view, EntryType.WORKOUT, ProgressMode.WEIGHT))
        assertEquals(listOf(c, w14, w16), HistoryLayout.shownPoints(view, EntryType.CHECK_IN, ProgressMode.WEIGHT))
    }

    @Test
    fun `no points in the current mode family says so, but a Reps history without weights is unchanged`() {
        // Right after a switch into a weight mode: only rep points so far.
        val repsOnly = listOf(c, d)
        val view = HistoryLayout.rangeView(repsOnly, HistoryRange.FOUR_WEEKS, now, la)
        val shownWeight = HistoryLayout.shownPoints(view, EntryType.WORKOUT, ProgressMode.WEIGHT)
        assertEquals(HistoryEmpty.NONE_IN_MODE, HistoryLayout.empty(repsOnly, shownWeight, ProgressMode.WEIGHT))
        // Switched back to Reps with only weight points recorded.
        val weightsOnly = listOf(w14, w16)
        val back = HistoryLayout.rangeView(weightsOnly, HistoryRange.FOUR_WEEKS, now, la)
        val shownReps = HistoryLayout.shownPoints(back, EntryType.WORKOUT, ProgressMode.REPS)
        assertEquals(HistoryEmpty.NONE_IN_MODE, HistoryLayout.empty(weightsOnly, shownReps, ProgressMode.REPS))
        // A Reps Counter with only Timer only points (no weight anywhere) still says "none in this range", as before.
        val untotalled = listOf(CheckInPoint(Instant.parse("2026-09-12T16:00:00Z"), null))
        val old = HistoryLayout.rangeView(untotalled, HistoryRange.FOUR_WEEKS, now, la)
        val shownOld = HistoryLayout.shownPoints(old, EntryType.WORKOUT, ProgressMode.REPS)
        assertEquals(HistoryEmpty.NONE_IN_RANGE, HistoryLayout.empty(untotalled, shownOld, ProgressMode.REPS))
        // Weight points that exist but fall outside the range: none in this range.
        val longAgo = listOf(w14.copy(at = Instant.parse("2026-06-10T16:00:00Z")))
        val far = HistoryLayout.rangeView(longAgo, HistoryRange.FOUR_WEEKS, now, la)
        val shownFar = HistoryLayout.shownPoints(far, EntryType.WORKOUT, ProgressMode.WEIGHT)
        assertEquals(HistoryEmpty.NONE_IN_RANGE, HistoryLayout.empty(longAgo, shownFar, ProgressMode.WEIGHT))
        assertNull(HistoryLayout.empty(listOf(w16), listOf(w16), ProgressMode.WEIGHT))
    }

    @Test
    fun `the series is rep totals in Reps mode and weights in the workout's unit in a weight mode`() {
        val reps = HistoryLayout.series(listOf(c, d), ProgressMode.REPS, null)
        assertEquals(ChartSeries.Reps(listOf(c, d)), reps)
        assertEquals(listOf(60, 62), reps.values)
        assertEquals(niceAxis(60, 62), reps.axis())
        assertEquals("62", reps.tickLabel(62))
        assertFalse(reps.stepped)

        val lb = w16.copy(weight = 3500, unit = WeightUnit.LB) // 35 lb = 16 kg
        val weights = HistoryLayout.series(listOf(w14, lb), ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG) as ChartSeries.Weights
        assertEquals(listOf(1400, 1600), weights.values)
        assertTrue(weights.stepped)
        assertEquals(Axis(1400, 1600, 50), weights.axis())
        assertEquals("15.5", weights.tickLabel(1550))
        assertEquals(UiText.Res(R.string.load_value, listOf(weightText(1600, WeightUnit.KG), 10)), weights.load(1))
        assertEquals(weightText(1400, WeightUnit.KG), weights.valueText(1400))
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*PlotPointTest*' --tests '*HistoryLayoutTest*'`
Expected: compilation fails (`stepVertices`, `ChartSeries`, `NONE_IN_MODE`, `series` unresolved).

- [ ] **Step 3: Implement**

Append to `ui/common/PlotPoint.kt`:

```kotlin

/**
 * A step line through [points] (spec rev 26 §4): flat from each point to the next one's x, then
 * straight up or down to it. Fewer than two points are returned as they are.
 */
fun stepVertices(points: List<PlotPoint>): List<PlotPoint> =
    if (points.size < 2) {
        points
    } else {
        buildList {
            add(points.first())
            for (i in 1 until points.size) {
                add(PlotPoint(points[i].x, points[i - 1].y))
                add(points[i])
            }
        }
    }
```

Create `ui/entry/ChartSeries.kt`:

```kotlin
package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.domain.Axis
import com.mitenko.repkit.domain.Prescription
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.model.CheckInPoint
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.niceAxis
import com.mitenko.repkit.domain.weightIn
import com.mitenko.repkit.ui.common.UiText
import com.mitenko.repkit.ui.common.loadText
import com.mitenko.repkit.ui.common.weightText

/** One unit either side of a flat weight line, in hundredths (plan Spec note 54). */
private const val WEIGHT_AXIS_PAD = 100

/** The smallest weight axis step, 0.1 in hundredths, so a tick label has at most one decimal. */
private const val WEIGHT_AXIS_MIN_STEP = 10

/**
 * What the Counter chart plots (spec R6 §4.2, rev 26 §4 and §9.4): [points] (oldest first, all of one
 * mode family) as [values] on the y axis. Kept pure so it's tested without a Canvas.
 */
sealed interface ChartSeries {
    val points: List<CheckInPoint>
    val values: List<Int>

    /** True for a step line (weights), false for today's straight segments (rep totals). */
    val stepped: Boolean

    fun axis(): Axis

    fun tickLabel(tick: Int): String

    /** Reps mode, exactly as before: rep totals on a straight line. */
    data class Reps(override val points: List<CheckInPoint>) : ChartSeries {
        override val values: List<Int> = points.map { requireNotNull(it.total) { "A chart point needs a total" } }
        override val stepped: Boolean get() = false

        override fun axis(): Axis = niceAxis(values.min(), values.max())

        override fun tickLabel(tick: Int): String = "$tick"
    }

    /** A weight mode: each point's weight in the workout's [unit] (§9.3, plan Spec note 54), as a step line. */
    data class Weights(override val points: List<CheckInPoint>, val unit: WeightUnit) : ChartSeries {
        override val values: List<Int> = points.map { requireNotNull(it.weightIn(unit)) { "A weight chart point needs a weight" } }
        override val stepped: Boolean get() = true

        override fun axis(): Axis = niceAxis(values.min(), values.max(), pad = WEIGHT_AXIS_PAD, minMagnitude = WEIGHT_AXIS_MIN_STEP)

        override fun tickLabel(tick: Int): String = WeightFormat.format(tick)

        /** "16 kg × 10": point [index]'s converted weight with the reps it recorded. */
        fun load(index: Int): UiText =
            loadText(Prescription.Load(values[index], requireNotNull(points[index].reps) { "A weight point needs reps" }), unit)

        /** "16 kg", for the chart's description. */
        fun valueText(hundredths: Int): UiText = weightText(hundredths, unit)
    }
}
```

In `ui/entry/HistoryLayout.kt`:

1. Add the imports `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightUnit` and `com.mitenko.repkit.domain.pointsInMode`.
2. Replace the `HistoryEmpty` enum and its KDoc with:

```kotlin
/**
 * Spec rev 9 §3: "No check-ins yet" or "No check-ins in this period"; spec rev 26 §9.4: "No check-ins in
 * this mode yet" when the Counter's current mode family has no points at all (plan Spec note 53).
 */
enum class HistoryEmpty { NO_CHECK_INS, NONE_IN_RANGE, NONE_IN_MODE }
```

3. Delete the stray one-line KDoc `/** A Workout plots only points with a total; a Timer only entry shows every point (plan Spec note 9). */` above `chartStart`'s KDoc. Then replace `shownPoints` and `empty` with:

```kotlin
    /**
     * A Counter plots its mode family's points (plan Spec note 9, spec rev 26 §9.4): rep totals without
     * a weight in Reps mode, weight points in a weight mode. A Timer only entry shows every point.
     */
    fun shownPoints(view: RangeView, type: EntryType, mode: ProgressMode = ProgressMode.REPS): List<CheckInPoint> =
        if (type == EntryType.WORKOUT) pointsInMode(view.points, mode) else view.points

    /**
     * Null when there is something to draw. [mode] is a Counter's mode, null for Timer only. NONE_IN_MODE
     * needs no point of the family at all: always in a weight mode, and in Reps mode only when some weight
     * point exists, so a Reps history without weights reads as before (plan Spec note 53).
     */
    fun empty(all: List<CheckInPoint>, shown: List<CheckInPoint>, mode: ProgressMode? = null): HistoryEmpty? = when {
        all.isEmpty() -> HistoryEmpty.NO_CHECK_INS
        mode != null && pointsInMode(all, mode).isEmpty() && (mode.usesWeights || all.any { it.weight != null }) ->
            HistoryEmpty.NONE_IN_MODE
        shown.isEmpty() -> HistoryEmpty.NONE_IN_RANGE
        else -> null
    }

    /** What the Counter chart draws (spec rev 26 §4): rep totals in Reps mode, weights in [unit] (KG if missing) otherwise. */
    fun series(points: List<CheckInPoint>, mode: ProgressMode, unit: WeightUnit?): ChartSeries =
        if (mode.usesWeights) ChartSeries.Weights(points, unit ?: WeightUnit.KG) else ChartSeries.Reps(points)
```

The existing callers (`EntryScreen.CentreRow`) still compile through the defaults; Task 5 passes the mode.

- [ ] **Step 4: Run them to see them pass**

Run: `RG testDebugUnitTest --tests '*PlotPointTest*' --tests '*HistoryLayoutTest*' --tests '*HardCodedTextGuardTest*'`
Expected: PASS, including every existing HistoryLayoutTest test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/common/PlotPoint.kt app/src/main/kotlin/com/mitenko/repkit/ui/entry/ChartSeries.kt app/src/main/kotlin/com/mitenko/repkit/ui/entry/HistoryLayout.kt app/src/test/kotlin/com/mitenko/repkit/ui/common/PlotPointTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/entry/HistoryLayoutTest.kt
git commit -m "Add the chart series, step vertices and history mode families"
```

---

### Task 5: The weight chart on the entry screen

**Files:**
- Modify: `ui/entry/WorkoutChart.kt`, `ui/entry/EntryScreen.kt`, `ui/entry/EntryViewModel.kt` (three `EntryUiState` fields only)
- Test: modify `ui/entry/EntryScreenTest.kt`

**Interfaces:**
- Consumes: `ChartSeries`, `HistoryLayout.series/shownPoints/empty`, `stepVertices` (Task 4); `history_empty_mode`, `chart_desc_weight` (Task 3).
- Produces:
  - `fun WorkoutChart(series: ChartSeries, start: Instant, end: Instant, zone: ZoneId, modifier: Modifier = Modifier)`, which replaces the `points` parameter;
  - new `EntryUiState` fields (all defaulted, appended after `zone`): `val mode: ProgressMode = ProgressMode.REPS`, `val unit: WeightUnit? = null`, `val load: Prescription.Load? = null`. Task 8 fills them.

- [ ] **Step 1: Write the failing tests**

In `ui/entry/EntryScreenTest.kt`, add the imports `com.mitenko.repkit.domain.Prescription`, `com.mitenko.repkit.domain.model.ProgressMode` and `com.mitenko.repkit.domain.model.WeightUnit`, and append inside the class:

```kotlin
    private val w14 = CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 3, weight = 1400, reps = 10, unit = WeightUnit.KG)
    private val w16 = CheckInPoint(Instant.parse("2026-09-23T16:00:00Z"), 4, weight = 1600, reps = 10, unit = WeightUnit.KG)

    /** A Weight-mode Counter on 16 kg × 10 for 8 sets, with one rep point (c) from before its switch. */
    private val weightState = state.copy(
        reps = List(8) { 10 }, total = 4, mode = ProgressMode.WEIGHT, unit = WeightUnit.KG,
        load = Prescription.Load(1600, 10), points = listOf(c, w14, w16),
    )

    @Test
    fun `a weight mode charts only its weight points, in the unit`() {
        show(weightState)
        compose.onNodeWithContentDescription("2 check-ins, from 14 kg to 16 kg").assertExists()
    }

    @Test
    fun `tapping a weight point shows its recorded load`() {
        show(weightState)
        // Spec rev 21: the axis starts at the first shown point (w14), not at c, which isn't shown.
        val start = w14.at
        compose.onNodeWithTag("chart").performTouchInput {
            val x = HistoryLayout.x(w16.at, start, now, ChartInsets.left.toPx(), width - ChartInsets.right.toPx())
            click(Offset(x, height / 2f))
        }
        compose.onNodeWithText("23 Sep · 16 kg × 10").assertExists()
    }

    @Test
    fun `a point recorded in lb shows in the workout's kg`() {
        show(weightState.copy(points = listOf(w16.copy(weight = 3500, unit = WeightUnit.LB))))
        compose.onNodeWithContentDescription("1 check-in, from 16 kg to 16 kg").assertExists()
    }

    @Test
    fun `right after a switch the chart says there are no check-ins in this mode yet`() {
        show(weightState.copy(points = listOf(c, d)))
        compose.onNodeWithText("No check-ins in this mode yet").assertExists()
        compose.onNodeWithTag("chart").assertDoesNotExist()
    }

    @Test
    fun `back in Reps mode the rep line returns without the weight points`() {
        show(state.copy(points = listOf(c, w14, w16, d)))
        compose.onNodeWithContentDescription("2 check-ins, from 60 to 62 reps").assertExists()
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*EntryScreenTest*'`
Expected: compilation fails (`mode`, `unit`, `load` aren't `EntryUiState` parameters).

- [ ] **Step 3: Add the state fields**

In `ui/entry/EntryViewModel.kt`, add the imports `com.mitenko.repkit.domain.Prescription`, `com.mitenko.repkit.domain.model.ProgressMode` and `com.mitenko.repkit.domain.model.WeightUnit`. Append to `EntryUiState`, after `val zone: ZoneId = ZoneOffset.UTC,`:

```kotlin
    /** Spec rev 26 §9.4: the family the chart plots; a Counter's Progress by, REPS for Timer only. */
    val mode: ProgressMode = ProgressMode.REPS,
    /** The workout's unit (spec rev 26 §9.3); null until it has been in a weight mode. */
    val unit: WeightUnit? = null,
    /** Spec rev 26 §4: a weight mode's next load, for the prescription card; null in Reps mode and for Timer only. */
    val load: Prescription.Load? = null,
```

- [ ] **Step 4: Draw a series in the chart**

Replace `ui/entry/WorkoutChart.kt`'s imports and its `WorkoutChart` function (keep `ChartInsets` and `PointLabel` as they are). The imports become:

```kotlin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.nearestPoint
import com.mitenko.repkit.ui.common.DateFormats
import com.mitenko.repkit.ui.common.PlotPoint
import com.mitenko.repkit.ui.common.resolve
import com.mitenko.repkit.ui.common.stepVertices
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
```

and the function becomes:

```kotlin
/**
 * The Counter chart (spec R6 §4.2, placed by rev 9 §3): [series] plotted by `at` from [start] to [end].
 * The y labels come from the series' axis and the x labels are dates, both in the theme's colours; the
 * line and dots are primary. Rep totals (Reps mode) are straight segments, as before; weights are a step
 * line in the workout's unit (spec rev 26 §4). Tapping within 24 dp of a point labels it ("12 Sep · 62",
 * or "12 Sep · 16 kg × 10"); tapping anywhere else on the chart clears the label. [HistoryLayout] and
 * [ChartSeries] do the maths.
 */
@Composable
fun WorkoutChart(series: ChartSeries, start: Instant, end: Instant, zone: ZoneId, modifier: Modifier = Modifier) {
    val points = series.points
    val values = series.values
    val axis = remember(series) { series.axis() }
    val ticks = remember(start, end, zone) { HistoryLayout.dateTicks(start, end, zone) }
    // Plan Spec note 8: the range's first value to its last one.
    val description = when (series) {
        is ChartSeries.Reps -> pluralStringResource(R.plurals.chart_desc, points.size, points.size, values.first(), values.last())
        is ChartSeries.Weights -> pluralStringResource(
            R.plurals.chart_desc_weight, points.size, points.size,
            series.valueText(values.first()).resolve(), series.valueText(values.last()).resolve(),
        )
    }
    val density = LocalDensity.current
    val left = with(density) { ChartInsets.left.toPx() }
    val right = with(density) { ChartInsets.right.toPx() }
    val top = with(density) { ChartInsets.top.toPx() }
    val bottom = with(density) { ChartInsets.bottom.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val xs = remember(points, start, end, canvasSize) {
        points.map { HistoryLayout.x(it.at, start, end, left, canvasSize.width - right) }
    }
    val ys = remember(values, axis, canvasSize) { values.map { HistoryLayout.y(it, axis, top, canvasSize.height - bottom) } }
    // Spec rev 26 §4: weights step from one point to the next; rep totals keep their straight segments.
    val path = remember(xs, ys, series.stepped) {
        val plotted = xs.indices.map { PlotPoint(xs[it], ys[it]) }
        if (series.stepped) stepVertices(plotted) else plotted
    }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Box(modifier.fillMaxWidth().height(ChartInsets.height).semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .testTag("chart")
                .pointerInput(xs) {
                    detectTapGestures { tap -> selected = nearestPoint(tap.x, xs, ChartInsets.touch.toPx()) }
                },
        ) {
            val plotBottom = size.height - bottom
            val plotRight = size.width - right
            axis.ticks.forEach { tick ->
                val y = HistoryLayout.y(tick, axis, top, plotBottom)
                drawLine(grid, Offset(left, y), Offset(plotRight, y))
                val text = measurer.measure(series.tickLabel(tick), labelStyle)
                drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y - text.size.height / 2f))
            }
            ticks.forEach { (fraction, label) ->
                val text = measurer.measure(label, labelStyle)
                val x = left + (plotRight - left) * fraction
                val textLeft = (x - text.size.width / 2f).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                drawText(text, topLeft = Offset(textLeft, plotBottom + 4.dp.toPx()))
            }
            for (i in 1 until path.size) {
                drawLine(line, Offset(path[i - 1].x, path[i - 1].y), Offset(path[i].x, path[i].y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            xs.indices.forEach { drawCircle(line, 4.dp.toPx(), Offset(xs[it], ys[it])) }
            selected?.let { drawCircle(line, 7.dp.toPx(), Offset(xs[it], ys[it]), style = Stroke(2.dp.toPx())) }
        }
        selected?.let { i ->
            val label = when (series) {
                is ChartSeries.Reps -> HistoryLayout.pointLabel(points[i], zone)
                // Plan Spec note 54: the recorded reps with the weight in the workout's unit.
                is ChartSeries.Weights -> "${DateFormats.dayMonth(points[i].at, zone)} · ${series.load(i).resolve()}"
            }
            PointLabel(label, xs[i], ys[i], canvasSize.width)
        }
    }
}
```

- [ ] **Step 5: Give the entry screen a chart area that knows the mode**

In `ui/entry/EntryScreen.kt`, add the import `com.mitenko.repkit.domain.model.CheckInPoint`, and replace `CentreRow` (with its KDoc) by:

```kotlin
/**
 * Spec rev 9 §3: the chart (or calendar) takes the remaining width at [ChartInsets.height], and a
 * Workout's reps column sits on the right. The empty states sit in the chart area (plan Spec note 17).
 */
@Composable
private fun CentreRow(state: EntryUiState, range: HistoryRange, highlight: Highlight?, onHighlightShown: () -> Unit) {
    val view = remember(state.points, range, state.now, state.zone) {
        HistoryLayout.rangeView(state.points, range, state.now, state.zone)
    }
    val shown = remember(view, state.type, state.mode) { HistoryLayout.shownPoints(view, state.type, state.mode) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartArea(state, range, view, shown, Modifier.weight(1f))
        if (state.type == EntryType.WORKOUT) RepsColumn(state.reps, highlight, onHighlightShown)
    }
}

/**
 * The chart or calendar at [ChartInsets.height], or an empty state in its place (plan Spec note 17). A
 * Counter's chart plots its mode family (spec rev 26 §9.4) and says so when that family has nothing yet.
 */
@Composable
private fun ChartArea(state: EntryUiState, range: HistoryRange, view: RangeView, shown: List<CheckInPoint>, modifier: Modifier) {
    val mode = if (state.type == EntryType.WORKOUT) state.mode else null
    Box(modifier.height(ChartInsets.height), contentAlignment = Alignment.Center) {
        when (HistoryLayout.empty(state.points, shown, mode)) {
            HistoryEmpty.NO_CHECK_INS -> EmptyText(R.string.history_empty)
            HistoryEmpty.NONE_IN_RANGE -> EmptyText(R.string.history_empty_range)
            HistoryEmpty.NONE_IN_MODE -> EmptyText(R.string.history_empty_mode)
            null -> when (state.type) {
                EntryType.WORKOUT -> {
                    // Built only for a Counter: a Timer only point has no total to plot.
                    val series = remember(shown, state.mode, state.unit) { HistoryLayout.series(shown, state.mode, state.unit) }
                    WorkoutChart(series, HistoryLayout.chartStart(view, shown), view.end, state.zone)
                }
                // key(range): a new range gets a fresh scroll state, so the calendar reopens at today.
                EntryType.CHECK_IN -> key(range) { CheckInCalendar(shown, view.start, view.end, state.zone) }
            }
        }
    }
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `RG testDebugUnitTest --tests '*EntryScreenTest*' --tests '*HardCodedTextGuardTest*'`
Expected: PASS, including every existing EntryScreenTest test ("23 Sep · 62", the range switch, the empty states).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/entry/WorkoutChart.kt app/src/main/kotlin/com/mitenko/repkit/ui/entry/EntryScreen.kt app/src/main/kotlin/com/mitenko/repkit/ui/entry/EntryViewModel.kt app/src/test/kotlin/com/mitenko/repkit/ui/entry/EntryScreenTest.kt
git commit -m "Chart a weight mode's weight points as a step line in the workout's unit"
```

---

### Task 6: The prescription card and its highlight

**Files:**
- Create: `ui/entry/PrescriptionLayout.kt`
- Modify: `ui/entry/EntryViewModel.kt` (`Highlight.card` only), `ui/entry/EntryScreen.kt`
- Test: create `ui/entry/PrescriptionLayoutTest.kt`; modify `ui/entry/EntryScreenTest.kt`

**Interfaces:**
- Consumes: `EntryUiState.load/unit` (Task 5), `weightText`, `loadText`, `prescriptionDescription` and the `prescription_sets_reps` / `load_now` / `load_holding` strings (Task 3).
- Produces:
  - `object PrescriptionLayout { fun change(before: Prescription.Load, after: Prescription.Load): RepsColumnLayout.Change? }`;
  - `data class Highlight(val id: Int, val changes: Map<Int, RepsColumnLayout.Change>, val hold: HoldStatus? = null, val card: RepsColumnLayout.Change? = null)`;
  - the test tags `prescription_card`, `prescription_weight`, `prescription_sets`, `load_announcement`.

- [ ] **Step 1: Write the failing tests**

Create `ui/entry/PrescriptionLayoutTest.kt`:

```kotlin
package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.domain.Prescription
import com.mitenko.repkit.ui.entry.RepsColumnLayout.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrescriptionLayoutTest {
    private fun load(weight: Int, reps: Int) = Prescription.Load(weight, reps)

    @Test
    fun `the weight decides first, then the reps`() {
        // Reps then weight at the top of the range: 16 kg × 12 → 20 kg × 8 is a step up.
        assertEquals(Change.UP, PrescriptionLayout.change(load(1600, 12), load(2000, 8)))
        assertEquals(Change.DOWN, PrescriptionLayout.change(load(1600, 8), load(1400, 12)))
        assertEquals(Change.UP, PrescriptionLayout.change(load(1600, 9), load(1600, 10)))
        assertEquals(Change.DOWN, PrescriptionLayout.change(load(1600, 10), load(1600, 8)))
        assertNull(PrescriptionLayout.change(load(1600, 10), load(1600, 10)))
    }
}
```

In `ui/entry/EntryScreenTest.kt`, add the imports `androidx.compose.ui.test.getBoundsInRoot` and `androidx.compose.ui.test.onRoot`, and append inside the class (`weightState` is Task 5's):

```kotlin
    @Test
    fun `a weight mode shows the prescription card instead of the reps column`() {
        show(weightState)
        compose.onNodeWithTag("reps_column").assertDoesNotExist()
        compose.onNodeWithTag("prescription_weight", useUnmergedTree = true).assertTextEquals("16 kg")
        compose.onNodeWithTag("prescription_sets", useUnmergedTree = true).assertTextEquals("8 × 10 reps")
        compose.onNodeWithContentDescription("16 kilograms, 8 sets of 10 reps").assertExists()
    }

    @Test
    fun `the card shows with no history yet`() {
        show(weightState.copy(points = emptyList()))
        compose.onNodeWithText("No check-ins yet").assertExists()
        compose.onNodeWithTag("prescription_card").assertExists()
    }

    @Test
    fun `the card flashes up and announces the new load`() {
        compose.mainClock.autoAdvance = false
        show(weightState, highlight = Highlight(id = 1, changes = emptyMap(), card = RepsColumnLayout.Change.UP))
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("prescription_card")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "increased"))
        compose.onNodeWithText("Now 16 kilograms, 8 sets of 10 reps").assertExists()
    }

    @Test
    fun `a drop flashes the card as decreased`() {
        compose.mainClock.autoAdvance = false
        show(weightState, highlight = Highlight(id = 1, changes = emptyMap(), card = RepsColumnLayout.Change.DOWN))
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("prescription_card")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "decreased"))
    }

    @Test
    fun `a weight hold flashes the card as holding and names the load`() {
        compose.mainClock.autoAdvance = false
        val hold = Highlight(id = 1, changes = emptyMap(), hold = HoldStatus(at = 4, day = 2, of = 4), card = RepsColumnLayout.Change.HOLD)
        show(weightState, highlight = hold)
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("prescription_card")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "holding"))
        compose.onNodeWithText("Holding at 16 kg × 10 · 2 of 4").assertExists()
    }

    @Test
    fun `the card's highlight is consumed once shown`() {
        compose.mainClock.autoAdvance = false
        var shown = 0
        show(weightState, highlight = Highlight(id = 1, changes = emptyMap(), card = RepsColumnLayout.Change.UP), onHighlightShown = { shown++ })
        compose.mainClock.advanceTimeBy(50)
        assertEquals(1, shown)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `the heaviest weight fits on the card at 320dp`() {
        show(weightState.copy(unit = WeightUnit.LB, load = Prescription.Load(99_975, 100)))
        compose.onNodeWithTag("prescription_weight", useUnmergedTree = true).assertTextEquals("999.75 lb")
        val root = compose.onRoot().getBoundsInRoot()
        val card = compose.onNodeWithTag("prescription_card").getBoundsInRoot()
        val weight = compose.onNodeWithTag("prescription_weight", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(card.left >= root.left && card.right <= root.right)
        assertTrue(weight.left >= card.left && weight.right <= card.right)
    }
```

Add `org.junit.Assert.assertTrue` to the imports if it's missing.

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*PrescriptionLayoutTest*' --tests '*EntryScreenTest*'`
Expected: compilation fails (`PrescriptionLayout` and `Highlight.card` unresolved).

- [ ] **Step 3: Implement the change rule and the highlight field**

Create `ui/entry/PrescriptionLayout.kt`:

```kotlin
package com.mitenko.repkit.ui.entry

import com.mitenko.repkit.domain.Prescription

/** The prescription card's maths (spec rev 26 §4), kept pure so it's tested without Compose. */
object PrescriptionLayout {
    /**
     * How a check-in moved the load (plan Spec note 51): the weight decides first, so 16 kg × 12 → 20 kg × 8
     * is UP; at the same weight the reps decide. Null when the load is the same (a hold, or a fresh start).
     */
    fun change(before: Prescription.Load, after: Prescription.Load): RepsColumnLayout.Change? = when {
        after.weight > before.weight -> RepsColumnLayout.Change.UP
        after.weight < before.weight -> RepsColumnLayout.Change.DOWN
        after.reps > before.reps -> RepsColumnLayout.Change.UP
        after.reps < before.reps -> RepsColumnLayout.Change.DOWN
        else -> null
    }
}
```

In `ui/entry/EntryViewModel.kt`, replace the `Highlight` class and its KDoc with:

```kotlin
/**
 * A one-shot check-in highlight (spec revision 12 §3): [id] increases on every event, so the UI's
 * `LaunchedEffect` keys replay even when the changed indices repeat. In Reps mode [changes] marks the
 * reps column's cells. In a weight mode [changes] is empty and [card] is the prescription card's flash
 * (spec rev 26 §4, plan Spec note 51). [hold] is set on a hold day in either mode.
 */
data class Highlight(
    val id: Int,
    val changes: Map<Int, RepsColumnLayout.Change>,
    val hold: HoldStatus? = null,
    val card: RepsColumnLayout.Change? = null,
)
```

- [ ] **Step 4: Implement the card**

In `ui/entry/EntryScreen.kt`, add the imports `androidx.compose.foundation.shape.RoundedCornerShape`, `com.mitenko.repkit.domain.Prescription`, `com.mitenko.repkit.domain.model.WeightUnit`, `com.mitenko.repkit.ui.common.loadText`, `com.mitenko.repkit.ui.common.prescriptionDescription` and `com.mitenko.repkit.ui.common.weightText`.

Replace the body of `CentreRow` from its `Row(` line to the end with:

```kotlin
    val load = state.load
    if (state.type == EntryType.WORKOUT && load != null) {
        // Spec rev 26 §4, plan Spec note 50: in a weight mode the chart takes the full width and the
        // prescription card replaces the reps column, under the chart.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartArea(state, range, view, shown, Modifier.fillMaxWidth())
            PrescriptionCard(load, state.reps.size, state.unit, highlight, onHighlightShown)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartArea(state, range, view, shown, Modifier.weight(1f))
            if (state.type == EntryType.WORKOUT) RepsColumn(state.reps, highlight, onHighlightShown)
        }
    }
}
```

Add these after `RepsColumn`'s `ChangeAnnouncement`:

```kotlin
/**
 * Spec rev 26 §4, plan Spec notes 50–51: a weight mode's next workout, "16 kg" over "8 × 10 reps", as one
 * TalkBack node ("16 kilograms, 8 sets of 10 reps"). After a check-in it flashes like the reps column
 * (rev 12, rev 20): primary when the load goes up, error when it goes down, neutral on a hold, with a
 * small pop except on a hold or with reduced motion. As in [RepsColumn], [highlight] is consumed at once
 * and a remembered copy drives the animation and the announcement.
 */
@Composable
private fun PrescriptionCard(load: Prescription.Load, sets: Int, unit: WeightUnit?, highlight: Highlight?, onHighlightShown: () -> Unit) {
    var active by remember { mutableStateOf<Highlight?>(null) }
    LaunchedEffect(highlight?.id) {
        if (highlight != null) {
            active = highlight
            onHighlightShown()
        }
    }
    val change = active?.card
    val reducedMotion = reducedMotionEnabled()
    val scale = remember { Animatable(1f) }
    val colorFraction = remember { Animatable(0f) }
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(active?.id) {
        if (change == null) return@LaunchedEffect
        flashing = true
        try {
            if (!reducedMotion && change != RepsColumnLayout.Change.HOLD) {
                launch {
                    scale.animateTo(CARD_POP_SCALE, tween(POP_HALF_MS))
                    scale.animateTo(1f, tween(POP_HALF_MS))
                }
            }
            colorFraction.snapTo(1f)
            colorFraction.animateTo(0f, tween(FADE_MS))
        } finally {
            // A cancelled flash (a new highlight, or leaving the screen) must still clear its state.
            flashing = false
        }
    }
    val tint: Color? = when (change) {
        RepsColumnLayout.Change.UP -> MaterialTheme.colorScheme.primary
        RepsColumnLayout.Change.DOWN -> MaterialTheme.colorScheme.error
        RepsColumnLayout.Change.HOLD -> MaterialTheme.colorScheme.onSurfaceVariant
        null -> null
    }
    val increased = stringResource(R.string.reps_state_increased)
    val decreased = stringResource(R.string.reps_state_decreased)
    val holding = stringResource(R.string.reps_state_holding)
    val stateText: String? = if (!flashing) {
        null
    } else {
        when (change) {
            RepsColumnLayout.Change.UP -> increased
            RepsColumnLayout.Change.DOWN -> decreased
            else -> holding
        }
    }
    val description = prescriptionDescription(load, sets, unit).resolve()
    val shape = RoundedCornerShape(12.dp)
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .scale(scale.value)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .then(if (tint != null) Modifier.background(tint.copy(alpha = HIGHLIGHT_ALPHA * colorFraction.value), shape) else Modifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    if (stateText != null) stateDescription = stateText
                }
                .testTag("prescription_card")
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                weightText(load.weight, unit).resolve(),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                modifier = Modifier.testTag("prescription_weight"),
            )
            Text(
                pluralStringResource(R.plurals.prescription_sets_reps, load.reps, sets, load.reps),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("prescription_sets"),
            )
        }
        LoadAnnouncement(active, load, unit, description)
    }
}

/**
 * TalkBack after a weight-mode check-in (plan Spec note 51): "Now 16 kilograms, 8 sets of 10 reps", or
 * "Holding at 16 kg × 10 · 2 of 4" on a hold day. Invisible, like [ChangeAnnouncement].
 */
@Composable
private fun LoadAnnouncement(active: Highlight?, load: Prescription.Load, unit: WeightUnit?, description: String) {
    val card = active?.card
    val hold = active?.hold
    val message = when {
        card == null -> ""
        card == RepsColumnLayout.Change.HOLD && hold != null ->
            stringResource(R.string.load_holding, loadText(load, unit).resolve(), hold.day, hold.of)
        else -> stringResource(R.string.load_now, description)
    }
    Text(
        message,
        modifier = Modifier
            .size(1.dp)
            .testTag("load_announcement")
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
```

Next to the other constants (`REPS_COLUMN_WIDTH` …), add:

```kotlin
/** The card is full width, so its pop is smaller than a reps cell's 1.15 (plan Spec note 51). */
private const val CARD_POP_SCALE = 1.05f
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `RG testDebugUnitTest --tests '*PrescriptionLayoutTest*' --tests '*EntryScreenTest*' --tests '*HardCodedTextGuardTest*'`
Expected: PASS, with every existing EntryScreenTest test unchanged.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/entry/PrescriptionLayout.kt app/src/main/kotlin/com/mitenko/repkit/ui/entry/EntryViewModel.kt app/src/main/kotlin/com/mitenko/repkit/ui/entry/EntryScreen.kt app/src/test/kotlin/com/mitenko/repkit/ui/entry/PrescriptionLayoutTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/entry/EntryScreenTest.kt
git commit -m "Show a weight mode's prescription card with its check-in highlight"
```

---

### Task 7: The run's weight in the timer controller, and the voice line (domain)

**Files:**
- Modify: `domain/model/Cue.kt`, `domain/TimerController.kt`
- Create: `domain/VoiceLine.kt`
- Test: modify `domain/TimerControllerTest.kt`; create `domain/VoiceLineTest.kt`

**Interfaces:**
- Consumes: `SetWeight` (Task 1).
- Produces:
  - `data class PhaseStart(val phase: Phase, val reps: Int? = null, val weight: SetWeight? = null, val newWeight: Boolean = false)`;
  - `TimerController.weight: SetWeight?` (read-only) and `fun start(repsPerSet: List<Int>, weight: SetWeight? = null): Boolean`;
  - `sealed interface VoiceLine { val number: Int }` with `Count(number)`, `Reps(number)` and `Load(weight: Int, unit: WeightUnit, number: Int)`, plus `VoiceLine.of(cue: Cue): VoiceLine?`.

- [ ] **Step 1: Write the failing tests**

In `domain/TimerControllerTest.kt`, add the imports `com.mitenko.repkit.domain.model.SetWeight` and `com.mitenko.repkit.domain.model.WeightUnit`, and append inside the class:

```kotlin
    @Test
    fun `a weight run carries its weight on every work start and marks only set 1 as new`() = runTest {
        val c = controller()
        val cues = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { cues += it } }
        val w = SetWeight(1600, WeightUnit.KG)
        c.prepare(snapshot)
        c.onServiceStarted()
        c.start(List(8) { 10 }, w)
        assertEquals(w, c.weight)
        advanceTimeBy(240_000)
        runCurrent()
        val work = cues.filterIsInstance<Cue.PhaseStart>().filter { it.phase == Phase.WORK }
        assertEquals(8, work.size)
        assertTrue(work.all { it.reps == 10 && it.weight == w })
        assertEquals(listOf(true) + List(7) { false }, work.map { it.newWeight })
        assertTrue(cues.filterIsInstance<Cue.PhaseStart>().filter { it.phase != Phase.WORK }.all { it.weight == null })
        c.dismissDone()
        assertNull(c.weight)
    }

    @Test
    fun `a stopped weight run clears its weight`() = runTest {
        val c = controller()
        c.prepare(snapshot)
        c.onServiceStarted()
        c.start(List(8) { 10 }, SetWeight(1600, WeightUnit.KG))
        runCurrent()
        c.stop()
        assertNull(c.weight)
    }

    @Test
    fun `a Timer only run ignores a weight`() = runTest {
        val c = controller()
        val cues = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { cues += it } }
        c.prepare(snapshot.copy(countsReps = false))
        c.onServiceStarted()
        c.start(List(8) { 10 }, SetWeight(1600, WeightUnit.KG))
        assertNull(c.weight)
        advanceTimeBy(240_000)
        runCurrent()
        val work = cues.filterIsInstance<Cue.PhaseStart>().filter { it.phase == Phase.WORK }
        assertEquals((8 downTo 1).toList(), work.map { it.reps })
        assertTrue(work.all { it.weight == null && !it.newWeight })
    }
```

Create `domain/VoiceLineTest.kt`:

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Cue
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceLineTest {
    private val kg16 = SetWeight(1600, WeightUnit.KG)

    @Test
    fun `Reps mode and Timer only say the bare number, as before`() {
        assertEquals(VoiceLine.Count(12), VoiceLine.of(Cue.PhaseStart(Phase.WORK, reps = 12)))
    }

    @Test
    fun `a weight run's first set says the weight and the reps, later sets just the reps`() {
        assertEquals(VoiceLine.Load(1600, WeightUnit.KG, 10), VoiceLine.of(Cue.PhaseStart(Phase.WORK, 10, kg16, newWeight = true)))
        assertEquals(VoiceLine.Reps(10), VoiceLine.of(Cue.PhaseStart(Phase.WORK, 10, kg16, newWeight = false)))
        assertEquals(10, VoiceLine.Load(1600, WeightUnit.KG, 10).number)
    }

    @Test
    fun `nothing is said without reps or outside a work start`() {
        assertNull(VoiceLine.of(Cue.PhaseStart(Phase.WORK)))
        assertNull(VoiceLine.of(Cue.PhaseStart(Phase.REST)))
        assertNull(VoiceLine.of(Cue.Countdown(3)))
        assertNull(VoiceLine.of(Cue.Finished))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*TimerControllerTest*' --tests '*VoiceLineTest*'`
Expected: compilation fails (`weight`, `newWeight`, `VoiceLine` unresolved).

- [ ] **Step 3: Implement**

In `domain/model/Cue.kt`, replace `PhaseStart` and its KDoc with:

```kotlin
    /**
     * [reps] is the starting set's reps for a WORK start, filled in by TimerController from the
     * list given to start() (spec R4 §5). It is null for every other phase. In a weight run, [weight]
     * is the run's weight on every WORK start and [newWeight] is true on set 1's, so the voice says the
     * weight once (spec rev 26 §4, plan Spec note 57).
     */
    data class PhaseStart(val phase: Phase, val reps: Int? = null, val weight: SetWeight? = null, val newWeight: Boolean = false) : Cue
```

Create `domain/VoiceLine.kt`:

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Cue
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.WeightUnit

/**
 * What the Voice cue says at a WORK start (spec R4 §5, rev 26 §4, plan Spec note 57). Pure; the platform
 * speaker turns it into words in the voice's language.
 */
sealed interface VoiceLine {
    /** The number every line says: the reps, or for Timer only the sets to go. */
    val number: Int

    /** Reps mode and Timer only, as before: the bare number ("12"). */
    data class Count(override val number: Int) : VoiceLine

    /** A weight run's later sets: "10 reps". */
    data class Reps(override val number: Int) : VoiceLine

    /** A weight run's first set: "16 kilos, 10 reps". [weight] is in hundredths of [unit]. */
    data class Load(val weight: Int, val unit: WeightUnit, override val number: Int) : VoiceLine

    companion object {
        /** The line for [cue], or null when nothing is said: not a WORK start, or no reps on it. */
        fun of(cue: Cue): VoiceLine? {
            if (cue !is Cue.PhaseStart || cue.phase != Phase.WORK) return null
            val reps = cue.reps ?: return null
            val weight = cue.weight ?: return Count(reps)
            return if (cue.newWeight) Load(weight.weight, weight.unit, reps) else Reps(reps)
        }
    }
}
```

In `domain/TimerController.kt`:

1. Add the import `com.mitenko.repkit.domain.model.SetWeight`.
2. After the `snapshot` property, add:

```kotlin
    /**
     * The run's weight (spec rev 26 §4, plan Spec note 56), set by [start] from the load the Start
     * check-in left; null in Reps mode, for Timer only and with no run. The timer, the voice and the
     * notification read it.
     */
    var weight: SetWeight? = null
        private set
```

3. Replace `start`'s signature and its first lines up to `val record = …` with:

```kotlin
    fun start(repsPerSet: List<Int>, weight: SetWeight? = null): Boolean {
        val snap = snapshot ?: return false
        // The timer only runs under a foreground service (v1 spec §4).
        if (_status.value != RunStatus.PREPARING || _serviceStatus.value != ServiceStatus.Started) return false
        // Before the engine's first state, so every screen sees the weight with it. Timer only counts no reps.
        this.weight = weight?.takeIf { snap.countsReps }
        val record = RunRecord(wallNow(), repsPerSet)
```

(the rest of `start` is unchanged).

4. Replace `withReps` (with its KDoc) by:

```kotlin
    /**
     * Spec R4 §5, amended by spec revision 8 and revision 10: a WORK start carries that set's
     * entry of [repsPerSet], or, when the run's snapshot has `countsReps = false` (Timer only),
     * the countdown of sets remaining including this one (`sets - set + 1`), so the voice matches
     * the centre number. A weight run's WORK start also carries [weight], new on set 1 (spec rev 26
     * §4). The engine publishes the set's first state just before its PhaseStart, so [state] already
     * names the starting set.
     */
    private fun withReps(cue: Cue, repsPerSet: List<Int>): Cue {
        if (cue !is Cue.PhaseStart || cue.phase != Phase.WORK) return cue
        val s = _state.value ?: return cue
        if (snapshot?.countsReps == false) return cue.copy(reps = s.sets - s.set + 1)
        val w = weight
        return cue.copy(reps = repsPerSet.getOrNull(s.set - 1), weight = w, newWeight = w != null && s.set == 1)
    }
```

5. In `clearRun`, after `snapshot = null`, add `weight = null`.

- [ ] **Step 4: Run them to see them pass**

Run: `RG testDebugUnitTest --tests '*TimerControllerTest*' --tests '*VoiceLineTest*' --tests '*CuePlayerTest*'`
Expected: PASS, including the existing cue tests (`Cue.PhaseStart(Phase.WORK, reps = 8)` still equals what a Reps run emits).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/model/Cue.kt app/src/main/kotlin/com/mitenko/repkit/domain/TimerController.kt app/src/main/kotlin/com/mitenko/repkit/domain/VoiceLine.kt app/src/test/kotlin/com/mitenko/repkit/domain/TimerControllerTest.kt app/src/test/kotlin/com/mitenko/repkit/domain/VoiceLineTest.kt
git commit -m "Carry a weight run's weight on its work cues and add the voice line"
```

---

### Task 8: The entry screen's ViewModel in weight modes (findings 1 and 3)

**Files:**
- Modify: `ui/entry/EntryViewModel.kt`
- Test: modify `ui/entry/EntryViewModelTest.kt`

**Interfaces:**
- Consumes: `Entry.setReps`, `Entry.load`, `Entry.setWeight`, `Entry.historyMode` (Task 1); `PrescriptionLayout.change`, `Highlight.card` (Task 6); `TimerController.start(reps, weight)`, `TimerController.weight` (Task 7); PR 1's `engineConfig()`, `loadAt`.
- Produces: `uiState` fills `mode`, `unit` and `load`; a weight-mode check-in sets `Highlight(card = …)`; Start passes the load's reps and weight to the controller.

- [ ] **Step 1: Write the failing tests**

In `ui/entry/EntryViewModelTest.kt`:

1. Add the imports `com.mitenko.repkit.domain.Prescription`, `com.mitenko.repkit.domain.RunLog`, `com.mitenko.repkit.domain.RunSummary`, `com.mitenko.repkit.domain.HistoryRange`, `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.SetWeight`, `com.mitenko.repkit.domain.model.WeightConfig`, `com.mitenko.repkit.domain.model.WeightHold`, `com.mitenko.repkit.domain.model.WeightUnit` and `com.mitenko.repkit.domain.model.WeightsKind`.
2. Give `harness` a `runLog` parameter (the one allowed edit to an existing helper):

```kotlin
    private fun TestScope.harness(
        behavior: Behavior = Behavior.SUCCEED,
        repository: FakeEntryRepository = repo,
        runLog: RunLog = RunLog {},
    ): Harness {
        val controller = TimerController(backgroundScope, wallNow = fixedWallNow, runLog = runLog) { testScheduler.currentTime }
```

   The rest of `harness` stays as it is.
3. Append inside the class:

```kotlin
    /** The curls ladder (8 / 10 / 12 / 14 / 16 kg) in Weight mode, 10 reps per set; level 3 is 14 kg. */
    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600), repsPerSet = 10)
    private val yesterday = Instant.parse("2026-09-23T12:55:00Z")

    private fun weightRepo(total: Int = 3, holdCount: Int = 0, holds: List<WeightHold> = emptyList()) = FakeEntryRepository(
        listOf(
            testEntry(
                1, "Curls",
                progression = ProgressionConfig(mode = ProgressMode.WEIGHT, weight = curls.copy(holds = holds)),
                counter = CounterState(total = total, bestStreak = 4, currentStreak = 4, lastCheckIn = yesterday, holdCount = holdCount),
            ),
        ),
    )

    @Test
    fun `a Reps entry's state has no load and reads as Reps`() = runTest {
        val h = harness()
        runCurrent()
        assertNull(h.vm.uiState.value.load)
        assertEquals(ProgressMode.REPS, h.vm.uiState.value.mode)
    }

    @Test
    fun `a weight mode's state carries the mode, the unit, the load and the reps per set`() = runTest {
        val h = harness(repository = weightRepo())
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals(ProgressMode.WEIGHT, s.mode)
        assertEquals(WeightUnit.KG, s.unit)
        assertEquals(Prescription.Load(1400, 10), s.load)
        assertEquals(List(8) { 10 }, s.reps)
    }

    @Test
    fun `a weight-mode check-in flashes the card up, not the reps column`() = runTest {
        val h = harness(repository = weightRepo())
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        val highlight = h.vm.highlight.value
        assertEquals(RepsColumnLayout.Change.UP, highlight?.card)
        assertEquals(emptyMap<Int, RepsColumnLayout.Change>(), highlight?.changes)
        assertNull(highlight?.hold)
    }

    @Test
    fun `a weight-mode miss flashes the card down`() = runTest {
        val h = harness(repository = weightRepo())
        runCurrent()
        // ~80 h after the last check-in: penalty 2, held to one weight lighter (level 2, 12 kg).
        clock.instant = Instant.parse("2026-09-26T20:55:00Z")
        h.vm.onCheckIn()
        runCurrent()
        assertEquals(RepsColumnLayout.Change.DOWN, h.vm.highlight.value?.card)
    }

    @Test
    fun `a check-in that stays on a weight hold flashes the card neutral with the hold day`() = runTest {
        // A weight hold on 14 kg for 4 (level 3); day 1 was yesterday. The Reps holds can't see it.
        val h = harness(repository = weightRepo(total = 3, holdCount = 1, holds = listOf(WeightHold(1400, 10, 4))))
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        val highlight = h.vm.highlight.value
        assertEquals(RepsColumnLayout.Change.HOLD, highlight?.card)
        assertEquals(HoldStatus(at = 3, day = 2, of = 4), highlight?.hold)
        assertEquals(emptyMap<Int, RepsColumnLayout.Change>(), highlight?.changes)
    }

    @Test
    fun `Start in a weight mode runs the load's reps with its weight, and the session counts sets times reps`() = runTest {
        val summaries = mutableListOf<RunSummary>()
        val h = harness(repository = weightRepo(), runLog = RunLog { summaries += it })
        runCurrent()
        h.vm.onStart()
        runCurrent()
        // The check-in moved 14 kg to 16 kg; the timer runs 16 kg × 10, not a split of level 4.
        assertEquals(SetWeight(1600, WeightUnit.KG), h.controller.weight)
        assertEquals(RepsColumnLayout.Change.UP, h.vm.highlight.value?.card)
        advanceTimeBy(31_000) // prepare 10 s + set 1's 20 s of work: set 1 is done
        runCurrent()
        assertEquals(10, h.controller.state.value?.repsThisSet)
        h.controller.stop()
        assertEquals(10, summaries.single().repsDone) // 1 completed set × 10 reps
    }

    @Test
    fun `after a switch the chart keeps only the new mode's points, and switching back restores the rep line`() = runTest {
        val h = harness()
        runCurrent()
        h.vm.onCheckIn() // 24 Sep: 65 → 66
        runCurrent()
        clock.instant = Instant.parse("2026-09-25T15:00:00Z")
        h.vm.onCheckIn() // 25 Sep: 67
        runCurrent()
        repo.switchMode(1, ProgressMode.REPS_THEN_WEIGHT, WeightUnit.KG)
        clock.instant = Instant.parse("2026-09-26T15:00:00Z")
        h.vm.onCheckIn() // fresh start: performed at 20 kg × 8 (§10 note 13)
        runCurrent()
        clock.instant = Instant.parse("2026-09-27T15:00:00Z")
        h.vm.onCheckIn() // 20 kg × 9
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals(4, s.points.size)
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, s.mode)
        val view = HistoryLayout.rangeView(s.points, HistoryRange.ALL, s.now, s.zone)
        assertEquals(listOf(2000 to 8, 2000 to 9), HistoryLayout.shownPoints(view, s.type, s.mode).map { it.weight to it.reps })
        repo.switchMode(1, ProgressMode.REPS, WeightUnit.KG)
        runCurrent()
        val back = h.vm.uiState.value
        val repsView = HistoryLayout.rangeView(back.points, HistoryRange.ALL, back.now, back.zone)
        assertEquals(listOf(66, 67), HistoryLayout.shownPoints(repsView, back.type, back.mode).map { it.total })
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*EntryViewModelTest*'`
Expected: the new tests fail: `load` is null and `mode` is REPS for a weight entry, no card highlight, `controller.weight` is null, and `repsDone` is 1 (the split of level 4). The existing tests pass.

- [ ] **Step 3: Implement**

In `ui/entry/EntryViewModel.kt`:

1. Add the imports `com.mitenko.repkit.domain.engineConfig`, `com.mitenko.repkit.domain.historyMode`, `com.mitenko.repkit.domain.load`, `com.mitenko.repkit.domain.loadAt`, `com.mitenko.repkit.domain.setReps` and `com.mitenko.repkit.domain.setWeight`. Keep `RepDistributor` (the Reps path still uses it).
2. In `uiState`, replace the line `reps = RepDistributor.distribute(counter.total, entry.timing.sets),` with `reps = entry.setReps(),`, and add these after `zone = zone,`:

```kotlin
                mode = entry.historyMode,
                unit = entry.progression.weight.unit,
                load = entry.load(),
```

3. In `onCheckIn`, after `val before = uiState.value.reps`, add `val beforeLoad = uiState.value.load`, and change the highlight call to:

```kotlin
                applyHighlight(type, before, beforeLoad, result, repo.entry(entryId).first()?.progression)
```

4. In `startWorkout`, replace everything from `val before = RepDistributor.distribute(…)` to the end of the function with:

```kotlin
        val before = entry.setReps()
        val beforeLoad = entry.load()
        val result = repo.checkIn(entryId, clock)
        // Starting navigates to the timer right away, so this may only ever play once the user
        // comes back here: collectAsStateWithLifecycle pauses collection while the screen isn't
        // started, so the event just waits on the StateFlow. If this screen (and this ViewModel)
        // is gone by the time this runs, there's nobody left to observe it, and it's discarded
        // along with the ViewModel — no extra "has the user left" check is needed.
        applyHighlight(entry.type, before, beforeLoad, result, entry.progression)
        // Spec rev 26 §4, plan Spec notes 56 and 59: a weight run does the load's reps per set with its
        // weight, so the timer, the voice and the session's repsDone follow the load, not the level.
        controller.start(entry.setReps(result.state.total), entry.setWeight(result.state.total))
    }
```

5. Replace `applyHighlight` (with its KDoc) by:

```kotlin
    /**
     * Spec revision 12 §3: nothing highlights for a Timer only entry or an AlreadyToday no-op. If
     * the set count changed while this check-in was in flight (e.g. a Settings edit raced it),
     * `before` no longer lines up with the live column, so this bails rather than diff against a
     * stale size and highlight the wrong cells. A weight mode flashes the prescription card instead
     * (spec rev 26 §4); [beforeLoad] is the card's load before the check-in, null in Reps mode.
     */
    private fun applyHighlight(
        type: EntryType,
        before: List<Int>,
        beforeLoad: Prescription.Load?,
        result: CheckInResult,
        progression: ProgressionConfig?,
    ) {
        if (type != EntryType.WORKOUT || result.outcome == Outcome.AlreadyToday) return
        if (beforeLoad != null || progression?.mode?.usesWeights == true) {
            applyLoadHighlight(beforeLoad, result, progression)
            return
        }
        if (before.size != uiState.value.reps.size) return
        val after = RepDistributor.distribute(result.state.total, before.size)
        val changes = RepsColumnLayout.changedSets(before, after)
        if (changes.isNotEmpty()) {
            _highlight.value = Highlight(++highlightSeq, changes)
            return
        }
        // Spec revision 20: nothing moved because the check-in stayed on a hold. A positive hold
        // count means the total sits on an active hold; flash every cell neutral and say the day.
        val hold = progression?.activeHold(result.state.total)
        if (hold != null && result.state.holdCount > 0) {
            val all = before.indices.associateWith { RepsColumnLayout.Change.HOLD }
            _highlight.value = Highlight(++highlightSeq, all, HoldStatus(result.state.total, result.state.holdCount, hold.forCount))
        }
    }

    /**
     * Spec rev 26 §4, plan Spec notes 51–52: the card flashes UP or DOWN as the load moved, or HOLD when a
     * check-in stayed on an active weight hold. The hold is looked up in the engine's level holds
     * (`engineConfig`), never the Reps holds. A mode switch that raced the check-in leaves the two loads
     * on different ladders, so nothing flashes.
     */
    private fun applyLoadHighlight(before: Prescription.Load?, result: CheckInResult, progression: ProgressionConfig?) {
        if (before == null || progression == null || !progression.mode.usesWeights) return
        val after = progression.loadAt(result.state.total) ?: return
        val change = PrescriptionLayout.change(before, after)
        if (change != null) {
            _highlight.value = Highlight(++highlightSeq, emptyMap(), card = change)
            return
        }
        val hold = progression.engineConfig().activeHold(result.state.total)
        if (hold != null && result.state.holdCount > 0) {
            _highlight.value = Highlight(
                ++highlightSeq, emptyMap(),
                HoldStatus(result.state.total, result.state.holdCount, hold.forCount),
                card = RepsColumnLayout.Change.HOLD,
            )
        }
    }
```

The Reps path's lines are the same as before, in the same order.

- [ ] **Step 4: Run them to see them pass**

Run: `RG testDebugUnitTest --tests '*EntryViewModelTest*' --tests '*EntryScreenTest*' --tests '*HardCodedTextGuardTest*'`
Expected: PASS: the new tests and every existing one (the snapshot test, the Timer only tests, all the Reps highlight tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/entry/EntryViewModel.kt app/src/test/kotlin/com/mitenko/repkit/ui/entry/EntryViewModelTest.kt
git commit -m "Drive the entry screen and Start from a weight mode's load"
```

---

### Task 9: The list tile and its row (findings 2 and 4)

**Files:**
- Modify: `ui/entries/TileLayout.kt`, `ui/entries/TileGraphic.kt`, `ui/entries/EntryListViewModel.kt`
- Test: modify `ui/entries/TileLayoutTest.kt`, `ui/entries/EntryListViewModelTest.kt`

**Interfaces:**
- Consumes: `pointsInMode`, `weightIn` (Task 2); `Entry.historyMode`, `Entry.nextWorkoutReps` (Task 1); `stepVertices` (Task 4).
- Produces: `TileData.steps: Boolean = false` (appended); `TileLayout.tile(points, now, zone, mode: ProgressMode = ProgressMode.REPS, unit: WeightUnit? = null)`; `EntryRow.reps` = `nextWorkoutReps()`.

- [ ] **Step 1: Write the failing tests**

In `ui/entries/TileLayoutTest.kt`, add the imports `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightUnit` and `org.junit.Assert.assertFalse`, and append inside the class:

```kotlin
    @Test
    fun `a weight tile plots the weight points in the workout's unit as steps`() {
        val points = listOf(
            CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 60), // a rep point from before the switch
            CheckInPoint(Instant.parse("2026-09-12T16:00:00Z"), 3, 1400, 10, WeightUnit.KG), // day 15
            CheckInPoint(Instant.parse("2026-09-24T14:00:00Z"), 4, 3500, 10, WeightUnit.LB), // today, 35 lb = 16 kg
        )
        val tile = TileLayout.tile(points, now, la, ProgressMode.WEIGHT, WeightUnit.KG)
        assertEquals(3, tile.count) // every check-in still counts
        assertEquals(listOf(SparkPoint(15, 1400), SparkPoint(27, 1600)), tile.spark)
        assertTrue(tile.steps)
    }

    @Test
    fun `a Reps tile leaves out weight points and draws a plain line`() {
        val points = listOf(
            CheckInPoint(Instant.parse("2026-09-10T16:00:00Z"), 60),
            CheckInPoint(Instant.parse("2026-09-12T16:00:00Z"), 3, 1400, 10, WeightUnit.KG),
        )
        val tile = TileLayout.tile(points, now, la)
        assertEquals(listOf(SparkPoint(13, 60)), tile.spark)
        assertFalse(tile.steps)
    }
```

In `ui/entries/EntryListViewModelTest.kt`, add the imports `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.ProgressionConfig`, `com.mitenko.repkit.domain.model.WeightConfig` and `com.mitenko.repkit.domain.model.WeightsKind`, and append inside the class:

```kotlin
    @Test
    fun `a weight row counts the next workout's reps and plots its weights as steps`() = runTest {
        val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600), repsPerSet = 10)
        val repo = FakeEntryRepository(
            listOf(testEntry(1, "Curls", progression = ProgressionConfig(mode = ProgressMode.WEIGHT, weight = curls), counter = CounterState(total = 3))),
        )
        val vm = vm(repo)
        repo.checkIn(1, clock) // the first check-in stays at level 3, 14 kg
        runCurrent()
        val r = row(vm)
        assertEquals(80, r.reps) // 8 sets × 10 reps, not the level
        assertEquals(listOf(SparkPoint(27, 1400)), r.tile.spark)
        assertTrue(r.tile.steps)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*TileLayoutTest*' --tests '*EntryListViewModelTest*'`
Expected: compilation fails (`steps`, the `mode` parameter unresolved).

- [ ] **Step 3: Implement**

In `ui/entries/TileLayout.kt`:

1. Add the imports `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightUnit`, `com.mitenko.repkit.domain.pointsInMode` and `com.mitenko.repkit.domain.weightIn`.
2. Replace `SparkPoint`'s KDoc with `/** One sparkline point: [day] 0 (27 days ago) to 27 (today), and its [total]: the rep total, or in a weight mode the weight in hundredths of the workout's unit (spec rev 9 §2, rev 26 §4). */`.
3. Replace `TileData` with:

```kotlin
/**
 * A tile's graph data (spec rev 9 §2, amended rev 11 §2). [count] is the check-ins in the 28-day
 * window (every one, whatever its mode), for the Workout content description. [week] are this calendar
 * week's 7 days, Monday first, for the Timer Only week circles. [spark] is the Workout sparkline: the
 * window's points of the entry's mode family, oldest first (plan Spec note 9, spec rev 26 §9.4). [steps]
 * draws it as a step line, for a weight mode (spec rev 26 §4).
 */
data class TileData(
    val count: Int = 0,
    val week: List<Boolean> = NO_WEEK,
    val spark: List<SparkPoint> = emptyList(),
    val steps: Boolean = false,
) {
    companion object {
        val NO_WEEK: List<Boolean> = List(7) { false }
    }
}
```

4. Replace `tile` with:

```kotlin
    /** [mode] is the entry's history family (`Entry.historyMode`) and [unit] its unit; a weight mode plots weights (plan Spec note 55). */
    fun tile(points: List<CheckInPoint>, now: Instant, zone: ZoneId, mode: ProgressMode = ProgressMode.REPS, unit: WeightUnit? = null): TileData {
        val start = tileWindowStart(now, zone)
        val window = points.filter { !it.at.isBefore(start) }
        val firstDay = start.atZone(zone).toLocalDate()
        val weightUnit = unit ?: WeightUnit.KG
        return TileData(
            count = window.size,
            week = weekDays(window, now, zone),
            spark = pointsInMode(window, mode).mapNotNull { p ->
                val day = ChronoUnit.DAYS.between(firstDay, p.at.atZone(zone).toLocalDate()).toInt()
                val value = if (mode.usesWeights) p.weightIn(weightUnit) else p.total
                value?.takeIf { day in 0 until TILE_DAYS }?.let { SparkPoint(day, it) }
            },
            steps = mode.usesWeights,
        )
    }
```

In `ui/entries/TileGraphic.kt`, add the import `com.mitenko.repkit.ui.common.stepVertices`; change the Workout branch's `drawSparkline(tile.spark, filled)` to `drawSparkline(tile.spark, filled, tile.steps)`; add "A weight mode's sparkline is a step line of its weights (spec rev 26 §4)." to `TileGraphic`'s KDoc after its first sentence; and replace `drawSparkline` with:

```kotlin
private fun DrawScope.drawSparkline(spark: List<SparkPoint>, color: Color, steps: Boolean) {
    val radius = POINT_RADIUS.toPx()
    val points = TileLayout.sparkline(spark, size.width, size.height, inset = radius)
    // Spec rev 26 §4: a weight tile steps from one point to the next; a Reps tile keeps straight segments.
    val path = if (steps) stepVertices(points) else points
    path.zipWithNext { a, b ->
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = LINE_WIDTH.toPx(), cap = StrokeCap.Round)
    }
    points.forEach { drawCircle(color, radius, Offset(it.x, it.y)) }
}
```

In `ui/entries/EntryListViewModel.kt`:

1. Add the imports `com.mitenko.repkit.domain.historyMode` and `com.mitenko.repkit.domain.nextWorkoutReps`.
2. Replace the `EntryRow` KDoc's first sentence with: "[reps] is the next workout's reps: the total in Reps mode and for Timer only, sets × reps per set in a weight mode (plan Spec note 60), and [streak] the current check-in streak (spec R4 §4.3); since rev 9 the tile shows neither (plan Spec note 22)."
3. In `uiState`, change `reps = e.counter.total,` to `reps = e.nextWorkoutReps(),` and `tile = TileLayout.tile(points, now, zone),` to `tile = TileLayout.tile(points, now, zone, e.historyMode, e.progression.weight.unit),`.

- [ ] **Step 4: Run them to see them pass**

Run: `RG testDebugUnitTest --tests '*TileLayoutTest*' --tests '*EntryListViewModelTest*' --tests '*EntryListScreenTest*'`
Expected: PASS, with every existing test unchanged (`EntryRow(1, "Burpees", 65, true)`, `TileData(count = 1, …)`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/entries/TileLayout.kt app/src/main/kotlin/com/mitenko/repkit/ui/entries/TileGraphic.kt app/src/main/kotlin/com/mitenko/repkit/ui/entries/EntryListViewModel.kt app/src/test/kotlin/com/mitenko/repkit/ui/entries/TileLayoutTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/entries/EntryListViewModelTest.kt
git commit -m "Plot a weight tile's weights as steps and count its next workout's reps"
```

---

### Task 10: The weight on the timer

**Files:**
- Modify: `ui/timer/TimerUiMapper.kt`, `ui/timer/TimerViewModel.kt`, `ui/timer/TimerScreen.kt`
- Test: modify `ui/timer/TimerUiMapperTest.kt`, `ui/timer/TimerViewModelTest.kt`, `ui/timer/TimerScreenTest.kt`

**Interfaces:**
- Consumes: `TimerController.weight` (Task 7); `weightText`, `spokenWeight`, `timer_desc_with_weight` (Task 3).
- Produces: `TimerUiState.weight: UiText? = null` (appended); `TimerUiMapper.map(s, entryName, countsReps = true, weight: SetWeight? = null)`; the test tag `center_weight`.

- [ ] **Step 1: Write the failing tests**

In `ui/timer/TimerUiMapperTest.kt`, add the imports `com.mitenko.repkit.domain.model.SetWeight` and `com.mitenko.repkit.domain.model.WeightUnit`, and append inside the class:

```kotlin
    private val kg16 = SetWeight(1600, WeightUnit.KG)

    @Test
    fun `a weight run shows the weight over the reps in work and in the rest preview`() {
        val work = TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20, reps = 10), "Curls", weight = kg16)
        assertEquals("16 kg", work.weight.en())
        assertEquals(10, work.centerNumber)
        assertNull(work.label.en())
        assertEquals("Work, set 2 of 8, 10 reps, 16 kilograms", work.description.en())
        val rest = TimerUiMapper.map(state(Phase.REST, set = 3, left = 10, duration = 10, reps = 10), "Curls", weight = kg16)
        assertEquals("16 kg", rest.weight.en())
        assertEquals("REST", rest.label.en())
        assertEquals("Rest, next set 3 of 8, 10 reps, 16 kilograms", rest.description.en())
    }

    @Test
    fun `get ready, cooldown and done show no weight`() {
        listOf(Phase.PREPARE, Phase.COOLDOWN, Phase.DONE).forEach { phase ->
            assertNull("$phase", TimerUiMapper.map(state(phase, set = 1, left = 10, duration = 10), "Curls", weight = kg16).weight)
        }
        val prep = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10), "Curls", weight = kg16)
        assertEquals("Get ready, first set 8 reps", prep.description.en())
    }

    @Test
    fun `a Timer only run and a Reps run show no weight`() {
        assertNull(TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20), "Stretch", countsReps = false, weight = kg16).weight)
        assertNull(TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20), "Burpees").weight)
    }
```

In `ui/timer/TimerViewModelTest.kt`, add the imports `com.mitenko.repkit.domain.model.SetWeight`, `com.mitenko.repkit.domain.model.WeightUnit` and `com.mitenko.repkit.ui.common.weightText`, and append inside the class:

```kotlin
    @Test
    fun `a weight run's ui state shows its weight`() = runTest {
        val controller = TimerController(backgroundScope, wallNow = fixedWallNow) { testScheduler.currentTime }
        val vm = TimerViewModel(controller, preferences(), FakeEntryRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        controller.prepare(WorkoutSnapshot(1L, "Curls", TimingConfig(prepareSec = 0, sets = 3, workSec = 20, restSec = 10), CueConfig()))
        controller.onServiceStarted()
        controller.start(List(3) { 10 }, SetWeight(1600, WeightUnit.KG))
        runCurrent()
        assertEquals(weightText(1600, WeightUnit.KG), vm.uiState.value?.weight)
        assertEquals(10, vm.uiState.value?.centerNumber)
    }
```

In `ui/timer/TimerScreenTest.kt`, add the imports `androidx.compose.ui.unit.DpRect`, `com.mitenko.repkit.domain.model.SetWeight` and `com.mitenko.repkit.domain.model.WeightUnit`, and append inside the class:

```kotlin
    private fun showUi(ui: TimerUiState) {
        compose.setContent {
            HiitTheme {
                TimerScreen(
                    ui, CueConfig(),
                    onTogglePause = {}, onSkipBack = {}, onSkipForward = {}, onClose = {},
                    onToggleSound = {}, onToggleVibration = {}, onToggleVoice = {},
                )
            }
        }
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    /** Every corner of each of [nodes] lies inside the inner ring's open centre (rev 35 §3: diameter d × 0.76). */
    private fun assertInsideRing(ring: DpRect, vararg nodes: DpRect) {
        val radius = (ring.width * (1f - 2f * (0.02f + 0.03f + 0.07f)) / 2f).value
        val cx = ((ring.left + ring.right) / 2f).value
        val cy = ((ring.top + ring.bottom) / 2f).value
        nodes.forEach { b ->
            listOf(b.left to b.top, b.right to b.top, b.left to b.bottom, b.right to b.bottom).forEach { (x, y) ->
                val distance = sqrt((x.value - cx) * (x.value - cx) + (y.value - cy) * (y.value - cy))
                assertTrue("corner (${x.value}, ${y.value}) of $b outside radius $radius", distance <= radius)
            }
        }
    }

    /** The longest realistic centre (rev 35 §3) plus a long weight: "88" reps, "00:59", "112.5 lb". */
    private fun weightUi(phase: Phase) = TimerUiMapper.map(
        TimerState(phase, set = 2, sets = 8, phaseSecondsLeft = 59, phaseDurationSec = 60, elapsedSec = 50,
            totalDurationSec = 240, repsThisSet = 88, totalReps = 88, paused = false),
        entryName = "Kettlebell Lunges",
        weight = SetWeight(11_250, WeightUnit.LB),
    )

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `a long weight fits over the reps inside the ring during work at 320dp`() {
        showUi(weightUi(Phase.WORK))
        compose.onNodeWithTag("center_weight", useUnmergedTree = true).assertTextEquals("112.5 lb")
        val weight = bounds("center_weight")
        val number = bounds("center_number")
        val countdown = bounds("countdown")
        assertTrue(weight.bottom <= number.top)
        assertTrue(number.bottom <= countdown.top)
        assertInsideRing(bounds("ring"), weight, number, countdown)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp")
    fun `a long weight shares the rest label's line inside the ring at 320dp`() {
        showUi(weightUi(Phase.REST))
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertTextEquals("REST")
        compose.onNodeWithTag("center_weight", useUnmergedTree = true).assertTextEquals("112.5 lb")
        val label = bounds("phase_label")
        val weight = bounds("center_weight")
        val number = bounds("center_number")
        val countdown = bounds("countdown")
        assertTrue(label.right <= weight.left)
        assertTrue(label.bottom <= number.top && weight.bottom <= number.top)
        assertTrue(number.bottom <= countdown.top)
        assertInsideRing(bounds("ring"), label, weight, number, countdown)
    }

    @Test
    fun `a Reps run shows no weight`() {
        setScreen(Phase.WORK)
        compose.onNodeWithTag("center_weight", useUnmergedTree = true).assertDoesNotExist()
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*TimerUiMapperTest*' --tests '*TimerViewModelTest*' --tests '*TimerScreenTest*'`
Expected: compilation fails (`weight` isn't a `map` parameter or a `TimerUiState` field).

- [ ] **Step 3: Implement the mapping**

In `ui/timer/TimerUiMapper.kt`:

1. Add the imports `com.mitenko.repkit.domain.model.SetWeight`, `com.mitenko.repkit.ui.common.spokenWeight` and `com.mitenko.repkit.ui.common.weightText`.
2. Append to `TimerUiState`, after `val description: UiText,`:

```kotlin
    /** Spec rev 26 §4, plan Spec note 56: a weight run's weight ("16 kg") over the reps in WORK and REST; null otherwise. */
    val weight: UiText? = null,
```

3. Add to `map`'s KDoc: "[weight] is a weight run's frozen weight (spec rev 26 §4): shown in WORK and REST, and appended to their descriptions; never for Timer only." Change the signature to `fun map(s: TimerState, entryName: String, countsReps: Boolean = true, weight: SetWeight? = null): TimerUiState {`.
4. Change `return when (s.phase) {` to `val mapped = when (s.phase) {` (the branches stay as they are), and after the `when`'s closing brace add:

```kotlin
        // Spec rev 26 §4, plan Spec note 56: a weight run shows its weight in WORK and in the rest preview.
        val w = weight?.takeIf { countsReps && (s.phase == Phase.WORK || s.phase == Phase.REST) } ?: return mapped
        return mapped.copy(
            weight = weightText(w.weight, w.unit),
            description = UiText.Res(R.string.timer_desc_with_weight, listOf(mapped.description, spokenWeight(w.weight, w.unit))),
        )
```

In `ui/timer/TimerViewModel.kt`, replace `toUi` with:

```kotlin
    /**
     * The name and countsReps come from the frozen snapshot, so a rename during the run never shows here
     * (spec §7.1); the weight is the one start() froze (spec rev 26 §4).
     */
    private fun toUi(state: TimerState): TimerUiState =
        TimerUiMapper.map(state, controller.snapshot?.entryName.orEmpty(), controller.snapshot?.countsReps ?: true, controller.weight)
```

- [ ] **Step 4: Implement the label line**

In `ui/timer/TimerScreen.kt`, replace

```kotlin
                    ui.label?.let {
                        Text(it.resolve(), color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("phase_label"))
                    }
```

with

```kotlin
                    val weight = ui.weight
                    if (weight == null) {
                        ui.label?.let { PhaseLabel(it.resolve(), color) }
                    } else {
                        // Spec rev 26 §4, plan Spec note 56: the weight takes the label's line (after "REST" in the
                        // rest preview), so the stack is no taller than rev 35's and still fits the ring.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ui.label?.let { PhaseLabel(it.resolve(), color) }
                            Text(
                                weight.resolve(),
                                color = color,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                modifier = Modifier.testTag("center_weight"),
                            )
                        }
                    }
```

and add, after `tightCentreTextStyle`:

```kotlin
/** The phase label over the number (WORK has none), unchanged since rev 35. */
@Composable
private fun PhaseLabel(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("phase_label"))
}
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `RG testDebugUnitTest --tests '*TimerUiMapperTest*' --tests '*TimerViewModelTest*' --tests '*TimerScreenTest*' --tests '*HardCodedTextGuardTest*'`
Expected: PASS, including rev 35's and rev 33's 320 dp tests.

If only the REST test's `assertInsideRing` fails, never change 0.95 / 0.44 / 0.21. Change the weight `Text` in the `Row` to `MaterialTheme.typography.titleMedium` and rerun. If it still fails, stop and report the measured bounds: open question 2 needs the user.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/ui/timer/TimerUiMapper.kt app/src/main/kotlin/com/mitenko/repkit/ui/timer/TimerViewModel.kt app/src/main/kotlin/com/mitenko/repkit/ui/timer/TimerScreen.kt app/src/test/kotlin/com/mitenko/repkit/ui/timer/TimerUiMapperTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/timer/TimerViewModelTest.kt app/src/test/kotlin/com/mitenko/repkit/ui/timer/TimerScreenTest.kt
git commit -m "Show a weight run's weight on the timer's label line"
```

---

### Task 11: The voice says the load

**Files:**
- Modify: `platform/CueSpeaker.kt`, `platform/VoiceLanguage.kt`, `platform/AndroidCueSpeaker.kt`, `service/CuePlayer.kt`
- Modify (test helper): `testutil/FakeCueSpeaker.kt`
- Test: modify `service/CuePlayerTest.kt`, `platform/VoiceLanguageTest.kt`

**Interfaces:**
- Consumes: `VoiceLine`, `Cue.PhaseStart.weight/newWeight` (Task 7); `voice_load`, `voice_weight_kg` / `voice_weight_lb`, `reps_count` (Task 3); `WeightFormat.pluralCount` (Task 1).
- Produces: `CueSpeaker.speak(line: VoiceLine)` (replaces `speak(number: Int)`); `VoiceLanguage.spokenLine(context, locale, line): String`; `FakeCueSpeaker.lines: List<VoiceLine>` and `spoken: List<Int>` (each line's number).

- [ ] **Step 1: Write the failing tests**

In `platform/VoiceLanguageTest.kt`, add the imports `com.mitenko.repkit.domain.VoiceLine` and `com.mitenko.repkit.domain.model.WeightUnit`, and append inside the class:

```kotlin
    @Test
    fun `a weight run's lines read as the load, then just the reps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("16 kilos, 10 reps", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Load(1600, WeightUnit.KG, 10)))
        assertEquals("1 kilo, 1 rep", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Load(100, WeightUnit.KG, 1)))
        assertEquals("22.5 pounds, 8 reps", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Load(2250, WeightUnit.LB, 8)))
        assertEquals("10 reps", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Reps(10)))
        assertEquals("12", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Count(12)))
    }

    @Test
    fun `the load is said in the voice's language`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val load = VoiceLine.Load(1600, WeightUnit.KG, 10)
        assertEquals("16 kilos, 10 repeticiones", VoiceLanguage.spokenLine(context, Locale.forLanguageTag("es"), load))
        assertEquals("16 公斤，10 次", VoiceLanguage.spokenLine(context, Locale.SIMPLIFIED_CHINESE, load))
        assertEquals("16 किलो, 10 रेप्स", VoiceLanguage.spokenLine(context, hindi, load))
    }
```

In `service/CuePlayerTest.kt`, add the imports `com.mitenko.repkit.domain.VoiceLine`, `com.mitenko.repkit.domain.model.SetWeight` and `com.mitenko.repkit.domain.model.WeightUnit`, and append inside the class:

```kotlin
    @Test
    fun `a weight run's first set says the load and later sets just the reps`() = runTest {
        val speaker = FakeCueSpeaker()
        val p = player(speaker)
        val w = SetWeight(1600, WeightUnit.KG)
        p.play(Cue.PhaseStart(Phase.WORK, reps = 10, weight = w, newWeight = true), voiceOnly)
        runCurrent()
        speaker.finishUtterance()
        p.play(Cue.PhaseStart(Phase.WORK, reps = 10, weight = w), voiceOnly)
        runCurrent()
        assertEquals(listOf(VoiceLine.Load(1600, WeightUnit.KG, 10), VoiceLine.Reps(10)), speaker.lines)
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `RG testDebugUnitTest --tests '*VoiceLanguageTest*' --tests '*CuePlayerTest*'`
Expected: compilation fails (`spokenLine`, `FakeCueSpeaker.lines` unresolved).

- [ ] **Step 3: Implement**

Replace `platform/CueSpeaker.kt`'s interface (keep the package line) with:

```kotlin
import com.mitenko.repkit.domain.VoiceLine
import kotlinx.coroutines.flow.StateFlow

/**
 * Says a voice line (spec R4 §5, rev 26 §4): a rep count, or a weight run's "16 kilos, 10 reps". [available]
 * is false until the engine is ready with a voice in the app's language or English (spec revision 24), and
 * stays false if it never is; nothing is said then. [speak] replaces any utterance in progress and suspends
 * until this one ends: done, failed or replaced (plan Spec note 2). Main thread only.
 */
interface CueSpeaker {
    val available: StateFlow<Boolean>

    suspend fun speak(line: VoiceLine)

    fun shutdown()
}
```

In `platform/VoiceLanguage.kt`, add the imports `android.content.res.Resources`, `com.mitenko.repkit.domain.VoiceLine`, `com.mitenko.repkit.domain.WeightFormat` and `com.mitenko.repkit.domain.model.WeightUnit`. Replace `spokenCount`'s body (keep its KDoc and `@SuppressLint`) and add the rest below it:

```kotlin
    fun spokenCount(context: Context, locale: Locale, count: Int): String =
        localized(context, locale).getQuantityString(R.plurals.voice_count, count, count)

    /**
     * What the voice says for [line] (spec rev 26 §4, plan Spec note 57), in the voice's own [locale]:
     * the bare number for [VoiceLine.Count] (as [spokenCount]), "10 reps" for [VoiceLine.Reps], and
     * "16 kilos, 10 reps" for [VoiceLine.Load], with the weight written as on screen ("22.5").
     */
    fun spokenLine(context: Context, locale: Locale, line: VoiceLine): String {
        val res = localized(context, locale)
        return when (line) {
            is VoiceLine.Count -> res.getQuantityString(R.plurals.voice_count, line.number, line.number)
            is VoiceLine.Reps -> res.getQuantityString(R.plurals.reps_count, line.number, line.number)
            is VoiceLine.Load -> res.getString(
                R.string.voice_load,
                res.getQuantityString(line.unit.voicePlural, WeightFormat.pluralCount(line.weight), WeightFormat.format(line.weight)),
                res.getQuantityString(R.plurals.reps_count, line.number, line.number),
            )
        }
    }

    /** [context]'s resources in [locale]; see [spokenCount] on why the lint warning doesn't apply. */
    @SuppressLint("AppBundleLocaleChanges")
    private fun localized(context: Context, locale: Locale): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config).resources
    }

    private val WeightUnit.voicePlural: Int
        get() = when (this) {
            WeightUnit.KG -> R.plurals.voice_weight_kg
            WeightUnit.LB -> R.plurals.voice_weight_lb
        }
```

In `platform/AndroidCueSpeaker.kt`, add the import `com.mitenko.repkit.domain.VoiceLine`; in the class KDoc replace "and the number as the `voice_count` resource in the voice's language (the digits, "12", in English), which the engine says as a word" with "and the line's words from resources in the voice's language (`VoiceLanguage.spokenLine`: the digits "12" for a rep count, "16 kilos, 10 reps" for a weight run)"; change `override suspend fun speak(number: Int) {` to `override suspend fun speak(line: VoiceLine) {` and `val text = VoiceLanguage.spokenCount(appContext, voiceLocale, number)` to `val text = VoiceLanguage.spokenLine(appContext, voiceLocale, line)`.

In `service/CuePlayer.kt`, add the import `com.mitenko.repkit.domain.VoiceLine`, and:
- in `play`, change `val reps = spokenReps(cue, config)` to `val line = spokenLine(cue, config)`, `if (!tones && reps == null) return` to `if (!tones && line == null) return`, and `if (reps != null) say(reps, afterMs = if (tones) pattern.durationMs else 0L)` to `if (line != null) say(line, afterMs = if (tones) pattern.durationMs else 0L)`;
- replace `spokenReps` with:

```kotlin
    /** What to say: only at a WORK start, with the voice on and a speaker that is ready (spec R4 §5, rev 26 §4). */
    private fun spokenLine(cue: Cue, config: CueConfig): VoiceLine? {
        if (!config.voice || cue !is Cue.PhaseStart || cue.phase != Phase.WORK) return null
        if (speaker?.available?.value != true) return null
        return VoiceLine.of(cue)
    }
```

- in `say`, change the signature to `private fun say(line: VoiceLine, afterMs: Long) {` and `s.speak(reps)` to `s.speak(line)`.

Replace `testutil/FakeCueSpeaker.kt` with:

```kotlin
package com.mitenko.repkit.testutil

import com.mitenko.repkit.domain.VoiceLine
import com.mitenko.repkit.platform.CueSpeaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** A [CueSpeaker] that records what it was asked to say. [speak] suspends until [finishUtterance], like an engine still talking. */
class FakeCueSpeaker(available: Boolean = true) : CueSpeaker {
    override val available = MutableStateFlow(available)

    /** Every line asked for, in order. */
    val lines = mutableListOf<VoiceLine>()

    /** Each line's number: the reps (or the sets to go), which is all a Reps or Timer only run says. */
    val spoken: List<Int> get() = lines.map { it.number }

    var shutdowns = 0
    private var utterance: CompletableDeferred<Unit>? = null

    override suspend fun speak(line: VoiceLine) {
        lines += line
        val done = CompletableDeferred<Unit>()
        utterance = done
        done.await()
    }

    fun finishUtterance() {
        utterance?.complete(Unit)
    }

    override fun shutdown() {
        shutdowns++
        available.value = false
    }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `RG testDebugUnitTest --tests '*VoiceLanguageTest*' --tests '*CuePlayerTest*' --tests '*TimerViewModelTest*'`
Expected: PASS, including the existing `listOf(12)` / `listOf(9)` assertions and `the spoken count is the number…`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/platform/CueSpeaker.kt app/src/main/kotlin/com/mitenko/repkit/platform/VoiceLanguage.kt app/src/main/kotlin/com/mitenko/repkit/platform/AndroidCueSpeaker.kt app/src/main/kotlin/com/mitenko/repkit/service/CuePlayer.kt app/src/test/kotlin/com/mitenko/repkit/testutil/FakeCueSpeaker.kt app/src/test/kotlin/com/mitenko/repkit/service/CuePlayerTest.kt app/src/test/kotlin/com/mitenko/repkit/platform/VoiceLanguageTest.kt
git commit -m "Say a weight run's load on its first set and the reps after"
```

---

### Task 12: The weight in the notification

**Files:**
- Modify: `service/NotificationText.kt`, `service/WorkoutNotifications.kt`, `service/TimerService.kt`
- Test: modify `service/NotificationTextTest.kt`

**Interfaces:**
- Consumes: `TimerController.weight` (Task 7); `weightText`, `notification_with_weight` (Task 3).
- Produces: `NotificationText.title(entryName, s, weight: SetWeight? = null)`; `WorkoutNotifications.build(state, entryName, weight: SetWeight? = null)` and `update(state, entryName, weight: SetWeight? = null)`.

- [ ] **Step 1: Write the failing test**

In `service/NotificationTextTest.kt`, add the imports `com.mitenko.repkit.domain.model.SetWeight` and `com.mitenko.repkit.domain.model.WeightUnit`, and append inside the class:

```kotlin
    @Test
    fun `a weight run's title ends with the weight while there's a set to do`() {
        val w = SetWeight(1600, WeightUnit.KG)
        assertEquals("Curls · Work · Set 2/8 · 16 kg", text.title("Curls", work, w))
        assertEquals("Curls · Rest · Set 2/8 · 16 kg", text.title("Curls", work.copy(phase = Phase.REST), w))
        assertEquals("Curls · Get ready · Set 2/8 · 16 kg", text.title("Curls", work.copy(phase = Phase.PREPARE), w))
        assertEquals("Curls · Cooldown · Set 2/8", text.title("Curls", work.copy(phase = Phase.COOLDOWN), w))
        assertEquals("Curls · Done · Set 2/8", text.title("Curls", work.copy(phase = Phase.DONE), w))
        assertEquals("Work · Set 2/8 · 22.5 lb", text.title(null, work, SetWeight(2250, WeightUnit.LB)))
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `RG testDebugUnitTest --tests '*NotificationTextTest*'`
Expected: compilation fails (`title` takes no weight).

- [ ] **Step 3: Implement**

In `service/NotificationText.kt`, add the imports `com.mitenko.repkit.domain.model.SetWeight` and `com.mitenko.repkit.ui.common.weightText`, and replace `title` (with its KDoc) by:

```kotlin
    /**
     * `"<entryName> · <Phase> · Set n/N"` with the name frozen in the run's snapshot (spec §7.6). A weight
     * run adds `" · 16 kg"` while there's a set to do: PREPARE, WORK and REST (spec rev 26 §4, plan Spec note 58).
     */
    fun title(entryName: String?, s: TimerState, weight: SetWeight? = null): String {
        val phaseSet = res.getString(R.string.notification_phase_set, phaseName(s.phase), s.set, s.sets)
        val title = if (entryName != null) res.getString(R.string.notification_named, entryName, phaseSet) else phaseSet
        val w = weight?.takeIf { s.phase == Phase.PREPARE || s.phase == Phase.WORK || s.phase == Phase.REST } ?: return title
        return res.getString(R.string.notification_with_weight, title, weightText(w.weight, w.unit).resolve(res))
    }
```

In `service/WorkoutNotifications.kt`, add the import `com.mitenko.repkit.domain.model.SetWeight` and:
- change `fun build(state: TimerState?, entryName: String?): Notification {` to `fun build(state: TimerState?, entryName: String?, weight: SetWeight? = null): Notification {`, its KDoc to `/** [entryName] is the run snapshot's frozen name (spec §7.6); null only if no run is prepared. [weight] is a weight run's (spec rev 26 §4). */`, and `.setContentTitle(state?.let { text.title(entryName, it) } ?: text.startingTitle(entryName))` to `.setContentTitle(state?.let { text.title(entryName, it, weight) } ?: text.startingTitle(entryName))`;
- change `fun update(state: TimerState, entryName: String?) {` to `fun update(state: TimerState, entryName: String?, weight: SetWeight? = null) {` and its last line to `manager.notify(NOTIFICATION_ID, build(state, entryName, weight))`.

In `service/TimerService.kt`:
- change `notifications.build(controller.state.value, controller.snapshot?.entryName),` (in the `if (started)` branch) to `notifications.build(controller.state.value, controller.snapshot?.entryName, controller.weight),`;
- change `notifications.update(state, controller.snapshot?.entryName)` to `notifications.update(state, controller.snapshot?.entryName, controller.weight)`.

The first-start `notifications.build(null, controller.snapshot?.entryName)` has no state yet and stays as it is.

- [ ] **Step 4: Run it to see it pass**

Run: `RG testDebugUnitTest --tests '*NotificationTextTest*' assembleDebug`
Expected: PASS (both existing tests unchanged) and the build compiles the service.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/service/NotificationText.kt app/src/main/kotlin/com/mitenko/repkit/service/WorkoutNotifications.kt app/src/main/kotlin/com/mitenko/repkit/service/TimerService.kt app/src/test/kotlin/com/mitenko/repkit/service/NotificationTextTest.kt
git commit -m "Add a weight run's weight to the workout notification"
```

---

### Task 13: Spec notes, project notes, the version and the full gate

**Files:**
- Modify: `docs/superpowers/specs/2026-10-04-weight-progression-design.md` (§7 and §10)
- Modify: `CLAUDE.md`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: everything above.
- Produces: the recorded decisions, 0.2.0 and a green gate.

- [ ] **Step 1: Record the decisions in the weight spec**

In §7, item 3, append: " Built from `docs/superpowers/plans/2026-10-10-weight-screens.md` (§10 notes 50–64)."

At the end of §10, after note 49, add a blank line, then:

```markdown
**PR 3 (screens in use, 2026-10-10)**, recorded from `docs/superpowers/plans/2026-10-10-weight-screens.md`. Notes 50, 56, 57, 59 and 64 record the user's rulings on that plan's open questions.
```

Then write notes 50–64 in full, copied from this plan's "Spec notes", with "plan Spec note" changed to "§10 note" and each "open question N" replaced by the user's ruling (for example "(open question 1, ruled 2026-10-1x: as recommended)"). Don't leave a "…". If the user ruled against a recommendation, write the ruling and say which task changed.

- [ ] **Step 2: Update CLAUDE.md**

- In the Weight progression bullet, replace "(approved, rev 26; PR 1 model and storage, PR 2 settings UI; PR 2 and PR 3 ship together as 0.2.0)" with "(approved, rev 26; PR 1 model and storage, PR 2 settings UI, PR 3 screens in use; shipped together as 0.2.0)", and "Its §10 records the PR 1 and PR 2 implementation notes." with "Its §10 records the PR 1–3 implementation notes."
- Under "Key decisions", after the "Cue toggles" bullet, add:

```markdown
- Weight modes in use (rev 26 §4, §10 notes 50–64): `domain/Workload.kt` (`setReps`, `load`, `setWeight`, `historyMode`, `nextWorkoutReps`) is the one answer for the next workout. The entry screen shows a prescription card ("16 kg" over "8 × 10 reps") instead of the reps column, flashing through `Highlight.card`, with holds looked up in `engineConfig()`. The chart and the tile plot only the current mode family (`pointsInMode`: weight points in a weight mode, weightless totals in Reps) as a step line in the workout's unit. `TimerController.start(reps, weight)` freezes the run's `SetWeight` for the timer (on the label line), the voice (`VoiceLine`: set 1 says "16 kilos, 10 reps", later sets "10 reps") and the notification ("· 16 kg"). `workout_session.repsDone` is completed sets × reps per set.
```

- In "Plans", append ", `docs/superpowers/plans/2026-10-10-weight-screens.md` (weight progression PR 3: screens in use)".

- [ ] **Step 3: Set the version**

In `app/build.gradle.kts`, change `versionCode = 1` to `versionCode = 2` and `versionName = "0.1.0"` to `versionName = "0.2.0"` (open question 5, §10 note 64).

- [ ] **Step 4: Run the full gate**

Announce it first: the gate runs on a remote worker and takes about 4 minutes.

Run: `RG assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL, every test green (the file-based `MigrationTestHelper` checks are skipped on Windows), lint at 0 errors. MissingTranslation is an error, so a green lint means 0 MissingTranslation. If one appears, a key is missing from the data file: add it and regenerate (Task 3 Step 4). A `HardCodedTextGuardTest` failure names the file and line: move the text to a resource.

Confirm nothing outside the plan changed:

```bash
git status --short                      # clean
git diff --stat main -- app/schemas     # empty: no Room change
git log --format='%an <%ae> | %s' main..HEAD   # every commit by mitenko <mitenko@gmail.com>, no trailers
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-04-weight-progression-design.md CLAUDE.md app/build.gradle.kts
git commit -m "Record the weight screens notes, update the project notes and set 0.2.0"
```

Don't push. The PR (squash, rebase onto `main`, `gh pr create`) follows CLAUDE.md's protocol when the user asks. Its description says it completes weight progression for 0.2.0, together with PR 2.

---

## Self-review

**Spec coverage (§4, §9.3, §9.4 and the PR 2 findings):**
- Entry screen prescription card, "16 kg" / "3 × 10 reps": Tasks 3, 6. Highlight on the card (primary up, error down, neutral on a hold): Tasks 6, 8. TalkBack "16 kilograms, 3 sets of 10 reps": Tasks 3, 6.
- History chart as a weight step line, y axis in the unit, "16 kg × 10" on tap, flat while reps climb in Reps then weight (same weight, same y), rev 21 x axis: Tasks 2, 4, 5.
- §9.4: weight points in a weight mode, rep totals in Reps mode, the other family kept and back on a switch back, "No check-ins in this mode yet", "N× this week" and streaks counting every check-in: Tasks 2, 4, 5, 8 (the scenario test), 9.
- §9.3: a point's weight converts from its own unit into the workout's, for the chart and the label: Tasks 2, 4, 5, 9.
- List tile sparkline as a step line, byline unchanged: Task 9.
- Timer: reps in the centre as today, the weight above it in WORK and in the rest preview, rev 35 sizes, a 320 dp test with "112.5 lb", Sets/Elapsed unchanged, Timer only unaffected: Tasks 7, 10.
- Voice "16 kilos, 10 reps", localised with plurals in four languages; later sets "10 reps": Tasks 3, 7, 11.
- Notification "Curls · Work · Set 2/3 · 16 kg": Task 12.
- Finding 1 (the hold lookup and the load in the announcement): Tasks 6, 8. Finding 2 (mode families in the chart, tile and labels): Tasks 2, 4, 5, 9. Finding 3 (`repsDone`): Tasks 1, 8, decided in note 59. Finding 4 (`EntryRow.reps`): Tasks 1, 9. Finding 5 (Reps and Timer only unchanged): the section above lists the proving tests; Tasks 1, 4, 7, 8, 9 keep the old code paths.
- Strings with es / zh-rCN / hi through the data file, 0 MissingTranslation: Tasks 3, 13. §10 notes from 50, CLAUDE.md and the gate: Task 13.

**Placeholder scan:** every code step has the code. The two variable parts are the CSV path ("your session scratchpad") and Task 13's rulings text, which depend on the executor's session and the user's answers.

**Type consistency:**
- `SetWeight(weight, unit)` is used the same way in Tasks 1, 7, 8, 10, 11 and 12.
- `VoiceLine.Load(weight, unit, number)` matches across Tasks 7 and 11.
- `Highlight(id, changes, hold, card)` matches across Tasks 6 and 8.
- `ChartSeries.Weights.load(index)` / `valueText` match across Tasks 4 and 5.
- `HistoryLayout.empty(all, shown, mode)` matches across Tasks 4 and 5.
- `TileLayout.tile(points, now, zone, mode, unit)` matches across Task 9.
- `TimerUiMapper.map(s, entryName, countsReps, weight)` matches across Task 10.
- `NotificationText.title(entryName, s, weight)` matches across Task 12.
