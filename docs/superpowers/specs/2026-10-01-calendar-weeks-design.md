# REPKIT — Continuous calendar weeks (spec revision 15)

Status: requested by the user in chat (2026-10-01): "why does the week have a break like that? keep it all on the same line".

Amends R6 §3.4 / §4.2 (the Timer only check-in calendar, placed in the chart area by rev 9 §3).

## Change
- The calendar no longer draws one block per month, where the 1st of a month always started a new row and a week spanning a month end was split over two rows.
- It now draws **one weekday header (M T W T F S S)**, then **unbroken Monday-first weeks** from the Monday on or before the range start to the Sunday on or after today.
- A **month label** ("October 2026") sits above the first row and above each row that contains the 1st of a month inside the range. The row itself stays whole (e.g. 28 29 30 1 2 3 4).
- Days outside the range are blank. Checked-in days are filled primary circles; other in-range days are hollow, as before.
- The weekday header stays fixed while the weeks scroll. The calendar still opens scrolled to the newest week, and screen readers still hear only the summary ("N check-in days").

## Code
- `domain/History.kt`: `calendarWeeks(points, start, end, zone): List<CalendarWeek>` replaces `calendarMonths`/`MonthGrid`. `CalendarWeek(days: List<CalendarDay> /* 7 */, month: YearMonth?)`.
- `ui/entry/CheckInCalendar.kt` renders it.
