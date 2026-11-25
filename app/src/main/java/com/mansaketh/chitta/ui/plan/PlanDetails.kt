package com.mansaketh.chitta.ui.plan

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mansaketh.chitta.core.design.kit.TickBox
import androidx.compose.foundation.ExperimentalFoundationApi
import com.mansaketh.chitta.core.design.kit.GaugeBar
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.ValueRow
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.*
import com.mansaketh.chitta.core.model.*
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.PaceStatus
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.*

@Composable
private fun Gone(what: String) = Text("This $what is gone. It may have been deleted.", style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(Dimens.Margin))

@Composable
private fun Figure(label: String, amount: Paise) {
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ChittaTheme.type.body, color = ChittaTheme.colors.inkMuted, modifier = Modifier.weight(1f))
        AmountText(amount, style = ChittaTheme.type.amountSmall)
    }
    HairlineRule()
}

@Composable
private fun EntryLine(t: Transaction, ui: PlanUi, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).pressClickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.name.ifBlank { categoryName(t.categoryId, ui.categories) }, style = ChittaTheme.type.body, color = ChittaTheme.colors.ink, maxLines = 1)
            Text(friendlyDay(t.dateMs, ui.nowMs, ui.zone) + " · " + accountName(t.accountId, ui.accounts), style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        }
        AmountText(Paise(Math.abs(t.amount)), tone = if (t.amount > 0) AmountTone.Income else AmountTone.Neutral, style = ChittaTheme.type.amountSmall)
    }
    HairlineRule()
}

// ---------------------------------------------------------------- Budget detail (PDF p.19)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetDetailScreen(vm: PlanViewModel, id: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val b = ui.budgets.firstOrNull { it.id == id }
    var editing by remember { mutableStateOf<CategoryLine?>(null) }
    var deleting by remember { mutableStateOf(false) }
    // PDF p.25: the rows under the chart open "Limits by category" and "History" as their own pages.
    var sub by remember { mutableStateOf<String?>(null) }
    var alertSheet by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = sub != null) { sub = null }
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxSize()) {
        val nameForBar = ui.budgets.firstOrNull { it.id == id }
        val dayLine = nameForBar?.let { nb -> val pd = Budgets.periodAt(nb, ui.nowMs, ui.zone); val sm = Budgets.summarize(nb, pd, ui.entries, ui.categories, ui.nowMs, ui.zone); if (sm.started && !sm.ended) "Day ${sm.dayOfPeriod} of ${sm.daysInPeriod}" else periodText(pd, ui.zone) }
        if (sub == null) TopBarDetail(nameForBar?.name ?: "Budget", subtitle = dayLine, onBack = onBack, actions = { IconAction(com.mansaketh.chitta.core.design.icons.ChittaIcons.Pencil, "Edit budget", onEdit) })
        else TopBarDetail(if (sub == "limits") "Limits by category" else "History", subtitle = nameForBar?.name, onBack = { sub = null })
        if (b == null) { if (!ui.loading) Gone("budget"); return@Column }
        val period = remember(b, ui.nowMs) { Budgets.periodAt(b, ui.nowMs, ui.zone) }
        val s = remember(b, ui.entries, ui.categories, ui.nowMs) { Budgets.summarize(b, period, ui.entries, ui.categories, ui.nowMs, ui.zone) }
        val lines = remember(b, ui.entries, ui.categories, ui.limits, ui.nowMs) { Budgets.breakdown(b, period, ui.entries, ui.categories, ui.limits) }
        val history = remember(b, ui.entries, ui.categories, ui.nowMs) { Budgets.history(b, ui.entries, ui.categories, ui.nowMs, ui.zone) }
        val verb = if (b.income) "saved" else "spent"
        if (sub == "limits") {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.FabInset)) {
                Text(if (b.absoluteSpendingLimit) "Press and hold a category to set its goal as an amount." else "Press and hold a category to set its goal as a percentage of the budget.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap3))
                if (lines.isEmpty()) Text("Nothing counted yet in this period.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap3))
                lines.forEach { l ->
                    Column(Modifier.fillMaxWidth().combinedClickable(role = Role.Button, onClick = {}, onLongClick = { editing = l }).padding(vertical = Dimens.Gap3)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(categoryName(l.categoryId, ui.categories), style = ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                            AmountText(l.spent, style = ChittaTheme.type.amountSmall)
                        }
                        if (l.limit != null && l.gauge != null) {
                            Text("goal ${rupees(l.limit!!)}", style = ChittaTheme.type.label, color = c.inkMuted)
                            GaugeBar(l.gauge!!, "${categoryName(l.categoryId, ui.categories)} against its goal")
                        }
                    }
                    HairlineRule()
                }
            }
        } else if (sub == "history") {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.FabInset)) {
                if (history.isEmpty()) Text("No earlier periods yet.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap3))
                history.forEach { (p, spent, limit) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin), verticalAlignment = Alignment.CenterVertically) {
                        Text(periodText(p, ui.zone), style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.weight(1f))
                        AmountText(spent, style = ChittaTheme.type.amountSmall)
                        Spacer(Modifier.width(Dimens.Gap2))
                        if (!b.income && spent > limit) Stamp("Over", tone = StampTone.Attention)
                    }
                    HairlineRule()
                }
            }
        }
        if (sub == null)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.FabInset)) {
            // PDF p.25: the pace stamp with its short verdict, then the figure with "left" on the same line.
            Row(Modifier.padding(top = Dimens.Gap3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                if (!b.income && s.pace == PaceStatus.OverLimit) Stamp("Over limit", tone = StampTone.Attention)
                else if (!b.income && s.pace == PaceStatus.OverPace) { Stamp("Over pace", tone = StampTone.Attention); Text("not over the limit", style = ChittaTheme.type.label, color = c.inkMuted) }
                if (!s.started) Stamp("Not started") else if (s.ended) Stamp("Ended")
            }
            Row(Modifier.padding(top = Dimens.Gap3), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                AmountText(if (s.left.isNegative) -s.left else s.left, style = ChittaTheme.type.amountLarge)
                Text(if (s.left.isNegative) "over" else "left", style = ChittaTheme.type.amountLarge, color = c.ink)
            }
            Text(if (s.left.isNegative) "over the budget of ${rupees(s.limit)}" else "of ${rupees(s.limit)}", style = ChittaTheme.type.label, color = c.inkMuted)
            if (BudgetAlerts.reached(b, s)) Text("You have reached ${b.alertPercent}% of the limit.", style = ChittaTheme.type.label, color = c.warningText)
            if (!s.carry.isZero) Text(if (s.carry.isNegative) "Includes ${rupees(-s.carry)} overspent in earlier periods, taken off." else "Includes ${rupees(s.carry)} carried in from earlier periods.", style = ChittaTheme.type.label, color = c.inkMuted)
            if (s.started) {
                // Spent and Forecast side by side, each under an ink rule (PDF p.25).
                Row(Modifier.fillMaxWidth().padding(top = Dimens.Gap3), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap6)) {
                    Column(Modifier.weight(1f)) {
                        HairlineRule(Modifier.padding(bottom = Dimens.Gap2))
                        Text(verb.replaceFirstChar { it.uppercase() }, style = ChittaTheme.type.label, color = c.inkMuted)
                        AmountText(s.spent, style = ChittaTheme.type.amountTile)
                        s.ideal?.let { Text("ideal by now ${rupees(it)}", style = ChittaTheme.type.label, color = c.inkMuted) }
                    }
                    Column(Modifier.weight(1f)) {
                        HairlineRule(Modifier.padding(bottom = Dimens.Gap2))
                        Text("Forecast", style = ChittaTheme.type.label, color = c.inkMuted)
                        if (!s.ended && s.dayOfPeriod >= 7 && s.forecast != null) {
                            AmountText(s.forecast!!, style = ChittaTheme.type.amountTile)
                            s.limitDay?.let { d ->
                                val on = Periods.date(period.startMs, ui.zone).plusDays((d - 1).toLong())
                                Text("reaches the limit ~${on.dayOfMonth} ${on.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)}", style = ChittaTheme.type.label, color = c.warningText)
                            }
                        } else Text("from day 7", style = ChittaTheme.type.label, color = c.inkMuted)
                    }
                }
                MonthRule(s.daysInPeriod, s.cumulative, s.limit, Modifier.padding(vertical = Dimens.Gap3))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap4)) {
                    listOf("Actual", "Ideal", "Forecast").forEach { Text(it, style = ChittaTheme.type.label, color = c.inkMuted) }
                }
                if (!s.ended && s.dayOfPeriod >= 1) FootNote("Forecast = ${rupees(Paise(s.spent.value / s.dayOfPeriod))} a day so far × ${s.daysInPeriod} days. Shown from day 7.")
            }
            // PDF p.25: four rows with their value and a caret. "Alerts" is left out: the app has no budget alerts.
            Spacer(Modifier.height(Dimens.Gap3))
            HairlineRule()
            NavRow("Limits by category", { sub = "limits" }, value = "${lines.count { it.limit != null }} set")
            NavRow("Rollover", onEdit, value = if (b.rollover) "On" else "Off")
            if (!b.income) NavRow("Alerts", { alertSheet = true }, value = BudgetAlerts.label(b.alertPercent))
            NavRow("History", { sub = "history" }, value = if (history.size == 1) "1 period" else "${history.size} periods")
            if (b.addedTransactionsOnly) Text("This was an \"added only\" budget in the old Paddhu Pusthakam app. This app cannot tell which entries were added to it, so it counts every matching entry.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap3))
            Text("Counts paid entries only. Lent and borrowed money is ${if (Budgets.filterOn(b.filters, BudgetFilter.INCLUDE_DEBT_AND_CREDIT)) "included" else "left out"}.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Delete this budget", { deleting = true }, kind = ButtonKind.Danger, modifier = Modifier.fillMaxWidth())
        }
    }
    editing?.let { l ->
        val current = ui.limits.firstOrNull { it.budgetId == id && it.categoryId == l.categoryId }
        val start = current?.let { if (b?.absoluteSpendingLimit == true) plainRupees(Paise(it.amount)) else (it.amount / 100.0).toString().removeSuffix(".0") } ?: ""
        TextSheet(
            "Goal for ${categoryName(l.categoryId, ui.categories)}", if (b?.absoluteSpendingLimit == true) "Amount (empty removes it)" else "Percent of the budget (empty removes it)", start,
            { raw -> if (b != null && vm.setLimit(b, l.categoryId, raw)) editing = null }, { editing = null }, numeric = true,
        )
    }
    if (alertSheet && b != null) ChoiceSheet(
        "Alert", buildList { add(Choice("Off", -1, selected = b.alertPercent == null)); BudgetAlerts.CHOICES.forEach { add(Choice(BudgetAlerts.label(it), it, selected = b.alertPercent == it)) } },
        { v -> alertSheet = false; vm.setAlert(b, v.takeIf { it > 0 }) }, { alertSheet = false },
    )
    if (deleting && b != null) ConfirmSheet("Delete ${b.name}?", listOf("Its category goals go too. Your entries are not touched.", "You can undo this for 10 seconds."), "Delete", { vm.deleteBudget(id); deleting = false; onBack() }, { deleting = false })
}

private fun plainRupees(p: Paise) = IndianFormat.rupees(p, symbol = "").replace(",", "")

// ---------------------------------------------------------------- Goal detail (PDF p.20)
@Composable
fun GoalDetailScreen(vm: PlanViewModel, id: String, onBack: () -> Unit, onEdit: () -> Unit, onOpenEntry: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    val o = ui.objectives.firstOrNull { it.id == id }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBarDetail("Goal", onBack = onBack, actions = { IconAction(com.mansaketh.chitta.core.design.icons.ChittaIcons.Pencil, "Edit goal", onEdit) })
        if (o == null) { if (!ui.loading) Gone("goal"); return@Column }
        val p = remember(o, ui.entries, ui.nowMs) { Goals.progress(o, ui.entries, ui.nowMs, ui.zone) }
        val linked = ui.entries.filter { it.objectiveId == o.id }.sortedByDescending { it.dateMs }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.FabInset)) {
            Text(o.name, style = ChittaTheme.type.headline, color = c.ink)
            Text(if (o.income) "Saving up" else "Spending on", style = ChittaTheme.type.label, color = c.inkMuted)
            Spacer(Modifier.height(Dimens.Gap3))
            AmountText(p.saved, style = ChittaTheme.type.amountHero)
            Text("of ${rupees(p.target)}", style = ChittaTheme.type.body, color = c.inkMuted)
            Spacer(Modifier.height(Dimens.Gap3))
            GaugeBar(p.gauge, "${o.name} goal")
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), modifier = Modifier.padding(vertical = Dimens.Gap3)) {
                if (p.reached) Stamp("Reached") else if (p.pastEnd) Stamp("Behind", tone = StampTone.Attention)
            }
            Figure("Still to go", p.remaining)
            p.perMonthNeeded?.let { Figure("Needed each month (${p.monthsLeft} left)", it) }
            o.endDateMs?.let { Text("Finish by ${dateText(it, ui.zone)}", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2)) }
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton(if (o.income) "Add money" else "Add a payment", { adding = true }, modifier = Modifier.fillMaxWidth())
            Section("Added so far")
            if (linked.isEmpty()) Text("Nothing added yet.", style = ChittaTheme.type.body, color = c.inkMuted)
            linked.forEach { EntryLine(it, ui) { onOpenEntry(it.id) } }
            Text("Counts paid entries linked to this goal.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Delete this goal", { deleting = true }, kind = ButtonKind.Danger, modifier = Modifier.fillMaxWidth())
        }
        if (adding) AmountAccountSheet(if (o.income) "Add money" else "Add a payment", "Amount", ui.accounts, o.accountId, confirmLabel = "Save", onSave = { amount, account, problem -> if (vm.contribute(o, amount, account, problem)) adding = false }, onDismiss = { adding = false })
        if (deleting) ConfirmSheet("Delete ${o.name}?", listOf("The ${linked.size} linked ${if (linked.size == 1) "entry stays" else "entries stay"} in your ledger but are no longer linked to a goal.", "You can undo this for 10 seconds."), "Delete", { vm.deleteObjective(id); deleting = false; onBack() }, { deleting = false })
    }
}

// ---------------------------------------------------------------- Loan detail (PDF p.21)
@Composable
fun LoanDetailScreen(vm: PlanViewModel, id: String, onBack: () -> Unit, onEdit: () -> Unit, onOpenEntry: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    val o = ui.objectives.firstOrNull { it.id == id }
    val terms = ui.terms.firstOrNull { it.objectiveId == id }
    var action by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBarDetail("Loan", onBack = onBack, actions = { IconAction(com.mansaketh.chitta.core.design.icons.ChittaIcons.Pencil, "Edit loan", onEdit) })
        if (o == null) { if (!ui.loading) Gone("loan"); return@Column }
        val linked = ui.entries.filter { it.objectiveLoanId == o.id }.sortedByDescending { it.dateMs }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = Dimens.FabInset)) {
            Text(o.name, style = ChittaTheme.type.headline, color = c.ink)
            Row(Modifier.padding(vertical = Dimens.Gap2)) { Stamp(if (o.income) "Lent" else "Borrowed") }
            if (terms == null) {
                val p = remember(o, ui.entries) { Loans.progress(o, ui.entries) }
                AmountText(p.outstanding, style = ChittaTheme.type.amountHero)
                Text(if (o.income) "still owed to you" else "still to pay back", style = ChittaTheme.type.body, color = c.inkMuted)
                Spacer(Modifier.height(Dimens.Gap3))
                GaugeBar(p.gauge, "${o.name} repaid")
                Spacer(Modifier.height(Dimens.Gap3))
                Figure("Total", p.total); Figure("Repaid so far", p.repaid)
                if (!p.overpaid.isZero) Figure("Paid beyond the total", p.overpaid)
                Spacer(Modifier.height(Dimens.Gap4))
                ChittaButton(if (o.income) "Record money back" else "Record a payment", { action = "repay" }, modifier = Modifier.fillMaxWidth())
                ChittaButton(if (o.income) "Lend more" else "Borrow more", { action = "more" }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
            } else {
                val p = remember(terms, linked) { Instalments.progress(terms, linked.sumOf { it.amount }, ui.zone) }
                val sum = remember(terms) { Instalments.summarize(terms, ui.zone) }
                val rows = remember(terms) { Instalments.schedule(terms, ui.zone) }
                AmountText(p.outstanding, style = ChittaTheme.type.amountHero)
                Text("still to pay of ${rupees(p.totalPayable)}", style = ChittaTheme.type.body, color = c.inkMuted)
                Spacer(Modifier.height(Dimens.Gap3)); GaugeBar(p.gauge, "${o.name} repaid"); Spacer(Modifier.height(Dimens.Gap3))
                Figure("Borrowed", sum.principal); Figure("Interest", sum.totalInterest); Figure("GST on interest", sum.gstOnInterest)
                if (!sum.fee.isZero) { Figure("Fee", sum.fee); Figure("GST on fee", sum.gstOnFee) }
                // Only instalment loans can be shared: a loan without terms has nothing a viewer could see.
                com.mansaketh.chitta.ui.household.sharingLine(vm.shared, SharedItems.KIND_LOAN, o.id)?.let { line -> ValueRow("Who sees it", line, onClick = { sharing = true }) }
                com.mansaketh.chitta.ui.household.editedByLine(vm.shared, SharedItems.KIND_LOAN, o.id)?.let { Text(it, style = ChittaTheme.type.label, color = c.inkMuted) }
                if (terms.note.isNotBlank()) ValueRow("Note", terms.note)
                FootNote("Interest is worked on the balance left each month; the last instalment is adjusted so the loan ends at zero. Tick an instalment when you have paid it.")
                Section("Instalments")
                rows.forEach { r ->
                    val paid = r.number in terms.paidInstalments
                    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).pressClickable(role = Role.Checkbox) { vm.tick(terms, r.number, !paid) }, verticalAlignment = Alignment.CenterVertically) {
                        TickBox(paid, Modifier.semantics { contentDescription = if (paid) "Paid" else "Not paid" })
                        Spacer(Modifier.width(Dimens.Gap3))
                        Column(Modifier.weight(1f)) {
                            Text("${r.number}. ${dateText(r.dueMs, ui.zone)}", style = ChittaTheme.type.body, color = c.ink)
                            Text("interest ${rupees(r.interest)}, principal ${rupees(r.principalPart)}", style = ChittaTheme.type.label, color = c.inkMuted)
                        }
                        AmountText(r.totalDue, style = ChittaTheme.type.amountSmall)
                    }
                    HairlineRule()
                }
            }
            if (linked.isNotEmpty()) {
                Section("Entries")
                linked.forEach { EntryLine(it, ui) { onOpenEntry(it.id) } }
            }
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Delete this loan", { deleting = true }, kind = ButtonKind.Danger, modifier = Modifier.fillMaxWidth())
        }
        when (action) {
            "repay", "more" -> {
                val more = action == "more"
                AmountAccountSheet(if (more) (if (o.income) "Lend more" else "Borrow more") else (if (o.income) "Money back" else "Payment"), "Amount", ui.accounts, o.accountId, confirmLabel = "Save",
                    onSave = { amount, account, problem -> if (vm.repay(o, amount, account, more, problem)) action = null }, onDismiss = { action = null })
            }
        }
        if (sharing) com.mansaketh.chitta.ui.household.ShareSheet(vm.shared, SharedItems.KIND_LOAN, o.id, o.name) { sharing = false }
        if (deleting) ConfirmSheet("Delete ${o.name}?", listOf("The ${linked.size} linked ${if (linked.size == 1) "entry stays" else "entries stay"} in your ledger but are no longer part of a loan.", "You can undo this for 10 seconds."), "Delete", { vm.deleteObjective(id); deleting = false; onBack() }, { deleting = false })
    }
}
