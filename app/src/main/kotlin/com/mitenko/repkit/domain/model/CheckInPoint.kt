package com.mitenko.repkit.domain.model

import java.time.Instant

/**
 * One logged check-in (spec R6 §3.3). [total] is the rep total (the level in a weight mode) after that
 * check-in for a Workout, and null for a Timer only entry. [weight] (hundredths of [unit]), [reps] and
 * [unit] are the load a weight-mode Counter check-in recorded (spec rev 26 §9.3), null otherwise:
 * history shows what was recorded, never a later remap.
 */
data class CheckInPoint(
    val at: Instant,
    val total: Int?,
    val weight: Int? = null,
    val reps: Int? = null,
    val unit: WeightUnit? = null,
)
