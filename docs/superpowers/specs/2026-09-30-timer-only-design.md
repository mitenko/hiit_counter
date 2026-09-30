# REPKIT — Timer only entries (spec revision 8)

**Date:** 2026-09-30
**Status:** Approved by the user in chat (2026-09-30).
**Amends:** `2026-09-29-checkin-voice-design.md` (R4) §4.2, §4.5, §4.6, §4.7 and §5. Everything else in v1, R2, R3 and R4, plus the cue-toggles revision, still holds.

## 1. Purpose

R4's "Check-in only" entry type was the wrong concept. The right one is **Timer only**: the timer
still runs, exactly as for a Workout, but nothing counts or shows reps. During a run, the centre
number and the voice cue give the current set instead of a rep count.

The stored value and the enum are unchanged: `Entry.type: EntryType`, `EntryType.CHECK_IN`. Only
the label, the screens and the timer's display change.

## 2. Scope

**In scope**
- Label: `type_check_in` → "Timer only"; `info_type` and `info_voice` reworded.
- `WorkoutSnapshot.countsReps: Boolean`, set from `entry.type == WORKOUT`, frozen at Start like the
  rest of the snapshot (name and timing).
- The entry screen: a Timer only entry shows both Check in and Start, laid out like a Workout; its
  table stays streak-only (no rep rows, no Total Reps).
- The timer, when `countsReps` is false: the centre shows the current (WORK) or upcoming
  (REST/PREPARE) set number, never a rep value; the voice cue speaks the set number; DONE shows no
  rep value.
- Settings: `SettingsPage.visibleFor` returns all four pages for every type; Entry Settings shows
  the Timing and Cues rows for a Timer only entry too; the Cues page's voice-availability check runs
  for every type.

**Out of scope**
- Renaming `EntryType.CHECK_IN` or its stored string, or any Room migration — the value is unchanged.
- Any change to a Workout's behaviour, which stays byte-identical.
- The entry list byline ("Streak N" with its ✓) — unchanged.
- `RepProgression.checkIn`'s `countsReps` semantics (§3.1 of R4) — unchanged: the total and hold
  count never move for a Timer only entry.

## 3. Data model

### 3.1 Domain
- `WorkoutSnapshot` gains `countsReps: Boolean = true`. `EntryViewModel` sets it to
  `entry.type == EntryType.WORKOUT` when preparing the run. Like the rest of the snapshot, it is
  frozen at Start: a mid-run type switch (not currently possible from the timer screen) would have
  no effect on the running workout.
- `TimerController.withReps` (the private helper that stamps a set's reps onto
  `Cue.PhaseStart(WORK)`) puts the **set number** on the cue when the run's snapshot has
  `countsReps == false`, instead of looking the reps up in the list passed to `start()`. The engine
  (`TabataEngine`) and `RepProgression` are untouched — a Timer only run still gets a per-set list
  built by `RepDistributor` (from the entry's current, unchanging total) so the engine's set count
  and durations are correct; that list's values are simply never shown or spoken.

### 3.2 No Room change
`entry.type` and its `'CHECK_IN'` value are unchanged. No migration.

## 4. Screens

### 4.1 Entry screen (amends R4 §4.2)
A Timer only entry now shows the same button row as a Workout: **Check in** (outlined) and
**Start** (filled), side by side, both at least 56 dp tall. Start's flow is the flow already
described in R4 §4.1: it starts the service, calls `checkIn` (streak-only, since
`RepProgression.checkIn` sees `countsReps = false` for this type via `EntryRepository.checkIn`),
then calls `controller.start(...)` with a per-set list built the normal way. The
`EntryViewModel.startWorkout` guard that previously returned early for a non-`WORKOUT` entry
(added as an R4 defensive check) is removed — that early return is exactly what stopped Timer only
entries from ever starting.

The table is unchanged from R4 §4.2: Last Check In, Best CI Streak, Curr CI Streak, Today — no rep
rows, no Total Reps.

### 4.2 Timer screen (amends R4 §5 and the cue-toggles spec (rev 7), the "Emission semantics" and screen sections of v1 §9.2)
When the run's snapshot has `countsReps == false`:
- **WORK:** the centre shows the current set number (e.g. "3" on set 3 of 8) in the phase colour,
  with no "reps" wording, exactly where the rep count sits for a Workout.
- **REST / PREPARE:** the centre shows the same (dimmed) number as today, but it is the upcoming
  set's number, not its rep count — consistent with `TimerState.set` already naming the upcoming
  set during these phases.
- **DONE:** no rep value is shown (no "N reps"); the label is "DONE" alone.
- The notification and `TimerText` already carry no rep values, so they need no change.

A Workout's mapping (`countsReps == true`, the default) is byte-identical to before.

### 4.3 Settings (amends R4 §4.5–4.6)
- `SettingsPage.visibleFor(type)` returns all four pages (`entries.toList()`) regardless of type.
  Entry Settings therefore shows the Timing and Cues rows again for a Timer only entry, alongside
  Progression and Current.
- The settings pager shows all four tabs for every entry. Progression stays window-only and Current
  stays total-less for a Timer only entry, exactly as in R4 §4.6 — only the *tab visibility*
  changes, not what each tab shows.
- The Cues page's voice-availability check (`CuesSettingsViewModel.init`) now runs unconditionally;
  the R4 §4.6 skip for a type with no Cues tab no longer applies, because every type has one. The
  Voice switch behaves exactly as for a Workout, with the reworded `info_voice` text.

## 5. Voice cue (amends R4 §5)

`Cue.PhaseStart(phase, reps)` is unchanged as a type. For a Timer only run, `reps` on the WORK cue
carries the **set number**, not a rep count (§3.1 above). `CuePlayer` is unchanged — it just speaks
whatever `Int` is on the cue — so "three" is spoken on set 3 exactly as "eight" would be spoken for
eight reps on a Workout.

## 6. Strings

| Key | Old (R4) | New |
|---|---|---|
| `type_check_in` | Check-in only | Timer only |
| `info_type` | Workout entries run a timer and count reps. Check-in only entries just record that you did it, with streaks. Switching keeps all your values. | Workout entries count reps and grow them as you check in. Timer only entries run the same timer without counting reps; checking in keeps your streak. Switching keeps all your values. |
| `info_voice` | Says the number of reps as each work set starts. | Says the number of reps (or the set number, for Timer only entries) as each work set starts. |

`streak_n`, `check_in`, `checked_in`, `type`, `type_workout`, `voice`, `voice_unavailable` are
unchanged.

## 7. Testing

- **Domain:** `withReps` with `countsReps = false` puts the set number on WORK and leaves other
  phases null; a Workout (`countsReps = true`, the default) keeps its reps; `WorkoutSnapshot`
  carries the flag.
- **Timer UI mapper:** the centre shows the set number (WORK and, dimmed, REST/PREPARE) for a
  Timer-only run and never a rep value, including on DONE; a Workout's mapping is unchanged.
- **EntryViewModel:** Start on a `CHECK_IN` entry prepares a snapshot with `countsReps = false`,
  checks in streak-only, and starts the timer; the old "no Start for check-in-only" test is
  replaced with one asserting the run actually starts.
- **EntryScreen:** a `CHECK_IN` entry shows both Check in and Start, and no rep rows.
- **Settings:** `visibleFor(CHECK_IN)` returns all four pages; Entry Settings shows the Timing and
  Cues rows for `CHECK_IN`; the pager shows four tabs for `CHECK_IN`, with Progression window-only
  and Current total-less; the Cues voice-availability check runs for `CHECK_IN`.

## 8. Project conventions

Unchanged from R4 §8: the mitenko identity, no AI attribution, squash to one commit and open a PR,
ask before pushing, TDD.
