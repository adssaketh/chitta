package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Entries as CSV rows, for the Export CSV action. Amounts are exact decimal text built from paise (no Double), signed:
 * money out is negative. The file can be read back by [CsvImport] (transfers, balance corrections, lent and borrowed rows are
 * written for completeness but skipped on import, and say so).
 */
object CsvExport {
    val HEADER = listOf("Date", "Account", "Category", "Subcategory", "Title", "Amount", "Currency", "Type", "Status", "Note")

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** -123456 becomes "-1234.56"; always two decimals. */
    fun amountText(paise: Long): String {
        val neg = paise < 0
        val abs = if (paise == Long.MIN_VALUE) return paise.toString() else Math.abs(paise)
        val text = (abs / 100).toString() + "." + (abs % 100).toString().padStart(2, '0')
        return if (neg) "-$text" else text
    }

    fun typeOf(t: Transaction): String = when {
        t.categoryId == QuickAdd.TRANSFER_CATEGORY_ID -> if (t.pairedTransactionId != null) "Transfer" else "Balance correction"
        t.kind == TransactionKind.LENT -> "Lent"
        t.kind == TransactionKind.BORROWED -> "Borrowed"
        t.income -> "Income"
        else -> "Expense"
    }

    fun statusOf(t: Transaction): String = when {
        t.skipPaid -> "Skipped"
        t.paid -> "Paid"
        else -> "Unpaid"
    }

    /**
     * The rows to export, oldest first. [accountIds] null means every account; [fromMs] is inclusive and [toMs] exclusive,
     * null meaning no limit.
     */
    fun rows(
        entries: List<Transaction>, accounts: List<Account>, categories: List<Category>, zone: ZoneId,
        accountIds: Set<String>? = null, fromMs: Long? = null, toMs: Long? = null,
    ): List<List<String>> {
        val accountName = accounts.associate { it.id to it.name }
        val categoryName = categories.associate { it.id to it.name }
        return entries.asSequence()
            .filter { accountIds == null || it.accountId in accountIds }
            .filter { (fromMs == null || it.dateMs >= fromMs) && (toMs == null || it.dateMs < toMs) }
            .sortedWith(compareBy<Transaction> { it.dateMs }.thenBy { it.id })
            .map { t ->
                listOf(
                    DATE.format(Instant.ofEpochMilli(t.dateMs).atZone(zone)),
                    accountName[t.accountId] ?: "",
                    if (t.categoryId == QuickAdd.TRANSFER_CATEGORY_ID) "" else categoryName[t.categoryId] ?: "",
                    t.subCategoryId?.let { categoryName[it] } ?: "",
                    t.name,
                    amountText(t.amount),
                    t.currency ?: "INR",
                    typeOf(t),
                    statusOf(t),
                    t.note,
                )
            }.toList()
    }

    fun file(
        entries: List<Transaction>, accounts: List<Account>, categories: List<Category>, zone: ZoneId,
        accountIds: Set<String>? = null, fromMs: Long? = null, toMs: Long? = null,
    ): String = Csv.write(listOf(HEADER) + rows(entries, accounts, categories, zone, accountIds, fromMs, toMs))
}
