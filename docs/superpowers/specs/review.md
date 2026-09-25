Here is a deep review of your spec. Overall, it’s remarkably sharp—the scope is tightly bounded, the pure-Kotlin domain separation is clean, and the progression rules handle edge cases (like clock rollback and rounding) that usually trip up fitness trackers.

That said, based on the **Deep Review Findings (Section 13)** in your spec, there are several key architecture, lifecycle, and domain edge cases that need explicit decisions and refinements before turning this into code.

---

### Key Architectural & Domain Decisions Needed

#### 1. Hold-After-Miss Policy (Finding #13)

Section 6 outlines hold behavior, but we should explicitly finalize how hold state reacts to a miss:

* **Recommended Decision:** Treat any miss that leaves the total at `holdAt` as resetting `holdCount = 1` (the check-in that incurred the miss counts as the first completed day at `holdAt`). A miss that drops the total *below* `holdAt` resets `holdCount = 0`.
* *Rationale:* This keeps the rule deterministic and easy to test without needing a secondary state variable to remember pre-miss history.

#### 2. Start / Check-in Atomicity (Finding #1)

Currently, `HomeViewModel.onStart()` commits the check-in to DataStore *before* starting `TimerService`. If `startForegroundService()` throws (e.g., `ForegroundServiceStartNotAllowedException` on Android 12+) or the process dies during navigation, the daily check-in is consumed, but no workout happened.

* **Recommended Adjustment:**
1. `TimerController.prepare(snapshot)` initializes the snapshot.
2. `TimerService` is started and calls `startForeground()`.
3. Once the service confirms it is active, `CounterRepository.checkIn()` is committed within the transaction.
4. The timer countdown begins.
5. If service startup fails, display an in-app error snackbar and leave the check-in untouched.



#### 3. Pause & Partial Wake Lock Strategy (Finding #3)

Holding a `PARTIAL_WAKE_LOCK` for `workout duration + 1 min` fails if the user pauses the workout and sets their phone down for 10 minutes.

* **Recommended Adjustment:**
* While `RUNNING`: Acquire/maintain the `PARTIAL_WAKE_LOCK`.
* While `PAUSED`: Release the wake lock (let the CPU sleep).
* On `RESUME`: Re-acquire the wake lock.
* In addition, add an auto-stop or maximum pause timeout (e.g., 30 minutes) after which `TimerController` transitions to `STOPPED` and `TimerService.stopSelf()` is called to prevent runaway background battery drain.



#### 4. Exact Timer Emissions & Boundary Semantics (Finding #4)

To eliminate ambiguity during `TabataEngine` implementation:

* For a 20-second WORK phase, `phaseSecondsLeft` will emit `20` immediately on phase entry, then tick `19, 18, ..., 1, 0`.
* The `0` tick coincides with the phase transition: `TabataEngine` emits `phaseSecondsLeft = 0` for the ending phase, immediately followed by `phaseSecondsLeft = nextPhaseDuration` for the starting phase.
* Cue sound triggers evaluate on `phaseSecondsLeft` entries (e.g., beeps at 3, 2, 1; phase-start tone at `nextPhaseDuration`).

---

### Suggested Revisions to the Spec

Below are the updated sections incorporating the review findings into your spec structure.

#### Updated Section 4: Data Flow — Pressing Start

```markdown
### Data flow — pressing Start
1. `HomeViewModel.onStart()` is debounced and requests `TimerController.prepareWorkout(config, cueConfig)`.
2. `TimerService` is started via `ContextCompat.startForegroundService()`.
3. On API 33+, if `POST_NOTIFICATIONS` has not been granted, request it. (If denied, the service continues running with task-manager visibility).
4. Once `TimerService` binds/acknowledges start, `CounterRepository.checkIn(config, clock)` runs **inside a single `DataStore.updateData {}` transaction**.
5. `RepDistributor.distribute(total, sets)` distributes the checked-in total to the snapshot.
6. `TimerController.start()` starts the `TabataEngine` flow, acquiring the `PARTIAL_WAKE_LOCK`.
7. App navigates to `TimerScreen`.
8. **Failure handling:** If `TimerService` fails to start (`ForegroundServiceStartNotAllowedException`), the check-in is NOT performed, an error banner is shown on `HomeScreen`, and the workout is cancelled.

```

#### Updated Section 6: Hold-After-a-Miss Explicit Rule

```markdown
**Hold after a miss:** 
If hold is enabled (`holdFor > 0 && floor <= holdAt < cap`) and a missed check-in's post-penalty total equals `holdAt`:
- `holdCount` is reset to `1` (the day of the missed check-in counts as day 1 at `holdAt`).
- If post-penalty total != `holdAt`, `holdCount` is reset to `0`.

```

#### Updated Section 10: Validation & Runtime Constraints

```markdown
**Validation & Limits**
- **Timing Max Boundary:** Individual phase limits remain `≤ 59:59`, but the **Total Workout Duration** must not exceed `02:00:00` (120 minutes). Save is disabled in Settings if total duration > 120 mins.
- **Wake Lock & Pause:** `PARTIAL_WAKE_LOCK` is acquired during active ticking and released while paused. If paused continuously for > 30 minutes, `TimerController` automatically stops the workout and releases the service.
- **Process Death:** If the app process is killed by OS low-memory management during a run:
  - `TimerService` is set to `START_NOT_STICKY`.
  - On process death, the workout ends. Because check-in was committed at start, the daily rep total is safe. 
  - On next app launch, the app detects an abnormal termination and opens to `HomeScreen` with a discreet "Previous workout was interrupted" toast.

```

---

### Recommended Version Catalog (`gradle/libs.versions.toml`) Pins

To resolve Finding #11 and guarantee reproducible build environments across machines and CI:

```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.20"
coreKtx = "1.13.1"
lifecycle = "2.8.5"
activityCompose = "1.9.2"
composeBom = "2024.09.00"
hilt = "2.52"
navigationCompose = "2.8.0"
datastore = "1.1.1"
coroutines = "1.8.1"
junit = "4.13.2"
androidxTest = "1.6.1"

[libraries]
# Standard Compose, Hilt, DataStore dependencies mapped to above versions...

```

---

### Next Steps

1. Accept the proposed resolutions for **Hold-after-miss**, **Start atomicity**, and **Pause/Wake-lock management**.
2. Incorporate these updates into `2026-09-24-hiit-counter-design.md` in your repo.
3. Begin execution on feature branches according to the project conventions (starting with `domain` models, `RepProgressionTest`, and `TabataEngineTest`).

---

## Additional Deep Review Findings (2026-09-24)

The following findings are from a consistency and implementation-risk review of the design spec against `references/sheet_script.js`, the referenced screens, and current Android lifecycle constraints. Items marked **Decision required** should be resolved before implementation; the remaining items should be incorporated into the implementation plan and tests.

### High priority

1. **Check-in commit can be orphaned from workout start — Decision required**

   The flow commits the check-in before requesting `POST_NOTIFICATIONS`, starting the service, and navigating. A process death, activity finish, service-start exception, or other interruption after the commit can leave the user charged for the day without a workout. “The workout starts whatever the answer” is an intent, not a guarantee. Define a failure/recovery policy and make the boundary explicit. The safest design is to start the controller/service before requesting notification permission, handle all start failures visibly, and persist a small “pending workout snapshot” until the service acknowledges ownership. Alternatively, document that a failed start consumes the check-in and add a user-visible retry/reset action. Add tests for permission cancellation, service-start failure, and activity recreation at each step.

2. **Timer ownership is underspecified across process death and service recreation**

   `TimerController` is an in-process singleton, while `TimerService` is described as a thin host. If Android kills the process, both disappear; if the service is recreated, there is no persisted run snapshot or explicit `START_NOT_STICKY`/`START_REDELIVER_INTENT` policy. The spec says the workout ends, but the user-facing state after a kill is not defined. Specify the service return mode, the exact notification/state shown after an unexpected end, and whether the persisted check-in remains valid. Also define idempotency for repeated `start`, `stop`, and service bind/start commands so a notification action cannot race an activity command.

3. **Wake-lock timeout is incompatible with an indefinitely paused workout**

   The partial wake lock has a timeout of “workout duration + 1 min,” but pause can last longer than that. After the timeout, a screen-off paused/resumed workout may stop receiving timely ticks and cues. Specify one of: release the wake lock while paused and reacquire on resume; use a renewable timeout while running; or impose and surface a maximum pause duration. Test pause beyond the nominal timeout and resume with the screen off. Also state the wake-lock tag and ensure release is in a `finally`/lifecycle-safe path if service setup fails.

4. **Timer boundary semantics are not precise enough to implement consistently**

   “Emits once per second and on every transition” does not define whether a phase of 20 seconds emits `20..1` or `19..0`, when `elapsedSec` increments, or whether the transition emission is both the prior `0` and the next phase’s full duration. This affects cues, UI snapshots, and drift tests. Specify the initial state, the exact timestamp-to-state mapping, transition ordering, and whether `phaseSecondsLeft == 0` is observable. Add table-driven tests for a one-second phase, zero-duration adjacent phases, pause at a boundary, and resume at a boundary.

### Medium priority

5. **Cue delivery needs an event contract, not only state collection**

   A service forwarding cues from `TimerController` state changes can replay a transition when a collector is recreated, or miss a cue if `SoundPool` has not finished loading. Define a one-shot cue/event stream with a run identifier and sequence number, separate from replayable `TimerState`; make cue handling idempotent. Specify behavior when audio focus or sound loading fails, and test that each phase transition produces exactly one cue sequence.

6. **Concurrent settings and check-in operations can use inconsistent configuration**

   `checkIn(config, clock)` receives a config snapshot, while progression settings are persisted separately. A settings save racing a Start action can apply the old config to the check-in and the new config to distribution/UI. Define the consistency rule: capture and persist a versioned progression snapshot with the counter update, serialize settings/check-in writes, or explicitly accept mixed-version behavior. Add a concurrency test covering a progression save racing `checkIn`.

7. **DataStore schema, encoding, and corruption behavior are incomplete**

   `Instant`, nullable values, and all configuration fields need a documented Preferences key/encoding format, schema version, and migration strategy. Malformed individual values are different from whole-file corruption; “fall back to defaults and log” can silently reset progression and is unsafe for counter data. Define per-key validation, an explicit corruption recovery UX, and whether restored Auto Backup data is treated as a fresh install. Add tests for missing keys, malformed numbers/timestamps, unknown schema versions, and backup restore.

8. **Configuration ranges permit impractical timer durations**

   The timing validator allows up to 59:59 for every phase and up to 20 sets. That permits a run of nearly 39 hours, which conflicts with a finite wake-lock timeout, notification usability, and the expected “interval timer” experience. Either cap the total workout duration, reject configurations above a documented maximum, or explicitly support long runs with a different lifecycle strategy. Validate the derived total, not only each field independently.

9. **Foreground-service permission and UX paths need more exact requirements**

   The special-use FGS choice is plausible for a sideloaded app, but Android 14+ requires the manifest permission/property and a user-visible justification, and background-start restrictions still apply. Specify the target/compile SDK used for this behavior, the notification channel importance, behavior when notifications are denied, and the exact handling of `ForegroundServiceStartNotAllowedException` or `SecurityException`. The app should not report a successful start until `startForeground` has succeeded.

10. **The notification is required operationally even when drawer visibility is limited**

    The spec says “only the notification isn't shown” when `POST_NOTIFICATIONS` is denied. On Android 13+, an FGS still has a system-managed task-manager entry and may have limited drawer visibility; this is not equivalent to having no notification. Define the channel and notification behavior for denied permission, and ensure Stop remains available through the supported system surface or an in-app fallback.

### Low priority / clarity

11. **“Latest stable” is not reproducible enough for the build contract**

    Pin the Android Gradle Plugin, Kotlin, Compose BOM, compile/target SDK, and Java/Kotlin toolchain versions in the version catalog or a documented compatibility matrix. “Latest stable” will otherwise make CI and future maintenance nondeterministic.

12. **The source-sheet parity boundary should be made explicit**

    The new rules intentionally differ from the script in several ways: same-day check-ins are blocked, the first check-in does not increment, hold state is explicit, and the script’s history sheet is removed. Add a compact parity table listing each intentional deviation and its rationale. This will prevent later implementation or test changes from “correcting” behavior back toward the script accidentally.

13. **The unresolved hold-after-miss option blocks a complete domain contract**

    The spec correctly flags Option R versus Option K, but the implementation and test plan cannot be completed until one is selected. Decide whether a missed check-in at `holdAt` consumes a hold day, and add examples for a zero-penalty miss, a penalty that lands exactly on `holdAt`, and a miss while `holdCount == holdFor`.

14. **Stop/finish semantics and counter accountability need one explicit policy**

    The check-in occurs before the timer, and stopping is allowed at any point. State whether an interrupted or stopped workout is still considered a completed check-in (the current text implies yes), whether DONE is emitted after a stop, and whether relaunching after a stop can ever resume. Reflect that policy in the notification, home indicator, and `TimerController` state machine tests.

15. **Accessibility and large-font behavior are missing from the UI contract**

    Portrait lock and dense timer rings may make the app unusable with large font scale, TalkBack, or reduced-motion settings. Add minimum touch-target and content-description requirements, a non-color phase indicator, and a layout test or manual acceptance check at large font scale. This is especially important because phase color is currently the primary state cue.