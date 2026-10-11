package com.mitenko.repkit.platform

import com.mitenko.repkit.domain.VoiceLine
import kotlinx.coroutines.flow.StateFlow

/**
 * Says a voice line (spec R4 §5, rev 26 §4): a rep count, or a weight run's "16 kilos, 10 reps". [available]
 * is false until the engine is ready with a voice in the app's language or English (spec revision 24), and
 * stays false if it never is; nothing is said then. [speak] replaces any utterance in progress and suspends
 * until this one ends: done, failed or replaced (plan Spec note 2). Main thread only.
 */
interface CueSpeaker {
    val available: StateFlow<Boolean>

    suspend fun speak(line: VoiceLine)

    fun shutdown()
}
