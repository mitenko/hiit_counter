package com.mitenko.hiitcounter.domain.model

/**
 * Spec R4 §3.1. A Workout runs the timer and counts reps. A check-in-only entry records the day
 * and the streaks; its total and hold count never change.
 */
enum class EntryType { WORKOUT, CHECK_IN }
