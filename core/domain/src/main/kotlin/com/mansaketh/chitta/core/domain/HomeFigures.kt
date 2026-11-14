package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.SinkingFund
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.BudgetFigures
import com.mansaketh.chitta.core.money.CashCheck
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.RangeResult
import com.mansaketh.chitta.core.money.RingTicks
import com.mansaketh.chitta.core.money.Segments
import java.time.ZoneId

/**
 * Everything the top of Home shows for one budget (master sections 3 and 12). All money comes from [BudgetFigures] and [Segments].
 *  - [safe] = budget − spent − unpaid bills due this period that count toward the budget − the sinking funds' set-aside for the period + spending they cover (negative when over);
 *  - [perDay] = safe ÷ days left, null when nothing is safe to spend;
 *  - [range] = safe ÷ average daily spending this period (D4), and [rangeShortDays] says how many days short of the period end it is;
 *  - [ring] is the 50-tick ring of the same three figures.
 */
data class HeroData(
    val budget: Budget,
    val summary: BudgetSummary,
    val billsDue: Paise,
    /** Sinking funds (Phase 8e): this period's set-aside, taken off [safe], and spending the funds already paid for, added back. */
    val sinking: SinkingEffect,
    val safe: Paise,
    val perDay: Paise?,
    val range: RangeResult,
    val rangeShortDays: Int?,
    val ring: RingTicks?,
)

object HomeFigures {
    /** A cash-check balance is an ESTIMATE when no entry has touched the account for this many days (master section 12). */
    const val STALE_DAYS = 7

    /**
     * The budget Home leads with: a spending budget whose period holds today. [preferredId] (the budget picked in the top bar) wins when it
     * qualifies, then a pinned budget, then the first in the user's order.
     */
    fun currentBudget(budgets: List<Budget>, nowMs: Long, zone: ZoneId, preferredId: String? = null): Budget? {
        val live = budgets.filter { !it.archived && !it.income && nowMs in Budgets.periodAt(it, nowMs, zone) }
        return live.firstOrNull { it.id == preferredId }
            ?: live.sortedWith(compareByDescending<Budget> { it.pinned }.thenBy { it.sortOrder }.thenBy { it.name }).firstOrNull()
    }

    /** Budgets that can be picked in the top bar, in the same order [currentBudget] prefers. */
    fun selectableBudgets(budgets: List<Budget>, nowMs: Long, zone: ZoneId): List<Budget> =
        budgets.filter { !it.archived && !it.income && nowMs in Budgets.periodAt(it, nowMs, zone) }
            .sortedWith(compareByDescending<Budget> { it.pinned }.thenBy { it.sortOrder }.thenBy { it.name })

    fun hero(budget: Budget, entries: List<Transaction>, categories: List<Category>, nowMs: Long, zone: ZoneId, funds: List<SinkingFund> = emptyList()): HeroData {
        val period = Budgets.periodAt(budget, nowMs, zone)
        val summary = Budgets.summarize(budget, period, entries, categories, nowMs, zone)
        val bills = Budgets.billsDue(budget, period, entries, categories)
        val limit = summary.limit
        val sinking = SinkingFunds.effect(funds, period, entries, categories, zone) { Budgets.contribution(it, budget, categories) != 0L }
        val safe = BudgetFigures.safeToSpend(limit, summary.spent, bills) + sinking.covered - sinking.reserved
        val perDay = if (safe.value > 0) BudgetFigures.perDay(safe, summary.daysLeft) else null
        val range = BudgetFigures.range(safe, summary.spent, summary.dayOfPeriod)
        val short = (range as? RangeResult.Days)?.let { (summary.daysLeft - it.days).takeIf { d -> d > 0 } }
        val ringSpent = Paise((summary.spent.value - sinking.covered.value).coerceAtLeast(0))
        return HeroData(budget, summary, bills, sinking, safe, perDay, range, short, Segments.ring(limit, ringSpent, bills + sinking.reserved))
    }

    /** All open money-out bills due inside [period], whatever budget they belong to. Card statements are bills here; transfers are not. */
    fun billsOut(entries: List<Transaction>, period: Period): Paise {
        var sum = 0L
        for (t in entries) {
            if (!Bills.isOpen(t) || t.dateMs !in period || Totals.isNeutral(t) || t.amount >= 0) continue
            sum = Math.addExact(sum, -t.amount)
        }
        return Paise(sum)
    }

    /**
     * This month's goal savings for the cash check: what each savings goal that has an end date still needs per month to get there (the Phase 5
     * "per month" figure). Goals without an end date, already reached, or past their end add nothing. DECISIONS D57.
     */
    fun goalSavings(objectives: List<Objective>, entries: List<Transaction>, nowMs: Long, zone: ZoneId): Paise {
        var sum = Paise.ZERO
        for (o in objectives) {
            if (o.kind != ObjectiveKind.GOAL || o.archived || !o.income) continue
            val need = Goals.progress(o, entries, nowMs, zone).perMonthNeeded ?: continue
            sum += need
        }
        return sum
    }

    /** The cash check for the accounts marked "for spending". Null when none is marked (the screen asks the user to mark some). Rupee accounts only (D43). */
    fun cashCheck(entries: List<Transaction>, accounts: List<Account>, forSpending: Set<String>, period: Period, goalSavings: Paise, sinking: Paise = Paise.ZERO): CashCheck? {
        val ids = accounts.filter { it.id in forSpending && NetWorth.isRupee(it) }.map { it.id }.toSet()
        if (ids.isEmpty()) return null
        var balances = Paise.ZERO
        for ((id, b) in Balances.balances(entries)) if (id in ids) balances += b
        return CashCheck(balances, billsOut(entries, period), goalSavings, sinking)
    }

    /**
     * Accounts marked "for spending" whose balance may be out of date: nothing has been entered on them for [STALE_DAYS] days. The cash check
     * then carries an ESTIMATE stamp. (Balances are worked out from entries, so a quiet account is the closest thing to a hand-kept balance.)
     */
    fun staleSpendingAccounts(entries: List<Transaction>, accounts: List<Account>, forSpending: Set<String>, nowMs: Long, zone: ZoneId): List<Account> {
        val today = Periods.date(nowMs, zone)
        val latest = HashMap<String, Long>()
        for (t in entries) if (t.dateMs <= nowMs) latest.merge(t.accountId, t.dateMs, ::maxOf)
        return accounts.filter { it.id in forSpending && NetWorth.isRupee(it) }.filter { a ->
            val last = latest[a.id] ?: return@filter true
            java.time.temporal.ChronoUnit.DAYS.between(Periods.date(last, zone), today) > STALE_DAYS
        }
    }

    /** The open bill that comes first, overdue ones before upcoming ones. Money-in entries count (a salary is a thing coming up). Null when nothing is open. */
    fun nextBill(entries: List<Transaction>): Transaction? =
        entries.filter { Bills.isOpen(it) && !Totals.isNeutral(it) }.minWithOrNull(compareBy<Transaction> { it.dateMs }.thenBy { it.id })

    /** Open bills still to come (not overdue) after [nowMs], soonest first, for the Coming up list. */
    fun comingUp(entries: List<Transaction>, limit: Int): List<Transaction> =
        entries.filter { Bills.isOpen(it) && !Totals.isNeutral(it) }.sortedWith(compareBy<Transaction> { it.dateMs }.thenBy { it.id }).take(limit)
}
