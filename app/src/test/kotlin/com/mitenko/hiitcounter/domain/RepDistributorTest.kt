package com.mitenko.hiitcounter.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RepDistributorTest {
    @Test
    fun `remainder goes to the first sets`() {
        assertEquals(listOf(9, 8, 8, 8, 8, 8, 8, 8), RepDistributor.distribute(65, 8))
        assertEquals(listOf(9, 9, 9, 8, 8, 8, 8, 8), RepDistributor.distribute(67, 8))
    }

    @Test
    fun `exact multiples split evenly`() {
        assertEquals(List(8) { 6 }, RepDistributor.distribute(48, 8))
    }

    @Test
    fun `single set gets everything`() {
        assertEquals(listOf(65), RepDistributor.distribute(65, 1))
    }

    @Test
    fun `total smaller than sets yields zeros at the end`() {
        assertEquals(listOf(1, 1, 1, 0, 0), RepDistributor.distribute(3, 5))
    }

    @Test
    fun `sum always equals total`() {
        for (total in 0..100) for (sets in 1..20) {
            assertEquals(total, RepDistributor.distribute(total, sets).sum())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero sets is rejected`() {
        RepDistributor.distribute(10, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative total is rejected`() {
        RepDistributor.distribute(-1, 8)
    }
}
