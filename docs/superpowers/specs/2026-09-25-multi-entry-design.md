# HIIT Counter — Multi-Entry Design (spec revision 2)

**Date:** 2026-09-25
**Status:** Approved by the user (2026-09-25). The findings from both reviews are incorporated; see §11.
**Amends:** `docs/superpowers/specs/2026-09-24-hiit-counter-design.md` (v1). Everything in v1 still holds unless this document replaces it. References such as "v1 §6" point into the v1 spec.

## 1. Purpose

This revision turns the single-workout app into a list of independent **HIIT entries**, such as "Kettlebell Lunges" or "Burpees". Each entry has its own name, timing, progression rules, cue settings and rep counter (total, streaks, last check-in and hold). A check-in on one entry never affects another.

It also brings every settings screen onto one **stepper row** pattern with a **tap-to-edit dialog**.

## 2. Scope

**In scope**
- An entry list as the new home screen. Tapping an entry opens its sheet-style table, which is the v1 home screen, per entry.
- Create (name prompt plus defaults), rename, duplicate, delete (with confirmation) and reorder.
- Per-entry settings: Timing, Progression, Current State and Cues.
- Room persistence in place of the v1 DataStore settings and counter files, with a one-time migration of v1 data into a first entry.
- The shared stepper-row and edit-dialog pattern on Timing, Progression and Current State.

**Out of scope (YAGNI)**
- Running two workouts at once. There is still one workout app-wide.
- History, charts, export/import, sync and sharing entries.
- Entry icons or colours, folders, tags, search, and drag-and-drop reordering.
- App-wide settings. Every setting belongs to an entry; the only global value is the notification-permission-asked flag.

## 3. Delta from v1

| Area | v1 | Revision 2 |
|---|---|---|
| Domain rules (`RepProgression`, `RepDistributor`, `TabataEngine`, `TimerController` core, `SettingsValidator`) | as specified | **unchanged**. They are now applied per entry. |
| Check-in semantics (window, penalty, clamp, hold, once per local day) | app-wide | **identical**, scoped to one entry |
| Timing, progression, cues, counter | one set, app-wide | one set **per entry** |
| Persistence | DataStore `settings` and `counter` files | Room `hiit.db`, plus DataStore `app.preferences_pb` for the global flag |
| Home screen | the table | the **entry list**; the table moves to the entry screen |
| Settings | the app-wide list | per-entry list, plus Rename, Duplicate and Delete |
| Settings controls | steppers on Timing only; text fields elsewhere | the **stepper row plus edit dialog** everywhere except Cues |
| Timer, notification, service lifecycle, active-run routing | as specified | unchanged, apart from the entry name in the snapshot, the UI and the notification |
| Existing data | — | migrated into an entry named **"Workout"** |

## 4. Stack changes (amends v1 §3)

| Item | Change |
|---|---|
| Persistence | **Room 2.8.1**: `room-runtime`, `room-ktx` and `room-compiler` (via KSP), plus `room-testing` for tests. This is the version already building in `D:\Claude\apps\storyteller` with the same Kotlin and KSP. |
| Room Gradle plugin | The `androidx.room` plugin 2.8.1 goes in the catalog and is applied in `:app` with `room { schemaDirectory("$projectDir/schemas") }`. `app/schemas/` is committed and added to the unit-test assets, so `MigrationTestHelper` works under Robolectric for future versions. |
| DataStore | Kept only for `app.preferences_pb` (§5.4). The v1 `@SettingsStore` and `@CounterStore` Hilt providers are **removed**, so `app.preferences_pb` is the only DataStore Hilt provides. |
| Backup | `allowBackup="true"` stays. Android Auto Backup includes `databases/` and `datastore/` by default. Restored data is treated as ordinary data; restore is not a migration trigger unless v1 files are present. |

## 5. Data model

### 5.1 Domain

```kotlin
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

**Invariant:** in memory, `Entry.counter.total` is always a real value. A stored `NULL` is resolved to `progression.startingTotal` when the row is mapped to the domain model, so the UI never sees a null. `TimingConfig`, `ProgressionConfig`, `CueConfig`, `CounterState` and all the domain rules are unchanged. The new pure helpers are `EntryNames` (§5.5) and `ValueFormat` (§8.2).

### 5.2 Room schema (`hiit.db`, version 1)

The `entry` table has one row per entry:

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `name` | TEXT NOT NULL | stored trimmed and valid per §5.5; duplicates allowed |
| `position` | INTEGER NOT NULL, **indexed (non-unique)** | 0-based, contiguous, the primary ordering key; the index is not unique so in-transaction swaps can't hit a constraint conflict |
| `prepare_sec`, `sets`, `work_sec`, `rest_sec`, `cooldown_sec` | INTEGER NOT NULL | timing |
| `starting_total`, `floor`, `cap`, `hold_at`, `hold_for`, `window_hours` | INTEGER NOT NULL | progression |
| `penalty_hours_per_rep` | REAL NOT NULL | progression |
| `cue_sound`, `cue_vibration` | INTEGER NOT NULL | booleans |
| `total` | INTEGER NULL | NULL means it reads as `starting_total` (v1 §5 semantics) |
| `best_streak`, `current_streak`, `hold_count` | INTEGER NOT NULL | counter |
| `last_check_in` | INTEGER NULL | epoch millis |

The `meta` table (`key TEXT PK`, `value TEXT`) holds `v1_migrated = "true"` once the migration has run (§6).

**Integrity:**
- **On write:** the repository validates the name with `EntryNames`, timing and progression with `SettingsValidator`, and the counter with `SettingsValidator.currentState`. Invalid input throws `IllegalArgumentException` and nothing is written. Room entity annotations can't express SQL CHECK constraints, so this is the enforcement point.
- **On read (safety net for corrupt rows):** each value is checked **on its own**. An invalid value is replaced by its default and logged, so one bad field never poisons the rest of the row. If a timing or progression group is still inconsistent after that per-field repair, only that group falls back to its defaults. An invalid `total` (< 1) reads as NULL.
- **Position contiguity** is maintained by the repository (§5.3) and verified by tests. It is not a database constraint.

### 5.3 Repository

```kotlin
interface EntryRepository {
    val entries: Flow<List<Entry>>           // ORDER BY position, id
    fun entry(id: Long): Flow<Entry?>        // null once loaded ⇒ does not exist
    suspend fun create(name: String): Long   // defaults, appended at the end
    suspend fun rename(id: Long, name: String)
    suspend fun duplicate(id: Long): Long    // §5.5 name, config copied, fresh counter, appended
    suspend fun delete(id: Long)             // compacts positions
    suspend fun moveBy(id: Long, delta: Int) // clamped to list bounds
    suspend fun setTiming(id: Long, timing: TimingConfig)
    suspend fun setProgression(id: Long, progression: ProgressionConfig) // same UPDATE sets hold_count = 0
    suspend fun setCues(id: Long, cues: CueConfig)
    suspend fun checkIn(id: Long, clock: Clock): CheckInResult
    suspend fun overwriteCounter(id: Long, total: Int, bestStreak: Int, currentStreak: Int, lastCheckIn: Instant?) // same UPDATE sets hold_count = 0
    suspend fun resetProgress(id: Long)      // total NULL, streaks 0, lastCheckIn NULL, holdCount 0
}
```

**General rules**
- **Every method and both flows first await migration readiness** (§6), so nothing touches the table before the v1 import has run.
- Missing ids throw `EntryNotFound`. This applies to `rename`, `duplicate`, `delete`, `moveBy`, `set*`, `checkIn`, `overwriteCounter` and `resetProgress`.

**Check-in and settings writes**
- `checkIn(id, clock)` runs as `db.withTransaction { read row → resolve NULL total → RepProgression.checkIn(state, row's own progression, now, zone) → write if not AlreadyToday }`.
  - The row's progression is the single source of truth; the caller doesn't supply a config.
  - Concurrent calls on one entry record exactly one check-in, and different entries stay independent.
- `setProgression` and `overwriteCounter` are each a **single-row UPDATE**, so the holdCount reset is atomic with the change.
- **Check-in versus a concurrent settings save on the same entry:** SQLite serializes writes, so the check-in transaction and the settings UPDATE run in one order or the other, never interleaved.
  - If the settings save runs first, the check-in uses the new rules.
  - If the check-in runs first, the save still applies, and `setProgression`'s holdCount reset applies after the check-in.
  - Both orders are valid and neither corrupts the row. Tests cover both orders.

**Ordering operations**
- `create`, `duplicate`, `delete` and `moveBy` each run in one transaction and keep positions contiguous. The contract:
  - `create` and `duplicate` append at position `count`.
  - `delete` shifts every later row down by 1.
  - `moveBy(id, delta)` computes the target as `(pos + delta).coerceIn(0, count - 1)` inside the transaction. It's a no-op when the target equals the current position. Otherwise it shifts the rows between the two positions by ∓1 and places the row at the target.
  - Because this is computed inside the transaction, rapid repeated taps never act on a stale list.

**Threading**
- Room's suspend functions return to the caller's dispatcher. ViewModels call the repository from `viewModelScope` (Main) and keep calling `TimerController` on Main. There must be no `withContext(Dispatchers.IO)` around controller calls.

The v1 `SettingsRepository` and `CounterRepository` are removed.

### 5.4 `AppPreferences`

DataStore file `app.preferences_pb`, with one key: `notification_permission_asked` (Boolean, absent ⇒ false).

- **Contract:** the flag is **app-level and sticky**. It is set once the Android 13+ prompt has been shown, whatever the answer, and it never resets. That includes when the user later changes the permission in system settings.
- **Lifetime:** it survives reinstall only if the app data is restored by Auto Backup.
- **Migration:** the migration writes it only if a v1 settings file exists, and never deletes it.

### 5.5 Names

- `EntryNames.validate(raw)`: the name is trimmed and must be 1–40 characters, counted with `String.length` (UTF-16 units).
- **Duplicate names are allowed.** Rows are keyed by id, and names are never auto-suffixed on create.
- A duplicate's name is `base.take(40 - " copy".length) + " copy"`, so the suffix always survives.

## 6. Migration from v1

A `@Singleton V1Migrator` exposes `ready: Deferred<Unit>`. It is started from `HiitApp.onCreate` on `Dispatchers.IO`, never with `runBlocking`, and every `EntryRepository` entry point awaits it (§5.3). The steps:

1. **Gate.** If `meta.v1_migrated` exists, go to step 4.
2. **Read the v1 data.** This happens only if `settings.preferences_pb` or `counter.preferences_pb` exists.
   - The files are read through `PreferenceDataStoreFactory.create` instances on a **migration-only scope**, with **no** corruption handler (a replace handler would rewrite the v1 file before the import commits). A `CorruptionException` on read is caught and that file's values are treated as defaults or a fresh counter; the corrupted file stays byte-for-byte untouched until step 5. Any other I/O error aborts the migration without writing the marker, so it retries on the next launch. No Hilt DataStore exists for these files any more, so there's no "multiple DataStores" conflict.
   - Timing, progression and cues use the v1 readers, with the per-key fallback. A missing or corrupted settings file yields the defaults.
   - **`total` is read raw** from the `total` key. If it is absent or invalid (< 1), it becomes NULL.
   - Streaks, hold count and last check-in follow the v1 reader rules. A missing or corrupted counter file yields a fresh counter.
   - The migration scope is then cancelled and joined, so both DataStores are closed.
3. **Write, in one Room transaction.** If either v1 file existed, insert the entry **"Workout"** at position 0 and shift any existing positions up by one. Write `meta.v1_migrated = "true"` in the same transaction.
4. **Copy the flag.** If the v1 settings file exists, copy `notification_permission_asked` into `app.preferences_pb`, as an idempotent overwrite.
5. **Clean up.** Delete `settings.preferences_pb`, `counter.preferences_pb` and any `*.preferences_pb.tmp` siblings, then complete `ready`.

**Crash safety:**
- **Crash before step 3 commits:** there is no marker, so the next launch redoes steps 2–5.
- **Crash after step 3 commits:** the marker is present, so the next launch skips the import and only completes steps 4–5.
- **Data is never deleted** before the transaction that copied it has committed.
- **No re-import later:** completion is recorded in `meta`, not inferred from an empty table, so deleting every entry never brings "Workout" back.
- **Fresh install:** only the marker is written.

## 7. Screens, navigation and run ownership (replaces v1 §9.1 and the v1 settings list)

### 7.1 Run ownership

The workout **snapshot is frozen at Start**: `WorkoutSnapshot(entryId, entryName, timing, cues)`. The service, the timer screen and the notification read only the snapshot, so renaming, editing or deleting the source entry never changes an active run. Once the run has started, it is no longer tied to the database.

**Busy rule:** `TimerController.isBusy(entryId): Boolean` returns `status != IDLE && snapshot?.entryId == entryId`, so the definition lives in the controller. The entry-settings ViewModel calls it at the moment of every destructive action:
- **Delete** of a busy entry throws `EntryBusy` and is disabled in the UI, with the hint "Stop the workout first". The check reads the singleton controller at the moment of the call, so a stale screen can't bypass it.
- **Rename and settings edits** on a busy entry are allowed. They affect future runs only, because the snapshot is frozen.
- **After process recreation:** the controller starts IDLE and v1's `START_NOT_STICKY` service has ended, so nothing is busy and deletes are allowed.

### 7.2 Routes

`entries` (start) · `entry/{id}` · `entry/{id}/settings` · `entry/{id}/settings/timing|progression|current|cues` · `timer`

- **Active-run routing** (v1 §4) is unchanged: while a workout is RUNNING, the app navigates to `timer` if it isn't already there.
- **Leaving the timer:** `TimerController` gains `lastEntryId: Long?`. It is set by `prepare()` and **not** cleared by `clearRun()`.
  - On exit, the nav host searches the back stack for the `entry/{id}` entry whose `id` argument equals `lastEntryId`, and pops to it.
  - If that entry isn't on the back stack (for example, after the activity was recreated), it pops to `entries`.
- **Missing entry:** any screen whose `entry(id)` flow emits `null` *after loading* pops to `entries`. That covers deletion elsewhere, stale saved state and bad ids. The initial loading value never triggers this.
- All user-triggered navigation keeps v1's double-tap guard (`dropUnlessResumed`, `launchSingleTop`).

### 7.3 Entry list (new home)

- **UI state:** `Loading | Empty | Items(list)`. While `Loading`, a progress indicator shows and the FAB and rows are disabled. `Empty` appears only after migration readiness, so it can never race the migration.
- **Top bar:** the app name and a **Reorder** toggle.
- **Rows:** a `LazyColumn` keyed by `id`, each row at least 56 dp, showing:
  - the name;
  - **"Reps N"**, the entry's current total, which is the next workout's total;
  - a ✓ "Checked in today" marker when it applies, refreshed on resume as on v1's home screen, so it's correct after midnight.
- **Tap a row** to open `entry/{id}`.
- **+ FAB:** opens the name dialog (§8.3), creates the entry, and navigates to `entry/{new}` with `popUpTo(entries)`.
- **Reorder mode:** each row shows ▲/▼ buttons (48 dp, with content descriptions "Move <name> up/down").
  - The first and last rows disable the direction they can't move in.
  - A tap calls `moveBy(id, ±1)`, and the moved row stays scrolled into view.
  - Reorder mode is exited with the toggle.
- **Empty state:** "No workouts yet", with an "Add your first workout" button that does the same as +.

### 7.4 Entry screen

- **Content:** the v1 home table for this entry: per-set reps, Total Reps, Last Check In, Best CI Streak, Curr CI Streak, Today, "Checked in today", and Start.
- **Top bar:** ← back to the list, the entry name, and ⚙ → `entry/{id}/settings`. **While Start is in progress (`starting`), ← and ⚙ are disabled.**
- **Start** behaves exactly as in v1 §4, using the frozen snapshot (§7.1) and `EntryRepository.checkIn(id, clock)`. If `checkIn` throws, including `EntryNotFound`, the v1 failure path applies: `fail()` → `cancelPrepare()`, and an error is shown.

### 7.5 Entry settings

- A list of **Timing**, **Progression**, **Current State** and **Cues**.
- **Rename:** opens the name dialog, prefilled.
- **Duplicate:** creates the copy, then navigates to `entry/{copy}` with `popUpTo(entries)`.
- **Delete:** a confirmation dialog titled "Delete <name>?", with the text "Its rep total, streaks and settings will be lost." and Delete/Cancel buttons. After deletion, it pops to `entries`. The busy rule (§7.1) applies.
- **Save racing a delete:** if a settings save runs against an entry that has just been deleted, it catches `EntryNotFound` and pops to `entries`. It never crashes.

### 7.6 Timer and notification

- **Timer screen:** shows the snapshot's `entryName` above the Sets/Elapsed row.
- **Notification:**
  - the title is `"<entryName> · <Phase> · Set n/N"`;
  - the first "Starting…" notification is `"<entryName> · Starting…"`, taken from `controller.snapshot`.
- The name is frozen for the run (§7.1). Everything else in v1 §8, §9.2 and §10 is unchanged.

## 8. Settings pattern (amends v1 §9.3)

### 8.1 Stepper row (shared component)

- **Layout:** the label on top, then the **−** button, a large value and the **+** button. The buttons are 48 dp; tap for one step, hold to repeat. An inline error or hint sits beneath.
- **Tapping the value** opens the edit dialog.
- **Steppers clamp** at each field's hard minimum and maximum, so − never goes below a hard minimum.
- **Cross-field rules** are not clamped. These are floor ≤ starting total ≤ cap, a TOTAL of at most 2:00:00, and best streak ≥ current streak. Instead, they show inline errors and disable Save, as in v1 §10.

| Screen | Field | Step | Hard range | Dialog input |
|---|---|---|---|---|
| Timing | Prepare, Rest, Cooldown | 5 s | 0 – 59:59 | time (§8.2) |
| Timing | Work | 5 s | 1 s – 59:59 | time |
| Timing | Sets | 1 | 1 – 20 | whole number |
| Progression | Starting total, Floor, Cap, Hold at | 1 | 1 – 9999 | whole number |
| Progression | Hold for | 1 | 0 – 999 | whole number |
| Progression | Check-in window (h) | 1 | 1 – 999 | whole number |
| Progression | Penalty rate (h/rep) | 0.5 | 0.5 – 999.5 | decimal |
| Current State | Total | 1 | 1 – 9999 | whole number |
| Current State | Best streak, Current streak | 1 | 0 – 99999 | whole number |

**Screen state model:**
- **Settings ViewModel:** holds the typed draft, one domain value per field, such as `TimingConfig`. It also holds the validation result, derived from the draft. The draft lives in the ViewModel, so it survives rotation and configuration changes. The last saved value is whatever the repository flow emits.
- **Edit dialog:** holds its own text state (`rememberSaveable`, so it survives rotation too) and a parse result derived from that text. It changes the draft only when OK is pressed.
- **Penalty rate:** the draft holds it as integer **half-hours**, so hold-to-repeat never builds up floating-point drift.
  - A dialog value that isn't a multiple of 0.5 is kept exactly, for display and saving.
  - The next ± press snaps it to the neighbouring multiple of 0.5. For example, 0.3 with + becomes 0.5, and with − it clamps to 0.5.

**Screen-specific rules:**
- **Timing** keeps its TOTAL footer, which turns red and disables Save above 2:00:00.
- **Progression** keeps "Reset to defaults" and the "Hold disabled" hint.
- **Current State:**
  - the last check-in keeps its date and time pickers (tapping the date text opens them) and Clear;
  - Reset progress is kept.
- **Cues** keeps its two switches, restyled with the stepper rows' spacing.

### 8.2 `ValueFormat` (pure, unit-tested)

- **`parseSeconds`:**
  - accepts `m:ss` with exactly two second digits from 00 to 59 (`"1:30"` = 90, `"0:05"` = 5), or plain whole seconds (`"90"` = 90);
  - rejects `"1:5"`, `"1:60"`, negatives, blank input and garbage, returning null.
- **`formatSeconds`:** `formatSeconds(90) = "01:30"`. Values can't exceed 59:59 because of the hard range, so there's no hour form.
- **`parseInt`:** accepts non-negative whole numbers; anything else returns null.
- **`parseDecimal`:** accepts non-negative finite decimals, with either a dot or a comma as the separator. Negatives, `NaN`, infinity and garbage return null.

### 8.3 Dialogs

- **Edit value dialog:**
  - The title is the field label. There's one text field, prefilled with the current value and selected, using the keyboard type for that input kind.
  - OK is disabled while the text doesn't parse, with an inline "Enter a number" or "Use m:ss or seconds".
  - A parsed value is clamped to the field's hard range. Cross-field rules are left to the screen's validation.
- **Name dialog:** one text field. OK is disabled unless `EntryNames.validate` passes, and an inline message explains why (empty, or longer than 40 characters). It's used for both create and rename, and a rename dialog is prefilled with the current name.

## 9. Testing

**Unit tests**
- `ValueFormat`: every rule in §8.2.
- `EntryNames`, and truncation of duplicate names.
- Entity ⇄ domain mapping:
  - NULL total resolves to the starting total;
  - an invalid total becomes NULL;
  - per-field repair, and per-group fallback only when a group is still inconsistent.
- Write validation rejects invalid input.
- Stepper clamping, and half-hour stepping.

**Room tests (Robolectric, in-memory database)**
- CRUD.
- Contiguous positions after `create`, `duplicate`, `delete` and `moveBy`, including:
  - first ↑ and last ↓ (no-ops);
  - rapid repeated `moveBy`;
  - a 100-entry list shuffled with random moves, then checked for contiguity and order.
- Duplicate copies the config but not the counter.
- Check-ins:
  - concurrent `checkIn` calls on one entry record exactly one check-in;
  - two entries stay independent;
  - `checkIn` uses the row's own progression;
  - `checkIn` and `setProgression` interleaved in both orders.
- `setProgression` and `overwriteCounter` reset holdCount in the same UPDATE.
- `resetProgress`.
- `EntryNotFound` for missing ids.

**Migration tests (Robolectric)**
- Both v1 files present: one "Workout" entry with identical values.
- Only the settings file present.
- Only the counter file present.
- A corrupted v1 file falls back to defaults.
- Non-default v1 values carry across.
- An absent `total` and an invalid `total` both become NULL.
- `notification_permission_asked` is copied when the v1 settings file exists, and not created otherwise.
- The v1 files and their `.tmp` siblings are deleted, and the marker is written.
- Re-running is a no-op.
- A simulated crash after the commit (marker present, files still there) completes the cleanup without creating a duplicate entry.
- The files are read with no Hilt DataStore present.
- A fresh install writes only the marker.
- A `create` racing the migration still produces the "Workout" entry, and the list never shows Empty before readiness.

**ViewModel tests**
- Entry list: Loading, Empty and Items; reorder; create navigates to the new entry.
- Entry screen:
  - the v1 start-flow tests, ported to per-entry;
  - `checkIn` throwing `EntryNotFound` during PREPARING returns to idle with an error;
  - ← and ⚙ are disabled while starting.
- Settings:
  - typed drafts survive recreation of the ViewModel's owner;
  - the busy rule, including `EntryBusy` on delete;
  - a rename during an active run leaves the snapshot's `entryName` unchanged;
  - a loaded-null entry triggers the pop;
  - a save racing a delete doesn't crash.

**Navigation tests**
- Exiting the timer pops to the run's entry.
- Exiting the timer pops to `entries` when that entry isn't on the back stack.

**Compose tests (Robolectric)**
- Stepper row:
  - +/− buttons;
  - hold-to-repeat, using the compose test clock;
  - tap the value → dialog → OK updates the value;
  - bad input disables OK.
- Name dialog: validation (empty, 41 characters), and prefill on rename.
- Delete confirmation: the entry name and warning text; the button disabled with its hint while busy.
- Duplicate: the name suffix.
- Reorder buttons, and their disabled edges.
- The empty state and the loading state.

**Manual checks on a device** (appended to v1 §11)
- Create, rename, duplicate, delete and reorder entries.
- Two entries keep separate counters across days.
- Renaming an entry during a run doesn't change the notification.
- Installing this build over the existing v1 install on the Pixel 9a keeps its data as the "Workout" entry.

## 10. Project conventions

These are unchanged from v1 §12 and `claude.md`:
- commits are authored as `mitenko <mitenko@gmail.com>`;
- no AI attribution;
- the squash-to-one-commit PR protocol.

## 11. Review findings (rewritten)

This design was reviewed twice. The independent review (Review A) was accepted in full. The deep review (Review B) was also incorporated and is summarized below in a rewritten form.

### Primary risks addressed
- Migration must be crash-safe and idempotent. The design now writes a Room migration marker in the same transaction as the insert, deletes v1 files only after commit, and includes explicit crash-path tests.
- Active workouts must not be coupled to mutable entry metadata. The run snapshot freezes `entryId`, `entryName`, timing and cues at start, so rename, delete, reorder or settings changes do not mutate an in-flight workout.
- `EntryBusy` must be enforced at the domain boundary, not only in UI. The ViewModel and singleton controller share the same busy-state contract so a deleted or busy entry cannot be changed after process recreation.
- Name collisions and ordering must be unambiguous. Duplicate names are allowed intentionally, with explicit suffix behavior on duplicate; `moveBy` has an explicit clamp/no-op contract; list reorder tests cover edge cases and long lists.
- Settings and persistence boundaries must be explicit. The notification flag is app-level and sticky; v1 DataStore is migration-only; Room writes validate row data; totals remain normalized and never null in the domain model.

### Findings mapped to the design
| Finding | Resolution | Where |
|---|---|---|
| Migration guard and crash recovery | Same transaction marker + cleanup after commit; crash windows tested | §6, §9 |
| Run ownership with multiple entries | Frozen snapshot at start; metadata edits do not affect active runs | §7.1 |
| Delete guard only in the UI | Busy check enforced in ViewModel and controller; process recreation is safe | §7.1 |
| Name collisions | Duplicate names allowed; explicit duplicate naming contract | §5.5 |
| Permission flag contract | Sticky app-level flag; migration writes it only if v1 settings exist | §5.4 |
| Move contract and long-list behavior | `moveBy` clamp/no-op/shift semantics; large shuffle tests | §5.3, §9 |
| Row integrity | Validation on write; repair on read; group fallback only when still inconsistent | §5.2 |
| `NULL total` handling | In-memory total never null; tests cover first-check-in rendering | §5.1, §9 |
| Typed draft state | Draft is typed in ViewModel; dialog text kept in `rememberSaveable` | §8.1 |
| Migration coverage | Partial file, corrupted file, non-default values, and permission scenarios covered | §9 |
| Check-in versus settings save | SQLite serialization and both interleavings tested | §5.3 |
| Rename during a run | Name frozen in the active run snapshot | §7.1, §7.6, §9 |
| Position index semantics | Non-unique `position` index + deterministic `ORDER BY position, id` contract | §5.2 |
| Loading/empty states during migration | `Loading` with disabled controls; `Empty` only after readiness | §7.3 |
| Backup and restore behavior | Auto Backup covers `databases/` and `datastore/`; restored data is normal app data | §4 |
| Dialog UX and validation | Name, delete, duplicate and busy-state dialogs tested | §9 |

### Additional accepted fixes from Review A
- v1 DataStore providers removed; only migration access remains
- `total` is read raw and normalized as needed
- `lastEntryId` is retained for timer exit flow
- Back and settings buttons are disabled while starting
- `EntryNotFound` handling is explicit on failure
- create/duplicate/delete navigation is safe and tested
- the row's progression is used by `checkIn`
- single-UPDATE atomicity is required for counter updates
- Room plugin and schema directory are included
- stepper clamping and half-hour value drafts are specified
- `ValueFormat` edge cases are covered
- the notification title during startup is explicit
- threading rules are documented and enforced

### Final assessment
The design is now materially stronger as an implementation contract. The remaining work is not speculative; it is a matter of finalizing the app-level behavior around migration, active-run ownership, and UI state transitions. Once those contracts are implemented and the test suite above is green, the design is ready to move into code.
