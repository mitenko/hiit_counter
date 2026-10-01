package com.mitenko.repkit.testutil

import com.mitenko.repkit.platform.VoiceAvailability

/** Answers the Cues page's device check with [available]. */
class FakeVoiceAvailability(private val available: Boolean = true) : VoiceAvailability {
    var checks = 0

    override suspend fun check(): Boolean {
        checks++
        return available
    }
}
