package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The four tabs on the Ledger. */
enum class LedgerTab { All, Spending, Income, Upcoming }

/** Search and filters (master section 7). Empty sets and nulls mean "no filter". Amounts are positive paise of the absolute value. */
data class LedgerFilter(
    val tab: LedgerTab = LedgerTab.All,
    val text: String = "",
    val categoryIds: Set<String> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val fromMs: Long? = null,
    val toMs: Long? = null,           // exclusive
    val minAmount: Paise? = null,
    val maxAmount: Paise? = null,
)

object LedgerSearch {
    fun isUpcoming(t: Transaction): Boolean =
        (t.kind == TransactionKind.UPCOMING || t.kind == TransactionKind.SUBSCRIPTION || t.kind == TransactionKind.REPETITIVE) && !t.paid && !t.skipPaid

    /** Entries that pass [filter], newest first. Text matches the title, note, category name and account name, ignoring case. */
    fun apply(entries: List<Transaction>, filter: LedgerFilter, categories: List<Category>, accounts: List<Account>): List<Transaction> {
        val categoryName = categories.associate { it.id to it.name }
        val accountName = accounts.associate { it.id to it.name }
        val incomeCategory = categories.filter { it.income }.map { it.id }.toSet()
        val q = filter.text.trim().lowercase()
        return entries.asSequence().filter { t ->
            when (filter.tab) {
                LedgerTab.All -> true
                LedgerTab.Upcoming -> isUpcoming(t)
                LedgerTab.Income -> !isUpcoming(t) && !Totals.isNeutral(t) && (t.categoryId in incomeCategory || (t.categoryId !in categoryName && t.amount > 0))
                LedgerTab.Spending -> !isUpcoming(t) && !Totals.isNeutral(t) && !(t.categoryId in incomeCategory || (t.categoryId !in categoryName && t.amount > 0))
            }
        }.filter { filter.categoryIds.isEmpty() || it.categoryId in filter.categoryIds || it.subCategoryId in filter.categoryIds }
            .filter { filter.accountIds.isEmpty() || it.accountId in filter.accountIds }
            .filter { filter.fromMs == null || it.dateMs >= filter.fromMs }
            .filter { filter.toMs == null || it.dateMs < filter.toMs }
            .filter { filter.minAmount == null || Math.abs(it.amount) >= filter.minAmount.value }
            .filter { filter.maxAmount == null || Math.abs(it.amount) <= filter.maxAmount.value }
            .filter {
                q.isEmpty() || it.name.lowercase().contains(q) || it.note.lowercase().contains(q) ||
                    categoryName[it.categoryId]?.lowercase()?.contains(q) == true || accountName[it.accountId]?.lowercase()?.contains(q) == true
            }
            .sortedWith(compareByDescending<Transaction> { it.dateMs }.thenBy { it.id })
            .toList()
    }
}

data class DayGroup(val date: LocalDate, val entries: List<Transaction>, val net: Paise)
data class MonthGroup(val month: YearMonth, val flow: Flow, val days: List<DayGroup>)

object LedgerGrouping {
    /** Days newest first inside months newest first; each day carries its net total, each month its in and out. */
    fun group(entries: List<Transaction>, categories: List<Category>, zone: ZoneId): List<MonthGroup> {
        fun dateOf(t: Transaction): LocalDate = Instant.ofEpochMilli(t.dateMs).atZone(zone).toLocalDate()
        val sorted = entries.sortedWith(compareByDescending<Transaction> { it.dateMs }.thenBy { it.id })
        return sorted.groupBy { YearMonth.from(dateOf(it)) }.map { (month, inMonth) ->
            val days = inMonth.groupBy { dateOf(it) }.map { (date, inDay) -> DayGroup(date, inDay, Totals.dayNet(inDay)) }
                .sortedByDescending { it.date }
            MonthGroup(month, Totals.of(inMonth, categories), days)
        }.sortedByDescending { it.month }
    }
}
