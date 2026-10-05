# REPKIT — Localisation prep (spec revision 24)

Status: Approved by the user in chat (2026-10-03).

Spanish, Chinese (Simplified) and Hindi come next. Before that, everything a user sees or hears comes from resources, and dates, weekday letters and the voice follow the device locale. English looks and sounds as before, with one exception in §6.

Amends v1 §8.3 and §10 (validation messages), §7.6 (notification), R4 §5 (voice language) and rev 9 / rev 15 (date and weekday text).

## 1. Rule

- All user-facing text comes from `strings.xml`: `<string>`, or `<plurals>` for any text with a count.
- `domain/` returns typed values, never English. The UI or the service turns them into text.
- Test tags, log messages and exception messages stay in code. An exception's message is shown to the user only as the untranslated detail in "Couldn't start the workout: …".
- `HardCodedTextGuardTest` scans `ui/` and `domain/` for string literals containing a word. It fails on any literal outside the exempt lines: test tags, logs, preconditions, exceptions, keys and tags. `Routes.kt` and `DateFormats.kt` are exempt as whole files, because their literals are routes and date patterns.

## 2. Typed messages

- `SettingsValidator` returns a `FieldMessage` for each error and hint:
  - `SetsRange(max)`, `WorkRange`, `PhaseRange`, `WorkoutTooLong`;
  - `AtLeastOne`, `ZeroOrMore`, `GreaterThanZero`;
  - `AtLeastFloor`, `AtLeastStartingTotal`, `AtLeastCurrentStreak`;
  - `TooManyHolds(max)`, `DuplicateHold(at)`;
  - the hints `HoldDisabled` and `OutsideFloorCap`;
  - `InTheFuture`.
- `NameCheck` (`Empty`, `TooLong`) is shown by the UI. The repository throws `InvalidEntryName(check)` instead of an English message.
- `ui/common/FieldMessages.kt` maps each message to a resource. This is the one mapper.
- `ui/common/UiText` holds a resource id and its args, a plural, or `Raw` text. Raw text is never translated, for example an exception message.
  - ViewModels and `TimerUiMapper` return `UiText`.
  - Composables resolve it with `resolve()`, which reads `LocalResources`.
- The duplicate name's suffix is the resource `copy_suffix` (" copy"). The repository reads it on each call.
- The edit dialog's "Enter a number" and "Use m:ss or seconds" are resources.

## 3. Timer screen and notification

- `TimerUiMapper` returns its label and TalkBack description as `UiText`.
  - Counts use plurals.
  - "Work, last set" and "Rest, last set next" stay separate strings, because they are worded differently, not just plural forms.
- The notification text is built in `service/NotificationText` from the service's resources.
- `TimerText` keeps only the digit formatting. It uses `Locale.ENGLISH` on purpose: the timer always shows the digits 0–9.

## 4. Dates and weekday letters

- `DateFormats` takes a locale, the device's by default.
  - Every English locale (en-US, en-GB, en-IN and the others) keeps today's day-first patterns with `Locale.ENGLISH` month names: "24 Sep 2026, 05:55", "24 Sep 2026", "12 Sep", "September 2026".
  - Every other language formats the same ICU skeleton (`yMMMdHHmm`, `yMMMd`, `MMMd`, `yMMMM`) with `android.icu.text.DateFormat.getInstanceForSkeleton`. This gives its own order, month names and punctuation, and keeps the 24-hour clock.
- The check-in calendar's narrow weekday names and month titles use the configuration locale.
- The tile circles' `week_day_letters` and `week_day_abbrev` arrays were already resources.

## 5. Voice

- `VoiceLanguage.choose` asks the TTS engine for the app's locale. If that language is missing or unsupported (`LANG_MISSING_DATA` or `LANG_NOT_SUPPORTED`), it asks for English. If neither works, there is no voice, and the "Voice cues aren't available" behaviour is unchanged.
- The spoken count is the `voice_count` plural (`%1$d`), resolved in the voice's own language. A fallback English voice never reads another language's words.
- `VoiceAvailability` uses the same rule.

## 6. English changes

- Counts now use the correct singular, so only a count of 1 changes:
  - a 1-rep set in the timer's TalkBack text: "Work, set 2 of 8, 1 rep" (was "1 reps"), and the same for Rest, Get ready and Done;
  - the check-in announcement: "Set 2: now 1 rep".
- The announcement's two strings became the plurals `reps_changed_set` and `reps_changed_sets`. "1 set updated" can't occur, because one changed set uses the other string.
- Every other English text is unchanged, and the tests assert the old strings.

## 7. Not in scope

- Translations (`values-es`, `values-zh-rCN`, `values-hi`) are written separately.
- Stored data keeps its English text: the v1 migration's and the fallback entry name "Workout".
- Some joiners are still built in code:
  - the list separator ", " in the reps-per-set and week descriptions;
  - the " · " in the chart's point label.
- Decimal input in the settings dialog still uses ".".
