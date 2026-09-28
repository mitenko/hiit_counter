# HIIT Counter Settings Pager Implementation Plan (revision 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make an entry's settings faster to use. The four settings sub-screens become one pager screen with tabs and swipe. Every valid change saves itself: stepper changes 400 ms after the last one, everything else at once. Every labelled setting gets an ⓘ tag that explains it. The hold gets an explicit **Hold** switch that remembers its values when off, stored in a new Room column. The hold count is reset only by edits that affect the hold.

**Architecture:** The app stays a single `:app` module using MVVM.
- **Domain (pure, test-first):** `ProgressionConfig` gains `hold` and folds it into `holdEnabled`. `SettingsValidator.progression` skips the hidden hold values when the switch is off. `holdResetNeeded` and `counterHoldReset` in `domain/HoldReset.kt` decide when the hold count resets.
- **Room:** `hiit.db` moves to version 2 with `entry.hold_enabled` and `HiitDatabase.MIGRATION_1_2`, registered in `StorageModule`. `RoomEntryRepository.setProgression` and `overwriteCounter` each become one transaction that reads the row and applies the hold-count rule. `V1Migrator` maps a v1 `hold_for` of 0 to the switch off.
- **Auto-save:** `ui/common/AutoSaver` is one page's save pipeline. It has a 400 ms debounce, a fair `Mutex` so writes land in order, `saveNow`, `cancel`, `flush`, and `flushIn(scope)`. A write that has started always finishes.
- **ViewModels:** Timing, Progression and Current State each keep a typed draft in their `SavedStateHandle` and own one `AutoSaver`. When the ViewModel is cleared, it flushes through the `@ApplicationScope`.
- **UI:** `SettingsPagerRoute` hosts the four pages (`TimingPage`, `ProgressionPage`, `CurrentStatePage`, `CuesPage`) in a `HorizontalPager` under a `PrimaryTabRow`. It flushes on every page change, on back or ←, on `ON_STOP` and on an `onEntryGone` pop. `InfoTag` is a 48 dp ⓘ button that opens a dialog. The texts are `info_*` string resources.

**Tech Stack:** Unchanged from revision 2. These are the pins in `gradle/libs.versions.toml`:
- Kotlin 2.2.10, AGP 8.13.0, Gradle 8.13, KSP 2.2.10-2.0.2.
- Compose BOM 2026.06.01 (Material 3 1.4.0).
- Hilt 2.57 and hilt-navigation-compose 1.3.0.
- Navigation Compose 2.9.5, lifecycle 2.9.4, activity-compose 1.10.0, core-ktx 1.16.0.
- Room 2.8.1 with the `androidx.room` plugin, DataStore Preferences 1.1.1, coroutines 1.10.2.
- JUnit 4.13.2, androidx.test.ext:junit 1.3.0, Robolectric 4.16.

**No new dependencies.** The ⓘ uses a vector drawable with the Material "info outline" glyph. `Icons.Outlined.Info` isn't available: Material 3 1.4.0 no longer brings in `material-icons-core`, and the project draws every icon from `res/drawable` (see Spec notes).

**Spec:** `docs/superpowers/specs/2026-09-28-settings-pager-design.md` (revision 3) is binding. It amends `docs/superpowers/specs/2026-09-25-multi-entry-design.md` (revision 2) and `docs/superpowers/specs/2026-09-24-hiit-counter-design.md` (v1). Read all three before starting. In this plan, `§n` points into revision 3 and `R2 §n` points into revision 2.

**Starting point:** revision 2 as merged on `main` (PR #3). The baseline suite is **265 tests**, with 1 skipped on Windows (`HiitDatabaseTest`'s MigrationTestHelper check). Don't lose any of these R2 behaviours when you edit the files that hold them:
- the `dropUnlessResumed` / `dropUnlessResumedWith` tap guards and active-run routing in `HiitNavHost`;
- `EntryScopedViewModel.missing` → `popToEntries`;
- the Cues read-modify-write `Mutex`;
- the `rememberSaveable` dialog text.

## How this plan is structured

- **9 tasks**, each split into **numbered subtasks** (`3.1`, `3.2`, …). A subtask is one small TDD slice:
  1. write the failing test(s);
  2. run them and see them fail for the stated reason;
  3. write the minimal code;
  4. run the full suite and see the stated cumulative test count pass;
  5. commit.

  **One commit per subtask.**
- Kotlin paths are relative to `app/src/main/kotlin/com/mitenko/hiitcounter/`. Paths starting with `test/` are relative to `app/src/test/kotlin/com/mitenko/hiitcounter/`.
- **New files, and files that grow across subtasks, are shown complete** at each stage, so replace the whole file. Existing files that change once or twice in a small way get **exact edits**: replace the quoted block with the new block. Test files grow by appending the listed test methods to the existing class, and new imports go with them. Test classes that this revision rewrites are shown complete, with a **test ledger** that says which tests are kept, rewritten, deleted or added.
- **Red** runs only the focused test class. **Green** runs the whole `testDebugUnitTest` suite and checks the cumulative count with the test-count command (Global Constraints). Every expected count starts from the **265** baseline recorded in 1.1. If the baseline differs, offset every expected count by the difference.
- A red step sometimes notes a test that already passes. That test is a regression guard for behaviour an earlier slice delivered, and it must stay green.
- Subtasks with nothing to unit-test (the nav swap, docs, the device check) replace red/green with a build or lint verification.
- Every task ends with a **task gate**: `./gradlew assembleDebug testDebugUnitTest lintDebug`. The gate compiles the Hilt graph, runs the suite and runs lint. It must pass before the next task starts.
- **The app compiles and the suite passes at every gate.** From 6.1 to 8.2 the old per-page routes stay registered in `HiitNavHost`. They render the new auto-saving pages through temporary wrappers in `ui/settings/LegacySettingsRoutes.kt`, which 8.2 deletes when the pager takes over. **Don't install any build on the device before 9.2.**
- **Diff review before every whole-file replacement commit:** run `git diff <file>` and confirm the only changes are the ones the subtask describes. In particular, check that no R2 behaviour listed under "Starting point" is lost, and that public signatures are unchanged unless the subtask says otherwise.
- **Pinned versions are fixed.** If anything fails to resolve, stop and report to the user. Don't bump, add or substitute dependencies on your own.

### Layout decision: per-page ViewModels scoped to the pager's back-stack entry

The spec offers two layouts (§6.1 says the drafts live "in the pager ViewModel", and each page has its own save pipeline). This plan keeps **one ViewModel per page**. The pager obtains each one with `hiltViewModel(key = "timing" | "progression" | "current" | "cues")` on its own `NavBackStackEntry`. A small `SettingsPagerViewModel` holds the entry name for the title.

Why this layout rather than one `SettingsPagerViewModel` holding every draft:
- **Each page already is one draft plus one save pipeline.** A keyed ViewModel gets its own `SavedStateHandle`, still seeded with the route's `id`, and owns exactly one `AutoSaver`. A single pager ViewModel would hold three drafts, three validators, three savers and three status flows. Every test would then drag in all four pages.
- **The existing ViewModels, their tests and the restoration test carry over.** Only the save path changes, so reviewers see a focused diff per page. Each page lands in its own subtask (6.1, 6.2, 7.1, 7.2), and a reviewer can reject one and approve its neighbour.
- **The lifetimes are the same as the spec's.** All four ViewModels live exactly as long as the pager's back-stack entry. They survive swipes and rotation. Their drafts survive process recreation through their `SavedStateHandle`, and they're cleared together when the pager is popped, which is when `onCleared` flushes through `@ApplicationScope`.
- **The cost is one line per exit in the route:** it calls `flush()` on the three auto-saving ViewModels on every page change and exit. `SettingsPagerTest` covers it.

## Spec notes (confirm with the user in 1.1)

Reading the real code against the spec surfaced the points below. The plan resolves each as stated, and 9.1 records them in the spec (§10, "Implementation notes") so code and spec don't silently diverge.

1. **Defect: `Icons.Outlined.Info` (§7.1) isn't on the classpath.**
   - Material 3 1.4.0 dropped its `material-icons-core` dependency, and the Gradle cache holds no material-icons artifact. The project draws every icon from `res/drawable`.
   - Resolution: `res/drawable/ic_info.xml` carries the same Material "info outline" glyph. Nothing else about the tag changes (48 dp, content description "About <title>").
2. **Defect: pager-lifetime drafts can go stale on Current.**
   - While the pager is open, the Current draft outlives edits made on Progression. Suppose the stored total is NULL (it reads as the starting total) and the starting total changes on Progression. The Current draft would still show the old total, and its next auto-save (say, a streak edit) would write that stale total, which also resets the hold count. The floor–cap hint would use a stale progression too.
   - Resolution: the Current ViewModel keeps observing the entry.
     - The progression feeds the hint live.
     - A draft that has **no unsaved edits** (it equals the counter last stored, and no save is pending) follows the stored counter.
     - A draft with unsaved edits is never overwritten.
   - Timing and Progression have no cross-page inputs, so they don't need this.
3. **Ambiguity: a Hold switch toggle that doesn't change the effective hold.** For example, the switch is toggled while *hold for* is 0.
   - §6.3 says the reset happens only if "holdAt, holdFor or the effective holdEnabled changed", while §8 says a "switch edit" resets it.
   - Resolution: the §6.3 rule is applied literally, so such a toggle keeps the count. It's harmless, because a disabled hold never reads the count (`RepProgression` gives 0).
4. **Ambiguity: "the total changed" when the stored total is NULL.**
   - Resolution: `overwriteCounter` compares against the **resolved** total (NULL reads as the starting total), which is what the Current page showed.
5. **Ambiguity: the §7.2 texts contain Markdown emphasis** (`*hold at*`).
   - Resolution: the string resources carry the words without the asterisks. Apostrophes are escaped (`\'`). Everything else is verbatim.
6. **Ambiguity: the tab label for Current.**
   - §4 names the tabs "Timing · Progression · Current · Cues", while the Entry Settings row keeps "Current State".
   - Resolution: a new `tab_current` = "Current" string and a `SettingsPage.tab` property. The row labels are unchanged.
7. **Ambiguity: the status line while a debounce is pending.**
   - Resolution: it reads **Saved** for any valid draft (the write is at most 400 ms away and is flushed on every exit). It reads **Not saved: fix the highlighted field** for an invalid draft, and **Not saved** after a rejected write.
   - Cues saves every toggle at once and has no status line.
8. **Clarification: the "flush on clear" in §6.2 names only a pending stepper change.**
   - A dialog-OK write can still be queued behind the `Mutex` when the ViewModel is cleared, and cancelling `viewModelScope` would drop it. So `AutoSaver` keeps a value pending until its write has **completed**. The write runs `NonCancellable` once started, and `flushIn(@ApplicationScope)` covers both cases.
   - **Known, accepted race.** The sequence is:
     1. a valid value V is launched (with `saveNow` or `flush`) and is queued behind an in-flight write;
     2. the draft turns invalid, and `cancel()` clears the pending slot;
     3. the ViewModel is cleared before V's coroutine takes the lock.

     Cancelling `viewModelScope` then drops V, and `flushIn` has nothing pending to re-issue. The stored values stay at the earlier valid state rather than V. That is still a valid state, and the window is a few milliseconds of Main-thread time. Closing it would mean re-issuing cancelled values, which would contradict "invalid cancels" (§6.2).
9. **Divergence: Reset progress stays on the page.**
   - In R2, Reset progress applied and then popped back (`onBack`). In the pager, Current is a tab, so leaving it would drop the user out of settings.
   - Resolution: it applies at once and stays on the page, and the draft shows the reset counter (§6.4 says only "applies immediately").
10. **Divergence: one ViewModel per page instead of all drafts in "the pager ViewModel" (§6.1).**
    - See the layout decision above.
    - Each draft still lives in a ViewModel scoped to the pager's back-stack entry and survives recreation through its own `SavedStateHandle`.
    - Each page still has its own save pipeline.

## Global Constraints

- Package / applicationId: `com.mitenko.hiitcounter`. minSdk **26**, compileSdk **36**, targetSdk **36**. Toolchain JDK **17** (`kotlin { jvmToolchain(17) }`).
- Sources live in `app/src/main/kotlin/...` and tests in `app/src/test/kotlin/...`, never under `java/`.
- `domain/` stays pure Kotlin: no `android.*` or `androidx.*` imports. `Clock` is injected everywhere, so never call `Instant.now()`, `System.currentTimeMillis()` or `SystemClock` outside `platform/AndroidClock.kt`. `ui/common/AutoSaver.kt` is also free of Android imports, so its tests run on the JVM with virtual time.
- `Locale.ENGLISH` for all date and number formatting. Dark theme only and portrait only. **No "WORK" label** on the timer.
- **Commits are authored as `mitenko <mitenko@gmail.com>`.** This identity is applied automatically for GitHub remotes. Confirm that `git config user.email` prints `mitenko@gmail.com` before the first commit (1.1). If it doesn't, stop and ask the user, and never commit with a work identity.
- **No AI attribution.** Commit messages and PR descriptions carry no co-author trailers and no "generated with" footers.
- Git: work on branch **`feat/settings-pager`**, created in 1.1 from an up-to-date `main`. Make **one commit per subtask**, with the messages given. Never merge locally into `main`. Never push or open a PR without asking the user (9.3).
- **Gradle logging convention.** Run Gradle from Git Bash at the repo root. `$SCRATCH` is **the executing session's scratchpad directory**. It is currently `C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad`. If the executing session's scratchpad differs, substitute it everywhere below. Every Bash call starts a fresh shell, so **every command that uses `$SCRATCH` sets it inline in the same call, followed by `mkdir -p "$SCRATCH"`** before the first redirect. Every Gradle run is labelled and logged:
  ```bash
  SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"; ./gradlew <tasks> 2>&1 | tee "$SCRATCH/claude-gradle-<label>.log"; echo "CLAUDE-<LABEL> DONE rc=${PIPESTATUS[0]}"
  ```
  The same `SCRATCH="…"` and `mkdir -p "$SCRATCH"` prefix goes in front of every non-Gradle command block below that writes to `$SCRATCH` (9.2, 9.3).
  `Run (label X, ~N min): <tasks>` below means exactly that command with `<label>` = X. **Announce** the command and its rough duration in the message that starts it. **Never kill a process you did not start.** `java`/`gradle`/`adb`/`studio64` processes may belong to the user's Android Studio.
- **Test-count command** (run after every green step and every gate; it reads the XML of the run that just finished):
  ```bash
  awk -F'"' '/<testsuite /{for(i=1;i<NF;i++) if($i ~ / tests=$/) s+=$(i+1)} END{print "CLAUDE-TEST-COUNT " s}' app/build/test-results/testDebugUnitTest/TEST-*.xml
  ```
  - "Expected: N tests" means `BUILD SUCCESSFUL`, `CLAUDE-TEST-COUNT N` and no failures. The count includes skipped tests.
  - On Windows, 1 test is skipped until 3.1 and 2 from 3.1 on (the MigrationTestHelper guard below).
- **Transient Gradle daemon `BindException`** (caused by the user's Android Studio): retry the same command, **up to 3 times**. Don't kill anything. If the third retry fails too, report it. A killed or interrupted run is **inconclusive**, so re-run it rather than reading its partial output.
- **Room schemas.**
  - `app/schemas/` is served as **debug assets** (`sourceSets["debug"].assets.srcDir("$projectDir/schemas")` in `app/build.gradle.kts`, with `mergeDebugAssets` depending on `copyRoomSchemas*`). Robolectric reads those assets, so `MigrationTestHelper` can load `1.json` and `2.json`.
  - **The generated `app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/2.json` must be committed** (3.1).
  - `1.json` must stay byte-for-byte unchanged (`git diff --exit-code` in 3.1).
- **Windows `MigrationTestHelper` guard.** androidx.sqlite 2.6.1's `SupportSQLiteDriver` compares database names with `substringAfterLast('/')`. That fails on Windows paths (backslashes) for **file-based** `MigrationTestHelper` databases.
  - Every file-based helper test starts with `assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))`. It is skipped locally and runs on CI (Linux).
  - To cover the migration SQL on Windows too, 3.1 also runs `HiitDatabase.MIGRATION_1_2_SQL` against an in-memory framework `SQLiteDatabase`.
- **Robolectric rules:**
  - Every Robolectric test class has `@RunWith(AndroidJUnit4::class)` and `@Config(sdk = [34])`.
  - The default viewport is 320×470 dp. `performClick()` does not scroll, so call `performScrollTo()` first on anything inside a scrolling column that might be below the fold. A footer node (the status line, TOTAL) isn't in a scrolling parent, so it's clicked without `performScrollTo()`.
  - Tests that need a phone-size screen, such as the pager, use `@Config(sdk = [34], qualifiers = "w411dp-h891dp")`.
  - Room in-memory databases need Robolectric: `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java).allowMainThreadQueries().build()`, closed in `@After`.
  - `app/src/test/resources/robolectric.properties` makes Robolectric use a plain `android.app.Application`, so `HiitApp` never runs in unit tests.
- **ViewModel tests and virtual time:**
  - `MainDispatcherRule()` installs an `UnconfinedTestDispatcher` as Main, and `runTest` shares its scheduler. So `advanceTimeBy` / `runCurrent` drive `viewModelScope` delays, including the 400 ms debounce.
  - ViewModels that take `@ApplicationScope` get the test's `backgroundScope` in JVM tests, and `MainScope()` in Robolectric Compose tests.
- **Threading:**
  - `AutoSaver`'s state is touched only on Main. `viewModelScope` and `@ApplicationScope` both use `Dispatchers.Main.immediate`.
  - Room suspend calls return to the caller's dispatcher.
  - There is still no `withContext(Dispatchers.IO)` around `TimerController` calls (R2 §5.3).
- `dropUnlessResumed` (lifecycle-runtime-compose 2.9.4) only has a **zero-arg** overload. Callbacks that take an argument use `dropUnlessResumedWith` (`ui/navigation/NavActions.kt`).

## Subtask Overview

| Task | Subtasks |
|---|---|
| 1 Branch | 1.1 docs on main, branch, identity, baseline |
| 2 Hold domain | 2.1 `ProgressionConfig.hold` · 2.2 validator skips hidden hold values · 2.3 `holdResetNeeded` + `counterHoldReset` |
| 3 Room v2 | 3.1 `hold_enabled`, version 2, `MIGRATION_1_2`, `2.json` · 3.2 mapping + v1 import of `hold_for` 0 · 3.3 transactional `setProgression`/`overwriteCounter`, duplicate, fake repository |
| 4 AutoSaver | 4.1 `AutoSaver` (debounce, `saveNow`, `cancel`, `flush`, `flushIn`, Mutex, `exclusive`) |
| 5 Info tags + rows | 5.1 `InfoTag`, `ic_info`, every R3 string · 5.2 ⓘ in `StepperRow`, dialog-update callback, shared `SwitchRow` |
| 6 Timing + Progression pages | 6.1 Timing page, auto-save, SavedStateHandle draft, status line · 6.2 Progression page, Hold switch, reset confirmation |
| 7 Current + Cues pages | 7.1 Current page, hold-aware counter saves, draft follows the store · 7.2 Cues page |
| 8 Pager + navigation | 8.1 `SettingsPagerViewModel` + `SettingsPagerRoute` · 8.2 pager route, nav swap, legacy routes removed |
| 9 Finish | 9.1 docs + full verification · 9.2 device verification on the Pixel 9a · 9.3 squash and PR |

## File Map

```
app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/2.json            generated, committed (3.1)
app/src/main/res/values/strings.xml                                        R3 strings (5.1); `save` removed (8.2)
app/src/main/res/drawable/ic_info.xml                                      ⓘ glyph (5.1)
app/src/main/kotlin/com/mitenko/hiitcounter/
  domain/model/ProgressionConfig.kt                    hold + holdEnabled (2.1)
  domain/SettingsValidator.kt                          hold off skips HOLD_AT/HOLD_FOR + hint (2.2)
  domain/HoldReset.kt                                  holdResetNeeded, counterHoldReset (2.3)
  data/db/EntryEntity.kt                               hold_enabled (3.1)
  data/db/HiitDatabase.kt                              version 2, MIGRATION_1_2(_SQL), MIGRATIONS (3.1)
  data/db/EntryDao.kt                                  setProgression writes hold_enabled, conditional hold_count (3.3)
  data/EntryMapping.kt                                 hold ⇄ hold_enabled (3.2)
  data/EntryRepository.kt                              setProgression/overwriteCounter transactions (3.3)
  data/v1/V1Readers.kt                                 v1 hold_for 0 → hold off, hold_for 4 (3.2)
  di/StorageModule.kt                                  addMigrations (3.1)
  ui/common/AutoSaver.kt                               save pipeline (4.1)
  ui/common/InfoTag.kt                                 ⓘ + dialog (5.1)
  ui/common/StepperRow.kt                              info, onDialogUpdate (5.2)
  ui/common/SettingsComponents.kt                      SwitchRow (5.2)
  ui/common/SettingsPageComponents.kt                  SaveStatus, SaveStatusLine, SettingsTopBar, SettingsPageLayout (6.1)
  ui/settings/TimingSettings.kt                        TimingSettingsViewModel, TimingPage, TimingPageContent (6.1)
  ui/settings/ProgressionSettings.kt                   ProgressionDraft.hold, ViewModel, ProgressionPage(+Content) (6.2)
  ui/settings/CurrentStateSettings.kt                  ViewModel, CurrentStatePage(+Content) (7.1)
  ui/settings/CuesSettings.kt                          CuesPage (7.2)
  ui/settings/LegacySettingsRoutes.kt                  TEMPORARY old routes over the new pages (6.1–7.2); DELETED (8.2)
  ui/settings/SettingsPager.kt                         SettingsPagerViewModel, SettingsPagerRoute (8.1)
  ui/settings/EntrySettings.kt                         SettingsPage.tab (8.1)
  ui/navigation/{Routes,HiitNavHost}.kt                pager route (8.2)
app/src/test/kotlin/com/mitenko/hiitcounter/
  testutil/FakeEntryRepository.kt                      R3 hold rules, write counters, writeError (3.3)
  domain/{model/ConfigsTest,SettingsValidatorTest}.kt  +1 each (2.1, 2.2)
  domain/HoldResetTest.kt                              new (2.3)
  data/db/HiitDatabaseTest.kt                          +3 (3.1)
  data/{EntryMappingTest,v1/V1ReadersTest,v1/V1MigratorTest}.kt               +2, +1, +1 (3.2)
  data/RoomEntryRepositoryTest.kt                      +5 (3.3)
  ui/common/{AutoSaverTest,InfoTagTest,InfoTextsTest,SwitchRowTest}.kt       new (4.1, 5.1, 5.2)
  ui/common/StepperRowTest.kt                          +2 (5.2)
  ui/settings/Timing{SettingsViewModelTest,SettingsScreenTest,SettingsRestorationTest}.kt   rewritten (6.1); route test deleted (8.2)
  ui/settings/Progression{SettingsViewModelTest,SettingsScreenTest}.kt       rewritten (6.2)
  ui/settings/{CurrentStateViewModelTest,CurrentStatePageTest,CuesPageTest}.kt              rewritten / new (7.1, 7.2)
  ui/settings/{SettingsPagerViewModelTest,SettingsPagerTest}.kt              new (8.1)
  ui/navigation/{RoutesTest,NavActionsTest}.kt         rewritten / +1 (8.2)
claude.md, docs/superpowers/specs/2026-09-28-settings-pager-design.md       notes (9.1)
```

---

## Task 1: Branch

### Subtask 1.1: Docs on main, branch, identity and baseline

**Files:** none.

- [ ] **Step 1: Confirm the spec notes.** Show the user the "Spec notes" section above and ask them to confirm the resolutions:
  - the drawable icon (note 1);
  - the Current draft that follows the store (note 2);
  - the literal §6.3 rule (note 3);
  - the accepted cancel-then-clear race (note 8);
  - Reset progress staying on the page (note 9);
  - one ViewModel per page instead of all drafts in the pager ViewModel (note 10). If they change a resolution, stop and ask how to amend the plan before starting.

- [ ] **Step 2: Preconditions and branch.** The docs branch (`docs/settings-pager`: the R3 spec and this plan) is merged into `main` **before** execution starts. Check that first, without switching branches, so the plan never disappears from the working tree mid-execution:
```bash
git status --short            # must be empty (bash.exe.stackdump is gitignored)
git fetch origin
git show origin/main:docs/superpowers/specs/2026-09-28-settings-pager-design.md > /dev/null && \
  git show origin/main:docs/superpowers/plans/2026-09-28-settings-pager.md > /dev/null && echo "CLAUDE-DOCS-ON-MAIN ok"
```
**Stop and ask the user if this doesn't print `CLAUDE-DOCS-ON-MAIN ok`.** It means the docs PR hasn't been merged yet. Don't check out `main` in that case. Only once the check passes:
```bash
git checkout main && git pull --ff-only     # main now contains the R3 spec and this plan
git show main:docs/superpowers/plans/2026-09-28-settings-pager.md > /dev/null && echo "CLAUDE-DOCS-ON-LOCAL-MAIN ok"
git config user.email         # must print mitenko@gmail.com
git checkout -b feat/settings-pager
```
If `git config user.email` doesn't print `mitenko@gmail.com`, stop and ask. Don't change git config yourself.

- [ ] **Step 3: Baseline** — Run (label `T1-1-BASELINE`, ~3 min): `./gradlew testDebugUnitTest`, then the test-count command. Expected: **265 tests**, all passing, 1 skipped on Windows. If the count differs, record it and offset every expected count in this plan by the difference.

- [ ] **Step 4: No commit** (nothing changed).

**Task 1 gate:** Step 3 is the gate.

---

## Task 2: Hold switch and hold-count rules in the domain (§5.1, §6.3)

**Interfaces produced:**
- `ProgressionConfig(…, penaltyHoursPerRep, hold: Boolean = true)`, where `holdEnabled = hold && holdFor > 0 && holdAt >= floor && holdAt < cap`. `hold` is the **last** parameter, so existing positional calls (`ProgressionConfig(50, 40, 80, 70, 3, 30, 12.5)`) still compile.
- `SettingsValidator.progression` skips `HOLD_AT` and `HOLD_FOR` and gives no hint when `hold` is false.
- `fun holdResetNeeded(old: ProgressionConfig, new: ProgressionConfig): Boolean` and `fun counterHoldReset(oldTotal: Int, newTotal: Int): Boolean`, top level in `domain/HoldReset.kt`.

### Subtask 2.1: `ProgressionConfig.hold`

**Files:** Replace `domain/model/ProgressionConfig.kt`; append to `test/domain/model/ConfigsTest.kt`.

- [ ] **Step 1: Failing test** — append to `ConfigsTest`:
```kotlin
    @Test
    fun `the hold switch turns the hold off whatever the values`() {
        assertFalse(ProgressionConfig(hold = false).holdEnabled)
        assertFalse(ProgressionConfig(hold = false, holdAt = 48).holdEnabled)
        assertTrue(ProgressionConfig(hold = true).holdEnabled)
    }
```

- [ ] **Step 2: Run red** — Run (label `T2-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.model.ConfigsTest"`. Expected: compile FAIL, "No parameter with name 'hold' found". The exact K2 wording may differ.

- [ ] **Step 3: Implement** — `domain/model/ProgressionConfig.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain.model

data class ProgressionConfig(
    val startingTotal: Int = 48,
    val floor: Int = 48,
    val cap: Int = 72,
    val holdAt: Int = 64,
    val holdFor: Int = 4,
    val windowHours: Int = 36,
    val penaltyHoursPerRep: Double = 19.5,
    /** The Hold switch (spec R3 §5.1). Off keeps [holdAt] and [holdFor] stored, but unused. */
    val hold: Boolean = true,
) {
    /** RepProgression reads only this, so the switch needs no change there (spec R3 §5.1). */
    val holdEnabled: Boolean
        get() = hold && holdFor > 0 && holdAt >= floor && holdAt < cap
}
```

- [ ] **Step 4: Run green** — Run (label `T2-1-GREEN`, ~3 min): `./gradlew testDebugUnitTest`, then the test-count command. Expected: **266 tests**.

- [ ] **Step 5: Commit** — diff-review `ProgressionConfig.kt`, then `git add -A && git commit -m "feat(domain): hold switch in ProgressionConfig"`

### Subtask 2.2: The validator skips the hidden hold values when the switch is off

**Files:** Edit `domain/SettingsValidator.kt`; append to `test/domain/SettingsValidatorTest.kt`.

- [ ] **Step 1: Failing test** — append to `SettingsValidatorTest`:
```kotlin
    @Test
    fun `with the hold switched off the hold checks and hint are skipped`() {
        val off = SettingsValidator.progression(ProgressionConfig(hold = false, holdAt = 0, holdFor = -1))
        assertTrue(off.isValid)
        assertTrue(off.hints.isEmpty())
        assertTrue(SettingsValidator.progression(ProgressionConfig(hold = false, holdFor = 0)).hints.isEmpty())
        // Switched on, the checks and the hint behave as before.
        assertEquals(setOf(Field.HOLD_AT, Field.HOLD_FOR), SettingsValidator.progression(ProgressionConfig(holdAt = 0, holdFor = -1)).errorFields())
        assertTrue(Field.HOLD_AT in SettingsValidator.progression(ProgressionConfig(holdFor = 0)).hints)
    }
```

- [ ] **Step 2: Run red** — Run (label `T2-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.SettingsValidatorTest"`. Expected: the new test FAILS at `assertTrue(off.isValid)`, because `HOLD_AT` and `HOLD_FOR` are still checked. The other 6 tests pass.

- [ ] **Step 3: Implement** — `domain/SettingsValidator.kt`. Replace:
```kotlin
        if (c.holdAt < 1) e[Field.HOLD_AT] = "Must be at least 1"
        if (c.holdFor < 0) e[Field.HOLD_FOR] = "Must be 0 or more"
```
with:
```kotlin
        // Spec R3 §5.1: with the Hold switch off, the hidden hold values can't block a save.
        if (c.hold) {
            if (c.holdAt < 1) e[Field.HOLD_AT] = "Must be at least 1"
            if (c.holdFor < 0) e[Field.HOLD_FOR] = "Must be 0 or more"
        }
```
Then replace:
```kotlin
        val hints = if (Field.HOLD_AT !in e && !c.holdEnabled) mapOf(Field.HOLD_AT to "Hold disabled") else emptyMap()
```
with:
```kotlin
        val hints = if (c.hold && Field.HOLD_AT !in e && !c.holdEnabled) mapOf(Field.HOLD_AT to "Hold disabled") else emptyMap()
```

- [ ] **Step 4: Run green** — Run (label `T2-2-GREEN`, ~3 min): full suite + count. Expected: **267 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): skip hidden hold checks when the hold is off"`

### Subtask 2.3: `holdResetNeeded` and `counterHoldReset`

**Files:** Create `domain/HoldReset.kt`, `test/domain/HoldResetTest.kt`.

- [ ] **Step 1: Failing tests** — `test/domain/HoldResetTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldResetTest {
    private val base = ProgressionConfig()

    @Test
    fun `edits that leave the hold unchanged keep the hold count`() {
        assertFalse(holdResetNeeded(base, base))
        assertFalse(holdResetNeeded(base, base.copy(startingTotal = 50)))
        assertFalse(holdResetNeeded(base, base.copy(windowHours = 30)))
        assertFalse(holdResetNeeded(base, base.copy(penaltyHoursPerRep = 12.5)))
        assertFalse(holdResetNeeded(base, base.copy(floor = 40)))
        assertFalse(holdResetNeeded(base, base.copy(cap = 80)))
        // Literal §6.3: a switch toggle that leaves the effective hold off (hold for 0) keeps the count.
        assertFalse(holdResetNeeded(base.copy(holdFor = 0), base.copy(holdFor = 0, hold = false)))
    }

    @Test
    fun `hold at, hold for and switch edits reset the hold count`() {
        assertTrue(holdResetNeeded(base, base.copy(holdAt = 66)))
        assertTrue(holdResetNeeded(base, base.copy(holdFor = 3)))
        assertTrue(holdResetNeeded(base, base.copy(hold = false)))
        assertTrue(holdResetNeeded(base.copy(hold = false), base))
    }

    @Test
    fun `a floor or cap edit that switches the hold on or off resets the hold count`() {
        assertTrue(holdResetNeeded(base, base.copy(cap = 64)))                        // holdAt == cap: off
        assertTrue(holdResetNeeded(base, base.copy(floor = 65, startingTotal = 65)))  // holdAt < floor: off
        assertTrue(holdResetNeeded(base.copy(cap = 64), base))                        // back on
    }

    @Test
    fun `only a total change resets the counter's hold count`() {
        assertFalse(counterHoldReset(64, 64))
        assertTrue(counterHoldReset(64, 65))
        assertTrue(counterHoldReset(64, 48))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T2-3-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.HoldResetTest"`. Expected: compile FAIL, "Unresolved reference 'holdResetNeeded'" and "'counterHoldReset'".

- [ ] **Step 3: Implement** — `domain/HoldReset.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.ProgressionConfig

/**
 * Spec R3 §6.3: a progression save resets the hold count only when the hold itself changes:
 * holdAt, holdFor or the effective [ProgressionConfig.holdEnabled]. Floor, cap and the switch
 * feed into holdEnabled. Starting-total, window and penalty edits keep the count.
 */
fun holdResetNeeded(old: ProgressionConfig, new: ProgressionConfig): Boolean =
    old.holdAt != new.holdAt || old.holdFor != new.holdFor || old.holdEnabled != new.holdEnabled

/** Spec R3 §6.3: overwriting the counter (Current page) resets the hold count only when the total changes. */
fun counterHoldReset(oldTotal: Int, newTotal: Int): Boolean = oldTotal != newTotal
```

- [ ] **Step 4: Run green** — Run (label `T2-3-GREEN`, ~3 min): full suite + count. Expected: **271 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): hold-count reset rules for progression and counter edits"`

**Task 2 gate:** Run (label `T2-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **271 tests**, no lint errors.

---

## Task 3: Room version 2, mapping, v1 import and transactional writes (§5.2, §6.3)

**Interfaces produced:**
- `EntryEntity.holdEnabled: Boolean = true` (column `hold_enabled`, `defaultValue = "1"`).
- `HiitDatabase` at version 2, with `MIGRATION_1_2_SQL: List<String>` (internal), `MIGRATION_1_2: Migration` and `MIGRATIONS: Array<Migration>`.
- `EntryDao.setProgression(id, startingTotal, floor, cap, holdAt, holdFor, holdEnabled, windowHours, penaltyHoursPerRep, resetHoldCount): Int`.
- `RoomEntryRepository.setProgression` / `overwriteCounter` run as one transaction each.
- `FakeEntryRepository` gains `timingWrites`, `progressionWrites`, `counterWrites` and `writeError`.

### Subtask 3.1: `hold_enabled`, version 2, `MIGRATION_1_2` and the exported `2.json`

The entity change and the version bump go in **one** subtask. Changing the entity while the database is still at version 1 would make Room re-export `1.json` with a new identity hash.

**Files:** Edit `data/db/EntryEntity.kt`, `di/StorageModule.kt`; replace `data/db/HiitDatabase.kt`; append to `test/data/db/HiitDatabaseTest.kt`. Generated: `app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/2.json`.

- [ ] **Step 1: Failing tests** — append to `HiitDatabaseTest`. First the new imports:
```kotlin
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
```
Then the tests, helpers and companion, inside the class after the last existing test:
```kotlin
    @Test
    fun `schema v2 is exported with hold_enabled defaulting to 1`() {
        val json = File("schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/2.json").readText()
        assertTrue(Regex("\"version\"\\s*:\\s*2").containsMatchIn(json))
        assertTrue(json.contains("`hold_enabled` INTEGER NOT NULL DEFAULT 1"))
    }

    @Test
    fun `the 1 to 2 migration SQL switches the hold off where hold_for was 0 and restores hold_for 4`() {
        // Runs everywhere (no file-based helper), so Windows also covers the §5.2 SQL.
        val raw = SQLiteDatabase.create(null)
        try {
            raw.execSQL(V1_ENTRY_TABLE)
            raw.execSQL(v1Row(1, holdFor = 4))
            raw.execSQL(v1Row(2, holdFor = 0))
            HiitDatabase.MIGRATION_1_2_SQL.forEach { raw.execSQL(it) }
            assertEquals(listOf(Triple(1L, 1, 4), Triple(2L, 0, 4)), raw.rawQuery(HOLD_QUERY, null).holdColumns())
        } finally {
            raw.close()
        }
    }

    @Test
    fun `migration 1 to 2 validates through MigrationTestHelper`() {
        // Same Windows guard as the v1 check above: androidx.sqlite 2.6.1 mishandles backslash paths. CI runs it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase(MIGRATION_DB, 1).use { db ->
            db.execSQL(v1Row(1, holdFor = 4))
            db.execSQL(v1Row(2, holdFor = 0))
        }
        helper.runMigrationsAndValidate(MIGRATION_DB, 2, true, HiitDatabase.MIGRATION_1_2).use { db ->
            assertEquals(listOf(Triple(1L, 1, 4), Triple(2L, 0, 4)), db.query(HOLD_QUERY).holdColumns())
        }
    }

    /** A v1 row with the default settings and the given hold_for. */
    private fun v1Row(id: Long, holdFor: Int) =
        "INSERT INTO entry (id, name, position, prepare_sec, sets, work_sec, rest_sec, cooldown_sec, starting_total, floor, cap, " +
            "hold_at, hold_for, window_hours, penalty_hours_per_rep, cue_sound, cue_vibration, total, best_streak, " +
            "current_streak, hold_count, last_check_in) " +
            "VALUES ($id, 'Workout', ${id - 1}, 10, 8, 20, 10, 0, 48, 48, 72, 64, $holdFor, 36, 19.5, 1, 1, NULL, 0, 0, 0, NULL)"

    /** (id, hold_enabled, hold_for) per row, ordered by id. */
    private fun Cursor.holdColumns(): List<Triple<Long, Int, Int>> = use {
        buildList { while (moveToNext()) add(Triple(getLong(0), getInt(1), getInt(2))) }
    }

    private companion object {
        const val MIGRATION_DB = "migration-1-2"
        const val HOLD_QUERY = "SELECT id, hold_enabled, hold_for FROM entry ORDER BY id"

        /** The v1 `entry` table exactly as 1.json creates it. */
        const val V1_ENTRY_TABLE = "CREATE TABLE IF NOT EXISTS `entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `prepare_sec` INTEGER NOT NULL, `sets` INTEGER NOT NULL, " +
            "`work_sec` INTEGER NOT NULL, `rest_sec` INTEGER NOT NULL, `cooldown_sec` INTEGER NOT NULL, " +
            "`starting_total` INTEGER NOT NULL, `floor` INTEGER NOT NULL, `cap` INTEGER NOT NULL, `hold_at` INTEGER NOT NULL, " +
            "`hold_for` INTEGER NOT NULL, `window_hours` INTEGER NOT NULL, `penalty_hours_per_rep` REAL NOT NULL, " +
            "`cue_sound` INTEGER NOT NULL, `cue_vibration` INTEGER NOT NULL, `total` INTEGER, `best_streak` INTEGER NOT NULL, " +
            "`current_streak` INTEGER NOT NULL, `hold_count` INTEGER NOT NULL, `last_check_in` INTEGER)"
    }
```

- [ ] **Step 2: Run red** — Run (label `T3-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.db.HiitDatabaseTest"`. Expected: compile FAIL, "Unresolved reference 'MIGRATION_1_2_SQL'" and "'MIGRATION_1_2'".

- [ ] **Step 3: Implement**

`data/db/EntryEntity.kt`. Replace:
```kotlin
    @ColumnInfo(name = "hold_for") val holdFor: Int,
```
with:
```kotlin
    @ColumnInfo(name = "hold_for") val holdFor: Int,
    /** The Hold switch (spec R3 §5.2), added in schema v2 as INTEGER NOT NULL DEFAULT 1. */
    @ColumnInfo(name = "hold_enabled", defaultValue = "1") val holdEnabled: Boolean = true,
```

`data/db/HiitDatabase.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `hiit.db` (spec R2 §5.2). Version 2 adds `entry.hold_enabled` (R3 §5.2). Schemas are exported
 * to app/schemas and committed. Every migration is registered in the builder (StorageModule), and
 * there is no destructive fallback.
 */
@Database(entities = [EntryEntity::class, MetaEntity::class], version = 2, exportSchema = true)
abstract class HiitDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "hiit.db"
        const val KEY_V1_MIGRATED = "v1_migrated"

        /**
         * Spec R3 §5.2, verbatim. A stored hold_for = 0 meant "hold off"; it becomes the switch off
         * with hold_for back at the default 4, so switching the hold on again gives a working hold.
         */
        internal val MIGRATION_1_2_SQL = listOf(
            "ALTER TABLE entry ADD COLUMN hold_enabled INTEGER NOT NULL DEFAULT 1",
            "UPDATE entry SET hold_enabled = 0, hold_for = 4 WHERE hold_for = 0",
        )

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
```

`di/StorageModule.kt`. Replace:
```kotlin
        Room.databaseBuilder(context, HiitDatabase::class.java, HiitDatabase.NAME).build()
```
with:
```kotlin
        Room.databaseBuilder(context, HiitDatabase::class.java, HiitDatabase.NAME)
            .addMigrations(*HiitDatabase.MIGRATIONS)
            .build()
```

- [ ] **Step 4: Run green** — Run (label `T3-1-GREEN`, ~4 min): full suite + count. Expected: **274 tests**, 2 skipped on Windows. Then:
```bash
git diff --exit-code app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/1.json && echo "CLAUDE-V1-SCHEMA-UNCHANGED ok"
git status --short app/schemas   # must list ?? app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/2.json
grep -c '"hold_enabled"' app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/2.json   # ≥ 1
```
If `1.json` changed, stop: the entity and version must change together, so `1.json` should never be re-exported.

- [ ] **Step 5: Commit** — diff-review `HiitDatabase.kt`, then:
```bash
git add -A && git commit -m "feat(db): schema v2 with entry.hold_enabled and migration 1 to 2"
```
`git show --stat HEAD` must list `2.json`.

### Subtask 3.2: `hold_enabled` ⇄ `hold` mapping and the v1 import of `hold_for` 0

**Files:** Edit `data/EntryMapping.kt`, `data/v1/V1Readers.kt`; append to `test/data/EntryMappingTest.kt`, `test/data/v1/V1ReadersTest.kt`, `test/data/v1/V1MigratorTest.kt`.

- [ ] **Step 1: Failing tests**

`EntryMappingTest`: add the imports `org.junit.Assert.assertFalse` and `org.junit.Assert.assertTrue`, then append:
```kotlin
    @Test
    fun `hold_enabled maps to the hold switch and back`() {
        val off = testEntity().copy(holdEnabled = false).toDomain().progression
        assertEquals(ProgressionConfig(hold = false), off)
        assertFalse(off.holdEnabled)
        assertTrue(testEntity().toDomain().progression.hold)
        assertFalse(entryEntity("Burpees", 0, progression = ProgressionConfig(hold = false)).holdEnabled)
        assertTrue(entryEntity("Burpees", 0).holdEnabled)
    }

    @Test
    fun `an inconsistent progression falls back with the hold switched on`() {
        assertEquals(ProgressionConfig(), testEntity().copy(holdEnabled = false, floor = 80, cap = 60).toDomain().progression)
    }
```

`V1ReadersTest`: add the import `org.junit.Assert.assertFalse`, then append:
```kotlin
    @Test
    fun `a v1 hold_for of 0 imports as the hold switched off with hold for 4`() {
        assertEquals(ProgressionConfig(hold = false), prefs { it[V1Keys.HOLD_FOR] = 0 }.readV1Progression())
        assertFalse(v1Entry(prefs { it[V1Keys.HOLD_FOR] = 0 }, emptyPreferences()).holdEnabled)
    }
```

`V1MigratorTest` (the imports are already there), append:
```kotlin
    @Test
    fun `a v1 hold_for of 0 migrates as the hold switched off`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.HOLD_FOR] = 0 }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertFalse(row.holdEnabled)
        assertEquals(4, row.holdFor)
    }
```

- [ ] **Step 2: Run red** — Run (label `T3-2-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.EntryMappingTest" --tests "com.mitenko.hiitcounter.data.v1.*"`. Expected:
  - `hold_enabled maps to the hold switch and back` FAILS: the mapping ignores the column, so `hold` reads true.
  - `a v1 hold_for of 0 …` FAILS in both classes: hold_for 0 is kept.
  - `an inconsistent progression falls back with the hold switched on` already passes (the group fallback gives the defaults). It's a regression guard.

- [ ] **Step 3: Implement**

`data/EntryMapping.kt`, in `EntryEntity.progression()`. Replace:
```kotlin
        penaltyHoursPerRep = checked(id, "penalty_hours_per_rep", penaltyHoursPerRep, d.penaltyHoursPerRep) {
            it > 0.0 && it.isFinite()
        },
    )
```
with:
```kotlin
        penaltyHoursPerRep = checked(id, "penalty_hours_per_rep", penaltyHoursPerRep, d.penaltyHoursPerRep) {
            it > 0.0 && it.isFinite()
        },
        // Spec R3 §5.2: a boolean column (default true); every stored value is valid, and the group fallback gives true.
        hold = holdEnabled,
    )
```
In `entryEntity(…)`, replace:
```kotlin
    holdFor = progression.holdFor,
```
with:
```kotlin
    holdFor = progression.holdFor,
    holdEnabled = progression.hold,
```

`data/v1/V1Readers.kt`, in `readV1Progression()`. Replace:
```kotlin
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "v1 progression inconsistent ($c); using defaults")
    return d
}
```
with:
```kotlin
    val valid = if (SettingsValidator.progression(c).isValid) {
        c
    } else {
        Log.w(TAG, "v1 progression inconsistent ($c); using defaults")
        d
    }
    // Spec R3 §5.2: v1's hold_for = 0 meant "hold off"; it imports as the switch off with hold_for back at 4.
    return if (valid.holdFor == 0) valid.copy(hold = false, holdFor = d.holdFor) else valid
}
```
Duplicate needs no change: it copies `source.progression`, and `entryEntity` now writes `hold`. Subtask 3.3 tests that end to end.

- [ ] **Step 4: Run green** — Run (label `T3-2-GREEN`, ~3 min): full suite + count. Expected: **278 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): map hold_enabled and import a v1 hold_for of 0 as the hold off"`

### Subtask 3.3: Transactional `setProgression` and `overwriteCounter`, duplicate and the fake repository

**Files:** Edit `data/db/EntryDao.kt`, `data/EntryRepository.kt`; replace `test/testutil/FakeEntryRepository.kt`; append to `test/data/RoomEntryRepositoryTest.kt`.

- [ ] **Step 1: Failing tests** — append to `RoomEntryRepositoryTest` (the imports are already there):
```kotlin
    @Test
    fun `setProgression keeps the hold count for floor, penalty and window edits`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        r.setProgression(a, ProgressionConfig(floor = 40))
        r.setProgression(a, ProgressionConfig(floor = 40, penaltyHoursPerRep = 12.5))
        r.setProgression(a, ProgressionConfig(floor = 40, penaltyHoursPerRep = 12.5, windowHours = 30))
        assertEquals(3, r.entry(a).first()!!.counter.holdCount)
    }

    @Test
    fun `setProgression resets the hold count for hold at, hold for and switch edits`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        suspend fun holdCountAfter(p: ProgressionConfig): Int {
            db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
            r.setProgression(a, p)
            return r.entry(a).first()!!.counter.holdCount
        }
        assertEquals(0, holdCountAfter(ProgressionConfig(holdAt = 66)))
        assertEquals(0, holdCountAfter(ProgressionConfig(holdAt = 66, holdFor = 3)))
        assertEquals(0, holdCountAfter(ProgressionConfig(holdAt = 66, holdFor = 3, hold = false)))
        assertFalse(r.entry(a).first()!!.progression.hold)
    }

    @Test
    fun `overwriteCounter keeps the hold count for streak and date edits`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        val last = clock.instant.minusSeconds(3600)
        r.overwriteCounter(a, total = 64, bestStreak = 5, currentStreak = 2, lastCheckIn = last)
        assertEquals(CounterState(64, 5, 2, last, 3), r.entry(a).first()!!.counter)
    }

    @Test
    fun `overwriteCounter resets the hold count for a total edit, a stored null total counting as the starting total`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = null, bestStreak = 0, currentStreak = 0, holdCount = 2, lastCheckIn = null)
        r.overwriteCounter(a, total = 48, bestStreak = 1, currentStreak = 0, lastCheckIn = null)
        assertEquals(2, r.entry(a).first()!!.counter.holdCount)
        r.overwriteCounter(a, total = 49, bestStreak = 1, currentStreak = 0, lastCheckIn = null)
        assertEquals(0, r.entry(a).first()!!.counter.holdCount)
    }

    @Test
    fun `duplicate copies the hold switch`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setProgression(a, ProgressionConfig(hold = false))
        val copy = r.duplicate(a)
        assertFalse(r.entry(copy).first()!!.progression.hold)
        assertFalse(db.entryDao().get(copy)!!.holdEnabled)
    }
```
The existing `setProgression resets holdCount in the same update` (holdFor 4 → 2), `overwriteCounter writes every field and resets holdCount` (total 64 → 65) and `a settings save after the check-in keeps both and resets the hold` (holdFor 4 → 2) still hold under the new rules. They stay unchanged.

- [ ] **Step 2: Run red** — Run (label `T3-3-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.RoomEntryRepositoryTest"`. Expected:
  - Both "keeps the hold count" tests and the null-total test FAIL, because every write still resets the count.
  - The switch-edit test FAILS at `assertFalse(…progression.hold)`, and `duplicate copies the hold switch` FAILS, because the DAO doesn't write `hold_enabled` yet.

- [ ] **Step 3: Implement**

`data/db/EntryDao.kt`. Replace:
```kotlin
    /** The same UPDATE resets hold_count (spec §5.3), so the reset is atomic with the change. */
    @Query(
        "UPDATE entry SET starting_total = :startingTotal, floor = :floor, cap = :cap, hold_at = :holdAt, " +
            "hold_for = :holdFor, window_hours = :windowHours, penalty_hours_per_rep = :penaltyHoursPerRep, " +
            "hold_count = 0 WHERE id = :id",
    )
    suspend fun setProgression(
        id: Long,
        startingTotal: Int,
        floor: Int,
        cap: Int,
        holdAt: Int,
        holdFor: Int,
        windowHours: Int,
        penaltyHoursPerRep: Double,
    ): Int
```
with:
```kotlin
    /**
     * Writes the progression group, resetting hold_count only when [resetHoldCount] is true. The
     * repository decides that with holdResetNeeded inside the same transaction (spec R3 §6.3).
     */
    @Query(
        "UPDATE entry SET starting_total = :startingTotal, floor = :floor, cap = :cap, hold_at = :holdAt, " +
            "hold_for = :holdFor, hold_enabled = :holdEnabled, window_hours = :windowHours, " +
            "penalty_hours_per_rep = :penaltyHoursPerRep, " +
            "hold_count = CASE WHEN :resetHoldCount THEN 0 ELSE hold_count END WHERE id = :id",
    )
    suspend fun setProgression(
        id: Long,
        startingTotal: Int,
        floor: Int,
        cap: Int,
        holdAt: Int,
        holdFor: Int,
        holdEnabled: Boolean,
        windowHours: Int,
        penaltyHoursPerRep: Double,
        resetHoldCount: Boolean,
    ): Int
```

`data/EntryRepository.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.domain.SettingsValidator
```
with:
```kotlin
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.counterHoldReset
import com.mitenko.hiitcounter.domain.holdResetNeeded
```
Replace:
```kotlin
    /** The same UPDATE resets holdCount. */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)
```
with:
```kotlin
    /** One transaction: holdCount is reset only if holdAt, holdFor or the effective holdEnabled changed (R3 §6.3). */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)
```
Replace:
```kotlin
    /** The same UPDATE resets holdCount. */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)
```
with:
```kotlin
    /** One transaction: holdCount is reset only if the total changed; a stored NULL counts as the starting total (R3 §6.3). */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)
```
Replace:
```kotlin
    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        found(
            id,
            with(progression) { dao.setProgression(id, startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep) },
        )
    }
```
with:
```kotlin
    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        db.withTransaction {
            // Compared with the row's effective (repaired) progression, the one checkIn uses.
            val old = dao.get(id)?.progression() ?: throw EntryNotFound(id)
            with(progression) {
                dao.setProgression(
                    id, startingTotal, floor, cap, holdAt, holdFor, hold, windowHours, penaltyHoursPerRep,
                    resetHoldCount = holdResetNeeded(old, progression),
                )
            }
        }
    }
```
Replace:
```kotlin
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        found(
            id,
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount = 0, lastCheckIn = lastCheckIn?.toEpochMilli()),
        )
    }
```
with:
```kotlin
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        db.withTransaction {
            // The resolved total (NULL reads as the starting total) is what the Current page showed.
            val old = dao.get(id)?.toDomain()?.counter ?: throw EntryNotFound(id)
            val holdCount = if (counterHoldReset(old.total, total)) 0 else old.holdCount
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount, lastCheckIn?.toEpochMilli())
        }
    }
```

`test/testutil/FakeEntryRepository.kt` (complete). It mirrors the new rules, and it adds the write counters and `writeError` that Tasks 6–7 use:
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.RepProgression
import com.mitenko.hiitcounter.domain.counterHoldReset
import com.mitenko.hiitcounter.domain.holdResetNeeded
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * In-memory [EntryRepository] with the same contract as RoomEntryRepository: both flows and every
 * suspend call wait for [readiness], missing ids throw EntryNotFound, invalid names throw
 * IllegalArgumentException, positions stay contiguous, and the hold count follows the R3 §6.3
 * rules. Settings validation is left to the ViewModels under test. The write counters and
 * [writeError] let the auto-save tests count and fail individual writes.
 */
class FakeEntryRepository(initial: List<Entry> = emptyList(), ready: Boolean = true) : EntryRepository {
    val readiness = CompletableDeferred<Unit>().apply { if (ready) complete(Unit) }
    val state = MutableStateFlow(initial.sortedWith(compareBy<Entry>({ it.position }, { it.id })))
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    var checkInCalls = 0
    var checkInError: Throwable? = null
    val moves = mutableListOf<Pair<Long, Int>>()
    var deleteCalls = 0

    /** setTiming / setProgression / overwriteCounter calls so far, including failed ones. */
    var timingWrites = 0
    var progressionWrites = 0
    var counterWrites = 0

    /** Thrown once by the next setTiming, setProgression or overwriteCounter (a repository-side rejection). */
    var writeError: Throwable? = null

    override val entries: Flow<List<Entry>> = flow {
        readiness.await()
        emitAll(state)
    }

    override fun entry(id: Long): Flow<Entry?> = flow {
        readiness.await()
        emitAll(state.map { list -> list.firstOrNull { it.id == id } })
    }

    override suspend fun create(name: String): Long {
        val valid = validName(name)
        readiness.await()
        val id = nextId++
        val p = ProgressionConfig()
        state.update { it + Entry(id, valid, it.size, TimingConfig(), p, CueConfig(), CounterState(total = p.startingTotal)) }
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = validName(name)
        edit(id) { it.copy(name = valid) }
    }

    override suspend fun duplicate(id: Long): Long {
        readiness.await()
        val source = find(id)
        val newId = nextId++
        state.update {
            it + source.copy(
                id = newId,
                name = EntryNames.duplicateName(source.name),
                position = it.size,
                counter = CounterState(total = source.progression.startingTotal),
            )
        }
        return newId
    }

    override suspend fun delete(id: Long) {
        readiness.await()
        deleteCalls++
        find(id)
        state.update { list -> list.filter { it.id != id }.mapIndexed { i, e -> e.copy(position = i) } }
    }

    override suspend fun moveBy(id: Long, delta: Int) {
        readiness.await()
        moves += id to delta
        val list = state.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) throw EntryNotFound(id)
        val to = (from + delta).coerceIn(0, list.size - 1)
        list.add(to, list.removeAt(from))
        state.value = list.mapIndexed { i, e -> e.copy(position = i) }
    }

    override suspend fun setTiming(id: Long, timing: TimingConfig) {
        timingWrites++
        failIfAsked()
        edit(id) { it.copy(timing = timing) }
    }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        progressionWrites++
        failIfAsked()
        edit(id) {
            val holdCount = if (holdResetNeeded(it.progression, progression)) 0 else it.counter.holdCount
            it.copy(progression = progression, counter = it.counter.copy(holdCount = holdCount))
        }
    }

    override suspend fun setCues(id: Long, cues: CueConfig) = edit(id) { it.copy(cues = cues) }

    override suspend fun checkIn(id: Long, clock: Clock): CheckInResult {
        readiness.await()
        checkInCalls++
        checkInError?.let { throw it }
        val e = find(id)
        val result = RepProgression.checkIn(e.counter, e.progression, clock.now(), clock.zone())
        if (result.outcome != Outcome.AlreadyToday) edit(id) { it.copy(counter = result.state) }
        return result
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        counterWrites++
        failIfAsked()
        edit(id) {
            val holdCount = if (counterHoldReset(it.counter.total, total)) 0 else it.counter.holdCount
            it.copy(counter = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount))
        }
    }

    override suspend fun resetProgress(id: Long) = edit(id) { it.copy(counter = CounterState(total = it.progression.startingTotal)) }

    fun find(id: Long): Entry = state.value.firstOrNull { it.id == id } ?: throw EntryNotFound(id)

    private fun failIfAsked() {
        writeError?.let { error ->
            writeError = null
            throw error
        }
    }

    private suspend fun edit(id: Long, transform: (Entry) -> Entry) {
        readiness.await()
        find(id)
        state.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun validName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T3-3-GREEN`, ~4 min): full suite + count. Expected: **283 tests**. The existing settings ViewModel tests still pass on the fake. `ProgressionSettingsViewModelTest`'s `save persists the entry's progression and resets the hold` changes holdAt, so the fake still resets the count.

- [ ] **Step 5: Commit** — diff-review `FakeEntryRepository.kt` (the only behaviour changes are the two hold rules, the counters and `writeError`), then `git add -A && git commit -m "feat(data): keep the hold count unless the hold or the total changes, in one transaction"`

**Task 3 gate:** Run (label `T3-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **283 tests** (2 skipped on Windows), no lint errors. `assembleDebug` compiles the Hilt graph with `addMigrations`.

---

## Task 4: The save pipeline (§6.2)

**Interfaces produced:** `class AutoSaver<T>(scope: CoroutineScope, debounceMs: Long = AutoSaver.DEBOUNCE_MS, write: suspend (T) -> Unit)` with:
- `schedule(value)`, `saveNow(value)`, `cancel()`, `flush()`, `flushIn(target: CoroutineScope)`;
- `suspend fun <R> exclusive(block: suspend () -> R): R`;
- `val hasPending: Boolean`;
- `DEBOUNCE_MS = 400L`.

### Subtask 4.1: `AutoSaver`

**Files:** Create `ui/common/AutoSaver.kt`, `test/ui/common/AutoSaverTest.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/common/AutoSaverTest.kt` (complete; JVM only, virtual time):
```kotlin
package com.mitenko.hiitcounter.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaverTest {
    private val writes = mutableListOf<Int>()

    private fun TestScope.saver(
        scope: CoroutineScope = backgroundScope,
        write: suspend (Int) -> Unit = { writes += it },
    ) = AutoSaver(scope, write = write)

    /** A scope on the test scheduler that the test can cancel, like viewModelScope. */
    private fun TestScope.ownerScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

    @Test
    fun `a burst of scheduled values is written once, 400 ms after the last`() = runTest {
        val s = saver()
        repeat(10) {
            s.schedule(it)
            advanceTimeBy(100)
        }
        advanceTimeBy(299)
        runCurrent()
        assertTrue(writes.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(9), writes)
        assertFalse(s.hasPending)
    }

    @Test
    fun `saveNow writes at once and cancels the pending debounce`() = runTest {
        val s = saver()
        s.schedule(1)
        s.saveNow(2)
        runCurrent()
        assertEquals(listOf(2), writes)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(2), writes)
    }

    @Test
    fun `cancel drops a pending value`() = runTest {
        val s = saver()
        s.schedule(1)
        s.cancel()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(writes.isEmpty())
        assertFalse(s.hasPending)
    }

    @Test
    fun `flush writes a pending value at once and does nothing when idle`() = runTest {
        val s = saver()
        s.flush()
        s.schedule(1)
        s.flush()
        runCurrent()
        assertEquals(listOf(1), writes)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(1), writes)
    }

    @Test
    fun `writes are serialised in the order they were requested`() = runTest {
        val s = saver { v ->
            delay(if (v == 1) 300 else 10) // without the Mutex, 2 would land first
            writes += v
        }
        s.saveNow(1)
        s.saveNow(2)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), writes)
    }

    @Test
    fun `flushIn writes the pending value in a scope that outlives the owner`() = runTest {
        val owner = ownerScope()
        val s = saver(scope = owner)
        s.schedule(7)
        owner.cancel() // the ViewModel is cleared: its timer dies with it
        s.flushIn(backgroundScope)
        runCurrent()
        assertEquals(listOf(7), writes)
    }

    @Test
    fun `a write that has started finishes even if its scope is cancelled`() = runTest {
        val owner = ownerScope()
        val s = saver(scope = owner) { v ->
            delay(100)
            writes += v
        }
        s.saveNow(3)
        runCurrent() // the write holds the lock and is suspended in delay
        owner.cancel()
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(3), writes)
        assertFalse(s.hasPending)
    }

    @Test
    fun `exclusive runs after the write in flight`() = runTest {
        val s = saver { v ->
            delay(100)
            writes += v
        }
        s.saveNow(1)
        runCurrent()
        backgroundScope.launch { s.exclusive { writes += -1 } }
        advanceUntilIdle()
        assertEquals(listOf(1, -1), writes)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T4-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.common.AutoSaverTest"`. Expected: compile FAIL, "Unresolved reference 'AutoSaver'".

- [ ] **Step 3: Implement** — `ui/common/AutoSaver.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One page's save pipeline (spec R3 §6.2):
 * - [schedule] debounces stepper changes by [debounceMs], so hold-to-repeat writes once;
 * - [saveNow] writes at once (dialog OK, switch, picker, reset), cancelling the debounce;
 * - [cancel] drops a pending value (the draft became invalid);
 * - [flush] writes a pending value now (page change, leaving, ON_STOP);
 * - [flushIn] does the same in another scope (the @ApplicationScope when the ViewModel is cleared).
 *
 * Writes are serialised by one fair [Mutex] in launch order, so a later draft is never
 * overwritten by an earlier write. A value stays pending until its write has completed, and a
 * write that has started always finishes, even if its scope is cancelled. Use it from one thread
 * (Main): the fields below aren't synchronised.
 *
 * Known race (plan Spec note 8):
 * 1. a launched write is still queued behind the lock;
 * 2. [cancel] clears the pending slot (the draft turned invalid);
 * 3. the owner scope is cancelled before that write takes the lock.
 * The queued value is then lost, because [flushIn] has nothing pending to re-issue. The stored
 * values stay at the earlier valid state.
 */
class AutoSaver<T>(
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val write: suspend (T) -> Unit,
) {
    private class Pending<T>(val value: T)

    private val mutex = Mutex()
    private var pending: Pending<T>? = null
    private var timer: Job? = null

    /**
     * True from [schedule] or [saveNow] until the write of the **latest** value completes. [cancel]
     * makes it false at once. False doesn't mean idle: after [cancel], a write for an older value
     * may still be queued or running. If the latest value's write was dropped with its scope, this
     * stays true until [flushIn] re-issues it.
     */
    val hasPending: Boolean get() = pending != null

    fun schedule(value: T) {
        pending = Pending(value)
        timer?.cancel()
        timer = scope.launch {
            delay(debounceMs)
            timer = null
            flushIn(scope)
        }
    }

    fun saveNow(value: T) {
        pending = Pending(value)
        flushIn(scope)
    }

    fun cancel() {
        timer?.cancel()
        timer = null
        pending = null
    }

    fun flush() = flushIn(scope)

    fun flushIn(target: CoroutineScope) {
        timer?.cancel()
        timer = null
        val next = pending ?: return
        target.launch {
            mutex.withLock {
                withContext(NonCancellable) {
                    write(next.value)
                    if (pending === next) pending = null
                }
            }
        }
    }

    /** Runs [block] after every write already launched, e.g. Reset progress after a pending counter save. */
    suspend fun <R> exclusive(block: suspend () -> R): R = mutex.withLock { block() }

    companion object {
        const val DEBOUNCE_MS = 400L
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T4-1-GREEN`, ~3 min): full suite + count. Expected: **291 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): AutoSaver save pipeline with debounce, ordering and flush"`

**Task 4 gate:** Run (label `T4-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **291 tests**, no lint errors.

---

## Task 5: Info tags and the shared rows (§7)

**Interfaces produced:**
- `InfoTag(title: String, text: String, modifier: Modifier = Modifier)`.
- `StepperRow(…, info: String? = null)`.
- `IntStepperField(label, value, range, input, onUpdate, onDialogUpdate = onUpdate, error, hint, info)` and `PenaltyStepperField(label, value, onUpdate, onDialogUpdate = onUpdate, error, info)`.
- `SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, info: String? = null, modifier: Modifier = Modifier)`, with the switch tagged `switch_<label>`.
- Every R3 string resource, including the 20 `info_*` texts.

### Subtask 5.1: `InfoTag`, the ⓘ drawable and every R3 string

**Files:** Edit `app/src/main/res/values/strings.xml`; create `app/src/main/res/drawable/ic_info.xml`, `ui/common/InfoTag.kt`, `test/ui/common/InfoTagTest.kt`, `test/ui/common/InfoTextsTest.kt`.

- [ ] **Step 1: Failing tests**

`test/ui/common/InfoTagTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InfoTagTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tapping the tag opens its title, text and OK`() {
        compose.setContent { HiitTheme { InfoTag(title = "Floor (min)", text = "The lowest your rep total can fall to.") } }
        compose.onNodeWithTag("info_text").assertDoesNotExist()
        compose.onNodeWithContentDescription("About Floor (min)").performClick()
        compose.onNodeWithTag("info_title").assertTextEquals("Floor (min)")
        compose.onNodeWithTag("info_text").assertTextEquals("The lowest your rep total can fall to.")
        compose.onNodeWithTag("info_ok").performClick()
        compose.onNodeWithTag("info_text").assertDoesNotExist()
    }

    @Test
    fun `the tag is a 48 dp target`() {
        compose.setContent { HiitTheme { InfoTag(title = "SETS", text = "How many work intervals the workout has.") } }
        compose.onNodeWithContentDescription("About SETS").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
}
```

`test/ui/common/InfoTextsTest.kt` (complete). §7.1 requires every row to have a non-blank text. This test pins the resource set; 8.1 checks that each page shows a tag for each row:
```kotlin
package com.mitenko.hiitcounter.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InfoTextsTest {
    @Test
    fun `every settings row has a non-blank info text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fields = R.string::class.java.fields.filter { it.name.startsWith("info_") }
        assertEquals(ROWS.sorted(), fields.map { it.name }.sorted())
        fields.forEach { assertTrue(it.name, context.getString(it.getInt(null)).isNotBlank()) }
    }

    private companion object {
        /** The 20 labelled rows of spec R3 §7.2, in page order. */
        val ROWS = listOf(
            "info_prepare", "info_sets", "info_work", "info_rest", "info_cooldown", "info_total",
            "info_starting_total", "info_floor", "info_cap", "info_hold", "info_hold_at", "info_hold_for",
            "info_window", "info_penalty_rate",
            "info_total_reps", "info_best_streak", "info_current_streak", "info_last_check_in",
            "info_sound", "info_vibration",
        )
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T5-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.common.Info*"`. Expected: compile FAIL, "Unresolved reference 'InfoTag'". (`InfoTextsTest` compiles, but it would fail on the empty resource set.)

- [ ] **Step 3: Implement**

`app/src/main/res/values/strings.xml`. Replace:
```xml
    <string name="edit_value">Edit %1$s</string>
</resources>
```
with the block below. It holds every R3 string, including those used from 6.1 on. The `info_*` texts are the §7.2 texts verbatim, except that the Markdown asterisks around *hold at* are dropped and apostrophes are escaped (Spec note 5):
```xml
    <string name="edit_value">Edit %1$s</string>

    <!-- Settings pager, auto-save, info tags and the Hold switch (spec R3 §4–§7) -->
    <string name="entry_settings_title">%1$s settings</string>
    <string name="tab_current">Current</string>
    <string name="total_label">Total</string>
    <string name="hold">Hold</string>
    <string name="about">About %1$s</string>
    <string name="saved">Saved</string>
    <string name="not_saved_invalid">Not saved: fix the highlighted field</string>
    <string name="not_saved">Not saved</string>
    <string name="reset_defaults_title">Reset progression to defaults?</string>
    <string name="info_prepare">Countdown before the first set, so you can get into position. 0 skips it.</string>
    <string name="info_sets">How many work intervals the workout has. A rest follows every set except the last.</string>
    <string name="info_work">Length of each work interval. The rep table shows how many reps to do in each set.</string>
    <string name="info_rest">Break between sets. There\'s no rest after the last set.</string>
    <string name="info_cooldown">Countdown after the last set. 0 skips it.</string>
    <string name="info_total">The workout\'s full length: prepare, all sets and rests, and cooldown. It can be at most 2:00:00.</string>
    <string name="info_starting_total">The rep total you do on your first check-in, and again after Reset progress. It must be between the floor and the cap.</string>
    <string name="info_floor">The lowest your rep total can fall to, however long you\'re away.</string>
    <string name="info_cap">The highest your rep total can climb to. At the cap, on-time check-ins keep the total where it is.</string>
    <string name="info_hold">When on, your total pauses at hold at for a few check-ins before climbing again.</string>
    <string name="info_hold_at">The rep total where the hold happens. It needs to be at or above the floor and below the cap.</string>
    <string name="info_hold_for">How many check-ins you stay at hold at before going up again. The day you first reach it counts. 0 skips the hold.</string>
    <string name="info_window">Check in within this many hours of your last check-in to count as on time: your total goes up by one and your streak continues.</string>
    <string name="info_penalty_rate">After a missed window, you lose reps for the time away: one rep for roughly every this-many hours past the first day, less one. The total never drops below the floor.</string>
    <string name="info_total_reps">Your current rep total, split across the sets in the rep table. Changing it here takes effect immediately.</string>
    <string name="info_best_streak">Your longest run of on-time check-ins. It must be at least your current streak.</string>
    <string name="info_current_streak">On-time check-ins in a row, including the last one. A missed window restarts it at 1.</string>
    <string name="info_last_check_in">When you last pressed Start. The window and penalty are measured from this. Clear it to make the next check-in count as your first.</string>
    <string name="info_sound">Beeps for the countdown and at each phase change.</string>
    <string name="info_vibration">Vibrates at each phase change, including with the screen off.</string>
</resources>
```

`app/src/main/res/drawable/ic_info.xml` (complete; the Material "info outline" glyph, 24 dp, tinted by `Icon`):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" />
</vector>
```

`ui/common/InfoTag.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R

/**
 * The ⓘ tag (spec R3 §7.1): a 48 dp button described as "About <title>", separate from the row's
 * own tap target. It opens a dialog with [title], [text] and OK. The glyph is Material's "info
 * outline" as a vector drawable, because material-icons isn't on the classpath.
 */
@Composable
fun InfoTag(title: String, text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.about, title))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title, modifier = Modifier.testTag("info_title")) },
            text = { Text(text, modifier = Modifier.testTag("info_text")) },
            confirmButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.testTag("info_ok")) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-1-GREEN`, ~3 min): full suite + count. Expected: **294 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): info tag, info texts and the settings pager strings"`

### Subtask 5.2: ⓘ in the stepper rows, the dialog-update callback and a shared `SwitchRow`

A dialog OK must save at once, while ± steps are debounced (§6.2). So `IntStepperField` and `PenaltyStepperField` gain `onDialogUpdate`. It defaults to `onUpdate`, so every existing caller keeps its behaviour. `SwitchRow` moves from a private helper in `CuesSettings.kt` to `ui/common`, with an ⓘ. Cues keeps its private copy until 7.2 replaces it.

**Files:** Replace `ui/common/StepperRow.kt`, `ui/common/SettingsComponents.kt`; append to `test/ui/common/StepperRowTest.kt`; create `test/ui/common/SwitchRowTest.kt`.

- [ ] **Step 1: Failing tests**

Append to `StepperRowTest` (the imports are already there):
```kotlin
    @Test
    fun `the info tag sits beside the label and opens without editing the value`() {
        compose.setContent {
            HiitTheme {
                Column {
                    IntStepperField(
                        "SETS", 8, FieldRanges.SETS, ValueInput.WHOLE, onUpdate = {},
                        info = "How many work intervals the workout has.",
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("About SETS").performClick()
        compose.onNodeWithTag("info_text").assertTextEquals("How many work intervals the workout has.")
        compose.onNodeWithTag("edit_field").assertDoesNotExist()
    }

    @Test
    fun `a dialog OK goes through the dialog update and a step through the plain update`() {
        var stepped = 0
        var dialog = 0
        value = 8
        compose.setContent {
            HiitTheme {
                Column {
                    IntStepperField(
                        "SETS", value, FieldRanges.SETS, ValueInput.WHOLE,
                        onUpdate = { f -> stepped++; value = f(value) },
                        onDialogUpdate = { f -> dialog++; value = f(value) },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Increase SETS").performClick()
        compose.onNodeWithTag("value_SETS").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("12")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(1 to 1, stepped to dialog)
        assertEquals(12, value)
    }
```

`test/ui/common/SwitchRowTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SwitchRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a switch row toggles and its info tag opens its text`() {
        var on by mutableStateOf(true)
        compose.setContent {
            HiitTheme { Column { SwitchRow("Sound", on, onChange = { on = it }, info = "Beeps for the countdown and at each phase change.") } }
        }
        compose.onNodeWithTag("switch_Sound").performClick()
        assertFalse(on)
        compose.onNodeWithContentDescription("About Sound").performClick()
        compose.onNodeWithTag("info_text").assertTextEquals("Beeps for the countdown and at each phase change.")
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T5-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.common.StepperRowTest" --tests "com.mitenko.hiitcounter.ui.common.SwitchRowTest"`. Expected: compile FAIL, "No parameter with name 'info'" / "'onDialogUpdate'", and "Unresolved reference 'SwitchRow'".

- [ ] **Step 3: Implement**

`ui/common/StepperRow.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.PenaltyDraft
import com.mitenko.hiitcounter.domain.StepRange
import com.mitenko.hiitcounter.domain.ValueFormat

/**
 * The shared stepper row (spec R2 §8.1): the label on top, with its ⓘ tag when [info] is given
 * (R3 §7.1). Below it, 48 dp −/+ buttons (tap = one step, hold = repeat) sit around a large value
 * that opens the edit dialog when tapped, and an inline error or hint goes beneath.
 */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueTap: () -> Unit,
    error: String? = null,
    hint: String? = null,
    info: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The ⓘ is its own 48 dp target, separate from the value's tap-to-edit (spec R3 §7.1).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            info?.let { InfoTag(title = label, text = it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RepeatingIconButton(onMinus, R.drawable.ic_remove, stringResource(R.string.decrease, label))
            Text(
                valueText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (error != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .clickable(onClickLabel = stringResource(R.string.edit_value, label), onClick = onValueTap)
                    .testTag("value_$label"),
            )
            RepeatingIconButton(onPlus, R.drawable.ic_add, stringResource(R.string.increase, label))
        }
        (error ?: hint)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).testTag("support_$label"),
            )
        }
    }
}

/**
 * An integer field on a [StepRange] (spec R2 §8.1). ± clamps at the hard edges and a dialog value
 * is clamped into the range. Cross-field rules are the screen's validation and are not clamped
 * here. [input] is [ValueInput.TIME] (shown as mm:ss) or [ValueInput.WHOLE]. Updates are
 * transforms, so repeated steps always apply to the latest draft. Steps go to [onUpdate] (the
 * pages debounce them), and a dialog OK goes to [onDialogUpdate] (the pages save it at once,
 * spec R3 §6.2).
 */
@Composable
fun IntStepperField(
    label: String,
    value: Int,
    range: StepRange,
    input: ValueInput,
    onUpdate: ((Int) -> Int) -> Unit,
    onDialogUpdate: ((Int) -> Int) -> Unit = onUpdate,
    error: String? = null,
    hint: String? = null,
    info: String? = null,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val time = input == ValueInput.TIME
    val text = if (time) ValueFormat.formatSeconds(value) else value.toString()
    StepperRow(
        label = label,
        valueText = text,
        onMinus = { onUpdate(range::minus) },
        onPlus = { onUpdate(range::plus) },
        onValueTap = { editing = true },
        error = error,
        hint = hint,
        info = info,
    )
    if (editing) {
        EditValueDialog<Int>(
            title = label,
            initialText = text,
            input = input,
            parse = if (time) ValueFormat::parseSeconds else ValueFormat::parseInt,
            onConfirm = { parsed ->
                editing = false
                onDialogUpdate { range.clamp(parsed) }
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * The penalty rate (spec R2 §8.1): ± steps of 0.5 on integer half-hours within 0.5 – 999.5. A
 * dialog value is clamped to that range and kept exactly, even if it isn't a multiple of 0.5; the
 * next ± press snaps it (see [PenaltyDraft]). Steps go to [onUpdate], a dialog OK to [onDialogUpdate].
 */
@Composable
fun PenaltyStepperField(
    label: String,
    value: PenaltyDraft,
    onUpdate: ((PenaltyDraft) -> PenaltyDraft) -> Unit,
    onDialogUpdate: ((PenaltyDraft) -> PenaltyDraft) -> Unit = onUpdate,
    error: String? = null,
    info: String? = null,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val text = ValueFormat.formatDecimal(value.hours)
    StepperRow(
        label = label,
        valueText = text,
        onMinus = { onUpdate(PenaltyDraft::minus) },
        onPlus = { onUpdate(PenaltyDraft::plus) },
        onValueTap = { editing = true },
        error = error,
        info = info,
    )
    if (editing) {
        EditValueDialog(
            title = label,
            initialText = text,
            input = ValueInput.DECIMAL,
            parse = ValueFormat::parseDecimal,
            onConfirm = { hours ->
                editing = false
                onDialogUpdate { PenaltyDraft.of(hours.coerceIn(PenaltyDraft.MIN_HOURS, PenaltyDraft.MAX_HOURS)) }
            },
            onDismiss = { editing = false },
        )
    }
}
```

`ui/common/SettingsComponents.kt` (complete; `SettingsScaffold` and `RepeatingIconButton` are unchanged, and `SwitchRow` is added):
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(bottomBar = bottomBar, contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                actions()
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), content = content)
        }
    }
}

/** 48 dp stepper button; tap = one step, hold = repeat. */
@Composable
fun RepeatingIconButton(onClick: () -> Unit, @DrawableRes icon: Int, contentDescription: String, modifier: Modifier = Modifier) {
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    coroutineScope {
                        currentOnClick()
                        val repeat = launch {
                            delay(400)
                            while (true) {
                                currentOnClick()
                                delay(80)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.surface)
    }
}

/**
 * A labelled switch with the stepper rows' spacing (spec R2 §8.1) and an optional ⓘ after the
 * label (R3 §7.1). The switch is tagged `switch_<label>`.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    info: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        info?.let { InfoTag(title = label, text = it) }
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("switch_$label"))
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-2-GREEN`, ~3 min): full suite + count. Expected: **297 tests**. The existing stepper, Timing, Progression and restoration tests pass unchanged, because `onDialogUpdate` defaults to `onUpdate`.

- [ ] **Step 5: Commit** — diff-review `StepperRow.kt` (only `info`, the label row and `onDialogUpdate` change) and `SettingsComponents.kt` (only `SwitchRow` and its imports are added). Then `git add -A && git commit -m "feat(ui): info tags on stepper rows, dialog-update callback, shared switch row"`

**Task 5 gate:** Run (label `T5-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **297 tests**, no lint errors. Lint may warn about the `info_*` strings that aren't used until Tasks 6–7 (`UnusedResources` is a warning, not an error).

---

## Task 6: Timing and Progression pages with auto-save (§5.3, §6)

Each page follows the same pattern:
- **ViewModel.** It is an `EntryScopedViewModel`. Its typed draft lives in a `MutableStateFlow`, and every change also writes it to the `SavedStateHandle`. On creation, the ViewModel restores the draft from the handle; if there's nothing there, it loads the draft from the repository. It owns one `AutoSaver`.
- **Edits.** `update` (a step) schedules a debounced save, and `updateNow` (a dialog OK, switch or reset) saves at once. An invalid draft cancels any pending save.
- **Flushing.** `flush()` is called by the pager. `onCleared()` calls `flushIn(appScope)`.
- **Status.** `status` is `SaveStatus.of(validation, failed)`.
- **Composables.** Each page has a stateless `XxxPageContent(…)` with `onChange` / `onChangeNow`, and a stateful `XxxPage(vm)`. Neither has a Save button or a top bar.

Until 8.2, the old routes wrap the new pages (`LegacySettingsRoutes.kt`), so `HiitNavHost` is unchanged in this task.

**Interfaces produced:**
- `ui/common/SettingsPageComponents.kt`: `enum class SaveStatus { SAVED, INVALID, FAILED }` with `SaveStatus.of(validation, failed)`, `SaveStatusLine(status, modifier)` (tag `save_status`), `SettingsTopBar(title, onBack)` and `SettingsPageLayout(footer = {}, content)`.
- `TimingSettingsViewModel(savedStateHandle, repo, @ApplicationScope appScope)` with `draft`, `validation`, `status`, `update`, `updateNow` and `flush`. It also has `TimingPage(vm)` and `TimingPageContent(draft, validation, status, onChange, onChangeNow)`.
- `ProgressionDraft(…, penalty, hold: Boolean)`. `ProgressionSettingsViewModel(savedStateHandle, repo, @ApplicationScope appScope)` adds `resetToDefaults()`. It also has `ProgressionPage(vm)` and `ProgressionPageContent(draft, validation, status, onChange, onChangeNow, onReset)`.
- TEMPORARY `LegacySettingsRoutes.kt`: `TimingSettingsRoute(onBack, onEntryGone, vm)` and, from 6.2, `ProgressionSettingsRoute(onBack, onEntryGone, vm)`. Both keep their R2 signatures.

### Subtask 6.1: Timing page (auto-save, SavedStateHandle draft, status line)

**Files:** Create `ui/common/SettingsPageComponents.kt`, `ui/settings/LegacySettingsRoutes.kt`; replace `ui/settings/TimingSettings.kt`, `test/ui/settings/TimingSettingsViewModelTest.kt`, `test/ui/settings/TimingSettingsScreenTest.kt`, `test/ui/settings/TimingSettingsRestorationTest.kt`.

**Test ledger (10 → 16 in these three classes, +6):**

| Class | Test | Fate |
|---|---|---|
| ViewModel | `loads the entry's timing, edits and saves it` | rewritten → `loads the entry's timing and a stepper change saves it after 400 ms` |
| ViewModel | `over two hours is not saved` | rewritten → `an invalid draft never saves and cancels a pending save` |
| ViewModel | `a missing entry reports missing after loading` | kept (constructor gains `appScope`) |
| ViewModel | `a save racing a delete reports missing instead of crashing` | rewritten (a flush replaces `save`) |
| ViewModel | burst of ten · dialog OK · flush · cleared → app scope · restored from handle · rejected write | **added (6)** |
| Screen | `steppers update values and total`, `work cannot step below one second` | kept (now on `TimingPageContent`) |
| Screen | `save disabled when invalid` | rewritten → `the status line reads Not saved while invalid and Saved once valid` |
| Screen | `the route pops to the list when its entry loads as missing` | kept on the legacy route; **deleted in 8.2** (ported to `SettingsPagerTest`) |
| Restoration | both tests | rewritten on `TimingPage(vm)`. The first drops its "unsaved" repository check, because the page now auto-saves. |

- [ ] **Step 1: Failing tests**

`test/ui/settings/TimingSettingsViewModelTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.common.SaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimingSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `loads the entry's timing and a stepper change saves it after 400 ms`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, timing = TimingConfig(sets = 5)), testEntry(2)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(TimingConfig(sets = 5), vm.draft.value)
        vm.update { it.copy(sets = 6) }
        advanceTimeBy(399)
        assertEquals(5, repo.find(1).timing.sets)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(6, repo.find(1).timing.sets)
        assertEquals(TimingConfig(), repo.find(2).timing)
        assertEquals(SaveStatus.SAVED, vm.status.value)
    }

    @Test
    fun `a burst of ten stepper changes writes once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        repeat(10) {
            vm.update { it.copy(sets = it.sets + 1) }
            advanceTimeBy(80) // hold-to-repeat pace
        }
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, repo.timingWrites)
        assertEquals(18, repo.find(1).timing.sets)
    }

    @Test
    fun `an invalid draft never saves and cancels a pending save`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(sets = 9) }
        vm.update { it.copy(sets = 20, workSec = 3599) }
        assertTrue(Field.TOTAL_DURATION in vm.validation.value.errors)
        assertEquals(SaveStatus.INVALID, vm.status.value)
        advanceTimeBy(1_000)
        vm.flush()
        runCurrent()
        assertEquals(0, repo.timingWrites)
        assertEquals(TimingConfig(), repo.find(1).timing)
    }

    @Test
    fun `a dialog OK saves at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(sets = 9) }
        vm.updateNow { it.copy(workSec = 30) }
        runCurrent()
        assertEquals(TimingConfig(sets = 9, workSec = 30), repo.find(1).timing)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, repo.timingWrites)
    }

    @Test
    fun `flush writes a pending stepper change at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(sets = 9) }
        vm.flush()
        runCurrent()
        assertEquals(9, repo.find(1).timing.sets)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, repo.timingWrites)
    }

    @Test
    fun `clearing the view model flushes a pending change through the application scope`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { TimingSettingsViewModel(handle, repo, backgroundScope) } }
        val vm = ViewModelProvider(store, factory)[TimingSettingsViewModel::class.java]
        vm.update { it.copy(sets = 9) }
        store.clear() // cancels viewModelScope (and the debounce), then onCleared
        runCurrent()
        assertEquals(9, repo.find(1).timing.sets)
    }

    @Test
    fun `the typed draft is restored from the saved state handle`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        TimingSettingsViewModel(handle, repo, backgroundScope).update { it.copy(sets = 20, workSec = 3599) } // invalid: never saved
        val restored = TimingSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(TimingConfig(sets = 20, workSec = 3599), restored.draft.value)
        assertEquals(SaveStatus.INVALID, restored.status.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, repo.timingWrites) // only a valid restored draft is scheduled
        assertEquals(TimingConfig(), repo.find(1).timing)
    }

    @Test
    fun `a missing entry reports missing after loading`() = runTest {
        val vm = TimingSettingsViewModel(handle, FakeEntryRepository(), backgroundScope)
        runCurrent()
        assertTrue(vm.missing.value)
        assertNull(vm.draft.value)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(sets = 9) }
        repo.delete(1)
        vm.flush()
        runCurrent()
        assertTrue(vm.missing.value)
    }

    @Test
    fun `a write the repository rejects shows Not saved until the next good write`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo, backgroundScope)
        repo.writeError = IllegalArgumentException("rejected")
        vm.updateNow { it.copy(sets = 9) }
        runCurrent()
        assertEquals(SaveStatus.FAILED, vm.status.value)
        vm.updateNow { it.copy(sets = 10) }
        runCurrent()
        assertEquals(SaveStatus.SAVED, vm.status.value)
        assertEquals(10, repo.find(1).timing.sets)
    }
}
```

`test/ui/settings/TimingSettingsScreenTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimingSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(initial: TimingConfig) {
        compose.setContent {
            HiitTheme {
                var draft by remember { mutableStateOf(initial) }
                val validation = SettingsValidator.timing(draft)
                TimingPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { draft = it(draft) }, onChangeNow = { draft = it(draft) },
                )
            }
        }
    }

    @Test
    fun `steppers update values and total`() {
        show(TimingConfig())
        compose.onNodeWithTag("total").assertTextEquals("TOTAL 04:00")
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.onNodeWithTag("value_SETS").assertTextEquals("9")
        compose.onNodeWithTag("total").assertTextEquals("TOTAL 04:30")
    }

    @Test
    fun `the status line reads Not saved while invalid and Saved once valid`() {
        // 0 + 3 × 59:59 = 2:59:57 is too long; one set fewer, 1:59:58, is fine.
        show(TimingConfig(prepareSec = 0, sets = 3, workSec = 3599, restSec = 0, cooldownSec = 0))
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
        compose.onNodeWithContentDescription("Decrease SETS").performScrollTo().performClick()
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }

    @Test
    fun `work cannot step below one second`() {
        show(TimingConfig(workSec = 5))
        repeat(2) { compose.onNodeWithContentDescription("Decrease WORK").performScrollTo().performClick() }
        compose.onNodeWithTag("value_WORK").assertTextEquals("00:01")
    }

    @Test
    fun `the route pops to the list when its entry loads as missing`() {
        var gone = 0
        val vm = TimingSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), FakeEntryRepository(), MainScope())
        compose.setContent { HiitTheme { TimingSettingsRoute(onBack = {}, onEntryGone = { gone++ }, vm = vm) } }
        compose.waitForIdle()
        assertEquals(1, gone)
    }
}
```

`test/ui/settings/TimingSettingsRestorationTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimingSettingsRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val repo = FakeEntryRepository(listOf(testEntry(1)))
    private val factory = viewModelFactory {
        initializer { TimingSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo, MainScope()) }
    }

    @Test
    fun `the typed draft survives recreation of the view model's owner`() {
        lateinit var before: TimingSettingsViewModel
        compose.setContent {
            HiitTheme {
                val vm: TimingSettingsViewModel = viewModel(factory = factory)
                before = vm
                TimingPage(vm)
            }
        }
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.onNodeWithTag("value_SETS").assertTextEquals("9")
        // Recreate the activity (the ViewModelStoreOwner). The recreated activity has no content of
        // its own, so the draft is checked on the ViewModel it gets back from its store.
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            val after = ViewModelProvider(activity, factory)[TimingSettingsViewModel::class.java]
            assertSame(before, after)
            assertEquals(9, after.draft.value?.sets)
        }
    }

    @Test
    fun `an open edit dialog keeps its text across recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { HiitTheme { TimingPage(viewModel(factory = factory)) } }
        compose.onNodeWithTag("value_PREPARE").performScrollTo().performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("1:3")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("edit_field").assertTextContains("1:3")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T6-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.Timing*"`. Expected: compile FAIL. It reports too many arguments for `TimingSettingsViewModel`, and unresolved references to `updateNow`, `status`, `SaveStatus`, `TimingPageContent` and `TimingPage`. The exact K2 wording may differ.

- [ ] **Step 3: Implement**

`ui/common/SettingsPageComponents.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.ValidationResult

/** What a page's status line says (spec R3 §6.2). */
enum class SaveStatus {
    /** The draft is valid: stored, or about to be (a debounce may still be pending, and every exit flushes it). */
    SAVED,

    /** The draft is invalid: nothing is saved, and the stored values stay at the last valid state. */
    INVALID,

    /** The repository rejected a valid draft (logged). This shouldn't happen. */
    FAILED;

    companion object {
        fun of(validation: ValidationResult, failed: Boolean): SaveStatus = when {
            !validation.isValid -> INVALID
            failed -> FAILED
            else -> SAVED
        }
    }
}

/** "Saved", "Not saved: fix the highlighted field" or "Not saved" (spec R3 §6.2). */
@Composable
fun SaveStatusLine(status: SaveStatus, modifier: Modifier = Modifier) {
    val text = when (status) {
        SaveStatus.SAVED -> R.string.saved
        SaveStatus.INVALID -> R.string.not_saved_invalid
        SaveStatus.FAILED -> R.string.not_saved
    }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = if (status == SaveStatus.SAVED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("save_status"),
    )
}

/** ← and a title, as on the other settings screens (spec R3 §4). */
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
}

/** One settings page: its rows scroll above a fixed [footer] (the status line). There's no Save button (spec R3 §6). */
@Composable
fun SettingsPageLayout(footer: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), content = content)
        footer()
    }
}
```

`ui/settings/TimingSettings.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.di.ApplicationScope
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.ui.common.AutoSaver
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.InfoTag
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.common.SaveStatusLine
import com.mitenko.hiitcounter.ui.common.SettingsPageLayout
import com.mitenko.hiitcounter.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Timing page (spec R3 §6). The typed draft is mirrored into the SavedStateHandle, so it
 * survives swipes, rotation and process recreation. Valid drafts auto-save through an
 * [AutoSaver]: a stepper change 400 ms after the last one, a dialog OK at once. Invalid drafts are
 * never saved.
 */
@HiltViewModel
class TimingSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _draft = MutableStateFlow(savedStateHandle.get<IntArray>(DRAFT_KEY)?.toTiming())
    val draft: StateFlow<TimingConfig?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(SettingsValidator::timing) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    private val saver = AutoSaver<TimingConfig>(viewModelScope) { timing ->
        try {
            repo.setTiming(entryId, timing)
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            // A valid draft can't be rejected; if it is, log it and say "Not saved" (spec R3 §6.2).
            Log.e(TAG, "Timing $timing rejected", e)
            failed.value = true
        }
    }

    init {
        val restored = _draft.value
        when {
            restored == null -> viewModelScope.launch { repo.entry(entryId).first()?.let { setDraft(it.timing) } }
            // A valid draft restored after process death may never have been written; the write is idempotent if it was.
            SettingsValidator.timing(restored).isValid -> saver.schedule(restored)
        }
    }

    /** A stepper change: saved 400 ms after the last one, so hold-to-repeat writes once. */
    fun update(transform: (TimingConfig) -> TimingConfig) = edit(transform, now = false)

    /** A dialog OK: saved at once, cancelling any pending debounce. */
    fun updateNow(transform: (TimingConfig) -> TimingConfig) = edit(transform, now = true)

    /** Writes a pending stepper change now (page change, leaving the pager, ON_STOP). */
    fun flush() = saver.flush()

    /** A change still pending is written in the application scope, so it outlives this ViewModel. */
    override fun onCleared() {
        saver.flushIn(appScope)
    }

    private fun edit(transform: (TimingConfig) -> TimingConfig, now: Boolean) {
        val d = _draft.value?.let(transform) ?: return
        setDraft(d)
        when {
            !SettingsValidator.timing(d).isValid -> saver.cancel()
            now -> saver.saveNow(d)
            else -> saver.schedule(d)
        }
    }

    private fun setDraft(d: TimingConfig) {
        _draft.value = d
        savedStateHandle[DRAFT_KEY] = intArrayOf(d.prepareSec, d.sets, d.workSec, d.restSec, d.cooldownSec)
    }

    private companion object {
        const val TAG = "TimingSettings"
        const val DRAFT_KEY = "timing_draft"

        fun IntArray.toTiming() = TimingConfig(this[0], this[1], this[2], this[3], this[4])
    }
}

/** The Timing page inside the pager (spec R3 §4). */
@Composable
fun TimingPage(vm: TimingSettingsViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    draft?.let { TimingPageContent(it, validation, status, onChange = vm::update, onChangeNow = vm::updateNow) }
}

@Composable
fun TimingPageContent(
    draft: TimingConfig,
    validation: ValidationResult,
    status: SaveStatus,
    onChange: ((TimingConfig) -> TimingConfig) -> Unit,
    onChangeNow: ((TimingConfig) -> TimingConfig) -> Unit,
) {
    val errors = validation.errors
    val totalError = errors[Field.TOTAL_DURATION]
    SettingsPageLayout(
        footer = {
            Column {
                SaveStatusLine(status)
                // The TOTAL footer, red above 2:00:00 (spec R2 §8.1, kept by R3 §6.2).
                Surface(color = if (totalError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.total_duration, TimerText.formatDuration(draft.totalDurationSec)),
                            modifier = Modifier.weight(1f).padding(vertical = 16.dp).testTag("total"),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        InfoTag(stringResource(R.string.total_label), stringResource(R.string.info_total))
                    }
                }
            }
        },
    ) {
        IntStepperField(
            stringResource(R.string.prepare), draft.prepareSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(prepareSec = f(it.prepareSec)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(prepareSec = f(it.prepareSec)) } },
            error = errors[Field.PREPARE], info = stringResource(R.string.info_prepare),
        )
        IntStepperField(
            stringResource(R.string.sets), draft.sets, FieldRanges.SETS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(sets = f(it.sets)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(sets = f(it.sets)) } },
            error = errors[Field.SETS], info = stringResource(R.string.info_sets),
        )
        IntStepperField(
            stringResource(R.string.work), draft.workSec, FieldRanges.WORK, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(workSec = f(it.workSec)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(workSec = f(it.workSec)) } },
            error = errors[Field.WORK], info = stringResource(R.string.info_work),
        )
        IntStepperField(
            stringResource(R.string.rest), draft.restSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(restSec = f(it.restSec)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(restSec = f(it.restSec)) } },
            error = errors[Field.REST], info = stringResource(R.string.info_rest),
        )
        IntStepperField(
            stringResource(R.string.cooldown), draft.cooldownSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(cooldownSec = f(it.cooldownSec)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(cooldownSec = f(it.cooldownSec)) } },
            error = errors[Field.COOLDOWN], info = stringResource(R.string.info_cooldown),
        )
        totalError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}
```

`ui/settings/LegacySettingsRoutes.kt` (complete; TEMPORARY, grows in 6.2, 7.1 and 7.2, deleted in 8.2):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.common.SettingsTopBar

/*
 * TEMPORARY: deleted in 8.2, when the settings pager replaces these routes. Until then each old
 * per-page route shows its new auto-saving page under a top bar, so the app works at every gate.
 * Leaving a page flushes its pending save.
 */

@Composable
fun TimingSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: TimingSettingsViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_timing, onBack = { vm.flush(); onBack() }) { TimingPage(vm) }
}

@Composable
private fun LegacyPage(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(title), onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
```
`HiitNavHost` is unchanged: `TimingSettingsRoute(onBack, onEntryGone)` keeps its signature and package.

- [ ] **Step 4: Run green** — Run (label `T6-1-GREEN`, ~4 min): full suite + count. Expected: **303 tests**.

- [ ] **Step 5: Commit** — diff-review `TimingSettings.kt` (no Save button, no `SettingsScaffold`, and the TOTAL footer is kept), then `git add -A && git commit -m "feat(settings): auto-saving Timing page with a saved-state draft and status line"`

### Subtask 6.2: Progression page (Hold switch, reset confirmation)

**Files:** Replace `ui/settings/ProgressionSettings.kt`, `ui/settings/LegacySettingsRoutes.kt`, `test/ui/settings/ProgressionSettingsViewModelTest.kt`, `test/ui/settings/ProgressionSettingsScreenTest.kt`.

**Test ledger (6 → 12, +6):**

| Class | Test | Fate |
|---|---|---|
| ViewModel | `save persists the entry's progression and resets the hold` | rewritten → `a hold at change auto-saves after 400 ms and resets the hold count` |
| ViewModel | `invalid drafts are not saved` | rewritten → `an invalid draft is never saved` |
| ViewModel | `reset to defaults fills the draft` | rewritten → `reset to defaults saves the defaults at once` |
| ViewModel | `the penalty steps in half hours and a stored non-multiple is saved exactly` | rewritten (flush replaces `save`) |
| ViewModel | `a save racing a delete reports missing instead of crashing` | rewritten (flush) |
| ViewModel | floor keeps the hold count · Hold switch · hold off skips checks · restored from handle | **added (4)** |
| Screen | `cross-field errors show inline and disable save, and the hold hint shows` | rewritten → `cross-field errors show inline with Not saved, and the hold hint shows` |
| Screen | Hold switch hides and restores rows · reset asks first | **added (2)** |

- [ ] **Step 1: Failing tests**

`test/ui/settings/ProgressionSettingsViewModelTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.common.SaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `a hold at change auto-saves after 400 ms and resets the hold count`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 64, holdCount = 2))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(holdAt = 66, holdFor = 3) }
        advanceTimeBy(399)
        assertEquals(ProgressionConfig(), repo.find(1).progression)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(ProgressionConfig(holdAt = 66, holdFor = 3), repo.find(1).progression)
        assertEquals(0, repo.find(1).counter.holdCount)
    }

    @Test
    fun `a floor change keeps the hold count`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 64, holdCount = 2))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(floor = 40) }
        vm.flush()
        runCurrent()
        assertEquals(40, repo.find(1).progression.floor)
        assertEquals(2, repo.find(1).counter.holdCount)
    }

    @Test
    fun `an invalid draft is never saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(cap = 40) }
        assertTrue(Field.CAP in vm.validation.value.errors)
        assertEquals(SaveStatus.INVALID, vm.status.value)
        advanceTimeBy(1_000)
        vm.flush()
        runCurrent()
        assertEquals(0, repo.progressionWrites)
        assertEquals(ProgressionConfig(), repo.find(1).progression)
    }

    @Test
    fun `reset to defaults saves the defaults at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(cap = 90, hold = false))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(90, vm.draft.value?.cap)
        vm.resetToDefaults()
        runCurrent()
        assertEquals(ProgressionDraft.from(ProgressionConfig()), vm.draft.value)
        assertEquals(ProgressionConfig(), repo.find(1).progression)
    }

    @Test
    fun `the penalty steps in half hours and a stored non-multiple is saved exactly`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(penaltyHoursPerRep = 0.3))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        assertEquals(0.3, vm.draft.value!!.penalty.hours, 0.0)
        vm.update { it.copy(windowHours = 30) }
        vm.flush()
        runCurrent()
        assertEquals(0.3, repo.find(1).progression.penaltyHoursPerRep, 0.0)
        vm.update { it.copy(penalty = it.penalty.plus()) }
        vm.flush()
        runCurrent()
        assertEquals(0.5, repo.find(1).progression.penaltyHoursPerRep, 0.0)
    }

    @Test
    fun `the hold switch saves at once and keeps the hidden values`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(holdAt = 66, holdFor = 3))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.updateNow { it.copy(hold = false) }
        runCurrent()
        assertEquals(ProgressionConfig(holdAt = 66, holdFor = 3, hold = false), repo.find(1).progression)
        vm.updateNow { it.copy(hold = true) }
        runCurrent()
        assertEquals(ProgressionConfig(holdAt = 66, holdFor = 3), repo.find(1).progression)
        assertEquals(2, repo.progressionWrites)
    }

    @Test
    fun `with the hold off the hold checks and hint are skipped`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(holdFor = 0))))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        assertTrue(Field.HOLD_AT in vm.validation.value.hints)
        vm.updateNow { it.copy(hold = false) }
        assertTrue(vm.validation.value.hints.isEmpty())
        vm.update { it.copy(holdAt = 80) } // hidden, and at or above the cap: it would give the hint with the hold on
        assertTrue(vm.validation.value.hints.isEmpty())
        vm.flush()
        runCurrent()
        assertEquals(SaveStatus.SAVED, vm.status.value)
        assertEquals(ProgressionConfig(holdAt = 80, holdFor = 0, hold = false), repo.find(1).progression)
    }

    @Test
    fun `the draft and its switch are restored from the saved state handle`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(penaltyHoursPerRep = 0.3))))
        val first = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        first.update { it.copy(cap = 40, hold = false) } // invalid (cap < starting total): never saved
        val restored = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        val d = restored.draft.value!!
        assertEquals(40, d.cap)
        assertFalse(d.hold)
        assertEquals(0.3, d.penalty.hours, 0.0)
        assertEquals(SaveStatus.INVALID, restored.status.value)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo, backgroundScope)
        vm.update { it.copy(floor = 40) }
        repo.delete(1)
        vm.flush()
        runCurrent()
        assertTrue(vm.missing.value)
    }
}
```

`test/ui/settings/ProgressionSettingsScreenTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProgressionSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private var draft by mutableStateOf(ProgressionDraft.from(ProgressionConfig()))
    private var resets = 0

    private fun show(initial: ProgressionConfig) {
        draft = ProgressionDraft.from(initial)
        compose.setContent {
            HiitTheme {
                val validation = SettingsValidator.progression(draft.toConfig())
                ProgressionPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { draft = it(draft) }, onChangeNow = { draft = it(draft) }, onReset = { resets++ },
                )
            }
        }
    }

    @Test
    fun `cross-field errors show inline with Not saved, and the hold hint shows`() {
        show(ProgressionConfig(startingTotal = 40))
        compose.onNodeWithTag("support_Starting total").assertTextEquals("Must be ≥ floor")
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
        draft = ProgressionDraft.from(ProgressionConfig(holdFor = 0))
        compose.onNodeWithTag("support_Hold at").performScrollTo().assertTextEquals("Hold disabled")
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }

    @Test
    fun `the hold switch hides and restores the rows and their values`() {
        show(ProgressionConfig(holdAt = 66, holdFor = 3))
        compose.onNodeWithTag("value_Hold at").performScrollTo().assertTextEquals("66")
        compose.onNodeWithTag("switch_Hold").performScrollTo().performClick()
        compose.onNodeWithTag("value_Hold at").assertDoesNotExist()
        compose.onNodeWithTag("value_Hold for (check-ins)").assertDoesNotExist()
        assertFalse(draft.hold)
        assertEquals(66, draft.holdAt)
        compose.onNodeWithTag("switch_Hold").performScrollTo().performClick()
        compose.onNodeWithTag("value_Hold at").performScrollTo().assertTextEquals("66")
        compose.onNodeWithTag("value_Hold for (check-ins)").performScrollTo().assertTextEquals("3")
    }

    @Test
    fun `reset to defaults asks for confirmation first`() {
        show(ProgressionConfig(cap = 90))
        compose.onNodeWithTag("reset_defaults").performScrollTo().performClick()
        compose.onNodeWithText("Reset progression to defaults?").assertIsDisplayed()
        assertEquals(0, resets)
        compose.onNodeWithTag("confirm_reset_defaults").performClick()
        assertEquals(1, resets)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T6-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.Progression*"`. Expected: compile FAIL. It reports too many arguments for `ProgressionSettingsViewModel`, no parameter `hold` in `ProgressionDraft.copy`, and unresolved references to `updateNow`, `status` and `ProgressionPageContent`.

- [ ] **Step 3: Implement**

`ui/settings/ProgressionSettings.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.di.ApplicationScope
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.PenaltyDraft
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.AutoSaver
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.PenaltyStepperField
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.common.SaveStatusLine
import com.mitenko.hiitcounter.ui.common.SettingsPageLayout
import com.mitenko.hiitcounter.ui.common.SwitchRow
import com.mitenko.hiitcounter.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Typed draft (spec R2 §8.1, R3 §5.3): one value per field, the penalty in integer half-hours, and the Hold switch. */
data class ProgressionDraft(
    val startingTotal: Int,
    val floor: Int,
    val cap: Int,
    val holdAt: Int,
    val holdFor: Int,
    val windowHours: Int,
    val penalty: PenaltyDraft,
    val hold: Boolean,
) {
    fun toConfig() = ProgressionConfig(startingTotal, floor, cap, holdAt, holdFor, windowHours, penalty.hours, hold)

    companion object {
        fun from(c: ProgressionConfig) = ProgressionDraft(
            c.startingTotal, c.floor, c.cap, c.holdAt, c.holdFor, c.windowHours, PenaltyDraft.of(c.penaltyHoursPerRep), c.hold,
        )
    }
}

/**
 * The Progression page (spec R3 §5.3, §6). It uses the same draft and save pipeline as Timing. The
 * Hold switch and Reset to defaults save at once; setProgression keeps the hold count unless the
 * hold itself changes (§6.3).
 */
@HiltViewModel
class ProgressionSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _draft = MutableStateFlow(savedStateHandle.restoredDraft())
    val draft: StateFlow<ProgressionDraft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { d -> d?.let { SettingsValidator.progression(it.toConfig()) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    private val saver = AutoSaver<ProgressionConfig>(viewModelScope) { config ->
        try {
            repo.setProgression(entryId, config)
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Progression $config rejected", e)
            failed.value = true
        }
    }

    init {
        val restored = _draft.value
        when {
            restored == null ->
                viewModelScope.launch { repo.entry(entryId).first()?.let { setDraft(ProgressionDraft.from(it.progression)) } }
            // A valid draft restored after process death may never have been written; the write is idempotent if it was.
            SettingsValidator.progression(restored.toConfig()).isValid -> saver.schedule(restored.toConfig())
        }
    }

    /** A stepper change: saved 400 ms after the last one. */
    fun update(transform: (ProgressionDraft) -> ProgressionDraft) = edit(transform, now = false)

    /** A dialog OK or the Hold switch: saved at once. */
    fun updateNow(transform: (ProgressionDraft) -> ProgressionDraft) = edit(transform, now = true)

    /** After the confirmation (spec R3 §6.4): the draft becomes the defaults and saves at once. */
    fun resetToDefaults() = updateNow { ProgressionDraft.from(ProgressionConfig()) }

    fun flush() = saver.flush()

    override fun onCleared() {
        saver.flushIn(appScope)
    }

    private fun edit(transform: (ProgressionDraft) -> ProgressionDraft, now: Boolean) {
        val d = _draft.value?.let(transform) ?: return
        setDraft(d)
        val config = d.toConfig()
        when {
            !SettingsValidator.progression(config).isValid -> saver.cancel()
            now -> saver.saveNow(config)
            else -> saver.schedule(config)
        }
    }

    private fun setDraft(d: ProgressionDraft) {
        _draft.value = d
        savedStateHandle[DRAFT_KEY] = intArrayOf(
            d.startingTotal, d.floor, d.cap, d.holdAt, d.holdFor, d.windowHours, d.penalty.halfHours, if (d.hold) 1 else 0,
        )
        savedStateHandle[EXACT_KEY] = d.penalty.exact
    }

    private companion object {
        const val TAG = "ProgressionSettings"
        const val DRAFT_KEY = "progression_draft"
        const val EXACT_KEY = "progression_penalty_exact"

        fun SavedStateHandle.restoredDraft(): ProgressionDraft? {
            val a = get<IntArray>(DRAFT_KEY) ?: return null
            return ProgressionDraft(a[0], a[1], a[2], a[3], a[4], a[5], PenaltyDraft(a[6], get<Double>(EXACT_KEY)), a[7] == 1)
        }
    }
}

/** The Progression page inside the pager (spec R3 §4). */
@Composable
fun ProgressionPage(vm: ProgressionSettingsViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    draft?.let {
        ProgressionPageContent(
            it, validation, status, onChange = vm::update, onChangeNow = vm::updateNow, onReset = vm::resetToDefaults,
        )
    }
}

@Composable
fun ProgressionPageContent(
    draft: ProgressionDraft,
    validation: ValidationResult,
    status: SaveStatus,
    onChange: ((ProgressionDraft) -> ProgressionDraft) -> Unit,
    onChangeNow: ((ProgressionDraft) -> ProgressionDraft) -> Unit,
    onReset: () -> Unit,
) {
    val errors = validation.errors
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        IntStepperField(
            stringResource(R.string.starting_total), draft.startingTotal, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(startingTotal = f(it.startingTotal)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(startingTotal = f(it.startingTotal)) } },
            error = errors[Field.STARTING_TOTAL], info = stringResource(R.string.info_starting_total),
        )
        IntStepperField(
            stringResource(R.string.floor), draft.floor, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(floor = f(it.floor)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(floor = f(it.floor)) } },
            error = errors[Field.FLOOR], info = stringResource(R.string.info_floor),
        )
        IntStepperField(
            stringResource(R.string.cap), draft.cap, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(cap = f(it.cap)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(cap = f(it.cap)) } },
            error = errors[Field.CAP], info = stringResource(R.string.info_cap),
        )
        // Spec R3 §5.3: the switch sits directly above Hold at; off hides both rows but keeps their values.
        SwitchRow(
            stringResource(R.string.hold), draft.hold,
            onChange = { on -> onChangeNow { it.copy(hold = on) } }, info = stringResource(R.string.info_hold),
        )
        AnimatedVisibility(visible = draft.hold) {
            Column {
                IntStepperField(
                    stringResource(R.string.hold_at), draft.holdAt, FieldRanges.REPS, ValueInput.WHOLE,
                    onUpdate = { f -> onChange { it.copy(holdAt = f(it.holdAt)) } },
                    onDialogUpdate = { f -> onChangeNow { it.copy(holdAt = f(it.holdAt)) } },
                    error = errors[Field.HOLD_AT], hint = validation.hints[Field.HOLD_AT], info = stringResource(R.string.info_hold_at),
                )
                IntStepperField(
                    stringResource(R.string.hold_for), draft.holdFor, FieldRanges.HOLD_FOR, ValueInput.WHOLE,
                    onUpdate = { f -> onChange { it.copy(holdFor = f(it.holdFor)) } },
                    onDialogUpdate = { f -> onChangeNow { it.copy(holdFor = f(it.holdFor)) } },
                    error = errors[Field.HOLD_FOR], info = stringResource(R.string.info_hold_for),
                )
            }
        }
        IntStepperField(
            stringResource(R.string.window_hours), draft.windowHours, FieldRanges.WINDOW_HOURS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(windowHours = f(it.windowHours)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(windowHours = f(it.windowHours)) } },
            error = errors[Field.WINDOW_HOURS], info = stringResource(R.string.info_window),
        )
        PenaltyStepperField(
            stringResource(R.string.penalty_rate), draft.penalty,
            onUpdate = { f -> onChange { it.copy(penalty = f(it.penalty)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(penalty = f(it.penalty)) } },
            error = errors[Field.PENALTY_RATE], info = stringResource(R.string.info_penalty_rate),
        )
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 16.dp).testTag("reset_defaults")) {
            Text(stringResource(R.string.reset_defaults))
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

`ui/settings/LegacySettingsRoutes.kt` (complete; adds `ProgressionSettingsRoute`):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.common.SettingsTopBar

/*
 * TEMPORARY: deleted in 8.2, when the settings pager replaces these routes. Until then each old
 * per-page route shows its new auto-saving page under a top bar, so the app works at every gate.
 * Leaving a page flushes its pending save.
 */

@Composable
fun TimingSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: TimingSettingsViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_timing, onBack = { vm.flush(); onBack() }) { TimingPage(vm) }
}

@Composable
fun ProgressionSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: ProgressionSettingsViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_progression, onBack = { vm.flush(); onBack() }) { ProgressionPage(vm) }
}

@Composable
private fun LegacyPage(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(title), onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T6-2-GREEN`, ~4 min): full suite + count. Expected: **309 tests**.

- [ ] **Step 5: Commit** — diff-review `ProgressionSettings.kt`: the Hold switch sits above Hold at, the "Hold disabled" hint is kept, the reset is confirmed, and there's no Save button. Then `git add -A && git commit -m "feat(settings): auto-saving Progression page with the Hold switch and a confirmed reset"`

**Task 6 gate:** Run (label `T6-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **309 tests**, no lint errors. `assembleDebug` compiles the Hilt graph with the new `@ApplicationScope CoroutineScope` parameters.

---

## Task 7: Current and Cues pages (§6, §6.3, §6.4)

**Interfaces produced:**
- `CurrentStateViewModel(savedStateHandle, repo, clock, @ApplicationScope appScope)` with `draft`, `validation`, `status`, `update`, `updateNow`, `flush`, `resetProgress()` (it no longer takes a callback), `zone` and `now()`. It also has `CurrentStatePage(vm)` and `CurrentStatePageContent(draft, validation, status, zone, now, onChange, onChangeNow, onResetProgress)`.
- `CuesPage(vm: CuesSettingsViewModel)`.
- `LegacySettingsRoutes.kt` gains `CurrentStateRoute` (7.1) and `CuesSettingsRoute` (7.2), with their R2 signatures.

### Subtask 7.1: Current page (hold-aware counter saves, a draft that follows the store)

This subtask implements Spec note 2. The ViewModel keeps collecting the entry.
- Each emission updates the progression, which feeds the floor–cap hint.
- If the draft equals the counter last stored (`stored`) and no save is pending (`!saver.hasPending`), the draft has no unsaved edits, so it takes the new stored counter. For example, a NULL total now reads as a new starting total. An edit back to the stored value whose save is still pending counts as unsaved.
- A draft with unsaved edits is never overwritten.
- Reset progress runs after any in-flight counter write (`AutoSaver.exclusive`). It drops a pending one first, then shows the reset counter.

**Files:** Replace `ui/settings/CurrentStateSettings.kt`, `ui/settings/LegacySettingsRoutes.kt`, `test/ui/settings/CurrentStateViewModelTest.kt`; create `test/ui/settings/CurrentStatePageTest.kt`.

**Test ledger (5 → 11, +6):**

| Class | Test | Fate |
|---|---|---|
| ViewModel | `loads the entry's counter into a typed draft` | kept (constructor gains `appScope`) |
| ViewModel | `save overwrites the entry's counter` | rewritten → `a stepper change overwrites the counter after 400 ms` |
| ViewModel | `invalid drafts are rejected` | rewritten → `invalid drafts are never saved` |
| ViewModel | `reset progress resets this entry only` | rewritten → `reset progress applies at once, drops a pending save and refreshes the draft` |
| ViewModel | `a save racing a delete reports missing instead of crashing` | rewritten (flush) |
| ViewModel | streak keeps / total resets the hold count · date pick saves at once · draft follows the store · restored from handle | **added (4)** |
| Page | Clear saves at once · reset progress asks first | **added (2)** |

- [ ] **Step 1: Failing tests**

`test/ui/settings/CurrentStateViewModelTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.common.SaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentStateViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock()
    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `loads the entry's counter into a typed draft`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4))))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        assertEquals(CurrentStateViewModel.Draft(65, 24, 4, null), vm.draft.value)
    }

    @Test
    fun `a stepper change overwrites the counter after 400 ms`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(total = 65, best = 24, current = 4) }
        advanceTimeBy(399)
        assertEquals(CounterState(total = 48), repo.find(1).counter)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(CounterState(65, 24, 4, null, 0), repo.find(1).counter)
    }

    @Test
    fun `a streak edit keeps the hold count and a total edit resets it`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 64, holdCount = 2))))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(best = 5) }
        vm.flush()
        runCurrent()
        assertEquals(2, repo.find(1).counter.holdCount)
        vm.update { it.copy(total = 65) }
        vm.flush()
        runCurrent()
        assertEquals(0, repo.find(1).counter.holdCount)
    }

    @Test
    fun `a date pick saves at once`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        val last = clock.instant.minusSeconds(3600)
        vm.updateNow { it.copy(lastCheckIn = last) }
        runCurrent()
        assertEquals(last, repo.find(1).counter.lastCheckIn)
    }

    @Test
    fun `invalid drafts are never saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(best = 3, current = 4) }
        assertTrue(Field.BEST_STREAK in vm.validation.value.errors)
        vm.update { it.copy(best = 4, lastCheckIn = clock.instant.plusSeconds(60)) }
        assertTrue(Field.LAST_CHECK_IN in vm.validation.value.errors)
        vm.update { it.copy(total = 0, lastCheckIn = null) }
        assertTrue(Field.TOTAL in vm.validation.value.errors)
        assertEquals(SaveStatus.INVALID, vm.status.value)
        advanceTimeBy(1_000)
        vm.flush()
        runCurrent()
        assertEquals(0, repo.counterWrites)
        assertEquals(CounterState(total = 48), repo.find(1).counter)
    }

    @Test
    fun `reset progress applies at once, drops a pending save and refreshes the draft`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(1, counter = CounterState(65, 24, 4, clock.instant, 1)),
                testEntry(2, counter = CounterState(total = 50, bestStreak = 3)),
            ),
        )
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(best = 30) } // pending, and must not land after the reset
        vm.resetProgress()
        runCurrent()
        assertEquals(CounterState(total = 48), repo.find(1).counter)
        assertEquals(CurrentStateViewModel.Draft(48, 0, 0, null), vm.draft.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, repo.counterWrites)
        assertEquals(CounterState(total = 50, bestStreak = 3), repo.find(2).counter)
    }

    @Test
    fun `the draft follows the stored counter while it has no unsaved edits`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        // A NULL total re-resolving to a new starting total (an edit on Progression) reaches the store like this.
        repo.state.update { list -> list.map { it.copy(counter = it.counter.copy(total = 50)) } }
        assertEquals(50, vm.draft.value?.total)
        vm.update { it.copy(best = 3) } // an unsaved edit
        repo.state.update { list -> list.map { it.copy(counter = it.counter.copy(total = 52)) } }
        assertEquals(CurrentStateViewModel.Draft(50, 3, 0, null), vm.draft.value)
        // An edit back to exactly the stored counter (52, 0, 0), with its save still pending: a store echo mustn't overwrite it.
        vm.update { it.copy(total = 52, best = 0) }
        repo.state.update { list -> list.map { it.copy(counter = it.counter.copy(total = 53)) } }
        assertEquals(CurrentStateViewModel.Draft(52, 0, 0, null), vm.draft.value)
    }

    @Test
    fun `the draft is restored from the saved state handle`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val last = clock.instant.minusSeconds(60)
        CurrentStateViewModel(handle, repo, clock, backgroundScope).update { it.copy(best = 3, current = 4, lastCheckIn = last) } // invalid
        val restored = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        assertEquals(CurrentStateViewModel.Draft(48, 3, 4, last), restored.draft.value)
        assertEquals(SaveStatus.INVALID, restored.status.value)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock, backgroundScope)
        vm.update { it.copy(best = 5) }
        repo.delete(1)
        vm.flush()
        runCurrent()
        assertTrue(vm.missing.value)
    }
}
```

`test/ui/settings/CurrentStatePageTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CurrentStatePageTest {
    @get:Rule val compose = createComposeRule()

    private var draft by mutableStateOf(CurrentStateViewModel.Draft(65, 24, 4, Instant.parse("2026-09-23T12:00:00Z")))
    private var immediate = 0
    private var resets = 0

    private fun show() {
        compose.setContent {
            HiitTheme {
                CurrentStatePageContent(
                    draft, ValidationResult(), SaveStatus.SAVED, ZoneOffset.UTC, now = { Instant.parse("2026-09-24T12:00:00Z") },
                    onChange = { draft = it(draft) },
                    onChangeNow = { immediate++; draft = it(draft) },
                    onResetProgress = { resets++ },
                )
            }
        }
    }

    @Test
    fun `Clear saves at once`() {
        show()
        compose.onNodeWithTag("clear_last_check_in").performScrollTo().performClick()
        assertEquals(1, immediate)
        assertNull(draft.lastCheckIn)
    }

    @Test
    fun `reset progress asks for confirmation first`() {
        show()
        compose.onNodeWithTag("reset_progress").performScrollTo().performClick()
        compose.onNodeWithText("Reset progress?").assertIsDisplayed()
        assertEquals(0, resets)
        compose.onNodeWithTag("confirm_reset_progress").performClick()
        assertEquals(1, resets)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T7-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.CurrentState*"`. Expected: compile FAIL. It reports too many arguments for `CurrentStateViewModel`, and unresolved references to `updateNow`, `status`, `flush` and `CurrentStatePageContent`. `resetProgress()` is also missing its required `onDone` argument.

- [ ] **Step 3: Implement**

`ui/settings/CurrentStateSettings.kt` (complete; `DateTimePickerDialog` is unchanged):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.di.ApplicationScope
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.AutoSaver
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.InfoTag
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.common.SaveStatusLine
import com.mitenko.hiitcounter.ui.common.SettingsPageLayout
import com.mitenko.hiitcounter.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

/**
 * The Current page (spec R3 §6). It uses the same draft and save pipeline as Timing. overwriteCounter
 * keeps the hold count unless the total changes (§6.3). While the pager is open, a draft without
 * unsaved edits follows the stored counter, and the floor–cap hint follows the stored progression
 * (plan Spec note 2).
 */
@HiltViewModel
class CurrentStateViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : EntryScopedViewModel(savedStateHandle, repo) {
    /** Typed draft (spec R2 §8.1). */
    data class Draft(val total: Int, val best: Int, val current: Int, val lastCheckIn: Instant?)

    /** The entry's own progression, used only for the "outside floor–cap" hint. */
    private val config = MutableStateFlow(ProgressionConfig())
    private val _draft = MutableStateFlow(savedStateHandle.get<LongArray>(DRAFT_KEY)?.toDraft())
    val draft: StateFlow<Draft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = combine(_draft, config) { d, c -> d?.let { validate(it, c) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    private val failed = MutableStateFlow(false)
    val status: StateFlow<SaveStatus> = combine(validation, failed) { v, f -> SaveStatus.of(v, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SaveStatus.SAVED)

    /** The counter as last stored. A draft equal to it, with no save pending, has no unsaved edits. */
    private var stored: Draft? = null

    private val saver = AutoSaver<Draft>(viewModelScope) { d ->
        try {
            repo.overwriteCounter(entryId, d.total, d.best, d.current, d.lastCheckIn)
            failed.value = false
        } catch (e: EntryNotFound) {
            markMissing()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Counter $d rejected", e)
            failed.value = true
        }
    }

    val zone: ZoneId get() = clock.zone()

    fun now(): Instant = clock.now()

    init {
        // A valid draft restored after process death may never have been written; the write is idempotent if it was.
        _draft.value?.let { if (validate(it, config.value).isValid) saver.schedule(it) }
        viewModelScope.launch {
            repo.entry(entryId).filterNotNull().collect { e ->
                config.value = e.progression
                val latest = e.counter.toDraft()
                val current = _draft.value
                // Follow the store only without unsaved edits. An edit back to the stored value
                // whose save is still pending counts as unsaved, so an echo can't overwrite it.
                if (current == null || (current == stored && !saver.hasPending)) setDraft(latest)
                stored = latest
            }
        }
    }

    /** A stepper change: saved 400 ms after the last one. */
    fun update(transform: (Draft) -> Draft) = edit(transform, now = false)

    /** A dialog OK, a date or time pick, or Clear: saved at once. */
    fun updateNow(transform: (Draft) -> Draft) = edit(transform, now = true)

    fun flush() = saver.flush()

    override fun onCleared() {
        saver.flushIn(appScope)
    }

    /**
     * Confirmed on the page and applied at once (spec R3 §6.4). A pending counter save is dropped,
     * and an in-flight one lands first. Then the draft shows the reset counter.
     */
    fun resetProgress() {
        saver.cancel()
        viewModelScope.launch {
            try {
                saver.exclusive { repo.resetProgress(entryId) }
                repo.entry(entryId).first()?.let { e ->
                    val reset = e.counter.toDraft()
                    stored = reset
                    setDraft(reset)
                }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    private fun edit(transform: (Draft) -> Draft, now: Boolean) {
        val d = _draft.value?.let(transform) ?: return
        setDraft(d)
        when {
            !validate(d, config.value).isValid -> saver.cancel()
            now -> saver.saveNow(d)
            else -> saver.schedule(d)
        }
    }

    private fun setDraft(d: Draft) {
        _draft.value = d
        savedStateHandle[DRAFT_KEY] = longArrayOf(
            d.total.toLong(), d.best.toLong(), d.current.toLong(),
            if (d.lastCheckIn != null) 1L else 0L, d.lastCheckIn?.toEpochMilli() ?: 0L,
        )
    }

    private fun validate(d: Draft, c: ProgressionConfig): ValidationResult =
        SettingsValidator.currentState(d.total, d.best, d.current, d.lastCheckIn, clock.now(), c)

    private companion object {
        const val TAG = "CurrentState"
        const val DRAFT_KEY = "current_draft"

        fun CounterState.toDraft() = Draft(total, bestStreak, currentStreak, lastCheckIn)

        fun LongArray.toDraft() =
            Draft(this[0].toInt(), this[1].toInt(), this[2].toInt(), if (this[3] == 1L) Instant.ofEpochMilli(this[4]) else null)
    }
}

/** The Current page inside the pager (spec R3 §4). */
@Composable
fun CurrentStatePage(vm: CurrentStateViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    draft?.let {
        CurrentStatePageContent(
            it, validation, status, vm.zone, vm::now,
            onChange = vm::update, onChangeNow = vm::updateNow, onResetProgress = vm::resetProgress,
        )
    }
}

@Composable
fun CurrentStatePageContent(
    draft: CurrentStateViewModel.Draft,
    validation: ValidationResult,
    status: SaveStatus,
    zone: ZoneId,
    now: () -> Instant,
    onChange: ((CurrentStateViewModel.Draft) -> CurrentStateViewModel.Draft) -> Unit,
    onChangeNow: ((CurrentStateViewModel.Draft) -> CurrentStateViewModel.Draft) -> Unit,
    onResetProgress: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val lastLabel = stringResource(R.string.last_check_in_field)

    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        IntStepperField(
            stringResource(R.string.current_total), draft.total, FieldRanges.TOTAL, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(total = f(it.total)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(total = f(it.total)) } },
            error = validation.errors[Field.TOTAL], hint = validation.hints[Field.TOTAL],
            info = stringResource(R.string.info_total_reps),
        )
        IntStepperField(
            stringResource(R.string.best_streak_field), draft.best, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(best = f(it.best)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(best = f(it.best)) } },
            error = validation.errors[Field.BEST_STREAK], info = stringResource(R.string.info_best_streak),
        )
        IntStepperField(
            stringResource(R.string.current_streak_field), draft.current, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(current = f(it.current)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(current = f(it.current)) } },
            error = validation.errors[Field.CURRENT_STREAK], info = stringResource(R.string.info_current_streak),
        )
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lastLabel, style = MaterialTheme.typography.labelLarge)
            InfoTag(lastLabel, stringResource(R.string.info_last_check_in))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tapping the date text opens the date and time pickers (spec R2 §8.1).
            Text(
                draft.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: stringResource(R.string.none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.edit_value, lastLabel)) { picking = true }
                    .padding(vertical = 12.dp)
                    .testTag("last_check_in"),
            )
            TextButton(
                onClick = { onChangeNow { it.copy(lastCheckIn = null) } },
                enabled = draft.lastCheckIn != null,
                modifier = Modifier.testTag("clear_last_check_in"),
            ) {
                Text(stringResource(R.string.clear))
            }
        }
        validation.errors[Field.LAST_CHECK_IN]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 24.dp).testTag("reset_progress"),
        ) { Text(stringResource(R.string.reset_progress)) }
    }

    if (picking) {
        DateTimePickerDialog(
            initial = draft.lastCheckIn ?: now(),
            zone = zone,
            onPicked = { t ->
                picking = false
                onChangeNow { it.copy(lastCheckIn = t) }
            },
            onDismiss = { picking = false },
        )
    }
    // Spec R3 §6.4: Reset progress keeps its confirmation and applies at once; the page stays open.
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_progress_title)) },
            text = { Text(stringResource(R.string.reset_progress_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetProgress()
                    },
                    modifier = Modifier.testTag("confirm_reset_progress"),
                ) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(initial: Instant, zone: ZoneId, onPicked: (Instant) -> Unit, onDismiss: () -> Unit) {
    val start = initial.atZone(zone)
    var step by remember { mutableIntStateOf(0) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { step = 1 }) { Text(stringResource(R.string.next)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker reports UTC midnight of the chosen day.
                    val date = Instant.ofEpochMilli(dateState.selectedDateMillis ?: start.toInstant().toEpochMilli())
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    onPicked(date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant())
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = timeState) },
        )
    }
}
```

`ui/settings/LegacySettingsRoutes.kt` (complete; adds `CurrentStateRoute`):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.common.SettingsTopBar

/*
 * TEMPORARY: deleted in 8.2, when the settings pager replaces these routes. Until then each old
 * per-page route shows its new auto-saving page under a top bar, so the app works at every gate.
 * Leaving a page flushes its pending save.
 */

@Composable
fun TimingSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: TimingSettingsViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_timing, onBack = { vm.flush(); onBack() }) { TimingPage(vm) }
}

@Composable
fun ProgressionSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: ProgressionSettingsViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_progression, onBack = { vm.flush(); onBack() }) { ProgressionPage(vm) }
}

@Composable
fun CurrentStateRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: CurrentStateViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_current_state, onBack = { vm.flush(); onBack() }) { CurrentStatePage(vm) }
}

@Composable
private fun LegacyPage(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(title), onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T7-1-GREEN`, ~4 min): full suite + count. Expected: **315 tests**.

- [ ] **Step 5: Commit** — diff-review `CurrentStateSettings.kt`:
  - the pickers, Clear and the "outside floor–cap" hint are kept;
  - Reset progress is still confirmed;
  - `DateTimePickerDialog` is byte-identical;
  - there's no Save button.

  Then `git add -A && git commit -m "feat(settings): auto-saving Current page with hold-aware counter saves"`

### Subtask 7.2: Cues page

**Files:** Replace `ui/settings/CuesSettings.kt`, `ui/settings/LegacySettingsRoutes.kt`; create `test/ui/settings/CuesPageTest.kt`. `CuesSettingsViewModelTest` is unchanged (3 tests).

- [ ] **Step 1: Failing test** — `test/ui/settings/CuesPageTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CuesPageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the switches save this entry's cues at once and each has an info tag`() {
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2)))
        val vm = CuesSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo)
        compose.setContent { HiitTheme { CuesPage(vm) } }
        compose.onNodeWithTag("switch_Sound").performClick()
        compose.waitForIdle()
        assertEquals(CueConfig(sound = false), repo.find(1).cues)
        assertEquals(CueConfig(), repo.find(2).cues)
        compose.onNodeWithContentDescription("About Sound").assertExists()
        compose.onNodeWithContentDescription("About Vibration").assertExists()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T7-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.CuesPageTest"`. Expected: compile FAIL, "Unresolved reference 'CuesPage'".

- [ ] **Step 3: Implement**

`ui/settings/CuesSettings.kt` (complete; `CuesSettingsViewModel` is unchanged, `CuesSettingsRoute` moves to the legacy file, and the private `SwitchRow` is replaced by the shared one):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.SettingsPageLayout
import com.mitenko.hiitcounter.ui.common.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class CuesSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val cues: StateFlow<CueConfig> = repo.entry(entryId).filterNotNull().map { it.cues }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CueConfig())

    /** Serialises the read-modify-write below so two quick toggles can't overwrite each other. */
    private val mutex = Mutex()

    fun setSound(on: Boolean) = edit { it.copy(sound = on) }

    fun setVibration(on: Boolean) = edit { it.copy(vibration = on) }

    /** Cues save immediately (as in v1). A toggle racing a delete pops to the list (spec §7.5). */
    private fun edit(transform: (CueConfig) -> CueConfig) {
        viewModelScope.launch {
            try {
                mutex.withLock {
                    val current = repo.entry(entryId).first()?.cues ?: return@withLock markMissing()
                    repo.setCues(entryId, transform(current))
                }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }
}

/** The Cues page inside the pager (spec R3 §4). Each switch saves at once, as before, so there's no status line. */
@Composable
fun CuesPage(vm: CuesSettingsViewModel) {
    val cues by vm.cues.collectAsStateWithLifecycle()
    SettingsPageLayout {
        SwitchRow(stringResource(R.string.sound), cues.sound, vm::setSound, info = stringResource(R.string.info_sound))
        SwitchRow(stringResource(R.string.vibration), cues.vibration, vm::setVibration, info = stringResource(R.string.info_vibration))
    }
}
```

`ui/settings/LegacySettingsRoutes.kt` (complete; adds `CuesSettingsRoute`):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.common.SettingsTopBar

/*
 * TEMPORARY: deleted in 8.2, when the settings pager replaces these routes. Until then each old
 * per-page route shows its new auto-saving page under a top bar, so the app works at every gate.
 * Leaving a page flushes its pending save.
 */

@Composable
fun TimingSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: TimingSettingsViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_timing, onBack = { vm.flush(); onBack() }) { TimingPage(vm) }
}

@Composable
fun ProgressionSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: ProgressionSettingsViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_progression, onBack = { vm.flush(); onBack() }) { ProgressionPage(vm) }
}

@Composable
fun CurrentStateRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: CurrentStateViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_current_state, onBack = { vm.flush(); onBack() }) { CurrentStatePage(vm) }
}

@Composable
fun CuesSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: CuesSettingsViewModel = hiltViewModel()) {
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LegacyPage(R.string.settings_cues, onBack = onBack) { CuesPage(vm) }
}

@Composable
private fun LegacyPage(@StringRes title: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(title), onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T7-2-GREEN`, ~4 min): full suite + count. Expected: **316 tests**.

- [ ] **Step 5: Commit** — diff-review `CuesSettings.kt`: the ViewModel, including its `Mutex`, must be byte-identical. Then `git add -A && git commit -m "feat(settings): Cues page with info tags"`

**Task 7 gate:** Run (label `T7-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **316 tests**, no lint errors.

---

## Task 8: The pager and the navigation swap (§4, §6.2)

**Interfaces produced:**
- `SettingsPage.tab: Int` (a string resource).
- `SettingsPagerViewModel(savedStateHandle, repo)` with `name: StateFlow<String?>`, which is null until loaded. Until then the title shows the existing `R.string.settings` ("Settings").
- `SettingsPagerRoute(initialPage, onBack, onEntryGone, pagerVm, timingVm, progressionVm, currentVm, cuesVm)`. The ViewModels default to `hiltViewModel(key = …)`.
- `Routes.PAGE_ARG = "page"`, `Routes.SETTINGS_PAGES = "entry/{id}/settings/pages?page={page}"`, and `Routes.settingsPage(id, page)`, whose signature is unchanged and which now builds `…/pages?page=<ordinal>`.

### Subtask 8.1: `SettingsPagerViewModel` and `SettingsPagerRoute`

The route is built and tested here, but it isn't wired into navigation until 8.2.

**Files:** Edit `ui/settings/EntrySettings.kt`; create `ui/settings/SettingsPager.kt`, `test/ui/settings/SettingsPagerViewModelTest.kt`, `test/ui/settings/SettingsPagerTest.kt`.

- [ ] **Step 1: Failing tests**

`test/ui/settings/SettingsPagerViewModelTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPagerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `exposes the entry name and follows a rename`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, name = "Burpees")))
        val vm = SettingsPagerViewModel(handle, repo)
        assertEquals("Burpees", vm.name.value)
        repo.rename(1, "Lunges")
        assertEquals("Lunges", vm.name.value)
    }

    @Test
    fun `a missing entry reports missing after loading`() = runTest {
        val vm = SettingsPagerViewModel(handle, FakeEntryRepository())
        runCurrent()
        assertTrue(vm.missing.value)
        assertNull(vm.name.value)
    }
}
```

`test/ui/settings/SettingsPagerTest.kt` (complete; phone-size viewport):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsPagerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val repo = FakeEntryRepository(listOf(testEntry(1, name = "Burpees")))
    private var backs = 0
    private var gone = 0

    private fun handle() = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    private fun show(initial: SettingsPage = SettingsPage.TIMING, repo: FakeEntryRepository = this.repo) {
        val appScope = MainScope()
        val pagerVm = SettingsPagerViewModel(handle(), repo)
        val timingVm = TimingSettingsViewModel(handle(), repo, appScope)
        val progressionVm = ProgressionSettingsViewModel(handle(), repo, appScope)
        val currentVm = CurrentStateViewModel(handle(), repo, FakeClock(), appScope)
        val cuesVm = CuesSettingsViewModel(handle(), repo)
        compose.setContent {
            HiitTheme {
                SettingsPagerRoute(
                    initialPage = initial, onBack = { backs++ }, onEntryGone = { gone++ },
                    pagerVm = pagerVm, timingVm = timingVm, progressionVm = progressionVm,
                    currentVm = currentVm, cuesVm = cuesVm,
                )
            }
        }
    }

    private fun tab(page: SettingsPage) = compose.onNodeWithTag("tab_${page.name}")

    private fun text(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    /** A labelled row and its info text (spec R3 §7.2). [scrolls] is false for the TOTAL footer. */
    private class InfoRow(@StringRes val label: Int, @StringRes val info: Int, val scrolls: Boolean = true)

    private val rows = mapOf(
        SettingsPage.TIMING to listOf(
            InfoRow(R.string.prepare, R.string.info_prepare),
            InfoRow(R.string.sets, R.string.info_sets),
            InfoRow(R.string.work, R.string.info_work),
            InfoRow(R.string.rest, R.string.info_rest),
            InfoRow(R.string.cooldown, R.string.info_cooldown),
            InfoRow(R.string.total_label, R.string.info_total, scrolls = false),
        ),
        SettingsPage.PROGRESSION to listOf(
            InfoRow(R.string.starting_total, R.string.info_starting_total),
            InfoRow(R.string.floor, R.string.info_floor),
            InfoRow(R.string.cap, R.string.info_cap),
            InfoRow(R.string.hold, R.string.info_hold),
            InfoRow(R.string.hold_at, R.string.info_hold_at),
            InfoRow(R.string.hold_for, R.string.info_hold_for),
            InfoRow(R.string.window_hours, R.string.info_window),
            InfoRow(R.string.penalty_rate, R.string.info_penalty_rate),
        ),
        SettingsPage.CURRENT to listOf(
            InfoRow(R.string.current_total, R.string.info_total_reps),
            InfoRow(R.string.best_streak_field, R.string.info_best_streak),
            InfoRow(R.string.current_streak_field, R.string.info_current_streak),
            InfoRow(R.string.last_check_in_field, R.string.info_last_check_in),
        ),
        SettingsPage.CUES to listOf(
            InfoRow(R.string.sound, R.string.info_sound),
            InfoRow(R.string.vibration, R.string.info_vibration),
        ),
    )

    @Test
    fun `the title shows the entry name`() {
        show()
        compose.onNodeWithText("Burpees settings").assertIsDisplayed()
    }

    @Test
    fun `tapping a tab changes the page`() {
        show()
        tab(SettingsPage.TIMING).assertIsSelected()
        tab(SettingsPage.PROGRESSION).performClick()
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_Starting total").assertIsDisplayed()
    }

    @Test
    fun `swiping changes the page and the selected tab`() {
        show()
        // Fallback if Robolectric's default swipe is too short or too fast to settle on the next page:
        // performTouchInput { swipeLeft(startX = right * 0.9f, endX = left, durationMillis = 400) }
        compose.onNodeWithTag("settings_pager").performTouchInput { swipeLeft() }
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_Starting total").assertIsDisplayed()
    }

    @Test
    fun `page 2 opens on Current`() {
        show(initial = SettingsPage.CURRENT)
        tab(SettingsPage.CURRENT).assertIsSelected()
        compose.onNodeWithTag("value_Current total").assertIsDisplayed()
    }

    @Test
    fun `changing page flushes a pending save`() {
        show()
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        tab(SettingsPage.CUES).performClick()
        compose.runOnIdle {
            assertEquals(1, repo.timingWrites)
            assertEquals(9, repo.find(1).timing.sets)
        }
    }

    @Test
    fun `the app going to the background flushes a pending save`() {
        show()
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // ON_STOP
        assertEquals(9, repo.find(1).timing.sets)
        assertEquals(0, backs)
    }

    @Test
    fun `the back arrow and system back both flush a pending save before leaving`() {
        show()
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals(9, repo.find(1).timing.sets)
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(2, backs)
        assertEquals(10, repo.find(1).timing.sets)
    }

    @Test
    fun `there is no Save button on any page`() {
        show()
        SettingsPage.entries.forEach { page ->
            tab(page).performClick()
            compose.onNodeWithText("Save").assertDoesNotExist()
        }
    }

    @Test
    fun `each info tag opens its own title and text`() {
        show()
        rows.forEach { (page, list) ->
            tab(page).performClick()
            list.forEach { row ->
                val label = text(row.label)
                val tag = compose.onNodeWithContentDescription("About $label")
                (if (row.scrolls) tag.performScrollTo() else tag).performClick()
                compose.onNodeWithTag("info_title").assertTextEquals(label)
                compose.onNodeWithTag("info_text").assertTextEquals(text(row.info))
                compose.onNodeWithTag("info_ok").performClick()
            }
        }
    }

    @Test
    fun `every row has an info tag of at least 48 dp`() {
        show()
        rows.forEach { (page, list) ->
            tab(page).performClick()
            list.forEach { row ->
                compose.onNodeWithContentDescription("About ${text(row.label)}")
                    .assertWidthIsAtLeast(48.dp)
                    .assertHeightIsAtLeast(48.dp)
            }
        }
    }

    @Test
    fun `the pager pops to the list when the entry loads as missing`() {
        show(repo = FakeEntryRepository())
        compose.waitForIdle()
        assertEquals(1, gone)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T8-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.SettingsPager*"`. Expected: compile FAIL, "Unresolved reference 'SettingsPagerViewModel'" and "'SettingsPagerRoute'".

- [ ] **Step 3: Implement**

`ui/settings/EntrySettings.kt`. Replace:
```kotlin
/** The four per-entry settings pages (spec §7.5). */
enum class SettingsPage(@StringRes val label: Int) {
    TIMING(R.string.settings_timing),
    PROGRESSION(R.string.settings_progression),
    CURRENT(R.string.settings_current_state),
    CUES(R.string.settings_cues),
}
```
with:
```kotlin
/**
 * The four per-entry settings pages (spec R2 §7.5). [label] is the Entry Settings row, and [tab]
 * is the pager tab (R3 §4: "Timing · Progression · Current · Cues"). The ordinal is the route's
 * `page` argument.
 */
enum class SettingsPage(@StringRes val label: Int, @StringRes val tab: Int) {
    TIMING(R.string.settings_timing, R.string.settings_timing),
    PROGRESSION(R.string.settings_progression, R.string.settings_progression),
    CURRENT(R.string.settings_current_state, R.string.tab_current),
    CUES(R.string.settings_cues, R.string.settings_cues),
}
```

`ui/settings/SettingsPager.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.SettingsTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The pager's own state: the entry name for the "<name> settings" title (spec R3 §4), null until loaded. */
@HiltViewModel
class SettingsPagerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val name: StateFlow<String?> = repo.entry(entryId).filterNotNull().map { it.name }
        .stateIn<String?>(viewModelScope, SharingStarted.Eagerly, null)
}

/**
 * An entry's settings on one screen (spec R3 §4): ← and "<name> settings", tabs over a
 * HorizontalPager. Each page keeps its own ViewModel, keyed on this back-stack entry, so each has
 * its own saved-state draft and AutoSaver (see the plan's layout decision). This route only hosts
 * them. It flushes the three auto-saving pages on every page change and on every exit: back, ←,
 * ON_STOP and an onEntryGone pop (§6.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPagerRoute(
    initialPage: SettingsPage,
    onBack: () -> Unit,
    onEntryGone: () -> Unit,
    pagerVm: SettingsPagerViewModel = hiltViewModel(),
    timingVm: TimingSettingsViewModel = hiltViewModel(key = "timing"),
    progressionVm: ProgressionSettingsViewModel = hiltViewModel(key = "progression"),
    currentVm: CurrentStateViewModel = hiltViewModel(key = "current"),
    cuesVm: CuesSettingsViewModel = hiltViewModel(key = "cues"),
) {
    val name by pagerVm.name.collectAsStateWithLifecycle()
    // Collected unconditionally (no short-circuit), so the composition shape never changes.
    val pagerGone by pagerVm.missing.collectAsStateWithLifecycle()
    val timingGone by timingVm.missing.collectAsStateWithLifecycle()
    val progressionGone by progressionVm.missing.collectAsStateWithLifecycle()
    val currentGone by currentVm.missing.collectAsStateWithLifecycle()
    val cuesGone by cuesVm.missing.collectAsStateWithLifecycle()
    val missing = pagerGone || timingGone || progressionGone || currentGone || cuesGone

    val flushAll = {
        timingVm.flush()
        progressionVm.flush()
        currentVm.flush()
    }
    val leave = {
        flushAll()
        onBack()
    }
    LaunchedEffect(missing) {
        if (missing) {
            flushAll()
            onEntryGone()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { flushAll() }
    BackHandler(onBack = leave)

    // rememberPagerState is saveable, so the page survives rotation and process recreation (§4).
    val pagerState = rememberPagerState(initialPage = initialPage.ordinal) { SettingsPage.entries.size }
    LaunchedEffect(pagerState) {
        // Tab taps and swipes alike: leaving a page writes what it had pending.
        snapshotFlow { pagerState.currentPage }.drop(1).collect { flushAll() }
    }
    val scope = rememberCoroutineScope()

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Until the name has loaded, the existing "Settings" string stands in, so there's never a bare " settings".
            val title = name?.let { stringResource(R.string.entry_settings_title, it) } ?: stringResource(R.string.settings)
            SettingsTopBar(title, onBack = leave)
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                SettingsPage.entries.forEach { page ->
                    Tab(
                        selected = pagerState.currentPage == page.ordinal,
                        onClick = { scope.launch { pagerState.animateScrollToPage(page.ordinal) } },
                        text = { Text(stringResource(page.tab)) },
                        modifier = Modifier.testTag("tab_${page.name}"),
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).testTag("settings_pager")) { index ->
                when (SettingsPage.entries[index]) {
                    SettingsPage.TIMING -> TimingPage(timingVm)
                    SettingsPage.PROGRESSION -> ProgressionPage(progressionVm)
                    SettingsPage.CURRENT -> CurrentStatePage(currentVm)
                    SettingsPage.CUES -> CuesPage(cuesVm)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T8-1-GREEN`, ~5 min): full suite + count. Expected: **329 tests**. `EntrySettingsScreenTest` and `EntrySettingsViewModelTest` pass unchanged, because `SettingsPage.label` keeps its values.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(settings): settings pager with tabs, swipe and flush on every exit"`

### Subtask 8.2: The pager route, the navigation swap and removal of the legacy routes

**Files:** Replace `ui/navigation/Routes.kt`, `ui/navigation/HiitNavHost.kt`, `test/ui/navigation/RoutesTest.kt`, `test/ui/settings/TimingSettingsScreenTest.kt`; edit `test/ui/navigation/NavActionsTest.kt`, `app/src/main/res/values/strings.xml`; delete `ui/settings/LegacySettingsRoutes.kt`.

**Test ledger (net 0):**
- `TimingSettingsScreenTest.the route pops to the list when its entry loads as missing` is **deleted**, because its route is gone. `SettingsPagerTest.the pager pops to the list when the entry loads as missing` covers it (8.1).
- `RoutesTest.routes follow the spec` is **rewritten**.
- `NavActionsTest` gains **1**: `a settings page route carries its page and defaults to the first`.

- [ ] **Step 1: Tests**

`test/ui/navigation/RoutesTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import com.mitenko.hiitcounter.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun `routes follow the spec`() {
        assertEquals("entry/{id}", Routes.ENTRY)
        assertEquals("entry/{id}/settings/pages?page={page}", Routes.SETTINGS_PAGES)
        assertEquals("entry/7", Routes.entry(7))
        assertEquals("entry/7/settings", Routes.entrySettings(7))
        assertEquals(
            listOf(
                "entry/7/settings/pages?page=0",
                "entry/7/settings/pages?page=1",
                "entry/7/settings/pages?page=2",
                "entry/7/settings/pages?page=3",
            ),
            SettingsPage.entries.map { Routes.settingsPage(7, it) },
        )
    }
}
```

`test/ui/navigation/NavActionsTest.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
```
with:
```kotlin
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.settings.SettingsPage
```
Replace:
```kotlin
                composable(Routes.ENTRY_SETTINGS, arguments = idArg) { }
```
with:
```kotlin
                composable(Routes.ENTRY_SETTINGS, arguments = idArg) { }
                composable(
                    Routes.SETTINGS_PAGES,
                    arguments = idArg + navArgument(Routes.PAGE_ARG) {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ) { }
```
Append to the class:
```kotlin
    @Test
    fun `a settings page route carries its page and defaults to the first`() {
        graph()
        compose.runOnIdle { nav.navigate(Routes.settingsPage(1, SettingsPage.CURRENT)) }
        compose.runOnIdle {
            assertEquals(Routes.SETTINGS_PAGES, nav.currentDestination?.route)
            assertEquals(2, nav.currentBackStackEntry?.arguments?.getInt(Routes.PAGE_ARG))
            nav.navigate("entry/1/settings/pages")
        }
        compose.runOnIdle { assertEquals(0, nav.currentBackStackEntry?.arguments?.getInt(Routes.PAGE_ARG)) }
    }
```

`test/ui/settings/TimingSettingsScreenTest.kt` (complete; the legacy-route test and its imports are removed):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.ui.common.SaveStatus
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimingSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(initial: TimingConfig) {
        compose.setContent {
            HiitTheme {
                var draft by remember { mutableStateOf(initial) }
                val validation = SettingsValidator.timing(draft)
                TimingPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { draft = it(draft) }, onChangeNow = { draft = it(draft) },
                )
            }
        }
    }

    @Test
    fun `steppers update values and total`() {
        show(TimingConfig())
        compose.onNodeWithTag("total").assertTextEquals("TOTAL 04:00")
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.onNodeWithTag("value_SETS").assertTextEquals("9")
        compose.onNodeWithTag("total").assertTextEquals("TOTAL 04:30")
    }

    @Test
    fun `the status line reads Not saved while invalid and Saved once valid`() {
        // 0 + 3 × 59:59 = 2:59:57 is too long; one set fewer, 1:59:58, is fine.
        show(TimingConfig(prepareSec = 0, sets = 3, workSec = 3599, restSec = 0, cooldownSec = 0))
        compose.onNodeWithTag("save_status").assertTextEquals("Not saved: fix the highlighted field")
        compose.onNodeWithContentDescription("Decrease SETS").performScrollTo().performClick()
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }

    @Test
    fun `work cannot step below one second`() {
        show(TimingConfig(workSec = 5))
        repeat(2) { compose.onNodeWithContentDescription("Decrease WORK").performScrollTo().performClick() }
        compose.onNodeWithTag("value_WORK").assertTextEquals("00:01")
    }
}
```

- [ ] **Step 2: Implement**

`ui/navigation/Routes.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.settings.SettingsPage

/** Spec R2 §7.2, with R3 §4's pager route in place of the four settings routes. */
object Routes {
    const val ENTRIES = "entries"
    const val ENTRY = "entry/{$ENTRY_ID_ARG}"
    const val ENTRY_SETTINGS = "entry/{$ENTRY_ID_ARG}/settings"

    /** The pager's optional page, 0–3 (Timing, Progression, Current, Cues), default 0. */
    const val PAGE_ARG = "page"
    const val SETTINGS_PAGES = "entry/{$ENTRY_ID_ARG}/settings/pages?$PAGE_ARG={$PAGE_ARG}"
    const val TIMER = "timer"

    fun entry(id: Long): String = "entry/$id"

    fun entrySettings(id: Long): String = "entry/$id/settings"

    /** Opens the pager at [page]. The signature is unchanged from R2. */
    fun settingsPage(id: Long, page: SettingsPage): String = "${entrySettings(id)}/pages?$PAGE_ARG=${page.ordinal}"
}
```

`ui/navigation/HiitNavHost.kt` (complete; only the settings routes change):
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.entries.EntryListRoute
import com.mitenko.hiitcounter.ui.entry.EntryRoute
import com.mitenko.hiitcounter.ui.settings.EntrySettingsRoute
import com.mitenko.hiitcounter.ui.settings.SettingsPage
import com.mitenko.hiitcounter.ui.settings.SettingsPagerRoute
import com.mitenko.hiitcounter.ui.timer.TimerRoute

@Composable
fun HiitNavHost(controller: TimerController) {
    val nav = rememberNavController()
    val status by controller.status.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = Routes.ENTRIES) {
        val idArg = listOf(navArgument(ENTRY_ID_ARG) { type = NavType.LongType })

        composable(Routes.ENTRIES) {
            val openEntry = dropUnlessResumedWith<Long> { id -> nav.navigate(Routes.entry(id)) { launchSingleTop = true } }
            EntryListRoute(
                onOpenEntry = openEntry,
                // Spec §7.3: the new entry opens with the list beneath it.
                onCreated = { id -> nav.openEntryOverList(id) },
            )
        }
        composable(Routes.ENTRY, arguments = idArg) { entry ->
            val id = entry.entryId()
            EntryRoute(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenSettings = dropUnlessResumed { nav.navigate(Routes.entrySettings(id)) { launchSingleTop = true } },
                onEntryGone = { nav.popToEntries() },
            )
        }
        composable(Routes.ENTRY_SETTINGS, arguments = idArg) { entry ->
            val id = entry.entryId()
            // Spec R3 §4: each row opens the pager at its page.
            val openPage = dropUnlessResumedWith<SettingsPage> { page ->
                nav.navigate(Routes.settingsPage(id, page)) { launchSingleTop = true }
            }
            EntrySettingsRoute(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpen = openPage,
                // Spec §7.5: the copy opens with the list beneath it.
                onDuplicated = { copy -> nav.openEntryOverList(copy) },
                onDeleted = { nav.popToEntries() },
                onEntryGone = { nav.popToEntries() },
            )
        }
        // Spec R3 §4: one pager route replaces the four settings routes. The pager flushes before every exit.
        composable(
            Routes.SETTINGS_PAGES,
            arguments = idArg + navArgument(Routes.PAGE_ARG) {
                type = NavType.IntType
                defaultValue = 0
            },
        ) { entry ->
            SettingsPagerRoute(
                initialPage = entry.settingsPage(),
                onBack = dropUnlessResumed { nav.popBackStack() },
                onEntryGone = { nav.popToEntries() },
            )
        }
        // Active-run routing relies on TimerRoute's BackHandler blocking back navigation while RUNNING,
        // so onExit only fires once the workout has stopped. lastEntryId outlives clearRun() (spec §7.2).
        composable(Routes.TIMER) { TimerRoute(onExit = { nav.exitTimer(controller.lastEntryId) }) }
    }

    // v1 §4 active-run routing, unchanged.
    LaunchedEffect(status) {
        if (status == RunStatus.RUNNING && nav.currentDestination?.route != Routes.TIMER) {
            nav.navigate(Routes.TIMER) { launchSingleTop = true }
        }
    }
}

private fun NavBackStackEntry.entryId(): Long = requireNotNull(arguments) { "Missing route arguments" }.getLong(ENTRY_ID_ARG)

/** The optional `page` argument (default 0); an out-of-range value opens the nearest page. */
private fun NavBackStackEntry.settingsPage(): SettingsPage {
    val index = requireNotNull(arguments) { "Missing route arguments" }.getInt(Routes.PAGE_ARG)
    return SettingsPage.entries[index.coerceIn(0, SettingsPage.entries.lastIndex)]
}
```

Delete the temporary wrappers:
```bash
git rm app/src/main/kotlin/com/mitenko/hiitcounter/ui/settings/LegacySettingsRoutes.kt
```

`app/src/main/res/values/strings.xml`: nothing uses `save` any more. Check with `grep -rn "R.string.save\b" app/src` (it must print nothing), then delete this line:
```xml
    <string name="save">Save</string>
```

- [ ] **Step 3: Verify** — Run (label `T8-2-FULL`, ~5 min): `./gradlew clean assembleDebug testDebugUnitTest lintDebug`, then the test-count command. Use `clean` so no stale XML from the deleted test survives. Expected: `BUILD SUCCESSFUL`, **329 tests** (329 − 1 + 1), and no lint errors. Then check that nothing references the old routes:
```bash
grep -rnE "ENTRY_(TIMING|PROGRESSION|CURRENT|CUES)|(Timing|Progression|Cues)SettingsRoute|CurrentStateRoute|LegacyPage" app/src && echo "CLAUDE-STALE-ROUTES found" || echo "CLAUDE-STALE-ROUTES none"
```
Expected: `CLAUDE-STALE-ROUTES none`. `EntrySettingsRoute` doesn't match the pattern, so it's fine that it stays.

- [ ] **Step 4: Commit** — diff-review `HiitNavHost.kt` against `main`:
  - the active-run routing `LaunchedEffect`, `dropUnlessResumed` / `dropUnlessResumedWith`, `launchSingleTop`, `openEntryOverList`, `exitTimer` and every `popToEntries` must still be there;
  - only the four settings `composable`s are replaced by the pager's.

  Then:
```bash
git add -A && git commit -m "feat(nav): settings pager route replaces the four settings routes"
```

**Task 8 gate:** Step 3 is the gate: **329 tests**, no lint errors.

---

## Task 9: Docs, full verification, device check, squash and PR (§8, §9)

### Subtask 9.1: Docs and full verification

**Files:** Edit `claude.md` and `docs/superpowers/specs/2026-09-28-settings-pager-design.md`.

- [ ] **Step 1: Update `claude.md`** (read it first) with these exact edits:
  1. Replace:
     ```markdown
     - **Multi-entry revision (approved, amends v1):** `docs/superpowers/specs/2026-09-25-multi-entry-design.md` — read both.
     ```
     with:
     ```markdown
     - **Multi-entry revision (approved, amends v1):** `docs/superpowers/specs/2026-09-25-multi-entry-design.md` — read both.
     - **Settings pager revision (approved, amends both):** `docs/superpowers/specs/2026-09-28-settings-pager-design.md` — read all three.
     ```
  2. Replace:
     ```markdown
     - Starting total (48) is separate from the floor. No first-run prompt; all values editable in Settings sub-screens (Timing, Progression, Current State, Cues).
     ```
     with:
     ```markdown
     - Starting total (48) is separate from the floor. No first-run prompt; all values are edited in one settings pager per entry (tabs Timing · Progression · Current · Cues, swipe). Valid drafts auto-save (steppers 400 ms after the last change, everything else at once; invalid drafts never save), and every row has an ⓘ info tag. The Hold switch (`hold_enabled`) keeps its values when off; the hold count resets only when holdAt, holdFor, the effective hold or the total changes.
     ```
  3. Replace:
     ```markdown
     - Plans: `docs/superpowers/plans/2026-09-24-hiit-counter.md` (v1), `docs/superpowers/plans/2026-09-25-multi-entry.md` (multi-entry).
     ```
     with:
     ```markdown
     - Plans: `docs/superpowers/plans/2026-09-24-hiit-counter.md` (v1), `docs/superpowers/plans/2026-09-25-multi-entry.md` (multi-entry), `docs/superpowers/plans/2026-09-28-settings-pager.md` (settings pager).
     ```
  4. Replace:
     ```markdown
     - Room schema changes: bump the `@Database` version, add a Migration and a `MigrationTestHelper` test (the schemas are debug assets, which Robolectric reads; the file-based test is skipped on Windows).
     ```
     with:
     ```markdown
     - Room schema changes (current version 2): bump the `@Database` version, add a Migration to `HiitDatabase.MIGRATIONS`, commit the exported `N.json`, and add a `MigrationTestHelper` test (the schemas are debug assets, which Robolectric reads; the file-based test is skipped on Windows, so also run the migration SQL on an in-memory framework database).
     ```

- [ ] **Step 2: Record the spec notes in the spec** — `docs/superpowers/specs/2026-09-28-settings-pager-design.md`. Replace:
```markdown
These are unchanged: the mitenko identity, no AI attribution, squash to one commit and open a PR, ask before pushing, TDD subtasks, and a phone backup before every install.
```
with:
```markdown
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
```

- [ ] **Step 3: Full verification** — Run (label `T9-1-FULL`, ~5 min): `./gradlew clean assembleDebug testDebugUnitTest lintDebug`, then the test-count command. Expected: `BUILD SUCCESSFUL`, **329 tests** (2 skipped on Windows), no lint errors. Record the count for the PR.

- [ ] **Step 4: Commit** — `git add -A && git commit -m "docs: settings pager notes in claude.md and the spec"`

### Subtask 9.2: Device verification on the Pixel 9a (over the R2 install)

The device is the user's Pixel 9a, serial **59251JEBF12416**. It has the R2 build installed, with real entries.
- **Never uninstall the app and never clear its data.** That would destroy the data this step exists to migrate.
- Install only **over** the existing build.
- Don't start or kill emulators or adb servers you didn't start.

- [ ] **Step 1: Device and backup**
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
adb devices                                   # 59251JEBF12416 must be listed as "device"
adb -s 59251JEBF12416 shell am force-stop com.mitenko.hiitcounter   # a stopped app has no open DB and no in-flight writes
adb -s 59251JEBF12416 exec-out run-as com.mitenko.hiitcounter tar -cf - files databases > "$SCRATCH/claude-r2-backup.tar"
tar -tf "$SCRATCH/claude-r2-backup.tar"       # must list databases/hiit.db (plus -wal/-shm if present)
adb -s 59251JEBF12416 shell am start -W -n com.mitenko.hiitcounter/.MainActivity
adb -s 59251JEBF12416 exec-out screencap -p > "$SCRATCH/claude-r2-list.png"
echo "CLAUDE-DEVICE-BACKUP DONE"
```
**Restore, only when the user asks.** It puts the backup back over the app's `files` and `databases`. Force-stop the app first:
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
adb -s 59251JEBF12416 shell am force-stop com.mitenko.hiitcounter
adb -s 59251JEBF12416 exec-in run-as com.mitenko.hiitcounter tar -xf - < "$SCRATCH/claude-r2-backup.tar"
```
- If the device isn't listed, ask the user to connect it.
- If `run-as` reports that the package is not debuggable, the installed build isn't this machine's debug build. `installDebug` would then fail on the signature, so **stop and ask**.
- If the tar is empty or lacks `databases/hiit.db`, stop and ask. Never install without a good backup.
- Open `claude-r2-list.png` and record every entry's name, its order and "Reps N".
- If `sqlite3` is on the PATH, extract a copy and record the progression and hold values:
  ```bash
  SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
  mkdir -p "$SCRATCH/claude-r2-backup" && tar -xf "$SCRATCH/claude-r2-backup.tar" -C "$SCRATCH/claude-r2-backup"
  sqlite3 "$SCRATCH/claude-r2-backup/databases/hiit.db" "SELECT id,name,position,total,hold_at,hold_for,hold_count FROM entry ORDER BY position"
  ```
  Otherwise, ask the user to note each entry's Progression values (above all, any entry with *Hold for* 0).

- [ ] **Step 2: Install over R2** — Run (label `T9-2-INSTALL`, ~3 min): `ANDROID_SERIAL=59251JEBF12416 ./gradlew installDebug`, using the logging convention with the inline `SCRATCH=` assignment. Expected: `Installed on 1 device.`
  - If it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signature mismatch) or a version-downgrade error, **stop and ask the user**. Never uninstall.

- [ ] **Step 3: Migration check**
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
adb -s 59251JEBF12416 shell am start -W -n com.mitenko.hiitcounter/.MainActivity
adb -s 59251JEBF12416 exec-out screencap -p > "$SCRATCH/claude-r3-list.png"
adb -s 59251JEBF12416 exec-out run-as com.mitenko.hiitcounter tar -cf - databases > "$SCRATCH/claude-r3-after.tar"
echo "CLAUDE-DEVICE-MIGRATION DONE"
```
Expected:
- `claude-r3-list.png` shows the same entries, in the same order, with the same "Reps N" as `claude-r2-list.png`.
- If `sqlite3` is available, extract `claude-r3-after.tar` and run `SELECT id,name,position,total,hold_enabled,hold_at,hold_for,hold_count FROM entry ORDER BY position`:
  - every row is unchanged except for the new `hold_enabled`, which is 1;
  - a row that had `hold_for` 0 now has `hold_enabled` 0 and `hold_for` 4;
  - `hold_count` is unchanged.
- The app opens without a crash. A failed migration throws at the first database access, so if it crashes, **stop**, keep the backup, and report `adb logcat -d | grep -i room`.

- [ ] **Step 4: Manual checklist** (spec §8 device checks, plus the pager's behaviour). Report each item to the user as pass or fail. Fix each failure TDD-style in a new commit on the branch, then re-run 9.1 Step 3 before continuing. Do the editing checks on a scratch entry, **"R3 check"**, created with + on the list, so the user's real entries aren't changed. Delete it at the end.
  1. **Entries survive:** every real entry opens with the same table (total, streaks, last check-in). On Settings → Progression, the hold values match the recorded ones. An entry that had *Hold for* 0 shows **Hold** off, with no *Hold at* / *Hold for* rows.
  2. **Pager:** on R3 check, ⚙ → Progression opens the pager on the Progression tab, titled "R3 check settings". Tapping a tab and swiping both change the page. ← and system back return to Entry Settings. Rotation isn't possible (portrait only), so skip it.
  3. **ⓘ:** every row on all four pages opens its text. The value tap still opens the edit dialog, separately.
  4. **Auto-save after a force-stop:**
     - Timing: tap + on SETS, wait about 1 s, then run `adb -s 59251JEBF12416 shell am force-stop com.mitenko.hiitcounter`. Relaunch: the new SETS is kept.
     - Tap + again and immediately press Home (`adb -s 59251JEBF12416 shell input keyevent KEYCODE_HOME`, which is the ON_STOP flush), then force-stop. Relaunch: that change is kept too.
  5. **Invalid drafts:** raise WORK until TOTAL turns red and the status reads "Not saved: fix the highlighted field". Force-stop and relaunch: the last valid value is shown.
  6. **Hold switch:** turn Hold off. The rows hide, and the status reads "Saved". Force-stop and relaunch: it's still off. Turn it on: *Hold at* 64 and *Hold for* 4 come back.
  7. **Resets:** Reset to defaults asks "Reset progression to defaults?" first, then applies. Reset progress asks first, applies, and the page stays open showing the starting total and zero streaks.
  8. **Clean-up:** delete "R3 check" (Entry Settings → Delete → confirm). The real entries are untouched.

### Subtask 9.3: Squash and PR

Follow the project PR protocol (`claude.md`, spec §9). No AI attribution anywhere.

- [ ] **Step 1: Verify the build** — Run (label `T9-3-VERIFY`, ~4 min): `./gradlew testDebugUnitTest lintDebug`. Expected: **329 tests**, no lint errors.

- [ ] **Step 2: Sync main and rebase**
```bash
git fetch origin
git checkout main && git pull --ff-only && git checkout feat/settings-pager
git rebase main
```
If the rebase conflicts, stop and report. If `git pull` brought new commits into `main`, re-run Step 1 after the rebase.

- [ ] **Step 3: Squash to one commit.** `main` already contains the spec and this plan (merged before 1.1), so `git reset --soft $(git merge-base main HEAD)` squashes **only this branch's commits**. Write `$SCRATCH/claude-commit-msg.txt` (the scratchpad path above):
```text
Settings pager with auto-save, info tags and a hold switch

- One settings pager route (entry/{id}/settings/pages?page=) with Timing, Progression, Current and Cues tabs and swipe; Entry Settings rows open it at their page
- Auto-save: a per-page AutoSaver (400 ms stepper debounce, immediate dialog/switch/picker/reset saves, Mutex-ordered writes, flush on page change, back, ON_STOP and entry-gone, application-scope flush on clear); typed drafts in SavedStateHandle; Saved / Not saved status line; no Save buttons
- Hold switch: ProgressionConfig.hold, validator skips the hidden hold values; Room v2 with entry.hold_enabled (MIGRATION_1_2, exported 2.json); v1 import and the migration map hold_for 0 to the switch off with hold_for 4
- Hold count kept unless holdAt, holdFor or the effective hold changes (setProgression) or the total changes (overwriteCounter), each in one transaction
- Info tag and dialog on every settings row, with the spec texts as string resources
- Reset to defaults confirmed and applied at once; Reset progress applies without leaving the page; the Current draft follows the stored counter while unedited
```
Then:
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
git reset --soft $(git merge-base main HEAD)   # this branch's commits only; the docs are already on main
git commit -F "$SCRATCH/claude-commit-msg.txt"
git log --format='%an <%ae>%n%B' -1        # author must be mitenko <mitenko@gmail.com>; no trailers
git show --stat HEAD | grep "2.json"       # the schema must be in the commit
```

- [ ] **Step 4: Ask the user before pushing.** Show them the commit message and the 9.2 checklist results. If they agree, write `$SCRATCH/claude-pr-body.md` first. It includes:
  - the commit bullets;
  - the test count from 9.1, noting the 2 Windows-skipped MigrationTestHelper tests that CI runs;
  - the migration check from 9.2 Step 3;
  - the checklist results from 9.2 Step 4.

  Don't add an AI attribution line. Then:
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
git push --force-with-lease -u origin feat/settings-pager
gh pr create --base main --head feat/settings-pager --title "Settings pager with auto-save, info tags and a hold switch" --body-file "$SCRATCH/claude-pr-body.md"
```

**Task 9 gate:** the PR is open and CI (`testDebugUnitTest lintDebug`) is green on it. CI must run both MigrationTestHelper tests, not skip them. Check the CI test report.

---

## Expected test counts

| After | Added | Removed | Cumulative |
|---|---|---|---|
| 1.1 baseline | | | **265** |
| 2.1 `ConfigsTest` | 1 | | 266 |
| 2.2 `SettingsValidatorTest` | 1 | | 267 |
| 2.3 `HoldResetTest` | 4 | | 271 |
| 3.1 `HiitDatabaseTest` | 3 | | 274 |
| 3.2 mapping 2 · readers 1 · migrator 1 | 4 | | 278 |
| 3.3 `RoomEntryRepositoryTest` | 5 | | 283 |
| 4.1 `AutoSaverTest` | 8 | | 291 |
| 5.1 `InfoTagTest` 2 · `InfoTextsTest` 1 | 3 | | 294 |
| 5.2 `StepperRowTest` 2 · `SwitchRowTest` 1 | 3 | | 297 |
| 6.1 Timing ViewModel (4 → 10) | 6 | | 303 |
| 6.2 Progression ViewModel (5 → 9) · screen (1 → 3) | 6 | | 309 |
| 7.1 Current ViewModel (5 → 9) · `CurrentStatePageTest` 2 | 6 | | 315 |
| 7.2 `CuesPageTest` | 1 | | 316 |
| 8.1 `SettingsPagerViewModelTest` 2 · `SettingsPagerTest` 11 | 13 | | 329 |
| 8.2 `NavActionsTest` +1 · legacy-route test −1 | 1 | 1 | **329** |

Windows skips: 1 up to 2.3, then 2 (both file-based `MigrationTestHelper` tests).

## Spec Coverage

| Spec | Where |
|---|---|
| §1–§3 purpose, scope, delta (out of scope: history/graph, reset-history choice, list/entry/timer/maths changes, none touched) | all tasks; `RepProgression` is unchanged (2.1) |
| §4 route `entry/{id}/settings/pages?page={page}`, optional int 0–3 with default 0, `Routes.settingsPage(id, page)` signature kept | 8.2 (`RoutesTest`, `NavActionsTest`) |
| §4 Entry Settings keeps its four rows, each opening the pager at its page; Rename/Duplicate/Delete unchanged | 8.1 (`SettingsPage.tab`, labels kept), 8.2 (`openPage`) |
| §4 pager screen: ← + "<name> settings", `PrimaryTabRow`, `HorizontalPager` (4), tab animates, swipe updates the tab, page survives recreation | 8.1 (`SettingsPagerRoute`, `rememberPagerState`; `SettingsPagerTest`) |
| §4 leaving flushes: back, ←, page change, `ON_STOP`, `onEntryGone` | 8.1 (route; tests for page change, ←, system back and ON_STOP via `moveToState(CREATED)`), 9.2 item 4 (ON_STOP on the device) |
| §4 active-run routing, `dropUnlessResumed`, `EntryNotFound → popToEntries` unchanged | 8.2 (nav host diff review), 8.1 (missing → pop test) |
| §5.1 `hold`, `holdEnabled`; validator skips hold checks and hint when off | 2.1, 2.2 |
| §5.2 migration SQL verbatim, version 2, registered, `2.json` committed, no destructive fallback | 3.1 |
| §5.2 read repair of `hold_enabled` (boolean, default true); `V1Migrator` maps `hold_for` 0; create uses defaults; duplicate copies the flag | 3.2, 3.3 |
| §5.3 Hold switch row above *Hold at*; `AnimatedVisibility` hides the rows; values kept and restored | 6.2 (`ProgressionSettingsScreenTest`) |
| §6.1 typed drafts in the page ViewModels, surviving swipes and recreation via `SavedStateHandle`; the validator runs on every change; cross-field rules unclamped | 6.1, 6.2, 7.1 (restored-from-handle tests); Spec note 2 (7.1) |
| §6.2 debounce 400 ms for steppers; immediate for dialog OK, switches, pickers, Clear and resets; invalid cancels; flush; `@ApplicationScope` on clear; `Mutex` ordering | 4.1 (`AutoSaver`), 5.2 (`onDialogUpdate`), 6.1, 6.2, 7.1, 8.1 |
| §6.2 errors: `EntryNotFound → markMissing`, IAE logged → "Not saved"; status line "Saved" / "Not saved: fix the highlighted field"; Timing TOTAL footer kept, red above 2:00:00 | 6.1 (`SaveStatus`, `SaveStatusLine`, rejected-write test), 6.2, 7.1 |
| §6.3 `setProgression` / `overwriteCounter` in one transaction with the hold-count rules; Reset progress still zeroes it | 2.3 (pure rules), 3.3 (Room + fake), 6.2 and 7.1 (ViewModel) |
| §6.4 Reset to defaults confirmed and saved at once; Reset progress confirmed and applied at once | 6.2, 7.1 |
| §7.1 `InfoTag`: 48 dp `IconButton`, "About <title>", after the label in `StepperRow`, the switch rows and the date row, separate touch target, dialog with OK | 5.1, 5.2, 6.1 (TOTAL), 7.1 (date row), 7.2; Spec note 1 (drawable) |
| §7.1 `info_<field>` strings; unit test that every row has a non-blank text | 5.1 (`InfoTextsTest`), 8.1 (`every row has an info tag of at least 48 dp`) |
| §7.2 texts (verbatim, Markdown emphasis dropped) | 5.1 (strings.xml); Spec note 5 |
| §8 domain tests | 2.1, 2.2, 2.3 |
| §8 Room tests (helper 1 → 2 on CI, repository hold rules, duplicate, `V1Migrator`) | 3.1, 3.2, 3.3 |
| §8 ViewModel tests (400 ms incl. burst of 10, invalid cancels, dialog OK at once, page change and clear flush, `EntryNotFound`, reset after confirmation) | 6.1, 6.2, 7.1, 4.1 |
| §8 Compose tests (tab and swipe, `page=2` → Current, Hold switch, ⓘ per page, ≥ 48 dp, no Save button, status line) | 8.1, 6.1, 6.2, 5.1, 5.2 |
| §8 device (backup, install over, entries/totals/hold survive, edits persist after a force-stop) | 9.2 |
| §9 identity, no attribution, squash to one commit, ask before pushing, TDD subtasks, phone backup | Global Constraints, 1.1, 9.2 Step 1, 9.3 |

## Self-Review

- **Spec coverage.** Every section from §4 to §9 maps to at least one subtask (table above). Each §8 test bullet has a named test.
  - "Changing page flushes" is covered at ViewModel level by `flush writes a pending stepper change at once` and in the pager by `changing page flushes a pending save`.
  - "Clearing the ViewModel" is covered by `clearing the view model flushes a pending change through the application scope`.
  - The `ON_STOP` flush is covered by `the app going to the background flushes a pending save` (8.1) and by 9.2 item 4 on the device.
- **Placeholder scan.** No step says "TBD", "similar to" or "add appropriate …".
  - Every new or grown file is shown complete.
  - Every other edit quotes its exact before/after block, and each before block was checked against `main`.
  - The only external value the plan can't pin is the generated `2.json`, which Room exports and 3.1 verifies (`hold_enabled`, `DEFAULT 1`, and `1.json` unchanged).
- **Type consistency.** These names are used identically in every task:
  - `AutoSaver<T>(scope, debounceMs, write)` with `schedule`/`saveNow`/`cancel`/`flush`/`flushIn`/`exclusive`/`hasPending`.
  - `SaveStatus.of(validation, failed)`.
  - The page composables:
    - `TimingPage(vm)` / `TimingPageContent(draft, validation, status, onChange, onChangeNow)`;
    - `ProgressionPage(vm)` / `ProgressionPageContent(…, onReset)`;
    - `CurrentStatePage(vm)` / `CurrentStatePageContent(draft, validation, status, zone, now, onChange, onChangeNow, onResetProgress)`;
    - `CuesPage(vm)`.
  - The ViewModel constructors:
    - `TimingSettingsViewModel(handle, repo, appScope)`;
    - `ProgressionSettingsViewModel(handle, repo, appScope)`;
    - `CurrentStateViewModel(handle, repo, clock, appScope)`;
    - `CuesSettingsViewModel(handle, repo)`;
    - `SettingsPagerViewModel(handle, repo)`.
  - `SettingsPagerRoute(initialPage, onBack, onEntryGone, pagerVm, timingVm, progressionVm, currentVm, cuesVm)`.
  - `EntryDao.setProgression(…, holdEnabled, …, resetHoldCount)`.
  - `holdResetNeeded(old, new)` and `counterHoldReset(oldTotal, newTotal)`.
  - `Routes.SETTINGS_PAGES`, `Routes.PAGE_ARG` and `Routes.settingsPage(id, page)`.
  - The test tags: `save_status`, `switch_<label>`, `tab_<PAGE>`, `settings_pager`, `info_title`/`info_text`/`info_ok`, `reset_defaults`/`confirm_reset_defaults`, `reset_progress`/`confirm_reset_progress` and `clear_last_check_in`.
- **Counts.** Each cumulative count equals the previous one plus the `@Test` methods the subtask adds, minus those it deletes (table above). 8.2's `clean` build removes the stale XML of the deleted test.
