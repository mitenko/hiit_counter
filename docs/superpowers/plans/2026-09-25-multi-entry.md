# HIIT Counter Multi-Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the single-workout HIIT Counter into a list of independent HIIT entries. Each entry has its own name, timing, progression, cues and rep counter. Every numeric setting moves onto one shared stepper row with a tap-to-edit dialog. Existing v1 data is migrated into an entry named "Workout".

**Architecture:** The app stays a single `:app` module using MVVM. New pure-Kotlin helpers go in `domain/`: `Entry`, `EntryNames`, `ValueFormat`, `StepRange`/`FieldRanges` and `PenaltyDraft`. They are developed test-first. Room (`hiit.db`: an `entry` table and a `meta` table) replaces the v1 DataStore settings and counter files. `RoomEntryRepository` is the only persistence API. It validates on write, repairs each field on read, and awaits a `V1Migrator` readiness gate before touching the table. A migration-only DataStore reads the v1 files once, and `AppPreferences` (`app.preferences_pb`) holds the one global flag. `TimerController` freezes a `WorkoutSnapshot(entryId, entryName, timing, cues)` at Start and exposes `isBusy(entryId)` and `lastEntryId`. Compose screens: the entry list (the new home), the entry screen (the v1 home table, per entry), entry settings (rename, duplicate, delete) and the four settings sub-screens built on `ui/common` steppers and dialogs.

**Tech Stack:** Unchanged from v1: Kotlin 2.2.10, AGP 8.13.0, Gradle 8.13, Compose BOM 2026.06.01 (Material 3), Hilt 2.57 + KSP 2.2.10-2.0.2, Navigation Compose 2.9.5, lifecycle 2.9.4, DataStore Preferences 1.1.1, coroutines 1.10.2, JUnit 4, Robolectric 4.16. **Added:** Room 2.8.1 (`room-runtime`, `room-ktx`, `room-compiler` via KSP, `room-testing`) and the `androidx.room` Gradle plugin 2.8.1.

**Spec:** `docs/superpowers/specs/2026-09-25-multi-entry-design.md` is binding. It amends `docs/superpowers/specs/2026-09-24-hiit-counter-design.md` (v1). Read both before starting. `§n` below points into the multi-entry spec; `v1 §n` points into the v1 spec.

**Starting point:** v1 as merged on `main` (PR #1), including the final-review fixes: HomeViewModel handles cancel/throw, HiitNavHost uses `dropUnlessResumed`, and TimerService holds the wake lock through the DONE grace, re-asserts `startForeground` on re-entry and drops a leftover lock on PREPARING. Don't lose any of these when you edit those files.

## How this plan is structured

- **16 tasks**, each split into **numbered subtasks** (`5.1`, `5.2`, …). A subtask is one small TDD slice:
  1. write the failing test(s);
  2. run them and see them fail for the stated reason;
  3. write the minimal code;
  4. run the full suite and see the stated cumulative test count pass;
  5. commit.

  **One commit per subtask.**
- Kotlin paths are relative to `app/src/main/kotlin/com/mitenko/hiitcounter/`. Paths starting with `test/` are relative to `app/src/test/kotlin/com/mitenko/hiitcounter/`.
- **New files and files that grow across subtasks are shown complete** at each stage, so replace the whole file. Existing files that change once or twice in a small way get **exact edits**: replace the quoted block with the new block. Test files grow by appending the listed test methods to the existing class, and new imports go with them.
- **Red** runs only the focused test class. **Green** runs the whole `testDebugUnitTest` suite and checks the cumulative count with the test-count command (Global Constraints). The expected counts assume the v1 baseline of **120** tests (recorded in 1.1). If the baseline differs, offset every expected count by the difference.
- A red step sometimes notes a test that already passes. That test is a regression guard for behaviour an earlier slice delivered, and it must stay green.
- Subtasks with nothing to unit-test (build files, DI, the navigation swap, the device check) replace red/green with a build or lint verification.
- Every task ends with a **task gate**: `./gradlew assembleDebug testDebugUnitTest lintDebug`. The gate compiles the Hilt graph, runs the suite and runs lint. It must pass before the next task starts.
- **The app compiles and the suite passes at every gate. The app is fully rewired only at 15.2.** Between Task 9 and 15.2 the old v1 screens and the new layer exist side by side. From Task 13 the old settings routes can't resolve an entry id at runtime. **Don't install any build on the device before 16.2**, because the v1 install must first meet the finished migration.
- **Diff review before every whole-file replacement commit:** run `git diff <file>` and confirm the only changes are the ones the subtask describes. Check that no fix from an earlier slice or from the v1 final review is lost, and that public signatures are unchanged unless the subtask says otherwise.
- **Pinned versions are fixed.** If anything fails to resolve (1.2 build, any Room artifact or the `androidx.room` plugin), stop and report to the user. Don't bump or substitute versions on your own.

## Global Constraints

- Package / applicationId: `com.mitenko.hiitcounter`. minSdk **26**, compileSdk **36**, targetSdk **36**. Toolchain JDK **17** (`kotlin { jvmToolchain(17) }`).
- Sources live in `app/src/main/kotlin/...` and tests in `app/src/test/kotlin/...`, never under `java/`.
- `domain/` stays pure Kotlin: no `android.*` or `androidx.*` imports. `Clock` is injected everywhere, so never call `Instant.now()`, `System.currentTimeMillis()` or `SystemClock` outside `platform/AndroidClock.kt`.
- `Locale.ENGLISH` for all date and number formatting. Dark theme only and portrait only. **No "WORK" label** on the timer.
- **Commits are authored as `mitenko <mitenko@gmail.com>`.** This identity is applied automatically for GitHub remotes. Confirm that `git config user.email` prints `mitenko@gmail.com` before the first commit (1.1). If it doesn't, stop and ask the user, and never commit with a work identity.
- **No AI attribution.** Commit messages and PR descriptions carry no co-author trailers and no "generated with" footers.
- Git: work on branch **`feat/multi-entry`**, created in 1.1 from an up-to-date `main`. Make **one commit per subtask**, with the messages given. Never merge locally into `main`. Never push or open a PR without asking the user (16.3).
- **Gradle logging convention.** Run Gradle from Git Bash at the repo root. `$SCRATCH` is the session scratchpad directory. Every Bash call starts a fresh shell, so **every command that uses `$SCRATCH` sets it inline in the same call**. Every Gradle run is labelled and logged:
  ```bash
  SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"; ./gradlew <tasks> 2>&1 | tee "$SCRATCH/claude-gradle-<label>.log"; echo "CLAUDE-<LABEL> DONE rc=${PIPESTATUS[0]}"
  ```
  The same `SCRATCH="…"` prefix goes in front of every non-Gradle command block below that writes to `$SCRATCH` (16.2, 16.3).
  `Run (label X, ~N min): <tasks>` below means exactly that command with `<label>` = X. **Announce** the command and its rough duration in the message that starts it. **Never kill a process you did not start.** `java`/`gradle`/`adb`/`studio64` processes may belong to the user's Android Studio.
- **Diff review before committing any whole-file replacement:** run `git diff <file>` and confirm that only the described changes are present. In particular, the v1 final-review fixes in `TimerService`, `HiitNavHost` and the start flow must survive.
- **Test-count command** (run after every green step and every gate; it reads the XML of the run that just finished):
  ```bash
  awk -F'"' '/<testsuite /{for(i=1;i<NF;i++) if($i ~ / tests=$/) s+=$(i+1)} END{print "CLAUDE-TEST-COUNT " s}' app/build/test-results/testDebugUnitTest/TEST-*.xml
  ```
  "Expected: N tests" means `BUILD SUCCESSFUL`, `CLAUDE-TEST-COUNT N` and no failures.
- **Transient Gradle daemon `BindException`** (caused by the user's Android Studio): retry the same command once. Don't kill anything. If it fails again, report it. A killed or interrupted run is **inconclusive**, so re-run it rather than reading its partial output.
- **Robolectric rules (lessons from v1):**
  - Every Robolectric test class has `@RunWith(AndroidJUnit4::class)` and `@Config(sdk = [34])`.
  - The default viewport is 320×470 dp. `performClick()` does not scroll, so call `performScrollTo()` first on anything inside a scrolling column that might be below the fold. Tests that need a phone-size screen use `@Config(sdk = [34], qualifiers = "w411dp-h891dp")`.
  - Room in-memory databases need Robolectric: `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java).allowMainThreadQueries().build()`, closed in `@After`.
  - From 1.2, `app/src/test/resources/robolectric.properties` makes Robolectric use a plain `android.app.Application`. That way `HiitApp` (and its migrator start, from 15.2) never runs inside unit tests.
- **Threading:** `TimerController` and `TabataEngine` must be driven on Main. Room suspend calls return to the caller's dispatcher, so ViewModels call the repository from `viewModelScope` and call the controller on Main. There is no `withContext(Dispatchers.IO)` around controller calls (§5.3).
- `TimerController.start()` requires `ServiceStatus.Started`, so controller tests call `onServiceStarted()` before `start()`.
- `dropUnlessResumed` (lifecycle-runtime-compose 2.9.4) only has a **zero-arg** overload. Callbacks that take an argument use `dropUnlessResumedWith` (15.1).

## Subtask Overview

| Task | Subtasks |
|---|---|
| 1 Branch + Room build | 1.1 branch, identity, baseline · 1.2 Room dependencies, plugin, schema dir, Robolectric app |
| 2 Entry + names | 2.1 `Entry`, `EntryNotFound`, `EntryBusy` · 2.2 `EntryNames.validate` · 2.3 duplicate names + messages |
| 3 Values + steps | 3.1 `parseSeconds`/`formatSeconds` · 3.2 `parseInt`/`parseDecimal`/`formatDecimal` · 3.3 `StepRange`/`FieldRanges` · 3.4 `PenaltyDraft` |
| 4 Room schema + mapping | 4.1 entities, DAOs, database, exported schema · 4.2 entity → domain, NULL total · 4.3 per-field repair, group fallback · 4.4 row builders |
| 5 Repository core | 5.1 gate, entries, entry, create · 5.2 rename + settings writes · 5.3 counter writes |
| 6 Ordering + check-in | 6.1 delete + duplicate · 6.2 `moveBy` · 6.3 `checkIn` (transaction, concurrency, interleavings) |
| 7 v1 migration | 7.1 `AppPreferences` · 7.2 v1 readers · 7.3 `V1Migrator` gate + import · 7.4 corrupted files read as defaults (no rewrite) · 7.5 flag copy + cleanup + crash window |
| 8 Run ownership | 8.1 snapshot entry id/name + `lastEntryId` · 8.2 `isBusy` · 8.3 notification titles · 8.4 timer screen name |
| 9 Storage DI | 9.1 `StorageModule` · 9.2 `TimerViewModel` → `AppPreferences` |
| 10 Entry list | 10.1 fake repository + list states · 10.2 reorder + create · 10.3 `NameDialog` · 10.4 `EntryListScreen` |
| 11 Entry screen | 11.1 `EntryScopedViewModel` + table state · 11.2 start flow · 11.3 `EntryScreen` |
| 12 Stepper + dialog | 12.1 `EditValueDialog` · 12.2 `StepperRow` + `IntStepperField` · 12.3 `PenaltyStepperField` |
| 13 Settings sub-screens | 13.1 Timing (+ restoration) · 13.2 Progression · 13.3 Current State · 13.4 Cues |
| 14 Entry settings | 14.1 `EntrySettingsViewModel` · 14.2 `EntrySettingsScreen` |
| 15 Navigation swap | 15.1 routes + nav actions · 15.2 new nav host, migrator start, v1 removal |
| 16 Finish | 16.1 docs + full verification · 16.2 device verification on the Pixel 9a · 16.3 squash and PR |

## File Map

```
gradle/libs.versions.toml, build.gradle.kts, app/build.gradle.kts           Room 2.8.1 + androidx.room plugin (1.2)
app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/1.json            exported schema, committed (4.1)
app/src/test/resources/robolectric.properties                              plain Application under Robolectric (1.2)
app/src/main/res/values/strings.xml                                        new strings (10.3); two removals (8.3, 13.3)
app/src/main/res/drawable/{ic_arrow_up,ic_arrow_down}.xml                  reorder icons (10.4)
app/src/main/kotlin/com/mitenko/hiitcounter/
  HiitApp.kt                                           starts V1Migrator (15.2)
  domain/
    model/Entry.kt                                     Entry, EntryNotFound, EntryBusy (2.1)
    EntryNames.kt                                      NameCheck, EntryNames (2.2–2.3)
    ValueFormat.kt                                     parse/format for the edit dialog (3.1–3.2)
    StepRange.kt                                       StepRange, FieldRanges (3.3)
    PenaltyDraft.kt                                    half-hour penalty draft (3.4)
    TimerController.kt                                 snapshot entry id/name, lastEntryId, isBusy (8.1–8.2)
    TimerText.kt                                       entry-name notification titles (8.3)
  data/
    db/{EntryEntity,MetaEntity,EntryDao,MetaDao,HiitDatabase}.kt              Room (4.1)
    EntryMapping.kt                                    validTotal, repair, toDomain, entryEntity, StoredCounter (4.2–4.4)
    EntryRepository.kt                                 EntryRepository, MigrationGate, RoomEntryRepository (5.1–6.3)
    AppPreferences.kt                                  app.preferences_pb (7.1)
    v1/V1Readers.kt                                    V1Keys, v1 readers, v1Entry (7.2)
    v1/V1Migrator.kt                                   readiness gate + one-time import (7.3–7.5)
    CounterRepository.kt, SettingsRepository.kt, PreferenceKeys.kt           DELETED (15.2)
  di/StorageModule.kt                                  Room, AppPreferences, V1Migrator, EntryRepository (9.1)
  di/DataModule.kt                                     DELETED (15.2)
  service/{WorkoutNotifications,TimerService}.kt       entry name in the notification (8.3)
  ui/common/
    EntryScopedViewModel.kt                            ENTRY_ID_ARG, entryId, missing (11.1)
    NameDialog.kt                                      create/rename dialog (10.3)
    EditValueDialog.kt                                 ValueInput, EditValueDialog (12.1)
    StepperRow.kt                                      StepperRow, IntStepperField, PenaltyStepperField (12.2–12.3)
    SettingsComponents.kt                              NumberField removed (13.3)
  ui/entries/{EntryListViewModel,EntryListScreen}.kt   the new home (10.1–10.4)
  ui/entry/{EntryViewModel,EntryScreen}.kt             per-entry table + Start (11.1–11.3)
  ui/settings/EntrySettings.kt                         SettingsPage, EntrySettingsViewModel, screen (14.1–14.2)
  ui/settings/{Timing,Progression,CurrentState,Cues}Settings.kt               per entry, steppers (13.1–13.4)
  ui/settings/SettingsListScreen.kt                    DELETED (15.2)
  ui/home/{HomeViewModel,HomeScreen}.kt                DELETED (15.2; ported to ui/entry in Task 11)
  ui/timer/{TimerUiMapper,TimerViewModel,TimerScreen}.kt                      entry name; AppPreferences (8.4, 9.2)
  ui/navigation/{Routes,NavActions,HiitNavHost}.kt     new routes, timer exit, swap (15.1–15.2)
app/src/test/kotlin/com/mitenko/hiitcounter/
  testutil/{Assertions,Entities,Entries,FakeEntryRepository}.kt              new helpers
  testutil/{FakeSettingsRepository,FakeCounterRepository}.kt                 DELETED (15.2)
  domain/model/EntryTest.kt, domain/{EntryNamesTest,ValueFormatTest,StepRangeTest,PenaltyDraftTest}.kt
  data/{db/HiitDatabaseTest,EntryMappingTest,RoomEntryRepositoryTest,AppPreferencesTest}.kt
  data/v1/{V1ReadersTest,V1MigratorTest}.kt
  data/{DataStoreSettingsRepositoryTest,DataStoreCounterRepositoryTest}.kt   DELETED (15.2)
  ui/entries/{EntryListViewModelTest,EntryListScreenTest}.kt
  ui/entry/{EntryViewModelTest,UntouchedTotalTest,EntryScreenTest}.kt
  ui/common/{NameDialogTest,EditValueDialogTest,StepperRowTest}.kt
  ui/settings/{Timing,Progression,CurrentState,Cues}*Test.kt                 rewritten per entry (Task 13)
  ui/settings/{TimingSettingsRestorationTest,ProgressionSettingsScreenTest,EntrySettingsViewModelTest,EntrySettingsScreenTest}.kt
  ui/settings/SettingsListScreenTest.kt                DELETED (15.2; ported to EntrySettingsScreenTest)
  ui/home/{HomeViewModelTest,HomeScreenTest}.kt        DELETED (15.2; ported to ui/entry in Task 11)
  ui/navigation/{RoutesTest,NavActionsTest}.kt
  ui/timer/{TimerUiMapperTest,TimerScreenTest,TimerViewModelTest}.kt, domain/{TimerControllerTest,TimerTextTest}.kt   updated
```

---

## Task 1: Branch and Room build setup

**Interfaces produced:** Gradle aliases `libs.room.runtime`, `libs.room.ktx`, `libs.room.compiler`, `libs.room.testing` and `libs.plugins.room`, plus the `room { schemaDirectory(...) }` block. Unit-test assets include `app/schemas`. Robolectric uses `android.app.Application`.

### Subtask 1.1: Branch, identity and baseline

**Files:** none.

- [ ] **Step 1: Preconditions and branch**

The docs branch (spec + plan) is squash-merged into `main` **before** execution starts. Check that first, without switching branches, so the plan never disappears from the working tree mid-execution:
```bash
git status --short            # must be empty (bash.exe.stackdump is gitignored)
git fetch origin
git show origin/main:docs/superpowers/specs/2026-09-25-multi-entry-design.md > /dev/null && \
  git show origin/main:docs/superpowers/plans/2026-09-25-multi-entry.md > /dev/null && echo "CLAUDE-DOCS-ON-MAIN ok"
```
**Stop and ask the user if this doesn't print `CLAUDE-DOCS-ON-MAIN ok`.** It means the docs PR hasn't been merged yet. Don't check out `main` in that case. Only once the check passes:
```bash
git checkout main && git pull --ff-only     # main now contains the spec and this plan
git show main:docs/superpowers/plans/2026-09-25-multi-entry.md > /dev/null && echo "CLAUDE-DOCS-ON-LOCAL-MAIN ok"
git config user.email         # must print mitenko@gmail.com
git checkout -b feat/multi-entry
```
If `git config user.email` doesn't print `mitenko@gmail.com`, stop and ask. Don't change git config yourself.

- [ ] **Step 2: Baseline** — Run (label `T1-1-BASELINE`, ~3 min): `./gradlew testDebugUnitTest`, then the test-count command. Expected: **120 tests**, all passing. If the count differs, record it and offset every expected count in this plan by the difference.

- [ ] **Step 3: No commit** (nothing changed).

### Subtask 1.2: Room dependencies, plugin, schema directory, Robolectric application

**Files:** Replace `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`. Create `app/src/test/resources/robolectric.properties`.

- [ ] **Step 1: Version catalog** — `gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.10"
ksp = "2.2.10-2.0.2"
coreKtx = "1.16.0"
lifecycle = "2.9.4"
activityCompose = "1.10.0"
composeBom = "2026.06.01"
navigationCompose = "2.9.5"
hilt = "2.57"
hiltNavigationCompose = "1.3.0"
datastore = "1.1.1"
room = "2.8.1"
coroutines = "1.10.2"
junit = "4.13.2"
androidxJunit = "1.3.0"
robolectric = "4.16"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Root build** — `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
```

- [ ] **Step 3: App build** — `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.mitenko.hiitcounter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mitenko.hiitcounter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    // Exported Room schemas as unit-test assets, so MigrationTestHelper works under Robolectric (spec §4).
    sourceSets["test"].assets.srcDir("$projectDir/schemas")
    lint {
        abortOnError = true
    }
}

kotlin { jvmToolchain(17) }

room {
    schemaDirectory("$projectDir/schemas")
}

// merge*UnitTestAssets reads app/schemas, which copyRoomSchemas* writes; declare the order Gradle can't infer.
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("UnitTestAssets"))
        dependsOn(tasks.matching { it.name.startsWith("copyRoomSchemas") })
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
```

- [ ] **Step 4: Robolectric application** — `app/src/test/resources/robolectric.properties`:
```properties
# Unit tests never run HiitApp (Hilt graph + V1Migrator start); Compose/Room tests don't need it.
application=android.app.Application
```

- [ ] **Step 5: Verify** — Run (label `T1-2-BUILD`, ~4 min): `./gradlew assembleDebug testDebugUnitTest`, then the test-count command. Expected: `BUILD SUCCESSFUL` and **120 tests**. Room's KSP processor runs with no `@Database` yet, which is fine. If any Room artifact or the `androidx.room` plugin fails to resolve, **stop and ask**.

- [ ] **Step 6: Commit** — diff-review the three build files first:
```bash
git add -A && git commit -m "build: add Room 2.8.1 and the Room Gradle plugin"
```

**Task 1 gate:** Run (label `T1-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **120 tests**, no lint errors.

---

## Task 2: Entry model and names (§5.1, §5.5)

**Interfaces produced:**
- `data class Entry(id: Long, name: String, position: Int, timing: TimingConfig, progression: ProgressionConfig, cues: CueConfig, counter: CounterState)` in `domain.model`
- `class EntryNotFound(val id: Long) : Exception`, `class EntryBusy(val id: Long) : Exception` in `domain.model`
- `sealed interface NameCheck { Ok(name), Empty, TooLong }`, `object EntryNames { MAX_LENGTH = 40; COPY_SUFFIX = " copy"; validate(raw): NameCheck; duplicateName(base): String; errorMessage(check): String? }` in `domain`

### Subtask 2.1: Entry, EntryNotFound, EntryBusy

**Files:** Create `domain/model/Entry.kt`; test `test/domain/model/EntryTest.kt`.

- [ ] **Step 1: Failing test** — `test/domain/model/EntryTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryTest {
    @Test
    fun `errors carry the id and a readable message`() {
        val notFound = EntryNotFound(7)
        assertEquals(7L, notFound.id)
        assertEquals("Entry 7 not found", notFound.message)
        val busy = EntryBusy(3)
        assertEquals(3L, busy.id)
        assertEquals("Entry 3 has an active workout", busy.message)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T2-1-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.model.EntryTest"`. Expected: compile FAIL, `Unresolved reference 'EntryNotFound'`.

- [ ] **Step 3: Implement** — `domain/model/Entry.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

/**
 * One HIIT entry (spec §5.1). Invariant: [counter].total is always a real value — a stored
 * NULL total is resolved to [progression].startingTotal when the row is mapped.
 */
data class Entry(
    val id: Long,
    val name: String,
    val position: Int,
    val timing: TimingConfig,
    val progression: ProgressionConfig,
    val cues: CueConfig,
    val counter: CounterState,
)

class EntryNotFound(val id: Long) : Exception("Entry $id not found")

class EntryBusy(val id: Long) : Exception("Entry $id has an active workout")
```

- [ ] **Step 4: Run green** — Run (label `T2-1-GREEN`, ~3 min): `./gradlew testDebugUnitTest` + test-count command. Expected: **121 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): Entry model and entry errors"`

### Subtask 2.2: EntryNames.validate

**Files:** Create `domain/EntryNames.kt`; test `test/domain/EntryNamesTest.kt`.

- [ ] **Step 1: Failing tests** — `test/domain/EntryNamesTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryNamesTest {
    @Test
    fun `trims and accepts 1 to 40 characters`() {
        assertEquals(NameCheck.Ok("Burpees"), EntryNames.validate("  Burpees "))
        assertEquals(NameCheck.Ok("a"), EntryNames.validate("a"))
        assertEquals(NameCheck.Ok("x".repeat(40)), EntryNames.validate("x".repeat(40)))
    }

    @Test
    fun `blank is empty`() {
        assertEquals(NameCheck.Empty, EntryNames.validate(""))
        assertEquals(NameCheck.Empty, EntryNames.validate("   "))
    }

    @Test
    fun `more than 40 characters after trimming is too long`() {
        assertEquals(NameCheck.TooLong, EntryNames.validate("x".repeat(41)))
        assertEquals(NameCheck.Ok("x".repeat(40)), EntryNames.validate(" " + "x".repeat(40) + " "))
    }

    @Test
    fun `length counts UTF-16 units`() {
        val flexed = "💪" // one emoji, two UTF-16 units
        assertEquals(NameCheck.Ok(flexed.repeat(20)), EntryNames.validate(flexed.repeat(20)))
        assertEquals(NameCheck.TooLong, EntryNames.validate(flexed.repeat(20) + "x"))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T2-2-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.EntryNamesTest"`. Expected: compile FAIL, `Unresolved reference 'EntryNames'`.

- [ ] **Step 3: Implement** — `domain/EntryNames.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

sealed interface NameCheck {
    data class Ok(val name: String) : NameCheck
    data object Empty : NameCheck
    data object TooLong : NameCheck
}

/** Entry-name rules (spec §5.5). Lengths are `String.length` (UTF-16 units). Duplicates are allowed. */
object EntryNames {
    const val MAX_LENGTH = 40
    const val COPY_SUFFIX = " copy"

    fun validate(raw: String): NameCheck {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameCheck.Empty
            name.length > MAX_LENGTH -> NameCheck.TooLong
            else -> NameCheck.Ok(name)
        }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T2-2-GREEN`, ~3 min): full suite + count. Expected: **125 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): entry name validation"`

### Subtask 2.3: Duplicate names and messages

**Files:** Replace `domain/EntryNames.kt`; append to `test/domain/EntryNamesTest.kt`.

- [ ] **Step 1: Failing tests** — append to `EntryNamesTest` (add `import org.junit.Assert.assertNull` and `import org.junit.Assert.assertTrue`):
```kotlin
    @Test
    fun `duplicate appends copy`() {
        assertEquals("Burpees copy", EntryNames.duplicateName("Burpees"))
    }

    @Test
    fun `long names are cut so the suffix survives`() {
        val dup = EntryNames.duplicateName("x".repeat(40))
        assertEquals("x".repeat(35) + " copy", dup)
        assertEquals(40, dup.length)
        assertTrue(dup.endsWith(EntryNames.COPY_SUFFIX))
        assertEquals(NameCheck.Ok(dup), EntryNames.validate(dup))
    }

    @Test
    fun `error messages explain the failure`() {
        assertEquals("Enter a name", EntryNames.errorMessage(NameCheck.Empty))
        assertEquals("Use at most 40 characters", EntryNames.errorMessage(NameCheck.TooLong))
        assertNull(EntryNames.errorMessage(NameCheck.Ok("Burpees")))
    }
```

- [ ] **Step 2: Run red** — Run (label `T2-3-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.EntryNamesTest"`. Expected: compile FAIL, `Unresolved reference 'duplicateName'`.

- [ ] **Step 3: Implement** — `domain/EntryNames.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

sealed interface NameCheck {
    data class Ok(val name: String) : NameCheck
    data object Empty : NameCheck
    data object TooLong : NameCheck
}

/** Entry-name rules (spec §5.5). Lengths are `String.length` (UTF-16 units). Duplicates are allowed. */
object EntryNames {
    const val MAX_LENGTH = 40
    const val COPY_SUFFIX = " copy"

    fun validate(raw: String): NameCheck {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameCheck.Empty
            name.length > MAX_LENGTH -> NameCheck.TooLong
            else -> NameCheck.Ok(name)
        }
    }

    /** The base is cut so the suffix always survives: `base.take(40 - " copy".length) + " copy"`. */
    fun duplicateName(base: String): String = base.take(MAX_LENGTH - COPY_SUFFIX.length) + COPY_SUFFIX

    /** Inline explanation for the name dialog (spec §8.3); null when the name is valid. */
    fun errorMessage(check: NameCheck): String? = when (check) {
        is NameCheck.Ok -> null
        NameCheck.Empty -> "Enter a name"
        NameCheck.TooLong -> "Use at most $MAX_LENGTH characters"
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T2-3-GREEN`, ~3 min): full suite + count. Expected: **128 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryNames.kt`, then `git add -A && git commit -m "feat(domain): duplicate-name suffix and name error messages"`

**Task 2 gate:** Run (label `T2-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **128 tests**, no lint errors.

---

## Task 3: ValueFormat, step ranges and the penalty draft (§8.1, §8.2)

**Interfaces produced:**
- `object ValueFormat { parseSeconds(text): Int?; formatSeconds(sec): String; parseInt(text): Int?; parseDecimal(text): Double?; formatDecimal(value: Double): String }`
- `data class StepRange(min: Int, max: Int, step: Int) { clamp(v); plus(v); minus(v) }`, `object FieldRanges { PHASE, WORK, SETS, REPS, HOLD_FOR, WINDOW_HOURS, TOTAL, STREAK }`
- `data class PenaltyDraft(halfHours: Int, exact: Double? = null) { hours; plus(); minus(); companion { MIN_HALF_HOURS, MAX_HALF_HOURS, MIN_HOURS, MAX_HOURS, of(hours) } }`

### Subtask 3.1: parseSeconds and formatSeconds

**Files:** Create `domain/ValueFormat.kt`; test `test/domain/ValueFormatTest.kt`.

- [ ] **Step 1: Failing tests** — `test/domain/ValueFormatTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValueFormatTest {
    @Test
    fun `m colon ss is accepted`() {
        assertEquals(90, ValueFormat.parseSeconds("1:30"))
        assertEquals(5, ValueFormat.parseSeconds("0:05"))
        assertEquals(3599, ValueFormat.parseSeconds("59:59"))
        assertEquals(120, ValueFormat.parseSeconds(" 2:00 "))
        assertEquals(10, ValueFormat.parseSeconds("00:10"))
    }

    @Test
    fun `plain whole seconds are accepted`() {
        assertEquals(90, ValueFormat.parseSeconds("90"))
        assertEquals(0, ValueFormat.parseSeconds("0"))
    }

    @Test
    fun `malformed times are rejected`() {
        listOf("1:5", "1:60", "-5", "-1:30", "", "  ", "abc", "1:30:00", "1.5", ":30", "1:", "99999999999").forEach {
            assertNull("'$it' should not parse", ValueFormat.parseSeconds(it))
        }
    }

    @Test
    fun `formats as mm ss`() {
        assertEquals("01:30", ValueFormat.formatSeconds(90))
        assertEquals("00:05", ValueFormat.formatSeconds(5))
        assertEquals("59:59", ValueFormat.formatSeconds(3599))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T3-1-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.ValueFormatTest"`. Expected: compile FAIL, `Unresolved reference 'ValueFormat'`.

- [ ] **Step 3: Implement** — `domain/ValueFormat.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

/** Parsing and formatting for the settings edit dialog (spec §8.2). Pure; null means "doesn't parse". */
object ValueFormat {
    private val MIN_SEC = Regex("""(\d+):([0-5]\d)""")
    private val WHOLE = Regex("""\d+""")

    /** `m:ss` with exactly two second digits 00–59 (`"1:30"` = 90), or plain whole seconds (`"90"`). */
    fun parseSeconds(text: String): Int? {
        val t = text.trim()
        MIN_SEC.matchEntire(t)?.let { m ->
            val minutes = m.groupValues[1].toLongOrNull() ?: return null
            val total = minutes * 60 + m.groupValues[2].toLong()
            return if (total <= Int.MAX_VALUE) total.toInt() else null
        }
        return if (WHOLE.matches(t)) t.toIntOrNull() else null
    }

    /** `formatSeconds(90) = "01:30"`. The hard range keeps values ≤ 59:59, so there is no hour form. */
    fun formatSeconds(sec: Int): String = TimerText.formatMmSs(sec)
}
```

- [ ] **Step 4: Run green** — Run (label `T3-1-GREEN`, ~3 min): full suite + count. Expected: **132 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): time parsing and formatting for the edit dialog"`

### Subtask 3.2: parseInt, parseDecimal, formatDecimal

**Files:** Replace `domain/ValueFormat.kt`; append to `test/domain/ValueFormatTest.kt`.

- [ ] **Step 1: Failing tests** — append to `ValueFormatTest`:
```kotlin
    @Test
    fun `parseInt accepts non-negative whole numbers`() {
        assertEquals(0, ValueFormat.parseInt("0"))
        assertEquals(42, ValueFormat.parseInt("42"))
        assertEquals(7, ValueFormat.parseInt(" 7 "))
    }

    @Test
    fun `parseInt rejects everything else`() {
        listOf("-1", "1.5", "", "abc", "99999999999", "+3", "1 000").forEach {
            assertNull("'$it' should not parse", ValueFormat.parseInt(it))
        }
    }

    @Test
    fun `parseDecimal accepts a dot or a comma`() {
        assertEquals(19.5, ValueFormat.parseDecimal("19.5")!!, 0.0)
        assertEquals(19.5, ValueFormat.parseDecimal("19,5")!!, 0.0)
        assertEquals(2.0, ValueFormat.parseDecimal("2")!!, 0.0)
        assertEquals(0.5, ValueFormat.parseDecimal(".5")!!, 0.0)
    }

    @Test
    fun `parseDecimal rejects negatives, NaN, infinity and garbage`() {
        listOf("-1", "-0.5", "NaN", "Infinity", "1e5", "", "abc", "1.2.3", "1,2,3").forEach {
            assertNull("'$it' should not parse", ValueFormat.parseDecimal(it))
        }
    }

    @Test
    fun `formatDecimal drops trailing zeros`() {
        assertEquals("19.5", ValueFormat.formatDecimal(19.5))
        assertEquals("20", ValueFormat.formatDecimal(20.0))
        assertEquals("0.3", ValueFormat.formatDecimal(0.3))
    }
```

- [ ] **Step 2: Run red** — Run (label `T3-2-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.ValueFormatTest"`. Expected: compile FAIL, `Unresolved reference 'parseInt'`.

- [ ] **Step 3: Implement** — `domain/ValueFormat.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

import java.math.BigDecimal

/** Parsing and formatting for the settings edit dialog (spec §8.2). Pure; null means "doesn't parse". */
object ValueFormat {
    private val MIN_SEC = Regex("""(\d+):([0-5]\d)""")
    private val WHOLE = Regex("""\d+""")
    private val DECIMAL = Regex("""\d+(\.\d*)?|\.\d+""")

    /** `m:ss` with exactly two second digits 00–59 (`"1:30"` = 90), or plain whole seconds (`"90"`). */
    fun parseSeconds(text: String): Int? {
        val t = text.trim()
        MIN_SEC.matchEntire(t)?.let { m ->
            val minutes = m.groupValues[1].toLongOrNull() ?: return null
            val total = minutes * 60 + m.groupValues[2].toLong()
            return if (total <= Int.MAX_VALUE) total.toInt() else null
        }
        return parseInt(t)
    }

    /** `formatSeconds(90) = "01:30"`. The hard range keeps values ≤ 59:59, so there is no hour form. */
    fun formatSeconds(sec: Int): String = TimerText.formatMmSs(sec)

    /** Non-negative whole numbers only. */
    fun parseInt(text: String): Int? {
        val t = text.trim()
        return if (WHOLE.matches(t)) t.toIntOrNull() else null
    }

    /** Non-negative finite decimals; `.` or `,` as the separator. */
    fun parseDecimal(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (!DECIMAL.matches(t)) return null
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Shortest plain form: 19.5 → "19.5", 20.0 → "20", 0.3 → "0.3". */
    fun formatDecimal(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
```

- [ ] **Step 4: Run green** — Run (label `T3-2-GREEN`, ~3 min): full suite + count. Expected: **137 tests**.

- [ ] **Step 5: Commit** — diff-review `ValueFormat.kt`, then `git add -A && git commit -m "feat(domain): whole-number and decimal parsing for the edit dialog"`

### Subtask 3.3: StepRange and FieldRanges

**Files:** Create `domain/StepRange.kt`; test `test/domain/StepRangeTest.kt`.

- [ ] **Step 1: Failing tests** — `test/domain/StepRangeTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StepRangeTest {
    @Test
    fun `steps clamp at the hard edges`() {
        assertEquals(0, FieldRanges.PHASE.minus(0))
        assertEquals(0, FieldRanges.PHASE.minus(3))
        assertEquals(3599, FieldRanges.PHASE.plus(3595))
        assertEquals(1, FieldRanges.WORK.minus(5))
        assertEquals(1, FieldRanges.WORK.minus(1))
        assertEquals(6, FieldRanges.WORK.plus(1))
        assertEquals(20, FieldRanges.SETS.plus(20))
        assertEquals(0, FieldRanges.STREAK.minus(0))
        assertEquals(9999, FieldRanges.REPS.plus(Int.MAX_VALUE))
    }

    @Test
    fun `clamp pulls dialog values into range`() {
        assertEquals(1, FieldRanges.REPS.clamp(0))
        assertEquals(9999, FieldRanges.REPS.clamp(12_000))
        assertEquals(0, FieldRanges.HOLD_FOR.clamp(-3))
        assertEquals(500, FieldRanges.WINDOW_HOURS.clamp(500))
    }

    @Test
    fun `ranges match the spec table`() {
        assertEquals(StepRange(0, 3599, 5), FieldRanges.PHASE)
        assertEquals(StepRange(1, 3599, 5), FieldRanges.WORK)
        assertEquals(StepRange(1, 20, 1), FieldRanges.SETS)
        assertEquals(StepRange(1, 9999, 1), FieldRanges.REPS)
        assertEquals(StepRange(0, 999, 1), FieldRanges.HOLD_FOR)
        assertEquals(StepRange(1, 999, 1), FieldRanges.WINDOW_HOURS)
        assertEquals(StepRange(1, 9999, 1), FieldRanges.TOTAL)
        assertEquals(StepRange(0, 99999, 1), FieldRanges.STREAK)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T3-3-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.StepRangeTest"`. Expected: compile FAIL, `Unresolved reference 'FieldRanges'`.

- [ ] **Step 3: Implement** — `domain/StepRange.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

/** A stepper field's step and hard range (spec §8.1): ± and dialog values never leave [min]..[max]. */
data class StepRange(val min: Int, val max: Int, val step: Int) {
    init {
        require(min <= max) { "min $min > max $max" }
        require(step > 0) { "step must be positive, was $step" }
    }

    fun clamp(value: Int): Int = value.coerceIn(min, max)

    fun plus(value: Int): Int = (value.toLong() + step).coerceIn(min.toLong(), max.toLong()).toInt()

    fun minus(value: Int): Int = (value.toLong() - step).coerceIn(min.toLong(), max.toLong()).toInt()
}

/** The hard ranges of spec §8.1. The penalty rate is stepped in half-hours by [PenaltyDraft]. */
object FieldRanges {
    /** Prepare, Rest, Cooldown: 0 – 59:59 in 5 s steps. */
    val PHASE = StepRange(0, SettingsValidator.MAX_PHASE_SEC, 5)
    val WORK = StepRange(1, SettingsValidator.MAX_PHASE_SEC, 5)
    val SETS = StepRange(1, SettingsValidator.MAX_SETS, 1)
    /** Starting total, Floor, Cap, Hold at. */
    val REPS = StepRange(1, 9999, 1)
    val HOLD_FOR = StepRange(0, 999, 1)
    val WINDOW_HOURS = StepRange(1, 999, 1)
    /** Current State total. */
    val TOTAL = StepRange(1, 9999, 1)
    /** Best and current streak. */
    val STREAK = StepRange(0, 99999, 1)
}
```

- [ ] **Step 4: Run green** — Run (label `T3-3-GREEN`, ~3 min): full suite + count. Expected: **140 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): stepper hard ranges with clamping"`

### Subtask 3.4: PenaltyDraft (half-hour stepping)

**Files:** Create `domain/PenaltyDraft.kt`; test `test/domain/PenaltyDraftTest.kt`.

- [ ] **Step 1: Failing tests** — `test/domain/PenaltyDraftTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PenaltyDraftTest {
    @Test
    fun `whole multiples of a half hour become half-hours`() {
        assertEquals(PenaltyDraft(39), PenaltyDraft.of(19.5))
        assertEquals(19.5, PenaltyDraft.of(19.5).hours, 0.0)
        assertEquals(PenaltyDraft(2), PenaltyDraft.of(1.0))
        assertNull(PenaltyDraft.of(1.0).exact)
    }

    @Test
    fun `a non-multiple is kept exactly`() {
        val d = PenaltyDraft.of(0.3)
        assertEquals(0.3, d.hours, 0.0)
        assertEquals(0.3, d.exact!!, 0.0)
    }

    @Test
    fun `plus and minus snap a non-multiple to the neighbouring multiple`() {
        assertEquals(PenaltyDraft(1), PenaltyDraft.of(0.3).plus())
        assertEquals(PenaltyDraft(1), PenaltyDraft.of(0.3).minus())
        assertEquals(1.5, PenaltyDraft.of(1.3).plus().hours, 0.0)
        assertEquals(1.0, PenaltyDraft.of(1.3).minus().hours, 0.0)
    }

    @Test
    fun `steps clamp at 0_5 and 999_5`() {
        assertEquals(PenaltyDraft(1), PenaltyDraft(1).minus())
        assertEquals(PenaltyDraft(1999), PenaltyDraft(1999).plus())
        assertEquals(0.5, PenaltyDraft(PenaltyDraft.MIN_HALF_HOURS).hours, 0.0)
        assertEquals(999.5, PenaltyDraft(PenaltyDraft.MAX_HALF_HOURS).hours, 0.0)
    }

    @Test
    fun `hold-to-repeat never drifts`() {
        var d = PenaltyDraft.of(19.5)
        repeat(1000) { d = d.plus() }
        assertEquals(519.5, d.hours, 0.0)
        repeat(1000) { d = d.minus() }
        assertEquals(PenaltyDraft(39), d)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T3-4-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.PenaltyDraftTest"`. Expected: compile FAIL, `Unresolved reference 'PenaltyDraft'`.

- [ ] **Step 3: Implement** — `domain/PenaltyDraft.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import kotlin.math.floor

/**
 * Penalty-rate draft (spec §8.1), held as whole half-hours so hold-to-repeat never drifts.
 * A dialog (or stored) value that isn't a multiple of 0.5 is kept exactly in [exact] for
 * display and saving; the next ± press snaps it to the neighbouring multiple (0.3 → + 0.5,
 * − clamps to 0.5).
 */
data class PenaltyDraft(val halfHours: Int, val exact: Double? = null) {
    val hours: Double get() = exact ?: (halfHours / 2.0)

    fun plus(): PenaltyDraft = PenaltyDraft(clampHalf(if (exact != null) floor(exact * 2).toInt() + 1 else halfHours + 1))

    fun minus(): PenaltyDraft = PenaltyDraft(clampHalf(if (exact != null) floor(exact * 2).toInt() else halfHours - 1))

    companion object {
        /** 0.5 h. */
        const val MIN_HALF_HOURS = 1
        /** 999.5 h. */
        const val MAX_HALF_HOURS = 1999
        const val MIN_HOURS = 0.5
        const val MAX_HOURS = 999.5

        fun of(hours: Double): PenaltyDraft {
            val doubled = hours * 2
            return if (doubled == floor(doubled) && doubled <= Int.MAX_VALUE) {
                PenaltyDraft(doubled.toInt())
            } else {
                PenaltyDraft(floor(doubled).toInt(), exact = hours)
            }
        }

        private fun clampHalf(halfHours: Int): Int = halfHours.coerceIn(MIN_HALF_HOURS, MAX_HALF_HOURS)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T3-4-GREEN`, ~3 min): full suite + count. Expected: **145 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): penalty-rate draft in integer half-hours"`

**Task 3 gate:** Run (label `T3-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **145 tests**, no lint errors.

---

## Task 4: Room schema and entity mapping (§5.2)

**Interfaces produced:**
- `@Entity("entry") data class EntryEntity(id: Long = 0, name, position, prepareSec, sets, workSec, restSec, cooldownSec, startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep: Double, cueSound, cueVibration, total: Int?, bestStreak, currentStreak, holdCount, lastCheckIn: Long?)`, with a non-unique index on `position`
- `@Entity("meta") data class MetaEntity(key: String, value: String)`
- `EntryDao { observeAll(); observe(id); get(id); getAll(); count(); insert(e): Long; delete(id): Int; rename(id, name): Int; setPosition(id, position): Int; shiftPositions(low, high, delta); setTiming(...): Int; setProgression(...): Int /* also hold_count = 0 */; setCues(...): Int; setCounter(id, total: Int?, bestStreak, currentStreak, holdCount, lastCheckIn: Long?): Int }`
- `MetaDao { get(key): String?; put(meta) }`
- `HiitDatabase { entryDao(); metaDao(); NAME = "hiit.db"; KEY_V1_MIGRATED = "v1_migrated" }`
- `data/EntryMapping.kt`: `internal fun validTotal(raw: Int?): Int?`, `internal fun EntryEntity.toDomain(): Entry` (with per-field repair), `internal data class StoredCounter(total: Int? = null, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn: Long? = null)`, `internal fun entryEntity(name, position, timing = TimingConfig(), progression = ProgressionConfig(), cues = CueConfig(), counter = StoredCounter()): EntryEntity`
- `testutil/Entities.kt`: `fun testEntity(id = 0, name = "Workout", position = 0, total: Int? = null): EntryEntity` (defaults matching v1)

### Subtask 4.1: Entities, DAOs, database and exported schema

**Files:** Create `data/db/EntryEntity.kt`, `data/db/MetaEntity.kt`, `data/db/EntryDao.kt`, `data/db/MetaDao.kt`, `data/db/HiitDatabase.kt`; test `test/data/db/HiitDatabaseTest.kt`, `test/testutil/Entities.kt`. The build generates `app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/1.json`, which you commit.

- [ ] **Step 1: Failing tests** — `test/testutil/Entities.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.db.EntryEntity

/** A row with the v1 default settings and an untouched counter; vary it with copy(). */
fun testEntity(id: Long = 0, name: String = "Workout", position: Int = 0, total: Int? = null) = EntryEntity(
    id = id, name = name, position = position,
    prepareSec = 10, sets = 8, workSec = 20, restSec = 10, cooldownSec = 0,
    startingTotal = 48, floor = 48, cap = 72, holdAt = 64, holdFor = 4, windowHours = 36, penaltyHoursPerRep = 19.5,
    cueSound = true, cueVibration = true,
    total = total, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null,
)
```

`test/data/db/HiitDatabaseTest.kt`:
```kotlin
package com.mitenko.hiitcounter.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mitenko.hiitcounter.testutil.testEntity
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
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HiitDatabaseTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HiitDatabase::class.java)

    private lateinit var db: HiitDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun `entry rows round-trip including nulls`() = runTest {
        val row = testEntity(name = "Burpees", position = 0)
        val id = db.entryDao().insert(row)
        val stored = db.entryDao().get(id)!!
        assertEquals(row.copy(id = id), stored)
        assertNull(stored.total)
        assertNull(stored.lastCheckIn)
    }

    @Test
    fun `meta values are upserted by key`() = runTest {
        db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, "false"))
        db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, "true"))
        assertEquals("true", db.metaDao().get(HiitDatabase.KEY_V1_MIGRATED))
        assertNull(db.metaDao().get("missing"))
    }

    @Test
    fun `schema v1 is exported with a non-unique position index`() {
        // Unit tests run with the module directory (app/) as the working directory.
        val json = File("schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/1.json").readText()
        assertTrue(Regex("\"tableName\"\\s*:\\s*\"entry\"").containsMatchIn(json))
        assertTrue(Regex("\"tableName\"\\s*:\\s*\"meta\"").containsMatchIn(json))
        assertTrue(Regex("\"name\"\\s*:\\s*\"index_entry_position\"\\s*,\\s*\"unique\"\\s*:\\s*false").containsMatchIn(json))
    }

    @Test
    fun `schema v1 opens through MigrationTestHelper`() {
        helper.createDatabase("migration-helper-check", 1).close()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T4-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.db.HiitDatabaseTest"`. Expected: compile FAIL, `Unresolved reference 'HiitDatabase'` / `'EntryEntity'`.

- [ ] **Step 3: Implement**

`data/db/EntryEntity.kt`:
```kotlin
package com.mitenko.hiitcounter.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per entry (spec §5.2). `total` NULL reads as `starting_total`. The `position` index is
 * deliberately non-unique so in-transaction shifts never hit a constraint. Validity is enforced
 * by RoomEntryRepository on write and repaired per field on read (EntryMapping.kt).
 */
@Entity(tableName = "entry", indices = [Index(value = ["position"])])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    @ColumnInfo(name = "prepare_sec") val prepareSec: Int,
    val sets: Int,
    @ColumnInfo(name = "work_sec") val workSec: Int,
    @ColumnInfo(name = "rest_sec") val restSec: Int,
    @ColumnInfo(name = "cooldown_sec") val cooldownSec: Int,
    @ColumnInfo(name = "starting_total") val startingTotal: Int,
    val floor: Int,
    val cap: Int,
    @ColumnInfo(name = "hold_at") val holdAt: Int,
    @ColumnInfo(name = "hold_for") val holdFor: Int,
    @ColumnInfo(name = "window_hours") val windowHours: Int,
    @ColumnInfo(name = "penalty_hours_per_rep") val penaltyHoursPerRep: Double,
    @ColumnInfo(name = "cue_sound") val cueSound: Boolean,
    @ColumnInfo(name = "cue_vibration") val cueVibration: Boolean,
    val total: Int?,
    @ColumnInfo(name = "best_streak") val bestStreak: Int,
    @ColumnInfo(name = "current_streak") val currentStreak: Int,
    @ColumnInfo(name = "hold_count") val holdCount: Int,
    @ColumnInfo(name = "last_check_in") val lastCheckIn: Long?,
)
```

`data/db/MetaEntity.kt`:
```kotlin
package com.mitenko.hiitcounter.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key/value markers, e.g. `v1_migrated = "true"` (spec §5.2, §6). */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
```

`data/db/EntryDao.kt`:
```kotlin
package com.mitenko.hiitcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Row-level access. Ordering invariants live in RoomEntryRepository, which wraps these in transactions. */
@Dao
interface EntryDao {
    @Query("SELECT * FROM entry ORDER BY position, id")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry WHERE id = :id")
    fun observe(id: Long): Flow<EntryEntity?>

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun get(id: Long): EntryEntity?

    @Query("SELECT * FROM entry ORDER BY position, id")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT COUNT(*) FROM entry")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE entry SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String): Int

    @Query("UPDATE entry SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int): Int

    /** Adds [delta] to every position in [low]..[high] (inclusive). */
    @Query("UPDATE entry SET position = position + :delta WHERE position BETWEEN :low AND :high")
    suspend fun shiftPositions(low: Int, high: Int, delta: Int)

    @Query(
        "UPDATE entry SET prepare_sec = :prepareSec, sets = :sets, work_sec = :workSec, rest_sec = :restSec, " +
            "cooldown_sec = :cooldownSec WHERE id = :id",
    )
    suspend fun setTiming(id: Long, prepareSec: Int, sets: Int, workSec: Int, restSec: Int, cooldownSec: Int): Int

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

    @Query("UPDATE entry SET cue_sound = :sound, cue_vibration = :vibration WHERE id = :id")
    suspend fun setCues(id: Long, sound: Boolean, vibration: Boolean): Int

    /** Writes the whole counter group in one UPDATE. */
    @Query(
        "UPDATE entry SET total = :total, best_streak = :bestStreak, current_streak = :currentStreak, " +
            "hold_count = :holdCount, last_check_in = :lastCheckIn WHERE id = :id",
    )
    suspend fun setCounter(id: Long, total: Int?, bestStreak: Int, currentStreak: Int, holdCount: Int, lastCheckIn: Long?): Int
}
```

`data/db/MetaDao.kt`:
```kotlin
package com.mitenko.hiitcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MetaDao {
    @Query("SELECT `value` FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: MetaEntity)
}
```

`data/db/HiitDatabase.kt`:
```kotlin
package com.mitenko.hiitcounter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** `hiit.db` (spec §5.2). Schemas are exported to app/schemas and committed. */
@Database(entities = [EntryEntity::class, MetaEntity::class], version = 1, exportSchema = true)
abstract class HiitDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "hiit.db"
        const val KEY_V1_MIGRATED = "v1_migrated"
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T4-1-GREEN`, ~3 min): full suite + count. Expected: **149 tests**. Confirm `app/schemas/com.mitenko.hiitcounter.data.db.HiitDatabase/1.json` now exists.
  - This is the first run that generates a schema. The `tasks.configureEach` block from 1.2 orders `copyRoomSchemas*` before `merge*UnitTestAssets`, which should prevent two errors on this first generation: Gradle's implicit-dependency validation error, and `Cannot find the schema file in the assets folder` from `MigrationTestHelper`. If either appears, first confirm that the 1.2 block is present exactly as written, then re-run once.
  - **Stop and report only if the error persists** with the block in place. Don't disable the test.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): Room entry and meta tables with exported schema"` (include `app/schemas/`).

### Subtask 4.2: Entity → domain mapping, NULL total

**Files:** Create `data/EntryMapping.kt`; test `test/data/EntryMappingTest.kt` (plain JVM).

- [ ] **Step 1: Failing tests** — `test/data/EntryMappingTest.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.testEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class EntryMappingTest {
    @Test
    fun `a null total resolves to the starting total`() {
        assertEquals(55, testEntity(total = null).copy(startingTotal = 55).toDomain().counter.total)
    }

    @Test
    fun `an invalid total reads as null`() {
        assertNull(validTotal(0))
        assertNull(validTotal(-3))
        assertNull(validTotal(null))
        assertEquals(5, validTotal(5))
        assertEquals(48, testEntity(total = 0).toDomain().counter.total)
    }

    @Test
    fun `a valid row maps field by field`() {
        val row = testEntity(id = 7, name = "Burpees", position = 2, total = 65).copy(
            prepareSec = 5, sets = 6, workSec = 30, restSec = 15, cooldownSec = 60,
            startingTotal = 50, floor = 40, cap = 80, holdAt = 70, holdFor = 3, windowHours = 30, penaltyHoursPerRep = 12.5,
            cueSound = false, cueVibration = true,
            bestStreak = 24, currentStreak = 4, holdCount = 2, lastCheckIn = 1_790_000_000_123,
        )
        assertEquals(
            Entry(
                id = 7, name = "Burpees", position = 2,
                timing = TimingConfig(5, 6, 30, 15, 60),
                progression = ProgressionConfig(50, 40, 80, 70, 3, 30, 12.5),
                cues = CueConfig(sound = false, vibration = true),
                counter = CounterState(65, 24, 4, Instant.ofEpochMilli(1_790_000_000_123), 2),
            ),
            row.toDomain(),
        )
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T4-2-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.EntryMappingTest"`. Expected: compile FAIL, `Unresolved reference 'toDomain'` / `'validTotal'`.

- [ ] **Step 3: Implement** — `data/EntryMapping.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import com.mitenko.hiitcounter.data.db.EntryEntity
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import java.time.Instant

/** A stored total below 1 is invalid and reads as NULL (spec §5.2). */
internal fun validTotal(raw: Int?): Int? = raw?.takeIf { it >= 1 }

/** Spec §5.1 invariant: a NULL total is resolved to the starting total here, so the UI never sees null. */
internal fun EntryEntity.toDomain(): Entry {
    val progression = ProgressionConfig(startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep)
    return Entry(
        id = id,
        name = name,
        position = position,
        timing = TimingConfig(prepareSec, sets, workSec, restSec, cooldownSec),
        progression = progression,
        cues = CueConfig(cueSound, cueVibration),
        counter = CounterState(
            total = validTotal(total) ?: progression.startingTotal,
            bestStreak = bestStreak,
            currentStreak = currentStreak,
            lastCheckIn = lastCheckIn?.let(Instant::ofEpochMilli),
            holdCount = holdCount,
        ),
    )
}
```

- [ ] **Step 4: Run green** — Run (label `T4-2-GREEN`, ~3 min): full suite + count. Expected: **152 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): map entry rows to the domain with a resolved total"`

### Subtask 4.3: Per-field read repair and per-group fallback

**Files:** Replace `data/EntryMapping.kt`; append to `test/data/EntryMappingTest.kt`.

- [ ] **Step 1: Failing tests** — append to `EntryMappingTest`:
```kotlin
    @Test
    fun `one invalid timing field is repaired on its own`() {
        val timing = testEntity().copy(sets = 99, workSec = 30).toDomain().timing
        assertEquals(8, timing.sets)
        assertEquals(30, timing.workSec)
    }

    @Test
    fun `timing still inconsistent after repair falls back as a group`() {
        val entry = testEntity().copy(sets = 20, workSec = 3599, restSec = 3599, cap = 80).toDomain()
        assertEquals(TimingConfig(), entry.timing)
        assertEquals(80, entry.progression.cap)
    }

    @Test
    fun `progression fields are repaired per field`() {
        val p = testEntity().copy(penaltyHoursPerRep = Double.NaN, holdFor = -1, windowHours = 30).toDomain().progression
        assertEquals(19.5, p.penaltyHoursPerRep, 0.0)
        assertEquals(4, p.holdFor)
        assertEquals(30, p.windowHours)
    }

    @Test
    fun `inconsistent progression falls back as a group and timing is kept`() {
        val entry = testEntity().copy(floor = 80, cap = 60, sets = 6).toDomain()
        assertEquals(ProgressionConfig(), entry.progression)
        assertEquals(6, entry.timing.sets)
    }

    @Test
    fun `invalid counter values and names are repaired`() {
        val entry = testEntity().copy(name = "   ", bestStreak = 7, currentStreak = -1, holdCount = -2).toDomain()
        assertEquals("Workout", entry.name)
        assertEquals(7, entry.counter.bestStreak)
        assertEquals(0, entry.counter.currentStreak)
        assertEquals(0, entry.counter.holdCount)
        assertEquals("x".repeat(40), testEntity().copy(name = "x".repeat(45)).toDomain().name)
    }
```

- [ ] **Step 2: Run red** — Run (label `T4-3-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.EntryMappingTest"`. Expected: FAIL on the 5 new tests, because values pass through unrepaired (for example `expected:<8> but was:<99>`). The 3 tests from 4.2 still pass.

- [ ] **Step 3: Implement** — `data/EntryMapping.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.data

import android.util.Log
import com.mitenko.hiitcounter.data.db.EntryEntity
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import java.time.Instant

private const val TAG = "EntryMapping"
private const val FALLBACK_NAME = "Workout"

/** A stored total below 1 is invalid and reads as NULL (spec §5.2). */
internal fun validTotal(raw: Int?): Int? {
    if (raw == null || raw >= 1) return raw
    Log.w(TAG, "Invalid total=$raw; reading as NULL")
    return null
}

/** Per-field repair (spec §5.2): an invalid value is replaced by its default and logged. */
private inline fun <T> checked(id: Long, column: String, value: T, default: T, ok: (T) -> Boolean): T {
    if (ok(value)) return value
    Log.w(TAG, "Entry $id: invalid $column=$value; using default $default")
    return default
}

internal fun EntryEntity.repairedName(): String = when (val check = EntryNames.validate(name)) {
    is NameCheck.Ok -> check.name
    NameCheck.Empty -> FALLBACK_NAME.also { Log.w(TAG, "Entry $id: blank name; using $it") }
    NameCheck.TooLong -> name.trim().take(EntryNames.MAX_LENGTH).also { Log.w(TAG, "Entry $id: name too long; truncated") }
}

/** Per-field repair, then the timing group falls back to defaults only if still inconsistent. */
internal fun EntryEntity.timing(): TimingConfig {
    val d = TimingConfig()
    val max = SettingsValidator.MAX_PHASE_SEC
    val c = TimingConfig(
        prepareSec = checked(id, "prepare_sec", prepareSec, d.prepareSec) { it in 0..max },
        sets = checked(id, "sets", sets, d.sets) { it in 1..SettingsValidator.MAX_SETS },
        workSec = checked(id, "work_sec", workSec, d.workSec) { it in 1..max },
        restSec = checked(id, "rest_sec", restSec, d.restSec) { it in 0..max },
        cooldownSec = checked(id, "cooldown_sec", cooldownSec, d.cooldownSec) { it in 0..max },
    )
    if (SettingsValidator.timing(c).isValid) return c
    Log.w(TAG, "Entry $id: timing inconsistent ($c); using default timing")
    return d
}

/** Per-field repair, then the progression group falls back to defaults only if still inconsistent. */
internal fun EntryEntity.progression(): ProgressionConfig {
    val d = ProgressionConfig()
    val c = ProgressionConfig(
        startingTotal = checked(id, "starting_total", startingTotal, d.startingTotal) { it >= 1 },
        floor = checked(id, "floor", floor, d.floor) { it >= 1 },
        cap = checked(id, "cap", cap, d.cap) { it >= 1 },
        holdAt = checked(id, "hold_at", holdAt, d.holdAt) { it >= 1 },
        holdFor = checked(id, "hold_for", holdFor, d.holdFor) { it >= 0 },
        windowHours = checked(id, "window_hours", windowHours, d.windowHours) { it >= 1 },
        penaltyHoursPerRep = checked(id, "penalty_hours_per_rep", penaltyHoursPerRep, d.penaltyHoursPerRep) {
            it > 0.0 && it.isFinite()
        },
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "Entry $id: progression inconsistent ($c); using default progression")
    return d
}

internal fun EntryEntity.counter(startingTotal: Int): CounterState = CounterState(
    total = validTotal(total) ?: startingTotal,
    bestStreak = checked(id, "best_streak", bestStreak, 0) { it >= 0 },
    currentStreak = checked(id, "current_streak", currentStreak, 0) { it >= 0 },
    lastCheckIn = lastCheckIn?.let(Instant::ofEpochMilli),
    holdCount = checked(id, "hold_count", holdCount, 0) { it >= 0 },
)

/** Spec §5.1 invariant: a NULL total is resolved to the (repaired) starting total, so the UI never sees null. */
internal fun EntryEntity.toDomain(): Entry {
    val progression = progression()
    return Entry(
        id = id,
        name = repairedName(),
        position = position,
        timing = timing(),
        progression = progression,
        cues = CueConfig(cueSound, cueVibration),
        counter = counter(progression.startingTotal),
    )
}
```

- [ ] **Step 4: Run green** — Run (label `T4-3-GREEN`, ~3 min): full suite + count. Expected: **157 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryMapping.kt`, then `git add -A && git commit -m "feat(data): per-field read repair with per-group fallback"`

### Subtask 4.4: Row builders

**Files:** Replace `data/EntryMapping.kt`; append to `test/data/EntryMappingTest.kt`.

- [ ] **Step 1: Failing tests** — append to `EntryMappingTest`:
```kotlin
    @Test
    fun `a new row has default settings and an untouched counter`() {
        val row = entryEntity("Burpees", position = 3)
        assertNull(row.total)
        assertEquals(
            Entry(0, "Burpees", 3, TimingConfig(), ProgressionConfig(), CueConfig(), CounterState(total = 48)),
            row.toDomain(),
        )
    }

    @Test
    fun `stored counter fields are written as given`() {
        val row = entryEntity(
            "Workout", 0,
            counter = StoredCounter(total = 65, bestStreak = 24, currentStreak = 4, holdCount = 1, lastCheckIn = 1_000),
        )
        assertEquals(
            listOf<Any?>(65, 24, 4, 1, 1_000L),
            listOf(row.total, row.bestStreak, row.currentStreak, row.holdCount, row.lastCheckIn),
        )
    }
```

- [ ] **Step 2: Run red** — Run (label `T4-4-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.EntryMappingTest"`. Expected: compile FAIL, `Unresolved reference 'entryEntity'` / `'StoredCounter'`.

- [ ] **Step 3: Implement** — `data/EntryMapping.kt` (complete: the 4.3 file with the builders appended):
```kotlin
package com.mitenko.hiitcounter.data

import android.util.Log
import com.mitenko.hiitcounter.data.db.EntryEntity
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import java.time.Instant

private const val TAG = "EntryMapping"
private const val FALLBACK_NAME = "Workout"

/** A stored total below 1 is invalid and reads as NULL (spec §5.2). */
internal fun validTotal(raw: Int?): Int? {
    if (raw == null || raw >= 1) return raw
    Log.w(TAG, "Invalid total=$raw; reading as NULL")
    return null
}

/** Per-field repair (spec §5.2): an invalid value is replaced by its default and logged. */
private inline fun <T> checked(id: Long, column: String, value: T, default: T, ok: (T) -> Boolean): T {
    if (ok(value)) return value
    Log.w(TAG, "Entry $id: invalid $column=$value; using default $default")
    return default
}

internal fun EntryEntity.repairedName(): String = when (val check = EntryNames.validate(name)) {
    is NameCheck.Ok -> check.name
    NameCheck.Empty -> FALLBACK_NAME.also { Log.w(TAG, "Entry $id: blank name; using $it") }
    NameCheck.TooLong -> name.trim().take(EntryNames.MAX_LENGTH).also { Log.w(TAG, "Entry $id: name too long; truncated") }
}

/** Per-field repair, then the timing group falls back to defaults only if still inconsistent. */
internal fun EntryEntity.timing(): TimingConfig {
    val d = TimingConfig()
    val max = SettingsValidator.MAX_PHASE_SEC
    val c = TimingConfig(
        prepareSec = checked(id, "prepare_sec", prepareSec, d.prepareSec) { it in 0..max },
        sets = checked(id, "sets", sets, d.sets) { it in 1..SettingsValidator.MAX_SETS },
        workSec = checked(id, "work_sec", workSec, d.workSec) { it in 1..max },
        restSec = checked(id, "rest_sec", restSec, d.restSec) { it in 0..max },
        cooldownSec = checked(id, "cooldown_sec", cooldownSec, d.cooldownSec) { it in 0..max },
    )
    if (SettingsValidator.timing(c).isValid) return c
    Log.w(TAG, "Entry $id: timing inconsistent ($c); using default timing")
    return d
}

/** Per-field repair, then the progression group falls back to defaults only if still inconsistent. */
internal fun EntryEntity.progression(): ProgressionConfig {
    val d = ProgressionConfig()
    val c = ProgressionConfig(
        startingTotal = checked(id, "starting_total", startingTotal, d.startingTotal) { it >= 1 },
        floor = checked(id, "floor", floor, d.floor) { it >= 1 },
        cap = checked(id, "cap", cap, d.cap) { it >= 1 },
        holdAt = checked(id, "hold_at", holdAt, d.holdAt) { it >= 1 },
        holdFor = checked(id, "hold_for", holdFor, d.holdFor) { it >= 0 },
        windowHours = checked(id, "window_hours", windowHours, d.windowHours) { it >= 1 },
        penaltyHoursPerRep = checked(id, "penalty_hours_per_rep", penaltyHoursPerRep, d.penaltyHoursPerRep) {
            it > 0.0 && it.isFinite()
        },
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "Entry $id: progression inconsistent ($c); using default progression")
    return d
}

internal fun EntryEntity.counter(startingTotal: Int): CounterState = CounterState(
    total = validTotal(total) ?: startingTotal,
    bestStreak = checked(id, "best_streak", bestStreak, 0) { it >= 0 },
    currentStreak = checked(id, "current_streak", currentStreak, 0) { it >= 0 },
    lastCheckIn = lastCheckIn?.let(Instant::ofEpochMilli),
    holdCount = checked(id, "hold_count", holdCount, 0) { it >= 0 },
)

/** Spec §5.1 invariant: a NULL total is resolved to the (repaired) starting total, so the UI never sees null. */
internal fun EntryEntity.toDomain(): Entry {
    val progression = progression()
    return Entry(
        id = id,
        name = repairedName(),
        position = position,
        timing = timing(),
        progression = progression,
        cues = CueConfig(cueSound, cueVibration),
        counter = counter(progression.startingTotal),
    )
}

/** The counter group as stored: [total] null means "reads as the starting total". */
internal data class StoredCounter(
    val total: Int? = null,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val holdCount: Int = 0,
    val lastCheckIn: Long? = null,
)

/** A row for insertion (id assigned by Room). The defaults give a fresh entry with an untouched counter. */
internal fun entryEntity(
    name: String,
    position: Int,
    timing: TimingConfig = TimingConfig(),
    progression: ProgressionConfig = ProgressionConfig(),
    cues: CueConfig = CueConfig(),
    counter: StoredCounter = StoredCounter(),
): EntryEntity = EntryEntity(
    name = name,
    position = position,
    prepareSec = timing.prepareSec,
    sets = timing.sets,
    workSec = timing.workSec,
    restSec = timing.restSec,
    cooldownSec = timing.cooldownSec,
    startingTotal = progression.startingTotal,
    floor = progression.floor,
    cap = progression.cap,
    holdAt = progression.holdAt,
    holdFor = progression.holdFor,
    windowHours = progression.windowHours,
    penaltyHoursPerRep = progression.penaltyHoursPerRep,
    cueSound = cues.sound,
    cueVibration = cues.vibration,
    total = counter.total,
    bestStreak = counter.bestStreak,
    currentStreak = counter.currentStreak,
    holdCount = counter.holdCount,
    lastCheckIn = counter.lastCheckIn,
)
```

- [ ] **Step 4: Run green** — Run (label `T4-4-GREEN`, ~3 min): full suite + count. Expected: **159 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryMapping.kt`, then `git add -A && git commit -m "feat(data): entry row builders for create, duplicate and import"`

**Task 4 gate:** Run (label `T4-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **159 tests**, no lint errors.

---

## Task 5: EntryRepository — gate, reads, create, settings and counter writes (§5.3)

**Interfaces produced:**
- `interface MigrationGate { suspend fun awaitReady() }`
- `interface EntryRepository` (grows through 5.1–6.3 to the full §5.3 contract) and `class RoomEntryRepository(db: HiitDatabase, gate: MigrationGate, validationClock: Clock) : EntryRepository`
- `testutil/Assertions.kt`: `inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T`. It is inlined, so `block` may make suspend calls inside `runTest`.

### Subtask 5.1: Gate, entries, entry, create

**Files:** Create `data/EntryRepository.kt`; test `test/data/RoomEntryRepositoryTest.kt`, `test/testutil/Assertions.kt`.

- [ ] **Step 1: Failing tests** — `test/testutil/Assertions.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

/** Returns the thrown [T]; fails if nothing or something else is thrown. Inlined, so [block] may suspend. */
inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.java.simpleName} but got ${e::class.java.simpleName}", e)
    }
    throw AssertionError("Expected ${T::class.java.simpleName} but nothing was thrown")
}
```

`test/data/RoomEntryRepositoryTest.kt` (the import list already covers Task 6):
```kotlin
package com.mitenko.hiitcounter.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.expectThrows
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomEntryRepositoryTest {
    private lateinit var db: HiitDatabase
    private val clock = FakeClock()
    private val open = object : MigrationGate {
        override suspend fun awaitReady() = Unit
    }

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

    private fun repo(gate: MigrationGate = open) = RoomEntryRepository(db, gate, clock)

    private suspend fun order() = db.entryDao().getAll().map { it.name to it.position }

    @Test
    fun `create appends entries with defaults in order`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("  Lunges ")
        assertNotEquals(a, b)
        assertEquals(listOf("Burpees" to 0, "Lunges" to 1), order())
        assertEquals(listOf(a, b), r.entries.first().map { it.id })
        val entry = r.entry(a).first()!!
        assertEquals(TimingConfig(), entry.timing)
        assertEquals(ProgressionConfig(), entry.progression)
        assertEquals(CueConfig(), entry.cues)
        assertEquals(CounterState(total = 48), entry.counter)
    }

    @Test
    fun `entry emits null for an unknown id`() = runTest {
        assertNull(repo().entry(99).first())
    }

    @Test
    fun `reads and writes wait for the migration gate`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val r = repo(object : MigrationGate {
            override suspend fun awaitReady() = gate.await()
        })
        val list = async { r.entries.first() }
        val created = async { r.create("Burpees") }
        runCurrent()
        assertFalse(list.isCompleted)
        assertFalse(created.isCompleted)
        assertEquals(0, db.entryDao().count())
        gate.complete(Unit)
        assertTrue(created.await() > 0)
        list.await()
    }

    @Test
    fun `create rejects invalid names and writes nothing`() = runTest {
        val r = repo()
        assertEquals("Enter a name", expectThrows<IllegalArgumentException> { r.create("   ") }.message)
        assertEquals("Use at most 40 characters", expectThrows<IllegalArgumentException> { r.create("x".repeat(41)) }.message)
        assertEquals(0, db.entryDao().count())
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T5-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.RoomEntryRepositoryTest"`. Expected: compile FAIL, `Unresolved reference 'MigrationGate'` / `'RoomEntryRepository'`.

- [ ] **Step 3: Implement** — `data/EntryRepository.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Per-entry persistence (spec §5.3). Every method and both flows await the migration gate first.
 * Invalid input throws IllegalArgumentException and writes nothing.
 */
interface EntryRepository {
    /** Ordered by position, then id. */
    val entries: Flow<List<Entry>>

    /** Emits null once loaded if the entry does not exist. */
    fun entry(id: Long): Flow<Entry?>

    /** A new entry with default settings, appended at the end. */
    suspend fun create(name: String): Long
}

/** Completes once the v1 import has run (spec §6). */
interface MigrationGate {
    suspend fun awaitReady()
}

/**
 * Room-backed [EntryRepository]. Suspend calls return on the caller's dispatcher: ViewModels call
 * it from viewModelScope (Main) and keep calling TimerController on Main — never wrap it in
 * withContext(Dispatchers.IO) around controller calls (spec §5.3 threading).
 * [validationClock] is only used to reject a last check-in in the future.
 */
class RoomEntryRepository(
    private val db: HiitDatabase,
    private val gate: MigrationGate,
    private val validationClock: Clock,
) : EntryRepository {
    private val dao = db.entryDao()

    override val entries: Flow<List<Entry>> = flow {
        gate.awaitReady()
        emitAll(dao.observeAll())
    }.map { rows -> rows.map { it.toDomain() } }

    override fun entry(id: Long): Flow<Entry?> = flow {
        gate.awaitReady()
        emitAll(dao.observe(id))
    }.map { it?.toDomain() }

    override suspend fun create(name: String): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count())) }
    }

    private fun requireName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }
}
```
(`validationClock` is unused until 5.3. The warning is expected. The constructor is final from here on.)

- [ ] **Step 4: Run green** — Run (label `T5-1-GREEN`, ~3 min): full suite + count. Expected: **163 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): entry repository reads and create behind the migration gate"`

### Subtask 5.2: Rename and settings writes

**Files:** Replace `data/EntryRepository.kt`; append to `test/data/RoomEntryRepositoryTest.kt`.

- [ ] **Step 1: Failing tests** — append to `RoomEntryRepositoryTest`:
```kotlin
    @Test
    fun `rename trims and validates`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.rename(a, "  Kettlebell Lunges  ")
        assertEquals("Kettlebell Lunges", r.entry(a).first()!!.name)
        expectThrows<IllegalArgumentException> { r.rename(a, "") }
        assertEquals("Kettlebell Lunges", r.entry(a).first()!!.name)
    }

    @Test
    fun `settings writes persist per entry`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        r.setTiming(a, TimingConfig(sets = 6))
        r.setProgression(a, ProgressionConfig(cap = 80))
        r.setCues(a, CueConfig(sound = false))
        val ea = r.entry(a).first()!!
        assertEquals(TimingConfig(sets = 6), ea.timing)
        assertEquals(ProgressionConfig(cap = 80), ea.progression)
        assertEquals(CueConfig(sound = false), ea.cues)
        val eb = r.entry(b).first()!!
        assertEquals(TimingConfig(), eb.timing)
        assertEquals(ProgressionConfig(), eb.progression)
        assertEquals(CueConfig(), eb.cues)
    }

    @Test
    fun `setProgression resets holdCount in the same update`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        r.setProgression(a, ProgressionConfig(holdFor = 2))
        val e = r.entry(a).first()!!
        assertEquals(0, e.counter.holdCount)
        assertEquals(64, e.counter.total)
        assertEquals(2, e.progression.holdFor)
    }

    @Test
    fun `invalid settings are rejected and nothing is written`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        expectThrows<IllegalArgumentException> { r.setTiming(a, TimingConfig(sets = 0)) }
        expectThrows<IllegalArgumentException> { r.setTiming(a, TimingConfig(sets = 20, workSec = 3599)) }
        expectThrows<IllegalArgumentException> { r.setProgression(a, ProgressionConfig(floor = 80, cap = 60)) }
        val e = r.entry(a).first()!!
        assertEquals(TimingConfig(), e.timing)
        assertEquals(ProgressionConfig(), e.progression)
    }

    @Test
    fun `settings writes on missing ids throw EntryNotFound`() = runTest {
        val r = repo()
        assertEquals(99L, expectThrows<EntryNotFound> { r.rename(99, "Burpees") }.id)
        expectThrows<EntryNotFound> { r.setTiming(99, TimingConfig()) }
        expectThrows<EntryNotFound> { r.setProgression(99, ProgressionConfig()) }
        expectThrows<EntryNotFound> { r.setCues(99, CueConfig()) }
    }
```

- [ ] **Step 2: Run red** — Run (label `T5-2-RED`, ~2 min): focused `RoomEntryRepositoryTest`. Expected: compile FAIL, `Unresolved reference 'rename'`.

- [ ] **Step 3: Implement** — `data/EntryRepository.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.data

import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Per-entry persistence (spec §5.3). Every method and both flows await the migration gate first.
 * Missing ids throw [EntryNotFound]; invalid input throws IllegalArgumentException and writes nothing.
 */
interface EntryRepository {
    /** Ordered by position, then id. */
    val entries: Flow<List<Entry>>

    /** Emits null once loaded if the entry does not exist. */
    fun entry(id: Long): Flow<Entry?>

    /** A new entry with default settings, appended at the end. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun setTiming(id: Long, timing: TimingConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)

    suspend fun setCues(id: Long, cues: CueConfig)
}

/** Completes once the v1 import has run (spec §6). */
interface MigrationGate {
    suspend fun awaitReady()
}

/**
 * Room-backed [EntryRepository]. Suspend calls return on the caller's dispatcher: ViewModels call
 * it from viewModelScope (Main) and keep calling TimerController on Main — never wrap it in
 * withContext(Dispatchers.IO) around controller calls (spec §5.3 threading).
 * [validationClock] is only used to reject a last check-in in the future.
 */
class RoomEntryRepository(
    private val db: HiitDatabase,
    private val gate: MigrationGate,
    private val validationClock: Clock,
) : EntryRepository {
    private val dao = db.entryDao()

    override val entries: Flow<List<Entry>> = flow {
        gate.awaitReady()
        emitAll(dao.observeAll())
    }.map { rows -> rows.map { it.toDomain() } }

    override fun entry(id: Long): Flow<Entry?> = flow {
        gate.awaitReady()
        emitAll(dao.observe(id))
    }.map { it?.toDomain() }

    override suspend fun create(name: String): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count())) }
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = requireName(name)
        gate.awaitReady()
        found(id, dao.rename(id, valid))
    }

    override suspend fun setTiming(id: Long, timing: TimingConfig) {
        require(SettingsValidator.timing(timing).isValid) { "Invalid timing: $timing" }
        gate.awaitReady()
        found(id, with(timing) { dao.setTiming(id, prepareSec, sets, workSec, restSec, cooldownSec) })
    }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        found(
            id,
            with(progression) { dao.setProgression(id, startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep) },
        )
    }

    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration))
    }

    private fun requireName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }

    private fun found(id: Long, updatedRows: Int) {
        if (updatedRows == 0) throw EntryNotFound(id)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-2-GREEN`, ~3 min): full suite + count. Expected: **168 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryRepository.kt`, then `git add -A && git commit -m "feat(data): validated rename and settings writes per entry"`

### Subtask 5.3: overwriteCounter and resetProgress

**Files:** Replace `data/EntryRepository.kt`; append to `test/data/RoomEntryRepositoryTest.kt`.

- [ ] **Step 1: Failing tests** — append to `RoomEntryRepositoryTest`:
```kotlin
    @Test
    fun `overwriteCounter writes every field and resets holdCount`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        db.entryDao().setCounter(a, total = 64, bestStreak = 1, currentStreak = 1, holdCount = 3, lastCheckIn = null)
        val last = clock.instant.minusSeconds(3600)
        r.overwriteCounter(a, total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = last)
        assertEquals(CounterState(65, 24, 4, last, 0), r.entry(a).first()!!.counter)
    }

    @Test
    fun `overwriteCounter validates like Current State`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 60, bestStreak = 3, currentStreak = 4, lastCheckIn = null) }
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 60, 5, 4, clock.instant.plusSeconds(60)) }
        expectThrows<IllegalArgumentException> { r.overwriteCounter(a, 0, 0, 0, null) }
        assertEquals(CounterState(total = 48), r.entry(a).first()!!.counter)
    }

    @Test
    fun `resetProgress returns the counter to its untouched state`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.overwriteCounter(a, 65, 24, 4, clock.instant.minusSeconds(60))
        r.resetProgress(a)
        assertNull(db.entryDao().get(a)!!.total)
        assertEquals(CounterState(total = 48), r.entry(a).first()!!.counter)
        r.setProgression(a, ProgressionConfig(startingTotal = 55))
        assertEquals(55, r.entry(a).first()!!.counter.total)
    }

    @Test
    fun `counter writes on missing ids throw EntryNotFound`() = runTest {
        val r = repo()
        expectThrows<EntryNotFound> { r.overwriteCounter(99, 60, 0, 0, null) }
        expectThrows<EntryNotFound> { r.resetProgress(99) }
    }
```

- [ ] **Step 2: Run red** — Run (label `T5-3-RED`, ~2 min): focused `RoomEntryRepositoryTest`. Expected: compile FAIL, `Unresolved reference 'overwriteCounter'`.

- [ ] **Step 3: Implement** — `data/EntryRepository.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.data

import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Per-entry persistence (spec §5.3). Every method and both flows await the migration gate first.
 * Missing ids throw [EntryNotFound]; invalid input throws IllegalArgumentException and writes nothing.
 */
interface EntryRepository {
    /** Ordered by position, then id. */
    val entries: Flow<List<Entry>>

    /** Emits null once loaded if the entry does not exist. */
    fun entry(id: Long): Flow<Entry?>

    /** A new entry with default settings, appended at the end. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun setTiming(id: Long, timing: TimingConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)

    suspend fun setCues(id: Long, cues: CueConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)

    /** total NULL, streaks 0, lastCheckIn NULL, holdCount 0. */
    suspend fun resetProgress(id: Long)
}

/** Completes once the v1 import has run (spec §6). */
interface MigrationGate {
    suspend fun awaitReady()
}

/**
 * Room-backed [EntryRepository]. Suspend calls return on the caller's dispatcher: ViewModels call
 * it from viewModelScope (Main) and keep calling TimerController on Main — never wrap it in
 * withContext(Dispatchers.IO) around controller calls (spec §5.3 threading).
 * [validationClock] is only used to reject a last check-in in the future.
 */
class RoomEntryRepository(
    private val db: HiitDatabase,
    private val gate: MigrationGate,
    private val validationClock: Clock,
) : EntryRepository {
    private val dao = db.entryDao()

    override val entries: Flow<List<Entry>> = flow {
        gate.awaitReady()
        emitAll(dao.observeAll())
    }.map { rows -> rows.map { it.toDomain() } }

    override fun entry(id: Long): Flow<Entry?> = flow {
        gate.awaitReady()
        emitAll(dao.observe(id))
    }.map { it?.toDomain() }

    override suspend fun create(name: String): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count())) }
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = requireName(name)
        gate.awaitReady()
        found(id, dao.rename(id, valid))
    }

    override suspend fun setTiming(id: Long, timing: TimingConfig) {
        require(SettingsValidator.timing(timing).isValid) { "Invalid timing: $timing" }
        gate.awaitReady()
        found(id, with(timing) { dao.setTiming(id, prepareSec, sets, workSec, restSec, cooldownSec) })
    }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        found(
            id,
            with(progression) { dao.setProgression(id, startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep) },
        )
    }

    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration))
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        // Only currentState's hints depend on the progression, so the defaults are enough to decide validity.
        val check = SettingsValidator.currentState(
            total, bestStreak, currentStreak, lastCheckIn, validationClock.now(), ProgressionConfig(),
        )
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        found(
            id,
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount = 0, lastCheckIn = lastCheckIn?.toEpochMilli()),
        )
    }

    override suspend fun resetProgress(id: Long) {
        gate.awaitReady()
        found(id, dao.setCounter(id, total = null, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null))
    }

    private fun requireName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }

    private fun found(id: Long, updatedRows: Int) {
        if (updatedRows == 0) throw EntryNotFound(id)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-3-GREEN`, ~3 min): full suite + count. Expected: **172 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryRepository.kt`, then `git add -A && git commit -m "feat(data): validated counter overwrite and progress reset"`

**Task 5 gate:** Run (label `T5-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **172 tests**, no lint errors.

---

## Task 6: Ordering operations and check-in (§5.3)

**Interfaces produced:** `EntryRepository.duplicate(id): Long`, `delete(id)`, `moveBy(id, delta)` and `checkIn(id, clock): CheckInResult`. This completes the §5.3 contract.

### Subtask 6.1: delete and duplicate

**Files:** Replace `data/EntryRepository.kt`; append to `test/data/RoomEntryRepositoryTest.kt`.

- [ ] **Step 1: Failing tests** — append to `RoomEntryRepositoryTest`:
```kotlin
    @Test
    fun `delete compacts later positions`() = runTest {
        val r = repo()
        r.create("A")
        val b = r.create("B")
        r.create("C")
        r.create("D")
        r.delete(b)
        assertEquals(listOf("A" to 0, "C" to 1, "D" to 2), order())
        assertEquals(listOf("A", "C", "D"), r.entries.first().map { it.name })
    }

    @Test
    fun `duplicate appends a copy with the config and a fresh counter`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setTiming(a, TimingConfig(sets = 6))
        r.setProgression(a, ProgressionConfig(startingTotal = 50, floor = 40))
        r.setCues(a, CueConfig(vibration = false))
        val last = clock.instant.minusSeconds(60)
        r.overwriteCounter(a, 65, 24, 4, last)
        r.create("Lunges")
        val copy = r.duplicate(a)
        val e = r.entry(copy).first()!!
        assertEquals("Burpees copy", e.name)
        assertEquals(2, e.position)
        assertEquals(TimingConfig(sets = 6), e.timing)
        assertEquals(ProgressionConfig(startingTotal = 50, floor = 40), e.progression)
        assertEquals(CueConfig(vibration = false), e.cues)
        assertEquals(CounterState(total = 50), e.counter)
        assertNull(db.entryDao().get(copy)!!.total)
        assertEquals(CounterState(65, 24, 4, last, 0), r.entry(a).first()!!.counter)
    }

    @Test
    fun `duplicate keeps the suffix on a 40-character name`() = runTest {
        val r = repo()
        val a = r.create("x".repeat(40))
        assertEquals("x".repeat(35) + " copy", r.entry(r.duplicate(a)).first()!!.name)
    }

    @Test
    fun `delete and duplicate throw EntryNotFound for missing ids`() = runTest {
        val r = repo()
        expectThrows<EntryNotFound> { r.delete(99) }
        expectThrows<EntryNotFound> { r.duplicate(99) }
    }
```

- [ ] **Step 2: Run red** — Run (label `T6-1-RED`, ~2 min): focused `RoomEntryRepositoryTest`. Expected: compile FAIL, `Unresolved reference 'delete'` / `'duplicate'`.

- [ ] **Step 3: Implement** — `data/EntryRepository.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.data

import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Per-entry persistence (spec §5.3). Every method and both flows await the migration gate first.
 * Missing ids throw [EntryNotFound]; invalid input throws IllegalArgumentException and writes nothing.
 */
interface EntryRepository {
    /** Ordered by position, then id. */
    val entries: Flow<List<Entry>>

    /** Emits null once loaded if the entry does not exist. */
    fun entry(id: Long): Flow<Entry?>

    /** A new entry with default settings, appended at the end. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    /** Copies the config with a fresh counter and the §5.5 name, appended at the end. */
    suspend fun duplicate(id: Long): Long

    /** Shifts every later row down by one. */
    suspend fun delete(id: Long)

    suspend fun setTiming(id: Long, timing: TimingConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)

    suspend fun setCues(id: Long, cues: CueConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)

    /** total NULL, streaks 0, lastCheckIn NULL, holdCount 0. */
    suspend fun resetProgress(id: Long)
}

/** Completes once the v1 import has run (spec §6). */
interface MigrationGate {
    suspend fun awaitReady()
}

/**
 * Room-backed [EntryRepository]. Suspend calls return on the caller's dispatcher: ViewModels call
 * it from viewModelScope (Main) and keep calling TimerController on Main — never wrap it in
 * withContext(Dispatchers.IO) around controller calls (spec §5.3 threading).
 * [validationClock] is only used to reject a last check-in in the future.
 */
class RoomEntryRepository(
    private val db: HiitDatabase,
    private val gate: MigrationGate,
    private val validationClock: Clock,
) : EntryRepository {
    private val dao = db.entryDao()

    override val entries: Flow<List<Entry>> = flow {
        gate.awaitReady()
        emitAll(dao.observeAll())
    }.map { rows -> rows.map { it.toDomain() } }

    override fun entry(id: Long): Flow<Entry?> = flow {
        gate.awaitReady()
        emitAll(dao.observe(id))
    }.map { it?.toDomain() }

    override suspend fun create(name: String): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count())) }
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = requireName(name)
        gate.awaitReady()
        found(id, dao.rename(id, valid))
    }

    override suspend fun duplicate(id: Long): Long {
        gate.awaitReady()
        return db.withTransaction {
            val source = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            dao.insert(
                entryEntity(
                    name = EntryNames.duplicateName(source.name),
                    position = dao.count(),
                    timing = source.timing,
                    progression = source.progression,
                    cues = source.cues,
                ),
            )
        }
    }

    override suspend fun delete(id: Long) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            dao.delete(id)
            dao.shiftPositions(low = row.position + 1, high = Int.MAX_VALUE, delta = -1)
        }
    }

    override suspend fun setTiming(id: Long, timing: TimingConfig) {
        require(SettingsValidator.timing(timing).isValid) { "Invalid timing: $timing" }
        gate.awaitReady()
        found(id, with(timing) { dao.setTiming(id, prepareSec, sets, workSec, restSec, cooldownSec) })
    }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        found(
            id,
            with(progression) { dao.setProgression(id, startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep) },
        )
    }

    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration))
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        // Only currentState's hints depend on the progression, so the defaults are enough to decide validity.
        val check = SettingsValidator.currentState(
            total, bestStreak, currentStreak, lastCheckIn, validationClock.now(), ProgressionConfig(),
        )
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        found(
            id,
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount = 0, lastCheckIn = lastCheckIn?.toEpochMilli()),
        )
    }

    override suspend fun resetProgress(id: Long) {
        gate.awaitReady()
        found(id, dao.setCounter(id, total = null, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null))
    }

    private fun requireName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }

    private fun found(id: Long, updatedRows: Int) {
        if (updatedRows == 0) throw EntryNotFound(id)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T6-1-GREEN`, ~3 min): full suite + count. Expected: **176 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryRepository.kt`, then `git add -A && git commit -m "feat(data): delete with position compaction and duplicate with fresh counter"`

### Subtask 6.2: moveBy

**Files:** Replace `data/EntryRepository.kt`; append to `test/data/RoomEntryRepositoryTest.kt`.

- [ ] **Step 1: Failing tests** — append to `RoomEntryRepositoryTest`:
```kotlin
    @Test
    fun `moveBy moves one step and shifts the neighbour`() = runTest {
        val r = repo()
        val a = r.create("A")
        r.create("B")
        val c = r.create("C")
        r.moveBy(c, -1)
        assertEquals(listOf("A" to 0, "C" to 1, "B" to 2), order())
        r.moveBy(a, +1)
        assertEquals(listOf("C" to 0, "A" to 1, "B" to 2), order())
    }

    @Test
    fun `first up and last down are no-ops`() = runTest {
        val r = repo()
        val a = r.create("A")
        r.create("B")
        val c = r.create("C")
        r.moveBy(a, -1)
        r.moveBy(c, +1)
        assertEquals(listOf("A" to 0, "B" to 1, "C" to 2), order())
    }

    @Test
    fun `large deltas clamp to the list bounds`() = runTest {
        val r = repo()
        val a = r.create("A")
        r.create("B")
        r.create("C")
        r.moveBy(a, +10)
        assertEquals(listOf("B" to 0, "C" to 1, "A" to 2), order())
        r.moveBy(a, Int.MIN_VALUE)
        assertEquals(listOf("A" to 0, "B" to 1, "C" to 2), order())
    }

    @Test
    fun `rapid repeated moves act on the current order`() = runTest {
        val r = repo()
        val first = r.create("E0")
        repeat(4) { r.create("E${it + 1}") }
        List(6) { async { r.moveBy(first, +1) } }.awaitAll()
        assertEquals(listOf("E1", "E2", "E3", "E4", "E0"), order().map { it.first })
        assertEquals((0..4).toList(), order().map { it.second })
    }

    @Test
    fun `a hundred entries stay contiguous and ordered through random moves`() = runTest {
        val r = repo()
        val model = MutableList(100) { r.create("E$it") }
        val random = Random(42)
        repeat(300) {
            val id = model[random.nextInt(model.size)]
            val delta = random.nextInt(-5, 6)
            r.moveBy(id, delta)
            val from = model.indexOf(id)
            model.removeAt(from)
            model.add((from + delta).coerceIn(0, model.size), id)
        }
        val rows = db.entryDao().getAll()
        assertEquals(model, rows.map { it.id })
        assertEquals((0 until 100).toList(), rows.map { it.position })
    }

    @Test
    fun `moveBy throws EntryNotFound for a missing id`() = runTest {
        expectThrows<EntryNotFound> { repo().moveBy(99, 1) }
    }
```

- [ ] **Step 2: Run red** — Run (label `T6-2-RED`, ~2 min): focused `RoomEntryRepositoryTest`. Expected: compile FAIL, `Unresolved reference 'moveBy'`.

- [ ] **Step 3: Implement** — `data/EntryRepository.kt` (complete: the 6.1 file with `moveBy` in the interface after `delete`, and its implementation after `delete`):
```kotlin
package com.mitenko.hiitcounter.data

import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Per-entry persistence (spec §5.3). Every method and both flows await the migration gate first.
 * Missing ids throw [EntryNotFound]; invalid input throws IllegalArgumentException and writes nothing.
 */
interface EntryRepository {
    /** Ordered by position, then id. */
    val entries: Flow<List<Entry>>

    /** Emits null once loaded if the entry does not exist. */
    fun entry(id: Long): Flow<Entry?>

    /** A new entry with default settings, appended at the end. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    /** Copies the config with a fresh counter and the §5.5 name, appended at the end. */
    suspend fun duplicate(id: Long): Long

    /** Shifts every later row down by one. */
    suspend fun delete(id: Long)

    /** Target = (position + delta) clamped to the list bounds; a no-op when it equals the current position. */
    suspend fun moveBy(id: Long, delta: Int)

    suspend fun setTiming(id: Long, timing: TimingConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)

    suspend fun setCues(id: Long, cues: CueConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)

    /** total NULL, streaks 0, lastCheckIn NULL, holdCount 0. */
    suspend fun resetProgress(id: Long)
}

/** Completes once the v1 import has run (spec §6). */
interface MigrationGate {
    suspend fun awaitReady()
}

/**
 * Room-backed [EntryRepository]. Suspend calls return on the caller's dispatcher: ViewModels call
 * it from viewModelScope (Main) and keep calling TimerController on Main — never wrap it in
 * withContext(Dispatchers.IO) around controller calls (spec §5.3 threading).
 * [validationClock] is only used to reject a last check-in in the future.
 */
class RoomEntryRepository(
    private val db: HiitDatabase,
    private val gate: MigrationGate,
    private val validationClock: Clock,
) : EntryRepository {
    private val dao = db.entryDao()

    override val entries: Flow<List<Entry>> = flow {
        gate.awaitReady()
        emitAll(dao.observeAll())
    }.map { rows -> rows.map { it.toDomain() } }

    override fun entry(id: Long): Flow<Entry?> = flow {
        gate.awaitReady()
        emitAll(dao.observe(id))
    }.map { it?.toDomain() }

    override suspend fun create(name: String): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count())) }
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = requireName(name)
        gate.awaitReady()
        found(id, dao.rename(id, valid))
    }

    override suspend fun duplicate(id: Long): Long {
        gate.awaitReady()
        return db.withTransaction {
            val source = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            dao.insert(
                entryEntity(
                    name = EntryNames.duplicateName(source.name),
                    position = dao.count(),
                    timing = source.timing,
                    progression = source.progression,
                    cues = source.cues,
                ),
            )
        }
    }

    override suspend fun delete(id: Long) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            dao.delete(id)
            dao.shiftPositions(low = row.position + 1, high = Int.MAX_VALUE, delta = -1)
        }
    }

    override suspend fun moveBy(id: Long, delta: Int) {
        gate.awaitReady()
        db.withTransaction {
            // Computed inside the transaction, so rapid repeated taps never act on a stale list.
            val row = dao.get(id) ?: throw EntryNotFound(id)
            val last = dao.count() - 1
            val target = (row.position.toLong() + delta).coerceIn(0L, last.toLong()).toInt()
            if (target != row.position) {
                if (target < row.position) {
                    dao.shiftPositions(low = target, high = row.position - 1, delta = 1)
                } else {
                    dao.shiftPositions(low = row.position + 1, high = target, delta = -1)
                }
                dao.setPosition(id, target)
            }
        }
    }

    override suspend fun setTiming(id: Long, timing: TimingConfig) {
        require(SettingsValidator.timing(timing).isValid) { "Invalid timing: $timing" }
        gate.awaitReady()
        found(id, with(timing) { dao.setTiming(id, prepareSec, sets, workSec, restSec, cooldownSec) })
    }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        found(
            id,
            with(progression) { dao.setProgression(id, startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep) },
        )
    }

    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration))
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        // Only currentState's hints depend on the progression, so the defaults are enough to decide validity.
        val check = SettingsValidator.currentState(
            total, bestStreak, currentStreak, lastCheckIn, validationClock.now(), ProgressionConfig(),
        )
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        found(
            id,
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount = 0, lastCheckIn = lastCheckIn?.toEpochMilli()),
        )
    }

    override suspend fun resetProgress(id: Long) {
        gate.awaitReady()
        found(id, dao.setCounter(id, total = null, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null))
    }

    private fun requireName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }

    private fun found(id: Long, updatedRows: Int) {
        if (updatedRows == 0) throw EntryNotFound(id)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T6-2-GREEN`, ~4 min): full suite + count. Expected: **182 tests**. The 100-entry test takes a few seconds under Robolectric.

- [ ] **Step 5: Commit** — diff-review `EntryRepository.kt`, then `git add -A && git commit -m "feat(data): clamped moveBy computed inside one transaction"`

### Subtask 6.3: checkIn — one transaction, row's own progression, concurrency and interleavings

**Files:** Replace `data/EntryRepository.kt`; append to `test/data/RoomEntryRepositoryTest.kt`.

SQLite serializes writes, so a settings save and a check-in on the same entry run in one order or the other. The two interleaving tests pin down both orders (§5.3).

- [ ] **Step 1: Failing tests** — append to `RoomEntryRepositoryTest`:
```kotlin
    @Test
    fun `check-in uses the row's own progression`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        r.setProgression(a, ProgressionConfig(startingTotal = 20, floor = 20, cap = 30))
        val result = r.checkIn(a, clock)
        assertEquals(Outcome.First, result.outcome)
        assertEquals(20, result.state.total)
        assertEquals(CounterState(total = 48), r.entry(b).first()!!.counter)
    }

    @Test
    fun `a null total resolves to the starting total and is written on the first check-in`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.checkIn(a, clock)
        val row = db.entryDao().get(a)!!
        assertEquals(48, row.total)
        assertEquals(clock.instant.toEpochMilli(), row.lastCheckIn)
        assertEquals(1, row.currentStreak)
    }

    @Test
    fun `a second check-in the same day writes nothing`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val first = r.checkIn(a, clock)
        clock.instant = clock.instant.plusSeconds(600)
        val second = r.checkIn(a, clock)
        assertEquals(Outcome.AlreadyToday, second.outcome)
        assertEquals(first.state, r.entry(a).first()!!.counter)
    }

    @Test
    fun `concurrent check-ins on one entry record exactly one`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val results = List(5) { async { r.checkIn(a, clock) } }.awaitAll()
        assertEquals(1, results.count { it.outcome != Outcome.AlreadyToday })
    }

    @Test
    fun `two entries check in independently`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        val b = r.create("Lunges")
        assertEquals(Outcome.First, r.checkIn(a, clock).outcome)
        assertEquals(Outcome.First, r.checkIn(b, clock).outcome)
        val day1 = clock.instant
        clock.instant = day1.plusSeconds(24 * 3600)
        assertEquals(Outcome.OnTime, r.checkIn(a, clock).outcome)
        assertEquals(49, r.entry(a).first()!!.counter.total)
        assertEquals(CounterState(48, 1, 1, day1, 0), r.entry(b).first()!!.counter)
    }

    @Test
    fun `a settings save before the check-in applies the new rules`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setProgression(a, ProgressionConfig(startingTotal = 30, floor = 30, cap = 40))
        assertEquals(30, r.checkIn(a, clock).state.total)
    }

    @Test
    fun `a settings save after the check-in keeps both and resets the hold`() = runTest {
        val r = repo()
        val a = r.create("Burpees")
        r.setProgression(a, ProgressionConfig(startingTotal = 64))
        assertEquals(1, r.checkIn(a, clock).state.holdCount)
        r.setProgression(a, ProgressionConfig(startingTotal = 64, holdFor = 2))
        val e = r.entry(a).first()!!
        assertEquals(0, e.counter.holdCount)
        assertEquals(64, e.counter.total)
        assertEquals(clock.instant, e.counter.lastCheckIn)
        assertEquals(2, e.progression.holdFor)
    }

    @Test
    fun `checkIn throws EntryNotFound for a missing id`() = runTest {
        expectThrows<EntryNotFound> { repo().checkIn(99, clock) }
    }
```

- [ ] **Step 2: Run red** — Run (label `T6-3-RED`, ~2 min): focused `RoomEntryRepositoryTest`. Expected: compile FAIL, `Unresolved reference 'checkIn'`.

- [ ] **Step 3: Implement** — `data/EntryRepository.kt` (complete, final):
```kotlin
package com.mitenko.hiitcounter.data

import android.util.Log
import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.RepProgression
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private const val TAG = "EntryRepository"

/**
 * Per-entry persistence (spec §5.3). Every method and both flows await the migration gate first.
 * Missing ids throw [EntryNotFound]; invalid input throws IllegalArgumentException and writes nothing.
 */
interface EntryRepository {
    /** Ordered by position, then id. */
    val entries: Flow<List<Entry>>

    /** Emits null once loaded if the entry does not exist. */
    fun entry(id: Long): Flow<Entry?>

    /** A new entry with default settings, appended at the end. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    /** Copies the config with a fresh counter and the §5.5 name, appended at the end. */
    suspend fun duplicate(id: Long): Long

    /** Shifts every later row down by one. */
    suspend fun delete(id: Long)

    /** Target = (position + delta) clamped to the list bounds; a no-op when it equals the current position. */
    suspend fun moveBy(id: Long, delta: Int)

    suspend fun setTiming(id: Long, timing: TimingConfig)

    /** The same UPDATE resets holdCount. */
    suspend fun setProgression(id: Long, progression: ProgressionConfig)

    suspend fun setCues(id: Long, cues: CueConfig)

    /** One transaction using the row's own progression: concurrent calls on one entry record exactly one check-in. */
    suspend fun checkIn(id: Long, clock: Clock): CheckInResult

    /** The same UPDATE resets holdCount. */
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)

    /** total NULL, streaks 0, lastCheckIn NULL, holdCount 0. */
    suspend fun resetProgress(id: Long)
}

/** Completes once the v1 import has run (spec §6). */
interface MigrationGate {
    suspend fun awaitReady()
}

/**
 * Room-backed [EntryRepository]. Suspend calls return on the caller's dispatcher: ViewModels call
 * it from viewModelScope (Main) and keep calling TimerController on Main — never wrap it in
 * withContext(Dispatchers.IO) around controller calls (spec §5.3 threading).
 * [validationClock] is only used to reject a last check-in in the future.
 */
class RoomEntryRepository(
    private val db: HiitDatabase,
    private val gate: MigrationGate,
    private val validationClock: Clock,
) : EntryRepository {
    private val dao = db.entryDao()

    override val entries: Flow<List<Entry>> = flow {
        gate.awaitReady()
        emitAll(dao.observeAll())
    }.map { rows -> rows.map { it.toDomain() } }

    override fun entry(id: Long): Flow<Entry?> = flow {
        gate.awaitReady()
        emitAll(dao.observe(id))
    }.map { it?.toDomain() }

    override suspend fun create(name: String): Long {
        val valid = requireName(name)
        gate.awaitReady()
        return db.withTransaction { dao.insert(entryEntity(valid, position = dao.count())) }
    }

    override suspend fun rename(id: Long, name: String) {
        val valid = requireName(name)
        gate.awaitReady()
        found(id, dao.rename(id, valid))
    }

    override suspend fun duplicate(id: Long): Long {
        gate.awaitReady()
        return db.withTransaction {
            val source = dao.get(id)?.toDomain() ?: throw EntryNotFound(id)
            dao.insert(
                entryEntity(
                    name = EntryNames.duplicateName(source.name),
                    position = dao.count(),
                    timing = source.timing,
                    progression = source.progression,
                    cues = source.cues,
                ),
            )
        }
    }

    override suspend fun delete(id: Long) {
        gate.awaitReady()
        db.withTransaction {
            val row = dao.get(id) ?: throw EntryNotFound(id)
            dao.delete(id)
            dao.shiftPositions(low = row.position + 1, high = Int.MAX_VALUE, delta = -1)
        }
    }

    override suspend fun moveBy(id: Long, delta: Int) {
        gate.awaitReady()
        db.withTransaction {
            // Computed inside the transaction, so rapid repeated taps never act on a stale list.
            val row = dao.get(id) ?: throw EntryNotFound(id)
            val last = dao.count() - 1
            val target = (row.position.toLong() + delta).coerceIn(0L, last.toLong()).toInt()
            if (target != row.position) {
                if (target < row.position) {
                    dao.shiftPositions(low = target, high = row.position - 1, delta = 1)
                } else {
                    dao.shiftPositions(low = row.position + 1, high = target, delta = -1)
                }
                dao.setPosition(id, target)
            }
        }
    }

    override suspend fun setTiming(id: Long, timing: TimingConfig) {
        require(SettingsValidator.timing(timing).isValid) { "Invalid timing: $timing" }
        gate.awaitReady()
        found(id, with(timing) { dao.setTiming(id, prepareSec, sets, workSec, restSec, cooldownSec) })
    }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) {
        require(SettingsValidator.progression(progression).isValid) { "Invalid progression: $progression" }
        gate.awaitReady()
        found(
            id,
            with(progression) { dao.setProgression(id, startingTotal, floor, cap, holdAt, holdFor, windowHours, penaltyHoursPerRep) },
        )
    }

    override suspend fun setCues(id: Long, cues: CueConfig) {
        gate.awaitReady()
        found(id, dao.setCues(id, cues.sound, cues.vibration))
    }

    override suspend fun checkIn(id: Long, clock: Clock): CheckInResult {
        gate.awaitReady()
        return db.withTransaction {
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
        }
    }

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        // Only currentState's hints depend on the progression, so the defaults are enough to decide validity.
        val check = SettingsValidator.currentState(
            total, bestStreak, currentStreak, lastCheckIn, validationClock.now(), ProgressionConfig(),
        )
        require(check.isValid) { "Invalid counter: ${check.errors}" }
        gate.awaitReady()
        found(
            id,
            dao.setCounter(id, total, bestStreak, currentStreak, holdCount = 0, lastCheckIn = lastCheckIn?.toEpochMilli()),
        )
    }

    override suspend fun resetProgress(id: Long) {
        gate.awaitReady()
        found(id, dao.setCounter(id, total = null, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null))
    }

    private fun requireName(raw: String): String = when (val check = EntryNames.validate(raw)) {
        is NameCheck.Ok -> check.name
        else -> throw IllegalArgumentException(EntryNames.errorMessage(check))
    }

    private fun found(id: Long, updatedRows: Int) {
        if (updatedRows == 0) throw EntryNotFound(id)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T6-3-GREEN`, ~4 min): full suite + count. Expected: **190 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryRepository.kt`, then `git add -A && git commit -m "feat(data): per-entry check-in in one transaction using the row's progression"`

**Task 6 gate:** Run (label `T6-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **190 tests**, no lint errors.

---

## Task 7: One-time v1 migration (§5.4, §6)

**Interfaces produced:**
- `class AppPreferences(store: DataStore<Preferences>) { notificationPermissionAsked: Flow<Boolean>; suspend markNotificationPermissionAsked(); companion { FILE_NAME = "app"; NOTIFICATION_ASKED } }`
- `data/v1/V1Readers.kt`: `internal object V1Keys`, `internal const val V1_ENTRY_NAME = "Workout"`, `Preferences.readV1Timing()`, `readV1Progression()`, `readV1Cues()`, `readV1Total(): Int?`, `readV1Counter(): StoredCounter`, `v1Entry(settings, counter): EntryEntity`
- `class V1Migrator(dataStoreDir: File, db: HiitDatabase, appPreferences: AppPreferences, scope: CoroutineScope, io: CoroutineDispatcher, deleteFile: (File) -> Boolean = { it.delete() }) : MigrationGate { val ready: Deferred<Unit>; fun start(); companion { SETTINGS_FILE, COUNTER_FILE } }`. The migration-only DataStores have **no** corruption handler, because `ReplaceFileCorruptionHandler` would rewrite a v1 file before the import commits. This deliberately departs from the literal wording of spec §6 step 2 (review fix). A `CorruptionException` is caught and the file reads as defaults.

### Subtask 7.1: AppPreferences

**Files:** Create `data/AppPreferences.kt`; test `test/data/AppPreferencesTest.kt` (plain JVM).

- [ ] **Step 1: Failing tests** — `test/data/AppPreferencesTest.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppPreferencesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "app.preferences_pb") })

    @Test
    fun `the flag reads false when absent`() = runTest {
        assertFalse(AppPreferences(store()).notificationPermissionAsked.first())
    }

    @Test
    fun `marking the flag is sticky`() = runTest {
        val prefs = AppPreferences(store())
        prefs.markNotificationPermissionAsked()
        prefs.markNotificationPermissionAsked()
        assertTrue(prefs.notificationPermissionAsked.first())
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T7-1-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.AppPreferencesTest"`. Expected: compile FAIL, `Unresolved reference 'AppPreferences'`.

- [ ] **Step 3: Implement** — `data/AppPreferences.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * `app.preferences_pb` (spec §5.4): the only app-wide value. The flag is sticky — set once the
 * Android 13+ prompt has been shown, whatever the answer, and never reset.
 */
class AppPreferences(private val store: DataStore<Preferences>) {
    val notificationPermissionAsked: Flow<Boolean> = store.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "App preferences read failed", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[NOTIFICATION_ASKED] ?: false }

    suspend fun markNotificationPermissionAsked() {
        store.edit { it[NOTIFICATION_ASKED] = true }
    }

    companion object {
        /** `app.preferences_pb` via `preferencesDataStoreFile(FILE_NAME)`. */
        const val FILE_NAME = "app"
        val NOTIFICATION_ASKED = booleanPreferencesKey("notification_permission_asked")
        private const val TAG = "AppPreferences"
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T7-1-GREEN`, ~3 min): full suite + count. Expected: **192 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): app-level preferences for the notification flag"`

### Subtask 7.2: v1 readers

**Files:** Create `data/v1/V1Readers.kt`; test `test/data/v1/V1ReadersTest.kt` (plain JVM).

These are the v1 per-key readers (v1 `readTiming`/`readProgression`, v1 counter rules), copied into the migration package, with one change: `total` is read **raw**. The v1 files are deleted in 15.2, so the migration must not depend on them.

- [ ] **Step 1: Failing tests** — `test/data/v1/V1ReadersTest.kt`:
```kotlin
package com.mitenko.hiitcounter.data.v1

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.mitenko.hiitcounter.data.StoredCounter
import com.mitenko.hiitcounter.data.entryEntity
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V1ReadersTest {
    private fun prefs(block: (MutablePreferences) -> Unit): Preferences = mutablePreferencesOf().apply(block)

    @Test
    fun `empty v1 files give the default Workout entry with a null total`() {
        assertEquals(entryEntity("Workout", 0), v1Entry(emptyPreferences(), emptyPreferences()))
    }

    @Test
    fun `non-default v1 values carry across`() {
        val settings = prefs {
            it[V1Keys.PREPARE_SEC] = 5
            it[V1Keys.SETS] = 6
            it[V1Keys.WORK_SEC] = 30
            it[V1Keys.REST_SEC] = 15
            it[V1Keys.COOLDOWN_SEC] = 60
            it[V1Keys.STARTING_TOTAL] = 50
            it[V1Keys.FLOOR] = 40
            it[V1Keys.CAP] = 80
            it[V1Keys.HOLD_AT] = 70
            it[V1Keys.HOLD_FOR] = 3
            it[V1Keys.WINDOW_HOURS] = 30
            it[V1Keys.PENALTY_HOURS_PER_REP] = 12.5
            it[V1Keys.CUE_SOUND] = false
            it[V1Keys.CUE_VIBRATION] = true
        }
        val counter = prefs {
            it[V1Keys.TOTAL] = 65
            it[V1Keys.BEST_STREAK] = 24
            it[V1Keys.CURRENT_STREAK] = 4
            it[V1Keys.HOLD_COUNT] = 2
            it[V1Keys.LAST_CHECK_IN] = 1_790_000_000_123L
        }
        assertEquals(
            entryEntity(
                "Workout", 0,
                TimingConfig(5, 6, 30, 15, 60),
                ProgressionConfig(50, 40, 80, 70, 3, 30, 12.5),
                CueConfig(sound = false, vibration = true),
                StoredCounter(total = 65, bestStreak = 24, currentStreak = 4, holdCount = 2, lastCheckIn = 1_790_000_000_123L),
            ),
            v1Entry(settings, counter),
        )
    }

    @Test
    fun `absent and invalid v1 totals become null`() {
        assertNull(prefs { it[V1Keys.BEST_STREAK] = 3 }.readV1Total())
        assertNull(prefs { it[V1Keys.TOTAL] = 0 }.readV1Total())
        assertNull(prefs { it[V1Keys.TOTAL] = -5 }.readV1Total())
        assertEquals(1, prefs { it[V1Keys.TOTAL] = 1 }.readV1Total())
    }

    @Test
    fun `invalid v1 values fall back per key`() {
        val timing = prefs { it[V1Keys.SETS] = 99; it[V1Keys.WORK_SEC] = 30 }.readV1Timing()
        assertEquals(8, timing.sets)
        assertEquals(30, timing.workSec)
        val counter = prefs { it[V1Keys.CURRENT_STREAK] = -1; it[V1Keys.BEST_STREAK] = 7 }.readV1Counter()
        assertEquals(0, counter.currentStreak)
        assertEquals(7, counter.bestStreak)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T7-2-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.v1.V1ReadersTest"`. Expected: compile FAIL, `Unresolved reference 'V1Keys'` / `'v1Entry'`.

- [ ] **Step 3: Implement** — `data/v1/V1Readers.kt`:
```kotlin
package com.mitenko.hiitcounter.data.v1

import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.mitenko.hiitcounter.data.StoredCounter
import com.mitenko.hiitcounter.data.db.EntryEntity
import com.mitenko.hiitcounter.data.entryEntity
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig

private const val TAG = "V1Readers"

/** The name the imported v1 data gets (spec §6 step 3). */
internal const val V1_ENTRY_NAME = "Workout"

/** v1 DataStore keys (v1 spec §5). Read only by the one-time migration. */
internal object V1Keys {
    val PREPARE_SEC = intPreferencesKey("prepare_sec")
    val SETS = intPreferencesKey("sets")
    val WORK_SEC = intPreferencesKey("work_sec")
    val REST_SEC = intPreferencesKey("rest_sec")
    val COOLDOWN_SEC = intPreferencesKey("cooldown_sec")
    val STARTING_TOTAL = intPreferencesKey("starting_total")
    val FLOOR = intPreferencesKey("floor")
    val CAP = intPreferencesKey("cap")
    val HOLD_AT = intPreferencesKey("hold_at")
    val HOLD_FOR = intPreferencesKey("hold_for")
    val WINDOW_HOURS = intPreferencesKey("window_hours")
    val PENALTY_HOURS_PER_REP = doublePreferencesKey("penalty_hours_per_rep")
    val CUE_SOUND = booleanPreferencesKey("cue_sound")
    val CUE_VIBRATION = booleanPreferencesKey("cue_vibration")
    val NOTIFICATION_ASKED = booleanPreferencesKey("notification_permission_asked")

    val TOTAL = intPreferencesKey("total")
    val BEST_STREAK = intPreferencesKey("best_streak")
    val CURRENT_STREAK = intPreferencesKey("current_streak")
    val HOLD_COUNT = intPreferencesKey("hold_count")
    val LAST_CHECK_IN = longPreferencesKey("last_check_in")
}

/** v1 per-key fallback: an invalid stored value is replaced by its default and logged. */
private fun <T> Preferences.valid(key: Preferences.Key<T>, default: T, ok: (T) -> Boolean): T {
    val value = this[key] ?: return default
    if (ok(value)) return value
    Log.w(TAG, "Invalid v1 ${key.name}=$value; using default $default")
    return default
}

internal fun Preferences.readV1Timing(): TimingConfig {
    val d = TimingConfig()
    val max = SettingsValidator.MAX_PHASE_SEC
    val c = TimingConfig(
        prepareSec = valid(V1Keys.PREPARE_SEC, d.prepareSec) { it in 0..max },
        sets = valid(V1Keys.SETS, d.sets) { it in 1..SettingsValidator.MAX_SETS },
        workSec = valid(V1Keys.WORK_SEC, d.workSec) { it in 1..max },
        restSec = valid(V1Keys.REST_SEC, d.restSec) { it in 0..max },
        cooldownSec = valid(V1Keys.COOLDOWN_SEC, d.cooldownSec) { it in 0..max },
    )
    if (SettingsValidator.timing(c).isValid) return c
    Log.w(TAG, "v1 timing inconsistent ($c); using defaults")
    return d
}

internal fun Preferences.readV1Progression(): ProgressionConfig {
    val d = ProgressionConfig()
    val c = ProgressionConfig(
        startingTotal = valid(V1Keys.STARTING_TOTAL, d.startingTotal) { it >= 1 },
        floor = valid(V1Keys.FLOOR, d.floor) { it >= 1 },
        cap = valid(V1Keys.CAP, d.cap) { it >= 1 },
        holdAt = valid(V1Keys.HOLD_AT, d.holdAt) { it >= 1 },
        holdFor = valid(V1Keys.HOLD_FOR, d.holdFor) { it >= 0 },
        windowHours = valid(V1Keys.WINDOW_HOURS, d.windowHours) { it >= 1 },
        penaltyHoursPerRep = valid(V1Keys.PENALTY_HOURS_PER_REP, d.penaltyHoursPerRep) { it > 0.0 && it.isFinite() },
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "v1 progression inconsistent ($c); using defaults")
    return d
}

internal fun Preferences.readV1Cues(): CueConfig =
    CueConfig(sound = this[V1Keys.CUE_SOUND] ?: true, vibration = this[V1Keys.CUE_VIBRATION] ?: true)

/** `total` is read raw (spec §6 step 2): absent or invalid (< 1) becomes NULL. */
internal fun Preferences.readV1Total(): Int? {
    val raw = this[V1Keys.TOTAL] ?: return null
    if (raw >= 1) return raw
    Log.w(TAG, "Invalid v1 total=$raw; importing as NULL")
    return null
}

/** Streaks, hold count and last check-in follow the v1 reader rules. */
internal fun Preferences.readV1Counter(): StoredCounter = StoredCounter(
    total = readV1Total(),
    bestStreak = valid(V1Keys.BEST_STREAK, 0) { it >= 0 },
    currentStreak = valid(V1Keys.CURRENT_STREAK, 0) { it >= 0 },
    holdCount = valid(V1Keys.HOLD_COUNT, 0) { it >= 0 },
    lastCheckIn = this[V1Keys.LAST_CHECK_IN],
)

/** The "Workout" row built from the two v1 files; either may be empty (missing or corrupted). */
internal fun v1Entry(settings: Preferences, counter: Preferences): EntryEntity = entryEntity(
    name = V1_ENTRY_NAME,
    position = 0,
    timing = settings.readV1Timing(),
    progression = settings.readV1Progression(),
    cues = settings.readV1Cues(),
    counter = counter.readV1Counter(),
)
```

- [ ] **Step 4: Run green** — Run (label `T7-2-GREEN`, ~3 min): full suite + count. Expected: **196 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): v1 readers for the one-time migration, raw total"`

### Subtask 7.3: V1Migrator — gate, read, import, marker (steps 1–3)

**Files:** Create `data/v1/V1Migrator.kt`; test `test/data/v1/V1MigratorTest.kt`.

The migrator reads the v1 files through `PreferenceDataStoreFactory.create` instances on a **migration-only scope**, then cancels and joins that scope so both DataStores are closed. No Hilt DataStore exists for these files any more (the v1 providers go in 15.2), so there is no "multiple DataStores" conflict. The tests also cover the race between a `create` and the migration.

- [ ] **Step 1: Failing tests** — `test/data/v1/V1MigratorTest.kt` (the import list already covers 7.4–7.5):
```kotlin
package com.mitenko.hiitcounter.data.v1

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.data.RoomEntryRepository
import com.mitenko.hiitcounter.data.StoredCounter
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.data.db.MetaEntity
import com.mitenko.hiitcounter.data.entryEntity
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.testEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class V1MigratorTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: HiitDatabase
    private lateinit var dir: File

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dir = tmp.newFolder("datastore")
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Call once per test: two DataStores on app.preferences_pb in one process would conflict. */
    private fun TestScope.appPreferences() = AppPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "app.preferences_pb") }),
    )

    private fun TestScope.migrator(prefs: AppPreferences, deleteFile: (File) -> Boolean = { it.delete() }) =
        V1Migrator(dir, db, prefs, backgroundScope, Dispatchers.IO, deleteFile)

    /** Writes a v1 file the way v1 did, then closes its DataStore so the file is released. */
    private suspend fun seed(fileName: String, block: (MutablePreferences) -> Unit) {
        val job = Job()
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { File(dir, fileName) })
            .edit { block(it) }
        job.cancelAndJoin()
    }

    private suspend fun marker() = db.metaDao().get(HiitDatabase.KEY_V1_MIGRATED)

    private suspend fun rows() = db.entryDao().getAll()

    @Test
    fun `a fresh install writes only the marker`() = runTest {
        migrator(appPreferences()).ready.await()
        assertEquals("true", marker())
        assertTrue(rows().isEmpty())
        assertFalse(File(dir, "app.preferences_pb").exists())
    }

    @Test
    fun `both v1 files become one Workout entry with identical values`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) {
            it[V1Keys.SETS] = 6
            it[V1Keys.WORK_SEC] = 30
            it[V1Keys.CAP] = 80
            it[V1Keys.PENALTY_HOURS_PER_REP] = 12.5
            it[V1Keys.CUE_SOUND] = false
        }
        seed(V1Migrator.COUNTER_FILE) {
            it[V1Keys.TOTAL] = 65
            it[V1Keys.BEST_STREAK] = 24
            it[V1Keys.CURRENT_STREAK] = 4
            it[V1Keys.HOLD_COUNT] = 2
            it[V1Keys.LAST_CHECK_IN] = 1_790_000_000_123L
        }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertEquals(
            entryEntity(
                "Workout", 0,
                TimingConfig(sets = 6, workSec = 30),
                ProgressionConfig(cap = 80, penaltyHoursPerRep = 12.5),
                CueConfig(sound = false),
                StoredCounter(total = 65, bestStreak = 24, currentStreak = 4, holdCount = 2, lastCheckIn = 1_790_000_000_123L),
            ).copy(id = row.id),
            row,
        )
        assertEquals("true", marker())
    }

    @Test
    fun `only the settings file imports settings with a fresh counter`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 5 }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertEquals(5, row.sets)
        assertNull(row.total)
        assertEquals(0, row.bestStreak)
    }

    @Test
    fun `only the counter file imports the counter with default settings`() = runTest {
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 70 }
        migrator(appPreferences()).ready.await()
        val row = rows().single()
        assertEquals(entryEntity("Workout", 0, counter = StoredCounter(total = 70)).copy(id = row.id), row)
    }

    @Test
    fun `re-running is a no-op`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        val prefs = appPreferences()
        migrator(prefs).ready.await()
        migrator(prefs).ready.await()
        assertEquals(1, rows().size)
    }

    @Test
    fun `the v1 files are read without any Hilt DataStore and released afterwards`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        migrator(appPreferences()).ready.await()
        assertEquals(6, rows().single().sets)
        // Throws "There are multiple DataStores active for the same file" if the migration scope leaked.
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, V1Migrator.SETTINGS_FILE) })
            .data.first()
    }

    @Test
    fun `a create racing the migration still yields Workout first and never an empty list`() = runTest {
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        val m = migrator(appPreferences())
        val repo = RoomEntryRepository(db, m, FakeClock())
        val seen = mutableListOf<List<String>>()
        val collector = backgroundScope.launch { repo.entries.collect { list -> seen += list.map { it.name } } }
        repo.create("Burpees")
        assertEquals(listOf("Workout", "Burpees"), repo.entries.first { it.size == 2 }.map { it.name })
        assertTrue("saw $seen", seen.none { it.isEmpty() })
        collector.cancel()
    }

    @Test
    fun `rows that exist without the marker shift up behind Workout`() = runTest {
        db.entryDao().insert(testEntity(name = "Restored", position = 0))
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        migrator(appPreferences()).ready.await()
        assertEquals(listOf("Workout" to 0, "Restored" to 1), rows().map { it.name to it.position })
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T7-3-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.v1.V1MigratorTest"`. Expected: compile FAIL, `Unresolved reference 'V1Migrator'`.

- [ ] **Step 3: Implement** — `data/v1/V1Migrator.kt`:
```kotlin
package com.mitenko.hiitcounter.data.v1

import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.data.MigrationGate
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.data.db.MetaEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * One-time import of the v1 DataStore files into Room (spec §6). [ready] completes once the gate,
 * the import (if any), the flag copy and the cleanup have run; every EntryRepository entry point
 * awaits it. HiitApp.onCreate starts it on [io] — never with runBlocking; awaiting also starts it.
 * A failure is logged and still completes [ready]: nothing is deleted and no marker is written,
 * so the next launch retries.
 */
class V1Migrator(
    private val dataStoreDir: File,
    private val db: HiitDatabase,
    private val appPreferences: AppPreferences,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Cleanup's file deletion; tests replace it to inspect files after the import. */
    private val deleteFile: (File) -> Boolean = { it.delete() },
) : MigrationGate {
    val ready: Deferred<Unit> = scope.async(io, start = CoroutineStart.LAZY) { migrate() }

    fun start() {
        ready.start()
    }

    override suspend fun awaitReady() = ready.await()

    private suspend fun migrate() {
        try {
            val settingsFile = File(dataStoreDir, SETTINGS_FILE)
            val counterFile = File(dataStoreDir, COUNTER_FILE)
            val hasSettings = settingsFile.exists()
            val hasCounter = counterFile.exists()
            // Step 1: the gate. Completion is recorded in meta, never inferred from an empty table.
            val migrated = db.metaDao().get(HiitDatabase.KEY_V1_MIGRATED) == MARKER_VALUE
            val needsImport = !migrated && (hasSettings || hasCounter)
            // Step 2: read the v1 files through migration-only DataStores.
            val (settings, counter) = readV1Files(
                settingsFile.takeIf { needsImport && hasSettings },
                counterFile.takeIf { needsImport && hasCounter },
            )
            // Step 3: the entry (shifting any existing rows up) and the marker commit together.
            if (!migrated) {
                db.withTransaction {
                    if (needsImport) {
                        db.entryDao().shiftPositions(low = 0, high = Int.MAX_VALUE, delta = 1)
                        db.entryDao().insert(v1Entry(settings, counter))
                    }
                    db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, MARKER_VALUE))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "v1 migration failed; it is retried on the next launch", e)
        }
    }

    private suspend fun readV1Files(settingsFile: File?, counterFile: File?): Pair<Preferences, Preferences> {
        if (settingsFile == null && counterFile == null) return emptyPreferences() to emptyPreferences()
        val job = SupervisorJob()
        val scope = CoroutineScope(io + job)
        try {
            val settings = settingsFile?.let { read(scope, it) } ?: emptyPreferences()
            val counter = counterFile?.let { read(scope, it) } ?: emptyPreferences()
            return settings to counter
        } finally {
            // Cancel and join the migration scope so both DataStores close and release their files.
            job.cancelAndJoin()
        }
    }

    private suspend fun read(scope: CoroutineScope, file: File): Preferences =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).data.first()

    companion object {
        const val SETTINGS_FILE = "settings.preferences_pb"
        const val COUNTER_FILE = "counter.preferences_pb"
        private const val MARKER_VALUE = "true"
        private const val TAG = "V1Migrator"
    }
}
```
(`appPreferences` and `deleteFile` are unused until 7.5. The warnings are expected; the constructor is final from here on.)

- [ ] **Step 4: Run green** — Run (label `T7-3-GREEN`, ~4 min): full suite + count. Expected: **204 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): v1 migrator with readiness gate, marker and one-transaction import"`

### Subtask 7.4: Corrupted v1 files read as defaults, never rewritten

**Files:** Replace `data/v1/V1Migrator.kt`; append to `test/data/v1/V1MigratorTest.kt`.

- [ ] **Step 1: Failing tests** — append to `V1MigratorTest` (`"not a protobuf"` starts with byte 0x6E, which is wire type 6. That is invalid, so the file always fails to parse):
```kotlin
    // deleteFile = { false } keeps the files past cleanup (added in 7.5), so the bytes can be checked after the import.
    @Test
    fun `a corrupted v1 file falls back to defaults and is never rewritten`() = runTest {
        val corrupt = File(dir, V1Migrator.SETTINGS_FILE).apply { writeText("not a protobuf") }
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        migrator(appPreferences(), deleteFile = { false }).ready.await()
        val row = rows().single()
        assertEquals(entryEntity("Workout", 0, counter = StoredCounter(total = 65)).copy(id = row.id), row)
        assertEquals("not a protobuf", corrupt.readText())
    }

    @Test
    fun `a corrupted counter file yields a fresh counter and is never rewritten`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        val corrupt = File(dir, V1Migrator.COUNTER_FILE).apply { writeText("not a protobuf") }
        migrator(appPreferences(), deleteFile = { false }).ready.await()
        val row = rows().single()
        assertEquals(entryEntity("Workout", 0, TimingConfig(sets = 6)).copy(id = row.id), row)
        assertEquals("not a protobuf", corrupt.readText())
    }
```

- [ ] **Step 2: Run red** — Run (label `T7-4-RED`, ~2 min): focused `V1MigratorTest`. Expected: the 2 new tests FAIL with `NoSuchElementException: List is empty`. The uncaught `CorruptionException` aborts the import, which is logged and retried on the next launch.

- [ ] **Step 3: Implement** — `data/v1/V1Migrator.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.data.v1

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.data.MigrationGate
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.data.db.MetaEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * One-time import of the v1 DataStore files into Room (spec §6). [ready] completes once the gate,
 * the import (if any), the flag copy and the cleanup have run; every EntryRepository entry point
 * awaits it. HiitApp.onCreate starts it on [io] — never with runBlocking; awaiting also starts it.
 * A failure is logged and still completes [ready]: nothing is deleted and no marker is written,
 * so the next launch retries.
 */
class V1Migrator(
    private val dataStoreDir: File,
    private val db: HiitDatabase,
    private val appPreferences: AppPreferences,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Cleanup's file deletion; tests replace it to inspect files after the import. */
    private val deleteFile: (File) -> Boolean = { it.delete() },
) : MigrationGate {
    val ready: Deferred<Unit> = scope.async(io, start = CoroutineStart.LAZY) { migrate() }

    fun start() {
        ready.start()
    }

    override suspend fun awaitReady() = ready.await()

    private suspend fun migrate() {
        try {
            val settingsFile = File(dataStoreDir, SETTINGS_FILE)
            val counterFile = File(dataStoreDir, COUNTER_FILE)
            val hasSettings = settingsFile.exists()
            val hasCounter = counterFile.exists()
            // Step 1: the gate. Completion is recorded in meta, never inferred from an empty table.
            val migrated = db.metaDao().get(HiitDatabase.KEY_V1_MIGRATED) == MARKER_VALUE
            val needsImport = !migrated && (hasSettings || hasCounter)
            // Step 2: read the v1 files through migration-only DataStores.
            val (settings, counter) = readV1Files(
                settingsFile.takeIf { needsImport && hasSettings },
                counterFile.takeIf { needsImport && hasCounter },
            )
            // Step 3: the entry (shifting any existing rows up) and the marker commit together.
            if (!migrated) {
                db.withTransaction {
                    if (needsImport) {
                        db.entryDao().shiftPositions(low = 0, high = Int.MAX_VALUE, delta = 1)
                        db.entryDao().insert(v1Entry(settings, counter))
                    }
                    db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, MARKER_VALUE))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "v1 migration failed; it is retried on the next launch", e)
        }
    }

    private suspend fun readV1Files(settingsFile: File?, counterFile: File?): Pair<Preferences, Preferences> {
        if (settingsFile == null && counterFile == null) return emptyPreferences() to emptyPreferences()
        val job = SupervisorJob()
        val scope = CoroutineScope(io + job)
        try {
            val settings = settingsFile?.let { read(scope, it) } ?: emptyPreferences()
            val counter = counterFile?.let { read(scope, it) } ?: emptyPreferences()
            return settings to counter
        } finally {
            // Cancel and join the migration scope so both DataStores close and release their files.
            job.cancelAndJoin()
        }
    }

    /**
     * No corruption handler: ReplaceFileCorruptionHandler would rewrite the v1 file before the import
     * commits. A corrupted file reads as empty (the v1 defaults / a fresh counter) and its bytes stay
     * untouched until cleanup. Any other IOException propagates, aborting the migration so it retries.
     */
    private suspend fun read(scope: CoroutineScope, file: File): Preferences = try {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).data.first()
    } catch (e: CorruptionException) {
        Log.w(TAG, "${file.name} is corrupt; using defaults", e)
        emptyPreferences()
    }

    companion object {
        const val SETTINGS_FILE = "settings.preferences_pb"
        const val COUNTER_FILE = "counter.preferences_pb"
        private const val MARKER_VALUE = "true"
        private const val TAG = "V1Migrator"
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T7-4-GREEN`, ~4 min): full suite + count. Expected: **206 tests**.

- [ ] **Step 5: Commit** — diff-review `V1Migrator.kt`, then `git add -A && git commit -m "feat(data): corrupted v1 files fall back to defaults during migration"`

### Subtask 7.5: Flag copy, cleanup and the crash windows (steps 4–5)

**Files:** Replace `data/v1/V1Migrator.kt`; append to `test/data/v1/V1MigratorTest.kt`.

The crash windows (§6):
- A crash before the step-3 commit leaves no marker, so the next launch redoes steps 2–5.
- A crash after the commit leaves the marker, so the next launch skips the import and only completes steps 4–5.
- Nothing is deleted before the commit.

The settings file is therefore read whenever it exists, even when the marker is already present, so step 4 can copy the flag.

- [ ] **Step 1: Failing tests** — append to `V1MigratorTest`:
```kotlin
    @Test
    fun `the notification flag is copied when the v1 settings file exists`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.NOTIFICATION_ASKED] = true }
        val prefs = appPreferences()
        migrator(prefs).ready.await()
        assertTrue(prefs.notificationPermissionAsked.first())
    }

    @Test
    fun `the notification flag is not created without a v1 settings file`() = runTest {
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        migrator(appPreferences()).ready.await()
        assertFalse(File(dir, "app.preferences_pb").exists())
    }

    @Test
    fun `v1 files and their tmp siblings are deleted and the marker is written`() = runTest {
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.SETS] = 6 }
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        File(dir, "${V1Migrator.SETTINGS_FILE}.tmp").writeText("partial")
        File(dir, "${V1Migrator.COUNTER_FILE}.tmp").writeText("partial")
        migrator(appPreferences()).ready.await()
        assertEquals("true", marker())
        listOf(
            V1Migrator.SETTINGS_FILE, V1Migrator.COUNTER_FILE,
            "${V1Migrator.SETTINGS_FILE}.tmp", "${V1Migrator.COUNTER_FILE}.tmp",
        ).forEach { assertFalse("$it should be deleted", File(dir, it).exists()) }
    }

    @Test
    fun `a crash after the commit completes the cleanup without a duplicate entry`() = runTest {
        // The import and the marker committed, then the process died before step 4.
        db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, "true"))
        db.entryDao().insert(testEntity(name = "Workout"))
        seed(V1Migrator.SETTINGS_FILE) { it[V1Keys.NOTIFICATION_ASKED] = true }
        seed(V1Migrator.COUNTER_FILE) { it[V1Keys.TOTAL] = 65 }
        val prefs = appPreferences()
        migrator(prefs).ready.await()
        assertEquals(1, rows().size)
        assertTrue(prefs.notificationPermissionAsked.first())
        assertFalse(File(dir, V1Migrator.SETTINGS_FILE).exists())
        assertFalse(File(dir, V1Migrator.COUNTER_FILE).exists())
    }
```

- [ ] **Step 2: Run red** — Run (label `T7-5-RED`, ~2 min): focused `V1MigratorTest`. Expected: FAIL on 3 new tests (flag `false`; files still exist). `the notification flag is not created without a v1 settings file` already passes and is a regression guard.

- [ ] **Step 3: Implement** — `data/v1/V1Migrator.kt` (complete, final):
```kotlin
package com.mitenko.hiitcounter.data.v1

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.withTransaction
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.data.MigrationGate
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.data.db.MetaEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * One-time import of the v1 DataStore files into Room (spec §6). [ready] completes once the gate,
 * the import (if any), the flag copy and the cleanup have run; every EntryRepository entry point
 * awaits it. HiitApp.onCreate starts it on [io] — never with runBlocking; awaiting also starts it.
 * A failure is logged and still completes [ready]: nothing is deleted before the import commits
 * and no marker is written without it, so the next launch retries.
 */
class V1Migrator(
    private val dataStoreDir: File,
    private val db: HiitDatabase,
    private val appPreferences: AppPreferences,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Cleanup's file deletion; tests replace it to inspect files after the import. */
    private val deleteFile: (File) -> Boolean = { it.delete() },
) : MigrationGate {
    val ready: Deferred<Unit> = scope.async(io, start = CoroutineStart.LAZY) { migrate() }

    fun start() {
        ready.start()
    }

    override suspend fun awaitReady() = ready.await()

    private suspend fun migrate() {
        try {
            val settingsFile = File(dataStoreDir, SETTINGS_FILE)
            val counterFile = File(dataStoreDir, COUNTER_FILE)
            val hasSettings = settingsFile.exists()
            val hasCounter = counterFile.exists()
            // Step 1: the gate. Completion is recorded in meta, never inferred from an empty table.
            val migrated = db.metaDao().get(HiitDatabase.KEY_V1_MIGRATED) == MARKER_VALUE
            val needsImport = !migrated && (hasSettings || hasCounter)
            // Step 2: read through migration-only DataStores. Settings are read whenever the file
            // exists, because step 4 still needs the flag after a crash that followed the commit.
            val (settings, counter) = readV1Files(
                settingsFile.takeIf { hasSettings },
                counterFile.takeIf { needsImport && hasCounter },
            )
            // Step 3: the entry (shifting any existing rows up) and the marker commit together.
            if (!migrated) {
                db.withTransaction {
                    if (needsImport) {
                        db.entryDao().shiftPositions(low = 0, high = Int.MAX_VALUE, delta = 1)
                        db.entryDao().insert(v1Entry(settings, counter))
                    }
                    db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, MARKER_VALUE))
                }
            }
            // Step 4: copy the sticky flag (idempotent; never resets it, never creates it without v1 settings).
            if (hasSettings && settings[V1Keys.NOTIFICATION_ASKED] == true) appPreferences.markNotificationPermissionAsked()
            // Step 5: only now, after the commit, delete the v1 data.
            cleanUp()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "v1 migration failed; it is retried on the next launch", e)
        }
    }

    private suspend fun readV1Files(settingsFile: File?, counterFile: File?): Pair<Preferences, Preferences> {
        if (settingsFile == null && counterFile == null) return emptyPreferences() to emptyPreferences()
        val job = SupervisorJob()
        val scope = CoroutineScope(io + job)
        try {
            val settings = settingsFile?.let { read(scope, it) } ?: emptyPreferences()
            val counter = counterFile?.let { read(scope, it) } ?: emptyPreferences()
            return settings to counter
        } finally {
            // Cancel and join the migration scope so both DataStores close and release their files.
            job.cancelAndJoin()
        }
    }

    /**
     * No corruption handler: ReplaceFileCorruptionHandler would rewrite the v1 file before the import
     * commits. A corrupted file reads as empty (the v1 defaults / a fresh counter) and its bytes stay
     * untouched until cleanup. Any other IOException propagates, aborting the migration so it retries.
     */
    private suspend fun read(scope: CoroutineScope, file: File): Preferences = try {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).data.first()
    } catch (e: CorruptionException) {
        Log.w(TAG, "${file.name} is corrupt; using defaults", e)
        emptyPreferences()
    }

    /** Deletes the two v1 files and their DataStore `.tmp` siblings; app.preferences_pb is never touched. */
    private fun cleanUp() {
        for (name in listOf(SETTINGS_FILE, COUNTER_FILE)) {
            for (file in listOf(File(dataStoreDir, name), File(dataStoreDir, "$name.tmp"))) {
                if (file.exists() && !deleteFile(file)) Log.w(TAG, "Couldn't delete ${file.name}")
            }
        }
    }

    companion object {
        const val SETTINGS_FILE = "settings.preferences_pb"
        const val COUNTER_FILE = "counter.preferences_pb"
        private const val MARKER_VALUE = "true"
        private const val TAG = "V1Migrator"
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T7-5-GREEN`, ~4 min): full suite + count. Expected: **210 tests**.

- [ ] **Step 5: Commit** — diff-review `V1Migrator.kt`, then `git add -A && git commit -m "feat(data): copy the notification flag and clean up v1 files after the commit"`

**Task 7 gate:** Run (label `T7-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **210 tests**, no lint errors.

---

## Task 8: Run ownership and the entry name in the run (§7.1, §7.2, §7.6)

**Interfaces produced:**
- `data class WorkoutSnapshot(entryId: Long, entryName: String, timing: TimingConfig, cues: CueConfig)`
- `TimerController.lastEntryId: Long?`, which is set by `prepare()` and not cleared by `clearRun()`
- `TimerController.isBusy(entryId: Long): Boolean` = `status != IDLE && snapshot?.entryId == entryId`
- `TimerText.notificationTitle(entryName: String?, s: TimerState)` and `TimerText.startingTitle(entryName: String?)`
- `WorkoutNotifications.build(state: TimerState?, entryName: String?)` and `update(state, entryName)`
- `TimerUiState.entryName: String` and `TimerUiMapper.map(s: TimerState, entryName: String)`

### Subtask 8.1: Snapshot entry id and name; lastEntryId

**Files:** Replace `domain/TimerController.kt`; edit `ui/home/HomeViewModel.kt` (temporary, deleted in 15.2), `test/domain/TimerControllerTest.kt`, `test/ui/timer/TimerViewModelTest.kt`.

- [ ] **Step 1: Failing test and snapshot updates**

In `test/domain/TimerControllerTest.kt`, replace:
```kotlin
    private val snapshot = WorkoutSnapshot(TimingConfig(), CueConfig())
```
with:
```kotlin
    private val snapshot = WorkoutSnapshot(entryId = 1L, entryName = "Burpees", timing = TimingConfig(), cues = CueConfig())
```
and append:
```kotlin
    @Test
    fun `lastEntryId is set by prepare and survives the end of the run`() = runTest {
        val c = controller()
        assertNull(c.lastEntryId)
        c.prepare(snapshot)
        assertEquals(1L, c.lastEntryId)
        c.cancelPrepare()
        assertEquals(1L, c.lastEntryId)
        c.prepare(snapshot.copy(entryId = 2L))
        c.onServiceStarted()
        c.start(reps)
        runCurrent()
        c.stop()
        assertNull(c.snapshot)
        assertEquals(2L, c.lastEntryId)
    }
```

In `test/ui/timer/TimerViewModelTest.kt`, replace:
```kotlin
        controller.prepare(WorkoutSnapshot(TimingConfig(prepareSec = 0, sets = 1, workSec = 2, restSec = 0), CueConfig()))
```
with:
```kotlin
        controller.prepare(WorkoutSnapshot(1L, "Burpees", TimingConfig(prepareSec = 0, sets = 1, workSec = 2, restSec = 0), CueConfig()))
```
and replace:
```kotlin
        controller.prepare(WorkoutSnapshot(TimingConfig(), CueConfig()))
```
with:
```kotlin
        controller.prepare(WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig()))
```

- [ ] **Step 2: Run red** — Run (label `T8-1-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.TimerControllerTest"`. Expected: compile FAIL, `No parameter with name 'entryId'` / `Unresolved reference 'lastEntryId'`.

- [ ] **Step 3: Implement** — `domain/TimerController.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Frozen at Start (spec §7.1): the service, timer screen and notification read only this, so
 * renaming, editing or deleting the source entry never changes an active run.
 */
data class WorkoutSnapshot(
    val entryId: Long,
    val entryName: String,
    val timing: TimingConfig,
    val cues: CueConfig,
)

enum class RunStatus { IDLE, PREPARING, RUNNING, DONE }

sealed interface ServiceStatus {
    data object Pending : ServiceStatus
    data object Started : ServiceStatus
    data class Failed(val reason: String) : ServiceStatus
}

/**
 * Owns the single running workout (v1 spec §4, §8). Commands are idempotent. Must be used
 * from the thread [scope] dispatches on (Main in production).
 */
class TimerController(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {
    private val _status = MutableStateFlow(RunStatus.IDLE)
    val status: StateFlow<RunStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow<TimerState?>(null)
    val state: StateFlow<TimerState?> = _state.asStateFlow()

    private val _cues = MutableSharedFlow<Cue>(replay = 0, extraBufferCapacity = 64)
    val cues: SharedFlow<Cue> = _cues.asSharedFlow()

    private val _serviceStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Pending)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    var snapshot: WorkoutSnapshot? = null
        private set

    /** The entry of the most recent prepare(); kept after the run ends so leaving the timer returns to it (spec §7.2). */
    var lastEntryId: Long? = null
        private set

    private var engine: TabataEngine? = null
    private var runJob: Job? = null
    private var pauseTimeoutJob: Job? = null

    fun prepare(snapshot: WorkoutSnapshot): Boolean {
        if (_status.value == RunStatus.PREPARING || _status.value == RunStatus.RUNNING) return false
        clearRun() // clears the previous snapshot; assign the new one after
        this.snapshot = snapshot
        lastEntryId = snapshot.entryId
        _serviceStatus.value = ServiceStatus.Pending
        _status.value = RunStatus.PREPARING
        return true
    }

    fun onServiceStarted() {
        if (_status.value == RunStatus.PREPARING) _serviceStatus.value = ServiceStatus.Started
    }

    fun onServiceFailed(reason: String) {
        if (_status.value == RunStatus.PREPARING) _serviceStatus.value = ServiceStatus.Failed(reason)
    }

    fun cancelPrepare() {
        if (_status.value != RunStatus.PREPARING) return
        clearRun()
        _status.value = RunStatus.IDLE
    }

    fun start(repsPerSet: List<Int>): Boolean {
        val snap = snapshot ?: return false
        // The timer only runs under a foreground service (v1 spec §4).
        if (_status.value != RunStatus.PREPARING || _serviceStatus.value != ServiceStatus.Started) return false
        val e = TabataEngine(
            timing = snap.timing,
            repsPerSet = repsPerSet,
            nowMs = nowMs,
            onState = { _state.value = it },
            onCue = { _cues.tryEmit(it) },
        )
        engine = e
        _status.value = RunStatus.RUNNING
        runJob = scope.launch {
            e.run()
            pauseTimeoutJob?.cancel()
            _status.value = RunStatus.DONE
        }
        return true
    }

    fun pause() {
        val e = engine ?: return
        if (_status.value != RunStatus.RUNNING || e.isPaused) return
        e.pause()
        pauseTimeoutJob = scope.launch {
            delay(MAX_PAUSE_MS)
            stop()
        }
    }

    fun resume() {
        val e = engine ?: return
        if (_status.value != RunStatus.RUNNING || !e.isPaused) return
        pauseTimeoutJob?.cancel()
        e.resume()
    }

    /** Ends the workout. The check-in made at Start stands; no DONE, no Finished cue. */
    fun stop() {
        when (_status.value) {
            RunStatus.RUNNING -> {
                clearRun()
                _status.value = RunStatus.IDLE
            }
            RunStatus.PREPARING -> cancelPrepare()
            RunStatus.IDLE, RunStatus.DONE -> Unit
        }
    }

    fun dismissDone() {
        if (_status.value != RunStatus.DONE) return
        clearRun()
        _status.value = RunStatus.IDLE
    }

    /** Clears the run but deliberately not [lastEntryId]. */
    private fun clearRun() {
        runJob?.cancel()
        runJob = null
        pauseTimeoutJob?.cancel()
        pauseTimeoutJob = null
        engine = null
        snapshot = null
        _state.value = null
    }

    companion object {
        const val MAX_PAUSE_MS = 30 * 60 * 1000L
    }
}
```

In `ui/home/HomeViewModel.kt` (deleted in 15.2), replace:
```kotlin
        if (!controller.prepare(WorkoutSnapshot(timing, cues))) {
```
with:
```kotlin
        // Temporary until 15.2 deletes HomeViewModel: v1 has no entry id or name.
        if (!controller.prepare(WorkoutSnapshot(entryId = 0L, entryName = "Workout", timing = timing, cues = cues))) {
```

- [ ] **Step 4: Run green** — Run (label `T8-1-GREEN`, ~3 min): full suite + count. Expected: **211 tests**.

- [ ] **Step 5: Commit** — diff-review `TimerController.kt` (the v1 `start()` service gate and `clearRun()` must be intact), then `git add -A && git commit -m "feat(domain): freeze entry id and name in the snapshot; keep lastEntryId"`

### Subtask 8.2: isBusy

**Files:** Replace `domain/TimerController.kt`; append to `test/domain/TimerControllerTest.kt`.

- [ ] **Step 1: Failing tests** — append to `TimerControllerTest`:
```kotlin
    @Test
    fun `isBusy follows the run's own entry through its lifecycle`() = runTest {
        val c = controller()
        assertFalse(c.isBusy(1L))
        c.prepare(snapshot)
        assertTrue(c.isBusy(1L))
        c.onServiceStarted()
        c.start(reps)
        runCurrent()
        assertTrue(c.isBusy(1L))
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
        assertTrue(c.isBusy(1L))
        c.dismissDone()
        assertFalse(c.isBusy(1L))
    }

    @Test
    fun `other entries and a stopped run are never busy`() = runTest {
        val c = controller()
        c.prepare(snapshot)
        assertFalse(c.isBusy(2L))
        c.onServiceStarted()
        c.start(reps)
        c.stop()
        assertFalse(c.isBusy(1L))
    }
```

- [ ] **Step 2: Run red** — Run (label `T8-2-RED`, ~1 min): focused `TimerControllerTest`. Expected: compile FAIL, `Unresolved reference 'isBusy'`.

- [ ] **Step 3: Implement** — `domain/TimerController.kt` (complete: the 8.1 file with `isBusy` added after `lastEntryId`):
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Frozen at Start (spec §7.1): the service, timer screen and notification read only this, so
 * renaming, editing or deleting the source entry never changes an active run.
 */
data class WorkoutSnapshot(
    val entryId: Long,
    val entryName: String,
    val timing: TimingConfig,
    val cues: CueConfig,
)

enum class RunStatus { IDLE, PREPARING, RUNNING, DONE }

sealed interface ServiceStatus {
    data object Pending : ServiceStatus
    data object Started : ServiceStatus
    data class Failed(val reason: String) : ServiceStatus
}

/**
 * Owns the single running workout (v1 spec §4, §8). Commands are idempotent. Must be used
 * from the thread [scope] dispatches on (Main in production).
 */
class TimerController(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
) {
    private val _status = MutableStateFlow(RunStatus.IDLE)
    val status: StateFlow<RunStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow<TimerState?>(null)
    val state: StateFlow<TimerState?> = _state.asStateFlow()

    private val _cues = MutableSharedFlow<Cue>(replay = 0, extraBufferCapacity = 64)
    val cues: SharedFlow<Cue> = _cues.asSharedFlow()

    private val _serviceStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Pending)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    var snapshot: WorkoutSnapshot? = null
        private set

    /** The entry of the most recent prepare(); kept after the run ends so leaving the timer returns to it (spec §7.2). */
    var lastEntryId: Long? = null
        private set

    private var engine: TabataEngine? = null
    private var runJob: Job? = null
    private var pauseTimeoutJob: Job? = null

    /**
     * Spec §7.1 busy rule, read at the moment of each destructive action. After process
     * recreation the controller starts IDLE, so nothing is busy.
     */
    fun isBusy(entryId: Long): Boolean = _status.value != RunStatus.IDLE && snapshot?.entryId == entryId

    fun prepare(snapshot: WorkoutSnapshot): Boolean {
        if (_status.value == RunStatus.PREPARING || _status.value == RunStatus.RUNNING) return false
        clearRun() // clears the previous snapshot; assign the new one after
        this.snapshot = snapshot
        lastEntryId = snapshot.entryId
        _serviceStatus.value = ServiceStatus.Pending
        _status.value = RunStatus.PREPARING
        return true
    }

    fun onServiceStarted() {
        if (_status.value == RunStatus.PREPARING) _serviceStatus.value = ServiceStatus.Started
    }

    fun onServiceFailed(reason: String) {
        if (_status.value == RunStatus.PREPARING) _serviceStatus.value = ServiceStatus.Failed(reason)
    }

    fun cancelPrepare() {
        if (_status.value != RunStatus.PREPARING) return
        clearRun()
        _status.value = RunStatus.IDLE
    }

    fun start(repsPerSet: List<Int>): Boolean {
        val snap = snapshot ?: return false
        // The timer only runs under a foreground service (v1 spec §4).
        if (_status.value != RunStatus.PREPARING || _serviceStatus.value != ServiceStatus.Started) return false
        val e = TabataEngine(
            timing = snap.timing,
            repsPerSet = repsPerSet,
            nowMs = nowMs,
            onState = { _state.value = it },
            onCue = { _cues.tryEmit(it) },
        )
        engine = e
        _status.value = RunStatus.RUNNING
        runJob = scope.launch {
            e.run()
            pauseTimeoutJob?.cancel()
            _status.value = RunStatus.DONE
        }
        return true
    }

    fun pause() {
        val e = engine ?: return
        if (_status.value != RunStatus.RUNNING || e.isPaused) return
        e.pause()
        pauseTimeoutJob = scope.launch {
            delay(MAX_PAUSE_MS)
            stop()
        }
    }

    fun resume() {
        val e = engine ?: return
        if (_status.value != RunStatus.RUNNING || !e.isPaused) return
        pauseTimeoutJob?.cancel()
        e.resume()
    }

    /** Ends the workout. The check-in made at Start stands; no DONE, no Finished cue. */
    fun stop() {
        when (_status.value) {
            RunStatus.RUNNING -> {
                clearRun()
                _status.value = RunStatus.IDLE
            }
            RunStatus.PREPARING -> cancelPrepare()
            RunStatus.IDLE, RunStatus.DONE -> Unit
        }
    }

    fun dismissDone() {
        if (_status.value != RunStatus.DONE) return
        clearRun()
        _status.value = RunStatus.IDLE
    }

    /** Clears the run but deliberately not [lastEntryId]. */
    private fun clearRun() {
        runJob?.cancel()
        runJob = null
        pauseTimeoutJob?.cancel()
        pauseTimeoutJob = null
        engine = null
        snapshot = null
        _state.value = null
    }

    companion object {
        const val MAX_PAUSE_MS = 30 * 60 * 1000L
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T8-2-GREEN`, ~3 min): full suite + count. Expected: **213 tests**.

- [ ] **Step 5: Commit** — diff-review `TimerController.kt`, then `git add -A && git commit -m "feat(domain): controller-owned busy rule for entry deletes"`

### Subtask 8.3: Entry name in the notification

**Files:** Replace `domain/TimerText.kt`, `test/domain/TimerTextTest.kt`, `service/WorkoutNotifications.kt`; edit `service/TimerService.kt` and `res/values/strings.xml`.

- [ ] **Step 1: Failing tests** — `test/domain/TimerTextTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerTextTest {
    private val work = TimerState(Phase.WORK, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20,
        elapsedSec = 50, totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false)

    @Test
    fun `formats times`() {
        assertEquals("00:15", TimerText.formatMmSs(15))
        assertEquals("01:15", TimerText.formatMmSs(75))
        assertEquals("00:00:50", TimerText.formatHms(50))
        assertEquals("01:02:05", TimerText.formatHms(3725))
        assertEquals("04:00", TimerText.formatDuration(240))
        assertEquals("2:00:00", TimerText.formatDuration(7200))
    }

    @Test
    fun `notification text`() {
        assertEquals("Burpees · Work · Set 2/8", TimerText.notificationTitle("Burpees", work))
        assertEquals("Work · Set 2/8", TimerText.notificationTitle(null, work))
        assertEquals("00:15 left", TimerText.notificationBody(work))
        assertEquals("Paused · 00:15 left", TimerText.notificationBody(work.copy(paused = true)))
        assertEquals("Workout complete", TimerText.notificationBody(work.copy(phase = Phase.DONE)))
        assertEquals("Get ready", TimerText.phaseName(Phase.PREPARE))
    }

    @Test
    fun `starting title uses the frozen entry name`() {
        assertEquals("Burpees · Starting…", TimerText.startingTitle("Burpees"))
        assertEquals("Starting workout…", TimerText.startingTitle(null))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T8-3-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.TimerTextTest"`. Expected: compile FAIL, `Too many arguments` for `notificationTitle` / `Unresolved reference 'startingTitle'`.

- [ ] **Step 3: Implement** — `domain/TimerText.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import java.util.Locale

object TimerText {
    fun formatMmSs(sec: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", sec / 60, sec % 60)

    fun formatHms(sec: Int): String =
        String.format(Locale.ENGLISH, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)

    fun formatDuration(sec: Int): String =
        if (sec >= 3600) String.format(Locale.ENGLISH, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
        else formatMmSs(sec)

    fun phaseName(phase: Phase): String = when (phase) {
        Phase.PREPARE -> "Get ready"
        Phase.WORK -> "Work"
        Phase.REST -> "Rest"
        Phase.COOLDOWN -> "Cooldown"
        Phase.DONE -> "Done"
    }

    /** `"<entryName> · <Phase> · Set n/N"` with the name frozen in the run's snapshot (spec §7.6). */
    fun notificationTitle(entryName: String?, s: TimerState): String =
        listOfNotNull(entryName, phaseName(s.phase), "Set ${s.set}/${s.sets}").joinToString(" · ")

    /** The first notification, before any timer state: `"<entryName> · Starting…"`. */
    fun startingTitle(entryName: String?): String = if (entryName != null) "$entryName · Starting…" else "Starting workout…"

    fun notificationBody(s: TimerState): String = when {
        s.phase == Phase.DONE -> "Workout complete"
        s.paused -> "Paused · ${formatMmSs(s.phaseSecondsLeft)} left"
        else -> "${formatMmSs(s.phaseSecondsLeft)} left"
    }
}
```

`service/WorkoutNotifications.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mitenko.hiitcounter.MainActivity
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.model.TimerState

class WorkoutNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_name))
                .build(),
        )
    }

    /** [entryName] is the run snapshot's frozen name (spec §7.6); null only if no run is prepared. */
    fun build(state: TimerState?, entryName: String?): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, TimerService::class.java).setAction(TimerService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(state?.let { TimerText.notificationTitle(entryName, it) } ?: TimerText.startingTitle(entryName))
            .setContentText(state?.let(TimerText::notificationBody))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.stop), stop)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun update(state: TimerState, entryName: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(NOTIFICATION_ID, build(state, entryName))
    }

    companion object {
        const val CHANNEL_ID = "workout"
        const val NOTIFICATION_ID = 1
    }
}
```

`service/TimerService.kt`: three exact edits. The v1 final-review fixes stay as they are: the re-entry `startForeground`, the DONE-grace wake lock and the PREPARING release.
1. Replace `notifications.build(controller.state.value),` with `notifications.build(controller.state.value, controller.snapshot?.entryName),`
2. Replace `notifications.build(null),` with `notifications.build(null, controller.snapshot?.entryName),`
3. Replace `notifications.update(state)` with `notifications.update(state, controller.snapshot?.entryName)`

`res/values/strings.xml`: delete the now-unused line
```xml
    <string name="notification_starting">Starting workout…</string>
```

- [ ] **Step 4: Run green** — Run (label `T8-3-GREEN`, ~3 min): full suite + count. Expected: **214 tests**.

- [ ] **Step 5: Commit** — diff-review `TimerText.kt`, `WorkoutNotifications.kt` and `TimerService.kt`, then `git add -A && git commit -m "feat(service): frozen entry name in the notification title"`

### Subtask 8.4: Entry name on the timer screen

**Files:** Replace `ui/timer/TimerUiMapper.kt`, `ui/timer/TimerViewModel.kt`, `test/ui/timer/TimerUiMapperTest.kt`; edit `ui/timer/TimerScreen.kt`, `test/ui/timer/TimerScreenTest.kt`; append to `test/ui/timer/TimerViewModelTest.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/timer/TimerUiMapperTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.timer

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerUiMapperTest {
    private fun state(phase: Phase, set: Int, left: Int, duration: Int, reps: Int = 8) = TimerState(
        phase = phase, set = set, sets = 8, phaseSecondsLeft = left, phaseDurationSec = duration,
        elapsedSec = 50, totalDurationSec = 240, repsThisSet = reps, totalReps = 65, paused = false,
    )

    @Test
    fun `work shows bright reps without a label`() {
        val ui = TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20), "Burpees")
        assertNull(ui.label)
        assertEquals(8, ui.centerNumber)
        assertFalse(ui.centerDimmed)
        assertEquals(PhaseTone.WORK, ui.tone)
        assertEquals("00:15", ui.countdownText)
        assertEquals("2/8", ui.setsText)
        assertEquals("00:00:50", ui.elapsedText)
        assertEquals(0.75f, ui.innerProgress, 1e-6f)
        assertEquals((1 + 5f / 20f) / 8f, ui.outerProgress, 1e-6f)
        assertEquals("Work, set 2 of 8, 8 reps", ui.description)
    }

    @Test
    fun `rest shows label and dimmed upcoming reps`() {
        val ui = TimerUiMapper.map(state(Phase.REST, set = 3, left = 10, duration = 10, reps = 9), "Burpees")
        assertEquals("REST", ui.label)
        assertEquals(9, ui.centerNumber)
        assertTrue(ui.centerDimmed)
        assertEquals(PhaseTone.REST, ui.tone)
        assertEquals(2f / 8f, ui.outerProgress, 1e-6f)
    }

    @Test
    fun `prepare cooldown and done`() {
        val prep = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10, reps = 9), "Burpees")
        assertEquals("GET READY", prep.label)
        assertEquals(9, prep.centerNumber)
        assertTrue(prep.centerDimmed)
        assertEquals(0f, prep.outerProgress, 1e-6f)

        val cool = TimerUiMapper.map(state(Phase.COOLDOWN, set = 8, left = 5, duration = 30, reps = 0), "Burpees")
        assertEquals("COOLDOWN", cool.label)
        assertNull(cool.centerNumber)
        assertEquals(1f, cool.outerProgress, 1e-6f)

        val done = TimerUiMapper.map(state(Phase.DONE, set = 8, left = 0, duration = 0, reps = 0), "Burpees")
        assertEquals("DONE", done.label)
        assertEquals(65, done.centerNumber)
        assertTrue(done.done)
        assertEquals("", done.countdownText)
    }

    @Test
    fun `the frozen entry name is carried through every phase`() {
        Phase.entries.forEach { phase ->
            assertEquals("Kettlebell Lunges", TimerUiMapper.map(state(phase, set = 1, left = 5, duration = 10), "Kettlebell Lunges").entryName)
        }
    }
}
```

In `test/ui/timer/TimerScreenTest.kt`, replace:
```kotlin
    private fun ui(phase: Phase) = TimerUiMapper.map(
        TimerState(phase, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20, elapsedSec = 50,
            totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false),
    )
```
with:
```kotlin
    private fun ui(phase: Phase) = TimerUiMapper.map(
        TimerState(phase, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20, elapsedSec = 50,
            totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false),
        entryName = "Kettlebell Lunges",
    )
```
and append:
```kotlin
    @Test
    fun `shows the frozen entry name above the stats`() {
        compose.setContent { HiitTheme { TimerScreen(ui(Phase.WORK), onTogglePause = {}, onClose = {}) } }
        compose.onNodeWithTag("entry_name").assertTextEquals("Kettlebell Lunges")
    }
```

Append to `test/ui/timer/TimerViewModelTest.kt`:
```kotlin
    @Test
    fun `ui state shows the snapshot's entry name`() = runTest {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = TimerViewModel(controller, FakeSettingsRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        controller.prepare(WorkoutSnapshot(7L, "Kettlebell Lunges", TimingConfig(), CueConfig()))
        controller.onServiceStarted()
        controller.start(List(8) { 8 })
        runCurrent()
        assertEquals("Kettlebell Lunges", vm.uiState.value?.entryName)
    }
```

- [ ] **Step 2: Run red** — Run (label `T8-4-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.timer.*"`. Expected: compile FAIL, `Too many arguments` for `map` / `Unresolved reference 'entryName'`.

- [ ] **Step 3: Implement** — `ui/timer/TimerUiMapper.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.timer

import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState

enum class PhaseTone { WORK, REST, NEUTRAL }

data class TimerUiState(
    /** Frozen in the run's snapshot at Start (spec §7.6). */
    val entryName: String,
    val setsText: String,
    val elapsedText: String,
    val label: String?,
    val centerNumber: Int?,
    val centerDimmed: Boolean,
    val countdownText: String,
    val innerProgress: Float,
    val outerProgress: Float,
    val tone: PhaseTone,
    val paused: Boolean,
    val done: Boolean,
    val description: String,
)

object TimerUiMapper {
    fun map(s: TimerState, entryName: String): TimerUiState {
        val inner = if (s.phaseDurationSec > 0) s.phaseSecondsLeft.toFloat() / s.phaseDurationSec else 0f
        val workFraction = if (s.phase == Phase.WORK && s.phaseDurationSec > 0) {
            (s.phaseDurationSec - s.phaseSecondsLeft).toFloat() / s.phaseDurationSec
        } else {
            0f
        }
        val base = TimerUiState(
            entryName = entryName,
            setsText = "${s.set}/${s.sets}",
            elapsedText = TimerText.formatHms(s.elapsedSec),
            label = null,
            centerNumber = null,
            centerDimmed = false,
            countdownText = TimerText.formatMmSs(s.phaseSecondsLeft),
            innerProgress = inner,
            outerProgress = ((s.completedWorkSets + workFraction) / s.sets).coerceIn(0f, 1f),
            tone = PhaseTone.NEUTRAL,
            paused = s.paused,
            done = false,
            description = "",
        )
        return when (s.phase) {
            Phase.WORK -> base.copy(
                centerNumber = s.repsThisSet, tone = PhaseTone.WORK,
                description = "Work, set ${s.set} of ${s.sets}, ${s.repsThisSet} reps",
            )
            Phase.REST -> base.copy(
                label = "REST", centerNumber = s.repsThisSet, centerDimmed = true, tone = PhaseTone.REST,
                description = "Rest, next set ${s.set} of ${s.sets}, ${s.repsThisSet} reps",
            )
            Phase.PREPARE -> base.copy(
                label = "GET READY", centerNumber = s.repsThisSet, centerDimmed = true,
                description = "Get ready, first set ${s.repsThisSet} reps",
            )
            Phase.COOLDOWN -> base.copy(label = "COOLDOWN", description = "Cooldown")
            Phase.DONE -> base.copy(
                label = "DONE", centerNumber = s.totalReps, countdownText = "", innerProgress = 0f,
                outerProgress = 1f, done = true, description = "Done, ${s.totalReps} reps",
            )
        }
    }
}
```

`ui/timer/TimerViewModel.kt` (complete; still on `SettingsRepository` until 9.2):
```kotlin
package com.mitenko.hiitcounter.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val controller: TimerController,
    private val settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<TimerUiState?> = controller.state
        .map { it?.let(::toUi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value?.let(::toUi))

    val status: StateFlow<RunStatus> = controller.status

    val notificationPermissionAsked: Flow<Boolean> = settings.notificationPermissionAsked

    fun togglePause() {
        val state = controller.state.value ?: return
        if (state.paused) controller.resume() else controller.pause()
    }

    fun stop() = controller.stop()

    fun leaveDone() = controller.dismissDone()

    fun onNotificationPermissionAsked() {
        viewModelScope.launch { settings.markNotificationPermissionAsked() }
    }

    /** The name comes from the frozen snapshot, so a rename during the run never shows here (spec §7.1). */
    private fun toUi(state: TimerState): TimerUiState = TimerUiMapper.map(state, controller.snapshot?.entryName.orEmpty())
}
```

`ui/timer/TimerScreen.kt`: add `import androidx.compose.ui.text.style.TextOverflow`, then replace:
```kotlin
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Stat(R.string.sets_label, ui.setsText)
                Stat(R.string.elapsed_label, ui.elapsedText)
            }
```
with:
```kotlin
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The name frozen at Start (spec §7.6), above the Sets/Elapsed row.
                Text(
                    ui.entryName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("entry_name"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Stat(R.string.sets_label, ui.setsText)
                    Stat(R.string.elapsed_label, ui.elapsedText)
                }
            }
```

- [ ] **Step 4: Run green** — Run (label `T8-4-GREEN`, ~3 min): full suite + count. Expected: **217 tests**. The 200 % font-scale test in `TimerScreenTest` must still pass.

- [ ] **Step 5: Commit** — diff-review `TimerViewModel.kt` and `TimerScreen.kt`, then `git add -A && git commit -m "feat(ui): show the frozen entry name on the timer screen"`

**Task 8 gate:** Run (label `T8-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **217 tests**, no lint errors.

---

## Task 9: Storage DI and AppPreferences consumers (§4, §5.4)

This task adds the new layer to Hilt **alongside** the v1 providers, so the build stays green. The callers switch over in Tasks 10–14, and the v1 providers are removed in 15.2. `HiitApp` doesn't start the migrator until 15.2. If it did, the migrator's DataStores and the v1 Hilt DataStores could open the same files in one process.

**Interfaces produced:** Hilt `@Singleton` bindings for `HiitDatabase`, `AppPreferences`, `V1Migrator` and `EntryRepository` (= `RoomEntryRepository(db, migrator, clock)`). `TimerViewModel(controller, preferences: AppPreferences)`.

### Subtask 9.1: StorageModule

**Files:** Create `di/StorageModule.kt`.

- [ ] **Step 1: Module** — `di/StorageModule.kt`:
```kotlin
package com.mitenko.hiitcounter.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.data.RoomEntryRepository
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.data.v1.V1Migrator
import com.mitenko.hiitcounter.domain.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

/** Room + app preferences + the v1 migrator (spec §4). app.preferences_pb is the only Hilt DataStore after 15.2. */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): HiitDatabase =
        Room.databaseBuilder(context, HiitDatabase::class.java, HiitDatabase.NAME).build()

    @Provides @Singleton
    fun appPreferences(@ApplicationContext context: Context): AppPreferences = AppPreferences(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { e ->
                Log.e("StorageModule", "app.preferences_pb corrupt; replacing with defaults", e)
                emptyPreferences()
            },
            produceFile = { context.preferencesDataStoreFile(AppPreferences.FILE_NAME) },
        ),
    )

    /** The v1 files live where v1's preferencesDataStoreFile put them: filesDir/datastore. */
    @Provides @Singleton
    fun v1Migrator(
        @ApplicationContext context: Context,
        db: HiitDatabase,
        preferences: AppPreferences,
        @ApplicationScope scope: CoroutineScope,
    ): V1Migrator = V1Migrator(File(context.filesDir, "datastore"), db, preferences, scope, Dispatchers.IO)

    @Provides @Singleton
    fun entryRepository(db: HiitDatabase, migrator: V1Migrator, clock: Clock): EntryRepository =
        RoomEntryRepository(db, migrator, clock)
}
```

- [ ] **Step 2: Verify** — Run (label `T9-1-BUILD`, ~4 min): `./gradlew assembleDebug testDebugUnitTest`, then the test-count command. Expected: `BUILD SUCCESSFUL` (the Hilt graph compiles with both modules) and **217 tests**.

- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(di): provide Room, app preferences, the v1 migrator and the entry repository"`

### Subtask 9.2: TimerViewModel reads the flag from AppPreferences

**Files:** Replace `ui/timer/TimerViewModel.kt`, `test/ui/timer/TimerViewModelTest.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/timer/TimerViewModelTest.kt` (complete; now backed by a real `AppPreferences` in a temp folder):
```kotlin
package com.mitenko.hiitcounter.ui.timer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.preferences() = AppPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "app.preferences_pb") }),
    )

    @Test
    fun `toggle pause, finish and leave done`() = runTest {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = TimerViewModel(controller, preferences())
        backgroundScope.launch { vm.uiState.collect {} }
        controller.prepare(WorkoutSnapshot(1L, "Burpees", TimingConfig(prepareSec = 0, sets = 1, workSec = 2, restSec = 0), CueConfig()))
        controller.onServiceStarted()
        controller.start(listOf(5))
        runCurrent()
        assertEquals(5, vm.uiState.value?.centerNumber)

        vm.togglePause()
        assertTrue(vm.uiState.value!!.paused)
        vm.togglePause()
        assertFalse(vm.uiState.value!!.paused)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(RunStatus.DONE, vm.status.value)
        vm.leaveDone()
        assertEquals(RunStatus.IDLE, vm.status.value)
    }

    @Test
    fun `stop returns to idle`() = runTest {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = TimerViewModel(controller, preferences())
        controller.prepare(WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig()))
        controller.onServiceStarted()
        controller.start(List(8) { 8 })
        runCurrent()
        vm.stop()
        assertEquals(RunStatus.IDLE, vm.status.value)
    }

    @Test
    fun `ui state shows the snapshot's entry name`() = runTest {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = TimerViewModel(controller, preferences())
        backgroundScope.launch { vm.uiState.collect {} }
        controller.prepare(WorkoutSnapshot(7L, "Kettlebell Lunges", TimingConfig(), CueConfig()))
        controller.onServiceStarted()
        controller.start(List(8) { 8 })
        runCurrent()
        assertEquals("Kettlebell Lunges", vm.uiState.value?.entryName)
    }

    @Test
    fun `notification permission is asked once`() = runTest {
        val prefs = preferences()
        val vm = TimerViewModel(TimerController(backgroundScope) { testScheduler.currentTime }, prefs)
        assertFalse(vm.notificationPermissionAsked.first())
        vm.onNotificationPermissionAsked()
        runCurrent()
        assertTrue(prefs.notificationPermissionAsked.first())
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T9-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.timer.TimerViewModelTest"`. Expected: compile FAIL on the constructor argument (argument type mismatch: `AppPreferences` passed where `SettingsRepository` is expected). The exact K2 wording may differ.

- [ ] **Step 3: Implement** — `ui/timer/TimerViewModel.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val controller: TimerController,
    private val preferences: AppPreferences,
) : ViewModel() {
    val uiState: StateFlow<TimerUiState?> = controller.state
        .map { it?.let(::toUi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value?.let(::toUi))

    val status: StateFlow<RunStatus> = controller.status

    /** App-level and sticky (spec §5.4). */
    val notificationPermissionAsked: Flow<Boolean> = preferences.notificationPermissionAsked

    fun togglePause() {
        val state = controller.state.value ?: return
        if (state.paused) controller.resume() else controller.pause()
    }

    fun stop() = controller.stop()

    fun leaveDone() = controller.dismissDone()

    fun onNotificationPermissionAsked() {
        viewModelScope.launch { preferences.markNotificationPermissionAsked() }
    }

    /** The name comes from the frozen snapshot, so a rename during the run never shows here (spec §7.1). */
    private fun toUi(state: TimerState): TimerUiState = TimerUiMapper.map(state, controller.snapshot?.entryName.orEmpty())
}
```

- [ ] **Step 4: Run green** — Run (label `T9-2-GREEN`, ~3 min): full suite + count. Expected: **217 tests** (the same tests, rewritten).

- [ ] **Step 5: Commit** — diff-review `TimerViewModel.kt`, then `git add -A && git commit -m "feat(timer): read the notification flag from app preferences"`

**Task 9 gate:** Run (label `T9-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **217 tests**, no lint errors.

---

## Task 10: Entry list — the new home (§7.3, §8.3)

**Interfaces produced:**
- `testutil/Entries.kt`: `fun testEntry(id: Long, name = "Entry $id", position = id - 1, timing = TimingConfig(), progression = ProgressionConfig(), cues = CueConfig(), counter = CounterState(progression.startingTotal)): Entry`
- `testutil/FakeEntryRepository(initial: List<Entry> = emptyList(), ready: Boolean = true) : EntryRepository`, with `readiness: CompletableDeferred<Unit>`, `state: MutableStateFlow<List<Entry>>`, `checkInCalls`, `checkInError`, `moves: List<Pair<Long, Int>>`, `deleteCalls` and `find(id)`
- `data class EntryRow(id, name, reps, checkedInToday)`, `sealed interface EntryListUiState { Loading; Empty; Items(rows) }`
- `EntryListViewModel(repo, clock) { uiState; reorderMode; onResume(); toggleReorder(); moveUp(id); moveDown(id); create(name, onCreated: (Long) -> Unit) }`
- `@Composable NameDialog(title, initial, onConfirm: (String) -> Unit, onDismiss)` in `ui/common`
- `@Composable EntryListRoute(onOpenEntry: (Long) -> Unit, onCreated: (Long) -> Unit, vm)`, `@Composable EntryListScreen(state, reorderMode, onOpenEntry, onToggleReorder, onMoveUp, onMoveDown, onCreate: (String) -> Unit)`
- Strings (10.3) and drawables `ic_arrow_up`, `ic_arrow_down` (10.4)

### Subtask 10.1: Fake repository and list states (Loading → Empty/Items)

**Files:** Create `ui/entries/EntryListViewModel.kt`; test `test/ui/entries/EntryListViewModelTest.kt`, `test/testutil/Entries.kt`, `test/testutil/FakeEntryRepository.kt`.

- [ ] **Step 1: Failing tests** — `test/testutil/Entries.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Entry
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig

/** A domain entry with defaults and an untouched counter; the counter total is already resolved. */
fun testEntry(
    id: Long,
    name: String = "Entry $id",
    position: Int = (id - 1).toInt(),
    timing: TimingConfig = TimingConfig(),
    progression: ProgressionConfig = ProgressionConfig(),
    cues: CueConfig = CueConfig(),
    counter: CounterState = CounterState(total = progression.startingTotal),
) = Entry(id, name, position, timing, progression, cues, counter)
```

`test/testutil/FakeEntryRepository.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.RepProgression
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
 * IllegalArgumentException, positions stay contiguous. Settings validation is left to the
 * ViewModels under test.
 */
class FakeEntryRepository(initial: List<Entry> = emptyList(), ready: Boolean = true) : EntryRepository {
    val readiness = CompletableDeferred<Unit>().apply { if (ready) complete(Unit) }
    val state = MutableStateFlow(initial.sortedWith(compareBy<Entry>({ it.position }, { it.id })))
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    var checkInCalls = 0
    var checkInError: Throwable? = null
    val moves = mutableListOf<Pair<Long, Int>>()
    var deleteCalls = 0

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

    override suspend fun setTiming(id: Long, timing: TimingConfig) = edit(id) { it.copy(timing = timing) }

    override suspend fun setProgression(id: Long, progression: ProgressionConfig) =
        edit(id) { it.copy(progression = progression, counter = it.counter.copy(holdCount = 0)) }

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

    override suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) =
        edit(id) { it.copy(counter = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount = 0)) }

    override suspend fun resetProgress(id: Long) = edit(id) { it.copy(counter = CounterState(total = it.progression.startingTotal)) }

    fun find(id: Long): Entry = state.value.firstOrNull { it.id == id } ?: throw EntryNotFound(id)

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

`test/ui/entries/EntryListViewModelTest.kt` (the import list already covers 10.2):
```kotlin
package com.mitenko.hiitcounter.ui.entries

import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EntryListViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(instant = Instant.parse("2026-09-24T15:00:00Z")) // 08:00 PDT
    private val checkedInThisMorning = Instant.parse("2026-09-24T14:00:00Z")    // 07:00 PDT

    private fun TestScope.vm(repo: FakeEntryRepository) = EntryListViewModel(repo, clock).also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `loading until migration readiness, then empty`() = runTest {
        val repo = FakeEntryRepository(ready = false)
        val vm = vm(repo)
        assertEquals(EntryListUiState.Loading, vm.uiState.value)
        repo.readiness.complete(Unit)
        runCurrent()
        assertEquals(EntryListUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `items show the name, the next total and today's check-in`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(1, "Burpees", counter = CounterState(total = 65, lastCheckIn = checkedInThisMorning)),
                testEntry(2, "Lunges"),
            ),
        )
        assertEquals(
            EntryListUiState.Items(listOf(EntryRow(1, "Burpees", 65, true), EntryRow(2, "Lunges", 48, false))),
            vm(repo).uiState.value,
        )
    }

    @Test
    fun `resume re-evaluates today after midnight`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, "Burpees", counter = CounterState(total = 65, lastCheckIn = checkedInThisMorning))))
        val vm = vm(repo)
        assertTrue((vm.uiState.value as EntryListUiState.Items).rows.single().checkedInToday)
        clock.instant = Instant.parse("2026-09-25T08:00:00Z") // 01:00 PDT the next day
        vm.onResume()
        runCurrent()
        assertFalse((vm.uiState.value as EntryListUiState.Items).rows.single().checkedInToday)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T10-1-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.entries.EntryListViewModelTest"`. Expected: compile FAIL, `Unresolved reference 'EntryListViewModel'`.

- [ ] **Step 3: Implement** — `ui/entries/EntryListViewModel.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** [reps] is the entry's current total, i.e. the next workout's total. */
data class EntryRow(val id: Long, val name: String, val reps: Int, val checkedInToday: Boolean)

sealed interface EntryListUiState {
    data object Loading : EntryListUiState
    data object Empty : EntryListUiState
    data class Items(val rows: List<EntryRow>) : EntryListUiState
}

@HiltViewModel
class EntryListViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    /** Loading until the repository first emits; it waits for the migration, so Empty never races the import (spec §7.3). */
    val uiState: StateFlow<EntryListUiState> = combine(repo.entries, refresh) { entries, _ ->
        if (entries.isEmpty()) {
            EntryListUiState.Empty
        } else {
            val zone = clock.zone()
            val today = clock.now().atZone(zone).toLocalDate()
            EntryListUiState.Items(
                entries.map { e ->
                    EntryRow(
                        id = e.id,
                        name = e.name,
                        reps = e.counter.total,
                        checkedInToday = e.counter.lastCheckIn?.atZone(zone)?.toLocalDate() == today,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryListUiState.Loading)

    /** Re-evaluates "Checked in today" when the list resumes, e.g. after midnight. */
    fun onResume() {
        refresh.update { it + 1 }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T10-1-GREEN`, ~3 min): full suite + count. Expected: **220 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(entries): entry list state with loading, empty and items"`

### Subtask 10.2: Reorder mode, moves and create

**Files:** Replace `ui/entries/EntryListViewModel.kt`; append to `test/ui/entries/EntryListViewModelTest.kt`.

- [ ] **Step 1: Failing tests** — append to `EntryListViewModelTest`:
```kotlin
    @Test
    fun `reorder mode toggles`() = runTest {
        val vm = vm(FakeEntryRepository())
        assertFalse(vm.reorderMode.value)
        vm.toggleReorder()
        assertTrue(vm.reorderMode.value)
        vm.toggleReorder()
        assertFalse(vm.reorderMode.value)
    }

    @Test
    fun `reorder mode ends when the list becomes empty`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = vm(repo)
        vm.toggleReorder()
        assertTrue(vm.reorderMode.value)
        repo.delete(1)
        runCurrent()
        assertEquals(EntryListUiState.Empty, vm.uiState.value)
        assertFalse(vm.reorderMode.value)
    }

    @Test
    fun `move up and down call moveBy one step at a time`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2), testEntry(3)))
        val vm = vm(repo)
        vm.moveUp(2)
        vm.moveDown(1)
        runCurrent()
        assertEquals(listOf(2L to -1, 1L to 1), repo.moves)
        assertEquals(listOf(2L, 3L, 1L), repo.state.value.map { it.id })
    }

    @Test
    fun `create reports the new id`() = runTest {
        val repo = FakeEntryRepository()
        val vm = vm(repo)
        var created: Long? = null
        vm.create(" Burpees ") { created = it }
        runCurrent()
        val entry = repo.state.value.single()
        assertEquals(entry.id, created)
        assertEquals("Burpees", entry.name)
    }

    @Test
    fun `an invalid name creates nothing`() = runTest {
        val repo = FakeEntryRepository()
        val vm = vm(repo)
        var created: Long? = null
        vm.create("   ") { created = it }
        runCurrent()
        assertNull(created)
        assertTrue(repo.state.value.isEmpty())
    }
```

- [ ] **Step 2: Run red** — Run (label `T10-2-RED`, ~1 min): focused `EntryListViewModelTest`. Expected: compile FAIL, `Unresolved reference 'reorderMode'`.

- [ ] **Step 3: Implement** — `ui/entries/EntryListViewModel.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.entries

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [reps] is the entry's current total, i.e. the next workout's total. */
data class EntryRow(val id: Long, val name: String, val reps: Int, val checkedInToday: Boolean)

sealed interface EntryListUiState {
    data object Loading : EntryListUiState
    data object Empty : EntryListUiState
    data class Items(val rows: List<EntryRow>) : EntryListUiState
}

@HiltViewModel
class EntryListViewModel @Inject constructor(
    private val repo: EntryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val _reorderMode = MutableStateFlow(false)

    /** Loading until the repository first emits; it waits for the migration, so Empty never races the import (spec §7.3). */
    val uiState: StateFlow<EntryListUiState> = combine(repo.entries, refresh) { entries, _ ->
        if (entries.isEmpty()) {
            EntryListUiState.Empty
        } else {
            val zone = clock.zone()
            val today = clock.now().atZone(zone).toLocalDate()
            EntryListUiState.Items(
                entries.map { e ->
                    EntryRow(
                        id = e.id,
                        name = e.name,
                        reps = e.counter.total,
                        checkedInToday = e.counter.lastCheckIn?.atZone(zone)?.toLocalDate() == today,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryListUiState.Loading)

    val reorderMode: StateFlow<Boolean> = _reorderMode.asStateFlow()

    init {
        // Reorder mode ends when the list becomes empty (the toggle disappears with the last row).
        viewModelScope.launch { repo.entries.collect { if (it.isEmpty()) _reorderMode.value = false } }
    }

    /** Re-evaluates "Checked in today" when the list resumes, e.g. after midnight. */
    fun onResume() {
        refresh.update { it + 1 }
    }

    fun toggleReorder() {
        _reorderMode.update { !it }
    }

    fun moveUp(id: Long) = move(id, -1)

    fun moveDown(id: Long) = move(id, +1)

    /** Creates with defaults and reports the new id for navigation (spec §7.3). The name dialog already blocks invalid names. */
    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = try {
                repo.create(name)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Create rejected: ${e.message}")
                return@launch
            }
            onCreated(id)
        }
    }

    /** The repository clamps the target inside its transaction, so rapid taps never act on a stale list. */
    private fun move(id: Long, delta: Int) {
        viewModelScope.launch {
            try {
                repo.moveBy(id, delta)
            } catch (e: EntryNotFound) {
                Log.w(TAG, "Move of a deleted entry ignored", e)
            }
        }
    }

    private companion object {
        const val TAG = "EntryListViewModel"
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T10-2-GREEN`, ~3 min): full suite + count. Expected: **225 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryListViewModel.kt`, then `git add -A && git commit -m "feat(entries): reorder mode, one-step moves and create"`

### Subtask 10.3: Name dialog and the new strings

**Files:** Create `ui/common/NameDialog.kt`; replace `res/values/strings.xml` (all strings for Tasks 10–14 are added here in one go); test `test/ui/common/NameDialogTest.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/common/NameDialogTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NameDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `an empty name disables OK and says why`() {
        compose.setContent { HiitTheme { NameDialog("New workout", "", onConfirm = {}, onDismiss = {}) } }
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
        compose.onNodeWithText("Enter a name").assertExists()
    }

    @Test
    fun `41 characters disables OK and says why`() {
        var confirmed: String? = null
        compose.setContent { HiitTheme { NameDialog("New workout", "", onConfirm = { confirmed = it }, onDismiss = {}) } }
        compose.onNodeWithTag("name_field").performTextReplacement("x".repeat(41))
        compose.onNodeWithTag("name_ok").assertIsNotEnabled()
        compose.onNodeWithText("Use at most 40 characters").assertExists()
        compose.onNodeWithTag("name_field").performTextReplacement("Burpees")
        compose.onNodeWithTag("name_ok").assertIsEnabled().performClick()
        assertEquals("Burpees", confirmed)
    }

    @Test
    fun `rename is prefilled and returns the trimmed name`() {
        var confirmed: String? = null
        compose.setContent { HiitTheme { NameDialog("Rename", "Burpees", onConfirm = { confirmed = it }, onDismiss = {}) } }
        compose.onNodeWithTag("name_field").assertTextContains("Burpees")
        compose.onNodeWithTag("name_field").performTextReplacement("  Kettlebell Lunges  ")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals("Kettlebell Lunges", confirmed)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T10-3-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.common.NameDialogTest"`. Expected: compile FAIL, `Unresolved reference 'NameDialog'`.

- [ ] **Step 3: Implement** — `res/values/strings.xml` (complete: the v1 strings minus `notification_starting`, which 8.3 removed, plus the new block):
```xml
<resources>
    <string name="app_name">HIIT Counter</string>
    <string name="settings">Settings</string>
    <string name="start">Start</string>
    <string name="total_reps">Total Reps</string>
    <string name="last_check_in">Last Check In</string>
    <string name="best_streak">Best CI Streak</string>
    <string name="current_streak">Curr CI Streak</string>
    <string name="today">Today</string>
    <string name="checked_in_today">Checked in today</string>
    <string name="sets_label">Sets</string>
    <string name="elapsed_label">Elapsed</string>
    <string name="pause">Pause</string>
    <string name="resume">Resume</string>
    <string name="stop">Stop</string>
    <string name="cancel">Cancel</string>
    <string name="stop_workout_title">Stop workout?</string>
    <string name="stop_workout_body">Today\'s check-in has already been recorded.</string>
    <string name="notification_channel_name">Workout timer</string>
    <string name="back">Back</string>
    <string name="save">Save</string>
    <string name="settings_timing">Timing</string>
    <string name="settings_progression">Progression</string>
    <string name="settings_current_state">Current State</string>
    <string name="settings_cues">Cues</string>
    <string name="prepare">PREPARE</string>
    <string name="sets">SETS</string>
    <string name="work">WORK</string>
    <string name="rest">REST</string>
    <string name="cooldown">COOLDOWN</string>
    <string name="total_duration">TOTAL %1$s</string>
    <string name="decrease">Decrease %1$s</string>
    <string name="increase">Increase %1$s</string>
    <string name="starting_total">Starting total</string>
    <string name="floor">Floor (min)</string>
    <string name="cap">Cap (max)</string>
    <string name="hold_at">Hold at</string>
    <string name="hold_for">Hold for (check-ins)</string>
    <string name="window_hours">Check-in window (hours)</string>
    <string name="penalty_rate">Penalty rate (hours per rep)</string>
    <string name="reset_defaults">Reset to defaults</string>
    <string name="current_total">Current total</string>
    <string name="best_streak_field">Best streak</string>
    <string name="current_streak_field">Current streak</string>
    <string name="last_check_in_field">Last check-in</string>
    <string name="set">Set</string>
    <string name="clear">Clear</string>
    <string name="none">—</string>
    <string name="reset_progress">Reset progress</string>
    <string name="reset_progress_title">Reset progress?</string>
    <string name="reset_progress_body">Total returns to the starting total, streaks to 0 and the last check-in is cleared.</string>
    <string name="reset">Reset</string>
    <string name="next">Next</string>
    <string name="ok">OK</string>
    <string name="sound">Sound</string>
    <string name="vibration">Vibration</string>

    <!-- Multi-entry (spec §7, §8) -->
    <string name="no_workouts">No workouts yet</string>
    <string name="add_first_workout">Add your first workout</string>
    <string name="add_workout">Add workout</string>
    <string name="new_workout">New workout</string>
    <string name="name_label">Name</string>
    <string name="reorder">Reorder</string>
    <string name="done">Done</string>
    <string name="reps_n">Reps %1$d</string>
    <string name="move_up">Move %1$s up</string>
    <string name="move_down">Move %1$s down</string>
    <string name="rename">Rename</string>
    <string name="duplicate">Duplicate</string>
    <string name="delete">Delete</string>
    <string name="delete_title">Delete %1$s?</string>
    <string name="delete_body">Its rep total, streaks and settings will be lost.</string>
    <string name="stop_workout_first">Stop the workout first</string>
    <string name="edit_value">Edit %1$s</string>
</resources>
```

`ui/common/NameDialog.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.EntryNames
import com.mitenko.hiitcounter.domain.NameCheck

/**
 * Create and rename dialog (spec §8.3). OK is enabled only when [EntryNames.validate] passes and
 * an inline message says why otherwise. A rename passes the current name as [initial]; the text
 * lives in rememberSaveable, so it survives rotation.
 */
@Composable
fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length)))
    }
    val check = EntryNames.validate(text.text)
    val error = EntryNames.errorMessage(check)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
            // Inside the dialog's own composition, so the requester is attached when this runs.
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = {
            TextButton(
                onClick = { (check as? NameCheck.Ok)?.let { onConfirm(it.name) } },
                enabled = check is NameCheck.Ok,
                modifier = Modifier.testTag("name_ok"),
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
```

- [ ] **Step 4: Run green** — Run (label `T10-3-GREEN`, ~3 min): full suite + count. Expected: **228 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): name dialog for create and rename"`

### Subtask 10.4: EntryListScreen (Loading, Empty, Items, reorder, keyed LazyColumn)

**Files:** Create `ui/entries/EntryListScreen.kt`, `res/drawable/ic_arrow_up.xml`, `res/drawable/ic_arrow_down.xml`; test `test/ui/entries/EntryListScreenTest.kt`.

Reorder mode hides the FAB so it can't overlap the ▼ buttons. After a move, the moved row is scrolled back into view if the move pushed it off the visible area.

- [ ] **Step 1: Failing tests** — `test/ui/entries/EntryListScreenTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.entries

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntryListScreenTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(
        EntryRow(1, "Burpees", 65, checkedInToday = true),
        EntryRow(2, "Lunges", 48, checkedInToday = false),
        EntryRow(3, "Squats", 50, checkedInToday = false),
    )

    private fun show(
        state: EntryListUiState,
        reorder: Boolean = false,
        onOpen: (Long) -> Unit = {},
        onUp: (Long) -> Unit = {},
        onDown: (Long) -> Unit = {},
        onCreate: (String) -> Unit = {},
    ) {
        compose.setContent {
            HiitTheme {
                EntryListScreen(
                    state, reorder, onOpenEntry = onOpen, onToggleReorder = {},
                    onMoveUp = onUp, onMoveDown = onDown, onCreate = onCreate,
                )
            }
        }
    }

    @Test
    fun `loading shows progress and the add button is disabled`() {
        show(EntryListUiState.Loading)
        compose.onNodeWithTag("loading").assertExists()
        compose.onNodeWithTag("add").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("name_field").assertDoesNotExist()
    }

    @Test
    fun `the empty state offers the first workout`() {
        show(EntryListUiState.Empty)
        compose.onNodeWithText("No workouts yet").assertExists()
        compose.onNodeWithTag("add_first").performClick()
        compose.onNodeWithTag("name_field").assertExists()
    }

    @Test
    fun `rows show the name, reps and today's marker and open on tap`() {
        val opened = mutableListOf<Long>()
        show(EntryListUiState.Items(rows), onOpen = { opened += it })
        compose.onNodeWithText("Reps 65").assertExists()
        compose.onNodeWithText("Reps 48").assertExists()
        compose.onAllNodesWithContentDescription("Checked in today", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Lunges").performClick()
        assertEquals(listOf(2L), opened)
    }

    @Test
    fun `reorder buttons move rows and disable the edges`() {
        val moves = mutableListOf<String>()
        show(EntryListUiState.Items(rows), reorder = true, onUp = { moves += "up $it" }, onDown = { moves += "down $it" })
        compose.onNodeWithContentDescription("Move Burpees up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Squats down").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move Burpees down").assertIsEnabled()
        compose.onNodeWithContentDescription("Move Lunges up").performClick()
        compose.onNodeWithContentDescription("Move Lunges down").performClick()
        assertEquals(listOf("up 2", "down 2"), moves)
        compose.onNodeWithTag("add").assertDoesNotExist()
    }

    @Test
    fun `a moved row stays scrolled into view`() {
        var list by mutableStateOf((1L..12L).map { EntryRow(it, "Entry $it", 48, false) })
        compose.setContent {
            HiitTheme {
                EntryListScreen(
                    EntryListUiState.Items(list), reorderMode = true, onOpenEntry = {}, onToggleReorder = {}, onMoveUp = {},
                    onMoveDown = { id ->
                        val i = list.indexOfFirst { it.id == id }
                        if (i < list.lastIndex) list = list.toMutableList().apply { add(i + 1, removeAt(i)) }
                    },
                    onCreate = {},
                )
            }
        }
        repeat(10) {
            compose.onNodeWithContentDescription("Move Entry 1 down").performClick()
            compose.waitForIdle()
        }
        assertEquals(10, list.indexOfFirst { it.id == 1L })
        compose.onNodeWithText("Entry 1").assertIsDisplayed()
    }

    @Test
    fun `add creates through the name dialog`() {
        var created: String? = null
        show(EntryListUiState.Items(rows), onCreate = { created = it })
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("name_field").performTextReplacement("Kettlebell Lunges")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals("Kettlebell Lunges", created)
        compose.onNodeWithTag("name_field").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T10-4-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.entries.EntryListScreenTest"`. Expected: compile FAIL, `Unresolved reference 'EntryListScreen'`.

- [ ] **Step 3: Implement**

`res/drawable/ic_arrow_up.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z" />
</vector>
```

`res/drawable/ic_arrow_down.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z" />
</vector>
```

`ui/entries/EntryListScreen.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.entries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.common.NameDialog

@Composable
fun EntryListRoute(onOpenEntry: (Long) -> Unit, onCreated: (Long) -> Unit, vm: EntryListViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val reorderMode by vm.reorderMode.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    EntryListScreen(
        state = state,
        reorderMode = reorderMode,
        onOpenEntry = onOpenEntry,
        onToggleReorder = vm::toggleReorder,
        onMoveUp = vm::moveUp,
        onMoveDown = vm::moveDown,
        onCreate = { name -> vm.create(name, onCreated) },
    )
}

/** The entry list (spec §7.3): Loading disables the FAB, Empty offers the first workout, Items is a keyed LazyColumn. */
@Composable
fun EntryListScreen(
    state: EntryListUiState,
    reorderMode: Boolean,
    onOpenEntry: (Long) -> Unit,
    onToggleReorder: () -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    val loading = state is EntryListUiState.Loading
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (state is EntryListUiState.Items) {
                    TextButton(onClick = onToggleReorder, modifier = Modifier.testTag("reorder")) {
                        Text(stringResource(if (reorderMode) R.string.done else R.string.reorder))
                    }
                }
            }
        },
        floatingActionButton = {
            if (!reorderMode) {
                FloatingActionButton(
                    onClick = { if (!loading) naming = true },
                    modifier = Modifier.testTag("add").semantics { if (loading) disabled() },
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_workout))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                EntryListUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("loading"))
                EntryListUiState.Empty ->
                    EmptyState(onAdd = { naming = true }, modifier = Modifier.align(Alignment.Center))
                is EntryListUiState.Items ->
                    EntryList(state.rows, reorderMode, onOpenEntry, onMoveUp, onMoveDown)
            }
        }
    }
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
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.no_workouts), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAdd, modifier = Modifier.testTag("add_first")) { Text(stringResource(R.string.add_first_workout)) }
    }
}

@Composable
private fun EntryList(
    rows: List<EntryRow>,
    reorderMode: Boolean,
    onOpen: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    var movedId by remember { mutableStateOf<Long?>(null) }
    // Keep the moved row in view (spec §7.3): wait one frame for the reordered layout, then scroll just enough.
    LaunchedEffect(rows, movedId) {
        val id = movedId ?: return@LaunchedEffect
        withFrameNanos { }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == id }
        when {
            item == null -> rows.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { listState.animateScrollToItem(it) }
            item.offset < info.viewportStartOffset ->
                listState.animateScrollBy((item.offset - info.viewportStartOffset).toFloat())
            item.offset + item.size > info.viewportEndOffset ->
                listState.animateScrollBy((item.offset + item.size - info.viewportEndOffset).toFloat())
            else -> Unit
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            EntryRowItem(
                row = row,
                reorderMode = reorderMode,
                canMoveUp = index > 0,
                canMoveDown = index < rows.lastIndex,
                onOpen = { onOpen(row.id) },
                onMoveUp = {
                    movedId = row.id
                    onMoveUp(row.id)
                },
                onMoveDown = {
                    movedId = row.id
                    onMoveDown(row.id)
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EntryRowItem(
    row: EntryRow,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val checkedDescription = stringResource(R.string.checked_in_today)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !reorderMode, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("entry_${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.reps_n, row.reps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.checkedInToday) {
            Text(
                "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = checkedDescription },
            )
        }
        if (reorderMode) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.move_up, row.name))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = stringResource(R.string.move_down, row.name))
            }
        }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T10-4-GREEN`, ~3 min): full suite + count. Expected: **234 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(entries): entry list screen with reorder mode and empty and loading states"`

**Task 10 gate:** Run (label `T10-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **234 tests**, no lint errors.

---

## Task 11: Entry screen — the v1 table, per entry (§7.2, §7.4)

This task ports `HomeViewModel`/`HomeScreen` and their tests to `ui/entry`. The v1 home files stay until 15.2, where they are deleted along with `HomeViewModelTest` and `HomeScreenTest`.

**Interfaces produced:**
- `ui/common/EntryScopedViewModel.kt`: `const val ENTRY_ID_ARG = "id"`, `abstract class EntryScopedViewModel(savedStateHandle, repo: EntryRepository) : ViewModel() { protected val entryId: Long; val missing: StateFlow<Boolean>; protected fun markMissing() }`
- `data class EntryUiState(name, reps, total, lastCheckIn, bestStreak, currentStreak, today, checkedInToday, starting, error)`
- `EntryViewModel(savedStateHandle, repo, controller: TimerController, starter: WorkoutServiceStarter, clock) { uiState; onResume(); onStart(); dismissError(); SERVICE_START_TIMEOUT_MS }`
- `@Composable EntryRoute(onBack, onOpenSettings, onEntryGone, vm)`, `@Composable EntryScreen(state, onBack, onStart, onOpenSettings, onDismissError)`

### Subtask 11.1: EntryScopedViewModel and the entry table state

**Files:** Create `ui/common/EntryScopedViewModel.kt`, `ui/entry/EntryViewModel.kt`; test `test/ui/entry/EntryViewModelTest.kt`, `test/ui/entry/UntouchedTotalTest.kt` (Robolectric + Room).

- [ ] **Step 1: Failing tests** — `test/ui/entry/EntryViewModelTest.kt` (the import list already covers 11.2):
```kotlin
package com.mitenko.hiitcounter.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.FakeServiceStarter
import com.mitenko.hiitcounter.testutil.FakeServiceStarter.Behavior
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(instant = Instant.parse("2026-09-24T15:00:00Z")) // 08:00 PDT
    private val repo = FakeEntryRepository(
        listOf(
            testEntry(
                1, "Burpees",
                counter = CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
            ),
            testEntry(2, "Lunges"),
        ),
    )

    private class Harness(val vm: EntryViewModel, val controller: TimerController, val starter: FakeServiceStarter)

    private fun TestScope.harness(
        behavior: Behavior = Behavior.SUCCEED,
        repository: FakeEntryRepository = repo,
    ): Harness {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val starter = FakeServiceStarter(controller, behavior)
        val vm = EntryViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repository, controller, starter, clock)
        backgroundScope.launch { vm.uiState.collect {} }
        return Harness(vm, controller, starter)
    }

    @Test
    fun `table shows the entry's distributed reps and sheet-style fields`() = runTest {
        val h = harness()
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals("Burpees", s.name)
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), s.reps)
        assertEquals(65, s.total)
        assertEquals("23 Sep 2026, 05:55", s.lastCheckIn)
        assertEquals(24, s.bestStreak)
        assertEquals(4, s.currentStreak)
        assertEquals("24 Sep 2026", s.today)
        assertFalse(s.checkedInToday)
    }

    @Test
    fun `reps follow the entry's own number of sets`() = runTest {
        val h = harness()
        repo.state.update { list -> list.map { if (it.id == 1L) it.copy(timing = it.timing.copy(sets = 5)) else it } }
        runCurrent()
        assertEquals(listOf(13, 13, 13, 13, 13), h.vm.uiState.value.reps)
    }

    @Test
    fun `a missing entry reports missing only after loading`() = runTest {
        val notReady = FakeEntryRepository(emptyList(), ready = false)
        val h = harness(repository = notReady)
        runCurrent()
        assertFalse(h.vm.missing.value)
        notReady.readiness.complete(Unit)
        runCurrent()
        assertTrue(h.vm.missing.value)
    }
}
```

`test/ui/entry/UntouchedTotalTest.kt`. This covers a NULL total before the first check-in, rendered through the real Room repository on both screens:
```kotlin
package com.mitenko.hiitcounter.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.data.MigrationGate
import com.mitenko.hiitcounter.data.RoomEntryRepository
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeServiceStarter
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.entries.EntryListUiState
import com.mitenko.hiitcounter.ui.entries.EntryListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UntouchedTotalTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `an untouched entry shows its starting total on the list and the entry screen`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HiitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val clock = FakeClock()
            val open = object : MigrationGate {
                override suspend fun awaitReady() = Unit
            }
            val repo = RoomEntryRepository(db, open, clock)
            val id = repo.create("Burpees")
            assertNull(db.entryDao().get(id)!!.total)
            val controller = TimerController(backgroundScope) { testScheduler.currentTime }
            val entryVm = EntryViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to id)), repo, controller, FakeServiceStarter(controller), clock)
            val listVm = EntryListViewModel(repo, clock)
            backgroundScope.launch { entryVm.uiState.collect {} }
            backgroundScope.launch { listVm.uiState.collect {} }
            val entry = entryVm.uiState.first { it.name == "Burpees" }
            assertEquals(48, entry.total)
            assertEquals(List(8) { 6 }, entry.reps)
            val list = listVm.uiState.first { it is EntryListUiState.Items } as EntryListUiState.Items
            assertEquals(48, list.rows.single().reps)
        } finally {
            db.close()
        }
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T11-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.entry.*"`. Expected: compile FAIL, `Unresolved reference 'EntryViewModel'` / `'ENTRY_ID_ARG'`.

- [ ] **Step 3: Implement** — `ui/common/EntryScopedViewModel.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.EntryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The `{id}` route argument of every per-entry screen (spec §7.2). */
const val ENTRY_ID_ARG = "id"

/**
 * Base for ViewModels bound to one entry. [missing] turns true only once the entry flow has
 * loaded and emitted null (deleted elsewhere, stale saved state, bad id), or when a write hit
 * EntryNotFound. The initial loading value never triggers it (spec §7.2).
 */
abstract class EntryScopedViewModel(
    savedStateHandle: SavedStateHandle,
    protected val repo: EntryRepository,
) : ViewModel() {
    protected val entryId: Long =
        checkNotNull(savedStateHandle.get<Long>(ENTRY_ID_ARG)) { "Missing route argument '$ENTRY_ID_ARG'" }

    private val gone = MutableStateFlow(false)

    val missing: StateFlow<Boolean> = combine(repo.entry(entryId).map { it == null }, gone) { absent, lost -> absent || lost }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** A write raced a delete (spec §7.5): the screen pops to the list instead of crashing. */
    protected fun markMissing() {
        gone.value = true
    }
}
```

`ui/entry/EntryViewModel.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.RepDistributor
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.service.WorkoutServiceStarter
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EntryUiState(
    val name: String = "",
    val reps: List<Int> = emptyList(),
    val total: Int = 0,
    val lastCheckIn: String = "—",
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val today: String = "",
    val checkedInToday: Boolean = false,
    val starting: Boolean = false,
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
    private data class Transient(val starting: Boolean = false, val error: String? = null)

    private val transient = MutableStateFlow(Transient())
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<EntryUiState> =
        combine(repo.entry(entryId).filterNotNull(), transient, refresh) { entry, tr, _ ->
            val now = clock.now()
            val zone = clock.zone()
            val counter = entry.counter
            EntryUiState(
                name = entry.name,
                reps = RepDistributor.distribute(counter.total, entry.timing.sets),
                total = counter.total,
                lastCheckIn = counter.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: "—",
                bestStreak = counter.bestStreak,
                currentStreak = counter.currentStreak,
                today = DateFormats.date(now, zone),
                checkedInToday = counter.lastCheckIn?.atZone(zone)?.toLocalDate() == now.atZone(zone).toLocalDate(),
                starting = tr.starting,
                error = tr.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState())

    /** Re-evaluates "Today" and "Checked in today" when the screen resumes (e.g. after midnight). */
    fun onResume() {
        refresh.update { it + 1 }
    }
}
```
(`controller`/`starter` are unused until 11.2. The warnings are expected, and the constructor is final.)

- [ ] **Step 4: Run green** — Run (label `T11-1-GREEN`, ~3 min): full suite + count. Expected: **238 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(entry): per-entry table state and the missing-entry signal"`

### Subtask 11.2: Start flow, ported per entry

**Files:** Replace `ui/entry/EntryViewModel.kt`; append to `test/ui/entry/EntryViewModelTest.kt`.

This is v1 §4 unchanged, with two differences:
- the snapshot is frozen from this entry;
- the check-in is `EntryRepository.checkIn(id, clock)`.

If `checkIn` throws, including `EntryNotFound`, the v1 failure path runs: `fail()` → `cancelPrepare()` and an error is shown.

- [ ] **Step 1: Failing tests** — append to `EntryViewModelTest`:
```kotlin
    @Test
    fun `a successful start freezes the snapshot and checks in this entry only`() = runTest {
        val h = harness()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertEquals(1, repo.checkInCalls)
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        assertEquals(WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig()), h.controller.snapshot)
        assertEquals(66, repo.find(1).counter.total)
        assertNull(repo.find(2).counter.lastCheckIn)
        assertTrue(h.vm.uiState.value.checkedInToday)
        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `a service start exception leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.THROW_ON_START)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("not allowed"))
    }

    @Test
    fun `a foreground failure inside the service leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.FAIL_IN_SERVICE)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("boom"))
    }

    @Test
    fun `no service response times out without checking in`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        advanceTimeBy(EntryViewModel.SERVICE_START_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(0, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error != null)
    }

    @Test
    fun `start is debounced and reports starting while in flight`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertTrue(h.vm.uiState.value.starting)
    }

    @Test
    fun `checkIn throwing returns the controller to idle without crashing`() = runTest {
        val h = harness()
        repo.checkInError = IOException("disk full")
        h.vm.onStart()
        runCurrent()
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("disk full"))
    }

    @Test
    fun `checkIn throwing EntryNotFound during PREPARING returns to idle with an error`() = runTest {
        val h = harness()
        repo.checkInError = EntryNotFound(1)
        h.vm.onStart()
        runCurrent()
        assertEquals(1, repo.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("Entry 1 not found"))
        assertFalse(h.vm.uiState.value.starting)
    }

    @Test
    fun `cancelling mid-start returns the controller to idle`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        runCurrent()
        h.vm.viewModelScope.cancel()
        runCurrent()
        assertEquals(RunStatus.IDLE, h.controller.status.value)
    }
```

- [ ] **Step 2: Run red** — Run (label `T11-2-RED`, ~1 min): focused `EntryViewModelTest`. Expected: compile FAIL, `Unresolved reference 'onStart'` / `'SERVICE_START_TIMEOUT_MS'`.

- [ ] **Step 3: Implement** — `ui/entry/EntryViewModel.kt` (complete):
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
import com.mitenko.hiitcounter.service.WorkoutServiceStarter
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class EntryUiState(
    val name: String = "",
    val reps: List<Int> = emptyList(),
    val total: Int = 0,
    val lastCheckIn: String = "—",
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val today: String = "",
    val checkedInToday: Boolean = false,
    val starting: Boolean = false,
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
    private data class Transient(val starting: Boolean = false, val error: String? = null)

    private val transient = MutableStateFlow(Transient())
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<EntryUiState> =
        combine(repo.entry(entryId).filterNotNull(), transient, refresh) { entry, tr, _ ->
            val now = clock.now()
            val zone = clock.zone()
            val counter = entry.counter
            EntryUiState(
                name = entry.name,
                reps = RepDistributor.distribute(counter.total, entry.timing.sets),
                total = counter.total,
                lastCheckIn = counter.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: "—",
                bestStreak = counter.bestStreak,
                currentStreak = counter.currentStreak,
                today = DateFormats.date(now, zone),
                checkedInToday = counter.lastCheckIn?.atZone(zone)?.toLocalDate() == now.atZone(zone).toLocalDate(),
                starting = tr.starting,
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

    /** v1 spec §4: the check-in is committed only after the foreground service has started. */
    fun onStart() {
        if (transient.value.starting) return
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

- [ ] **Step 4: Run green** — Run (label `T11-2-GREEN`, ~3 min): full suite + count. Expected: **246 tests**.

- [ ] **Step 5: Commit** — diff-review `EntryViewModel.kt` against `ui/home/HomeViewModel.kt`. The cancel/throw handling must match v1. Then `git add -A && git commit -m "feat(entry): per-entry start flow with a frozen snapshot"`

### Subtask 11.3: EntryScreen

**Files:** Create `ui/entry/EntryScreen.kt`; test `test/ui/entry/EntryScreenTest.kt` (ported from `HomeScreenTest`).

- [ ] **Step 1: Failing tests** — `test/ui/entry/EntryScreenTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.entry

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val state = EntryUiState(
        name = "Burpees", reps = listOf(9, 8, 8, 8, 8, 8, 8, 8), total = 65, lastCheckIn = "24 Sep 2026, 05:55",
        bestStreak = 24, currentStreak = 4, today = "24 Sep 2026", checkedInToday = true,
    )

    private fun show(s: EntryUiState, onBack: () -> Unit = {}, onStart: () -> Unit = {}, onSettings: () -> Unit = {}) {
        compose.setContent {
            HiitTheme { EntryScreen(s, onBack = onBack, onStart = onStart, onOpenSettings = onSettings, onDismissError = {}) }
        }
    }

    @Test
    fun `renders the entry's sheet table and name`() {
        show(state)
        compose.onNodeWithTag("entry_name").assertTextEquals("Burpees")
        compose.onNodeWithTag("rep_0").assertTextEquals("9")
        compose.onNodeWithTag("rep_7").assertTextEquals("8")
        compose.onNodeWithText("Total Reps").assertExists()
        compose.onNodeWithText("65").assertExists()
        compose.onNodeWithText("24 Sep 2026, 05:55").assertExists()
        compose.onNodeWithText("Best CI Streak").assertExists()
        compose.onNodeWithText("Checked in today").assertExists()
    }

    @Test
    fun `start invokes the callback`() {
        var clicks = 0
        show(state, onStart = { clicks++ })
        compose.onNodeWithTag("start").performScrollTo().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `start, back and settings are disabled while starting`() {
        show(state.copy(starting = true))
        compose.onNodeWithTag("start").assertIsNotEnabled()
        compose.onNodeWithTag("back").assertIsNotEnabled()
        compose.onNodeWithTag("settings").assertIsNotEnabled()
    }

    @Test
    fun `back and settings invoke their callbacks`() {
        var backs = 0
        var settings = 0
        show(state, onBack = { backs++ }, onSettings = { settings++ })
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("settings").performClick()
        assertEquals(1, backs)
        assertEquals(1, settings)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T11-3-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.entry.EntryScreenTest"`. Expected: compile FAIL, `Unresolved reference 'EntryScreen'`.

- [ ] **Step 3: Implement** — `ui/entry/EntryScreen.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.entry

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
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
    EntryScreen(state, onBack = onBack, onStart = vm::onStart, onOpenSettings = onOpenSettings, onDismissError = vm::dismissError)
}

@Composable
fun EntryScreen(
    state: EntryUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
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
            Button(
                onClick = onStart,
                enabled = !state.starting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("start"),
            ) {
                Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RepTable(state: EntryUiState) {
    val line = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().border(1.dp, line)) {
        state.reps.forEachIndexed { index, reps ->
            Text(
                "$reps",
                modifier = Modifier.fillMaxWidth().border(0.5.dp, line).padding(vertical = 8.dp).testTag("rep_$index"),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        TableRow(R.string.total_reps, "${state.total}")
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

- [ ] **Step 4: Run green** — Run (label `T11-3-GREEN`, ~3 min): full suite + count. Expected: **250 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(entry): entry screen with back and settings disabled while starting"`

**Task 11 gate:** Run (label `T11-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **250 tests**, no lint errors.

---

## Task 12: Shared stepper row and edit dialog (§8.1–§8.3)

**Interfaces produced** (all in `ui/common`):
- `enum class ValueInput(keyboardType: KeyboardType, invalidMessage: String) { TIME, WHOLE, DECIMAL }`
- `@Composable fun <T : Any> EditValueDialog(title, initialText, input: ValueInput, parse: (String) -> T?, onConfirm: (T) -> Unit, onDismiss)`. Test tags are `edit_field` and `edit_ok`.
- `@Composable StepperRow(label, valueText, onMinus, onPlus, onValueTap, error = null, hint = null)`. Test tags are `value_<label>` and `support_<label>`, and the content descriptions are "Decrease <label>" and "Increase <label>".
- `@Composable IntStepperField(label, value: Int, range: StepRange, input: ValueInput, onUpdate: ((Int) -> Int) -> Unit, error = null, hint = null)`
- `@Composable PenaltyStepperField(label, value: PenaltyDraft, onUpdate: ((PenaltyDraft) -> PenaltyDraft) -> Unit, error = null)`

State model (§8.1):
- The **draft** is a typed domain value held by the settings ViewModel (Task 13).
- The **dialog** holds its own text in `rememberSaveable` and changes the draft only on OK.
- `onUpdate` takes a transform, so each hold-to-repeat step applies to the latest draft.

### Subtask 12.1: EditValueDialog

**Files:** Create `ui/common/EditValueDialog.kt`; test `test/ui/common/EditValueDialogTest.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/common/EditValueDialogTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.ValueFormat
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EditValueDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the prefilled value can be replaced and confirmed`() {
        var confirmed: Int? = null
        compose.setContent {
            HiitTheme {
                EditValueDialog("PREPARE", "00:10", ValueInput.TIME, ValueFormat::parseSeconds, onConfirm = { confirmed = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("edit_field").assertTextContains("00:10")
        compose.onNodeWithTag("edit_ok").assertIsEnabled()
        compose.onNodeWithTag("edit_field").performTextReplacement("1:30")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(90, confirmed)
    }

    @Test
    fun `an unparseable time disables OK and explains why`() {
        compose.setContent {
            HiitTheme { EditValueDialog("PREPARE", "00:10", ValueInput.TIME, ValueFormat::parseSeconds, onConfirm = {}, onDismiss = {}) }
        }
        compose.onNodeWithTag("edit_field").performTextReplacement("1:5")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
        compose.onNodeWithText("Use m:ss or seconds").assertExists()
    }

    @Test
    fun `decimals accept a comma and reject garbage`() {
        var confirmed: Double? = null
        compose.setContent {
            HiitTheme {
                EditValueDialog("PENALTY", "19.5", ValueInput.DECIMAL, ValueFormat::parseDecimal, onConfirm = { confirmed = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("edit_field").performTextReplacement("abc")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
        compose.onNodeWithText("Enter a number").assertExists()
        compose.onNodeWithTag("edit_field").performTextReplacement("12,5")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(12.5, confirmed!!, 0.0)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T12-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.common.EditValueDialogTest"`. Expected: compile FAIL, `Unresolved reference 'EditValueDialog'` / `'ValueInput'`.

- [ ] **Step 3: Implement** — `ui/common/EditValueDialog.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.SettingsValidator

/** How the edit dialog reads its text (spec §8.2, §8.3). */
enum class ValueInput(val keyboardType: KeyboardType, val invalidMessage: String) {
    /** m:ss or plain seconds; Ascii so the keyboard has ':'. */
    TIME(KeyboardType.Ascii, "Use m:ss or seconds"),
    WHOLE(KeyboardType.Number, SettingsValidator.NOT_A_NUMBER),
    DECIMAL(KeyboardType.Decimal, SettingsValidator.NOT_A_NUMBER),
}

/**
 * Edit-value dialog (spec §8.3). The title is the field label and the text starts selected.
 * OK is disabled while [parse] returns null, with an inline explanation. The text lives in
 * rememberSaveable, so it survives rotation. The caller clamps the confirmed value to the
 * field's hard range; cross-field rules are left to the screen's validation.
 */
@Composable
fun <T : Any> EditValueDialog(
    title: String,
    initialText: String,
    input: ValueInput,
    parse: (String) -> T?,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val parsed = parse(text.text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val focus = remember { FocusRequester() }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = parsed == null,
                supportingText = if (parsed == null) {
                    { Text(input.invalidMessage) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = input.keyboardType, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("edit_field"),
            )
            // Inside the dialog's own composition, so the requester is attached when this runs.
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null, modifier = Modifier.testTag("edit_ok")) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
```

- [ ] **Step 4: Run green** — Run (label `T12-1-GREEN`, ~3 min): full suite + count. Expected: **253 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): edit-value dialog with typed parsing"`

### Subtask 12.2: StepperRow and IntStepperField

**Files:** Create `ui/common/StepperRow.kt`; test `test/ui/common/StepperRowTest.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/common/StepperRowTest.kt` (the import list already covers 12.3):
```kotlin
package com.mitenko.hiitcounter.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.PenaltyDraft
import com.mitenko.hiitcounter.domain.StepRange
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StepperRowTest {
    @get:Rule val compose = createComposeRule()

    private var value by mutableIntStateOf(0)

    private fun showInt(initial: Int, range: StepRange, input: ValueInput, label: String) {
        value = initial
        compose.setContent {
            HiitTheme { Column { IntStepperField(label, value, range, input, onUpdate = { f -> value = f(value) }) } }
        }
    }

    @Test
    fun `plus and minus step and clamp at the hard minimum`() {
        showInt(1, FieldRanges.SETS, ValueInput.WHOLE, "SETS")
        compose.onNodeWithContentDescription("Decrease SETS").performClick()
        assertEquals(1, value)
        compose.onNodeWithContentDescription("Increase SETS").performClick()
        compose.onNodeWithContentDescription("Increase SETS").performClick()
        assertEquals(3, value)
        compose.onNodeWithTag("value_SETS").assertTextEquals("3")
    }

    @Test
    fun `holding a button repeats on the compose clock`() {
        showInt(8, FieldRanges.SETS, ValueInput.WHOLE, "SETS")
        // One step on press, then one every 80 ms after a 400 ms hold (RepeatingIconButton).
        compose.onNodeWithContentDescription("Increase SETS").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        compose.waitForIdle()
        assertTrue("held value was $value", value in 14..20)
    }

    @Test
    fun `tapping the value opens the dialog and OK applies the clamped value`() {
        showInt(10, FieldRanges.PHASE, ValueInput.TIME, "PREPARE")
        compose.onNodeWithTag("value_PREPARE").assertTextEquals("00:10").performClick()
        compose.onNodeWithTag("edit_field").assertTextContains("00:10")
        compose.onNodeWithTag("edit_field").performTextReplacement("90:00")
        compose.onNodeWithTag("edit_ok").performClick()
        assertEquals(3599, value)
        compose.onNodeWithTag("value_PREPARE").assertTextEquals("59:59")
        compose.onNodeWithTag("edit_field").assertDoesNotExist()
    }

    @Test
    fun `bad input keeps OK disabled and the value unchanged`() {
        showInt(10, FieldRanges.PHASE, ValueInput.TIME, "PREPARE")
        compose.onNodeWithTag("value_PREPARE").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("1:60")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
        compose.onNodeWithText("Use m:ss or seconds").assertExists()
        assertEquals(10, value)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T12-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.common.StepperRowTest"`. Expected: compile FAIL, `Unresolved reference 'IntStepperField'`.

- [ ] **Step 3: Implement** — `ui/common/StepperRow.kt`:
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
import com.mitenko.hiitcounter.domain.StepRange
import com.mitenko.hiitcounter.domain.ValueFormat

/**
 * The shared stepper row (spec §8.1): the label on top, then 48 dp −/+ buttons (tap = one step,
 * hold = repeat) around a large value that opens the edit dialog when tapped, and an inline
 * error or hint beneath.
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
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium)
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
 * An integer field on a [StepRange] (spec §8.1). ± clamps at the hard edges and a dialog value
 * is clamped into the range. Cross-field rules are the screen's validation and are not clamped
 * here. [input] is [ValueInput.TIME] (shown as mm:ss) or [ValueInput.WHOLE]. [onUpdate] receives
 * a transform, so repeated steps always apply to the latest draft.
 */
@Composable
fun IntStepperField(
    label: String,
    value: Int,
    range: StepRange,
    input: ValueInput,
    onUpdate: ((Int) -> Int) -> Unit,
    error: String? = null,
    hint: String? = null,
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
    )
    if (editing) {
        EditValueDialog<Int>(
            title = label,
            initialText = text,
            input = input,
            parse = if (time) ValueFormat::parseSeconds else ValueFormat::parseInt,
            onConfirm = { parsed ->
                editing = false
                onUpdate { range.clamp(parsed) }
            },
            onDismiss = { editing = false },
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T12-2-GREEN`, ~3 min): full suite + count. Expected: **257 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): shared stepper row with clamped steps and tap-to-edit"`

### Subtask 12.3: PenaltyStepperField

**Files:** Replace `ui/common/StepperRow.kt`; append to `test/ui/common/StepperRowTest.kt`.

- [ ] **Step 1: Failing tests** — append to `StepperRowTest` (the property and helper go inside the class too):
```kotlin
    private var penalty by mutableStateOf(PenaltyDraft.of(19.5))

    private fun showPenalty(initial: PenaltyDraft) {
        penalty = initial
        compose.setContent {
            HiitTheme { Column { PenaltyStepperField("PENALTY", penalty, onUpdate = { f -> penalty = f(penalty) }) } }
        }
    }

    @Test
    fun `penalty steps in half hours and keeps an exact dialog value until the next press`() {
        showPenalty(PenaltyDraft.of(19.5))
        compose.onNodeWithContentDescription("Increase PENALTY").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("20")
        compose.onNodeWithTag("value_PENALTY").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("1,3")
        compose.onNodeWithTag("edit_ok").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("1.3")
        assertEquals(1.3, penalty.hours, 0.0)
        compose.onNodeWithContentDescription("Increase PENALTY").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("1.5")
    }

    @Test
    fun `penalty dialog values clamp to the hard range`() {
        showPenalty(PenaltyDraft.of(19.5))
        compose.onNodeWithTag("value_PENALTY").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("0.2")
        compose.onNodeWithTag("edit_ok").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("0.5")
        compose.onNodeWithTag("value_PENALTY").performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("5000")
        compose.onNodeWithTag("edit_ok").performClick()
        compose.onNodeWithTag("value_PENALTY").assertTextEquals("999.5")
    }
```

- [ ] **Step 2: Run red** — Run (label `T12-3-RED`, ~2 min): focused `StepperRowTest`. Expected: compile FAIL, `Unresolved reference 'PenaltyStepperField'`.

- [ ] **Step 3: Implement** — `ui/common/StepperRow.kt` (complete: the 12.2 file plus the penalty field and its imports):
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
 * The shared stepper row (spec §8.1): the label on top, then 48 dp −/+ buttons (tap = one step,
 * hold = repeat) around a large value that opens the edit dialog when tapped, and an inline
 * error or hint beneath.
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
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium)
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
 * An integer field on a [StepRange] (spec §8.1). ± clamps at the hard edges and a dialog value
 * is clamped into the range. Cross-field rules are the screen's validation and are not clamped
 * here. [input] is [ValueInput.TIME] (shown as mm:ss) or [ValueInput.WHOLE]. [onUpdate] receives
 * a transform, so repeated steps always apply to the latest draft.
 */
@Composable
fun IntStepperField(
    label: String,
    value: Int,
    range: StepRange,
    input: ValueInput,
    onUpdate: ((Int) -> Int) -> Unit,
    error: String? = null,
    hint: String? = null,
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
    )
    if (editing) {
        EditValueDialog<Int>(
            title = label,
            initialText = text,
            input = input,
            parse = if (time) ValueFormat::parseSeconds else ValueFormat::parseInt,
            onConfirm = { parsed ->
                editing = false
                onUpdate { range.clamp(parsed) }
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * The penalty rate (spec §8.1): ± steps of 0.5 on integer half-hours within 0.5 – 999.5. A dialog
 * value is clamped to that range and kept exactly, even if it isn't a multiple of 0.5; the next
 * ± press snaps it (see [PenaltyDraft]).
 */
@Composable
fun PenaltyStepperField(
    label: String,
    value: PenaltyDraft,
    onUpdate: ((PenaltyDraft) -> PenaltyDraft) -> Unit,
    error: String? = null,
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
    )
    if (editing) {
        EditValueDialog(
            title = label,
            initialText = text,
            input = ValueInput.DECIMAL,
            parse = ValueFormat::parseDecimal,
            onConfirm = { hours ->
                editing = false
                onUpdate { PenaltyDraft.of(hours.coerceIn(PenaltyDraft.MIN_HOURS, PenaltyDraft.MAX_HOURS)) }
            },
            onDismiss = { editing = false },
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T12-3-GREEN`, ~3 min): full suite + count. Expected: **259 tests**.

- [ ] **Step 5: Commit** — diff-review `StepperRow.kt`, then `git add -A && git commit -m "feat(ui): penalty stepper in half-hour steps with exact dialog values"`

**Task 12 gate:** Run (label `T12-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **259 tests**, no lint errors.

---

## Task 13: Per-entry settings sub-screens with steppers (§7.5, §8.1)

Each settings ViewModel becomes an `EntryScopedViewModel` and holds a **typed draft**. Saves call the entry's repository methods. A save that hits `EntryNotFound` calls `markMissing()`, so the screen pops to the list instead of crashing (§7.5).

Each Route gains `onEntryGone`. The **old v1 nav host** (still in place until 15.2) gets a one-line edit per screen so it keeps compiling. Those routes carry no `id`, so they can't resolve an entry at runtime until 15.2 wires the new routes. That's expected, and it's why no build is installed before 16.2.

**Interfaces produced:**
- `TimingSettingsViewModel(savedStateHandle, repo)` with `draft: StateFlow<TimingConfig?>`, `validation`, `update`, `save(onSaved)`. `TimingSettingsRoute(onBack, onEntryGone, vm)`. `TimingSettingsScreen(draft, validation, onBack, onChange, onSave)` (the signature is unchanged).
- `data class ProgressionDraft(startingTotal, floor, cap, holdAt, holdFor, windowHours, penalty: PenaltyDraft) { toConfig(); companion from(config) }`. `ProgressionSettingsViewModel(savedStateHandle, repo)` with `update`, `resetToDefaults`, `save`. `ProgressionSettingsRoute(onBack, onEntryGone, vm)`. `ProgressionSettingsScreen(draft, validation, onBack, onChange, onSave, onReset)`.
- `CurrentStateViewModel(savedStateHandle, repo, clock)` with `Draft(total: Int, best: Int, current: Int, lastCheckIn: Instant?)`, `update`, `save`, `resetProgress`. `CurrentStateRoute(onBack, onEntryGone, vm)`.
- `CuesSettingsViewModel(savedStateHandle, repo)` with `cues`, `setSound`, `setVibration`. `CuesSettingsRoute(onBack, onEntryGone, vm)`.

### Subtask 13.1: Timing (typed draft, steppers, recreation)

**Files:** Replace `ui/settings/TimingSettings.kt`, `test/ui/settings/TimingSettingsViewModelTest.kt`, `test/ui/settings/TimingSettingsScreenTest.kt`; create `test/ui/settings/TimingSettingsRestorationTest.kt`; edit `ui/navigation/HiitNavHost.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/settings/TimingSettingsViewModelTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimingSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `loads the entry's timing, edits and saves it`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, timing = TimingConfig(sets = 5)), testEntry(2)))
        val vm = TimingSettingsViewModel(handle, repo)
        assertEquals(TimingConfig(sets = 5), vm.draft.value)
        vm.update { it.copy(sets = 6) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(6, repo.find(1).timing.sets)
        assertEquals(TimingConfig(), repo.find(2).timing)
    }

    @Test
    fun `over two hours is not saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo)
        vm.update { it.copy(sets = 20, workSec = 3599) }
        assertTrue(Field.TOTAL_DURATION in vm.validation.value.errors)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertEquals(TimingConfig(), repo.find(1).timing)
    }

    @Test
    fun `a missing entry reports missing after loading`() = runTest {
        val vm = TimingSettingsViewModel(handle, FakeEntryRepository())
        runCurrent()
        assertTrue(vm.missing.value)
        assertNull(vm.draft.value)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = TimingSettingsViewModel(handle, repo)
        repo.delete(1)
        var saved = false
        vm.save { saved = true }
        runCurrent()
        assertFalse(saved)
        assertTrue(vm.missing.value)
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
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.mitenko.hiitcounter.ui.theme.HiitTheme
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
                TimingSettingsScreen(draft, SettingsValidator.timing(draft), onBack = {}, onChange = { draft = it(draft) }, onSave = {})
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
    fun `save disabled when invalid`() {
        val tooLong = TimingConfig(sets = 20, workSec = 3599)
        compose.setContent { HiitTheme { TimingSettingsScreen(tooLong, SettingsValidator.timing(tooLong), {}, {}, {}) } }
        compose.onNodeWithTag("save").assertIsNotEnabled()
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
        val vm = TimingSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), FakeEntryRepository())
        compose.setContent { HiitTheme { TimingSettingsRoute(onBack = {}, onEntryGone = { gone++ }, vm = vm) } }
        compose.waitForIdle()
        assertEquals(1, gone)
    }
}
```

`test/ui/settings/TimingSettingsRestorationTest.kt`. This covers the typed draft surviving recreation of the ViewModel's owner. The activity is recreated and the same ViewModel instance, holding the edited draft, comes back from its ViewModelStore. It also covers the dialog text surviving (rememberSaveable):
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
        initializer { TimingSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo) }
    }

    @Test
    fun `the typed draft survives recreation of the view model's owner`() {
        lateinit var before: TimingSettingsViewModel
        compose.setContent {
            HiitTheme {
                val vm: TimingSettingsViewModel = viewModel(factory = factory)
                before = vm
                TimingSettingsRoute(onBack = {}, onEntryGone = {}, vm = vm)
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
        assertEquals(8, repo.find(1).timing.sets) // unsaved: only the draft changed
    }

    @Test
    fun `an open edit dialog keeps its text across recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            HiitTheme { TimingSettingsRoute(onBack = {}, onEntryGone = {}, vm = viewModel(factory = factory)) }
        }
        compose.onNodeWithTag("value_PREPARE").performScrollTo().performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("1:3")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("edit_field").assertTextContains("1:3")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.Timing*"`. Expected: compile FAIL on the constructor argument (argument type mismatch / too many arguments for `TimingSettingsViewModel`) and on the unknown `onEntryGone` parameter. The exact K2 wording may differ.

- [ ] **Step 3: Implement** — `ui/settings/TimingSettings.kt` (complete; the private v1 `StepperRow` is gone, replaced by `ui/common`):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Typed draft (spec §8.1): one TimingConfig held in the ViewModel, so it survives configuration changes. */
@HiltViewModel
class TimingSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _draft = MutableStateFlow<TimingConfig?>(null)
    val draft: StateFlow<TimingConfig?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(SettingsValidator::timing) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    init {
        viewModelScope.launch { repo.entry(entryId).first()?.let { _draft.value = it.timing } }
    }

    fun update(transform: (TimingConfig) -> TimingConfig) {
        _draft.update { it?.let(transform) }
    }

    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (!SettingsValidator.timing(d).isValid) return
        viewModelScope.launch {
            try {
                repo.setTiming(entryId, d)
                onSaved()
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }
}

@Composable
fun TimingSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: TimingSettingsViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    draft?.let { TimingSettingsScreen(it, validation, onBack, onChange = vm::update, onSave = { vm.save(onBack) }) }
}

@Composable
fun TimingSettingsScreen(
    draft: TimingConfig,
    validation: ValidationResult,
    onBack: () -> Unit,
    onChange: ((TimingConfig) -> TimingConfig) -> Unit,
    onSave: () -> Unit,
) {
    val errors = validation.errors
    val totalError = errors[Field.TOTAL_DURATION]
    SettingsScaffold(
        title = stringResource(R.string.settings_timing),
        onBack = onBack,
        actions = {
            TextButton(onClick = onSave, enabled = validation.isValid, modifier = Modifier.testTag("save")) {
                Text(stringResource(R.string.save))
            }
        },
        bottomBar = {
            Surface(color = if (totalError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    stringResource(R.string.total_duration, TimerText.formatDuration(draft.totalDurationSec)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("total"),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
    ) {
        IntStepperField(
            stringResource(R.string.prepare), draft.prepareSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(prepareSec = f(it.prepareSec)) } }, error = errors[Field.PREPARE],
        )
        IntStepperField(
            stringResource(R.string.sets), draft.sets, FieldRanges.SETS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(sets = f(it.sets)) } }, error = errors[Field.SETS],
        )
        IntStepperField(
            stringResource(R.string.work), draft.workSec, FieldRanges.WORK, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(workSec = f(it.workSec)) } }, error = errors[Field.WORK],
        )
        IntStepperField(
            stringResource(R.string.rest), draft.restSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(restSec = f(it.restSec)) } }, error = errors[Field.REST],
        )
        IntStepperField(
            stringResource(R.string.cooldown), draft.cooldownSec, FieldRanges.PHASE, ValueInput.TIME,
            onUpdate = { f -> onChange { it.copy(cooldownSec = f(it.cooldownSec)) } }, error = errors[Field.COOLDOWN],
        )
        totalError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}
```

`ui/navigation/HiitNavHost.kt` (the old v1 host, replaced in 15.2). Replace:
```kotlin
            TimingSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() })
```
with:
```kotlin
            TimingSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popBackStack(Routes.HOME, inclusive = false) })
```

- [ ] **Step 4: Run green** — Run (label `T13-1-GREEN`, ~3 min): full suite + count. Expected: **265 tests**.

- [ ] **Step 5: Commit** — diff-review `TimingSettings.kt`, then `git add -A && git commit -m "feat(settings): per-entry timing with shared steppers and a typed draft"`

### Subtask 13.2: Progression (typed draft, half-hour penalty)

**Files:** Replace `ui/settings/ProgressionSettings.kt`, `test/ui/settings/ProgressionSettingsViewModelTest.kt`; create `test/ui/settings/ProgressionSettingsScreenTest.kt`; edit `ui/navigation/HiitNavHost.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/settings/ProgressionSettingsViewModelTest.kt` (complete):
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `save persists the entry's progression and resets the hold`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, counter = CounterState(total = 64, holdCount = 2))))
        val vm = ProgressionSettingsViewModel(handle, repo)
        vm.update { it.copy(holdAt = 66, holdFor = 3) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(ProgressionConfig(holdAt = 66, holdFor = 3), repo.find(1).progression)
        assertEquals(0, repo.find(1).counter.holdCount)
    }

    @Test
    fun `invalid drafts are not saved`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo)
        vm.update { it.copy(cap = 40) }
        assertTrue(Field.CAP in vm.validation.value.errors)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertEquals(ProgressionConfig(), repo.find(1).progression)
    }

    @Test
    fun `reset to defaults fills the draft`() = runTest {
        val vm = ProgressionSettingsViewModel(handle, FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(cap = 90)))))
        assertEquals(90, vm.draft.value?.cap)
        vm.resetToDefaults()
        assertEquals(72, vm.draft.value?.cap)
    }

    @Test
    fun `the penalty steps in half hours and a stored non-multiple is saved exactly`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1, progression = ProgressionConfig(penaltyHoursPerRep = 0.3))))
        val vm = ProgressionSettingsViewModel(handle, repo)
        assertEquals(0.3, vm.draft.value!!.penalty.hours, 0.0)
        vm.save { }
        assertEquals(0.3, repo.find(1).progression.penaltyHoursPerRep, 0.0)
        vm.update { it.copy(penalty = it.penalty.plus()) }
        vm.save { }
        assertEquals(0.5, repo.find(1).progression.penaltyHoursPerRep, 0.0)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = ProgressionSettingsViewModel(handle, repo)
        repo.delete(1)
        vm.save { }
        runCurrent()
        assertTrue(vm.missing.value)
    }
}
```

`test/ui/settings/ProgressionSettingsScreenTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProgressionSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `cross-field errors show inline and disable save, and the hold hint shows`() {
        var draft by mutableStateOf(ProgressionDraft.from(ProgressionConfig(startingTotal = 40)))
        compose.setContent {
            HiitTheme {
                ProgressionSettingsScreen(
                    draft, SettingsValidator.progression(draft.toConfig()),
                    onBack = {}, onChange = { draft = it(draft) }, onSave = {}, onReset = {},
                )
            }
        }
        compose.onNodeWithTag("support_Starting total").assertTextEquals("Must be ≥ floor")
        compose.onNodeWithTag("save").assertIsNotEnabled()
        draft = ProgressionDraft.from(ProgressionConfig(holdFor = 0))
        compose.onNodeWithTag("support_Hold at").performScrollTo().assertTextEquals("Hold disabled")
        compose.onNodeWithTag("save").assertIsEnabled()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.Progression*"`. Expected: compile FAIL on the constructor argument (argument type mismatch for `ProgressionSettingsViewModel`), plus unresolved `ProgressionSettingsScreen` / `toConfig`. The exact K2 wording may differ.

- [ ] **Step 3: Implement** — `ui/settings/ProgressionSettings.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.PenaltyDraft
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.PenaltyStepperField
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Typed draft (spec §8.1): one domain value per field, with the penalty in integer half-hours. */
data class ProgressionDraft(
    val startingTotal: Int,
    val floor: Int,
    val cap: Int,
    val holdAt: Int,
    val holdFor: Int,
    val windowHours: Int,
    val penalty: PenaltyDraft,
) {
    fun toConfig() = ProgressionConfig(startingTotal, floor, cap, holdAt, holdFor, windowHours, penalty.hours)

    companion object {
        fun from(c: ProgressionConfig) = ProgressionDraft(
            c.startingTotal, c.floor, c.cap, c.holdAt, c.holdFor, c.windowHours, PenaltyDraft.of(c.penaltyHoursPerRep),
        )
    }
}

@HiltViewModel
class ProgressionSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val _draft = MutableStateFlow<ProgressionDraft?>(null)
    val draft: StateFlow<ProgressionDraft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { d -> d?.let { SettingsValidator.progression(it.toConfig()) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    init {
        viewModelScope.launch { repo.entry(entryId).first()?.let { _draft.value = ProgressionDraft.from(it.progression) } }
    }

    fun update(transform: (ProgressionDraft) -> ProgressionDraft) {
        _draft.update { it?.let(transform) }
    }

    fun resetToDefaults() {
        _draft.value = ProgressionDraft.from(ProgressionConfig())
    }

    /** setProgression resets holdCount in the same UPDATE (spec §5.3). */
    fun save(onSaved: () -> Unit) {
        val config = _draft.value?.toConfig() ?: return
        if (!SettingsValidator.progression(config).isValid) return
        viewModelScope.launch {
            try {
                repo.setProgression(entryId, config)
                onSaved()
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }
}

@Composable
fun ProgressionSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: ProgressionSettingsViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    draft?.let {
        ProgressionSettingsScreen(
            it, validation, onBack, onChange = vm::update, onSave = { vm.save(onBack) }, onReset = vm::resetToDefaults,
        )
    }
}

@Composable
fun ProgressionSettingsScreen(
    draft: ProgressionDraft,
    validation: ValidationResult,
    onBack: () -> Unit,
    onChange: ((ProgressionDraft) -> ProgressionDraft) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    val errors = validation.errors
    SettingsScaffold(
        title = stringResource(R.string.settings_progression),
        onBack = onBack,
        actions = {
            TextButton(onClick = onSave, enabled = validation.isValid, modifier = Modifier.testTag("save")) {
                Text(stringResource(R.string.save))
            }
        },
    ) {
        IntStepperField(
            stringResource(R.string.starting_total), draft.startingTotal, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(startingTotal = f(it.startingTotal)) } }, error = errors[Field.STARTING_TOTAL],
        )
        IntStepperField(
            stringResource(R.string.floor), draft.floor, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(floor = f(it.floor)) } }, error = errors[Field.FLOOR],
        )
        IntStepperField(
            stringResource(R.string.cap), draft.cap, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(cap = f(it.cap)) } }, error = errors[Field.CAP],
        )
        IntStepperField(
            stringResource(R.string.hold_at), draft.holdAt, FieldRanges.REPS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(holdAt = f(it.holdAt)) } },
            error = errors[Field.HOLD_AT], hint = validation.hints[Field.HOLD_AT],
        )
        IntStepperField(
            stringResource(R.string.hold_for), draft.holdFor, FieldRanges.HOLD_FOR, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(holdFor = f(it.holdFor)) } }, error = errors[Field.HOLD_FOR],
        )
        IntStepperField(
            stringResource(R.string.window_hours), draft.windowHours, FieldRanges.WINDOW_HOURS, ValueInput.WHOLE,
            onUpdate = { f -> onChange { it.copy(windowHours = f(it.windowHours)) } }, error = errors[Field.WINDOW_HOURS],
        )
        PenaltyStepperField(
            stringResource(R.string.penalty_rate), draft.penalty,
            onUpdate = { f -> onChange { it.copy(penalty = f(it.penalty)) } }, error = errors[Field.PENALTY_RATE],
        )
        OutlinedButton(onClick = onReset, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.reset_defaults))
        }
    }
}
```

`ui/navigation/HiitNavHost.kt` (old v1 host). Replace:
```kotlin
            ProgressionSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() })
```
with:
```kotlin
            ProgressionSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popBackStack(Routes.HOME, inclusive = false) })
```

- [ ] **Step 4: Run green** — Run (label `T13-2-GREEN`, ~3 min): full suite + count. Expected: **268 tests**.

- [ ] **Step 5: Commit** — diff-review `ProgressionSettings.kt`, then `git add -A && git commit -m "feat(settings): per-entry progression with steppers and a half-hour penalty"`

### Subtask 13.3: Current State (typed draft, date text opens the pickers)

**Files:** Replace `ui/settings/CurrentStateSettings.kt`, `ui/common/SettingsComponents.kt` (drops the now-unused `NumberField`), `test/ui/settings/CurrentStateViewModelTest.kt`; edit `ui/navigation/HiitNavHost.kt`, `res/values/strings.xml`.

- [ ] **Step 1: Failing tests** — `test/ui/settings/CurrentStateViewModelTest.kt` (complete):
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val vm = CurrentStateViewModel(handle, repo, clock)
        assertEquals(CurrentStateViewModel.Draft(65, 24, 4, null), vm.draft.value)
    }

    @Test
    fun `save overwrites the entry's counter`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock)
        val last = clock.instant.minusSeconds(3600)
        vm.update { it.copy(total = 65, best = 24, current = 4, lastCheckIn = last) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(CounterState(65, 24, 4, last, 0), repo.find(1).counter)
    }

    @Test
    fun `invalid drafts are rejected`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock)
        vm.update { it.copy(best = 3, current = 4) }
        assertTrue(Field.BEST_STREAK in vm.validation.value.errors)
        vm.update { it.copy(best = 4, lastCheckIn = clock.instant.plusSeconds(60)) }
        assertTrue(Field.LAST_CHECK_IN in vm.validation.value.errors)
        vm.update { it.copy(total = 0, lastCheckIn = null) }
        assertTrue(Field.TOTAL in vm.validation.value.errors)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertEquals(CounterState(total = 48), repo.find(1).counter)
    }

    @Test
    fun `reset progress resets this entry only`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                testEntry(1, counter = CounterState(65, 24, 4, clock.instant, 1)),
                testEntry(2, counter = CounterState(total = 50, bestStreak = 3)),
            ),
        )
        val vm = CurrentStateViewModel(handle, repo, clock)
        var done = false
        vm.resetProgress { done = true }
        assertTrue(done)
        assertEquals(CounterState(total = 48), repo.find(1).counter)
        assertEquals(CounterState(total = 50, bestStreak = 3), repo.find(2).counter)
    }

    @Test
    fun `a save racing a delete reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CurrentStateViewModel(handle, repo, clock)
        repo.delete(1)
        vm.save { }
        runCurrent()
        assertTrue(vm.missing.value)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-3-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.CurrentStateViewModelTest"`. Expected: compile FAIL, a constructor mismatch for `CurrentStateViewModel`, and `Draft` expecting `String` arguments.

- [ ] **Step 3: Implement** — `ui/settings/CurrentStateSettings.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.FieldRanges
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.IntStepperField
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.common.ValueInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class CurrentStateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val clock: Clock,
) : EntryScopedViewModel(savedStateHandle, repo) {
    /** Typed draft (spec §8.1). */
    data class Draft(val total: Int, val best: Int, val current: Int, val lastCheckIn: Instant?)

    /** The entry's own progression, used only for the "outside floor–cap" hint. */
    private val config = MutableStateFlow(ProgressionConfig())
    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = combine(_draft, config) { d, c -> d?.let { validate(it, c) } ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    val zone: ZoneId get() = clock.zone()

    fun now(): Instant = clock.now()

    init {
        viewModelScope.launch {
            repo.entry(entryId).first()?.let { e ->
                config.value = e.progression
                _draft.value = Draft(e.counter.total, e.counter.bestStreak, e.counter.currentStreak, e.counter.lastCheckIn)
            }
        }
    }

    fun update(transform: (Draft) -> Draft) {
        _draft.update { it?.let(transform) }
    }

    /** Overwrites the counter; the same UPDATE resets holdCount (spec §5.3). */
    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (!validate(d, config.value).isValid) return
        viewModelScope.launch {
            try {
                repo.overwriteCounter(entryId, d.total, d.best, d.current, d.lastCheckIn)
                onSaved()
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    fun resetProgress(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.resetProgress(entryId)
                onDone()
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    private fun validate(d: Draft, c: ProgressionConfig): ValidationResult =
        SettingsValidator.currentState(d.total, d.best, d.current, d.lastCheckIn, clock.now(), c)
}

@Composable
fun CurrentStateRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: CurrentStateViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    val d = draft ?: return
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val lastLabel = stringResource(R.string.last_check_in_field)

    SettingsScaffold(
        title = stringResource(R.string.settings_current_state),
        onBack = onBack,
        actions = {
            TextButton(onClick = { vm.save(onBack) }, enabled = validation.isValid, modifier = Modifier.testTag("save")) {
                Text(stringResource(R.string.save))
            }
        },
    ) {
        IntStepperField(
            stringResource(R.string.current_total), d.total, FieldRanges.TOTAL, ValueInput.WHOLE,
            onUpdate = { f -> vm.update { it.copy(total = f(it.total)) } },
            error = validation.errors[Field.TOTAL], hint = validation.hints[Field.TOTAL],
        )
        IntStepperField(
            stringResource(R.string.best_streak_field), d.best, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> vm.update { it.copy(best = f(it.best)) } }, error = validation.errors[Field.BEST_STREAK],
        )
        IntStepperField(
            stringResource(R.string.current_streak_field), d.current, FieldRanges.STREAK, ValueInput.WHOLE,
            onUpdate = { f -> vm.update { it.copy(current = f(it.current)) } }, error = validation.errors[Field.CURRENT_STREAK],
        )
        Text(lastLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tapping the date text opens the date and time pickers (spec §8.1).
            Text(
                d.lastCheckIn?.let { DateFormats.dateTime(it, vm.zone) } ?: stringResource(R.string.none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.edit_value, lastLabel)) { picking = true }
                    .padding(vertical = 12.dp)
                    .testTag("last_check_in"),
            )
            TextButton(onClick = { vm.update { it.copy(lastCheckIn = null) } }, enabled = d.lastCheckIn != null) {
                Text(stringResource(R.string.clear))
            }
        }
        validation.errors[Field.LAST_CHECK_IN]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = { confirmReset = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.padding(top = 24.dp),
        ) { Text(stringResource(R.string.reset_progress)) }
    }

    if (picking) {
        DateTimePickerDialog(
            initial = d.lastCheckIn ?: vm.now(),
            zone = vm.zone,
            onPicked = { t ->
                picking = false
                vm.update { it.copy(lastCheckIn = t) }
            },
            onDismiss = { picking = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_progress_title)) },
            text = { Text(stringResource(R.string.reset_progress_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetProgress(onBack)
                }) { Text(stringResource(R.string.reset)) }
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

`ui/common/SettingsComponents.kt` (complete; `NumberField` has no users left):
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
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
```

`ui/navigation/HiitNavHost.kt` (old v1 host). Replace:
```kotlin
            CurrentStateRoute(onBack = dropUnlessResumed { nav.popBackStack() })
```
with:
```kotlin
            CurrentStateRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popBackStack(Routes.HOME, inclusive = false) })
```

`res/values/strings.xml`: delete the now-unused line (the old "Set" button is replaced by tapping the date):
```xml
    <string name="set">Set</string>
```

- [ ] **Step 4: Run green** — Run (label `T13-3-GREEN`, ~3 min): full suite + count. Expected: **269 tests**.

- [ ] **Step 5: Commit** — diff-review `CurrentStateSettings.kt` and `SettingsComponents.kt` (`RepeatingIconButton` must be unchanged), then `git add -A && git commit -m "feat(settings): per-entry current state with steppers and tap-to-pick date"`

### Subtask 13.4: Cues

**Files:** Replace `ui/settings/CuesSettings.kt`, `test/ui/settings/CuesSettingsViewModelTest.kt`; edit `ui/navigation/HiitNavHost.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/settings/CuesSettingsViewModelTest.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CuesSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val handle = SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L))

    @Test
    fun `toggles persist immediately for this entry`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1), testEntry(2)))
        val vm = CuesSettingsViewModel(handle, repo)
        vm.setSound(false)
        assertEquals(CueConfig(sound = false, vibration = true), repo.find(1).cues)
        vm.setVibration(false)
        assertEquals(CueConfig(sound = false, vibration = false), repo.find(1).cues)
        assertEquals(CueConfig(), repo.find(2).cues)
    }

    @Test
    fun `a toggle on a deleted entry reports missing instead of crashing`() = runTest {
        val repo = FakeEntryRepository(listOf(testEntry(1)))
        val vm = CuesSettingsViewModel(handle, repo)
        repo.delete(1)
        vm.setSound(false)
        runCurrent()
        assertTrue(vm.missing.value)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-4-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.CuesSettingsViewModelTest"`. Expected: compile FAIL, a constructor mismatch for `CuesSettingsViewModel`.

- [ ] **Step 3: Implement** — `ui/settings/CuesSettings.kt` (complete):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuesSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
) : EntryScopedViewModel(savedStateHandle, repo) {
    val cues: StateFlow<CueConfig> = repo.entry(entryId).filterNotNull().map { it.cues }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CueConfig())

    fun setSound(on: Boolean) = edit { it.copy(sound = on) }

    fun setVibration(on: Boolean) = edit { it.copy(vibration = on) }

    /** Cues save immediately (as in v1). A toggle racing a delete pops to the list (spec §7.5). */
    private fun edit(transform: (CueConfig) -> CueConfig) {
        viewModelScope.launch {
            try {
                val current = repo.entry(entryId).first()?.cues ?: return@launch markMissing()
                repo.setCues(entryId, transform(current))
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }
}

@Composable
fun CuesSettingsRoute(onBack: () -> Unit, onEntryGone: () -> Unit, vm: CuesSettingsViewModel = hiltViewModel()) {
    val cues by vm.cues.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(missing) { if (missing) onEntryGone() }
    SettingsScaffold(title = stringResource(R.string.settings_cues), onBack = onBack) {
        SwitchRow(R.string.sound, cues.sound, vm::setSound)
        SwitchRow(R.string.vibration, cues.vibration, vm::setVibration)
    }
}

/** Restyled with the stepper rows' spacing (spec §8.1). */
@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
```

`ui/navigation/HiitNavHost.kt` (old v1 host). Replace:
```kotlin
            CuesSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() })
```
with:
```kotlin
            CuesSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popBackStack(Routes.HOME, inclusive = false) })
```

- [ ] **Step 4: Run green** — Run (label `T13-4-GREEN`, ~3 min): full suite + count. Expected: **270 tests**.

- [ ] **Step 5: Commit** — diff-review `CuesSettings.kt`, then `git add -A && git commit -m "feat(settings): per-entry cues"`

**Task 13 gate:** Run (label `T13-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **270 tests**, no lint errors.

---

## Task 14: Entry settings — list, rename, duplicate, delete (§7.1, §7.5)

**Interfaces produced:**
- `enum class SettingsPage(@StringRes label) { TIMING, PROGRESSION, CURRENT, CUES }`
- `data class EntrySettingsUiState(name = "", busy = false, error: String? = null)`
- `EntrySettingsViewModel(savedStateHandle, repo, controller: TimerController)` with `uiState`, `rename(name)`, `duplicate(onCreated: (Long) -> Unit)`, `delete(onDeleted: () -> Unit)` and `BUSY_HINT`
- `@Composable EntrySettingsRoute(onBack, onOpen: (SettingsPage) -> Unit, onDuplicated: (Long) -> Unit, onDeleted, onEntryGone, vm)` and `@Composable EntrySettingsScreen(state, onBack, onOpen, onRename: (String) -> Unit, onDuplicate, onDelete)`. Test tags: `page_<PAGE>`, `rename`, `duplicate`, `delete`, `confirm_delete`, `busy_hint`.

### Subtask 14.1: EntrySettingsViewModel (busy rule, EntryBusy, rename during a run)

**Files:** Create `ui/settings/EntrySettings.kt`; test `test/ui/settings/EntrySettingsViewModelTest.kt`.

- [ ] **Step 1: Failing tests** — `test/ui/settings/EntrySettingsViewModelTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.lifecycle.SavedStateHandle
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntrySettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeEntryRepository(listOf(testEntry(1, "Burpees"), testEntry(2, "Lunges")))
    private val snapshot = WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig())

    private class Harness(val vm: EntrySettingsViewModel, val controller: TimerController)

    private fun TestScope.harness(repository: FakeEntryRepository = repo): Harness {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = EntrySettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repository, controller)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return Harness(vm, controller)
    }

    @Test
    fun `ui state shows the name and follows the controller's busy rule`() = runTest {
        val h = harness()
        assertEquals(EntrySettingsUiState(name = "Burpees", busy = false), h.vm.uiState.value)
        h.controller.prepare(snapshot)
        runCurrent()
        assertTrue(h.vm.uiState.value.busy)
        h.controller.cancelPrepare()
        runCurrent()
        assertFalse(h.vm.uiState.value.busy)
    }

    @Test
    fun `rename updates the entry`() = runTest {
        val h = harness()
        h.vm.rename("  Kettlebell Lunges ")
        runCurrent()
        assertEquals("Kettlebell Lunges", repo.find(1).name)
        assertEquals("Kettlebell Lunges", h.vm.uiState.value.name)
    }

    @Test
    fun `a rename during an active run leaves the frozen snapshot name`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot)
        h.controller.onServiceStarted()
        h.controller.start(List(8) { 6 })
        runCurrent()
        h.vm.rename("Kettlebell Lunges")
        runCurrent()
        assertEquals("Kettlebell Lunges", repo.find(1).name)
        assertEquals("Burpees", h.controller.snapshot!!.entryName)
    }

    @Test
    fun `duplicate reports the copy's id`() = runTest {
        val h = harness()
        var copy: Long? = null
        h.vm.duplicate { copy = it }
        runCurrent()
        assertEquals("Burpees copy", repo.find(copy!!).name)
    }

    @Test
    fun `deleting a busy entry fails with EntryBusy and deletes nothing`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot)
        runCurrent()
        var deleted = false
        h.vm.delete { deleted = true }
        runCurrent()
        assertFalse(deleted)
        assertEquals(0, repo.deleteCalls)
        assertEquals(EntrySettingsViewModel.BUSY_HINT, h.vm.uiState.value.error)
    }

    @Test
    fun `deleting an idle entry removes it and reports back`() = runTest {
        val h = harness()
        h.controller.prepare(snapshot.copy(entryId = 2L)) // another entry's run doesn't block this delete
        var deleted = false
        h.vm.delete { deleted = true }
        runCurrent()
        assertTrue(deleted)
        assertEquals(listOf(2L), repo.state.value.map { it.id })
        assertTrue(h.vm.missing.value)
    }

    @Test
    fun `a missing entry reports missing after loading`() = runTest {
        val notReady = FakeEntryRepository(emptyList(), ready = false)
        val h = harness(notReady)
        assertFalse(h.vm.missing.value)
        notReady.readiness.complete(Unit)
        runCurrent()
        assertTrue(h.vm.missing.value)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T14-1-RED`, ~1 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.EntrySettingsViewModelTest"`. Expected: compile FAIL, `Unresolved reference 'EntrySettingsViewModel'`.

- [ ] **Step 3: Implement** — `ui/settings/EntrySettings.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.EntryBusy
import com.mitenko.hiitcounter.domain.model.EntryNotFound
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The four per-entry settings pages (spec §7.5). */
enum class SettingsPage(@StringRes val label: Int) {
    TIMING(R.string.settings_timing),
    PROGRESSION(R.string.settings_progression),
    CURRENT(R.string.settings_current_state),
    CUES(R.string.settings_cues),
}

data class EntrySettingsUiState(val name: String = "", val busy: Boolean = false, val error: String? = null)

@HiltViewModel
class EntrySettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val controller: TimerController,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val error = MutableStateFlow<String?>(null)

    /** busy is re-read on every run-status change; the snapshot is set before PREPARING is emitted. */
    val uiState: StateFlow<EntrySettingsUiState> =
        combine(repo.entry(entryId).filterNotNull(), controller.status, error) { entry, _, err ->
            EntrySettingsUiState(name = entry.name, busy = controller.isBusy(entryId), error = err)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntrySettingsUiState())

    /** Allowed while busy: it only affects future runs, because the snapshot is frozen (spec §7.1). */
    fun rename(name: String) {
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

    fun duplicate(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onCreated(repo.duplicate(entryId))
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    /**
     * Spec §7.1: the busy check reads the singleton controller at the moment of the call, so a
     * stale screen can't bypass it. A busy entry fails with [EntryBusy] and nothing is deleted.
     */
    fun delete(onDeleted: () -> Unit) {
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
```

- [ ] **Step 4: Run green** — Run (label `T14-1-GREEN`, ~3 min): full suite + count. Expected: **277 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(settings): entry settings state with the controller busy rule"`

### Subtask 14.2: EntrySettingsScreen (pages, rename, duplicate, delete confirmation)

**Files:** Replace `ui/settings/EntrySettings.kt`; test `test/ui/settings/EntrySettingsScreenTest.kt` (it ports `SettingsListScreenTest`, which 15.2 deletes).

- [ ] **Step 1: Failing tests** — `test/ui/settings/EntrySettingsScreenTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeEntryRepository
import com.mitenko.hiitcounter.testutil.testEntry
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.entries.EntryListRoute
import com.mitenko.hiitcounter.ui.entries.EntryListViewModel
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EntrySettingsScreenTest {
    @get:Rule val compose = createComposeRule()

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

    @Test
    fun `pages open their settings`() {
        val opened = mutableListOf<SettingsPage>()
        show(onOpen = { opened += it })
        SettingsPage.entries.forEach { compose.onNodeWithTag("page_${it.name}").performScrollTo().performClick() }
        assertEquals(SettingsPage.entries.toList(), opened)
    }

    @Test
    fun `rename opens a prefilled name dialog`() {
        var renamed: String? = null
        show(onRename = { renamed = it })
        compose.onNodeWithTag("rename").performScrollTo().performClick()
        compose.onNodeWithTag("name_field").assertTextContains("Burpees")
        compose.onNodeWithTag("name_field").performTextReplacement("Kettlebell Lunges")
        compose.onNodeWithTag("name_ok").performClick()
        assertEquals("Kettlebell Lunges", renamed)
    }

    @Test
    fun `delete asks for confirmation with the name and the warning`() {
        var deletes = 0
        show(onDelete = { deletes++ })
        compose.onNodeWithTag("delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete Burpees?").assertExists()
        compose.onNodeWithText("Its rep total, streaks and settings will be lost.").assertExists()
        compose.onNodeWithTag("confirm_delete").performClick()
        assertEquals(1, deletes)
    }

    @Test
    fun `delete is disabled with its hint while the entry is busy`() {
        show(EntrySettingsUiState(name = "Burpees", busy = true))
        compose.onNodeWithTag("delete").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("busy_hint").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stop the workout first").assertExists()
    }

    @Test
    fun `duplicate shows the suffixed copy in the list`() {
        val repo = FakeEntryRepository(listOf(testEntry(1, "Burpees")))
        val controller = TimerController(MainScope()) { 0L }
        val settingsVm = EntrySettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo, controller)
        val listVm = EntryListViewModel(repo, FakeClock())
        var copied by mutableStateOf(false)
        compose.setContent {
            HiitTheme {
                if (copied) {
                    EntryListRoute(onOpenEntry = {}, onCreated = {}, vm = listVm)
                } else {
                    EntrySettingsRoute(
                        onBack = {}, onOpen = {}, onDuplicated = { copied = true }, onDeleted = {}, onEntryGone = {},
                        vm = settingsVm,
                    )
                }
            }
        }
        compose.onNodeWithTag("duplicate").performScrollTo().performClick()
        compose.onNodeWithText("Burpees copy").assertIsDisplayed()
        compose.onNodeWithText("Burpees").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T14-2-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.EntrySettingsScreenTest"`. Expected: compile FAIL, `Unresolved reference 'EntrySettingsScreen'` / `'EntrySettingsRoute'`.

- [ ] **Step 3: Implement** — `ui/settings/EntrySettings.kt` (complete: the 14.1 ViewModel plus the Route and Screen):
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.mitenko.hiitcounter.ui.common.EntryScopedViewModel
import com.mitenko.hiitcounter.ui.common.NameDialog
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The four per-entry settings pages (spec §7.5). */
enum class SettingsPage(@StringRes val label: Int) {
    TIMING(R.string.settings_timing),
    PROGRESSION(R.string.settings_progression),
    CURRENT(R.string.settings_current_state),
    CUES(R.string.settings_cues),
}

data class EntrySettingsUiState(val name: String = "", val busy: Boolean = false, val error: String? = null)

@HiltViewModel
class EntrySettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: EntryRepository,
    private val controller: TimerController,
) : EntryScopedViewModel(savedStateHandle, repo) {
    private val error = MutableStateFlow<String?>(null)

    /** busy is re-read on every run-status change; the snapshot is set before PREPARING is emitted. */
    val uiState: StateFlow<EntrySettingsUiState> =
        combine(repo.entry(entryId).filterNotNull(), controller.status, error) { entry, _, err ->
            EntrySettingsUiState(name = entry.name, busy = controller.isBusy(entryId), error = err)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntrySettingsUiState())

    /** Allowed while busy: it only affects future runs, because the snapshot is frozen (spec §7.1). */
    fun rename(name: String) {
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

    fun duplicate(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onCreated(repo.duplicate(entryId))
            } catch (e: EntryNotFound) {
                markMissing()
            }
        }
    }

    /**
     * Spec §7.1: the busy check reads the singleton controller at the moment of the call, so a
     * stale screen can't bypass it. A busy entry fails with [EntryBusy] and nothing is deleted.
     */
    fun delete(onDeleted: () -> Unit) {
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
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(title = state.name, onBack = onBack) {
        SettingsPage.entries.forEach { page ->
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

- [ ] **Step 4: Run green** — Run (label `T14-2-GREEN`, ~3 min): full suite + count. Expected: **282 tests**.

- [ ] **Step 5: Commit** — diff-review `EntrySettings.kt`, then `git add -A && git commit -m "feat(settings): entry settings with rename, duplicate and a guarded delete"`

**Task 14 gate:** Run (label `T14-GATE`, ~4 min): `./gradlew assembleDebug testDebugUnitTest lintDebug`. Expected: **282 tests**, no lint errors.

---

## Task 15: Navigation swap and v1 removal (§7.2, §4, §5.3)

**Interfaces produced:**
- `Routes.ENTRIES`, `ENTRY`, `ENTRY_SETTINGS`, `ENTRY_TIMING`, `ENTRY_PROGRESSION`, `ENTRY_CURRENT`, `ENTRY_CUES`, `TIMER`, plus `Routes.entry(id)`, `entrySettings(id)` and `settingsPage(id, page)`
- `NavController.popToEntries()`, `NavController.openEntryOverList(id: Long)` (`popUpTo(entries)`), `NavController.exitTimer(entryId: Long?)`, and `@Composable fun <T> dropUnlessResumedWith(block: (T) -> Unit): (T) -> Unit`
- `HiitApp` injects `V1Migrator` and calls `start()` in `onCreate`

### Subtask 15.1: Routes and navigation actions

**Files:** Replace `ui/navigation/Routes.kt`; create `ui/navigation/NavActions.kt`; test `test/ui/navigation/RoutesTest.kt` (plain JVM), `test/ui/navigation/NavActionsTest.kt` (Robolectric). The nav tests cover the timer exit (both cases) and `popUpTo(entries)` after create and after duplicate.

The v1 constants stay in `Routes` until 15.2, so the old nav host keeps compiling.

`exitTimer` checks the **topmost** `entry/{id}` back-stack entry. Only one `entry/{id}` can be on the back stack at a time:
- entry screens are only opened from the list;
- create and duplicate navigate with `popUpTo(entries)`.

So the topmost match is the search §7.2 asks for. It uses only public NavController API.

- [ ] **Step 1: Failing tests** — `test/ui/navigation/RoutesTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import com.mitenko.hiitcounter.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun `routes follow the spec`() {
        assertEquals("entry/{id}", Routes.ENTRY)
        assertEquals("entry/{id}/settings/timing", Routes.ENTRY_TIMING)
        assertEquals("entry/7", Routes.entry(7))
        assertEquals("entry/7/settings", Routes.entrySettings(7))
        assertEquals(
            listOf("entry/7/settings/timing", "entry/7/settings/progression", "entry/7/settings/current", "entry/7/settings/cues"),
            SettingsPage.entries.map { Routes.settingsPage(7, it) },
        )
    }
}
```

`test/ui/navigation/NavActionsTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NavActionsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun graph() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.ENTRIES) {
                val idArg = listOf(navArgument(ENTRY_ID_ARG) { type = NavType.LongType })
                composable(Routes.ENTRIES) { }
                composable(Routes.ENTRY, arguments = idArg) { }
                composable(Routes.ENTRY_SETTINGS, arguments = idArg) { }
                composable(Routes.TIMER) { }
            }
        }
    }

    @Test
    fun `exiting the timer pops to the run's entry`() {
        graph()
        compose.runOnIdle {
            nav.navigate(Routes.entry(1))
            nav.navigate(Routes.entrySettings(1))
            nav.navigate(Routes.TIMER)
            nav.exitTimer(1L)
        }
        compose.runOnIdle {
            assertEquals(Routes.ENTRY, nav.currentDestination?.route)
            assertEquals(1L, nav.currentBackStackEntry?.arguments?.getLong(ENTRY_ID_ARG))
        }
    }

    @Test
    fun `exiting the timer pops to the list when the run's entry isn't on the back stack`() {
        graph()
        compose.runOnIdle {
            nav.navigate(Routes.entry(2))
            nav.navigate(Routes.TIMER)
            nav.exitTimer(1L)
        }
        compose.runOnIdle { assertEquals(Routes.ENTRIES, nav.currentDestination?.route) }
    }

    @Test
    fun `a created entry opens with only the list beneath it`() {
        graph()
        compose.runOnIdle { nav.openEntryOverList(5) }
        compose.runOnIdle {
            assertEquals(5L, nav.currentBackStackEntry?.arguments?.getLong(ENTRY_ID_ARG))
            nav.popBackStack()
        }
        compose.runOnIdle { assertEquals(Routes.ENTRIES, nav.currentDestination?.route) }
    }

    @Test
    fun `a duplicate opens with only the list beneath it`() {
        graph()
        compose.runOnIdle {
            nav.navigate(Routes.entry(1))
            nav.navigate(Routes.entrySettings(1))
            nav.openEntryOverList(2)
        }
        compose.runOnIdle {
            assertEquals(Routes.ENTRY, nav.currentDestination?.route)
            assertEquals(2L, nav.currentBackStackEntry?.arguments?.getLong(ENTRY_ID_ARG))
            nav.popBackStack()
        }
        compose.runOnIdle { assertEquals(Routes.ENTRIES, nav.currentDestination?.route) }
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T15-1-RED`, ~2 min): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.navigation.*"`. Expected: compile FAIL, `Unresolved reference 'ENTRIES'` / `'exitTimer'`.

- [ ] **Step 3: Implement** — `ui/navigation/Routes.kt` (complete, transitional):
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.settings.SettingsPage

object Routes {
    // v1 routes — removed in 15.2 together with the old nav host.
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SETTINGS_TIMING = "settings/timing"
    const val SETTINGS_PROGRESSION = "settings/progression"
    const val SETTINGS_CURRENT = "settings/current"
    const val SETTINGS_CUES = "settings/cues"

    // Spec §7.2.
    const val ENTRIES = "entries"
    const val ENTRY = "entry/{$ENTRY_ID_ARG}"
    const val ENTRY_SETTINGS = "entry/{$ENTRY_ID_ARG}/settings"
    const val ENTRY_TIMING = "entry/{$ENTRY_ID_ARG}/settings/timing"
    const val ENTRY_PROGRESSION = "entry/{$ENTRY_ID_ARG}/settings/progression"
    const val ENTRY_CURRENT = "entry/{$ENTRY_ID_ARG}/settings/current"
    const val ENTRY_CUES = "entry/{$ENTRY_ID_ARG}/settings/cues"
    const val TIMER = "timer"

    fun entry(id: Long): String = "entry/$id"

    fun entrySettings(id: Long): String = "entry/$id/settings"

    fun settingsPage(id: Long, page: SettingsPage): String = entrySettings(id) + when (page) {
        SettingsPage.TIMING -> "/timing"
        SettingsPage.PROGRESSION -> "/progression"
        SettingsPage.CURRENT -> "/current"
        SettingsPage.CUES -> "/cues"
    }
}
```

`ui/navigation/NavActions.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG

/** Pops everything above the entry list (the start destination). */
fun NavController.popToEntries() {
    popBackStack(Routes.ENTRIES, inclusive = false)
}

/** After create (spec §7.3) and duplicate (§7.5): open the entry with only the list beneath it. */
fun NavController.openEntryOverList(id: Long) {
    navigate(Routes.entry(id)) {
        popUpTo(Routes.ENTRIES)
        launchSingleTop = true
    }
}

/**
 * Leaving the timer (spec §7.2): pop to the `entry/{id}` whose id is the run's [entryId]
 * (TimerController.lastEntryId). If that entry isn't on the back stack (e.g. after the activity
 * was recreated), pop to the list. At most one `entry/{id}` is ever on the stack, so the topmost
 * one is the only candidate.
 */
fun NavController.exitTimer(entryId: Long?) {
    val topEntry = runCatching { getBackStackEntry(Routes.ENTRY) }.getOrNull()
    if (entryId != null && topEntry != null && topEntry.arguments?.getLong(ENTRY_ID_ARG) == entryId) {
        popBackStack(topEntry.destination.id, inclusive = false)
    } else {
        popToEntries()
    }
}

/**
 * The double-tap guard for callbacks that carry an argument: dropUnlessResumed
 * (lifecycle-runtime-compose 2.9.4) only has a zero-arg overload.
 */
@Composable
fun <T> dropUnlessResumedWith(block: (T) -> Unit): (T) -> Unit {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(block)
    return remember(owner) {
        { value: T -> if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) latest(value) }
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T15-1-GREEN`, ~3 min): full suite + count. Expected: **287 tests**.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(nav): entry routes, timer exit to the run's entry, argument-aware tap guard"`

### Subtask 15.2: New nav host, migrator start, v1 removal

**Files:**
- Replace `ui/navigation/HiitNavHost.kt`, `ui/navigation/Routes.kt` and `HiitApp.kt`.
- **Delete (main):** `data/CounterRepository.kt`, `data/SettingsRepository.kt`, `data/PreferenceKeys.kt`, `di/DataModule.kt` (the `@SettingsStore` and `@CounterStore` providers), `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/settings/SettingsListScreen.kt`.
- **Delete (test):** `data/DataStoreCounterRepositoryTest.kt` (9 tests), `data/DataStoreSettingsRepositoryTest.kt` (5), `ui/home/HomeViewModelTest.kt` (9, ported in 11.1–11.2), `ui/home/HomeScreenTest.kt` (3, ported in 11.3), `ui/settings/SettingsListScreenTest.kt` (1, ported in 14.2), `testutil/FakeCounterRepository.kt`, `testutil/FakeSettingsRepository.kt`. That removes **27** tests.

This is a wiring subtask with no new test. The navigation behaviour is covered by `NavActionsTest`, and the screens by their own tests.

- [ ] **Step 1: Delete the v1 layer**
```bash
git rm app/src/main/kotlin/com/mitenko/hiitcounter/data/CounterRepository.kt \
       app/src/main/kotlin/com/mitenko/hiitcounter/data/SettingsRepository.kt \
       app/src/main/kotlin/com/mitenko/hiitcounter/data/PreferenceKeys.kt \
       app/src/main/kotlin/com/mitenko/hiitcounter/di/DataModule.kt \
       app/src/main/kotlin/com/mitenko/hiitcounter/ui/home/HomeScreen.kt \
       app/src/main/kotlin/com/mitenko/hiitcounter/ui/home/HomeViewModel.kt \
       app/src/main/kotlin/com/mitenko/hiitcounter/ui/settings/SettingsListScreen.kt \
       app/src/test/kotlin/com/mitenko/hiitcounter/data/DataStoreCounterRepositoryTest.kt \
       app/src/test/kotlin/com/mitenko/hiitcounter/data/DataStoreSettingsRepositoryTest.kt \
       app/src/test/kotlin/com/mitenko/hiitcounter/ui/home/HomeViewModelTest.kt \
       app/src/test/kotlin/com/mitenko/hiitcounter/ui/home/HomeScreenTest.kt \
       app/src/test/kotlin/com/mitenko/hiitcounter/ui/settings/SettingsListScreenTest.kt \
       app/src/test/kotlin/com/mitenko/hiitcounter/testutil/FakeCounterRepository.kt \
       app/src/test/kotlin/com/mitenko/hiitcounter/testutil/FakeSettingsRepository.kt
```

- [ ] **Step 2: Routes** — `ui/navigation/Routes.kt` (complete, final):
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.settings.SettingsPage

/** Spec §7.2. */
object Routes {
    const val ENTRIES = "entries"
    const val ENTRY = "entry/{$ENTRY_ID_ARG}"
    const val ENTRY_SETTINGS = "entry/{$ENTRY_ID_ARG}/settings"
    const val ENTRY_TIMING = "entry/{$ENTRY_ID_ARG}/settings/timing"
    const val ENTRY_PROGRESSION = "entry/{$ENTRY_ID_ARG}/settings/progression"
    const val ENTRY_CURRENT = "entry/{$ENTRY_ID_ARG}/settings/current"
    const val ENTRY_CUES = "entry/{$ENTRY_ID_ARG}/settings/cues"
    const val TIMER = "timer"

    fun entry(id: Long): String = "entry/$id"

    fun entrySettings(id: Long): String = "entry/$id/settings"

    fun settingsPage(id: Long, page: SettingsPage): String = entrySettings(id) + when (page) {
        SettingsPage.TIMING -> "/timing"
        SettingsPage.PROGRESSION -> "/progression"
        SettingsPage.CURRENT -> "/current"
        SettingsPage.CUES -> "/cues"
    }
}
```

- [ ] **Step 3: Nav host** — `ui/navigation/HiitNavHost.kt` (complete). `idArg` is declared inside the builder, so the builder lambda captures only stable values and the graph isn't rebuilt on recomposition:
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
import com.mitenko.hiitcounter.ui.settings.CuesSettingsRoute
import com.mitenko.hiitcounter.ui.settings.CurrentStateRoute
import com.mitenko.hiitcounter.ui.settings.EntrySettingsRoute
import com.mitenko.hiitcounter.ui.settings.ProgressionSettingsRoute
import com.mitenko.hiitcounter.ui.settings.SettingsPage
import com.mitenko.hiitcounter.ui.settings.TimingSettingsRoute
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
        composable(Routes.ENTRY_TIMING, arguments = idArg) {
            TimingSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popToEntries() })
        }
        composable(Routes.ENTRY_PROGRESSION, arguments = idArg) {
            ProgressionSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popToEntries() })
        }
        composable(Routes.ENTRY_CURRENT, arguments = idArg) {
            CurrentStateRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popToEntries() })
        }
        composable(Routes.ENTRY_CUES, arguments = idArg) {
            CuesSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() }, onEntryGone = { nav.popToEntries() })
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
```

- [ ] **Step 4: Start the migrator** — `HiitApp.kt` (complete):
```kotlin
package com.mitenko.hiitcounter

import android.app.Application
import com.mitenko.hiitcounter.data.v1.V1Migrator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HiitApp : Application() {
    @Inject lateinit var migrator: V1Migrator

    override fun onCreate() {
        super.onCreate()
        // Spec §6: import any v1 data on Dispatchers.IO, never with runBlocking. Every repository call awaits it.
        migrator.start()
    }
}
```

- [ ] **Step 5: Check nothing references the v1 layer**
```bash
grep -rnE "SettingsRepository|CounterRepository|SettingsStore|CounterStore|HomeViewModel|HomeRoute|HomeScreen|SettingsListScreen|Routes\.HOME|Routes\.SETTINGS|FakeSettingsRepository|FakeCounterRepository|NumberField" app/src ; echo "CLAUDE-V1-REFS rc=$?"
```
Expected: no matches, and `rc=1` from grep.

- [ ] **Step 6: Verify** — Run (label `T15-2-FULL`, ~5 min): `./gradlew clean assembleDebug testDebugUnitTest lintDebug`, then the test-count command. Use `clean` so no stale XML from the deleted test classes survives. Expected: `BUILD SUCCESSFUL`, **260 tests** (287 − 27), and no lint errors.

- [ ] **Step 7: Commit** — diff-review `HiitNavHost.kt` against the v1 version. The active-run routing `LaunchedEffect` and `dropUnlessResumed`/`launchSingleTop` must still be there. Then:
```bash
git add -A && git commit -m "feat(nav): entry list home and per-entry routes; start the v1 migrator; remove the v1 repositories"
```

**Task 15 gate:** Step 6 is the gate: **260 tests**, no lint errors.

---

## Task 16: Final verification, device check, squash and PR (§9, §10)

### Subtask 16.1: Docs and full verification

**Files:** Edit `claude.md`.

- [ ] **Step 1: Update `claude.md`** with these exact edits:
  1. Under "Source of truth", after the design-spec bullet, add:
     ```markdown
     - **Multi-entry revision (approved, amends v1):** `docs/superpowers/specs/2026-09-25-multi-entry-design.md` — read both.
     ```
  2. Replace `- Check-in happens on **Start**, max once per local calendar day, atomically in DataStore.` with:
     ```markdown
     - Check-in happens on **Start**, max once per local calendar day per entry, atomically in one Room transaction using the entry's own progression.
     ```
  3. Replace `- Home = table like the sheet. No history.` with:
     ```markdown
     - Home = the entry list (create, rename, duplicate, delete, reorder); each entry opens the sheet-style table. The run snapshot (entry id, name, timing, cues) is frozen at Start. No history.
     ```
  4. Replace the `- Stack:` line with:
     ```markdown
     - Stack: Compose + Material 3 (dark), Hilt, Navigation Compose, Room 2.8.1 (`hiit.db`, schemas committed in `app/schemas/`), DataStore Preferences (only `app.preferences_pb`), coroutines/Flow. minSdk 26. Package `com.mitenko.hiitcounter`.
     ```
  5. Replace the `- Plan:` line (the last "Working rules" bullet) with:
     ```markdown
     - Plans: `docs/superpowers/plans/2026-09-24-hiit-counter.md` (v1), `docs/superpowers/plans/2026-09-25-multi-entry.md` (multi-entry).
     - Room schema changes: bump the `@Database` version, add a Migration and a `MigrationTestHelper` test (the schemas are unit-test assets).
     ```

- [ ] **Step 2: Full verification** — Run (label `T16-1-FULL`, ~5 min): `./gradlew clean assembleDebug testDebugUnitTest lintDebug`, then the test-count command. Expected: `BUILD SUCCESSFUL`, **260 tests**, no lint errors. Record the count for the PR.

- [ ] **Step 3: Commit** — `git add -A && git commit -m "docs: multi-entry notes in claude.md"`

### Subtask 16.2: Device verification on the Pixel 9a (over the v1 install)

The device is the user's Pixel 9a, serial **59251JEBF12416**, running Android 17. It has the v1 build installed, with real data. **Never uninstall the app and never clear its data.** Either would destroy the v1 data this step exists to migrate.

- [ ] **Step 1: Device and v1 snapshot**
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"
adb devices                                   # 59251JEBF12416 must be listed as "device"
adb -s 59251JEBF12416 shell run-as com.mitenko.hiitcounter ls -la files/datastore
adb -s 59251JEBF12416 exec-out run-as com.mitenko.hiitcounter tar -cf - files/datastore > "$SCRATCH/claude-v1-datastore.tar"
adb -s 59251JEBF12416 shell am start -n com.mitenko.hiitcounter/.MainActivity
adb -s 59251JEBF12416 exec-out screencap -p > "$SCRATCH/claude-v1-home.png"
```
- If the device isn't listed, ask the user to connect it. Don't start or kill emulators or adb servers you didn't start.
- If `run-as` reports that the package is not debuggable, the v1 install isn't this machine's debug build. `installDebug` would then fail on the signature, so **stop and ask**.
- The `ls` should show `settings.preferences_pb` and `counter.preferences_pb`.
- The tar is a safety copy.
- Open the v1 home screenshot. Record Total Reps, Last Check In and both streaks. Ask the user to open Settings → Timing/Progression on the phone, or take screenshots there too (`claude-v1-timing.png`, `claude-v1-progression.png`), so the values can be compared after the upgrade.

- [ ] **Step 2: Install over v1** — Run (label `T16-2-INSTALL`, ~3 min): `ANDROID_SERIAL=59251JEBF12416 ./gradlew installDebug`, using the logging convention with the inline `SCRATCH=` assignment. Expected: `Installed on 1 device.`
  - If it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signature mismatch) or a version-downgrade error, **stop and ask the user**. Never uninstall.

- [ ] **Step 3: Migration check**
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"
adb -s 59251JEBF12416 shell am start -n com.mitenko.hiitcounter/.MainActivity
adb -s 59251JEBF12416 exec-out screencap -p > "$SCRATCH/claude-v2-list.png"
adb -s 59251JEBF12416 shell run-as com.mitenko.hiitcounter ls -la files/datastore databases
```
Expected:
- The list shows exactly one entry, **"Workout"**, with "Reps N", where N is the v1 total.
- Opening it shows the same table values as `claude-v1-home.png`.
- Its settings match the v1 Timing, Progression, Current State and Cues.
- `files/datastore` no longer contains `settings.preferences_pb` or `counter.preferences_pb`. It contains `app.preferences_pb` only if v1 had asked for notification permission.
- `databases/` contains `hiit.db`.
- Force-stop and relaunch: still exactly one "Workout", so the migration isn't re-run.

- [ ] **Step 4: Manual checklist** (spec §9 manual checks, plus the key v1 §11 regressions). Report each item to the user as pass or fail. Fix each failure TDD-style in a new commit on the branch, then re-run 16.1 Step 2 before continuing.
  1. **Create:** + → "Burpees" opens its entry screen with defaults (8 rows of 6, total 48). ← returns to the list, which shows Workout and Burpees. The first-workout empty state appears only if every entry is deleted.
  2. **Rename:** ⚙ → Rename → "Kettlebell Lunges". The dialog is prefilled, an empty name or 41 characters disables OK with a message, and the list shows the new name.
  3. **Duplicate:** "Kettlebell Lunges copy" opens with the same timing and a fresh counter. ← goes to the list.
  4. **Delete:** Delete on the copy shows "Delete Kettlebell Lunges copy?" with the warning. Confirm, and it's gone from the list.
  5. **Reorder:** toggle Reorder. ▲/▼ move rows, the first ▲ and last ▼ are disabled, and a row moved past the bottom stays in view. Done. The order survives a force-stop and relaunch.
  6. **Separate counters:** Start on Workout, then stop. Workout gets ✓ and its streak changes, while the other entry is unchanged. **Next day** (ask the user to check tomorrow): starting only one entry changes only that entry's streak and penalty.
  7. **Notification name:** Start Kettlebell Lunges. The first notification reads "Kettlebell Lunges · Starting…", then "Kettlebell Lunges · Work · Set n/N".
  8. **Rename during a run:** active-run routing keeps the timer on screen while RUNNING, so the settings screens can't be reached mid-run. Record this item as "not reachable from the UI; covered by `EntrySettingsViewModelTest` (frozen snapshot) and `TimerViewModelTest` (name on screen)". Confirm the notification title keeps the Start-time name throughout the run.
  9. **Timer exit:** stop or finish a run → returns to that entry's screen, not the list. Then start a run, swipe the app away, reopen it from the notification (the timer shows) and stop → returns to the list, because the entry isn't on the recreated back stack.
  10. **Steppers:**
      - Timing: hold + to repeat; tap the value, "1:30" gives 01:30; "1:5" disables OK with "Use m:ss or seconds"; above 2:00:00 TOTAL turns red and Save is disabled.
      - Progression: the penalty steps by 0.5; a dialog value of "0,3" becomes 0.5; the "Hold disabled" hint shows when hold for is 0.
      - Current State: tapping the date text opens the date and time pickers; Clear works.
  11. **v1 regressions:** cues duck music; screen-off timing and vibration stay on time; notification Stop ends the run; swipe-away keeps it running.
  12. **Font scale 200 %:** the list, entry screen, settings and dialogs remain usable.

### Subtask 16.3: Squash and PR

Follow the project PR protocol (`claude.md`, spec §10). No AI attribution anywhere.

- [ ] **Step 1: Verify the build** — Run (label `T16-3-VERIFY`, ~4 min): `./gradlew testDebugUnitTest lintDebug`. Expected: **260 tests**, no lint errors.

- [ ] **Step 2: Sync main and rebase**
```bash
git fetch origin
git checkout main && git pull --ff-only && git checkout feat/multi-entry
git rebase main
```
If the rebase conflicts, stop and report. If `git pull` brought new commits into `main`, re-run Step 1 after the rebase.

- [ ] **Step 3: Squash to one commit.** `main` already contains the spec and this plan (merged before 1.1), so `git reset --soft $(git merge-base main HEAD)` squashes **only the code commits** from this branch. Write `$SCRATCH/claude-commit-msg.txt` (the scratchpad path above):
```text
Multi-entry HIIT workouts

- Room hiit.db (entry + meta tables, exported schema v1) replaces the v1 settings/counter DataStores
- EntryRepository: validation on write, per-field repair on read, migration gate on every call, contiguous positions via transactional create/duplicate/delete/moveBy
- Per-entry check-in in one Room transaction using the row's own progression
- V1Migrator imports v1 data as "Workout" with the marker in the same transaction, copies the notification flag to app.preferences_pb, then deletes the v1 files
- TimerController freezes WorkoutSnapshot(entryId, entryName, timing, cues) and adds isBusy and lastEntryId; the notification and timer show the frozen name
- Entry list home (loading/empty/items, reorder, name dialog), per-entry screen, entry settings with rename, duplicate and a guarded delete
- Shared stepper row and edit dialog for Timing, Progression and Current State; ValueFormat, step ranges and a half-hour penalty draft
- Entry routes with timer exit to the run's entry; v1 repositories, providers and home screen removed
```
Then:
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"
git reset --soft $(git merge-base main HEAD)   # contains only code commits; the docs are already on main
git commit -F "$SCRATCH/claude-commit-msg.txt"
git log --format='%an <%ae>%n%B' -1        # author must be mitenko <mitenko@gmail.com>; no trailers
```

- [ ] **Step 4: Ask the user before pushing.** Show them the commit message and the 16.2 checklist results. If they agree:
```bash
SCRATCH="C:/Users/miten/AppData/Local/Temp/claude/D--Claude-apps-hiit-tracker/28c6cae0-9319-41f0-8af7-51045a6a46f6/scratchpad"
git push --force-with-lease -u origin feat/multi-entry
gh pr create --base main --head feat/multi-entry --title "Multi-entry HIIT workouts" --body-file "$SCRATCH/claude-pr-body.md"
```
Write `$SCRATCH/claude-pr-body.md` first. Include:
- the commit bullets;
- the test count from 16.1;
- the migration check from 16.2 Step 3;
- the checklist results from 16.2 Step 4, noting any items deferred to the next day.

Don't add an AI attribution line.

**Task 16 gate:** the PR is open and CI (`testDebugUnitTest lintDebug`) is green on it.

---

## Spec Coverage

| Spec | Where |
|---|---|
| §3 delta, §4 stack (Room 2.8.1, plugin, schemas as test assets, only `app.preferences_pb` in Hilt, backup unchanged) | 1.2, 4.1, 9.1, 15.2 |
| §5.1 `Entry`, `EntryNotFound`, `EntryBusy`, resolved-total invariant | 2.1, 4.2 |
| §5.2 schema, non-unique position index, `meta`, write validation, per-field repair, group fallback, invalid total → NULL | 4.1–4.4, 5.1–5.3 |
| §5.3 repository contract incl. `moveBy`, `EntryNotFound`, the gate on every call, single-UPDATE hold reset, `checkIn` transaction, interleavings, threading | 5.1–6.3; threading in Global Constraints and 11.2 |
| §5.4 `AppPreferences` (sticky, migration-only write) | 7.1, 7.5, 9.1–9.2 |
| §5.5 names, duplicates allowed, suffix | 2.2–2.3, 6.1 |
| §6 readiness gate, migration-only DataStores (a corrupted file reads as defaults without being rewritten; see 7.4), raw total, same-transaction marker, flag copy, cleanup incl. `.tmp`, crash windows, no re-import, fresh install, create race | 7.2–7.5, 15.2 (start) |
| §7.1 frozen snapshot, `isBusy`, busy delete (`EntryBusy`), rename during a run | 8.1–8.2, 11.2, 14.1–14.2 |
| §7.2 routes, active-run routing, `lastEntryId` exit, loaded-null pops, double-tap guard | 8.1, 11.1, 13.x, 15.1–15.2 |
| §7.3 list: Loading/Empty/Items, top bar, rows (reps, ✓, 56 dp), keyed LazyColumn, reorder ▲/▼ with edges and scroll-into-view, FAB → name → create → `popUpTo(entries)`, empty state | 10.1–10.4, 15.2 |
| §7.4 entry screen (table, ← ⚙ disabled while starting, start flow, `checkIn` failure path) | 11.1–11.3 |
| §7.5 entry settings (pages, rename, duplicate → `popUpTo(entries)`, delete confirm, save racing delete) | 13.1–13.4, 14.1–14.2, 15.2 |
| §7.6 timer/notification name | 8.3–8.4 |
| §8.1 stepper row, clamping table, cross-field rules unclamped, typed drafts, dialog text in `rememberSaveable`, half-hour penalty, screen-specific rules | 3.3–3.4, 12.1–12.3, 13.1–13.4 |
| §8.2 `ValueFormat` | 3.1–3.2 |
| §8.3 edit dialog and name dialog | 10.3, 12.1 |
| §9 every listed unit, Room, migration, ViewModel, navigation and Compose test | the subtasks above; manual checks 16.2 |
| §10 identity, no attribution, squash-to-one PR | Global Constraints, 16.3 |
