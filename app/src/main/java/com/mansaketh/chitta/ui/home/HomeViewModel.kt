package com.mansaketh.chitta.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.PlanRepository
import com.mansaketh.chitta.core.domain.Balances
import com.mansaketh.chitta.core.domain.Budgets
import com.mansaketh.chitta.core.domain.BudgetSummary
import com.mansaketh.chitta.core.domain.CategoryTotal
import com.mansaketh.chitta.core.domain.DayGroup
import com.mansaketh.chitta.core.domain.GoalProgress
import com.mansaketh.chitta.core.domain.Goals
import com.mansaketh.chitta.core.domain.HeatCell
import com.mansaketh.chitta.core.domain.Heatmap
import com.mansaketh.chitta.core.domain.HeroData
import com.mansaketh.chitta.core.domain.HomeFigures
import com.mansaketh.chitta.core.domain.HomeLayout
import com.mansaketh.chitta.core.domain.HomeSection
import com.mansaketh.chitta.core.domain.Insights
import com.mansaketh.chitta.core.domain.LedgerFilter
import com.mansaketh.chitta.core.domain.LedgerGrouping
import com.mansaketh.chitta.core.domain.LedgerSearch
import com.mansaketh.chitta.core.domain.LoanProgress
import com.mansaketh.chitta.core.domain.Loans
import com.mansaketh.chitta.core.domain.NetWorth
import com.mansaketh.chitta.core.domain.NetWorthExtras
import com.mansaketh.chitta.data.NetWorthInputs
import com.mansaketh.chitta.core.domain.NetWorthFigures
import com.mansaketh.chitta.core.domain.NetWorthPoint
import com.mansaketh.chitta.core.domain.Period
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.domain.Ranges
import com.mansaketh.chitta.core.domain.SectionConfig
import com.mansaketh.chitta.core.domain.SpendingRange
import com.mansaketh.chitta.core.domain.SummaryFilter
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.CashCheck
import com.mansaketh.chitta.core.money.HeroFigures
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.UiPrefs
import com.mansaketh.chitta.di.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class BudgetRowData(val budget: Budget, val summary: BudgetSummary)

/** What one optional section of Home shows. Built only for sections that are switched on. */
sealed interface SectionData {
    data class BudgetsData(val rows: List<BudgetRowData>) : SectionData
    data class BalancesData(val rows: List<Pair<Account, Paise>>, val total: Paise, val otherCurrency: Int) : SectionData
    data class RecentData(val days: List<DayGroup>) : SectionData
    data class NetWorthData(val now: NetWorthFigures, val series: List<NetWorthPoint>) : SectionData
    data class GoalsData(val rows: List<Pair<Objective, GoalProgress>>) : SectionData
    data class LoansData(val rows: List<Pair<Objective, LoanProgress>>) : SectionData
    data class SpendingData(val bars: List<CategoryTotal>, val days: Int) : SectionData
    data class HeatmapData(val month: YearMonth, val weeks: List<List<HeatCell>>) : SectionData
}

data class HomeUi(
    val loading: Boolean = true,
    val nowMs: Long = 0L,
    val zone: ZoneId = ZoneId.systemDefault(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /** The budget the top of Home is about. Null when no spending budget runs today. */
    val hero: HeroData? = null,
    val budgetOptions: List<Budget> = emptyList(),
    val cash: CashCheck? = null,
    val cashStale: Boolean = false,
    val choice: HeroFigures.Choice? = null,
    val coming: List<Transaction> = emptyList(),
    /** Open money-out bills due this period (the figure beside "Coming up"). */
    val comingTotal: Paise = Paise.ZERO,
    val period: Period? = null,
    val layout: HomeLayout = HomeLayout.DEFAULT,
    val sections: Map<HomeSection, SectionData> = emptyMap(),
    val hasEntries: Boolean = false,
    val preferCash: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    ledger: LedgerRepository,
    plan: PlanRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
    val prefs: UiPrefs,
    netWorthInputs: NetWorthInputs,
    private val clock: Clock,
) : ViewModel() {
    private data class Raw(val entries: List<Transaction>, val budgets: List<Budget>, val objectives: List<Objective>, val categories: List<Category>, val accounts: List<Account>, val extras: NetWorthExtras = NetWorthExtras.NONE)
    private data class P(val layout: HomeLayout, val cash: Set<String>, val preferCash: Boolean, val heroId: String?, val firstDay: DayOfWeek)

    private val raw = combine(combine(ledger.observeAll(), plan.observeBudgets(), plan.observeObjectives(), categoryRepo.observeAll(), accountRepo.observeAll()) { e, b, o, c, a -> Raw(e, b, o, c, a) }, netWorthInputs.extras) { r, x -> r.copy(extras = x) }
    private val choices = combine(prefs.homeLayout, prefs.cashAccounts, prefs.preferCash, prefs.heroBudgetId, prefs.firstDay) { l, c, pc, h, f -> P(l, c, pc, h, f) }

    val ui: StateFlow<HomeUi> = combine(raw, choices) { r, p -> compute(r, p) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun pickBudget(id: String?) = prefs.setHeroBudgetId(id)

    private fun compute(r: Raw, p: P): HomeUi {
        val now = clock.nowMs()
        val zone = clock.zone()
        val budget = HomeFigures.currentBudget(r.budgets, now, zone, p.heroId)
        val hero = budget?.let { HomeFigures.hero(it, r.entries, r.categories, now, zone) }
        val period = hero?.summary?.period ?: monthPeriod(now, zone)
        val goalSavings = HomeFigures.goalSavings(r.objectives, r.entries, now, zone)
        val cash = HomeFigures.cashCheck(r.entries, r.accounts, p.cash, period, goalSavings)
        val stale = cash != null && HomeFigures.staleSpendingAccounts(r.entries, r.accounts, p.cash, now, zone).isNotEmpty()
        val choice = HeroFigures.choose(hero?.safe, cash, p.preferCash)
        val sections = p.layout.shown.associate { cfg -> cfg.section to section(cfg, r, now, zone, p.firstDay) }
        return HomeUi(
            loading = false, nowMs = now, zone = zone, categories = r.categories, accounts = r.accounts, hero = hero,
            budgetOptions = HomeFigures.selectableBudgets(r.budgets, now, zone), cash = cash, cashStale = stale, choice = choice,
            coming = HomeFigures.comingUp(r.entries, 3), comingTotal = HomeFigures.billsOut(r.entries, period), period = period,
            layout = p.layout, sections = sections, hasEntries = r.entries.isNotEmpty(), preferCash = p.preferCash,
        )
    }

    private fun monthPeriod(now: Long, zone: ZoneId): Period {
        val ym = YearMonth.from(Periods.date(now, zone))
        return Period(Periods.startOfDay(ym.atDay(1), zone), Periods.startOfDay(ym.plusMonths(1).atDay(1), zone))
    }

    private fun section(cfg: SectionConfig, r: Raw, now: Long, zone: ZoneId, firstDay: DayOfWeek): SectionData {
        val filter = SummaryFilter(cfg.accountIds)
        return when (cfg.section) {
            HomeSection.Budgets -> {
                val live = r.budgets.filter { !it.archived }
                val chosen = live.filter { it.pinned }.ifEmpty { live.take(3) }
                SectionData.BudgetsData(chosen.map { b -> BudgetRowData(b, Budgets.summarize(b, Budgets.periodAt(b, now, zone), r.entries, r.categories, now, zone)) })
            }
            HomeSection.Balances -> {
                val balances = Balances.balances(r.entries)
                val shown = r.accounts.filter { !it.archived && (cfg.accountIds.isEmpty() || it.id in cfg.accountIds) }
                val rows = shown.map { it to (balances[it.id] ?: Paise.ZERO) }
                SectionData.BalancesData(rows, rows.filter { NetWorth.isRupee(it.first) }.fold(Paise.ZERO) { a, b -> a + b.second }, shown.count { !NetWorth.isRupee(it) })
            }
            HomeSection.Recent -> {
                val recent = LedgerSearch.apply(r.entries, LedgerFilter(accountIds = cfg.accountIds), r.categories, r.accounts)
                    .filter { !LedgerSearch.isUpcoming(it) && it.dateMs <= now }.take(5)
                SectionData.RecentData(LedgerGrouping.group(recent, r.categories, zone).flatMap { it.days })
            }
            HomeSection.NetWorth -> SectionData.NetWorthData(
                NetWorth.at(now, r.entries, r.accounts, r.objectives, r.extras, zone), NetWorth.series(r.entries, r.accounts, r.objectives, now, zone, 6, r.extras),
            )
            HomeSection.Goals -> SectionData.GoalsData(
                r.objectives.filter { it.kind == ObjectiveKind.GOAL && !it.archived }.take(3).map { it to Goals.progress(it, r.entries, now, zone) },
            )
            HomeSection.Loans -> SectionData.LoansData(
                r.objectives.filter { it.kind == ObjectiveKind.LOAN && !it.archived }.map { it to Loans.progress(it, r.entries) }.sortedByDescending { it.second.outstanding.value }.take(3),
            )
            HomeSection.Spending -> {
                val days = cfg.rangeDays ?: 30
                val period = Ranges.resolve(SpendingRange.PastDays(days), r.entries, now, zone)
                SectionData.SpendingData(Insights.categoryTotals(r.entries, r.categories, period, income = false, filter = filter).take(5), days)
            }
            HomeSection.Heatmap -> {
                val month = YearMonth.from(Periods.date(now, zone))
                SectionData.HeatmapData(month, Heatmap.month(r.entries, r.categories, month, firstDay, zone, filter))
            }
        }
    }
}
