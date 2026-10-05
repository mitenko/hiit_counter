package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightUnitsTest {
    @Test
    fun `the US, Liberia and Myanmar default to pounds and everywhere else to kilograms`() {
        listOf("US", "LR", "MM", "us").forEach { assertEquals(it, WeightUnit.LB, defaultWeightUnit(it)) }
        listOf("GB", "CA", "DE", "IN", "CN", "ES", "").forEach { assertEquals(it, WeightUnit.KG, defaultWeightUnit(it)) }
    }
}
