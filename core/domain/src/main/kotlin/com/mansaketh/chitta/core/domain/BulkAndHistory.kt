package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise

/** What a bulk edit may change. A null field is left alone. */
data class BulkChange(
    val categoryId: String? = null,
    val subCategoryId: String? = null,
    val accountId: String? = null,
    val dateMs: Long? = null,
    val note: String? = null,
)

data class BulkResult(val changed: List<Transaction>, val skipped: List<Transaction>)

object BulkEdit {
    /**
     * Applies [change] to [selected]. Transfer legs are skipped when the change would move a leg to another
     * account or category (that would break the pair); their dates and notes can change, and both legs follow.
     */
    fun apply(selected: List<Transaction>, all: List<Transaction>, change: BulkChange, nowMs: Long): BulkResult {
        val changed = LinkedHashMap<String, Transaction>()
        val skipped = ArrayList<Transaction>()
        for (t in selected) {
            val isLeg = t.pairedTransactionId != null
            if (isLeg && (change.categoryId != null || change.subCategoryId != null || change.accountId != null)) { skipped.add(t); continue }
            fun edit(e: Transaction) = e.copy(
                categoryId = change.categoryId ?: e.categoryId,
                subCategoryId = if (change.categoryId != null) change.subCategoryId else (change.subCategoryId ?: e.subCategoryId),
                accountId = change.accountId ?: e.accountId,
                dateMs = change.dateMs ?: e.dateMs,
                note = change.note ?: e.note,
                modifiedAtMs = nowMs,
            )
            changed[t.id] = edit(t)
            if (isLeg && (change.dateMs != null || change.note != null)) {
                all.firstOrNull { it.id == t.pairedTransactionId }?.let { twin -> if (twin.id !in changed) changed[twin.id] = edit(twin) }
            }
        }
        return BulkResult(changed.values.toList(), skipped)
    }
}

/** One account's balance before and after deleting some entries. */
data class BalanceShift(val account: Account, val before: Paise, val after: Paise)

data class DeleteImpact(
    /** The entries that will go: the selection plus the other leg of every selected transfer. */
    val toDelete: List<Transaction>,
    val extraTransferLegs: Int,
    val balanceShifts: List<BalanceShift>,
    val linkedToGoalOrLoan: Int,
)

object Deletion {
    /** What a delete changes, to show before the person confirms (section 13). */
    fun impact(selected: List<Transaction>, all: List<Transaction>, accounts: List<Account>): DeleteImpact {
        val ids = LinkedHashMap<String, Transaction>()
        var extra = 0
        for (t in selected) {
            ids[t.id] = t
            for (leg in Transfers.legs(t, all)) if (leg.id !in ids) { ids[leg.id] = leg; if (leg.id != t.id) extra++ }
        }
        val remaining = all.filter { it.id !in ids }
        val before = Balances.balances(all)
        val after = Balances.balances(remaining)
        val shifts = accounts.filter { a -> (before[a.id] ?: Paise.ZERO) != (after[a.id] ?: Paise.ZERO) }
            .map { BalanceShift(it, before[it.id] ?: Paise.ZERO, after[it.id] ?: Paise.ZERO) }
        val linked = ids.values.count { it.objectiveId != null || it.objectiveLoanId != null }
        return DeleteImpact(ids.values.toList(), extra, shifts, linked)
    }
}

/** One field that differs between two versions of an entry. */
data class FieldChange(val field: String, val before: String, val after: String)

object EditDiff {
    /** The fields a person can see change, in the order the detail screen lists them. */
    fun between(before: Transaction, after: Transaction): List<FieldChange> = buildList {
        fun add(field: String, a: Any?, b: Any?) { if (a != b) add(FieldChange(field, a?.toString() ?: "", b?.toString() ?: "")) }
        add("Title", before.name, after.name)
        add("Amount", before.amount, after.amount)
        add("Category", before.categoryId, after.categoryId)
        add("Subcategory", before.subCategoryId, after.subCategoryId)
        add("Account", before.accountId, after.accountId)
        add("Date", before.dateMs, after.dateMs)
        add("Note", before.note, after.note)
        add("Paid", before.paid, after.paid)
        add("Private", before.isPrivate, after.isPrivate)
    }
}
