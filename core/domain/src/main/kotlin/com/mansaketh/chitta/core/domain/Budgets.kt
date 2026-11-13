package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.CategoryLimit
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.BudgetFigures
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.PaceStatus
import com.mansaketh.chitta.core.money.Segments
import java.time.ZoneId

/** Everything the Plan screens show about one budget in one period. All money comes from tested functions (master section 12). */
data class BudgetSummary(
    val period: Period,
    val limit: Paise,
    val spent: Paise,
    val left: Paise,
    val daysInPeriod: Int,
    /** 1-based, today counts. 0 when the period has not started. */
    val dayOfPeriod: Int,
    val daysLeft: Int,
    val ideal: Paise?,
    val pace: PaceStatus,
    val forecast: Paise?,
    val limitDay: Int?,
    /** Money spent up to and including each day gone, for the month rule. */
    val cumulative: List<Paise>,
    val started: Boolean,
    val ended: Boolean,
)

data class CategoryLine(val categoryId: String, val spent: Paise, val limit: Paise?, val gauge: Segments.Gauge?)

/**
 * Which entries count toward a budget, and how much. This follows Paddhu's `onlyShowIfFollowsFilters` and
 * `watchTotalSpentInTimeRangeFromCategories` (read, not copied). DECISIONS D46.
 *
 *  - only entries that count toward balances (paid; see [Balances]);
 *  - a spending budget counts money out and an income budget counts money in, unless "include income" is on, in which case the other kind
 *    is counted against it. Paddhu decides by the entry's income flag; Chitta decides by the entry's category (an income-flag entry in an
 *    expense category is a refund and reduces spending, master section 12). Where the category is unknown the flag decides;
 *  - lent, borrowed and loan-linked entries only when "include debt and credit" is on;
 *  - entries linked to a goal count unless the filter list leaves "added to goal" off; the transfer / balance-correction category "0" only when "include balance correction" is on;
 *  - the budget's accounts and categories (a sub-category counts under its main category), and entries marked "exclude from this budget" never.
 *
 * Not carried over: "added only" budgets. Paddhu kept their link in the shared-budget columns that Chitta does not import (D10, D33),
 * so such a budget counts like a normal one. The screen says so.
 */
object Budgets {
    private val OFF_BY_DEFAULT = setOf(BudgetFilter.INCLUDE_INCOME, BudgetFilter.INCLUDE_DEBT_AND_CREDIT, BudgetFilter.INCLUDE_BALANCE_CORRECTION)

    /** Paddhu's `isFilterSelectedWithDefaults`: no list means everything is on; the "default" marker turns the three optional ones off. */
    fun filterOn(filters: List<BudgetFilter>?, f: BudgetFilter): Boolean = when {
        filters == null -> true
        BudgetFilter.DEFAULT_FILTERS in filters -> f !in OFF_BY_DEFAULT
        else -> f in filters
    }

    private fun mainOf(categoryId: String, categories: List<Category>): String {
        val c = categories.firstOrNull { it.id == categoryId } ?: return categoryId
        return c.mainCategoryId ?: c.id
    }

    private fun matches(set: List<String>?, t: Transaction, categories: List<Category>): Boolean {
        if (set == null) return true
        return t.categoryId in set || t.subCategoryId in set || mainOf(t.categoryId, categories) in set
    }

    /** What [t] adds to the budget's spent figure, in paise. 0 when it does not count. Positive means more spent (or more saved, for an income budget). */
    fun contribution(t: Transaction, budget: Budget, categories: List<Category>): Long {
        if (!Balances.countsTowardBalance(t)) return 0
        val f = budget.filters
        if (budget.id in t.excludeFromBudgetIds) return 0
        val accounts = budget.accountIds
        if (accounts != null && t.accountId !in accounts) return 0
        if (!matches(budget.categoryIds, t, categories)) return 0
        val excluded = budget.excludedCategoryIds
        if (excluded != null && matches(excluded, t, categories)) return 0
        val incomeSide = categories.firstOrNull { it.id == t.categoryId }?.income ?: t.income
        if (!filterOn(f, BudgetFilter.INCLUDE_INCOME) && incomeSide != budget.income) return 0
        val debt = t.kind == TransactionKind.LENT || t.kind == TransactionKind.BORROWED || t.objectiveLoanId != null
        if (debt && !filterOn(f, BudgetFilter.INCLUDE_DEBT_AND_CREDIT)) return 0
        if (t.objectiveId != null && !filterOn(f, BudgetFilter.ADDED_TO_OBJECTIVE)) return 0
        if (t.categoryId == QuickAdd.TRANSFER_CATEGORY_ID && !filterOn(f, BudgetFilter.INCLUDE_BALANCE_CORRECTION)) return 0
        return if (budget.income) t.amount else -t.amount
    }

    fun spent(budget: Budget, period: Period, entries: List<Transaction>, categories: List<Category>): Paise {
        var sum = 0L
        for (t in entries) if (t.dateMs in period) sum = Math.addExact(sum, contribution(t, budget, categories))
        return Paise(sum)
    }

    fun periodAt(budget: Budget, nowMs: Long, zone: ZoneId): Period =
        Periods.budgetPeriod(budget.startMs, budget.endMs, budget.repeat, budget.periodLength, nowMs, zone)

    fun earlier(budget: Budget, nowMs: Long, zone: ZoneId, count: Int = 12): List<Period> =
        Periods.earlierPeriods(budget.startMs, budget.repeat, budget.periodLength, nowMs, zone, count)

    /** Figures for one period. For a finished period everything is as at its last day, so the forecast equals what was spent. */
    fun summarize(budget: Budget, period: Period, entries: List<Transaction>, categories: List<Category>, nowMs: Long, zone: ZoneId): BudgetSummary {
        val limit = Paise(budget.amount)
        val days = Periods.daysIn(period, zone)
        val day = Periods.dayOfPeriod(period, nowMs, zone)
        val first = Periods.date(period.startMs, zone)
        val perDay = LongArray(days)
        for (t in entries) {
            if (t.dateMs !in period) continue
            val idx = java.time.temporal.ChronoUnit.DAYS.between(first, Periods.date(t.dateMs, zone)).toInt().coerceIn(0, days - 1)
            perDay[idx] = Math.addExact(perDay[idx], contribution(t, budget, categories))
        }
        val cumulative = ArrayList<Paise>(day)
        var running = 0L
        for (i in 0 until day) { running += perDay[i]; cumulative += Paise(running) }
        val spent = Paise(running)
        val ideal = BudgetFigures.idealSpend(limit, day, days)
        return BudgetSummary(
            period = period, limit = limit, spent = spent, left = limit - spent, daysInPeriod = days, dayOfPeriod = day, daysLeft = days - day,
            ideal = ideal, pace = BudgetFigures.paceStatus(spent, ideal, limit), forecast = BudgetFigures.forecast(spent, day, days),
            limitDay = BudgetFigures.limitDay(spent, limit, day, days), cumulative = cumulative,
            started = nowMs >= period.startMs, ended = nowMs >= period.endMs,
        )
    }

    /**
     * The limit of one category line. In a budget with absolute limits the stored amount is paise. Otherwise it is a percentage of the budget,
     * stored as percent × 100 (25% is 2500; the importer reads Paddhu's 25.0 as 2500 paise), so ₹60,000 × 25% = ₹15,000.
     */
    fun limitFor(budget: Budget, limit: CategoryLimit): Paise =
        if (budget.absoluteSpendingLimit) Paise(limit.amount) else Paise(mulDivRound(budget.amount, limit.amount, 10_000))

    /** Spending by main category, biggest first, with the category's limit and gauge where one is set. Categories with a limit but no spending are listed too. */
    fun breakdown(budget: Budget, period: Period, entries: List<Transaction>, categories: List<Category>, limits: List<CategoryLimit>): List<CategoryLine> {
        val byCategory = LinkedHashMap<String, Long>()
        for (t in entries) {
            if (t.dateMs !in period) continue
            val c = contribution(t, budget, categories)
            if (c == 0L) continue
            val key = mainOf(t.categoryId, categories)
            byCategory[key] = Math.addExact(byCategory[key] ?: 0L, c)
        }
        val mine = limits.filter { it.budgetId == budget.id }
        for (l in mine) byCategory.putIfAbsent(l.categoryId, 0L)
        return byCategory.map { (id, sum) ->
            val limit = mine.firstOrNull { it.categoryId == id }?.let { limitFor(budget, it) }
            CategoryLine(id, Paise(sum), limit, limit?.let { Segments.gauge(Paise(sum), it) })
        }.sortedByDescending { it.spent.value }
    }

    /** Spent per earlier period, for the history list: (period, spent, limit). */
    fun history(budget: Budget, entries: List<Transaction>, categories: List<Category>, nowMs: Long, zone: ZoneId, count: Int = 12): List<Triple<Period, Paise, Paise>> =
        earlier(budget, nowMs, zone, count).map { Triple(it, spent(budget, it, entries, categories), Paise(budget.amount)) }

    /** Total of unpaid bills due inside [period] that count toward a spending budget: what Safe to spend (Phase 6) subtracts. Transfers are not bills. */
    fun billsDue(budget: Budget, period: Period, entries: List<Transaction>, categories: List<Category>): Paise {
        var sum = 0L
        for (t in entries) {
            if (!Bills.isOpen(t) || t.dateMs !in period || t.pairedTransactionId != null || t.categoryId == QuickAdd.TRANSFER_CATEGORY_ID) continue
            // an unpaid bill would count if it were paid: test it as if it were
            val asPaid = t.copy(paid = true)
            sum += contribution(asPaid, budget, categories)
        }
        return Paise(sum.coerceAtLeast(0))
    }
}
