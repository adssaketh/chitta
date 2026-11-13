package com.mansaketh.chitta.core.money

/** Ticks of the Home budget ring. [spent] + [bills] + [safe] always equals [TOTAL]. */
data class RingTicks(
    val spent: Int,
    val bills: Int,
    val safe: Int,
    /** Spending plus bills exceed the budget: the ring is drawn full with "over" in the middle. */
    val over: Boolean,
    /** Safe to spend as a percentage of the budget, rounded half up. 0 when over. */
    val safePercent: Int,
) {
    companion object {
        const val TOTAL = 50 // 50 ticks of 2% each (master section 3)
    }
}

object Segments {
    /**
     * The budget ring (master section 3). Hidden (null) when no budget is set. Ticks are shares of
     * the budget in 2% steps, assigned by the largest-remainder method so they always add to 50.
     * When spend plus bills exceed the budget the ring is full: spent and bills share the 50
     * ticks in proportion and safe is 0 (assumption, see DECISIONS.md).
     */
    fun ring(budget: Paise, spent: Paise, billsDue: Paise): RingTicks? {
        if (budget.value <= 0) return null
        val s = spent.value.coerceAtLeast(0)
        val b = billsDue.value.coerceAtLeast(0)
        val over = s + b > budget.value
        val total = RingTicks.TOTAL
        if (over) {
            val parts = largestRemainder(longArrayOf(s, b), s + b, total)
            return RingTicks(parts[0], parts[1], 0, over = true, safePercent = 0)
        }
        val safe = budget.value - s - b
        val parts = largestRemainder(longArrayOf(s, b, safe), budget.value, total)
        val percent = mulDivRound(safe, 100, budget.value).toInt()
        return RingTicks(parts[0], parts[1], parts[2], over = false, safePercent = percent)
    }

    /** Splits [total] units across [values] in proportion to value / [sum], summing exactly to [total]. */
    private fun largestRemainder(values: LongArray, sum: Long, total: Int): IntArray {
        val shares = LongArray(values.size) { Math.multiplyExact(values[it], total.toLong()) }
        val result = IntArray(values.size) { (shares[it] / sum).toInt() }
        var left = total - result.sum()
        val order = values.indices.sortedWith(
            compareByDescending<Int> { shares[it] % sum }.thenBy { it }
        )
        var i = 0
        while (left > 0) {
            result[order[i % order.size]]++
            left--
            i++
        }
        return result
    }

    /** Filled segments of a gauge bar. */
    data class Gauge(val filled: Int, val segments: Int, val over: Boolean)

    /**
     * Gauge bars (10 or 20 segments) for budgets, goals, loans and card utilisation. Whole segments
     * only (rounded down), but any non-zero amount lights at least one. Over the limit is flagged,
     * and the bar is then full; only an over gauge may be drawn in the accent colour.
     */
    fun gauge(actual: Paise, limit: Paise, segments: Int = 10): Gauge {
        require(segments > 0) { "segments must be positive" }
        if (limit.value <= 0 || actual.value <= 0) return Gauge(0, segments, over = limit.value <= 0 && actual.value > 0)
        val over = actual > limit
        val raw = Math.multiplyExact(actual.value, segments.toLong()) / limit.value
        val filled = raw.toInt().coerceIn(1, segments)
        return Gauge(filled, segments, over)
    }
}
