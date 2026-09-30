# Repkit — Check-in-only Entries, Separate Check in / Start, and a Voice Cue (spec revision 4)

**Date:** 2026-09-29
**Status:** Approved by the user (2026-09-29).
**Amends:** `2026-09-24-hiit-counter-design.md` (v1), `2026-09-25-multi-entry-design.md` (R2) and `2026-09-28-settings-pager-design.md` (R3). Everything in those still holds unless this document replaces it.

## 1. Purpose

- Entries can be **check-in only**: a habit tracker with no timer and no reps.
- Workout entries get a separate **Check in** button, so you can see today's reps before pressing **Start**.
- An optional **Voice** cue says each set's reps as work starts.

## 2. Scope

**In scope**
- `EntryType` (Workout / Check-in only), chosen at create time and switchable in Entry Settings.
- Check-in-only check-ins update the day and the streaks only.
- Separate Check in and Start buttons on Workout entries.
- A Voice cue that speaks each set's reps at work start.
- Room version 2 → 3: `entry.type` and `entry.cue_voice`.

**Out of scope**
- Drag-to-reorder. That is PR B, with its own short spec.
- Check-in history, the "X× this week" byline, sparklines and the History screen. These come in a later PR, with Room version 4.
- Voice languages other than English, and spoken words other than the rep number.

## 3. Data model

### 3.1 Domain
- `enum class EntryType { WORKOUT, CHECK_IN }`.
- `Entry.type: EntryType`. New entries default to `WORKOUT`.
- `CueConfig.voice: Boolean = false`.
- `RepProgression.checkIn(state, config, now, zone, countsReps: Boolean = true)`.
  - When `countsReps` is **false**, rules 1–4 decide the outcome and the streaks exactly as today: already today, first, missed and on time. The result keeps `state.total` and `state.holdCount` **unchanged**, so there is no +1, no penalty, no clamp and no hold.
  - `lastCheckIn` is still set to `now`.
  - `Outcome.Missed.penalty` is 0.

### 3.2 Room migration 2 → 3
```sql
ALTER TABLE entry ADD COLUMN type TEXT NOT NULL DEFAULT 'WORKOUT';
ALTER TABLE entry ADD COLUMN cue_voice INTEGER NOT NULL DEFAULT 0;
```
- The database moves to `HiitDatabase` version 3. `MIGRATION_2_3` joins `MIGRATIONS`, and `3.json` is exported and committed. `1.json` and `2.json` stay byte-identical. There is still no destructive fallback.
- Existing entries become Workouts with the voice off. Check-in behaviour is unchanged.
- **Read repair.** An unknown `type` string reads as `WORKOUT`. `cue_voice` defaults to false.
- **Duplicate** copies `type` and `cue_voice`.
- **V1Migrator** imports as `WORKOUT` with the voice off.

### 3.3 Repository
- `checkIn(id, clock)` passes `countsReps = (type == WORKOUT)` inside its existing transaction.
- New method `setType(id, type)`. It waits for the migration gate and throws `EntryNotFound` if the entry is missing. It is a single UPDATE of `type`. Counter, timing, progression and cues are untouched, so switching back restores everything.
- `setCues` also writes `cue_voice`.
- `FakeEntryRepository` mirrors all of the above.

## 4. Screens

### 4.1 Entry screen, Workout
- **The table is unchanged.**
- **Buttons.** The single Start becomes a row of two, each at least 48 dp tall:
  - **Check in** (outlined). It calls `checkIn`, and the table then shows the new counter. Once checked in today (`lastCheckIn` is on today's local date), it reads **"Checked in ✓"** and is disabled until the date changes.
  - **Start** (filled). This is the existing start flow. It calls `checkIn` first. If the entry was already checked in today, that call returns `AlreadyToday` and nothing changes. It then prepares and starts the run.
- **Unchanged from v1 and R2:** cancel/throw handling, `dropUnlessResumed`, "busy while starting", and the check-in failure path.
- **Double-tap guard.** Check in is guarded against double taps: it is disabled while its call is in flight.

### 4.2 Entry screen, Check-in only
- **Table rows:** Last Check In, Best CI Streak, Curr CI Streak and Today. There are no rep rows and no Total Reps row.
- **Button:** one **Check in** button, which behaves as in §4.1. There is no Start button.
- **Top bar:** ← and ⚙, as today.

### 4.3 Entry list
- A Workout row is unchanged: "Reps N" and ✓.
- A Check-in-only row reads **"Streak N"**, where N is the current streak, with ✓ when checked in today.

### 4.4 Create
- The New workout name dialog gets a segmented button, **Workout | Check-in only**, defaulting to Workout.
- `create(name, type)`. The existing `create(name)` means `WORKOUT`.

### 4.5 Entry Settings
- **Type row.** A new **Type** row, with the subtitle "Workout" or "Check-in only" and an ⓘ, sits above the page rows.
  - Tapping it opens a dialog with two radio options and OK/Cancel. OK calls `setType` at once.
  - It is allowed while busy, because the run's snapshot is frozen.
  - `EntryNotFound` pops to the list.
- **Page rows for Check-in only.** The Timing and Cues rows are hidden. Progression and Current State remain.

### 4.6 Settings pager for Check-in only
- **Tabs.** The pager shows only **Progression · Current**.
  - `page` arguments map onto the visible tabs. An argument naming a hidden page opens the first visible tab.
- **Progression tab.** It shows only **Check-in window (hours)**, with its ⓘ. The other fields keep their stored values. They are still part of the draft that is validated and saved, and they are valid because they came from the store.
- **Current tab.** It shows Best streak, Current streak and Last check-in, with Clear and Reset progress. The total row is hidden, and saves keep the stored total.
- **Auto-save** works exactly as in R3.

### 4.7 Cues (Workouts)
- A third switch, **Voice**, with ⓘ.
- It persists immediately, the same way as Sound and Vibration.
- If the device has no usable text-to-speech engine, or no English voice, the switch shows the supporting text **"Voice not available on this device"**. The switch still saves.

## 5. Voice cue

- **The cue.** `Cue.PhaseStart(phase, reps: Int? = null)`. For `Phase.WORK`, `TimerController` fills `reps` from the rep list passed to `start()`, using that set's entry. For every other phase it stays null. This is pure domain.
- **`platform/CueSpeaker`** is an interface with `speak(number: Int)`, `available: StateFlow<Boolean>` and `shutdown()`. The Android implementation wraps `TextToSpeech`:
  - `Locale.ENGLISH`;
  - audio attributes `USAGE_ASSISTANCE_SONIFICATION`;
  - `QUEUE_FLUSH`;
  - the number spoken as its word, which the engine produces from `"12"`.
- **Lifecycle.**
  - `TimerService` creates the speaker when a run starts whose frozen snapshot has `cues.voice`, and shuts it down when the run ends or the service stops.
  - An initialisation failure or a missing language sets `available = false`, and the run continues silently.
- **`CuePlayer`.** On `PhaseStart(WORK, reps)`, it plays the existing beep and then, if voice is on and available, speaks `reps`.
  - Speech sits inside the same `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` request as the beeps, so music is lowered.
  - Focus is abandoned after the utterance completes (`UtteranceProgressListener`), with a 3 s timeout.
- **Screen-off timing.** It is unchanged. Speech never blocks the engine, and the wake lock rules are as in v1.

## 6. Strings (new)

| Key | Text |
|---|---|
| `check_in` | Check in |
| `checked_in` | Checked in ✓ |
| `type` | Type |
| `type_workout` | Workout |
| `type_check_in` | Check-in only |
| `streak_n` | Streak %1$d |
| `voice` | Voice |
| `voice_unavailable` | Voice not available on this device |
| `info_type` | Workout entries run a timer and count reps. Check-in only entries just record that you did it, with streaks. Switching keeps all your values. |
| `info_voice` | Says the number of reps as each work set starts. |

## 7. Testing

- **Domain (test-first).**
  - `countsReps = false` for the first, on-time, missed and already-today cases: the streaks and date change, and the total and hold count don't.
  - `PhaseStart(WORK)` carries the set's reps.
- **Room.**
  - The 2 → 3 `MigrationTestHelper` test, Windows-guarded and run on CI.
  - The migration SQL on an in-memory framework database.
  - Read repair of an unknown type.
  - `setType` keeps every other column.
  - Duplicate copies the new fields.
  - A check-in-only `checkIn` through Room.
- **Service.** `CuePlayer` with a fake `CueSpeaker`:
  - it speaks only for WORK with voice on and the speaker available;
  - it never speaks when unavailable;
  - focus is released.
- **ViewModel and Compose (Robolectric).**
  - Workout:
    - Check in updates the table, then shows "Checked in ✓", disabled.
    - Start without a check-in checks in, then starts.
    - Start after a check-in starts with no second check-in.
  - Check-in only:
    - The screen shows no rep rows and no Start.
    - The list shows "Streak N".
  - Settings:
    - The create dialog offers the type choice, and the Type row saves the type.
    - Hidden pager tabs, and `page` mapping.
    - The Voice switch, with its unavailable text.
- **Device (Pixel 9a).**
  - Back up with `run-as … tar` after a force-stop, then install over the R3 build.
  - Pushups and Bridges survive as Workouts.
  - Create a check-in-only entry and check in.
  - Check in, then Start, on a Workout.
  - Hear the voice during a run with the screen off and music playing.

## 8. Project conventions

These are unchanged:
- the mitenko identity;
- no AI attribution;
- squash to one commit and open a PR;
- ask before pushing;
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
