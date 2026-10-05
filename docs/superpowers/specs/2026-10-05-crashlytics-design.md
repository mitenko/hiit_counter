# REPKIT — Crash reporting and analytics (spec revision 30)

Status: Requested by the user (2026-10-05). The orchestrator chose the recommended defaults below, and the user can change them. Analytics was added after the user turned on Google Analytics in the Firebase console.

New; amends nothing. Firebase project `repkit-mitenko`, app `com.mitenko.repkit` (`app/google-services.json`, committed: it isn't a secret).

## 1. Scope
- **Firebase Crashlytics** reports crashes, plus the non-fatals and breadcrumbs in §4.
- **Firebase Analytics** sends its **automatic events only**. The app logs no custom events and sets no user properties.
- One switch controls both (§3).

Versions: `com.google.firebase:firebase-bom` 34.19.0 with `firebase-crashlytics` and `firebase-analytics` (no versions of their own); Gradle plugins `com.google.gms.google-services` 4.5.0 and `com.google.firebase.crashlytics` 3.0.8. All are in `gradle/libs.versions.toml`.

## 2. When collection is on
- The manifest starts both off: `firebase_crashlytics_collection_enabled` and `firebase_analytics_collection_enabled` are `false`. Nothing is sent before the app applies the rule below.
- `domain/telemetryEnabled(debugBuild, sendInDebug, switchOn)` = `(!debugBuild || sendInDebug) && switchOn`, the same rule for both services.
  - `debugBuild` is `BuildConfig.DEBUG`.
  - `sendInDebug` is `BuildConfig.CRASHLYTICS_IN_DEBUG`. It's true only when the build is made with `-PcrashlyticsInDebug=true`, for a one-off check on a device.
  - So a debug build sends nothing by default. A release build follows the switch.

## 3. The switch
- **The ⚙ dialog is renamed "Settings"** (the `settings` string), for both its title and the ⚙ content description. Its first section keeps an **Appearance** heading (the `appearance` string) over System / Light / Dark.
- **"Share crash reports and usage"** sits under the theme options, after a divider. It has an ⓘ that explains what's sent. It's on by default.
- It's stored in `app.preferences_pb` as `crash_reports_enabled` (boolean, true when absent): `AppPreferences.crashReportsEnabled` / `setCrashReportsEnabled`.
- Toggling it saves at once and leaves the dialog open. (Picking a theme still closes it.)
- `platform/TelemetryInitializer`, started by `HiitApp.onCreate`, collects the preference on the application scope. Whenever the effective value changes, it calls `CrashReporter.setEnabled`. That call sets `FirebaseCrashlytics.setCrashlyticsCollectionEnabled` and `FirebaseAnalytics.setAnalyticsCollectionEnabled` together. DataStore reads off the main thread, so start-up isn't blocked.
- When collection is switched off, any unsent Crashlytics reports are deleted too (`deleteUnsentReports`). Reports from while it was off are never sent later.

## 4. What the app reports
`domain/CrashReporter` (`setEnabled`, `recordNonFatal(t, context)`, `log(message)`) is bound in `di/TelemetryModule` to `platform/FirebaseCrashReporter`. Tests use `NoOpCrashReporter` or a fake, so JVM tests never load Firebase. Every case below keeps its old behaviour. The report is an addition.

| Where | What's caught | Report |
| --- | --- | --- |
| `data/SessionRecorder` | A workout session row that fails to save | `recordNonFatal` |
| `data/EntryMapping` | Read repairs: the `checked()` field fallbacks, the timing and progression group fallbacks, the holds repair, an invalid name, total or type | `log` (a breadcrumb, since repairs can be frequent) |
| `platform/AndroidCueSpeaker` | Text-to-speech can't initialise or has no usable voice | `log` |
| `platform/AndroidVoiceAvailability` | The voice check times out | `log` |

- **EntryMapping** is plain code. It reaches the reporter through `RepairBreadcrumbs.reporter`, which `TelemetryInitializer` sets and which is a no-op otherwise. Mapping results don't change.
- **A breadcrumb names only the entry id and the field** ("EntryMapping: entry 7 repaired sets"). The stored values go to Logcat only.
- **A non-fatal's context is a fixed string** ("SessionRecorder: could not record a run").

## 5. Privacy
**Sent, while the switch is on (release builds):**
- **Crashlytics:**
  - crash and non-fatal stack traces;
  - the device model, OS version and app version;
  - a Crashlytics installation ID;
  - the §4 breadcrumbs.
- **Analytics:**
  - automatic events, such as `first_open`, `session_start`, `user_engagement` and screen views;
  - the app instance ID;
  - device and OS information (model, OS version, language, and the country Google derives from the connection).

**Never sent:**
- workout data (reps, totals, streaks, holds, timings, sessions);
- entry names;
- check-in history;
- custom events or user properties.

Turning the switch off stops both services at once and deletes unsent crash reports.

## 6. Tests
- `CrashReporterTest`: `telemetryEnabled` for every combination of build type, flag and switch. The one value drives both services.
- `TelemetryInitializerTest`: the switch reaches `setEnabled` as it changes, for release, debug and debug with the flag. `start` routes EntryMapping's breadcrumbs.
- `AppPreferencesTest`: `crashReportsEnabled` defaults to true and round-trips.
- `SessionRecorderTest`: a failed save calls `recordNonFatal` once and still doesn't crash.
- `EntryMappingTest`: a repair leaves value-free breadcrumbs and the same result. A valid row leaves none.
- `EntryListViewModelTest` covers the switch's flow and its setter. `EntryListScreenTest` covers the switch: on by default, toggling calls the setter, and the ⓘ text.
