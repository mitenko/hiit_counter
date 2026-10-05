# REPKIT — Best streak is read-only (spec revision 29)

Status: Approved by the user in chat (2026-10-05).

Amends R3 §6 (the Current page) and rev 28 rule 5 (the Progression overrides spec).

## 1. Rule
"You can't edit Best Streak." Best streak is a record the app keeps, not a field the user sets: the Current page shows it as a plain value, with its label and ⓘ info tag, but no −/+ buttons and no edit dialog.

Rule 5 (rev 28) is unchanged: **a current streak edited above the best streak still raises the best streak to match**, and the Current page still shows "Best streak raised to N" (`streak_note`) under Current streak. That's the only way the best streak changes now.

## 2. Domain (`domain/ProgressionOverrides.kt`, pure)
- `StreakField` had two values (`BEST`, `CURRENT`); `BEST` was only ever reached from the UI's Best streak stepper, which no longer exists, so it's removed. `StreakField` now names the one streak field the user can still edit: `CURRENT`.
- `resolveStreaks(best, current, edited: StreakField?)` is unchanged in behaviour: with `edited == CURRENT` and `current > best` it raises the best; otherwise nothing moves.
- `SettingsValidator.currentState`'s `bestStreak >= currentStreak` rule (`Field.BEST_STREAK` / `FieldMessage.AtLeastCurrentStreak`) stays as a safety net, even though the UI no longer offers a way to violate it directly.

## 3. UI (`ui/settings/CurrentStateSettings.kt`)
- The Best streak row is now `ValueRow` (new, `ui/common/StepperRow.kt`, next to `StepperRow`): the same card, label and ⓘ layout, but just the value as text — no steppers, no tap-to-edit. Test tags are unchanged: `card_Best streak`, `value_Best streak` (still shows the number), `About Best streak` (the ⓘ).
- `CurrentStateViewModel` no longer has a Best streak edit path: nothing in the UI calls `onChange`/`onChangeNow` with `StreakField.BEST` (it no longer exists). The best value is still part of the typed `Draft` and is still saved through `overwriteCounter`, exactly as before — it just can't be the field the user is editing.

## 4. Strings
`info_best_streak` (`values/`, `values-es`, `values-zh-rCN`, `values-hi`) changes from describing the "can't be lower than current streak" rule to saying it updates on its own:
- en: "Your longest run of on-time check-ins. It updates on its own as your streak grows."
- es: "Tu mayor racha de registros a tiempo. Se actualiza sola a medida que crece tu racha."
- zh-rCN: "你最长的准时打卡连续记录。会随着连续记录增长自动更新。"
- hi: "समय पर चेक-इन की आपकी सबसे लंबी स्ट्रीक। आपकी स्ट्रीक बढ़ने पर यह अपने आप अपडेट होती है।"

## 5. Known edges
- A best streak stored below the current streak (e.g. old data, or a future code path that isn't the Current page) is still caught by the validator as a safety net; there's just no UI control that can produce it any more.
