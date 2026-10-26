# REPKIT — New workout kinds (spec revision 44)

**Date:** 2026-10-26
**Status:** requested by the user (2026-10-26).
**Amends:** R4 §4.4 (the New workout dialog's type choice).
**Pairs with:** rev 43 (the welcome dialog), whose headings use these labels.

## 1. The choice

- The New workout dialog's segmented control becomes three choices instead of Counter | Timer Only:

  | Choice | Label (`new_kind_*`) | Creates |
  | --- | --- | --- |
  | Reps (default) | Reps | a Counter in Reps mode (as before) |
  | Weights | Weights | a Counter in **Reps then weight** mode |
  | Timer Only | Timer | a Timer Only workout (as before) |

- `ui/entries/NewWorkoutKind` maps each choice to its `EntryType` and `ProgressMode`. Segments are tagged `kind_<KIND>`.
- Reps is selected each time the dialog opens; the choice survives rotation.
- **Labels:** they're short, so all three stay on one line (`maxLines = 1`, ellipsis). "Timer Only" was cut off
  on the Pixel, next to the selected tick, so the button says **Timer**. The translations:

  | | Spanish | Chinese | Hindi |
  | --- | --- | --- | --- |
  | Reps | Reps | 次数 | रेप्स |
  | Weights | Pesas | 重量 | वज़न |
  | Timer | Tiempo | 计时 | टाइमर |

- **Settings are unchanged:** the Type stays Counter / Timer Only, and Progression still offers Reps / Weight / Reps
  then weight. The three choices are only a shortcut at creation.

## 2. Creating a Weights workout

- `EntryListViewModel.create(name, kind)` creates the entry with `kind.type`. For Weights it then calls
  `switchMode(id, REPS_THEN_WEIGHT, weightUnitDefault)` (the ⚙ › Units default) before reporting the id. This is the
  same switch Settings makes, so the default ladder applies.
- If the switch fails, the workout is left as plain Reps (logged), and Settings can still switch it.
- **Navigation:** like every new workout, it opens with the list beneath it. A Weights workout then also opens its
  Progression page, so the user sets their weights first, and Back from there lands on the workout.

## 3. Testing

- **Screen:** the three labels; Reps is the default; choosing Timer Only or Weights reports that kind.
- **Spanish:** the three segments are the same height (one line), showing Pesas and Tiempo.
- **ViewModel:**
  - Weights creates a Counter in Reps then weight mode, in the default unit, before `onCreated` runs;
  - Reps and Timer Only make no mode switch.
