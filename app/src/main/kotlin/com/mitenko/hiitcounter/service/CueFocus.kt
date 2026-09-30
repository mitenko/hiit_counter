package com.mitenko.hiitcounter.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** The cues' audio focus (spec §8, R4 §5): one transient, ducking request shared by the beeps and the voice. */
interface CueFocus {
    /** True when focus was granted; the caller plays nothing otherwise. */
    fun request(): Boolean

    fun abandon()
}

class AndroidCueFocus(context: Context, attributes: AudioAttributes) : CueFocus {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    override fun request(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
