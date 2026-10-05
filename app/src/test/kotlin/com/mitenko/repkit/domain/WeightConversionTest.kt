package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightUnit.KG
import com.mitenko.repkit.domain.model.WeightUnit.LB
import com.mitenko.repkit.domain.model.WeightsKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightConversionTest {
    @Test
    fun `kg to lb rounds to the nearest quarter`() {
        assertEquals(4400, WeightConversion.convert(2000, KG, LB)) // 44.09 → 44
        assertEquals(4950, WeightConversion.convert(2250, KG, LB)) // 49.60 → 49.5
        assertEquals(3525, WeightConversion.convert(1600, KG, LB)) // 35.27 → 35.25
    }

    @Test
    fun `lb to kg rounds to the nearest quarter`() {
        assertEquals(2050, WeightConversion.convert(4500, LB, KG)) // 20.41 → 20.5
        assertEquals(1600, WeightConversion.convert(3500, LB, KG)) // 15.876 → 16
        assertEquals(1125, WeightConversion.convert(2500, LB, KG)) // 11.34 → 11.25
    }

    @Test
    fun `the same unit is unchanged`() {
        assertEquals(2257, WeightConversion.convert(2257, KG, KG))
    }

    @Test
    fun `a result stays between a quarter and 999_75`() {
        assertEquals(25, WeightConversion.convert(1, LB, KG))
        assertEquals(WeightConfig.MAX_WEIGHT, WeightConversion.convert(WeightConfig.MAX_WEIGHT, KG, LB))
    }

    @Test
    fun `kg to lb and back is the identity on the default ladder`() {
        val ladder = WeightConfig().weights
        assertEquals(17, ladder.size)
        for (kg in ladder) assertEquals("$kg", kg, WeightConversion.convert(WeightConversion.convert(kg, KG, LB), LB, KG))
        val kg = WeightConfig(unit = KG)
        assertEquals(ladder, WeightConversion.convert(WeightConversion.convert(kg, LB), KG).list)
    }

    @Test
    fun `a config converts its weights, starting weight and holds, and steps become a list`() {
        val kg = WeightConfig(unit = KG, startWeight = 2250, holds = listOf(WeightHold(2500, 8, 4)))
        val lb = WeightConversion.convert(kg, LB)
        assertEquals(LB, lb.unit)
        assertEquals(WeightsKind.LIST, lb.kind)
        assertEquals(17, lb.list.size)
        assertEquals(listOf(4400, 4950), lb.list.take(2))
        assertEquals(13225, lb.list.last())
        assertEquals(4950, lb.startWeight)
        assertEquals(listOf(WeightHold(5500, 8, 4)), lb.holds)
        assertEquals(kg.steps, lb.steps)
    }

    @Test
    fun `weights that round to the same value collapse, keeping the first`() {
        val lb = WeightConfig(unit = LB, kind = WeightsKind.LIST, list = listOf(100, 125, 1000))
        assertEquals(listOf(50, 450), WeightConversion.convert(lb, KG).list)
    }

    @Test
    fun `a config without a unit only gains one, and its own unit is a no-op`() {
        assertEquals(WeightConfig(unit = LB), WeightConversion.convert(WeightConfig(), LB))
        val kg = WeightConfig(unit = KG)
        assertEquals(kg, WeightConversion.convert(kg, KG))
    }
}
