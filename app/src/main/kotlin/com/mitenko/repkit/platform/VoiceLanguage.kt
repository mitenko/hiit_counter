package com.mitenko.repkit.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.speech.tts.TextToSpeech
import com.mitenko.repkit.R
import java.util.Locale

/** The voice cue's language and words (spec revision 24). */
object VoiceLanguage {
    /**
     * The app's [preferred] locale when the engine can speak it, else [Locale.ENGLISH], else null
     * (no voice). [setLanguage] is `TextToSpeech.setLanguage`, which also selects the language.
     */
    fun choose(preferred: Locale, setLanguage: (Locale) -> Int): Locale? {
        if (usable(setLanguage(preferred))) return preferred
        if (preferred.language == Locale.ENGLISH.language) return null
        return Locale.ENGLISH.takeIf { usable(setLanguage(it)) }
    }

    /**
     * What the voice says for [count]: the `voice_count` plural in the voice's own [locale], so an
     * English fallback voice never reads another language's words. In English it's just the digits.
     *
     * The lint warning is about in-app language switchers and Play language splits. It doesn't
     * apply: the locale here is the device's (whose split is installed) or English (the base
     * `values/`, always present).
     */
    @SuppressLint("AppBundleLocaleChanges")
    fun spokenCount(context: Context, locale: Locale, count: Int): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config).resources.getQuantityString(R.plurals.voice_count, count, count)
    }

    private fun usable(result: Int): Boolean =
        result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
}
