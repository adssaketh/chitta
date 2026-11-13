package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
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
 * Not included yet: fixed deposits, mutual funds, other assets and instalment-loan balances (Phase 8), and card dues read from SMS (Phase 7).
 * A credit card kept as an account already counts through its negative balance.
 */
data class NetWorthFigures(val accounts: Paise, val owedToYou: Paise, val youOwe: Paise, val otherCurrencyAccounts: Int) {
    val total: Paise get() = accounts + owedToYou - youOwe
}

/** One point of the net-worth-over-time chart: the figures at the end of [month], or now for the current month. */
data class NetWorthPoint(val month: YearMonth, val asOfMs: Long, val figures: NetWorthFigures)

object NetWorth {
    fun isRupee(a: Account) = a.currency == null || a.currency.equals("INR", ignoreCase = true)

    /** Net worth as of [asOfMs]: only entries dated up to then are counted, so a past month shows what the book said that day. */
    fun at(asOfMs: Long, entries: List<Transaction>, accounts: List<Account>, objectives: List<Objective>): NetWorthFigures {
        val known = entries.filter { it.dateMs <= asOfMs }
        val rupee = accounts.filter(::isRupee).map { it.id }.toSet()
        var balance = Paise.ZERO
        for ((id, b) in Balances.balances(known)) if (id in rupee) balance += b

        var owed = Paise.ZERO
        var owe = Paise.ZERO
        for (o in objectives) {
            if (o.kind != ObjectiveKind.LOAN || o.archived) continue
            val outstanding = Loans.progress(o, known).outstanding
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
        return NetWorthFigures(balance, owed, owe, accounts.count { !isRupee(it) })
    }

    /** The last [months] month ends, oldest first; the last point is now. */
    fun series(entries: List<Transaction>, accounts: List<Account>, objectives: List<Objective>, nowMs: Long, zone: ZoneId, months: Int): List<NetWorthPoint> {
        require(months > 0) { "months must be positive" }
        val current = YearMonth.from(Periods.date(nowMs, zone))
        return (months - 1 downTo 0).map { back ->
            val ym = current.minusMonths(back.toLong())
            val asOf = if (back == 0) nowMs else Periods.startOfDay(ym.plusMonths(1).atDay(1), zone) - 1
            NetWorthPoint(ym, asOf, at(asOf, entries, accounts, objectives))
        }
    }
}
