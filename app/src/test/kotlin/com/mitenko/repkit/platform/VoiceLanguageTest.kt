package com.mitenko.repkit.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.VoiceLine
import com.mitenko.repkit.domain.model.WeightUnit
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

    @Test
    fun `a weight run's lines read as the load, then just the reps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("16 kilos, 10 reps", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Load(1600, WeightUnit.KG, 10)))
        assertEquals("1 kilo, 1 rep", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Load(100, WeightUnit.KG, 1)))
        assertEquals("22.5 pounds, 8 reps", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Load(2250, WeightUnit.LB, 8)))
        assertEquals("10 reps", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Reps(10)))
        assertEquals("12", VoiceLanguage.spokenLine(context, Locale.ENGLISH, VoiceLine.Count(12)))
    }

    @Test
    fun `the load is said in the voice's language`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val load = VoiceLine.Load(1600, WeightUnit.KG, 10)
        assertEquals("16 kilos, 10 repeticiones", VoiceLanguage.spokenLine(context, Locale.forLanguageTag("es"), load))
        assertEquals("16 公斤，10 次", VoiceLanguage.spokenLine(context, Locale.SIMPLIFIED_CHINESE, load))
        assertEquals("16 किलो, 10 रेप्स", VoiceLanguage.spokenLine(context, hindi, load))
    }

    @Test
    fun `the load plurals resolve es's many and hi's one`() {
        // Batch 1 review finding 4, moved here (batch 3 review finding 3) now spokenLine wraps the
        // resource: es has a "many" category (exact multiples of a million); hi's "one" covers more
        // than just 1. Both are generated resources, not hand-picked wording.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val es = Locale.forLanguageTag("es")
        assertEquals("2000000 kilos, 10 repeticiones", VoiceLanguage.spokenLine(context, es, VoiceLine.Load(200_000_000, WeightUnit.KG, 10)))
        assertEquals("2000000 libras, 10 repeticiones", VoiceLanguage.spokenLine(context, es, VoiceLine.Load(200_000_000, WeightUnit.LB, 10)))
        assertEquals("1 किलो, 1 रेप", VoiceLanguage.spokenLine(context, hindi, VoiceLine.Load(100, WeightUnit.KG, 1)))
        assertEquals("1 पाउंड, 1 रेप", VoiceLanguage.spokenLine(context, hindi, VoiceLine.Load(100, WeightUnit.LB, 1)))
    }
}
