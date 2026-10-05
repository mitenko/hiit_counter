package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.WeightConfig
import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightsKind
import com.mitenko.repkit.testutil.expectThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionScaleTest {
    private val curls = listOf(800, 1000, 1200, 1400, 1600)
    private val rtw = ProgressionScale.RepsThenWeight(curls, 8, 12)
    private val weight = ProgressionScale.Weight(curls, 10)
    private val curlsConfig = ProgressionConfig(
        mode = ProgressMode.REPS_THEN_WEIGHT,
        weight = WeightConfig(kind = WeightsKind.LIST, list = curls),
    )

    @Test
    fun `Reps then weight levels map to weight x reps and back`() {
        assertEquals(listOf(0, 24, 5), listOf(rtw.minLevel, rtw.maxLevel, rtw.span))
        assertEquals(Prescription.Load(800, 8), rtw.prescription(0))
        assertEquals(Prescription.Load(800, 12), rtw.prescription(4))
        assertEquals(Prescription.Load(1000, 8), rtw.prescription(5))
        assertEquals(Prescription.Load(1600, 8), rtw.prescription(20))
        assertEquals(Prescription.Load(1600, 12), rtw.prescription(24))
        for (level in 0..24) {
            val p = rtw.prescription(level)
            assertEquals(level, rtw.levelOf(p.weight, p.reps))
        }
    }

    @Test
    fun `Weight levels are weight indexes with fixed reps`() {
        assertEquals(listOf(0, 4), listOf(weight.minLevel, weight.maxLevel))
        assertEquals(Prescription.Load(1400, 10), weight.prescription(3))
        for (level in 0..4) assertEquals(level, weight.levelOf(weight.prescription(level).weight, 10))
    }

    @Test
    fun `a level outside the ladder is clamped`() {
        assertEquals(Prescription.Load(800, 8), rtw.prescription(-3))
        assertEquals(Prescription.Load(1600, 12), rtw.prescription(99))
        assertEquals(Prescription.Load(1600, 10), weight.prescription(7))
    }

    @Test
    fun `levelOf takes the nearest lower weight, else the lightest, and clamps the reps`() {
        assertEquals(2, weight.levelOf(1300, 10))
        assertEquals(0, weight.levelOf(500, 10))
        assertEquals(10, rtw.levelOf(1300, 3))   // 12 kg × 8
        assertEquals(14, rtw.levelOf(1200, 15))  // 12 kg × 12
        assertEquals(1, rtw.levelOf(500, 9))     // no weight ≤ 5 kg: the lightest, 8 kg × 9
    }

    @Test
    fun `missFloor is the first level of the next lighter weight`() {
        assertEquals(15, rtw.missFloor(20))
        assertEquals(15, rtw.missFloor(24))
        assertEquals(5, rtw.missFloor(13))
        assertEquals(0, rtw.missFloor(7))
        assertEquals(0, rtw.missFloor(3))
        assertEquals(0, rtw.missFloor(0))
        assertEquals(3, weight.missFloor(4))
        assertEquals(0, weight.missFloor(0))
    }

    @Test
    fun `Reps mode is the identity with no miss floor`() {
        val reps = ProgressionConfig().scale()
        assertEquals(ProgressionScale.Reps(48, 72), reps)
        assertEquals(Prescription.RepTotal(64), reps.prescription(64))
        assertEquals(Int.MIN_VALUE, reps.missFloor(60))
        assertEquals(ProgressionConfig(), ProgressionConfig().engineConfig())
        assertEquals(48, ProgressionConfig().startLevel())
        assertNull(ProgressionConfig().loadAt(60))
    }

    @Test
    fun `the start level comes from the starting weight and reps`() {
        assertEquals(0, curlsConfig.startLevel())
        val start = curlsConfig.weight.copy(startWeight = 1200, startReps = 10)
        assertEquals(12, curlsConfig.copy(weight = start).startLevel())
        assertEquals(2, curlsConfig.copy(mode = ProgressMode.WEIGHT, weight = start).startLevel())
    }

    @Test
    fun `the engine config runs on levels, with the weight holds as level holds`() {
        val c = curlsConfig.copy(weight = curlsConfig.weight.copy(startWeight = 1000, holds = listOf(WeightHold(1400, 8, 4))))
        val e = c.engineConfig()
        assertEquals(listOf(5, 0, 24), listOf(e.startingTotal, e.floor, e.cap))
        assertEquals(listOf(Hold(15, 4)), e.holds)
        assertEquals(listOf(c.windowHours, c.hold), listOf(e.windowHours, e.hold))
        assertEquals(Prescription.Load(1400, 8), c.loadAt(15))
    }

    @Test
    fun `the default steps give a Weight ladder from 20 to 60`() {
        val s = ProgressionConfig(mode = ProgressMode.WEIGHT).scale()
        assertEquals(16, s.maxLevel)
        assertEquals(Prescription.Load(2250, 10), s.prescription(1))
    }

    @Test
    fun `a single weight is one level in Weight mode and one rep range in Reps then weight`() {
        val one = listOf(1200)
        val w = ProgressionScale.Weight(one, 10)
        assertEquals(listOf(0, 0, 0), listOf(w.minLevel, w.maxLevel, w.missFloor(0)))
        assertEquals(Prescription.Load(1200, 10), w.prescription(3))
        assertEquals(0, w.levelOf(2000, 10))
        val r = ProgressionScale.RepsThenWeight(one, 8, 12)
        assertEquals(listOf(0, 4), listOf(r.minLevel, r.maxLevel))
        for (level in 0..4) assertEquals(0, r.missFloor(level))
        assertEquals(Prescription.Load(1200, 12), r.prescription(4))
        val fixed = ProgressionScale.RepsThenWeight(one, 10, 10)
        assertEquals(listOf(0, 0), listOf(fixed.maxLevel, fixed.missFloor(0)))
    }

    @Test
    fun `a span of 1 behaves exactly like Weight mode`() {
        val one = ProgressionScale.RepsThenWeight(curls, 10, 10)
        assertEquals(1, one.span)
        assertEquals(listOf(weight.minLevel, weight.maxLevel), listOf(one.minLevel, one.maxLevel))
        for (level in -1..5) {
            assertEquals(weight.prescription(level), one.prescription(level))
            assertEquals(weight.missFloor(level), one.missFloor(level))
        }
        for (w in listOf(500, 800, 1300, 1600, 2000)) assertEquals(weight.levelOf(w, 10), one.levelOf(w, 10))
    }

    @Test
    fun `Reps mode has no ladder`() {
        expectThrows<IllegalArgumentException> { ladderOf(ProgressMode.REPS, WeightConfig()) }
    }
}
