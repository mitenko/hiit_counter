package com.mitenko.repkit.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Says a rep count (spec R4 §5). [available] is false until the engine is ready with an English
 * voice, and stays false if it never is; nothing is said then. [speak] replaces any utterance in
 * progress and suspends until this one ends: done, failed or replaced (plan Spec note 2).
 * Main thread only.
 */
interface CueSpeaker {
    val available: StateFlow<Boolean>

    suspend fun speak(number: Int)

    fun shutdown()
}
