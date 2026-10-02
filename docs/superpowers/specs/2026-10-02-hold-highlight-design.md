# REPKIT — Neutral highlight on a hold day (spec revision 20)

Status: requested by the user in chat (2026-10-02). They "didn't see any animations" after a check-in that stayed on a hold, and chose "use a neutral colour for hold".

Amends rev 12 §3, the check-in highlight.

- A check-in can leave every reps cell unchanged because the total sits on an active hold (`holdCount > 0` after the check-in, at `activeHold(total)`). When that happens, every cell flashes in a **neutral colour** (`onSurfaceVariant` at the usual highlight alpha) and fades over the usual ~1.2 s.
- There is **no scale pop**, since no value changed.
- TalkBack announces "Holding at N, D of F" (for example "Holding at 64, 2 of 4"), and each cell reports "holding" while the flash is active.
- Applies to both Check in and the check-in that Start performs.
- Finishing a hold climbs +1, so it highlights UP as before.
- A check-in at the cap with no hold still shows nothing.
