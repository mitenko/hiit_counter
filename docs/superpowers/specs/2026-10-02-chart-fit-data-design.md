# REPKIT — Charts fit their data (spec revision 21)

Status: requested by the user in chat (2026-10-02): "the graph still isn't stretched horizontally". With only a few days of history, the line sat squashed at the right edge of the 4-week axis.

Amends rev 9 §2 (tile sparkline) and §3 (entry chart).

- **Entry screen chart (Counter):**
  - The x-axis starts at the **first check-in shown** in the selected range, instead of the range start. It still ends at today.
  - The range buttons (4 weeks / 3 months / All) still decide which check-ins are shown. Once there's a full range of history, the axis is the range again.
  - With no points shown, the range start stands. "All" already started at the first point.
- **List tile sparkline:** x runs from the first point's day to today (day 27). A lone point today sits in the middle.
- The Timer Only calendar is unchanged.
