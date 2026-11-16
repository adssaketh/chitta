package com.mansaketh.chitta.ui.home

import com.mansaketh.chitta.core.design.kit.SheetHeader
import androidx.compose.foundation.background
import com.mansaketh.chitta.ui.FeatureIcons
import com.mansaketh.chitta.core.domain.TabKey
import com.mansaketh.chitta.core.design.kit.EntryRow
import com.mansaketh.chitta.core.design.kit.Note
import com.mansaketh.chitta.ui.plan.PlanRow
import com.mansaketh.chitta.core.money.Segments
import com.mansaketh.chitta.core.design.kit.EmptyState
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.BadgedIconAction
import com.mansaketh.chitta.core.design.kit.TopBarMain
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import com.mansaketh.chitta.core.design.theme.rupeeAware
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
    onOpenBell: () -> Unit = {},
) {
    val ui by vm.ui.collectAsState()
    val waiting by vm.waiting.collectAsState()
    val firstDay by vm.prefs.firstDay.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    val c = ChittaTheme.colors

    Column(Modifier.fillMaxSize()) {
        TopBarMain("Home", ChittaIcons.Home, actions = {
            // The bell: bank messages the app was not sure about wait here. The count shows only when something waits.
            BadgedIconAction(ChittaIcons.Bell, "Bank messages", waiting, onOpenBell)
            IconAction(ChittaIcons.Gear, "Customise Home", onCustomise)
            HideAmountsAction(hidden, onToggleHide)
        })
        if (ui.loading) { LoadingPlaceholder(Modifier.padding(Dimens.Margin)); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.FabInset)) {
            ui.reviewMonth?.let { month ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).pressClickable(role = Role.Button, onClickLabel = "Open the month in review", onClick = onOpenReview),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " in review", style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Icon(ChittaIcons.CaretRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconSmall))
                }
                HairlineRule()
            }
            val hero = ui.hero
            val choice = ui.choice
            if (choice == null) {
                if (ui.hasEntries) EmptyState(FeatureIcons.tab(TabKey.Budgets), null, "No budget runs today. Add one in Plan and Safe to spend appears here.", "Open Plan", onOpenPlan)
                else Text("Nothing here yet. Add your first transaction with the plus button.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.SectionGap))
            } else {
                val cashLeads = choice.hero == Hero.CashCheck
                // Page 13: the budget line, then Safe to spend with its info button, the hero, the per-day sentence, and the ring beside them.
                if (hero != null && (!cashLeads || ui.budgetOptions.size > 1)) {
                    BudgetLine(hero.budget.name, canChange = ui.budgetOptions.size > 1) { sheet = "budget" }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        InfoLabel(if (cashLeads) "Cash check" else "Safe to spend") { sheet = "explain" }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AmountText(
                                choice.value, style = ChittaTheme.type.amountHero,
                                fallbackStyles = listOf(ChittaTheme.type.amountLarge, ChittaTheme.type.amount),
                                roundToRupee = true, modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(Dimens.Gap3))
                            if (ui.cashStale && cashLeads) Stamp("Estimate", tone = StampTone.Attention)
                        }
                        val pd = hero?.perDay
                        if (!cashLeads && hero != null && pd != null) {
                            val d = hero.summary.daysLeft
                            Text("About ${amt(pd)} a day for $d ${if (d == 1) "day" else "days"}".rupeeAware(), style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
                        }
                    }
                    if (!cashLeads && hero != null) {
                        Spacer(Modifier.width(Dimens.FigureGap))
                        BudgetRing(hero.ring, diameter = Dimens.RingHome, animate = true)
                    }
                }
                if (choice.warn) {
                    Text("Your cash is lower than your budget says. The cash figure is the safer one.", style = ChittaTheme.type.body, color = c.warningText, modifier = Modifier.padding(top = Dimens.Gap2))
                }

                if (hero != null) {
                    if (cashLeads) {
                        // The budget block stays under the cash check, named, so one budget never passes for all your money (D187).
                        Section(hero.budget.name + " budget")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.FigureGap)) {
                            BudgetRing(hero.ring, diameter = Dimens.RingHome, animate = true)
                            RingSwatchLegend(Modifier.weight(1f))
                        }
                    } else {
                        Spacer(Modifier.height(Dimens.Space3))
                        RingSwatchLegend()
                    }
                    val s = hero.summary
                    Spacer(Modifier.height(Dimens.SectionGap))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.FigureGap)) {
                        Tile("Budget left", Modifier.weight(1f)) {
                            AmountText(s.left, style = ChittaTheme.type.amountTile, fallbackStyles = listOf(ChittaTheme.type.amountRow), roundToRupee = true)
                            Text("of ${amt(s.limit)}".rupeeAware(), style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
                        }
                        Tile("Range", Modifier.weight(1f)) {
                            when (val r = hero.range) {
                                is RangeResult.Days -> Text(if (r.days == 1) "1 day" else "${r.days} days", style = ChittaTheme.type.amountTile, color = c.ink, maxLines = 1)
                                RangeResult.NotEnoughData -> Text("Not enough data yet", style = ChittaTheme.type.body, color = c.inkMuted)
                                RangeResult.NoSpending -> Text("No spending yet", style = ChittaTheme.type.body, color = c.inkMuted)
                            }
                            hero.rangeShortDays?.let { Text(if (it == 1) "1 day short" else "$it days short", style = ChittaTheme.type.rowMeta14, color = c.warningText) }
                        }
                    }
                    Spacer(Modifier.height(Dimens.Space6))
                    // Page 13: "₹26,520 spent" with the pace stamp at the end. OVER PACE and ON PACE are ink-outlined; only OVER LIMIT is red.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${amt(s.spent)} spent".rupeeAware(), style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(Dimens.InlineGap))
                        when (s.pace) {
                            PaceStatus.OnPace -> Stamp("On pace", tone = StampTone.Ink)
                            PaceStatus.OverPace -> Stamp("Over pace", tone = StampTone.Ink)
                            PaceStatus.OverLimit -> Stamp("Over limit", tone = StampTone.Attention)
                        }
                    }
                    Spacer(Modifier.height(Dimens.HeadingGap))
                    MonthRuleThin(s.daysInPeriod, s.dayOfPeriod, s.spent, s.limit, s.limitDay)
                    Row(Modifier.fillMaxWidth().padding(top = Dimens.Space1), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Today · day ${s.dayOfPeriod}", style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
                        s.limitDay?.let { d ->
                            val date = Periods.date(s.period.startMs, ui.zone).plusDays((d - 1).toLong())
                            Text("Limit ~${date.format(dayMonth)}", style = ChittaTheme.type.rowMeta14, color = c.warningText)
                        }
                    }
                }
            }

            if (ui.coming.isNotEmpty()) {
                // Page 13: the heading carries what is due in total (open bills due this period); no "See all", no stamp on the first row.
                Section("Coming up", note = if (ui.comingTotal.value > 0) "${amt(ui.comingTotal)} due" else null) {
                    val lui = LedgerUi(categories = ui.categories, accounts = ui.accounts)
                    ui.coming.forEach { t ->
                        val row = t.toRow(lui, c.inkMuted) { id -> c.categoryTint(id) }
                        val date = java.time.Instant.ofEpochMilli(t.dateMs).atZone(ui.zone).toLocalDate()
                        EntryRow(
                            title = row.title, meta = row.category, glyph = row.glyph, glyphColor = row.categoryColor, amount = row.amount, tone = row.tone,
                            marginDay = date.dayOfMonth, weekday = date.weekdayShort(),
                            onClick = { onOpenEntry(t.id) },
                        )
                    }
                }
            }

            ui.layout.shown.forEach { cfg ->
                val data = ui.sections[cfg.section] ?: return@forEach
                Section(cfg.section.title) {
                    when (data) {
                        is SectionData.BudgetsData -> if (data.rows.isEmpty()) Muted("No budgets yet.") else data.rows.forEach { r ->
                            PlanRow(
                                r.budget.name, r.summary.left, "left of ${IndianFormat.rupeesRounded(r.summary.limit)}",
                                Segments.gauge(r.summary.spent, r.summary.limit), "${r.budget.name} budget", onClick = { onOpenBudget(r.budget.id) },
                            )
                        }
                        is SectionData.BalancesData -> {
                            // One line per account name with an ellipsis and at least 16 dp before the amount.
                            data.rows.forEach { (a, bal) ->
                                ListRow(title = a.name, trailing = { AmountText(bal, style = ChittaTheme.type.rowAmount) })
                            }
                            ListRow(
                                title = "Total", titleStyle = ChittaTheme.type.bodyMedium, rule = false,
                                trailing = { AmountText(data.total, style = ChittaTheme.type.amount, fallbackStyles = listOf(ChittaTheme.type.rowAmount)) },
                            )
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
                            if (data.series.size >= 2) TrendLine(data.series.map { it.figures.total.value }, "Net worth over the last ${data.series.size} months", height = Dimens.TrendHeight)
                        }
                        is SectionData.GoalsData -> if (data.rows.isEmpty()) Muted("No goals yet.") else data.rows.forEach { (g, p) ->
                            PlanRow(g.name, null, "${IndianFormat.rupeesRounded(p.saved)} of ${IndianFormat.rupeesRounded(p.target)}", p.gauge, g.name, onClick = { onOpenGoal(g.id) })
                        }
                        is SectionData.LoansData -> if (data.rows.isEmpty()) Muted("No loans yet.") else data.rows.forEach { (l, p) ->
                            PlanRow(l.name, null, "${IndianFormat.rupeesRounded(p.outstanding)} still to go", p.gauge, l.name, onClick = { onOpenLoan(l.id) })
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
            Spacer(Modifier.height(Dimens.HeadingGap))
            LinkText("Customise Home", onClick = onCustomise)
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

/** Money in a sentence. Reads the hide-amounts setting, because a plain string would not be masked by AmountText. */
@Composable
@ReadOnlyComposable
private fun amt(p: Paise): String = if (ChittaTheme.hideAmounts) "₹ ••••" else IndianFormat.rupeesRounded(p)

/** "November budget" (page 13). A budget already named "... budget" is not given the word twice. The caret shows only when there is another budget to pick. */
@Composable
private fun BudgetLine(name: String, canChange: Boolean, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val text = if (name.endsWith("budget", ignoreCase = true)) name else "$name budget"
    Box(Modifier.fillMaxWidth().height(Dimens.Space8), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.then(if (canChange) Modifier.requiredHeight(Dimens.TouchTarget).pressClickable(role = Role.Button, onClickLabel = "Change budget", onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = ChittaTheme.type.body, color = c.inkMuted)
            if (canChange) {
                Spacer(Modifier.width(Dimens.Gap1))
                Icon(ChittaIcons.CaretDown, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(Dimens.IconSmall))
            }
        }
    }
}

/** "Safe to spend ⓘ" (page 13). The info button opens the sum; its 48 dp target overflows the 24 dp line so the page's spacing holds. */
@Composable
private fun InfoLabel(text: String, onInfo: () -> Unit) {
    val c = ChittaTheme.colors
    Row(Modifier.height(Dimens.Space6), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = ChittaTheme.type.body, color = c.ink)
        Spacer(Modifier.width(Dimens.Space1))
        Box(
            Modifier.requiredSize(Dimens.TouchTarget).clip(CircleShape).pressClickable(role = Role.Button, shape = CircleShape, onClick = onInfo),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ChittaIcons.Info, contentDescription = "How $text is worked out", tint = c.inkMuted, modifier = Modifier.size(Dimens.IconSmall))
        }
    }
}

/** One of the two figures under the hero (page 13): an ink rule on top, a muted label, then the figure. */
@Composable
private fun Tile(label: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = ChittaTheme.colors
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(Dimens.Hairline).background(c.ink))
        Spacer(Modifier.height(Dimens.Space3))
        Text(label, style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
        content()
    }
}

/** A text link: the underlined action style, 16 dp of air above and below so the whole line is a 48 dp touch target. */
@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text, style = ChittaTheme.type.link, color = ChittaTheme.colors.ink,
        modifier = Modifier.pressClickable(role = Role.Button, onClick = onClick).padding(vertical = Dimens.Gap4),
    )
}

@Composable
private fun Muted(text: String) = Text(text, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))

/** Design page 10: the sum behind the big number, with the person's real figures. */
@Composable
private fun ExplainSheet(ui: HomeUi, onDismiss: () -> Unit) {
    val c = ChittaTheme.colors
    BottomOverlay(onDismiss, handle = false) {
        SheetHeader("How this is worked out")
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
            Note("Home shows the smaller of the two, so you never plan around money the accounts do not hold.", Modifier.padding(top = Dimens.Gap2))
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
