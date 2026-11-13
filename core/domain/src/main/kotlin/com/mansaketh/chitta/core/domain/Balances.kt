package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise

/**
 * Which entries move money, and the figures built from them.
 *
 * Sign convention (checked on Paddhu's data): expenses are stored negative, income positive; lent and
 * borrowed entries carry the sign of the money that moved.
 *
 * **Assumption to verify (DECISIONS D35):** a normal entry always counts; lent and borrowed entries count;
 * upcoming, subscription and repeating entries count only once paid and not skipped. This is my reading of
 * Cashew, not something I confirmed in Paddhu's source. The dry run prints every balance under this rule so you
 * can compare it with Paddhu's screen before anything is imported for real.
 */
object Balances {
    fun countsTowardBalance(t: Transaction): Boolean = when (t.kind) {
        null, TransactionKind.LENT, TransactionKind.BORROWED -> true
        TransactionKind.UPCOMING, TransactionKind.SUBSCRIPTION, TransactionKind.REPETITIVE -> t.paid && !t.skipPaid
    }

    fun balance(entries: List<Transaction>, accountId: String): Paise =
        entries.asSequence().filter { it.accountId == accountId && countsTowardBalance(it) }
            .fold(Paise.ZERO) { acc, t -> acc + Paise(t.amount) }

    fun balances(entries: List<Transaction>): Map<String, Paise> {
        val out = LinkedHashMap<String, Paise>()
        for (t in entries) if (countsTowardBalance(t)) out[t.accountId] = (out[t.accountId] ?: Paise.ZERO) + Paise(t.amount)
        return out
    }
}

/** Money in and out for a list of entries. [moneyOut] is a positive number; a refund reduces it. */
data class Flow(val moneyIn: Paise, val moneyOut: Paise) {
    val net: Paise get() = moneyIn - moneyOut
}

object Totals {
    /** A leg of a transfer between own accounts. */
    fun isTransfer(t: Transaction): Boolean = t.pairedTransactionId != null

    /** A balance correction: moves a balance to match reality, so it is neither spending nor income. */
    fun isBalanceCorrection(t: Transaction): Boolean = t.categoryId == QuickAdd.TRANSFER_CATEGORY_ID && t.pairedTransactionId == null

    /** Entries that never count as spending or income: transfers and balance corrections. */
    fun isNeutral(t: Transaction): Boolean = isTransfer(t) || isBalanceCorrection(t)

    /**
     * Totals for the month header and the Insights summary. Transfers between own accounts and balance corrections are left out;
     * lent and borrowed entries are left out (they have their own totals); upcoming entries count once paid.
     * An entry in an income category adds to money in (a negative one reduces it); every other entry adds to
     * money out, so a refund (positive, in an expense category) reduces what was spent (section 12).
     */
    fun of(entries: List<Transaction>, categories: List<Category>): Flow {
        val incomeCategory = categories.filter { it.income }.map { it.id }.toSet()
        var moneyIn = Paise.ZERO
        var moneyOut = Paise.ZERO
        for (t in entries) {
            if (!Balances.countsTowardBalance(t) || isNeutral(t)) continue
            if (t.kind == TransactionKind.LENT || t.kind == TransactionKind.BORROWED) continue
            if (t.categoryId in incomeCategory) moneyIn += Paise(t.amount) else moneyOut -= Paise(t.amount)
        }
        return Flow(moneyIn, moneyOut)
    }

    /** What a day's total on the rule shows: the signed net of entries that count, transfers excluded. */
    fun dayNet(entries: List<Transaction>): Paise =
        entries.asSequence().filter { Balances.countsTowardBalance(it) && !isNeutral(it) }
            .filter { it.kind != TransactionKind.LENT && it.kind != TransactionKind.BORROWED }
            .fold(Paise.ZERO) { acc, t -> acc + Paise(t.amount) }
}
