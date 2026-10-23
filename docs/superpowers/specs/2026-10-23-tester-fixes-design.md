# REPKIT — Closed-test fixes (spec revision 42)

**Date:** 2026-10-23
**Status:** approved by the user (2026-10-23); implemented.
**Source:** Phase 1 tester feedback, `docs/tester-feedback-phase1.md` (themes 3–7).
**Amends:** R2 / rev 24 (the name dialog's error), rev 22 / rev 37 / rev 38 (the timer controls and centre),
rev 9 §3 (the reps column), rev 25 (the Spanish type name).

Not in this revision: the ANR (it needs the Android vitals trace first), the first-run intro (its own spec), and
the layout questions (visible workout settings, a summary before Start).

## 1. The name dialog's empty-name error (Synthory)

- `ui/common/NameDialog` keeps a `touched` flag, false on open and set by the first `onValueChange`.
- The field is red, and "Enter a name" replaces the length counter, only when `touched` and the name is invalid.
  - Before the first edit the supporting text shows the counter as usual (`0/40`).
  - A too-long name is reported the moment it happens, because typing sets `touched`.
- OK stays disabled while the name is invalid, so an untouched empty name can't be confirmed.
- **Rename** opens with the current name, so nothing changes there until the user clears it.

## 2. Labels under the timer controls (Synthory, LingoMia)

- **The cue toggles** get a one-line caption under each 48 dp circle:

  | Toggle | Label |
  | --- | --- |
  | Sound | Sound |
  | Vibration | Vibrate |
  | Voice | Voice |

- **The transport buttons** get the same kind of caption:

  | Button | Label |
  | --- | --- |
  | Skip back | Back (`skip_back`) |
  | Pause / Resume | Pause or Resume (`pause` / `resume`), following the state |
  | Skip forward | Skip (new `skip_label`) |

- **Style:**
  - `labelSmall`, in the timer text colour (`TimerColors.text`) at 70% alpha;
  - 4 dp below the control, centred on it;
  - `maxLines = 1` with an ellipsis.
- **Screen readers:** the captions stay fully visible on screen but are hidden from TalkBack with
  `semantics { hideFromAccessibility() }` on the caption only, never on the control (`clearAndSetSemantics {}` also
  removed them from test lookups). Each control's own content description
  ("Sound on", "Pause") stays the single thing TalkBack reads, so nothing is announced twice.
- **Layout:**
  - The cue row grows by the caption's height, about 16 dp; the 12 dp gap to the workout name stays.
  - The transport row's captions sit in the flexible space above the bottom padding, and the buttons keep their
    56 dp spacing.
  - The ring keeps its size: the extra height comes out of the flexible spacer, and the 320 dp layout test still
    has to pass.
  - On short screens (e.g. 320×640, or large system text) the ring yields height (side = min(0.85·width, the
    height left)) so the captioned controls keep their size; on taller screens nothing changes.
- **When there are no captions:** the cue row is hidden at DONE, as today.

## 3. A clearer prepare phase (Rascacielos)

During PREPARE the ring centre currently shows "GET READY", the upcoming rep count dimmed, and the countdown. That
reads as if the exercise has started. The new layout:

| Line | Content | Style |
| --- | --- | --- |
| Label | **GET READY** alone; in a weight run the weight joins the Next line instead ("Next: 10 · 16 kg"), so the label stays short enough to fit the ring | `PhaseLabel` (titleLarge, bold), neutral colour |
| Big number | **The countdown**: whole seconds while under a minute (`7`), else `m:ss` (`1:30`) | The rep number's style (bold, sized to the ring), neutral colour, not dimmed; `m:ss` uses 0.22 of the ring width instead of 0.3, so it fits inside the ring |
| Below | **Next: 42** (Counter: the first set's reps), **Next: 8 sets** (Timer Only: the sets to go); in a weight run **Next: 10 · 16 kg** | In the countdown's usual place under the number, at `titleMedium`, the timer text colour at 70%; capped to the ring's opening (0.6 of the ring's side) on one line, shrinking its text to fit (down to 12 sp) |

- The countdown's whole-second rounding matches the existing `countdownText` (`TimerText.formatMmSs` rounds up),
  so the big number and the voice and beeps agree.
- **Screen readers:** the merged ring description is unchanged, except that in a weight run PREPARE's ring
  description gains the spoken weight, like WORK and REST.
- **Unchanged:** WORK, REST, COOLDOWN and DONE.
- **Pure parts, test-first:**
  - `TimerUiMapper` gains a `PrepareCentre(countdown: String, next: UiText?)` (or equivalent fields) for PREPARE.
  - `TimerText.formatCountdown(seconds)` returns `"7"` under 60 and `"1:30"` otherwise.

## 4. A heading on the reps column (Synthory)

- The reps column beside the chart (Counter, Reps mode) gets a one-word heading, **Reps**:
  - `labelSmall`, `onSurfaceVariant`, centred over the 56 dp column;
  - tagged `reps_heading`.
- The row is bottom-aligned, so the chart's top drops by the heading's height and its rows stay level with the
  column.
- **Screen readers:** the column's merged description ("Reps per set: …") is unchanged, and the heading is hidden
  from them, since the description already says it. The heading stays visible and is hidden from TalkBack with
  `semantics { hideFromAccessibility() }`.
- **Weight modes** show the prescription card instead of the column, so nothing changes there.

## 5. The Spanish type name (Impostor)

- **The string:** the Spanish `type_check_in` changes from "Solo temporizador" to **"Temporizador"**, which pairs
  with "Contador".
  - The Spanish `info_type` and `info_voice` texts that name the type ("Los de Solo temporizador…", "En los
    entrenamientos de Solo temporizador…") change to "Temporizador" to match.
  - These go through the l10n data file and the generator.
- **Single line:** the New workout dialog's segmented buttons get `maxLines = 1` with an ellipsis on their labels,
  so no language can make the control grow.

## 6. Strings

New translatable strings, which need es / zh-rCN / hi through the l10n data file:

| Key | English |
| --- | --- |
| `cue_label_sound` | Sound |
| `cue_label_vibration` | Vibrate |
| `cue_label_voice` | Voice |
| `skip_label` | Skip |
| `timer_next_reps` (plural) | Next: %1$d |
| `timer_next_sets` (plural) | Next: %1$d sets |
| `reps_heading` | Reps |

Changed: the Spanish `type_check_in`, `info_type` and `info_voice` (§5).

## 7. Testing

- **NameDialog:**
  - opening with an empty name shows no error and the counter;
  - typing then clearing shows "Enter a name";
  - OK is disabled throughout;
  - a value entered in one action (`performTextReplacement`, as a paste or IME commit does) sets `touched` like
    typing: an invalid one shows its error at once and OK stays disabled.
- **Timer:**
  - the cue and transport captions show with their text, Pause switches to Resume;
  - captions are absent from the semantics tree;
  - the 320 dp layout test still passes.
- **Prepare (Compose + mapper):**
  - the big number is the countdown (`7`, and `1:30` for a 90 s prepare);
  - "Next: 42" for Counter, "Next: 8 sets" for Timer Only;
  - WORK still shows the rep number.
- **`TimerText.formatCountdown`:** 0, 7, 59, 60, 90 and 600 seconds.
- **Entry screen:** `reps_heading` shows for a Counter in Reps mode and not in a weight mode.
- **Dialog buttons:** the segmented button labels are single-line, checked through the semantics or measured
  height with the Spanish locale.

## Review notes

A review asked for three clarifications, now in the spec: captions stay visible and are left out of the
accessibility tree only (§2); in a weight run the weight joins the Next line ("Next: 10 · 16 kg") so the label stays
short enough to fit the ring, and the Next line is capped to the ring's opening and shrinks its text to fit (§3); and a pasted or
IME-committed value sets `touched` like typing (§7).
