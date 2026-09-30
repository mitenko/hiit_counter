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
- **References:** `references/` (local only, gitignored — not in the repo)
  - `sheet_script.js` — the Google Sheets Apps Script this app replaces (original progression logic).
  - `images.jfif` — timer screen look (dual ring; centre shows reps, not "WORK").
  - `unnamed.png` — Timing settings screen look (PREPARE / SETS / WORK / REST / COOLDOWN steppers, TOTAL footer).

## Key decisions (see spec for detail)
- Tabata: prepare → N × (work / rest, no rest after last set) → cooldown. Defaults 10s / 8 × 20s / 10s / 0s = 4:00.
- Check-in happens on **Check in** or on **Start** (Start still checks in, after the service has started; a second call the same day is a no-op), max once per local calendar day per entry, atomically in one Room transaction using the entry's own progression and type.
- Entries are **Workout** (timer + reps) or **Check-in only** (`entry.type`; streaks and date only, the total and hold count never change). The type is chosen at create time and switched in Entry Settings without losing any values; check-in-only entries have no Timing or Cues pages, and their pager shows only Progression (window) · Current (no total).
- Progression math matches the sheet (36 h window, penalty `round((h−24)/19.5)−1`, floor 48, cap 72) except the hold: hold at `holdAt` (64) for `holdFor` (4) check-ins, counting the day it's reached; a miss that leaves the total at `holdAt` restarts the hold.
- Starting total (48) is separate from the floor. No first-run prompt; all values are edited in one settings pager per entry (tabs Timing · Progression · Current · Cues, swipe). Valid drafts auto-save (steppers 400 ms after the last change, everything else at once; invalid drafts never save), and every row has an ⓘ info tag. The Hold switch (`hold_enabled`) keeps its values when off; the hold count resets only when holdAt, holdFor, the effective hold or the total changes.
- Home = the entry list (create, rename, duplicate, delete, reorder); each entry opens the sheet-style table. The run snapshot (entry id, name, timing, cues) is frozen at Start. No history.
- Stack: Compose + Material 3 (dark), Hilt, Navigation Compose, Room 2.8.1 (`hiit.db`, schemas committed in `app/schemas/`), DataStore Preferences (only `app.preferences_pb`), coroutines/Flow. minSdk 26. Package `com.mitenko.hiitcounter`. User-visible app name: **Repkit** (`app_name`); the package id, db and repo keep the old name, because changing the package id loses user data.
- Timer: `TimerController` singleton owns the engine; `TimerService` (specialUse foreground service + partial wake lock) only hosts it. ViewModels never bind to the service.
- Voice cue (`cue_voice`): `TimerController` puts each WORK set's reps on `Cue.PhaseStart`; `CuePlayer` says them after the beep through `platform/CueSpeaker` (TextToSpeech, English), inside the beeps' ducking focus. `TimerService` creates the speaker only for a voice run. The manifest's `<queries>` TTS_SERVICE entry is required for Android 11+.

## Working rules
- `domain/` stays pure Kotlin (no Android imports) and is developed test-first.
- Inject `Clock` (wall + monotonic time) everywhere; never call `System.currentTimeMillis()` / `Instant.now()` directly.
- Git: all work on feature branches + PRs to `main` (GitHub, `gh`). Never merge locally into `main`; after a server-side merge, sync with `git pull --ff-only`. Ask before pushing.
- Commit identity: `mitenko <mitenko@gmail.com>` (applied automatically for GitHub remotes); never commit with a work identity.
- No AI co-author trailers or "generated with" footers in commits or PR descriptions.
- PR protocol (before opening/updating a PR): verify the build (`./gradlew testDebugUnitTest lintDebug`), `git fetch` + fast-forward `main`, `git rebase main`, squash to ONE commit (`git reset --soft $(git merge-base main HEAD)` then a concise title + bullets describing the code changes), `git push --force-with-lease`, then `gh pr create` / `gh pr edit`.
- Long-running commands (Gradle builds, test runs, emulator): announce them, tag output with a `CLAUDE-<LABEL>` sentinel, log to the session scratchpad.
- Build/test: `./gradlew assembleDebug testDebugUnitTest lintDebug` (Git Bash, repo root). Compose UI tests run on Robolectric (`@Config(sdk = [34])`), no emulator needed.
- Plans: `docs/superpowers/plans/2026-09-24-hiit-counter.md` (v1), `docs/superpowers/plans/2026-09-25-multi-entry.md` (multi-entry), `docs/superpowers/plans/2026-09-28-settings-pager.md` (settings pager), `docs/superpowers/plans/2026-09-29-checkin-voice.md` (check-in / voice).
- Room schema changes (current version 3): bump the `@Database` version, add a Migration to `HiitDatabase.MIGRATIONS`, commit the exported `N.json`, and add a `MigrationTestHelper` test (the schemas are debug assets, which Robolectric reads; the file-based test is skipped on Windows, so also run the migration SQL on an in-memory framework database).
