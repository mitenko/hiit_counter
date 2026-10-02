# REPKIT — Workout session log (spec revision 17)

**Date:** 2026-10-01
**Status:** Approved by the user in chat (2026-10-01).
**Amends:** the Room schema (now v6) and R6 §3.2's delete and "Clear history too" rules. Adds no UI.

## Purpose

Groundwork for Strava and Health Connect later. Every timer run is recorded as one `workout_session` row. Nothing shows these rows yet.

## 1. Table (Room v6)

`workout_session`:

| Column | Type | Meaning |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `entry_id` | INTEGER NOT NULL | FK → `entry(id)` ON DELETE CASCADE, indexed (`index_workout_session_entry_id`) |
| `started_at` | INTEGER NOT NULL | Wall-clock epoch ms when `start()` began the engine (not prepare) |
| `ended_at` | INTEGER NOT NULL | Wall-clock epoch ms when the run ended |
| `active_sec` | INTEGER NOT NULL | Seconds actually running: the engine's active time, so pauses are left out (not the wall difference) |
| `planned_sec` | INTEGER NOT NULL | The snapshot's total duration |
| `sets_planned` | INTEGER NOT NULL | The snapshot's sets |
| `sets_completed` | INTEGER NOT NULL | Distinct work sets that ended, by time or by ⏩ skip forward |
| `reps_done` | INTEGER NULL | Sum of `repsPerSet` over the completed sets for a Counter run; NULL for Timer only |
| `completed` | INTEGER NOT NULL | 1 = reached DONE, 0 = stopped early |

`MIGRATION_5_6` creates the table and index with Room's own text (the exported `6.json` createSql). It seeds nothing: no run before v6 was recorded.

## 2. Counting

- **Sets completed** is the highest `TimerState.completedWorkSets` seen during the run. Sets finish in order, so this equals the number of distinct work sets that ended. A work phase skipped forward counts. Restarting a work phase with ⏪ doesn't count it. Skipping back over a completed set and running it again counts it once.
- **Reps done** is `repsPerSet.take(setsCompleted).sum()` for a Counter run, NULL for Timer only.
- **Active time** is the engine's active ms (pauses excluded), truncated to whole seconds. The engine keeps its total after DONE.

## 3. When a row is written

- Exactly once per run, when it ends: it reaches DONE, or it is stopped (`stop()`, including the 30-minute pause auto-stop).
- No row when the prepare step is cancelled or the service fails before `start()`.
- Nothing is recorded if the process dies mid-run (accepted).

Wiring: `TimerController` (domain) takes a `RunLog` (`fun interface RunLog { fun record(summary: RunSummary) }`) and a `wallNow` clock. At the end of a run it calls `runLog.record(RunSummary(...))` directly. `data/SessionRecorder` (a Hilt `@Singleton`) is that `RunLog`: `AppModule` passes it in when it builds the controller, so it exists before any run can start. It inserts the row on the application scope. A direct call is used instead of a flow so no summary can be missed for want of a collector. A failed insert (for example, the entry was deleted meanwhile) is logged and dropped.

## 4. Lifecycle (amends R6 §3.2)

- `EntryRepository.delete` deletes the entry's sessions explicitly, as it does its check-ins, plus the cascade.
- **Reset progress** with **Clear history too** also deletes the entry's sessions, in the same transaction. Without it, they're kept.
- **Duplicate** copies no sessions.
