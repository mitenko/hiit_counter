# REPKIT — Full-width tile graphs (spec revision 19)

Status: requested by the user in chat (2026-10-02): "the graphs need to expand horizontally to fill the width".

Amends rev 9 §2 and rev 11 §2 (the list tile graphic).

- On each list tile's second line, "X× this week" takes its natural width. The tile graph fills the rest of the line, starting 12 dp after the text and ending 8 dp before the ≡ handle.
- **Counter:** the sparkline is full width and stays 32 dp tall. It was a fixed 96 × 32 dp.
- **Timer Only:** the 7 lettered day circles stay 18 dp and spread evenly across the same width.
- The entry screen's chart already filled its row beside the reps column and is unchanged.
