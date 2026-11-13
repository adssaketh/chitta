package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.money.Paise

/** One slice of the optional donut. [categoryId] is null for the combined "Other" slice. [percent] is a whole number; all slices add to 100. */
data class DonutSlice(val categoryId: String?, val amount: Paise, val percent: Int)

object Donut {
    /**
     * The biggest categories as slices, with everything past [maxSlices] - 1 folded into one "Other" slice. Zero and negative totals
     * (a category refunded below zero) are left out. Percentages use the largest-remainder rule so they always add to exactly 100.
     */
    fun slices(totals: List<CategoryTotal>, maxSlices: Int = 6): List<DonutSlice> {
        require(maxSlices >= 2) { "need room for at least one category and Other" }
        val positive = totals.filter { it.amount.value > 0 }.sortedByDescending { it.amount.value }
        val sum = positive.sumOf { it.amount.value }
        if (sum <= 0) return emptyList()
        val parts: List<Pair<String?, Long>> =
            if (positive.size <= maxSlices) positive.map { it.categoryId to it.amount.value }
            else positive.take(maxSlices - 1).map { it.categoryId to it.amount.value } + (null to positive.drop(maxSlices - 1).sumOf { it.amount.value })

        // Whole percents from exact integer arithmetic: floor of amount * 100 / sum, then the spare percents go to the largest remainders.
        val floors = parts.map { (it.second * 100 / sum).toInt() }.toMutableList()
        var left = 100 - floors.sum()
        val remainders = parts.mapIndexed { i, p -> i to ((p.second * 100) % sum) }.sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenBy { it.first })
        for ((i, _) in remainders) { if (left <= 0) break; floors[i] += 1; left-- }
        return parts.mapIndexed { i, p -> DonutSlice(p.first, Paise(p.second), floors[i]) }
    }
}
