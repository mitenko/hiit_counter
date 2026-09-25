package com.mitenko.hiitcounter.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.mitenko.hiitcounter.R
import com.mitenko.hiitcounter.domain.CuePattern
import com.mitenko.hiitcounter.domain.CuePatterns
import com.mitenko.hiitcounter.domain.Tone
import com.mitenko.hiitcounter.domain.model.Cue
import com.mitenko.hiitcounter.domain.model.CueConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays cue sounds (ducking other audio) and vibrations — spec §8. Main thread only. */
class CuePlayer(context: Context, private val scope: CoroutineScope) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
    private val loaded = mutableSetOf<Int>()
    private val soundIds: Map<Tone, Int>
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private var abandonJob: Job? = null

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
        if (config.sound && pattern.tones.isNotEmpty()) playTones(pattern)
    }

    private fun playTones(pattern: CuePattern) {
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus denied; skipping sound")
            return
        }
        scope.launch {
            var t = 0L
            for (tone in pattern.tones) {
                delay(tone.atMs - t)
                t = tone.atMs
                val id = soundIds.getValue(tone.tone)
                if (id in loaded) soundPool.play(id, 1f, 1f, 1, 0, 1f) else Log.w(TAG, "Sound $tone not loaded yet; skipped")
            }
        }
        abandonJob?.cancel()
        abandonJob = scope.launch {
            delay(pattern.durationMs + 100)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private fun vibrate(timings: List<Long>) {
        vibrator?.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), -1))
    }

    fun release() {
        abandonJob?.cancel()
        audioManager.abandonAudioFocusRequest(focusRequest)
        soundPool.release()
    }

    private companion object {
        const val TAG = "CuePlayer"
    }
}
