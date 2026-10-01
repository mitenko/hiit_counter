# REPKIT — Feature List

**The interval timer that knows how many reps you should do today.**

Most HIIT timers count seconds. REPKIT counts seconds *and* reps: it runs your Tabata intervals, splits today's rep target across your sets, and raises that target every time you show up on time. Miss a day and it eases you back in, never below your floor. One tap to start, nothing to sign up for, and every workout stays on your phone.

## The ad

### Ten years of push-ups. Now in your pocket.

> "I've had this as a push-ups spreadsheet for over a decade and I finally decided to make it into an app. The concept is basic enough: when you check in to do your exercises, the number of reps you do increases by one. If you miss a few days, the reps go down. There's a minimum and a maximum number of reps and a bunch of other features, but that's the real gist of it."
>
> — the maker of REPKIT

No plans to follow. No program to buy. Just one more rep than last time.

REPKIT is that spreadsheet, rebuilt as a proper interval timer. Show up, and your target climbs by one. Skip a few days, and it comes down just enough to get you going again. It never drops below your floor or runs past your cap, so every session is one you can finish.

Press **Start**. The timer counts your sets, tells you how many reps to do in each one, and checks you in for the day. That's the whole routine.

**REPKIT: one more rep than last time.**

## A timer built for the workout, not the stopwatch

REPKIT runs a classic Tabata: prepare, then work and rest for every set, then cooldown. Out of the box that's 10 s to get ready, 8 × 20 s of work with 10 s rests, for exactly 4:00.

- **Every phase is yours to tune:** prepare, sets, work, rest and cooldown, with a live total that tops out at 2 hours.
- **No wasted rest:** there's no rest after your final set.
- **Big, glanceable dual ring:** the centre shows the reps for this set, not a word like "WORK".
- **Skip forward and back:** buttons either side of Pause end a phase early, restart it, or (in its first 2 s) jump to the one before. They work while paused, too.
- **Keeps running with the screen off:** the timer lives in a foreground service with a wake lock, so locking your phone or switching apps never stops a set.
- **Safe stop:** stopping mid-workout asks first, and your check-in for the day is already saved.

## Reps that grow with your consistency

Check in on time and your rep total goes up by one. Come back late and REPKIT takes off a few reps for the time away, so you restart at a level you can actually hit. The rep table splits today's total across your sets, so you always know what this set asks of you.

- **One check-in per day per workout:** tap Check in, or just press Start.
- **Fair window:** check in within 36 hours of your last session to count as on time.
- **Gentle penalty:** about one rep lost for every 19.5 hours past the first day away, less one, and never below your floor.
- **Floor and cap:** defaults of 48 and 72 keep you between "easy restart" and "enough".
- **Plateau hold:** pause at 64 reps for 4 check-ins to consolidate before climbing again. Turn it off any time; your values are kept.
- **Streaks:** your current streak and your best, right on the entry screen.

## One app for every routine

Track as many workouts as you like, each with its own timing, progression, cues and history.

- **Counter entries:** the full package: timer, rep table and a rep total that grows.
- **Timer Only entries:** the same timer without rep counting, for stretching, planks or anything you just want to time. The centre counts your sets down, and checking in keeps your streak alive.
- **Switch types any time:** change an entry between Counter and Timer Only without losing a single value.
- **Manage with ease:** create, rename, duplicate and delete, and drag the ≡ handle to reorder.

## See your progress at a glance

Every check-in is logged, so REPKIT can show you how far you've come without you lifting a finger.

- **Home screen tiles:** each workout shows its name, a ✓ once you've checked in today, and "X× this week" (Monday to Sunday).
- **Tile graphics:** Counter entries get a 28-day sparkline of your rep total; Timer Only entries show this week as seven lettered circles, M T W T F S S, filled on the days you showed up.
- **Chart-centred entry screen:** a line chart of your reps for Counter entries, or a check-in calendar for Timer Only, with a 4 weeks / 3 months / All switch.
- **Reps per set, side by side:** the rep column sits next to the chart, so today's plan is always in view.
- **Streak line:** "Streak N · best M", right above Check in and Start.
- **Clean slate when you want one:** Reset progress returns you to your starting total, and can clear history too.

## Cues you can hear, feel and switch mid-set

Keep your eyes on your form. REPKIT tells you when to go and how many reps to do.

- **Sound:** countdown beeps and a beep at every phase change.
- **Vibration:** a buzz at each phase change, even with the screen off.
- **Voice:** as each work set starts, REPKIT says your reps out loud. For Timer Only entries it counts the sets remaining down.
- **Plays nicely with your music:** cues briefly lower other audio instead of stopping it.
- **Live toggles on the timer screen:** turn Sound, Vibration or Voice on or off mid-workout. The change applies at once and is saved for next time.

## Settings that stay out of your way

Every number in REPKIT can be tuned, and none of it needs a setup wizard. Sensible defaults get you started on the first tap.

- **One swipeable pager per workout:** four icon tabs for Timing, Progression, Current and Cues.
- **Saves itself:** valid changes save automatically, and invalid values are highlighted rather than saved.
- **Built-in explanations:** every setting has an ⓘ tag that says what it does in plain words.
- **Full control of your numbers:** set your starting total, floor, cap, hold, check-in window and penalty rate, or edit your current total, streaks and last check-in directly.
- **Reset to defaults:** one tap restores the standard progression.

## Your data stays yours

Your workouts, reps and history are stored only on your phone. The only thing REPKIT sends is a crash report if the app crashes, through Firebase Crashlytics, so bugs get fixed fast.

- **No account, no sign-up, no ads.**
- **Minimal permissions:** vibration, notifications, what the timer needs to keep running with the screen off, and internet access for crash reports.
- **Dark Material 3 design** that's easy on the eyes at 6 a.m.
- **Runs on Android 8.0 and up.**

## Everything in REPKIT

- [x] Tabata timer: prepare, sets, work, rest, cooldown (default 4:00)
- [x] Dual-ring timer with reps per set in the centre
- [x] Skip forward and back, pause and resume
- [x] Keeps running with the screen off
- [x] Daily check-in on Check in or Start
- [x] Rep total that grows by one per on-time check-in
- [x] Fair 36-hour window and gentle missed-day penalty
- [x] Floor, cap and optional plateau hold
- [x] Current and best streaks
- [x] Unlimited workouts, Counter or Timer Only
- [x] Create, rename, duplicate, delete, drag to reorder
- [x] Weekly count, sparkline and week-circle tiles
- [x] Rep chart and check-in calendar (4 weeks, 3 months, All)
- [x] Sound, vibration and spoken rep cues, toggled live
- [x] Auto-saving settings with an ⓘ explanation on every row
- [x] Works fully offline; your workout data never leaves your phone

**REPKIT. One more rep than last time.**
