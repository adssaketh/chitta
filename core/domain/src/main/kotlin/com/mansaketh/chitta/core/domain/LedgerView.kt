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
    /** Typed filters from the search box (Phase 8f). They apply on top of everything above. */
    val smart: SmartFilter = SmartFilter(),
)

object LedgerSearch {
    fun isUpcoming(t: Transaction): Boolean =
        (t.kind == TransactionKind.UPCOMING || t.kind == TransactionKind.SUBSCRIPTION || t.kind == TransactionKind.REPETITIVE) && !t.paid && !t.skipPaid

    private fun isIncomeSide(t: Transaction, incomeCategory: Set<String>, known: Set<String>) =
        t.categoryId in incomeCategory || (t.categoryId !in known && t.amount > 0)

    private fun hasType(t: Transaction, type: SearchType, incomeCategory: Set<String>, known: Set<String>): Boolean = when (type) {
        SearchType.Transfer -> Totals.isTransfer(t)
        SearchType.Upcoming -> isUpcoming(t)
        SearchType.Lent -> t.kind == TransactionKind.LENT
        SearchType.Borrowed -> t.kind == TransactionKind.BORROWED
        SearchType.Subscription -> t.kind == TransactionKind.SUBSCRIPTION
        SearchType.Repeating -> t.kind == TransactionKind.REPETITIVE
        SearchType.Income -> !isUpcoming(t) && !Totals.isNeutral(t) && isIncomeSide(t, incomeCategory, known)
        SearchType.Spending -> !isUpcoming(t) && !Totals.isNeutral(t) && !isIncomeSide(t, incomeCategory, known)
    }

    /** The typed filters of [SmartFilter]: all kinds must hold; inside one kind any value may hold; excluded values must not. */
    private fun matchesSmart(t: Transaction, s: SmartFilter, incomeCategory: Set<String>, known: Set<String>): Boolean {
        if (!s.isActive) return true
        fun inCats(ids: Set<String>) = t.categoryId in ids || t.subCategoryId in ids
        if (s.categoryIds != null && !inCats(s.categoryIds)) return false
        if (s.excludeCategoryIds.isNotEmpty() && inCats(s.excludeCategoryIds)) return false
        if (s.accountIds != null && t.accountId !in s.accountIds) return false
        if (t.accountId in s.excludeAccountIds) return false
        if (s.types != null && s.types.none { hasType(t, it, incomeCategory, known) }) return false
        if (s.excludeTypes.any { hasType(t, it, incomeCategory, known) }) return false
        val note = t.note.lowercase()
        if (s.notes.isNotEmpty() && s.notes.none { note.contains(it) }) return false
        if (s.excludeNotes.any { note.contains(it) }) return false
        val abs = Math.abs(t.amount)
        if (s.minAmount != null && abs < s.minAmount.value) return false
        if (s.maxAmount != null && abs > s.maxAmount.value) return false
        if (s.fromMs != null && t.dateMs < s.fromMs) return false
        if (s.toMs != null && t.dateMs >= s.toMs) return false
        return true
    }

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
            .filter { matchesSmart(it, filter.smart, incomeCategory, categoryName.keys) }
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
