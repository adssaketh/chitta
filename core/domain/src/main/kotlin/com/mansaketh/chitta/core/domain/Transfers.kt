package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise

/**
 * A transfer between two of your own accounts is two entries that point at each other: money out of one
 * account, money into the other. Neither counts as spending or income (section 12). Deleting one leg
 * deletes both, so a balance is never left half-moved.
 */
object Transfers {
    fun create(
        from: Account,
        to: Account,
        sourceAmount: Paise,
        destinationAmount: Paise,
        atMs: Long,
        note: String,
        ownerUid: String?,
        newId: () -> String,
    ): List<Transaction> {
        require(sourceAmount.value > 0 && destinationAmount.value > 0) { "transfer amounts must be positive" }
        val outId = newId()
        val inId = newId()
        val crossCurrency = from.currency != to.currency && sourceAmount != destinationAmount
        fun leg(id: String, other: String, name: String, account: Account, amount: Long, income: Boolean) = Transaction(
            id = id, pairedTransactionId = other, name = name, amount = amount, currency = account.currency, note = note,
            categoryId = QuickAdd.TRANSFER_CATEGORY_ID, subCategoryId = null, accountId = account.id, dateMs = atMs, modifiedAtMs = atMs,
            originalDueMs = null, income = income, periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null,
            kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null,
            objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = ownerUid, isPrivate = false, householdSyncedAtMs = null,
            originalAmount = if (crossCurrency && income) sourceAmount.value else null,
            originalCurrency = if (crossCurrency && income) from.currency else null,
            exchangeRate = null,
        )
        return listOf(
            leg(outId, inId, "Transfer to ${to.name}", from, -sourceAmount.value, income = false),
            leg(inId, outId, "Transfer from ${from.name}", to, destinationAmount.value, income = true),
        )
    }

    /** Both legs of the transfer [entry] belongs to (or just [entry] when it is not a transfer or its twin is missing). */
    fun legs(entry: Transaction, all: List<Transaction>): List<Transaction> {
        val twinId = entry.pairedTransactionId ?: return listOf(entry)
        val twin = all.firstOrNull { it.id == twinId } ?: return listOf(entry)
        return listOf(entry, twin)
    }
}
