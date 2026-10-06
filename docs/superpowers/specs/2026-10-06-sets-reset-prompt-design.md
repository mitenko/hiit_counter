# REPKIT — Reset prompt on a Sets change (spec revision 32)

Status: Approved by the user 2026-10-06 (option B, chosen over "keep reps per set" and a three-way dialog).

Amends R3 §6.4 (Reset progress) and the Timing page (R3 §6).

## 1. Why
A Counter entry's total is spread over its sets, so changing Sets changes the reps per set. The app offers to start the reps again from the starting total, without forcing it.

## 2. Baseline
The saved `timing.sets` when the Timing page was last entered (including when settings open on it). Answering the prompt, either way, makes the current saved sets the new baseline.

## 3. Trigger
The user leaves the Timing page: a page change away from Timing (tab tap or swipe), or exiting settings (back, ←). The prompt shows only after the pending Timing save has flushed, and only when the saved sets differ from the baseline.

## 4. Dialog
The same AlertDialog style as Current's Reset progress dialog:
- Title "Sets changed" (`sets_changed_title`).
- Body "You changed sets from %1$d to %2$d. Reset progress so your reps start again from the starting total?" (`sets_changed_body`, baseline and saved sets).
- A "Clear history too" checkbox (`clear_history_too`), unchecked each time, the same toggleable row as Reset progress.
- Confirm "Reset progress" (`reset_progress`); dismiss "Keep progress" (`keep_progress`).

## 5. Answers
- **Reset progress:** the same reset as Current's Reset progress (§6.4): `resetProgress(entryId, clearHistory)` after the pending Timing write.
- **Keep progress:** nothing changes. Tapping outside or back counts as Keep progress.
- **On a page change** the dialog shows over the new page.
- **On exit** the screen leaves only after the answer; Keep progress (or a dismissal) then continues the exit.

## 6. Never
- For Timer only entries (`EntryType.CHECK_IN`).
- When Sets ended equal to the baseline (8 → 10 → 8).
- Twice for the same change.

## 7. Implementation
`TimingSettingsViewModel` owns the baseline and the pending prompt (`setsPrompt`, `SetsChange(from, to)`); `SettingsPagerRoute` reports the visible page (`pageShown`), routes exits through `exit(onDone)` and shows `SetsChangedDialog`. The checks and the reset run behind the page's `AutoSaver.exclusive`, so they follow the flushed write.
