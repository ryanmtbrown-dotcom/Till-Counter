package com.tillcounter.app

/** Builds a physically possible cash drop from the counted denomination inventory. */
internal object DropPlanner {
    private val values = longArrayOf(10000, 5000, 2000, 1000, 500, 200, 100, 100, 50, 25, 10, 5, 1)
    private const val SEARCH_LIMIT = 50_000

    data class Recommendation(val requestedCents: Long, val actualCents: Long, val counts: IntArray) {
        val exact: Boolean get() = actualCents == requestedCents
        fun coinCents(): Long = (7 until counts.size).sumOf { counts[it].toLong() * values[it] }
    }

    fun recommend(requestedCents: Long, loose: IntArray, rolls: IntArray, rollSizes: IntArray): Recommendation {
        require(loose.size == values.size && rolls.size == values.size && rollSizes.size == values.size)
        val requested = requestedCents.coerceAtLeast(0L)
        val available = IntArray(values.size) { i ->
            val count = loose[i].coerceAtLeast(0).toLong() + rolls[i].coerceAtLeast(0).toLong() * rollSizes[i].coerceAtLeast(0)
            count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val greedy = IntArray(values.size)
        var remainder = requested
        for (i in values.indices) {
            val count = minOf(available[i].toLong(), remainder / values[i]).toInt()
            greedy[i] = count
            remainder -= count.toLong() * values[i]
        }
        var bestAmount = requested - remainder
        var bestCounts = greedy.copyOf()
        if (remainder == 0L) return Recommendation(requested, bestAmount, bestCounts)

        val suffixCapacity = LongArray(values.size + 1)
        for (i in values.indices.reversed()) {
            suffixCapacity[i] = suffixCapacity[i + 1] + available[i].toLong() * values[i]
        }
        val choices = IntArray(values.size)
        var examined = 0
        fun search(index: Int, left: Long) {
            if (bestAmount == requested || index >= values.size || examined >= SEARCH_LIMIT) return
            val maxPossible = requested - left + minOf(left, suffixCapacity[index])
            if (maxPossible <= bestAmount) return
            val maxCount = minOf(available[index].toLong(), left / values[index]).toInt()
            for (count in maxCount downTo 0) {
                if (++examined > SEARCH_LIMIT) break
                choices[index] = count
                val next = left - count.toLong() * values[index]
                val possible = requested - next + minOf(next, suffixCapacity[index + 1])
                if (possible <= bestAmount) continue
                val attained = requested - next
                if (attained > bestAmount) {
                    bestAmount = attained
                    bestCounts = choices.copyOf()
                }
                if (next > 0L) search(index + 1, next)
                if (bestAmount == requested) break
            }
            choices[index] = 0
        }
        search(0, requested)
        return Recommendation(requested, bestAmount, bestCounts)
    }
}
