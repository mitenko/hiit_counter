# HIIT Counter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the HIIT Counter Android app: a Tabata timer whose per-set rep counts come from a daily-check-in rep progression ported from a Google Sheets script.

**Architecture:** Single `:app` module, MVVM. All rules live in a pure-Kotlin `domain/` package (progression, distribution, timer engine, validation, cue patterns) developed test-first. DataStore repositories persist settings and counter state; a Hilt singleton `TimerController` owns the running engine; a thin `specialUse` foreground service keeps the process/CPU alive, plays cues and shows the notification. Compose screens observe ViewModels' `StateFlow`s.

**Tech Stack:** Kotlin 2.2.10, AGP 8.13.0, Gradle 8.13, Jetpack Compose (BOM 2026.06.01, Material 3), Hilt 2.57 + KSP, Navigation Compose 2.9.5, DataStore Preferences 1.1.1, kotlinx-coroutines 1.10.2, JUnit 4, kotlinx-coroutines-test, Robolectric 4.16 (Compose UI tests on the JVM).

**Spec:** `docs/superpowers/specs/2026-09-24-hiit-counter-design.md` — read it before starting any task. Section references (§n) below point into it.

## How this plan is structured

- **14 tasks**, each split into **numbered subtasks** (`4.1`, `4.2`, …). A subtask is one small TDD slice: write failing test(s) → run and see them fail for the stated reason → write the minimal code → run and see them pass → commit. **One commit per subtask.**
- When a file grows across subtasks, the subtask shows the **complete file as it must be after that subtask** — replace the whole file. Test files grow by appending the listed test methods to the existing class.
- Subtasks with nothing to unit-test (build scaffolding, Android wiring) replace red/green with a build or lint verification.
- Every task ends with a **task gate**: the full `testDebugUnitTest` suite (and `lintDebug` where noted) must pass before the next task starts.
- A subtask's red step sometimes notes a test that already passes — those are regression guards for behaviour an earlier slice delivered; they must stay green.
- **Diff review before every whole-file replacement commit:** run `git diff <file>` and confirm the only changes are the ones the subtask describes — no lost fixes from earlier slices, and public signatures unchanged unless the subtask says so.
- **Pinned versions are fixed.** If anything fails to resolve (1.1 `help`, 1.2 `assembleDebug`), stop and report to the user; do not bump or substitute versions on your own.

## Global Constraints

- Package / applicationId: `com.mitenko.hiitcounter`. minSdk **26**, compileSdk **36**, targetSdk **36**.
- Toolchain: JDK 17 (`kotlin { jvmToolchain(17) }`), Gradle wrapper 8.13. All versions come from `gradle/libs.versions.toml` exactly as written in 1.1 — never "latest", never inline versions in build files.
- Sources live in `app/src/main/kotlin/...` and tests in `app/src/test/kotlin/...` (not `java/`).
- `domain/` must not import `android.*` or `androidx.*`. It is developed test-first.
- Never call `Instant.now()`, `System.currentTimeMillis()` or `SystemClock` outside `platform/AndroidClock.kt`; inject `Clock`.
- Date formatting uses `Locale.ENGLISH` (matches the sheet: `24 Sep 2026, 05:55`).
- Dark theme only; portrait only.
- No `WORK` label on the timer (user decision) — WORK shows only the big bright rep number.
- Git: all work on branch `feat/app-v1` (created in 1.1 from an up-to-date `main`). One commit per subtask (messages given). Never merge locally into `main`; never push or open a PR without asking the user.
- Running Gradle (Git Bash, repo root). Every long-running command is labelled and logged to the session scratchpad (`$SCRATCH` = the session scratchpad directory):
  ```bash
  ./gradlew <tasks> 2>&1 | tee "$SCRATCH/claude-gradle-<label>.log"; echo "CLAUDE-<LABEL> DONE rc=${PIPESTATUS[0]}"
  ```
  `Run (label X): <tasks>` below means exactly that command with `<label>` = X. Announce the command and its rough duration before running it. Never kill a Gradle/Java process you did not start.
- Commit messages and PR descriptions carry no AI co-author trailers or "generated with" footers (user preference).

## Subtask Overview

| Task | Subtasks |
|---|---|
| 1 Scaffold | 1.1 wrapper + root build + version catalog · 1.2 app module, manifest, resources, theme, placeholder activity · 1.3 CI + lint |
| 2 Models | 2.1 half-up rounding · 2.2 timing & progression configs · 2.3 counter/timer state, cues, Clock |
| 3 RepDistributor | 3.1 even split with remainder · 3.2 argument guards |
| 4 RepProgression | 4.1 same-day rule + on-time +1 · 4.2 first check-in · 4.3 clamp to floor/cap · 4.4 missed + penalty · 4.5 hold |
| 5 TabataEngine | 5.1 phase sequence & drift-free ticks · 5.2 one-shot cues · 5.3 pause/resume |
| 6 TimerController | 6.1 prepare/start/done lifecycle · 6.2 stop + cue stream · 6.3 pause/resume + max-pause auto-stop |
| 7 SettingsValidator | 7.1 timing · 7.2 progression · 7.3 current state |
| 8 Data | 8.1 settings repository · 8.2 counter repository (read/write) · 8.3 atomic check-in · 8.4 Clock + Hilt modules |
| 9 Cues | 9.1 cue patterns · 9.2 tone assets · 9.3 CuePlayer |
| 10 Service | 10.1 TimerText · 10.2 wake-lock policy · 10.3 notifications + foreground service + manifest · 10.4 service starter + DI |
| 11 Home | 11.1 date formats · 11.2 HomeViewModel table state · 11.3 start flow · 11.4 HomeScreen |
| 12 Timer UI | 12.1 TimerUiMapper · 12.2 TimerViewModel · 12.3 DualRing + TimerScreen · 12.4 TimerRoute |
| 13 Settings UI | 13.1 routes, shared components, settings list · 13.2 cues · 13.3 timing · 13.4 progression · 13.5 current state |
| 14 Wiring | 14.1 nav host + MainActivity · 14.2 device verification · 14.3 docs + PR |

## File Map

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew(.bat), gradle/wrapper/*
.github/workflows/ci.yml
tools/gen_tones.py                                   generates res/raw tone WAVs
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/{strings.xml,themes.xml}
app/src/main/res/drawable/ic_*.xml                   vector icons (no icon library dependency)
app/src/main/res/raw/{tone_short.wav,tone_long.wav}
app/src/main/kotlin/com/mitenko/hiitcounter/
  HiitApp.kt, MainActivity.kt
  domain/
    Clock.kt, Rounding.kt
    model/{TimingConfig,ProgressionConfig,CueConfig,CounterState,TimerState,Cue}.kt
    RepDistributor.kt, RepProgression.kt, TabataEngine.kt, TimerController.kt
    SettingsValidator.kt, CuePatterns.kt, TimerText.kt
  platform/AndroidClock.kt
  data/{PreferenceKeys,SettingsRepository,CounterRepository}.kt
  di/{DataModule,AppModule}.kt
  service/{TimerService,WorkoutNotifications,CuePlayer,WorkoutServiceStarter}.kt
  ui/theme/Theme.kt
  ui/common/{DateFormats,SettingsComponents}.kt
  ui/home/{HomeViewModel,HomeScreen}.kt
  ui/timer/{TimerUiMapper,TimerViewModel,DualRing,TimerScreen,TimerRoute}.kt
  ui/settings/{SettingsListScreen,CuesSettings,TimingSettings,ProgressionSettings,CurrentStateSettings}.kt
  ui/navigation/{Routes,HiitNavHost}.kt
app/src/test/kotlin/com/mitenko/hiitcounter/
  testutil/{FakeClock,FakeSettingsRepository,FakeCounterRepository,FakeServiceStarter,MainDispatcherRule}.kt
  domain/..., data/..., ui/...                       tests mirroring the main tree
```

---

## Task 1: Project scaffold, build, CI

**Interfaces produced:** `HiitTheme { }` composable; `HiitColors` (`Work`, `Rest`, `Neutral`, `SetRing`, `Track`); all string resources; drawables `ic_launcher, ic_play, ic_pause, ic_close, ic_back, ic_add, ic_remove, ic_settings`.

### Subtask 1.1: Wrapper, root build files, version catalog

**Files:** Copy `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` from `D:\Claude\apps\storyteller` (known-good Gradle 8.13 wrapper on this machine). Create `gradle/wrapper/gradle-wrapper.properties`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`.

- [ ] **Step 1: Branch from an up-to-date main**

```bash
git checkout main && git fetch && git pull --ff-only && git checkout -b feat/app-v1
```

- [ ] **Step 2: Copy the wrapper and mark it executable**

```bash
cp /d/Claude/apps/storyteller/gradlew /d/Claude/apps/storyteller/gradlew.bat .
mkdir -p gradle/wrapper && cp /d/Claude/apps/storyteller/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/
git add gradlew && git update-index --chmod=+x gradlew
```

`gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 3: Root build files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "hiit_counter"
```

(`include(":app")` is added in 1.2 together with the module.)

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:
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
```

- [ ] **Step 4: Verify the wrapper and plugin resolution**

Run (label `T1-1-VERSION`): `./gradlew --version` — expected output includes `Gradle 8.13` (confirms the copied wrapper jar honours our properties file).

Run (label `T1-1`, ~1–2 min): `./gradlew help`
Expected: `BUILD SUCCESSFUL` (all catalog plugins resolve; no modules yet). If resolution fails, stop and report (see Global Constraints).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "build: add Gradle 8.13 wrapper and version catalog"
```

### Subtask 1.2: App module, manifest, resources, theme, placeholder activity

**Files:** Modify `settings.gradle.kts`. Create `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/{strings,themes}.xml`, `app/src/main/res/drawable/ic_*.xml`, `app/src/main/kotlin/com/mitenko/hiitcounter/{HiitApp,MainActivity}.kt`, `.../ui/theme/Theme.kt`. Modify `.gitignore`.

- [ ] **Step 1: Include the module**

Append to `settings.gradle.kts`:
```kotlin
include(":app")
```

- [ ] **Step 2: App build file**

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
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
    lint {
        abortOnError = true
    }
}

kotlin { jvmToolchain(17) }

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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
```

`app/proguard-rules.pro`: empty file.

Append to `.gitignore`:
```
/app/build/
/.kotlin/
```

- [ ] **Step 3: Manifest and resources**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".HiitApp"
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.HiitCounter">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.HiitCounter" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@android:color/black</item>
    </style>
</resources>
```

`app/src/main/res/values/strings.xml` (all strings for the whole app):
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
    <string name="notification_starting">Starting workout…</string>
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
</resources>
```

Vector drawables in `app/src/main/res/drawable/` — each file uses this template with its `PATH`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="PATH" />
</vector>
```

| File | `PATH` |
|---|---|
| `ic_play.xml` | `M8,5v14l11,-7z` |
| `ic_pause.xml` | `M6,19h4V5H6v14zM14,5v14h4V5h-4z` |
| `ic_close.xml` | `M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z` |
| `ic_back.xml` | `M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z` |
| `ic_add.xml` | `M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z` |
| `ic_remove.xml` | `M19,13H5v-2h14v2z` |
| `ic_settings.xml` | `M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z` |

`ic_launcher.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF8BC34A" android:pathData="M12,2A10,10 0 1,0 12,22A10,10 0 1,0 12,2z" />
    <path android:fillColor="#FF000000" android:pathData="M10,8v8l6,-4z" />
</vector>
```

- [ ] **Step 4: Theme, application class, placeholder activity**

`app/src/main/kotlin/com/mitenko/hiitcounter/ui/theme/Theme.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HiitColors {
    val Work = Color(0xFF8BC34A)
    val Rest = Color(0xFFFFB300)
    val Neutral = Color(0xFF78909C)
    val SetRing = Color(0xFF4FC3F7)
    val Track = Color(0xFF1E2A30)
}

private val DarkScheme = darkColorScheme(
    primary = HiitColors.Work,
    onPrimary = Color.Black,
    secondary = HiitColors.SetRing,
    background = Color(0xFF12181B),
    surface = Color(0xFF12181B),
    error = Color(0xFFEF5350),
)

@Composable
fun HiitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
```

`app/src/main/kotlin/com/mitenko/hiitcounter/HiitApp.kt`:
```kotlin
package com.mitenko.hiitcounter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HiitApp : Application()
```

`app/src/main/kotlin/com/mitenko/hiitcounter/MainActivity.kt` (placeholder; replaced in 14.1):
```kotlin
package com.mitenko.hiitcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HiitTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) { Text("HIIT Counter") }
            }
        }
    }
}
```

- [ ] **Step 5: Verify the build**

Run (label `T1-2`, ~3–5 min first time): `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`; `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "build: add app module with Hilt, Compose theme and placeholder activity"
```

### Subtask 1.3: CI workflow and lint baseline

**Files:** Create `.github/workflows/ci.yml`.

- [ ] **Step 1: Workflow**

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew --no-daemon testDebugUnitTest lintDebug
```

- [ ] **Step 2: Verify the CI command locally**

Run (label `T1-3`, ~2 min): `./gradlew testDebugUnitTest lintDebug`
Expected: `BUILD SUCCESSFUL`; no tests yet (NO-SOURCE is fine); lint warnings allowed, no errors.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "ci: run unit tests and lint on push and PR"
```

**Task 1 gate:** 1.3 Step 2 is the gate.

---

## Task 2: Domain models, Clock, rounding

**Interfaces produced:**
- `fun roundHalfUp(x: Double): Int`
- `data class TimingConfig(prepareSec=10, sets=8, workSec=20, restSec=10, cooldownSec=0)` + `totalDurationSec`
- `data class ProgressionConfig(startingTotal=48, floor=48, cap=72, holdAt=64, holdFor=4, windowHours=36, penaltyHoursPerRep=19.5)` + `holdEnabled`
- `data class CueConfig(sound=true, vibration=true)`
- `data class CounterState(total: Int, bestStreak=0, currentStreak=0, lastCheckIn: Instant?=null, holdCount=0)`
- `enum class Phase { PREPARE, WORK, REST, COOLDOWN, DONE }`; `data class TimerState(phase, set, sets, phaseSecondsLeft, phaseDurationSec, elapsedSec, totalDurationSec, repsThisSet, totalReps, paused)` + `completedWorkSets`, `remainingSec`
- `sealed interface Cue { Countdown(secondsLeft); PhaseStart(phase); Finished }`
- `interface Clock { now(): Instant; zone(): ZoneId; elapsedRealtimeMs(): Long }`; test util `FakeClock`

### Subtask 2.1: Half-up rounding

**Files:** Create `domain/Rounding.kt`; test `domain/RoundingTest.kt`.

- [ ] **Step 1: Failing test** — `app/src/test/kotlin/com/mitenko/hiitcounter/domain/RoundingTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundingTest {
    @Test
    fun `rounds half up like JavaScript Math round`() {
        assertEquals(1, roundHalfUp(0.5))
        assertEquals(1, roundHalfUp(1.4999))
        assertEquals(3, roundHalfUp(2.5))
        assertEquals(1, roundHalfUp(0.667))
        assertEquals(0, roundHalfUp(-0.5))
        assertEquals(-1, roundHalfUp(-0.51))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T2-1-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.RoundingTest"`. Expected: compile FAIL, unresolved `roundHalfUp`.

- [ ] **Step 3: Implement** — `app/src/main/kotlin/com/mitenko/hiitcounter/domain/Rounding.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import kotlin.math.floor

/** Round half up, matching JavaScript Math.round used by the original sheet. */
fun roundHalfUp(x: Double): Int = floor(x + 0.5).toInt()
```

- [ ] **Step 4: Run green** — Run (label `T2-1-GREEN`): same command. Expected: PASS.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): half-up rounding matching the sheet"`

### Subtask 2.2: Timing and progression configs

**Files:** Create `domain/model/TimingConfig.kt`, `domain/model/ProgressionConfig.kt`; test `domain/model/ConfigsTest.kt`.

- [ ] **Step 1: Failing test** — `app/src/test/kotlin/com/mitenko/hiitcounter/domain/model/ConfigsTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigsTest {
    @Test
    fun `default timing totals four minutes with no rest after the last set`() {
        assertEquals(240, TimingConfig().totalDurationSec)
        assertEquals(10 + 20 + 5, TimingConfig(sets = 1, cooldownSec = 5).totalDurationSec)
    }

    @Test
    fun `hold is enabled only for a positive count and holdAt within floor to cap exclusive`() {
        assertTrue(ProgressionConfig().holdEnabled)
        assertFalse(ProgressionConfig(holdFor = 0).holdEnabled)
        assertFalse(ProgressionConfig(holdAt = 72).holdEnabled)
        assertFalse(ProgressionConfig(holdAt = 40).holdEnabled)
        assertTrue(ProgressionConfig(holdAt = 48).holdEnabled)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T2-2-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.model.ConfigsTest"`. Expected: compile FAIL, unresolved `TimingConfig`, `ProgressionConfig`.

- [ ] **Step 3: Implement**

`domain/model/TimingConfig.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

data class TimingConfig(
    val prepareSec: Int = 10,
    val sets: Int = 8,
    val workSec: Int = 20,
    val restSec: Int = 10,
    val cooldownSec: Int = 0,
) {
    /** No rest after the final set. */
    val totalDurationSec: Int
        get() = prepareSec + sets * workSec + (sets - 1).coerceAtLeast(0) * restSec + cooldownSec
}
```

`domain/model/ProgressionConfig.kt`:
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
) {
    val holdEnabled: Boolean
        get() = holdFor > 0 && holdAt >= floor && holdAt < cap
}
```

- [ ] **Step 4: Run green** — Run (label `T2-2-GREEN`): same command. Expected: PASS.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): timing and progression configs"`

### Subtask 2.3: Counter/timer state, cues, Clock

**Files:** Create `domain/model/{CueConfig,CounterState,TimerState,Cue}.kt`, `domain/Clock.kt`; test `domain/model/TimerStateTest.kt`, `testutil/FakeClock.kt`.

- [ ] **Step 1: Failing test** — `app/src/test/kotlin/com/mitenko/hiitcounter/domain/model/TimerStateTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerStateTest {
    @Test
    fun `derives completed sets and remaining time`() {
        val work = TimerState(Phase.WORK, set = 3, sets = 8, phaseSecondsLeft = 10, phaseDurationSec = 20,
            elapsedSec = 100, totalDurationSec = 240, repsThisSet = 8, totalReps = 64, paused = false)
        assertEquals(2, work.completedWorkSets)
        assertEquals(140, work.remainingSec)
        assertEquals(8, work.copy(phase = Phase.COOLDOWN).completedWorkSets)
        assertEquals(8, work.copy(phase = Phase.DONE).completedWorkSets)
        assertEquals(0, work.copy(elapsedSec = 300).remainingSec)
    }
}
```

`app/src/test/kotlin/com/mitenko/hiitcounter/testutil/FakeClock.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.domain.Clock
import java.time.Instant
import java.time.ZoneId

class FakeClock(
    var instant: Instant = Instant.parse("2026-09-24T12:55:00Z"),
    var zoneId: ZoneId = ZoneId.of("America/Los_Angeles"),
    var elapsedMs: Long = 0L,
) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    override fun elapsedRealtimeMs(): Long = elapsedMs
}
```

- [ ] **Step 2: Run red** — Run (label `T2-3-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.model.TimerStateTest"`. Expected: compile FAIL, unresolved `TimerState`, `Phase`, `Clock`.

- [ ] **Step 3: Implement**

`domain/Clock.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import java.time.Instant
import java.time.ZoneId

/** Wall-clock + monotonic time. The only real implementation is platform/AndroidClock. */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
    fun elapsedRealtimeMs(): Long
}
```

`domain/model/CueConfig.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

data class CueConfig(
    val sound: Boolean = true,
    val vibration: Boolean = true,
)
```

`domain/model/CounterState.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

import java.time.Instant

data class CounterState(
    val total: Int,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val lastCheckIn: Instant? = null,
    val holdCount: Int = 0,
)
```

`domain/model/TimerState.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

enum class Phase { PREPARE, WORK, REST, COOLDOWN, DONE }

/**
 * Snapshot of a running workout. [set] is the current work set during WORK, and the
 * upcoming work set during PREPARE and REST.
 */
data class TimerState(
    val phase: Phase,
    val set: Int,
    val sets: Int,
    val phaseSecondsLeft: Int,
    val phaseDurationSec: Int,
    val elapsedSec: Int,
    val totalDurationSec: Int,
    val repsThisSet: Int,
    val totalReps: Int,
    val paused: Boolean,
) {
    val completedWorkSets: Int
        get() = when (phase) {
            Phase.COOLDOWN, Phase.DONE -> sets
            else -> set - 1
        }

    val remainingSec: Int
        get() = (totalDurationSec - elapsedSec).coerceAtLeast(0)
}
```

`domain/model/Cue.kt`:
```kotlin
package com.mitenko.hiitcounter.domain.model

sealed interface Cue {
    data class Countdown(val secondsLeft: Int) : Cue
    data class PhaseStart(val phase: Phase) : Cue
    data object Finished : Cue
}
```

- [ ] **Step 4: Run green** — Run (label `T2-3-GREEN`): same command. Expected: PASS.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): counter/timer state, cue types and Clock"`

**Task 2 gate:** Run (label `T2-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 3: RepDistributor

**Interfaces produced:** `object RepDistributor { fun distribute(total: Int, sets: Int): List<Int> }` (§7).

### Subtask 3.1: Even split with remainder on the first sets

**Files:** Create `domain/RepDistributor.kt`; test `domain/RepDistributorTest.kt`.

- [ ] **Step 1: Failing test** — `app/src/test/kotlin/com/mitenko/hiitcounter/domain/RepDistributorTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RepDistributorTest {
    @Test
    fun `remainder goes to the first sets`() {
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), RepDistributor.distribute(65, 8))
        assertEquals(listOf(9, 9, 9, 8, 8, 8, 8, 8), RepDistributor.distribute(67, 8))
    }

    @Test
    fun `exact multiples split evenly`() {
        assertEquals(List(8) { 6 }, RepDistributor.distribute(48, 8))
    }

    @Test
    fun `single set gets everything`() {
        assertEquals(listOf(65), RepDistributor.distribute(65, 1))
    }

    @Test
    fun `total smaller than sets yields zeros at the end`() {
        assertEquals(listOf(1, 1, 1, 0, 0), RepDistributor.distribute(3, 5))
    }

    @Test
    fun `sum always equals total`() {
        for (total in 0..100) for (sets in 1..20) {
            assertEquals(total, RepDistributor.distribute(total, sets).sum())
        }
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T3-1-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.RepDistributorTest"`. Expected: compile FAIL, unresolved `RepDistributor`.

- [ ] **Step 3: Implement** — `domain/RepDistributor.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

object RepDistributor {
    /** Splits [total] across [sets]; the first `total % sets` sets get one extra rep. */
    fun distribute(total: Int, sets: Int): List<Int> {
        val base = total / sets
        val extra = total % sets
        return List(sets) { index -> if (index < extra) base + 1 else base }
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (5 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): distribute rep total across sets"`

### Subtask 3.2: Argument guards

**Files:** Modify `domain/RepDistributor.kt`, `domain/RepDistributorTest.kt`.

- [ ] **Step 1: Failing tests** — append to `RepDistributorTest`:
```kotlin
    @Test(expected = IllegalArgumentException::class)
    fun `zero sets is rejected`() {
        RepDistributor.distribute(10, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative total is rejected`() {
        RepDistributor.distribute(-1, 8)
    }
```

- [ ] **Step 2: Run red** — Run (label `T3-2-RED`): same command as 3.1. Expected: FAIL — `zero sets` throws `ArithmeticException` instead of `IllegalArgumentException`; `negative total` throws nothing.

- [ ] **Step 3: Implement** — replace `domain/RepDistributor.kt` with:
```kotlin
package com.mitenko.hiitcounter.domain

object RepDistributor {
    /** Splits [total] across [sets]; the first `total % sets` sets get one extra rep. */
    fun distribute(total: Int, sets: Int): List<Int> {
        require(sets >= 1) { "sets must be >= 1, was $sets" }
        require(total >= 0) { "total must be >= 0, was $total" }
        val base = total / sets
        val extra = total % sets
        return List(sets) { index -> if (index < extra) base + 1 else base }
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (7 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): validate RepDistributor arguments"`

**Task 3 gate:** Run (label `T3-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 4: RepProgression (check-in rules, §6)

**Interfaces produced:**
- `sealed interface Outcome { AlreadyToday; First; OnTime; Missed(penalty: Int) }`
- `data class CheckInResult(val state: CounterState, val outcome: Outcome)`
- `object RepProgression { fun checkIn(state: CounterState, config: ProgressionConfig, now: Instant, zone: ZoneId): CheckInResult }`

**Files (all subtasks):** `app/src/main/kotlin/com/mitenko/hiitcounter/domain/RepProgression.kt`; test `app/src/test/kotlin/com/mitenko/hiitcounter/domain/RepProgressionTest.kt`.

Test command for every subtask: `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.RepProgressionTest"`.

### Subtask 4.1: Same-day no-op and on-time +1

- [ ] **Step 1: Failing tests** — create `RepProgressionTest.kt` (the helpers are used by every later subtask):
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RepProgressionTest {
    private val zone: ZoneId = ZoneId.of("America/Los_Angeles")
    private val cfg = ProgressionConfig()
    private val t0: Instant = ZonedDateTime.of(2026, 9, 1, 7, 0, 0, 0, zone).toInstant()

    private fun state(total: Int, streak: Int = 5, best: Int = 10, last: Instant? = t0, hold: Int = 0) =
        CounterState(total = total, bestStreak = best, currentStreak = streak, lastCheckIn = last, holdCount = hold)

    private fun hoursLater(h: Double): Instant = t0.plusMillis((h * 3_600_000).toLong())

    private fun check(s: CounterState, now: Instant, c: ProgressionConfig = cfg) =
        RepProgression.checkIn(s, c, now, zone)

    private fun CounterState.totalAndHold() = total to holdCount

    @Test
    fun `same local day is a no-op`() {
        val s = state(60)
        val r = check(s, hoursLater(10.0))
        assertEquals(Outcome.AlreadyToday, r.outcome)
        assertEquals(s, r.state)
    }

    @Test
    fun `same local day across a DST change is a no-op`() {
        val last = ZonedDateTime.of(2026, 3, 8, 0, 30, 0, 0, zone).toInstant()
        val now = ZonedDateTime.of(2026, 3, 8, 23, 30, 0, 0, zone).toInstant()
        assertEquals(Outcome.AlreadyToday, check(state(60, last = last), now).outcome)
    }

    @Test
    fun `just after midnight is a new day`() {
        val last = ZonedDateTime.of(2026, 9, 1, 23, 30, 0, 0, zone).toInstant()
        val now = ZonedDateTime.of(2026, 9, 2, 0, 10, 0, 0, zone).toInstant()
        val r = check(state(60, last = last), now)
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(61, r.state.total)
    }

    @Test
    fun `on time adds one rep and extends the streak`() {
        val now = hoursLater(24.0)
        val r = check(state(50, streak = 5, best = 10), now)
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(CounterState(total = 51, bestStreak = 10, currentStreak = 6, lastCheckIn = now, holdCount = 0), r.state)
    }

    @Test
    fun `on time raises the best streak`() {
        val r = check(state(50, streak = 10, best = 10), hoursLater(24.0))
        assertEquals(11, r.state.currentStreak)
        assertEquals(11, r.state.bestStreak)
    }

    @Test
    fun `on time never exceeds the cap`() {
        assertEquals(72, check(state(72), hoursLater(24.0)).state.total)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T4-1-RED`). Expected: compile FAIL, unresolved `RepProgression`, `Outcome`.

- [ ] **Step 3: Implement** — `RepProgression.kt`:
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
    fun checkIn(state: CounterState, config: ProgressionConfig, now: Instant, zone: ZoneId): CheckInResult {
        val last = state.lastCheckIn

        // Rule 1: already checked in today.
        if (last != null && last.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            return CheckInResult(state, Outcome.AlreadyToday)
        }

        // Rule 4: on time.
        val total = state.total
        val streak = state.currentStreak + 1
        val newTotal = if (total < config.cap) total + 1 else total
        return CheckInResult(
            CounterState(
                total = newTotal,
                bestStreak = max(state.bestStreak, streak),
                currentStreak = streak,
                lastCheckIn = now,
                holdCount = 0,
            ),
            Outcome.OnTime,
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T4-1-GREEN`). Expected: PASS (6 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): check-in same-day rule and on-time increment"`

### Subtask 4.2: First ever check-in

- [ ] **Step 1: Failing test** — append:
```kotlin
    @Test
    fun `first check-in keeps the starting total and starts the streak`() {
        val now = hoursLater(1.0)
        val r = check(CounterState(total = 48), now)
        assertEquals(Outcome.First, r.outcome)
        assertEquals(CounterState(total = 48, bestStreak = 1, currentStreak = 1, lastCheckIn = now, holdCount = 0), r.state)
    }
```

- [ ] **Step 2: Run red** — Run (label `T4-2-RED`). Expected: FAIL — outcome `OnTime`, total 49.

- [ ] **Step 3: Implement** — replace `RepProgression.kt`:
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
    fun checkIn(state: CounterState, config: ProgressionConfig, now: Instant, zone: ZoneId): CheckInResult {
        val last = state.lastCheckIn

        // Rule 1: already checked in today.
        if (last != null && last.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            return CheckInResult(state, Outcome.AlreadyToday)
        }

        val total = state.total

        // Rule 2: first ever check-in — perform the starting total on day 1.
        if (last == null) {
            return CheckInResult(
                CounterState(
                    total = total,
                    bestStreak = max(state.bestStreak, 1),
                    currentStreak = 1,
                    lastCheckIn = now,
                    holdCount = 0,
                ),
                Outcome.First,
            )
        }

        // Rule 4: on time.
        val streak = state.currentStreak + 1
        val newTotal = if (total < config.cap) total + 1 else total
        return CheckInResult(
            CounterState(
                total = newTotal,
                bestStreak = max(state.bestStreak, streak),
                currentStreak = streak,
                lastCheckIn = now,
                holdCount = 0,
            ),
            Outcome.OnTime,
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T4-2-GREEN`). Expected: PASS (7 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): first check-in keeps the starting total"`

### Subtask 4.3: Clamp to floor and cap

- [ ] **Step 1: Failing tests** — append:
```kotlin
    @Test
    fun `first check-in clamps to the floor`() {
        assertEquals(48, check(CounterState(total = 40), t0).state.total)
    }

    @Test
    fun `total above the cap is clamped`() {
        assertEquals(72, check(state(80), hoursLater(24.0)).state.total)
    }

    @Test
    fun `total below the floor is clamped then incremented`() {
        assertEquals(49, check(state(40), hoursLater(24.0)).state.total)
    }
```

- [ ] **Step 2: Run red** — Run (label `T4-3-RED`). Expected: FAIL — 40, 80 and 41 respectively.

- [ ] **Step 3: Implement** — in `RepProgression.kt` replace the line `val total = state.total` with:
```kotlin
        // Rules 2–4 start from a total clamped to [floor, cap] (config may have changed).
        val total = state.total.coerceIn(config.floor, config.cap)
```

- [ ] **Step 4: Run green** — Run (label `T4-3-GREEN`). Expected: PASS (10 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): clamp total to floor and cap at check-in"`

### Subtask 4.4: Missed check-in and penalty

- [ ] **Step 1: Failing tests** — append:
```kotlin
    @Test
    fun `window boundary uses half-up hour rounding`() {
        assertEquals(Outcome.OnTime, check(state(60), hoursLater(36.49)).outcome)
        assertEquals(Outcome.Missed(0), check(state(60), hoursLater(36.5)).outcome)
    }

    @Test
    fun `penalty matches the sheet formula`() {
        val expected = mapOf(37.0 to 0, 48.0 to 0, 53.0 to 0, 54.0 to 1, 60.0 to 1, 72.0 to 1, 73.0 to 2, 84.0 to 2)
        for ((hours, penalty) in expected) {
            val now = hoursLater(hours)
            val r = check(state(60, streak = 5, best = 10), now)
            assertEquals("hours=$hours", Outcome.Missed(penalty), r.outcome)
            assertEquals(
                "hours=$hours",
                CounterState(total = 60 - penalty, bestStreak = 10, currentStreak = 1, lastCheckIn = now, holdCount = 0),
                r.state,
            )
        }
    }

    @Test
    fun `penalty never drops below the floor`() {
        assertEquals(48, check(state(49), hoursLater(84.0)).state.total)
    }

    @Test
    fun `cap equal to floor pins the total`() {
        val c = ProgressionConfig(startingTotal = 60, floor = 60, cap = 60)
        assertEquals(60, check(state(60), hoursLater(24.0), c).state.total)
        assertEquals(60, check(state(60), hoursLater(84.0), c).state.total)
    }

    @Test
    fun `clock moved backwards is treated as on time`() {
        val r = check(state(60), t0.minusSeconds(30 * 3600))
        assertEquals(Outcome.OnTime, r.outcome)
        assertEquals(61, r.state.total)
    }
```

- [ ] **Step 2: Run red** — Run (label `T4-4-RED`). Expected: FAIL — `window boundary`, `penalty matches`, `penalty never drops` report `OnTime` instead of `Missed`. (`cap equal to floor` and `clock moved backwards` already pass — regression guards.)

- [ ] **Step 3: Implement** — replace `RepProgression.kt`:
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

    fun checkIn(state: CounterState, config: ProgressionConfig, now: Instant, zone: ZoneId): CheckInResult {
        val last = state.lastCheckIn

        // Rule 1: already checked in today.
        if (last != null && last.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            return CheckInResult(state, Outcome.AlreadyToday)
        }

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
                    holdCount = 0,
                ),
                Outcome.First,
            )
        }

        val hours = roundHalfUp((now.toEpochMilli() - last.toEpochMilli()) / MS_PER_HOUR)

        // Rule 3: missed.
        if (hours > config.windowHours) {
            val penalty = max(0, roundHalfUp((hours - 24) / config.penaltyHoursPerRep) - 1)
            return CheckInResult(
                CounterState(
                    total = max(config.floor, total - penalty),
                    bestStreak = max(state.bestStreak, 1),
                    currentStreak = 1,
                    lastCheckIn = now,
                    holdCount = 0,
                ),
                Outcome.Missed(penalty),
            )
        }

        // Rule 4: on time (includes a clock that moved backwards: negative hours).
        val streak = state.currentStreak + 1
        val newTotal = if (total < config.cap) total + 1 else total
        return CheckInResult(
            CounterState(
                total = newTotal,
                bestStreak = max(state.bestStreak, streak),
                currentStreak = streak,
                lastCheckIn = now,
                holdCount = 0,
            ),
            Outcome.OnTime,
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T4-4-GREEN`). Expected: PASS (15 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): missed check-in penalty from the sheet formula"`

### Subtask 4.5: Hold

- [ ] **Step 1: Failing tests** — append:
```kotlin
    @Test
    fun `first check-in at the hold value starts the hold`() {
        val c = cfg.copy(startingTotal = 64)
        assertEquals(64 to 1, check(CounterState(total = 64), t0, c).state.totalAndHold())
    }

    @Test
    fun `hold performs the hold value on holdFor check-ins then advances`() {
        var s = state(63)
        var now = t0
        val performed = mutableListOf<Pair<Int, Int>>()
        repeat(5) {
            now = now.plusSeconds(24 * 3600)
            s = check(s, now).state
            performed += s.totalAndHold()
        }
        assertEquals(listOf(64 to 1, 64 to 2, 64 to 3, 64 to 4, 65 to 0), performed)
    }

    @Test
    fun `holdFor of one holds for a single check-in`() {
        val c = cfg.copy(holdFor = 1)
        val first = check(state(63), hoursLater(24.0), c).state
        val second = check(first, hoursLater(47.0), c).state
        assertEquals(listOf(64 to 1, 65 to 0), listOf(first.totalAndHold(), second.totalAndHold()))
    }

    @Test
    fun `hold after a miss restarts when the total lands on the hold value`() {
        data class Case(val total: Int, val hold: Int, val hours: Double, val expected: Pair<Int, Int>)
        val cases = listOf(
            Case(64, 3, 48.0, 64 to 1),
            Case(64, 4, 48.0, 64 to 1),
            Case(66, 0, 84.0, 64 to 1),
            Case(65, 0, 84.0, 63 to 0),
            Case(64, 2, 60.0, 63 to 0),
        )
        for (c in cases) {
            val r = check(state(c.total, hold = c.hold), hoursLater(c.hours))
            assertEquals("$c", c.expected, r.state.totalAndHold())
        }
    }

    @Test
    fun `disabled hold configurations advance normally`() {
        assertEquals(65 to 0, check(state(64), hoursLater(24.0), cfg.copy(holdFor = 0)).state.totalAndHold())
        assertEquals(72 to 0, check(state(71), hoursLater(24.0), cfg.copy(holdAt = 72)).state.totalAndHold())
        assertEquals(65 to 0, check(state(64), hoursLater(24.0), cfg.copy(holdAt = 40)).state.totalAndHold())
    }

    @Test
    fun `hold at the floor restarts after a miss down to the floor`() {
        assertEquals(48 to 1, check(state(49), hoursLater(84.0), cfg.copy(holdAt = 48)).state.totalAndHold())
    }
```

- [ ] **Step 2: Run red** — Run (label `T4-5-RED`). Expected: FAIL — hold counts are 0 and 64 advances straight to 65. (`disabled hold configurations` already passes — regression guard.)

- [ ] **Step 3: Implement** — replace `RepProgression.kt` with the final version:
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

    fun checkIn(state: CounterState, config: ProgressionConfig, now: Instant, zone: ZoneId): CheckInResult {
        val last = state.lastCheckIn

        // Rule 1: already checked in today.
        if (last != null && last.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            return CheckInResult(state, Outcome.AlreadyToday)
        }

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

        val hours = roundHalfUp((now.toEpochMilli() - last.toEpochMilli()) / MS_PER_HOUR)

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

    /** 1 when this check-in is the first performed at the hold value, else 0. */
    private fun startingHoldCount(total: Int, config: ProgressionConfig): Int =
        if (config.holdEnabled && total == config.holdAt) 1 else 0
}
```

- [ ] **Step 4: Run green** — Run (label `T4-5-GREEN`). Expected: PASS (21 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): literal hold at holdAt for holdFor check-ins"`

**Task 4 gate:** Run (label `T4-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 5: TabataEngine (§8)

**Interfaces produced:** `class TabataEngine(timing: TimingConfig, repsPerSet: List<Int>, nowMs: () -> Long, onState: (TimerState) -> Unit, onCue: (Cue) -> Unit)` with `suspend fun run()`, `fun pause()`, `fun resume()`, `val isPaused: Boolean`. Driven from a single thread (Main in production).

Emission contract: on phase entry emit `phaseSecondsLeft = d`; ticks at `+1 s … +(d−1) s` emit `d−1 … 1`; no `0`; after the last phase emit DONE. Cues: `PhaseStart` on entry to WORK/REST/COOLDOWN; `Countdown(n)` on ticks where `n ≤ 3` (not on phase entry); `Finished` on DONE.

**Files (all subtasks):** `domain/TabataEngine.kt`; test `domain/TabataEngineTest.kt`. Test command: `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.TabataEngineTest"`.

### Subtask 5.1: Phase sequence and drift-free ticks

- [ ] **Step 1: Failing tests** — create `TabataEngineTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.Phase.COOLDOWN
import com.mitenko.hiitcounter.domain.model.Phase.DONE
import com.mitenko.hiitcounter.domain.model.Phase.PREPARE
import com.mitenko.hiitcounter.domain.model.Phase.REST
import com.mitenko.hiitcounter.domain.model.Phase.WORK
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class TabataEngineTest {
    private data class Tick(val atMs: Long, val phase: Phase, val set: Int, val left: Int, val elapsed: Int, val paused: Boolean = false)

    private class Recorder {
        val states = mutableListOf<Pair<Long, TimerState>>()
        val cues = mutableListOf<Cue>()
        val ticks get() = states.map { (t, s) -> Tick(t, s.phase, s.set, s.phaseSecondsLeft, s.elapsedSec, s.paused) }
    }

    private val small = TimingConfig(prepareSec = 2, sets = 2, workSec = 3, restSec = 2, cooldownSec = 1)
    private val single5 = TimingConfig(prepareSec = 0, sets = 1, workSec = 5, restSec = 0, cooldownSec = 0)

    private fun TestScope.engine(timing: TimingConfig, reps: List<Int>, rec: Recorder) = TabataEngine(
        timing = timing,
        repsPerSet = reps,
        nowMs = { testScheduler.currentTime },
        onState = { rec.states += testScheduler.currentTime to it },
        onCue = { rec.cues += it },
    )

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `emits the exact tick sequence`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(
            listOf(
                Tick(0, PREPARE, 1, 2, 0), Tick(1000, PREPARE, 1, 1, 1),
                Tick(2000, WORK, 1, 3, 2), Tick(3000, WORK, 1, 2, 3), Tick(4000, WORK, 1, 1, 4),
                Tick(5000, REST, 2, 2, 5), Tick(6000, REST, 2, 1, 6),
                Tick(7000, WORK, 2, 3, 7), Tick(8000, WORK, 2, 2, 8), Tick(9000, WORK, 2, 1, 9),
                Tick(10000, COOLDOWN, 2, 1, 10),
                Tick(11000, DONE, 2, 0, 11),
            ),
            rec.ticks,
        )
    }

    @Test
    fun `reps follow the current or upcoming set`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(prepareSec = 1, sets = 2, workSec = 1, restSec = 1, cooldownSec = 1), listOf(5, 4), rec)
        launch { e.run() }
        advanceUntilIdle()
        val reps = rec.states.map { (_, s) -> s.phase to s.repsThisSet }
        assertEquals(listOf(PREPARE to 5, WORK to 5, REST to 4, WORK to 4, COOLDOWN to 0, DONE to 0), reps)
        assertEquals(9, rec.states.last().second.totalReps)
    }

    @Test
    fun `zero-duration phases are skipped`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(prepareSec = 0, sets = 2, workSec = 1, restSec = 0, cooldownSec = 0), listOf(3, 3), rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(listOf(Tick(0, WORK, 1, 1, 0), Tick(1000, WORK, 2, 1, 1), Tick(2000, DONE, 2, 0, 2)), rec.ticks)
    }

    @Test
    fun `default workout ends at four minutes without drift`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(), List(8) { 8 }, rec)
        launch { e.run() }
        advanceUntilIdle()
        val last = rec.states.last()
        assertEquals(240_000L, last.first)
        assertEquals(DONE, last.second.phase)
        assertEquals(240, last.second.elapsedSec)
        rec.states.forEach { (t, s) -> assertEquals(s.elapsedSec * 1000L, t) }
    }
}
```

(Imports for `assertFalse`, `assertTrue` and `advance` are used by later subtasks; unused-import warnings are fine until then.)

- [ ] **Step 2: Run red** — Run (label `T5-1-RED`). Expected: compile FAIL, unresolved `TabataEngine`.

- [ ] **Step 3: Implement** — `domain/TabataEngine.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.delay

/**
 * Runs one Tabata workout (spec §8). Ticks are scheduled against absolute targets measured
 * with [nowMs] (monotonic), so they never drift. Not thread-safe: drive from one thread.
 */
class TabataEngine(
    private val timing: TimingConfig,
    private val repsPerSet: List<Int>,
    private val nowMs: () -> Long,
    private val onState: (TimerState) -> Unit,
    private val onCue: (Cue) -> Unit, // wired in 5.2
) {
    private data class Step(val phase: Phase, val set: Int, val durationSec: Int)

    init {
        require(repsPerSet.size == timing.sets) { "repsPerSet has ${repsPerSet.size} entries for ${timing.sets} sets" }
    }

    private val steps: List<Step> = buildList {
        if (timing.prepareSec > 0) add(Step(Phase.PREPARE, 1, timing.prepareSec))
        for (set in 1..timing.sets) {
            add(Step(Phase.WORK, set, timing.workSec))
            if (set < timing.sets && timing.restSec > 0) add(Step(Phase.REST, set + 1, timing.restSec))
        }
        if (timing.cooldownSec > 0) add(Step(Phase.COOLDOWN, timing.sets, timing.cooldownSec))
    }
    private val totalReps = repsPerSet.sum()
    private val totalDurationSec = timing.totalDurationSec
    private var startMs = 0L

    suspend fun run() {
        startMs = nowMs()
        var phaseStartMs = 0L
        var phaseStartSec = 0
        for (step in steps) {
            emit(step, secondsLeft = step.durationSec, elapsedSec = phaseStartSec)
            for (k in 1..step.durationSec) {
                awaitActiveMs(phaseStartMs + k * 1000L)
                if (k < step.durationSec) {
                    emit(step, secondsLeft = step.durationSec - k, elapsedSec = phaseStartSec + k)
                }
            }
            phaseStartMs += step.durationSec * 1000L
            phaseStartSec += step.durationSec
        }
        onState(doneState())
    }

    private fun doneState() = TimerState(
        phase = Phase.DONE, set = timing.sets, sets = timing.sets, phaseSecondsLeft = 0, phaseDurationSec = 0,
        elapsedSec = totalDurationSec, totalDurationSec = totalDurationSec, repsThisSet = 0, totalReps = totalReps,
        paused = false,
    )

    private suspend fun awaitActiveMs(targetMs: Long) {
        val remaining = targetMs - (nowMs() - startMs)
        if (remaining > 0) delay(remaining)
    }

    private fun emit(step: Step, secondsLeft: Int, elapsedSec: Int) {
        val reps = if (step.phase == Phase.COOLDOWN) 0 else repsPerSet[step.set - 1]
        onState(
            TimerState(
                phase = step.phase, set = step.set, sets = timing.sets, phaseSecondsLeft = secondsLeft,
                phaseDurationSec = step.durationSec, elapsedSec = elapsedSec, totalDurationSec = totalDurationSec,
                repsThisSet = reps, totalReps = totalReps, paused = false,
            ),
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-1-GREEN`). Expected: PASS (4 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): Tabata phase sequence with drift-free ticks"`

### Subtask 5.2: One-shot cues

- [ ] **Step 1: Failing tests** — append:
```kotlin
    @Test
    fun `emits cues at phase starts and in the final seconds`() = runTest {
        val rec = Recorder()
        val e = engine(small, listOf(5, 4), rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(
            listOf(
                Cue.Countdown(1),
                Cue.PhaseStart(WORK), Cue.Countdown(2), Cue.Countdown(1),
                Cue.PhaseStart(REST), Cue.Countdown(1),
                Cue.PhaseStart(WORK), Cue.Countdown(2), Cue.Countdown(1),
                Cue.PhaseStart(COOLDOWN),
                Cue.Finished,
            ),
            rec.cues,
        )
    }

    @Test
    fun `zero-duration phases emit no cues`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(prepareSec = 0, sets = 2, workSec = 1, restSec = 0, cooldownSec = 0), listOf(3, 3), rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(listOf(Cue.PhaseStart(WORK), Cue.PhaseStart(WORK), Cue.Finished), rec.cues)
    }

    @Test
    fun `finished is emitted exactly once`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(), List(8) { 8 }, rec)
        launch { e.run() }
        advanceUntilIdle()
        assertEquals(1, rec.cues.count { it == Cue.Finished })
        assertEquals(Cue.Finished, rec.cues.last())
    }

    @Test
    fun `cancelling the run emits no DONE and no Finished`() = runTest {
        val rec = Recorder()
        val e = engine(TimingConfig(), List(8) { 8 }, rec)
        val job = launch { e.run() }
        advance(15_000)
        job.cancel()
        advanceUntilIdle()
        assertFalse(rec.states.any { it.second.phase == DONE })
        assertFalse(Cue.Finished in rec.cues)
    }
```

- [ ] **Step 2: Run red** — Run (label `T5-2-RED`). Expected: FAIL — cue lists are empty. (`cancelling the run` already passes — regression guard.)

- [ ] **Step 3: Implement** — replace `TabataEngine.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.delay

/**
 * Runs one Tabata workout (spec §8). Ticks are scheduled against absolute targets measured
 * with [nowMs] (monotonic), so they never drift. Not thread-safe: drive from one thread.
 */
class TabataEngine(
    private val timing: TimingConfig,
    private val repsPerSet: List<Int>,
    private val nowMs: () -> Long,
    private val onState: (TimerState) -> Unit,
    private val onCue: (Cue) -> Unit,
) {
    private data class Step(val phase: Phase, val set: Int, val durationSec: Int)

    init {
        require(repsPerSet.size == timing.sets) { "repsPerSet has ${repsPerSet.size} entries for ${timing.sets} sets" }
    }

    private val steps: List<Step> = buildList {
        if (timing.prepareSec > 0) add(Step(Phase.PREPARE, 1, timing.prepareSec))
        for (set in 1..timing.sets) {
            add(Step(Phase.WORK, set, timing.workSec))
            if (set < timing.sets && timing.restSec > 0) add(Step(Phase.REST, set + 1, timing.restSec))
        }
        if (timing.cooldownSec > 0) add(Step(Phase.COOLDOWN, timing.sets, timing.cooldownSec))
    }
    private val totalReps = repsPerSet.sum()
    private val totalDurationSec = timing.totalDurationSec
    private var startMs = 0L

    suspend fun run() {
        startMs = nowMs()
        var phaseStartMs = 0L
        var phaseStartSec = 0
        for (step in steps) {
            emit(step, secondsLeft = step.durationSec, elapsedSec = phaseStartSec)
            if (step.phase != Phase.PREPARE) onCue(Cue.PhaseStart(step.phase))
            for (k in 1..step.durationSec) {
                awaitActiveMs(phaseStartMs + k * 1000L)
                if (k < step.durationSec) {
                    val left = step.durationSec - k
                    emit(step, secondsLeft = left, elapsedSec = phaseStartSec + k)
                    if (left <= 3) onCue(Cue.Countdown(left))
                }
            }
            phaseStartMs += step.durationSec * 1000L
            phaseStartSec += step.durationSec
        }
        onState(doneState())
        onCue(Cue.Finished)
    }

    private fun doneState() = TimerState(
        phase = Phase.DONE, set = timing.sets, sets = timing.sets, phaseSecondsLeft = 0, phaseDurationSec = 0,
        elapsedSec = totalDurationSec, totalDurationSec = totalDurationSec, repsThisSet = 0, totalReps = totalReps,
        paused = false,
    )

    private suspend fun awaitActiveMs(targetMs: Long) {
        val remaining = targetMs - (nowMs() - startMs)
        if (remaining > 0) delay(remaining)
    }

    private fun emit(step: Step, secondsLeft: Int, elapsedSec: Int) {
        val reps = if (step.phase == Phase.COOLDOWN) 0 else repsPerSet[step.set - 1]
        onState(
            TimerState(
                phase = step.phase, set = step.set, sets = timing.sets, phaseSecondsLeft = secondsLeft,
                phaseDurationSec = step.durationSec, elapsedSec = elapsedSec, totalDurationSec = totalDurationSec,
                repsThisSet = reps, totalReps = totalReps, paused = false,
            ),
        )
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-2-GREEN`). Expected: PASS (8 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): one-shot phase-start, countdown and finished cues"`

### Subtask 5.3: Pause and resume

- [ ] **Step 1: Failing tests** — append:
```kotlin
    @Test
    fun `pause mid-second keeps the sub-second remainder`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        launch { e.run() }
        runCurrent()
        advance(1500)
        e.pause()
        advance(10_000)
        e.resume()
        advance(500)
        assertEquals(
            listOf(
                Tick(0, WORK, 1, 5, 0),
                Tick(1000, WORK, 1, 4, 1),
                Tick(1500, WORK, 1, 4, 1, paused = true),
                Tick(11_500, WORK, 1, 4, 1, paused = false),
                Tick(12_000, WORK, 1, 3, 2),
            ),
            rec.ticks,
        )
    }

    @Test
    fun `pause exactly at a boundary resumes a full second later`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        launch { e.run() }
        runCurrent()
        advance(2000)
        e.pause()
        advance(3000)
        e.resume()
        advance(1000)
        assertEquals(Tick(6000, WORK, 1, 2, 3), rec.ticks.last())
    }

    @Test
    fun `pause and resume are idempotent`() = runTest {
        val rec = Recorder()
        val e = engine(single5, listOf(5), rec)
        launch { e.run() }
        runCurrent()
        e.resume()
        e.pause()
        e.pause()
        assertTrue(e.isPaused)
        e.resume()
        e.resume()
        assertFalse(e.isPaused)
        assertEquals(listOf(false, true, false), rec.states.map { it.second.paused })
    }
```

- [ ] **Step 2: Run red** — Run (label `T5-3-RED`). Expected: compile FAIL, unresolved `pause`, `resume`, `isPaused`.

- [ ] **Step 3: Implement** — replace `TabataEngine.kt` with the final version:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs one Tabata workout (spec §8). Ticks are scheduled against absolute active-time
 * targets measured with [nowMs] (monotonic), so they never drift and pausing keeps the
 * sub-second remainder. Not thread-safe: call [run], [pause] and [resume] from one thread.
 */
class TabataEngine(
    private val timing: TimingConfig,
    private val repsPerSet: List<Int>,
    private val nowMs: () -> Long,
    private val onState: (TimerState) -> Unit,
    private val onCue: (Cue) -> Unit,
) {
    private data class Step(val phase: Phase, val set: Int, val durationSec: Int)

    init {
        require(repsPerSet.size == timing.sets) { "repsPerSet has ${repsPerSet.size} entries for ${timing.sets} sets" }
    }

    private val steps: List<Step> = buildList {
        if (timing.prepareSec > 0) add(Step(Phase.PREPARE, 1, timing.prepareSec))
        for (set in 1..timing.sets) {
            add(Step(Phase.WORK, set, timing.workSec))
            if (set < timing.sets && timing.restSec > 0) add(Step(Phase.REST, set + 1, timing.restSec))
        }
        if (timing.cooldownSec > 0) add(Step(Phase.COOLDOWN, timing.sets, timing.cooldownSec))
    }
    private val totalReps = repsPerSet.sum()
    private val totalDurationSec = timing.totalDurationSec

    private val paused = MutableStateFlow(false)
    private var activeBaseMs = 0L
    private var runningSinceMs: Long? = null
    private var current: TimerState? = null

    val isPaused: Boolean get() = paused.value

    suspend fun run() {
        runningSinceMs = nowMs()
        var phaseStartMs = 0L
        var phaseStartSec = 0
        for (step in steps) {
            emit(step, secondsLeft = step.durationSec, elapsedSec = phaseStartSec)
            if (step.phase != Phase.PREPARE) onCue(Cue.PhaseStart(step.phase))
            for (k in 1..step.durationSec) {
                awaitActiveMs(phaseStartMs + k * 1000L)
                if (k < step.durationSec) {
                    val left = step.durationSec - k
                    emit(step, secondsLeft = left, elapsedSec = phaseStartSec + k)
                    if (left <= 3) onCue(Cue.Countdown(left))
                }
            }
            phaseStartMs += step.durationSec * 1000L
            phaseStartSec += step.durationSec
        }
        runningSinceMs = null
        val done = TimerState(
            phase = Phase.DONE, set = timing.sets, sets = timing.sets, phaseSecondsLeft = 0, phaseDurationSec = 0,
            elapsedSec = totalDurationSec, totalDurationSec = totalDurationSec, repsThisSet = 0, totalReps = totalReps,
            paused = false,
        )
        current = done
        onState(done)
        onCue(Cue.Finished)
    }

    fun pause() {
        val since = runningSinceMs ?: return
        if (paused.value) return
        activeBaseMs += nowMs() - since
        runningSinceMs = null
        paused.value = true
        current?.copy(paused = true)?.let { current = it; onState(it) }
    }

    fun resume() {
        if (!paused.value) return
        runningSinceMs = nowMs()
        paused.value = false
        current?.copy(paused = false)?.let { current = it; onState(it) }
    }

    private fun activeMs(): Long = activeBaseMs + (runningSinceMs?.let { nowMs() - it } ?: 0L)

    private suspend fun awaitActiveMs(targetMs: Long) {
        while (true) {
            paused.first { !it }
            val remaining = targetMs - activeMs()
            if (remaining <= 0) return
            withTimeoutOrNull(remaining) { paused.first { it } }
        }
    }

    private fun emit(step: Step, secondsLeft: Int, elapsedSec: Int) {
        val reps = if (step.phase == Phase.COOLDOWN) 0 else repsPerSet[step.set - 1]
        val state = TimerState(
            phase = step.phase, set = step.set, sets = timing.sets, phaseSecondsLeft = secondsLeft,
            phaseDurationSec = step.durationSec, elapsedSec = elapsedSec, totalDurationSec = totalDurationSec,
            repsThisSet = reps, totalReps = totalReps, paused = paused.value,
        )
        current = state
        onState(state)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T5-3-GREEN`). Expected: PASS (11 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): pause/resume keeping the sub-second remainder"`

**Task 5 gate:** Run (label `T5-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 6: TimerController (§4, §8)

**Interfaces produced:**
- `data class WorkoutSnapshot(val timing: TimingConfig, val cues: CueConfig)`
- `enum class RunStatus { IDLE, PREPARING, RUNNING, DONE }`
- `sealed interface ServiceStatus { Pending; Started; Failed(reason: String) }`
- `class TimerController(scope: CoroutineScope, nowMs: () -> Long)`: `status: StateFlow<RunStatus>`, `state: StateFlow<TimerState?>`, `cues: SharedFlow<Cue>` (replay 0), `serviceStatus: StateFlow<ServiceStatus>`, `snapshot: WorkoutSnapshot?`, `prepare(WorkoutSnapshot): Boolean`, `onServiceStarted()`, `onServiceFailed(reason)`, `cancelPrepare()`, `start(repsPerSet): Boolean`, `pause()`, `resume()`, `stop()`, `dismissDone()`, `MAX_PAUSE_MS = 1_800_000L`.

**Files (all subtasks):** `domain/TimerController.kt`; test `domain/TimerControllerTest.kt`. Test command: `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.TimerControllerTest"`.

### Subtask 6.1: Prepare / start / done lifecycle

- [ ] **Step 1: Failing tests** — create `TimerControllerTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    private val snapshot = WorkoutSnapshot(TimingConfig(), CueConfig())
    private val reps = List(8) { 8 }

    private fun TestScope.controller() = TimerController(backgroundScope) { testScheduler.currentTime }

    private fun TestScope.running(): TimerController = controller().also {
        it.prepare(snapshot)
        it.onServiceStarted()
        it.start(reps)
        runCurrent()
    }

    @Test
    fun `prepare only from idle`() = runTest {
        val c = controller()
        assertTrue(c.prepare(snapshot))
        assertEquals(RunStatus.PREPARING, c.status.value)
        assertEquals(ServiceStatus.Pending, c.serviceStatus.value)
        assertEquals(snapshot, c.snapshot)
        assertFalse(c.prepare(snapshot))
    }

    @Test
    fun `start requires prepare and runs once`() = runTest {
        val c = controller()
        assertFalse(c.start(reps))
        c.prepare(snapshot)
        c.onServiceStarted()
        assertTrue(c.start(reps))
        assertFalse(c.start(reps))
    }

    @Test
    fun `start requires the foreground service to have started`() = runTest {
        val pending = controller()
        pending.prepare(snapshot)
        assertFalse(pending.start(reps))
        assertEquals(RunStatus.PREPARING, pending.status.value)
        val failed = controller()
        failed.prepare(snapshot)
        failed.onServiceFailed("boom")
        assertFalse(failed.start(reps))
    }

    @Test
    fun `service status is reported while preparing`() = runTest {
        val a = controller()
        a.prepare(snapshot)
        a.onServiceStarted()
        assertEquals(ServiceStatus.Started, a.serviceStatus.value)
        val b = controller()
        b.prepare(snapshot)
        b.onServiceFailed("boom")
        assertEquals(ServiceStatus.Failed("boom"), b.serviceStatus.value)
    }

    @Test
    fun `cancelPrepare returns to idle`() = runTest {
        val c = controller()
        c.prepare(snapshot)
        c.cancelPrepare()
        assertEquals(RunStatus.IDLE, c.status.value)
        assertNull(c.snapshot)
    }

    @Test
    fun `runs to done and dismisses to idle`() = runTest {
        val c = running()
        assertEquals(RunStatus.RUNNING, c.status.value)
        assertEquals(Phase.PREPARE, c.state.value?.phase)
        advanceTimeBy(240_000)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
        assertEquals(Phase.DONE, c.state.value?.phase)
        c.dismissDone()
        assertEquals(RunStatus.IDLE, c.status.value)
        assertNull(c.state.value)
        assertTrue(c.prepare(snapshot))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T6-1-RED`). Expected: compile FAIL, unresolved `TimerController`, `WorkoutSnapshot`, `RunStatus`, `ServiceStatus`.

- [ ] **Step 3: Implement** — `domain/TimerController.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkoutSnapshot(val timing: TimingConfig, val cues: CueConfig)

enum class RunStatus { IDLE, PREPARING, RUNNING, DONE }

sealed interface ServiceStatus {
    data object Pending : ServiceStatus
    data object Started : ServiceStatus
    data class Failed(val reason: String) : ServiceStatus
}

/**
 * Owns the single running workout (spec §4, §8). Commands are idempotent. Must be used
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

    private val _serviceStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Pending)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    var snapshot: WorkoutSnapshot? = null
        private set

    private var engine: TabataEngine? = null
    private var runJob: Job? = null

    fun prepare(snapshot: WorkoutSnapshot): Boolean {
        if (_status.value == RunStatus.PREPARING || _status.value == RunStatus.RUNNING) return false
        clearRun() // clears the previous snapshot; assign the new one after
        this.snapshot = snapshot
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
        // The timer only runs under a foreground service (spec §4).
        if (_status.value != RunStatus.PREPARING || _serviceStatus.value != ServiceStatus.Started) return false
        val e = TabataEngine(
            timing = snap.timing,
            repsPerSet = repsPerSet,
            nowMs = nowMs,
            onState = { _state.value = it },
            onCue = { }, // cue stream added in 6.2
        )
        engine = e
        _status.value = RunStatus.RUNNING
        runJob = scope.launch {
            e.run()
            _status.value = RunStatus.DONE
        }
        return true
    }

    fun dismissDone() {
        if (_status.value != RunStatus.DONE) return
        clearRun()
        _status.value = RunStatus.IDLE
    }

    private fun clearRun() {
        runJob?.cancel()
        runJob = null
        engine = null
        snapshot = null
        _state.value = null
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T6-1-GREEN`). Expected: PASS (6 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): timer controller prepare/start/done lifecycle"`

### Subtask 6.2: Stop and the one-shot cue stream

- [ ] **Step 1: Failing tests** — append:
```kotlin
    @Test
    fun `stop goes idle without a finished cue and is idempotent`() = runTest {
        val c = controller()
        val cues = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { cues += it } }
        c.prepare(snapshot)
        c.onServiceStarted()
        c.start(reps)
        advanceTimeBy(15_000)
        runCurrent()
        c.stop()
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(RunStatus.IDLE, c.status.value)
        assertNull(c.state.value)
        assertTrue(cues.isNotEmpty())
        assertFalse(Cue.Finished in cues)
        c.stop()
        assertEquals(RunStatus.IDLE, c.status.value)
    }

    @Test
    fun `cues are one-shot and never replayed`() = runTest {
        val c = controller()
        val first = mutableListOf<Cue>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { first += it } }
        c.prepare(snapshot)
        c.onServiceStarted()
        c.start(reps)
        advanceTimeBy(11_000)
        runCurrent()
        assertEquals(listOf(Cue.Countdown(3), Cue.Countdown(2), Cue.Countdown(1), Cue.PhaseStart(Phase.WORK)), first)
        job.cancel()
        val second = mutableListOf<Cue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.cues.collect { second += it } }
        runCurrent()
        assertTrue(second.isEmpty())
    }
```

- [ ] **Step 2: Run red** — Run (label `T6-2-RED`). Expected: compile FAIL, unresolved `cues`, `stop`.

- [ ] **Step 3: Implement** — replace `TimerController.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkoutSnapshot(val timing: TimingConfig, val cues: CueConfig)

enum class RunStatus { IDLE, PREPARING, RUNNING, DONE }

sealed interface ServiceStatus {
    data object Pending : ServiceStatus
    data object Started : ServiceStatus
    data class Failed(val reason: String) : ServiceStatus
}

/**
 * Owns the single running workout (spec §4, §8). Commands are idempotent. Must be used
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

    private var engine: TabataEngine? = null
    private var runJob: Job? = null

    fun prepare(snapshot: WorkoutSnapshot): Boolean {
        if (_status.value == RunStatus.PREPARING || _status.value == RunStatus.RUNNING) return false
        clearRun() // clears the previous snapshot; assign the new one after
        this.snapshot = snapshot
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
        // The timer only runs under a foreground service (spec §4).
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
            _status.value = RunStatus.DONE
        }
        return true
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

    private fun clearRun() {
        runJob?.cancel()
        runJob = null
        engine = null
        snapshot = null
        _state.value = null
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T6-2-GREEN`). Expected: PASS (8 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): controller stop and one-shot cue stream"`

### Subtask 6.3: Pause, resume and max-pause auto-stop

- [ ] **Step 1: Failing tests** — append:
```kotlin
    @Test
    fun `pausing for the maximum auto-stops`() = runTest {
        val c = running()
        c.pause()
        advanceTimeBy(TimerController.MAX_PAUSE_MS - 1)
        runCurrent()
        assertEquals(RunStatus.RUNNING, c.status.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(RunStatus.IDLE, c.status.value)
    }

    @Test
    fun `resume cancels the pause timeout`() = runTest {
        val c = running()
        c.pause()
        advanceTimeBy(20 * 60_000L)
        c.resume()
        advanceTimeBy(20 * 60_000L)
        runCurrent()
        assertEquals(RunStatus.DONE, c.status.value)
    }

    @Test
    fun `pause and resume are idempotent and ignored when not running`() = runTest {
        val idle = controller()
        idle.pause()
        idle.resume()
        assertEquals(RunStatus.IDLE, idle.status.value)

        val c = running()
        c.resume()
        c.pause()
        c.pause()
        assertTrue(c.state.value!!.paused)
        c.resume()
        c.resume()
        assertFalse(c.state.value!!.paused)
    }

    @Test
    fun `a resumed pause's timeout cannot stop a later pause`() = runTest {
        val c = running()
        c.pause()
        advanceTimeBy(TimerController.MAX_PAUSE_MS - 1_000)
        c.resume()
        runCurrent()
        c.pause()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(RunStatus.RUNNING, c.status.value)
        assertTrue(c.state.value!!.paused)
    }
```

(Cancelling the first timeout job is enough — a cancelled `delay` never completes — so no pause-generation counter is needed; this test guards it.)

- [ ] **Step 2: Run red** — Run (label `T6-3-RED`). Expected: compile FAIL, unresolved `pause`, `resume`, `MAX_PAUSE_MS`.

- [ ] **Step 3: Implement** — replace `TimerController.kt` with the final version:
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

data class WorkoutSnapshot(val timing: TimingConfig, val cues: CueConfig)

enum class RunStatus { IDLE, PREPARING, RUNNING, DONE }

sealed interface ServiceStatus {
    data object Pending : ServiceStatus
    data object Started : ServiceStatus
    data class Failed(val reason: String) : ServiceStatus
}

/**
 * Owns the single running workout (spec §4, §8). Commands are idempotent. Must be used
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

    private var engine: TabataEngine? = null
    private var runJob: Job? = null
    private var pauseTimeoutJob: Job? = null

    fun prepare(snapshot: WorkoutSnapshot): Boolean {
        if (_status.value == RunStatus.PREPARING || _status.value == RunStatus.RUNNING) return false
        clearRun() // clears the previous snapshot; assign the new one after
        this.snapshot = snapshot
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
        // The timer only runs under a foreground service (spec §4).
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

- [ ] **Step 4: Run green** — Run (label `T6-3-GREEN`). Expected: PASS (12 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): controller pause/resume with 30-minute auto-stop"`

**Task 6 gate:** Run (label `T6-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 7: SettingsValidator (§10)

**Interfaces produced:**
- `enum class Field { PREPARE, SETS, WORK, REST, COOLDOWN, TOTAL_DURATION, STARTING_TOTAL, FLOOR, CAP, HOLD_AT, HOLD_FOR, WINDOW_HOURS, PENALTY_RATE, TOTAL, BEST_STREAK, CURRENT_STREAK, LAST_CHECK_IN }`
- `data class ValidationResult(errors: Map<Field, String> = emptyMap(), hints: Map<Field, String> = emptyMap())` + `isValid`
- `object SettingsValidator { MAX_PHASE_SEC = 3599; MAX_SETS = 20; MAX_TOTAL_SEC = 7200; NOT_A_NUMBER = "Enter a number"; timing(TimingConfig); progression(ProgressionConfig); currentState(total, bestStreak, currentStreak, lastCheckIn: Instant?, now: Instant, config: ProgressionConfig) }`

**Files (all subtasks):** `domain/SettingsValidator.kt`; test `domain/SettingsValidatorTest.kt`. Test command: `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.SettingsValidatorTest"`.

### Subtask 7.1: Timing validation incl. 2-hour cap

- [ ] **Step 1: Failing tests** — create `SettingsValidatorTest.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SettingsValidatorTest {
    private fun ValidationResult.errorFields() = errors.keys

    @Test
    fun `default timing is valid`() {
        assertTrue(SettingsValidator.timing(TimingConfig()).isValid)
    }

    @Test
    fun `timing field ranges`() {
        assertEquals(setOf(Field.SETS), SettingsValidator.timing(TimingConfig(sets = 0)).errorFields())
        assertEquals(setOf(Field.SETS), SettingsValidator.timing(TimingConfig(sets = 21)).errorFields())
        assertEquals(setOf(Field.WORK), SettingsValidator.timing(TimingConfig(workSec = 0)).errorFields())
        assertEquals(setOf(Field.REST), SettingsValidator.timing(TimingConfig(restSec = -1)).errorFields())
        assertEquals(setOf(Field.PREPARE), SettingsValidator.timing(TimingConfig(prepareSec = 3600)).errorFields())
        assertTrue(SettingsValidator.timing(TimingConfig(prepareSec = 0, restSec = 0, cooldownSec = 0)).isValid)
    }

    @Test
    fun `derived total above two hours is rejected`() {
        val nearly = TimingConfig(prepareSec = 0, sets = 20, workSec = 300, restSec = 60, cooldownSec = 0) // 7140 s
        assertTrue(SettingsValidator.timing(nearly).isValid)
        assertEquals(setOf(Field.TOTAL_DURATION), SettingsValidator.timing(nearly.copy(cooldownSec = 61)).errorFields())
    }
}
```

(`ProgressionConfig`, `Instant`, `assertFalse` imports are used by 7.2/7.3.)

- [ ] **Step 2: Run red** — Run (label `T7-1-RED`). Expected: compile FAIL, unresolved `SettingsValidator`, `Field`, `ValidationResult`.

- [ ] **Step 3: Implement** — `domain/SettingsValidator.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.TimingConfig

enum class Field {
    PREPARE, SETS, WORK, REST, COOLDOWN, TOTAL_DURATION,
    STARTING_TOTAL, FLOOR, CAP, HOLD_AT, HOLD_FOR, WINDOW_HOURS, PENALTY_RATE,
    TOTAL, BEST_STREAK, CURRENT_STREAK, LAST_CHECK_IN,
}

data class ValidationResult(
    val errors: Map<Field, String> = emptyMap(),
    val hints: Map<Field, String> = emptyMap(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Settings validation — spec §10. */
object SettingsValidator {
    const val MAX_PHASE_SEC = 59 * 60 + 59
    const val MAX_SETS = 20
    const val MAX_TOTAL_SEC = 2 * 60 * 60
    const val NOT_A_NUMBER = "Enter a number"

    fun timing(c: TimingConfig): ValidationResult {
        val e = mutableMapOf<Field, String>()
        if (c.sets !in 1..MAX_SETS) e[Field.SETS] = "1–$MAX_SETS sets"
        if (c.workSec !in 1..MAX_PHASE_SEC) e[Field.WORK] = "1 s – 59:59"
        if (c.prepareSec !in 0..MAX_PHASE_SEC) e[Field.PREPARE] = "0 – 59:59"
        if (c.restSec !in 0..MAX_PHASE_SEC) e[Field.REST] = "0 – 59:59"
        if (c.cooldownSec !in 0..MAX_PHASE_SEC) e[Field.COOLDOWN] = "0 – 59:59"
        if (e.isEmpty() && c.totalDurationSec > MAX_TOTAL_SEC) e[Field.TOTAL_DURATION] = "Workout longer than 2:00:00"
        return ValidationResult(e)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T7-1-GREEN`). Expected: PASS (3 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): timing validation with 2h total cap"`

### Subtask 7.2: Progression validation

- [ ] **Step 1: Failing tests** — append:
```kotlin
    @Test
    fun `progression ordering rules`() {
        assertTrue(SettingsValidator.progression(ProgressionConfig()).isValid)
        assertEquals(setOf(Field.FLOOR), SettingsValidator.progression(ProgressionConfig(floor = 0)).errorFields())
        assertEquals(setOf(Field.STARTING_TOTAL), SettingsValidator.progression(ProgressionConfig(startingTotal = 40)).errorFields())
        assertEquals(setOf(Field.CAP), SettingsValidator.progression(ProgressionConfig(cap = 47)).errorFields())
        assertEquals(setOf(Field.HOLD_FOR), SettingsValidator.progression(ProgressionConfig(holdFor = -1)).errorFields())
        assertEquals(setOf(Field.WINDOW_HOURS), SettingsValidator.progression(ProgressionConfig(windowHours = 0)).errorFields())
        assertEquals(setOf(Field.PENALTY_RATE), SettingsValidator.progression(ProgressionConfig(penaltyHoursPerRep = 0.0)).errorFields())
        assertEquals(setOf(Field.PENALTY_RATE), SettingsValidator.progression(ProgressionConfig(penaltyHoursPerRep = Double.NaN)).errorFields())
    }

    @Test
    fun `hold outside floor to cap is allowed with a hint`() {
        val r = SettingsValidator.progression(ProgressionConfig(holdAt = 80))
        assertTrue(r.isValid)
        assertTrue(Field.HOLD_AT in r.hints)
        assertTrue(Field.HOLD_AT in SettingsValidator.progression(ProgressionConfig(holdFor = 0)).hints)
        assertFalse(Field.HOLD_AT in SettingsValidator.progression(ProgressionConfig()).hints)
    }
```

- [ ] **Step 2: Run red** — Run (label `T7-2-RED`). Expected: compile FAIL, unresolved `progression`.

- [ ] **Step 3: Implement** — in `SettingsValidator.kt` add the import `import com.mitenko.hiitcounter.domain.model.ProgressionConfig` and add inside `object SettingsValidator`, after `timing`:
```kotlin
    fun progression(c: ProgressionConfig): ValidationResult {
        val e = mutableMapOf<Field, String>()
        if (c.floor < 1) e[Field.FLOOR] = "Must be at least 1"
        if (c.startingTotal < c.floor) e[Field.STARTING_TOTAL] = "Must be ≥ floor"
        if (c.cap < c.startingTotal) e[Field.CAP] = "Must be ≥ starting total"
        if (c.holdAt < 1) e[Field.HOLD_AT] = "Must be at least 1"
        if (c.holdFor < 0) e[Field.HOLD_FOR] = "Must be 0 or more"
        if (c.windowHours < 1) e[Field.WINDOW_HOURS] = "Must be at least 1"
        if (!(c.penaltyHoursPerRep > 0.0) || !c.penaltyHoursPerRep.isFinite()) e[Field.PENALTY_RATE] = "Must be greater than 0"
        val hints = if (Field.HOLD_AT !in e && !c.holdEnabled) mapOf(Field.HOLD_AT to "Hold disabled") else emptyMap()
        return ValidationResult(e, hints)
    }
```

- [ ] **Step 4: Run green** — Run (label `T7-2-GREEN`). Expected: PASS (5 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): progression validation with hold-disabled hint"`

### Subtask 7.3: Current-state validation

- [ ] **Step 1: Failing test** — append:
```kotlin
    @Test
    fun `current state rules`() {
        val now = Instant.parse("2026-09-24T12:00:00Z")
        val cfg = ProgressionConfig()
        assertTrue(SettingsValidator.currentState(65, 24, 4, now.minusSeconds(60), now, cfg).isValid)
        assertEquals(setOf(Field.TOTAL), SettingsValidator.currentState(0, 0, 0, null, now, cfg).errorFields())
        assertEquals(setOf(Field.BEST_STREAK), SettingsValidator.currentState(65, 3, 4, null, now, cfg).errorFields())
        assertEquals(setOf(Field.BEST_STREAK, Field.CURRENT_STREAK), SettingsValidator.currentState(65, -1, -2, null, now, cfg).errorFields())
        assertEquals(setOf(Field.LAST_CHECK_IN), SettingsValidator.currentState(65, 24, 4, now.plusSeconds(60), now, cfg).errorFields())
        val outside = SettingsValidator.currentState(80, 24, 4, null, now, cfg)
        assertTrue(outside.isValid)
        assertTrue(Field.TOTAL in outside.hints)
    }
```

- [ ] **Step 2: Run red** — Run (label `T7-3-RED`). Expected: compile FAIL, unresolved `currentState`.

- [ ] **Step 3: Implement** — in `SettingsValidator.kt` add `import java.time.Instant` and add inside the object, after `progression`:
```kotlin
    fun currentState(
        total: Int,
        bestStreak: Int,
        currentStreak: Int,
        lastCheckIn: Instant?,
        now: Instant,
        config: ProgressionConfig,
    ): ValidationResult {
        val e = mutableMapOf<Field, String>()
        val hints = mutableMapOf<Field, String>()
        if (total < 1) e[Field.TOTAL] = "Must be at least 1"
        else if (total !in config.floor..config.cap) hints[Field.TOTAL] = "Outside floor–cap; clamped at the next check-in"
        if (currentStreak < 0) e[Field.CURRENT_STREAK] = "Must be 0 or more"
        if (bestStreak < 0) e[Field.BEST_STREAK] = "Must be 0 or more"
        else if (bestStreak < currentStreak) e[Field.BEST_STREAK] = "Must be ≥ current streak"
        if (lastCheckIn != null && lastCheckIn.isAfter(now)) e[Field.LAST_CHECK_IN] = "Can't be in the future"
        return ValidationResult(e, hints)
    }
```

- [ ] **Step 4: Run green** — Run (label `T7-3-GREEN`). Expected: PASS (6 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): current-state validation"`

**Task 7 gate:** Run (label `T7-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 8: DataStore repositories and DI (§5)

**Interfaces produced:**
- `interface SettingsRepository { timing: Flow<TimingConfig>; progression: Flow<ProgressionConfig>; cues: Flow<CueConfig>; notificationPermissionAsked: Flow<Boolean>; suspend setTiming/setProgression/setCues(config); suspend markNotificationPermissionAsked() }` + `DataStoreSettingsRepository(store)`
- `interface CounterRepository { state: Flow<CounterState>; suspend checkIn(config: ProgressionConfig, clock: Clock): CheckInResult; suspend overwrite(total, bestStreak, currentStreak, lastCheckIn: Instant?); suspend resetHoldCount(); suspend resetProgress() }` + `DataStoreCounterRepository(store, settings)`
- Hilt `@Singleton` bindings: `Clock` (= `AndroidClock`), `@ApplicationScope CoroutineScope`, `TimerController`, `SettingsRepository`, `CounterRepository`
- Test fakes: `FakeSettingsRepository` (`timingFlow`, `progressionFlow`, `cuesFlow`, `askedFlow`), `FakeCounterRepository` (`stateFlow`, `checkInCalls`, `overwriteCalls`, `resetHoldCountCalls`, `resetProgressCalls`)

### Subtask 8.1: Settings repository

**Files:** Create `data/PreferenceKeys.kt`, `data/SettingsRepository.kt`; test `data/DataStoreSettingsRepositoryTest.kt`, `testutil/FakeSettingsRepository.kt`.

- [ ] **Step 1: Failing tests** — `app/src/test/kotlin/com/mitenko/hiitcounter/data/DataStoreSettingsRepositoryTest.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "settings.preferences_pb") })

    @Test
    fun `empty store yields defaults`() = runTest {
        val repo = DataStoreSettingsRepository(store())
        assertEquals(TimingConfig(), repo.timing.first())
        assertEquals(ProgressionConfig(), repo.progression.first())
        assertEquals(CueConfig(), repo.cues.first())
        assertFalse(repo.notificationPermissionAsked.first())
    }

    @Test
    fun `values round-trip`() = runTest {
        val repo = DataStoreSettingsRepository(store())
        val timing = TimingConfig(prepareSec = 5, sets = 6, workSec = 30, restSec = 15, cooldownSec = 60)
        val progression = ProgressionConfig(startingTotal = 50, floor = 40, cap = 80, holdAt = 70, holdFor = 3, windowHours = 30, penaltyHoursPerRep = 12.5)
        repo.setTiming(timing)
        repo.setProgression(progression)
        repo.setCues(CueConfig(sound = false, vibration = true))
        repo.markNotificationPermissionAsked()
        assertEquals(timing, repo.timing.first())
        assertEquals(progression, repo.progression.first())
        assertEquals(CueConfig(sound = false, vibration = true), repo.cues.first())
        assertTrue(repo.notificationPermissionAsked.first())
    }

    @Test
    fun `invalid single timing value falls back per key`() = runTest {
        val s = store()
        s.edit { it[intPreferencesKey("sets")] = 99; it[intPreferencesKey("work_sec")] = 30 }
        val t = DataStoreSettingsRepository(s).timing.first()
        assertEquals(8, t.sets)
        assertEquals(30, t.workSec)
    }

    @Test
    fun `inconsistent progression falls back to defaults`() = runTest {
        val s = store()
        s.edit { it[intPreferencesKey("floor")] = 80; it[intPreferencesKey("cap")] = 60 }
        assertEquals(ProgressionConfig(), DataStoreSettingsRepository(s).progression.first())
    }

    @Test
    fun `non-finite penalty falls back`() = runTest {
        val s = store()
        s.edit { it[doublePreferencesKey("penalty_hours_per_rep")] = Double.NaN }
        assertEquals(19.5, DataStoreSettingsRepository(s).progression.first().penaltyHoursPerRep, 0.0)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T8-1-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.DataStoreSettingsRepositoryTest"`. Expected: compile FAIL, unresolved `DataStoreSettingsRepository`.

- [ ] **Step 3: Implement**

`data/PreferenceKeys.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/** Persistence keys — spec §5 table. */
internal object Keys {
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
```

`data/SettingsRepository.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface SettingsRepository {
    val timing: Flow<TimingConfig>
    val progression: Flow<ProgressionConfig>
    val cues: Flow<CueConfig>
    val notificationPermissionAsked: Flow<Boolean>
    suspend fun setTiming(config: TimingConfig)
    suspend fun setProgression(config: ProgressionConfig)
    suspend fun setCues(config: CueConfig)
    suspend fun markNotificationPermissionAsked()
}

private const val TAG = "SettingsRepository"

/** Per-key fallback (spec §5): an invalid stored value is replaced by its default and logged. */
internal fun <T> Preferences.valid(key: Preferences.Key<T>, default: T, ok: (T) -> Boolean): T {
    val value = this[key] ?: return default
    if (ok(value)) return value
    Log.w(TAG, "Invalid ${key.name}=$value; using default $default")
    return default
}

internal fun Preferences.readTiming(): TimingConfig {
    val d = TimingConfig()
    val max = SettingsValidator.MAX_PHASE_SEC
    val c = TimingConfig(
        prepareSec = valid(Keys.PREPARE_SEC, d.prepareSec) { it in 0..max },
        sets = valid(Keys.SETS, d.sets) { it in 1..SettingsValidator.MAX_SETS },
        workSec = valid(Keys.WORK_SEC, d.workSec) { it in 1..max },
        restSec = valid(Keys.REST_SEC, d.restSec) { it in 0..max },
        cooldownSec = valid(Keys.COOLDOWN_SEC, d.cooldownSec) { it in 0..max },
    )
    if (SettingsValidator.timing(c).isValid) return c
    Log.w(TAG, "Stored timing inconsistent ($c); using defaults")
    return d
}

internal fun Preferences.readProgression(): ProgressionConfig {
    val d = ProgressionConfig()
    val c = ProgressionConfig(
        startingTotal = valid(Keys.STARTING_TOTAL, d.startingTotal) { it >= 1 },
        floor = valid(Keys.FLOOR, d.floor) { it >= 1 },
        cap = valid(Keys.CAP, d.cap) { it >= 1 },
        holdAt = valid(Keys.HOLD_AT, d.holdAt) { it >= 1 },
        holdFor = valid(Keys.HOLD_FOR, d.holdFor) { it >= 0 },
        windowHours = valid(Keys.WINDOW_HOURS, d.windowHours) { it >= 1 },
        penaltyHoursPerRep = valid(Keys.PENALTY_HOURS_PER_REP, d.penaltyHoursPerRep) { it > 0.0 && it.isFinite() },
    )
    if (SettingsValidator.progression(c).isValid) return c
    Log.w(TAG, "Stored progression inconsistent ($c); using defaults")
    return d
}

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) {
            Log.e(TAG, "Settings read failed; using defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    override val timing: Flow<TimingConfig> = prefs.map { it.readTiming() }
    override val progression: Flow<ProgressionConfig> = prefs.map { it.readProgression() }
    override val cues: Flow<CueConfig> = prefs.map {
        CueConfig(sound = it[Keys.CUE_SOUND] ?: true, vibration = it[Keys.CUE_VIBRATION] ?: true)
    }
    override val notificationPermissionAsked: Flow<Boolean> = prefs.map { it[Keys.NOTIFICATION_ASKED] ?: false }

    override suspend fun setTiming(config: TimingConfig) {
        store.edit {
            it[Keys.PREPARE_SEC] = config.prepareSec
            it[Keys.SETS] = config.sets
            it[Keys.WORK_SEC] = config.workSec
            it[Keys.REST_SEC] = config.restSec
            it[Keys.COOLDOWN_SEC] = config.cooldownSec
        }
    }

    override suspend fun setProgression(config: ProgressionConfig) {
        store.edit {
            it[Keys.STARTING_TOTAL] = config.startingTotal
            it[Keys.FLOOR] = config.floor
            it[Keys.CAP] = config.cap
            it[Keys.HOLD_AT] = config.holdAt
            it[Keys.HOLD_FOR] = config.holdFor
            it[Keys.WINDOW_HOURS] = config.windowHours
            it[Keys.PENALTY_HOURS_PER_REP] = config.penaltyHoursPerRep
        }
    }

    override suspend fun setCues(config: CueConfig) {
        store.edit {
            it[Keys.CUE_SOUND] = config.sound
            it[Keys.CUE_VIBRATION] = config.vibration
        }
    }

    override suspend fun markNotificationPermissionAsked() {
        store.edit { it[Keys.NOTIFICATION_ASKED] = true }
    }
}
```

`app/src/test/kotlin/com/mitenko/hiitcounter/testutil/FakeSettingsRepository.kt` (used from 8.2 on):
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(
    timing: TimingConfig = TimingConfig(),
    progression: ProgressionConfig = ProgressionConfig(),
    cues: CueConfig = CueConfig(),
    asked: Boolean = false,
) : SettingsRepository {
    val timingFlow = MutableStateFlow(timing)
    val progressionFlow = MutableStateFlow(progression)
    val cuesFlow = MutableStateFlow(cues)
    val askedFlow = MutableStateFlow(asked)

    override val timing: Flow<TimingConfig> = timingFlow
    override val progression: Flow<ProgressionConfig> = progressionFlow
    override val cues: Flow<CueConfig> = cuesFlow
    override val notificationPermissionAsked: Flow<Boolean> = askedFlow

    override suspend fun setTiming(config: TimingConfig) { timingFlow.value = config }
    override suspend fun setProgression(config: ProgressionConfig) { progressionFlow.value = config }
    override suspend fun setCues(config: CueConfig) { cuesFlow.value = config }
    override suspend fun markNotificationPermissionAsked() { askedFlow.value = true }
}
```

- [ ] **Step 4: Run green** — Run (label `T8-1-GREEN`): same command. Expected: PASS (5 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): DataStore settings repository with per-key fallback"`

### Subtask 8.2: Counter repository — read, overwrite, reset

**Files:** Create `data/CounterRepository.kt`; test `data/DataStoreCounterRepositoryTest.kt`, `testutil/FakeCounterRepository.kt`.

- [ ] **Step 1: Failing tests** — `app/src/test/kotlin/com/mitenko/hiitcounter/data/DataStoreCounterRepositoryTest.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class DataStoreCounterRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val clock = FakeClock()

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "counter.preferences_pb") })

    @Test
    fun `absent total follows the live starting total`() = runTest {
        val settings = FakeSettingsRepository()
        val repo = DataStoreCounterRepository(store(), settings)
        assertEquals(CounterState(total = 48), repo.state.first())
        settings.progressionFlow.value = ProgressionConfig(startingTotal = 55)
        assertEquals(55, repo.state.first().total)
    }

    @Test
    fun `overwrite and resetHoldCount clear the hold`() = runTest {
        val s = store()
        val repo = DataStoreCounterRepository(s, FakeSettingsRepository())
        s.edit { it[intPreferencesKey("hold_count")] = 3 }
        assertEquals(3, repo.state.first().holdCount)
        repo.resetHoldCount()
        assertEquals(0, repo.state.first().holdCount)
        s.edit { it[intPreferencesKey("hold_count")] = 2 }
        repo.overwrite(total = 64, bestStreak = 5, currentStreak = 3, lastCheckIn = null)
        assertEquals(CounterState(total = 64, bestStreak = 5, currentStreak = 3, lastCheckIn = null, holdCount = 0), repo.state.first())
    }

    @Test
    fun `last check-in round-trips as epoch millis`() = runTest {
        val repo = DataStoreCounterRepository(store(), FakeSettingsRepository())
        val t = Instant.ofEpochMilli(1_790_000_000_123)
        repo.overwrite(60, 3, 2, t)
        assertEquals(t, repo.state.first().lastCheckIn)
        repo.overwrite(60, 3, 2, null)
        assertNull(repo.state.first().lastCheckIn)
    }

    @Test
    fun `reset progress returns to the fresh-install state`() = runTest {
        val repo = DataStoreCounterRepository(store(), FakeSettingsRepository())
        repo.overwrite(65, 24, 4, Instant.ofEpochMilli(1_000))
        repo.resetProgress()
        assertEquals(CounterState(total = 48), repo.state.first())
    }

    @Test
    fun `reset progress uses the live starting total`() = runTest {
        val settings = FakeSettingsRepository(progression = ProgressionConfig(startingTotal = 55))
        val repo = DataStoreCounterRepository(store(), settings)
        repo.overwrite(65, 24, 4, Instant.ofEpochMilli(1_000))
        repo.resetProgress()
        assertEquals(CounterState(total = 55), repo.state.first())
    }

    @Test
    fun `invalid stored values fall back per key`() = runTest {
        val s = store()
        s.edit {
            it[intPreferencesKey("total")] = -5
            it[intPreferencesKey("current_streak")] = -1
            it[intPreferencesKey("best_streak")] = 7
        }
        val st = DataStoreCounterRepository(s, FakeSettingsRepository()).state.first()
        assertEquals(48, st.total)
        assertEquals(0, st.currentStreak)
        assertEquals(7, st.bestStreak)
    }
}
```

(`Outcome`, `async`, `awaitAll` and `clock` are used by 8.3.)

- [ ] **Step 2: Run red** — Run (label `T8-2-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.data.DataStoreCounterRepositoryTest"`. Expected: compile FAIL, unresolved `DataStoreCounterRepository`.

- [ ] **Step 3: Implement**

`data/CounterRepository.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.mitenko.hiitcounter.domain.model.CounterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.io.IOException
import java.time.Instant

interface CounterRepository {
    val state: Flow<CounterState>
    suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)
    suspend fun resetHoldCount()
    suspend fun resetProgress()
}

private const val COUNTER_TAG = "CounterRepository"

/** Absent `total` reads as the live starting total (spec §5). */
internal fun Preferences.readCounter(startingTotal: Int) = CounterState(
    total = valid(Keys.TOTAL, startingTotal) { it >= 1 },
    bestStreak = valid(Keys.BEST_STREAK, 0) { it >= 0 },
    currentStreak = valid(Keys.CURRENT_STREAK, 0) { it >= 0 },
    lastCheckIn = this[Keys.LAST_CHECK_IN]?.let(Instant::ofEpochMilli),
    holdCount = valid(Keys.HOLD_COUNT, 0) { it >= 0 },
)

internal fun MutablePreferences.writeCounter(s: CounterState) {
    this[Keys.TOTAL] = s.total
    this[Keys.BEST_STREAK] = s.bestStreak
    this[Keys.CURRENT_STREAK] = s.currentStreak
    this[Keys.HOLD_COUNT] = s.holdCount
    val last = s.lastCheckIn
    if (last == null) remove(Keys.LAST_CHECK_IN) else this[Keys.LAST_CHECK_IN] = last.toEpochMilli()
}

class DataStoreCounterRepository(
    private val store: DataStore<Preferences>,
    settings: SettingsRepository,
) : CounterRepository {
    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) {
            Log.e(COUNTER_TAG, "Counter read failed", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    override val state: Flow<CounterState> =
        combine(prefs, settings.progression) { p, config -> p.readCounter(config.startingTotal) }

    /** Overwrites everything from Settings → Current State and resets holdCount (spec §6). */
    override suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        store.edit { it.writeCounter(CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount = 0)) }
    }

    override suspend fun resetHoldCount() {
        store.edit { it[Keys.HOLD_COUNT] = 0 }
    }

    override suspend fun resetProgress() {
        store.edit { it.clear() }
    }
}
```

`app/src/test/kotlin/com/mitenko/hiitcounter/testutil/FakeCounterRepository.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.domain.model.CounterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

class FakeCounterRepository(initial: CounterState = CounterState(total = 48)) : CounterRepository {
    val stateFlow = MutableStateFlow(initial)
    var overwriteCalls = 0
    var resetHoldCountCalls = 0
    var resetProgressCalls = 0

    override val state: Flow<CounterState> = stateFlow

    override suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        overwriteCalls++
        stateFlow.value = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount = 0)
    }

    override suspend fun resetHoldCount() {
        resetHoldCountCalls++
        stateFlow.update { it.copy(holdCount = 0) }
    }

    override suspend fun resetProgress() {
        resetProgressCalls++
        stateFlow.value = CounterState(total = 48)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T8-2-GREEN`): same command. Expected: PASS (6 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): counter repository with live starting total and resets"`

### Subtask 8.3: Atomic check-in

**Files:** Modify `data/CounterRepository.kt`, `testutil/FakeCounterRepository.kt`, `data/DataStoreCounterRepositoryTest.kt`.

- [ ] **Step 1: Failing tests** — append to `DataStoreCounterRepositoryTest`:
```kotlin
    @Test
    fun `check-in is persisted`() = runTest {
        val repo = DataStoreCounterRepository(store(), FakeSettingsRepository())
        val r = repo.checkIn(ProgressionConfig(), clock)
        assertEquals(Outcome.First, r.outcome)
        assertEquals(r.state, repo.state.first())
        assertEquals(clock.instant, repo.state.first().lastCheckIn)
    }

    @Test
    fun `concurrent check-ins record exactly one`() = runTest {
        val repo = DataStoreCounterRepository(store(), FakeSettingsRepository())
        val results = List(5) { async { repo.checkIn(ProgressionConfig(), clock) } }.awaitAll()
        assertEquals(1, results.count { it.outcome != Outcome.AlreadyToday })
    }

    @Test
    fun `first check-in at the hold value persists the hold count`() = runTest {
        val settings = FakeSettingsRepository(progression = ProgressionConfig(startingTotal = 64))
        val repo = DataStoreCounterRepository(store(), settings)
        repo.checkIn(settings.progressionFlow.value, clock)
        assertEquals(64 to 1, repo.state.first().let { it.total to it.holdCount })
    }
```

- [ ] **Step 2: Run red** — Run (label `T8-3-RED`): same command as 8.2. Expected: compile FAIL, unresolved `checkIn`.

- [ ] **Step 3: Implement** — replace `data/CounterRepository.kt`:
```kotlin
package com.mitenko.hiitcounter.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.Outcome
import com.mitenko.hiitcounter.domain.RepProgression
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.io.IOException
import java.time.Instant

interface CounterRepository {
    val state: Flow<CounterState>
    suspend fun checkIn(config: ProgressionConfig, clock: Clock): CheckInResult
    suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?)
    suspend fun resetHoldCount()
    suspend fun resetProgress()
}

private const val COUNTER_TAG = "CounterRepository"

/** Absent `total` reads as the live starting total (spec §5). */
internal fun Preferences.readCounter(startingTotal: Int) = CounterState(
    total = valid(Keys.TOTAL, startingTotal) { it >= 1 },
    bestStreak = valid(Keys.BEST_STREAK, 0) { it >= 0 },
    currentStreak = valid(Keys.CURRENT_STREAK, 0) { it >= 0 },
    lastCheckIn = this[Keys.LAST_CHECK_IN]?.let(Instant::ofEpochMilli),
    holdCount = valid(Keys.HOLD_COUNT, 0) { it >= 0 },
)

internal fun MutablePreferences.writeCounter(s: CounterState) {
    this[Keys.TOTAL] = s.total
    this[Keys.BEST_STREAK] = s.bestStreak
    this[Keys.CURRENT_STREAK] = s.currentStreak
    this[Keys.HOLD_COUNT] = s.holdCount
    val last = s.lastCheckIn
    if (last == null) remove(Keys.LAST_CHECK_IN) else this[Keys.LAST_CHECK_IN] = last.toEpochMilli()
}

class DataStoreCounterRepository(
    private val store: DataStore<Preferences>,
    settings: SettingsRepository,
) : CounterRepository {
    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) {
            Log.e(COUNTER_TAG, "Counter read failed", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    override val state: Flow<CounterState> =
        combine(prefs, settings.progression) { p, config -> p.readCounter(config.startingTotal) }

    /** Atomic: runs inside one DataStore transaction, so concurrent calls check in once. */
    override suspend fun checkIn(config: ProgressionConfig, clock: Clock): CheckInResult {
        lateinit var result: CheckInResult
        store.edit { p ->
            val before = p.readCounter(config.startingTotal)
            val now = clock.now()
            before.lastCheckIn?.let { last ->
                if (now.isBefore(last)) Log.w(COUNTER_TAG, "Clock moved backwards: now=$now last=$last")
            }
            result = RepProgression.checkIn(before, config, now, clock.zone())
            if (result.outcome != Outcome.AlreadyToday) p.writeCounter(result.state)
        }
        return result
    }

    /** Overwrites everything from Settings → Current State and resets holdCount (spec §6). */
    override suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        store.edit { it.writeCounter(CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount = 0)) }
    }

    override suspend fun resetHoldCount() {
        store.edit { it[Keys.HOLD_COUNT] = 0 }
    }

    override suspend fun resetProgress() {
        store.edit { it.clear() }
    }
}
```

Replace `testutil/FakeCounterRepository.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.domain.CheckInResult
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.RepProgression
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

class FakeCounterRepository(initial: CounterState = CounterState(total = 48)) : CounterRepository {
    val stateFlow = MutableStateFlow(initial)
    var checkInCalls = 0
    var overwriteCalls = 0
    var resetHoldCountCalls = 0
    var resetProgressCalls = 0

    override val state: Flow<CounterState> = stateFlow

    override suspend fun checkIn(config: ProgressionConfig, clock: Clock): CheckInResult {
        checkInCalls++
        val r = RepProgression.checkIn(stateFlow.value, config, clock.now(), clock.zone())
        stateFlow.value = r.state
        return r
    }

    override suspend fun overwrite(total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) {
        overwriteCalls++
        stateFlow.value = CounterState(total, bestStreak, currentStreak, lastCheckIn, holdCount = 0)
    }

    override suspend fun resetHoldCount() {
        resetHoldCountCalls++
        stateFlow.update { it.copy(holdCount = 0) }
    }

    override suspend fun resetProgress() {
        resetProgressCalls++
        stateFlow.value = CounterState(total = 48)
    }
}
```

- [ ] **Step 4: Run green** — Run (label `T8-3-GREEN`): same command. Expected: PASS (9 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(data): atomic check-in inside one DataStore transaction"`

### Subtask 8.4: Platform clock and Hilt modules

**Files:** Create `platform/AndroidClock.kt`, `di/DataModule.kt`, `di/AppModule.kt`.

- [ ] **Step 1: Implement**

`platform/AndroidClock.kt`:
```kotlin
package com.mitenko.hiitcounter.platform

import android.os.SystemClock
import com.mitenko.hiitcounter.domain.Clock
import java.time.Instant
import java.time.ZoneId

/** The only place that reads real time. */
object AndroidClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
```

`di/DataModule.kt`:
```kotlin
package com.mitenko.hiitcounter.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.data.DataStoreCounterRepository
import com.mitenko.hiitcounter.data.DataStoreSettingsRepository
import com.mitenko.hiitcounter.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SettingsStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CounterStore

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private fun create(context: Context, name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            Log.e("DataModule", "$name.preferences_pb corrupt; replacing with defaults", e)
            emptyPreferences()
        },
        produceFile = { context.preferencesDataStoreFile(name) },
    )

    @Provides @Singleton @SettingsStore
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> = create(context, "settings")

    @Provides @Singleton @CounterStore
    fun counterStore(@ApplicationContext context: Context): DataStore<Preferences> = create(context, "counter")

    @Provides @Singleton
    fun settingsRepository(@SettingsStore store: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(store)

    @Provides @Singleton
    fun counterRepository(@CounterStore store: DataStore<Preferences>, settings: SettingsRepository): CounterRepository =
        DataStoreCounterRepository(store, settings)
}
```

`di/AppModule.kt`:
```kotlin
package com.mitenko.hiitcounter.di

import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.platform.AndroidClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun clock(): Clock = AndroidClock

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides @Singleton
    fun timerController(@ApplicationScope scope: CoroutineScope, clock: Clock): TimerController =
        TimerController(scope, clock::elapsedRealtimeMs)
}
```

- [ ] **Step 2: Verify** — Run (label `T8-4-BUILD`, ~2 min): `./gradlew assembleDebug`. Expected: `BUILD SUCCESSFUL` (Hilt/KSP generates the component with these bindings).

- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(di): Android clock and Hilt modules for data and timer"`

**Task 8 gate:** Run (label `T8-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 9: Cue patterns, tones, CuePlayer (§8)

**Interfaces produced:** `enum class Tone { SHORT, LONG }`; `data class ToneAt(atMs: Long, tone: Tone)`; `data class CuePattern(tones: List<ToneAt>, vibration: List<Long>?)` + `durationMs`; `object CuePatterns { SHORT_MS = 120; LONG_MS = 600; lengthOf(Tone); forCue(Cue): CuePattern }`; `class CuePlayer(context, scope) { play(cue, config); release() }`.

### Subtask 9.1: Cue patterns

**Files:** Create `domain/CuePatterns.kt`; test `domain/CuePatternsTest.kt`.

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuePatternsTest {
    @Test
    fun `countdown is a single short beep without vibration`() {
        val p = CuePatterns.forCue(Cue.Countdown(3))
        assertEquals(listOf(ToneAt(0, Tone.SHORT)), p.tones)
        assertNull(p.vibration)
    }

    @Test
    fun `work start is a long tone with a pulse`() {
        val p = CuePatterns.forCue(Cue.PhaseStart(Phase.WORK))
        assertEquals(listOf(ToneAt(0, Tone.LONG)), p.tones)
        assertEquals(listOf(0L, 400L), p.vibration)
        assertEquals(CuePatterns.LONG_MS, p.durationMs)
    }

    @Test
    fun `rest and cooldown start are double beeps`() {
        for (phase in listOf(Phase.REST, Phase.COOLDOWN)) {
            val p = CuePatterns.forCue(Cue.PhaseStart(phase))
            assertEquals(listOf(ToneAt(0, Tone.SHORT), ToneAt(250, Tone.SHORT)), p.tones)
            assertEquals(listOf(0L, 150L, 100L, 150L), p.vibration)
        }
    }

    @Test
    fun `finished is a triple beep`() {
        val p = CuePatterns.forCue(Cue.Finished)
        assertEquals(listOf(ToneAt(0, Tone.SHORT), ToneAt(250, Tone.SHORT), ToneAt(500, Tone.SHORT)), p.tones)
        assertEquals(listOf(0L, 150L, 100L, 150L, 100L, 150L), p.vibration)
        assertEquals(500 + CuePatterns.SHORT_MS, p.durationMs)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T9-1-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.CuePatternsTest"`. Expected: compile FAIL, unresolved `CuePatterns`.

- [ ] **Step 3: Implement** — `domain/CuePatterns.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.Phase

enum class Tone { SHORT, LONG }

data class ToneAt(val atMs: Long, val tone: Tone)

/** [vibration] is VibrationEffect.createWaveform timings: [delay, on, off, on, …]. */
data class CuePattern(val tones: List<ToneAt>, val vibration: List<Long>?) {
    val durationMs: Long
        get() = tones.maxOfOrNull { it.atMs + CuePatterns.lengthOf(it.tone) } ?: 0L
}

object CuePatterns {
    const val SHORT_MS = 120L
    const val LONG_MS = 600L

    fun lengthOf(tone: Tone): Long = when (tone) {
        Tone.SHORT -> SHORT_MS
        Tone.LONG -> LONG_MS
    }

    fun forCue(cue: Cue): CuePattern = when (cue) {
        is Cue.Countdown -> CuePattern(listOf(ToneAt(0, Tone.SHORT)), vibration = null)
        is Cue.PhaseStart -> when (cue.phase) {
            Phase.WORK -> CuePattern(listOf(ToneAt(0, Tone.LONG)), listOf(0L, 400L))
            Phase.REST, Phase.COOLDOWN ->
                CuePattern(listOf(ToneAt(0, Tone.SHORT), ToneAt(250, Tone.SHORT)), listOf(0L, 150L, 100L, 150L))
            Phase.PREPARE, Phase.DONE -> CuePattern(emptyList(), null)
        }
        Cue.Finished -> CuePattern(
            listOf(ToneAt(0, Tone.SHORT), ToneAt(250, Tone.SHORT), ToneAt(500, Tone.SHORT)),
            listOf(0L, 150L, 100L, 150L, 100L, 150L),
        )
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (4 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): cue sound and vibration patterns"`

### Subtask 9.2: Tone assets

**Files:** Create `tools/gen_tones.py`; generated `app/src/main/res/raw/tone_short.wav`, `tone_long.wav`.

- [ ] **Step 1: Generator script** — `tools/gen_tones.py`:
```python
"""Generates the cue tones in app/src/main/res/raw (16-bit mono 44.1 kHz WAV)."""
import math
import os
import struct
import sys
import traceback
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "..", "app", "src", "main", "res", "raw")
RATE = 44100


def tone(path, freq_hz, ms, volume=0.6, fade_ms=5):
    n = int(RATE * ms / 1000)
    fade = int(RATE * fade_ms / 1000)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        frames = bytearray()
        for i in range(n):
            env = min(1.0, i / fade, (n - 1 - i) / fade) if fade else 1.0
            sample = volume * env * math.sin(2 * math.pi * freq_hz * i / RATE)
            frames += struct.pack("<h", int(sample * 32767))
        w.writeframes(bytes(frames))


def main():
    os.makedirs(RAW, exist_ok=True)
    tone(os.path.join(RAW, "tone_short.wav"), 1000, 120)
    tone(os.path.join(RAW, "tone_long.wav"), 1500, 600)
    print("wrote", os.path.abspath(RAW))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        with open(os.path.join(HERE, "error.txt"), "w", encoding="utf-8") as f:
            f.write(traceback.format_exc())
        sys.exit(1)
```

- [ ] **Step 2: Generate and verify**

```bash
python tools/gen_tones.py
ls -l app/src/main/res/raw/
```
Expected: prints `wrote …/res/raw`; `tone_short.wav` ≈ 10.6 KB and `tone_long.wav` ≈ 52.9 KB.

- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(cues): generated short and long cue tones"`

### Subtask 9.3: CuePlayer

**Files:** Create `service/CuePlayer.kt`; modify `AndroidManifest.xml`.

- [ ] **Step 1: Permission** — add inside `<manifest>`, above `<application>`:
```xml
    <uses-permission android:name="android.permission.VIBRATE" />
```

- [ ] **Step 2: Implement** — `service/CuePlayer.kt`:
```kotlin
package com.mitenko.hiitcounter.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays cue sounds (ducking other audio) and vibrations — spec §8. Main thread only. */
class CuePlayer(context: Context, private val scope: CoroutineScope) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
    private val loaded = mutableSetOf<Int>()
    private val soundIds: Map<Tone, Int>
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private var abandonJob: Job? = null

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
        if (config.sound && pattern.tones.isNotEmpty()) playTones(pattern)
    }

    private fun playTones(pattern: CuePattern) {
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus denied; skipping sound")
            return
        }
        scope.launch {
            var t = 0L
            for (tone in pattern.tones) {
                delay(tone.atMs - t)
                t = tone.atMs
                val id = soundIds.getValue(tone.tone)
                if (id in loaded) soundPool.play(id, 1f, 1f, 1, 0, 1f) else Log.w(TAG, "Sound $tone not loaded yet; skipped")
            }
        }
        abandonJob?.cancel()
        abandonJob = scope.launch {
            delay(pattern.durationMs + 100)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun vibrate(timings: List<Long>) {
        vibrator?.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), -1))
    }

    fun release() {
        abandonJob?.cancel()
        audioManager.abandonAudioFocusRequest(focusRequest)
        soundPool.release()
    }

    private companion object {
        const val TAG = "CuePlayer"
    }
}
```

- [ ] **Step 3: Verify** — Run (label `T9-3-BUILD`, ~2 min): `./gradlew assembleDebug lintDebug`. Expected: `BUILD SUCCESSFUL`, no lint errors (no `MissingPermission` for vibrate).

- [ ] **Step 4: Commit** — `git add -A && git commit -m "feat(cues): SoundPool and vibration cue player with audio ducking"`

**Task 9 gate:** Run (label `T9-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 10: Foreground service, notification, service starter (§4, §10)

**Interfaces produced:** `object TimerText { formatMmSs; formatHms; formatDuration; phaseName; notificationTitle; notificationBody }`; `object WakeLockPolicy { MARGIN_MS; timeoutMs(TimerState?): Long? }`; `WorkoutNotifications(context) { ensureChannel(); build(state?); update(state) }`; `TimerService` (`ACTION_STOP`); `interface WorkoutServiceStarter { fun start(): Result<Unit> }` + `AndroidWorkoutServiceStarter` bound in Hilt.

### Subtask 10.1: TimerText

**Files:** Create `domain/TimerText.kt`; test `domain/TimerTextTest.kt`.

- [ ] **Step 1: Failing test**:
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
        assertEquals("Work · Set 2/8", TimerText.notificationTitle(work))
        assertEquals("00:15 left", TimerText.notificationBody(work))
        assertEquals("Paused · 00:15 left", TimerText.notificationBody(work.copy(paused = true)))
        assertEquals("Workout complete", TimerText.notificationBody(work.copy(phase = Phase.DONE)))
        assertEquals("Get ready", TimerText.phaseName(Phase.PREPARE))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T10-1-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.TimerTextTest"`. Expected: compile FAIL, unresolved `TimerText`.

- [ ] **Step 3: Implement** — `domain/TimerText.kt`:
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

    fun notificationTitle(s: TimerState): String = "${phaseName(s.phase)} · Set ${s.set}/${s.sets}"

    fun notificationBody(s: TimerState): String = when {
        s.phase == Phase.DONE -> "Workout complete"
        s.paused -> "Paused · ${formatMmSs(s.phaseSecondsLeft)} left"
        else -> "${formatMmSs(s.phaseSecondsLeft)} left"
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (2 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): timer text formatting for UI and notification"`

### Subtask 10.2: Wake-lock policy

**Files:** Create `domain/WakeLockPolicy.kt`; test `domain/WakeLockPolicyTest.kt`.

The service holds a partial wake lock only while the workout is ticking. The timeout is the remaining *active* time plus a margin, taken fresh on every acquire; pause releases the lock and resume re-acquires it, so the timeout can never expire mid-workout (review finding #5).

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeLockPolicyTest {
    private val running = TimerState(Phase.WORK, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20,
        elapsedSec = 50, totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false)

    @Test
    fun `ticking holds the lock for the remaining active time plus a margin`() {
        assertEquals(190_000L + WakeLockPolicy.MARGIN_MS, WakeLockPolicy.timeoutMs(running))
    }

    @Test
    fun `paused, done and idle release the lock`() {
        assertNull(WakeLockPolicy.timeoutMs(running.copy(paused = true)))
        assertNull(WakeLockPolicy.timeoutMs(running.copy(phase = Phase.DONE)))
        assertNull(WakeLockPolicy.timeoutMs(null))
    }

    @Test
    fun `timeout always outlasts the remaining active time`() {
        for (elapsed in 0..240) {
            val timeout = WakeLockPolicy.timeoutMs(running.copy(elapsedSec = elapsed))!!
            assertTrue(timeout > (240 - elapsed) * 1000L)
        }
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T10-2-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.domain.WakeLockPolicyTest"`. Expected: compile FAIL, unresolved `WakeLockPolicy`.

- [ ] **Step 3: Implement** — `domain/WakeLockPolicy.kt`:
```kotlin
package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState

/** Wake-lock rule for TimerService (spec §10). */
object WakeLockPolicy {
    const val MARGIN_MS = 60_000L

    /** Timeout for a lock held while [state] is ticking, or null when the lock must be released. */
    fun timeoutMs(state: TimerState?): Long? =
        if (state == null || state.phase == Phase.DONE || state.paused) null
        else state.remainingSec * 1000L + MARGIN_MS
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (3 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(domain): wake-lock policy for the timer service"`

### Subtask 10.3: Notifications, foreground service, manifest

**Files:** Create `service/WorkoutNotifications.kt`, `service/TimerService.kt`; modify `AndroidManifest.xml`.

- [ ] **Step 1: Notifications** — `service/WorkoutNotifications.kt`:
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

    fun build(state: TimerState?): Notification {
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
            .setContentTitle(state?.let(TimerText::notificationTitle) ?: context.getString(R.string.notification_starting))
            .setContentText(state?.let(TimerText::notificationBody))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.stop), stop)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun update(state: TimerState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(NOTIFICATION_ID, build(state))
    }

    companion object {
        const val CHANNEL_ID = "workout"
        const val NOTIFICATION_ID = 1
    }
}
```

- [ ] **Step 2: Service** — `service/TimerService.kt`:
```kotlin
package com.mitenko.hiitcounter.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WakeLockPolicy
import com.mitenko.hiitcounter.domain.model.TimerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin host (spec §4, §10): keeps the process and CPU alive while TimerController runs,
 * plays cues, shows the notification. No business logic.
 */
@AndroidEntryPoint
class TimerService : Service() {
    @Inject lateinit var controller: TimerController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var cuePlayer: CuePlayer
    private lateinit var notifications: WorkoutNotifications
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        cuePlayer = CuePlayer(this, scope)
        notifications = WorkoutNotifications(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stop() // idempotent: no-op when idle or done
            if (!started) stopSelf() // stale notification action after process recreation
            return START_NOT_STICKY
        }
        if (started) {
            // Already foreground, e.g. a new workout started during the DONE grace period.
            controller.onServiceStarted()
            return START_NOT_STICKY
        }
        try {
            notifications.ensureChannel()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, WorkoutNotifications.NOTIFICATION_ID, notifications.build(null), type)
            started = true
            observe()
            controller.onServiceStarted()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            controller.onServiceFailed(e.message ?: e.javaClass.simpleName)
            releaseWakeLock()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun observe() {
        scope.launch {
            controller.cues.collect { cue -> controller.snapshot?.let { cuePlayer.play(cue, it.cues) } }
        }
        scope.launch {
            controller.state.collect { state ->
                if (state != null) {
                    notifications.update(state)
                    updateWakeLock(state)
                }
            }
        }
        scope.launch {
            // collectLatest: a new PREPARING/RUNNING status cancels a pending DONE-grace stop.
            controller.status.collectLatest { status ->
                if (status == RunStatus.IDLE || status == RunStatus.DONE) {
                    releaseWakeLock()
                    if (status == RunStatus.DONE) delay(FINISH_CUE_GRACE_MS)
                    ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun updateWakeLock(state: TimerState) {
        val timeoutMs = WakeLockPolicy.timeoutMs(state)
        if (timeoutMs != null) acquireWakeLock(timeoutMs) else releaseWakeLock()
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        // A held lock already covers the remaining active time; pause releases it and
        // resume re-acquires with a fresh timeout (WakeLockPolicy).
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running when the app is swiped away; the notification is the way back in.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        cuePlayer.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.mitenko.hiitcounter.action.STOP"
        private const val TAG = "TimerService"
        private const val WAKE_LOCK_TAG = "hiitcounter:workout"
        private const val FINISH_CUE_GRACE_MS = 1_500L
    }
}
```

- [ ] **Step 3: Manifest** — add next to the `VIBRATE` permission:
```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
Add inside `<application>`, after the activity:
```xml
        <service
            android:name=".service.TimerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="workout interval timer" />
        </service>
```

- [ ] **Step 4: Verify** — Run (label `T10-3-BUILD`, ~2 min): `./gradlew assembleDebug lintDebug`. Expected: `BUILD SUCCESSFUL`, no lint errors (in particular no `ForegroundServiceType`, `ForegroundServicePermission`, `MissingPermission`, `NotificationPermission`).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(service): specialUse foreground timer service with wake lock and notification"`

### Subtask 10.4: Service starter and DI binding

**Files:** Create `service/WorkoutServiceStarter.kt`; modify `di/AppModule.kt`.

- [ ] **Step 1: Starter** — `service/WorkoutServiceStarter.kt`:
```kotlin
package com.mitenko.hiitcounter.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts [TimerService]. Returns failure if the platform refuses (e.g. background-start restrictions). */
interface WorkoutServiceStarter {
    fun start(): Result<Unit>
}

class AndroidWorkoutServiceStarter(private val context: Context) : WorkoutServiceStarter {
    override fun start(): Result<Unit> = runCatching {
        ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
    }
}
```

- [ ] **Step 2: Bind it** — in `di/AppModule.kt` add imports `android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`, `com.mitenko.hiitcounter.service.AndroidWorkoutServiceStarter`, `com.mitenko.hiitcounter.service.WorkoutServiceStarter`, and add inside `object AppModule`:
```kotlin
    @Provides @Singleton
    fun workoutServiceStarter(@ApplicationContext context: Context): WorkoutServiceStarter =
        AndroidWorkoutServiceStarter(context)
```

- [ ] **Step 3: Verify** — Run (label `T10-4-BUILD`): `./gradlew assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit** — `git add -A && git commit -m "feat(service): injectable workout service starter"`

**Task 10 gate:** Run (label `T10-GATE`, ~2 min): `./gradlew testDebugUnitTest lintDebug`. Expected: all PASS, no lint errors.

---

## Task 11: Home screen (§4, §9.1)

**Interfaces produced:** `object DateFormats { dateTime(Instant, ZoneId); date(Instant, ZoneId) }`; `data class HomeUiState(reps, total, lastCheckIn, bestStreak, currentStreak, today, checkedInToday, starting, error)`; `HomeViewModel(counter, settings, controller, starter, clock)` with `uiState`, `onStart()`, `onResume()`, `dismissError()`, `SERVICE_START_TIMEOUT_MS = 5_000L`; `@Composable HomeRoute(onOpenSettings)`; stateless `HomeScreen(state, onStart, onOpenSettings, onDismissError)`; test utils `MainDispatcherRule`, `FakeServiceStarter`.

Start flow: prepare → start service → await `serviceStatus` ≠ Pending (5 s timeout) → **only then** `checkIn` → `controller.start(distribute(total, sets))`. Any failure: `cancelPrepare()`, error snackbar, no check-in. Navigation to the timer happens via active-run routing (14.1) when status becomes RUNNING.

### Subtask 11.1: Date formats

**Files:** Create `ui/common/DateFormats.kt`; test `ui/common/DateFormatsTest.kt`.

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DateFormatsTest {
    @Test
    fun `formats like the sheet in the given zone`() {
        val t = Instant.parse("2026-09-24T12:55:00Z")
        val zone = ZoneId.of("America/Los_Angeles")
        assertEquals("24 Sep 2026, 05:55", DateFormats.dateTime(t, zone))
        assertEquals("24 Sep 2026", DateFormats.date(t, zone))
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T11-1-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.common.DateFormatsTest"`. Expected: compile FAIL, unresolved `DateFormats`.

- [ ] **Step 3: Implement** — `ui/common/DateFormats.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun dateTime(instant: Instant, zone: ZoneId): String = dateTime.format(instant.atZone(zone))
    fun date(instant: Instant, zone: ZoneId): String = date.format(instant.atZone(zone))
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): sheet-style date formatting"`

### Subtask 11.2: HomeViewModel table state

**Files:** Create `ui/home/HomeViewModel.kt`; test `ui/home/HomeViewModelTest.kt`, `testutil/MainDispatcherRule.kt`, `testutil/FakeServiceStarter.kt`.

- [ ] **Step 1: Test utilities**

`testutil/MainDispatcherRule.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`testutil/FakeServiceStarter.kt`:
```kotlin
package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.service.WorkoutServiceStarter

class FakeServiceStarter(
    private val controller: TimerController,
    var behavior: Behavior = Behavior.SUCCEED,
) : WorkoutServiceStarter {
    enum class Behavior { SUCCEED, THROW_ON_START, FAIL_IN_SERVICE, NO_RESPONSE }

    var calls = 0

    override fun start(): Result<Unit> {
        calls++
        return when (behavior) {
            Behavior.SUCCEED -> { controller.onServiceStarted(); Result.success(Unit) }
            Behavior.THROW_ON_START -> Result.failure(IllegalStateException("not allowed"))
            Behavior.FAIL_IN_SERVICE -> { controller.onServiceFailed("boom"); Result.success(Unit) }
            Behavior.NO_RESPONSE -> Result.success(Unit)
        }
    }
}
```

- [ ] **Step 2: Failing test** — `ui/home/HomeViewModelTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.home

import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeCounterRepository
import com.mitenko.hiitcounter.testutil.FakeServiceStarter
import com.mitenko.hiitcounter.testutil.FakeServiceStarter.Behavior
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = FakeClock(instant = Instant.parse("2026-09-24T15:00:00Z")) // 08:00 PDT
    private val counter = FakeCounterRepository(
        CounterState(total = 65, bestStreak = 24, currentStreak = 4, lastCheckIn = Instant.parse("2026-09-23T12:55:00Z")),
    )
    private val settings = FakeSettingsRepository()

    private class Harness(val vm: HomeViewModel, val controller: TimerController, val starter: FakeServiceStarter)

    private fun TestScope.harness(behavior: Behavior = Behavior.SUCCEED): Harness {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val starter = FakeServiceStarter(controller, behavior)
        val vm = HomeViewModel(counter, settings, controller, starter, clock)
        backgroundScope.launch { vm.uiState.collect {} }
        return Harness(vm, controller, starter)
    }

    @Test
    fun `table shows distributed reps and sheet-style fields`() = runTest {
        val h = harness()
        runCurrent()
        val s = h.vm.uiState.value
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), s.reps)
        assertEquals(65, s.total)
        assertEquals("23 Sep 2026, 05:55", s.lastCheckIn)
        assertEquals(24, s.bestStreak)
        assertEquals(4, s.currentStreak)
        assertEquals("24 Sep 2026", s.today)
        assertFalse(s.checkedInToday)
    }

    @Test
    fun `reps follow the configured number of sets`() = runTest {
        val h = harness()
        settings.timingFlow.value = settings.timingFlow.value.copy(sets = 5)
        runCurrent()
        assertEquals(listOf(13, 13, 13, 13, 13), h.vm.uiState.value.reps)
    }
}
```

(`RunStatus`, `advanceTimeBy`, `assertNull`, `assertTrue` are used by 11.3.)

- [ ] **Step 3: Run red** — Run (label `T11-2-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.home.HomeViewModelTest"`. Expected: compile FAIL, unresolved `HomeViewModel`.

- [ ] **Step 4: Implement** — `ui/home/HomeViewModel.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.RepDistributor
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.service.WorkoutServiceStarter
import com.mitenko.hiitcounter.ui.common.DateFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class HomeUiState(
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
class HomeViewModel @Inject constructor(
    private val counter: CounterRepository,
    private val settings: SettingsRepository,
    private val controller: TimerController, // used by onStart (11.3)
    private val starter: WorkoutServiceStarter, // used by onStart (11.3)
    private val clock: Clock,
) : ViewModel() {
    private data class Transient(val starting: Boolean = false, val error: String? = null)

    private val transient = MutableStateFlow(Transient())
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> =
        combine(counter.state, settings.timing, transient, refresh) { state, timing, tr, _ ->
            val now = clock.now()
            val zone = clock.zone()
            HomeUiState(
                reps = RepDistributor.distribute(state.total, timing.sets),
                total = state.total,
                lastCheckIn = state.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: "—",
                bestStreak = state.bestStreak,
                currentStreak = state.currentStreak,
                today = DateFormats.date(now, zone),
                checkedInToday = state.lastCheckIn?.atZone(zone)?.toLocalDate() == now.atZone(zone).toLocalDate(),
                starting = tr.starting,
                error = tr.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Re-evaluates "Today" and "Checked in today" when the screen resumes (e.g. after midnight). */
    fun onResume() {
        refresh.update { it + 1 }
    }

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }
}
```

- [ ] **Step 5: Run green** — same command. Expected: PASS (2 tests).

- [ ] **Step 6: Commit** — `git add -A && git commit -m "feat(ui): home view model table state"`

### Subtask 11.3: Start flow gated on service start

**Files:** Modify `ui/home/HomeViewModel.kt`, `ui/home/HomeViewModelTest.kt`.

- [ ] **Step 1: Failing tests** — append to `HomeViewModelTest`:
```kotlin
    @Test
    fun `successful start checks in after the service starts and runs the timer`() = runTest {
        val h = harness()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertEquals(1, counter.checkInCalls)
        assertEquals(RunStatus.RUNNING, h.controller.status.value)
        assertEquals(66, counter.stateFlow.value.total)
        assertTrue(h.vm.uiState.value.checkedInToday)
        assertNull(h.vm.uiState.value.error)
    }

    @Test
    fun `service start exception leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.THROW_ON_START)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, counter.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("not allowed"))
    }

    @Test
    fun `foreground failure inside the service leaves the check-in untouched`() = runTest {
        val h = harness(Behavior.FAIL_IN_SERVICE)
        h.vm.onStart()
        runCurrent()
        assertEquals(0, counter.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error!!.contains("boom"))
    }

    @Test
    fun `no service response times out without checking in`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        advanceTimeBy(HomeViewModel.SERVICE_START_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(0, counter.checkInCalls)
        assertEquals(RunStatus.IDLE, h.controller.status.value)
        assertTrue(h.vm.uiState.value.error != null)
    }

    @Test
    fun `start is debounced while in flight`() = runTest {
        val h = harness(Behavior.NO_RESPONSE)
        h.vm.onStart()
        h.vm.onStart()
        runCurrent()
        assertEquals(1, h.starter.calls)
        assertTrue(h.vm.uiState.value.starting)
    }
```

- [ ] **Step 2: Run red** — Run (label `T11-3-RED`): same command as 11.2. Expected: compile FAIL, unresolved `onStart`, `SERVICE_START_TIMEOUT_MS`.

- [ ] **Step 3: Implement** — replace `ui/home/HomeViewModel.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.RepDistributor
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.ServiceStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.service.WorkoutServiceStarter
import com.mitenko.hiitcounter.ui.common.DateFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class HomeUiState(
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
class HomeViewModel @Inject constructor(
    private val counter: CounterRepository,
    private val settings: SettingsRepository,
    private val controller: TimerController,
    private val starter: WorkoutServiceStarter,
    private val clock: Clock,
) : ViewModel() {
    private data class Transient(val starting: Boolean = false, val error: String? = null)

    private val transient = MutableStateFlow(Transient())
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> =
        combine(counter.state, settings.timing, transient, refresh) { state, timing, tr, _ ->
            val now = clock.now()
            val zone = clock.zone()
            HomeUiState(
                reps = RepDistributor.distribute(state.total, timing.sets),
                total = state.total,
                lastCheckIn = state.lastCheckIn?.let { DateFormats.dateTime(it, zone) } ?: "—",
                bestStreak = state.bestStreak,
                currentStreak = state.currentStreak,
                today = DateFormats.date(now, zone),
                checkedInToday = state.lastCheckIn?.atZone(zone)?.toLocalDate() == now.atZone(zone).toLocalDate(),
                starting = tr.starting,
                error = tr.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Re-evaluates "Today" and "Checked in today" when the screen resumes (e.g. after midnight). */
    fun onResume() {
        refresh.update { it + 1 }
    }

    fun dismissError() {
        transient.update { it.copy(error = null) }
    }

    /** Spec §4: the check-in is committed only after the foreground service has started. */
    fun onStart() {
        if (transient.value.starting) return
        if (controller.status.value == RunStatus.RUNNING) return
        transient.value = Transient(starting = true)
        viewModelScope.launch {
            try {
                startWorkout()
            } finally {
                transient.update { it.copy(starting = false) }
            }
        }
    }

    private suspend fun startWorkout() {
        val timing = settings.timing.first()
        val cues = settings.cues.first()
        val progression = settings.progression.first()
        if (!controller.prepare(WorkoutSnapshot(timing, cues))) return

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
        val result = counter.checkIn(progression, clock)
        controller.start(RepDistributor.distribute(result.state.total, timing.sets))
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

- [ ] **Step 4: Run green** — same command. Expected: PASS (7 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): start flow checks in only after the service starts"`

### Subtask 11.4: HomeScreen

**Files:** Create `ui/home/HomeScreen.kt`; test `ui/home/HomeScreenTest.kt`.

- [ ] **Step 1: Failing test** — `ui/home/HomeScreenTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.home

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val state = HomeUiState(
        reps = listOf(9, 8, 8, 8, 8, 8, 8, 8), total = 65, lastCheckIn = "24 Sep 2026, 05:55",
        bestStreak = 24, currentStreak = 4, today = "24 Sep 2026", checkedInToday = true,
    )

    @Test
    fun `renders the sheet table`() {
        compose.setContent { HiitTheme { HomeScreen(state, onStart = {}, onOpenSettings = {}, onDismissError = {}) } }
        compose.onNodeWithTag("rep_0").assertTextEquals("9")
        compose.onNodeWithTag("rep_7").assertTextEquals("8")
        compose.onNodeWithText("Total Reps").assertExists()
        compose.onNodeWithText("65").assertExists()
        compose.onNodeWithText("24 Sep 2026, 05:55").assertExists()
        compose.onNodeWithText("Best CI Streak").assertExists()
        compose.onNodeWithText("Checked in today").assertExists()
    }

    @Test
    fun `start button invokes the callback`() {
        var clicks = 0
        compose.setContent { HiitTheme { HomeScreen(state, onStart = { clicks++ }, onOpenSettings = {}, onDismissError = {}) } }
        compose.onNodeWithTag("start").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `start button is disabled while starting`() {
        compose.setContent { HiitTheme { HomeScreen(state.copy(starting = true), {}, {}, {}) } }
        compose.onNodeWithTag("start").assertIsNotEnabled()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T11-4-RED`, ~2 min first Robolectric run): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.home.HomeScreenTest"`. Expected: compile FAIL, unresolved `HomeScreen`.

- [ ] **Step 3: Implement** — `ui/home/HomeScreen.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.home

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R

@Composable
fun HomeRoute(onOpenSettings: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }
    HomeScreen(state, onStart = vm::onStart, onOpenSettings = onOpenSettings, onDismissError = vm::dismissError)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings")) {
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
private fun RepTable(state: HomeUiState) {
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

- [ ] **Step 4: Run green** — same command. Expected: PASS (3 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): home screen table like the sheet"`

**Task 11 gate:** Run (label `T11-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 12: Timer screen (§9.2)

**Interfaces produced:** `enum class PhaseTone { WORK, REST, NEUTRAL }`; `data class TimerUiState(setsText, elapsedText, label: String?, centerNumber: Int?, centerDimmed, countdownText, innerProgress, outerProgress, tone, paused, done, description)`; `object TimerUiMapper { map(TimerState) }`; `TimerViewModel(controller, settings)` with `uiState`, `status`, `notificationPermissionAsked`, `togglePause()`, `stop()`, `leaveDone()`, `onNotificationPermissionAsked()`; `DualRing(inner, outer, innerColor, outerColor, modifier)`; stateless `TimerScreen(ui, onTogglePause, onClose)`; `@Composable TimerRoute(onExit)`.

Centre rules: WORK → big bright reps, no label; REST → "REST" + dimmed upcoming reps; PREPARE → "GET READY" + dimmed first-set reps; COOLDOWN → "COOLDOWN", no number; DONE → "DONE" + total reps. Inner ring = `secondsLeft / duration`; outer ring = `(completedWorkSets + workFraction) / sets`. Centre digits are sized relative to the ring (not font-scaled); other text uses `sp`. The centre content description omits seconds.

### Subtask 12.1: TimerUiMapper

**Files:** Create `ui/timer/TimerUiMapper.kt`; test `ui/timer/TimerUiMapperTest.kt`.

- [ ] **Step 1: Failing test**:
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
        val ui = TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20))
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
        val ui = TimerUiMapper.map(state(Phase.REST, set = 3, left = 10, duration = 10, reps = 9))
        assertEquals("REST", ui.label)
        assertEquals(9, ui.centerNumber)
        assertTrue(ui.centerDimmed)
        assertEquals(PhaseTone.REST, ui.tone)
        assertEquals(2f / 8f, ui.outerProgress, 1e-6f)
    }

    @Test
    fun `prepare cooldown and done`() {
        val prep = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10, reps = 9))
        assertEquals("GET READY", prep.label)
        assertEquals(9, prep.centerNumber)
        assertTrue(prep.centerDimmed)
        assertEquals(0f, prep.outerProgress, 1e-6f)

        val cool = TimerUiMapper.map(state(Phase.COOLDOWN, set = 8, left = 5, duration = 30, reps = 0))
        assertEquals("COOLDOWN", cool.label)
        assertNull(cool.centerNumber)
        assertEquals(1f, cool.outerProgress, 1e-6f)

        val done = TimerUiMapper.map(state(Phase.DONE, set = 8, left = 0, duration = 0, reps = 0))
        assertEquals("DONE", done.label)
        assertEquals(65, done.centerNumber)
        assertTrue(done.done)
        assertEquals("", done.countdownText)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T12-1-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.timer.TimerUiMapperTest"`. Expected: compile FAIL, unresolved `TimerUiMapper`.

- [ ] **Step 3: Implement** — `ui/timer/TimerUiMapper.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.timer

import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState

enum class PhaseTone { WORK, REST, NEUTRAL }

data class TimerUiState(
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
    fun map(s: TimerState): TimerUiState {
        val inner = if (s.phaseDurationSec > 0) s.phaseSecondsLeft.toFloat() / s.phaseDurationSec else 0f
        val workFraction = if (s.phase == Phase.WORK && s.phaseDurationSec > 0) {
            (s.phaseDurationSec - s.phaseSecondsLeft).toFloat() / s.phaseDurationSec
        } else {
            0f
        }
        val base = TimerUiState(
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

- [ ] **Step 4: Run green** — same command. Expected: PASS (3 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): map timer state to centre, rings and labels"`

### Subtask 12.2: TimerViewModel

**Files:** Create `ui/timer/TimerViewModel.kt`; test `ui/timer/TimerViewModelTest.kt`.

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.ui.timer

import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `toggle pause, finish and leave done`() = runTest {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val settings = FakeSettingsRepository()
        val vm = TimerViewModel(controller, settings)
        backgroundScope.launch { vm.uiState.collect {} }
        controller.prepare(WorkoutSnapshot(TimingConfig(prepareSec = 0, sets = 1, workSec = 2, restSec = 0), CueConfig()))
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
        val vm = TimerViewModel(controller, FakeSettingsRepository())
        controller.prepare(WorkoutSnapshot(TimingConfig(), CueConfig()))
        controller.onServiceStarted()
        controller.start(List(8) { 8 })
        runCurrent()
        vm.stop()
        assertEquals(RunStatus.IDLE, vm.status.value)
    }

    @Test
    fun `notification permission is asked once`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = TimerViewModel(TimerController(backgroundScope) { testScheduler.currentTime }, settings)
        assertFalse(vm.notificationPermissionAsked.first())
        vm.onNotificationPermissionAsked()
        runCurrent()
        assertTrue(settings.askedFlow.value)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T12-2-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.timer.TimerViewModelTest"`. Expected: compile FAIL, unresolved `TimerViewModel`.

- [ ] **Step 3: Implement** — `ui/timer/TimerViewModel.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
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
        .map { it?.let(TimerUiMapper::map) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controller.state.value?.let(TimerUiMapper::map))

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
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (3 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): timer view model over the controller"`

### Subtask 12.3: DualRing and stateless TimerScreen

**Files:** Create `ui/timer/DualRing.kt`, `ui/timer/TimerScreen.kt`; test `ui/timer/TimerScreenTest.kt`.

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.ui.timer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.model.Phase
import com.mitenko.hiitcounter.domain.model.TimerState
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimerScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun ui(phase: Phase) = TimerUiMapper.map(
        TimerState(phase, set = 2, sets = 8, phaseSecondsLeft = 15, phaseDurationSec = 20, elapsedSec = 50,
            totalDurationSec = 240, repsThisSet = 8, totalReps = 65, paused = false),
    )

    @Test
    fun `work shows reps and no phase label`() {
        compose.setContent { HiitTheme { TimerScreen(ui(Phase.WORK), onTogglePause = {}, onClose = {}) } }
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertTextEquals("8")
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertTextEquals("00:15")
    }

    @Test
    fun `rest shows label and upcoming reps`() {
        compose.setContent { HiitTheme { TimerScreen(ui(Phase.REST), onTogglePause = {}, onClose = {}) } }
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertTextEquals("REST")
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertTextEquals("8")
    }

    @Test
    fun `buttons invoke callbacks`() {
        var toggles = 0
        var closes = 0
        compose.setContent { HiitTheme { TimerScreen(ui(Phase.WORK), onTogglePause = { toggles++ }, onClose = { closes++ }) } }
        compose.onNodeWithTag("pause").performClick()
        compose.onNodeWithTag("close").performClick()
        assertEquals(1, toggles)
        assertEquals(1, closes)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun `centre and controls stay visible at 200 percent font scale`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                HiitTheme { TimerScreen(ui(Phase.REST), onTogglePause = {}, onClose = {}) }
            }
        }
        compose.onNodeWithTag("phase_label", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("center_number", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("countdown", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("pause").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T12-3-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.timer.TimerScreenTest"`. Expected: compile FAIL, unresolved `TimerScreen`.

- [ ] **Step 3: Implement**

`ui/timer/DualRing.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.timer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mitenko.hiitcounter.ui.theme.HiitColors

/** Thick inner ring = current phase; thin outer ring = work-set progress (spec §9.2). */
@Composable
fun DualRing(inner: Float, outer: Float, innerColor: Color, outerColor: Color, modifier: Modifier = Modifier) {
    val innerAnim = remember { Animatable(inner) }
    val outerAnim = remember { Animatable(outer) }
    // Sweep smoothly while counting down / filling up; snap on phase or set resets.
    LaunchedEffect(inner) {
        if (inner < innerAnim.value) innerAnim.animateTo(inner, tween(900, easing = LinearEasing)) else innerAnim.snapTo(inner)
    }
    LaunchedEffect(outer) {
        if (outer > outerAnim.value) outerAnim.animateTo(outer, tween(900, easing = LinearEasing)) else outerAnim.snapTo(outer)
    }
    Canvas(modifier) {
        val d = size.minDimension
        val outerStroke = d * 0.02f
        val innerStroke = d * 0.07f
        val outerInset = outerStroke / 2
        val innerInset = outerStroke + d * 0.03f + innerStroke / 2
        ring(HiitColors.Track, 1f, outerInset, outerStroke)
        ring(outerColor, outerAnim.value, outerInset, outerStroke)
        ring(HiitColors.Track, 1f, innerInset, innerStroke)
        ring(innerColor, innerAnim.value, innerInset, innerStroke)
    }
}

private fun DrawScope.ring(color: Color, fraction: Float, inset: Float, stroke: Float) {
    if (fraction <= 0f) return
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * fraction.coerceAtMost(1f),
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}
```

`ui/timer/TimerScreen.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.timer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.theme.HiitColors

@Composable
fun TimerScreen(ui: TimerUiState, onTogglePause: () -> Unit, onClose: () -> Unit) {
    val color = when (ui.tone) {
        PhaseTone.WORK -> HiitColors.Work
        PhaseTone.REST -> HiitColors.Rest
        PhaseTone.NEUTRAL -> HiitColors.Neutral
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).testTag("close")) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.stop), tint = Color.White)
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Stat(R.string.sets_label, ui.setsText)
                Stat(R.string.elapsed_label, ui.elapsedText)
            }
            BoxWithConstraints(Modifier.fillMaxWidth(0.85f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                // Dp.toSp() cancels the user's font scale, so the digits always fit the ring.
                val numberSize = with(LocalDensity.current) { (maxWidth * 0.3f).toSp() }
                DualRing(ui.innerProgress, ui.outerProgress, innerColor = color, outerColor = HiitColors.SetRing, modifier = Modifier.fillMaxSize())
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = ui.description },
                ) {
                    ui.label?.let {
                        Text(it, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("phase_label"))
                    }
                    ui.centerNumber?.let {
                        Text(
                            "$it",
                            color = if (ui.centerDimmed) color.copy(alpha = 0.45f) else color,
                            fontSize = numberSize,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("center_number"),
                        )
                    }
                    Text(ui.countdownText, color = Color.White, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("countdown"))
                }
            }
            if (!ui.done) {
                FilledIconButton(
                    onClick = onTogglePause,
                    modifier = Modifier.size(72.dp).testTag("pause"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
                ) {
                    Icon(
                        painterResource(if (ui.paused) R.drawable.ic_play else R.drawable.ic_pause),
                        contentDescription = stringResource(if (ui.paused) R.string.resume else R.string.pause),
                        tint = Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(@StringRes label: Int, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(label), color = Color.LightGray, style = MaterialTheme.typography.labelLarge)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (4 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): dual-ring timer screen with reps in the centre"`

### Subtask 12.4: TimerRoute (stop dialog, back, keep-screen-on, notification permission)

**Files:** Create `ui/timer/TimerRoute.kt`.

- [ ] **Step 1: Implement** — `ui/timer/TimerRoute.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.RunStatus

@Composable
fun TimerRoute(onExit: () -> Unit, vm: TimerViewModel = hiltViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }

    LaunchedEffect(status) { if (status == RunStatus.IDLE) onExit() }
    KeepScreenOn()
    NotificationPermissionRequest(vm)

    // Running: confirm before stopping. DONE: leave without confirmation (spec §9.2).
    val onClose: () -> Unit = {
        if (status == RunStatus.DONE) {
            vm.leaveDone()
        } else {
            confirmStop = true
        }
    }
    BackHandler(onBack = onClose)

    ui?.let { TimerScreen(it, onTogglePause = vm::togglePause, onClose = onClose) }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.stop_workout_title)) },
            text = { Text(stringResource(R.string.stop_workout_body)) },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; vm.stop() }) { Text(stringResource(R.string.stop)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Asks for POST_NOTIFICATIONS once, after the workout has started (spec §4 step 6). */
@Composable
private fun NotificationPermissionRequest(vm: TimerViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val asked by vm.notificationPermissionAsked.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(asked) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!asked && !granted) {
            vm.onNotificationPermissionAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
```

- [ ] **Step 2: Verify** — Run (label `T12-4-BUILD`, ~2 min): `./gradlew assembleDebug lintDebug`. Expected: `BUILD SUCCESSFUL`, no lint errors. (Behaviour is exercised on-device in 14.2.)

- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(ui): timer route with stop confirmation, keep-screen-on and permission request"`

**Task 12 gate:** Run (label `T12-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 13: Settings screens (§9.3)

**Interfaces produced:** `object Routes { HOME, TIMER, SETTINGS, SETTINGS_TIMING, SETTINGS_PROGRESSION, SETTINGS_CURRENT, SETTINGS_CUES }`; `SettingsScaffold`, `NumberField`, `RepeatingIconButton`; `SettingsListScreen(onBack, onOpen: (String) -> Unit)`; `CuesSettingsViewModel` + `CuesSettingsRoute(onBack)`; `TimingSettingsViewModel` + `TimingSettingsRoute(onBack)` + stateless `TimingSettingsScreen(draft, validation, onBack, onChange, onSave)`; `ProgressionDraft`, `ProgressionSettingsViewModel` + `ProgressionSettingsRoute(onBack)`; `CurrentStateViewModel` + `CurrentStateRoute(onBack)`.

### Subtask 13.1: Routes, shared components, settings list

**Files:** Create `ui/navigation/Routes.kt`, `ui/common/SettingsComponents.kt`, `ui/settings/SettingsListScreen.kt`; test `ui/settings/SettingsListScreenTest.kt`.

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.navigation.Routes
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `entries open their routes`() {
        val opened = mutableListOf<String>()
        compose.setContent { HiitTheme { SettingsListScreen(onBack = {}, onOpen = { opened += it }) } }
        compose.onNodeWithText("Timing").performClick()
        compose.onNodeWithText("Progression").performClick()
        compose.onNodeWithText("Current State").performClick()
        compose.onNodeWithText("Cues").performClick()
        assertEquals(
            listOf(Routes.SETTINGS_TIMING, Routes.SETTINGS_PROGRESSION, Routes.SETTINGS_CURRENT, Routes.SETTINGS_CUES),
            opened,
        )
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-1-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.SettingsListScreenTest"`. Expected: compile FAIL, unresolved `SettingsListScreen`, `Routes`.

- [ ] **Step 3: Implement**

`ui/navigation/Routes.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.navigation

object Routes {
    const val HOME = "home"
    const val TIMER = "timer"
    const val SETTINGS = "settings"
    const val SETTINGS_TIMING = "settings/timing"
    const val SETTINGS_PROGRESSION = "settings/progression"
    const val SETTINGS_CURRENT = "settings/current"
    const val SETTINGS_CUES = "settings/cues"
}
```

`ui/common/SettingsComponents.kt`:
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
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

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    hint: String? = null,
    decimal: Boolean = false,
) {
    val support = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = support?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
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

`ui/settings/SettingsListScreen.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import com.mitenko.hiitcounter.ui.navigation.Routes

@Composable
fun SettingsListScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val entries = listOf(
        R.string.settings_timing to Routes.SETTINGS_TIMING,
        R.string.settings_progression to Routes.SETTINGS_PROGRESSION,
        R.string.settings_current_state to Routes.SETTINGS_CURRENT,
        R.string.settings_cues to Routes.SETTINGS_CUES,
    )
    SettingsScaffold(title = stringResource(R.string.settings), onBack = onBack) {
        entries.forEach { (label, route) ->
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(route) }.padding(vertical = 16.dp).testTag(route),
            )
            HorizontalDivider()
        }
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): settings list, routes and shared settings components"`

### Subtask 13.2: Cues settings

**Files:** Create `ui/settings/CuesSettings.kt`; test `ui/settings/CuesSettingsViewModelTest.kt`.

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CuesSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `toggles persist immediately`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = CuesSettingsViewModel(settings)
        vm.setSound(false)
        assertEquals(CueConfig(sound = false, vibration = true), settings.cuesFlow.value)
        vm.setVibration(false)
        assertEquals(CueConfig(sound = false, vibration = false), settings.cuesFlow.value)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-2-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.CuesSettingsViewModelTest"`. Expected: compile FAIL, unresolved `CuesSettingsViewModel`.

- [ ] **Step 3: Implement** — `ui/settings/CuesSettings.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuesSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val cues: StateFlow<CueConfig> = settings.cues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CueConfig())

    fun setSound(on: Boolean) {
        viewModelScope.launch { settings.setCues(settings.cues.first().copy(sound = on)) }
    }

    fun setVibration(on: Boolean) {
        viewModelScope.launch { settings.setCues(settings.cues.first().copy(vibration = on)) }
    }
}

@Composable
fun CuesSettingsRoute(onBack: () -> Unit, vm: CuesSettingsViewModel = hiltViewModel()) {
    val cues by vm.cues.collectAsStateWithLifecycle()
    SettingsScaffold(title = stringResource(R.string.settings_cues), onBack = onBack) {
        SwitchRow(R.string.sound, cues.sound, vm::setSound)
        SwitchRow(R.string.vibration, cues.vibration, vm::setVibration)
    }
}

@Composable
private fun SwitchRow(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): cues settings screen"`

### Subtask 13.3: Timing settings

**Files:** Create `ui/settings/TimingSettings.kt`; test `ui/settings/TimingSettingsViewModelTest.kt`, `ui/settings/TimingSettingsScreenTest.kt`.

- [ ] **Step 1: Failing tests**

`TimingSettingsViewModelTest.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimingSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `loads, edits and saves`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = TimingSettingsViewModel(settings)
        assertEquals(TimingConfig(), vm.draft.value)
        vm.update { it.copy(sets = 6) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(6, settings.timingFlow.value.sets)
    }

    @Test
    fun `over two hours is not saved`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = TimingSettingsViewModel(settings)
        vm.update { it.copy(sets = 20, workSec = 3599) }
        assertTrue(Field.TOTAL_DURATION in vm.validation.value.errors)
        var saved = false
        vm.save { saved = true }
        assertFalse(saved)
        assertEquals(TimingConfig(), settings.timingFlow.value)
    }
}
```

`TimingSettingsScreenTest.kt`:
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimingSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `steppers update values and total`() {
        compose.setContent {
            HiitTheme {
                var draft by remember { mutableStateOf(TimingConfig()) }
                TimingSettingsScreen(draft, SettingsValidator.timing(draft), onBack = {}, onChange = { draft = it(draft) }, onSave = {})
            }
        }
        compose.onNodeWithTag("total").assertTextEquals("TOTAL 04:00")
        compose.onNodeWithContentDescription("Increase SETS").performClick()
        compose.onNodeWithTag("value_SETS").assertTextEquals("9")
        compose.onNodeWithTag("total").assertTextEquals("TOTAL 04:30")
    }

    @Test
    fun `save disabled when invalid`() {
        val tooLong = TimingConfig(sets = 20, workSec = 3599)
        compose.setContent { HiitTheme { TimingSettingsScreen(tooLong, SettingsValidator.timing(tooLong), {}, {}, {}) } }
        compose.onNodeWithTag("save").assertIsNotEnabled()
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-3-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.TimingSettings*"`. Expected: compile FAIL, unresolved `TimingSettingsViewModel`, `TimingSettingsScreen`.

- [ ] **Step 3: Implement** — `ui/settings/TimingSettings.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.TimerText
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.ui.common.RepeatingIconButton
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
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

@HiltViewModel
class TimingSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    private val _draft = MutableStateFlow<TimingConfig?>(null)
    val draft: StateFlow<TimingConfig?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(SettingsValidator::timing) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    init {
        viewModelScope.launch { _draft.value = settings.timing.first() }
    }

    fun update(transform: (TimingConfig) -> TimingConfig) {
        _draft.update { it?.let(transform) }
    }

    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (!SettingsValidator.timing(d).isValid) return
        viewModelScope.launch {
            settings.setTiming(d)
            onSaved()
        }
    }
}

@Composable
fun TimingSettingsRoute(onBack: () -> Unit, vm: TimingSettingsViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
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
    val max = SettingsValidator.MAX_PHASE_SEC
    val totalError = validation.errors[Field.TOTAL_DURATION]
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
        StepperRow(stringResource(R.string.prepare), TimerText.formatMmSs(draft.prepareSec),
            onMinus = { onChange { it.copy(prepareSec = (it.prepareSec - 5).coerceAtLeast(0)) } },
            onPlus = { onChange { it.copy(prepareSec = (it.prepareSec + 5).coerceAtMost(max)) } })
        StepperRow(stringResource(R.string.sets), "${draft.sets}",
            onMinus = { onChange { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } },
            onPlus = { onChange { it.copy(sets = (it.sets + 1).coerceAtMost(SettingsValidator.MAX_SETS)) } })
        StepperRow(stringResource(R.string.work), TimerText.formatMmSs(draft.workSec),
            onMinus = { onChange { it.copy(workSec = (it.workSec - 5).coerceAtLeast(5)) } },
            onPlus = { onChange { it.copy(workSec = (it.workSec + 5).coerceAtMost(max)) } })
        StepperRow(stringResource(R.string.rest), TimerText.formatMmSs(draft.restSec),
            onMinus = { onChange { it.copy(restSec = (it.restSec - 5).coerceAtLeast(0)) } },
            onPlus = { onChange { it.copy(restSec = (it.restSec + 5).coerceAtMost(max)) } })
        StepperRow(stringResource(R.string.cooldown), TimerText.formatMmSs(draft.cooldownSec),
            onMinus = { onChange { it.copy(cooldownSec = (it.cooldownSec - 5).coerceAtLeast(0)) } },
            onPlus = { onChange { it.copy(cooldownSec = (it.cooldownSec + 5).coerceAtMost(max)) } })
        totalError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RepeatingIconButton(onMinus, R.drawable.ic_remove, stringResource(R.string.decrease, label))
            Text(
                value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 140.dp).testTag("value_$label"),
            )
            RepeatingIconButton(onPlus, R.drawable.ic_add, stringResource(R.string.increase, label))
        }
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (4 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): timing settings with steppers and total footer"`

### Subtask 13.4: Progression settings

**Files:** Create `ui/settings/ProgressionSettings.kt`; test `ui/settings/ProgressionSettingsViewModelTest.kt`.

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.testutil.FakeCounterRepository
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProgressionSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `save persists and resets hold count`() = runTest {
        val settings = FakeSettingsRepository()
        val counter = FakeCounterRepository()
        val vm = ProgressionSettingsViewModel(settings, counter)
        vm.update { it.copy(holdAt = "66", holdFor = "3") }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(ProgressionConfig(holdAt = 66, holdFor = 3), settings.progressionFlow.value)
        assertEquals(1, counter.resetHoldCountCalls)
    }

    @Test
    fun `unparseable and invalid drafts are not saved`() = runTest {
        val settings = FakeSettingsRepository()
        val counter = FakeCounterRepository()
        val vm = ProgressionSettingsViewModel(settings, counter)
        vm.update { it.copy(cap = "abc") }
        assertTrue(Field.CAP in vm.validation.value.errors)
        vm.save { }
        vm.update { it.copy(cap = "40") }
        assertFalse(vm.validation.value.isValid)
        vm.save { }
        assertEquals(ProgressionConfig(), settings.progressionFlow.value)
        assertEquals(0, counter.resetHoldCountCalls)
    }

    @Test
    fun `reset to defaults fills the draft`() = runTest {
        val vm = ProgressionSettingsViewModel(FakeSettingsRepository(progression = ProgressionConfig(cap = 90)), FakeCounterRepository())
        assertEquals("90", vm.draft.value?.cap)
        vm.resetToDefaults()
        assertEquals("72", vm.draft.value?.cap)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-4-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.ProgressionSettingsViewModelTest"`. Expected: compile FAIL, unresolved `ProgressionSettingsViewModel`.

- [ ] **Step 3: Implement** — `ui/settings/ProgressionSettings.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.NumberField
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
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

/** Text-field draft; parsed on validate/save. */
data class ProgressionDraft(
    val startingTotal: String,
    val floor: String,
    val cap: String,
    val holdAt: String,
    val holdFor: String,
    val windowHours: String,
    val penaltyHoursPerRep: String,
) {
    fun parse(): Pair<ProgressionConfig?, Map<Field, String>> {
        val errors = mutableMapOf<Field, String>()
        fun int(text: String, field: Field): Int? =
            text.trim().toIntOrNull().also { if (it == null) errors[field] = SettingsValidator.NOT_A_NUMBER }
        val st = int(startingTotal, Field.STARTING_TOTAL)
        val fl = int(floor, Field.FLOOR)
        val cp = int(cap, Field.CAP)
        val ha = int(holdAt, Field.HOLD_AT)
        val hf = int(holdFor, Field.HOLD_FOR)
        val wh = int(windowHours, Field.WINDOW_HOURS)
        val pr = penaltyHoursPerRep.trim().toDoubleOrNull().also {
            if (it == null) errors[Field.PENALTY_RATE] = SettingsValidator.NOT_A_NUMBER
        }
        if (errors.isNotEmpty()) return null to errors
        return ProgressionConfig(st!!, fl!!, cp!!, ha!!, hf!!, wh!!, pr!!) to errors
    }

    companion object {
        fun from(c: ProgressionConfig) = ProgressionDraft(
            c.startingTotal.toString(), c.floor.toString(), c.cap.toString(), c.holdAt.toString(),
            c.holdFor.toString(), c.windowHours.toString(), c.penaltyHoursPerRep.toString(),
        )

        fun validate(d: ProgressionDraft): ValidationResult {
            val (config, errors) = d.parse()
            return if (config == null) ValidationResult(errors) else SettingsValidator.progression(config)
        }
    }
}

@HiltViewModel
class ProgressionSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val counter: CounterRepository,
) : ViewModel() {
    private val _draft = MutableStateFlow<ProgressionDraft?>(null)
    val draft: StateFlow<ProgressionDraft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(ProgressionDraft::validate) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    init {
        viewModelScope.launch { _draft.value = ProgressionDraft.from(settings.progression.first()) }
    }

    fun update(transform: (ProgressionDraft) -> ProgressionDraft) {
        _draft.update { it?.let(transform) }
    }

    fun resetToDefaults() {
        _draft.value = ProgressionDraft.from(ProgressionConfig())
    }

    /** Saving also resets holdCount (spec §6) since hold-at/hold-for may have changed. */
    fun save(onSaved: () -> Unit) {
        val config = _draft.value?.parse()?.first ?: return
        if (!SettingsValidator.progression(config).isValid) return
        viewModelScope.launch {
            settings.setProgression(config)
            counter.resetHoldCount()
            onSaved()
        }
    }
}

@Composable
fun ProgressionSettingsRoute(onBack: () -> Unit, vm: ProgressionSettingsViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val d = draft ?: return
    val onChange = vm::update
    SettingsScaffold(
        title = stringResource(R.string.settings_progression),
        onBack = onBack,
        actions = { TextButton(onClick = { vm.save(onBack) }, enabled = validation.isValid) { Text(stringResource(R.string.save)) } },
    ) {
        NumberField(stringResource(R.string.starting_total), d.startingTotal, { v -> onChange { it.copy(startingTotal = v) } }, validation.errors[Field.STARTING_TOTAL])
        NumberField(stringResource(R.string.floor), d.floor, { v -> onChange { it.copy(floor = v) } }, validation.errors[Field.FLOOR])
        NumberField(stringResource(R.string.cap), d.cap, { v -> onChange { it.copy(cap = v) } }, validation.errors[Field.CAP])
        NumberField(stringResource(R.string.hold_at), d.holdAt, { v -> onChange { it.copy(holdAt = v) } }, validation.errors[Field.HOLD_AT], hint = validation.hints[Field.HOLD_AT])
        NumberField(stringResource(R.string.hold_for), d.holdFor, { v -> onChange { it.copy(holdFor = v) } }, validation.errors[Field.HOLD_FOR])
        NumberField(stringResource(R.string.window_hours), d.windowHours, { v -> onChange { it.copy(windowHours = v) } }, validation.errors[Field.WINDOW_HOURS])
        NumberField(stringResource(R.string.penalty_rate), d.penaltyHoursPerRep, { v -> onChange { it.copy(penaltyHoursPerRep = v) } }, validation.errors[Field.PENALTY_RATE], decimal = true)
        OutlinedButton(onClick = vm::resetToDefaults, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.reset_defaults))
        }
    }
}
```

- [ ] **Step 4: Run green** — same command. Expected: PASS (3 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): progression settings; saving resets hold count"`

### Subtask 13.5: Current state settings

**Files:** Create `ui/settings/CurrentStateSettings.kt`; test `ui/settings/CurrentStateViewModelTest.kt`.

- [ ] **Step 1: Failing test**:
```kotlin
package com.mitenko.hiitcounter.ui.settings

import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.model.CounterState
import com.mitenko.hiitcounter.testutil.FakeClock
import com.mitenko.hiitcounter.testutil.FakeCounterRepository
import com.mitenko.hiitcounter.testutil.FakeSettingsRepository
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CurrentStateViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = FakeClock()

    @Test
    fun `loads the current counter into the draft`() = runTest {
        val counter = FakeCounterRepository(CounterState(total = 65, bestStreak = 24, currentStreak = 4))
        val vm = CurrentStateViewModel(counter, FakeSettingsRepository(), clock)
        assertEquals(CurrentStateViewModel.Draft("65", "24", "4", null), vm.draft.value)
    }

    @Test
    fun `save overwrites the counter`() = runTest {
        val counter = FakeCounterRepository()
        val vm = CurrentStateViewModel(counter, FakeSettingsRepository(), clock)
        val last = clock.instant.minusSeconds(3600)
        vm.update { it.copy(total = "65", best = "24", current = "4", lastCheckIn = last) }
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        assertEquals(CounterState(65, 24, 4, last, 0), counter.stateFlow.value)
    }

    @Test
    fun `invalid drafts are rejected`() = runTest {
        val counter = FakeCounterRepository()
        val vm = CurrentStateViewModel(counter, FakeSettingsRepository(), clock)
        vm.update { it.copy(best = "3", current = "4") }
        assertTrue(Field.BEST_STREAK in vm.validation.value.errors)
        vm.update { it.copy(best = "4", lastCheckIn = clock.instant.plusSeconds(60)) }
        assertTrue(Field.LAST_CHECK_IN in vm.validation.value.errors)
        vm.update { it.copy(total = "x", lastCheckIn = null) }
        assertTrue(Field.TOTAL in vm.validation.value.errors)
        vm.save { }
        assertEquals(0, counter.overwriteCalls)
    }

    @Test
    fun `reset progress delegates to the repository`() = runTest {
        val counter = FakeCounterRepository()
        val vm = CurrentStateViewModel(counter, FakeSettingsRepository(), clock)
        var done = false
        vm.resetProgress { done = true }
        assertTrue(done)
        assertEquals(1, counter.resetProgressCalls)
    }
}
```

- [ ] **Step 2: Run red** — Run (label `T13-5-RED`): `./gradlew testDebugUnitTest --tests "com.mitenko.hiitcounter.ui.settings.CurrentStateViewModelTest"`. Expected: compile FAIL, unresolved `CurrentStateViewModel`.

- [ ] **Step 3: Implement** — `ui/settings/CurrentStateSettings.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.settings

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.Clock
import com.mitenko.hiitcounter.domain.Field
import com.mitenko.hiitcounter.domain.SettingsValidator
import com.mitenko.hiitcounter.domain.ValidationResult
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.ui.common.DateFormats
import com.mitenko.hiitcounter.ui.common.NumberField
import com.mitenko.hiitcounter.ui.common.SettingsScaffold
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class CurrentStateViewModel @Inject constructor(
    private val counter: CounterRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    data class Draft(val total: String, val best: String, val current: String, val lastCheckIn: Instant?)

    private var config = ProgressionConfig()
    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()
    val validation: StateFlow<ValidationResult> = _draft
        .map { it?.let(::validate) ?: ValidationResult() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ValidationResult())

    val zone: ZoneId get() = clock.zone()
    fun now(): Instant = clock.now()

    init {
        viewModelScope.launch {
            config = settings.progression.first()
            val s = counter.state.first()
            _draft.value = Draft(s.total.toString(), s.bestStreak.toString(), s.currentStreak.toString(), s.lastCheckIn)
        }
    }

    fun update(transform: (Draft) -> Draft) {
        _draft.update { it?.let(transform) }
    }

    private fun parsed(d: Draft): Triple<Int, Int, Int>? {
        val t = d.total.trim().toIntOrNull() ?: return null
        val b = d.best.trim().toIntOrNull() ?: return null
        val c = d.current.trim().toIntOrNull() ?: return null
        return Triple(t, b, c)
    }

    private fun validate(d: Draft): ValidationResult {
        val numbers = parsed(d)
        if (numbers == null) {
            val errors = mutableMapOf<Field, String>()
            if (d.total.trim().toIntOrNull() == null) errors[Field.TOTAL] = SettingsValidator.NOT_A_NUMBER
            if (d.best.trim().toIntOrNull() == null) errors[Field.BEST_STREAK] = SettingsValidator.NOT_A_NUMBER
            if (d.current.trim().toIntOrNull() == null) errors[Field.CURRENT_STREAK] = SettingsValidator.NOT_A_NUMBER
            return ValidationResult(errors)
        }
        val (t, b, c) = numbers
        return SettingsValidator.currentState(t, b, c, d.lastCheckIn, clock.now(), config)
    }

    /** Overwrites the counter and resets holdCount (spec §6). */
    fun save(onSaved: () -> Unit) {
        val d = _draft.value ?: return
        if (!validate(d).isValid) return
        val (t, b, c) = parsed(d) ?: return
        viewModelScope.launch {
            counter.overwrite(t, b, c, d.lastCheckIn)
            onSaved()
        }
    }

    fun resetProgress(onDone: () -> Unit) {
        viewModelScope.launch {
            counter.resetProgress()
            onDone()
        }
    }
}

@Composable
fun CurrentStateRoute(onBack: () -> Unit, vm: CurrentStateViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val validation by vm.validation.collectAsStateWithLifecycle()
    val d = draft ?: return
    var picking by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.settings_current_state),
        onBack = onBack,
        actions = { TextButton(onClick = { vm.save(onBack) }, enabled = validation.isValid) { Text(stringResource(R.string.save)) } },
    ) {
        NumberField(stringResource(R.string.current_total), d.total, { v -> vm.update { it.copy(total = v) } }, validation.errors[Field.TOTAL], hint = validation.hints[Field.TOTAL])
        NumberField(stringResource(R.string.best_streak_field), d.best, { v -> vm.update { it.copy(best = v) } }, validation.errors[Field.BEST_STREAK])
        NumberField(stringResource(R.string.current_streak_field), d.current, { v -> vm.update { it.copy(current = v) } }, validation.errors[Field.CURRENT_STREAK])
        Text(stringResource(R.string.last_check_in_field), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(d.lastCheckIn?.let { DateFormats.dateTime(it, vm.zone) } ?: stringResource(R.string.none), modifier = Modifier.weight(1f))
            TextButton(onClick = { picking = true }) { Text(stringResource(R.string.set)) }
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
            onPicked = { t -> picking = false; vm.update { it.copy(lastCheckIn = t) } },
            onDismiss = { picking = false },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_progress_title)) },
            text = { Text(stringResource(R.string.reset_progress_body)) },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetProgress(onBack) }) { Text(stringResource(R.string.reset)) } },
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

- [ ] **Step 4: Run green** — same command. Expected: PASS (4 tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): current state settings with date/time picker and reset"`

**Task 13 gate:** Run (label `T13-GATE`): `./gradlew testDebugUnitTest`. Expected: all PASS.

---

## Task 14: Navigation, wiring, device verification, PR

### Subtask 14.1: Nav host, active-run routing, MainActivity

**Files:** Create `ui/navigation/HiitNavHost.kt`; replace `MainActivity.kt`.

Active-run routing (§4): whenever `TimerController.status` is RUNNING and the current destination isn't the timer, navigate to it (covers Start, notification tap and a recreated activity). Leaving the timer (stop / DONE dismissed) pops back to home.

- [ ] **Step 1: Nav host** — `ui/navigation/HiitNavHost.kt`:
```kotlin
package com.mitenko.hiitcounter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.ui.home.HomeRoute
import com.mitenko.hiitcounter.ui.settings.CuesSettingsRoute
import com.mitenko.hiitcounter.ui.settings.CurrentStateRoute
import com.mitenko.hiitcounter.ui.settings.ProgressionSettingsRoute
import com.mitenko.hiitcounter.ui.settings.SettingsListScreen
import com.mitenko.hiitcounter.ui.settings.TimingSettingsRoute
import com.mitenko.hiitcounter.ui.timer.TimerRoute

@Composable
fun HiitNavHost(controller: TimerController) {
    val nav = rememberNavController()
    val status by controller.status.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeRoute(onOpenSettings = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.TIMER) { TimerRoute(onExit = { nav.popBackStack(Routes.HOME, inclusive = false) }) }
        composable(Routes.SETTINGS) { SettingsListScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }) }
        composable(Routes.SETTINGS_TIMING) { TimingSettingsRoute(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS_PROGRESSION) { ProgressionSettingsRoute(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS_CURRENT) { CurrentStateRoute(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS_CUES) { CuesSettingsRoute(onBack = { nav.popBackStack() }) }
    }

    LaunchedEffect(status) {
        if (status == RunStatus.RUNNING && nav.currentDestination?.route != Routes.TIMER) {
            nav.navigate(Routes.TIMER) { launchSingleTop = true }
        }
    }
}
```

- [ ] **Step 2: MainActivity** — replace `MainActivity.kt`:
```kotlin
package com.mitenko.hiitcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.ui.navigation.HiitNavHost
import com.mitenko.hiitcounter.ui.theme.HiitTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var controller: TimerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HiitTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) { HiitNavHost(controller) }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Full verification** — Run (label `T14-1-FULL`, ~3–5 min): `./gradlew clean assembleDebug testDebugUnitTest lintDebug`. Expected: `BUILD SUCCESSFUL`; every test passes; no lint errors. Record the total test count from `app/build/reports/tests/testDebugUnitTest/index.html`.

- [ ] **Step 4: Commit** — `git add -A && git commit -m "feat: wire navigation, active-run routing and app entry point"`

### Subtask 14.2: Device verification

- [ ] **Step 1: Install**

```bash
adb devices
./gradlew installDebug
adb shell am start -n com.mitenko.hiitcounter/.MainActivity
```
If no device is attached, ask the user to connect one or start an emulator — do not start or kill emulators you did not launch.

- [ ] **Step 2: Manual acceptance checklist (§11)** — report each item pass/fail to the user; fix failures in new commits on the branch before continuing:
1. Fresh install: home shows 8 rows of 6 (48 total), streaks 0, Last Check In "—".
2. Settings → Current State: enter 65 / 24 / 4 and a last check-in of yesterday → Save → home shows 9,8,8,8,8,8,8,8 and 65.
3. Start: notification-permission prompt appears once (Android 13+); "GET READY" + dimmed 9 with a 10 s countdown and 3-2-1 beeps; WORK shows a bright **9** with no label; inner ring sweeps, outer ring fills; REST shows "REST" + dimmed next reps; afterwards home shows 66 and "Checked in today".
4. Start again the same day → workout runs, total unchanged.
5. With music playing, cues duck the music and it recovers.
6. Screen off mid-workout → cues and vibration stay on time; notification shows phase/time; notification Stop ends it.
7. Swipe the app away mid-workout → the timer keeps running; tapping the notification reopens the timer screen.
8. Pause for > 2 minutes with the screen off, then resume → countdown continues correctly.
9. Deny notification permission (fresh install) → workout still runs; ✕ stops it.
10. Timing settings: steppers and long-press repeat work; TOTAL updates; above 2:00:00 it turns red and Save is disabled.
11. TalkBack: centre announces "Work, set 2 of 8, 9 reps"-style text once per phase; all buttons are labelled. Font scale 200 %: home, timer and settings remain usable.

### Subtask 14.3: Docs and PR

- [ ] **Step 1: Update claude.md** — add under "Working rules":
```markdown
- Build/test: `./gradlew assembleDebug testDebugUnitTest lintDebug` (Git Bash, repo root). Compose UI tests run on Robolectric (`@Config(sdk = [34])`), no emulator needed.
- Plan: `docs/superpowers/plans/2026-09-24-hiit-counter.md`.
```

- [ ] **Step 2: Commit** — `git add -A && git commit -m "docs: build/test instructions in claude.md"`

- [ ] **Step 3: Ask the user before pushing.** If they agree:
```bash
git push -u origin feat/app-v1
gh pr create --base main --head feat/app-v1 --title "HIIT Counter v1" --body-file "$SCRATCH/claude-pr-body.md"
```
Write `$SCRATCH/claude-pr-body.md` first: a summary of what was built, the test count from 14.1, and the checklist results from 14.2 (no AI attribution line).

---

## Plan Review Findings (2026-09-24)

This review checks the implementation plan for executable correctness and alignment with the approved design spec and the findings recorded in `docs/superpowers/specs/review.md`.

### High priority

1. **Task 6 tests and implementation contradict the service-start contract**

   `TimerController.start()` only checks `RunStatus.PREPARING`; it does not require `ServiceStatus.Started`. The Task 6.1 test even calls `prepare()` followed directly by `start()` and expects success, while Task 11 relies on service acknowledgement before starting. This permits a timer to run without a foreground service. Make `start()` reject `Pending` and `Failed`, update the controller tests to call `onServiceStarted()`, and add a regression test that a pending/failed service cannot start the engine.

2. **Service-start acknowledgement has an unbounded/racy ownership window**

   `TimerService` calls `controller.onServiceStarted()` only after `startForeground()` succeeds, but the service is not tied to a unique preparation/run ID. A delayed callback from an old service start can acknowledge a newer preparation, and a timeout can call `cancelPrepare()` while the old service later becomes active. Add a monotonically unique run ID/token to `WorkoutSnapshot`, `ServiceStatus`, and callbacks; ignore stale acknowledgements; and explicitly stop the service on timeout/cancellation. Test timeout followed by a late success and rapid stop/start.

3. **The plan does not implement an abnormal-termination recovery signal**

   The design requires an interrupted-workout indication after process death, but the plan's `TimerController` is in-memory only, `TimerService` uses `START_NOT_STICKY`, and there is no persisted run marker or last-known run state. Task 14.2 only checks swipe-away behavior, not OS process death. Either remove the promised toast/abnormal-termination behavior or add a small persisted run lifecycle marker, define when it is written/cleared, and test process/service recreation and stale-marker cleanup.

4. **`TimerService` can stop itself from its initial `IDLE` state**

   In Task 10.2, `observe()` launches a collector over `controller.status`, whose initial value is `IDLE`; the collector immediately releases resources and calls `stopSelf()`. This can race `controller.onServiceStarted()` and make service startup flaky. Gate lifecycle shutdown on an acknowledged run ID, or call `onServiceStarted()` before registering the terminal-status collector and ignore terminal states from unrelated runs. Add a service lifecycle test for the initial state.

5. **Wake-lock renewal is not actually guaranteed**

   `updateWakeLock()` is called only when a new `TimerState` arrives, and `acquireWakeLock()` returns early if the lock is already held. This means the original timeout is never renewed. It currently lasts `remaining + 60s`, but pauses, delayed callbacks, long cue work, and future state changes can invalidate that assumption. Define renewal/release behavior explicitly, test a timeout boundary, and ensure pause releases the lock and resume reacquires it with a fresh timeout.

### Medium priority

6. **Pause timeout is not protected against stale jobs**

   `TimerController.pause()` launches a timeout job that eventually calls the generic `stop()`. If a pause is resumed and a new pause begins, cancellation is cooperative; a previously scheduled job may still observe completion and stop the new pause/run. Associate the timeout with a pause generation or job identity and stop only if the generation is still current. Add a pause → resume → pause test around the first timeout boundary.

7. **The fake repository does not model the atomicity being tested**

   Task 8.3 tests concurrent DataStore check-ins, but `FakeCounterRepository.checkIn()` is a plain read/compute/write sequence with no serialization. Home/ViewModel concurrency tests can therefore pass or fail for reasons unrelated to production behavior. Add a mutex or a DataStore-backed fake, and include a concurrent fake test if the fake is used to validate UI behavior.

8. **Reset-progress tests hard-code the default starting total**

   `FakeCounterRepository.resetProgress()` sets `CounterState(total = 48)`, while the design explicitly says a fresh state reads the live `ProgressionConfig.startingTotal`. Add a test with a non-default starting total and make the fake derive the value from its settings dependency, matching `DataStoreCounterRepository`.

9. **DataStore recovery still risks silent counter loss**

   Task 8.4 uses `ReplaceFileCorruptionHandler { emptyPreferences() }`, and the repositories fall back to defaults after `IOException`. For the counter store this can reset progress while presenting a valid-looking state. Add a persisted recovery/error signal and a user-visible recovery decision, or explicitly document and test the accepted data-loss policy. Also add malformed timestamp coverage; `Instant.ofEpochMilli(...)` is currently not guarded.

10. **Settings writes are not validated at the repository boundary**

    The plan validates UI input, but `setTiming`, `setProgression`, and `overwrite` accept invalid values from any caller and can persist configurations that readers later replace piecemeal or wholesale. Define whether repositories reject invalid domain objects or normalize them, then test direct invalid calls. This is especially important for `cap == floor`, total-duration limits, and hold configuration changes.

11. **The progression snapshot is not part of the workout snapshot**

    Task 11 reads timing, cues, and progression separately, then checks in with the progression object. A settings update can occur between those reads, producing a mixed-version run (counter changed under one config while the timer uses another). Capture a single immutable settings snapshot before `prepare`, or serialize settings/check-in operations. Add a race test with a settings update during `onStart()`.

12. **Notification permission handling is missing the “asked” persistence path**

    The plan defines `notificationPermissionAsked` and `markNotificationPermissionAsked()`, but the visible Task 11/14 flow does not show the permission launcher callback calling `markNotificationPermissionAsked()`, nor does it specify behavior when permission is denied or already permanently denied. Add the Activity-result wiring, persist the flag on both grant and denial, and test that the prompt is shown at most once while the workout remains usable.

13. **The service notification action is not covered for stale or absent runs**

    `ACTION_STOP` calls `controller.stop()` but does not define behavior when the controller has already been cleared, the process was recreated, or the notification belongs to a prior run. Add run-token validation to the action intent and tests for duplicate Stop, Stop after DONE, and Stop after service recreation. Ensure the notification is removed on every terminal path.

### Low priority / plan quality

14. **The version catalog contains future or environment-dependent pins without a compatibility check**

    The plan pins compile/target SDK 36, AGP 8.13.0, Kotlin 2.2.10, and Compose BOM `2026.06.01`, but only says to run `help`/`assembleDebug`. Add an explicit toolchain/dependency resolution gate, document the required JDK/SDK packages, and provide a fallback decision if the locally installed SDK or repository cannot resolve those versions. Do not copy a wrapper from another project without verifying its distribution checksum/version.

15. **The plan's “replace the whole file” rule increases merge and review risk**

    Multiple subtasks replace core files such as `TabataEngine.kt`, `TimerController.kt`, and `CounterRepository.kt`. This is workable for a blank repository but makes it easy to lose fixes or silently alter earlier contracts. Add a required diff review at each replacement, keep public interfaces stable unless the task explicitly changes them, and make each task gate include the affected focused tests rather than relying only on the full suite.

16. **Accessibility verification is too late and too narrow**

    Accessibility appears only in the final manual checklist. Add content descriptions, phase text alternatives, minimum touch targets, and large-font layout checks to the relevant Home, Timer, and Settings subtasks so regressions are caught before Task 14. The timer must not rely on ring color alone to communicate phase.

### Plan Review Resolutions (2026-09-24)

Each finding above was checked against the plan's code and the approved spec. Accepted items are now in the plan; rejected items keep the current design, with the reason.

| # | Resolution | Where |
|---|---|---|
| 1 | **Accepted.** `TimerController.start()` now also requires `ServiceStatus.Started`; new test `start requires the foreground service to have started`; controller and timer-VM tests acknowledge the service before starting. | 6.1–6.3, 12.2 |
| 2 | **Partly accepted.** Run tokens rejected: `TimerService` is a process singleton, so an acknowledgement means "the service is in the foreground" — true for whichever preparation is current, and a late ack after a timeout hits `IDLE` and is ignored. The real bug found: a Start during the DONE grace period re-entered a `started` service and was never acknowledged (5 s timeout). `onStartCommand` now re-acknowledges when already started. | 10.3 |
| 3 | **Rejected.** Spec §10 explicitly decided a killed workout ends silently with no "interrupted" message (review.md item 2 resolution); nothing is lost because the check-in happened at Start. | spec §10 |
| 4 | **Premise rejected, related race fixed.** The service is only started after `prepare()`, so its first observed status is PREPARING, never IDLE. The real race: the 1.5 s DONE grace `delay` could `stopSelf()` during the next run — the status collector now uses `collectLatest`, so a new status cancels the pending stop. | 10.3 |
| 5 | **Premise rejected, now proven by tests.** The timeout (remaining active time + 60 s) is taken fresh on every acquire, pause releases, resume re-acquires, so it cannot expire mid-workout. The rule moved into pure `WakeLockPolicy` with tests, including "timeout always outlasts remaining active time". | new 10.2, 10.3 |
| 6 | **Test accepted, mechanism rejected.** A cancelled `delay` never completes (prompt cancellation), so a generation counter adds nothing. Added `a resumed pause's timeout cannot stop a later pause` as a guard. | 6.3 |
| 7 | **Rejected.** Atomicity is a property of the real repository and is tested there (8.3 concurrent check-ins). The fake runs single-threaded in VM tests; Home's debounce is tested at the VM level. | — |
| 8 | **Accepted for the real repository:** `reset progress uses the live starting total`. The fake only counts calls in VM tests; no VM test depends on its reset value. | 8.2 |
| 9 | **Rejected.** Corruption-recovery UX was declined in the spec review (review.md item 7). `Instant.ofEpochMilli` accepts every `Long` value, so a stored timestamp can't throw. | spec §5 |
| 10 | **Rejected.** Only validating ViewModels write; reads already validate per key and fall back. Throwing in a repository setter would only turn a UI bug into a crash. | — |
| 11 | **Rejected.** Accepted and documented in spec §6 (settings and Start are on different screens; single user). | spec §6 |
| 12 | **Already covered.** 12.4 calls `onNotificationPermissionAsked()` (persisting the flag) before launching the prompt, so it's recorded on grant *and* denial and shown at most once; the workout runs regardless (tested in 12.2 and on device in 14.2 item 9). | 12.2, 12.4 |
| 13 | **Partly accepted.** `stop()` is already idempotent (tested: stop when idle, stop after DONE is a no-op). Fixed the gap: a Stop action arriving at a freshly recreated, never-foregrounded service now calls `stopSelf()`. The notification is removed on every terminal path via `stopForeground(REMOVE)`. | 10.3 |
| 14 | **Accepted in part.** Added a `./gradlew --version` = 8.13 check and a stop-and-ask rule if any pin fails to resolve. The pins themselves are the set already building in `D:\Claude\apps\storyteller` on this machine (all present in the local Gradle cache), not future versions. | Structure rules, 1.1 |
| 15 | **Accepted.** Mandatory `git diff` review before committing any whole-file replacement; focused tests already run in every subtask, with the full suite at each task gate. | Structure rules |
| 16 | **Partly accepted.** Added a 200 % font-scale Compose test for the timer. Content descriptions and 48 dp targets are already in the components from their first subtask (11.4, 12.3, 13.1), and phase is never colour-only: REST/GET READY/COOLDOWN/DONE show text labels and WORK is distinguished by the bright number with no label (user decision: no "WORK" label). | 12.3 |
