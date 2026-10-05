# REPKIT Weight progression, PR 1 of 3: Model and storage — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the pure weight-progression model (modes, units, weight ladders, `ProgressionScale`, `missFloor`, the remap by value, unit conversion, validation) and its storage (Room v7, history load columns, the repository's mode switch and weight saves, the app-wide default unit), with no UI change and Reps mode byte-for-byte unchanged.

**Architecture:**
- **Domain (pure, test-first):** `RepProgression` keeps moving one integer. In a weight mode that integer is a **level** on a ladder, and `ProgressionScale` translates it to a `Prescription.Load` (weight × reps per set) and back. `ProgressionConfig.engineConfig()` turns a weight-mode config into the floor/cap/holds the engine already understands. The engine's only change is an optional `missFloor`, which is "no floor" in Reps mode.
- **Storage:** Room v7 adds columns with defaults (no row is rewritten). `EntryMapping` repairs the new columns per field, in the existing `checked()` style. `RoomEntryRepository` gains two methods: `setWeightConfig`, which runs the §9.2 remap and validation in one transaction, and `switchMode`, which starts fresh. `checkIn` records weight, reps and unit for weight modes. `AppPreferences` gains `weight_unit_default`.
- **Nothing user-visible:** no Compose, no strings, no `FieldMessage` or `Field` changes. PR 2 (settings) and PR 3 (screens in use) build on the interfaces produced here.

**Tech Stack:** Kotlin 2.2.10, Room 2.8.1 (KSP, `androidx.room` plugin, schemas in `app/schemas/`), DataStore Preferences 1.1.1, coroutines 1.10.2, JUnit 4.13.2, Robolectric 4.16. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-04-weight-progression-design.md` (revision 26). Read all of it, especially §2, §5, §6, §7.1 and §9. Also read `CLAUDE.md` (the project rules). In this plan, `§n` points into revision 26.

## Global Constraints

- **Paths:** main code is under `app/src/main/kotlin/com/mitenko/repkit/`, and tests are under `app/src/test/kotlin/com/mitenko/repkit/`. The package is `com.mitenko.repkit`.
- **`domain/` is pure Kotlin:** no `android.*` imports. Develop it test-first (red, then green).
- **Clock:** time always comes from the injected `Clock`. Never call `System.currentTimeMillis()` or `Instant.now()`.
- **Reps mode stays byte-for-byte the same.** Don't edit any existing test's assertions. Every existing test must pass unchanged. New fields go at the **end** of constructors and have defaults, so existing positional and named calls keep compiling.
- **No UI change in PR 1:**
  - no Compose files;
  - no `res/` files and no new strings;
  - no new `FieldMessage` subtypes and no new `Field` values (`ui/common/FieldMessages.kt` maps `FieldMessage` exhaustively, so a new subtype would need a string).
- **Units of storage (§2, §5):**
  - Weights are **integer hundredths** of the workout's unit: 2250 = 22.5.
  - The limits are: 2–40 weights, each 1..99 975 (0.01..999.75), and step choices 50 / 100 / 125 / 200 / 250 / 500.
  - Reps (per set, min, max) are 1..100 with min < max, and there are at most 8 holds (`ProgressionConfig.MAX_HOLDS`).
  - Conversion uses ×2.20462 or ÷2.20462, rounded to the nearest 25 (0.25).
- **Room rule (CLAUDE.md):**
  - bump `@Database(version = 7)`;
  - add `MIGRATION_6_7` to `HiitDatabase.MIGRATIONS`;
  - commit the exported `app/schemas/com.mitenko.repkit.data.db.HiitDatabase/7.json`;
  - write a `MigrationTestHelper` test (skipped on Windows) and an in-memory framework-SQL test.
  - `1.json`…`6.json` stay byte-for-byte unchanged.
- **Builds run remotely:**
  - **Targeted tests:** from the repo root in PowerShell, `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*X*"`. In Git Bash, quote the script path: `'D:\Dev\scripts\rgradle.ps1'`.
  - **Remote output** is tagged `CLAUDE-RGRADLE-<id>` automatically.
  - **Fallback:** exit code **2** means the worker is offline. Only then, fall back to a local targeted run: `./gradlew testDebugUnitTest --tests "*X*" 2>&1 | tee "<scratchpad>/claude-weight-task<N>.log"; echo "CLAUDE-WEIGHT-TASK<N> rc=${PIPESTATUS[0]}"`. Announce the run first (about 2–4 minutes).
  - **Never run two builds at once.**
  - **Final gate:** `assembleDebug testDebugUnitTest lintDebug` (Task 13).
- **Commits:**
  - Commit as `mitenko <mitenko@gmail.com>` (check `git config user.email` once, before the first commit).
  - Plain messages with **no trailers** (no Co-Authored-By, no "Generated with").
  - Use **explicit `git add <paths>`**, never `git add -A` or `git add .`.
  - Leave the untracked `docs/feedback.md` alone, and don't push.
- **Naming:** the ladder integer is called a **level** in code. `Entry.position` already means the list order.

## Spec notes (rulings made while reading the real code)

Task 13 records these in the spec as §10 "Implementation notes (PR 1)". Each one is binding for this plan.

1. **`missFloor` is "no floor" in Reps mode, not the identity.**
   - §2 says `missFloor` is "identity for Reps mode", but §9.1 step 3 is `next = max(raw, missFloor(start), min)`. With the identity, `missFloor(start) = start`, so every Reps penalty would be cancelled.
   - Ruling: `ProgressionScale.Reps.missFloor` returns `Int.MIN_VALUE`. Then `max(raw, MIN, floor)` equals today's `max(floor, total − penalty)` exactly.
2. **Weight-mode holds are stored by value in a new column, `entry.weight_holds`** (TEXT NOT NULL DEFAULT ''), as `weight:reps:for` items. This is a deviation from §5, which says "`holds` keeps storing positions". Three reasons:
   - `HoldsCodec` and `SettingsValidator` reject `at < 1`, but level 0 (the lightest weight × min reps) is a valid hold.
   - One shared column would read a Reps hold (64) as level 64 after a switch, and a Weight level as a different Reps-then-weight level. That breaks "each mode's settings are remembered".
   - Stored values make the §9.2 hold remap a plain filter.

   The engine still sees **level** holds: `engineConfig()` converts them. `ProgressionConfig.holds` (Reps) is untouched. The Hold switch (`hold_enabled`) is shared by all modes.
3. **A level can be 0.** `validTotal` reads a stored total below 1 as NULL. It gains a `min` parameter, which is 0 in a weight mode. A NULL total in a weight mode reads as the **start level**, so the existing "NULL = untouched" rule carries over: Reset progress and Start fresh both write NULL.
4. **The remap lives in a new `setWeightConfig(id, WeightConfig)`, not in `setProgression`.**
   - The existing Reps page builds its save from `ProgressionDraft.toConfig()`, which rebuilds a `ProgressionConfig` from the Reps fields only. If `setProgression` wrote the weight group, every Reps-page save would wipe it.
   - So `setProgression` stays byte-for-byte the same, and never writes the mode or the weight columns. `setWeightConfig` writes the weight group, with the §9.2 remap and validation.
5. **`switchMode(id, mode, defaultUnit)` takes the app default as a parameter.** The caller (PR 2's ViewModel) reads `AppPreferences.weightUnitDefault`, which keeps the repository free of DataStore.
   - Start fresh means: total NULL (the new mode's start level), hold count 0, and `weight_unit = COALESCE(weight_unit, defaultUnit)` when entering a weight mode. Streaks, the last check-in and the history are kept.
   - Switching to the current mode is a no-op.
6. **What a weight-mode `check_in` row stores.** Its `total` is the **level**, so a Workout point keeps a non-NULL total, and PR 3 tells the mode families apart by `weight IS NOT NULL` (§9.4). `weight`, `reps` and `unit` are written only for a Counter (`WORKOUT`) check-in in a weight mode. They stay NULL for Reps and for Timer Only, including a Timer Only entry whose stored mode is a weight mode.
7. **Validation is typed and string-free.** `WeightValidator.validate(config, mode): List<WeightProblem>` covers every row of §3.1. PR 2 maps each problem to a field and a string. "At most 2 decimals" is automatic with integer hundredths; parsing input is PR 2's job.
8. **A unit change can merge weights, and Steps become My weights.**
   - §9.2 says ties "can't arise". They can after a unit change: 1.00 and 1.25 lb both round to 0.50 kg. `WeightConversion.convert(config, to)` keeps the first of any merged weights, and clamps every value to 25..99 975.
   - A converted step (2.5 kg = 5.51 lb) isn't one of the step choices, so a converted Steps config becomes a **My weights** list of the converted values. The stored `steps` value is left as it was.
9. **Hold-count reset on a weight save** (§9.2 step 5, read literally): the count resets when the current load (weight value, reps per set) changes. In Weight mode, that includes a reps-per-set edit. It also resets when the weight-hold list changes (order counts, as in rev 16 §4), or when the set of active holds changes (a hold that lands on the top level is inactive, as in Reps).
10. **Two holds in one place.** In Weight mode, two holds on one weight with different reps sit on the same position. The validator reports the later one, and read-repair keeps the first. In Reps then weight they are different positions.
11. **A weight mode with no unit** can only come from a corrupt row, because §9.3 writes the unit on the first switch. Such a row reads as KG and is logged, so check-ins always record a unit.
12. **Where things live:**
    - The weight codecs go in `data/WeightCodecs.kt`, next to `HoldsCodec`, following the existing pattern even though they are pure.
    - `defaultWeightUnit(country)` is pure domain code. `AppPreferences` takes a `country: () -> String` parameter, which defaults to `Locale.getDefault().country`.
13. **RULING (user, 2026-10-05, option A): the first check-in after Start fresh is performed AT the starting point.** It does not go +1, and the streak continues. Mechanism, which binds Tasks 4, 8, 9 and 11:
    - **Storage (Task 8):** v7 also adds `entry.fresh_start INTEGER NOT NULL DEFAULT 0`. Every existing row reads 0, so nothing changes.
    - **Model (Task 9 mapping, and `CounterState`):** add `val freshStart: Boolean = false` to `CounterState`, mapped to and from `fresh_start`.
    - **Engine (Task 4, `RepProgression.checkIn`):** when `state.freshStart` is true and the outcome is **not** AlreadyToday:
      - **On time:** keep the position exactly as it is (the start). Do not +1. The streak still goes +1 and `lastCheckIn = now`. The hold count follows `startingHoldCount(position)`, so if the start is itself a hold, that day counts as day 1.
      - **Missed:** keep the position at the start. The start is already the fresh baseline, so no penalty applies. The streak resets to 1, as for any miss.
      - **First** (no last check-in): unchanged, as today.
      - In every case the result has `freshStart = false`.
      - If `freshStart` is false, the engine behaves byte-for-byte as before. This applies to every mode, including Reps.
    - **Repository (Task 11):**
      - `switchMode` writes `fresh_start = 1`, along with total NULL and hold count 0.
      - `checkIn` persists the `freshStart` the engine returns, in the same transaction.
      - `resetProgress` and `overwriteCounter` write `fresh_start = 0`. Reset clears the last check-in, so the First rule already covers it; an explicit Current edit means the user chose the position.
      - `duplicate` writes 0.
    - **Tests:**
      - **Engine (Task 4):** on time after a fresh start stays at the start with streak +1 and the flag cleared; a miss after a fresh start stays at the start with streak 1 and the flag cleared; the next on-time check-in after that goes +1; with the flag false, nothing changes.
      - **Repository (Task 11):** switchMode, then checkIn, gives the start; a second checkIn the next day gives +1.
14. **Cosmetic:** §9.5 says "the validation matrix is §3.1 below", but §3.1 is above it.
15. **The brainstorm's simulation isn't in the repo.** The expected positions in Tasks 4 and 6 are computed from §2 and §9.1. They agree with §2's one example: 16 kg × 8 plus a 6-day absence lands on 14 kg × 8. "A 6-day absence" means the check-ins are 168 h apart: the penalty is 6, the raw level is 14 (12 kg × 12), and the floor is 15 (14 kg × 8).
16. **Error precedence in `overwriteCounter`:** a total of exactly 0 is now checked inside the transaction (0 is a valid level in a weight mode). So `overwriteCounter(missingId, total = 0, …)` throws `EntryNotFound` instead of `IllegalArgumentException`. Everything else is unchanged: Reps still rejects 0, and a negative total is still rejected up front.

## File structure

| File | Status | Responsibility |
| --- | --- | --- |
| `domain/model/Weights.kt` | create | `ProgressMode`, `WeightUnit`, `WeightsKind`, `WeightSteps`, `WeightHold`, `WeightConfig` |
| `domain/model/ProgressionConfig.kt` | modify | `+ mode`, `+ weight` (last, with defaults) |
| `domain/model/CheckInPoint.kt` | modify | `+ weight`, `reps`, `unit` (defaults null) |
| `domain/WeightConversion.kt` | create | kg↔lb at 0.25, one value or a whole `WeightConfig` |
| `domain/ProgressionScale.kt` | create | `Prescription`, `ProgressionScale` (Reps / Weight / RepsThenWeight), `ladderOf`, `scale()`, `startLevel()`, `engineConfig()`, `loadAt()` |
| `domain/RepProgression.kt` | modify | `missFloor` parameter, §9.1 order, `checkInByMode` |
| `domain/WeightValidator.kt` | create | `WeightProblem`, `WeightValidator` (§3.1) |
| `domain/WeightRemap.kt` | create | `WeightRemapResult`, `remapWeights` (§9.2), `weightHoldResetNeeded` |
| `domain/WeightUnits.kt` | create | `defaultWeightUnit(country)` |
| `data/WeightCodecs.kt` | create | steps / list / holds text codecs |
| `data/db/EntryEntity.kt`, `data/db/CheckInEntity.kt` | modify | v7 columns |
| `data/db/HiitDatabase.kt` | modify | version 7, `MIGRATION_6_7` |
| `app/schemas/…/7.json` | generated | committed |
| `data/db/EntryDao.kt` | modify | `setWeightConfig`, `switchMode` queries |
| `data/EntryMapping.kt` | modify | weight-group read repair and write, a mode-aware counter, points with load |
| `data/EntryRepository.kt` | modify | `checkIn` load, `setWeightConfig`, `switchMode`, `overwriteCounter` level range |
| `data/AppPreferences.kt` | modify | `weightUnitDefault`, `setWeightUnitDefault` |
| `di/*` | no change | `AppPreferences(store)` and `RoomEntryRepository(...)` keep their call shapes |
| `testutil/FakeEntryRepository.kt` | modify | mirrors every repository change |
| tests | create / append | listed per task |

---

### Task 1: Weight model types

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/model/Weights.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/domain/model/ProgressionConfig.kt:6-16`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/model/WeightConfigTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `enum class ProgressMode { REPS, WEIGHT, REPS_THEN_WEIGHT }` with `val usesWeights: Boolean`;
  - `enum class WeightUnit { KG, LB }` and `enum class WeightsKind { STEPS, LIST }`;
  - `data class WeightSteps(start: Int, step: Int, top: Int)` with `fun expand(): List<Int>` and `WeightSteps.DEFAULT = WeightSteps(2000, 250, 6000)`;
  - `data class WeightHold(weight: Int, reps: Int, forCount: Int)`;
  - `data class WeightConfig(unit: WeightUnit? = null, kind = STEPS, steps = DEFAULT, list: List<Int> = emptyList(), repsPerSet = 10, repMin = 8, repMax = 12, startWeight: Int? = null, startReps: Int? = null, holds: List<WeightHold> = emptyList())`, with `val weights: List<Int>` and the constants `MIN_WEIGHTS = 2`, `MAX_WEIGHTS = 40`, `MAX_WEIGHT = 99_975`, `MAX_REPS = 100`;
  - `ProgressionConfig.mode: ProgressMode = REPS` and `ProgressionConfig.weight: WeightConfig = WeightConfig()`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mitenko.repkit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightConfigTest {
    @Test
    fun `the default steps expand to 20 to 60 in 2_5 steps`() {
        val w = WeightSteps.DEFAULT.expand()
        assertEquals(17, w.size)
        assertEquals(listOf(2000, 2250, 2500), w.take(3))
        assertEquals(6000, w.last())
    }

    @Test
    fun `steps that don't reach the top stop below it`() {
        assertEquals(listOf(2000, 2300, 2600), WeightSteps(2000, 300, 2800).expand())
    }

    @Test
    fun `steps that can't generate expand to nothing`() {
        listOf(WeightSteps(2000, 0, 6000), WeightSteps(0, 250, 6000), WeightSteps(6000, 250, 2000), WeightSteps(2000, -250, 6000))
            .forEach { assertEquals(it.toString(), emptyList<Int>(), it.expand()) }
    }

    @Test
    fun `weights come from the steps or from the list`() {
        val list = listOf(800, 1200, 1600)
        assertEquals(WeightSteps.DEFAULT.expand(), WeightConfig(list = list).weights)
        assertEquals(list, WeightConfig(kind = WeightsKind.LIST, list = list).weights)
    }

    @Test
    fun `a default progression is in Reps mode with the default weight settings`() {
        val p = ProgressionConfig()
        assertEquals(ProgressMode.REPS, p.mode)
        assertEquals(WeightConfig(), p.weight)
        val w = p.weight
        assertNull(w.unit)
        assertEquals(WeightsKind.STEPS, w.kind)
        assertEquals(listOf(10, 8, 12), listOf(w.repsPerSet, w.repMin, w.repMax))
        assertNull(w.startWeight)
        assertNull(w.startReps)
        assertTrue(w.holds.isEmpty())
    }

    @Test
    fun `only the two weight modes use weights`() {
        assertFalse(ProgressMode.REPS.usesWeights)
        assertTrue(ProgressMode.WEIGHT.usesWeights)
        assertTrue(ProgressMode.REPS_THEN_WEIGHT.usesWeights)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightConfigTest*"`
Expected: FAIL. `compileDebugUnitTestKotlin` reports "Unresolved reference 'WeightSteps'" (and `WeightConfig`, `ProgressMode`, `mode`, `weight`).

- [ ] **Step 3: Write the minimal implementation**

Create `domain/model/Weights.kt`:

```kotlin
package com.mitenko.repkit.domain.model

/** What a Counter check-in moves (spec rev 26 §1). Stored by name in `entry.progress_mode`. */
enum class ProgressMode {
    REPS,
    WEIGHT,
    REPS_THEN_WEIGHT,
    ;

    /** True for the two modes whose level is a rung on a weight ladder (spec rev 26 §2). */
    val usesWeights: Boolean get() = this != REPS
}

/** A workout's unit (spec rev 26 §2 Units). Stored by name in `entry.weight_unit` and `check_in.unit`. */
enum class WeightUnit { KG, LB }

/** Where a workout's weights come from (spec rev 26 §2 Weights): Steps or My weights. */
enum class WeightsKind { STEPS, LIST }

/**
 * Steps (spec rev 26 §2): [start], [step] and [top] in integer hundredths of the workout's unit, so
 * 2000 / 250 / 6000 is 20 / 2.5 / 60. WeightValidator checks the values; [expand] never throws.
 */
data class WeightSteps(val start: Int, val step: Int, val top: Int) {
    /** start, start + step, … while ≤ top. Empty when nothing can be generated (start or step ≤ 0, top < start). */
    fun expand(): List<Int> {
        if (step <= 0 || start <= 0 || top < start) return emptyList()
        return (start..top).step(step).toList()
    }

    companion object {
        /** What `entry.weight_steps` '' reads as (spec rev 26 §5): 20 / 2.5 / 60. */
        val DEFAULT = WeightSteps(2000, 250, 6000)
    }
}

/**
 * A weight-mode hold (spec rev 26 §2 Holds), stored by value in `entry.weight_holds` (plan Spec
 * note 2): [weight] in hundredths, [reps] (Reps then weight; Weight mode ignores it) and [forCount]
 * check-ins, counting the day it's reached.
 */
data class WeightHold(val weight: Int, val reps: Int, val forCount: Int)

/**
 * A Counter workout's weight settings (spec rev 26 §2, §5). Every mode keeps them, so switching away
 * and back restores them. [unit] is null only while the workout has never been in a weight mode
 * (§9.3). [startWeight] null means the lightest weight; [startReps] null means [repMin].
 */
data class WeightConfig(
    val unit: WeightUnit? = null,
    val kind: WeightsKind = WeightsKind.STEPS,
    val steps: WeightSteps = WeightSteps.DEFAULT,
    val list: List<Int> = emptyList(),
    val repsPerSet: Int = 10,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val startWeight: Int? = null,
    val startReps: Int? = null,
    val holds: List<WeightHold> = emptyList(),
) {
    /** The ladder in hundredths: the expanded steps, or the list as stored (sorted on save). */
    val weights: List<Int>
        get() = when (kind) {
            WeightsKind.STEPS -> steps.expand()
            WeightsKind.LIST -> list
        }

    companion object {
        const val MIN_WEIGHTS = 2
        const val MAX_WEIGHTS = 40

        /** 999.75 in the unit (spec rev 26 §2 Limits). */
        const val MAX_WEIGHT = 99_975
        const val MAX_REPS = 100
    }
}
```

In `domain/model/ProgressionConfig.kt`, replace:

```kotlin
    /** The Hold switch (spec R3 §5.1, rev 16 §2). Off keeps every hold stored, but unused. */
    val hold: Boolean = true,
) {
```

with:

```kotlin
    /** The Hold switch (spec R3 §5.1, rev 16 §2). Off keeps every hold stored, but unused. All modes share it. */
    val hold: Boolean = true,
    /** Spec rev 26 §1: what a check-in moves. In a weight mode, CounterState.total is the level on the ladder. */
    val mode: ProgressMode = ProgressMode.REPS,
    /** Spec rev 26 §2: the weight settings, kept in every mode. */
    val weight: WeightConfig = WeightConfig(),
) {
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightConfigTest*" --tests "*ConfigsTest*" --tests "*RepProgressionTest*"`
Expected: PASS. The two existing classes still pass, because the new fields come last and have defaults.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/model/Weights.kt \
  app/src/main/kotlin/com/mitenko/repkit/domain/model/ProgressionConfig.kt \
  app/src/test/kotlin/com/mitenko/repkit/domain/model/WeightConfigTest.kt
git commit -m "Add the weight model types and the progression's mode and weight settings"
```

---

### Task 2: Unit conversion

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/WeightConversion.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightConversionTest.kt`

**Interfaces:**
- Consumes: `WeightUnit`, `WeightConfig`, `WeightsKind`, `WeightConfig.MAX_WEIGHT` (Task 1), and `roundHalfUp(Double): Int` (existing, `domain/Rounding.kt`).
- Produces:
  - `object WeightConversion { const val LB_PER_KG = 2.20462 }`;
  - `WeightConversion.convert(hundredths: Int, from: WeightUnit, to: WeightUnit): Int`;
  - `WeightConversion.convert(config: WeightConfig, to: WeightUnit): WeightConfig`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightUnit.KG
import com.mitenko.repkit.domain.model.WeightUnit.LB
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightConversionTest {
    @Test
    fun `kg to lb rounds to the nearest quarter`() {
        assertEquals(4400, WeightConversion.convert(2000, KG, LB)) // 44.09 → 44
        assertEquals(4950, WeightConversion.convert(2250, KG, LB)) // 49.60 → 49.5
        assertEquals(3525, WeightConversion.convert(1600, KG, LB)) // 35.27 → 35.25
    }

    @Test
    fun `lb to kg rounds to the nearest quarter`() {
        assertEquals(2050, WeightConversion.convert(4500, LB, KG)) // 20.41 → 20.5
        assertEquals(1600, WeightConversion.convert(3500, LB, KG)) // 15.876 → 16
        assertEquals(1125, WeightConversion.convert(2500, LB, KG)) // 11.34 → 11.25
    }

    @Test
    fun `the same unit is unchanged`() {
        assertEquals(2257, WeightConversion.convert(2257, KG, KG))
    }

    @Test
    fun `a result stays between a quarter and 999_75`() {
        assertEquals(25, WeightConversion.convert(1, LB, KG))
        assertEquals(WeightConfig.MAX_WEIGHT, WeightConversion.convert(WeightConfig.MAX_WEIGHT, KG, LB))
    }

    @Test
    fun `a config converts its weights, starting weight and holds, and steps become a list`() {
        val kg = WeightConfig(unit = KG, startWeight = 2250, holds = listOf(WeightHold(2500, 8, 4)))
        val lb = WeightConversion.convert(kg, LB)
        assertEquals(LB, lb.unit)
        assertEquals(WeightsKind.LIST, lb.kind)
        assertEquals(17, lb.list.size)
        assertEquals(listOf(4400, 4950), lb.list.take(2))
        assertEquals(13225, lb.list.last())
        assertEquals(4950, lb.startWeight)
        assertEquals(listOf(WeightHold(5500, 8, 4)), lb.holds)
        assertEquals(kg.steps, lb.steps)
    }

    @Test
    fun `weights that round to the same value collapse, keeping the first`() {
        val lb = WeightConfig(unit = LB, kind = WeightsKind.LIST, list = listOf(100, 125, 1000))
        assertEquals(listOf(50, 450), WeightConversion.convert(lb, KG).list)
    }

    @Test
    fun `a config without a unit only gains one, and its own unit is a no-op`() {
        assertEquals(WeightConfig(unit = LB), WeightConversion.convert(WeightConfig(), LB))
        val kg = WeightConfig(unit = KG)
        assertEquals(kg, WeightConversion.convert(kg, KG))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightConversionTest*"`
Expected: FAIL with "Unresolved reference 'WeightConversion'".

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind

/** kg ↔ lb (spec rev 26 §2 Units), on integer hundredths. Pure. */
object WeightConversion {
    const val LB_PER_KG = 2.20462
    private const val QUARTER = 25

    /** [hundredths] from [from] to [to], to the nearest 0.25 (half up), kept within 0.25..999.75. The same unit returns it as is. */
    fun convert(hundredths: Int, from: WeightUnit, to: WeightUnit): Int {
        if (from == to) return hundredths
        val raw = if (to == WeightUnit.LB) hundredths * LB_PER_KG else hundredths / LB_PER_KG
        return (roundHalfUp(raw / QUARTER) * QUARTER).coerceIn(QUARTER, WeightConfig.MAX_WEIGHT)
    }

    /**
     * [config] in [to] (spec rev 26 §2 Units, §9.2): the weights, the starting weight and every hold
     * convert with the same rounding, so the remap that follows compares exact values. Steps become
     * My weights, because a converted step isn't one of the step choices (plan Spec note 8). Weights that
     * round to one value collapse, keeping the first. A config without a unit only gains [to].
     */
    fun convert(config: WeightConfig, to: WeightUnit): WeightConfig {
        val from = config.unit ?: return config.copy(unit = to)
        if (from == to) return config
        fun c(v: Int) = convert(v, from, to)
        return config.copy(
            unit = to,
            kind = WeightsKind.LIST,
            list = config.weights.map(::c).distinct(),
            startWeight = config.startWeight?.let(::c),
            holds = config.holds.map { it.copy(weight = c(it.weight)) },
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightConversionTest*"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/WeightConversion.kt \
  app/src/test/kotlin/com/mitenko/repkit/domain/WeightConversionTest.kt
git commit -m "Add kg and lb conversion rounded to the nearest quarter"
```

---

### Task 3: ProgressionScale

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/ProgressionScale.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/ProgressionScaleTest.kt`

**Interfaces:**
- Consumes: `ProgressionConfig.mode` and `.weight`, `WeightConfig.weights`, `WeightHold` (Task 1), and `Hold` (existing).
- Produces:
  - `sealed interface Prescription`, with `data class RepTotal(total: Int)` and `data class Load(weight: Int, reps: Int)`;
  - `sealed interface ProgressionScale { val minLevel: Int; val maxLevel: Int; fun missFloor(start: Int): Int; fun prescription(level: Int): Prescription }`;
  - `ProgressionScale.Reps(floor: Int, cap: Int)`;
  - `sealed interface ProgressionScale.Ladder { val weights: List<Int>; override fun prescription(level: Int): Prescription.Load; fun levelOf(weight: Int, reps: Int): Int; fun weightIndexOf(weight: Int): Int }`;
  - `ProgressionScale.Weight(weights, repsPerSet)` and `ProgressionScale.RepsThenWeight(weights, repMin, repMax)` (with `val span`);
  - `fun ladderOf(mode: ProgressMode, weight: WeightConfig): ProgressionScale.Ladder`;
  - `fun ProgressionConfig.scale(): ProgressionScale`, `fun ProgressionConfig.startLevel(): Int`, `fun ProgressionConfig.engineConfig(): ProgressionConfig` and `fun ProgressionConfig.loadAt(level: Int): Prescription.Load?`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.testutil.expectThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionScaleTest {
    private val curls = listOf(800, 1000, 1200, 1400, 1600)
    private val rtw = ProgressionScale.RepsThenWeight(curls, 8, 12)
    private val weight = ProgressionScale.Weight(curls, 10)
    private val curlsConfig = ProgressionConfig(
        mode = ProgressMode.REPS_THEN_WEIGHT,
        weight = WeightConfig(kind = WeightsKind.LIST, list = curls),
    )

    @Test
    fun `Reps then weight levels map to weight x reps and back`() {
        assertEquals(listOf(0, 24, 5), listOf(rtw.minLevel, rtw.maxLevel, rtw.span))
        assertEquals(Prescription.Load(800, 8), rtw.prescription(0))
        assertEquals(Prescription.Load(800, 12), rtw.prescription(4))
        assertEquals(Prescription.Load(1000, 8), rtw.prescription(5))
        assertEquals(Prescription.Load(1600, 8), rtw.prescription(20))
        assertEquals(Prescription.Load(1600, 12), rtw.prescription(24))
        for (level in 0..24) {
            val p = rtw.prescription(level)
            assertEquals(level, rtw.levelOf(p.weight, p.reps))
        }
    }

    @Test
    fun `Weight levels are weight indexes with fixed reps`() {
        assertEquals(listOf(0, 4), listOf(weight.minLevel, weight.maxLevel))
        assertEquals(Prescription.Load(1400, 10), weight.prescription(3))
        for (level in 0..4) assertEquals(level, weight.levelOf(weight.prescription(level).weight, 10))
    }

    @Test
    fun `a level outside the ladder is clamped`() {
        assertEquals(Prescription.Load(800, 8), rtw.prescription(-3))
        assertEquals(Prescription.Load(1600, 12), rtw.prescription(99))
        assertEquals(Prescription.Load(1600, 10), weight.prescription(7))
    }

    @Test
    fun `levelOf takes the nearest lower weight, else the lightest, and clamps the reps`() {
        assertEquals(2, weight.levelOf(1300, 10))
        assertEquals(0, weight.levelOf(500, 10))
        assertEquals(10, rtw.levelOf(1300, 3))   // 12 kg × 8
        assertEquals(14, rtw.levelOf(1200, 15))  // 12 kg × 12
        assertEquals(1, rtw.levelOf(500, 9))     // no weight ≤ 5 kg: the lightest, 8 kg × 9
    }

    @Test
    fun `missFloor is the first level of the next lighter weight`() {
        assertEquals(15, rtw.missFloor(20))
        assertEquals(15, rtw.missFloor(24))
        assertEquals(5, rtw.missFloor(13))
        assertEquals(0, rtw.missFloor(7))
        assertEquals(0, rtw.missFloor(3))
        assertEquals(0, rtw.missFloor(0))
        assertEquals(3, weight.missFloor(4))
        assertEquals(0, weight.missFloor(0))
    }

    @Test
    fun `Reps mode is the identity with no miss floor`() {
        val reps = ProgressionConfig().scale()
        assertEquals(ProgressionScale.Reps(48, 72), reps)
        assertEquals(Prescription.RepTotal(64), reps.prescription(64))
        assertEquals(Int.MIN_VALUE, reps.missFloor(60))
        assertEquals(ProgressionConfig(), ProgressionConfig().engineConfig())
        assertEquals(48, ProgressionConfig().startLevel())
        assertNull(ProgressionConfig().loadAt(60))
    }

    @Test
    fun `the start level comes from the starting weight and reps`() {
        assertEquals(0, curlsConfig.startLevel())
        val start = curlsConfig.weight.copy(startWeight = 1200, startReps = 10)
        assertEquals(12, curlsConfig.copy(weight = start).startLevel())
        assertEquals(2, curlsConfig.copy(mode = ProgressMode.WEIGHT, weight = start).startLevel())
    }

    @Test
    fun `the engine config runs on levels, with the weight holds as level holds`() {
        val c = curlsConfig.copy(weight = curlsConfig.weight.copy(startWeight = 1000, holds = listOf(WeightHold(1400, 8, 4))))
        val e = c.engineConfig()
        assertEquals(listOf(5, 0, 24), listOf(e.startingTotal, e.floor, e.cap))
        assertEquals(listOf(Hold(15, 4)), e.holds)
        assertEquals(listOf(c.windowHours, c.hold), listOf(e.windowHours, e.hold))
        assertEquals(Prescription.Load(1400, 8), c.loadAt(15))
    }

    @Test
    fun `the default steps give a Weight ladder from 20 to 60`() {
        val s = ProgressionConfig(mode = ProgressMode.WEIGHT).scale()
        assertEquals(16, s.maxLevel)
        assertEquals(Prescription.Load(2250, 10), s.prescription(1))
    }

    @Test
    fun `Reps mode has no ladder`() {
        expectThrows<IllegalArgumentException> { ladderOf(ProgressMode.REPS, WeightConfig()) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*ProgressionScaleTest*"`
Expected: FAIL with "Unresolved reference 'ProgressionScale'".

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig

/** What one level asks for (spec rev 26 §2). */
sealed interface Prescription {
    /** Reps mode: the rep total, split across the sets by RepDistributor. */
    data class RepTotal(val total: Int) : Prescription

    /** A weight mode: [weight] in hundredths of the workout's unit, [reps] per set. */
    data class Load(val weight: Int, val reps: Int) : Prescription
}

/**
 * Spec rev 26 §2: maps the integer RepProgression moves (CounterState.total; a "level" in code,
 * because Entry.position is the list order) to a prescription and back. Pure.
 */
sealed interface ProgressionScale {
    val minLevel: Int
    val maxLevel: Int

    /** The lowest level a miss from [start] (already clamped) may land on (spec rev 26 §9.1 step 3). */
    fun missFloor(start: Int): Int

    fun prescription(level: Int): Prescription

    /** Today's Reps mode: the level is the rep total, floor..cap. */
    data class Reps(val floor: Int, val cap: Int) : ProgressionScale {
        override val minLevel: Int get() = floor
        override val maxLevel: Int get() = cap

        /** No floor of its own (plan Spec note 1), so a Reps miss is exactly today's. */
        override fun missFloor(start: Int): Int = Int.MIN_VALUE

        override fun prescription(level: Int): Prescription.RepTotal = Prescription.RepTotal(level)
    }

    /** The two weight modes. Level 0 is the lightest weight (× the lowest reps); [weights] is ascending and non-empty. */
    sealed interface Ladder : ProgressionScale {
        val weights: List<Int>
        override val minLevel: Int get() = 0

        override fun prescription(level: Int): Prescription.Load

        /** Spec rev 26 §9.2 steps 1–3: the level of [weight] (by weightIndexOf) × [reps] clamped into the range. */
        fun levelOf(weight: Int, reps: Int): Int

        /** §9.2 step 1: the largest index whose weight is ≤ [weight], or 0 (the lightest) when none is. */
        fun weightIndexOf(weight: Int): Int = weights.indexOfLast { it <= weight }.coerceAtLeast(0)
    }

    /** Weight: one level per weight; the reps per set are fixed. */
    data class Weight(override val weights: List<Int>, val repsPerSet: Int) : Ladder {
        init {
            require(weights.isNotEmpty()) { "A ladder needs at least one weight" }
        }

        override val maxLevel: Int get() = weights.lastIndex

        /** At most one lighter weight (spec rev 26 §2): index − 1, never below 0. */
        override fun missFloor(start: Int): Int = (start.coerceIn(minLevel, maxLevel) - 1).coerceAtLeast(0)

        override fun prescription(level: Int): Prescription.Load =
            Prescription.Load(weights[level.coerceIn(minLevel, maxLevel)], repsPerSet)

        override fun levelOf(weight: Int, reps: Int): Int = weightIndexOf(weight)
    }

    /** Reps then weight: [span] levels per weight; weightIndex = level / span, reps = repMin + level % span. */
    data class RepsThenWeight(override val weights: List<Int>, val repMin: Int, val repMax: Int) : Ladder {
        init {
            require(weights.isNotEmpty()) { "A ladder needs at least one weight" }
            require(repMin in 1..repMax) { "Invalid rep range $repMin..$repMax" }
        }

        val span: Int get() = repMax - repMin + 1
        override val maxLevel: Int get() = weights.size * span - 1

        /** The first level of the next lighter weight (spec rev 26 §2): (weightIndex − 1) × span, never below 0. */
        override fun missFloor(start: Int): Int = ((start.coerceIn(minLevel, maxLevel) / span - 1) * span).coerceAtLeast(0)

        override fun prescription(level: Int): Prescription.Load {
            val l = level.coerceIn(minLevel, maxLevel)
            return Prescription.Load(weights[l / span], repMin + l % span)
        }

        override fun levelOf(weight: Int, reps: Int): Int =
            weightIndexOf(weight) * span + (reps.coerceIn(repMin, repMax) - repMin)
    }
}

/** The ladder of weight mode [mode] over [weight]'s weights (spec rev 26 §2). Reps mode has none. */
fun ladderOf(mode: ProgressMode, weight: WeightConfig): ProgressionScale.Ladder = when (mode) {
    ProgressMode.WEIGHT -> ProgressionScale.Weight(weight.weights, weight.repsPerSet)
    ProgressMode.REPS_THEN_WEIGHT -> ProgressionScale.RepsThenWeight(weight.weights, weight.repMin, weight.repMax)
    ProgressMode.REPS -> throw IllegalArgumentException("Reps mode has no ladder")
}

/** This config's scale (spec rev 26 §2). A weight mode needs a non-empty, valid weight group (EntryMapping guarantees one). */
fun ProgressionConfig.scale(): ProgressionScale =
    if (mode == ProgressMode.REPS) ProgressionScale.Reps(floor, cap) else ladderOf(mode, weight)

/** Where an untouched counter is (spec rev 26 §2 Starting point): startingTotal, or the starting weight × starting reps. */
fun ProgressionConfig.startLevel(): Int = when (val s = scale()) {
    is ProgressionScale.Reps -> startingTotal
    is ProgressionScale.Ladder -> s.levelOf(weight.startWeight ?: s.weights.first(), weight.startReps ?: weight.repMin)
}

/**
 * The config RepProgression runs on (spec rev 26 §2): itself in Reps mode. In a weight mode it is the
 * ladder's levels as floor..cap, the start level as the starting total and the weight holds as level
 * holds; the window, penalty and Hold switch are kept.
 */
fun ProgressionConfig.engineConfig(): ProgressionConfig = when (val s = scale()) {
    is ProgressionScale.Reps -> this
    is ProgressionScale.Ladder -> copy(
        startingTotal = startLevel(),
        floor = s.minLevel,
        cap = s.maxLevel,
        holds = weight.holds.map { Hold(s.levelOf(it.weight, it.reps), it.forCount) },
    )
}

/** The load at [level] in a weight mode (spec rev 26 §9.3), or null in Reps mode. */
fun ProgressionConfig.loadAt(level: Int): Prescription.Load? = (scale() as? ProgressionScale.Ladder)?.prescription(level)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*ProgressionScaleTest*"`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/ProgressionScale.kt \
  app/src/test/kotlin/com/mitenko/repkit/domain/ProgressionScaleTest.kt
git commit -m "Add ProgressionScale to map levels to weight and reps"
```

---

### Task 4: missFloor in RepProgression (§9.1)

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/domain/RepProgression.kt:22-33` (signature), `:62-66` (rule 3), plus a new function
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightProgressionTest.kt`

**Interfaces:**
- Consumes: `scale()`, `engineConfig()`, `startLevel()`, `ProgressionScale.Ladder` and `Prescription.Load` (Task 3).
- Produces:
  - `RepProgression.checkIn(state, config, now, zone, countsReps = true, missFloor: (Int) -> Int = RepProgression.NO_MISS_FLOOR)`;
  - `RepProgression.NO_MISS_FLOOR: (Int) -> Int`;
  - `RepProgression.checkInByMode(state: CounterState, config: ProgressionConfig, now: Instant, zone: ZoneId, countsReps: Boolean = true): CheckInResult`.

  Callers (repository, fake) use `checkInByMode` from Task 10 on.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.CounterState
import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Spec rev 26 §6 and §9.1, on the curls ladder: 8/10/12/14/16 kg × 8–12 reps, 3 sets. */
class WeightProgressionTest {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val t0: Instant = ZonedDateTime.of(2026, 9, 1, 7, 0, 0, 0, zone).toInstant()
    private val curlsWeights = listOf(800, 1000, 1200, 1400, 1600)

    private fun curls(holds: List<WeightHold> = emptyList(), mode: ProgressMode = ProgressMode.REPS_THEN_WEIGHT) =
        ProgressionConfig(
            mode = mode,
            weight = WeightConfig(
                unit = WeightUnit.KG, kind = WeightsKind.LIST, list = curlsWeights,
                repsPerSet = 10, repMin = 8, repMax = 12, holds = holds,
            ),
        )

    private fun ladder(c: ProgressionConfig) = c.scale() as ProgressionScale.Ladder

    private fun level(c: ProgressionConfig, weight: Int, reps: Int) = ladder(c).levelOf(weight, reps)

    private fun load(c: ProgressionConfig, level: Int) = ladder(c).prescription(level).let { it.weight to it.reps }

    private fun hoursLater(h: Long): Instant = t0.plusSeconds(h * 3600)

    /** At [weight] × [reps], last checked in at t0, streak 5. */
    private fun at(c: ProgressionConfig, weight: Int, reps: Int, holdCount: Int = 0) =
        CounterState(total = level(c, weight, reps), bestStreak = 5, currentStreak = 5, lastCheckIn = t0, holdCount = holdCount)

    @Test
    fun `the climb adds a rep per set, resets to 8 at each new weight and stops at the top`() {
        val c = curls()
        var state = CounterState(total = c.startLevel())
        val seen = (0..25).map { day ->
            state = RepProgression.checkInByMode(state, c, t0.plusSeconds(day * 24L * 3600), zone).state
            load(c, state.total)
        }
        val climb = curlsWeights.flatMap { w -> (8..12).map { r -> w to r } }
        assertEquals(climb + (1600 to 12), seen)
        assertEquals(1000 to 8, seen[5])
        assertEquals(1200 to 8, seen[10])
    }

    private data class Miss(
        val name: String,
        val from: Pair<Int, Int>,
        val hours: Long,
        val penalty: Int,
        val to: Pair<Int, Int>,
        val holdCount: Int = 0,
        val holds: List<WeightHold> = emptyList(),
        val mode: ProgressMode = ProgressMode.REPS_THEN_WEIGHT,
    )

    @Test
    fun `misses clamp, take the penalty, stop at the miss floor, then check the hold (spec 9_1)`() {
        val cases = listOf(
            Miss("inside the same weight (−1)", 1200 to 11, 60, 1, 1200 to 10),
            Miss("across one weight boundary (−3)", 1200 to 9, 96, 3, 1000 to 11),
            Miss("a long miss stops at the next lighter weight", 1600 to 8, 168, 6, 1400 to 8),
            Miss("landing on an active hold restarts it", 1600 to 8, 168, 6, 1400 to 8, holdCount = 1, holds = listOf(WeightHold(1400, 8, 4))),
            Miss("position 0 is a no-op", 800 to 8, 96, 3, 800 to 8),
            Miss("from the top", 1600 to 12, 96, 3, 1600 to 9),
            Miss("Weight: at most one lighter weight", 1600 to 10, 168, 6, 1400 to 10, mode = ProgressMode.WEIGHT),
            Miss("Weight: the lightest is a no-op", 800 to 10, 96, 3, 800 to 10, mode = ProgressMode.WEIGHT),
        )
        for (m in cases) {
            val c = curls(m.holds, m.mode)
            val r = RepProgression.checkInByMode(at(c, m.from.first, m.from.second), c, hoursLater(m.hours), zone)
            assertEquals(m.name, Outcome.Missed(m.penalty), r.outcome)
            assertEquals(m.name, m.to, load(c, r.state.total))
            assertEquals(m.name, m.holdCount, r.state.holdCount)
            assertEquals(m.name, 1, r.state.currentStreak)
        }
    }

    @Test
    fun `without the miss floor the same long miss would drop to 12 kg x 12`() {
        val c = curls()
        val r = RepProgression.checkIn(at(c, 1600, 8), c.engineConfig(), hoursLater(168), zone)
        assertEquals(1200 to 12, load(c, r.state.total))
    }

    @Test
    fun `a hold on 12 kg x 8 holds for its count, counting the day it's reached`() {
        val c = curls(holds = listOf(WeightHold(1200, 8, 2)))
        var s = at(c, 1000, 12)
        val seen = (1..3).map { day ->
            s = RepProgression.checkInByMode(s, c, hoursLater(24L * day), zone).state
            load(c, s.total) to s.holdCount
        }
        assertEquals(listOf((1200 to 8) to 1, (1200 to 8) to 2, (1200 to 9) to 0), seen)
    }

    @Test
    fun `Reps mode is exactly checkIn without a miss floor`() {
        val c = ProgressionConfig(holds = listOf(Hold(56, 2), Hold(64, 4)))
        for (total in listOf(40, 48, 56, 60, 64, 72, 80)) {
            for (hours in listOf(10L, 24L, 37L, 60L, 96L, 168L, 400L)) {
                for (holdCount in 0..4) {
                    val s = CounterState(total, 9, 5, t0, holdCount)
                    assertEquals(
                        "$total/$hours/$holdCount",
                        RepProgression.checkIn(s, c, hoursLater(hours), zone),
                        RepProgression.checkInByMode(s, c, hoursLater(hours), zone),
                    )
                }
            }
        }
        val miss = RepProgression.checkInByMode(CounterState(60, 9, 5, t0), ProgressionConfig(), hoursLater(168), zone)
        assertEquals(Outcome.Missed(6) to 54, miss.outcome to miss.state.total)
        assertEquals(48, RepProgression.checkInByMode(CounterState(50, 9, 5, t0), ProgressionConfig(), hoursLater(168), zone).state.total)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightProgressionTest*"`
Expected: FAIL with "Unresolved reference 'checkInByMode'".

- [ ] **Step 3: Write the minimal implementation**

In `domain/RepProgression.kt`, replace:

```kotlin
    /**
     * [countsReps] is false for a Timer only entry (spec R4 §3.1): rules 1–4 still decide the
     * outcome and the streaks, and [CounterState.lastCheckIn] becomes [now], but the total and the
     * hold count are kept exactly (no +1, no penalty, no clamp, no hold). A miss reports a penalty of 0.
     */
    fun checkIn(
        state: CounterState,
        config: ProgressionConfig,
        now: Instant,
        zone: ZoneId,
        countsReps: Boolean = true,
    ): CheckInResult {
```

with:

```kotlin
    /** Reps mode's miss floor: none, so a miss is exactly `max(floor, total − penalty)` (plan Spec note 1). */
    val NO_MISS_FLOOR: (Int) -> Int = { Int.MIN_VALUE }

    /**
     * [countsReps] is false for a Timer only entry (spec R4 §3.1): rules 1–4 still decide the
     * outcome and the streaks, and [CounterState.lastCheckIn] becomes [now], but the total and the
     * hold count are kept exactly (no +1, no penalty, no clamp, no hold). A miss reports a penalty of 0.
     * [missFloor] (spec rev 26 §9.1) is the lowest total a miss from the clamped total may land on.
     */
    fun checkIn(
        state: CounterState,
        config: ProgressionConfig,
        now: Instant,
        zone: ZoneId,
        countsReps: Boolean = true,
        missFloor: (Int) -> Int = NO_MISS_FLOOR,
    ): CheckInResult {
```

Replace:

```kotlin
            val penalty = max(0, roundHalfUp((hours - 24) / config.penaltyHoursPerRep) - 1)
            val newTotal = max(config.floor, total - penalty)
```

with:

```kotlin
            val penalty = max(0, roundHalfUp((hours - 24) / config.penaltyHoursPerRep) - 1)
            // Spec rev 26 §9.1: clamp (above), penalty, the miss floor measured from the clamped start, then the minimum.
            val newTotal = maxOf(total - penalty, missFloor(total), config.floor)
```

Then add this function right after `checkIn`'s closing brace, before `streaksOnly`:

```kotlin
    /**
     * Spec rev 26 §2: [checkIn] on [config]'s levels, using its engine config and its scale's miss floor.
     * In Reps mode this is exactly [checkIn]. Callers with an entry use this, never [checkIn] directly.
     */
    fun checkInByMode(
        state: CounterState,
        config: ProgressionConfig,
        now: Instant,
        zone: ZoneId,
        countsReps: Boolean = true,
    ): CheckInResult {
        val scale = config.scale()
        return checkIn(state, config.engineConfig(), now, zone, countsReps, scale::missFloor)
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightProgressionTest*" --tests "*RepProgressionTest*"`
Expected: PASS. Every existing `RepProgressionTest` case still passes; it is the Reps byte-for-byte guard.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/RepProgression.kt \
  app/src/test/kotlin/com/mitenko/repkit/domain/WeightProgressionTest.kt
git commit -m "Add the miss floor to RepProgression and check in by progress mode"
```

---

### Task 5: Weight validation (§3.1)

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/WeightValidator.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightValidatorTest.kt`

**Interfaces:**
- Consumes: `WeightConfig`, `WeightSteps`, `WeightHold`, `WeightsKind` and `ProgressMode` (Task 1), and `ProgressionConfig.MAX_HOLDS` (existing).
- Produces:
  - `sealed interface WeightProblem`, whose members are `TooFewWeights`, `TooManyWeights`, `WeightOutOfRange(index)`, `DuplicateWeight(index)`, `StepsStart`, `StepsStep`, `StepsTop`, `RepsPerSet`, `RepMin`, `RepMax`, `StartWeight`, `StartReps`, `TooManyHolds`, `HoldWeight(index)`, `HoldReps(index)`, `HoldFor(index)` and `DuplicateHold(index)`;
  - `WeightValidator.STEP_CHOICES: List<Int>`;
  - `WeightValidator.validate(c: WeightConfig, mode: ProgressMode): List<WeightProblem>` (empty means valid).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.WeightProblem.*
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightValidatorTest {
    private val rtw = ProgressMode.REPS_THEN_WEIGHT
    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600))

    private fun problems(c: WeightConfig, mode: ProgressMode = rtw) = WeightValidator.validate(c, mode)

    @Test
    fun `the defaults and the curls ladder are valid in every mode`() {
        for (mode in ProgressMode.entries) {
            assertEquals(mode.name, emptyList<WeightProblem>(), problems(WeightConfig(), mode))
            assertEquals(mode.name, emptyList<WeightProblem>(), problems(curls, mode))
        }
    }

    @Test
    fun `My weights needs 2 to 40 weights in range with no duplicates`() {
        assertEquals(listOf(TooFewWeights), problems(curls.copy(list = listOf(800))))
        assertEquals(listOf(TooManyWeights), problems(curls.copy(list = (1..41).map { it * 100 })))
        assertEquals(listOf(WeightOutOfRange(1)), problems(curls.copy(list = listOf(800, 0))))
        assertEquals(listOf(WeightOutOfRange(1)), problems(curls.copy(list = listOf(800, 100_000))))
        assertEquals(listOf(DuplicateWeight(2)), problems(curls.copy(list = listOf(800, 1200, 800))))
    }

    @Test
    fun `Steps need a positive start, an offered step and a top the step divides`() {
        fun steps(start: Int, step: Int, top: Int) = problems(WeightConfig(steps = WeightSteps(start, step, top)))
        assertEquals(listOf(StepsStart), steps(0, 250, 6000))
        assertEquals(listOf(StepsStep), steps(2000, 300, 6000))
        assertEquals(listOf(StepsTop), steps(2000, 250, 6100))
        assertEquals(listOf(StepsTop), steps(2000, 250, 2000))
        assertEquals(listOf(StepsTop), steps(2000, 250, 100_000))
        assertEquals(listOf(TooManyWeights), steps(50, 50, 5000))
        WeightValidator.STEP_CHOICES.forEach { assertEquals(emptyList<WeightProblem>(), steps(1000, it, 1000 + 4 * it)) }
    }

    @Test
    fun `reps per set and the rep range are 1 to 100 with min below max`() {
        assertEquals(listOf(RepsPerSet), problems(curls.copy(repsPerSet = 0)))
        assertEquals(listOf(RepsPerSet), problems(curls.copy(repsPerSet = 101)))
        assertEquals(listOf(RepMin), problems(curls.copy(repMin = 0)))
        assertEquals(listOf(RepMax), problems(curls.copy(repMin = 12, repMax = 12)))
        assertEquals(listOf(RepMax), problems(curls.copy(repMax = 101)))
    }

    @Test
    fun `the starting weight is on the ladder and the starting reps in the range`() {
        assertEquals(listOf(StartWeight), problems(curls.copy(startWeight = 900)))
        assertEquals(listOf(StartReps), problems(curls.copy(startReps = 13)))
        assertEquals(emptyList<WeightProblem>(), problems(curls.copy(startWeight = 1200, startReps = 12)))
    }

    @Test
    fun `each hold is on the ladder, in the range, held for 0 or more, and in its own place`() {
        assertEquals(listOf(HoldWeight(0)), problems(curls.copy(holds = listOf(WeightHold(900, 8, 4)))))
        assertEquals(listOf(HoldReps(0)), problems(curls.copy(holds = listOf(WeightHold(1200, 13, 4)))))
        assertEquals(listOf(HoldFor(0)), problems(curls.copy(holds = listOf(WeightHold(1200, 8, -1)))))
        val sameWeight = curls.copy(holds = listOf(WeightHold(1200, 8, 4), WeightHold(1200, 10, 2)))
        assertEquals(emptyList<WeightProblem>(), problems(sameWeight, rtw))
        assertEquals(listOf(DuplicateHold(1)), problems(sameWeight, ProgressMode.WEIGHT))
        val nine = curls.copy(holds = (0 until 9).map { WeightHold(curls.list[it % 5], 8 + it / 5, 1) })
        assertEquals(listOf(TooManyHolds), problems(nine))
    }

    @Test
    fun `a draft with one bad row fails, and fixing that row passes`() {
        val draft = curls.copy(list = listOf(800, 1000, 1000, 1600))
        assertEquals(listOf(DuplicateWeight(2)), problems(draft))
        assertEquals(emptyList<WeightProblem>(), problems(draft.copy(list = listOf(800, 1000, 1200, 1600))))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightValidatorTest*"`
Expected: FAIL with "Unresolved reference 'WeightValidator'".

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightsKind

/**
 * One problem with a weight config (spec rev 26 §3.1). Typed and free of English (plan Spec note 7):
 * PR 2 maps each to its field and a string. Indexes are list positions in [WeightConfig.list] or
 * [WeightConfig.holds].
 */
sealed interface WeightProblem {
    /** My weights: fewer than [WeightConfig.MIN_WEIGHTS]. */
    data object TooFewWeights : WeightProblem

    /** More than [WeightConfig.MAX_WEIGHTS], listed or generated by the steps. */
    data object TooManyWeights : WeightProblem

    /** My weights row [index] is ≤ 0 or over 999.75. */
    data class WeightOutOfRange(val index: Int) : WeightProblem

    /** My weights row [index] repeats an earlier row. */
    data class DuplicateWeight(val index: Int) : WeightProblem

    /** Steps: the start is ≤ 0 or over 999.75. */
    data object StepsStart : WeightProblem

    /** Steps: the step isn't one of [WeightValidator.STEP_CHOICES]. */
    data object StepsStep : WeightProblem

    /** Steps: the top is out of range, not above the start, or not reachable in whole steps. */
    data object StepsTop : WeightProblem

    data object RepsPerSet : WeightProblem

    data object RepMin : WeightProblem

    /** Out of 1..100, or not above the minimum. */
    data object RepMax : WeightProblem

    /** Not one of the current weights. */
    data object StartWeight : WeightProblem

    /** Outside the rep range. */
    data object StartReps : WeightProblem

    data object TooManyHolds : WeightProblem

    /** Hold [index]'s weight isn't on the ladder. */
    data class HoldWeight(val index: Int) : WeightProblem

    /** Hold [index]'s reps are outside the rep range. */
    data class HoldReps(val index: Int) : WeightProblem

    /** Hold [index]'s "for" is below 0. */
    data class HoldFor(val index: Int) : WeightProblem

    /** Hold [index] sits on the same position as an earlier hold (the later one carries it, as in rev 16 §3). */
    data class DuplicateHold(val index: Int) : WeightProblem
}

/** Spec rev 26 §3.1, for every mode: a stored weight group is valid whatever the mode, so switching never breaks it. */
object WeightValidator {
    /** The steps the Step stepper offers, in hundredths: 0.5, 1, 1.25, 2, 2.5, 5. */
    val STEP_CHOICES = listOf(50, 100, 125, 200, 250, 500)

    fun validate(c: WeightConfig, mode: ProgressMode): List<WeightProblem> = buildList {
        when (c.kind) {
            WeightsKind.LIST -> listProblems(c.list)
            WeightsKind.STEPS -> stepsProblems(c.steps)
        }
        if (c.repsPerSet !in 1..WeightConfig.MAX_REPS) add(WeightProblem.RepsPerSet)
        if (c.repMin !in 1..WeightConfig.MAX_REPS) add(WeightProblem.RepMin)
        if (c.repMax !in 1..WeightConfig.MAX_REPS || c.repMax <= c.repMin) add(WeightProblem.RepMax)
        val weights = c.weights.toSet()
        val range = c.repMin..c.repMax
        if (c.startWeight != null && c.startWeight !in weights) add(WeightProblem.StartWeight)
        if (c.startReps != null && c.startReps !in range) add(WeightProblem.StartReps)
        if (c.holds.size > ProgressionConfig.MAX_HOLDS) add(WeightProblem.TooManyHolds)
        val seen = HashSet<Pair<Int, Int>>()
        c.holds.forEachIndexed { i, h ->
            if (h.weight !in weights) add(WeightProblem.HoldWeight(i))
            if (h.reps !in range) add(WeightProblem.HoldReps(i))
            if (h.forCount < 0) add(WeightProblem.HoldFor(i))
            // Plan Spec note 10: a Weight-mode hold sits on its weight alone; otherwise on weight × reps.
            val position = if (mode == ProgressMode.WEIGHT) h.weight to 0 else h.weight to h.reps
            if (!seen.add(position)) add(WeightProblem.DuplicateHold(i))
        }
    }

    private fun MutableList<WeightProblem>.listProblems(list: List<Int>) {
        if (list.size < WeightConfig.MIN_WEIGHTS) add(WeightProblem.TooFewWeights)
        if (list.size > WeightConfig.MAX_WEIGHTS) add(WeightProblem.TooManyWeights)
        val seen = HashSet<Int>()
        list.forEachIndexed { i, w ->
            if (w !in 1..WeightConfig.MAX_WEIGHT) add(WeightProblem.WeightOutOfRange(i))
            else if (!seen.add(w)) add(WeightProblem.DuplicateWeight(i))
        }
    }

    private fun MutableList<WeightProblem>.stepsProblems(s: WeightSteps) {
        val startOk = s.start in 1..WeightConfig.MAX_WEIGHT
        val stepOk = s.step in STEP_CHOICES
        if (!startOk) add(WeightProblem.StepsStart)
        if (!stepOk) add(WeightProblem.StepsStep)
        val topOk = s.top in 1..WeightConfig.MAX_WEIGHT && s.top > s.start && (!stepOk || (s.top - s.start) % s.step == 0)
        if (!topOk) {
            add(WeightProblem.StepsTop)
        } else if (startOk && stepOk && (s.top - s.start) / s.step + 1 > WeightConfig.MAX_WEIGHTS) {
            add(WeightProblem.TooManyWeights)
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightValidatorTest*"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/WeightValidator.kt \
  app/src/test/kotlin/com/mitenko/repkit/domain/WeightValidatorTest.kt
git commit -m "Add typed validation for the weight settings"
```

---

### Task 6: Remap by value (§9.2)

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/WeightRemap.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightRemapTest.kt`

**Interfaces:**
- Consumes: `ladderOf` and `Prescription.Load` (Task 3), and `WeightConversion.convert(config, to)` (Task 2, test only).
- Produces:
  - `data class WeightRemapResult(config: WeightConfig, level: Int?, currentChanged: Boolean)`;
  - `fun remapWeights(mode: ProgressMode, old: WeightConfig, new: WeightConfig, oldLevel: Int?): WeightRemapResult`. Here `mode` is a weight mode, `old` and `new` are in the same unit, and `new` has non-empty, ascending weights with `1 ≤ repMin ≤ repMax`;
  - `fun weightHoldResetNeeded(mode: ProgressMode, old: WeightConfig, remap: WeightRemapResult): Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec rev 26 §9.2 on the curls ladder, 8/10/12/14/16 kg × 8–12. */
class WeightRemapTest {
    private val rtw = ProgressMode.REPS_THEN_WEIGHT
    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600))

    private fun without(vararg weights: Int) = curls.copy(list = curls.list - weights.toSet())

    private fun levelOf(c: WeightConfig, weight: Int, reps: Int, mode: ProgressMode = rtw) = ladderOf(mode, c).levelOf(weight, reps)

    private fun load(c: WeightConfig, level: Int, mode: ProgressMode = rtw) = ladderOf(mode, c).prescription(level)

    @Test
    fun `removing the current weight moves to the nearest lower weight`() {
        val r = remapWeights(rtw, curls, without(1200), oldLevel = levelOf(curls, 1200, 10))
        assertEquals(7, r.level)
        assertEquals(Prescription.Load(1000, 10), load(r.config, r.level!!))
        assertTrue(r.currentChanged)
    }

    @Test
    fun `removing a weight below or above keeps the current weight`() {
        val at = levelOf(curls, 1200, 10)
        val below = remapWeights(rtw, curls, without(800), at)
        assertEquals(7 to false, below.level to below.currentChanged)
        assertEquals(Prescription.Load(1200, 10), load(below.config, 7))
        val above = remapWeights(rtw, curls, without(1600), at)
        assertEquals(12 to false, above.level to above.currentChanged)
    }

    @Test
    fun `removing the lightest while on it moves to the new lightest`() {
        val r = remapWeights(rtw, curls, without(800), oldLevel = 0)
        assertEquals(0, r.level)
        assertEquals(Prescription.Load(1000, 8), load(r.config, 0))
        assertTrue(r.currentChanged)
    }

    @Test
    fun `a step change that skips the current value goes to the nearest lower weight`() {
        val old = WeightConfig(unit = WeightUnit.KG)
        val new = old.copy(steps = WeightSteps(2000, 500, 6000))
        val r = remapWeights(rtw, old, new, levelOf(old, 2250, 9))
        assertEquals(Prescription.Load(2000, 9), load(new, r.level!!))
        assertTrue(r.currentChanged)
    }

    @Test
    fun `a shrinking rep range clamps the reps`() {
        val top = remapWeights(rtw, curls, curls.copy(repMax = 10), levelOf(curls, 1200, 12))
        assertEquals(8, top.level)
        assertEquals(Prescription.Load(1200, 10), load(curls.copy(repMax = 10), 8))
        assertTrue(top.currentChanged)
        val bottom = remapWeights(rtw, curls, curls.copy(repMin = 9), levelOf(curls, 1200, 8))
        assertEquals(Prescription.Load(1200, 9), load(curls.copy(repMin = 9), bottom.level!!))
    }

    @Test
    fun `an exact match changes nothing`() {
        val r = remapWeights(rtw, curls, curls, 12)
        assertEquals(WeightRemapResult(curls, 12, currentChanged = false), r)
    }

    @Test
    fun `a hold on a removed weight is dropped and the others are kept`() {
        val draft = without(1200).copy(holds = listOf(WeightHold(1200, 8, 4), WeightHold(1400, 8, 4)))
        assertEquals(listOf(WeightHold(1400, 8, 4)), remapWeights(rtw, curls, draft, null).config.holds)
    }

    @Test
    fun `two holds that collide after a remap keep the first`() {
        val draft = curls.copy(repMax = 10, holds = listOf(WeightHold(1200, 11, 2), WeightHold(1200, 12, 3)))
        assertEquals(listOf(WeightHold(1200, 10, 2)), remapWeights(rtw, curls, draft, null).config.holds)
    }

    @Test
    fun `Weight mode remaps by weight alone`() {
        val weight = ProgressMode.WEIGHT
        val r = remapWeights(weight, curls, without(1200), oldLevel = 2)
        assertEquals(1, r.level)
        assertEquals(Prescription.Load(1000, 10), load(r.config, 1, weight))
        val holds = curls.copy(holds = listOf(WeightHold(1400, 8, 4), WeightHold(1400, 10, 2)))
        assertEquals(listOf(WeightHold(1400, 8, 4)), remapWeights(weight, curls, holds, null).config.holds)
    }

    @Test
    fun `an untouched counter stays untouched and the starting point follows its weight`() {
        val draft = without(1200).copy(startWeight = 1200, startReps = 12, repMax = 10)
        val r = remapWeights(rtw, curls, draft, oldLevel = null)
        assertNull(r.level)
        assertFalse(r.currentChanged)
        assertEquals(1000 to 10, r.config.startWeight to r.config.startReps)
    }

    @Test
    fun `a unit change converted first keeps the same rung`() {
        val lb = WeightConversion.convert(curls, WeightUnit.LB)
        assertEquals(listOf(1775, 2200, 2650, 3075, 3525), lb.list)
        val r = remapWeights(rtw, lb, lb, 12)
        assertEquals(12 to false, r.level to r.currentChanged)
        assertEquals(Prescription.Load(2650, 10), load(lb, 12))
    }

    @Test
    fun `the hold count resets only when the load, the holds or the active holds change`() {
        val held = curls.copy(holds = listOf(WeightHold(1400, 8, 4)))
        val at = levelOf(curls, 1200, 10)
        // Same everything.
        assertFalse(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, held, at)))
        // A lighter weight removed: same load, same holds, still active.
        val lighter = held.copy(list = held.list - 800)
        assertFalse(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, lighter, at)))
        // The current weight removed.
        assertTrue(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, held.copy(list = held.list - 1200), at)))
        // A hold added.
        val added = held.copy(holds = held.holds + WeightHold(1600, 8, 2))
        assertTrue(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, added, at)))
        // A hold dropped with its weight.
        assertTrue(weightHoldResetNeeded(rtw, held, remapWeights(rtw, held, held.copy(list = held.list - 1400), 0)))
        // The heaviest removed: 14 kg × 12 becomes the top level, where a hold can't apply.
        val topHold = curls.copy(holds = listOf(WeightHold(1400, 12, 4)))
        assertTrue(weightHoldResetNeeded(rtw, topHold, remapWeights(rtw, topHold, topHold.copy(list = topHold.list - 1600), 0)))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightRemapTest*"`
Expected: FAIL with "Unresolved reference 'remapWeights'".

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold

/** What [remapWeights] returns (spec rev 26 §9.2). */
data class WeightRemapResult(
    /** The new settings with the starting point and holds remapped: holds on removed weights dropped, collisions keeping the first. */
    val config: WeightConfig,
    /** The current level on the new ladder, or null when it was null (an untouched counter follows the start). */
    val level: Int?,
    /** §9.2 step 5: the current load (weight value, reps per set) changed. */
    val currentChanged: Boolean,
)

/**
 * Spec rev 26 §9.2: the one remap for the current level, the starting point and every hold, by exact
 * value in hundredths. [mode] is a weight mode. [old] and [new] are in the same unit: after a unit
 * change, convert [old] first with WeightConversion.convert. [new]'s weights must be non-empty and
 * ascending, with 1 ≤ repMin ≤ repMax. WeightValidator runs on the result.
 */
fun remapWeights(mode: ProgressMode, old: WeightConfig, new: WeightConfig, oldLevel: Int?): WeightRemapResult {
    val to = ladderOf(mode, new)
    fun reps(r: Int) = r.coerceIn(new.repMin, new.repMax)
    val holds = new.holds
        .filter { it.weight in to.weights }
        .map { it.copy(reps = reps(it.reps)) }
        .distinctBy { to.levelOf(it.weight, it.reps) }
    val config = new.copy(
        startWeight = new.startWeight?.let { to.weights[to.weightIndexOf(it)] },
        startReps = new.startReps?.let(::reps),
        holds = holds,
    )
    if (oldLevel == null) return WeightRemapResult(config, level = null, currentChanged = false)
    val before = ladderOf(mode, old).prescription(oldLevel)
    val level = to.levelOf(before.weight, before.reps)
    return WeightRemapResult(config, level, currentChanged = to.prescription(level) != before)
}

/**
 * The hold count after a weight save (spec rev 26 §9.2 step 5, with rev 16 §4 applied by value, plan
 * Spec note 9): it resets when the current load changed, when the hold list changed (as a list, so
 * order counts), or when the set of active holds changed.
 */
fun weightHoldResetNeeded(mode: ProgressMode, old: WeightConfig, remap: WeightRemapResult): Boolean =
    remap.currentChanged || old.holds != remap.config.holds || activeHolds(mode, old) != activeHolds(mode, remap.config)

/** The holds that can apply: held for more than 0 and below the top level (RepProgression never holds at the cap). */
private fun activeHolds(mode: ProgressMode, c: WeightConfig): Set<WeightHold> {
    val ladder = ladderOf(mode, c)
    return c.holds.filter { it.forCount > 0 && ladder.levelOf(it.weight, it.reps) < ladder.maxLevel }.toSet()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightRemapTest*"`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/WeightRemap.kt \
  app/src/test/kotlin/com/mitenko/repkit/domain/WeightRemapTest.kt
git commit -m "Add the remap by value for weight and rep range edits"
```

---

### Task 7: Weight codecs

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/data/WeightCodecs.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/data/WeightCodecsTest.kt`

**Interfaces:**
- Consumes: `WeightSteps`, `WeightSteps.DEFAULT` and `WeightHold` (Task 1).
- Produces:
  - `internal object WeightCodecs`, with these functions:
  - `encodeSteps(WeightSteps): String` and `decodeSteps(String): WeightSteps?` (where `""` decodes to `DEFAULT`);
  - `encodeList(List<Int>): String` and `decodeList(String): List<Int>?` (where `""` decodes to `[]`);
  - `encodeHolds(List<WeightHold>): String` and `decodeHolds(String): List<WeightHold>` (where `""` decodes to `[]`, and bad items are dropped).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mitenko.repkit.data

import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightCodecsTest {
    @Test
    fun `steps encode as start step top and round trip`() {
        assertEquals("2000:250:6000", WeightCodecs.encodeSteps(WeightSteps.DEFAULT))
        assertEquals(WeightSteps(800, 400, 2400), WeightCodecs.decodeSteps("800:400:2400"))
    }

    @Test
    fun `the empty steps column reads as the default steps`() {
        assertEquals(WeightSteps.DEFAULT, WeightCodecs.decodeSteps(""))
    }

    @Test
    fun `malformed steps decode to null`() {
        listOf("2000:250", "a:b:c", "2000:250:6000:1", "20.5:2.5:60", " ", ":::")
            .forEach { assertNull(it, WeightCodecs.decodeSteps(it)) }
    }

    @Test
    fun `a list encodes in order and round trips, with the empty list as the empty string`() {
        assertEquals("800,1200,1600", WeightCodecs.encodeList(listOf(800, 1200, 1600)))
        assertEquals(listOf(1600, 800), WeightCodecs.decodeList("1600,800"))
        assertEquals("", WeightCodecs.encodeList(emptyList()))
        assertEquals(emptyList<Int>(), WeightCodecs.decodeList(""))
    }

    @Test
    fun `a malformed list decodes to null`() {
        listOf("800,,1200", "8.5", "a", ",", " ").forEach { assertNull(it, WeightCodecs.decodeList(it)) }
    }

    @Test
    fun `holds encode as weight reps for and round trip`() {
        val holds = listOf(WeightHold(1600, 8, 4), WeightHold(1200, 10, 0))
        assertEquals("1600:8:4,1200:10:0", WeightCodecs.encodeHolds(holds))
        assertEquals(holds, WeightCodecs.decodeHolds("1600:8:4,1200:10:0"))
        assertEquals("", WeightCodecs.encodeHolds(emptyList()))
        assertEquals(emptyList<WeightHold>(), WeightCodecs.decodeHolds(""))
    }

    @Test
    fun `bad hold items are dropped and the good ones kept in order`() {
        assertEquals(
            listOf(WeightHold(1600, 8, 4), WeightHold(1200, 10, 2)),
            WeightCodecs.decodeHolds("1600:8:4,x,0:8:4,1200:0:4,1200:8:-1,1400:8:4:1,1200:10:2"),
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightCodecsTest*"`
Expected: FAIL with "Unresolved reference 'WeightCodecs'".

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.mitenko.repkit.data

import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps

/**
 * The text columns of the weight group (spec rev 26 §5, plan Spec note 2), all in integer hundredths:
 * `weight_steps` "start:step:top", `weight_list` "800,1200,1600" and `weight_holds`
 * "weight:reps:for,…". Each column's '' default means "nothing written yet": the default steps, an
 * empty list, no holds. Values are range-checked by EntryMapping / WeightValidator, not here.
 */
internal object WeightCodecs {
    fun encodeSteps(s: WeightSteps): String = "${s.start}:${s.step}:${s.top}"

    /** "" reads as [WeightSteps.DEFAULT]; anything but three whole numbers is null. */
    fun decodeSteps(text: String): WeightSteps? {
        if (text.isEmpty()) return WeightSteps.DEFAULT
        val parts = text.split(":")
        if (parts.size != 3) return null
        val (start, step, top) = parts.map { it.toIntOrNull() ?: return null }
        return WeightSteps(start, step, top)
    }

    fun encodeList(list: List<Int>): String = list.joinToString(",")

    /** "" is the empty list; one malformed item makes the whole list null. */
    fun decodeList(text: String): List<Int>? =
        if (text.isEmpty()) emptyList() else text.split(",").map { it.toIntOrNull() ?: return null }

    fun encodeHolds(holds: List<WeightHold>): String = holds.joinToString(",") { "${it.weight}:${it.reps}:${it.forCount}" }

    /** Item by item, like HoldsCodec: a malformed item, or one with weight < 1, reps < 1 or for < 0, is dropped. */
    fun decodeHolds(text: String): List<WeightHold> =
        if (text.isEmpty()) emptyList() else text.split(",").mapNotNull(::decodeHold)

    private fun decodeHold(item: String): WeightHold? {
        val parts = item.split(":")
        if (parts.size != 3) return null
        val weight = parts[0].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val reps = parts[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val forCount = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return WeightHold(weight, reps, forCount)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightCodecsTest*"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/data/WeightCodecs.kt \
  app/src/test/kotlin/com/mitenko/repkit/data/WeightCodecsTest.kt
git commit -m "Add the text codecs for weight steps, lists and holds"
```

---

### Task 8: Room v7

Add the entity columns and bump the version in **one** commit. Adding columns while the database is still at version 6 would make Room re-export `6.json` with a new identity hash.

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/db/EntryEntity.kt:52-53` (append the columns after `lastCheckIn`)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/db/CheckInEntity.kt:9-31`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/db/HiitDatabase.kt:8-18`, `:111-112`
- Generated: `app/schemas/com.mitenko.repkit.data.db.HiitDatabase/7.json`
- Test: `app/src/test/kotlin/com/mitenko/repkit/data/db/HiitDatabaseTest.kt` (append)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `EntryEntity` gains `progressMode: String = "REPS"`, `weightUnit: String? = null`, `weightsKind: String = "STEPS"`, `weightSteps: String = ""`, `weightList: String = ""`, `weightHolds: String = ""`, `repsPerSet: Int = 10`, `repMin: Int = 8`, `repMax: Int = 12`, `startWeight: Int? = null` and `startReps: Int? = null`;
  - `CheckInEntity` gains `weight: Int? = null`, `reps: Int? = null` and `unit: String? = null`;
  - `HiitDatabase.MIGRATION_6_7_SQL: List<String>` and `HiitDatabase.MIGRATION_6_7`.

- [ ] **Step 1: Write the failing tests** (append inside `HiitDatabaseTest`, and add `MIGRATION_DB_7`, `NEW_ENTRY_DEFAULTS` and `NEW_CHECK_IN_COLUMNS` to its `companion object`)

```kotlin
    @Test
    fun `entry rows round-trip the weight columns`() = runTest {
        val row = testEntity(name = "Curls").copy(
            progressMode = "REPS_THEN_WEIGHT", weightUnit = "LB", weightsKind = "LIST", weightSteps = "2000:500:6000",
            weightList = "800,1600", weightHolds = "1600:8:4", repsPerSet = 6, repMin = 6, repMax = 10,
            startWeight = 1600, startReps = 7,
        )
        val id = db.entryDao().insert(row)
        assertEquals(row.copy(id = id), db.entryDao().get(id))
    }

    @Test
    fun `check-in rows round-trip the weight, reps and unit`() = runTest {
        val a = db.entryDao().insert(testEntity(name = "A", position = 0))
        val row = CheckInEntity(entryId = a, at = 1_000, total = 15, weight = 1400, reps = 8, unit = "KG")
        val id = db.checkInDao().insert(row)
        assertEquals(listOf(row.copy(id = id)), db.checkInDao().getForEntry(a))
    }

    @Test
    fun `schema v7 exports every column exactly as the 6 to 7 migration adds it`() {
        val json = File("schemas/com.mitenko.repkit.data.db.HiitDatabase/7.json").readText()
        assertTrue(Regex("\"version\"\\s*:\\s*7").containsMatchIn(json))
        assertEquals(14, HiitDatabase.MIGRATION_6_7_SQL.size)
        HiitDatabase.MIGRATION_6_7_SQL.forEach { sql ->
            // "ALTER TABLE t ADD COLUMN name TYPE …" ↔ Room's "`name` TYPE …" in the createSql.
            val def = sql.substringAfter("ADD COLUMN ")
            assertTrue(sql, json.contains("`${def.substringBefore(' ')}` ${def.substringAfter(' ')}"))
        }
    }

    @Test
    fun `the 6 to 7 migration SQL adds the weight columns with defaults and keeps every existing value`() {
        // Runs everywhere (no file-based helper), so Windows also covers the rev 26 §5 SQL.
        val raw = SQLiteDatabase.create(null)
        try {
            v5Schema(raw)
            HiitDatabase.MIGRATION_5_6_SQL.forEach { raw.execSQL(it) }
            val entriesBefore = raw.rawQuery(ALL_QUERY, null).allColumns()
            val checkInsBefore = raw.rawQuery(CHECK_IN_QUERY, null).allColumns()
            HiitDatabase.MIGRATION_6_7_SQL.forEach { raw.execSQL(it) }
            val entriesAfter = raw.rawQuery(ALL_QUERY, null).allColumns()
            val checkInsAfter = raw.rawQuery(CHECK_IN_QUERY, null).allColumns()
            assertEquals(entriesBefore, entriesAfter.map { it - NEW_ENTRY_DEFAULTS.keys })
            assertEquals(checkInsBefore, checkInsAfter.map { it - NEW_CHECK_IN_COLUMNS })
            assertTrue(checkInsAfter.isNotEmpty())
            entriesAfter.forEach { assertEquals(NEW_ENTRY_DEFAULTS, it.filterKeys { k -> k in NEW_ENTRY_DEFAULTS }) }
            checkInsAfter.forEach { row -> NEW_CHECK_IN_COLUMNS.forEach { assertNull(it, row[it]) } }
        } finally {
            raw.close()
        }
    }

    @Test
    fun `migration 6 to 7 validates through MigrationTestHelper`() {
        // Same Windows guard as the checks above: androidx.sqlite 2.6.1 mishandles backslash paths. CI runs it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase(MIGRATION_DB_7, 6).use { db ->
            v4Rows.forEach { db.execSQL(it) }
            db.execSQL("UPDATE entry SET holds = hold_at || ':' || hold_for")
        }
        helper.runMigrationsAndValidate(MIGRATION_DB_7, 7, true, HiitDatabase.MIGRATION_6_7).use { db ->
            assertEquals(listOf("REPS", "REPS", "REPS"), db.query("SELECT progress_mode FROM entry ORDER BY id").holdsColumn())
            assertEquals(MIGRATED_HOLDS, db.query(HOLDS_QUERY).holdsColumn())
            db.query("SELECT COUNT(*) FROM entry WHERE weight_unit IS NULL AND weight_holds = ''").use {
                it.moveToFirst()
                assertEquals(3, it.getInt(0))
            }
        }
    }
```

Add to the `companion object`:

```kotlin
        const val MIGRATION_DB_7 = "migration-6-7"

        /** Every column 6 → 7 adds to `entry`, with the value an existing row gets (rev 26 §5). */
        val NEW_ENTRY_DEFAULTS: Map<String, Any?> = mapOf(
            "progress_mode" to "REPS", "weight_unit" to null, "weights_kind" to "STEPS", "weight_steps" to "",
            "weight_list" to "", "weight_holds" to "", "reps_per_set" to 10L, "rep_min" to 8L, "rep_max" to 12L,
            "start_weight" to null, "start_reps" to null,
        )

        /** Every column 6 → 7 adds to `check_in`, all NULL on existing rows (rev 26 §9.3). */
        val NEW_CHECK_IN_COLUMNS = setOf("weight", "reps", "unit")
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*HiitDatabaseTest*"`
Expected: FAIL with "No parameter with name 'progressMode' found" and "Unresolved reference 'MIGRATION_6_7_SQL'".

- [ ] **Step 3: Write the minimal implementation**

In `data/db/EntryEntity.kt`, replace:

```kotlin
    @ColumnInfo(name = "last_check_in") val lastCheckIn: Long?,
)
```

with:

```kotlin
    @ColumnInfo(name = "last_check_in") val lastCheckIn: Long?,
    /** Spec rev 26 §5 (schema v7): REPS / WEIGHT / REPS_THEN_WEIGHT. EntryMapping reads an unknown value as REPS. */
    @ColumnInfo(name = "progress_mode", defaultValue = "REPS") val progressMode: String = "REPS",
    /** KG / LB. NULL until the workout first switches into a weight mode (spec rev 26 §9.3). */
    @ColumnInfo(name = "weight_unit") val weightUnit: String? = null,
    /** STEPS / LIST (spec rev 26 §5). */
    @ColumnInfo(name = "weights_kind", defaultValue = "STEPS") val weightsKind: String = "STEPS",
    /** WeightCodecs "start:step:top" in hundredths; '' = WeightSteps.DEFAULT. */
    @ColumnInfo(name = "weight_steps", defaultValue = "") val weightSteps: String = "",
    /** WeightCodecs "800,1200,1600" in hundredths; '' = empty. */
    @ColumnInfo(name = "weight_list", defaultValue = "") val weightList: String = "",
    /** Weight-mode holds by value, WeightCodecs "weight:reps:for" (plan Spec note 2); '' = none. */
    @ColumnInfo(name = "weight_holds", defaultValue = "") val weightHolds: String = "",
    @ColumnInfo(name = "reps_per_set", defaultValue = "10") val repsPerSet: Int = 10,
    @ColumnInfo(name = "rep_min", defaultValue = "8") val repMin: Int = 8,
    @ColumnInfo(name = "rep_max", defaultValue = "12") val repMax: Int = 12,
    /** NULL = the lightest weight (spec rev 26 §5). */
    @ColumnInfo(name = "start_weight") val startWeight: Int? = null,
    /** NULL = rep_min (spec rev 26 §5). */
    @ColumnInfo(name = "start_reps") val startReps: Int? = null,
)
```

In `data/db/CheckInEntity.kt`, replace the KDoc and the class body:

```kotlin
/**
 * One logged check-in (spec R6 §3.1). [at] is epoch ms, like entry.last_check_in; [total] is the rep
 * total after that check-in, NULL for a Timer only entry. Deleting the entry cascades, and
 * RoomEntryRepository also deletes the rows explicitly, so deletion never depends on the pragma.
 */
```

with:

```kotlin
/**
 * One logged check-in (spec R6 §3.1). [at] is epoch ms, like entry.last_check_in; [total] is the rep
 * total (the level in a weight mode) after that check-in, NULL for a Timer only entry. [weight] (in
 * hundredths of [unit]), [reps] and [unit] are the load a weight-mode Counter check-in recorded (spec
 * rev 26 §9.3, schema v7); all three are NULL otherwise. Deleting the entry cascades, and
 * RoomEntryRepository also deletes the rows explicitly, so deletion never depends on the pragma.
 */
```

and:

```kotlin
    val at: Long,
    val total: Int?,
)
```

with:

```kotlin
    val at: Long,
    val total: Int?,
    val weight: Int? = null,
    val reps: Int? = null,
    val unit: String? = null,
)
```

In `data/db/HiitDatabase.kt`:
- In the KDoc, replace `version 6 adds the \`workout_session\` run log (rev 17 §1).` with `version 6 adds the \`workout_session\` run log (rev 17 §1), version 7 adds the weight columns to \`entry\` and \`check_in\` (rev 26 §5).`
- Change `version = 6,` to `version = 7,`.
- Add this block after `MIGRATION_5_6`:

```kotlin
        /**
         * Spec rev 26 §5: columns with defaults only, so no row is rewritten and every existing entry
         * reads as Reps with nothing changed. weight_holds is plan Spec note 2.
         */
        internal val MIGRATION_6_7_SQL = listOf(
            "ALTER TABLE entry ADD COLUMN progress_mode TEXT NOT NULL DEFAULT 'REPS'",
            "ALTER TABLE entry ADD COLUMN weight_unit TEXT",
            "ALTER TABLE entry ADD COLUMN weights_kind TEXT NOT NULL DEFAULT 'STEPS'",
            "ALTER TABLE entry ADD COLUMN weight_steps TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE entry ADD COLUMN weight_list TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE entry ADD COLUMN weight_holds TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE entry ADD COLUMN reps_per_set INTEGER NOT NULL DEFAULT 10",
            "ALTER TABLE entry ADD COLUMN rep_min INTEGER NOT NULL DEFAULT 8",
            "ALTER TABLE entry ADD COLUMN rep_max INTEGER NOT NULL DEFAULT 12",
            "ALTER TABLE entry ADD COLUMN start_weight INTEGER",
            "ALTER TABLE entry ADD COLUMN start_reps INTEGER",
            "ALTER TABLE check_in ADD COLUMN weight INTEGER",
            "ALTER TABLE check_in ADD COLUMN reps INTEGER",
            "ALTER TABLE check_in ADD COLUMN unit TEXT",
        )

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7_SQL.forEach { db.execSQL(it) }
            }
        }
```

- Replace the `MIGRATIONS` line with:

```kotlin
        val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

- [ ] **Step 4: Run the tests and fetch the exported schema**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*HiitDatabaseTest*" --tests "*EntryMappingTest*" --tests "*RoomEntryRepositoryTest*"`
Expected: PASS. The `MigrationTestHelper` tests are skipped on the Windows worker; GitHub CI (Linux) runs them on the PR.

The worker generates `7.json` in its own checkout, not here. Copy it back:

```bash
scp miten@192.168.1.86:C:/rbuild/hiit_tracker/app/schemas/com.mitenko.repkit.data.db.HiitDatabase/7.json \
  app/schemas/com.mitenko.repkit.data.db.HiitDatabase/7.json
```

If the worker was offline (exit 2) and the run was local, the local build has already written it.

Then check the schema files:

```bash
git diff --exit-code app/schemas/com.mitenko.repkit.data.db.HiitDatabase/[1-6].json && echo "CLAUDE-OLD-SCHEMAS-UNCHANGED ok"
grep -c '"version": 7' app/schemas/com.mitenko.repkit.data.db.HiitDatabase/7.json   # 1
grep -c 'progress_mode' app/schemas/com.mitenko.repkit.data.db.HiitDatabase/7.json  # ≥ 1
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/data/db/EntryEntity.kt \
  app/src/main/kotlin/com/mitenko/repkit/data/db/CheckInEntity.kt \
  app/src/main/kotlin/com/mitenko/repkit/data/db/HiitDatabase.kt \
  app/schemas/com.mitenko.repkit.data.db.HiitDatabase/7.json \
  app/src/test/kotlin/com/mitenko/repkit/data/db/HiitDatabaseTest.kt
git commit -m "Move hiit.db to v7 with the weight columns on entry and check_in"
```

---

### Task 9: Entry mapping

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/EntryMapping.kt`, at:
  - `:22-27` (`validTotal`);
  - `:58-76` (`progression`);
  - `:101-107` (`counter`);
  - `:114-127` (`toDomain`);
  - `:129-130` (`toPoint`);
  - `:145-180` (`entryEntity`).
- Modify: `app/src/main/kotlin/com/mitenko/repkit/domain/model/CheckInPoint.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/domain/model/Entry.kt:3-6` (KDoc only)
- Test: `app/src/test/kotlin/com/mitenko/repkit/data/EntryMappingTest.kt` (append)

**Interfaces:**
- Consumes:
  - `WeightCodecs` (Task 7);
  - `WeightValidator` (Task 5);
  - `startLevel()` (Task 3);
  - the entity columns (Task 8);
  - the model types (Task 1).
- Produces:
  - `CheckInPoint(at, total, weight: Int? = null, reps: Int? = null, unit: WeightUnit? = null)`;
  - `internal fun validTotal(raw: Int?, min: Int = 1): Int?`;
  - `internal fun EntryEntity.mode(): ProgressMode`;
  - `internal fun EntryEntity.weightConfig(mode: ProgressMode): WeightConfig`;
  - `EntryEntity.progression()` now carries the mode and weight group;
  - `EntryEntity.counter(startingTotal: Int, minTotal: Int = 1)`;
  - `toDomain()` resolves a NULL total to `startLevel()`;
  - `entryEntity(...)` writes every weight column.

- [ ] **Step 1: Write the failing tests** (append inside `EntryMappingTest`, and add the imports `com.mitenko.repkit.domain.model.ProgressMode`, `WeightConfig`, `WeightHold`, `WeightSteps`, `WeightUnit` and `WeightsKind`)

```kotlin
    /** Spec §6's curls: Reps then weight, 8/10/12/14/16 kg × 8–12. */
    private fun curlsRow(total: Int? = null) = testEntity(total = total).copy(
        progressMode = "REPS_THEN_WEIGHT", weightUnit = "KG", weightsKind = "LIST", weightList = "800,1000,1200,1400,1600",
    )

    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600))

    @Test
    fun `a row as the v7 migration leaves it reads as Reps with the default weight settings`() {
        val e = testEntity(total = 60).toDomain()
        assertEquals(ProgressionConfig(), e.progression)
        assertEquals(60, e.counter.total)
    }

    @Test
    fun `a weight row maps field by field`() {
        val row = curlsRow(total = 13).copy(startWeight = 1000, startReps = 9, weightHolds = "1400:8:4", repsPerSet = 6)
        val expected = ProgressionConfig(
            mode = ProgressMode.REPS_THEN_WEIGHT,
            weight = curls.copy(repsPerSet = 6, startWeight = 1000, startReps = 9, holds = listOf(WeightHold(1400, 8, 4))),
        )
        assertEquals(expected, row.toDomain().progression)
        assertEquals(13, row.toDomain().counter.total)
    }

    @Test
    fun `in a weight mode a null total reads as the start level and 0 is a real level`() {
        assertEquals(6, curlsRow().copy(startWeight = 1000, startReps = 9).toDomain().counter.total)
        assertEquals(0, curlsRow(total = 0).copy(startWeight = 1000).toDomain().counter.total)
        assertEquals(5, curlsRow(total = -1).copy(startWeight = 1000).toDomain().counter.total)
        // Reps mode keeps today's rule: 0 is invalid and reads as the starting total.
        assertEquals(48, testEntity(total = 0).toDomain().counter.total)
    }

    @Test
    fun `unknown mode, kind and unit strings are repaired`() {
        assertEquals(ProgressMode.REPS, testEntity().copy(progressMode = "LEGS").toDomain().progression.mode)
        assertEquals(WeightsKind.STEPS, curlsRow().copy(weightsKind = "PLATES").toDomain().progression.weight.kind)
        assertNull(testEntity().copy(weightUnit = "STONE").toDomain().progression.weight.unit)
        // Plan Spec note 11: a weight mode always has a unit.
        assertEquals(WeightUnit.KG, curlsRow().copy(weightUnit = null).toDomain().progression.weight.unit)
        assertEquals(WeightUnit.KG, curlsRow().copy(weightUnit = "STONE").toDomain().progression.weight.unit)
    }

    @Test
    fun `the weight list is sorted and de-duplicated, and bad steps read as the default`() {
        assertEquals(listOf(800, 1200, 1600), curlsRow().copy(weightList = "1200,800,800,1600").toDomain().progression.weight.list)
        assertEquals(WeightSteps.DEFAULT, testEntity().copy(weightSteps = "20:2.5").toDomain().progression.weight.steps)
    }

    @Test
    fun `a starting point or hold that isn't on the ladder is dropped`() {
        val w = curlsRow().copy(startWeight = 900, startReps = 13, weightHolds = "900:8:4,1400:8:4,1400:13:2,x")
            .toDomain().progression.weight
        assertNull(w.startWeight)
        assertNull(w.startReps)
        assertEquals(listOf(WeightHold(1400, 8, 4)), w.holds)
    }

    @Test
    fun `in Weight mode two holds on one weight keep the first`() {
        val w = curlsRow().copy(progressMode = "WEIGHT", weightHolds = "1400:8:4,1400:10:2").toDomain().progression.weight
        assertEquals(listOf(WeightHold(1400, 8, 4)), w.holds)
    }

    @Test
    fun `an inconsistent weight group falls back on its own, keeping the unit and the Reps progression`() {
        val p = curlsRow().copy(weightList = "800", cap = 80).toDomain().progression
        assertEquals(WeightConfig(unit = WeightUnit.KG), p.weight)
        assertEquals(80, p.cap)
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, p.mode)
    }

    @Test
    fun `an inconsistent Reps progression falls back and keeps the mode and the weight group`() {
        val p = curlsRow().copy(floor = 80, cap = 60).toDomain().progression
        assertEquals(ProgressionConfig(mode = ProgressMode.REPS_THEN_WEIGHT, weight = curls), p)
    }

    @Test
    fun `a weight config is written and read back unchanged`() {
        val p = ProgressionConfig(
            mode = ProgressMode.WEIGHT,
            weight = curls.copy(unit = WeightUnit.LB, repsPerSet = 6, startWeight = 1200, holds = listOf(WeightHold(1400, 8, 4))),
        )
        val row = entryEntity("Curls", 0, progression = p)
        assertEquals(
            listOf<Any?>("WEIGHT", "LB", "LIST", "2000:250:6000", "800,1000,1200,1400,1600", "1400:8:4", 6, 8, 12, 1200, null),
            listOf(
                row.progressMode, row.weightUnit, row.weightsKind, row.weightSteps, row.weightList, row.weightHolds,
                row.repsPerSet, row.repMin, row.repMax, row.startWeight, row.startReps,
            ),
        )
        assertEquals(p, row.toDomain().progression)
        assertNull(entryEntity("Burpees", 0).weightUnit)
    }

    @Test
    fun `a weight check-in row maps to a point with its load`() {
        assertEquals(
            CheckInPoint(Instant.ofEpochMilli(1_000), 15, 1400, 8, WeightUnit.KG),
            CheckInEntity(1, 7, 1_000, 15, 1400, 8, "KG").toPoint(),
        )
        val reps = CheckInEntity(2, 7, 1_000, 62).toPoint()
        assertEquals(listOf<Any?>(null, null, null), listOf(reps.weight, reps.reps, reps.unit))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*EntryMappingTest*"`
Expected: FAIL with "Too many arguments for constructor CheckInPoint". The weight assertions fail, too, once that compiles.

- [ ] **Step 3: Write the minimal implementation**

Replace `domain/model/CheckInPoint.kt` with:

```kotlin
package com.mitenko.repkit.domain.model

import java.time.Instant

/**
 * One logged check-in (spec R6 §3.3). [total] is the rep total (the level in a weight mode) after that
 * check-in for a Workout, and null for a Timer only entry. [weight] (hundredths of [unit]), [reps] and
 * [unit] are the load a weight-mode Counter check-in recorded (spec rev 26 §9.3), null otherwise:
 * history shows what was recorded, never a later remap.
 */
data class CheckInPoint(
    val at: Instant,
    val total: Int?,
    val weight: Int? = null,
    val reps: Int? = null,
    val unit: WeightUnit? = null,
)
```

In `domain/model/Entry.kt`, replace the KDoc:

```kotlin
/**
 * One HIIT entry (spec §5.1). Invariant: [counter].total is always a real value — a stored
 * NULL total is resolved to [progression].startingTotal when the row is mapped.
 */
```

with:

```kotlin
/**
 * One HIIT entry (spec §5.1). Invariant: [counter].total is always a real value — a stored NULL total
 * is resolved to the progression's start level when the row is mapped: startingTotal in Reps mode,
 * the starting weight × reps in a weight mode (spec rev 26 §2).
 */
```

In `data/EntryMapping.kt`:

Add these imports: `com.mitenko.repkit.domain.WeightValidator`, `com.mitenko.repkit.domain.startLevel`, `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightConfig`, `com.mitenko.repkit.domain.model.WeightHold`, `com.mitenko.repkit.domain.model.WeightUnit` and `com.mitenko.repkit.domain.model.WeightsKind`.

Replace `validTotal`:

```kotlin
/** A stored total below 1 is invalid and reads as NULL (spec §5.2). */
internal fun validTotal(raw: Int?): Int? {
    if (raw == null || raw >= 1) return raw
```

with:

```kotlin
/**
 * A stored total below [min] is invalid and reads as NULL (spec §5.2): [min] is 1 in Reps mode and 0,
 * the lightest level, in a weight mode (spec rev 26 §2, plan Spec note 3).
 */
internal fun validTotal(raw: Int?, min: Int = 1): Int? {
    if (raw == null || raw >= min) return raw
```

In `progression()`, replace:

```kotlin
internal fun EntryEntity.progression(): ProgressionConfig {
    val d = ProgressionConfig()
    val c = ProgressionConfig(
```

with:

```kotlin
internal fun EntryEntity.progression(): ProgressionConfig {
    val d = ProgressionConfig()
    val mode = mode()
    val weight = weightConfig(mode)
    val c = ProgressionConfig(
```

Then replace:

```kotlin
        hold = holdEnabled,
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "Entry $id: progression inconsistent ($c); using default progression")
    return d
}
```

with:

```kotlin
        hold = holdEnabled,
        mode = mode,
        weight = weight,
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "Entry $id: progression inconsistent ($c); using default progression")
    // Spec rev 26 §5: the weight group is repaired on its own, so a Reps fallback keeps it and the mode.
    return d.copy(mode = mode, weight = weight)
}

/** Spec rev 26 §5: an unknown progress_mode is logged and reads as REPS. */
internal fun EntryEntity.mode(): ProgressMode =
    ProgressMode.entries.firstOrNull { it.name == progressMode }
        ?: ProgressMode.REPS.also { Log.w(TAG, "Entry $id: unknown progress_mode=$progressMode; reading as REPS") }

/**
 * Spec rev 26 §5, repaired per field like the progression:
 * - unknown strings read as their defaults;
 * - malformed steps read as the default steps, and a malformed list as empty;
 * - the list is sorted and de-duplicated;
 * - reps per set or a rep range outside 1..100 reads as its default;
 * - a starting point or hold that isn't on the ladder is dropped (in Weight mode, so is a later hold on the same weight).
 *
 * A group that is still invalid falls back to the defaults with the row's unit, on its own, so a bad
 * weight group never resets the Reps progression and the reverse. A weight mode with no unit (only a
 * corrupt row: §9.3 writes one on the first switch) reads as KG (plan Spec note 11).
 */
internal fun EntryEntity.weightConfig(mode: ProgressMode): WeightConfig {
    val d = WeightConfig()
    val storedUnit = weightUnit?.let { s ->
        WeightUnit.entries.firstOrNull { it.name == s } ?: null.also { Log.w(TAG, "Entry $id: unknown weight_unit=$s") }
    }
    val unit = storedUnit
        ?: if (mode.usesWeights) WeightUnit.KG.also { Log.w(TAG, "Entry $id: no weight_unit in $mode; reading as KG") } else null
    val kind = WeightsKind.entries.firstOrNull { it.name == weightsKind }
        ?: d.kind.also { Log.w(TAG, "Entry $id: unknown weights_kind=$weightsKind; reading as ${d.kind}") }
    val steps = checked(id, "weight_steps", WeightCodecs.decodeSteps(weightSteps), d.steps) { it != null }!!
    val list = checked(id, "weight_list", WeightCodecs.decodeList(weightList), d.list) { it != null }!!.sorted().distinct()
    val perSet = checked(id, "reps_per_set", repsPerSet, d.repsPerSet) { it in 1..WeightConfig.MAX_REPS }
    val rangeOk = repMin in 1..WeightConfig.MAX_REPS && repMax in 1..WeightConfig.MAX_REPS && repMin < repMax
    if (!rangeOk) Log.w(TAG, "Entry $id: invalid rep range $repMin..$repMax; using ${d.repMin}..${d.repMax}")
    val min = if (rangeOk) repMin else d.repMin
    val max = if (rangeOk) repMax else d.repMax
    val ladder = WeightConfig(kind = kind, steps = steps, list = list).weights.toSet()
    val c = WeightConfig(
        unit = unit,
        kind = kind,
        steps = steps,
        list = list,
        repsPerSet = perSet,
        repMin = min,
        repMax = max,
        startWeight = checked(id, "start_weight", startWeight, null) { it == null || it in ladder },
        startReps = checked(id, "start_reps", startReps, null) { it == null || it in min..max },
        holds = storedWeightHolds(ladder, min..max, mode),
    )
    if (WeightValidator.validate(c, mode).isEmpty()) return c
    Log.w(TAG, "Entry $id: weight settings inconsistent ($c); using defaults")
    return WeightConfig(unit = unit)
}

/** The weight_holds column, item by item: off-ladder or out-of-range holds dropped, then later collisions, then holds past the 8th. */
private fun EntryEntity.storedWeightHolds(ladder: Set<Int>, reps: IntRange, mode: ProgressMode): List<WeightHold> {
    val repaired = WeightCodecs.decodeHolds(weightHolds)
        .filter { it.weight in ladder && it.reps in reps }
        .distinctBy { if (mode == ProgressMode.WEIGHT) it.weight to 0 else it.weight to it.reps }
        .take(ProgressionConfig.MAX_HOLDS)
    if (WeightCodecs.encodeHolds(repaired) != weightHolds) Log.w(TAG, "Entry $id: repaired weight_holds=$weightHolds to $repaired")
    return repaired
}
```

Replace the `counter` header:

```kotlin
internal fun EntryEntity.counter(startingTotal: Int): CounterState = CounterState(
    total = validTotal(total) ?: startingTotal,
```

with:

```kotlin
/** [minTotal] is 0 in a weight mode (spec rev 26 §2), so level 0 is a real value. */
internal fun EntryEntity.counter(startingTotal: Int, minTotal: Int = 1): CounterState = CounterState(
    total = validTotal(total, minTotal) ?: startingTotal,
```

In `toDomain()`, replace:

```kotlin
        counter = counter(progression.startingTotal),
```

with:

```kotlin
        counter = counter(progression.startLevel(), minTotal = if (progression.mode.usesWeights) 0 else 1),
```

Replace `toPoint`:

```kotlin
/** A history row as a domain point (spec R6 §3.3). */
internal fun CheckInEntity.toPoint(): CheckInPoint = CheckInPoint(Instant.ofEpochMilli(at), total)
```

with:

```kotlin
/** A history row as a domain point (spec R6 §3.3, rev 26 §9.3). An unknown unit string reads as null. */
internal fun CheckInEntity.toPoint(): CheckInPoint = CheckInPoint(
    at = Instant.ofEpochMilli(at),
    total = total,
    weight = weight,
    reps = reps,
    unit = unit?.let { u -> WeightUnit.entries.firstOrNull { it.name == u } },
)
```

In `entryEntity(...)`, replace the last lines:

```kotlin
    lastCheckIn = counter.lastCheckIn,
)
```

with:

```kotlin
    lastCheckIn = counter.lastCheckIn,
    progressMode = progression.mode.name,
    weightUnit = progression.weight.unit?.name,
    weightsKind = progression.weight.kind.name,
    weightSteps = WeightCodecs.encodeSteps(progression.weight.steps),
    weightList = WeightCodecs.encodeList(progression.weight.list),
    weightHolds = WeightCodecs.encodeHolds(progression.weight.holds),
    repsPerSet = progression.weight.repsPerSet,
    repMin = progression.weight.repMin,
    repMax = progression.weight.repMax,
    startWeight = progression.weight.startWeight,
    startReps = progression.weight.startReps,
)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*EntryMappingTest*" --tests "*RoomEntryRepositoryTest*" --tests "*HiitDatabaseTest*" --tests "*V1MigratorTest*"`
Expected: PASS. All existing mapping, repository and v1 tests pass unchanged: Reps rows map exactly as before.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/data/EntryMapping.kt \
  app/src/main/kotlin/com/mitenko/repkit/domain/model/CheckInPoint.kt \
  app/src/main/kotlin/com/mitenko/repkit/domain/model/Entry.kt \
  app/src/test/kotlin/com/mitenko/repkit/data/EntryMappingTest.kt
git commit -m "Map and repair the weight columns, and resolve weight-mode totals as levels"
```

---

### Task 10: Check-in records the load (§9.3)

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/EntryRepository.kt:217-239` (`checkIn`)
- Modify: `app/src/test/kotlin/com/mitenko/repkit/testutil/FakeEntryRepository.kt:154-169` (`checkIn`)
- Test: `app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt` (append)

**Interfaces:**
- Consumes: `RepProgression.checkInByMode` (Task 4), `ProgressionConfig.loadAt` (Task 3), `CheckInEntity.weight`/`reps`/`unit` (Task 8), and `CheckInPoint(at, total, weight, reps, unit)` and the mapping (Task 9).
- Produces: `checkIn` logs `weight`, `reps` and `unit` for a Counter check-in in a weight mode, and NULLs otherwise. The fake mirrors this. The test helper `curlsRow(total, edit)` in `RoomEntryRepositoryTest` is reused by Task 11.

- [ ] **Step 1: Write the failing tests** (append inside `RoomEntryRepositoryTest`, and add the imports `com.mitenko.repkit.data.db.EntryEntity`, `com.mitenko.repkit.domain.model.WeightUnit` and `com.mitenko.repkit.testutil.testEntity`)

```kotlin
    /** Spec §6's curls: Reps then weight, 8/10/12/14/16 kg × 8–12 in kg, appended at the end; [edit] varies the row. */
    private suspend fun curlsRow(total: Int? = null, edit: (EntryEntity) -> EntryEntity = { it }): Long =
        db.entryDao().insert(
            edit(
                testEntity(name = "Curls", position = db.entryDao().count(), total = total).copy(
                    progressMode = "REPS_THEN_WEIGHT", weightUnit = "KG", weightsKind = "LIST",
                    weightList = "800,1000,1200,1400,1600",
                ),
            ),
        )

    @Test
    fun `a Reps then weight check-in logs the weight, the reps and the unit`() = runTest {
        val r = repo()
        val a = curlsRow()
        r.checkIn(a, clock)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        assertEquals(
            listOf(CheckInPoint(day1, 0, 800, 8, WeightUnit.KG), CheckInPoint(clock.instant, 1, 800, 9, WeightUnit.KG)),
            r.history(a, null).first(),
        )
        assertEquals(1, db.entryDao().get(a)!!.total)
        assertEquals(listOf("KG", "KG"), db.checkInDao().getForEntry(a).map { it.unit })
    }

    @Test
    fun `a Weight check-in logs the fixed reps per set`() = runTest {
        val r = repo()
        val a = curlsRow { it.copy(progressMode = "WEIGHT", repsPerSet = 6) }
        r.checkIn(a, clock)
        assertEquals(listOf(CheckInPoint(clock.instant, 0, 800, 6, WeightUnit.KG)), r.history(a, null).first())
    }

    @Test
    fun `a long miss in a weight mode stops one weight lighter`() = runTest {
        val r = repo()
        val a = curlsRow()
        db.entryDao().setCounter(
            a, total = 20, bestStreak = 3, currentStreak = 3, holdCount = 0,
            lastCheckIn = clock.instant.minusSeconds(168 * 3600L).toEpochMilli(),
        ) // 16 kg × 8, a week ago
        val result = r.checkIn(a, clock)
        assertEquals(Outcome.Missed(6), result.outcome)
        assertEquals(15, result.state.total)
        assertEquals(CheckInPoint(clock.instant, 15, 1400, 8, WeightUnit.KG), r.history(a, null).first().single())
    }

    @Test
    fun `Reps and Timer only points leave the weight, reps and unit NULL`() = runTest {
        val r = repo()
        val reps = r.create("Burpees")
        val timerOnly = curlsRow { it.copy(type = "CHECK_IN") }
        r.checkIn(reps, clock)
        r.checkIn(timerOnly, clock)
        for (id in listOf(reps, timerOnly)) {
            val row = db.checkInDao().getForEntry(id).single()
            assertEquals(listOf<Any?>(null, null, null), listOf(row.weight, row.reps, row.unit))
        }
        assertNull(db.entryDao().get(timerOnly)!!.total)
        assertEquals(listOf(CheckInPoint(clock.instant, null)), r.history(timerOnly, null).first())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*RoomEntryRepositoryTest*"`
Expected: FAIL. The new tests fail on their first assertion: the logged points have NULL weight, reps and unit, and the long miss lands on 14 (12 kg × 12, the Reps engine with no floor and no ladder). Every existing test still passes.

- [ ] **Step 3: Write the minimal implementation**

In `data/EntryRepository.kt`, add the import `com.mitenko.repkit.domain.loadAt`. Then, in `checkIn`, replace:

```kotlin
            val countsReps = entry.type == EntryType.WORKOUT
            val result = RepProgression.checkIn(entry.counter, entry.progression, now, clock.zone(), countsReps)
            if (result.outcome != Outcome.AlreadyToday) {
                val s = result.state
                // A Timer only entry never touches its total column, so a NULL total stays NULL (plan Spec note 6).
                val total = if (countsReps) s.total else row.total
                dao.setCounter(id, total, s.bestStreak, s.currentStreak, s.holdCount, s.lastCheckIn?.toEpochMilli())
                // Spec R6 §3.2: at is the new lastCheckIn (now); a Timer only point has no total.
                checkIns.insert(CheckInEntity(entryId = id, at = now.toEpochMilli(), total = if (countsReps) s.total else null))
            }
```

with:

```kotlin
            val countsReps = entry.type == EntryType.WORKOUT
            val result = RepProgression.checkInByMode(entry.counter, entry.progression, now, clock.zone(), countsReps)
            if (result.outcome != Outcome.AlreadyToday) {
                val s = result.state
                // A Timer only entry never touches its total column, so a NULL total stays NULL (plan Spec note 6).
                val total = if (countsReps) s.total else row.total
                dao.setCounter(id, total, s.bestStreak, s.currentStreak, s.holdCount, s.lastCheckIn?.toEpochMilli())
                // Spec R6 §3.2: at is the new lastCheckIn (now); a Timer only point has no total. Spec rev 26 §9.3:
                // a weight-mode Counter point also records its load and unit, so history never depends on later edits.
                val load = if (countsReps) entry.progression.loadAt(s.total) else null
                checkIns.insert(
                    CheckInEntity(
                        entryId = id,
                        at = now.toEpochMilli(),
                        total = if (countsReps) s.total else null,
                        weight = load?.weight,
                        reps = load?.reps,
                        unit = load?.let { entry.progression.weight.unit?.name },
                    ),
                )
            }
```

In `testutil/FakeEntryRepository.kt`, add the import `com.mitenko.repkit.domain.loadAt`. Then replace:

```kotlin
        val result = RepProgression.checkIn(e.counter, e.progression, clock.now(), clock.zone(), countsReps)
        if (result.outcome != Outcome.AlreadyToday) {
            edit(id) { it.copy(counter = result.state) }
            // Spec R6 §3.2: one point per recorded check-in; a Timer only point has no total.
            val point = CheckInPoint(clock.now(), if (countsReps) result.state.total else null)
```

with:

```kotlin
        val result = RepProgression.checkInByMode(e.counter, e.progression, clock.now(), clock.zone(), countsReps)
        if (result.outcome != Outcome.AlreadyToday) {
            edit(id) { it.copy(counter = result.state) }
            // Spec R6 §3.2: one point per recorded check-in; a Timer only point has no total. Rev 26 §9.3: weight modes add the load.
            val load = if (countsReps) e.progression.loadAt(result.state.total) else null
            val point = CheckInPoint(
                clock.now(), if (countsReps) result.state.total else null, load?.weight, load?.reps,
                load?.let { e.progression.weight.unit },
            )
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*RoomEntryRepositoryTest*" --tests "*EntryViewModelTest*" --tests "*EntryListViewModelTest*"`
Expected: PASS. The ViewModel tests use the fake, and they pass unchanged.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/data/EntryRepository.kt \
  app/src/test/kotlin/com/mitenko/repkit/testutil/FakeEntryRepository.kt \
  app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt
git commit -m "Record the weight, reps and unit of weight-mode check-ins"
```

---

### Task 11: Repository — weight saves, mode switch, level range, duplicate

**Files:**
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/db/EntryDao.kt` (two queries, appended before the closing brace)
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/EntryRepository.kt`, at:
  - the interface: append `setWeightConfig` and `switchMode` after `setType`;
  - the implementation: `overwriteCounter` (`:241-254`), and the two new overrides.
- Modify: `app/src/test/kotlin/com/mitenko/repkit/testutil/FakeEntryRepository.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt` (append)

**Interfaces:**
- Consumes:
  - `remapWeights`, `weightHoldResetNeeded` and `WeightRemapResult` (Task 6);
  - `WeightValidator` (Task 5);
  - `WeightConversion` (Task 2);
  - `scale()`, `startLevel()` and `ProgressionScale.Ladder` (Task 3);
  - `WeightCodecs` (Task 7);
  - `progression()`, `mode()` and `validTotal(raw, min)` (Task 9);
  - `curlsRow` (Task 10).
- Produces:
  - `EntryRepository.setWeightConfig(id: Long, weight: WeightConfig)`;
  - `EntryRepository.switchMode(id: Long, mode: ProgressMode, defaultUnit: WeightUnit)`;
  - `EntryDao.setWeightConfig(...)` and `EntryDao.switchMode(id, mode, unit)`;
  - `overwriteCounter` accepts `0..maxLevel` in a weight mode;
  - `FakeEntryRepository.weightWrites` and `FakeEntryRepository.modeSwitches`.

  PR 2's settings ViewModels call `setWeightConfig` for the weight sections, `setProgression` for window, penalty and the Hold switch, and `switchMode` for Progress by.

- [ ] **Step 1: Write the failing tests** (append inside `RoomEntryRepositoryTest`, and add the imports `com.mitenko.repkit.domain.Prescription`, `com.mitenko.repkit.domain.WeightConversion`, `com.mitenko.repkit.domain.loadAt`, `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightConfig`, `com.mitenko.repkit.domain.model.WeightHold`, `com.mitenko.repkit.domain.model.WeightSteps` and `com.mitenko.repkit.domain.model.WeightsKind`)

```kotlin
    private val curls = WeightConfig(unit = WeightUnit.KG, kind = WeightsKind.LIST, list = listOf(800, 1000, 1200, 1400, 1600))

    @Test
    fun `setWeightConfig keeps you on the same weight and resets the hold count only when it moves`() = runTest {
        val r = repo()
        val below = curlsRow(total = 12) { it.copy(holdCount = 2) } // 12 kg × 10
        r.setWeightConfig(below, curls.copy(list = listOf(1000, 1200, 1400, 1600)))
        assertEquals(7 to 2, db.entryDao().get(below)!!.let { it.total to it.holdCount })
        assertEquals(Prescription.Load(1200, 10), r.entry(below).first()!!.progression.loadAt(7))

        val current = curlsRow(total = 12) { it.copy(holdCount = 2) }
        r.setWeightConfig(current, curls.copy(list = listOf(800, 1000, 1400, 1600)))
        assertEquals(7 to 0, db.entryDao().get(current)!!.let { it.total to it.holdCount })
        assertEquals(Prescription.Load(1000, 10), r.entry(current).first()!!.progression.loadAt(7))
    }

    @Test
    fun `setWeightConfig sorts the list and clamps the reps into a shrunk range`() = runTest {
        val r = repo()
        val a = curlsRow(total = 14) // 12 kg × 12
        r.setWeightConfig(a, curls.copy(list = listOf(1600, 800, 1200, 1000, 1400), repMax = 10))
        val row = db.entryDao().get(a)!!
        assertEquals(listOf<Any?>("800,1000,1200,1400,1600", 10, 8), listOf(row.weightList, row.repMax, row.total))
        assertEquals(Prescription.Load(1200, 10), r.entry(a).first()!!.progression.loadAt(8))
    }

    @Test
    fun `setWeightConfig drops a hold on a removed weight`() = runTest {
        val r = repo()
        val a = curlsRow(total = 0) { it.copy(weightHolds = "1200:8:4,1400:8:4") }
        val holds = listOf(WeightHold(1200, 8, 4), WeightHold(1400, 8, 4))
        r.setWeightConfig(a, curls.copy(list = listOf(800, 1000, 1400, 1600), holds = holds))
        assertEquals("1400:8:4", db.entryDao().get(a)!!.weightHolds)
    }

    @Test
    fun `an invalid weight draft writes nothing and the fixed draft saves`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12)
        val before = db.entryDao().get(a)
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, curls.copy(list = listOf(800, 1000, 1000, 1600))) }
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, WeightConfig(unit = WeightUnit.KG, steps = WeightSteps(2000, 250, 6100))) }
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, curls.copy(list = listOf(800))) }
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, curls.copy(repMin = 12, repMax = 8)) }
        // Nine holds on nine different positions, so the remap's de-duplication can't hide the excess.
        expectThrows<IllegalArgumentException> { r.setWeightConfig(a, curls.copy(holds = List(9) { WeightHold(curls.list[it % 5], 8 + it / 5, 1) })) }
        assertEquals(before, db.entryDao().get(a))
        r.setWeightConfig(a, curls.copy(list = listOf(800, 1000, 1200, 1600)))
        assertEquals("800,1000,1200,1600", db.entryDao().get(a)!!.weightList)
    }

    @Test
    fun `setWeightConfig leaves an untouched counter untouched and moves the starting weight down`() = runTest {
        val r = repo()
        val a = curlsRow { it.copy(startWeight = 1200) }
        r.setWeightConfig(a, curls.copy(list = listOf(800, 1000, 1400, 1600), startWeight = 1200))
        val row = db.entryDao().get(a)!!
        assertNull(row.total)
        assertEquals(1000, row.startWeight)
        assertEquals(5, r.entry(a).first()!!.counter.total) // 10 kg × 8
    }

    @Test
    fun `a unit change converts before the remap and keeps the rung and the hold count`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12) { it.copy(holdCount = 2) }
        r.setWeightConfig(a, WeightConversion.convert(r.entry(a).first()!!.progression.weight, WeightUnit.LB))
        val row = db.entryDao().get(a)!!
        assertEquals(listOf<Any?>("LB", "1775,2200,2650,3075,3525", 12, 2), listOf(row.weightUnit, row.weightList, row.total, row.holdCount))
    }

    @Test
    fun `setWeightConfig keeps the stored unit when the draft has none`() = runTest {
        val r = repo()
        val a = curlsRow(total = 3)
        r.setWeightConfig(a, curls.copy(unit = null))
        assertEquals("KG", db.entryDao().get(a)!!.weightUnit)
    }

    @Test
    fun `in Reps mode the weight group saves without touching the total or the mode`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.overwriteCounter(a, 60, 1, 1, null)
        r.setWeightConfig(a, curls)
        val e = r.entry(a).first()!!
        assertEquals(60, e.counter.total)
        assertEquals(curls, e.progression.weight)
        assertEquals(ProgressMode.REPS, e.progression.mode)
    }

    @Test
    fun `setProgression never touches the mode or the weight group`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12)
        r.setProgression(a, ProgressionConfig(cap = 80))
        val p = r.entry(a).first()!!.progression
        assertEquals(ProgressMode.REPS_THEN_WEIGHT, p.mode)
        assertEquals(curls, p.weight)
        assertEquals(80, p.cap)
        assertEquals(12, db.entryDao().get(a)!!.total)
    }

    @Test
    fun `switchMode starts fresh and keeps the streaks, the last check-in and the history`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        r.checkIn(a, clock)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        val before = db.entryDao().get(a)!!
        db.entryDao().setCounter(a, before.total, before.bestStreak, before.currentStreak, holdCount = 1, lastCheckIn = before.lastCheckIn)

        r.switchMode(a, ProgressMode.REPS_THEN_WEIGHT, defaultUnit = WeightUnit.LB)
        val row = db.entryDao().get(a)!!
        assertEquals(listOf<Any?>("REPS_THEN_WEIGHT", null, 0, "LB"), listOf(row.progressMode, row.total, row.holdCount, row.weightUnit))
        assertEquals(listOf<Any?>(2, 2, before.lastCheckIn), listOf(row.currentStreak, row.bestStreak, row.lastCheckIn))
        assertEquals(0, r.entry(a).first()!!.counter.total) // 20 lb × 8, the default steps' start
        assertEquals(listOf(CheckInPoint(day1, 48), CheckInPoint(clock.instant, 49)), r.history(a, null).first())

        // Plan Spec note 13: the last check-in is kept, so the next on-time check-in moves +1.
        clock.instant = clock.instant.plusSeconds(24 * 3600)
        r.checkIn(a, clock)
        assertEquals(CheckInPoint(clock.instant, 1, 2000, 9, WeightUnit.LB), r.history(a, null).first().last())

        r.switchMode(a, ProgressMode.REPS, WeightUnit.KG)
        assertEquals(48, r.entry(a).first()!!.counter.total)
        assertEquals(3, r.history(a, null).first().size)
        // §9.3: once set, the workout owns its unit; a later default doesn't change it.
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        assertEquals("LB", db.entryDao().get(a)!!.weightUnit)
    }

    @Test
    fun `a new entry has no unit until its first switch into a weight mode`() = runTest {
        val r = repo()
        val a = r.create("Curls")
        assertNull(db.entryDao().get(a)!!.weightUnit)
        r.switchMode(a, ProgressMode.WEIGHT, WeightUnit.KG)
        assertEquals("KG", db.entryDao().get(a)!!.weightUnit)
    }

    @Test
    fun `switching to the current mode changes nothing`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.overwriteCounter(a, 60, 3, 3, null)
        r.switchMode(a, ProgressMode.REPS, WeightUnit.KG)
        assertEquals(CounterState(60, 3, 3, null, 0), r.entry(a).first()!!.counter)
        assertNull(db.entryDao().get(a)!!.weightUnit)
    }

    @Test
    fun `weight writes on missing ids throw EntryNotFound`() = runTest {
        val r = repo()
        expectThrows<EntryNotFound> { r.setWeightConfig(99, curls) }
        expectThrows<EntryNotFound> { r.switchMode(99, ProgressMode.WEIGHT, WeightUnit.KG) }
    }

    @Test
    fun `overwriteCounter takes a level from 0 to the top in a weight mode`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12)
        r.overwriteCounter(a, total = 0, bestStreak = 1, currentStreak = 1, lastCheckIn = null)
        assertEquals(0, db.entryDao().get(a)!!.total)
        assertEquals(0, r.entry(a).first()!!.counter.total)
        r.overwriteCounter(a, total = 24, bestStreak = 1, currentStreak = 1, lastCheckIn = null)
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 25, 1, 1, null) }
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, -1, 1, 1, null) }
        assertEquals(24, db.entryDao().get(a)!!.total)
    }

    @Test
    fun `duplicate copies the mode and the weight group with a fresh counter`() = runTest {
        val r = repo()
        val a = curlsRow(total = 12) { it.copy(weightHolds = "1400:8:4", startWeight = 1000, startReps = 9) }
        val copy = r.duplicate(a)
        val e = r.entry(copy).first()!!
        assertEquals(r.entry(a).first()!!.progression, e.progression)
        assertNull(db.entryDao().get(copy)!!.total)
        assertEquals(6, e.counter.total) // the start: 10 kg × 9
        assertEquals("KG", db.entryDao().get(copy)!!.weightUnit)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*RoomEntryRepositoryTest*"`
Expected: FAIL with "Unresolved reference 'setWeightConfig'" and "Unresolved reference 'switchMode'".

- [ ] **Step 3: Write the minimal implementation**

In `data/db/EntryDao.kt`, append before the closing brace:

```kotlin
    /**
     * Spec rev 26 §2, §9.2: the weight group, the remapped [total] and the hold count in one UPDATE.
     * The repository decides [total] and [resetHoldCount] in the same transaction.
     */
    @Query(
        "UPDATE entry SET weight_unit = :unit, weights_kind = :kind, weight_steps = :steps, weight_list = :list, " +
            "weight_holds = :holds, reps_per_set = :repsPerSet, rep_min = :repMin, rep_max = :repMax, " +
            "start_weight = :startWeight, start_reps = :startReps, total = :total, " +
            "hold_count = CASE WHEN :resetHoldCount THEN 0 ELSE hold_count END WHERE id = :id",
    )
    suspend fun setWeightConfig(
        id: Long,
        unit: String?,
        kind: String,
        steps: String,
        list: String,
        holds: String,
        repsPerSet: Int,
        repMin: Int,
        repMax: Int,
        startWeight: Int?,
        startReps: Int?,
        total: Int?,
        resetHoldCount: Boolean,
    ): Int

    /**
     * Spec rev 26 §2 "Start fresh", §9.3: the mode, an untouched counter (NULL = the new mode's start)
     * and hold count 0, plus [unit] only if the row has none yet. Streaks, last check-in and history stay.
     */
    @Query(
        "UPDATE entry SET progress_mode = :mode, total = NULL, hold_count = 0, " +
            "weight_unit = COALESCE(weight_unit, :unit) WHERE id = :id",
    )
    suspend fun switchMode(id: Long, mode: String, unit: String?): Int
```

In `data/EntryRepository.kt`, add these imports:
- `com.mitenko.repkit.domain.ProgressionScale`, `com.mitenko.repkit.domain.WeightConversion`, `com.mitenko.repkit.domain.WeightValidator`;
- `com.mitenko.repkit.domain.remapWeights`, `com.mitenko.repkit.domain.scale`, `com.mitenko.repkit.domain.weightHoldResetNeeded`;
- `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightConfig`, `com.mitenko.repkit.domain.model.WeightUnit`.

In the interface, after `setType`, add:

```kotlin
    /**
     * Spec rev 26 §2, §9.2, §3.1, plan Spec note 4: writes the weight group in one transaction.
     * - [weight]'s list is sorted, and a null unit keeps the stored one.
     * - The stored group is converted to [weight]'s unit first, if that changed.
     * - The starting point, the holds and, in a weight mode, the current level are remapped by value.
     *   An untouched (NULL) counter stays NULL.
     * - The result must pass WeightValidator, or IllegalArgumentException is thrown and nothing is written.
     * - In a weight mode, the hold count resets only as weightHoldResetNeeded says.
     *
     * Neither the mode nor the Reps fields are written.
     */
    suspend fun setWeightConfig(id: Long, weight: WeightConfig)

    /**
     * Spec rev 26 §2 "Start fresh", §9.3, plan Spec note 5: sets [mode] and returns the counter to the
     * mode's start (total NULL), with hold count 0. Streaks, the last check-in, the history and every
     * mode's settings are kept. Entering a weight mode writes [defaultUnit] (the app default) only if
     * the row has no unit yet. Switching to the current mode is a no-op.
     */
    suspend fun switchMode(id: Long, mode: ProgressMode, defaultUnit: WeightUnit)
```

Also change `overwriteCounter`'s KDoc in the interface:

```kotlin
    /** One transaction: holdCount is reset only if the total changed; a stored NULL counts as the starting total (R3 §6.3). */
```

to:

```kotlin
    /**
     * One transaction: holdCount is reset only if the total changed; a stored NULL counts as the start
     * (R3 §6.3). The total is ≥ 1 in Reps mode and a level in 0..top in a weight mode (spec rev 26 §2).
     */
```

In `RoomEntryRepository`, replace `overwriteCounter`:

```kotlin
    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        // Only currentState's hints depend on the progression, so the defaults are enough to decide validity.
        val check = SettingsValidator.currentState(
            total, bestStreak, currentStreak, lastCheckIn, validationClock.now(), ProgressionConfig(),
        )
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        db.withTransaction {
            // The resolved total (NULL reads as the starting total) is what the Current page showed.
            val old = dao.get(id)?.toDomain()?.counter ?: throw EntryNotFound(id)
```

with:

```kotlin
    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        // Only currentState's hints depend on the progression, so the defaults are enough to decide validity.
        // The total's range depends on the mode (a weight level may be 0), so it's checked in the transaction (plan Spec note 16).
        val check = SettingsValidator.currentState(
            total.coerceAtLeast(1), bestStreak, currentStreak, lastCheckIn, validationClock.now(), ProgressionConfig(),
        )
        require(check.isValid && total >= 0) { "Invalid counter: ${check.errors}, total=$total" }
        gate.awaitReady()
        db.withTransaction {
            // The resolved total (NULL reads as the start) is what the Current page showed.
            val entry = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            val scale = entry.progression.scale()
            val range = if (scale is ProgressionScale.Ladder) scale.minLevel..scale.maxLevel else 1..Int.MAX_VALUE
            require(total in range) { "Invalid total $total for ${entry.progression.mode}" }
            val old = entry.counter
```

The rest of `overwriteCounter` (the `holdCount` and `dao.setCounter` lines) is unchanged.

Add these overrides after `setType`:

```kotlin
    override suspend fun setWeightConfig(id: Long, weight: WeightConfig) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            val stored = row.progression()
            val mode = stored.mode
            // §3.1: sorted on save. §9.2: a unit change converts the stored group first, so values compare exactly.
            val draft = weight.copy(list = weight.list.sorted(), unit = weight.unit ?: stored.weight.unit)
            val old = draft.unit?.let { WeightConversion.convert(stored.weight, it) } ?: stored.weight
            // Reps mode has no level to move, but the starting point and holds still remap, as Reps then weight.
            val remapMode = if (mode.usesWeights) mode else ProgressMode.REPS_THEN_WEIGHT
            val oldLevel = if (mode.usesWeights) validTotal(row.total, min = 0) else null
            val remap = if (draft.weights.isNotEmpty() && draft.repMin in 1..draft.repMax) {
                remapWeights(remapMode, old, draft, oldLevel)
            } else {
                null // the validator rejects this draft below
            }
            val config = remap?.config ?: draft
            val problems = WeightValidator.validate(config, mode)
            require(problems.isEmpty()) { "Invalid weights: $problems" }
            val resetHoldCount = mode.usesWeights && remap != null && weightHoldResetNeeded(mode, old, remap)
            val total = if (mode.usesWeights) remap?.level else row.total
            with(config) {
                dao.setWeightConfig(
                    id, unit?.name, kind.name, WeightCodecs.encodeSteps(steps), WeightCodecs.encodeList(list),
                    WeightCodecs.encodeHolds(holds), repsPerSet, repMin, repMax, startWeight, startReps, total, resetHoldCount,
                )
            }
        }
    }

    override suspend fun switchMode(id: Long, mode: ProgressMode, defaultUnit: WeightUnit) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            if (row.mode() == mode) return@withTransaction
            dao.switchMode(id, mode.name, if (mode.usesWeights) defaultUnit.name else null)
        }
    }
```

`duplicate` needs no code change: `entryEntity(progression = source.progression)` now writes the mode and the weight columns (Task 9). The duplicate test is its guard.

In `testutil/FakeEntryRepository.kt`:
- Add these imports: `com.mitenko.repkit.domain.WeightConversion`, `com.mitenko.repkit.domain.remapWeights`, `com.mitenko.repkit.domain.startLevel`, `com.mitenko.repkit.domain.weightHoldResetNeeded`, `com.mitenko.repkit.domain.model.ProgressMode`, `com.mitenko.repkit.domain.model.WeightConfig` and `com.mitenko.repkit.domain.model.WeightUnit`.
- After `var typeWrites = 0`, add:

```kotlin
    var weightWrites = 0

    /** Every switchMode call as (id, mode), including failed ones. */
    val modeSwitches = mutableListOf<Pair<Long, ProgressMode>>()
```

- Change the `writeError` KDoc to: `/** Thrown once by the next setTiming, setProgression, setWeightConfig, switchMode, overwriteCounter or setType (a repository-side rejection). */`
- Replace `setProgression`'s body:

```kotlin
        edit(id) {
            val holdCount = if (holdResetNeeded(it.progression, progression)) 0 else it.counter.holdCount
            it.copy(progression = progression, counter = it.counter.copy(holdCount = holdCount))
        }
```

with:

```kotlin
        edit(id) {
            // Like Room, setProgression never writes the mode or the weight group (plan Spec note 4).
            val merged = progression.copy(mode = it.progression.mode, weight = it.progression.weight)
            val holdCount = if (holdResetNeeded(it.progression, merged)) 0 else it.counter.holdCount
            it.copy(progression = merged, counter = it.counter.copy(holdCount = holdCount))
        }
```

- After `setType`, add:

```kotlin
    /** Room's remap without its validation (settings validation is left to the ViewModels under test). */
    override suspend fun setWeightConfig(id: Long, weight: WeightConfig) {
        weightWrites++
        failIfAsked()
        edit(id) {
            val stored = it.progression
            val mode = stored.mode
            val draft = weight.copy(list = weight.list.sorted(), unit = weight.unit ?: stored.weight.unit)
            val old = draft.unit?.let { u -> WeightConversion.convert(stored.weight, u) } ?: stored.weight
            val remapMode = if (mode.usesWeights) mode else ProgressMode.REPS_THEN_WEIGHT
            val remap = remapWeights(remapMode, old, draft, if (mode.usesWeights) it.counter.total else null)
            val holdCount = if (mode.usesWeights && weightHoldResetNeeded(mode, old, remap)) 0 else it.counter.holdCount
            it.copy(
                progression = stored.copy(weight = remap.config),
                counter = it.counter.copy(total = remap.level ?: it.counter.total, holdCount = holdCount),
            )
        }
    }

    override suspend fun switchMode(id: Long, mode: ProgressMode, defaultUnit: WeightUnit) {
        modeSwitches += id to mode
        failIfAsked()
        edit(id) {
            if (it.progression.mode == mode) return@edit it
            val unit = it.progression.weight.unit ?: defaultUnit.takeIf { mode.usesWeights }
            val progression = it.progression.copy(mode = mode, weight = it.progression.weight.copy(unit = unit))
            it.copy(progression = progression, counter = it.counter.copy(total = progression.startLevel(), holdCount = 0))
        }
    }
```

- In `duplicate`, replace `counter = CounterState(total = source.progression.startingTotal),` with `counter = CounterState(total = source.progression.startLevel()),`.
- In `resetProgress`, replace `edit(id) { it.copy(counter = CounterState(total = it.progression.startingTotal)) }` with `edit(id) { it.copy(counter = CounterState(total = it.progression.startLevel())) }`. Update its KDoc to `/** Room stores a NULL total, which resolves to the start level; the fake stores the start level directly. */`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*RoomEntryRepositoryTest*" --tests "*ProgressionSettingsViewModelTest*" --tests "*CurrentStateViewModelTest*" --tests "*EntrySettingsViewModelTest*" --tests "*UntouchedTotalTest*"`
Expected: PASS. The existing `overwriteCounter validates like Current State` test still throws `IllegalArgumentException` for a total of 0, now from inside the transaction.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/data/db/EntryDao.kt \
  app/src/main/kotlin/com/mitenko/repkit/data/EntryRepository.kt \
  app/src/test/kotlin/com/mitenko/repkit/testutil/FakeEntryRepository.kt \
  app/src/test/kotlin/com/mitenko/repkit/data/RoomEntryRepositoryTest.kt
git commit -m "Add weight saves with the remap, the start-fresh mode switch and weight-mode levels to the repository"
```

---

### Task 12: App-wide default unit

**Files:**
- Create: `app/src/main/kotlin/com/mitenko/repkit/domain/WeightUnits.kt`
- Modify: `app/src/main/kotlin/com/mitenko/repkit/data/AppPreferences.kt`
- Test: `app/src/test/kotlin/com/mitenko/repkit/domain/WeightUnitsTest.kt`, and `app/src/test/kotlin/com/mitenko/repkit/data/AppPreferencesTest.kt` (append)

**Interfaces:**
- Consumes: `WeightUnit` (Task 1).
- Produces:
  - `fun defaultWeightUnit(country: String): WeightUnit`;
  - `AppPreferences(store, country: () -> String = { Locale.getDefault().country })`;
  - `AppPreferences.weightUnitDefault: Flow<WeightUnit>`;
  - `suspend fun AppPreferences.setWeightUnitDefault(unit: WeightUnit)`;
  - `AppPreferences.WEIGHT_UNIT_DEFAULT` (key `weight_unit_default`).

  PR 2 reads `weightUnitDefault` to pass into `switchMode` and shows it in ⚙ › Units.

- [ ] **Step 1: Write the failing tests**

`domain/WeightUnitsTest.kt`:

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightUnitsTest {
    @Test
    fun `the US, Liberia and Myanmar default to pounds and everywhere else to kilograms`() {
        listOf("US", "LR", "MM", "us").forEach { assertEquals(it, WeightUnit.LB, defaultWeightUnit(it)) }
        listOf("GB", "CA", "DE", "IN", "CN", "ES", "").forEach { assertEquals(it, WeightUnit.KG, defaultWeightUnit(it)) }
    }
}
```

Append inside `AppPreferencesTest` (and add the import `com.mitenko.repkit.domain.model.WeightUnit`):

```kotlin
    @Test
    fun `weightUnitDefault follows the locale when absent`() = runTest {
        val store = store()
        listOf("US" to WeightUnit.LB, "LR" to WeightUnit.LB, "MM" to WeightUnit.LB, "GB" to WeightUnit.KG, "" to WeightUnit.KG)
            .forEach { (country, unit) -> assertEquals(country, unit, AppPreferences(store) { country }.weightUnitDefault.first()) }
    }

    @Test
    fun `setWeightUnitDefault round trips and wins over the locale`() = runTest {
        val prefs = AppPreferences(store()) { "US" }
        prefs.setWeightUnitDefault(WeightUnit.KG)
        assertEquals(WeightUnit.KG, prefs.weightUnitDefault.first())
        prefs.setWeightUnitDefault(WeightUnit.LB)
        assertEquals(WeightUnit.LB, prefs.weightUnitDefault.first())
    }

    @Test
    fun `an unknown stored unit reads as the locale default`() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("weight_unit_default")] = "STONE" }
        assertEquals(WeightUnit.KG, AppPreferences(store) { "GB" }.weightUnitDefault.first())
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightUnitsTest*" --tests "*AppPreferencesTest*"`
Expected: FAIL with "Unresolved reference 'defaultWeightUnit'" and "Unresolved reference 'weightUnitDefault'".

- [ ] **Step 3: Write the minimal implementation**

`domain/WeightUnits.kt`:

```kotlin
package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightUnit
import java.util.Locale

/** The countries that weigh in pounds (spec rev 26 §5): the United States, Liberia and Myanmar. */
private val POUND_COUNTRIES = setOf("US", "LR", "MM")

/** Spec rev 26 §5: LB when [country] (ISO 3166 alpha-2, any case) is in POUND_COUNTRIES, otherwise KG, including for "". */
fun defaultWeightUnit(country: String): WeightUnit =
    if (country.uppercase(Locale.ROOT) in POUND_COUNTRIES) WeightUnit.LB else WeightUnit.KG
```

In `data/AppPreferences.kt`:
- Add these imports: `com.mitenko.repkit.domain.defaultWeightUnit`, `com.mitenko.repkit.domain.model.WeightUnit` and `java.util.Locale`.
- Replace the class KDoc and header:

```kotlin
/**
 * `app.preferences_pb` (spec §5.4): the only app-wide value. The flag is sticky — set once the
 * Android 13+ prompt has been shown, whatever the answer, and never reset.
 */
class AppPreferences(private val store: DataStore<Preferences>) {
```

with:

```kotlin
/**
 * `app.preferences_pb` (spec §5.4): the app-wide values. The notification flag is sticky — set once
 * the Android 13+ prompt has been shown, whatever the answer, and never reset. [country] gives the
 * locale's region for the default unit (spec rev 26 §5); tests pass their own.
 */
class AppPreferences(
    private val store: DataStore<Preferences>,
    private val country: () -> String = { Locale.getDefault().country },
) {
```

- After `setThemeMode`, add:

```kotlin
    /**
     * Spec rev 26 §5: the unit new weight workouts start in (⚙ › Units in PR 2). When it is absent or
     * unrecognised, it follows the locale (defaultWeightUnit). A workout copies it on its first switch
     * into a weight mode (§9.3), so changing it never changes an existing workout.
     */
    val weightUnitDefault: Flow<WeightUnit> = store.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "App preferences read failed", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            prefs[WEIGHT_UNIT_DEFAULT]?.let { stored -> WeightUnit.entries.firstOrNull { it.name == stored } }
                ?: defaultWeightUnit(country())
        }

    suspend fun setWeightUnitDefault(unit: WeightUnit) {
        store.edit { it[WEIGHT_UNIT_DEFAULT] = unit.name }
    }
```

- In the `companion object`, after `THEME_MODE`, add `val WEIGHT_UNIT_DEFAULT = stringPreferencesKey("weight_unit_default")`.

`di/StorageModule.kt` stays unchanged: `AppPreferences(store)` uses the default `country`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 testDebugUnitTest --tests "*WeightUnitsTest*" --tests "*AppPreferencesTest*" --tests "*V1MigratorTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitenko/repkit/domain/WeightUnits.kt \
  app/src/main/kotlin/com/mitenko/repkit/data/AppPreferences.kt \
  app/src/test/kotlin/com/mitenko/repkit/domain/WeightUnitsTest.kt \
  app/src/test/kotlin/com/mitenko/repkit/data/AppPreferencesTest.kt
git commit -m "Add the app-wide default weight unit, derived from the locale"
```

---

### Task 13: Spec notes, project notes and the full gate

**Files:**
- Modify: `docs/superpowers/specs/2026-10-04-weight-progression-design.md` (§5 table, and a new §10)
- Modify: `CLAUDE.md` (the Source of truth list, Plans, and the Room version)

**Interfaces:**
- Consumes: everything above.
- Produces: the documented decisions and a green full gate.

- [ ] **Step 1: Record the decisions in the spec**

In §5's `entry` table, add a row after `weight_list`:

```markdown
| `weight_holds` | TEXT NOT NULL DEFAULT '' | weight-mode holds by value, "weight:reps:for" in hundredths (§10 note 2) |
```

In the bullet under the table, replace "`holds` keeps storing positions. The UI shows them as weight (× reps)." with "`holds` keeps the Reps holds. Weight-mode holds are stored by value in `weight_holds` (§10 note 2), and the engine sees them as positions."

Then append a new section at the end, with one bullet per plan Spec note 1–16, copied from this plan's "Spec notes" with "plan Spec note" changed to "§10 note":

```markdown
## 10. Implementation notes (PR 1, 2026-10-05)

Recorded from `docs/superpowers/plans/2026-10-05-weight-model.md`. Note 13 is an open question for the user.

1. `missFloor` is "no floor" (`Int.MIN_VALUE`) in Reps mode, not the identity: with the identity, §9.1 step 3 would cancel every Reps penalty.
2. …
```

Write the full text of notes 2–16 here; don't leave the "…".

- [ ] **Step 2: Update CLAUDE.md**

- In "Source of truth", add after the last spec bullet: `- **Weight progression (approved, rev 26):** \`docs/superpowers/specs/2026-10-04-weight-progression-design.md\` — Progress by Reps / Weight / Reps then weight; positions on a ladder via \`ProgressionScale\`; Room v7. Its §10 records the PR 1 implementation notes.`
- In "Plans", append: `, \`docs/superpowers/plans/2026-10-05-weight-model.md\` (weight progression PR 1: model and storage)`.
- Change "Room schema changes (current version 6)" to "Room schema changes (current version 7)".

- [ ] **Step 3: Run the full gate**

Announce it first: the full gate runs on the remote worker and takes about 3 minutes.

Run: `pwsh -NoProfile -File D:\Dev\scripts\rgradle.ps1 assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL, every test green (the file-based `MigrationTestHelper` checks are skipped on Windows), and lint at 0 errors with no new warnings in the files this PR touched.

If the worker is offline (exit 2), run the targeted classes locally instead of the full gate, each tagged as Global Constraints says:
- `*WeightConfigTest*`, `*WeightConversionTest*`, `*ProgressionScaleTest*`, `*WeightProgressionTest*`, `*RepProgressionTest*`;
- `*WeightValidatorTest*`, `*WeightRemapTest*`, `*WeightCodecsTest*`, `*HiitDatabaseTest*`, `*EntryMappingTest*`;
- `*RoomEntryRepositoryTest*`, `*AppPreferencesTest*`, `*WeightUnitsTest*`.

Then report that the full gate is still owed.

Confirm that nothing outside the plan changed:

```bash
git status --short          # only ?? docs/feedback.md is left
git diff --stat main -- app/src/main/res app/src/main/kotlin/com/mitenko/repkit/ui   # empty: no UI or string change
```

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-10-04-weight-progression-design.md CLAUDE.md
git commit -m "Record the weight model implementation notes in the spec and project notes"
```

Don't push. Opening the PR (squash, rebase onto `main`, `gh pr create`) follows the PR protocol in CLAUDE.md, and the user asks for it. The PR description must list Spec note 13 as an open question.

---

## Self-review

**Spec coverage (§7.1 and the PR 1 parts of §6):**
- `ProgressionScale`, with round trips for each mode: Task 3.
- `missFloor`, including Reps as "no floor": Tasks 3–4, Spec note 1.
- RepProgression on weight positions (the climb, the reset to 8 at each new weight, −1 and −3 misses, a hold, the cap, the gentler miss): Task 4.
- The §9.1 table:
  - inside one weight, across a boundary, the long miss capped at one weight: Task 4;
  - a miss landing on an active hold, a no-op at position 0, Reps unchanged: Task 4.
- The §9.2 cases:
  - removing the current weight, and a weight below or above it: Task 6;
  - a step change that skips the current value, and a shrinking rep range: Task 6;
  - a hold on a removed weight, and two holds that collide: Task 6;
  - a unit change converted first: Task 6, and Task 11 at the repository.
- The weight list from steps: Task 1. The codecs: Task 7. Unit conversion with rounding: Task 2.
- §3.1 validation (partial edits, duplicates, a step that doesn't divide, a hold on a removed weight): Task 5 (pure) and Task 11 (nothing written).
- Room v7 and the v6→v7 migration (existing rows become REPS with nothing changed): Task 8. The mapping: Task 9.
- History rows carry weight, reps and unit: Task 10.
- Switching mode keeps the history: Task 11.
- §9.3:
  - the migration leaves every unit NULL: Task 8;
  - the first switch writes the default, and a later default leaves the workout alone: Task 11;
  - Reps and Timer Only points are NULL: Task 10;
  - an lb point shown on a kg workout is PR 3's display; the conversion it needs is Task 2.
- The AppPreferences default from the locale: Task 12. Duplicate copies the weight config: Task 11.
- Out of scope, and not touched: Compose, settings screens, chart, timer, voice, notifications, strings and translations (Task 13 checks with `git diff --stat`).

**Placeholder scan:** every code step has the full code. The only "…" in the plan is in Task 13's spec-section skeleton, and that step tells the executor to write out notes 2–16 from this plan's Spec notes.

**Type consistency:**
- `WeightConfig` field names are the same in every task: `unit`, `kind`, `steps`, `list`, `repsPerSet`, `repMin`, `repMax`, `startWeight`, `startReps`, `holds`.
- `WeightHold(weight, reps, forCount)`, `Prescription.Load(weight, reps)` and `WeightRemapResult(config, level, currentChanged)` keep the same shape everywhere.
- These names match between their definitions and their uses: `ladderOf`, `scale()`, `startLevel()`, `engineConfig()`, `loadAt()`, `RepProgression.checkInByMode` and `NO_MISS_FLOOR`.
- So do `remapWeights(mode, old, new, oldLevel)`, `weightHoldResetNeeded(mode, old, remap)` and `WeightValidator.validate(c, mode)`.
- The repository side matches too: `EntryDao.setWeightConfig(..., total, resetHoldCount)`, `EntryDao.switchMode(id, mode, unit)`, `EntryRepository.setWeightConfig(id, weight)` and `EntryRepository.switchMode(id, mode, defaultUnit)`.
- The entity property names in Tasks 8–11 match: `progressMode`, `weightUnit`, `weightsKind`, `weightSteps`, `weightList`, `weightHolds`, `repsPerSet`, `repMin`, `repMax`, `startWeight`, `startReps`, and `CheckInEntity.weight` / `reps` / `unit`.
