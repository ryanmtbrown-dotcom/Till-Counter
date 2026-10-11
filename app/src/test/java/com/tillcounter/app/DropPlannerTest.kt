package com.tillcounter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DropPlannerTest {
    private val sizes = intArrayOf(0,0,0,0,0,0,0,25,20,40,50,40,50)
    private fun calculate(target: Long, loose: IntArray, rolls: IntArray = IntArray(13)) =
        DropPlanner.recommend(target, loose, rolls, sizes)

    @Test fun standardDropUsesAvailableLargeNotesAndCoins() {
        val result = calculate(18375, intArrayOf(1,1,3,1,0,1,1,0,0,3,0,0,0))
        assertTrue(result.exact)
        assertEquals(18375, result.actualCents)
        listOf(0,1,2,3,5,6).forEach { assertEquals(1, result.counts[it]) }
        assertEquals(3, result.counts[9])
        assertEquals(75, result.coinCents())
    }

    @Test fun exactCombinationBeatsGreedyFiftyWhenNeeded() {
        val result = calculate(8000, intArrayOf(0,1,4,0,0,0,0,0,0,0,0,0,0))
        assertTrue(result.exact)
        assertEquals(0, result.counts[1])
        assertEquals(4, result.counts[2])
    }

    @Test fun impossibleExactDropReportsOnlyBillsActuallyAvailable() {
        val result = calculate(9500, intArrayOf(0,1,2,0,0,0,0,0,0,0,0,0,0))
        assertFalse(result.exact)
        assertEquals(9000, result.actualCents)
        assertEquals(1, result.counts[1])
        assertEquals(2, result.counts[2])
    }

    @Test fun countedRollsBecomeAvailableCoins() {
        val rolls = IntArray(13).apply { this[12] = 2 }
        val result = calculate(125, intArrayOf(0,0,0,0,0,0,0,0,0,1,0,0,0), rolls)
        assertTrue(result.exact)
        assertEquals(125, result.coinCents())
        assertEquals(100, result.counts[12])
    }

    @Test fun zeroAndHighCountsAreBoundedAndConsistent() {
        assertEquals(0, calculate(0, IntArray(13) { 5 }).actualCents)
        val result = calculate(99999999, IntArray(13).apply { this[0] = 99999 })
        assertEquals(99990000, result.actualCents)
        assertEquals(9999, result.counts[0])
    }

    @Test fun oldWorkflowCanAssembleNinetyEightDollarsAndFiftyTwoCents() {
        val rolls = IntArray(13).apply { for (i in 7..12) this[i] = 1 }
        val loose = intArrayOf(1,2,3,4,5,6,7,1,1,1,1,1,12)
        val result = calculate(9852, loose, rolls)
        assertTrue(result.exact)
        assertEquals(9852, result.actualCents)
        assertEquals(52, result.coinCents())
    }
}
