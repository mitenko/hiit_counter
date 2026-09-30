# HIIT Counter

## Brief
Build a HIIT timer (exercise timer) app that also incorporates the incremental counter for the number of reps / exercises to do per interval.
Android app, Kotlin, MVVM. Repo: https://github.com/mitenko/hiit_counter
As you develop, refine the instructions here.

## Source of truth
- **Design spec (approved):** `docs/superpowers/specs/2026-09-24-hiit-counter-design.md` — read before any change. If code and spec disagree, raise it; don't silently diverge. Update the spec when a decision changes.
- **Multi-entry revision (approved, amends v1):** `docs/superpowers/specs/2026-09-25-multi-entry-design.md` — read both.
- **Settings pager revision (approved, amends both):** `docs/superpowers/specs/2026-09-28-settings-pager-design.md` — read all three.
- **Check-in / voice revision (approved, amends all three):** `docs/superpowers/specs/2026-09-29-checkin-voice-design.md` — read all four.
- **Drag-to-reorder (approved, amends R2 §7.3):** `docs/superpowers/specs/2026-09-29-drag-reorder-design.md` — ≡ handles replace Reorder mode.
- **Cue toggles (approved, amends R4 §5):** `docs/superpowers/specs/2026-09-30-cue-toggles-design.md` — Sound/Vibration/Voice toggle buttons on the timer screen; cues are live mid-run, not frozen.
- **Timer only (approved, amends R4 §4.2, §4.5, §4.6, §4.7 and §5):** `docs/superpowers/specs/2026-09-30-timer-only-design.md` — the "Check-in only" entry type becomes **Timer only**: the timer still runs, reps aren't counted or shown, the centre and voice use the current set number, and all four settings tabs are shown.
- **History (approved, R6):** `docs/superpowers/specs/2026-09-29-history-design.md` — the check-in log (`check_in`, Room v4), the weekly count and Clear history too. Its §4.1–4.2 screens are replaced by the UI refresh.
- **UI refresh (approved, rev 9, amends R6 §4.1–4.2, R2 §7.3–7.5, R3 §4, R5 and rev 8):** `docs/superpowers/specs/2026-09-30-ui-refresh-design.md` — rounded list tiles with "X× this week" and a tile graph, a chart-centred entry screen (no History screen), card-style settings and icon tabs.
- **References:** `references/` (local only, gitignored — not in the repo)
  - `sheet_script.js` — the Google Sheets Apps Script this app replaces (original progression logic).
  - `images.jfif` — timer screen look (dual ring; centre shows reps, not "WORK").
  - `unnamed.png` — Timing settings screen look (PREPARE / SETS / WORK / REST / COOLDOWN steppers, TOTAL footer).

## Key decisions (see spec for detail)
- Tabata: prepare → N × (work / rest, no rest after last set) → cooldown. Defaults 10s / 8 × 20s / 10s / 0s = 4:00.
- Check-in happens on **Check in** or on **Start** (Start still checks in, after the service has started; a second call the same day is a no-op), max once per local calendar day per entry, atomically in one Room transaction using the entry's own progression and type.
- Entries are **Counter** (label; code name `WORKOUT`: timer + reps) or **Timer Only** (label) (`entry.type`, stored as `CHECK_IN`): the timer runs exactly as for a Workout, but no reps are counted or shown — the timer's centre number and the voice cue use the current set instead — and checking in updates only the streaks and date, never the total or hold count. The type is chosen at create time and switched in Entry Settings without losing any values; every entry shows all four settings tabs (Timing · Progression (window only for Timer only) · Current (no total for Timer only) · Cues).
- Progression math matches the sheet (36 h window, penalty `round((h−24)/19.5)−1`, floor 48, cap 72) except the hold: hold at `holdAt` (64) for `holdFor` (4) check-ins, counting the day it's reached; a miss that leaves the total at `holdAt` restarts the hold.
- Starting total (48) is separate from the floor. No first-run prompt; all values are edited in one settings pager per entry (tabs Timing · Progression · Current · Cues, swipe). Valid drafts auto-save (steppers 400 ms after the last change, everything else at once; invalid drafts never save), and every row has an ⓘ info tag. The Hold switch (`hold_enabled`) keeps its values when off; the hold count resets only when holdAt, holdFor, the effective hold or the total changes.
- Home = the entry list (create, rename, duplicate, delete, reorder) as rounded tiles: name + ✓, "X× this week" (Monday–Sunday, local) and a 28-day tile graph before ≡. Each entry opens the entry screen: a 4 weeks · 3 months · All switch over a line chart (Counter) or a check-in calendar (Timer Only), a reps column for Counter entries, "Streak N · best M", then Check in / Start. The run snapshot (entry id, name, timing, countsReps) is frozen at Start; cues are live via `TimerController.liveCues`.
- History (`check_in` table, Room v4): every recorded check-in logs one row (`at`, `total`; NULL total for Timer only) inside the check-in transaction; the v4 migration seeded one point per entry. Only `checkIn` writes rows; Reset progress can clear them ("Clear history too"); `delete` removes them explicitly (plus `ON DELETE CASCADE`). There is no History screen.
- Stack: Compose + Material 3 (dark), Hilt, Navigation Compose, Room 2.8.1 (`hiit.db`, schemas committed in `app/schemas/`), DataStore Preferences (only `app.preferences_pb`), coroutines/Flow. minSdk 26. Package `com.mitenko.hiitcounter`. User-visible app name: **REPKIT** (`app_name`); the package id, db and repo keep the old name, because changing the package id loses user data.
- Timer: `TimerController` singleton owns the engine; `TimerService` (specialUse foreground service + partial wake lock) only hosts it. ViewModels never bind to the service.
- Voice cue (`cue_voice`): `TimerController` puts each WORK set's reps (the set number for Timer only entries) on `Cue.PhaseStart`; `CuePlayer` says them after the beep through `platform/CueSpeaker` (TextToSpeech, English), inside the beeps' ducking focus. `TimerService` creates the speaker while the live cues have Voice on, and shuts it down when Voice is toggled off. The manifest's `<queries>` TTS_SERVICE entry is required for Android 11+.
- Cue toggles: cues are live mid-run via `TimerController.liveCues`, not frozen with the rest of the snapshot; each toggle applies at once and saves to the entry.

## Working rules
- `domain/` stays pure Kotlin (no Android imports) and is developed test-first.
- Inject `Clock` (wall + monotonic time) everywhere; never call `System.currentTimeMillis()` / `Instant.now()` directly.
- Git: all work on feature branches + PRs to `main` (GitHub, `gh`). Never merge locally into `main`; after a server-side merge, sync with `git pull --ff-only`. Ask before pushing.
- Commit identity: `mitenko <mitenko@gmail.com>` (applied automatically for GitHub remotes); never commit with a work identity.
- No AI co-author trailers or "generated with" footers in commits or PR descriptions.
- PR protocol (before opening/updating a PR): verify the build (`./gradlew testDebugUnitTest lintDebug`), `git fetch` + fast-forward `main`, `git rebase main`, squash to ONE commit (`git reset --soft $(git merge-base main HEAD)` then a concise title + bullets describing the code changes), `git push --force-with-lease`, then `gh pr create` / `gh pr edit`.
- Long-running commands (Gradle builds, test runs, emulator): announce them, tag output with a `CLAUDE-<LABEL>` sentinel, log to the session scratchpad.
- Build/test: `./gradlew assembleDebug testDebugUnitTest lintDebug` (Git Bash, repo root). Compose UI tests run on Robolectric (`@Config(sdk = [34])`), no emulator needed.
- Plans: `docs/superpowers/plans/2026-09-24-hiit-counter.md` (v1), `docs/superpowers/plans/2026-09-25-multi-entry.md` (multi-entry), `docs/superpowers/plans/2026-09-28-settings-pager.md` (settings pager), `docs/superpowers/plans/2026-09-29-checkin-voice.md` (check-in / voice), `docs/superpowers/plans/2026-09-30-history.md` (history + UI refresh).
- Room schema changes (current version 4): bump the `@Database` version, add a Migration to `HiitDatabase.MIGRATIONS`, commit the exported `N.json`, and add a `MigrationTestHelper` test (the schemas are debug assets, which Robolectric reads; the file-based test is skipped on Windows, so also run the migration SQL on an in-memory framework database).
