package com.mitenko.hiitcounter.domain

import kotlin.math.floor

/** Round half up, matching JavaScript Math.round used by the original sheet. */
fun roundHalfUp(x: Double): Int = floor(x + 0.5).toInt()
