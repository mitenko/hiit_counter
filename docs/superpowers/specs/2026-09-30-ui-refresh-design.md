# REPKIT — UI Refresh: Tiles, Chart-Centred Entry Screen, Button-Style Settings (spec revision 9)

**Date:** 2026-09-30
**Status:** Approved by the user (2026-09-30). It comes from `docs/feedback.md`, and the three decisions it depends on were confirmed in chat on 2026-09-30.
**Amends:** `2026-09-29-history-design.md` (R6). §4.1 is rewritten and §4.2 is replaced, while R6 §3 (data) stands as written. It also amends R2 §7.3–7.5, R3 §4, R5 and the Timer only spec (rev 8, `2026-09-30-timer-only-design.md`). It ships in one PR with R6's data layer.

## 1. Purpose

- The list becomes rounded tiles. Each tile shows the week's check-ins and a small graph instead of the rep total.
- The entry screen puts progress in the centre: a graph for Workouts and a check-in calendar for Timer only entries. The per-set reps move to a narrow column on the right. The separate History screen from R6 §4.2 is **dropped**.
- Settings look friendlier: every option is a rounded button, and the pager tabs are icons.

## 2. Entry list (replaces R6 §4.1 and R2 §7.3's row layout)

- **Top bar:** "REPKIT" is **centred**. Nothing else is in the bar, because R5 removed Reorder.
- **Tiles:** each entry is a **rounded-rectangle card**.
  - It has a 16 dp corner radius and the `surfaceContainer` colour.
  - Cards sit 16 dp in from the sides and 8 dp apart, with no dividers. R5's divider is removed.
  - Each card is at least 72 dp tall and contains:
    - **left:** the name (titleMedium), with a ✓ after it when the entry is checked in today;
    - **under the name:** **"X× this week"**, where X is `weekCount` from R6 §3.4. **The rep total and streak are no longer shown on the tile.**
    - **right, before the ≡ handle:** the **tile graph**, about 96 × 32 dp. For a Workout it is a sparkline of the total over the 28-day tile window with check-in dots. For a Timer only entry it is 28 day-dots. The content description is "N check-ins in the last 4 weeks", using a plural.
    - **far right:** R5's ≡ drag handle, unchanged in behaviour. The whole card lifts while it is dragged.
  - Tapping the card body opens the entry.
- **Unchanged:** R5's drag behaviour (local order, id-set resync, one write on drop, accessibility actions) and R2's loading and empty states.

## 3. Entry screen (replaces R6 §4.2 and R2/R4's table layout)

- **Top bar:** ←, the entry name, and ⚙. There is **no chart icon**, because there is no History screen.
- **Body**, top to bottom:
  1. **Range switch:** a segmented button, **4 weeks · 3 months · All**. It defaults to 4 weeks and is kept in `rememberSaveable`. The ranges follow R6 §3.4, where 4 weeks means 28 days.
  2. **Centre row:**
     - **The chart** takes the remaining width and about 240 dp of height.
       - **Workout:** R6's line chart of total over time, with niceAxis y labels, date labels in `Locale.ENGLISH`, and tap-a-point within 24 dp showing "12 Sep · 62". Tapping elsewhere on the chart clears the label.
       - **Timer only:** R6's month-block check-in calendar, scrolling vertically inside the chart area.
       - With no points in the range it shows "No check-ins in this range". With no points at all it shows "No check-ins yet".
     - **Reps column** (Workouts only), about 56 dp wide, on the right: the per-set rep counts stacked top to bottom (for example 8, 8, 8, 8, 8, 7, 7, 7), in the style of the existing table cells. It scrolls if there are more than 10 sets. Timer only entries have no reps column, and the chart takes the full width.
  3. **Streak line** (all types), centred under the chart: "Streak N · best M". This replaces the Best/Curr CI Streak rows.
  4. **Buttons:** R4's **Check in** (outlined) and **Start** (filled) row, unchanged. Timer only entries have both, as in rev 8.
- **Removed rows:** Total Reps, Last Check In, Best CI Streak, Curr CI Streak and Today. The "Checked in today" line also goes, because "Checked in ✓" on the button carries that state.
- **Screen readers:** the chart and calendar keep R6's summary descriptions. The reps column gets "Reps per set: 8, 8, 8, …".
- **Unchanged:** the `loaded` gating (R4 final fix), the in-flight guards, NonCancellable Check in, and active-run routing.

## 4. Settings styling (amends R3 §4, R2 §7.5)

- **Entry Settings:** the rows become **rounded-rectangle buttons**.
  - This covers Type, Timing, Progression, Current State, Cues, Rename and Duplicate.
  - Each is a full-width card with a 16 dp radius and the `surfaceContainer` colour, at least 56 dp tall, with 8 dp gaps between cards.
  - Delete uses the same shape with the `errorContainer` colour.
  - Tap targets, ⓘ tags, the busy rules and dialogs are unchanged.
- **Settings pages:** each stepper row and switch row is wrapped in the same rounded card, so each option reads as one friendly block. Steppers, tap-to-edit, ⓘ and auto-save are unchanged.
- **Pager tabs:** the tabs show **icons only**, as vector drawables, because material-icons isn't on the classpath.
  - The icons are Timing ⏱ `ic_tab_timing`, Progression 📈 `ic_tab_progression`, Current 🎯 `ic_tab_current` and Cues 🔔 `ic_tab_cues`.
  - Each tab has a content description with its old name ("Timing", …) and a 48 dp minimum target.
  - The selected tab uses the primary colour, with the indicator unchanged.

## 5. Strings

- **Removed** (grep for them first; they are no longer used): `reps_week`, `streak_week` and the History screen strings from R6 §5 (`history`, `history_title`), plus any table-row labels the entry screen no longer uses (`total_reps`, `last_check_in`, `best_streak`, `current_streak` or whatever they are named), if nothing else references them.
- **Added:**
  - `streak_line` "Streak %1$d · best %2$d";
  - `reps_per_set_desc` "Reps per set: %1$s";
  - `tab_timing` "Timing", `tab_progression` "Progression", `tab_current` "Current", `tab_cues` "Cues", used as content descriptions.
- **Kept from R6:** `week_count`, `tile_desc` (plural), `range_*`, `history_empty*`, `chart_desc` (plural), `calendar_desc` (plural) and `clear_history_too`.

## 6. Testing (replaces R6 §6's History-screen items; R6's domain and Room tests stand)

- **Pure:** layout helpers for the reps column, including how many rows fit before it scrolls.
- **Compose (Robolectric):**
  - **List:** the title is centred (its bounds are centred in the bar); the tile shows the name, ✓ and "X× this week", and no "Reps"; the tile graph exists with its description; the drag handle still works, with R5's tests kept.
  - **Entry screen:**
    - A Workout shows the range switch, the chart, the reps column with the right values, the streak line, and Check in plus Start.
    - A Timer only entry shows the calendar, no reps column, and both buttons.
    - None of the removed rows exist.
    - The range switch changes the point set.
    - Tap-a-point shows its label, and both empty states appear when they should.
  - **Settings:**
    - The Entry Settings options are cards at least 56 dp tall.
    - The pager tabs have icons, their content descriptions, and no text nodes. The existing tab tests are rewritten to find tabs by description.
- **Device (Pixel 9a):** back up first, then install over the current build. Check the list tiles, the Workout entry's chart and reps column, the Timer only entry's calendar, the settings cards and the icon tabs.

## 7. Delivery

- This lands together with R6's data layer in one PR: the check_in table, the 3 → 4 migration, seeding, the week count and Clear history too. The R6 plan (`docs/superpowers/plans/2026-09-30-history.md`) is revised so that its screen tasks build this spec instead of R6 §4.1–4.2, and its data tasks stay as written.
- Project conventions are unchanged: the mitenko identity, no AI attribution, squash to one commit and open a PR, ask before pushing, TDD subtasks, and a phone backup before every install.
