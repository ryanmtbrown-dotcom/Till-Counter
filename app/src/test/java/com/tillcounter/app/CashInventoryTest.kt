package com.tillcounter.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CashInventoryTest {
    @Test fun strapsAndBundlesAddToLooseBillsWithoutChangingCoins() {
        val loose = IntArray(13).apply { this[0] = 3; this[2] = 4; this[9] = 17 }
        val straps = IntArray(13).apply { this[0] = 2; this[2] = 1 }
        val bundles = IntArray(13).apply { this[0] = 1; this[9] = 99 }
        val result = CashInventory.expandedCounts(loose, straps, bundles)
        assertEquals(1203, result[0])
        assertEquals(104, result[2])
        assertEquals(17, result[9])
        assertEquals(3, loose[0])
    }

    @Test fun expandedNotesReachDropPlannerWithoutLosingCoinRolls() {
        val loose = IntArray(13)
        val straps = IntArray(13).apply { this[0] = 1 }
        val bundles = IntArray(13).apply { this[2] = 1 }
        val rolls = IntArray(13).apply { this[12] = 1 }
        val rollSizes = intArrayOf(0,0,0,0,0,0,0,25,20,40,50,40,50)
        val expanded = CashInventory.expandedCounts(loose, straps, bundles)
        val result = DropPlanner.recommend(10025L, expanded, rolls, rollSizes)
        assertEquals(10525L, result.actualCents)
        assertEquals(1, result.counts[0])
        assertEquals(25, result.counts[12])
    }
}
