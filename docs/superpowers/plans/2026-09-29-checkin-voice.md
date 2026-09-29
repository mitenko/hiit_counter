# Repkit Check-in-only Entries, Check in / Start and Voice Cue Implementation Plan (revision 4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Entries can be **check-in only**: a habit tracker with no timer and no reps, whose check-ins move only the day and the streaks. Workout entries get a separate **Check in** button next to **Start**, so today's reps are visible before the run. An optional **Voice** cue says each set's reps as work starts, ducking music, with the screen off.

**Architecture:** The app stays a single `:app` module using MVVM.
- **Domain (pure, test-first):** `EntryType { WORKOUT, CHECK_IN }` and `Entry.type`. `CueConfig.voice`. `RepProgression.checkIn(…, countsReps)` keeps the total and hold count when `countsReps` is false. `Cue.PhaseStart(phase, reps)`, with `TimerController` filling `reps` for WORK from the list given to `start()`. `VoicePolicy.speakerWanted` decides when the service holds a speaker.
- **Room:** `hiit.db` moves to version 3 with `entry.type` (TEXT, default `'WORKOUT'`) and `entry.cue_voice` (INTEGER, default 0), `HiitDatabase.MIGRATION_2_3` in `MIGRATIONS`, and a committed `3.json`. `type` is a plain `String` column mapped in `EntryMapping` (read repair: unknown → WORKOUT). The repository gains `create(name, type)`, `setType`, a voice-writing `setCues`, and a `checkIn` that passes `countsReps = (type == WORKOUT)` inside its transaction.
- **Voice:** `platform/CueSpeaker` (interface) with `AndroidCueSpeaker` over `TextToSpeech`, and `platform/VoiceAvailability` for the Settings check. `CuePlayer` says the reps after the WORK beep inside the same ducking focus request, and releases focus when the utterance ends (3 s timeout). `TimerService` creates the speaker for a voice run and shuts it down when the run ends or the service stops.
- **UI:** the Entry screen gets Check in (outlined) + Start (filled) for Workouts and a streak-only table with one Check in for check-in-only entries. The list byline reads "Streak N" for check-in-only rows. The create dialog gets a Workout | Check-in only segmented button. Entry Settings gets a Type row (ⓘ, radio dialog, saved at once) and hides Timing and Cues for check-in-only entries. The pager shows only Progression · Current for them, with only the window on Progression and no total on Current. Cues gets a Voice switch.

**Tech Stack:** Unchanged from revision 3. These are the pins in `gradle/libs.versions.toml`:
- Kotlin 2.2.10, AGP 8.13.0, Gradle 8.13, KSP 2.2.10-2.0.2.
- Compose BOM 2026.06.01 (Material 3 1.4.0).
- Hilt 2.57 and hilt-navigation-compose 1.3.0.
- Navigation Compose 2.9.5, lifecycle 2.9.4, activity-compose 1.10.0, core-ktx 1.16.0.
- Room 2.8.1 with the `androidx.room` plugin, DataStore Preferences 1.1.1, coroutines 1.10.2.
- JUnit 4.13.2, androidx.test.ext:junit 1.3.0, Robolectric 4.16.

**No new dependencies.** Text-to-speech is the platform `android.speech.tts.TextToSpeech`. The segmented button, radio button and outlined button are all in Material 3 1.4.0.

**Spec:** `docs/superpowers/specs/2026-09-29-checkin-voice-design.md` (revision 4) is binding. It amends `docs/superpowers/specs/2026-09-28-settings-pager-design.md` (revision 3, including its §10 implementation notes), `docs/superpowers/specs/2026-09-25-multi-entry-design.md` (revision 2) and `docs/superpowers/specs/2026-09-24-hiit-counter-design.md` (v1). Read all four before starting. In this plan, `§n` points into revision 4, `R3 §n` into revision 3, `R2 §n` into revision 2 and `v1 §n` into v1.

**Starting point:** revision 3 as merged on `main` (`c09474c`, PR #5). The baseline suite is **333 tests**, with 2 skipped on Windows (`HiitDatabaseTest`'s two file-based MigrationTestHelper checks). Don't lose any of these earlier behaviours when you edit the files that hold them:
- **Start flow (v1 §4, R2 §7.4):** the check-in is committed only after the foreground service reports Started; `CancellationException` → `cancelPrepare()` and rethrow; every other failure → `fail()` (cancel the prepare, show the error); "busy while starting" disables ←, ⚙ and system back; `dropUnlessResumed` tap guards in `HiitNavHost`.
- **TimerService (v1 §10, R2):** the wake lock is held through the DONE grace (`FINISH_CUE_GRACE_MS`) and released by the status collector, not by the DONE state; a second `onStartCommand` re-asserts `startForeground`; a new PREPARING drops a leftover wake lock; a `startForeground` failure reports `onServiceFailed`, releases the lock and stops the service; `collectLatest` cancels a pending DONE-grace stop.
- **R3:** the `AutoSaver` contract (its `write` never throws and is idempotent; `exclusive` is not re-entrant); every pager exit flushes; `EntryScopedViewModel.missing` → `popToEntries`; the Cues read-modify-write `Mutex`; the `rememberSaveable` dialog text; Reset progress in `@ApplicationScope`.

## How this plan is structured

- **9 tasks**, each split into **numbered subtasks** (`3.1`, `3.2`, …), **20 subtasks** in all. A subtask is one small TDD slice:
  1. write the failing test(s);
  2. run them and see them fail for the stated reason;
  3. write the minimal code;
  4. run the full suite and see the stated cumulative test count pass;
  5. commit.

  **One commit per subtask.**
- Kotlin paths are relative to `app/src/main/kotlin/com/mitenko/hiitcounter/`. Paths starting with `test/` are relative to `app/src/test/kotlin/com/mitenko/hiitcounter/`.
- **New files, and files that grow substantially, are shown complete**, so replace the whole file. Existing files that change once or twice in a small way get **exact edits**: replace the quoted block with the new block. Every quoted "before" block was checked against `main` (or against the state an earlier subtask of this plan leaves). Test files grow by appending the listed test methods to the existing class, and new imports go with them.
- **Red** runs only the focused test class(es). **Green** runs the whole `testDebugUnitTest` suite and checks the cumulative count with the test-count command (Global Constraints). Every expected count starts from the **333** baseline recorded in 1.1. If the baseline differs, offset every expected count by the difference.
- A red step sometimes notes a test that already passes. That test is a regression guard for behaviour an earlier slice delivered, and it must stay green.
- Subtasks with nothing to unit-test (docs, the device check, the PR) replace red/green with a build or lint verification.
- Every task ends with a **task gate**: `./gradlew assembleDebug testDebugUnitTest lintDebug`. The gate compiles the Hilt graph, runs the suite and runs lint. It must pass before the next task starts.
- **The app compiles and the suite passes at every gate.** New parameters get defaults where an existing caller would otherwise break, so no subtask leaves the build red. **Don't install any build on the device before 9.2.**
- **Diff review before every whole-file replacement commit:** run `git diff <file>` and confirm the only changes are the ones the subtask describes. In particular, check that no behaviour listed under "Starting point" is lost, and that public signatures are unchanged unless the subtask says otherwise.
- **Pinned versions are fixed.** If anything fails to resolve, stop and report to the user. Don't bump, add or substitute dependencies on your own.

## Spec notes (confirm with the user in 1.1)

Reading the real code against the spec surfaced the points below. The plan resolves each as stated, and 9.1 records them in the spec (§9, "Implementation notes") so code and spec don't silently diverge.

1. **Ambiguity: "Start … calls `checkIn` first" (§4.1).**
   - The existing flow (v1 §4, kept by §4.1's "unchanged" list) prepares the controller, starts the foreground service, waits for Started, **then** calls `checkIn`, then starts the timer. Moving the check-in before the service would record a check-in for a run that then fails to start.
   - Resolution: "first" means "before the timer starts". The order is unchanged. After a manual Check in, Start's `checkIn` returns `AlreadyToday` and writes nothing.
2. **Gap: how the utterance's end reaches `CuePlayer` (§5).**
   - §5 names `speak(number: Int)` and says focus is abandoned "after the utterance completes (`UtteranceProgressListener`)", but the interface has no completion signal.
   - Resolution: `CueSpeaker.speak` is a `suspend` function that returns when the utterance ends (done, error, or replaced by a newer one). `CuePlayer` wraps it in `withTimeoutOrNull(3_000)`. The Android implementation resumes it from the `UtteranceProgressListener`.
3. **Ambiguity: beep then speech (§5).**
   - "It plays the existing beep and then … speaks": with `QUEUE_FLUSH` and no wait, the number would sound over the 600 ms WORK tone.
   - Resolution: with Sound on, the number is spoken once the WORK tone's pattern has played (`CuePattern.durationMs`, 600 ms). With Sound off, it's spoken at once. The 3 s timeout counts from the start of speech. Focus is abandoned only when both the tones' hold and the speech have ended.
4. **Gap: "Voice not available on this device" outside a run (§4.7).**
   - §5 creates a speaker only while a voice run is active, so the Cues page has nothing to ask.
   - Resolution: `platform/VoiceAvailability.check()` initialises a throwaway `AndroidCueSpeaker`, waits for it (5 s timeout) and shuts it down. `CuesSettingsViewModel` calls it once; the supporting text shows only once the check has said "unavailable" (not while it's still running).
5. **Design choice: `type` is a `String` column mapped in `EntryMapping`, not a `TypeConverter`.**
   - Why: read repair (§3.2) must log the entry id and fall back to WORKOUT, like every other per-field repair in `EntryMapping`. A converter would either throw on an unknown value (`valueOf`), breaking the whole list flow, or repair silently without the entry id. The column stays the plain `TEXT` of the §3.2 SQL, and the DAO's `setType` writes `type.name`.
6. **Clarification: a check-in-only check-in never touches the stored total.**
   - `RoomEntryRepository.checkIn` writes the row's raw `total` back unchanged when `countsReps` is false, so a NULL total stays NULL (it keeps reading as the starting total). The streaks, the hold count (unchanged) and `last_check_in` are written as usual.
7. **Ambiguity: Reset to defaults on a check-in-only Progression tab (§4.6).** "It shows only Check-in window (hours)".
   - Resolution: Reset to defaults is hidden too (it would reset the hidden values). The status line stays.
8. **Clarification: the Current tab without the total (§4.6).** "Saves keep the stored total."
   - Resolution: the draft still carries the total (the stored counter, resolved), and `overwriteCounter` writes it back. A NULL total therefore becomes the starting-total value on the first streak edit, exactly as the R3 Current page already does for Workouts. The total never changes, so the hold count is kept (R3 §6.3).
9. **Clarification: the two buttons guard each other (§4.1).** Check in is disabled while Start is in flight, and Start is disabled while a Check in call is in flight. The ViewModel re-checks both.
10. **Clarification: a destructive-looking write survives leaving the screen.** Check in and `setType` run their repository call in `withContext(NonCancellable)`, so pressing ← right after the tap can't cancel a write that has started.
11. **Clarification: the "Checked in today" line under the table stays** (it's also the list's ✓ content description). §4.1 says the table is unchanged.
12. **Clarification: Type dialog OK with the current type selected writes nothing.**
13. **Defect: Android 11+ package visibility.** With `targetSdk 36`, `TextToSpeech` can't see any engine unless the manifest declares `<queries><intent><action android:name="android.intent.action.TTS_SERVICE" /></intent></queries>`. Without it, every device would show "Voice not available". Resolution: 4.2 adds the `<queries>` element.
14. **Accepted limitation: a very short prepare.** The engine initialises asynchronously after the run is prepared. If the first work set starts before it's ready (e.g. PREPARE 0), that set is silent; later sets speak. `available` is false until then, so nothing blocks.
15. **Defect (not fixed here): `info_last_check_in` says "When you last pressed Start."** Check in now sets it too. §6 doesn't list a new text, so the string is unchanged; 9.1 records it for the user to decide.
16. **Test seam: `CueFocus`.** `CuePlayer` takes an optional `CueFocus` (request/abandon), defaulting to the `AudioManager` one, so the §7 "focus is released" tests don't depend on Robolectric's audio shadows.
17. **Pending user question: the `info_last_check_in` wording.** It may change from "When you last pressed Start. …" to "When you last checked in. …". **The string is not changed by this plan** until the user answers in 1.1. If they approve, the change is one `strings.xml` line in 5.2 (no test counts change: `InfoTextsTest` checks only that the text isn't blank).
18. **Clarification: no voice check for a check-in-only entry.** Its Cues tab is hidden, so `CuesSettingsViewModel` skips the throwaway engine and `voiceAvailable` stays null.

## Global Constraints

- Package / applicationId: `com.mitenko.hiitcounter`. minSdk **26**, compileSdk **36**, targetSdk **36**. Toolchain JDK **17** (`kotlin { jvmToolchain(17) }`).
- Sources live in `app/src/main/kotlin/...` and tests in `app/src/test/kotlin/...`, never under `java/`.
- `domain/` stays pure Kotlin: no `android.*` or `androidx.*` imports. `Clock` is injected everywhere, so never call `Instant.now()`, `System.currentTimeMillis()` or `SystemClock` outside `platform/AndroidClock.kt`. `ui/common/AutoSaver.kt` stays free of Android imports.
- `Locale.ENGLISH` for all date and number formatting, and for the voice. Dark theme only and portrait only. **No "WORK" label** on the timer.
- **Commits are authored as `mitenko <mitenko@gmail.com>`.** This identity is applied automatically for GitHub remotes. Confirm that `git config user.email` prints `mitenko@gmail.com` before the first commit (1.1). If it doesn't, stop and ask the user, and never commit with a work identity.
- **No AI attribution.** Commit messages and PR descriptions carry no co-author trailers and no "generated with" footers.
- Git: work on branch **`feat/checkin-voice`**, created in 1.1 from an up-to-date `main`. Make **one commit per subtask**, with the messages given. Never merge locally into `main`. Never push or open a PR without asking the user (9.3).
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
  - On Windows, 2 tests are skipped until 3.1 and 3 from 3.1 on (the MigrationTestHelper guard below).
- **Transient Gradle daemon `BindException`** (caused by the user's Android Studio): retry the same command, **up to 3 times**. Don't kill anything. If the third retry fails too, report it. A killed or interrupted run is **inconclusive**, so re-run it rather than reading its partial output.
- **Room schemas.**
  - `app/schemas/` is served as **debug assets** (`sourceSets["debug"].assets.srcDir("$projectDir/schemas")` in `app/build.gradle.kts`, with `mergeDebugAssets` depending on `copyRoomSchemas*`). Robolectric reads those assets, so `MigrationTestHelper` can load `1.json`, `2.json` and `3.json`.
  - **The generated `app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/3.json` must be committed** (3.1).
  - `1.json` and `2.json` must stay byte-for-byte unchanged (`git diff --exit-code` in 3.1).
- **Windows `MigrationTestHelper` guard.** androidx.sqlite 2.6.1's `SupportSQLiteDriver` compares database names with `substringAfterLast('/')`. That fails on Windows paths (backslashes) for **file-based** `MigrationTestHelper` databases.
  - Every file-based helper test starts with `assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))`. It is skipped locally and runs on CI (Linux).
  - To cover the migration SQL on Windows too, 3.1 also runs `HiitDatabase.MIGRATION_2_3_SQL` against an in-memory framework `SQLiteDatabase`.
- **Robolectric rules:**
  - Every Robolectric test class has `@RunWith(AndroidJUnit4::class)` and `@Config(sdk = [34])`.
  - The default viewport is 320×470 dp. `performClick()` does not scroll, so call `performScrollTo()` first on anything inside a scrolling column that might be below the fold. A footer node (the status line, TOTAL) isn't in a scrolling parent, so it's clicked without `performScrollTo()`. `assertIsNotEnabled`, `assertTextEquals`, `assertExists` and the size assertions don't need the node on screen.
  - A tagged node inside a clickable or selectable parent (which merges its descendants) is found with `useUnmergedTree = true`, as the Type row's `type_value` is in 7.2. Tags on the clickable node itself (`check_in`, `start`, `type_option_*`, `type_WORKOUT`) use the merged tree.
  - Tests that need a phone-size screen, such as the pager, use `@Config(sdk = [34], qualifiers = "w411dp-h891dp")`.
  - Room in-memory databases need Robolectric: `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java).allowMainThreadQueries().build()`, closed in `@After`.
  - `app/src/test/resources/robolectric.properties` makes Robolectric use a plain `android.app.Application`, so `HiitApp` never runs in unit tests. No unit test constructs `AndroidCueSpeaker`, `AndroidVoiceAvailability` or `TimerService`; the fakes stand in.
- **ViewModel tests and virtual time:**
  - `MainDispatcherRule()` installs an `UnconfinedTestDispatcher` as Main, and `runTest` shares its scheduler. So `advanceTimeBy` / `runCurrent` drive `viewModelScope` delays.
  - **`advanceUntilIdle()` does not advance work that lives only in `backgroundScope`.** Every test that drives coroutines launched in `backgroundScope` (the `TimerController` and `CuePlayer` tests) uses `advanceTimeBy(N)` followed by `runCurrent()`. Never use `advanceUntilIdle()` in this plan.
  - ViewModels that take `@ApplicationScope` get the test's `backgroundScope` in JVM tests, and `MainScope()` in Robolectric Compose tests.
- **Writes that must survive the ViewModel.** A user-confirmed write (Check in, the Type dialog's OK) runs its repository call in `withContext(NonCancellable)` inside `viewModelScope.launch`. `viewModelScope` dispatches on `Main.immediate`, so the call has started before `launch` returns. `CancellationException` is always rethrown, never reported as an error.
- **`AutoSaver` is unchanged.** Its `write` lambdas must not throw (they map `EntryNotFound` and `IllegalArgumentException` to page state) and must be idempotent, and `exclusive` is not re-entrant. No subtask adds a new `write` or `exclusive` caller.
- **Threading:**
  - `CuePlayer`, `AndroidCueSpeaker` and `AndroidVoiceAvailability` are used on Main. `UtteranceProgressListener` callbacks arrive on a binder thread, so `AndroidCueSpeaker` keeps its pending utterances in a `ConcurrentHashMap` and only resumes continuations from there.
  - Room suspend calls return to the caller's dispatcher. There is still no `withContext(Dispatchers.IO)` around `TimerController` calls (R2 §5.3).
- `dropUnlessResumed` (lifecycle-runtime-compose 2.9.4) only has a **zero-arg** overload. Callbacks that take an argument use `dropUnlessResumedWith` (`ui/navigation/NavActions.kt`). The navigation graph is unchanged by this revision.

## Subtask Overview

| Task | Subtasks |
|---|---|
| 1 Branch | 1.1 docs on main, branch, identity, baseline |
| 2 Pure domain | 2.1 `EntryType`, `Entry.type`, `CueConfig.voice` · 2.2 `RepProgression.checkIn(…, countsReps)` · 2.3 `Cue.PhaseStart(phase, reps)` filled by `TimerController` |
| 3 Room v3 | 3.1 `type`, `cue_voice`, version 3, `MIGRATION_2_3`, `3.json` · 3.2 mapping, read repair, v1 import · 3.3 repository (`create(name, type)`, `setType`, `setCues`, `checkIn`, duplicate) and the fake |
| 4 Voice cue | 4.1 `CueSpeaker`, `CueFocus`, `CuePlayer` speaks at WORK start · 4.2 `AndroidCueSpeaker`, `VoiceAvailability`, `VoicePolicy`, `TimerService` lifecycle, `<queries>` |
| 5 Entry screen | 5.1 `EntryViewModel.onCheckIn`, type in the state · 5.2 Check in / Start buttons, check-in-only table, every R4 string |
| 6 Entry list | 6.1 "Streak N" byline · 6.2 create dialog with Workout \| Check-in only |
| 7 Entry Settings | 7.1 `setType`, type in the state, `SettingsPage.visibleFor` · 7.2 Type row, radio dialog, hidden page rows |
| 8 Pager + Cues | 8.1 check-in-only tabs, page mapping, window-only Progression, total-less Current · 8.2 Voice switch with its unavailable text |
| 9 Finish | 9.1 docs + full verification · 9.2 device verification on the Pixel 9a · 9.3 squash and PR |

## File Map

```
app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/3.json            generated, committed (3.1)
app/src/main/AndroidManifest.xml                                           <queries> TTS_SERVICE (4.2)
app/src/main/res/values/strings.xml                                        R4 §6 strings (5.2)
app/src/main/kotlin/com/mitenko/hiitcounter/
  domain/model/EntryType.kt                            new (2.1)
  domain/model/Entry.kt                                type (2.1)
  domain/model/CueConfig.kt                            voice (2.1)
  domain/RepProgression.kt                             countsReps (2.2)
  domain/model/Cue.kt                                  PhaseStart.reps (2.3)
  domain/TimerController.kt                            fills WORK reps (2.3)
  domain/VoicePolicy.kt                                new (4.2)
  data/db/EntryEntity.kt                               type, cue_voice (3.1)
  data/db/HiitDatabase.kt                              version 3, MIGRATION_2_3(_SQL), MIGRATIONS (3.1)
  data/EntryMapping.kt                                 type ⇄ EntryType with repair, cue_voice ⇄ voice (3.2)
  data/db/EntryDao.kt                                  setCues writes cue_voice, setType (3.3)
  data/EntryRepository.kt                              create(name, type), setType, setCues, checkIn countsReps, duplicate (3.3)
  platform/CueSpeaker.kt                               new interface (4.1)
  platform/AndroidCueSpeaker.kt                        new (4.2)
  platform/VoiceAvailability.kt                        new interface + Android implementation (4.2)
  service/CueFocus.kt                                  new (4.1)
  service/CuePlayer.kt                                 speaks WORK reps, shared focus (4.1)
  service/TimerService.kt                              speaker lifecycle (4.2)
  di/AppModule.kt                                      VoiceAvailability binding (4.2)
  ui/entry/EntryViewModel.kt                           type, checkingIn, onCheckIn (5.1)
  ui/entry/EntryScreen.kt                              buttons, check-in-only table (5.2)
  ui/entries/EntryListViewModel.kt                     row type + streak (6.1), create(name, type) (6.2)
  ui/entries/EntryListScreen.kt                        "Streak N" (6.1), type choice in the create dialog (6.2)
  ui/common/NameDialog.kt                              optional extra slot (6.2)
  ui/common/EntryTypeLabel.kt                          new (6.2)
  ui/settings/EntrySettings.kt                         visibleFor, setType (7.1); Type row + dialog (7.2)
  ui/settings/SettingsPager.kt                         type, visible tabs, tabIndex (8.1)
  ui/settings/ProgressionSettings.kt                   windowOnly (8.1)
  ui/settings/CurrentStateSettings.kt                  showTotal (8.1)
  ui/settings/CuesSettings.kt                          Voice switch, availability (8.2)
  ui/common/SettingsComponents.kt                      SwitchRow supportingText (8.2)
app/src/test/kotlin/com/mitenko/hiitcounter/
  testutil/Entries.kt                                  type (2.1)
  testutil/FakeEntryRepository.kt                      R4 rules, typeWrites, checkInGate (3.3)
  testutil/FakeCueSpeaker.kt                           new (4.1)
  testutil/FakeVoiceAvailability.kt                    new (8.2)
  domain/{model/ConfigsTest,model/EntryTest}.kt        +1 each (2.1)
  domain/RepProgressionTest.kt                         +4 (2.2)
  domain/TimerControllerTest.kt                        +1, one assertion updated (2.3)
  domain/VoicePolicyTest.kt                            new (4.2)
  data/db/HiitDatabaseTest.kt                          +3 (3.1)
  data/{EntryMappingTest,v1/V1MigratorTest}.kt         +2, +1 (3.2)
  data/RoomEntryRepositoryTest.kt                      +6 (3.3)
  service/CuePlayerTest.kt                             new (4.1)
  ui/entry/{EntryViewModelTest,EntryScreenTest}.kt     +5, +5 (5.1, 5.2)
  ui/common/InfoTextsTest.kt                           ROWS updated (5.2)
  ui/entries/{EntryListViewModelTest,EntryListScreenTest}.kt                  +1 +1 (6.1), +1 +1 (6.2)
  ui/settings/{EntrySettingsViewModelTest,SettingsPagesTest}.kt               +2, new (7.1)
  ui/settings/EntrySettingsScreenTest.kt               +2 (7.2)
  ui/settings/{SettingsPagesTest,SettingsPagerViewModelTest,SettingsPagerTest,ProgressionSettingsScreenTest,CurrentStatePageTest}.kt   +1 +1 +2 +1 +1 (8.1)
  ui/settings/{CuesSettingsViewModelTest,CuesPageTest}.kt                      +1, +1 (8.2)
claude.md, docs/superpowers/specs/2026-09-29-checkin-voice-design.md          notes (9.1)
```

---

## Task 1: Branch

### Subtask 1.1: Docs on main, branch, identity and baseline

**Files:** none.

- [ ] **Step 1: Confirm the spec notes.** Show the user the "Spec notes" section above and ask them to confirm the resolutions, above all:
  - the unchanged Start order (note 1);
  - `speak` as a suspend call (note 2) and speech after the beep (note 3);
  - the Settings voice check (note 4);
  - the String column (note 5);
  - Reset to defaults hidden for check-in-only entries (note 7);
  - the manifest `<queries>` fix (note 13) and the stale `info_last_check_in` text (note 15);
  - **ask:** should `info_last_check_in` read "When you last checked in…" instead of "When you last pressed Start…" (note 17)? Record the answer; change the string only if they say yes.

  If they change a resolution, stop and ask how to amend the plan before starting.

- [ ] **Step 2: Preconditions and branch.** The docs branch (`docs/checkin-voice`: the R4 spec and this plan) is merged into `main` **before** execution starts. Check that first, without switching branches, so the plan never disappears from the working tree mid-execution:
```bash
git status --short            # must be empty (bash.exe.stackdump is gitignored)
git fetch origin
git show origin/main:docs/superpowers/specs/2026-09-29-checkin-voice-design.md > /dev/null && \
  git show origin/main:docs/superpowers/plans/2026-09-29-checkin-voice.md > /dev/null && echo "CLAUDE-DOCS-ON-MAIN ok"
```
**Stop and ask the user if this doesn't print `CLAUDE-DOCS-ON-MAIN ok`.** It means the docs PR hasn't been merged yet. Don't check out `main` in that case. Only once the check passes:
```bash
git checkout main && git pull --ff-only     # main now contains the R4 spec and this plan
git show main:docs/superpowers/plans/2026-09-29-checkin-voice.md > /dev/null && echo "CLAUDE-DOCS-ON-LOCAL-MAIN ok"
git config user.email         # must print mitenko@gmail.com
git checkout -b feat/checkin-voice
```
If `git config user.email` doesn't print `mitenko@gmail.com`, stop and ask. Don't change git config yourself.

- [ ] **Step 3: Baseline** — Run (label `T1-1-BASELINE`, ~3 min): `./gradlew testDebugUnitTest`, then the test-count command. Expected: **333 tests**, all passing, 2 skipped on Windows. If the count differs, record it and offset every expected count in this plan by the difference.

- [ ] **Step 4: No commit** (nothing changed).

**Task 1 gate:** Step 3 is the gate.

---

## Task 2: Entry type, voice flag, streak-only check-ins and WORK reps in the domain (§3.1, §5)

**Interfaces produced:**
- `enum class EntryType { WORKOUT, CHECK_IN }` in `domain/model/EntryType.kt`.
- `Entry(…, counter, type: EntryType = EntryType.WORKOUT)`. `type` is the **last** parameter, so every existing positional `Entry(…)` call still compiles.
- `CueConfig(sound = true, vibration = true, voice = false)`.
- `RepProgression.checkIn(state, config, now, zone, countsReps: Boolean = true)`.
- `Cue.PhaseStart(phase: Phase, reps: Int? = null)`. `TimerController` emits WORK starts with the set's reps.
- `testEntry(…, type: EntryType = EntryType.WORKOUT)` in `test/testutil/Entries.kt`.

### Subtask 2.1: `EntryType`, `Entry.type` and `CueConfig.voice`

**Files:** Create `domain/model/EntryType.kt`; edit `domain/model/Entry.kt`, `test/testutil/Entries.kt`; replace `domain/model/CueConfig.kt`; append to `test/domain/model/ConfigsTest.kt`, `test/domain/model/EntryTest.kt`.

- [ ] **Step 1: Failing tests**

Append to `ConfigsTest`:
```kotlin
    @Test
    fun `the voice cue is off by default`() {
        assertFalse(CueConfig().voice)
        assertTrue(CueConfig(voice = true).voice)
        assertEquals(CueConfig(sound = true, vibration = true, voice = false), CueConfig())
    }
```

Append to `EntryTest` (same package, so no new imports):
```kotlin
    @Test
    fun `a new entry is a workout unless told otherwise`() {
        val entry = Entry(1, "Burpees", 0, TimingConfig(), ProgressionConfig(), CueConfig(), CounterState(total = 48))
        assertEquals(EntryType.WORKOUT, entry.type)
        assertEquals(EntryType.CHECK_IN, entry.copy(type = EntryType.CHECK_IN).type)
        assertEquals(listOf(EntryType.WORKOUT, EntryType.CHECK_IN), EntryType.entries.toList())
    }
```

- [ ] **Step 2: Run red** — Run (label `T2-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.model.*"`. Expected: compile FAIL, "No parameter with name 'voice' found" and "Unresolved reference 'EntryType'". The exact K2 wording may differ.

- [ ] **Step 3: Implement**

`domain/model/EntryType.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain.model

/**
 * Spec R4 §3.1. A Workout runs the timer and counts reps. A check-in-only entry records the day
 * and the streaks; its total and hold count never change.
 */
enum class EntryType { WORKOUT, CHECK_IN }
```

`domain/model/Entry.kt`. Replace:
```kotlin
    val counter: CounterState,
)
```
with:
```kotlin
    val counter: CounterState,
    /** Spec R4 §3.1: new entries are Workouts. */
    val type: EntryType = EntryType.WORKOUT,
)
```

`domain/model/CueConfig.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain.model

data class CueConfig(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    /** The Voice cue (spec R4 §5): says each set's reps as work starts. Off by default. */
    val voice: Boolean = false,
)
```

`test/testutil/Entries.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.domain.model.Entry
```
with:
```kotlin
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryType
```
Then replace:
```kotlin
    counter: CounterState = CounterState(total = progression.startingTotal),
) = Entry(id, name, position, timing, progression, cues, counter)
```
with:
```kotlin
    counter: CounterState = CounterState(total = progression.startingTotal),
    type: EntryType = EntryType.WORKOUT,
) = Entry(id, name, position, timing, progression, cues, counter, type)
```

- [ ] **Step 4: Run green** — Run (label `T2-1-GREEN`, ~3 min): `./gradlew testDebugUnitTest`, then the test-count command. Expected: **335 tests**.

- [ ] **Step 5: Commit** — diff-review `CueConfig.kt`, then `git add -A && git commit -m "feat(domain): entry type and the voice cue flag"`

### Subtask 2.2: `RepProgression.checkIn(…, countsReps)`

**Files:** Replace `domain/RepProgression.kt`; append to `test/domain/RepProgressionTest.kt`.

- [ ] **Step 1: Failing tests** — append to `RepProgressionTest` (the imports are already there). Each case is chosen so the rep-counting result would differ: a clamp to the floor, a +1, a penalty of 3.
```kotlin
    private fun streaksOnly(s: CounterState, now: Instant) = RepProgression.checkIn(s, cfg, now, zone, countsReps = false)

    @Test
    fun `without reps a first check-in starts the streak and keeps the total and hold count`() {
        val now = hoursLater(1.0)
        // Counting reps would clamp 40 up to the floor (48) and reset the hold count.
        val r = streaksOnly(CounterState(total = 40, holdCount = 2), now)
        assertEquals(Outcome.First, r.outcome)
        assertEquals(CounterState(total = 40, bestStreak = 1, currentStreak = 1, lastCheckIn = now, holdCount = 2), r.state)
    }

    @Test
    fun `without reps an on-time check-in extends the streaks and keeps the total`() {
        val now = hoursLater(24.0)
        val r = streaksOnly(state(50, streak = 10, best = 10, hold = 2), now)
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(CounterState(total = 50, bestStreak = 11, currentStreak = 11, lastCheckIn = now, holdCount = 2), r.state)
    }

    @Test
    fun `without reps a missed window restarts the streak with no penalty`() {
        val now = hoursLater(100.0) // counting reps: round((100 − 24) / 19.5) − 1 = 3 reps lost
        val r = streaksOnly(state(60, streak = 5, best = 10, hold = 1), now)
        assertEquals(Outcome.Missed(0), r.outcome)
        assertEquals(CounterState(total = 60, bestStreak = 10, currentStreak = 1, lastCheckIn = now, holdCount = 1), r.state)
    }

    @Test
    fun `without reps a second check-in the same day changes nothing`() {
        val s = state(60)
        val r = streaksOnly(s, hoursLater(10.0))
        assertEquals(Outcome.AlreadyToday, r.outcome)
        assertEquals(s, r.state)
    }
```

- [ ] **Step 2: Run red** — Run (label `T2-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.RepProgressionTest"`. Expected: compile FAIL, "No parameter with name 'countsReps' found".

- [ ] **Step 3: Implement** — `domain/RepProgression.kt` (complete). The rep-counting path is unchanged apart from the shared `hoursSince`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

sealed interface Outcome {
    data object AlreadyToday : Outcome
    data object First : Outcome
    data object OnTime : Outcome
    data class Missed(val penalty: Int) : Outcome
}

data class CheckInResult(val state: CounterState, val outcome: Outcome)

/** Check-in rules — spec §6. Pure; evaluated in order, first match wins. */
object RepProgression {
    private const val MS_PER_HOUR = 3_600_000.0

    /**
     * [countsReps] is false for a check-in-only entry (spec R4 §3.1): rules 1–4 still decide the
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
        val last = state.lastCheckIn

        // Rule 1: already checked in today.
        if (last != null && last.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            return CheckInResult(state, Outcome.AlreadyToday)
        }

        if (!countsReps) return streaksOnly(state, config, now, last)

        // Rules 2–4 start from a total clamped to [floor, cap] (config may have changed).
        val total = state.total.coerceIn(config.floor, config.cap)

        // Rule 2: first ever check-in — perform the starting total on day 1.
        if (last == null) {
            return CheckInResult(
                CounterState(
                    total = total,
                    bestStreak = max(state.bestStreak, 1),
                    currentStreak = 1,
                    lastCheckIn = now,
                    holdCount = startingHoldCount(total, config),
                ),
                Outcome.First,
            )
        }

        val hours = hoursSince(last, now)

        // Rule 3: missed. A miss that leaves the total on holdAt restarts the hold.
        if (hours > config.windowHours) {
            val penalty = max(0, roundHalfUp((hours - 24) / config.penaltyHoursPerRep) - 1)
            val newTotal = max(config.floor, total - penalty)
            return CheckInResult(
                CounterState(
                    total = newTotal,
                    bestStreak = max(state.bestStreak, 1),
                    currentStreak = 1,
                    lastCheckIn = now,
                    holdCount = startingHoldCount(newTotal, config),
                ),
                Outcome.Missed(penalty),
            )
        }

        // Rule 4: on time (includes a clock that moved backwards: negative hours).
        val streak = state.currentStreak + 1
        val newTotal: Int
        val holdCount: Int
        if (config.holdEnabled && total == config.holdAt) {
            if (state.holdCount >= config.holdFor) {
                newTotal = total + 1
                holdCount = 0
            } else {
                newTotal = total
                holdCount = state.holdCount + 1
            }
        } else {
            newTotal = if (total < config.cap) total + 1 else total
            holdCount = startingHoldCount(newTotal, config)
        }
        return CheckInResult(
            CounterState(
                total = newTotal,
                bestStreak = max(state.bestStreak, streak),
                currentStreak = streak,
                lastCheckIn = now,
                holdCount = holdCount,
            ),
            Outcome.OnTime,
        )
    }

    /** Rules 2–4 for the streaks and the date only (spec R4 §3.1); the total and hold count are copied as they are. */
    private fun streaksOnly(state: CounterState, config: ProgressionConfig, now: Instant, last: Instant?): CheckInResult {
        val (streak, outcome) = when {
            last == null -> 1 to Outcome.First
            hoursSince(last, now) > config.windowHours -> 1 to Outcome.Missed(penalty = 0)
            else -> state.currentStreak + 1 to Outcome.OnTime
        }
        return CheckInResult(
            state.copy(bestStreak = max(state.bestStreak, streak), currentStreak = streak, lastCheckIn = now),
            outcome,
        )
    }

    private fun hoursSince(last: Instant, now: Instant): Int =
        roundHalfUp((now.toEpochMilli() - last.toEpochMilli()) / MS_PER_HOUR)

    /** 1 when this check-in is the first performed at the hold value, else 0. */
    private fun startingHoldCount(total: Int, config: ProgressionConfig): Int =
        if (config.holdEnabled && total == config.holdAt) 1 else 0
}
```

- [ ] **Step 4: Run green** — Run (label `T2-2-GREEN`, ~3 min): full suite + count. Expected: **339 tests**. The 21 existing `RepProgressionTest` cases must stay green: they pin the unchanged rep-counting path.

- [ ] **Step 5: Commit** — diff-review `RepProgression.kt` (only the `countsReps` parameter, the early `streaksOnly` return, `streaksOnly` and `hoursSince` are new), then `git add -A && git commit -m "feat(domain): streak-only check-ins that keep the total"`

### Subtask 2.3: `Cue.PhaseStart(phase, reps)`, filled by `TimerController`

`TabataEngine` keeps emitting `Cue.PhaseStart(phase)` (reps null), so `TabataEngineTest` and `CuePatternsTest` are untouched. The controller adds the set's reps to WORK starts. The engine calls `onState` for a step's first tick **before** its `PhaseStart` (`TabataEngine.run`, the `emit(step, …)` line right above `onCue(Cue.PhaseStart(step.phase))`), so `state.set` is the set that is starting.

**Files:** Replace `domain/model/Cue.kt`; edit `domain/TimerController.kt`; edit and append to `test/domain/TimerControllerTest.kt`.

- [ ] **Step 1: Failing tests** — in `TimerControllerTest`, the existing `cues are one-shot and never replayed` now receives a WORK start with its reps (the test's `reps` are 8 per set). Replace:
```kotlin
        assertEquals(listOf(Cue.Countdown(3), Cue.Countdown(2), Cue.Countdown(1), Cue.PhaseStart(Phase.WORK)), first)
```
with:
```kotlin
        assertEquals(listOf(Cue.Countdown(3), Cue.Countdown(2), Cue.Countdown(1), Cue.PhaseStart(Phase.WORK, reps = 8)), first)
```
Then append:
```kotlin
    @Test
    fun `each work start carries its own set's reps and other phases carry none`() = runTest {
        val c = controller()
        val cues = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { cues += it } }
        c.prepare(snapshot)
        c.onServiceStarted()
        c.start(listOf(1, 2, 3, 4, 5, 6, 7, 8))
        advanceTimeBy(240_000)
        runCurrent()
        val starts = cues.filterIsInstance<Cue.PhaseStart>()
        assertEquals((1..8).toList(), starts.filter { it.phase == Phase.WORK }.map { it.reps })
        assertEquals(7, starts.count { it.phase == Phase.REST })
        assertTrue(starts.filter { it.phase != Phase.WORK }.all { it.reps == null })
    }
```

- [ ] **Step 2: Run red** — Run (label `T2-3-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.TimerControllerTest"`. Expected: compile FAIL, "No parameter with name 'reps' found" and "Unresolved reference 'reps'".

- [ ] **Step 3: Implement**

`domain/model/Cue.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain.model

sealed interface Cue {
    data class Countdown(val secondsLeft: Int) : Cue

    /**
     * [reps] is the starting set's reps for a WORK start, filled in by TimerController from the
     * list given to start() (spec R4 §5). It is null for every other phase.
     */
    data class PhaseStart(val phase: Phase, val reps: Int? = null) : Cue
    data object Finished : Cue
}
```

`domain/TimerController.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.domain.model.CueConfig
```
with:
```kotlin
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Phase
```
Then replace:
```kotlin
            onCue = { _cues.tryEmit(it) },
```
with:
```kotlin
            onCue = { _cues.tryEmit(withReps(it, repsPerSet)) },
```
Then replace:
```kotlin
    /** Clears the run but deliberately not [lastEntryId]. */
```
with:
```kotlin
    /**
     * Spec R4 §5: a WORK start carries that set's entry of [repsPerSet]. The engine publishes the
     * set's first state just before its PhaseStart, so [state] already names the starting set.
     */
    private fun withReps(cue: Cue, repsPerSet: List<Int>): Cue {
        if (cue !is Cue.PhaseStart || cue.phase != Phase.WORK) return cue
        val set = _state.value?.set ?: return cue
        return cue.copy(reps = repsPerSet.getOrNull(set - 1))
    }

    /** Clears the run but deliberately not [lastEntryId]. */
```

- [ ] **Step 4: Run green** — Run (label `T2-3-GREEN`, ~3 min): full suite + count. Expected: **340 tests**. `TabataEngineTest` (11) and `CuePatternsTest` (4) must stay green unchanged.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): work starts carry the set's reps"`

**Task 2 gate:** Run (label `T2-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **340 tests**, no lint errors.

---

## Task 3: Room version 3, mapping, read repair and the repository (§3.2, §3.3)

**Interfaces produced:**
- `EntryEntity.type: String = "WORKOUT"` (column `type`, `defaultValue = "WORKOUT"`) and `EntryEntity.cueVoice: Boolean = false` (column `cue_voice`, `defaultValue = "0"`).
- `HiitDatabase` at version 3, with `MIGRATION_2_3_SQL: List<String>` (internal), `MIGRATION_2_3: Migration` and `MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)`. `StorageModule` already registers `MIGRATIONS`, so it's unchanged.
- `EntryEntity.entryType(): EntryType` (repair: unknown → WORKOUT) and `entryEntity(…, type: EntryType = EntryType.WORKOUT)`.
- `EntryDao.setCues(id, sound, vibration, voice)` and `EntryDao.setType(id, type: String)`.
- `EntryRepository.create(name, type = WORKOUT)`, `setType(id, type)`; `setCues` writes the voice; `checkIn` passes `countsReps`; `duplicate` copies the type (the voice rides along in `cues`).
- `FakeEntryRepository` mirrors all of it, plus `typeWrites` and `checkInGate`.

### Subtask 3.1: `type`, `cue_voice`, version 3, `MIGRATION_2_3` and the exported `3.json`

The entity change and the version bump go in **one** subtask. Changing the entity while the database is still at version 2 would make Room re-export `2.json` with a new identity hash.

**Files:** Edit `data/db/EntryEntity.kt`; replace `data/db/HiitDatabase.kt`; append to `test/data/db/HiitDatabaseTest.kt`. Generated: `app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/3.json`.

- [ ] **Step 1: Failing tests** — append to `HiitDatabaseTest` (all imports are already there). First the tests and helpers, inside the class after the last existing helper (`holdColumns`):
```kotlin
    @Test
    fun `schema v3 is exported with the type and cue_voice defaults`() {
        val json = File("schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/3.json").readText()
        assertTrue(Regex("\"version\"\\s*:\\s*3").containsMatchIn(json))
        assertTrue(json.contains("`type` TEXT NOT NULL DEFAULT 'WORKOUT'"))
        assertTrue(json.contains("`cue_voice` INTEGER NOT NULL DEFAULT 0"))
    }

    @Test
    fun `the 2 to 3 migration SQL makes every row a workout with the voice off`() {
        // Runs everywhere (no file-based helper), so Windows also covers the §3.2 SQL.
        val raw = SQLiteDatabase.create(null)
        try {
            raw.execSQL(V2_ENTRY_TABLE)
            raw.execSQL(v2Row(1, holdEnabled = 1, total = "65"))
            raw.execSQL(v2Row(2, holdEnabled = 0, total = "NULL"))
            HiitDatabase.MIGRATION_2_3_SQL.forEach { raw.execSQL(it) }
            assertEquals(MIGRATED_V3_ROWS, raw.rawQuery(TYPE_QUERY, null).typeColumns())
        } finally {
            raw.close()
        }
    }

    @Test
    fun `migration 2 to 3 validates through MigrationTestHelper`() {
        // Same Windows guard as the checks above: androidx.sqlite 2.6.1 mishandles backslash paths. CI runs it.
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        helper.createDatabase(MIGRATION_DB_3, 2).use { db ->
            db.execSQL(v2Row(1, holdEnabled = 1, total = "65"))
            db.execSQL(v2Row(2, holdEnabled = 0, total = "NULL"))
        }
        helper.runMigrationsAndValidate(MIGRATION_DB_3, 3, true, HiitDatabase.MIGRATION_2_3).use { db ->
            assertEquals(MIGRATED_V3_ROWS, db.query(TYPE_QUERY).typeColumns())
        }
    }

    /** A v2 row with the default settings, the given hold switch and a raw SQL total ("65" or "NULL"). */
    private fun v2Row(id: Long, holdEnabled: Int, total: String) =
        "INSERT INTO entry (id, name, position, prepare_sec, sets, work_sec, rest_sec, cooldown_sec, starting_total, floor, cap, " +
            "hold_at, hold_for, hold_enabled, window_hours, penalty_hours_per_rep, cue_sound, cue_vibration, total, best_streak, " +
            "current_streak, hold_count, last_check_in) " +
            "VALUES ($id, 'Workout', ${id - 1}, 10, 8, 20, 10, 0, 48, 48, 72, 64, 4, $holdEnabled, 36, 19.5, 1, 1, $total, 0, 0, 0, NULL)"

    /** (id, type, cue_voice, hold_enabled, total) per row, ordered by id. */
    private fun Cursor.typeColumns(): List<List<Any?>> = use {
        buildList {
            while (moveToNext()) add(listOf(getLong(0), getString(1), getInt(2), getInt(3), if (isNull(4)) null else getInt(4)))
        }
    }
```
Then, in the `private companion object`, replace:
```kotlin
        const val HOLD_QUERY = "SELECT id, hold_enabled, hold_for FROM entry ORDER BY id"
```
with:
```kotlin
        const val HOLD_QUERY = "SELECT id, hold_enabled, hold_for FROM entry ORDER BY id"
        const val MIGRATION_DB_3 = "migration-2-3"
        const val TYPE_QUERY = "SELECT id, type, cue_voice, hold_enabled, total FROM entry ORDER BY id"

        /** Both v2 rows after 2 → 3: Workouts with the voice off, every other column kept. */
        val MIGRATED_V3_ROWS = listOf(listOf<Any?>(1L, "WORKOUT", 0, 1, 65), listOf<Any?>(2L, "WORKOUT", 0, 0, null))

        /** The v2 `entry` table exactly as 2.json creates it. */
        const val V2_ENTRY_TABLE = "CREATE TABLE IF NOT EXISTS `entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `position` INTEGER NOT NULL, `prepare_sec` INTEGER NOT NULL, `sets` INTEGER NOT NULL, " +
            "`work_sec` INTEGER NOT NULL, `rest_sec` INTEGER NOT NULL, `cooldown_sec` INTEGER NOT NULL, " +
            "`starting_total` INTEGER NOT NULL, `floor` INTEGER NOT NULL, `cap` INTEGER NOT NULL, `hold_at` INTEGER NOT NULL, " +
            "`hold_for` INTEGER NOT NULL, `hold_enabled` INTEGER NOT NULL DEFAULT 1, `window_hours` INTEGER NOT NULL, " +
            "`penalty_hours_per_rep` REAL NOT NULL, `cue_sound` INTEGER NOT NULL, `cue_vibration` INTEGER NOT NULL, " +
            "`total` INTEGER, `best_streak` INTEGER NOT NULL, `current_streak` INTEGER NOT NULL, " +
            "`hold_count` INTEGER NOT NULL, `last_check_in` INTEGER)"
```

- [ ] **Step 2: Run red** — Run (label `T3-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.db.HiitDatabaseTest"`. Expected: compile FAIL, "Unresolved reference 'MIGRATION_2_3_SQL'" and "'MIGRATION_2_3'".

- [ ] **Step 3: Implement**

`data/db/EntryEntity.kt`. Replace:
```kotlin
    val position: Int,
```
with:
```kotlin
    val position: Int,
    /**
     * WORKOUT or CHECK_IN (spec R4 §3.2), added in schema v3 as TEXT NOT NULL DEFAULT 'WORKOUT'.
     * A plain string: EntryMapping reads an unknown value as WORKOUT (plan Spec note 5).
     */
    @ColumnInfo(defaultValue = "WORKOUT") val type: String = "WORKOUT",
```
Then replace:
```kotlin
    @ColumnInfo(name = "cue_vibration") val cueVibration: Boolean,
```
with:
```kotlin
    @ColumnInfo(name = "cue_vibration") val cueVibration: Boolean,
    /** The Voice cue (spec R4 §3.2), added in schema v3 as INTEGER NOT NULL DEFAULT 0. */
    @ColumnInfo(name = "cue_voice", defaultValue = "0") val cueVoice: Boolean = false,
```
Room quotes a TEXT column's plain default in the generated SQL, so `defaultValue = "WORKOUT"` becomes `DEFAULT 'WORKOUT'`, matching the ALTER. The schema test above checks it, and the MigrationTestHelper test (CI) checks that the migrated table validates against the entity.

`data/db/HiitDatabase.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * `hiit.db` (spec §5.2). Version 2 adds `entry.hold_enabled` (R3 §5.2), version 3 adds
 * `entry.type` and `entry.cue_voice` (R4 §3.2). Schemas are exported to app/schemas and committed.
 * Every migration is registered in the builder (StorageModule), and there is no destructive fallback.
 */
@Database(entities = [EntryEntity::class, MetaEntity::class], version = 3, exportSchema = true)
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

        /** Spec R4 §3.2, verbatim: existing entries become Workouts with the voice off. */
        internal val MIGRATION_2_3_SQL = listOf(
            "ALTER TABLE entry ADD COLUMN type TEXT NOT NULL DEFAULT 'WORKOUT'",
            "ALTER TABLE entry ADD COLUMN cue_voice INTEGER NOT NULL DEFAULT 0",
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T3-1-GREEN`, ~4 min): full suite + count. Expected: **343 tests**, 3 skipped on Windows. Then:
```bash
git diff --exit-code app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/1.json \
  app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/2.json && echo "CLAUDE-OLD-SCHEMAS-UNCHANGED ok"
git status --short app/schemas   # must list ?? app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/3.json
grep -c '"cue_voice"' app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/3.json   # ≥ 1
```
If `1.json` or `2.json` changed, stop: the entity and version must change together, so an older schema should never be re-exported. If the schema test fails because the generated default reads differently from `DEFAULT 'WORKOUT'`, stop and report the `createSql` line from `3.json`; don't edit the ALTER, which is the spec's SQL.

- [ ] **Step 5: Commit** — diff-review `HiitDatabase.kt`, then:
```bash
git add -A && git commit -m "feat(db): schema v3 with entry.type and entry.cue_voice"
```
`git show --stat HEAD` must list `3.json`.

### Subtask 3.2: `type` ⇄ `EntryType` with read repair, `cue_voice` ⇄ `voice`, and the v1 import

**Files:** Edit `data/EntryMapping.kt`; append to `test/data/EntryMappingTest.kt`, `test/data/v1/V1MigratorTest.kt`.

- [ ] **Step 1: Failing tests**

`EntryMappingTest`: add the import `com.mitenko.hiitcounter.domain.model.EntryType`, then append:
```kotlin
    @Test
    fun `type and voice map to the domain and back`() {
        val entry = testEntity().copy(type = "CHECK_IN", cueVoice = true).toDomain()
        assertEquals(EntryType.CHECK_IN, entry.type)
        assertEquals(CueConfig(voice = true), entry.cues)
        val row = entryEntity("Stretch", 0, cues = CueConfig(voice = true), type = EntryType.CHECK_IN)
        assertEquals("CHECK_IN", row.type)
        assertTrue(row.cueVoice)
        assertEquals("WORKOUT", entryEntity("Burpees", 0).type)
        assertFalse(entryEntity("Burpees", 0).cueVoice)
    }

    @Test
    fun `an unknown type reads as a workout`() {
        assertEquals(EntryType.WORKOUT, testEntity().copy(type = "HABIT").toDomain().type)
        assertEquals(EntryType.WORKOUT, testEntity().copy(type = "").toDomain().type)
        assertEquals(EntryType.WORKOUT, testEntity().copy(type = "check_in").toDomain().type)
    }
```

`V1MigratorTest` (the imports are already there), append:
```kotlin
    @Test
    fun `v1 data imports as a workout with the voice off`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 5 }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertEquals("WORKOUT", row.type)
        assertFalse(row.cueVoice)
    }
```

- [ ] **Step 2: Run red** — Run (label `T3-2-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.EntryMappingTest" --tests "com.mitenko.hiitcounter.data.v1.V1MigratorTest"`. Expected: compile FAIL, "No parameter with name 'type' found" (on `entryEntity`). Once it compiles, `v1 data imports as a workout…` already passes: `entryEntity` leaves the entity defaults, which are WORKOUT and off. It's a regression guard for §3.2's V1Migrator rule.

- [ ] **Step 3: Implement** — `data/EntryMapping.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.domain.model.Entry
```
with:
```kotlin
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryType
```
Then replace:
```kotlin
/** Spec §5.1 invariant: a NULL total is resolved to the (repaired) starting total, so the UI never sees null. */
```
with:
```kotlin
/** Read repair (spec R4 §3.2): an unknown type string is logged and reads as a Workout. */
internal fun EntryEntity.entryType(): EntryType =
    EntryType.entries.firstOrNull { it.name == type }
        ?: EntryType.WORKOUT.also { Log.w(TAG, "Entry $id: unknown type=$type; reading as WORKOUT") }

/** Spec §5.1 invariant: a NULL total is resolved to the (repaired) starting total, so the UI never sees null. */
```
Then replace:
```kotlin
        cues = CueConfig(cueSound, cueVibration),
        counter = counter(progression.startingTotal),
    )
```
with:
```kotlin
        cues = CueConfig(cueSound, cueVibration, cueVoice),
        counter = counter(progression.startingTotal),
        type = entryType(),
    )
```
Then replace:
```kotlin
    counter: StoredCounter = StoredCounter(),
): EntryEntity = EntryEntity(
    name = name,
    position = position,
```
with:
```kotlin
    counter: StoredCounter = StoredCounter(),
    type: EntryType = EntryType.WORKOUT,
): EntryEntity = EntryEntity(
    name = name,
    position = position,
    type = type.name,
```
Then replace:
```kotlin
    cueVibration = cues.vibration,
```
with:
```kotlin
    cueVibration = cues.vibration,
    cueVoice = cues.voice,
```
`V1Readers.v1Entry` needs no change: it doesn't pass `type`, so the import is a WORKOUT, and `readV1Cues` leaves `voice` false.

- [ ] **Step 4: Run green** — Run (label `T3-2-GREEN`, ~3 min): full suite + count. Expected: **346 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): map the entry type with read repair and the voice cue"`

### Subtask 3.3: `create(name, type)`, `setType`, `setCues`, `checkIn`, duplicate and the fake repository

**Files:** Edit `data/db/EntryDao.kt`, `data/EntryRepository.kt`; replace `test/testutil/FakeEntryRepository.kt`; append to `test/data/RoomEntryRepositoryTest.kt`.

- [ ] **Step 1: Failing tests** — `RoomEntryRepositoryTest`: add the import `com.mitenko.hiitcounter.domain.model.EntryType`, then append:
```kotlin
    @Test
    fun `create stores the chosen type and create(name) makes a workout`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Stretch", EntryType.CHECK_IN)
        assertEquals(EntryType.WORKOUT, r.entry(a).first()!!.type)
        assertEquals(EntryType.CHECK_IN, r.entry(b).first()!!.type)
        assertEquals("CHECK_IN", db.entryDao().get(b)!!.type)
    }

    @Test
    fun `setType changes only the type, and switching back restores everything`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setTiming(a, TimingConfig(sets = 6))
        r.setProgression(a, ProgressionConfig(cap = 80, hold = false))
        r.setCues(a, CueConfig(sound = false, voice = true))
        r.overwriteCounter(a, 65, 24, 4, clock.instant.minusSeconds(60))
        val before = db.entryDao().get(a)!!
        r.setType(a, EntryType.CHECK_IN)
        assertEquals(before.copy(type = "CHECK_IN"), db.entryDao().get(a)!!)
        r.setType(a, EntryType.WORKOUT)
        assertEquals(before, db.entryDao().get(a)!!)
    }

    @Test
    fun `setType waits for the migration gate and throws EntryNotFound for a missing id`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val r = repo(object : MigrationGate {
            override suspend fun awaitReady() = gate.await()
        })
        val write = async { runCatching { r.setType(99, EntryType.CHECK_IN) } }
        runCurrent()
        assertFalse(write.isCompleted)
        gate.complete(Unit)
        assertTrue(write.await().exceptionOrNull() is EntryNotFound)
    }

    @Test
    fun `setCues writes the voice`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setCues(a, CueConfig(voice = true))
        assertEquals(CueConfig(voice = true), r.entry(a).first()!!.cues)
        assertTrue(db.entryDao().get(a)!!.cueVoice)
    }

    @Test
    fun `duplicate copies the type and the voice`() = runTest {
        val r = repo()
        val a = r.create("Stretch", EntryType.CHECK_IN)
        r.setCues(a, CueConfig(voice = true))
        val copy = r.entry(r.duplicate(a)).first()!!
        assertEquals(EntryType.CHECK_IN, copy.type)
        assertTrue(copy.cues.voice)
    }

    @Test
    fun `a check-in-only check-in moves the day and streaks and never touches the total`() = runTest {
        val r = repo()
        val a = r.create("Stretch", EntryType.CHECK_IN)
        db.entryDao().setCounter(a, total = null, bestStreak = 0, currentStreak = 0, holdCount = 2, lastCheckIn = null)
        assertEquals(Outcome.First, r.checkIn(a, clock).outcome)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(24 * 3600)
        assertEquals(Outcome.OnTime, r.checkIn(a, clock).outcome)
        val row = db.entryDao().get(a)!!
        assertNull(row.total)
        assertEquals(2, row.currentStreak)
        assertEquals(2, row.bestStreak)
        assertEquals(2, row.holdCount)
        assertEquals(clock.instant.toEpochMilli(), row.lastCheckIn)
        assertEquals(48, r.entry(a).first()!!.counter.total)
    }
```

- [ ] **Step 2: Run red** — Run (label `T3-3-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.RoomEntryRepositoryTest"`. Expected: compile FAIL, "Too many arguments for 'create'" and "Unresolved reference 'setType'".

- [ ] **Step 3: Implement**

`data/db/EntryDao.kt`. Replace:
```kotlin
    @Query("UPDATE entry SET cue_sound = :sound, cue_vibration = :vibration WHERE id = :id")
    suspend fun setCues(id: Long, sound: Boolean, vibration: Boolean): Int
```
with:
```kotlin
    @Query("UPDATE entry SET cue_sound = :sound, cue_vibration = :vibration, cue_voice = :voice WHERE id = :id")
    suspend fun setCues(id: Long, sound: Boolean, vibration: Boolean, voice: Boolean): Int

    /** Spec R4 §3.3: the type alone. Every other column is kept, so switching back restores everything. */
    @Query("UPDATE entry SET type = :type WHERE id = :id")
    suspend fun setType(id: Long, type: String): Int
```

`data/EntryRepository.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.domain.model.EntryNotFound
```
with:
```kotlin
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
```
Then, in the interface, replace:
```kotlin
    /** A new entry with default settings, appended at the end. */
    suspend fun create(name: String): Long
```
with:
```kotlin
    /** A new entry with default settings, appended at the end. `create(name)` makes a Workout (spec R4 §4.4). */
    suspend fun create(name: String, type: EntryType = EntryType.WORKOUT): Long
```
Then replace:
```kotlin
    suspend fun setCues(id: Long, cues: CueConfig)

    /** One transaction using the row's own progression: concurrent calls on one entry record exactly one check-in. */
    suspend fun checkIn(id: Long, clock: Clock): CheckInResult
```
with:
```kotlin
    suspend fun setCues(id: Long, cues: CueConfig)

    /** Spec R4 §3.3: one UPDATE of the type; counter, timing, progression and cues are untouched. */
    suspend fun setType(id: Long, type: EntryType)

    /**
     * One transaction using the row's own progression and type: concurrent calls on one entry record
     * exactly one check-in. A check-in-only entry keeps its total and hold count (spec R4 §3.1).
     */
    suspend fun checkIn(id: Long, clock: Clock): CheckInResult
```
Then replace:
```kotlin
    override suspend fun create(name: String): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count())) }
    }
```
with:
```kotlin
    override suspend fun create(name: String, type: EntryType): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count(), type = type)) }
    }
```
Then replace:
```kotlin
                    progression = source.progression,
                    cues = source.cues,
                ),
```
with:
```kotlin
                    progression = source.progression,
                    cues = source.cues,
                    type = source.type,
                ),
```
Then replace:
```kotlin
    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration))
    }
```
with:
```kotlin
    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration, cues.voice))
    }

    override suspend fun setType(id: Long, type: EntryType) {
        gate.awaitReady()
        found(id, dao.setType(id, type.name))
    }
```
Then replace:
```kotlin
            // The row's progression is the single source of truth (spec §5.3).
            val entry = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            val now = clock.now()
            entry.counter.lastCheckIn?.let { last ->
                if (now.isBefore(last)) Log.w(TAG, "Clock moved backwards: now=$now last=$last")
            }
            val result = RepProgression.checkIn(entry.counter, entry.progression, now, clock.zone())
            if (result.outcome != Outcome.AlreadyToday) {
                val s = result.state
                dao.setCounter(id, s.total, s.bestStreak, s.currentStreak, s.holdCount, s.lastCheckIn?.toEpochMilli())
            }
            result
```
with:
```kotlin
            // The row's progression and type are the single source of truth (spec §5.3, R4 §3.3).
            val row = dao.get(id) ?: throw EntryNotFound(id)
            val entry = row.toDomain()
            val now = clock.now()
            entry.counter.lastCheckIn?.let { last ->
                if (now.isBefore(last)) Log.w(TAG, "Clock moved backwards: now=$now last=$last")
            }
            val countsReps = entry.type == EntryType.WORKOUT
            val result = RepProgression.checkIn(entry.counter, entry.progression, now, clock.zone(), countsReps)
            if (result.outcome != Outcome.AlreadyToday) {
                val s = result.state
                // A check-in-only entry never touches its total column, so a NULL total stays NULL (plan Spec note 6).
                val total = if (countsReps) s.total else row.total
                dao.setCounter(id, total, s.bestStreak, s.currentStreak, s.holdCount, s.lastCheckIn?.toEpochMilli())
            }
            result
```

`test/testutil/FakeEntryRepository.kt` (complete):
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
import com.mitenko.hiitcounter.domain.model.EntryType
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
 * IllegalArgumentException, positions stay contiguous, the hold count follows the R3 §6.3 rules,
 * and a check-in-only entry's check-in keeps its total (R4 §3.1). Settings validation is left to
 * the ViewModels under test. The write counters, [writeError] and [checkInGate] let the tests
 * count, fail and hold individual calls.
 */
class FakeEntryRepository(initial: List<Entry> = emptyList(), ready: Boolean = true) : EntryRepository {
    val readiness = CompletableDeferred<Unit>().apply { if (ready) complete(Unit) }
    val state = MutableStateFlow(initial.sortedWith(compareBy<Entry>({ it.position }, { it.id })))
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    var checkInCalls = 0
    var checkInError: Throwable? = null

    /** When set, checkIn suspends on it after counting the call, so a test can hold a check-in in flight. */
    var checkInGate: CompletableDeferred<Unit>? = null
    val moves = mutableListOf<Pair<Long, Int>>()
    var deleteCalls = 0

    /** setTiming / setProgression / overwriteCounter / setType calls so far, including failed ones. */
    var timingWrites = 0
    var progressionWrites = 0
    var counterWrites = 0
    var typeWrites = 0

    /** Thrown once by the next setTiming, setProgression, overwriteCounter or setType (a repository-side rejection). */
    var writeError: Throwable? = null

    override val entries: Flow<List<Entry>> = flow {
        readiness.await()
        emitAll(state)
    }

    override fun entry(id: Long): Flow<Entry?> = flow {
        readiness.await()
        emitAll(state.map { list -> list.firstOrNull { it.id == id } })
    }

    override suspend fun create(name: String, type: EntryType): Long {
        val valid = validName(name)
        readiness.await()
        val id = nextId++
        val p = ProgressionConfig()
        state.update {
            it + Entry(id, valid, it.size, TimingConfig(), p, CueConfig(), CounterState(total = p.startingTotal), type)
        }
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = validName(name)
        edit(id) { it.copy(name = valid) }
    }

    /** Copies the config, the type and the cues (voice included) with a fresh counter, as Room does. */
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

    override suspend fun setType(id: Long, type: EntryType) {
        typeWrites++
        failIfAsked()
        edit(id) { it.copy(type = type) }
    }

    override suspend fun checkIn(id: Long, clock: Clock): CheckInResult {
        readiness.await()
        checkInCalls++
        checkInGate?.await()
        checkInError?.let { throw it }
        val e = find(id)
        val result = RepProgression.checkIn(
            e.counter, e.progression, clock.now(), clock.zone(), countsReps = e.type == EntryType.WORKOUT,
        )
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

    /** Room stores a NULL total, which resolves to startingTotal; the fake stores startingTotal directly. */
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

- [ ] **Step 4: Run green** — Run (label `T3-3-GREEN`, ~4 min): full suite + count. Expected: **352 tests**, 3 skipped on Windows.

- [ ] **Step 5: Commit** — diff-review `FakeEntryRepository.kt` (new: `create`'s type, `setType`, `typeWrites`, `checkInGate`, `countsReps`; everything else unchanged), then `git add -A && git commit -m "feat(data): create with a type, setType, voice cues and streak-only check-ins"`

**Task 3 gate:** Run (label `T3-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **352 tests** (3 skipped on Windows), no lint errors. `assembleDebug` compiles the Room DAO with the new queries and the Hilt graph.

---

## Task 4: The Voice cue (§5)

**Interfaces produced:**
- `platform/CueSpeaker`: `val available: StateFlow<Boolean>`, `suspend fun speak(number: Int)` (returns when the utterance ends), `fun shutdown()`.
- `service/CueFocus` (`request(): Boolean`, `abandon()`) and `AndroidCueFocus(context, attributes)`.
- `CuePlayer(context, scope, focusOverride: CueFocus? = null)` with `var speaker: CueSpeaker?` and `CuePlayer.SPEECH_TIMEOUT_MS = 3_000L`.
- `platform/AndroidCueSpeaker(context)` with `ready: CompletableDeferred<Boolean>`.
- `platform/VoiceAvailability` (`suspend fun check(): Boolean`) and `AndroidVoiceAvailability(context)`, bound in `AppModule`.
- `domain/VoicePolicy.speakerWanted(status, snapshot)`.
- `test/testutil/FakeCueSpeaker`.

### Subtask 4.1: `CueSpeaker`, `CueFocus` and a `CuePlayer` that says the reps

**Files:** Create `platform/CueSpeaker.kt`, `service/CueFocus.kt`, `test/testutil/FakeCueSpeaker.kt`, `test/service/CuePlayerTest.kt`; replace `service/CuePlayer.kt`.

- [ ] **Step 1: Failing tests**

`test/testutil/FakeCueSpeaker.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.platform.CueSpeaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** A [CueSpeaker] that records what it was asked to say. [speak] suspends until [finishUtterance], like an engine still talking. */
class FakeCueSpeaker(available: Boolean = true) : CueSpeaker {
    override val available = MutableStateFlow(available)
    val spoken = mutableListOf<Int>()
    var shutdowns = 0
    private var utterance: CompletableDeferred<Unit>? = null

    override suspend fun speak(number: Int) {
        spoken += number
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

`test/service/CuePlayerTest.kt` (complete). `CuePlayer` builds a `SoundPool` and a `Vibrator` from the context, so it runs on Robolectric; the fake focus keeps the audio-focus assertions off Robolectric's shadows (plan Spec note 16). Its coroutines live in `backgroundScope`, so every test advances with `advanceTimeBy` + `runCurrent`, never `advanceUntilIdle`.
```kotlin
package com.mitenko.hiitcounter.service

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.CuePatterns
import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.platform.CueSpeaker
import com.mitenko.hiitcounter.testutil.FakeCueSpeaker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CuePlayerTest {
    private class FakeFocus : CueFocus {
        var requests = 0
        var abandons = 0

        override fun request(): Boolean {
            requests++
            return true
        }

        override fun abandon() {
            abandons++
        }
    }

    private val focus = FakeFocus()

    /** Beep and voice, no vibration (the vibrator isn't under test). */
    private val voiceOn = CueConfig(vibration = false, voice = true)

    /** Voice only: no beep, so speech starts at once. */
    private val voiceOnly = CueConfig(sound = false, vibration = false, voice = true)

    private fun TestScope.player(speaker: CueSpeaker?) =
        CuePlayer(ApplicationProvider.getApplicationContext(), backgroundScope, focus).also { it.speaker = speaker }

    @Test
    fun `a work start with the voice on says its reps after the beep`() = runTest {
        val speaker = FakeCueSpeaker()
        player(speaker).play(Cue.PhaseStart(Phase.WORK, reps = 12), voiceOn)
        runCurrent()
        assertTrue(speaker.spoken.isEmpty()) // the 600 ms WORK tone plays first
        advanceTimeBy(CuePatterns.LONG_MS)
        runCurrent()
        assertEquals(listOf(12), speaker.spoken)
    }

    @Test
    fun `the voice speaks only at a work start with the voice switched on`() = runTest {
        val speaker = FakeCueSpeaker()
        val p = player(speaker)
        p.play(Cue.PhaseStart(Phase.REST), voiceOnly)
        p.play(Cue.PhaseStart(Phase.COOLDOWN), voiceOnly)
        p.play(Cue.Countdown(3), voiceOnly)
        p.play(Cue.Finished, voiceOnly)
        p.play(Cue.PhaseStart(Phase.WORK, reps = 9), voiceOnly.copy(voice = false))
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(speaker.spoken.isEmpty())
        p.play(Cue.PhaseStart(Phase.WORK, reps = 9), voiceOnly)
        runCurrent()
        assertEquals(listOf(9), speaker.spoken)
    }

    @Test
    fun `an unavailable or missing speaker never speaks and takes no focus`() = runTest {
        val unavailable = FakeCueSpeaker(available = false)
        player(unavailable).play(Cue.PhaseStart(Phase.WORK, reps = 9), voiceOnly)
        player(null).play(Cue.PhaseStart(Phase.WORK, reps = 9), voiceOnly)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(unavailable.spoken.isEmpty())
        assertEquals(0, focus.requests)
    }

    @Test
    fun `focus is held through the utterance and released when it ends`() = runTest {
        val speaker = FakeCueSpeaker()
        player(speaker).play(Cue.PhaseStart(Phase.WORK, reps = 12), voiceOn)
        advanceTimeBy(CuePatterns.LONG_MS + 500) // the tone and its 100 ms hold are over; the voice is still talking
        runCurrent()
        assertEquals(1, focus.requests)
        assertEquals(0, focus.abandons)
        speaker.finishUtterance()
        runCurrent()
        assertEquals(1, focus.abandons)
    }

    @Test
    fun `focus is released after 3 s if the utterance never ends`() = runTest {
        player(FakeCueSpeaker()).play(Cue.PhaseStart(Phase.WORK, reps = 12), voiceOnly)
        advanceTimeBy(CuePlayer.SPEECH_TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(0, focus.abandons)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, focus.abandons)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T4-1-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.service.CuePlayerTest"`. Expected: compile FAIL, "Unresolved reference 'CueSpeaker'", "'CueFocus'", "'speaker'" and "'SPEECH_TIMEOUT_MS'".

- [ ] **Step 3: Implement**

`platform/CueSpeaker.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Says a rep count (spec R4 §5). [available] is false until the engine is ready with an English
 * voice, and stays false if it never is; nothing is said then. [speak] replaces any utterance in
 * progress and suspends until this one ends: done, failed or replaced (plan Spec note 2).
 * Main thread only.
 */
interface CueSpeaker {
    val available: StateFlow<Boolean>

    suspend fun speak(number: Int)

    fun shutdown()
}
```

`service/CueFocus.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** The cues' audio focus (spec §8, R4 §5): one transient, ducking request shared by the beeps and the voice. */
interface CueFocus {
    /** True when focus was granted; the caller plays nothing otherwise. */
    fun request(): Boolean

    fun abandon()
}

class AndroidCueFocus(context: Context, attributes: AudioAttributes) : CueFocus {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    override fun request(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
```

`service/CuePlayer.kt` (complete). The tone scheduling and vibration are unchanged; the focus request moved into `CueFocus`, and the voice shares it:
```kotlin
package com.mitenko.hiitcounter.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.CuePattern
import com.mitenko.hiitcounter.domain.CuePatterns
import com.mitenko.hiitcounter.domain.Tone
import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.platform.CueSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays cue sounds, the voice and vibrations — spec §8, R4 §5. Main thread only.
 *
 * The beeps and the voice share one transient ducking focus request, so music is lowered while
 * either plays. Focus is abandoned once the last tone's hold has ended **and** the utterance has
 * ended, or [SPEECH_TIMEOUT_MS] after speech started. Speech never blocks the caller: it runs in
 * [scope], so the engine's timing is untouched.
 */
class CuePlayer(
    context: Context,
    private val scope: CoroutineScope,
    focusOverride: CueFocus? = null,
) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
    private val loaded = mutableSetOf<Int>()
    private val soundIds: Map<Tone, Int>
    private val focus: CueFocus = focusOverride ?: AndroidCueFocus(context, attributes)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private var tonesJob: Job? = null
    private var speechJob: Job? = null
    private var tonesHold = false
    private var speechHold = false

    /** The run's speaker (spec R4 §5). TimerService sets it only for a voice run; null means no voice. */
    var speaker: CueSpeaker? = null

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        soundIds = mapOf(
            Tone.SHORT to soundPool.load(context, R.raw.tone_short, 1),
            Tone.LONG to soundPool.load(context, R.raw.tone_long, 1),
        )
    }

    fun play(cue: Cue, config: CueConfig) {
        val pattern = CuePatterns.forCue(cue)
        if (config.vibration) pattern.vibration?.let(::vibrate)
        val tones = config.sound && pattern.tones.isNotEmpty()
        val reps = spokenReps(cue, config)
        if (!tones && reps == null) return
        if (!focus.request()) {
            Log.w(TAG, "Audio focus denied; skipping sound")
            return
        }
        if (tones) playTones(pattern)
        // The number follows the beep, so the two never overlap (plan Spec note 3).
        if (reps != null) say(reps, afterMs = if (tones) pattern.durationMs else 0L)
    }

    /** The reps to say: only at a WORK start, with the voice on and a speaker that is ready (spec R4 §5). */
    private fun spokenReps(cue: Cue, config: CueConfig): Int? {
        if (!config.voice || cue !is Cue.PhaseStart || cue.phase != Phase.WORK) return null
        if (speaker?.available?.value != true) return null
        return cue.reps
    }

    private fun playTones(pattern: CuePattern) {
        scope.launch {
            var t = 0L
            for (tone in pattern.tones) {
                delay(tone.atMs - t)
                t = tone.atMs
                val id = soundIds.getValue(tone.tone)
                if (id in loaded) soundPool.play(id, 1f, 1f, 1, 0, 1f) else Log.w(TAG, "Sound $tone not loaded yet; skipped")
            }
        }
        tonesHold = true
        tonesJob?.cancel()
        tonesJob = scope.launch {
            delay(pattern.durationMs + 100)
            tonesHold = false
            abandonIfIdle()
        }
    }

    private fun say(reps: Int, afterMs: Long) {
        val s = speaker ?: return
        speechHold = true
        speechJob?.cancel()
        speechJob = scope.launch {
            delay(afterMs)
            withTimeoutOrNull(SPEECH_TIMEOUT_MS) { s.speak(reps) }
            speechHold = false
            abandonIfIdle()
        }
    }

    private fun abandonIfIdle() {
        if (!tonesHold && !speechHold) focus.abandon()
    }

    private fun vibrate(timings: List<Long>) {
        vibrator?.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), -1))
    }

    fun release() {
        tonesJob?.cancel()
        speechJob?.cancel()
        tonesHold = false
        speechHold = false
        focus.abandon()
        soundPool.release()
    }

    companion object {
        /** Focus is released at the latest this long after speech starts (spec R4 §5). */
        const val SPEECH_TIMEOUT_MS = 3_000L
        private const val TAG = "CuePlayer"
    }
}
```
`TimerService` still calls `CuePlayer(this, scope)`, which now uses `AndroidCueFocus`. Nothing sets `speaker` yet, so the app's behaviour is unchanged until 4.2.

- [ ] **Step 4: Run green** — Run (label `T4-1-GREEN`, ~4 min): full suite + count. Expected: **357 tests**.

- [ ] **Step 5: Commit** — diff-review `CuePlayer.kt` against `main`: the tone loop, the vibrator and `release()`'s `soundPool.release()` are unchanged; the focus request is the same `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with the same attributes, now in `AndroidCueFocus`. Then `git add -A && git commit -m "feat(service): CuePlayer says each set's reps in the cues' focus"`

### Subtask 4.2: `AndroidCueSpeaker`, `VoiceAvailability`, `VoicePolicy` and the `TimerService` lifecycle

`AndroidCueSpeaker`, `AndroidVoiceAvailability` and `TimerService` wrap platform services and have no unit tests (as before for `TimerService`). The lifecycle rule they follow is the pure `VoicePolicy`, which is tested. The device check (9.2) covers the engine.

**Files:** Create `domain/VoicePolicy.kt`, `platform/AndroidCueSpeaker.kt`, `platform/VoiceAvailability.kt`, `test/domain/VoicePolicyTest.kt`; edit `service/TimerService.kt`, `di/AppModule.kt`, `app/src/main/AndroidManifest.xml`.

- [ ] **Step 1: Failing test** — `test/domain/VoicePolicyTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePolicyTest {
    @Test
    fun `a speaker is wanted only while a voice run prepares or runs`() {
        val voice = WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig(voice = true))
        val silent = voice.copy(cues = CueConfig())
        assertTrue(VoicePolicy.speakerWanted(RunStatus.PREPARING, voice))
        assertTrue(VoicePolicy.speakerWanted(RunStatus.RUNNING, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.DONE, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.IDLE, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.RUNNING, silent))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.PREPARING, null))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T4-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.VoicePolicyTest"`. Expected: compile FAIL, "Unresolved reference 'VoicePolicy'".

- [ ] **Step 3: Implement**

`domain/VoicePolicy.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

/**
 * Spec R4 §5 lifecycle: TimerService holds a speaker only while a run whose frozen snapshot has
 * the voice on is preparing or running. DONE and IDLE shut it down (the Finished cue says nothing).
 */
object VoicePolicy {
    fun speakerWanted(status: RunStatus, snapshot: WorkoutSnapshot?): Boolean =
        (status == RunStatus.PREPARING || status == RunStatus.RUNNING) && snapshot?.cues?.voice == true
}
```

`platform/AndroidCueSpeaker.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.platform

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * [CueSpeaker] over the platform TextToSpeech (spec R4 §5): `Locale.ENGLISH`,
 * `USAGE_ASSISTANCE_SONIFICATION`, `QUEUE_FLUSH`, and the number passed as its digits ("12"),
 * which the engine says as a word. An init failure or a missing English voice leaves [available]
 * false, and the run continues silently. Create, speak and shut down on Main.
 */
class AndroidCueSpeaker(context: Context) : CueSpeaker {
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    /** Completes with the outcome of initialisation: true once an English voice is ready, false otherwise. */
    val ready = CompletableDeferred<Boolean>()

    /** Utterances still talking. The progress listener runs on a binder thread, hence the concurrent map. */
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private var engine: TextToSpeech? = null
    private var earlyStatus: Int? = null
    private var shutDown = false
    private var nextId = 0

    init {
        val tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
        engine = tts
        // TextToSpeech reports ERROR from inside its constructor when no engine is installed.
        earlyStatus?.let { onInit(it) }
    }

    private fun onInit(status: Int) {
        val tts = engine
        if (tts == null) {
            earlyStatus = status
            return
        }
        if (shutDown || ready.isCompleted) return
        val usable = status == TextToSpeech.SUCCESS && configure(tts)
        if (!usable) Log.w(TAG, "Text-to-speech unavailable (status $status); the voice cue stays silent")
        _available.value = usable
        ready.complete(usable)
    }

    private fun configure(tts: TextToSpeech): Boolean {
        val language = tts.setLanguage(Locale.ENGLISH)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) return false
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
        return true
    }

    override suspend fun speak(number: Int) {
        val tts = engine
        if (tts == null || !_available.value) return
        val id = "rep-${nextId++}"
        suspendCancellableCoroutine<Unit> { cont ->
            pending[id] = cont
            cont.invokeOnCancellation { pending.remove(id) }
            // QUEUE_FLUSH stops the previous utterance, whose onStop resumes its caller.
            if (tts.speak(number.toString(), TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                pending.remove(id)?.resume(Unit)
            }
        }
    }

    private fun finish(utteranceId: String?) {
        pending.remove(utteranceId ?: return)?.resume(Unit)
    }

    override fun shutdown() {
        if (shutDown) return
        shutDown = true
        _available.value = false
        ready.complete(false)
        engine?.stop()
        engine?.shutdown()
        // remove() hands each continuation to exactly one resumer, even if onStop races this drain.
        pending.keys.toList().forEach { id -> pending.remove(id)?.resume(Unit) }
    }

    private companion object {
        const val TAG = "AndroidCueSpeaker"
    }
}
```

`platform/VoiceAvailability.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.platform

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether this device can say the Voice cue (spec R4 §4.7): a text-to-speech engine that
 * initialises with an English voice. Used by the Cues page, outside any run (plan Spec note 4).
 */
interface VoiceAvailability {
    suspend fun check(): Boolean
}

/** Initialises a throwaway [AndroidCueSpeaker], waits for it and shuts it down. Call on Main. */
class AndroidVoiceAvailability(private val context: Context) : VoiceAvailability {
    override suspend fun check(): Boolean {
        val speaker = AndroidCueSpeaker(context)
        return try {
            withTimeoutOrNull(INIT_TIMEOUT_MS) { speaker.ready.await() } ?: false
        } finally {
            speaker.shutdown()
        }
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 5_000L
    }
}
```

`di/AppModule.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.platform.AndroidClock
```
with:
```kotlin
import com.mitenko.hiitcounter.platform.AndroidClock
import com.mitenko.hiitcounter.platform.AndroidVoiceAvailability
import com.mitenko.hiitcounter.platform.VoiceAvailability
```
Then replace:
```kotlin
    @Provides @Singleton
    fun workoutServiceStarter(@ApplicationContext context: Context): WorkoutServiceStarter =
        AndroidWorkoutServiceStarter(context)
}
```
with:
```kotlin
    @Provides @Singleton
    fun workoutServiceStarter(@ApplicationContext context: Context): WorkoutServiceStarter =
        AndroidWorkoutServiceStarter(context)

    /** The Cues page's device check (spec R4 §4.7). */
    @Provides @Singleton
    fun voiceAvailability(@ApplicationContext context: Context): VoiceAvailability = AndroidVoiceAvailability(context)
}
```

`app/src/main/AndroidManifest.xml`. Replace:
```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
```
with:
```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Android 11+ package visibility: TextToSpeech can only bind to an engine it can see (plan Spec note 13). -->
    <queries>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>

    <application
```

`service/TimerService.kt`. Every v1/R2 behaviour stays as it is; the speaker is added beside it. Replace:
```kotlin
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WakeLockPolicy
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
```
with:
```kotlin
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.VoicePolicy
import com.mitenko.hiitcounter.domain.WakeLockPolicy
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.platform.AndroidCueSpeaker
import com.mitenko.hiitcounter.platform.CueSpeaker
```
Then replace:
```kotlin
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false
```
with:
```kotlin
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    /** The run's voice (spec R4 §5): created for a voice run, shut down when it ends or the service stops. */
    private var speaker: CueSpeaker? = null
```
Then replace:
```kotlin
            controller.status.collectLatest { status ->
                if (status == RunStatus.IDLE || status == RunStatus.DONE) {
```
with:
```kotlin
            controller.status.collectLatest { status ->
                // Spec R4 §5: before any delay below, so DONE and IDLE shut the speaker down at once.
                syncSpeaker(VoicePolicy.speakerWanted(status, controller.snapshot))
                if (status == RunStatus.IDLE || status == RunStatus.DONE) {
```
Then replace:
```kotlin
    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
```
with:
```kotlin
    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * Creates the speaker for a voice run (the snapshot is set before PREPARING is emitted, so it is
     * frozen here) and shuts it down otherwise. Initialisation is asynchronous; until it succeeds
     * the speaker reports unavailable and CuePlayer says nothing.
     */
    private fun syncSpeaker(wanted: Boolean) {
        if (wanted && speaker == null) {
            speaker = AndroidCueSpeaker(this).also { cuePlayer.speaker = it }
        } else if (!wanted) {
            shutdownSpeaker()
        }
    }

    private fun shutdownSpeaker() {
        cuePlayer.speaker = null
        speaker?.shutdown()
        speaker = null
    }
```
Then replace:
```kotlin
        releaseWakeLock()
        cuePlayer.release()
        scope.cancel()
```
with:
```kotlin
        releaseWakeLock()
        shutdownSpeaker()
        cuePlayer.release()
        scope.cancel()
```

- [ ] **Step 4: Run green** — Run (label `T4-2-GREEN`, ~4 min): full suite + count. Expected: **358 tests**.

- [ ] **Step 5: Commit** — diff-review `TimerService.kt`: the only changes are the imports, the `speaker` field, the `syncSpeaker` line at the top of the status collector, `syncSpeaker`/`shutdownSpeaker`, and `shutdownSpeaker()` in `onDestroy`. The wake-lock, `startForeground` and DONE-grace code is byte-identical. Then `git add -A && git commit -m "feat(service): text-to-speech voice for voice runs"`

**Task 4 gate:** Run (label `T4-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **358 tests**, no lint errors. `assembleDebug` compiles the Hilt graph with the `VoiceAvailability` binding and merges the `<queries>` element.

---

## Task 5: The Entry screen: Check in, Start and check-in-only entries (§4.1, §4.2, §6)

**Interfaces produced:**
- `EntryUiState.type: EntryType = WORKOUT` and `EntryUiState.checkingIn: Boolean = false`.
- `EntryViewModel.onCheckIn()`: guarded while a check-in or a start is in flight, `NonCancellable` repository call, `EntryNotFound` → `markMissing()`, other failures → `error`.
- `EntryScreen(state, onBack, onStart, onCheckIn, onOpenSettings, onDismissError)`, with test tags `check_in` and `start`.
- The R4 §6 strings: `check_in`, `checked_in`, `type`, `type_workout`, `type_check_in`, `streak_n`, `voice`, `voice_unavailable`, `info_type`, `info_voice`.

### Subtask 5.1: `EntryViewModel.onCheckIn` and the type in the state

**Files:** Replace `ui/entry/EntryViewModel.kt`; append to `test/ui/entry/EntryViewModelTest.kt`.

- [ ] **Step 1: Failing tests** — `EntryViewModelTest`: add the imports `com.mitenko.hiitcounter.domain.model.EntryType` and `kotlinx.coroutines.CompletableDeferred`, then append:
```kotlin
    @Test
    fun `check in updates the counter and then reads as checked in today`() = runTest {
        val h = harness()
        runCurrent()
        h.vm.onCheckIn()
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertEquals(66, h.vm.uiState.value.total)
        assertTrue(h.vm.uiState.value.checkedInToday)
        assertFalse(h.vm.uiState.value.checkingIn)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
    }

    @Test
    fun `check in ignores taps on either button while its call is in flight`() = runTest {
        val h = harness()
        val gate = CompletableDeferred<Unit>()
        repo.checkInGate = gate
        h.vm.onCheckIn()
        h.vm.onCheckIn()
        h.vm.onStart()
        runCurrent()
        assertTrue(h.vm.uiState.value.checkingIn)
        assertEquals(0, h.starter.calls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertFalse(h.vm.uiState.value.checkingIn)
        assertEquals(66, repo.find(1).counter.total)
    }

    @Test
    fun `start after a check-in starts with no second check-in`() = runTest {
        val h = harness()
        h.vm.onCheckIn()
        runCurrent()
        h.vm.onStart()
        runCurrent()
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        // Start still calls checkIn; the second call is AlreadyToday and writes nothing.
        assertEquals(2, repo.checkInCalls)
        assertEquals(66, repo.find(1).counter.total)
        assertEquals(66, h.controller.state.value!!.totalReps)
    }

    @Test
    fun `a check-in-only entry checks in without changing its total`() = runTest {
        val habit = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Stretch", type = EntryType.CHECK_IN,
                    counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
                ),
            ),
        )
        val h = harness(repository = habit)
        runCurrent()
        assertEquals(EntryType.CHECK_IN, h.vm.uiState.value.type)
        h.vm.onCheckIn()
        runCurrent()
        val counter = habit.find(1).counter
        assertEquals(65, counter.total)
        assertEquals(5, counter.currentStreak)
        assertEquals(clock.instant, counter.lastCheckIn)
        assertTrue(h.vm.uiState.value.checkedInToday)
    }

    @Test
    fun `a failed check-in shows an error, and a deleted entry pops`() = runTest {
        val h = harness()
        repo.checkInError = IOException("disk full")
        h.vm.onCheckIn()
        runCurrent()
        assertTrue(h.vm.uiState.value.error!!.contains("disk full"))
        assertFalse(h.vm.uiState.value.checkingIn)
        assertFalse(h.vm.missing.value)
        repo.checkInError = EntryNotFound(1)
        h.vm.onCheckIn()
        runCurrent()
        assertTrue(h.vm.missing.value)
    }
```
The fixture's entry 1 last checked in 26 hours before the clock (on time), so a counted check-in takes 65 → 66.

- [ ] **Step 2: Run red** — Run (label `T5-1-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.entry.EntryViewModelTest"`. Expected: compile FAIL, "Unresolved reference 'onCheckIn'", "'checkingIn'" and "'type'".

- [ ] **Step 3: Implement** — `ui/entry/EntryViewModel.kt` (complete). `onStart`, `startWorkout` and `fail` keep their v1/R2 logic; `onStart` gains only the `checkingIn` guard:
```kotlin
package com.mitenko.hiitcounter.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.RepDistributor
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.ServiceStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.service.WorkoutServiceStarter
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class EntryUiState(
    val name: String = "",
    /** Spec R4 §4.1–4.2: a Workout shows the rep table and both buttons; a check-in-only entry shows the streak rows and Check in. */
    val type: EntryType = EntryType.WORKOUT,
    val reps: List<Int> = emptyList(),
    val total: Int = 0,
    val lastCheckIn: String = "—",
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val today: String = "",
    val checkedInToday: Boolean = false,
    val starting: Boolean = false,
    /** A Check in call is in flight (spec R4 §4.1): both buttons are disabled until it returns. */
    val checkingIn: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class EntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val controller: TimerController,
    private val starter: WorkoutServiceStarter,
    private val clock: Clock,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private data class Transient(val starting: Boolean = false, val checkingIn: Boolean = false, val error: String? = null)

    private val transient = MutableStateFlow(Transient())
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<EntryUiState> =
        combine(repo.entry(entryId).filterNotNull(), transient, refresh) { entry, tr, _ ->
            val now = clock.now()
            val zone = clock.zone()
            val counter = entry.counter
            EntryUiState(
                name = entry.name,
                type = entry.type,
                reps = RepDistributor.distribute(counter.total, entry.timing.sets),
                total = counter.total,
                lastCheckIn = counter.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: "—",
                bestStreak = counter.bestStreak,
                currentStreak = counter.currentStreak,
                today = DateFormats.date(now, zone),
                checkedInToday = counter.lastCheckIn?.atZone(zone)?.toLocalDate() == now.atZone(zone).toLocalDate(),
                starting = tr.starting,
                checkingIn = tr.checkingIn,
                error = tr.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState())

    /** Re-evaluates "Today" and "Checked in today" when the screen resumes (e.g. after midnight). */
    fun onResume() {
        refresh.update { it + 1 }
    }

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }

    /**
     * Spec R4 §4.1: Check in on its own. Ignored while a check-in or a start is in flight. The
     * repository call runs NonCancellable, so leaving the screen can't drop a check-in that has
     * started. A missing entry pops to the list; any other failure shows a message.
     */
    fun onCheckIn() {
        val t = transient.value
        if (t.checkingIn || t.starting) return
        transient.update { it.copy(checkingIn = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { repo.checkIn(entryId, clock) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: EntryNotFound) {
                markMissing()
            } catch (e: Exception) {
                transient.update { it.copy(error = "Couldn't check in: ${e.message ?: e.javaClass.simpleName}") }
            } finally {
                transient.update { it.copy(checkingIn = false) }
            }
        }
    }

    /**
     * v1 spec §4: the check-in is committed only after the foreground service has started. After a
     * manual Check in, that call returns AlreadyToday and writes nothing (R4 §4.1).
     */
    fun onStart() {
        val t = transient.value
        if (t.starting || t.checkingIn) return
        if (controller.status.value == RunStatus.RUNNING) return
        transient.value = Transient(starting = true)
        viewModelScope.launch {
            try {
                startWorkout()
            } catch (e: CancellationException) {
                controller.cancelPrepare()
                throw e
            } catch (e: Exception) {
                fail("Couldn't start the workout: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                transient.update { it.copy(starting = false) }
            }
        }
    }

    private suspend fun startWorkout() {
        val entry = repo.entry(entryId).first() ?: throw EntryNotFound(entryId)
        // Frozen at Start (spec §7.1): the run never reads the entry again.
        if (!controller.prepare(WorkoutSnapshot(entry.id, entry.name, entry.timing, entry.cues))) {
            transient.update { it.copy(error = "A workout is already starting") }
            return
        }

        starter.start().onFailure { e ->
            fail("Couldn't start the workout: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        val status = withTimeoutOrNull(SERVICE_START_TIMEOUT_MS) {
            controller.serviceStatus.first { it != ServiceStatus.Pending }
        }
        if (status != ServiceStatus.Started) {
            val reason = (status as? ServiceStatus.Failed)?.reason ?: "the timer service didn't respond"
            fail("Couldn't start the workout: $reason")
            return
        }
        // Uses the row's own progression; throwing (incl. EntryNotFound) takes the fail() path above.
        val result = repo.checkIn(entryId, clock)
        controller.start(RepDistributor.distribute(result.state.total, entry.timing.sets))
    }

    private fun fail(message: String) {
        controller.cancelPrepare()
        transient.update { it.copy(error = message) }
    }

    companion object {
        const val SERVICE_START_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-1-GREEN`, ~4 min): full suite + count. Expected: **363 tests**. The 11 existing `EntryViewModelTest` cases (start success, service failures, timeout, debounce, check-in failure, `EntryNotFound` during PREPARING, cancel mid-start) must stay green unchanged.

- [ ] **Step 5: Commit** — diff-review `EntryViewModel.kt` (new: `type`, `checkingIn`, `onCheckIn`, the `checkingIn` guard in `onStart`; `startWorkout` and `fail` byte-identical), then `git add -A && git commit -m "feat(entry): check in without starting a workout"`

### Subtask 5.2: Check in / Start buttons, the check-in-only table and every R4 string

**Files:** Replace `ui/entry/EntryScreen.kt`; edit `app/src/main/res/values/strings.xml`, `test/ui/common/InfoTextsTest.kt`; edit and append to `test/ui/entry/EntryScreenTest.kt`.

- [ ] **Step 1: Failing tests**

`InfoTextsTest`: the two new ⓘ texts join the list. Replace:
```kotlin
        /** The 20 labelled rows of spec R3 §7.2, in page order. */
        val ROWS = listOf(
            "info_prepare", "info_sets", "info_work", "info_rest", "info_cooldown", "info_total",
            "info_starting_total", "info_floor", "info_cap", "info_hold", "info_hold_at", "info_hold_for",
            "info_window", "info_penalty_rate",
            "info_total_reps", "info_best_streak", "info_current_streak", "info_last_check_in",
            "info_sound", "info_vibration",
        )
```
with:
```kotlin
        /** The 20 labelled rows of spec R3 §7.2 in page order, then R4 §6's Type (Entry Settings) and Voice (Cues). */
        val ROWS = listOf(
            "info_prepare", "info_sets", "info_work", "info_rest", "info_cooldown", "info_total",
            "info_starting_total", "info_floor", "info_cap", "info_hold", "info_hold_at", "info_hold_for",
            "info_window", "info_penalty_rate",
            "info_total_reps", "info_best_streak", "info_current_streak", "info_last_check_in",
            "info_sound", "info_vibration",
            "info_type", "info_voice",
        )
```

`EntryScreenTest`: add the imports `androidx.compose.ui.test.assertHeightIsAtLeast`, `androidx.compose.ui.test.assertIsEnabled`, `androidx.compose.ui.unit.dp` and `com.mitenko.hiitcounter.domain.model.EntryType`. Replace the helper:
```kotlin
    private fun show(s: EntryUiState, onBack: () -> Unit = {}, onStart: () -> Unit = {}, onSettings: () -> Unit = {}) {
        compose.setContent {
            HiitTheme { EntryScreen(s, onBack = onBack, onStart = onStart, onOpenSettings = onSettings, onDismissError = {}) }
        }
    }
```
with:
```kotlin
    private fun show(
        s: EntryUiState,
        onBack: () -> Unit = {},
        onStart: () -> Unit = {},
        onSettings: () -> Unit = {},
        onCheckIn: () -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                EntryScreen(
                    s, onBack = onBack, onStart = onStart, onCheckIn = onCheckIn, onOpenSettings = onSettings, onDismissError = {},
                )
            }
        }
    }
```
Then append:
```kotlin
    @Test
    fun `check in sits next to start and invokes its callback`() {
        var checks = 0
        show(state.copy(checkedInToday = false), onCheckIn = { checks++ })
        compose.onNodeWithTag("check_in").performScrollTo().assertTextEquals("Check in").performClick()
        assertEquals(1, checks)
        compose.onNodeWithTag("start").assertIsEnabled()
    }

    @Test
    fun `once checked in today it reads Checked in and is disabled`() {
        show(state) // checkedInToday = true
        compose.onNodeWithTag("check_in").assertTextEquals("Checked in ✓").assertIsNotEnabled()
        compose.onNodeWithTag("start").assertIsEnabled()
    }

    @Test
    fun `both buttons are disabled while a check-in is in flight`() {
        show(state.copy(checkedInToday = false, checkingIn = true))
        compose.onNodeWithTag("check_in").assertIsNotEnabled()
        compose.onNodeWithTag("start").assertIsNotEnabled()
    }

    @Test
    fun `a check-in-only entry shows the streak rows and one Check in`() {
        show(state.copy(type = EntryType.CHECK_IN, checkedInToday = false))
        compose.onNodeWithTag("rep_0").assertDoesNotExist()
        compose.onNodeWithText("Total Reps").assertDoesNotExist()
        compose.onNodeWithText("Last Check In").assertExists()
        compose.onNodeWithText("Best CI Streak").assertExists()
        compose.onNodeWithText("Curr CI Streak").assertExists()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithTag("start").assertDoesNotExist()
        compose.onNodeWithTag("check_in").assertIsEnabled()
    }

    @Test
    fun `both buttons are at least 48 dp tall`() {
        show(state.copy(checkedInToday = false))
        compose.onNodeWithTag("check_in").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("start").assertHeightIsAtLeast(48.dp)
    }
```

- [ ] **Step 2: Run red** — Run (label `T5-2-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.entry.EntryScreenTest" --tests "com.mitenko.hiitcounter.ui.common.InfoTextsTest"`. Expected: compile FAIL, "No parameter with name 'onCheckIn' found". (Once it compiles, `InfoTextsTest` fails until the strings exist.)

- [ ] **Step 3: Implement**

`app/src/main/res/values/strings.xml`. Replace:
```xml
    <string name="info_vibration">Vibrates at each phase change, including with the screen off.</string>
</resources>
```
with:
```xml
    <string name="info_vibration">Vibrates at each phase change, including with the screen off.</string>

    <!-- Check-in-only entries, Check in / Start and the Voice cue (spec R4 §6, verbatim) -->
    <string name="check_in">Check in</string>
    <string name="checked_in">Checked in ✓</string>
    <string name="type">Type</string>
    <string name="type_workout">Workout</string>
    <string name="type_check_in">Check-in only</string>
    <string name="streak_n">Streak %1$d</string>
    <string name="voice">Voice</string>
    <string name="voice_unavailable">Voice not available on this device</string>
    <string name="info_type">Workout entries run a timer and count reps. Check-in only entries just record that you did it, with streaks. Switching keeps all your values.</string>
    <string name="info_voice">Says the number of reps as each work set starts.</string>
</resources>
```

`ui/entry/EntryScreen.kt` (complete). The top bar, snackbar, "Checked in today" line and `TableRow` are unchanged:
```kotlin
package com.mitenko.hiitcounter.ui.entry

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.model.EntryType

@Composable
fun EntryRoute(onBack: () -> Unit, onOpenSettings: () -> Unit, onEntryGone: () -> Unit, vm: EntryViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    // ← and ⚙ are disabled while starting (spec §7.4); system back is held too.
    BackHandler(enabled = state.starting) { }
    EntryScreen(
        state, onBack = onBack, onStart = vm::onStart, onCheckIn = vm::onCheckIn,
        onOpenSettings = onOpenSettings, onDismissError = vm::dismissError,
    )
}

@Composable
fun EntryScreen(
    state: EntryUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onCheckIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            onDismissError()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !state.starting, modifier = Modifier.testTag("back")) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
                Text(
                    state.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("entry_name"),
                )
                IconButton(onClick = onOpenSettings, enabled = !state.starting, modifier = Modifier.testTag("settings")) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                }
            }
            Spacer(Modifier.height(16.dp))
            RepTable(state)
            if (state.checkedInToday) {
                Text(
                    stringResource(R.string.checked_in_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            // Spec R4 §4.1–4.2: a Workout has Check in (outlined) and Start (filled); a check-in-only entry has one Check in.
            when (state.type) {
                EntryType.WORKOUT -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CheckInButton(state, onCheckIn, Modifier.weight(1f))
                    Button(
                        onClick = onStart,
                        enabled = !state.starting && !state.checkingIn,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("start"),
                    ) {
                        Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
                    }
                }
                EntryType.CHECK_IN -> CheckInButton(state, onCheckIn, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Check in (spec R4 §4.1). It reads "Checked in ✓" and is disabled once checked in today (the
 * screen re-evaluates the date on resume). It's also disabled while its own call or a start is in flight.
 */
@Composable
private fun CheckInButton(state: EntryUiState, onCheckIn: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onCheckIn,
        enabled = !state.checkedInToday && !state.checkingIn && !state.starting,
        modifier = modifier.heightIn(min = 56.dp).testTag("check_in"),
    ) {
        Text(
            stringResource(if (state.checkedInToday) R.string.checked_in else R.string.check_in),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun RepTable(state: EntryUiState) {
    val line = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().border(1.dp, line)) {
        // Spec R4 §4.2: a check-in-only entry has no rep rows and no Total Reps row.
        if (state.type == EntryType.WORKOUT) {
            state.reps.forEachIndexed { index, reps ->
                Text(
                    "$reps",
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, line).padding(vertical = 8.dp).testTag("rep_$index"),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TableRow(R.string.total_reps, "${state.total}")
        }
        TableRow(R.string.last_check_in, state.lastCheckIn)
        TableRow(R.string.best_streak, "${state.bestStreak}", boldValue = true)
        TableRow(R.string.current_streak, "${state.currentStreak}")
        TableRow(R.string.today, state.today)
    }
}

@Composable
private fun TableRow(@StringRes label: Int, value: String, boldValue: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(
            value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = if (boldValue) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-2-GREEN`, ~4 min): full suite + count. Expected: **368 tests**. The 4 existing `EntryScreenTest` cases stay green (the table and the "Checked in today" line are unchanged; Start keeps its tag and its "disabled while starting" rule).

- [ ] **Step 5: Commit** — diff-review `EntryScreen.kt`, then `git add -A && git commit -m "feat(entry): Check in and Start buttons, and the check-in-only table"`

**Task 5 gate:** Run (label `T5-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **368 tests**, no lint errors. Lint may warn about the `type*`, `streak_n` and `voice*` strings that aren't used until Tasks 6–8 (`UnusedResources` is a warning, not an error).

---

## Task 6: The entry list: "Streak N" and the type at create time (§4.3, §4.4)

**Interfaces produced:**
- `EntryRow(id, name, reps, checkedInToday, type: EntryType = WORKOUT, streak: Int = 0)`.
- `EntryListViewModel.create(name, type = WORKOUT, onCreated)`.
- `EntryListScreen(…, onCreate: (String, EntryType) -> Unit)`, with segmented buttons tagged `type_WORKOUT` / `type_CHECK_IN`.
- `NameDialog(title, initial, onConfirm, onDismiss, extra: (@Composable () -> Unit)? = null)`.
- `val EntryType.label: Int` (`@StringRes`) in `ui/common/EntryTypeLabel.kt`.

### Subtask 6.1: The "Streak N" byline for check-in-only rows

**Files:** Edit `ui/entries/EntryListViewModel.kt`, `ui/entries/EntryListScreen.kt`; append to `test/ui/entries/EntryListViewModelTest.kt`, `test/ui/entries/EntryListScreenTest.kt`.

- [ ] **Step 1: Failing tests**

`EntryListViewModelTest`: add the import `com.mitenko.hiitcounter.domain.model.EntryType`, then append:
```kotlin
    @Test
    fun `a check-in-only row carries its type and current streak`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(
                    1, "Stretch", type = EntryType.CHECK_IN,
                    counter = CounterState(total = 48, currentStreak = 5, lastCheckIn = checkedInThisMorning),
                ),
            ),
        )
        assertEquals(
            EntryListUiState.Items(listOf(EntryRow(1, "Stretch", 48, true, type = EntryType.CHECK_IN, streak = 5))),
            vm(repo).uiState.value,
        )
    }
```

`EntryListScreenTest`: add the import `com.mitenko.hiitcounter.domain.model.EntryType`, then append:
```kotlin
    @Test
    fun `a check-in-only row reads Streak N with today's marker`() {
        show(EntryListUiState.Items(listOf(EntryRow(4, "Stretch", 48, checkedInToday = true, type = EntryType.CHECK_IN, streak = 5))))
        compose.onNodeWithText("Streak 5").assertExists()
        compose.onNodeWithText("Reps 48").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Checked in today", useUnmergedTree = true).assertCountEquals(1)
    }
```

- [ ] **Step 2: Run red** — Run (label `T6-1-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.entries.*"`. Expected: compile FAIL, "No parameter with name 'type' found" (on `EntryRow`).

- [ ] **Step 3: Implement**

`ui/entries/EntryListViewModel.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.domain.model.EntryNotFound
```
with:
```kotlin
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
```
Then replace:
```kotlin
/** [reps] is the entry's current total, i.e. the next workout's total. */
data class EntryRow(val id: Long, val name: String, val reps: Int, val checkedInToday: Boolean)
```
with:
```kotlin
/**
 * [reps] is the entry's current total, i.e. the next workout's total. [streak] is the current
 * check-in streak, which a check-in-only row shows instead (spec R4 §4.3).
 */
data class EntryRow(
    val id: Long,
    val name: String,
    val reps: Int,
    val checkedInToday: Boolean,
    val type: EntryType = EntryType.WORKOUT,
    val streak: Int = 0,
)
```
Then replace:
```kotlin
                        checkedInToday = e.counter.lastCheckIn?.atZone(zone)?.toLocalDate() == today,
                    )
```
with:
```kotlin
                        checkedInToday = e.counter.lastCheckIn?.atZone(zone)?.toLocalDate() == today,
                        type = e.type,
                        streak = e.counter.currentStreak,
                    )
```

`ui/entries/EntryListScreen.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.R
```
with:
```kotlin
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.model.EntryType
```
Then replace:
```kotlin
            Text(
                stringResource(R.string.reps_n, row.reps),
```
with:
```kotlin
            Text(
                // Spec R4 §4.3: a Workout reads "Reps N", a check-in-only entry "Streak N".
                if (row.type == EntryType.CHECK_IN) stringResource(R.string.streak_n, row.streak) else stringResource(R.string.reps_n, row.reps),
```

- [ ] **Step 4: Run green** — Run (label `T6-1-GREEN`, ~4 min): full suite + count. Expected: **370 tests**. The existing `items show the name, the next total and today's check-in` stays green: its entries are Workouts with a current streak of 0, the new fields' defaults.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(list): Streak N byline for check-in-only entries"`

### Subtask 6.2: The create dialog's Workout | Check-in only choice

**Files:** Create `ui/common/EntryTypeLabel.kt`; edit `ui/common/NameDialog.kt`, `ui/entries/EntryListViewModel.kt`, `ui/entries/EntryListScreen.kt`; edit and append to `test/ui/entries/EntryListScreenTest.kt`; append to `test/ui/entries/EntryListViewModelTest.kt`.

- [ ] **Step 1: Failing tests**

`EntryListViewModelTest` (the import from 6.1 is there), append:
```kotlin
    @Test
    fun `create passes the chosen type`() = runTest {
        val repo = FakeEntryRepository()
        val vm = vm(repo)
        vm.create("Stretch", EntryType.CHECK_IN) {}
        vm.create("Burpees") {}
        runCurrent()
        assertEquals(listOf(EntryType.CHECK_IN, EntryType.WORKOUT), repo.state.value.map { it.type })
    }
```

`EntryListScreenTest`: add the import `androidx.compose.ui.test.assertIsSelected`. The screen's `onCreate` now takes the type, so three call sites change. Replace:
```kotlin
        onCreate: (String) -> Unit = {},
```
with:
```kotlin
        onCreate: (String, EntryType) -> Unit = { _, _ -> },
```
Then, in `a moved row stays scrolled into view`, replace:
```kotlin
                    onCreate = {},
```
with:
```kotlin
                    onCreate = { _, _ -> },
```
Then, in `add creates through the name dialog`, replace:
```kotlin
        show(EntryListUiState.Items(rows), onCreate = { created = it })
```
with:
```kotlin
        show(EntryListUiState.Items(rows), onCreate = { name, _ -> created = name })
```
Then append:
```kotlin
    @Test
    fun `the create dialog offers Workout or Check-in only, defaulting to Workout`() {
        val created = mutableListOf<Pair<String, EntryType>>()
        show(EntryListUiState.Items(rows), onCreate = { name, type -> created += name to type })
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("type_WORKOUT").assertIsSelected()
        compose.onNodeWithTag("type_CHECK_IN").performClick()
        compose.onNodeWithTag("type_CHECK_IN").assertIsSelected()
        compose.onNodeWithTag("name_field").performTextReplacement("Stretch")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals(listOf("Stretch" to EntryType.CHECK_IN), created)
    }
```

- [ ] **Step 2: Run red** — Run (label `T6-2-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.entries.*"`. Expected: compile FAIL, a type mismatch on `onCreate` (`(String, EntryType) -> Unit` where `(String) -> Unit` is expected) and "Too many arguments for 'create'".

- [ ] **Step 3: Implement**

`ui/common/EntryTypeLabel.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.annotation.StringRes
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.model.EntryType

/** An entry type's user-facing name (spec R4 §6): "Workout" or "Check-in only". */
@get:StringRes
val EntryType.label: Int
    get() = when (this) {
        EntryType.WORKOUT -> R.string.type_workout
        EntryType.CHECK_IN -> R.string.type_check_in
    }
```

`ui/common/NameDialog.kt`. Replace:
```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
```
with:
```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
```
Then replace:
```kotlin
import androidx.compose.ui.text.input.TextFieldValue
```
with:
```kotlin
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
```
Then replace:
```kotlin
 * lives in rememberSaveable, so it survives rotation.
 */
@Composable
fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
```
with:
```kotlin
 * lives in rememberSaveable, so it survives rotation. [extra] goes under the field (the create
 * dialog's type choice, spec R4 §4.4).
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
```
Then replace:
```kotlin
        text = {
            val focus = remember { FocusRequester() }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "${text.text.trim().length}/${EntryNames.MAX_LENGTH}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("name_field"),
            )
```
with:
```kotlin
        text = {
            val focus = remember { FocusRequester() }
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "${text.text.trim().length}/${EntryNames.MAX_LENGTH}") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("name_field"),
                )
                extra?.let {
                    Spacer(Modifier.height(12.dp))
                    it()
                }
            }
```
The `LaunchedEffect(Unit) { focus.requestFocus() }` that follows stays where it is.

`ui/entries/EntryListViewModel.kt`. Replace:
```kotlin
    /** Creates with defaults and reports the new id for navigation (spec §7.3). The name dialog already blocks invalid names. */
    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = try {
                repo.create(name)
```
with:
```kotlin
    /**
     * Creates with defaults and the chosen [type] (spec R4 §4.4) and reports the new id for
     * navigation (spec §7.3). The name dialog already blocks invalid names.
     */
    fun create(name: String, type: EntryType = EntryType.WORKOUT, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = try {
                repo.create(name, type)
```

`ui/entries/EntryListScreen.kt`. Replace:
```kotlin
import androidx.compose.material3.CircularProgressIndicator
```
with:
```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
```
Then replace:
```kotlin
import androidx.compose.material3.Scaffold
```
with:
```kotlin
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
```
Then replace:
```kotlin
import com.mitenko.hiitcounter.ui.common.NameDialog
```
with:
```kotlin
import com.mitenko.hiitcounter.ui.common.NameDialog
import com.mitenko.hiitcounter.ui.common.label
```
Then replace:
```kotlin
        onCreate = { name -> vm.create(name, onCreated) },
```
with:
```kotlin
        onCreate = { name, type -> vm.create(name, type, onCreated) },
```
Then replace:
```kotlin
    onCreate: (String) -> Unit,
```
with:
```kotlin
    onCreate: (String, EntryType) -> Unit,
```
Then replace:
```kotlin
    if (naming) {
        NameDialog(
            title = stringResource(R.string.new_workout),
            initial = "",
            onConfirm = { name ->
                naming = false
                onCreate(name)
            },
            onDismiss = { naming = false },
        )
    }
```
with:
```kotlin
    if (naming) {
        // Spec R4 §4.4: Workout by default, every time the dialog opens; kept across rotation.
        var type by rememberSaveable { mutableStateOf(EntryType.WORKOUT) }
        NameDialog(
            title = stringResource(R.string.new_workout),
            initial = "",
            onConfirm = { name ->
                naming = false
                onCreate(name, type)
            },
            onDismiss = { naming = false },
            extra = { EntryTypeChoice(type, onSelect = { type = it }) },
        )
    }
```
Then append at the end of the file:
```kotlin

/** Workout | Check-in only (spec R4 §4.4). Each segment is tagged `type_<TYPE>`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTypeChoice(selected: EntryType, onSelect: (EntryType) -> Unit) {
    val types = EntryType.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                modifier = Modifier.testTag("type_${type.name}"),
            ) {
                Text(stringResource(type.label))
            }
        }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T6-2-GREEN`, ~4 min): full suite + count. Expected: **372 tests**. `EntrySettingsScreenTest`'s duplicate test (which renders `EntryListRoute`) and `NameDialogTest` (rename, no `extra`) stay green.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(list): choose Workout or Check-in only when creating an entry"`

**Task 6 gate:** Run (label `T6-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **372 tests**, no lint errors.

---

## Task 7: Entry Settings: the Type row and the rows a check-in-only entry keeps (§4.5)

**Interfaces produced:**
- `SettingsPage.visibleFor(type): List<SettingsPage>`: all four for a Workout, `[PROGRESSION, CURRENT]` for check-in only.
- `EntrySettingsUiState(name, busy, error, type: EntryType = WORKOUT)`.
- `EntrySettingsViewModel.setType(type)`: allowed while busy, `NonCancellable`, `EntryNotFound` → `markMissing()`.
- `EntrySettingsScreen(state, onBack, onOpen, onRename, onDuplicate, onDelete, onSetType)`, with test tags `type`, `type_value`, `type_option_<TYPE>`, `type_ok`, `type_cancel`.

### Subtask 7.1: `setType`, the type in the state and `SettingsPage.visibleFor`

**Files:** Edit `ui/settings/EntrySettings.kt`; create `test/ui/settings/SettingsPagesTest.kt`; append to `test/ui/settings/EntrySettingsViewModelTest.kt`.

- [ ] **Step 1: Failing tests**

`test/ui/settings/SettingsPagesTest.kt` (complete; plain JVM, it only reads the enum):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.model.EntryType
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPagesTest {
    @Test
    fun `a check-in-only entry keeps only Progression and Current`() {
        assertEquals(SettingsPage.entries.toList(), SettingsPage.visibleFor(EntryType.WORKOUT))
        assertEquals(listOf(SettingsPage.PROGRESSION, SettingsPage.CURRENT), SettingsPage.visibleFor(EntryType.CHECK_IN))
    }
}
```

`EntrySettingsViewModelTest`: add the imports `com.mitenko.hiitcounter.domain.model.EntryNotFound` and `com.mitenko.hiitcounter.domain.model.EntryType`, then append:
```kotlin
    @Test
    fun `setType saves at once, even while busy, and keeps every other value`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot)
        runCurrent()
        assertTrue(h.vm.uiState.value.busy)
        val before = repo.find(1)
        h.vm.setType(EntryType.CHECK_IN)
        runCurrent()
        assertEquals(before.copy(type = EntryType.CHECK_IN), repo.find(1))
        assertEquals(EntryType.CHECK_IN, h.vm.uiState.value.type)
        assertEquals(1, repo.typeWrites)
    }

    @Test
    fun `setType racing a delete reports missing`() = runTest {
        val h = harness()
        repo.writeError = EntryNotFound(1)
        h.vm.setType(EntryType.CHECK_IN)
        runCurrent()
        assertTrue(h.vm.missing.value)
        assertEquals(EntryType.WORKOUT, repo.find(1).type)
    }
```

- [ ] **Step 2: Run red** — Run (label `T7-1-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.SettingsPagesTest" --tests "com.mitenko.hiitcounter.ui.settings.EntrySettingsViewModelTest"`. Expected: compile FAIL, "Unresolved reference 'visibleFor'", "'setType'" and "'type'".

- [ ] **Step 3: Implement** — `ui/settings/EntrySettings.kt`. Replace:
```kotlin
import com.mitenko.hiitcounter.domain.model.EntryNotFound
```
with:
```kotlin
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
```
Then replace:
```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
```
with:
```kotlin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
```
Then replace:
```kotlin
import kotlinx.coroutines.launch
```
with:
```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```
Then replace:
```kotlin
    CUES(R.string.settings_cues, R.string.settings_cues),
}

data class EntrySettingsUiState(val name: String = "", val busy: Boolean = false, val error: String? = null)
```
with:
```kotlin
    CUES(R.string.settings_cues, R.string.settings_cues);

    companion object {
        /** Spec R4 §4.5–4.6: a check-in-only entry has no timer and no cues, so only Progression and Current remain. */
        fun visibleFor(type: EntryType): List<SettingsPage> = when (type) {
            EntryType.WORKOUT -> entries.toList()
            EntryType.CHECK_IN -> listOf(PROGRESSION, CURRENT)
        }
    }
}

data class EntrySettingsUiState(
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val type: EntryType = EntryType.WORKOUT,
)
```
Then replace:
```kotlin
            EntrySettingsUiState(name = entry.name, busy = controller.isBusy(entryId), error = err)
```
with:
```kotlin
            EntrySettingsUiState(name = entry.name, busy = controller.isBusy(entryId), error = err, type = entry.type)
```
Then replace:
```kotlin
    fun duplicate(onCreated: (Long) -> Unit) {
```
with:
```kotlin
    /**
     * Spec R4 §4.5: saved at once on the dialog's OK. Allowed while busy, because the run's snapshot
     * is frozen. NonCancellable, so leaving right after OK can't drop a write that has started. A
     * deleted entry pops to the list.
     */
    fun setType(type: EntryType) {
        error.value = null
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { repo.setType(entryId, type) }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    fun duplicate(onCreated: (Long) -> Unit) {
```

- [ ] **Step 4: Run green** — Run (label `T7-1-GREEN`, ~4 min): full suite + count. Expected: **375 tests**. The existing `ui state shows the name and follows the controller's busy rule` stays green: its expected `EntrySettingsUiState(name = "Burpees", busy = false)` has the default type WORKOUT.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(settings): setType and the pages a check-in-only entry keeps"`

### Subtask 7.2: The Type row, its radio dialog and the hidden page rows

**Files:** Replace `ui/settings/EntrySettings.kt`; edit and append to `test/ui/settings/EntrySettingsScreenTest.kt`.

- [ ] **Step 1: Failing tests** — `EntrySettingsScreenTest`: add the imports `androidx.compose.ui.test.assertIsSelected`, `androidx.compose.ui.test.assertTextEquals`, `androidx.compose.ui.test.onNodeWithContentDescription`, `com.mitenko.hiitcounter.domain.model.EntryType` and `org.junit.Assert.assertTrue`. Replace the helper:
```kotlin
    private fun show(
        state: EntrySettingsUiState = EntrySettingsUiState(name = "Burpees"),
        onOpen: (SettingsPage) -> Unit = {},
        onRename: (String) -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                EntrySettingsScreen(state, onBack = {}, onOpen = onOpen, onRename = onRename, onDuplicate = {}, onDelete = onDelete)
            }
        }
    }
```
with:
```kotlin
    private fun show(
        state: EntrySettingsUiState = EntrySettingsUiState(name = "Burpees"),
        onOpen: (SettingsPage) -> Unit = {},
        onRename: (String) -> Unit = {},
        onDelete: () -> Unit = {},
        onSetType: (EntryType) -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                EntrySettingsScreen(
                    state, onBack = {}, onOpen = onOpen, onRename = onRename, onDuplicate = {}, onDelete = onDelete,
                    onSetType = onSetType,
                )
            }
        }
    }
```
Then append:
```kotlin
    @Test
    fun `the type row opens a radio dialog and only OK with a new type saves it`() {
        val chosen = mutableListOf<EntryType>()
        show(onSetType = { chosen += it })
        compose.onNodeWithTag("type_value", useUnmergedTree = true).assertTextEquals("Workout")
        compose.onNodeWithContentDescription("About Type").assertExists()
        compose.onNodeWithTag("type").performClick()
        compose.onNodeWithTag("type_option_CHECK_IN").performClick()
        compose.onNodeWithTag("type_cancel").performClick()
        assertTrue(chosen.isEmpty())
        compose.onNodeWithTag("type").performClick()
        compose.onNodeWithTag("type_ok").performClick() // OK with the current type (Workout) writes nothing
        assertTrue(chosen.isEmpty())
        compose.onNodeWithTag("type").performClick()
        compose.onNodeWithTag("type_option_WORKOUT").assertIsSelected() // Cancel discarded the pick
        compose.onNodeWithTag("type_option_CHECK_IN").performClick()
        compose.onNodeWithTag("type_ok").performClick()
        assertEquals(listOf(EntryType.CHECK_IN), chosen)
    }

    @Test
    fun `a check-in-only entry hides the Timing and Cues rows`() {
        show(EntrySettingsUiState(name = "Stretch", type = EntryType.CHECK_IN))
        compose.onNodeWithTag("type_value", useUnmergedTree = true).assertTextEquals("Check-in only")
        compose.onNodeWithTag("page_TIMING").assertDoesNotExist()
        compose.onNodeWithTag("page_CUES").assertDoesNotExist()
        compose.onNodeWithTag("page_PROGRESSION").assertExists()
        compose.onNodeWithTag("page_CURRENT").assertExists()
    }
```

- [ ] **Step 2: Run red** — Run (label `T7-2-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.EntrySettingsScreenTest"`. Expected: compile FAIL, "No parameter with name 'onSetType' found".

- [ ] **Step 3: Implement** — `ui/settings/EntrySettings.kt` (complete). The enum, the ViewModel (as left by 7.1), Rename, Duplicate, Delete and `ListRow` are unchanged:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.EntryBusy
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.InfoTag
import com.mitenko.hiitcounter.ui.common.NameDialog
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The four per-entry settings pages (spec R2 §7.5). [label] is the Entry Settings row, and [tab]
 * is the pager tab (R3 §4: "Timing · Progression · Current · Cues"). The ordinal is the route's
 * `page` argument.
 */
enum class SettingsPage(@StringRes val label: Int, @StringRes val tab: Int) {
    TIMING(R.string.settings_timing, R.string.settings_timing),
    PROGRESSION(R.string.settings_progression, R.string.settings_progression),
    CURRENT(R.string.settings_current_state, R.string.tab_current),
    CUES(R.string.settings_cues, R.string.settings_cues);

    companion object {
        /** Spec R4 §4.5–4.6: a check-in-only entry has no timer and no cues, so only Progression and Current remain. */
        fun visibleFor(type: EntryType): List<SettingsPage> = when (type) {
            EntryType.WORKOUT -> entries.toList()
            EntryType.CHECK_IN -> listOf(PROGRESSION, CURRENT)
        }
    }
}

data class EntrySettingsUiState(
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val type: EntryType = EntryType.WORKOUT,
)

@HiltViewModel
class EntrySettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val controller: TimerController,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val error = MutableStateFlow<String?>(null)

    /** In-flight guard; touched only on Main. A double-tap while a duplicate is running is ignored. */
    private var duplicating = false

    /** busy is re-read on every run-status change; the snapshot is set before PREPARING is emitted. */
    val uiState: StateFlow<EntrySettingsUiState> =
        combine(repo.entry(entryId).filterNotNull(), controller.status, error) { entry, _, err ->
            EntrySettingsUiState(name = entry.name, busy = controller.isBusy(entryId), error = err, type = entry.type)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntrySettingsUiState())

    /** Allowed while busy: it only affects future runs, because the snapshot is frozen (spec §7.1). */
    fun rename(name: String) {
        error.value = null
        viewModelScope.launch {
            try {
                repo.rename(entryId, name)
            } catch (e: EntryNotFound) {
                markMissing()
            } catch (e: IllegalArgumentException) {
                error.value = e.message
            }
        }
    }

    /**
     * Spec R4 §4.5: saved at once on the dialog's OK. Allowed while busy, because the run's snapshot
     * is frozen. NonCancellable, so leaving right after OK can't drop a write that has started. A
     * deleted entry pops to the list.
     */
    fun setType(type: EntryType) {
        error.value = null
        viewModelScope.launch {
            try {
                withContext(NonCancellable) { repo.setType(entryId, type) }
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    fun duplicate(onCreated: (Long) -> Unit) {
        if (duplicating) return
        duplicating = true
        error.value = null
        viewModelScope.launch {
            try {
                onCreated(repo.duplicate(entryId))
            } catch (e: EntryNotFound) {
                markMissing()
            } finally {
                duplicating = false
            }
        }
    }

    /**
     * Spec §7.1: the busy check reads the singleton controller at the moment of the call, so a
     * stale screen can't bypass it. A busy entry fails with [EntryBusy] and nothing is deleted.
     */
    fun delete(onDeleted: () -> Unit) {
        error.value = null
        viewModelScope.launch {
            try {
                if (controller.isBusy(entryId)) throw EntryBusy(entryId)
                repo.delete(entryId)
                onDeleted()
            } catch (e: EntryBusy) {
                error.value = BUSY_HINT
            } catch (e: EntryNotFound) {
                onDeleted()
            }
        }
    }

    companion object {
        const val BUSY_HINT = "Stop the workout first"
    }
}

@Composable
fun EntrySettingsRoute(
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onDuplicated: (Long) -> Unit,
    onDeleted: () -> Unit,
    onEntryGone: () -> Unit,
    vm: EntrySettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    EntrySettingsScreen(
        state = state,
        onBack = onBack,
        onOpen = onOpen,
        onRename = vm::rename,
        onDuplicate = { vm.duplicate(onDuplicated) },
        onDelete = { vm.delete(onDeleted) },
        onSetType = vm::setType,
    )
}

@Composable
fun EntrySettingsScreen(
    state: EntrySettingsUiState,
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSetType: (EntryType) -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var choosingType by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(title = state.name, onBack = onBack) {
        // Spec R4 §4.5: the Type row sits above the page rows, and a check-in-only entry has no Timing or Cues.
        TypeRow(state.type) { choosingType = true }
        SettingsPage.visibleFor(state.type).forEach { page ->
            ListRow(stringResource(page.label), tag = "page_${page.name}") { onOpen(page) }
        }
        ListRow(stringResource(R.string.rename), tag = "rename") { renaming = true }
        ListRow(stringResource(R.string.duplicate), tag = "duplicate", onClick = onDuplicate)
        // Busy rule (spec §7.1): disabled with a hint; the ViewModel re-checks at the moment of the call.
        ListRow(stringResource(R.string.delete), tag = "delete", enabled = !state.busy, color = MaterialTheme.colorScheme.error) {
            confirmDelete = true
        }
        if (state.busy) {
            Text(
                stringResource(R.string.stop_workout_first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).testTag("busy_hint"),
            )
        }
        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.rename),
            initial = state.name,
            onConfirm = { name ->
                renaming = false
                onRename(name)
            },
            onDismiss = { renaming = false },
        )
    }
    if (choosingType) {
        TypeDialog(
            current = state.type,
            onConfirm = { type ->
                choosingType = false
                if (type != state.type) onSetType(type)
            },
            onDismiss = { choosingType = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, state.name)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete"),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "Type" over "Workout" or "Check-in only", with its ⓘ as a separate target (spec R4 §4.5, R3 §7.1). */
@Composable
private fun TypeRow(type: EntryType, onClick: () -> Unit) {
    val title = stringResource(R.string.type)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp)
                .testTag("type"),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(type.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("type_value"),
            )
        }
        InfoTag(title = title, text = stringResource(R.string.info_type))
    }
    HorizontalDivider()
}

/** Two radio options with OK and Cancel (spec R4 §4.5). The pick lives in rememberSaveable until OK. */
@Composable
private fun TypeDialog(current: EntryType, onConfirm: (EntryType) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.type)) },
        text = {
            Column(Modifier.selectableGroup()) {
                EntryType.entries.forEach { type ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = selected == type, onClick = { selected = type }, role = Role.RadioButton)
                            .testTag("type_option_${type.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == type, onClick = null)
                        Text(stringResource(type.label), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, modifier = Modifier.testTag("type_ok")) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("type_cancel")) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ListRow(
    text: String,
    tag: String,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp)
            .testTag(tag),
    )
    HorizontalDivider()
}
```

- [ ] **Step 4: Run green** — Run (label `T7-2-GREEN`, ~4 min): full suite + count. Expected: **377 tests**. The 5 existing `EntrySettingsScreenTest` cases stay green (a Workout still shows all four page rows; Rename, Delete, busy and Duplicate are unchanged).

- [ ] **Step 5: Commit** — diff-review `EntrySettings.kt` against 7.1's version (new: `TypeRow`, `TypeDialog`, `choosingType`, `onSetType`, the `visibleFor` loop), then `git add -A && git commit -m "feat(settings): Type row with a radio dialog; check-in-only entries hide Timing and Cues"`

**Task 7 gate:** Run (label `T7-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **377 tests**, no lint errors.

---

## Task 8: The pager for check-in-only entries and the Voice switch (§4.6, §4.7)

**Interfaces produced:**
- `SettingsPagerViewModel.type: StateFlow<EntryType?>` (null until loaded).
- `internal fun SettingsPage.tabIndex(pages: List<SettingsPage>): Int` (a hidden page → 0).
- `ProgressionPage(vm, windowOnly = false)` / `ProgressionPageContent(…, onReset, windowOnly = false)`.
- `CurrentStatePage(vm, showTotal = true)` / `CurrentStatePageContent(…, onResetProgress, showTotal = true)`.
- `CuesSettingsViewModel(handle, repo, voice: VoiceAvailability)` with `voiceAvailable: StateFlow<Boolean?>` and `setVoice(on)`.
- `SwitchRow(label, checked, onChange, modifier, info, supportingText: String? = null)`, the text tagged `support_<label>`.
- `test/testutil/FakeVoiceAvailability`.

### Subtask 8.1: Check-in-only tabs, page mapping, a window-only Progression and a Current without the total

**Files:** Replace `ui/settings/SettingsPager.kt`; edit `ui/settings/ProgressionSettings.kt`, `ui/settings/CurrentStateSettings.kt`; append to `test/ui/settings/SettingsPagesTest.kt`, `test/ui/settings/SettingsPagerViewModelTest.kt`; edit and append to `test/ui/settings/SettingsPagerTest.kt`, `test/ui/settings/ProgressionSettingsScreenTest.kt`, `test/ui/settings/CurrentStatePageTest.kt`.

- [ ] **Step 1: Failing tests**

`SettingsPagesTest`, append:
```kotlin
    @Test
    fun `a page argument maps onto the visible tabs, and a hidden page opens the first`() {
        val checkIn = SettingsPage.visibleFor(EntryType.CHECK_IN)
        assertEquals(0, SettingsPage.PROGRESSION.tabIndex(checkIn))
        assertEquals(1, SettingsPage.CURRENT.tabIndex(checkIn))
        assertEquals(0, SettingsPage.TIMING.tabIndex(checkIn))
        assertEquals(0, SettingsPage.CUES.tabIndex(checkIn))
        assertEquals(3, SettingsPage.CUES.tabIndex(SettingsPage.visibleFor(EntryType.WORKOUT)))
    }
```

`SettingsPagerViewModelTest`: add the import `com.mitenko.hiitcounter.domain.model.EntryType`, then append:
```kotlin
    @Test
    fun `exposes the entry type and follows a change`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, name = "Burpees")))
        val vm = SettingsPagerViewModel(handle, repo)
        assertEquals(EntryType.WORKOUT, vm.type.value)
        repo.setType(1, EntryType.CHECK_IN)
        assertEquals(EntryType.CHECK_IN, vm.type.value)
    }
```

`SettingsPagerTest`: add the import `com.mitenko.hiitcounter.domain.model.EntryType`. After `private fun handle() = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))`, add:
```kotlin

    private fun checkInRepo() = FakeEntryRepository(listOf(testEntry(1, name = "Stretch", type = EntryType.CHECK_IN)))
```
Then append:
```kotlin
    @Test
    fun `a check-in-only entry shows only the Progression and Current tabs`() {
        show(initial = SettingsPage.CURRENT, repo = checkInRepo())
        tab(SettingsPage.TIMING).assertDoesNotExist()
        tab(SettingsPage.CUES).assertDoesNotExist()
        tab(SettingsPage.CURRENT).assertIsSelected()
        compose.onNodeWithTag("value_Best streak").assertIsDisplayed()
        compose.onNodeWithTag("value_Current total").assertDoesNotExist()
        tab(SettingsPage.PROGRESSION).performClick()
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_Check-in window (hours)").assertIsDisplayed()
        compose.onNodeWithTag("value_Starting total").assertDoesNotExist()
    }

    @Test
    fun `a hidden page argument opens the first visible tab`() {
        show(initial = SettingsPage.TIMING, repo = checkInRepo())
        tab(SettingsPage.PROGRESSION).assertIsSelected()
        compose.onNodeWithTag("value_Check-in window (hours)").assertIsDisplayed()
    }
```

`ProgressionSettingsScreenTest`: add the import `androidx.compose.ui.test.onNodeWithContentDescription`. Replace:
```kotlin
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
```
with:
```kotlin
    private fun show(initial: ProgressionConfig, windowOnly: Boolean = false) {
        draft = ProgressionDraft.from(initial)
        compose.setContent {
            HiitTheme {
                val validation = SettingsValidator.progression(draft.toConfig())
                ProgressionPageContent(
                    draft, validation, SaveStatus.of(validation, failed = false),
                    onChange = { draft = it(draft) }, onChangeNow = { draft = it(draft) }, onReset = { resets++ },
                    windowOnly = windowOnly,
                )
            }
        }
    }
```
Then append:
```kotlin
    @Test
    fun `window only shows just the check-in window and keeps the other values`() {
        show(ProgressionConfig(cap = 80, holdAt = 66), windowOnly = true)
        compose.onNodeWithTag("value_Check-in window (hours)").assertIsDisplayed()
        listOf("Starting total", "Floor (min)", "Cap (max)", "Hold at", "Hold for (check-ins)", "Penalty rate (hours per rep)")
            .forEach { compose.onNodeWithTag("value_$it").assertDoesNotExist() }
        compose.onNodeWithTag("switch_Hold").assertDoesNotExist()
        compose.onNodeWithTag("reset_defaults").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase Check-in window (hours)").performClick()
        assertEquals(37, draft.windowHours)
        assertEquals(80, draft.cap)
        assertEquals(66, draft.holdAt)
        compose.onNodeWithTag("save_status").assertTextEquals("Saved")
    }
```

`CurrentStatePageTest`: add the import `androidx.compose.ui.test.onNodeWithContentDescription`, then replace:
```kotlin
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
```
with:
```kotlin
    private fun show(showTotal: Boolean = true) {
        compose.setContent {
            HiitTheme {
                CurrentStatePageContent(
                    draft, ValidationResult(), SaveStatus.SAVED, ZoneOffset.UTC, now = { Instant.parse("2026-09-24T12:00:00Z") },
                    onChange = { draft = it(draft) },
                    onChangeNow = { immediate++; draft = it(draft) },
                    onResetProgress = { resets++ },
                    showTotal = showTotal,
                )
            }
        }
    }
```
Then append:
```kotlin
    @Test
    fun `without the total the page shows the streaks, the date and Reset progress`() {
        show(showTotal = false)
        compose.onNodeWithTag("value_Current total").assertDoesNotExist()
        compose.onNodeWithTag("value_Best streak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("value_Current streak").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("last_check_in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reset_progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase Best streak").performScrollTo().performClick()
        assertEquals(25, draft.best)
        assertEquals(65, draft.total) // a streak edit keeps the stored total in the draft, so the save writes it back unchanged
    }
```

- [ ] **Step 2: Run red** — Run (label `T8-1-RED`, ~4 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.*"`. Expected: compile FAIL, "Unresolved reference 'tabIndex'", "'type'" (on `SettingsPagerViewModel`), "No parameter with name 'windowOnly' found" and "'showTotal'".

- [ ] **Step 3: Implement**

`ui/settings/SettingsPager.kt` (complete). The exit flushes (back, ←, ON_STOP, `onEntryGone`) and the flush on every page change are unchanged; the tabs move into `SettingsTabs`, which waits for the type:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.mitenko.hiitcounter.domain.model.EntryType
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

/**
 * The pager's own state: the entry name for the "<name> settings" title (spec R3 §4) and the
 * entry type, which decides the visible tabs (R4 §4.6). Both are null until loaded.
 */
@HiltViewModel
class SettingsPagerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val name: StateFlow<String?> = repo.entry(entryId).filterNotNull().map { it.name }
        .stateIn<String?>(viewModelScope, SharingStarted.Eagerly, null)

    val type: StateFlow<EntryType?> = repo.entry(entryId).filterNotNull().map { it.type }
        .stateIn<EntryType?>(viewModelScope, SharingStarted.Eagerly, null)
}

/** Spec R4 §4.6: where this page opens among the visible [pages]; a hidden page opens the first tab. */
internal fun SettingsPage.tabIndex(pages: List<SettingsPage>): Int = pages.indexOf(this).coerceAtLeast(0)

/**
 * An entry's settings on one screen (spec R3 §4): ← and "<name> settings", tabs over a
 * HorizontalPager. Each page keeps its own ViewModel, keyed on this back-stack entry, so each has
 * its own saved-state draft and AutoSaver (see the R3 plan's layout decision). This route only hosts
 * them. It flushes the three auto-saving pages on every page change and on every exit: back, ←,
 * ON_STOP and an onEntryGone pop (§6.2). A check-in-only entry shows only Progression · Current (R4 §4.6).
 */
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
    val type by pagerVm.type.collectAsStateWithLifecycle()
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

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Until the name has loaded, the existing "Settings" string stands in, so there's never a bare " settings".
            val title = name?.let { stringResource(R.string.entry_settings_title, it) } ?: stringResource(R.string.settings)
            SettingsTopBar(title, onBack = leave)
            // The visible tabs depend on the type, so they wait until the entry has loaded (spec R4 §4.6).
            type?.let { SettingsTabs(it, initialPage, onPageChange = flushAll, timingVm, progressionVm, currentVm, cuesVm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.SettingsTabs(
    type: EntryType,
    initialPage: SettingsPage,
    onPageChange: () -> Unit,
    timingVm: TimingSettingsViewModel,
    progressionVm: ProgressionSettingsViewModel,
    currentVm: CurrentStateViewModel,
    cuesVm: CuesSettingsViewModel,
) {
    val pages = SettingsPage.visibleFor(type)
    val checkInOnly = type == EntryType.CHECK_IN
    // rememberPagerState is saveable, so the page survives rotation and process recreation (R3 §4).
    val pagerState = rememberPagerState(initialPage = initialPage.tabIndex(pages)) { pages.size }
    val latestOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pagerState) {
        // Tab taps and swipes alike: leaving a page writes what it had pending.
        snapshotFlow { pagerState.currentPage }.drop(1).collect { latestOnPageChange() }
    }
    val scope = rememberCoroutineScope()
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        pages.forEachIndexed { index, page ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(stringResource(page.tab)) },
                modifier = Modifier.testTag("tab_${page.name}"),
            )
        }
    }
    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).testTag("settings_pager")) { index ->
        when (pages[index]) {
            SettingsPage.TIMING -> TimingPage(timingVm)
            SettingsPage.PROGRESSION -> ProgressionPage(progressionVm, windowOnly = checkInOnly)
            SettingsPage.CURRENT -> CurrentStatePage(currentVm, showTotal = !checkInOnly)
            SettingsPage.CUES -> CuesPage(cuesVm)
        }
    }
}
```

`ui/settings/ProgressionSettings.kt`. Replace everything from the line `/** The Progression page inside the pager (spec R3 §4). */` to the end of the file with:
```kotlin
/**
 * The Progression page inside the pager (spec R3 §4). [windowOnly] is a check-in-only entry
 * (R4 §4.6): only the check-in window shows. The hidden fields keep their stored values and stay in
 * the draft that is validated and saved. Reset to defaults is hidden too, since it would reset them
 * (plan Spec note 7).
 */
@Composable
fun ProgressionPage(vm: ProgressionSettingsViewModel, windowOnly: Boolean = false) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    draft?.let {
        ProgressionPageContent(
            it, validation, status, onChange = vm::update, onChangeNow = vm::updateNow, onReset = vm::resetToDefaults,
            windowOnly = windowOnly,
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
    windowOnly: Boolean = false,
) {
    val errors = validation.errors
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        if (!windowOnly) {
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
        }
        IntStepperField(
            stringResource(R.string.window_hours), draft.windowHours, FieldRanges.WINDOW_HOURS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(windowHours = f(it.windowHours)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(windowHours = f(it.windowHours)) } },
            error = errors[Field.WINDOW_HOURS], info = stringResource(R.string.info_window),
        )
        if (!windowOnly) {
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

`ui/settings/CurrentStateSettings.kt`. Replace:
```kotlin
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
```
with:
```kotlin
/**
 * The Current page inside the pager (spec R3 §4). Without [showTotal] (a check-in-only entry,
 * R4 §4.6) the total row is hidden; the draft keeps the stored total, so saves write it back
 * unchanged and the hold count is kept (plan Spec note 8).
 */
@Composable
fun CurrentStatePage(vm: CurrentStateViewModel, showTotal: Boolean = true) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    draft?.let {
        CurrentStatePageContent(
            it, validation, status, vm.zone, vm::now,
            onChange = vm::update, onChangeNow = vm::updateNow, onResetProgress = vm::resetProgress,
            showTotal = showTotal,
        )
    }
}
```
Then replace:
```kotlin
    onResetProgress: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
```
with:
```kotlin
    onResetProgress: () -> Unit,
    showTotal: Boolean = true,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
```
Then replace:
```kotlin
    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        IntStepperField(
            stringResource(R.string.current_total), draft.total, FieldRanges.TOTAL, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(total = f(it.total)) } },
            onDialogUpdate = { f -> onChangeNow { it.copy(total = f(it.total)) } },
            error = validation.errors[Field.TOTAL], hint = validation.hints[Field.TOTAL],
            info = stringResource(R.string.info_total_reps),
        )
```
with:
```kotlin
    SettingsPageLayout(footer = { SaveStatusLine(status) }) {
        if (showTotal) {
            IntStepperField(
                stringResource(R.string.current_total), draft.total, FieldRanges.TOTAL, ValueInput.WHOLE,
                onUpdate = { f -> onChange { it.copy(total = f(it.total)) } },
                onDialogUpdate = { f -> onChangeNow { it.copy(total = f(it.total)) } },
                error = validation.errors[Field.TOTAL], hint = validation.hints[Field.TOTAL],
                info = stringResource(R.string.info_total_reps),
            )
        }
```

- [ ] **Step 4: Run green** — Run (label `T8-1-GREEN`, ~5 min): full suite + count. Expected: **383 tests**. The 11 existing `SettingsPagerTest` cases stay green: a Workout still shows all four tabs, `page 2 opens on Current` still maps to index 2, and every flush path is unchanged.

- [ ] **Step 5: Commit** — diff-review `SettingsPager.kt` against `main` (the ViewModel gains `type`; the route's flush and exit code is byte-identical; the tab and pager code moved into `SettingsTabs` and iterates `pages`), then `git add -A && git commit -m "feat(settings): Progression and Current tabs only for check-in-only entries"`

### Subtask 8.2: The Voice switch and its unavailable text

**Files:** Create `test/testutil/FakeVoiceAvailability.kt`; replace `ui/settings/CuesSettings.kt`; edit `ui/common/SettingsComponents.kt`; edit and append to `test/ui/settings/CuesSettingsViewModelTest.kt`, `test/ui/settings/CuesPageTest.kt`; edit `test/ui/settings/SettingsPagerTest.kt`.

- [ ] **Step 1: Failing tests**

`test/testutil/FakeVoiceAvailability.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.platform.VoiceAvailability

/** Answers the Cues page's device check with [available]. */
class FakeVoiceAvailability(private val available: Boolean = true) : VoiceAvailability {
    var checks = 0

    override suspend fun check(): Boolean {
        checks++
        return available
    }
}
```

`CuesSettingsViewModelTest`: add the import `com.mitenko.hiitcounter.testutil.FakeVoiceAvailability`. In all three existing tests, replace `CuesSettingsViewModel(handle, repo)` with `CuesSettingsViewModel(handle, repo, FakeVoiceAvailability())` (three occurrences, one per test). Then append:
```kotlin
    @Test
    fun `the voice switch persists at once and the device check is exposed`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val voice = FakeVoiceAvailability(available = false)
        val vm = CuesSettingsViewModel(handle, repo, voice)
        assertEquals(false, vm.voiceAvailable.value)
        assertEquals(1, voice.checks)
        vm.setVoice(true)
        assertEquals(CueConfig(voice = true), repo.find(1).cues)
    }
```

`CuesPageTest`: add the imports `androidx.compose.ui.test.assertTextEquals` and `com.mitenko.hiitcounter.testutil.FakeVoiceAvailability`. Replace:
```kotlin
        val vm = CuesSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo)
```
with:
```kotlin
        val vm = CuesSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo, FakeVoiceAvailability())
```
Then append:
```kotlin
    @Test
    fun `the voice switch saves at once and says when the device has no voice`() {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CuesSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo, FakeVoiceAvailability(available = false))
        compose.setContent { HiitTheme { CuesPage(vm) } }
        compose.onNodeWithTag("support_Voice").assertTextEquals("Voice not available on this device")
        compose.onNodeWithTag("switch_Voice").performClick()
        compose.waitForIdle()
        assertEquals(CueConfig(voice = true), repo.find(1).cues)
        compose.onNodeWithContentDescription("About Voice").assertExists()
    }
```

`SettingsPagerTest`: add the import `com.mitenko.hiitcounter.testutil.FakeVoiceAvailability`. Replace:
```kotlin
        val cuesVm = CuesSettingsViewModel(handle(), repo)
```
with:
```kotlin
        val cuesVm = CuesSettingsViewModel(handle(), repo, FakeVoiceAvailability())
```
Then, in `rows`, replace:
```kotlin
            InfoRow(R.string.vibration, R.string.info_vibration),
        ),
    )
```
with:
```kotlin
            InfoRow(R.string.vibration, R.string.info_vibration),
            InfoRow(R.string.voice, R.string.info_voice),
        ),
    )
```

- [ ] **Step 2: Run red** — Run (label `T8-2-RED`, ~3 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.*"`. Expected: compile FAIL, "Too many arguments for constructor 'CuesSettingsViewModel'", "Unresolved reference 'voiceAvailable'" and "'setVoice'".

- [ ] **Step 3: Implement**

`ui/common/SettingsComponents.kt`. Replace:
```kotlin
/**
 * A labelled switch with the stepper rows' spacing (spec R2 §8.1) and an optional ⓘ after the
 * label (R3 §7.1). The switch is tagged `switch_<label>`.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    info: String? = null,
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
with:
```kotlin
/**
 * A labelled switch with the stepper rows' spacing (spec R2 §8.1) and an optional ⓘ after the
 * label (R3 §7.1). The switch is tagged `switch_<label>`. [supportingText] goes under the row,
 * tagged `support_<label>` (the Voice switch's "not available", R4 §4.7).
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    info: String? = null,
    supportingText: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            info?.let { InfoTag(title = label, text = it) }
            Spacer(Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("switch_$label"))
        }
        supportingText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp).testTag("support_$label"),
            )
        }
    }
}
```
(`Column` is already imported in `SettingsComponents.kt`.)

`ui/settings/CuesSettings.kt` (complete):
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
import com.mitenko.hiitcounter.domain.model.EntryType
import com.mitenko.hiitcounter.platform.VoiceAvailability
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.SettingsPageLayout
import com.mitenko.hiitcounter.ui.common.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    voice: VoiceAvailability,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val cues: StateFlow<CueConfig> = repo.entry(entryId).filterNotNull().map { it.cues }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CueConfig())

    /** Whether this device can say the Voice cue; null while the check runs (spec R4 §4.7, plan Spec note 4). */
    private val _voiceAvailable = MutableStateFlow<Boolean?>(null)
    val voiceAvailable: StateFlow<Boolean?> = _voiceAvailable.asStateFlow()

    /** Serialises the read-modify-write below so two quick toggles can't overwrite each other. */
    private val mutex = Mutex()

    init {
        viewModelScope.launch {
            // A check-in-only entry has no Cues tab (R4 §4.6), so skip the throwaway engine; voiceAvailable stays null.
            if (repo.entry(entryId).first()?.type == EntryType.CHECK_IN) return@launch
            _voiceAvailable.value = voice.check()
        }
    }

    fun setSound(on: Boolean) = edit { it.copy(sound = on) }

    fun setVibration(on: Boolean) = edit { it.copy(vibration = on) }

    /** Spec R4 §4.7: saved at once, like Sound and Vibration, whether or not the device has a voice. */
    fun setVoice(on: Boolean) = edit { it.copy(voice = on) }

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
    val voiceAvailable by vm.voiceAvailable.collectAsStateWithLifecycle()
    SettingsPageLayout {
        SwitchRow(stringResource(R.string.sound), cues.sound, vm::setSound, info = stringResource(R.string.info_sound))
        SwitchRow(stringResource(R.string.vibration), cues.vibration, vm::setVibration, info = stringResource(R.string.info_vibration))
        // Spec R4 §4.7: the switch still saves without a usable voice; the text says why nothing will be heard.
        SwitchRow(
            stringResource(R.string.voice), cues.voice, vm::setVoice, info = stringResource(R.string.info_voice),
            supportingText = if (voiceAvailable == false) stringResource(R.string.voice_unavailable) else null,
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T8-2-GREEN`, ~5 min): full suite + count. Expected: **385 tests**. `SwitchRowTest` and `ProgressionSettingsScreenTest`'s Hold switch tests stay green (no `supportingText`, same row).

- [ ] **Step 5: Commit** — diff-review `CuesSettings.kt` (new: the `voice` parameter, `voiceAvailable` (not checked for a check-in-only entry), `setVoice`, the Voice row; the Mutex edit is unchanged), then `git add -A && git commit -m "feat(settings): Voice switch with a not-available note"`

**Task 8 gate:** Run (label `T8-GATE`, ~5 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **385 tests**, no lint errors, and no `UnusedResources` warnings left for the R4 strings. `assembleDebug` compiles the Hilt graph with `CuesSettingsViewModel`'s `VoiceAvailability`.

---

## Task 9: Docs, full verification, device check, squash and PR (§7, §8)

### Subtask 9.1: Docs and full verification

**Files:** Edit `claude.md` and `docs/superpowers/specs/2026-09-29-checkin-voice-design.md`.

- [ ] **Step 1: Update `claude.md`** (read it first) with these exact edits:
  1. Replace:
     ```markdown
     - **Settings pager revision (approved, amends both):** `docs/superpowers/specs/2026-09-28-settings-pager-design.md` — read all three.
     ```
     with:
     ```markdown
     - **Settings pager revision (approved, amends both):** `docs/superpowers/specs/2026-09-28-settings-pager-design.md` — read all three.
     - **Check-in / voice revision (approved, amends all three):** `docs/superpowers/specs/2026-09-29-checkin-voice-design.md` — read all four.
     ```
  2. Replace:
     ```markdown
     - Check-in happens on **Start**, max once per local calendar day per entry, atomically in one Room transaction using the entry's own progression.
     ```
     with:
     ```markdown
     - Check-in happens on **Check in** or on **Start** (Start still checks in, after the service has started; a second call the same day is a no-op), max once per local calendar day per entry, atomically in one Room transaction using the entry's own progression and type.
     - Entries are **Workout** (timer + reps) or **Check-in only** (`entry.type`; streaks and date only, the total and hold count never change). The type is chosen at create time and switched in Entry Settings without losing any values; check-in-only entries have no Timing or Cues pages, and their pager shows only Progression (window) · Current (no total).
     ```
  3. Replace:
     ```markdown
     - Timer: `TimerController` singleton owns the engine; `TimerService` (specialUse foreground service + partial wake lock) only hosts it. ViewModels never bind to the service.
     ```
     with:
     ```markdown
     - Timer: `TimerController` singleton owns the engine; `TimerService` (specialUse foreground service + partial wake lock) only hosts it. ViewModels never bind to the service.
     - Voice cue (`cue_voice`): `TimerController` puts each WORK set's reps on `Cue.PhaseStart`; `CuePlayer` says them after the beep through `platform/CueSpeaker` (TextToSpeech, English), inside the beeps' ducking focus. `TimerService` creates the speaker only for a voice run. The manifest's `<queries>` TTS_SERVICE entry is required for Android 11+.
     ```
  4. Replace:
     ```markdown
     - Plans: `docs/superpowers/plans/2026-09-24-hiit-counter.md` (v1), `docs/superpowers/plans/2026-09-25-multi-entry.md` (multi-entry), `docs/superpowers/plans/2026-09-28-settings-pager.md` (settings pager).
     ```
     with:
     ```markdown
     - Plans: `docs/superpowers/plans/2026-09-24-hiit-counter.md` (v1), `docs/superpowers/plans/2026-09-25-multi-entry.md` (multi-entry), `docs/superpowers/plans/2026-09-28-settings-pager.md` (settings pager), `docs/superpowers/plans/2026-09-29-checkin-voice.md` (check-in / voice).
     ```
  5. Replace:
     ```markdown
     - Room schema changes (current version 2):
     ```
     with:
     ```markdown
     - Room schema changes (current version 3):
     ```
     (only these words change; the rest of that line stays as it is).

- [ ] **Step 2: Record the spec notes in the spec** — `docs/superpowers/specs/2026-09-29-checkin-voice-design.md`. Replace:
```markdown
- TDD subtasks;
- a phone backup before every install.
```
with:
```markdown
- TDD subtasks;
- a phone backup before every install.

## 9. Implementation notes (from the plan, confirmed with the user)

- **Start order (§4.1).** "Calls `checkIn` first" means before the timer starts. As in v1 §4, the foreground service is started and confirmed first, then `checkIn` runs, then the timer. After a manual Check in, Start's call returns `AlreadyToday`.
- **`CueSpeaker.speak` is `suspend` (§5).** It returns when the utterance ends (done, error or replaced), which is how the `UtteranceProgressListener` reaches `CuePlayer`. `CuePlayer` wraps it in a 3 s timeout.
- **Speech follows the beep (§5).** With Sound on, the number is said once the 600 ms WORK tone has played; with Sound off, at once. Focus is abandoned when both the tone's hold and the speech have ended.
- **Voice availability in Settings (§4.7).** `platform/VoiceAvailability` initialises a throwaway engine (5 s timeout) for the Cues page. The note shows only once the check says unavailable.
- **`type` storage (§3.2).** A plain TEXT column mapped in `EntryMapping`, not a `TypeConverter`, so an unknown value is repaired to WORKOUT with the entry id logged.
- **Check-in-only check-ins (§3.1).** The stored total column is never written, so a NULL total stays NULL.
- **Check-in-only Progression tab (§4.6).** Reset to defaults is hidden with the other fields. The status line stays.
- **Check-in-only Current tab (§4.6).** The draft keeps the stored total and writes it back, so a NULL total becomes the starting-total value on the first streak edit (as on the R3 Current page). The hold count is kept.
- **Buttons (§4.1).** Check in and Start disable each other while either call is in flight. Check in and the Type dialog's OK finish their write even if the screen is left at once. The "Checked in today" line under the table stays.
- **Type dialog.** OK with the current type selected writes nothing.
- **Manifest (defect in §5).** Android 11+ needs `<queries>` for `android.intent.action.TTS_SERVICE`, or no engine is visible.
- **Short prepare.** The engine initialises asynchronously; if the first work set starts before it's ready (e.g. PREPARE 0), that set is silent.
- **Open item.** `info_last_check_in` still says "When you last pressed Start." Check in now sets it too; the text is unchanged until the user decides.
```

- [ ] **Step 3: Full verification** — Run (label `T9-1-FULL`, ~6 min): `./gradlew clean assembleDebug testDebugUnitTest lintDebug`, then the test-count command. Expected: `BUILD SUCCESSFUL`, **385 tests** (3 skipped on Windows), no lint errors. Record the count for the PR.

- [ ] **Step 4: Commit** — `git add -A && git commit -m "docs: check-in and voice notes in claude.md and the spec"`

### Subtask 9.2: Device verification on the Pixel 9a (over the R3 install)

The device is the user's Pixel 9a, serial **59251JEBF12416**. It has the R3 build installed, with real entries (Pushups and Bridges among them).
- **Never uninstall the app and never clear its data.** That would destroy the data this step exists to migrate.
- Install only **over** the existing build.
- **Don't tap Check in or Start on the user's real entries** unless the user asks; that records a real check-in. Every interactive check below uses scratch entries.
- Don't start or kill emulators or adb servers you didn't start.

- [ ] **Step 1: Device and backup**
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
adb devices                                   # 59251JEBF12416 must be listed as "device"
adb -s 59251JEBF12416 shell am force-stop com.mitenko.hiitcounter   # a stopped app has no open DB and no in-flight writes
adb -s 59251JEBF12416 exec-out run-as com.mitenko.hiitcounter sh -c 'tar -cf - files databases 2>/dev/null' > "$SCRATCH/claude-r3-backup.tar"
tar -tf "$SCRATCH/claude-r3-backup.tar"       # must list databases/hiit.db (plus -wal/-shm if present)
adb -s 59251JEBF12416 shell am start -W -n com.mitenko.hiitcounter/.MainActivity
adb -s 59251JEBF12416 exec-out screencap -p > "$SCRATCH/claude-r3-list.png"
echo "CLAUDE-DEVICE-BACKUP DONE"
```
**Restore, only when the user asks.** It puts the backup back over the app's `files` and `databases`. Force-stop the app first:
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
adb -s 59251JEBF12416 shell am force-stop com.mitenko.hiitcounter
# Remove the current database and its WAL/SHM first, so no v3 journal is replayed over the restored v2 file.
adb -s 59251JEBF12416 shell run-as com.mitenko.hiitcounter rm -f databases/hiit.db databases/hiit.db-wal databases/hiit.db-shm
adb -s 59251JEBF12416 exec-in run-as com.mitenko.hiitcounter tar -xf - < "$SCRATCH/claude-r3-backup.tar"
echo "CLAUDE-DEVICE-RESTORE DONE"
```
If the R4 build is still installed, Room migrates the restored v2 database (2 → 3) again on the next launch. That is expected.
- If the device isn't listed, ask the user to connect it.
- If `run-as` reports that the package is not debuggable, the installed build isn't this machine's debug build. `installDebug` would then fail on the signature, so **stop and ask**.
- If the tar is empty or lacks `databases/hiit.db`, stop and ask. Never install without a good backup.
- Open `claude-r3-list.png` and record every entry's name, its order and "Reps N".
- If `sqlite3` is on the PATH, extract a copy and record the rows:
  ```bash
  SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
  mkdir -p "$SCRATCH/claude-r3-backup" && tar -xf "$SCRATCH/claude-r3-backup.tar" -C "$SCRATCH/claude-r3-backup"
  sqlite3 "$SCRATCH/claude-r3-backup/databases/hiit.db" "SELECT id,name,position,total,best_streak,current_streak,hold_count,last_check_in,cue_sound,cue_vibration FROM entry ORDER BY position"
  echo "CLAUDE-DEVICE-R3-ROWS DONE"
  ```
  Otherwise, ask the user to note each entry's table (total, streaks, last check-in).

- [ ] **Step 2: Install over R3** — Run (label `T9-2-INSTALL`, ~3 min): `ANDROID_SERIAL=59251JEBF12416 ./gradlew installDebug`, using the logging convention with the inline `SCRATCH=` assignment. Expected: `Installed on 1 device.`
  - If it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signature mismatch) or a version-downgrade error, **stop and ask the user**. Never uninstall.

- [ ] **Step 3: Migration check**
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
adb -s 59251JEBF12416 shell am start -W -n com.mitenko.hiitcounter/.MainActivity
adb -s 59251JEBF12416 exec-out screencap -p > "$SCRATCH/claude-r4-list.png"
adb -s 59251JEBF12416 exec-out run-as com.mitenko.hiitcounter sh -c 'tar -cf - databases 2>/dev/null' > "$SCRATCH/claude-r4-after.tar"
echo "CLAUDE-DEVICE-MIGRATION DONE"
```
Expected:
- `claude-r4-list.png` shows the same entries, in the same order, each still reading "Reps N" with the same N as `claude-r3-list.png`.
- If `sqlite3` is available, extract `claude-r4-after.tar` into `$SCRATCH/claude-r4-after` and run the Step 1 query plus `,type,cue_voice`: every row is unchanged, with `type` = `WORKOUT` and `cue_voice` = 0.
- The app opens without a crash. A failed migration throws at the first database access, so if it crashes, **stop**, keep the backup, and report `adb -s 59251JEBF12416 logcat -d | grep -i room`.

- [ ] **Step 4: Manual checklist** (spec §7 device checks). Report each item to the user as pass or fail. Fix each failure TDD-style in a new commit on the branch, then re-run 9.1 Step 3 before continuing. **Before any re-install**, repeat Step 1's force-stop and backup to a new file (`claude-r4-backup-N.tar`, N = 1, 2, …), verify it with `tar -tf` (it must list `databases/hiit.db`), then `installDebug`.
  1. **Workouts survive:** Pushups and Bridges (and every other real entry) open with the same table. Each shows **Check in** and **Start** side by side; Check in reads "Checked in ✓" and is disabled only if that entry was already checked in today. Entry Settings → Type reads "Workout", with all four page rows. Don't tap Check in or Start here.
  2. **Check-in-only entry:** on the list, + → name "R4 habit", choose **Check-in only** → OK. The entry screen shows Last Check In, Best CI Streak, Curr CI Streak and Today, no rep rows, no Total Reps and one **Check in**. Tap it: it turns "Checked in ✓" and disabled, and Curr CI Streak reads 1. Back on the list, the row reads "Streak 1 ✓".
  3. **Its settings:** ⚙ on R4 habit → Type reads "Check-in only"; there are no Timing or Cues rows. Progression opens the pager with only **Progression · Current**; Progression shows only Check-in window (hours); Current shows no total. ⓘ on Type opens its text.
  4. **Check in, then Start, on a Workout:** + → "R4 workout" (Workout). ⚙ → Timing: PREPARE 0:05, SETS 2, WORK 0:10, REST 0:05. Cues: turn **Voice** on (it must not show "Voice not available on this device"; if it does, check that a TTS engine with English is installed and report). Back on the entry: tap **Check in** → the table stays at 24 / 24, Total Reps 48, and the button reads "Checked in ✓". Tap **Start** → the timer runs; after it ends, Curr CI Streak is still 1 (no second check-in).
  5. **Voice with the screen off and music playing:** ringer on, system volume up (the voice and beeps use the system stream). Ask the user to start music, then tap Start on R4 workout and lock the phone. The user confirms they hear the WORK beep followed by "twenty-four" at each work start, with the music ducked and restored after each cue, and that the run finishes on time. If it's silent, report `adb -s 59251JEBF12416 logcat -d -s AndroidCueSpeaker CuePlayer`.
  6. **Type round trip:** R4 workout → Type → Check-in only → OK: the Timing and Cues rows disappear and the entry screen loses its rep rows. Switch back to Workout: Timing still reads SETS 2, WORK 0:10, and Voice is still on.
  7. **Clean-up:** delete R4 habit and R4 workout (Entry Settings → Delete → confirm). The real entries are untouched.

### Subtask 9.3: Squash and PR

Follow the project PR protocol (`claude.md`, spec §8). No AI attribution anywhere.

- [ ] **Step 1: Verify the build** — Run (label `T9-3-VERIFY`, ~4 min): `./gradlew testDebugUnitTest lintDebug`. Expected: **385 tests**, no lint errors.

- [ ] **Step 2: Sync main and rebase**
```bash
git fetch origin
git checkout main && git pull --ff-only && git checkout feat/checkin-voice
git rebase main
```
If the rebase conflicts, stop and report. If `git pull` brought new commits into `main`, re-run Step 1 after the rebase.

- [ ] **Step 3: Squash to one commit.** `main` already contains the spec and this plan (merged before 1.1), so `git reset --soft $(git merge-base main HEAD)` squashes **only this branch's commits**. Write `$SCRATCH/claude-commit-msg.txt` (the scratchpad path above):
```text
Check-in-only entries, separate Check in and Start, and a voice cue

- EntryType (Workout / Check-in only) on Entry; Room v3 adds entry.type (TEXT, default WORKOUT, unknown values read as WORKOUT) and entry.cue_voice (MIGRATION_2_3, exported 3.json)
- RepProgression.checkIn(countsReps): check-in-only entries move the date and streaks only; the total and hold count stay, and the stored total is never written
- Repository: create(name, type), setType (one UPDATE, everything else kept), setCues writes the voice, checkIn passes countsReps, duplicate copies type and voice
- Entry screen: Check in (outlined, "Checked in ✓" once done today, in-flight guard) next to Start (unchanged start flow); check-in-only entries show the streak rows and one Check in
- Entry list "Streak N" for check-in-only rows; the create dialog picks Workout or Check-in only
- Entry Settings Type row with an info tag and a radio dialog; check-in-only entries hide Timing and Cues, and the pager shows only Progression (window) and Current (no total)
- Voice cue: work starts carry the set's reps; CuePlayer says them after the beep through a TextToSpeech CueSpeaker inside the cues' ducking focus (3 s timeout); TimerService holds the speaker only during a voice run; Cues page Voice switch with a not-available note; TTS_SERVICE <queries> for Android 11+
```
Then:
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
git reset --soft $(git merge-base main HEAD)   # this branch's commits only; the docs are already on main
git commit -F "$SCRATCH/claude-commit-msg.txt"
git log --format='%an <%ae>%n%B' -1        # author must be mitenko <mitenko@gmail.com>; no trailers
git show --stat HEAD | grep "3.json"       # the schema must be in the commit
echo "CLAUDE-SQUASH DONE"
```

- [ ] **Step 4: Ask the user before pushing.** Show them the commit message and the 9.2 checklist results. If they agree, write `$SCRATCH/claude-pr-body.md` first. It includes:
  - the commit bullets;
  - the test count from 9.1, noting the 3 Windows-skipped MigrationTestHelper tests that CI runs;
  - the migration check from 9.2 Step 3;
  - the checklist results from 9.2 Step 4, including the user's confirmation of the voice with the screen off;
  - the open item: `info_last_check_in` still mentions only Start (Spec note 15).

  Don't add an AI attribution line. Then:
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; mkdir -p "$SCRATCH"
git push --force-with-lease -u origin feat/checkin-voice
gh pr create --base main --head feat/checkin-voice --title "Check-in-only entries, separate Check in and Start, and a voice cue" --body-file "$SCRATCH/claude-pr-body.md"
echo "CLAUDE-PR DONE"
```

**Task 9 gate:** the PR is open and CI (`testDebugUnitTest lintDebug`) is green on it. CI must run all three MigrationTestHelper tests, not skip them. Check the CI test report.

---

## Expected test counts

| After | Added | Removed | Cumulative |
|---|---|---|---|
| 1.1 baseline | | | **333** |
| 2.1 `ConfigsTest` 1 · `EntryTest` 1 | 2 | | 335 |
| 2.2 `RepProgressionTest` | 4 | | 339 |
| 2.3 `TimerControllerTest` (one assertion updated) | 1 | | 340 |
| 3.1 `HiitDatabaseTest` | 3 | | 343 |
| 3.2 mapping 2 · migrator 1 | 3 | | 346 |
| 3.3 `RoomEntryRepositoryTest` | 6 | | 352 |
| 4.1 `CuePlayerTest` | 5 | | 357 |
| 4.2 `VoicePolicyTest` | 1 | | 358 |
| 5.1 `EntryViewModelTest` | 5 | | 363 |
| 5.2 `EntryScreenTest` (`InfoTextsTest` list updated) | 5 | | 368 |
| 6.1 list ViewModel 1 · list screen 1 | 2 | | 370 |
| 6.2 list ViewModel 1 · list screen 1 | 2 | | 372 |
| 7.1 `EntrySettingsViewModelTest` 2 · `SettingsPagesTest` 1 | 3 | | 375 |
| 7.2 `EntrySettingsScreenTest` | 2 | | 377 |
| 8.1 `SettingsPagesTest` 1 · pager ViewModel 1 · `SettingsPagerTest` 2 · Progression screen 1 · `CurrentStatePageTest` 1 | 6 | | 383 |
| 8.2 `CuesSettingsViewModelTest` 1 · `CuesPageTest` 1 | 2 | | **385** |

Windows skips: 2 up to 2.3, then 3 (the three file-based `MigrationTestHelper` tests).

## Spec Coverage

| Spec | Where |
|---|---|
| §1–§2 purpose and scope (out of scope: drag-to-reorder, history, byline "X× this week", other voice languages, none touched) | all tasks |
| §3.1 `EntryType`, `Entry.type` default WORKOUT, `CueConfig.voice` | 2.1 |
| §3.1 `checkIn(…, countsReps)`: rules 1–4 decide outcome and streaks; total and hold count kept; `lastCheckIn` = now; `Missed.penalty` 0 | 2.2 (four cases, each chosen so counting reps would differ) |
| §3.2 migration SQL verbatim, version 3, `MIGRATIONS`, `3.json` committed, `1.json`/`2.json` byte-identical, no destructive fallback | 3.1 (schema test, in-memory SQL test, guarded helper test, `git diff --exit-code`) |
| §3.2 existing entries become voice-off Workouts; read repair of an unknown type; `cue_voice` default false | 3.1, 3.2 |
| §3.2 duplicate copies `type` and `cue_voice`; V1Migrator imports WORKOUT with the voice off | 3.3, 3.2 |
| §3.3 `checkIn` passes `countsReps` in its transaction; `setType` (gate, `EntryNotFound`, single UPDATE, switch back restores); `setCues` writes the voice; the fake mirrors all | 3.3; Spec note 6 |
| §4.1 table unchanged; Check in (outlined) → table updates → "Checked in ✓" disabled until the date changes; Start (filled) checks in first, `AlreadyToday` after a manual check-in; ≥ 48 dp; double-tap guard; unchanged cancel/throw, `dropUnlessResumed`, busy while starting, check-in failure path | 5.1, 5.2; Spec notes 1, 9, 10, 11 |
| §4.2 check-in-only: streak rows only, one Check in, ← and ⚙ | 5.2 |
| §4.3 list byline "Streak N" with ✓; Workouts unchanged | 6.1 |
| §4.4 segmented Workout \| Check-in only, default Workout; `create(name, type)`, `create(name)` = WORKOUT | 6.2, 3.3 |
| §4.5 Type row with subtitle and ⓘ above the page rows; radio dialog with OK/Cancel; OK saves at once; allowed while busy; `EntryNotFound` pops; Timing and Cues hidden for check-in only | 7.1, 7.2; Spec note 12 |
| §4.6 tabs Progression · Current only; `page` mapping, hidden page → first tab; Progression shows only the window; hidden fields keep stored values in the validated draft; Current hides the total and keeps it; auto-save unchanged | 8.1; Spec notes 7, 8 |
| §4.7 Voice switch with ⓘ, saved at once; "Voice not available on this device", switch still saves | 8.2; Spec note 4 |
| §5 `PhaseStart(phase, reps)` filled for WORK by `TimerController` | 2.3 |
| §5 `CueSpeaker` (`speak`, `available`, `shutdown`); TextToSpeech with `Locale.ENGLISH`, `USAGE_ASSISTANCE_SONIFICATION`, `QUEUE_FLUSH`, "12" | 4.1 (interface), 4.2 (Android implementation); Spec note 2 |
| §5 lifecycle: created for a voice snapshot, shut down when the run ends or the service stops; init failure → unavailable, run continues | 4.2 (`VoicePolicy`, `TimerService`); Spec notes 13, 14 |
| §5 `CuePlayer`: beep then reps when voice on and available; same ducking focus; abandoned after the utterance, 3 s timeout; speech never blocks the engine; wake lock rules unchanged | 4.1 (five tests); 4.2 (TimerService diff review); Spec note 3 |
| §6 strings verbatim | 5.2 (`strings.xml`), used in 5.2, 6.1, 6.2, 7.2, 8.2; `InfoTextsTest` |
| §7 domain tests | 2.2, 2.3 |
| §7 Room tests (helper 2 → 3 on CI, in-memory SQL, read repair, `setType` keeps every column, duplicate, check-in-only `checkIn`) | 3.1, 3.2, 3.3 |
| §7 service tests (`CuePlayer` with a fake `CueSpeaker`: WORK only with voice on and available, never when unavailable, focus released) | 4.1 |
| §7 ViewModel and Compose tests (Check in → "Checked in ✓"; Start without and after a check-in; check-in-only screen and list; create type choice; Type row saves; hidden tabs and `page` mapping; Voice switch and its text) | 5.1, 5.2, 6.1, 6.2, 7.1, 7.2, 8.1, 8.2 |
| §7 device (backup after a force-stop, install over R3, Pushups and Bridges survive as Workouts, check-in-only entry, Check in then Start, voice with the screen off and music) | 9.2 |
| §8 identity, no attribution, squash to one commit, PR, ask before pushing, TDD subtasks, phone backup | Global Constraints, 1.1, 9.2 Step 1, 9.3 |

## Self-Review

- **Spec coverage.** Every section from §3 to §8 maps to at least one subtask (table above), and each §7 test bullet has a named test:
  - "`countsReps = false` for the first, on-time, missed and already-today cases" → the four `without reps …` tests (2.2).
  - "`PhaseStart(WORK)` carries the set's reps" → `each work start carries its own set's reps …` (2.3).
  - "Read repair of an unknown type" → `an unknown type reads as a workout` (3.2); "`setType` keeps every other column" → `setType changes only the type, …` (3.3).
  - "focus is released" → `focus is held through the utterance and released when it ends` and the 3 s timeout test (4.1).
  - "Start without a check-in checks in, then starts" → the existing `a successful start freezes the snapshot and checks in this entry only`; "Start after a check-in" → `start after a check-in starts with no second check-in` (5.1).
- **Placeholder scan.** No step says "TBD", "similar to" or "add appropriate …".
  - Every new file and every substantially grown file is shown complete.
  - Every other edit quotes its exact before/after block, checked against `main` or the state an earlier subtask leaves (`EntrySettings.kt` in 7.2 is shown complete over 7.1's edits).
  - The only external value the plan can't pin is the generated `3.json`, which Room exports and 3.1 verifies (`DEFAULT 'WORKOUT'`, `DEFAULT 0`, old schemas unchanged).
- **Lessons from the R3 execution.**
  - No test uses `advanceUntilIdle()`; the `backgroundScope` tests (`TimerControllerTest`, `CuePlayerTest`) advance with `advanceTimeBy` + `runCurrent`.
  - No new `AutoSaver.write` or `exclusive` caller; the window-only and total-less pages reuse the R3 pipelines untouched.
  - Check in and `setType` run `NonCancellable`, so a confirmed write survives the ViewModel being cleared.
- **Type consistency.** These names are used identically in every task:
  - `EntryType.WORKOUT` / `EntryType.CHECK_IN`, `Entry.type`, `CueConfig.voice`, `EntryType.label`.
  - `RepProgression.checkIn(state, config, now, zone, countsReps)`, `Outcome.Missed(0)`.
  - `Cue.PhaseStart(phase, reps)`, `TimerController.withReps`.
  - `EntryEntity.type` (String) / `cueVoice`, `EntryEntity.entryType()`, `entryEntity(…, type)`, `HiitDatabase.MIGRATION_2_3(_SQL)`.
  - `EntryDao.setCues(id, sound, vibration, voice)`, `EntryDao.setType(id, type: String)`, `EntryRepository.create(name, type)` / `setType(id, type)`.
  - `FakeEntryRepository.typeWrites` / `checkInGate` / `writeError`, `FakeCueSpeaker.spoken` / `finishUtterance()`, `FakeVoiceAvailability(available)`.
  - `CueSpeaker` (`available`, `speak`, `shutdown`), `AndroidCueSpeaker.ready`, `VoiceAvailability.check()`, `CueFocus` (`request`, `abandon`), `CuePlayer.speaker`, `CuePlayer.SPEECH_TIMEOUT_MS`, `VoicePolicy.speakerWanted(status, snapshot)`.
  - `EntryUiState.type` / `checkingIn`, `EntryViewModel.onCheckIn()`, `EntryScreen(…, onCheckIn, …)`.
  - `EntryRow(…, type, streak)`, `EntryListViewModel.create(name, type, onCreated)`, `EntryListScreen(…, onCreate: (String, EntryType) -> Unit)`, `NameDialog(…, extra)`.
  - `SettingsPage.visibleFor(type)`, `SettingsPage.tabIndex(pages)`, `EntrySettingsUiState.type`, `EntrySettingsViewModel.setType`, `EntrySettingsScreen(…, onSetType)`, `SettingsPagerViewModel.type`.
  - `ProgressionPage(vm, windowOnly)` / `ProgressionPageContent(…, windowOnly)`, `CurrentStatePage(vm, showTotal)` / `CurrentStatePageContent(…, showTotal)`, `CuesSettingsViewModel(handle, repo, voice)` with `voiceAvailable` / `setVoice`, `SwitchRow(…, supportingText)`.
  - The test tags: `check_in`, `start`, `type_WORKOUT` / `type_CHECK_IN` (create dialog), `type`, `type_value`, `type_option_<TYPE>`, `type_ok`, `type_cancel`, `support_Voice`, `switch_Voice`, `tab_<PAGE>`.
- **Counts.** Each cumulative count equals the previous one plus the `@Test` methods the subtask adds (table above). No subtask deletes a test; 2.3, 5.2, 6.2, 7.2, 8.1 and 8.2 edit existing tests without changing their number.
