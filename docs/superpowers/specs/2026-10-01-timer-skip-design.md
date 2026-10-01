# REPKIT — Timer skip buttons and Timer Only set countdown (spec revision 10)

**Date:** 2026-10-01
**Status:** Approved by the user in chat (2026-10-01).
**Amends:** `2026-09-24-hiit-counter-design.md` (v1) §8 (the engine) and §9.2 (the timer screen);
`2026-09-30-timer-only-design.md` (rev 8) §4.2 (the Timer Only centre and voice). Everything else in
v1 and every later revision still holds.

## 1. Purpose

Two independent additions to the running timer:

1. **⏩ Skip forward / ⏪ Back** buttons either side of Pause, so a set or rest can be cut short or
   restarted without waiting it out.
2. **Timer Only runs count the sets down** instead of up: the centre number and the Voice cue both
   show how many sets are left, including the current one, so "Voice should match the display."

## 2. Scope

**In scope**
- `TabataEngine.skipForward()` / `skipBack()`: jump the running workout to the next or a rebased
  current/previous step, preserving the absolute active-time scheduling (v1 §8) and pause semantics.
- `TimerController.skipForward()` / `skipBack()`: thin, no-op-unless-RUNNING delegation.
- `TimerController.withReps`: for a Timer Only run (`countsReps == false`), the WORK cue's `reps`
  becomes the countdown `sets - set + 1`, not the set number.
- `TimerUiMapper`: for `countsReps == false`, `centerNumber` and the phase descriptions count down.
- `TimerScreen` / `TimerViewModel`: two 48 dp round buttons either side of Pause, wired to the
  controller, visible under the same condition as Pause (`!ui.done`).
- `TimerService.acquireWakeLock`: re-assert the wake lock's timeout from every ticking state, not
  only the first one held, since a skip back can now make the remaining time grow (§6).

**Out of scope**
- Any change to a Counter run's reps display or voice — byte-identical to before.
- The engine's phase sequence, durations or `RepDistributor` — unchanged; a skip only moves where in
  that fixed sequence playback is.
- The notification layout and `TimerText` — already derived fresh from each state, so a jump needs no
  change there.

## 3. Engine (`TabataEngine`, amends v1 §8)

`run()` iterates by step index (`stepIndex`) instead of a plain `for (step in steps)` loop, tracking
for the in-progress step:
- `stepStartMs`: the active-ms value (`activeMs()`) at which the step began:
- `stepStartSec`: the elapsed-seconds value at which it began (sum of the durations of every
  earlier step).

**Jump signal.** `skipForward()` / `skipBack()` write a target step index to a
`MutableStateFlow<Int?>` (`jumpTarget`). The tick wait (`awaitActiveMsOrJump`, replacing
`awaitActiveMs`) watches `jumpTarget` alongside `paused` and returns early — interrupting the
in-progress wait — the moment one is set, whether or not a tick's `remaining` time has elapsed and
whether or not the run is currently paused.

When `run()`'s loop sees a jump:
- `stepIndex` becomes the target.
- Past the last step (`stepIndex >= steps.size`), the loop breaks and falls into the same DONE +
  `Cue.Finished` emission as the natural end — `elapsedSec` is `totalDurationSec`, exactly as today.
- Otherwise, `stepStartMs = activeMs()` (the active time *right now*, not projected from the old
  schedule) and `stepStartSec` = the sum of the durations of every step before the new `stepIndex`.
  This is **not** real elapsed time — it is the elapsed time implied by the new position in the
  workout, so the outer ring and `elapsedSec` reflect where the run now is, not how much real active
  time has actually passed.
- The loop then continues exactly as on normal entry: the target step's first state is emitted
  (`secondsLeft` = its full duration, `elapsedSec = stepStartSec`) and, unless it is PREPARE, its
  `Cue.PhaseStart` fires. Every later tick for that step is scheduled from the new `stepStartMs`, so
  there is no drift, even when the jump lands mid-second.

**skipForward:** target = `stepIndex + 1`.

**skipBack:** if the active time already spent in the current step (`activeMs() - stepStartMs`) is
under 2000 ms **and** `stepIndex > 0`, target = `stepIndex - 1` (the previous step); otherwise target
= `stepIndex` (restart the current step from its beginning).

**While paused:** a jump is processed immediately — it does not wait for `resume()` — because the
interrupt also watches `paused`. The target step's first state is emitted with `paused = true` (the
engine's own `paused.value`, read by `emit()` as always) and its cue still fires. The next tick's
wait then blocks on `!paused` as usual, so no time advances until `resume()`; `activeBaseMs` and
`runningSinceMs` are untouched by the jump itself, so a later resume continues from the new step's
full duration with correct bookkeeping.

**No-ops:** both methods check a private `isRunning` flag (true for the duration of `run()`) and
return immediately if it is false — before `run()` starts, or after it has finished (DONE).

**Threading:** unchanged — single-threaded, Main-only, called from `run()`'s own thread.

## 4. Controller (`TimerController`)

- `skipForward()` / `skipBack()` delegate to the engine and are no-ops unless `status == RUNNING`
  (mirroring `pause()` / `resume()`'s guard style). Nothing else about `isBusy`, the pause timeout,
  `setCues`, or the start-failure handling changes.
- `withReps` (spec R4 §5, amended by rev 8 and now rev 10): for `countsReps == false`, the WORK
  cue's `reps` is `s.sets - s.set + 1` — the countdown including the starting set — instead of the
  set number. A Counter run (`countsReps == true`) is unaffected.

## 5. Timer UI mapper and screen

**`TimerUiMapper`** (amends rev 8 §4.2), for `countsReps == false` only:
- `centerNumber = s.sets - s.set + 1` on WORK, REST and PREPARE (the same countdown the voice now
  speaks). DONE is unchanged — no rep value, same as before.
- Descriptions:
  - WORK: `"Work, N sets to go"`, or `"Work, last set"` when `N == 1`.
  - REST: `"Rest, next set N to go"`, or `"Rest, last set next"` when `N == 1`.
  - PREPARE: `"Get ready, N sets"`.
- `setsText` ("n/N") is unchanged. A Counter run's mapping is byte-identical to before.

**`TimerScreen` / `TimerViewModel` / `TimerRoute`** (amends v1 §9.2): two new 48 dp round icon
buttons sit either side of the existing 72 dp Pause button, in a `Row` under the centre ring:
- ⏪ `skip_back` (content description "Back", `R.drawable.ic_skip_back`, the Material "fast_rewind"
  glyph as a single `<path>`, following the `ic_info` single-path pattern).
- ⏩ `skip_forward` (content description "Skip forward", `R.drawable.ic_skip_forward`, the Material
  "fast_forward" glyph, same pattern).
- Both are visible exactly when Pause is (`!ui.done`), call `TimerViewModel.onSkipBack()` /
  `onSkipForward()`, which delegate to `TimerController.skipBack()` / `skipForward()`. Both work
  while paused (the controller has no paused-gate of its own beyond `status == RUNNING`).
- Test tags `skip_back` / `skip_forward`. Strings `skip_back` = "Back", `skip_forward` = "Skip
  forward".
- Everything else on the timer screen — the cue toggle row, the rings, the centre number, the
  entry name, the close button, keep-screen-on, and the 200 % font layout — is unchanged.

## 6. Service (`TimerService`)

No cue or state-flow logic changes: cues and state already flow to the service exactly as before, so
a jump needs no new wiring. One existing assumption, however, no longer holds:

`acquireWakeLock` used to return early whenever a lock was already held, on the reasoning that a
held lock's timeout — computed from `WakeLockPolicy.timeoutMs(state)` at the moment it was first
acquired — already covered the rest of the run, since `elapsedSec` only ever grew between
acquisitions. A skip back can now move `elapsedSec` **backwards**, so the remaining time the lock
needs to cover can grow after it is already held. `acquireWakeLock` now always calls `acquire(timeoutMs)`
on the (possibly already-held) lock instead of skipping when one exists; `PowerManager.WakeLock.acquire()`
on a non-reference-counted lock simply resets its release deadline, so this keeps the held time in
sync with the current state after both a forward and a backward jump, at the cost of one extra
(very cheap) native call per tick. `WorkoutNotifications.update` needed no equivalent fix — it already
rebuilds its content from the state passed to it on every call.

Every existing `TimerService` fix stays byte-identical: the DONE grace delay before releasing the
wake lock and stopping the service, the `startForeground` re-assert when already `started`, the
wake-lock drop while PREPARING, and the start cancel/throw handling.

## 7. Testing

- **`TabataEngineTest`** (virtual time): skip forward mid-WORK reaches the next REST (or next WORK
  when rest is 0) and fires its `PhaseStart`; skip forward from COOLDOWN, or from the last WORK when
  cooldown is 0, finishes the workout (DONE + `Finished`); skip back after 5 s restarts the phase
  with its full duration; skip back within 2 s goes to the previous phase, or restarts the first step
  if there is none; a jump while paused emits the new step paused with its full duration, holds until
  `resume()`, then ticks down normally; `elapsedSec` after a jump is the sum of the earlier steps;
  skips introduce no drift even when they land mid-second; both methods are no-ops before `run()`
  starts and after DONE.
- **`TimerControllerTest`**: skip calls are no-ops unless RUNNING; a Timer Only run's WORK cue
  carries the countdown (`sets - set + 1`); a Counter run's cue is unchanged.
- **`TimerUiMapperTest`**: Timer Only centre and descriptions count down, with the "last set" wording
  at 1; a Counter run's mapping is unchanged.
- **`TimerScreenTest`**: both skip buttons exist, are at least 48 dp, carry their content
  descriptions, sit either side of Pause by bounds, invoke their callbacks, and are hidden at DONE.
- **`TimerViewModelTest`**: the skip callbacks delegate through to the controller end to end.

## 8. Project conventions

Unchanged from v1 §8 / rev 8 §8: the mitenko identity, no AI attribution, squash to one commit and
open a PR, ask before pushing, TDD.
