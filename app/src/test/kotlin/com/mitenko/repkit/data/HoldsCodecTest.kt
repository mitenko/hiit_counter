package com.mitenko.repkit.data

import com.mitenko.repkit.domain.model.Hold
import com.mitenko.repkit.domain.model.HoldKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoldsCodecTest {
    @Test
    fun `holds encode in list order and round trip`() {
        val holds = listOf(Hold(64, 4), Hold(56, 3))
        assertEquals("64:4,56:3", HoldsCodec.encode(holds))
        assertEquals(holds, HoldsCodec.decode("64:4,56:3"))
        assertEquals(listOf(Hold(64, 0)), HoldsCodec.decode(HoldsCodec.encode(listOf(Hold(64, 0)))))
    }

    @Test
    fun `an empty list is written as a dash, never as the column default`() {
        assertEquals("-", HoldsCodec.encode(emptyList()))
        assertEquals(emptyList<Hold>(), HoldsCodec.decode("-"))
    }

    @Test
    fun `text without a single good item decodes to null`() {
        listOf("", " ", "64", "64:", ":4", "a:b", "64:4;56:3", "0:4", "64:-1", "64:4:1", "--", "99999999999:1", ",")
            .forEach { assertNull(it, HoldsCodec.decode(it)) }
    }

    @Test
    fun `bad items are dropped and the good ones kept in order`() {
        assertEquals(listOf(Hold(56, 3), Hold(64, 4)), HoldsCodec.decode("56:3,x:1,64:4"))
        assertEquals(listOf(Hold(64, 4)), HoldsCodec.decode("64:4,"))
        assertEquals(listOf(Hold(70, 0)), HoldsCodec.decode("0:4,70:0,64:-1"))
    }

    @Test
    fun `a From hold is written with a plus and round trips with At holds`() {
        val mixed = listOf(Hold(56, 3), Hold(60, 2, HoldKind.FROM))
        assertEquals("56:3,60+:2", HoldsCodec.encode(mixed))
        assertEquals(mixed, HoldsCodec.decode("56:3,60+:2"))
        assertEquals("64:4", HoldsCodec.encode(listOf(Hold(64, 4))))
        assertEquals(listOf(Hold(60, 2, HoldKind.FROM)), HoldsCodec.decode("60+:2"))
        val both = listOf(Hold(64, 4), Hold(64, 2, HoldKind.FROM))
        assertEquals(both, HoldsCodec.decode(HoldsCodec.encode(both)))
    }

    @Test
    fun `old text decodes exactly as before, every item an At hold`() {
        assertEquals(listOf(Hold(56, 3, HoldKind.AT), Hold(64, 4, HoldKind.AT)), HoldsCodec.decode("56:3,64:4"))
    }

    @Test
    fun `a malformed plus item is dropped and the rest kept`() {
        assertEquals(listOf(Hold(56, 3), Hold(64, 4)), HoldsCodec.decode("56:3,60++:2,+60:2,60+1:2,+:2,60 +:2,64:4"))
        listOf("60++:2", "+60:2", "+:2", "60+:", "60+:-1", "0+:2", "60+").forEach { assertNull(it, HoldsCodec.decode(it)) }
    }
}
