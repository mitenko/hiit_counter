package com.mitenko.repkit.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.speech.tts.TextToSpeech
import com.mitenko.repkit.R
import com.mitenko.repkit.domain.VoiceLine
import com.mitenko.repkit.domain.WeightFormat
import com.mitenko.repkit.domain.model.WeightUnit
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
    fun spokenCount(context: Context, locale: Locale, count: Int): String =
        localized(context, locale).getQuantityString(R.plurals.voice_count, count, count)

    /**
     * What the voice says for [line] (spec rev 26 §4, plan Spec note 57), in the voice's own [locale]:
     * the bare number for [VoiceLine.Count] (as [spokenCount]), "10 reps" for [VoiceLine.Reps], and
     * "16 kilos, 10 reps" for [VoiceLine.Load], with the weight written as on screen ("22.5").
     */
    fun spokenLine(context: Context, locale: Locale, line: VoiceLine): String {
        val res = localized(context, locale)
        return when (line) {
            is VoiceLine.Count -> res.getQuantityString(R.plurals.voice_count, line.number, line.number)
            is VoiceLine.Reps -> res.getQuantityString(R.plurals.reps_count, line.number, line.number)
            is VoiceLine.Load -> res.getString(
                R.string.voice_load,
                res.getQuantityString(line.unit.voicePlural, WeightFormat.pluralCount(line.weight), WeightFormat.format(line.weight)),
                res.getQuantityString(R.plurals.reps_count, line.number, line.number),
            )
        }
    }

    /** [context]'s resources in [locale]; see [spokenCount] on why the lint warning doesn't apply. */
    @SuppressLint("AppBundleLocaleChanges")
    private fun localized(context: Context, locale: Locale): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config).resources
    }

    private val WeightUnit.voicePlural: Int
        get() = when (this) {
            WeightUnit.KG -> R.plurals.voice_weight_kg
            WeightUnit.LB -> R.plurals.voice_weight_lb
        }

    private fun usable(result: Int): Boolean =
        result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
}
