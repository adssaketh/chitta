package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.SinkingFund
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.YearMonth
import java.time.ZoneId

/** What sinking funds do to one budget period: [reserved] is taken off Safe to spend, [covered] is added back for spending the funds already paid for. */
data class SinkingEffect(val reserved: Paise, val covered: Paise) {
    /** The change to Safe to spend (never positive on its own: covering a payment only cancels spending that is already counted). */
    val net: Paise get() = covered - reserved

    companion object { val NONE = SinkingEffect(Paise.ZERO, Paise.ZERO) }
}

/** One fund's figures at a moment, for its screen. */
data class FundStatus(
    val fund: SinkingFund,
    /** Set aside so far, through the current month (paper money, nothing moved). */
    val setAside: Paise,
    /** Spent in the linked category since the fund started (refunds reduce it). */
    val drawn: Paise,
    /** [setAside] minus [drawn]. Negative when more was paid than was set aside. */
    val balance: Paise,
    /** This month's share; zero before the start, after the target month, or once [paid]. */
    val thisMonth: Paise,
    /** Whole months still to set aside for, counting this one; 0 once the target month has passed. */
    val monthsLeft: Int,
    val months: Int,
    /** The cost has been paid: drawn reached the target. The fund stops reserving. */
    val paid: Boolean,
    val started: Boolean,
    val past: Boolean,
)

/**
 * Sinking funds (Phase 8e). The target is spread over the calendar months from the start month to the target month, both included:
 * the money set aside through month m (0 = start month) is floor(target × (m + 1) ÷ n), and the last month reaches the target exactly, so
 * the shares add up to the target to the paisa with the larger shares last. DECISIONS D125 to D128.
 */
object SinkingFunds {
    private fun ym(ms: Long, zone: ZoneId) = YearMonth.from(Periods.date(ms, zone))

    fun months(f: SinkingFund, zone: ZoneId): Int =
        (java.time.temporal.ChronoUnit.MONTHS.between(ym(f.startMs, zone), ym(f.targetDateMs, zone)).toInt() + 1).coerceAtLeast(1)

    /** Set aside through month index [m] (0 = the start month). Zero before the start, the whole target from the last month on. */
    fun cumulative(f: SinkingFund, m: Int, n: Int): Long =
        if (m < 0) 0L else if (m + 1 >= n) f.targetPaise else Math.multiplyExact(f.targetPaise, (m + 1).toLong()) / n

    /** This month's share. Zero outside the start-to-target months. */
    fun monthShare(f: SinkingFund, m: Int, n: Int): Long = if (m < 0 || m >= n) 0L else cumulative(f, m, n) - cumulative(f, m - 1, n)

    private fun monthIndex(f: SinkingFund, ms: Long, zone: ZoneId): Int =
        java.time.temporal.ChronoUnit.MONTHS.between(ym(f.startMs, zone), ym(ms, zone)).toInt()

    /** Spending in the fund's category from the fund's start up to (not including) [untilMs]; refunds reduce it. Paid entries only. */
    fun drawn(f: SinkingFund, entries: List<Transaction>, categories: List<Category>, fromMs: Long, untilMs: Long, counted: (Transaction) -> Boolean = { true }): Long {
        val id = f.categoryId ?: return 0L
        var sum = 0L
        for (t in entries) {
            if (t.dateMs < maxOf(fromMs, f.startMs) || t.dateMs >= untilMs) continue
            if (!Balances.countsTowardBalance(t) || t.pairedTransactionId != null || t.categoryId == QuickAdd.TRANSFER_CATEGORY_ID) continue
            if (!Budgets.matches(listOf(id), t, categories) || !counted(t)) continue
            sum = Math.subtractExact(sum, t.amount) // expenses are stored negative, so spending adds
        }
        return sum
    }

    fun status(f: SinkingFund, entries: List<Transaction>, categories: List<Category>, nowMs: Long, zone: ZoneId): FundStatus {
        val n = months(f, zone)
        val m = monthIndex(f, nowMs, zone)
        val setAside = cumulative(f, m, n)
        val drawn = drawn(f, entries, categories, f.startMs, Long.MAX_VALUE)
        val paid = f.categoryId != null && drawn >= f.targetPaise
        return FundStatus(
            fund = f, setAside = Paise(setAside), drawn = Paise(drawn), balance = Paise(setAside - drawn),
            thisMonth = Paise(if (paid) 0 else monthShare(f, m, n)), monthsLeft = (n - m).coerceIn(0, n), months = n, paid = paid,
            started = m >= 0, past = m >= n,
        )
    }

    /**
     * The effect of every active fund on one budget [period] (usually a month).
     *
     *  - reserved: each month the period touches contributes its share × the days of the period inside that month ÷ the days in that month,
     *    rounded half away from zero per fund. A calendar-month period gets the exact share. A paid fund reserves nothing.
     *  - covered: spending in the fund's category inside the period is already in "spent", but the fund set that money aside earlier, so it is
     *    added back, up to what the fund held at the start of the period plus this period's reserve. Money paid beyond that counts as ordinary
     *    spending. Only entries that [counted] (the ones that are in the budget's spent figure) can be covered.
     */
    fun effect(funds: List<SinkingFund>, period: Period, entries: List<Transaction>, categories: List<Category>, zone: ZoneId, counted: (Transaction) -> Boolean = { true }): SinkingEffect {
        var reserved = 0L
        var covered = 0L
        val first = Periods.date(period.startMs, zone)
        val last = Periods.date(period.endMs - 1, zone)
        for (f in funds) {
            if (f.archived) continue
            val n = months(f, zone)
            val paidTotal = f.categoryId != null && drawn(f, entries, categories, f.startMs, Long.MAX_VALUE) >= f.targetPaise
            var r = 0L
            if (!paidTotal) {
                var month = YearMonth.from(first)
                val lastMonth = YearMonth.from(last)
                while (month <= lastMonth) {
                    val share = monthShare(f, monthIndex(f, Periods.startOfDay(month.atDay(1), zone), zone), n)
                    if (share != 0L) {
                        val from = maxOf(first, month.atDay(1))
                        val to = minOf(last, month.atEndOfMonth())
                        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
                        r = Math.addExact(r, mulDivRound(share, days, month.lengthOfMonth().toLong()))
                    }
                    month = month.plusMonths(1)
                }
            }
            reserved = Math.addExact(reserved, r)
            if (f.categoryId != null) {
                val inPeriod = drawn(f, entries, categories, period.startMs, period.endMs, counted).coerceAtLeast(0)
                if (inPeriod > 0) {
                    val before = cumulative(f, monthIndex(f, period.startMs, zone) - 1, n) - drawn(f, entries, categories, f.startMs, period.startMs)
                    val held = maxOf(0L, before) + r
                    covered = Math.addExact(covered, minOf(inPeriod, held))
                }
            }
        }
        return SinkingEffect(Paise(reserved), Paise(covered))
    }
}
