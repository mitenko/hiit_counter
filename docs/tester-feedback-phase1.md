# REPKIT closed test: Phase 1 tester feedback

**Source:** the testing service's "Test Reports" (Phase 1). Reports for 10 of 12 testers were shared on 2026-10-23
(`feedback.md`, screenshots only). The testers were on an older build, from before the timer stats band (rev 38) and
the colour themes (rev 36/39). Testers are named by the app name they tested under.

## Per tester

| # | Tester | First impressions | Bugs and crashes | Suggestions |
| --- | --- | --- | --- | --- |
| 1 | Arrow domimo | "The UI is nice but I didn't understand the aim of this app" | None | "It would be nice to have a tutorial at the beginning" |
| 2 | Delivery Profit calculator | "I love the idea" | None reported | A tutorial screen explaining what the app is about and what it can do |
| 5 | LingoMia | No crashes or major bugs; worked as expected | None | "Adding more visual guidance and clearer instructions for new users" |
| 6 | Rascacielos: Construye Alto (Spanish) | "Very easy to use and intuitive" | None | At first thought the first countdown was the exercise itself and nearly missed the "get ready" (*prepárate*) text; fine after the first time |
| 7 | ScoreAssist | "Looks good, took me a minute to understand what it was for" | None | "Make it obvious it's a workout app" |
| 8 | Synthory Channel Finder | Created a Counter workout and started a session. Pause changes to a play symbol, but the session controls are unlabelled icons, and the New workout form shows a red empty-name warning as soon as it opens | **Android showed "REPKIT is not responding"** during the session, which ended the test early; cause unknown, completion and saved progress unverified | Short labels or tooltips on the session toggles and skip controls; a summary of the default sets, reps and timing before Start; delay the red name error until the user leaves the field or confirms; label the repeated numbers next to the empty statistics area |
| 9 | Impostor | "The onboarding is pretty simple and intuitive" | No crashes. In **Spanish, the chip showing the training type grows vertically** | "The config of the training should be more visible and not hidden in the config option" |
| 10 | Tieng Viet 1 | "Works smoothly. The user interface is clean, modern" | None | None; "the instructions were very clear" |
| 11 | ToneCue | "I need an explanation of what you're doing" | None reported | Same as first impressions |

Testers 3, 4 and 12 weren't included.

## Themes

| Priority | Theme | Reported by | Proposed response |
| --- | --- | --- | --- |
| 1 | **Purpose unclear; wants a tutorial** | Arrow domimo, Delivery Profit, ScoreAssist, ToneCue, LingoMia (and Synthory, indirectly) | A short first-run intro: what REPKIT is (a HIIT timer whose rep count grows as you check in), how progression works, what the timer controls do |
| 2 | **ANR during a Counter session** | Synthory | Check Play Console → Android vitals → ANRs and Crashlytics for the trace; reproduce on a low-end device |
| 3 | **Unlabelled timer controls** | Synthory, LingoMia | Labels under the sound / vibration / voice toggles; content descriptions are already there for TalkBack |
| 4 | **Prepare looks like the exercise** | Rascacielos | Make the prepare phase clearly distinct (a prominent "Get ready" label and its own colour) |
| 5 | **New workout name error shows at once** | Synthory | Show the empty-name error only after the field loses focus or Create is tapped |
| 6 | **Unlabelled numbers beside an empty chart** | Synthory | A label or empty state for the reps column on a workout with no history |
| 7 | **Spanish type chip wraps** | Impostor | Let "Solo temporizador" fit on one line (shorter label, smaller text, or single-line ellipsis) |
| 8 | **Workout settings hidden behind ⚙** | Impostor | Design question: a visible "Edit workout" entry on the entry screen |
| 9 | **No summary before Start** | Synthory | Design question: show sets × work / rest and total time on the entry screen |

## Positives

"Very easy to use and intuitive", "clean, modern", "works smoothly", "the onboarding is pretty simple and
intuitive". Nine of ten reported no crashes.
