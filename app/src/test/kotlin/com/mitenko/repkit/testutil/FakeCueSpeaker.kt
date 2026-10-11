package com.mitenko.repkit.testutil

import com.mitenko.repkit.domain.VoiceLine
import com.mitenko.repkit.platform.CueSpeaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** A [CueSpeaker] that records what it was asked to say. [speak] suspends until [finishUtterance], like an engine still talking. */
class FakeCueSpeaker(available: Boolean = true) : CueSpeaker {
    override val available = MutableStateFlow(available)

    /** Every line asked for, in order. */
    val lines = mutableListOf<VoiceLine>()

    /** Each line's number: the reps (or the sets to go), which is all a Reps or Timer only run says. */
    val spoken: List<Int> get() = lines.map { it.number }

    var shutdowns = 0
    private var utterance: CompletableDeferred<Unit>? = null

    override suspend fun speak(line: VoiceLine) {
        lines += line
        val done = CompletableDeferred<Unit>()
        utterance = done
        done.await()
    }

    fun finishUtterance() {
        utterance?.complete(Unit)
    }

    override fun shutdown() {
        shutdowns++
        available.value = false
    }
}
