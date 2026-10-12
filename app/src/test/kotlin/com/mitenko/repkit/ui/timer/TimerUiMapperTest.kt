package com.mitenko.repkit.ui.timer

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.model.Phase
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.TimerState
import com.mitenko.repkit.domain.model.WeightUnit
import com.mitenko.repkit.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** Robolectric, so each description and label is checked as the English text it resolves to (spec revision 24). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimerUiMapperTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    private fun UiText?.en(): String? = this?.resolve(res)

    private fun state(phase: Phase, set: Int, left: Int, duration: Int, reps: Int = 8) = TimerState(
        phase = phase, set = set, sets = 8, phaseSecondsLeft = left, phaseDurationSec = duration,
        elapsedSec = 50, totalDurationSec = 240, repsThisSet = reps, totalReps = 65, paused = false,
    )

    @Test
    fun `work shows bright reps without a label`() {
        val ui = TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20), "Burpees")
        assertNull(ui.label.en())
        assertEquals(8, ui.centerNumber)
        assertFalse(ui.centerDimmed)
        assertEquals(PhaseTone.WORK, ui.tone)
        assertEquals("00:15", ui.countdownText)
        assertEquals("2/8", ui.setsText)
        assertEquals("00:50", ui.elapsedText)
        assertEquals("Sets 2 of 8", ui.setsDescription.en())
        assertEquals("Elapsed 50 seconds", ui.elapsedDescription.en())
        assertEquals(0.75f, ui.innerProgress, 1e-6f)
        assertEquals((1 + 5f / 20f) / 8f, ui.outerProgress, 1e-6f)
        assertEquals("Work, set 2 of 8, 8 reps", ui.description.en())
    }

    @Test
    fun `rest shows label and dimmed upcoming reps`() {
        val ui = TimerUiMapper.map(state(Phase.REST, set = 3, left = 10, duration = 10, reps = 9), "Burpees")
        assertEquals("REST", ui.label.en())
        assertEquals(9, ui.centerNumber)
        assertTrue(ui.centerDimmed)
        assertEquals(PhaseTone.REST, ui.tone)
        assertEquals(2f / 8f, ui.outerProgress, 1e-6f)
    }

    @Test
    fun `prepare cooldown and done`() {
        val prep = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10, reps = 9), "Burpees")
        assertEquals("GET READY", prep.label.en())
        assertEquals(9, prep.centerNumber)
        assertTrue(prep.centerDimmed)
        assertEquals(0f, prep.outerProgress, 1e-6f)

        val cool = TimerUiMapper.map(state(Phase.COOLDOWN, set = 8, left = 5, duration = 30, reps = 0), "Burpees")
        assertEquals("COOLDOWN", cool.label.en())
        assertNull(cool.centerNumber)
        assertEquals(1f, cool.outerProgress, 1e-6f)

        val done = TimerUiMapper.map(state(Phase.DONE, set = 8, left = 0, duration = 0, reps = 0), "Burpees")
        assertEquals("DONE", done.label.en())
        assertEquals(65, done.centerNumber)
        assertTrue(done.done)
        assertEquals("", done.countdownText)
    }

    @Test
    fun `the frozen entry name is carried through every phase`() {
        Phase.entries.forEach { phase ->
            assertEquals("Kettlebell Lunges", TimerUiMapper.map(state(phase, set = 1, left = 5, duration = 10), "Kettlebell Lunges").entryName)
        }
    }

    @Test
    fun `a Timer only run counts the sets down, on work and between sets`() {
        // sets = 8 (see state()); set 3 WORK means 6 sets still to go, including this one.
        val work = TimerUiMapper.map(state(Phase.WORK, set = 3, left = 15, duration = 20), "Stretch", countsReps = false)
        assertEquals(6, work.centerNumber)
        assertFalse(work.centerDimmed)
        assertEquals("Work, 6 sets to go", work.description.en())

        // set 4 upcoming means 5 to go, including it.
        val rest = TimerUiMapper.map(state(Phase.REST, set = 4, left = 10, duration = 10), "Stretch", countsReps = false)
        assertEquals(5, rest.centerNumber)
        assertTrue(rest.centerDimmed)
        assertEquals("Rest, next set 5 to go", rest.description.en())

        val prepare = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10), "Stretch", countsReps = false)
        assertEquals(8, prepare.centerNumber)
        assertEquals("Get ready, 8 sets", prepare.description.en())
    }

    @Test
    fun `a Timer only run's last set shows 1 and the singular wording`() {
        val work = TimerUiMapper.map(state(Phase.WORK, set = 8, left = 15, duration = 20), "Stretch", countsReps = false)
        assertEquals(1, work.centerNumber)
        assertEquals("Work, last set", work.description.en())

        val rest = TimerUiMapper.map(state(Phase.REST, set = 8, left = 10, duration = 10), "Stretch", countsReps = false)
        assertEquals(1, rest.centerNumber)
        assertEquals("Rest, last set next", rest.description.en())

        val prepareOne = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10).copy(sets = 1), "Stretch", countsReps = false)
        assertEquals("Get ready, 1 set", prepareOne.description.en())
    }

    @Test
    fun `a Timer only run shows no rep value on DONE`() {
        val done = TimerUiMapper.map(state(Phase.DONE, set = 8, left = 0, duration = 0), "Stretch", countsReps = false)
        assertEquals("DONE", done.label.en())
        assertNull(done.centerNumber)
        assertTrue(done.done)
        assertEquals("Done", done.description.en())
    }

    @Test
    fun `counts use plurals, so a single rep reads in the singular`() {
        val prep = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10, reps = 1), "Burpees")
        assertEquals("Get ready, first set 1 rep", prep.description.en())
        assertEquals("Work, set 2 of 8, 1 rep", TimerUiMapper.map(state(Phase.WORK, set = 2, left = 5, duration = 20, reps = 1), "Burpees").description.en())
        assertEquals("Rest, next set 3 of 8, 9 reps", TimerUiMapper.map(state(Phase.REST, set = 3, left = 5, duration = 10, reps = 9), "Burpees").description.en())
        assertEquals("Cooldown", TimerUiMapper.map(state(Phase.COOLDOWN, set = 8, left = 5, duration = 30), "Burpees").description.en())
        assertEquals("Done, 65 reps", TimerUiMapper.map(state(Phase.DONE, set = 8, left = 0, duration = 0), "Burpees").description.en())
    }

    private val kg16 = SetWeight(1600, WeightUnit.KG)

    @Test
    fun `a weight run shows the weight over the reps in work and in the rest preview`() {
        val work = TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20, reps = 10), "Curls", weight = kg16)
        assertEquals("16 kg", work.weight.en())
        assertEquals(10, work.centerNumber)
        assertNull(work.label.en())
        assertEquals("Work, set 2 of 8, 10 reps, 16 kilograms", work.description.en())
        val rest = TimerUiMapper.map(state(Phase.REST, set = 3, left = 10, duration = 10, reps = 10), "Curls", weight = kg16)
        assertEquals("16 kg", rest.weight.en())
        assertEquals("Rest, next set 3 of 8, 10 reps, 16 kilograms", rest.description.en())
    }

    @Test
    fun `get ready, cooldown and done show no weight`() {
        listOf(Phase.PREPARE, Phase.COOLDOWN, Phase.DONE).forEach { phase ->
            assertNull("$phase", TimerUiMapper.map(state(phase, set = 1, left = 10, duration = 10), "Curls", weight = kg16).weight)
        }
        val prep = TimerUiMapper.map(state(Phase.PREPARE, set = 1, left = 10, duration = 10), "Curls", weight = kg16)
        assertEquals("Get ready, first set 8 reps", prep.description.en())
    }

    @Test
    fun `a Timer only run and a Reps run show no weight`() {
        assertNull(TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20), "Stretch", countsReps = false, weight = kg16).weight)
        assertNull(TimerUiMapper.map(state(Phase.WORK, set = 2, left = 15, duration = 20), "Burpees").weight)
    }

    @Test
    fun `the stat circles read their values to TalkBack`() {
        // Spec revision 37: the visible Sets / Elapsed labels are gone, so each circle carries its own description.
        val ui = TimerUiMapper.map(state(Phase.WORK, set = 8, left = 5, duration = 20).copy(sets = 8, elapsedSec = 213), "Burpees")
        assertEquals("8/8", ui.setsText)
        assertEquals("03:33", ui.elapsedText)
        assertEquals("Sets 8 of 8", ui.setsDescription.en())
        assertEquals("Elapsed 3 minutes 33 seconds", ui.elapsedDescription.en())
        val round = TimerUiMapper.map(state(Phase.WORK, set = 1, left = 5, duration = 20).copy(elapsedSec = 60), "Burpees")
        assertEquals("Elapsed 1 minute", round.elapsedDescription.en())
        val long = TimerUiMapper.map(state(Phase.WORK, set = 1, left = 5, duration = 20).copy(elapsedSec = 4512), "Burpees")
        assertEquals("75:12", long.elapsedText)
        assertEquals("Elapsed 75 minutes 12 seconds", long.elapsedDescription.en())
        val one = TimerUiMapper.map(state(Phase.WORK, set = 1, left = 5, duration = 20).copy(elapsedSec = 61), "Burpees")
        assertEquals("Elapsed 1 minute 1 second", one.elapsedDescription.en())
    }

    @Test
    fun `the stat circle descriptions resolve in every language with no format left over`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("en", "es", "zh-CN", "hi").forEach { tag ->
            val r = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
            ).resources
            listOf(0, 1, 59, 60, 61, 213, 4512).forEach { sec ->
                val ui = TimerUiMapper.map(state(Phase.WORK, set = 1, left = 5, duration = 20).copy(elapsedSec = sec), "Burpees")
                listOf(ui.setsDescription.resolve(r), ui.elapsedDescription.resolve(r)).forEach { text ->
                    assertTrue(text, text.isNotBlank())
                    assertFalse(text, text.contains('%'))
                }
            }
        }
    }
}
