# Repkit — Drag-to-Reorder (spec revision 5)

**Date:** 2026-09-29
**Status:** Approved by the user in chat (2026-09-29); written up after implementation, alongside PR B.
**Amends:** `2026-09-25-multi-entry-design.md` (R2) §2 and §7.3, and `2026-09-29-checkin-voice-design.md` (R4) §2. Everything else there still holds.

## 1. Change

The entry list's **Reorder** mode, with its toggle, ▲/▼ buttons and scroll-into-view, is replaced by **drag handles**. R2 §2 listed "drag-and-drop reordering" as out of scope; that line no longer applies.

## 2. Entry list (replaces the R2 §7.3 reorder bullets)

- **Top bar.** Only the app name; the Reorder toggle is gone.
- **Handle.** Every row has a ≡ drag handle at its right edge.
  - 48 dp touch target, vector drawable `ic_drag_handle`.
  - Content description "Reorder <name>", test tag `drag_<id>`.
  - Not announced as a clickable button. Tapping it does nothing and never opens the row.
- **Dragging.** Pressing and dragging the handle lifts the row (background `surfaceVariant`). The other rows animate aside, and the list auto-scrolls near its edges.
  - Only the handle starts a drag. Tapping the row body opens the entry, and swiping the body scrolls the list.
  - The divider moves with its row.
  - Library: `sh.calvin.reorderable:reorderable:3.1.0`, pinned in the version catalog.
- **Persisting the order.**
  - While a drag is in progress, the list reorders locally only.
  - On drop, `delta = finalIndex − startIndex`. If it isn't 0, `EntryListViewModel.move(id, delta)` calls `repo.moveBy(id, delta)` once, inside `withContext(NonCancellable)`. A delta of 0 writes nothing. `EntryNotFound` (the entry was deleted mid-drag) is ignored.
  - After a drop, the dropped order stays on screen until the store re-emits, so the row never snaps back. Rows emitted during a drag are applied once it ends.
- **Accessibility.** Each row offers the custom actions **Move up**, which is missing on the first row, and **Move down**, which is missing on the last row. They call `move(id, ∓1)`.
- **Unchanged.** Check-in-only rows keep "Streak N" and ✓.

## 3. Testing

- **Pure.** The local list-move helper.
- **ViewModel.** One `moveBy` per non-zero move. A zero move writes nothing. A deleted id is ignored.
- **Compose (Robolectric).**
  - There is no Reorder node, and every handle is at least 48 dp.
  - The custom actions appear on the right rows, and the re-rendered UI updates after an action.
  - A drag on the handle persists exactly one move with the right delta. A drag on the row body doesn't reorder.
  - The dropped order holds before the store re-emits.
- **Device.** Drag on the Pixel 9a: no snap-back after a drop, the list auto-scrolls near its edges, and TalkBack offers Move up/down.
