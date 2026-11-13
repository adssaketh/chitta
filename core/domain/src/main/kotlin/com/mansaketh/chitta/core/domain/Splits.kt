package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise

/** One line of a split: a part of the payment that belongs to one category. Amount is positive paise. */
data class SplitLine(val categoryId: String, val subCategoryId: String? = null, val amount: Paise)

/** Where a split stands against its total. Save is allowed only for [Balanced]. */
sealed interface SplitCheck {
    data object Balanced : SplitCheck
    data object NeedsTwoLines : SplitCheck
    data object ZeroLine : SplitCheck
    /** Still to place, in paise (always positive). */
    data class Left(val amount: Paise) : SplitCheck
    /** Lines add up to more than the total by this much (always positive). */
    data class Over(val amount: Paise) : SplitCheck
}

/**
 * Split entries (master section 8, rules in section 12). A split is stored as one ordinary entry per line, all sharing a
 * `splitGroupId`, so totals, budgets and balances need no special case (DECISIONS D85).
 */
object Splits {
    /** Equal parts over [categoryIds]; the leftover paise all go to the first line (master section 12). */
    fun equalLines(total: Paise, categoryIds: List<String>): List<SplitLine> {
        require(total.value > 0) { "total must be above zero" }
        require(categoryIds.isNotEmpty()) { "need at least one category" }
        val n = categoryIds.size.toLong()
        val base = total.value / n
        val extra = total.value % n
        return categoryIds.mapIndexed { i, id -> SplitLine(id, null, Paise(if (i == 0) base + extra else base)) }
    }

    fun check(total: Paise, lines: List<SplitLine>): SplitCheck {
        if (lines.size < 2) return SplitCheck.NeedsTwoLines
        if (lines.any { it.amount.value <= 0 }) return SplitCheck.ZeroLine
        val sum = lines.fold(Paise.ZERO) { acc, l -> acc + l.amount }
        return when {
            sum == total -> SplitCheck.Balanced
            sum < total -> SplitCheck.Left(total - sum)
            else -> SplitCheck.Over(sum - total)
        }
    }

    /** What is left to place, shown live under the lines. Negative means over. */
    fun remainder(total: Paise, lines: List<SplitLine>): Paise = total - lines.fold(Paise.ZERO) { acc, l -> acc + l.amount }

    /**
     * Turns [template] (the entry as typed: account, date, title, income flag, note) into one entry per line.
     * Expenses are stored negative and income positive, the same as a single entry. Returns null unless balanced.
     */
    fun build(template: Transaction, total: Paise, lines: List<SplitLine>, newId: () -> String): List<Transaction>? {
        if (check(total, lines) != SplitCheck.Balanced) return null
        val group = newId()
        return lines.map { l ->
            template.copy(
                id = newId(),
                categoryId = l.categoryId,
                subCategoryId = l.subCategoryId,
                amount = if (template.income) l.amount.value else -l.amount.value,
                splitGroupId = group,
                // A foreign-currency original cannot be split exactly; the converted paise on each line are what count.
                originalAmount = null, originalCurrency = null, exchangeRate = null,
            )
        }
    }

    /** The other lines of the same split, for the detail screen. Empty when [entry] is not part of one. */
    fun siblings(entry: Transaction, all: List<Transaction>): List<Transaction> {
        val g = entry.splitGroupId ?: return emptyList()
        return all.filter { it.splitGroupId == g && it.id != entry.id }
    }

    /** The whole payment a split line belongs to, in paise (magnitude). */
    fun groupTotal(entry: Transaction, all: List<Transaction>): Paise {
        val g = entry.splitGroupId ?: return Paise(kotlin.math.abs(entry.amount))
        return Paise(all.filter { it.splitGroupId == g }.sumOf { kotlin.math.abs(it.amount) })
    }
}
