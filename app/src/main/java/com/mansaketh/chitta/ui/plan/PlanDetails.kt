package com.mansaketh.chitta.ui.plan

import androidx.compose.foundation.ExperimentalFoundationApi
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
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
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
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Budget", onBack = onBack, actions = { IconAction(com.mansaketh.chitta.core.design.icons.ChittaIcons.Pencil, "Edit budget", onEdit) })
        if (b == null) { if (!ui.loading) Gone("budget"); return@Column }
        val period = remember(b, ui.nowMs) { Budgets.periodAt(b, ui.nowMs, ui.zone) }
        val s = remember(b, ui.entries, ui.categories, ui.nowMs) { Budgets.summarize(b, period, ui.entries, ui.categories, ui.nowMs, ui.zone) }
        val lines = remember(b, ui.entries, ui.categories, ui.limits, ui.nowMs) { Budgets.breakdown(b, period, ui.entries, ui.categories, ui.limits) }
        val history = remember(b, ui.entries, ui.categories, ui.nowMs) { Budgets.history(b, ui.entries, ui.categories, ui.nowMs, ui.zone) }
        val verb = if (b.income) "saved" else "spent"
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 120.dp)) {
            Text(b.name, style = ChittaTheme.type.headline, color = c.ink)
            Text(periodText(period, ui.zone), style = ChittaTheme.type.label, color = c.inkMuted)
            Spacer(Modifier.height(Dimens.Gap3))
            AmountText(if (s.left.isNegative) -s.left else s.left, style = ChittaTheme.type.amountHero)
            Text(if (s.left.isNegative) "over the budget of ${rupees(s.limit)}" else "left of ${rupees(s.limit)}", style = ChittaTheme.type.body, color = c.inkMuted)
            if (!s.carry.isZero) Text(if (s.carry.isNegative) "Includes ${rupees(-s.carry)} overspent in earlier periods, taken off." else "Includes ${rupees(s.carry)} carried in from earlier periods.", style = ChittaTheme.type.label, color = c.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), modifier = Modifier.padding(vertical = Dimens.Gap3)) {
                if (!b.income && s.pace == PaceStatus.OverLimit) Stamp("Over limit", tone = StampTone.Attention)
                else if (!b.income && s.pace == PaceStatus.OverPace) Stamp("Over pace", tone = StampTone.Attention)
                if (!s.started) Stamp("Not started") else if (s.ended) Stamp("Ended")
            }
            if (s.started) {
                Text(if (s.ended) "The whole period." else "Day ${s.dayOfPeriod} of ${s.daysInPeriod}, ${s.daysLeft} left.", style = ChittaTheme.type.label, color = c.inkMuted)
                MonthRule(s.daysInPeriod, s.cumulative, s.limit, Modifier.padding(vertical = Dimens.Gap3))
                Figure("$verb so far".replaceFirstChar { it.uppercase() }, s.spent)
                s.ideal?.let { Figure("On an even pace by today", it) }
                if (!s.ended && s.dayOfPeriod >= 7) s.forecast?.let { Figure("At this pace you will have $verb", it) }
                else if (!s.ended) Text("The forecast shows from day 7, when there is enough to go on.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))
                if (!s.ended) s.limitDay?.let { Text("At this pace the limit is reached on day $it.", style = ChittaTheme.type.label, color = c.inkMuted) }
            }
            SectionHeader("Where it went")
            Text(if (b.absoluteSpendingLimit) "Press and hold a category to set its goal as an amount." else "Press and hold a category to set its goal as a percentage of the budget.", style = ChittaTheme.type.label, color = c.inkMuted)
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
            if (history.isNotEmpty()) {
                SectionHeader("Earlier periods")
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
            if (b.addedTransactionsOnly) Text("This was an \"added only\" budget in Paddhu Pusthakam. Chitta cannot tell which entries were added to it, so it counts every matching entry.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap3))
            Text("Counts paid entries only. Lent and borrowed money is ${if (Budgets.filterOn(b.filters, BudgetFilter.INCLUDE_DEBT_AND_CREDIT)) "included" else "left out"}.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Delete this budget", { deleting = true }, style = ButtonStyle.Quiet)
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
        ChittaTopBar("Goal", onBack = onBack, actions = { IconAction(com.mansaketh.chitta.core.design.icons.ChittaIcons.Pencil, "Edit goal", onEdit) })
        if (o == null) { if (!ui.loading) Gone("goal"); return@Column }
        val p = remember(o, ui.entries, ui.nowMs) { Goals.progress(o, ui.entries, ui.nowMs, ui.zone) }
        val linked = ui.entries.filter { it.objectiveId == o.id }.sortedByDescending { it.dateMs }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 120.dp)) {
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
            ChittaButton(if (o.income) "Add money" else "Add a payment", { adding = true })
            SectionHeader("Added so far")
            if (linked.isEmpty()) Text("Nothing added yet.", style = ChittaTheme.type.body, color = c.inkMuted)
            linked.forEach { EntryLine(it, ui) { onOpenEntry(it.id) } }
            Text("Counts paid entries linked to this goal.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Delete this goal", { deleting = true }, style = ButtonStyle.Quiet)
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
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Loan", onBack = onBack, actions = { IconAction(com.mansaketh.chitta.core.design.icons.ChittaIcons.Pencil, "Edit loan", onEdit) })
        if (o == null) { if (!ui.loading) Gone("loan"); return@Column }
        val linked = ui.entries.filter { it.objectiveLoanId == o.id }.sortedByDescending { it.dateMs }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 120.dp)) {
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
                ChittaButton(if (o.income) "Record money back" else "Record a payment", { action = "repay" })
                ChittaButton(if (o.income) "Lend more" else "Borrow more", { action = "more" }, style = ButtonStyle.Quiet)
            } else {
                val p = remember(terms, linked) { Instalments.progress(terms, linked.sumOf { it.amount }, ui.zone) }
                val sum = remember(terms) { Instalments.summarize(terms, ui.zone) }
                val rows = remember(terms) { Instalments.schedule(terms, ui.zone) }
                AmountText(p.outstanding, style = ChittaTheme.type.amountHero)
                Text("still to pay of ${rupees(p.totalPayable)}", style = ChittaTheme.type.body, color = c.inkMuted)
                Spacer(Modifier.height(Dimens.Gap3)); GaugeBar(p.gauge, "${o.name} repaid"); Spacer(Modifier.height(Dimens.Gap3))
                Figure("Borrowed", sum.principal); Figure("Interest", sum.totalInterest); Figure("GST on interest", sum.gstOnInterest)
                if (!sum.fee.isZero) { Figure("Fee", sum.fee); Figure("GST on fee", sum.gstOnFee) }
                Text("Interest is worked on the balance left each month; the last instalment is adjusted so the loan ends at zero. Tick an instalment when you have paid it.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap2))
                SectionHeader("Instalments")
                rows.forEach { r ->
                    val paid = r.number in terms.paidInstalments
                    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(role = Role.Checkbox) { vm.tick(terms, r.number, !paid) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (paid) "✓" else "○", style = ChittaTheme.type.title, color = c.ink, modifier = Modifier.width(Dimens.Gap8))
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
                SectionHeader("Entries")
                linked.forEach { EntryLine(it, ui) { onOpenEntry(it.id) } }
            }
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Delete this loan", { deleting = true }, style = ButtonStyle.Quiet)
        }
        when (action) {
            "repay", "more" -> {
                val more = action == "more"
                AmountAccountSheet(if (more) (if (o.income) "Lend more" else "Borrow more") else (if (o.income) "Money back" else "Payment"), "Amount", ui.accounts, o.accountId, confirmLabel = "Save",
                    onSave = { amount, account, problem -> if (vm.repay(o, amount, account, more, problem)) action = null }, onDismiss = { action = null })
            }
        }
        if (deleting) ConfirmSheet("Delete ${o.name}?", listOf("The ${linked.size} linked ${if (linked.size == 1) "entry stays" else "entries stay"} in your ledger but are no longer part of a loan.", "You can undo this for 10 seconds."), "Delete", { vm.deleteObjective(id); deleting = false; onBack() }, { deleting = false })
    }
}
