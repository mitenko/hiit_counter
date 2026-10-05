package com.mitenko.repkit.data

import com.mitenko.repkit.domain.model.WeightHold
import com.mitenko.repkit.domain.model.WeightSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightCodecsTest {
    @Test
    fun `steps encode as start step top and round trip`() {
        assertEquals("2000:250:6000", WeightCodecs.encodeSteps(WeightSteps.DEFAULT))
        assertEquals(WeightSteps(800, 400, 2400), WeightCodecs.decodeSteps("800:400:2400"))
    }

    @Test
    fun `the empty steps column reads as the default steps`() {
        assertEquals(WeightSteps.DEFAULT, WeightCodecs.decodeSteps(""))
    }

    @Test
    fun `malformed steps decode to null`() {
        listOf("2000:250", "a:b:c", "2000:250:6000:1", "20.5:2.5:60", " ", ":::")
            .forEach { assertNull(it, WeightCodecs.decodeSteps(it)) }
    }

    @Test
    fun `a list encodes in order and round trips, with the empty list as the empty string`() {
        assertEquals("800,1200,1600", WeightCodecs.encodeList(listOf(800, 1200, 1600)))
        assertEquals(listOf(1600, 800), WeightCodecs.decodeList("1600,800"))
        assertEquals("", WeightCodecs.encodeList(emptyList()))
        assertEquals(emptyList<Int>(), WeightCodecs.decodeList(""))
    }

    @Test
    fun `a malformed list decodes to null`() {
        listOf("800,,1200", "8.5", "a", ",", " ").forEach { assertNull(it, WeightCodecs.decodeList(it)) }
    }

    @Test
    fun `holds encode as weight reps for and round trip`() {
        val holds = listOf(WeightHold(1600, 8, 4), WeightHold(1200, 10, 0))
        assertEquals("1600:8:4,1200:10:0", WeightCodecs.encodeHolds(holds))
        assertEquals(holds, WeightCodecs.decodeHolds("1600:8:4,1200:10:0"))
        assertEquals("", WeightCodecs.encodeHolds(emptyList()))
        assertEquals(emptyList<WeightHold>(), WeightCodecs.decodeHolds(""))
    }

    @Test
    fun `bad hold items are dropped and the good ones kept in order`() {
        assertEquals(
            listOf(WeightHold(1600, 8, 4), WeightHold(1200, 10, 2)),
            WeightCodecs.decodeHolds("1600:8:4,x,0:8:4,1200:0:4,1200:8:-1,1400:8:4:1,1200:10:2"),
        )
    }
}
