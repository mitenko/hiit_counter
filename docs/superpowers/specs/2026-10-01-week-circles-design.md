# REPKIT — Timer Only week circles (spec revision 11)

**Date:** 2026-10-01
**Status:** Approved by the user in chat (2026-10-01).
**Amends:** `2026-09-30-ui-refresh-design.md` (rev 9) §2, the Timer Only tile graph only. Everything else in rev 9 §2 — the card, the name, the ✓, "X× this week", and R5's ≡ drag handle and drag logic — is unchanged.

## 1. Purpose

On the entry list, a **Timer Only** tile's graph no longer draws the 28 flat day-dots. It draws **this calendar week** instead, as seven small circles with the day letters inside them: **M T W T F S S**.

## 2. Entry list tile graph (replaces rev 9 §2's "For a Timer only entry it is 28 day-dots")

- For a **Counter** entry the tile graph is unchanged: a 96 × 32 dp sparkline of the total over the 28-day tile window, with a dot on each point. Its content description stays the plural "N check-ins in the last 4 weeks" (`tile_desc`).
- For a **Timer Only** entry the tile graph is a **row of 7 circles**, about 18 dp each with about 2 dp of spacing (about 138 × 18 dp overall; a larger slot than a Counter tile's is fine).
  - Each circle shows its day letter centred, Monday first: **M T W T F S S**.
  - A day the entry was checked in on is **filled** (`primary`, with the letter in `onPrimary`); other days are **outlined** (a 1 dp `outline` ring, with the letter in `onSurfaceVariant`).
  - The week is the **calendar week, Monday to Sunday, in local time** — the same week as the tile's "X× this week" count and `weekStart` (R6 §3.4). A day later in the week than today is simply outlined/unfilled, since it has no check-ins yet.
  - The row carries **one content description** naming the checked-in days, for example "Checked in Mon, Wed this week", or "No check-ins this week" when none. Each circle also exposes its own checked-in/not-checked-in state so it can be read or tested individually.
  - It must hold up at 200% font scale: the letter's size is pinned in dp (converted to sp once, ignoring the font-scale factor), so it cannot overflow the circle.

## 3. Domain and data

- `domain/History.kt` gains `weekDays(points, now, zone): List<Boolean>` — 7 values, Monday first, true when that local day in the calendar week containing `now` has at least one point. It replaces `dayDots` (28 flat day-dots), which becomes dead code once the Timer Only tile stops drawing them and is removed along with its tests.
- `TileData` gains `week: List<Boolean>` (7 values), filled by `TileLayout.tile` from the same 28-day `recentCheckIns` window the tile already queries — that window always contains the current week, so there is no extra query. `TileData.days` and `TileLayout.dotX` are removed as dead code for the same reason.
- `TileData.count` and `.spark` (the Counter sparkline) are unchanged.

## 4. Strings

- Added: a 7-item array of day letters (`week_day_letters`: M T W T F S S) and a 7-item array of day abbreviations (`week_day_abbrev`: Mon … Sun) for the content description; `week_check_in_desc` ("Checked in %1$s this week") and `week_no_check_in_desc` ("No check-ins this week"); `day_checked_in` / `day_not_checked_in` for each circle's state.
- Unchanged: `tile_desc` (Counter tiles only now), `week_count`.

## 5. Testing

- **Domain:** `weekDays` — a Monday, a Sunday, a check-in from last week excluded, two points on the same day counted once, and a week that crosses the DST fallback.
- **TileLayout:** `week` is populated correctly from the 28-day window.
- **Compose (Robolectric):** a Timer Only tile shows 7 circles tagged `day_<id>_<0..6>` inside a row tagged `week_<id>`, with the letters M T W T F S S in order and the checked-in days reporting a "checked in" state; Counter tiles have no `week_<id>` node and keep their sparkline and `tile_<id>` tag; the existing R5 drag tests and tile tests are unaffected.

## 6. Delivery

Bounded change on `feat/week-circles`, off `main` 9127ff6. One PR, squashed to one commit per the project's usual protocol.
