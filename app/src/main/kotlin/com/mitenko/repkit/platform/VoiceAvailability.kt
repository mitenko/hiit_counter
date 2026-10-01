package com.mitenko.repkit.platform

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether this device can say the Voice cue (spec R4 §4.7): a text-to-speech engine that
 * initialises with an English voice. Used by the Cues page, outside any run (plan Spec note 4).
 */
interface VoiceAvailability {
    suspend fun check(): Boolean
}

/**
 * Initialises a throwaway [AndroidCueSpeaker], waits for it and shuts it down. Call on Main.
 *
 * The result is cached for the process's lifetime (this device's voice support doesn't change at
 * runtime), so only the first call actually spins up an engine; every Workout pager open after
 * that returns the cached answer. A second caller racing the first one's in-flight check awaits
 * the same [mutex] rather than starting its own engine. A timeout is never cached, so a later call
 * gets another chance.
 *
 * No unit test constructs this class (Robolectric never runs it; the fakes stand in), and there is
 * no seam here for a fake TTS engine, so this caching isn't covered by a test — see the fix report.
 */
class AndroidVoiceAvailability(private val context: Context) : VoiceAvailability {
    private val mutex = Mutex()
    private var cached: Boolean? = null

    override suspend fun check(): Boolean {
        cached?.let { return it }
        mutex.withLock {
            cached?.let { return it }
            val speaker = AndroidCueSpeaker(context)
            val result = try {
                withTimeoutOrNull(INIT_TIMEOUT_MS) { speaker.ready.await() }
            } finally {
                speaker.shutdown()
            }
            if (result != null) cached = result
            return result ?: false
        }
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 5_000L
    }
}
