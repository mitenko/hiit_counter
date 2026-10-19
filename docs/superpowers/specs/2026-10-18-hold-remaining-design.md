# REPKIT — Hold remaining on the entry screen (spec revision 40)

Status: requested by the user, 2026-10-18: "In the info screen for the exercise, capitalize the Streak and Best
and also put in a 'Hold for x more' for info". Amends revision 9 §3 (the streak line). Defaults chosen by Claude
when the user skipped the layout question ("Make a build for install"): title case, and the hold line on its
own line. Either is a one-line change if the user prefers all caps or one line.

## 1. Streak line

`streak_line` reads "Streak 6 · Best 9" (was "best"). Spanish follows ("Racha 6 · Mejor 9"); Chinese and Hindi
have no case.

## 2. Hold for x more

- Under the streak line, in `bodyMedium` and `onSurfaceVariant`, tagged `hold_remaining`: "Hold for 3 more"
  (`plurals/hold_remaining`, with es / zh-rCN / hi).
- `x` = the hold's count minus the check-ins already done on it (`domain/holdRemaining`): the day a hold is
  reached counts as day 1 (rev 16), so a hold for 4 reached today shows 3. It uses the engine's holds
  (`engineConfig`, so weight holds in a weight mode) and the total clamped to min..max, as a check-in does.
- Shown only while the next on-time check-in stays on the hold: hidden off a hold, on the hold's last day,
  with the Hold switch off, and always for Timer only. A missed window restarts the hold (rev 16), so "x" is
  the on-time count.

## 3. Testing

`HoldRemainingTest` (reached today, last day, uncounted, off a hold, switch off, From holds, clamping);
`EntryScreenTest` (the line shows with its text, and is absent off a hold; the streak text).
