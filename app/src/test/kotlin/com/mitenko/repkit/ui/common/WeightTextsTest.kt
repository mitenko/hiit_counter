package com.mitenko.repkit.ui.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.domain.WeightMove
import com.mitenko.repkit.domain.WeightProblem
import com.mitenko.repkit.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WeightTextsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun res(tag: String): Resources =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }).resources

    private val en = res("en")

    @Test
    fun `weights read with their unit`() {
        assertEquals("22.5 kg", weightText(2250, WeightUnit.KG).resolve(en))
        assertEquals("20 lb", weightText(2000, WeightUnit.LB).resolve(en))
        assertEquals("20", weightText(2000, null).resolve(en))
    }

    @Test
    fun `moves read as notes`() {
        assertEquals("Top raised to 35 lb", WeightMove.TopRaised(3500).uiText(WeightUnit.LB).resolve(en))
        assertEquals("Starting weight moved to 8 kg", WeightMove.StartWeightMoved(800).uiText(WeightUnit.KG).resolve(en))
        assertEquals("2 holds removed (their weights are gone)", WeightMove.HoldsRemoved(2).uiText(WeightUnit.KG).resolve(en))
        assertEquals("Current weight moved to 12 kg × 9", WeightMove.CurrentMoved(1200, 9).uiText(WeightUnit.KG).resolve(en))
        assertEquals("Maximum reps per set raised to 13", WeightMove.RepMaxRaised(13).uiText(WeightUnit.KG).resolve(en))
    }

    @Test
    fun `problems read as field errors`() {
        assertEquals("Add at least 2 weights", WeightProblem.TooFewWeights.uiText().resolve(en))
        assertEquals("At most 40 weights", WeightProblem.TooManyWeights.uiText().resolve(en))
        assertEquals("Already in the list", WeightProblem.DuplicateWeight(1).uiText().resolve(en))
        assertEquals("Pick one of your weights", WeightProblem.HoldWeight(0).uiText().resolve(en))
    }

    @Test
    fun `every problem and move resolves in every language`() {
        val problems = listOf(
            WeightProblem.TooFewWeights, WeightProblem.TooManyWeights, WeightProblem.WeightOutOfRange(0), WeightProblem.DuplicateWeight(1),
            WeightProblem.StepsStart, WeightProblem.StepsStep, WeightProblem.StepsTop, WeightProblem.RepsPerSet, WeightProblem.RepMin,
            WeightProblem.RepMax, WeightProblem.StartWeight, WeightProblem.StartReps, WeightProblem.TooManyHolds, WeightProblem.HoldWeight(0),
            WeightProblem.HoldReps(0), WeightProblem.HoldFor(0), WeightProblem.DuplicateHold(1),
        )
        val moves = listOf(
            WeightMove.TopRaised(3500), WeightMove.TopLowered(2500), WeightMove.StartLowered(1750), WeightMove.RepMaxRaised(13),
            WeightMove.RepMinLowered(7), WeightMove.StartRepsRaised(12), WeightMove.StartRepsLowered(8), WeightMove.StartWeightMoved(800),
            WeightMove.HoldsRemoved(1), WeightMove.HoldsRemoved(2), WeightMove.CurrentMoved(800, null), WeightMove.CurrentMoved(1200, 9),
        )
        listOf("en", "es", "zh-CN", "hi").map(::res).forEach { r ->
            problems.forEach { assertTrue("$it", it.uiText().resolve(r).isNotBlank()) }
            moves.forEach {
                val text = it.uiText(WeightUnit.KG).resolve(r)
                assertTrue(text, text.isNotBlank())
                assertFalse(text, text.contains('%'))
            }
        }
    }
}
