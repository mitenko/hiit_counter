# REPKIT — Cue Toggles on the Timer Screen (spec revision 7)

**Date:** 2026-09-30
**Status:** Approved by the user in chat (2026-09-30).
**Amends:** `2026-09-29-checkin-voice-design.md` (R4) §5, the Voice cue lifecycle. It also amends R4's "frozen at Start" rule (see `2026-09-24-hiit-counter-design.md` §7.1): **only the cues** become live during a run; name, timing and reps stay frozen in the snapshot. Everything else in R1–R6 still holds.

## 1. Requirement

The timer screen gets three round toggle buttons in a row across the top: **Sound**, **Vibration** and **Voice**.

- Each is a 48 dp circle, centred, 16 dp apart.
- On: a filled circle in the primary colour with the icon.
- Off: an outlined circle with the icon at reduced alpha and a diagonal slash.
- `contentDescription`: "Sound on"/"Sound off", "Vibration on"/"Vibration off", "Voice on"/"Voice off".
- `Role.Switch`, toggleable semantics (`Modifier.toggleable`).
- Test tags `cue_sound`, `cue_vibration`, `cue_voice`.
- Icons: vector drawables `ic_cue_sound`, `ic_cue_vibration`, `ic_cue_voice` (material-icons is not on the classpath; drawables follow the existing `ic_info.xml` pattern — single 24dp path, no on/off variants; the off slash is drawn in Compose).

A toggle **applies immediately to the running workout** and is **saved to the entry's cues**, so the next run keeps it. This is the R4 §5 change: cues are no longer frozen at Start. Only the cues are live; the name, timing and reps stay in the frozen `WorkoutSnapshot`.

## 2. Domain: `TimerController`

- `liveCues: StateFlow<CueConfig?>`.
  - `prepare()` seeds it from `snapshot.cues`.
  - `clearRun()` (called by `stop()`, `cancelPrepare()`, `dismissDone()`, and by `prepare()` itself before assigning the new snapshot) resets it to `null`.
- `setCues(config: CueConfig)` updates `liveCues` only while a run's snapshot exists (i.e. PREPARING, RUNNING or a lingering DONE before the next `clearRun()`); it is a no-op otherwise (notably IDLE).
- `WorkoutSnapshot` is unchanged: its `cues` field stays the value read at Start, used only to seed `liveCues` and as a service-side fallback.

## 3. Service: `TimerService`

- Cue playback reads `controller.liveCues.value`, falling back to `controller.snapshot?.cues` only if `liveCues` is null (defensive; in practice it is non-null whenever a snapshot exists).
- The speaker (voice) lifecycle follows the live cues, not the frozen snapshot: `TimerService` collects `combine(controller.status, controller.liveCues)` (replacing the former `status`-only collector) and calls `VoicePolicy.speakerWanted(status, cues)` on every emission — so toggling Voice on mid-run creates the speaker, and toggling it off shuts it down, without any status change.
- `VoicePolicy.speakerWanted(status: RunStatus, cues: CueConfig?): Boolean` now takes the cues directly instead of a `WorkoutSnapshot?`.
- Voice's "not available" state (R4 §5) still applies: the toggle always saves, whether or not the device has a usable voice; a voice-on run with no engine available just stays silent.
- All existing `TimerService` fixes are unchanged: the DONE-grace wake lock, the `startForeground` re-assert on an already-started service, the PREPARING wake-lock drop, and the start cancel/throw path.

## 4. Persistence: `TimerViewModel`

- `cues: StateFlow<CueConfig?>` exposes `controller.liveCues` directly.
- `toggleSound()`, `toggleVibration()`, `toggleVoice()` each:
  1. compute the new `CueConfig` from the current live value (or the snapshot's, if `liveCues` is momentarily null);
  2. call `controller.setCues(new)` at once, so the running workout reacts immediately;
  3. persist with `repo.setCues(snapshot.entryId, new)`, launched in `viewModelScope`, serialised through a `Mutex` (as `CuesSettingsViewModel` does) and wrapped in `withContext(NonCancellable)` so a write that has started always finishes even if the screen is left;
  4. ignore `EntryNotFound` — the entry may have been deleted during the run.
- With no snapshot (`controller.snapshot == null`), a toggle is a no-op: no controller update, no write.

## 5. UI: `TimerScreen`

- The row sits at the top of the timer screen, overlaid like the existing close button (`Modifier.align(Alignment.TopCenter)`), so it never displaces the entry name, stats, rings or pause button.
- Visible whenever the timer screen itself is showing a run (PREPARING's brief pre-service window has no `TimerUiState` to render, same as before this change) — in practice, for the whole visible run: the PREPARE countdown, WORK, REST, COOLDOWN and paused. Hidden once the phase reaches DONE (`!ui.done`, the same flag that already hides the pause button).
- Uses `Modifier.toggleable(value, onValueChange, role = Role.Switch)` plus `Modifier.semantics { contentDescription = ... }`, all on the single 48 dp circle node — no nested merge needed since the inner `Icon` has `contentDescription = null`.

## 6. Testing

- `TimerController`: prepare seeds `liveCues`; `setCues` updates it while PREPARING or RUNNING; `setCues` is ignored when IDLE; `clearRun` resets it; the snapshot is never mutated.
- `VoicePolicy`: wanted only with the live cues' `voice = true` and status PREPARING/RUNNING; a toggle changes the answer without a status change.
- `TimerViewModel`: a toggle updates `liveCues` and persists; rapid toggles of two different cues both persist; `EntryNotFound` is ignored; no snapshot means no crash and no write.
- `TimerScreen` (Robolectric Compose): the three buttons exist and are at least 48 dp; their content descriptions reflect on/off; a click invokes the callback; they are hidden at DONE.

## 7. Strings (new)

| Key | Text |
|---|---|
| `cue_sound_on` | Sound on |
| `cue_sound_off` | Sound off |
| `cue_vibration_on` | Vibration on |
| `cue_vibration_off` | Vibration off |
| `cue_voice_on` | Voice on |
| `cue_voice_off` | Voice off |
