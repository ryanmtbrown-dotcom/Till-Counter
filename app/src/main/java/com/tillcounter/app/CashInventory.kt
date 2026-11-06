package com.tillcounter.app

/** Count bank straps and bundles as physical notes, never as a replacement for loose counts. */
internal object CashInventory {
    const val NOTES_PER_STRAP = 100
    const val NOTES_PER_BUNDLE = 1000
    private const val BILL_DENOMINATIONS = 7

    fun expandedCounts(loose: IntArray, straps: IntArray, bundles: IntArray): IntArray {
        require(loose.size == straps.size && loose.size == bundles.size && loose.size >= BILL_DENOMINATIONS)
        return IntArray(loose.size) { i ->
            val total = loose[i].coerceAtLeast(0).toLong() +
                if (i < BILL_DENOMINATIONS) {
                    straps[i].coerceAtLeast(0).toLong() * NOTES_PER_STRAP +
                        bundles[i].coerceAtLeast(0).toLong() * NOTES_PER_BUNDLE
                } else 0L
            total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
