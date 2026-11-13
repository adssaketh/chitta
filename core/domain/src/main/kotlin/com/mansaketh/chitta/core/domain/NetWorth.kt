package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.YearMonth
import java.time.ZoneId

/**
 * Net worth in rupees at one moment (master section 12: included assets minus included liabilities).
 *  - [accounts]: the balances of every rupee account, archived ones too (money in a hidden account is still money). Accounts in another
 *    currency are not added in (DECISIONS D43); [otherCurrencyAccounts] says how many were left out so the screen can say so.
 *  - [owedToYou]: what is still to be collected on loans you lent, long-term and one-time. Lending moves money out of an account, so this
 *    puts it back: lending changes nothing about net worth.
 *  - [youOwe]: what is still to be paid on loans you borrowed.
 *  - [deposits]: open fixed deposits at today's value, only when the person switched that on ([NetWorthExtras.includeDeposits]); [depositCount] says how many.
 *  - instalment loans (loans with terms): when [NetWorthExtras.includeInstalments] is on, what is still to pay by the schedule (interest and GST included,
 *    as the Plan screen shows it) replaces that loan's plain figure in [owedToYou] / [youOwe], so nothing is counted twice.
 * Not included yet: mutual funds and other assets (Phase 8 Should items). A credit card kept as an account already counts through its negative
 * balance; card dues read from SMS feed the card cockpit, not Net worth, so the same purchase is never counted twice.
 */
data class NetWorthFigures(
    val accounts: Paise, val owedToYou: Paise, val youOwe: Paise, val otherCurrencyAccounts: Int,
    val deposits: Paise = Paise.ZERO, val depositCount: Int = 0,
) {
    val total: Paise get() = accounts + deposits + owedToYou - youOwe
}

/** Loan terms and deposits, and which of them Net worth counts. Deposits are off until switched on; instalment balances are on. */
data class NetWorthExtras(
    val terms: List<LoanTerms> = emptyList(), val deposits: List<FixedDeposit> = emptyList(),
    val includeDeposits: Boolean = false, val includeInstalments: Boolean = true,
) { companion object { val NONE = NetWorthExtras() } }

/** One point of the net-worth-over-time chart: the figures at the end of [month], or now for the current month. */
data class NetWorthPoint(val month: YearMonth, val asOfMs: Long, val figures: NetWorthFigures)

object NetWorth {
    fun isRupee(a: Account) = a.currency == null || a.currency.equals("INR", ignoreCase = true)

    /** Net worth as of [asOfMs]: only entries dated up to then are counted, so a past month shows what the book said that day. */
    fun at(
        asOfMs: Long, entries: List<Transaction>, accounts: List<Account>, objectives: List<Objective>,
        extras: NetWorthExtras = NetWorthExtras.NONE, zone: ZoneId = ZoneId.systemDefault(),
    ): NetWorthFigures {
        val known = entries.filter { it.dateMs <= asOfMs }
        val termsOf = if (extras.includeInstalments) extras.terms.associateBy { it.objectiveId } else emptyMap()
        val rupee = accounts.filter(::isRupee).map { it.id }.toSet()
        var balance = Paise.ZERO
        for ((id, b) in Balances.balances(known)) if (id in rupee) balance += b

        var owed = Paise.ZERO
        var owe = Paise.ZERO
        for (o in objectives) {
            if (o.kind != ObjectiveKind.LOAN || o.archived) continue
            val outstanding = termsOf[o.id]?.let { instalmentOutstanding(o, it, known, asOfMs, zone) } ?: Loans.progress(o, known).outstanding
            if (o.income) owed += outstanding else owe += outstanding
        }
        for (t in known) {
            if (!Loans.isOneTime(t) || !t.paid) continue
            if (t.accountId !in rupee) continue
            when (t.kind) {
                TransactionKind.LENT -> owed += Paise(-t.amount)
                TransactionKind.BORROWED -> owe += Paise(t.amount)
                else -> Unit
            }
        }
        val open = if (extras.includeDeposits) extras.deposits.filter { !it.closed && it.startMs <= asOfMs } else emptyList()
        val deposits = open.fold(Paise.ZERO) { a, d -> a + FixedDeposits.currentValue(d, asOfMs, zone) }
        return NetWorthFigures(balance, owed, owe, accounts.count { !isRupee(it) }, deposits, open.size)
    }

    /**
     * What is still to pay on an instalment loan as of [asOfMs]: only instalments due by then count as ticked, and only payments dated by then.
     * Null before the loan existed (its first linked entry, or a month before its first instalment), so the history chart does not owe a loan
     * that had not been taken. Then the caller falls back to the plain figure, which is zero for an empty loan.
     */
    private fun instalmentOutstanding(o: Objective, terms: LoanTerms, known: List<Transaction>, asOfMs: Long, zone: ZoneId): Paise? {
        val linked = known.filter { it.objectiveLoanId == o.id && it.paid }
        val began = linked.minOfOrNull { it.dateMs } ?: (terms.firstEmiMs - 31L * 86_400_000L)
        if (asOfMs < began) return null
        val dueByThen = Instalments.schedule(terms, zone).filter { it.dueMs <= asOfMs }.map { it.number }.toSet()
        val asThen = terms.copy(paidInstalments = terms.paidInstalments.filter { it in dueByThen })
        return Instalments.progress(asThen, linked.sumOf { it.amount }, zone).outstanding
    }

    /** The last [months] month ends, oldest first; the last point is now. */
    fun series(
        entries: List<Transaction>, accounts: List<Account>, objectives: List<Objective>, nowMs: Long, zone: ZoneId, months: Int,
        extras: NetWorthExtras = NetWorthExtras.NONE,
    ): List<NetWorthPoint> {
        require(months > 0) { "months must be positive" }
        val current = YearMonth.from(Periods.date(nowMs, zone))
        return (months - 1 downTo 0).map { back ->
            val ym = current.minusMonths(back.toLong())
            val asOf = if (back == 0) nowMs else Periods.startOfDay(ym.plusMonths(1).atDay(1), zone) - 1
            NetWorthPoint(ym, asOf, at(asOf, entries, accounts, objectives, extras, zone))
        }
    }
}
