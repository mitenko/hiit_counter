package com.mitenko.hiitcounter.platform

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * [CueSpeaker] over the platform TextToSpeech (spec R4 §5): `Locale.ENGLISH`,
 * `USAGE_ASSISTANCE_SONIFICATION`, `QUEUE_FLUSH`, and the number passed as its digits ("12"),
 * which the engine says as a word. An init failure or a missing English voice leaves [available]
 * false, and the run continues silently. Create, speak and shut down on Main.
 */
class AndroidCueSpeaker(context: Context) : CueSpeaker {
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    /** Completes with the outcome of initialisation: true once an English voice is ready, false otherwise. */
    val ready = CompletableDeferred<Boolean>()

    /** Utterances still talking. The progress listener runs on a binder thread, hence the concurrent map. */
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private var engine: TextToSpeech? = null
    private var earlyStatus: Int? = null
    private var shutDown = false
    private var nextId = 0

    init {
        val tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
        engine = tts
        // TextToSpeech reports ERROR from inside its constructor when no engine is installed.
        earlyStatus?.let { onInit(it) }
    }

    private fun onInit(status: Int) {
        val tts = engine
        if (tts == null) {
            earlyStatus = status
            return
        }
        if (shutDown || ready.isCompleted) return
        val usable = status == TextToSpeech.SUCCESS && configure(tts)
        if (!usable) Log.w(TAG, "Text-to-speech unavailable (status $status); the voice cue stays silent")
        _available.value = usable
        ready.complete(usable)
    }

    private fun configure(tts: TextToSpeech): Boolean {
        val language = tts.setLanguage(Locale.ENGLISH)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) return false
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
        return true
    }

    override suspend fun speak(number: Int) {
        val tts = engine
        if (tts == null || !_available.value) return
        val id = "rep-${nextId++}"
        suspendCancellableCoroutine<Unit> { cont ->
            pending[id] = cont
            cont.invokeOnCancellation { pending.remove(id) }
            // QUEUE_FLUSH stops the previous utterance, whose onStop resumes its caller.
            if (tts.speak(number.toString(), TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                pending.remove(id)?.resume(Unit)
            }
        }
    }

    private fun finish(utteranceId: String?) {
        pending.remove(utteranceId ?: return)?.resume(Unit)
    }

    override fun shutdown() {
        if (shutDown) return
        shutDown = true
        _available.value = false
        ready.complete(false)
        engine?.stop()
        engine?.shutdown()
        // remove() hands each continuation to exactly one resumer, even if onStop races this drain.
        pending.keys.toList().forEach { id -> pending.remove(id)?.resume(Unit) }
    }

    private companion object {
        const val TAG = "AndroidCueSpeaker"
    }
}
