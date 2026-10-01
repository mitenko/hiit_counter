package com.mitenko.repkit.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.CuePattern
import com.mitenko.repkit.domain.CuePatterns
import com.mitenko.repkit.domain.Tone
import com.mitenko.repkit.domain.model.Cue
import com.mitenko.repkit.domain.model.CueConfig
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.platform.CueSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays cue sounds, the voice and vibrations — spec §8, R4 §5. Main thread only.
 *
 * The beeps and the voice share one transient ducking focus request, so music is lowered while
 * either plays. Focus is abandoned once the last tone's hold has ended **and** the utterance has
 * ended, or [SPEECH_TIMEOUT_MS] after speech started. Speech never blocks the caller: it runs in
 * [scope], so the engine's timing is untouched.
 */
class CuePlayer(
    context: Context,
    private val scope: CoroutineScope,
    focusOverride: CueFocus? = null,
) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
    private val loaded = mutableSetOf<Int>()
    private val soundIds: Map<Tone, Int>
    private val focus: CueFocus = focusOverride ?: AndroidCueFocus(context, attributes)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private var tonesJob: Job? = null
    private var speechJob: Job? = null
    private var tonesHold = false
    private var speechHold = false

    /** The run's speaker (spec R4 §5). TimerService sets it only for a voice run; null means no voice. */
    var speaker: CueSpeaker? = null

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        soundIds = mapOf(
            Tone.SHORT to soundPool.load(context, R.raw.tone_short, 1),
            Tone.LONG to soundPool.load(context, R.raw.tone_long, 1),
        )
    }

    fun play(cue: Cue, config: CueConfig) {
        val pattern = CuePatterns.forCue(cue)
        if (config.vibration) pattern.vibration?.let(::vibrate)
        val tones = config.sound && pattern.tones.isNotEmpty()
        val reps = spokenReps(cue, config)
        if (!tones && reps == null) return
        if (!focus.request()) {
            Log.w(TAG, "Audio focus denied; skipping sound")
            return
        }
        if (tones) playTones(pattern)
        // The number follows the beep, so the two never overlap (plan Spec note 3).
        if (reps != null) say(reps, afterMs = if (tones) pattern.durationMs else 0L)
    }

    /** The reps to say: only at a WORK start, with the voice on and a speaker that is ready (spec R4 §5). */
    private fun spokenReps(cue: Cue, config: CueConfig): Int? {
        if (!config.voice || cue !is Cue.PhaseStart || cue.phase != Phase.WORK) return null
        if (speaker?.available?.value != true) return null
        return cue.reps
    }

    private fun playTones(pattern: CuePattern) {
        scope.launch {
            var t = 0L
            for (tone in pattern.tones) {
                delay(tone.atMs - t)
                t = tone.atMs
                val id = soundIds.getValue(tone.tone)
                if (id in loaded) soundPool.play(id, 1f, 1f, 1, 0, 1f) else Log.w(TAG, "Sound $tone not loaded yet; skipped")
            }
        }
        tonesHold = true
        tonesJob?.cancel()
        tonesJob = scope.launch {
            delay(pattern.durationMs + 100)
            tonesHold = false
            abandonIfIdle()
        }
    }

    private fun say(reps: Int, afterMs: Long) {
        val s = speaker ?: return
        speechHold = true
        speechJob?.cancel()
        speechJob = scope.launch {
            delay(afterMs)
            withTimeoutOrNull(SPEECH_TIMEOUT_MS) { s.speak(reps) }
            speechHold = false
            abandonIfIdle()
        }
    }

    private fun abandonIfIdle() {
        if (!tonesHold && !speechHold) focus.abandon()
    }

    private fun vibrate(timings: List<Long>) {
        vibrator?.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), -1))
    }

    fun release() {
        tonesJob?.cancel()
        speechJob?.cancel()
        tonesHold = false
        speechHold = false
        focus.abandon()
        soundPool.release()
    }

    companion object {
        /** Focus is released at the latest this long after speech starts (spec R4 §5). */
        const val SPEECH_TIMEOUT_MS = 3_000L
        private const val TAG = "CuePlayer"
    }
}
