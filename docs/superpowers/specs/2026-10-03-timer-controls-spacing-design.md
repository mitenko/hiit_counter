# REPKIT — Timer control spacing (spec revision 22)

Status: requested by the user in chat (2026-10-03): "the sound buttons need to be on their own row and spaced between. The < pause > buttons also need more space between them".

Amends rev 7 (cue toggles) and rev 10 §5 (skip buttons).

- **Cue toggles (Sound / Vibration / Voice):**
  - They now have their own row directly under the ✕. They used to share the top strip with it.
  - The row is spread across the screen width with `SpaceBetween`, inside 48 dp side margins, so the gaps are equal and the middle button is centred.
  - They are still hidden at Done.
- **Back / Pause / Skip forward:** 56 dp between the buttons (was 24 dp).
- The ring, stats and name keep their layout in the space below.
