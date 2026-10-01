package com.mitenko.repkit.testutil

import com.mitenko.repkit.platform.CueSpeaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** A [CueSpeaker] that records what it was asked to say. [speak] suspends until [finishUtterance], like an engine still talking. */
class FakeCueSpeaker(available: Boolean = true) : CueSpeaker {
    override val available = MutableStateFlow(available)
    val spoken = mutableListOf<Int>()
    var shutdowns = 0
    private var utterance: CompletableDeferred<Unit>? = null

    override suspend fun speak(number: Int) {
        spoken += number
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
