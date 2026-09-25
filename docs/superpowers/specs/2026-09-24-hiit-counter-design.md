# HIIT Counter — Design Spec

**Date:** 2026-09-24
**Status:** Approved (reviewed by user 2026-09-24); revised for second review (`review.md`)
**Repo:** https://github.com/mitenko/hiit_counter
**References (local only, not in repo):** `references/sheet_script.js` (Google Sheets Apps Script being replaced), `references/images.jfif` (timer screen look), `references/unnamed.png` (timing settings screen look)

## 1. Purpose

An Android app that replaces a Google Sheet + Apps Script rep tracker. It combines:

1. A **Tabata-style HIIT timer** (prepare → N × work/rest → cooldown).
2. An **incremental rep counter**: each daily check-in adjusts a running rep total, which is distributed across the timer's work sets. Consistent check-ins increase the total; missed days reduce it.

Single user, single workout, fully offline. No accounts, no sync, no history log.

## 2. Scope

**In scope**
- Home screen showing the rep table (mirrors the sheet) and a Start button.
- Timer screen with dual progress rings and rep count in the centre.
- Foreground service so the timer survives screen lock / backgrounding.
- Sound + vibration cues.
- Settings with sub-screens: Timing, Progression, Current State, Cues.
- Check-in on Start, at most once per local calendar day.

**Out of scope (YAGNI)**
- Presets / multiple named workouts (the "Preset Name" field in `unnamed.png` is not carried over).
- Check-in history, charts, stats export.
- Importing data from the Google Sheet (values are entered manually in Settings → Current State).
- Cloud sync, accounts, widgets, Wear OS.

## 3. Platform & Stack

| Item | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, dark theme app-wide |
| Architecture | MVVM — one ViewModel per screen exposing `StateFlow<UiState>` |
| DI | Hilt |
| Navigation | Navigation Compose |
| Persistence | Jetpack DataStore (Preferences) — no Room (no history) |
| Async | Kotlin Coroutines / Flow |
| minSdk / compile & targetSdk | 26 (Android 8.0) / pinned in the implementation plan to the current stable SDK installed locally |
| Toolchain | AGP, Kotlin, Compose BOM, Hilt, JDK — exact versions pinned in `gradle/libs.versions.toml` (chosen at plan time against current stable releases; never "latest") |
| Package | `com.mitenko.hiitcounter` |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`), single `:app` module |
| Orientation | Portrait-locked |
| CI | GitHub Actions: unit tests + lint on push/PR |

## 4. Architecture

```
app/src/main/kotlin/com/mitenko/hiitcounter/
├─ domain/                 pure Kotlin, no Android imports
│  ├─ model/               CounterState, ProgressionConfig, TimingConfig, CueConfig, TimerState, Phase
│  ├─ RepProgression.kt    check-in rules (§6)
│  ├─ RepDistributor.kt    total → per-set counts (§7)
│  ├─ TabataEngine.kt      Flow<TimerState> timer (§8)
│  ├─ Clock.kt             interface { now(): Instant; zone(): ZoneId; elapsedRealtimeMs(): Long } — wall + monotonic time, injected for testability
│  └─ TimerController.kt   Hilt @Singleton owning the running TabataEngine; exposes StateFlow<TimerState?> (null = idle)
├─ data/
│  ├─ SettingsRepository.kt  DataStore: TimingConfig, ProgressionConfig, CueConfig
│  └─ CounterRepository.kt   DataStore: CounterState
├─ service/
│  ├─ TimerService.kt      foreground service: keeps process + CPU alive while TimerController runs; notification
│  └─ CuePlayer.kt         SoundPool + Vibrator/VibratorManager wrapper
└─ ui/
   ├─ home/                HomeScreen, HomeViewModel
   ├─ timer/               TimerScreen, TimerViewModel, DualRing composable
   ├─ settings/            SettingsListScreen + Timing/Progression/CurrentState/Cues screens & ViewModels
   └─ theme/
```

**Unit boundaries**
- `domain/` has zero Android dependencies and is the bulk of the logic; everything in it is unit-tested directly.
- Repositories are the only code touching DataStore. ViewModels depend on repository interfaces so tests use in-memory fakes.
- `TimerController` (singleton) owns the engine and its state. `TimerService` is a thin host: it holds the wake lock, plays `TimerController.cues` (one-shot events, §8) through `CuePlayer`, and updates the notification from `TimerController.state`. No business logic. ViewModels never bind to the service — they collect `TimerController.state`.

### Data flow — pressing Start
1. `HomeViewModel.onStart()` is debounced (Start disabled until the flow completes).
2. `TimerController.prepare(snapshot)` stores the snapshot (TimingConfig + CueConfig; per-set counts filled in at step 5). The controller is now `Preparing` — not ticking.
3. `TimerService` is started (`ContextCompat.startForegroundService`) and calls `ServiceCompat.startForeground` in `onStartCommand`. It reports success or failure to `TimerController`.
4. **Only after `startForeground` succeeds:** `CounterRepository.checkIn(config, clock)` runs `RepProgression.checkIn` **inside a single `DataStore.updateData {}` transaction** — two rapid taps cannot both check in.
5. `RepDistributor.distribute(total, sets)` fills the per-set counts; `TimerController.start()` begins ticking (PREPARE phase) and the app navigates to the timer screen.
6. On Android 13+, if `POST_NOTIFICATIONS` has never been asked, it is requested once the timer screen is shown. The dialog result has no effect on the running workout.

**Start failure:** if the service can't start or `startForeground` throws (`ForegroundServiceStartNotAllowedException`, `SecurityException`, …), the controller returns to idle, **no check-in is recorded**, and Home shows an error snackbar with the reason. The window between `startForeground` succeeding and the check-in committing is a single in-process call; no pending-run state is persisted.

**Active-run routing:** whenever the app opens (launcher, notification tap, recreated activity) and `TimerController.state` is non-null and not DONE, navigation goes straight to the timer screen. The notification uses a `singleTop` activity intent.

Settings changes made during a workout do **not** affect the running workout (it uses the snapshot).

## 5. Data Model

```kotlin
data class TimingConfig(
    val prepareSec: Int = 10,
    val sets: Int = 8,
    val workSec: Int = 20,
    val restSec: Int = 10,
    val cooldownSec: Int = 0,
)

data class ProgressionConfig(
    val startingTotal: Int = 48,
    val floor: Int = 48,              // sheet: MIN_COUNT
    val cap: Int = 72,                // sheet: MAX_COUNT
    val holdAt: Int = 64,             // sheet: HOLD_COUNT
    val holdFor: Int = 4,             // check-ins performed at holdAt before advancing (sheet: HOLD_DAYS)
    val windowHours: Int = 36,        // sheet: CHECK_IN_LIMIT
    val penaltyHoursPerRep: Double = 19.5, // sheet: HOURS_PER_DECREASE
)

data class CueConfig(
    val sound: Boolean = true,
    val vibration: Boolean = true,
)

data class CounterState(
    val total: Int,                   // initialised to ProgressionConfig.startingTotal
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val lastCheckIn: Instant? = null,
    val holdCount: Int = 0,
)
```

**Fresh install:** no setup prompt. The `total` key is absent until the first write; while absent, `total` reads as the **live** `ProgressionConfig.startingTotal`, so editing Starting total before the first check-in takes effect. State is otherwise `streaks = 0, lastCheckIn = null, holdCount = 0`.

**Reset progress** (Settings → Current State): returns the counter to the fresh-install state (total key removed ⇒ `startingTotal`, streaks 0, `lastCheckIn = null`, `holdCount = 0`). Progression "Reset to defaults" resets only config, never counter state.

**Persistence (DataStore Preferences).** Two files: `settings.preferences_pb`, `counter.preferences_pb`.

| Key | Type | Encoding | Absent ⇒ |
|---|---|---|---|
| `prepare_sec`, `sets`, `work_sec`, `rest_sec`, `cooldown_sec` | Int | int | default |
| `starting_total`, `floor`, `cap`, `hold_at`, `hold_for`, `window_hours` | Int | int | default |
| `penalty_hours_per_rep` | Double | double | default |
| `cue_sound`, `cue_vibration` | Boolean | boolean | default |
| `notification_permission_asked` | Boolean | boolean | false |
| `total` | Int | int | live `starting_total` |
| `best_streak`, `current_streak`, `hold_count` | Int | int | 0 |
| `last_check_in` | Instant? | epoch millis (Long) | null |

Per-key robustness: a value that fails its §10 validation on read (e.g. negative `sets`) is replaced by its default and logged; other keys are unaffected. Whole-file corruption uses DataStore's `ReplaceFileCorruptionHandler` → defaults, logged. No schema version or migrations in v1 (single schema; add a `schema_version` key when the first migration is needed). Auto Backup restores are treated as ordinary data.

## 6. Progression Rules — `RepProgression.checkIn(state, config, now, zone): CheckInResult`

```kotlin
data class CheckInResult(val state: CounterState, val outcome: Outcome)
sealed interface Outcome {
    data object AlreadyToday : Outcome
    data object First : Outcome
    data object OnTime : Outcome
    data class Missed(val penalty: Int) : Outcome
}
```

Rounding everywhere uses **round-half-up** (`floor(x + 0.5)`), matching JavaScript `Math.round` in the sheet. `hours = roundHalfUp(millisBetween(lastCheckIn, now) / 3_600_000.0)`.

Evaluated in order; first match wins:

1. **Already checked in today** — `lastCheckIn != null` and `lastCheckIn` and `now` fall on the same local date in `zone` → state unchanged, `Outcome.AlreadyToday`. The workout still runs with the current total.

For rules 2–4, first **clamp** `total = total.coerceIn(floor, cap)` (handles config changes that left the total out of range). Rules 2–4 all set `lastCheckIn = now`.

2. **First ever check-in** — `lastCheckIn == null` → `currentStreak = 1`, `bestStreak = max(bestStreak, 1)`, total unchanged (you perform the starting total on day 1). If hold is enabled and `total == holdAt`, `holdCount = 1`. *(Deliberate deviation: the sheet's empty-date path would add 1.)*
3. **Missed** — `hours > windowHours`:
   - `penalty = max(0, roundHalfUp((hours − 24) / penaltyHoursPerRep) − 1)`
   - `total = max(floor, total − penalty)`
   - `currentStreak = 1`, `bestStreak = max(bestStreak, 1)`
   - Hold: see **Hold after a miss** below.
4. **On time** — otherwise (including `now < lastCheckIn`, i.e. clock moved backwards → treated as on time, warning logged):
   - `currentStreak += 1`, `bestStreak = max(bestStreak, currentStreak)`
   - **4a. Hold:** if hold is enabled and `total == holdAt`:
     - if `holdCount >= holdFor` → `total += 1`, `holdCount = 0`
     - else → `holdCount += 1`, total unchanged
   - **4b. Normal:** else if `total < cap` → `total += 1`. If hold is enabled and the new total `== holdAt`, `holdCount = 1` (today's workout is the first at the hold value).
   - Total is never raised above `cap`.

`holdCount` is only ever set non-zero when hold is enabled.

**Hold enabled** ⇔ `holdFor > 0 && floor <= holdAt < cap`. Any other combination is valid and simply disables the hold.

**Hold semantics (holdAt = 64, holdFor = 4):** the hold value is performed on exactly 4 check-ins, then the total resumes climbing to `cap`.

| Check-in | Total performed | holdCount after |
|---|---|---|
| A (63 → 64) | 64 | 1 |
| B | 64 | 2 |
| C | 64 | 3 |
| D | 64 | 4 |
| next | 65 | 0 |

**Hold after a miss:** if hold is enabled and the post-penalty total `== holdAt`, `holdCount = 1` — the hold restarts, this check-in counting as the first. This includes a 0-rep miss while mid-hold (e.g. 64 with holdCount 3 → miss → 64 with holdCount 1). A miss that leaves the total ≠ `holdAt` sets `holdCount = 0`.

| Before miss | Penalty | After |
|---|---|---|
| 64, holdCount 3 | 0 | 64, holdCount 1 (restart) |
| 64, holdCount 4 (would advance next) | 0 | 64, holdCount 1 (restart) |
| 66, holdCount 0 | 2 | 64, holdCount 1 (landed on hold) |
| 65, holdCount 0 | 2 | 63, holdCount 0 |
| 64, holdCount 2 | 1 | 63, holdCount 0 (hold starts again when 64 is next reached) |

**Penalty reference values** (from the sheet formula, for tests):

| Hours since last check-in | Penalty |
|---|---|
| ≤ 36 (incl. 36.49 h) | not missed (on time) |
| 37 (from 36.5 h) | 0 |
| 48 | 0 |
| 53 | 0 |
| 54 | 1 |
| 60 | 1 |
| 72 | 1 |
| 73 | 2 |
| 84 | 2 |

A single missed day therefore costs 0 reps but resets the streak — this is intentional, preserved from the sheet.

**Sheet bugs fixed:** multiple check-ins per day now impossible (rule 1); stale streak logging is moot (no history).

**Editing Current State in Settings** overwrites `total`, `bestStreak`, `currentStreak`, `lastCheckIn` as entered and resets `holdCount = 0`.
**Saving Progression settings** also resets `holdCount = 0` (hold-at / hold-for may have changed).

**Accepted edge cases (documented, not guarded):** setting the phone clock back across midnight, or travelling across time zones, can allow an extra check-in within 24 h. Single-user personal app; not worth a minimum-gap rule.
- A Progression save racing a check-in may apply the old config to the check-in. Settings and Start live on different screens, so this is accepted rather than serialized.

**Intentional deviations from `sheet_script.js`** (do not "fix" these back):

| Sheet behaviour | App behaviour | Why |
|---|---|---|
| Any edit to A9 checks in; repeatable | Once per local calendar day | Sheet bug: repeated edits inflated reps/streak |
| Empty last-check-in (NaN) → on-time branch, +1 | First check-in: total unchanged | You perform the starting total on day 1 |
| Hold: at ≥ 64, +1 only if streak > 4 (no hold during a long streak) | Literal hold: `holdFor` check-ins at `holdAt`, restarts on a miss landing on `holdAt` | User decision |
| Stats sheet logs stale streak after a miss | No history | Out of scope; bug moot |
| `MIN_COUNT` is also the start value | `startingTotal` separate from `floor` | User decision |
| Timestamps formatted as "PST" strings, re-parsed in sheet TZ | `Instant` + device zone | Removes timezone drift |
| Total may be > max after edits (only +1 is capped) | Clamped to `[floor, cap]` at each check-in | Consistency |

## 7. Rep Distribution — `RepDistributor.distribute(total, sets)`

`q = total / sets`, `r = total % sets`; the first `r` sets get `q + 1`, the rest get `q`. Example: 65 over 8 → `[9, 8, 8, 8, 8, 8, 8, 8]`. Sum always equals `total`.

## 8. Timer — `TabataEngine`

**Phase sequence:** `PREPARE(prepareSec)` → for set 1..N: `WORK(workSec)`, then `REST(restSec)` except after the final set → `COOLDOWN(cooldownSec)` → `DONE`. Phases with duration 0 are skipped.

Total duration = `prepare + sets × work + (sets − 1) × rest + cooldown` (defaults: 4:00).

**TimerController.state** (`StateFlow<TimerState?>`, null = idle) — conflated, replayable, for UI and notification:
```kotlin
data class TimerState(
    val phase: Phase,              // PREPARE, WORK, REST, COOLDOWN, DONE
    val set: Int,                  // 1-based current (or upcoming, during PREPARE/REST) work set
    val sets: Int,
    val phaseSecondsLeft: Int,
    val phaseDurationSec: Int,
    val elapsedSec: Int,           // active (unpaused) time only
    val repsThisSet: Int,          // reps for `set`
    val paused: Boolean,
)
```

**Emission semantics** (phase of duration `d` seconds, entered at monotonic time `t0`):
- On entry: emit `phaseSecondsLeft = d`.
- At `t0 + k·1000 ms` for `k = 1 … d−1`: emit `phaseSecondsLeft = d − k`.
- At `t0 + d·1000 ms`: **no `0` emission** — the next phase is entered and emits its own `d'`. The UI therefore shows `d … 1` and never flashes `00:00`. After the last phase, `DONE` is emitted with `phaseSecondsLeft = 0`.
- Zero-duration phases are skipped entirely (no emission, no cue).
- `elapsedSec` = whole seconds of active time since PREPARE entry; it increments on the same ticks.
- Pause captures the remaining milliseconds of the current second; resume schedules the next tick after exactly that remainder (so pause/resume at or near a boundary neither loses nor gains time).

**TimerController.cues** (`SharedFlow<Cue>`, `replay = 0`) — one-shot events, emitted by the engine exactly once each; recollecting never replays them:
- `Countdown(n)` when a tick sets `phaseSecondsLeft` to 3, 2 or 1 (ticks only — not on phase entry, which emits `PhaseStart` instead).
- `PhaseStart(phase)` on entry to WORK / REST / COOLDOWN.
- `Finished` on DONE.
`TimerService` is the single collector. If a sound isn't loaded yet (SoundPool loads on service create) or audio focus is denied, that cue's sound is skipped (vibration still fires) and logged — no retry.

**Stop semantics:** the check-in made at Start **stands** whether the workout finishes or is stopped. `stop()` returns the controller straight to idle (no DONE emitted, no Finished cue); a stopped workout cannot be resumed — Start begins a new one (and, same day, is `AlreadyToday`).

**Command idempotency:** `start` is refused unless the service has reported `Started`; `prepare/start` while not idle is ignored; `stop` while idle is a no-op; `pause` while paused and `resume` while running are no-ops. Notification actions and UI buttons call the same controller methods.

**Controls:** `pause()`, `resume()`, `stop()`. Pause freezes the countdown and elapsed time. Time is tracked from `Clock.elapsedRealtimeMs()` (monotonic; faked in tests) and each tick is scheduled against its absolute target time, not by summing `delay(1000)`s, to avoid drift.

**Cue rendering** (via `CuePlayer`, each gated by `CueConfig`):
- Short beep at 3, 2, 1 seconds left in any phase.
- Long tone + vibration pulse at the start of each WORK phase.
- Double tone + vibration at the start of REST / COOLDOWN, triple at DONE.
- Audio: `SoundPool` with `AudioAttributes(USAGE_ASSISTANCE_SONIFICATION)`, requesting `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` around each cue and abandoning it afterwards, so music keeps playing (ducked).
- Vibration: `VibratorManager` on API 31+, `Vibrator` below.

## 9. Screens

### 9.1 Home (mirrors the sheet)
A table:

| Rows | Content |
|---|---|
| 1..N | per-set rep counts from `RepDistributor` |
| Total Reps | `total` |
| Last Check In | `lastCheckIn` formatted `d MMM yyyy, HH:mm` local, or "—" |
| Best CI Streak | `bestStreak` (bold) |
| Curr CI Streak | `currentStreak` |
| Today | local date `d MMM yyyy` |

- Before today's check-in the table shows the **current stored** total. Pressing **Start** performs the check-in, updates the table, then opens the timer.
- If already checked in today, a small "Checked in today" indicator is shown; Start still runs the workout.
- Settings gear in the top bar.

### 9.2 Timer (look: `references/images.jfif`)
- Dark background.
- Top: **Sets `2/8`** and **Elapsed `00:00:50`**.
- **Inner ring (thick):** progress of the current phase, sweeping down as it counts. Colour by phase: WORK = green, REST = amber, PREPARE/COOLDOWN = grey-blue.
- **Outer ring (thin, blue):** work-set progress, fraction = `(completedWorkSets + currentWorkFraction) / N`, where `currentWorkFraction` is elapsed/duration of the current WORK phase (0 outside WORK). Fills smoothly during work, holds still during rest.
- **Centre:**
  - WORK: large rep count (e.g. **8**) in the phase colour; countdown `00:15` below.
  - REST / PREPARE: small label "REST" / "GET READY", the **upcoming** set's rep count shown large but dimmed, countdown below.
  - COOLDOWN: label "COOLDOWN", countdown.
  - DONE: "DONE", total reps performed.
- Bottom: pause/resume button. Stop via top-left ✕ with a confirmation dialog while running; on DONE, ✕ / back return home without confirmation.
- `FLAG_KEEP_SCREEN_ON` while the timer screen is visible.
- System back while running = same as ✕ (confirm stop).
- Phase is never conveyed by colour alone: WORK shows the large bright rep count with no label; REST / GET READY / COOLDOWN / DONE show a text label and a dimmed number. (No "WORK" label — user decision.)

**Accessibility (all screens):** touch targets ≥ 48 dp; content descriptions on icon buttons and on the timer centre (e.g. "Work, set 2 of 8, 8 reps, 15 seconds left"), updated once per phase for TalkBack rather than every second; text sized in `sp` and every screen checked at 200 % font scale. Exception: the timer's centre rep number is sized relative to the ring (it is already the largest element), so it doesn't overflow at large font scales.

### 9.3 Settings
List screen → sub-screens:

- **Timing** (look: `references/unnamed.png`): PREPARE, SETS, WORK, REST, COOLDOWN, each with −/+ steppers (times in `mm:ss`, steps of 5s; long-press repeats); **TOTAL mm:ss** footer bar (turns red and disables Save above 2:00:00).
- **Progression:** Starting total, Floor, Cap, Hold at, Hold for (check-ins), Check-in window (hours), Penalty rate (hours per rep). "Reset to defaults" action.
- **Current State:** Current total, Best streak, Current streak, Last check-in (date + time pickers, clearable). Save applies §6 "Editing Current State". "Reset progress" action with confirmation (§5).
- **Cues:** Sound on/off, Vibration on/off.

## 10. Validation & Error Handling

**Settings validation** (invalid fields show inline errors; Save disabled until valid):
- Timing: `1 ≤ sets ≤ 20`, `workSec ≥ 1`, `prepareSec, restSec, cooldownSec ≥ 0`, each ≤ 59:59, **and derived total duration ≤ 2:00:00**.
- Progression: `1 ≤ floor ≤ startingTotal ≤ cap`; `holdAt ≥ 1`; `holdFor ≥ 0`; `windowHours ≥ 1`; `penaltyHoursPerRep > 0`. A `holdAt` outside `[floor, cap)` or `holdFor = 0` is allowed and shows a "hold disabled" hint.
- Current State: `total ≥ 1` (a warning, not an error, if outside `[floor, cap]` — clamped at the next check-in), streaks `≥ 0`, `bestStreak ≥ currentStreak`, `lastCheckIn` not in the future.

**Runtime**
- Clock moved backwards (`now < lastCheckIn`): treated as on time (§6 rule 4), warning logged.
- Config changed so `total` is outside `[floor, cap]`: displayed as-is; the next check-in (rules 2–4) clamps it first.
- **Foreground service:** `foregroundServiceType="specialUse"` with manifest `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="workout interval timer"/>`. Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK`, `VIBRATE`, `POST_NOTIFICATIONS`. Start via `ServiceCompat.startForeground`, passing the specialUse type on API 34+ and 0 below. Rejected: `shortService` (≈3 min limit < default 4:00), `health` (Android 14 requires sensor / activity-recognition permissions the app doesn't need), `mediaPlayback` (not media). Sideloaded / personal; if ever published to Play, the special-use justification must be declared.
- **Wake lock:** tag `hiitcounter:workout`. Acquired when ticking starts or resumes, with timeout = remaining workout time + 1 min; released on pause, stop, and after the DONE grace period (so the Finished cue plays with the screen off), and in a `finally` if service setup fails.
- **Max pause:** if paused continuously for 30 minutes, the controller stops the workout (§8 stop semantics) and the service stops. The 30 minutes is measured in awake time — the Main-dispatcher delay does not advance in deep sleep. A paused workout holds no wake lock, so there is no battery cost. Real-time enforcement is deferred.
- **Lifecycle:** `onStartCommand` returns `START_NOT_STICKY` (a killed service is not recreated with a stale run). The service calls `stopSelf()` when the controller reaches DONE or is stopped. `onTaskRemoved` (app swiped away): the workout keeps running; the notification is the way back in.
- **Notification:** channel `workout`, importance LOW (silent — cues are the app's own sounds); ongoing; shows phase, set and time left; Stop action; tap opens the app (`singleTop`), which routes to the timer screen (§4 active-run routing).
- **POST_NOTIFICATIONS denied (13+):** the foreground service still runs and still appears in the system's active-apps (task manager) list, but not in the drawer. The in-app timer screen's ✕ is the always-available stop path.
- **Activity destroyed mid-workout:** service and `TimerController` keep running; reopening routes to the timer screen.
- **Process killed mid-workout:** the workout ends silently; next launch opens Home as normal (no "interrupted" message). The check-in already happened at Start, so no counter data is lost.
- DataStore read failures: per-key and whole-file handling in §5 Persistence.
- Android Auto Backup enabled so DataStore survives reinstall/device migration.

## 11. Testing

- **Unit (JUnit + kotlinx-coroutines-test):**
  - `RepProgression`: every rule branch and `Outcome`; `lastCheckIn` set by rules 2–4; full penalty table (§6) incl. hour-rounding boundary 36.49 h / 36.5 h and thresholds 53/54, 72/73; hold sequence A–D → 65; `holdFor = 1`; hold restart after a 0-rep miss mid-hold and after a penalty landing on holdAt; hold disabled (`holdFor = 0`, `holdAt ≥ cap`, `holdAt < floor`); `holdAt == startingTotal` on first check-in; `holdAt == floor` after a miss; `cap == floor`; total above cap / below floor clamped; same-day no-op across midnight and across a DST change in a fixed zone; clock-backwards.
  - Hold-after-miss examples table (§6).
  - `CounterRepository`: concurrent `checkIn` calls yield exactly one check-in; per-key fallback for missing / invalid values; `last_check_in` epoch-millis round-trip; Current State save and Progression save reset `holdCount`; absent `total` key reads as live `startingTotal`; Reset progress.
  - `RepDistributor`: 65/8 → `[9,8,8,8,8,8,8,8]`; exact multiples; `sets = 1`; total < sets.
  - `TabataEngine` (table-driven, `TestScope` virtual time + fake monotonic clock): exact emission sequence per §8 (`d … 1`, no `0`, DONE); a 1-second phase; adjacent zero-duration phases; no rest after final set; `elapsedSec`; pause/resume mid-second and at a boundary; no drift over a full default run (4:00); each transition emits exactly one cue; recollecting `cues` replays nothing.
  - `TimerController`: command idempotency; stop → idle without DONE/Finished; max-pause auto-stop at 30 min; start failure → idle and no check-in.
- **ViewModel:** `HomeViewModel` start flow — check-in committed only after service-start success; start failure shows error and leaves state untouched; debounce. Settings validation incl. 2:00:00 total cap. Fake repositories, fake `Clock`, fake service starter.
- **Compose UI:** home table rendering; timer centre switching between reps (WORK) and REST + dimmed next reps.
- **Manual on device:** cues and vibration with the screen off; music ducking; background operation; notification Stop and tap-to-return; swipe app away mid-workout then reopen; POST_NOTIFICATIONS denied path; pause > wake-lock timeout then resume with the screen off; TalkBack pass and 200 % font scale on home, timer and settings.

## 12. Project Conventions

- `claude.md` at repo root holds working instructions for Claude and is refined as development proceeds.
- The initial commit (spec, references, `claude.md`) goes directly on `main` to create the default branch; all subsequent work uses feature branches + PRs to `main`. Never merge locally into a shared branch.
- `references/` is local-only (gitignored); not committed to the repo.

## 13. Review Resolutions (2026-09-24)

Full findings: `docs/superpowers/specs/review.md`. Resolution of each:

| # | Finding | Resolution | Where |
|---|---|---|---|
| 1 | Check-in orphaned from workout start | Check-in commits only after `startForeground` succeeds; start failure → error, no check-in. No persisted pending snapshot (window is one in-process call). | §4 |
| 2 | Timer ownership across process death | `START_NOT_STICKY`; killed run ends silently, check-in stands; idempotent commands. No "interrupted" toast (nothing is lost). | §8, §10 |
| 3 | Wake lock vs. long pause | Released on pause, re-acquired on resume (timeout = remaining + 1 min); 30-min max pause auto-stops; tag + `finally` release. | §10 |
| 4 | Boundary semantics | Exact emission contract: `d … 1`, no `0`, next phase on the boundary; pause keeps sub-second remainder. | §8 |
| 5 | Cue event contract | Separate `SharedFlow<Cue>` (replay 0), single collector; missing sound/focus → skip sound, still vibrate. Sequence IDs not needed. | §8 |
| 6 | Settings vs. check-in race | Accepted and documented (separate screens, single user). | §6 |
| 7 | DataStore schema/corruption | Key/encoding table; per-key fallback; whole-file `ReplaceFileCorruptionHandler`. Schema versioning deferred until first migration. | §5 |
| 8 | Impractical durations | Derived total ≤ 2:00:00 enforced in validation and the TOTAL footer. | §9.3, §10 |
| 9 | FGS permission/UX | Permissions, property, `ServiceCompat`, LOW channel, failure handling; success only after `startForeground`. | §4, §10 |
| 10 | Notification when denied | FGS still in task manager; in-app ✕ is the always-available stop. | §10 |
| 11 | "Latest stable" not reproducible | Versions pinned in `libs.versions.toml` at plan time against current stable (review's example pins are outdated and not used). | §3 |
| 12 | Sheet parity boundary | Intentional-deviations table. | §6 |
| 13 | Hold-after-miss unresolved | Option R chosen by user; examples table added. | §6 |
| 14 | Stop/finish semantics | Check-in stands on stop; stop → idle, no DONE; no resume. | §8 |
| 15 | Accessibility | 48 dp targets, content descriptions, 200 % font check, non-colour phase cue (without a "WORK" label, per user decision). | §9.2 |
