package com.mitenko.hiitcounter.domain.model

import java.time.Instant

/**
 * One logged check-in (spec R6 §3.3). [total] is the rep total after that check-in for a Workout,
 * and null for a Timer only entry.
 */
data class CheckInPoint(val at: Instant, val total: Int?)
