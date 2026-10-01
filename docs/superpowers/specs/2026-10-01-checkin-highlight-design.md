# REPKIT — Check-in highlight on the reps column (spec revision 12)

**Date:** 2026-10-01
**Status:** Approved by the user in chat (2026-10-01).
**Amends:** `2026-09-30-ui-refresh-design.md` (rev 9) §3, the entry screen's reps column. Everything
else in rev 9 and every later revision still holds.

## 1. Requirement

On a **Workout** entry's screen, after a check-in, the reps-per-set column highlights the cells
whose value changed.

- **Trigger:** the user's own Check in, or the check-in Start performs. Nothing else animates:
  opening the screen, changing the range, resume, or any other emission.
- **Which cells:** compare the per-set rep list from just before the check-in with the list just
  after. Every index whose value differs is highlighted. Usually one set gains +1; after a miss,
  several sets may drop.
- **Timer Only** entries have no reps column, so nothing happens for them.

## 2. Pure helper — `RepsColumnLayout`

- `enum class Change { UP, DOWN }`.
- `fun changedSets(before: List<Int>, after: List<Int>): Map<Int, Change>` — every index whose
  value differs. A size mismatch (the set count changed between the two lists) reports no change
  at all, rather than a misaligned diff.

## 3. `EntryViewModel`

- `data class Highlight(val id: Int, val changes: Map<Int, RepsColumnLayout.Change>)`. `id` is a
  monotonically increasing counter, so the UI's `LaunchedEffect` key changes on every event even
  when the same cell changes the same way twice in a row.
- `val highlight: StateFlow<Highlight?>`, `fun highlightShown()` — clears it. Both are independent
  of `uiState`, so a consumed highlight doesn't force a full state recomposition.
- `onCheckIn()` captures `uiState.value.type` and `uiState.value.reps` ("before") synchronously,
  before launching. The repository call stays exactly as it was — `NonCancellable`, the same
  catch/finally, the same in-flight guards — the only addition is capturing the `CheckInResult`
  it already discarded, and calling the new `applyHighlight` with it on success.
- `startWorkout()` captures "before" from the freshly-fetched `entry.counter.total` /
  `entry.timing.sets` (the same values used to freeze the `WorkoutSnapshot`), and calls
  `applyHighlight` right after its own `repo.checkIn`, before `controller.start(...)`.
  `startWorkout`/`fail` are otherwise byte-for-byte unchanged.
- `applyHighlight(type, before, result)`: no-op for a Timer Only entry or an `AlreadyToday`
  outcome; otherwise redistributes `result.state.total` over `before.size` sets, diffs with
  `changedSets`, and sets `highlight` if anything changed.
- **Start navigates away immediately**, so its highlight may never be seen live. No special
  "has the user left" check is needed: `EntryRoute` collects `highlight` with
  `collectAsStateWithLifecycle`, which pauses while the screen isn't started, so the event just
  waits on the StateFlow until the user comes back to it (Start pushes the timer destination
  without popping Entry off the back stack, so its ViewModel survives). If the screen — and this
  ViewModel — is gone by the time the check-in resolves, there is nothing left to observe the
  StateFlow and it is discarded along with the ViewModel.

## 4. `EntryScreen` / `RepsColumn`

- `EntryScreen` takes `highlight: Highlight? = null` and `onHighlightShown: () -> Unit = {}`,
  threaded through `CentreRow` to `RepsColumn`. The 56 dp column layout, scrolling past 10 sets,
  and the column's summary content description (`reps_per_set_desc`) are unchanged.
- `RepsColumn` captures the incoming `highlight` into a `remember`ed local copy the moment its id
  changes, and calls `onHighlightShown()` in the same `LaunchedEffect` — consuming the event right
  away so a later recomposition with a cleared (null) highlight can't drop an animation already in
  flight. The local copy is what each cell and the announcement actually key off.
- Each cell (`RepCell`) runs two `Animatable`s, keyed on the highlight id (not on the `Change`
  value itself, so the same cell changing the same way twice running still replays):
  - **Scale:** 1 → 1.15 → 1 over ~300 ms (two 150 ms tweens).
  - **Colour:** snaps to full alpha, then fades to zero over ~1200 ms. `primary` for `UP`, `error`
    for `DOWN`, at a fixed highlight alpha.
- **Reduced motion:** `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (read once via
  `LocalContext.current.contentResolver`) skips the scale sub-animation; the colour still snaps to
  full and fades, which also covers the degenerate "can't animate" case — the colour appears for at
  least one frame before it clears, never jumping straight to invisible.
- **Semantics:** while a cell's animation is active, it carries `stateDescription` "increased" or
  "decreased" (test tag stays `rep_<i>`; no separate `_changed` tag).
- **TalkBack:** an invisible (1 dp) `Text` with `liveRegion = Polite`, tagged `reps_announcement`,
  reading `reps_changed_one` ("Set %1$d now %2$d reps", 1-based index + the post-check-in value)
  for one changed cell, or `reps_changed_many` ("%1$d sets changed") for several.

## 5. Strings

- `reps_changed_one` = "Set %1$d now %2$d reps"
- `reps_changed_many` = "%1$d sets changed"

## 6. Out of scope

- No change to the check-in rules, `RepProgression`, or `RepDistributor`.
- No change to Timer Only entries, which never call into any of this (`CentreRow` only calls
  `RepsColumn` for `EntryType.WORKOUT`).
