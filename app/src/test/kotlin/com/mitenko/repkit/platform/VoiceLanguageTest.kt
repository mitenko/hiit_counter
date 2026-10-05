package com.mitenko.repkit.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** Spec revision 24: the voice follows the app's locale and falls back to English. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class VoiceLanguageTest {
    private val hindi = Locale.forLanguageTag("hi-IN")

    /** A fake engine: answers [results] per language and records every locale asked for. */
    private class Engine(private val results: Map<String, Int>) {
        val asked = mutableListOf<Locale>()

        fun setLanguage(locale: Locale): Int {
            asked += locale
            return results[locale.language] ?: TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    @Test
    fun `the app's locale is used when the engine has it`() {
        val engine = Engine(mapOf("hi" to TextToSpeech.LANG_COUNTRY_AVAILABLE, "en" to TextToSpeech.LANG_AVAILABLE))
        assertEquals(hindi, VoiceLanguage.choose(hindi, engine::setLanguage))
        assertEquals(listOf(hindi), engine.asked)
    }

    @Test
    fun `a missing or unsupported language falls back to English`() {
        val missing = Engine(mapOf("hi" to TextToSpeech.LANG_MISSING_DATA, "en" to TextToSpeech.LANG_AVAILABLE))
        assertEquals(Locale.ENGLISH, VoiceLanguage.choose(hindi, missing::setLanguage))
        assertEquals(listOf(hindi, Locale.ENGLISH), missing.asked)

        val unsupported = Engine(mapOf("en" to TextToSpeech.LANG_AVAILABLE))
        assertEquals(Locale.ENGLISH, VoiceLanguage.choose(hindi, unsupported::setLanguage))
    }

    @Test
    fun `no usable language means no voice`() {
        val none = Engine(emptyMap())
        assertNull(VoiceLanguage.choose(hindi, none::setLanguage))
        // English isn't asked twice.
        val englishMissing = Engine(emptyMap())
        assertNull(VoiceLanguage.choose(Locale.US, englishMissing::setLanguage))
        assertEquals(listOf(Locale.US), englishMissing.asked)
    }

    @Test
    fun `the spoken count is the number, from resources in the voice's language`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("12", VoiceLanguage.spokenCount(context, Locale.ENGLISH, 12))
        assertEquals("1", VoiceLanguage.spokenCount(context, Locale.ENGLISH, 1))
    }
}
