package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.BudgetFigures
import com.mansaketh.chitta.core.money.Paise
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The time ranges of the All Spending Summary (master section 7): all time, a repeating cycle, the past N days, or a date range
 * whose end may be left open.
 */
sealed interface SpendingRange {
    data object AllTime : SpendingRange

    /** A repeating cycle such as the calendar month. [startMs] is when the first cycle began; the cycle that holds today is used. */
    data class Cycle(val startMs: Long, val repeat: Repeat, val length: Int = 1) : SpendingRange

    /** Today and the [days] - 1 days before it. */
    data class PastDays(val days: Int) : SpendingRange

    /** Both dates are inclusive. A null [to] means "up to today". */
    data class Dates(val from: LocalDate, val to: LocalDate?) : SpendingRange
}

/** A period and the one it is compared with ("1 to 12 Nov, compared with 1 to 12 Oct"). Both end after the last day counted. */
data class Comparison(val current: Period, val previous: Period)

object Ranges {
    private const val DAY_MS = 86_400_000L

    /** The period a range means today. Ends are exclusive. All time starts at the first entry and ends after the last one (or tomorrow). */
    fun resolve(range: SpendingRange, entries: List<Transaction>, nowMs: Long, zone: ZoneId): Period {
        val today = Periods.date(nowMs, zone)
        val tomorrowStart = Periods.startOfDay(today.plusDays(1), zone)
        return when (range) {
            SpendingRange.AllTime -> {
                val first = entries.minOfOrNull { it.dateMs } ?: Periods.startOfDay(today, zone)
                val last = entries.maxOfOrNull { it.dateMs } ?: nowMs
                Period(minOf(first, Periods.startOfDay(today, zone)), maxOf(tomorrowStart, last + 1))
            }
            is SpendingRange.Cycle -> {
                require(range.repeat != Repeat.CUSTOM) { "A cycle must repeat." }
                val k = Periods.indexAt(range.startMs, range.repeat, range.length, nowMs, zone)
                Periods.periodAtIndex(range.startMs, range.repeat, range.length, k, zone)
            }
            is SpendingRange.PastDays -> {
                val n = range.days.coerceAtLeast(1)
                Period(Periods.startOfDay(today.minusDays(n - 1L), zone), tomorrowStart)
            }
            is SpendingRange.Dates -> {
                val start = Periods.startOfDay(range.from, zone)
                val end = Periods.startOfDay((range.to ?: today).plusDays(1), zone)
                Period(start, if (end > start) end else Periods.startOfDay(range.from.plusDays(1), zone))
            }
        }
    }

    /**
     * What a range is compared with. A cycle is compared to-date with the same days of the cycle before it; a past-N-days or date range with
     * the stretch of the same length just before it. All time has no comparison.
     */
    fun comparison(range: SpendingRange, nowMs: Long, zone: ZoneId): Comparison? = when (range) {
        SpendingRange.AllTime -> null
        is SpendingRange.Cycle -> {
            val k = Periods.indexAt(range.startMs, range.repeat, range.length, nowMs, zone)
            if (k == 0) null else {
                val cur = Periods.periodAtIndex(range.startMs, range.repeat, range.length, k, zone)
                val prev = Periods.periodAtIndex(range.startMs, range.repeat, range.length, k - 1, zone)
                val today = Periods.date(nowMs, zone)
                val elapsed = (ChronoUnit.DAYS.between(Periods.date(cur.startMs, zone), today) + 1).coerceAtLeast(1)
                val prevStartDate = Periods.date(prev.startMs, zone)
                val prevEnd = minOf(prev.endMs, Periods.startOfDay(prevStartDate.plusDays(elapsed), zone))
                Comparison(Period(cur.startMs, minOf(cur.endMs, Periods.startOfDay(today.plusDays(1), zone))), Period(prev.startMs, maxOf(prevEnd, prev.startMs + 1)))
            }
        }
        is SpendingRange.PastDays -> {
            val cur = resolve(range, emptyList(), nowMs, zone)
            val n = range.days.coerceAtLeast(1).toLong()
            Comparison(cur, Period(Periods.startOfDay(Periods.date(cur.startMs, zone).minusDays(n), zone), cur.startMs))
        }
        is SpendingRange.Dates -> {
            val cur = resolve(range, emptyList(), nowMs, zone)
            val n = ChronoUnit.DAYS.between(Periods.date(cur.startMs, zone), Periods.date(cur.endMs - 1, zone)) + 1
            Comparison(cur, Period(Periods.startOfDay(Periods.date(cur.startMs, zone).minusDays(n), zone), cur.startMs))
        }
    }
}

/**
 * The Current tab of the All Spending Summary. Every field is a total over entries in the period (and, when [SummaryFilter.accountIds] is not
 * empty, in those accounts). Rules, all in one place so the screen only draws them:
 *  - [expense] and [income] come from [Totals.of]: transfers, balance corrections and lent/borrowed entries are left out, a refund reduces its
 *    category, upcoming entries count once paid;
 *  - [net] is income minus expense; [savedPercent] is net as a share of income, null when nothing came in;
 *  - [balanceCorrection] is the signed sum of corrections (what was added to or removed from balances to match reality);
 *  - [lent] and [borrowed] are the paid one-time and loan entries of that kind, as positive amounts;
 *  - [upcoming] is what is still to pay on open bills dated today or later, [overdue] on open bills dated before today. Money to receive
 *    reduces them, so both are net of income bills.
 */
data class SpendingSummary(
    val period: Period,
    val expense: Paise,
    val income: Paise,
    val net: Paise,
    val savedPercent: Int?,
    val balanceCorrection: Paise,
    val lent: Paise,
    val borrowed: Paise,
    val upcoming: Paise,
    val overdue: Paise,
    val entryCount: Int,
)

data class CategoryTotal(val categoryId: String, val amount: Paise)

/** One category's change against the comparison period. [percent] is null when it had nothing before ("new"). */
data class CategoryChange(val categoryId: String, val current: Paise, val previous: Paise, val percent: Int?)

/** Which entries a summary looks at besides the period. Empty [accountIds] means every account. */
data class SummaryFilter(val accountIds: Set<String> = emptySet())

object Insights {
    private fun mainOf(categoryId: String, categories: List<Category>): String {
        val c = categories.firstOrNull { it.id == categoryId } ?: return categoryId
        return c.mainCategoryId ?: c.id
    }

    /** True when an entry is on the income side: its category says so, or (category unknown) its amount is positive, as on the Ledger. */
    private fun incomeSide(t: Transaction, categories: List<Category>): Boolean =
        categories.firstOrNull { it.id == t.categoryId }?.income ?: (t.amount > 0)

    private fun inScope(entries: List<Transaction>, period: Period, filter: SummaryFilter): List<Transaction> =
        entries.filter { it.dateMs in period && (filter.accountIds.isEmpty() || it.accountId in filter.accountIds) }

    fun summary(entries: List<Transaction>, categories: List<Category>, period: Period, nowMs: Long, zone: ZoneId, filter: SummaryFilter = SummaryFilter()): SpendingSummary {
        val inRange = inScope(entries, period, filter)
        val flow = Totals.of(inRange, categories)
        val todayStart = Periods.startOfDay(Periods.date(nowMs, zone), zone)
        var correction = 0L
        var lent = 0L
        var borrowed = 0L
        var upcoming = 0L
        var overdue = 0L
        for (t in inRange) {
            if (Totals.isBalanceCorrection(t) && Balances.countsTowardBalance(t)) correction = Math.addExact(correction, t.amount)
            if (Balances.countsTowardBalance(t) && !Totals.isTransfer(t)) {
                if (t.kind == TransactionKind.LENT) lent = Math.addExact(lent, -t.amount)
                if (t.kind == TransactionKind.BORROWED) borrowed = Math.addExact(borrowed, t.amount)
            }
            if (Bills.isOpen(t) && !Totals.isNeutral(t)) {
                if (t.dateMs >= todayStart) upcoming = Math.addExact(upcoming, -t.amount) else overdue = Math.addExact(overdue, -t.amount)
            }
        }
        val net = flow.net
        val saved = if (flow.moneyIn.value > 0) mulDivRound(net.value, 100, flow.moneyIn.value).toInt() else null
        return SpendingSummary(
            period, flow.moneyOut, flow.moneyIn, net, saved, Paise(correction), Paise(lent), Paise(borrowed), Paise(upcoming), Paise(overdue), inRange.size,
        )
    }

    /**
     * Totals by main category, biggest first. [income] picks the income side. Entries counted are the ones [Totals.of] counts, so a refund reduces
     * its category. A category that nets to zero is left out; one that nets below zero (refunds larger than spending) is kept with its negative amount.
     */
    fun categoryTotals(entries: List<Transaction>, categories: List<Category>, period: Period, income: Boolean, filter: SummaryFilter = SummaryFilter()): List<CategoryTotal> {
        val sums = LinkedHashMap<String, Long>()
        for (t in inScope(entries, period, filter)) {
            if (!countsInTotals(t) || incomeSide(t, categories) != income) continue
            val key = mainOf(t.categoryId, categories)
            sums[key] = Math.addExact(sums[key] ?: 0L, if (income) t.amount else -t.amount)
        }
        return sums.filterValues { it != 0L }.map { CategoryTotal(it.key, Paise(it.value)) }.sortedWith(compareByDescending<CategoryTotal> { it.amount.value }.thenBy { it.categoryId })
    }

    /** What [Totals.of] counts: not neutral, not lent/borrowed, and moving money (paid when scheduled). */
    private fun countsInTotals(t: Transaction): Boolean =
        Balances.countsTowardBalance(t) && !Totals.isNeutral(t) && t.kind != TransactionKind.LENT && t.kind != TransactionKind.BORROWED

    /** Drill-down: one main category split by sub-category, biggest first. Entries with no sub-category are listed under the main category itself. */
    fun drill(entries: List<Transaction>, categories: List<Category>, period: Period, mainId: String, income: Boolean, filter: SummaryFilter = SummaryFilter()): List<CategoryTotal> {
        val sums = LinkedHashMap<String, Long>()
        for (t in inScope(entries, period, filter)) {
            if (!countsInTotals(t) || incomeSide(t, categories) != income || mainOf(t.categoryId, categories) != mainId) continue
            val own = categories.firstOrNull { it.id == t.categoryId }
            val key = t.subCategoryId ?: if (own?.mainCategoryId != null) own.id else mainId
            sums[key] = Math.addExact(sums[key] ?: 0L, if (income) t.amount else -t.amount)
        }
        return sums.filterValues { it != 0L }.map { CategoryTotal(it.key, Paise(it.value)) }.sortedWith(compareByDescending<CategoryTotal> { it.amount.value }.thenBy { it.categoryId })
    }

    /** The entries behind one category bar (or one sub-category), newest first, for the drill-down list. */
    fun entriesOf(entries: List<Transaction>, categories: List<Category>, period: Period, categoryId: String, income: Boolean, filter: SummaryFilter = SummaryFilter()): List<Transaction> =
        inScope(entries, period, filter).filter { t ->
            countsInTotals(t) && incomeSide(t, categories) == income &&
                (mainOf(t.categoryId, categories) == categoryId || t.categoryId == categoryId || t.subCategoryId == categoryId)
        }.sortedWith(compareByDescending<Transaction> { it.dateMs }.thenBy { it.id })

    /**
     * Biggest changes against the comparison period: spending by main category now and before, ordered by the size of the change in rupees,
     * with the change as a percentage of what it was (rounded half away from zero). A category with nothing before has no percentage.
     */
    fun changes(entries: List<Transaction>, categories: List<Category>, cmp: Comparison, limit: Int = 5, filter: SummaryFilter = SummaryFilter()): List<CategoryChange> {
        val now = categoryTotals(entries, categories, cmp.current, income = false, filter = filter).associate { it.categoryId to it.amount.value }
        val before = categoryTotals(entries, categories, cmp.previous, income = false, filter = filter).associate { it.categoryId to it.amount.value }
        return (now.keys + before.keys).map { id ->
            val c = now[id] ?: 0L
            val p = before[id] ?: 0L
            CategoryChange(id, Paise(c), Paise(p), if (p > 0) mulDivSigned(c - p, 100, p).toInt() else null)
        }.filter { it.current != it.previous }
            .sortedWith(compareByDescending<CategoryChange> { Math.abs(it.current.value - it.previous.value) }.thenBy { it.categoryId })
            .take(limit)
    }

    private fun mulDivSigned(value: Long, numerator: Long, denominator: Long): Long =
        if (value >= 0) mulDivRound(value, numerator, denominator) else -mulDivRound(-value, numerator, denominator)
}

enum class Bucket { Day, Week, Month, Year }

/** One bar of the History tab and the By month chart. [projectedOut] is set only on the current bucket of a monthly series, from day 7. */
data class HistoryPoint(
    val start: LocalDate,
    val period: Period,
    val moneyIn: Paise,
    val moneyOut: Paise,
    val net: Paise,
    val cumulativeNet: Paise,
    val projectedOut: Paise?,
    val isCurrent: Boolean,
)

object History {
    private fun bucketStart(d: LocalDate, b: Bucket, firstDay: DayOfWeek): LocalDate = when (b) {
        Bucket.Day -> d
        Bucket.Week -> d.with(TemporalAdjusters.previousOrSame(firstDay))
        Bucket.Month -> d.withDayOfMonth(1)
        Bucket.Year -> d.withDayOfYear(1)
    }

    private fun next(d: LocalDate, b: Bucket): LocalDate = when (b) {
        Bucket.Day -> d.plusDays(1)
        Bucket.Week -> d.plusWeeks(1)
        Bucket.Month -> d.plusMonths(1)
        Bucket.Year -> d.plusYears(1)
    }

    private fun prev(d: LocalDate, b: Bucket): LocalDate = when (b) {
        Bucket.Day -> d.minusDays(1)
        Bucket.Week -> d.minusWeeks(1)
        Bucket.Month -> d.minusMonths(1)
        Bucket.Year -> d.minusYears(1)
    }

    /**
     * The last [count] buckets, oldest first, ending with the one that holds today. Each has money in and out as on the Summary
     * ([Totals.of]), their net, and a running net from the first bucket shown (the cumulative view).
     */
    fun series(
        entries: List<Transaction>, categories: List<Category>, bucket: Bucket, count: Int, nowMs: Long, zone: ZoneId,
        filter: SummaryFilter = SummaryFilter(), firstDay: DayOfWeek = DayOfWeek.MONDAY,
    ): List<HistoryPoint> {
        require(count > 0) { "count must be positive" }
        val today = Periods.date(nowMs, zone)
        val currentStart = bucketStart(today, bucket, firstDay)
        val starts = ArrayList<LocalDate>(count)
        var s = currentStart
        repeat(count) { starts += s; s = prev(s, bucket) }
        starts.reverse()
        val inScope = entries.filter { filter.accountIds.isEmpty() || it.accountId in filter.accountIds }
        var running = 0L
        return starts.map { start ->
            val end = next(start, bucket)
            val period = Period(Periods.startOfDay(start, zone), Periods.startOfDay(end, zone))
            val flow = Totals.of(inScope.filter { it.dateMs in period }, categories)
            running = Math.addExact(running, flow.net.value)
            val isCurrent = start == currentStart
            val projected = if (isCurrent && bucket == Bucket.Month) {
                val ym = YearMonth.from(start)
                BudgetFigures.forecast(flow.moneyOut, today.dayOfMonth, ym.lengthOfMonth())
            } else null
            HistoryPoint(start, period, flow.moneyIn, flow.moneyOut, flow.net, Paise(running), projected, isCurrent)
        }
    }

    /**
     * The average money out and in over the finished buckets (the current one is still filling, so it is left out). Null when no bucket
     * has finished. Rounded half away from zero.
     */
    fun average(points: List<HistoryPoint>): Flow? {
        val done = points.filter { !it.isCurrent }
        if (done.isEmpty()) return null
        val n = done.size.toLong()
        return Flow(
            Paise(mulDivRound(done.sumOf { it.moneyIn.value }, 1, n)),
            Paise(mulDivRound(done.sumOf { it.moneyOut.value }, 1, n)),
        )
    }

    /** The bucket that suits a period: days up to about two months, weeks up to a year, months beyond that. */
    fun bucketFor(period: Period, zone: ZoneId): Bucket {
        val days = Periods.daysIn(period, zone)
        return when {
            days <= 62 -> Bucket.Day
            days <= 366 -> Bucket.Week
            else -> Bucket.Month
        }
    }
}

/** One day of the spending heatmap. [level] is 0 (nothing spent) to 4 (the heaviest days). A null [date] is padding before the 1st. */
data class HeatCell(val date: LocalDate?, val spent: Paise, val level: Int)

object Heatmap {
    const val LEVELS = 4

    /**
     * A month as weeks of seven cells, starting on [firstDay] (a setting). Spending is what [Totals.of] calls money out for that day.
     * Level = the share of the month's biggest day, rounded up into four steps; a day with nothing spent (or only refunds) is level 0.
     * Days after today are drawn but empty.
     */
    fun month(
        entries: List<Transaction>, categories: List<Category>, month: YearMonth, firstDay: DayOfWeek, zone: ZoneId,
        filter: SummaryFilter = SummaryFilter(),
    ): List<List<HeatCell>> {
        val scope = entries.filter { filter.accountIds.isEmpty() || it.accountId in filter.accountIds }
        val byDay = scope.groupBy { Periods.date(it.dateMs, zone) }.filterKeys { YearMonth.from(it) == month }
            .mapValues { (_, list) -> Totals.of(list, categories).moneyOut.value.coerceAtLeast(0) }
        val max = byDay.values.maxOrNull() ?: 0L
        val first = month.atDay(1)
        val pad = Math.floorMod(first.dayOfWeek.value - firstDay.value, 7)
        val cells = ArrayList<HeatCell>()
        repeat(pad) { cells += HeatCell(null, Paise.ZERO, 0) }
        for (d in 1..month.lengthOfMonth()) {
            val date = month.atDay(d)
            val spent = byDay[date] ?: 0L
            val level = if (spent <= 0 || max <= 0) 0 else ((spent * LEVELS + max - 1) / max).toInt().coerceIn(1, LEVELS)
            cells += HeatCell(date, Paise(spent), level)
        }
        while (cells.size % 7 != 0) cells += HeatCell(null, Paise.ZERO, 0)
        return cells.chunked(7)
    }
}
