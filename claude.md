# HIIT Counter

## Brief
Build a HIIT timer (exercise timer) app that also incorporates the incremental counter for the number of reps / exercises to do per interval.
Android app, Kotlin, MVVM. Repo: https://github.com/mitenko/hiit_counter
As you develop, refine the instructions here.

## Source of truth
- **Design spec (approved):** `docs/superpowers/specs/2026-09-24-hiit-counter-design.md` — read before any change. If code and spec disagree, raise it; don't silently diverge. Update the spec when a decision changes.
- **Multi-entry revision (approved, amends v1):** `docs/superpowers/specs/2026-09-25-multi-entry-design.md` — read both.
- **References:** `references/` (local only, gitignored — not in the repo)
  - `sheet_script.js` — the Google Sheets Apps Script this app replaces (original progression logic).
  - `images.jfif` — timer screen look (dual ring; centre shows reps, not "WORK").
  - `unnamed.png` — Timing settings screen look (PREPARE / SETS / WORK / REST / COOLDOWN steppers, TOTAL footer).

## Key decisions (see spec for detail)
- Tabata: prepare → N × (work / rest, no rest after last set) → cooldown. Defaults 10s / 8 × 20s / 10s / 0s = 4:00.
- Check-in happens on **Start**, max once per local calendar day per entry, atomically in one Room transaction using the entry's own progression.
- Progression math matches the sheet (36 h window, penalty `round((h−24)/19.5)−1`, floor 48, cap 72) except the hold: hold at `holdAt` (64) for `holdFor` (4) check-ins, counting the day it's reached; a miss that leaves the total at `holdAt` restarts the hold.
- Starting total (48) is separate from the floor. No first-run prompt; all values editable in Settings sub-screens (Timing, Progression, Current State, Cues).
- Home = the entry list (create, rename, duplicate, delete, reorder); each entry opens the sheet-style table. The run snapshot (entry id, name, timing, cues) is frozen at Start. No history.
- Stack: Compose + Material 3 (dark), Hilt, Navigation Compose, Room 2.8.1 (`hiit.db`, schemas committed in `app/schemas/`), DataStore Preferences (only `app.preferences_pb`), coroutines/Flow. minSdk 26. Package `com.mitenko.hiitcounter`.
- Timer: `TimerController` singleton owns the engine; `TimerService` (specialUse foreground service + partial wake lock) only hosts it. ViewModels never bind to the service.

## Working rules
- `domain/` stays pure Kotlin (no Android imports) and is developed test-first.
- Inject `Clock` (wall + monotonic time) everywhere; never call `System.currentTimeMillis()` / `Instant.now()` directly.
- Git: all work on feature branches + PRs to `main` (GitHub, `gh`). Never merge locally into `main`; after a server-side merge, sync with `git pull --ff-only`. Ask before pushing.
- Commit identity: `mitenko <mitenko@gmail.com>` (applied automatically for GitHub remotes); never commit with a work identity.
- No AI co-author trailers or "generated with" footers in commits or PR descriptions.
- PR protocol (before opening/updating a PR): verify the build (`./gradlew testDebugUnitTest lintDebug`), `git fetch` + fast-forward `main`, `git rebase main`, squash to ONE commit (`git reset --soft $(git merge-base main HEAD)` then a concise title + bullets describing the code changes), `git push --force-with-lease`, then `gh pr create` / `gh pr edit`.
- Long-running commands (Gradle builds, test runs, emulator): announce them, tag output with a `CLAUDE-<LABEL>` sentinel, log to the session scratchpad.
- Build/test: `./gradlew assembleDebug testDebugUnitTest lintDebug` (Git Bash, repo root). Compose UI tests run on Robolectric (`@Config(sdk = [34])`), no emulator needed.
- Plans: `docs/superpowers/plans/2026-09-24-hiit-counter.md` (v1), `docs/superpowers/plans/2026-09-25-multi-entry.md` (multi-entry).
- Room schema changes: bump the `@Database` version, add a Migration and a `MigrationTestHelper` test (the schemas are debug assets, which Robolectric reads; the file-based test is skipped on Windows).
