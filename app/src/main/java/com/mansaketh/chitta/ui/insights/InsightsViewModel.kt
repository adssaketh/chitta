package com.mansaketh.chitta.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.domain.Bucket
import com.mansaketh.chitta.core.domain.CategoryChange
import com.mansaketh.chitta.core.domain.CategoryTotal
import com.mansaketh.chitta.core.domain.Comparison
import com.mansaketh.chitta.core.domain.Flow
import com.mansaketh.chitta.core.domain.History
import com.mansaketh.chitta.core.domain.HistoryPoint
import com.mansaketh.chitta.core.domain.Insights
import com.mansaketh.chitta.core.domain.NetWorth
import com.mansaketh.chitta.core.domain.NetWorthFigures
import com.mansaketh.chitta.core.domain.NetWorthPoint
import com.mansaketh.chitta.core.domain.Period
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.domain.Ranges
import com.mansaketh.chitta.core.domain.SpendingRange
import com.mansaketh.chitta.core.domain.SpendingSummary
import com.mansaketh.chitta.core.domain.SummaryFilter
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.UiPrefs
import com.mansaketh.chitta.di.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** The ranges offered on the Current tab. */
sealed interface RangeChoice {
    data object ThisMonth : RangeChoice
    data object Past30 : RangeChoice
    data object Past6Months : RangeChoice
    data object ThisYear : RangeChoice
    data object AllTime : RangeChoice
    data class PastDays(val days: Int) : RangeChoice
    data class Dates(val from: LocalDate, val to: LocalDate?) : RangeChoice
}

/** One main category opened up: its sub-categories and the entries behind the bar. */
data class DrillData(val categoryId: String, val income: Boolean, val parts: List<CategoryTotal>, val entries: List<Transaction>, val total: Paise)

data class InsightsUi(
    val loading: Boolean = true,
    val nowMs: Long = 0L,
    val zone: ZoneId = ZoneId.systemDefault(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val accountIds: Set<String> = emptySet(),
    val range: RangeChoice = RangeChoice.ThisMonth,
    val period: Period? = null,
    val comparison: Comparison? = null,
    val summary: SpendingSummary? = null,
    val previous: SpendingSummary? = null,
    val flow: List<HistoryPoint> = emptyList(),
    val flowBucket: Bucket = Bucket.Day,
    val bars: List<CategoryTotal> = emptyList(),
    val showIncome: Boolean = false,
    val changes: List<CategoryChange> = emptyList(),
    val drill: DrillData? = null,
    val history: List<HistoryPoint> = emptyList(),
    val historyBucket: Bucket = Bucket.Month,
    val historyAverage: Flow? = null,
    val cumulative: Boolean = false,
    val netNow: NetWorthFigures? = null,
    val netSeries: List<NetWorthPoint> = emptyList(),
    val hasEntries: Boolean = false,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    ledger: LedgerRepository,
    plan: PlanRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
    private val prefs: UiPrefs,
    private val clock: Clock,
) : ViewModel() {
    private data class Raw(val entries: List<Transaction>, val objectives: List<Objective>, val categories: List<Category>, val accounts: List<Account>)
    private data class Q(
        val range: RangeChoice, val accountIds: Set<String>, val income: Boolean, val bucket: Bucket, val cumulative: Boolean, val drill: String?, val firstDay: DayOfWeek,
    )

    private val range = MutableStateFlow<RangeChoice>(RangeChoice.ThisMonth)
    private val accountIds = MutableStateFlow<Set<String>>(emptySet())
    private val income = MutableStateFlow(false)
    private val bucket = MutableStateFlow(Bucket.Month)
    private val cumulative = MutableStateFlow(false)
    private val drill = MutableStateFlow<String?>(null)

    private val raw = combine(ledger.observeAll(), plan.observeObjectives(), categoryRepo.observeAll(), accountRepo.observeAll()) { e, o, c, a -> Raw(e, o, c, a) }
    private val q = combine(range, accountIds, income, bucket, cumulative) { r, a, i, b, c -> Triple(Triple(r, a, i), b, c) }
    private val query = combine(q, drill, prefs.firstDay) { t, d, f -> Q(t.first.first, t.first.second, t.first.third, t.second, t.third, d, f) }

    val ui: StateFlow<InsightsUi> = combine(raw, query) { r, qq -> compute(r, qq) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUi())

    fun setRange(v: RangeChoice) { range.value = v }
    fun setAccounts(v: Set<String>) { accountIds.value = v }
    fun showIncome(v: Boolean) { income.value = v }
    fun setBucket(v: Bucket) { bucket.value = v }
    fun setCumulative(v: Boolean) { cumulative.value = v }
    fun openDrill(categoryId: String?) { drill.update { categoryId } }

    private fun spendingRange(c: RangeChoice, now: Long, zone: ZoneId): SpendingRange {
        val today = Periods.date(now, zone)
        return when (c) {
            RangeChoice.ThisMonth -> SpendingRange.Cycle(Periods.startOfDay(LocalDate.of(2000, 1, 1), zone), Repeat.MONTHLY, 1)
            RangeChoice.Past30 -> SpendingRange.PastDays(30)
            RangeChoice.Past6Months -> SpendingRange.Dates(today.minusMonths(5).withDayOfMonth(1), null)
            RangeChoice.ThisYear -> SpendingRange.Dates(today.withDayOfYear(1), null)
            RangeChoice.AllTime -> SpendingRange.AllTime
            is RangeChoice.PastDays -> SpendingRange.PastDays(c.days)
            is RangeChoice.Dates -> SpendingRange.Dates(c.from, c.to)
        }
    }

    private fun compute(r: Raw, q: Q): InsightsUi {
        val now = clock.nowMs()
        val zone = clock.zone()
        val sr = spendingRange(q.range, now, zone)
        val period = Ranges.resolve(sr, r.entries, now, zone)
        val cmp = Ranges.comparison(sr, now, zone)
        val filter = SummaryFilter(q.accountIds)
        val summary = Insights.summary(r.entries, r.categories, period, now, zone, filter)
        val previous = cmp?.let { Insights.summary(r.entries, r.categories, it.previous, now, zone, filter) }

        // cash flow over the chosen range: the bucket that suits its length, from the bucket holding the first day to today
        val flowBucket = History.bucketFor(period, zone)
        val flow = History.series(r.entries, r.categories, flowBucket, bucketsFor(period, flowBucket, now, zone), now, zone, filter, q.firstDay)
            .filter { it.period.endMs > period.startMs && it.period.startMs < period.endMs }

        val bars = Insights.categoryTotals(r.entries, r.categories, period, q.income, filter)
        val changes = cmp?.let { Insights.changes(r.entries, r.categories, it, 5, filter) }.orEmpty()
        val drillData = q.drill?.let { id ->
            DrillData(
                id, q.income, Insights.drill(r.entries, r.categories, period, id, q.income, filter),
                Insights.entriesOf(r.entries, r.categories, period, id, q.income, filter).take(50),
                bars.firstOrNull { it.categoryId == id }?.amount ?: Paise.ZERO,
            )
        }

        val histCount = when (q.bucket) { Bucket.Day -> 30; Bucket.Week -> 12; Bucket.Month -> 12; Bucket.Year -> 5 }
        val history = History.series(r.entries, r.categories, q.bucket, histCount, now, zone, filter, q.firstDay)

        return InsightsUi(
            loading = false, nowMs = now, zone = zone, categories = r.categories, accounts = r.accounts, accountIds = q.accountIds, range = q.range,
            period = period, comparison = cmp, summary = summary, previous = previous, flow = flow, flowBucket = flowBucket, bars = bars,
            showIncome = q.income, changes = changes, drill = drillData, history = history, historyBucket = q.bucket, historyAverage = History.average(history),
            cumulative = q.cumulative, netNow = NetWorth.at(now, r.entries, r.accounts, r.objectives),
            netSeries = NetWorth.series(r.entries, r.accounts, r.objectives, now, zone, 12), hasEntries = r.entries.isNotEmpty(),
        )
    }

    /** How many buckets reach back from today to the first day of [period], with a little margin and a cap so a huge range stays light. */
    private fun bucketsFor(period: Period, bucket: Bucket, now: Long, zone: ZoneId): Int {
        val first = Periods.date(period.startMs, zone)
        val today = Periods.date(now, zone)
        val n = when (bucket) {
            Bucket.Day -> ChronoUnit.DAYS.between(first, today) + 1
            Bucket.Week -> ChronoUnit.WEEKS.between(first, today) + 2
            Bucket.Month -> ChronoUnit.MONTHS.between(first, today) + 2
            Bucket.Year -> ChronoUnit.YEARS.between(first, today) + 2
        }
        return n.coerceIn(1, 120).toInt()
    }
}
