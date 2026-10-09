# REPKIT — Larger timer stats; exits go to the list (spec revision 33)

Status: approved by the user 2026-10-08.

Amends rev 22's controls-spacing spec (`2026-10-03-timer-controls-spacing-design.md`) for the Sets/Elapsed
stat typography, and the original exit rule from the multi-entry design
(`2026-09-25-multi-entry-design.md` §7.2), which had `TimerController.lastEntryId` and
`NavController.exitTimer` pop back to the run's `entry/{id}` when it was still on the back stack.

## 1. Why
The Sets and Elapsed values on the timer screen were too small to read at a glance mid-workout. Separately,
leaving the timer could land on the entry screen the run was started from, which was inconsistent: some
exits (a deleted or stale entry) always fell back to the list already, so the screen you landed on depended
on how the run started and whether its entry was still on the back stack. Every exit should behave the
same way and land on the main screen.

## 2. Larger stats (`ui/timer/TimerScreen.kt`, the `Stat` composable)
- Values: `MaterialTheme.typography.titleLarge` → `headlineMedium`.
- Labels: `MaterialTheme.typography.labelLarge` → `titleSmall`.
- Colours are unchanged (label `Color.LightGray`, value `Color.White`).
- They must still fit side by side at 320 dp width, the smallest phone width the app targets. Verified by
  a Robolectric test at `@Config(qualifiers = "w320dp-h640dp")` using the longest realistic values —
  `"99/99"` for Sets and the formatHms-produced `"01:59:59"` for Elapsed — asserting the two stats don't
  overlap and neither is clipped by the root.

## 3. Every exit goes to the list
Previously, `HiitNavHost.kt` called `nav.exitTimer(controller.lastEntryId)`, and `NavController.exitTimer`
(`ui/navigation/NavActions.kt`) popped back to the matching `entry/{id}` when one was on the back stack,
falling back to `popToEntries()` otherwise.

Now every way out of the timer calls `popToEntries()` directly:
- the ✕ then Stop (confirm dialog, unchanged);
- the system Back then Stop (same confirm dialog via `BackHandler`);
- leaving the DONE screen (no confirmation, spec §9.2);
- the IDLE auto-exit.

All four route through the same place: `TimerRoute`'s `LaunchedEffect(status) { if (status == RunStatus.IDLE) onExit() }`
fires once per run regardless of which action drove the status to IDLE, and `HiitNavHost` wires `onExit` to
`nav.popToEntries()`.

`NavController.exitTimer` is removed (nothing else called it), along with `TimerController.lastEntryId` and
its plumbing (set by `prepare()`, read only by `exitTimer`) — grepping the codebase confirmed no other
caller depended on it. The "Stop workout?" confirm dialog is unchanged.

## 4. Testing
- `ui/timer/TimerScreenTest.kt`: a `w320dp-h640dp` test with `"99/99"`/`"01:59:59"` values, asserting
  `stat_sets` and `stat_elapsed` don't overlap and stay within the root's width.
- `ui/navigation/NavActionsTest.kt`: from a back stack of list → entry → entry-settings → timer,
  `popToEntries()` lands on the list with the entry (and entry-settings) screens popped; a second test
  covers no entry screen being on the back stack at all. The two old `exitTimer` tests (pop to the run's
  entry; pop to the list when the run's entry isn't on the back stack) are removed, since `exitTimer` no
  longer exists.
- `domain/TimerControllerTest.kt`: the `lastEntryId` test is removed; `isBusy`'s comment is updated to
  reference "leaving the timer always lands on the list" instead of `exitTimer`.
