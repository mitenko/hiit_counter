package com.mitenko.repkit.testutil

import java.time.Instant

/** A fixed wall clock for TimerController tests that don't look at run summaries' times. */
val fixedWallNow: () -> Instant = { Instant.parse("2026-10-01T17:00:00Z") }
