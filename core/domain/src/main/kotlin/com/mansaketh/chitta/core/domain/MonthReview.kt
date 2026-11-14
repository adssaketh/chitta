package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.YearMonth
import java.time.ZoneId

/** How one budget did in the stretch that ended inside the month. [limit] includes anything rolled in. A budget is hit when spent is not above the limit. */
data class BudgetResult(val budget: Budget, val period: Period, val spent: Paise, val limit: Paise) {
    val hit: Boolean get() = spent <= limit
    /** How far over the limit it went; zero when hit. */
    val over: Paise get() = if (hit) Paise.ZERO else spent - limit
    /** What was left; zero when missed. */
    val left: Paise get() = if (hit) limit - spent else Paise.ZERO
}

/**
 * One finished month at a glance (Phase 8f, master section 8 "Month-in-review"). Every figure is a total from tested functions:
 *  - [moneyIn], [moneyOut], [net] and [savedPercent] are `Insights.summary` over the calendar month, all accounts, so transfers, balance
 *    corrections and lent or borrowed entries are left out and a refund reduces its category;
 *  - [previous] is the same for the month before, null when that month has no entries at all (so nothing is compared with an empty month);
 *  - [up] and [down] are the biggest category changes against the month before, by rupees, each at least [MonthReviews.MIN_CHANGE];
 *  - [budgets] are repeating or one-time spending budgets whose period ended inside the month and has finished, missed ones first.
 */
data class MonthReview(
    val month: YearMonth,
    val period: Period,
    val moneyIn: Paise,
    val moneyOut: Paise,
    val net: Paise,
    val savedPercent: Int?,
    val previousMonth: YearMonth,
    val previous: Flow?,
    val up: List<CategoryChange>,
    val down: List<CategoryChange>,
    val budgets: List<BudgetResult>,
    val entryCount: Int,
)

object MonthReviews {
    /** A category must move by at least this much to be listed, so small changes do not crowd the list. */
    val MIN_CHANGE: Paise = Paise.rupees(500)

    /** How many categories are listed going up and going down. */
    const val LIST_SIZE = 3

    /** Home shows the one-line link on the first this many days of a month. */
    const val HOME_DAYS = 3

    fun periodOf(month: YearMonth, zone: ZoneId): Period =
        Period(Periods.startOfDay(month.atDay(1), zone), Periods.startOfDay(month.plusMonths(1).atDay(1), zone))

    /** Finished months that have at least one entry, newest first. The month that holds today is not finished, so it is not listed. */
    fun months(entries: List<Transaction>, nowMs: Long, zone: ZoneId): List<YearMonth> {
        val current = YearMonth.from(Periods.date(nowMs, zone))
        return entries.map { YearMonth.from(Periods.date(it.dateMs, zone)) }.filter { it < current }.distinct().sortedDescending()
    }

    /** The month Home links to: the one before this month, during the first [HOME_DAYS] days, when it has entries. Otherwise null. */
    fun homeMonth(entries: List<Transaction>, nowMs: Long, zone: ZoneId): YearMonth? {
        val today = Periods.date(nowMs, zone)
        if (today.dayOfMonth > HOME_DAYS) return null
        val previous = YearMonth.from(today).minusMonths(1)
        return previous.takeIf { it in months(entries, nowMs, zone) }
    }

    fun review(
        month: YearMonth, entries: List<Transaction>, categories: List<Category>, budgets: List<Budget>, nowMs: Long, zone: ZoneId,
    ): MonthReview {
        val period = periodOf(month, zone)
        val previousMonth = month.minusMonths(1)
        val previousPeriod = periodOf(previousMonth, zone)
        val summary = Insights.summary(entries, categories, period, nowMs, zone)
        val previousCount = entries.count { it.dateMs in previousPeriod }
        val previous = if (previousCount == 0) null else Insights.summary(entries, categories, previousPeriod, nowMs, zone).let { Flow(it.income, it.expense) }

        var up = emptyList<CategoryChange>()
        var down = emptyList<CategoryChange>()
        if (previous != null) {
            val all = Insights.changes(entries, categories, Comparison(period, previousPeriod), limit = Int.MAX_VALUE)
            fun delta(c: CategoryChange) = c.current.value - c.previous.value
            up = all.filter { delta(it) >= MIN_CHANGE.value }.sortedWith(compareByDescending<CategoryChange> { delta(it) }.thenBy { it.categoryId }).take(LIST_SIZE)
            down = all.filter { -delta(it) >= MIN_CHANGE.value }.sortedWith(compareByDescending<CategoryChange> { -delta(it) }.thenBy { it.categoryId }).take(LIST_SIZE)
        }

        return MonthReview(
            month = month, period = period, moneyIn = summary.income, moneyOut = summary.expense, net = summary.net, savedPercent = summary.savedPercent,
            previousMonth = previousMonth, previous = previous, up = up, down = down,
            budgets = budgetResults(period, entries, categories, budgets, nowMs, zone), entryCount = summary.entryCount,
        )
    }

    /**
     * Spending budgets whose period ended inside [month] and has finished by [nowMs]. A budget that repeats monthly from the 15th is
     * judged in the review of the month its period ended in. Where several periods ended in the month (a weekly budget) the last one is used.
     * Savings budgets are left out: "hit or missed" is about a limit on spending. Archived budgets are left out.
     */
    private fun budgetResults(month: Period, entries: List<Transaction>, categories: List<Category>, budgets: List<Budget>, nowMs: Long, zone: ZoneId): List<BudgetResult> {
        val results = ArrayList<BudgetResult>()
        for (b in budgets) {
            if (b.income || b.archived) continue
            val period = lastPeriodEndingIn(b, month, zone) ?: continue
            if (period.endMs > nowMs) continue
            val s = Budgets.summarize(b, period, entries, categories, nowMs, zone)
            results += BudgetResult(b, period, s.spent, s.limit)
        }
        return results.sortedWith(compareBy<BudgetResult> { it.hit }.thenBy { it.budget.name.lowercase() }.thenBy { it.budget.id })
    }

    private fun lastPeriodEndingIn(b: Budget, month: Period, zone: ZoneId): Period? {
        fun endsIn(p: Period) = p.endMs > month.startMs && p.endMs <= month.endMs
        val repeat = b.repeat
        if (repeat == null || repeat == Repeat.CUSTOM) {
            val p = Period(b.startMs, maxOf(b.endMs, b.startMs + 1))
            return p.takeIf { endsIn(it) }
        }
        val k = Periods.indexAt(b.startMs, repeat, b.periodLength, month.endMs - 1, zone)
        val here = Periods.periodAtIndex(b.startMs, repeat, b.periodLength, k, zone)
        if (endsIn(here) && here.startMs >= b.startMs) return here
        if (k == 0) return null
        return Periods.periodAtIndex(b.startMs, repeat, b.periodLength, k - 1, zone).takeIf { endsIn(it) }
    }
}
