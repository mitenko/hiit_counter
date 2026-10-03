# REPKIT — Launcher icon (spec revision 23)

Status: chosen by the user in chat on 2026-10-03 ("I like 1") from eight brainstormed concepts.

The launcher icon is the **"+1 ring"**: the timer's ring, 270° filled in teal, with a bold "+1" in the centre. It replaces the old green circle with a play triangle.

- **Adaptive icon** (minSdk 26), `res/drawable/ic_launcher.xml`:
  - background `#12181B`, the app's dark slate;
  - foreground: the ring track `#2A363C`, the arc `#4DB6AC` (stroke 6, round caps, radius 28 on the 108 dp canvas, inside the 66 dp safe zone), and "+1" in `#ECF4F2`.
- **Themed icon** (Android 13+): a monochrome layer. The arc and "+1" are solid; the track is at 35% alpha so the arc still reads when tinted.
- The notification icon is unchanged (`ic_play`).
