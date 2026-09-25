# Multi-Entry Spec — User Deep Review (2026-09-25)

Verbatim copy of the review appended to the spec draft (commit 555a372). Resolutions: spec §10.

This review focuses on implementation risk, migration correctness, and multi-entry lifecycle semantics. The design is structurally sound, but a few areas need explicit contracts before implementation.

### High priority

1. **Migration guard is not safe enough for crash-recovery and duplicate-prevention**

   The migration rule says: if the v1 files exist and the entry table is empty, migrate once; if the table is non-empty, skip. That works only if the database is durable and the migration is single-transaction. The spec does not define the exact ordering of `v1 file delete` vs. `Room insert` or whether the database is created before/after the migration. The risk is a partially applied migration that duplicates entries or leaves the v1 files deleted even though the copy failed. Add a migration marker in Room or in a small app-preferences flag, and define “inserted successfully” before deleting v1 data. Test both a crash after insert-before-delete and a crash after delete-before-insert.

2. **The app-wide timer ownership is still underspecified with multiple entries**

   The app still runs one workout globally. Once there are multiple entries, the timer must bind to a specific `entryId` and `entryName`, but the design does not state how rename, delete, move, or recreation of an entry affects an active run. A running workout should not silently change to a different entry if the user renames or deletes the source entry. Define a run token and freeze the entry metadata snapshot used by the service: `entryId`, `entryName`, and the config snapshot when the workout starts. A renamed or deleted source entry should not affect the active run while it is running.

3. **Delete-disabled-while-running is only a UI policy, not a lifecycle guarantee**

   The design says: “delete of the running entry is refused by the UI; repository itself has no knowledge of the timer.” That is not enough. Background process death, notification action races, or stale navigation can still leave a run attached to an entry that is later deleted by a different screen or a stale local state. Add a run state in the singleton controller/service with the active `entryId`, and reject any delete or rename action that targets that entry at the repository or viewmodel boundary. Test the case where the user deletes an entry while the service is still active, especially after process recreation.

4. **Default name collisions are not handled in create/duplicate flows**

   The duplicate rule says `"<name> copy"`, truncated to 40 chars. The create flow is described as name prompt + defaults, but the design does not say whether duplicate names are allowed or how a name collision is resolved. If a user creates “Workout”, then duplicates it, then creates another “Workout” later, the list can become ambiguous. Define the exact naming rule: either allow duplicates, or auto-suffix until unique, and update the empty-state and validation tests accordingly.

5. **A global notification-permission flag is easy to lose across migration and app restarts**

   The spec says the only global value is the notification-permission-asked flag, and the migration copies it from v1 `settings.preferences_pb` into a new `app.preferences_pb`. But the app also has a Room database, and the design does not say whether the flag is still needed after a fresh install or if a user denies the permission once and later re-enables it. Add a clear contract: permission asked is a sticky app-level flag, not entry-level; it survives reinstall only if the app data persists; and it is never created or deleted during migration unless the v1 file exists. Add tests for permission requested on Android 13+ before/after migration and permission denied permanently.

### Medium priority

6. **The transactional guarantees for positions are too optimistic without a canonical order model**

   `create`, `duplicate`, `delete`, and `move` each run in one transaction so positions remain contiguous, but the spec does not define how move behaves at the edges and whether duplicate positions or an out-of-range index are accepted. `move(id, toPosition)` can be called with `toPosition` beyond the last item or with the same position. The repository must canonicalize order deterministically on every write. Document the exact move contract: clamp to bounds, shift items in between, preserve stable ordering for duplicates, and test edge moves such as first→last, last→first, and moving an item to its current index.

7. **Room schema is very broad and can hide data-integrity bugs**

   One table with many nullable and non-null columns is convenient, but the design does not define what valid means for a row after a partial write, a migration failure, or a corrupted DB. In particular, `position` must be contiguous, `name` must be trimmed and 1–40 chars, and `total` must be either null or within floor/cap semantics. Add `CHECK` constraints or validator logic at query/write time, plus a migration test covering invalid/malformed row values. The spec currently says “reads apply the same validity rules as v1’s per-key fallback,” but assuming a single row, one invalid field must not poison the entire entity.

8. **The per-entry starting-total semantics for a null total need a clearer startup contract**

   `total` can be null and is read as the live `starting_total`. That is fine for a single value, but with per-entry list screens, the “today’s rep total” may be displayed from a nullable field while the actual entry progression uses a different live default. The design does not say whether `CounterState.total` is still a domain value or whether the repository maps null to `starting_total` everywhere. Define an explicit domain invariant: `Entry.counter.total` is always a value in the memory model, while the row stores `NULL` only for the fresh install semantics, and the UI should never display a null. Add a test for listing and entry screen rendering when `total` is null before first check-in.

9. **The stepper component needs stronger contracts for typed drafts and invalid values**

   The design says “Drafts become typed values (no string drafts)” and validates at the screen layer. That is good, but the flow still needs to handle “tapping value opens dialog” and “OK disabled while text doesn't parse.” Without a typed draft state, the UI can lose the user’s in-progress input across configuration changes or rotation. Specify the state model at the screen level: typed value + text representation + validation result + last successful value, and test text changes for time, decimal, and integer fields. Otherwise the stepper row + dialog pattern will be inconsistent across screens.

10. **Room migration tests are too narrow for a real upgrade path**

    The migration test mentions seeding v1 files and checking the one `Workout` entry, but the real app already has a v1 database and preferences possibly with settings not matching defaults. The design needs migration tests for: partial v1 data, a missing `counter.preferences_pb` but present `settings.preferences_pb`, a corrupted v1 preferences file, an old install with multiple previous values, and the built-in `notification_permission_asked` flag. Without those, migration can silently create a broken first entry or skip data unexpectedly.

11. **The design’s `EntryRepository.checkIn` does not define all scheduler and stale callback semantics**

    It says `checkIn` runs inside `db.withTransaction { read → RepProgression.checkIn → write if not AlreadyToday }`, keeping concurrent calls per entry safe. But with multiple entries and a single global timer, there is still a race between a check-in for an entry and an active workout for the same entry being started or stopped. Define “checkIn after a run has started” as allowed or forbidden, and specify whether the entry settings snapshot is frozen at start or a late settings edit can alter the in-flight check-in. Add a UI/test case forcing a settings save concurrently with a check-in on the same entry.

12. **The timer/notification title uses entry name, but rename during active run is not handled**

    The spec says the timer screen and notification title become `"<name> · <Phase> · Set n/N"`. If the user renames the entry while a run is active, the notification and timer title should not flicker or show a now-deleted name. Freeze the name in the working snapshot and ensure the screen and notification observe the frozen `entryName` while the run is active. Add a test for rename during active run and a test for delete disabled while running.

13. **The list reorder UX is accessible but not performance-tested**

    Reorder mode with `▲/▼` controls is accessible and simple, but the design does not specify how many entries are expected, whether there is a scroll state, or how the move operation behaves with large lists and the selected row. Ensure the repository translates position moves into a stable contiguous order and the UI doesn’t produce duplicate/removable positions due to lag. Add tests for move of first item up/down and for a list of 100 items to confirm contiguity and ordering.

14. **Single-table Room design without indexes may be too slow for list operations**

    Ordered entry lists are keyed by `position`, but the design does not mention an index or query ordering strategy. With a long list of entries and frequent UI refreshes, simply sorting by `position` may be fine, but future features (search/filter) will need an index. For now, make the model explicit: `position` is unique and non-null, and it is the primary ordering key. Add a Room index and tests to ensure ordering stays stable after delete/move operations.

### Low priority / clarity

15. **The new design amends v1 but does not state which v1 rules are intentionally preserved or changed**

    This revision keeps all domain rules unchanged, but the list of entry-level settings may implicitly change workflow semantics for start, check-in, hold and reset behavior. A compact “delta from v1” table is still valuable: which rules are preserved exactly, which are entry-scoped, and whether migration creates one `Workout` entry or a renamed default entry. This helps prevent regressions when the implementation moves to Room.

16. **List empty state and creation flow are not tied to migration state clearly enough**

    The design says the home screen is the entry list with empty state and FAB. But it does not state the behavior during migration: should the list be blocked, show a loading spinner, or show the empty state while migration runs in the background? Define a loading state and ensure the list is not interactable until migration completes. This matters because the app can start with no entries and a stale migration still pending.

17. **Room + DataStore split is sensible, but the app-level persistence contract should be nailed down**

    The design says DataStore stays only for the global flag, while Room stores everything else. It does not state whether the Room database is used for settings-only data, whether it is single-user app-local only, or how the app is backed up. Add the backup policy and app-local data expectations, especially for install migration and recovered device state.

18. **The spec is missing a concrete test plan for the rename/duplicate/delete dialog UX**

    The list screen and settings screen describe the flows, but the test suite only mentions stepper row + dialog tests and the entry list. Add direct tests for rename validation, delete confirmation content, duplicate naming suffixes, and delete-disabled hint while a workout is running. These are high-value UI regressions and should be in the first pass.
