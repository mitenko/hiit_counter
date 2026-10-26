# REPKIT — Welcome dialog (spec revision 43)

**Date:** 2026-10-26
**Status:** requested by the user (2026-10-26).
**Source:** Phase 1 tester feedback, theme 1 ("Purpose unclear"), `docs/tester-feedback-phase1.md`.
**Amends:** rev 9 §2 / rev 14 §5 (the entry list's top bar).

The user turned down a multi-card intro: the app is straightforward, so one dialog explains its purpose and
its three uses. **Amended 2026-10-26 (same day, before release):** the user asked for the three options (reps,
weights, timer only) to be immediately obvious, so they are now the dialog's headings, matching the New workout
dialog's choices (rev 44).

## 1. The dialog

- `ui/common/WelcomeDialog` is an `AlertDialog` in the ⓘ dialogs' style: a title, scrolling text and OK.
- **Title:** "Welcome to REPKIT" (`welcome_title`).
- **Text,** in this order:
  1. `welcome_intro`: an interval timer that tracks your progress, used one of three ways.
  2. **Reps** (`new_kind_reps`), then `welcome_reps`: for counted exercises; check in and the reps go up; holds and
     missed days.
  3. **Weights** (`new_kind_weights`), then `welcome_weights`: REPKIT moves you up to the next weight, or
     adds reps first and then weight.
  4. **Timer** (`new_kind_timer`), then `welcome_timer`: nothing counted; checking in keeps
     the streak.
  5. `welcome_start`: + makes a workout and offers Reps, Weights or Timer Only; ⚙ on the workout changes it.
- **Headings:** `titleMedium` in the primary colour, 4 dp extra space above, tagged `welcome_heading_<text>`. They reuse
  the New workout dialog's `new_kind_*` labels, so they always match its buttons in every language.
- **Screen readers:** the text is one merged node, read in order.

## 2. When it shows

- **First launch:** the entry list shows it while `welcome_seen` (a new boolean in `app.preferences_pb`) is false or
  absent, so existing installs see it once after updating.
- OK, back or a tap outside dismisses it and sets `welcome_seen`.
- `EntryListViewModel.showWelcome` starts false and stays false until the flag loads, so the dialog never flashes for
  someone who has already seen it.

## 3. The ⓘ in the top bar

- An ⓘ (`ic_info`, 48 dp, tag `about_app`) sits at the left of the entry list's top bar, mirroring the ⚙ on the
  right; the title stays centred.
- TalkBack name: "About REPKIT" (`about` with `app_name`).
- It reopens the same dialog. Dismissing a reopened dialog doesn't touch the flag.

## 4. Strings

New, translated to es / zh-rCN / hi through the l10n data file: `welcome_title`, `welcome_intro`, `welcome_reps`,
`welcome_weights`, `welcome_timer`, `welcome_start` (the headings are rev 44's `new_kind_*`).

## 5. Testing

- `AppPreferences`: `welcomeSeen` defaults to false, and `markWelcomeSeen` sets it.
- `EntryListViewModel`: `showWelcome` is true on a first launch and false after `dismissWelcome`, and it stays false
  once the welcome has been seen.
- `EntryListScreen`:
  - it shows with its title and text, and OK calls `onDismissWelcome`;
  - the three headings read Reps, Weights and Timer;
  - it's absent when `welcome` is false;
  - the About ⓘ reopens it, and OK closes it without calling `onDismissWelcome`.
