package com.mansaketh.chitta.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.HomeSection
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.money.Hero
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.PaceStatus
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.RangeResult
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.categoryName
import com.mansaketh.chitta.ui.categoryTint
import com.mansaketh.chitta.ui.insights.TrendLine
import com.mansaketh.chitta.ui.insights.HeatGrid
import com.mansaketh.chitta.ui.ledger.LedgerUi
import com.mansaketh.chitta.ui.ledger.toRow
import com.mansaketh.chitta.ui.plan.dateText
import com.mansaketh.chitta.ui.weekdayShort
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * Home (master section 3, design pages 8 and 10). One number first: Safe to spend, or the cash check when that is lower. Below it the
 * ring, the month rule, what is coming up, then only the sections the person switched on.
 */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    hidden: Boolean,
    onToggleHide: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenBudget: (String) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenLoan: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenLedger: () -> Unit,
    onCustomise: () -> Unit,
    onOpenReview: () -> Unit = {},
) {
    val ui by vm.ui.collectAsState()
    val firstDay by vm.prefs.firstDay.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    val c = ChittaTheme.colors

    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Home", actions = {
            IconAction(ChittaIcons.Gear, "Customise Home", onCustomise)
            HideAmountsAction(hidden, onToggleHide)
        })
        if (ui.loading) { LoadingPlaceholder(Modifier.padding(Dimens.Margin)); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 160.dp)) {
            ui.reviewMonth?.let { month ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(role = Role.Button, onClickLabel = "Open the month in review", onClick = onOpenReview),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " in review", style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Icon(ChittaIcons.CaretRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(16.dp))
                }
                HairlineRule()
            }
            val hero = ui.hero
            val choice = ui.choice
            if (choice == null) {
                if (ui.hasEntries) EmptyState("No budget runs today. Add one in Plan and Safe to spend appears here.", "Open Plan", onOpenPlan)
                else Text("Nothing here yet. Add your first transaction with the plus button.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.SectionGap))
            } else {
                // budget selector, only when there is more than one to choose from
                if (ui.budgetOptions.size > 1 && hero != null) {
                    Text(
                        hero.budget.name + "  ▾", style = ChittaTheme.type.labelMedium, color = c.ink,
                        modifier = Modifier.heightIn(min = Dimens.TouchTarget).clickable(role = Role.Button, onClickLabel = "Change budget") { sheet = "budget" }.wrapContentHeight(Alignment.CenterVertically),
                    )
                }
                Text(if (choice.hero == Hero.CashCheck) "Cash check" else "Safe to spend", style = ChittaTheme.type.label, color = c.inkMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AmountText(choice.value, style = ChittaTheme.type.amountHero, roundToRupee = true, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(Dimens.Gap3))
                    if (ui.cashStale && choice.hero == Hero.CashCheck) Stamp("Estimate", tone = StampTone.Attention)
                }
                if (choice.warn) {
                    Text("Your cash is lower than your budget says. The cash figure is the safer one.", style = ChittaTheme.type.body, color = c.warningText, modifier = Modifier.padding(top = Dimens.Gap2))
                }
                Text(
                    "How this is worked out", style = ChittaTheme.type.labelMedium, color = c.ink,
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget).clickable(role = Role.Button) { sheet = "explain" }.wrapContentHeight(Alignment.CenterVertically),
                )

                if (hero != null && choice.hero == Hero.SafeToSpend) {
                    Spacer(Modifier.height(Dimens.Gap3))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap6)) {
                        BudgetRing(hero.ring, diameter = 120.dp)
                        RingLegend(Paise((hero.summary.spent.value - hero.sinking.covered.value).coerceAtLeast(0)), hero.billsDue + hero.sinking.reserved, hero.safe, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Dimens.SectionGap))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap6)) {
                        Column(Modifier.weight(1f)) {
                            Text("Per day", style = ChittaTheme.type.label, color = c.inkMuted)
                            val pd = hero.perDay
                            if (pd != null) AmountText(pd, style = ChittaTheme.type.amountLarge, roundToRupee = true)
                            else Text("None", style = ChittaTheme.type.amountLarge, color = c.ink)
                            Text("for the ${hero.summary.daysLeft} days left", style = ChittaTheme.type.label, color = c.inkMuted)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Range", style = ChittaTheme.type.label, color = c.inkMuted)
                            when (val r = hero.range) {
                                is RangeResult.Days -> Text("${r.days} days", style = ChittaTheme.type.amountLarge, color = c.ink)
                                RangeResult.NotEnoughData -> Text("Not enough data yet", style = ChittaTheme.type.body, color = c.inkMuted)
                                RangeResult.NoSpending -> Text("No spending yet", style = ChittaTheme.type.body, color = c.inkMuted)
                            }
                            hero.rangeShortDays?.let { Text("$it days short of the month end", style = ChittaTheme.type.label, color = c.warningText) }
                        }
                    }
                    Spacer(Modifier.height(Dimens.SectionGap))
                    val s = hero.summary
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Spent", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.weight(1f))
                        AmountText(s.spent, style = ChittaTheme.type.amount)
                        Spacer(Modifier.width(Dimens.Gap2))
                        Stamp(
                            when (s.pace) { PaceStatus.OnPace -> "On pace"; PaceStatus.OverPace -> "Ahead of pace"; PaceStatus.OverLimit -> "Over budget" },
                            tone = if (s.pace == PaceStatus.OnPace) StampTone.Neutral else StampTone.Attention,
                        )
                    }
                    Spacer(Modifier.height(Dimens.Gap3))
                    MonthRule(s.daysInPeriod, s.cumulative, s.limit)
                    Row(Modifier.fillMaxWidth().padding(top = Dimens.Gap1), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Today · day ${s.dayOfPeriod}", style = ChittaTheme.type.label, color = c.inkMuted)
                        s.limitDay?.let { d ->
                            val date = Periods.date(s.period.startMs, ui.zone).plusDays((d - 1).toLong())
                            Text("Limit ~${date.format(dayMonth)}", style = ChittaTheme.type.label, color = c.inkMuted)
                        }
                    }
                }
            }

            if (ui.coming.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.SectionGap))
                SectionHeader("Coming up", action = "See all", onAction = onOpenPlan)
                HairlineRule()
                val lui = LedgerUi(categories = ui.categories, accounts = ui.accounts)
                ui.coming.forEachIndexed { i, t ->
                    val row = t.toRow(lui, c.inkMuted) { id -> c.categoryTint(id) }
                    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).clickable(role = Role.Button) { onOpenEntry(t.id) }.padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = ChittaTheme.type.body, color = c.ink, maxLines = 1)
                            Text(dateText(t.dateMs, ui.zone), style = ChittaTheme.type.label, color = c.inkMuted)
                        }
                        AmountText(row.amount, style = ChittaTheme.type.amountRow)
                        // one stamp only: on the first, nearest bill
                        if (i == 0) { Spacer(Modifier.width(Dimens.Gap2)); Stamp("Upcoming") }
                    }
                    HairlineRule()
                }
            }

            ui.layout.shown.forEach { cfg ->
                val data = ui.sections[cfg.section] ?: return@forEach
                Spacer(Modifier.height(Dimens.SectionGap))
                Section(cfg.section.title) {
                    when (data) {
                        is SectionData.BudgetsData -> if (data.rows.isEmpty()) Muted("No budgets yet.") else data.rows.forEach { r ->
                            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenBudget(r.budget.id) }.padding(vertical = Dimens.Gap3)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.budget.name, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                                    AmountText(r.summary.left, style = ChittaTheme.type.amountRow)
                                }
                                Text("left of ${IndianFormat.rupeesRounded(r.summary.limit)}", style = ChittaTheme.type.label, color = c.inkMuted)
                            }
                            HairlineRule()
                        }
                        is SectionData.BalancesData -> {
                            data.rows.forEach { (a, bal) ->
                                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
                                    Text(a.name, style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                                    AmountText(bal, style = ChittaTheme.type.amountRow)
                                }
                                HairlineRule()
                            }
                            Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
                                Text("Total", style = ChittaTheme.type.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                                AmountText(data.total, style = ChittaTheme.type.amount)
                            }
                            if (data.otherCurrency > 0) Muted("${data.otherCurrency} in another currency, not added in.")
                        }
                        is SectionData.RecentData -> {
                            val lui = LedgerUi(categories = ui.categories, accounts = ui.accounts)
                            if (data.days.isEmpty()) Muted("No entries yet.") else data.days.forEach { day ->
                                LedgerDay(
                                    dayNumber = day.date.dayOfMonth, weekday = day.date.weekdayShort(),
                                    entries = day.entries.map { t -> t.toRow(lui, c.inkMuted) { id -> c.categoryTint(id) } }, dayTotal = day.net,
                                    onEntryClick = { e -> onOpenEntry(e.id) },
                                )
                            }
                        }
                        is SectionData.NetWorthData -> {
                            AmountText(data.now.total, style = ChittaTheme.type.amountLarge, roundToRupee = true)
                            if (data.series.size >= 2) TrendLine(data.series.map { it.figures.total.value }, "Net worth over the last ${data.series.size} months", height = 80.dp)
                        }
                        is SectionData.GoalsData -> if (data.rows.isEmpty()) Muted("No goals yet.") else data.rows.forEach { (g, p) ->
                            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenGoal(g.id) }.padding(vertical = Dimens.Gap3)) {
                                Text(g.name, style = ChittaTheme.type.body, color = c.ink)
                                GaugeBar(p.gauge, g.name)
                                Text("${IndianFormat.rupeesRounded(p.saved)} of ${IndianFormat.rupeesRounded(p.target)}", style = ChittaTheme.type.label, color = c.inkMuted)
                            }
                            HairlineRule()
                        }
                        is SectionData.LoansData -> if (data.rows.isEmpty()) Muted("No loans yet.") else data.rows.forEach { (l, p) ->
                            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenLoan(l.id) }.padding(vertical = Dimens.Gap3)) {
                                Text(l.name, style = ChittaTheme.type.body, color = c.ink)
                                GaugeBar(p.gauge, l.name)
                                Text("${IndianFormat.rupeesRounded(p.outstanding)} still to go", style = ChittaTheme.type.label, color = c.inkMuted)
                            }
                            HairlineRule()
                        }
                        is SectionData.SpendingData -> if (data.bars.isEmpty()) Muted("No spending in the past ${data.days} days.") else {
                            val top = data.bars.maxOf { it.amount.value }.coerceAtLeast(1L)
                            data.bars.forEach { LedgerBar(categoryName(it.categoryId, ui.categories), it.amount, it.amount.value.coerceAtLeast(0L).toFloat() / top) }
                        }
                        is SectionData.HeatmapData -> {
                            Muted("Darker days are where more went out.")
                            Spacer(Modifier.height(Dimens.Gap2))
                            HeatGrid(data.weeks, firstDay)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.SectionGap))
            Text(
                "Customise Home", style = ChittaTheme.type.labelMedium, color = c.ink,
                modifier = Modifier.heightIn(min = Dimens.TouchTarget).clickable(role = Role.Button, onClick = onCustomise).wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }

    when (sheet) {
        "budget" -> ChoiceSheet(
            "Budget on Home", ui.budgetOptions.map { Choice(it.name, it.id, selected = it.id == ui.hero?.budget?.id) },
            onPick = { vm.pickBudget(it); sheet = null }, onDismiss = { sheet = null },
        )
        "explain" -> ExplainSheet(ui, onDismiss = { sheet = null })
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        HairlineRule()
        content()
    }
}

@Composable
private fun Muted(text: String) = Text(text, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))

/** Design page 10: the sum behind the big number, with the person's real figures. */
@Composable
private fun ExplainSheet(ui: HomeUi, onDismiss: () -> Unit) {
    val c = ChittaTheme.colors
    BottomOverlay(onDismiss) {
        Text("How this is worked out", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap3))
        val hero = ui.hero
        if (hero != null) {
            Text("Safe to spend", style = ChittaTheme.type.bodyMedium, color = c.ink)
            FigureLine("Budget", hero.summary.limit)
            FigureLine("Already spent", hero.summary.spent, minus = true)
            FigureLine("Bills still to come", hero.billsDue, minus = true)
            if (!hero.sinking.reserved.isZero) FigureLine("Set aside in sinking funds", hero.sinking.reserved, minus = true)
            if (!hero.sinking.covered.isZero) FigureLine("Spending your sinking funds already cover", hero.sinking.covered)
            HairlineRule()
            FigureLine("Safe to spend", hero.safe, strong = true)
            Muted("Bills still to come are open money-out entries due before this period ends.")
        }
        val cash = ui.cash
        if (cash != null) {
            Spacer(Modifier.height(Dimens.Gap4))
            Text("Cash check", style = ChittaTheme.type.bodyMedium, color = c.ink)
            FigureLine("In your spending accounts", cash.balances)
            FigureLine("Bills still to come", cash.bills, minus = true)
            FigureLine("Goal savings set aside", cash.goalSavings, minus = true)
            if (!cash.sinking.isZero) FigureLine("Set aside in sinking funds", cash.sinking, minus = true)
            HairlineRule()
            FigureLine("Cash check", cash.value, strong = true)
            if (ui.cashStale) Row(verticalAlignment = Alignment.CenterVertically) {
                Stamp("Estimate", tone = StampTone.Attention)
                Spacer(Modifier.width(Dimens.Gap2))
                Text("A spending account has had no entry for over 7 days.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.weight(1f))
            }
            Muted("Home shows the smaller of the two, so you never plan around money the accounts do not hold.")
        } else if (hero == null) {
            Muted("Set a budget in Plan, or mark spending accounts in Customise Home.")
        }
    }
}

@Composable
private fun FigureLine(label: String, amount: Paise, minus: Boolean = false, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
        Text(if (minus) "less  $label" else label, style = if (strong) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = ChittaTheme.colors.ink, modifier = Modifier.weight(1f))
        AmountText(amount, style = if (strong) ChittaTheme.type.amount else ChittaTheme.type.amountRow)
    }
}
