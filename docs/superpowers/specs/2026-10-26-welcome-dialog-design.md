# REPKIT — Welcome dialog (spec revision 43)

**Date:** 2026-10-26
**Status:** requested by the user (2026-10-26).
**Source:** Phase 1 tester feedback, theme 1 ("Purpose unclear"), `docs/tester-feedback-phase1.md`.
**Amends:** rev 9 §2 / rev 14 §5 (the entry list's top bar).

The user turned down a multi-card intro: the app is straightforward, so one dialog explains its purpose and
the two kinds of workout.

## 1. The dialog

- `ui/common/WelcomeDialog` is an `AlertDialog` in the ⓘ dialogs' style: a title, scrolling text and OK.
- **Title:** "Welcome to REPKIT" (`welcome_title`).
- **Text,** in this order:
  1. `welcome_intro`: REPKIT is an interval timer that builds up your reps over time.
  2. A `titleSmall` heading with `type_workout` ("Counter"), then `welcome_counter`: for counted exercises; the timer
     runs the sets and shows each set's reps; check in and the reps go up; holds and missed days.
  3. A `titleSmall` heading with `type_check_in` ("Timer Only"), then `welcome_timer`: for timed exercises; the
     same timer, nothing counted; checking in keeps the streak.
  4. `welcome_start`: + makes a workout, ⚙ changes it, every setting has an ⓘ.
- The headings reuse the type names, so they always match the New workout dialog in every language.
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

New, translated to es / zh-rCN / hi through the l10n data file: `welcome_title`, `welcome_intro`, `welcome_counter`,
`welcome_timer`, `welcome_start`.

## 5. Testing

- `AppPreferences`: `welcomeSeen` defaults to false, and `markWelcomeSeen` sets it.
- `EntryListViewModel`: `showWelcome` is true on a first launch and false after `dismissWelcome`, and it stays false
  once the welcome has been seen.
- `EntryListScreen`:
  - it shows with its title and text, and OK calls `onDismissWelcome`;
  - it's absent when `welcome` is false;
  - the About ⓘ reopens it, and OK closes it without calling `onDismissWelcome`.
