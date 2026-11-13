package com.mansaketh.chitta.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.Bucket
import com.mansaketh.chitta.core.domain.HistoryPoint
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.TextSheet
import com.mansaketh.chitta.ui.TextTabs
import com.mansaketh.chitta.ui.ValueRow
import com.mansaketh.chitta.ui.categoryName
import com.mansaketh.chitta.ui.plan.MultiPickSheet
import com.mansaketh.chitta.ui.plan.dateText
import com.mansaketh.chitta.ui.plan.periodText
import com.mansaketh.chitta.ui.plan.pickDate
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * Insights (master section 6, design page 24): "What changed?". Three tabs: Current (the All Spending Summary for a range), History (per
 * period or running totals) and Net worth over time. Every number comes from `Insights`, `History` and `NetWorth` in `:core:domain`.
 */
@Composable
fun InsightsScreen(vm: InsightsViewModel, onOpenEntry: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val c = ChittaTheme.colors

    Column(Modifier.fillMaxSize()) {
        Text("Insights", style = ChittaTheme.type.headline, color = c.ink, modifier = Modifier.padding(horizontal = Dimens.Margin, vertical = Dimens.Gap3))
        TextTabs(listOf("Current", "History", "Net worth"), tab, { tab = it }, Modifier.padding(horizontal = Dimens.Margin))
        HairlineRule()
        when {
            ui.loading -> LoadingPlaceholder(Modifier.padding(Dimens.Margin))
            !ui.hasEntries -> Text("Nothing to show yet. Add a transaction and the totals appear here.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 160.dp)) {
                when (tab) {
                    0 -> CurrentTab(ui, onRange = { sheet = "range" }, onAccounts = { sheet = "accounts" }, onIncome = vm::showIncome, onDrill = vm::openDrill)
                    1 -> HistoryTab(ui, onBucket = vm::setBucket, onCumulative = vm::setCumulative, onAccounts = { sheet = "accounts" })
                    else -> NetWorthTab(ui)
                }
            }
        }
    }

    when (sheet) {
        "range" -> ChoiceSheet(
            "Range",
            listOf(
                Choice("This month", "month", selected = ui.range == RangeChoice.ThisMonth),
                Choice("Past 30 days", "30", selected = ui.range == RangeChoice.Past30),
                Choice("Past 6 months", "6m", selected = ui.range == RangeChoice.Past6Months),
                Choice("This year", "year", selected = ui.range == RangeChoice.ThisYear),
                Choice("All time", "all", selected = ui.range == RangeChoice.AllTime),
                Choice("Past number of days", "days"),
                Choice("Between two dates", "dates", "The end can stay open, up to today"),
            ),
            onPick = { key ->
                sheet = null
                when (key) {
                    "month" -> vm.setRange(RangeChoice.ThisMonth)
                    "30" -> vm.setRange(RangeChoice.Past30)
                    "6m" -> vm.setRange(RangeChoice.Past6Months)
                    "year" -> vm.setRange(RangeChoice.ThisYear)
                    "all" -> vm.setRange(RangeChoice.AllTime)
                    "days" -> sheet = "days"
                    "dates" -> sheet = "dates"
                }
            },
            onDismiss = { sheet = null },
        )
        "days" -> TextSheet("Past number of days", "Days, counting today", "30", onSave = { text ->
            val n = text.trim().toIntOrNull()
            if (n != null && n in 1..3650) { vm.setRange(RangeChoice.PastDays(n)); sheet = null }
        }, onDismiss = { sheet = null }, numeric = true, saveLabel = "Show")
        "dates" -> DatesSheet(ui.zone, onShow = { from, to -> vm.setRange(RangeChoice.Dates(from, to)); sheet = null }, onDismiss = { sheet = null })
        "accounts" -> MultiPickSheet(
            "Accounts", ui.accounts.filter { !it.archived }.map { it.id to it.name }, ui.accountIds, "All accounts",
            onDone = { vm.setAccounts(it); sheet = null }, onDismiss = { sheet = null },
        )
    }

    ui.drill?.let { d ->
        BottomOverlay(onDismiss = { vm.openDrill(null) }) {
            Text(categoryName(d.categoryId, ui.categories), style = ChittaTheme.type.title, color = c.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rangeLabel(ui.range, ui.zone), style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.weight(1f))
                AmountText(d.total, style = ChittaTheme.type.amountSmall)
            }
            Spacer(Modifier.height(Dimens.Gap2))
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                if (d.parts.size > 1) {
                    val top = d.parts.maxOf { it.amount.value }.coerceAtLeast(1L)
                    items(d.parts, key = { "p-" + it.categoryId }) { part ->
                        LedgerBar(categoryName(part.categoryId, ui.categories), part.amount, part.amount.value.coerceAtLeast(0L).toFloat() / top)
                    }
                    item { Spacer(Modifier.height(Dimens.Gap3)); HairlineRule() }
                }
                items(d.entries, key = { "e-" + it.id }) { t ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(role = Role.Button) { vm.openDrill(null); onOpenEntry(t.id) }.padding(vertical = Dimens.Gap2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name.ifBlank { categoryName(t.categoryId, ui.categories) }, style = ChittaTheme.type.body, color = c.ink, maxLines = 1)
                            Text(dateText(t.dateMs, ui.zone), style = ChittaTheme.type.label, color = c.inkMuted)
                        }
                        AmountText(Paise(Math.abs(t.amount)), style = ChittaTheme.type.amountSmall)
                    }
                    HairlineRule()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------- Current

@Composable
private fun CurrentTab(ui: InsightsUi, onRange: () -> Unit, onAccounts: () -> Unit, onIncome: (Boolean) -> Unit, onDrill: (String) -> Unit) {
    val c = ChittaTheme.colors
    val s = ui.summary ?: return
    ValueRow("Range", rangeLabel(ui.range, ui.zone), onClick = onRange)
    ValueRow("Accounts", accountsLabel(ui), onClick = onAccounts)
    val cmp = ui.comparison
    Text(
        if (cmp != null) "${periodText(cmp.current, ui.zone)}, compared with ${periodText(cmp.previous, ui.zone)}" else ui.period?.let { periodText(it, ui.zone) }.orEmpty(),
        style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap3),
    )
    Spacer(Modifier.height(Dimens.Gap4))

    // two figures side by side, as on the design page
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap6)) {
        Column(Modifier.weight(1f)) {
            Text("Spent", style = ChittaTheme.type.label, color = c.inkMuted)
            AmountText(s.expense, style = ChittaTheme.type.amountLarge, roundToRupee = true)
            val prev = ui.previous
            if (prev != null) {
                val diff = s.expense - prev.expense
                val name = if (ui.range == RangeChoice.ThisMonth && cmp != null) Periods.date(cmp.previous.startMs, ui.zone).format(monthShort) else "the period before"
                Text(
                    when {
                        diff.value > 0 -> "${IndianFormat.rupeesRounded(diff)} more than $name"
                        diff.value < 0 -> "${IndianFormat.rupeesRounded(-diff)} less than $name"
                        else -> "Same as $name"
                    }.let { if (ChittaTheme.hideAmounts) "Compared with $name" else it },
                    style = ChittaTheme.type.label, color = c.inkMuted,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Saved", style = ChittaTheme.type.label, color = c.inkMuted)
            val pct = s.savedPercent
            Text(if (pct != null) "$pct%" else "None", style = ChittaTheme.type.amountLarge, color = c.ink)
            Text(if (pct != null) "of income" else "No income in this range", style = ChittaTheme.type.label, color = c.inkMuted)
        }
    }

    Spacer(Modifier.height(Dimens.SectionGap))
    SectionHeader("Totals")
    HairlineRule()
    TotalRow("Net total", s.net, bold = true)
    TotalRow("Money in", s.income)
    TotalRow("Money out", s.expense)
    if (!s.balanceCorrection.isZero) TotalRow("Balance corrections", s.balanceCorrection)
    if (!s.lent.isZero) TotalRow("Lent", s.lent)
    if (!s.borrowed.isZero) TotalRow("Borrowed", s.borrowed)
    if (!s.upcoming.isZero) TotalRow("Upcoming to pay", s.upcoming)
    if (!s.overdue.isZero) TotalRow("Overdue to pay", s.overdue)
    Text(
        "Transfers between your own accounts are left out. A refund reduces its category.",
        style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2),
    )

    Spacer(Modifier.height(Dimens.SectionGap))
    SectionHeader("Cash flow")
    if (ui.flow.isEmpty()) {
        Text("Nothing in this range.", style = ChittaTheme.type.body, color = c.inkMuted)
    } else {
        FlowBars(ui.flow, label = { bucketLabel(it, ui.flowBucket) }, average = null, labelEvery = maxOf(1, ui.flow.size / 6))
        Text("Money out in each ${bucketWord(ui.flowBucket)}. The grey outline is where this one ends at today's pace.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
    }

    Spacer(Modifier.height(Dimens.SectionGap))
    SectionHeader("By category")
    TextTabs(listOf("Spending", "Income"), if (ui.showIncome) 1 else 0, { onIncome(it == 1) })
    Spacer(Modifier.height(Dimens.Gap2))
    if (ui.bars.isEmpty()) {
        Text(if (ui.showIncome) "No income in this range." else "No spending in this range.", style = ChittaTheme.type.body, color = c.inkMuted)
    } else {
        val top = ui.bars.maxOf { it.amount.value }.coerceAtLeast(1L)
        var donut by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        TextTabs(listOf("Bars", "Donut"), if (donut) 1 else 0, { donut = it == 1 })
        Spacer(Modifier.height(Dimens.Gap2))
        if (donut) {
            val slices = com.mansaketh.chitta.core.domain.Donut.slices(ui.bars)
            val whole = com.mansaketh.chitta.core.money.Paise(slices.sumOf { it.amount.value })
            DonutChart(slices, whole, "Share of ${if (ui.showIncome) "income" else "spending"} by category: " + slices.joinToString(", ") { (it.categoryId?.let { id -> categoryName(id, ui.categories) } ?: "Other") + " ${it.percent} percent" })
            slices.forEachIndexed { i, sl ->
                val name = sl.categoryId?.let { categoryName(it, ui.categories) } ?: "Other"
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).let { m -> if (sl.categoryId != null) m.clickable(role = Role.Button, onClickLabel = "Open $name") { onDrill(sl.categoryId!!) } else m },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap3),
                ) {
                    Box(Modifier.size(12.dp).background(if (sl.categoryId == null) c.inkMuted else c.category[i % c.category.size]))
                    Text("$name · ${sl.percent}%", style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    com.mansaketh.chitta.core.design.components.AmountText(sl.amount)
                }
            }
        } else
        ui.bars.forEach { bar ->
            Box(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open ${categoryName(bar.categoryId, ui.categories)}") { onDrill(bar.categoryId) }) {
                LedgerBar(categoryName(bar.categoryId, ui.categories), bar.amount, bar.amount.value.coerceAtLeast(0L).toFloat() / top)
            }
        }
        Text("Tap a category to see what is in it.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
    }

    if (ui.changes.isNotEmpty()) {
        Spacer(Modifier.height(Dimens.SectionGap))
        SectionHeader("Biggest changes")
        HairlineRule()
        ui.changes.forEach { ch ->
            Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(categoryName(ch.categoryId, ui.categories), style = ChittaTheme.type.body, color = c.ink)
                    Text("${IndianFormat.rupeesRounded(ch.current)} now, ${IndianFormat.rupeesRounded(ch.previous)} before", style = ChittaTheme.type.label, color = c.inkMuted)
                }
                val p = ch.percent
                Text(if (p == null) "New" else (if (p > 0) "+$p%" else "$p%"), style = ChittaTheme.type.amountSmall, color = c.ink)
            }
            HairlineRule()
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Paise, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (bold) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = if (bold) ChittaTheme.colors.ink else ChittaTheme.colors.inkMuted, modifier = Modifier.weight(1f))
        AmountText(amount, style = if (bold) ChittaTheme.type.amount else ChittaTheme.type.amountSmall)
    }
    HairlineRule()
}

// ---------------------------------------------------------------------------------------------------- History

@Composable
private fun HistoryTab(ui: InsightsUi, onBucket: (Bucket) -> Unit, onCumulative: (Boolean) -> Unit, onAccounts: () -> Unit) {
    val c = ChittaTheme.colors
    val buckets = listOf(Bucket.Day, Bucket.Week, Bucket.Month, Bucket.Year)
    ValueRow("Accounts", accountsLabel(ui), onClick = onAccounts)
    Spacer(Modifier.height(Dimens.Gap2))
    TextTabs(listOf("Days", "Weeks", "Months", "Years"), buckets.indexOf(ui.historyBucket), { onBucket(buckets[it]) })
    TextTabs(listOf("Per ${bucketWord(ui.historyBucket)}", "Running total"), if (ui.cumulative) 1 else 0, { onCumulative(it == 1) })
    Spacer(Modifier.height(Dimens.Gap3))

    val avg = ui.historyAverage
    Text(
        if (avg != null) "Average money out: ${IndianFormat.rupeesRounded(avg.moneyOut)} a ${bucketWord(ui.historyBucket)}, from the finished ones" else "Not enough finished periods for an average yet.",
        style = ChittaTheme.type.body, color = c.inkMuted,
    )
    Spacer(Modifier.height(Dimens.Gap4))
    if (ui.cumulative) {
        TrendLine(
            ui.history.map { it.cumulativeNet.value },
            "Running net total, from ${IndianFormat.spoken(ui.history.first().cumulativeNet)} to ${IndianFormat.spoken(ui.history.last().cumulativeNet)}",
        )
        Row(Modifier.fillMaxWidth().padding(top = Dimens.Gap2), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(bucketLabel(ui.history.first(), ui.historyBucket), style = ChittaTheme.type.label, color = c.inkMuted)
            AmountText(ui.history.last().cumulativeNet, style = ChittaTheme.type.amountSmall)
        }
    } else {
        FlowBars(ui.history, label = { bucketLabel(it, ui.historyBucket) }, average = avg?.moneyOut, labelEvery = maxOf(1, ui.history.size / 6))
    }
    Spacer(Modifier.height(Dimens.SectionGap))
    HairlineRule()
    ui.history.reversed().forEach { p ->
        Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(longLabel(p, ui.historyBucket, ui.zone), style = ChittaTheme.type.body, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                    Text("In", style = ChittaTheme.type.label, color = c.inkMuted)
                    AmountText(p.moneyIn, style = ChittaTheme.type.label, color = c.inkMuted)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountText(if (ui.cumulative) p.cumulativeNet else p.moneyOut, style = ChittaTheme.type.amountRow)
                Text(if (ui.cumulative) "running total" else "out", style = ChittaTheme.type.label, color = c.inkMuted)
            }
        }
        HairlineRule()
    }
}

// ---------------------------------------------------------------------------------------------------- Net worth

@Composable
private fun NetWorthTab(ui: InsightsUi) {
    val c = ChittaTheme.colors
    val now = ui.netNow ?: return
    Spacer(Modifier.height(Dimens.Gap4))
    Text("Net worth", style = ChittaTheme.type.label, color = c.inkMuted)
    AmountText(now.total, style = ChittaTheme.type.amountHero, roundToRupee = true)
    Text("As of ${dateText(ui.nowMs, ui.zone)}", style = ChittaTheme.type.label, color = c.inkMuted)
    Spacer(Modifier.height(Dimens.SectionGap))
    HairlineRule()
    TotalRow("Accounts", now.accounts)
    TotalRow("Owed to you", now.owedToYou)
    TotalRow("You owe", now.youOwe)
    Spacer(Modifier.height(Dimens.SectionGap))
    SectionHeader("Last 12 months")
    if (ui.netSeries.size >= 2) {
        TrendLine(
            ui.netSeries.map { it.figures.total.value },
            "Net worth over the last ${ui.netSeries.size} months, now ${IndianFormat.spoken(now.total)}",
        )
        Row(Modifier.fillMaxWidth().padding(top = Dimens.Gap2), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ui.netSeries.first().month.format(monthYear), style = ChittaTheme.type.label, color = c.inkMuted)
            Text(ui.netSeries.last().month.format(monthYear), style = ChittaTheme.type.label, color = c.inkMuted)
        }
        Text(
            "Each point is the book as it stood at the end of that month.",
            style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2),
        )
    }
    Spacer(Modifier.height(Dimens.SectionGap))
    if (now.otherCurrencyAccounts > 0) {
        Text(
            "${now.otherCurrencyAccounts} ${if (now.otherCurrencyAccounts == 1) "account is" else "accounts are"} in another currency and not added in.",
            style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2),
        )
    }
    Text(
        "Not counted yet: fixed deposits, mutual funds, other assets, instalment loans and card dues read from messages. They come in later phases.",
        style = ChittaTheme.type.body, color = c.inkMuted,
    )
}

// ---------------------------------------------------------------------------------------------------- helpers

@Composable
private fun DatesSheet(zone: ZoneId, onShow: (LocalDate, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now(zone) }
    var from by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    fun ms(d: LocalDate) = Periods.startOfDay(d, zone) + 12 * 3_600_000L
    BottomOverlay(onDismiss) {
        Text("Between two dates", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        ValueRow("From", from.format(dayMonthYear), onClick = { pickDate(context, ms(from), zone, { from = Periods.date(it, zone) }) })
        ValueRow("To", to?.format(dayMonthYear) ?: "Up to today", onClick = { pickDate(context, ms(to ?: today), zone, { to = Periods.date(it, zone) }) })
        if (to != null) ChittaButton("Leave the end open", { to = null }, style = ButtonStyle.Quiet)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Show", { onShow(from, to) }, confirmHaptic = true)
    }
}

private fun accountsLabel(ui: InsightsUi): String = when (ui.accountIds.size) {
    0 -> "All accounts"
    1 -> ui.accounts.firstOrNull { it.id in ui.accountIds }?.name ?: "1 account"
    else -> "${ui.accountIds.size} accounts"
}

private fun rangeLabel(r: RangeChoice, zone: ZoneId): String = when (r) {
    RangeChoice.ThisMonth -> "This month"
    RangeChoice.Past30 -> "Past 30 days"
    RangeChoice.Past6Months -> "Past 6 months"
    RangeChoice.ThisYear -> "This year"
    RangeChoice.AllTime -> "All time"
    is RangeChoice.PastDays -> "Past ${r.days} days"
    is RangeChoice.Dates -> r.to?.let { "${r.from.format(dayMonthYear)} to ${it.format(dayMonthYear)}" } ?: "From ${r.from.format(dayMonthYear)}"
}

private fun bucketWord(b: Bucket) = when (b) { Bucket.Day -> "day"; Bucket.Week -> "week"; Bucket.Month -> "month"; Bucket.Year -> "year" }

/** Short label under a bar: the day of the month, a week's first day, a month name or a year. */
private fun bucketLabel(p: HistoryPoint, b: Bucket): String = when (b) {
    Bucket.Day -> p.start.dayOfMonth.toString()
    Bucket.Week -> p.start.format(dayMonth)
    Bucket.Month -> p.start.format(monthShort)
    Bucket.Year -> p.start.year.toString()
}

private fun longLabel(p: HistoryPoint, b: Bucket, zone: ZoneId): String = when (b) {
    Bucket.Day -> p.start.format(dayMonthYear)
    Bucket.Week -> "Week of ${p.start.format(dayMonthYear)}"
    Bucket.Month -> p.start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + p.start.year
    Bucket.Year -> p.start.year.toString()
}
