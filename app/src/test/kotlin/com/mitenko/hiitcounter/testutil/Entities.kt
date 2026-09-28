package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.db.EntryEntity

/** A row with the v1 default settings and an untouched counter; vary it with copy(). */
fun testEntity(id: Long = 0, name: String = "Workout", position: Int = 0, total: Int? = null) = EntryEntity(
    id = id, name = name, position = position,
    prepareSec = 10, sets = 8, workSec = 20, restSec = 10, cooldownSec = 0,
    startingTotal = 48, floor = 48, cap = 72, holdAt = 64, holdFor = 4, windowHours = 36, penaltyHoursPerRep = 19.5,
    cueSound = true, cueVibration = true,
    total = total, bestStreak = 0, currentStreak = 0, holdCount = 0, lastCheckIn = null,
)
