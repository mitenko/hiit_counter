# Repkit — Check-in History, Weekly Count, Tile Graphics and History Screen (spec revision 6)

**Date:** 2026-09-29
**Status:** Approved by the user (2026-09-30).
**Amends:** v1, R2 (`2026-09-25-multi-entry-design.md`), R3 (`2026-09-28-settings-pager-design.md`), R4 (`2026-09-29-checkin-voice-design.md`) and R5 (`2026-09-29-drag-reorder-design.md`). Everything in those still holds unless this document replaces it. v1 §2 and R2 §2 listed history and charts as out of scope; this revision adds them.

## 1. Purpose

- Every check-in is logged, starting with this release. Past check-ins are not reconstructed, apart from one seed point per entry.
- The entry list shows how often you checked in this week, plus a small graphic of the last 4 weeks.
- A History screen charts reps over time for Workouts, and shows a calendar of check-in days for Check-in-only entries.

## 2. Scope

**In scope**
- The `check_in` table (Room 3 → 4), with seeding, cascade on delete, and a write inside the check-in transaction.
- The list byline "… · X× this week" (calendar week, Monday to Sunday, local time) and the tile graphic.
- The History screen, with a 4 weeks / 3 months / All range switch, a Workout line chart with tap-a-point, and a Check-in-only calendar heatmap.
- A "Keep history / Clear history" choice on Reset progress.

**Out of scope**
- Importing past data (for example from the Google Sheet), and editing or deleting single history points.
- Export and sharing.
- Charts that combine several entries.
- Stats beyond the week count, such as averages or trends.

## 3. Data

### 3.1 Room migration 3 → 4
```sql
CREATE TABLE IF NOT EXISTS check_in (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  entry_id INTEGER NOT NULL,
  at INTEGER NOT NULL,
  total INTEGER,
  FOREIGN KEY(entry_id) REFERENCES entry(id) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_check_in_entry_id_at ON check_in (entry_id, at);
INSERT INTO check_in (entry_id, at, total)
  SELECT id, last_check_in, CASE WHEN type = 'WORKOUT' THEN COALESCE(total, starting_total) ELSE NULL END
  FROM entry WHERE last_check_in IS NOT NULL;
```
- `at` is epoch milliseconds, the same format as `entry.last_check_in`.
- `total` is the rep total after that check-in. It is NULL for check-in-only entries.
- `HiitDatabase` moves to version 4. `MIGRATION_3_4` joins `MIGRATIONS`, and `4.json` is exported and committed. `1.json`, `2.json` and `3.json` stay byte-identical. There is still no destructive fallback.
- **Foreign keys.** SQLite enforces foreign keys only when `PRAGMA foreign_keys=ON`. Room turns this on for connections it opens once an entity declares a `ForeignKey`, so `ON DELETE CASCADE` is enforced. `EntryRepository.delete` also deletes the entry's history rows explicitly in the same transaction, so deletion never depends on the pragma.
- The SQL in this section must exactly match what Room generates for the entity. The plan confirms it against the exported `4.json`. If Room's generated SQL differs, for example in how the index is named or quoted, `4.json` wins and this section is updated to match.

### 3.2 Writing history
- `checkIn(id, clock)` inserts one `check_in` row in the same transaction whenever the outcome is not `AlreadyToday`.
  - `at` is the new `lastCheckIn`.
  - `total` is the new total for a Workout, or NULL for a Check-in-only entry.
- Nothing else creates history rows. That includes Current-tab edits, Clear, type switches and duplicate, which starts with no history.
- **Reset progress** becomes `resetProgress(id, clearHistory: Boolean)`. When `clearHistory` is true, the entry's `check_in` rows are deleted in the same transaction as the counter reset.
- **Delete** removes the entry's rows, through the cascade and the explicit delete.

### 3.3 Reading history
- `history(id: Long, since: Instant?): Flow<List<CheckInPoint>>` returns points ordered by `at`, oldest first. `since == null` means all points.
  - `CheckInPoint(at: Instant, total: Int?)` is a pure domain type.
- `EntryRepository.entries` stays as it is. The list gets its week counts and tile data from `recentCheckIns(since: Instant): Flow<Map<Long, List<CheckInPoint>>>`, one query covering every entry, so the list doesn't need a query per row.
- Every call waits for the migration gate, as in R2. The fake repository mirrors all of this.

### 3.4 Pure domain helpers (`domain/History.kt`)
- `weekStart(now: Instant, zone: ZoneId): Instant` returns local Monday 00:00 of the week that contains `now`.
- `weekCount(points, now, zone)` counts the points with `at >= weekStart(now, zone)`.
- `rangeStart(range: HistoryRange, now, zone): Instant?`:
  - `FOUR_WEEKS` → 27 local days before today's start, the same start as `tileWindowStart`, so History's 4 weeks match the tile (user decision, 2026-09-30);
  - `THREE_MONTHS` → 3 calendar months before today's start;
  - `ALL` → null.
- `tileWindowStart(now, zone)` returns the start of the day 27 days before today, so the window covers 28 local days including today.
- `dayDots(points, now, zone): List<Boolean>` returns 28 values, oldest first. A value is true when that local day has at least one point.
- `niceAxis(min: Int, max: Int): Axis(lo, hi, step)` gives rounded bounds with 3–5 ticks. When `min == max`, it pads to `min − 2 .. max + 2`, never below 0.
- `nearestPoint(tapX, xs, thresholdPx): Int?` returns the index of the nearest x within the threshold, or null.
- `calendarMonths(points, start, end, zone): List<MonthGrid>` returns Monday-first week rows of days, each day marked `checkedIn` and `inRange`.

## 4. Screens

### 4.1 Entry list
- **Byline.**
  - Workout: **"Reps N · X× this week"**.
  - Check-in only: **"Streak N · X× this week"**.
  - X is `weekCount`, and it shows even when X is 0.
  - The ✓ stays as it is.
- **Tile graphic.** It is about 72 × 24 dp, sits just before the ≡ handle, and is decorative, so it has no touch target.
  - Workout: a sparkline of `total` over the 28-day tile window, with a dot on each point. One point draws a single dot. No points draw nothing.
  - Check-in only: 28 day-dots in a single row. A filled dot is a checked-in day and a hollow dot is a missed day.
  - Content description: "X check-ins in the last 4 weeks".
- **Refresh.** "This week" and the tile window are recomputed on resume, just as "Checked in today" already is (R2 §7.3).

### 4.2 History screen
- **Route and entry point.** The route is `entry/{id}/history`, opened from a **chart icon** placed before ⚙ in the entry screen's top bar.
  - The icon is 48 dp, uses the vector drawable `ic_history`, and has the content description "History".
  - Navigation uses `dropUnlessResumed` + `launchSingleTop`. A missing entry pops back to the list.
- **Top bar.** ← and "<name> history".
- **Range switch.** A segmented button: **4 weeks · 3 months · All**. It defaults to 4 weeks and is kept with `rememberSaveable`.
- **Workout chart.**
  - A Canvas line chart of `total` against `at`, using the range's points. For All, the x-axis runs from the first point to today; for the other ranges, from the range start to today.
  - Y axis: `niceAxis`, with labels on the left. X axis: 3–5 date labels ("12 Sep"), using `Locale.ENGLISH`.
  - The line and dots use the theme's primary colour.
  - Tapping within 24 dp of a point shows a small label above it with the date and reps ("12 Sep · 62"). Tapping anywhere else clears the label.
- **Check-in-only calendar.**
  - Month blocks in a vertical scroll, oldest first, from the range start (or the first point, for All) to today.
  - Each block has a month title and Monday-first day columns.
  - A checked-in day gets a filled primary circle. Other in-range days are hollow, and out-of-range days are blank.
- **Empty state.** With no points in the range, the screen shows **"No check-ins in this range"**. With no points at all, it shows **"No check-ins yet"**.
- **Screen readers.** Both charts get a summary content description, for example "12 check-ins, from 48 to 62 reps".

### 4.3 Reset progress
- The Current tab's confirmation dialog, "Reset progress?", gains a **Clear history too** checkbox, unchecked by default. Reset then calls `resetProgress(id, clearHistory = checked)`.
- The body text is unchanged.

## 5. Strings (new)

The three count strings marked (plurals) are `<plurals>` resources with `one` and `other` items (user decision, 2026-09-30).

| Key | Text |
|---|---|
| `week_count` | %1$d× this week |
| `reps_week` | Reps %1$d · %2$s |
| `streak_week` | Streak %1$d · %2$s |
| `tile_desc` (plurals) | one: %1$d check-in in the last 4 weeks · other: %1$d check-ins in the last 4 weeks |
| `history` | History |
| `history_title` | %1$s history |
| `range_4w` | 4 weeks |
| `range_3m` | 3 months |
| `range_all` | All |
| `history_empty_range` | No check-ins in this range |
| `history_empty` | No check-ins yet |
| `chart_desc` (plurals) | one: %1$d check-in, from %2$d to %3$d reps · other: %1$d check-ins, from %2$d to %3$d reps |
| `calendar_desc` (plurals) | one: %1$d check-in day · other: %1$d check-in days |
| `clear_history_too` | Clear history too |

## 6. Testing

- **Domain, test-first:**
  - `weekStart`: Monday itself, Sunday night, a time-zone edge and a DST week;
  - `weekCount`, `rangeStart`, `tileWindowStart` and `dayDots`, including the boundary days;
  - `niceAxis`: equal values, small spans and large spans;
  - `nearestPoint`: inside and outside the threshold, and ties;
  - `calendarMonths`: Monday-first layout and in-range marking.
- **Room:**
  - The 3 → 4 `MigrationTestHelper` test, guarded to skip on Windows and run on CI. It checks the seeded rows, including a NULL total for a check-in-only entry and COALESCE on a NULL Workout total.
  - The same SQL run on an in-memory framework database.
  - Check-in history: a check-in inserts one row, and an `AlreadyToday` check-in inserts none.
  - Reset progress with `clearHistory` both true and false.
  - Deleting an entry removes its history, and a duplicate has none.
  - `history` and `recentCheckIns` queries return the right rows in order.
- **ViewModel and Compose (Robolectric):**
  - Byline text for both entry types.
  - The tile content description, and that the tile graphic exists.
  - The chart icon navigates to History.
  - The range switch changes the point set.
  - Tapping a point shows its label.
  - Both empty states.
  - A check-in-only entry shows the calendar and not the chart.
  - The Reset dialog's checkbox is passed through.
- **Device (Pixel 9a):**
  1. Force-stop the app and back it up with `run-as … tar`.
  2. Install over the R5 build.
  3. Pushups and Bridges each show one seeded point and "1× this week" where it applies.
  4. A new check-in adds a point.
  5. The History screen works for both entry types.

## 7. Project conventions

Unchanged:
- the mitenko identity;
- no AI attribution;
- squash to one commit and open a PR;
- ask before pushing;
- TDD subtasks;
- a phone backup before every install.
