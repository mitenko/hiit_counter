package com.mitenko.repkit.domain.model

/**
 * Spec R4 §3.1, amended by spec revision 8. A Workout runs the timer and counts reps. A Timer
 * only entry (stored value unchanged: `CHECK_IN`) runs the same timer without counting reps —
 * the centre and voice use the current set number — and records the day and the streaks; its
 * total and hold count never change.
 */
enum class EntryType { WORKOUT, CHECK_IN }
