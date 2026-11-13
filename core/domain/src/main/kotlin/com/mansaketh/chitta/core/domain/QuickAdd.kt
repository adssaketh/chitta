package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal

enum class EntryType { Expense, Income, Transfer }

/** What the Quick Add sheet holds before saving. */
data class QuickAddDraft(
    val type: EntryType = EntryType.Expense,
    val amountRaw: String = "",
    val title: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,   // transfers only
    val dateMs: Long,
    val note: String = "",
    /** Set when the amount is typed in another currency than the account's: decimal text, original -> account. */
    val rate: String? = null,
    val originalCurrency: String? = null,
    val ownerUid: String? = null,
)

sealed interface QuickAddResult {
    /** One entry, or two for a transfer. */
    data class Ready(val entries: List<Transaction>) : QuickAddResult
    data class Invalid(val reason: String) : QuickAddResult
}

/** Turns a draft into entries. The Save button stays off until this returns Ready. */
object QuickAdd {
    /** The id of Cashew's built-in balance-correction/transfer category (DECISIONS D36, to verify on the real file). */
    const val TRANSFER_CATEGORY_ID = "0"

    fun build(draft: QuickAddDraft, accounts: List<Account>, newId: () -> String): QuickAddResult {
        val typed = AmountExpression.evaluate(draft.amountRaw) ?: return QuickAddResult.Invalid("Enter an amount.")
        if (typed.isZero) return QuickAddResult.Invalid("Enter an amount above zero.")
        val account = accounts.firstOrNull { it.id == draft.accountId } ?: return QuickAddResult.Invalid("Pick an account.")

        val rate: BigDecimal? = draft.rate?.let { Exchange.parseRate(it) ?: return QuickAddResult.Invalid("The exchange rate is not a number.") }
        val inAccount = if (rate != null) Exchange.convert(typed, rate) else typed
        if (inAccount.isZero) return QuickAddResult.Invalid("That converts to zero.")

        return when (draft.type) {
            EntryType.Transfer -> {
                val to = accounts.firstOrNull { it.id == draft.toAccountId } ?: return QuickAddResult.Invalid("Pick the account it goes to.")
                if (to.id == account.id) return QuickAddResult.Invalid("Pick two different accounts.")
                // A transfer's rate (if any) converts the source amount into the destination account's currency.
                val toAmount = if (rate != null) inAccount else typed
                val sourceAmount = typed
                QuickAddResult.Ready(Transfers.create(account, to, sourceAmount, toAmount, draft.dateMs, draft.note, draft.ownerUid, newId))
            }
            EntryType.Expense, EntryType.Income -> {
                val categoryId = draft.categoryId ?: return QuickAddResult.Invalid("Pick a category.")
                val income = draft.type == EntryType.Income
                val signed = if (income) inAccount.value else -inAccount.value
                QuickAddResult.Ready(
                    listOf(
                        Transaction(
                            id = newId(), pairedTransactionId = null, name = draft.title.trim(), amount = signed, currency = account.currency,
                            note = draft.note, categoryId = categoryId, subCategoryId = null, accountId = account.id, dateMs = draft.dateMs,
                            modifiedAtMs = draft.dateMs, originalDueMs = null, income = income, periodLength = null, repeat = null,
                            endDateMs = null, upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null,
                            skipPaid = false, methodAdded = null, objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(),
                            ownerUid = draft.ownerUid, isPrivate = false, householdSyncedAtMs = null,
                            originalAmount = if (rate != null) typed.value * (if (income) 1 else -1) else null,
                            originalCurrency = if (rate != null) draft.originalCurrency else null,
                            exchangeRate = if (rate != null) rate.toPlainString() else null,
                        ),
                    ),
                )
            }
        }
    }
}

/** Suggestions while typing a title, from the titles memory (Paddhu's `AssociatedTitles`). */
object TitleSuggest {
    /** Titles that start with [typed] (case-insensitive), best first; an exact-match entry only matches the whole title. */
    fun suggest(typed: String, memory: List<TitleMemory>, limit: Int = 5): List<TitleMemory> {
        val q = typed.trim()
        if (q.isEmpty()) return emptyList()
        return memory.asSequence()
            .filter { if (it.exactMatch) it.title.equals(q, ignoreCase = true) else it.title.startsWith(q, ignoreCase = true) }
            .sortedBy { it.sortOrder }
            .take(limit).toList()
    }

    /** The category remembered for exactly this title, so choosing it fills the category in. */
    fun categoryFor(title: String, memory: List<TitleMemory>): String? =
        memory.firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }?.categoryId
}

object CategoryOrder {
    /** Top-level categories of the right kind, most recently used first; unused ones keep their own order. */
    fun recentFirst(categories: List<Category>, entries: List<Transaction>, income: Boolean): List<Category> {
        val lastUse = HashMap<String, Long>()
        for (t in entries) lastUse.merge(t.categoryId, t.dateMs) { a, b -> maxOf(a, b) }
        return categories.asSequence()
            .filter { it.mainCategoryId == null && it.income == income && !it.archived }
            .sortedWith(compareByDescending<Category> { lastUse[it.id] ?: Long.MIN_VALUE }.thenBy { it.sortOrder })
            .toList()
    }
}
