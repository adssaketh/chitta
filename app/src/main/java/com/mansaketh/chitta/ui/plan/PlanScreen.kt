package com.mansaketh.chitta.ui.plan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.*
import com.mansaketh.chitta.core.model.*
import com.mansaketh.chitta.core.money.PaceStatus
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.Segments
import com.mansaketh.chitta.ui.*
import java.time.YearMonth

val PLAN_TABS = listOf("Budgets", "Goals", "Loans", "Bills", "Calendar", "Sinking funds")

/** One row in a Plan list: a title with an amount on the right, an optional gauge, a line of small text and an optional stamp. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlanRow(
    title: String, amount: Paise?, subtitle: String? = null, gauge: Segments.Gauge? = null, gaugeLabel: String = "", stamp: String? = null,
    attention: Boolean = false, onClick: () -> Unit = {}, onLongClick: (() -> Unit)? = null,
) {
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick).padding(vertical = Dimens.Gap3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = ChittaTheme.type.bodyMedium, color = c.ink, maxLines = 2)
                if (subtitle != null) Text(subtitle, style = ChittaTheme.type.label, color = c.inkMuted)
            }
            if (stamp != null) { Stamp(stamp, tone = if (attention) StampTone.Attention else StampTone.Neutral); Spacer(Modifier.width(Dimens.Gap2)) }
            if (amount != null) AmountText(amount)
        }
        if (gauge != null) { Spacer(Modifier.height(Dimens.Gap2)); GaugeBar(gauge, gaugeLabel) }
    }
    HairlineRule()
}

@Composable
fun PlanScreen(vm: PlanViewModel, tab: Int, onTab: (Int) -> Unit, onOpen: (String) -> Unit, onSheet: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    var reminders by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.runAutoPay() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.Margin), verticalAlignment = Alignment.CenterVertically) {
            com.mansaketh.chitta.core.design.components.ScreenTitle("Plan", com.mansaketh.chitta.core.design.icons.ChittaIcons.Plan, Modifier.weight(1f))
            IconAction(com.mansaketh.chitta.core.design.icons.ChittaIcons.Bell, "Bills and reminders", { reminders = true })
        }
        TextTabs(PLAN_TABS, tab, onTab, Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin))
        HairlineRule()
        when {
            ui.loading -> LoadingPlaceholder(Modifier.padding(Dimens.Margin))
            else -> Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> BudgetsTab(ui, onOpen, onSheet)
                    1 -> GoalsTab(ui, onOpen, onSheet)
                    2 -> LoansTab(vm, ui, onOpen, onSheet)
                    3 -> BillsTab(vm, ui, onSheet, { reminders = true })
                    4 -> CalendarTab(vm, ui)
                    else -> FundsTab(ui, onSheet)
                }
            }
        }
    }
    if (reminders) ReminderSheet(
        settings.reminders, settings.autoPay, settings.paidOnDue, vm.askedNotifications, vm::notificationsAsked, vm::setReminders, vm::setAutoPay, vm::setPaidOnDue, { reminders = false },
    )
}

// ---------------------------------------------------------------- Budgets
@Composable
private fun BudgetsTab(ui: PlanUi, onOpen: (String) -> Unit, onSheet: (String) -> Unit) {
    val budgets = ui.budgets.filter { !it.archived }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 120.dp)) {
        if (budgets.isEmpty()) item {
            EmptyState("A budget says how much you mean to spend on something, and shows if you are on pace.", "Add a budget", { onSheet("new-budget") })
        }
        items(budgets, key = { it.id }) { b ->
            val s = remember(b, ui.entries, ui.categories, ui.nowMs) { Budgets.summarize(b, Budgets.periodAt(b, ui.nowMs, ui.zone), ui.entries, ui.categories, ui.nowMs, ui.zone) }
            PlanRow(
                b.name, s.spent, "of ${rupees(s.limit)} · ${if (b.income) "saved" else "spent"} ${periodText(s.period, ui.zone)}",
                Segments.gauge(s.spent, s.limit), "${b.name} budget",
                stamp = when (s.pace) { PaceStatus.OverLimit -> "Over limit"; PaceStatus.OverPace -> "Over pace"; else -> null }.takeIf { !b.income },
                attention = true, onClick = { onOpen("budget:${b.id}") }, onLongClick = { onSheet("edit-budget:${b.id}") },
            )
        }
        if (budgets.isNotEmpty()) item { Spacer(Modifier.height(Dimens.Gap4)); ChittaButton("Add a budget", { onSheet("new-budget") }, style = ButtonStyle.Quiet) }
    }
}

// ---------------------------------------------------------------- Goals
@Composable
private fun GoalsTab(ui: PlanUi, onOpen: (String) -> Unit, onSheet: (String) -> Unit) {
    val goals = ui.objectives.filter { Goals.isGoal(it) && !it.archived }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 120.dp)) {
        if (goals.isEmpty()) item { EmptyState("A goal is money you are setting aside for something, with a target and an optional date.", "Add a goal", { onSheet("new-goal") }) }
        items(goals, key = { it.id }) { o ->
            val p = remember(o, ui.entries, ui.nowMs) { Goals.progress(o, ui.entries, ui.nowMs, ui.zone) }
            val sub = when {
                p.reached -> "Reached"
                p.perMonthNeeded != null -> "${rupees(p.perMonthNeeded!!)} a month for ${p.monthsLeft} ${if (p.monthsLeft == 1) "month" else "months"}"
                else -> "${rupees(p.remaining)} to go"
            }
            PlanRow(o.name, p.saved, "of ${rupees(p.target)} · $sub", p.gauge, "${o.name} goal", stamp = if (p.pastEnd) "Behind" else null, attention = true, onClick = { onOpen("goal:${o.id}") }, onLongClick = { onSheet("edit-goal:${o.id}") })
        }
        if (goals.isNotEmpty()) item { Spacer(Modifier.height(Dimens.Gap4)); ChittaButton("Add a goal", { onSheet("new-goal") }, style = ButtonStyle.Quiet) }
    }
}

// ---------------------------------------------------------------- Loans
@Composable
private fun LoansTab(vm: PlanViewModel, ui: PlanUi, onOpen: (String) -> Unit, onSheet: (String) -> Unit) {
    val loans = ui.objectives.filter { Loans.isLoan(it) && !it.archived }
    val termsOf = ui.terms.associateBy { it.objectiveId }
    val oneTime = ui.entries.filter { Loans.isOneTime(it) }
    val open = oneTime.filter { it.paid }
    val done = oneTime.filter { !it.paid }.sortedByDescending { it.modifiedAtMs ?: it.dateMs }.take(5)
    var acting by remember { mutableStateOf<Transaction?>(null) }
    var partOf by remember { mutableStateOf<Transaction?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 120.dp)) {
        if (loans.isEmpty() && oneTime.isEmpty()) item { EmptyState("Money you lent or borrowed, kept apart from your spending.", "Add a loan", { onSheet("new-loan") }) }
        if (loans.isNotEmpty()) item { SectionHeader("Loans") }
        items(loans, key = { it.id }) { o ->
            val t = termsOf[o.id]
            val linked = ui.entries.filter { it.objectiveLoanId == o.id }
            if (t != null) {
                val p = Instalments.progress(t, linked.sumOf { it.amount }, ui.zone)
                val next = Instalments.next(t, ui.zone)
                PlanRow(o.name, p.outstanding, (if (o.income) "Lent" else "Borrowed") + (next?.let { " · next ${rupees(it.totalDue)} on ${dateText(it.dueMs, ui.zone)}" } ?: " · all instalments ticked"), p.gauge, "${o.name} repaid", onClick = { onOpen("loan:${o.id}") }, onLongClick = { onSheet("edit-loan:${o.id}") })
            } else {
                val p = Loans.progress(o, ui.entries)
                PlanRow(o.name, p.outstanding, (if (o.income) "Lent" else "Borrowed") + " · repaid ${rupees(p.repaid)} of ${rupees(p.total)}", p.gauge, "${o.name} repaid", onClick = { onOpen("loan:${o.id}") }, onLongClick = { onSheet("edit-loan:${o.id}") })
            }
        }
        if (open.isNotEmpty()) item { SectionHeader("Lent and borrowed once") }
        items(open, key = { it.id }) { t ->
            val lent = t.kind == TransactionKind.LENT
            PlanRow(t.name.ifBlank { if (lent) "Lent" else "Borrowed" }, Paise(Math.abs(t.amount)), (if (lent) "Lent " else "Borrowed ") + friendlyDay(t.dateMs, ui.nowMs, ui.zone), stamp = if (lent) "Lent" else "Borrowed", onClick = { acting = t })
        }
        if (done.isNotEmpty()) item { SectionHeader("Collected recently") }
        items(done, key = { it.id }) { t ->
            PlanRow(t.name.ifBlank { "Entry" }, Paise(Math.abs(t.amount)), "Collected", stamp = "Collected", onClick = { acting = t })
        }
        item { Spacer(Modifier.height(Dimens.Gap4)); ChittaButton("Add a loan", { onSheet("new-loan") }, style = ButtonStyle.Quiet) }
    }
    acting?.let { t ->
        val lent = t.kind == TransactionKind.LENT
        if (t.paid) ChoiceSheet(t.name.ifBlank { "Entry" }, listOf(Choice(if (lent) "Collect all" else "Settle all", "all"), Choice(if (lent) "Collect part" else "Pay part", "part"), Choice("Open the entry", "open")), { v ->
            when (v) { "all" -> { vm.collectAll(t); acting = null }; "part" -> { partOf = t; acting = null }; else -> { acting = null; onOpen("detail:${t.id}") } }
        }, { acting = null })
        else ChoiceSheet(t.name.ifBlank { "Entry" }, listOf(Choice("Mark as not collected", "undo"), Choice("Open the entry", "open")), { v ->
            if (v == "undo") vm.uncollect(t) else onOpen("detail:${t.id}"); acting = null
        }, { acting = null })
    }
    partOf?.let { t ->
        val lent = t.kind == TransactionKind.LENT
        AmountAccountSheet(
            if (lent) "Collect part" else "Pay part", "Amount", ui.accounts, t.accountId, confirmLabel = "Save",
            note = "The rest stays owed. This becomes a loan with the original as its first record.",
            onSave = { amount, account, problem -> if (vm.collectPart(t, amount, account, problem)) partOf = null }, onDismiss = { partOf = null },
        )
    }
}

// ---------------------------------------------------------------- Bills (Upcoming and subscriptions)
@Composable
private fun BillsTab(vm: PlanViewModel, ui: PlanUi, onSheet: (String) -> Unit, onSettings: () -> Unit) {
    val scheduled = ui.entries.filter { Bills.isScheduled(it) && Bills.isOpen(it) && it.pairedTransactionId == null }
    val overdue = scheduled.filter { Bills.status(it, ui.nowMs, ui.zone) == BillStatus.Overdue }.sortedBy { Bills.dueMs(it) }
    val future = scheduled.filter { Bills.status(it, ui.nowMs, ui.zone) != BillStatus.Overdue }.sortedBy { Bills.dueMs(it) }
    val totals = remember(ui.entries) { Scheduled.totals(ui.entries) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showTotals by rememberSaveable { mutableStateOf(false) }
    var acting by remember { mutableStateOf<Transaction?>(null) }
    var different by remember { mutableStateOf<Transaction?>(null) }
    var amountFor by remember { mutableStateOf<Transaction?>(null) }
    var deleting by remember { mutableStateOf<Transaction?>(null) }
    val shown = if (!expanded && future.size >= 3) future.take(2) else future
    val suggestions by vm.suggestions.collectAsState()
    val dismissed by vm.dismissedSubscriptions.collectAsState()
    var suggestionsOpen by rememberSaveable { mutableStateOf(false) }
    var dismissedOpen by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf<SubscriptionSuggestion?>(null) }

    @Composable fun Bill(t: Transaction) {
        val st = Bills.status(t, ui.nowMs, ui.zone)
        val repeating = Bills.isRepeating(t)
        PlanRow(
            t.name.ifBlank { categoryName(t.categoryId, ui.categories) }, if (Bills.needsAmount(t)) null else Paise(Math.abs(t.amount)),
            (if (Bills.needsAmount(t)) "Amount not set · " else "") + friendlyDay(Bills.dueMs(t), ui.nowMs, ui.zone) + (if (repeating) " · repeats ${repeatLabel(t.repeat!!, t.periodLength ?: 1)}" else "") + (if (t.income) " · to receive" else ""),
            stamp = if (st == BillStatus.Overdue) "Overdue" else if (t.kind == TransactionKind.SUBSCRIPTION) "Subscription" else null,
            attention = st == BillStatus.Overdue, onClick = { acting = t },
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 120.dp)) {
        if (scheduled.isEmpty()) item { EmptyState("Bills and subscriptions you know are coming, so nothing surprises you.", "Add a bill", { onSheet("new-bill") }) }
        else item {
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { showTotals = true }.padding(vertical = Dimens.Gap3)) {
                Text("Still to pay", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
                AmountText(totals.total, style = ChittaTheme.type.amountLarge)
                Text("About ${rupees(totals.perMonth)} a month · ${rupees(totals.perYear)} a year. Tap to see how this adds up.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
            }
            HairlineRule()
        }
        if (suggestions.isNotEmpty()) item {
            val names = suggestions.take(2).joinToString(", ") { it.name } + if (suggestions.size > 2) " and ${suggestions.size - 2} more" else ""
            PlanRow(
                if (suggestions.size == 1) "1 possible subscription" else "${suggestions.size} possible subscriptions",
                null, "$names. Found in your ledger. Nothing is added until you say so.", onClick = { suggestionsOpen = true },
            )
        }
        if (overdue.isNotEmpty()) item { SectionHeader("Overdue") }
        items(overdue, key = { it.id }) { Bill(it) }
        if (future.isNotEmpty()) item { SectionHeader("Coming up") }
        items(shown, key = { it.id }) { Bill(it) }
        if (future.size >= 3 && !expanded) item { ChittaButton("Show ${future.size - 2} more", { expanded = true }, style = ButtonStyle.Quiet) }
        item {
            Spacer(Modifier.height(Dimens.Gap4)); ChittaButton("Add a bill", { onSheet("new-bill") }, style = ButtonStyle.Quiet)
            if (dismissed.isNotEmpty()) ChittaButton("Not subscriptions (${dismissed.size})", { dismissedOpen = true }, style = ButtonStyle.Quiet)
            ChittaButton("Reminders and auto-pay", onSettings, style = ButtonStyle.Quiet)
        }
    }

    LaunchedEffect(suggestions.isEmpty()) { if (suggestions.isEmpty()) suggestionsOpen = false }
    if (suggestionsOpen && suggestions.isNotEmpty()) BottomOverlay({ suggestionsOpen = false }) {
        Text("Possible subscriptions", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Text("Charges that came back on a steady rhythm. Tap one to add it or set it aside.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(suggestions, key = { it.key }) { s ->
                PlanRow(
                    s.name, s.amount,
                    "${s.cadence.label.replaceFirstChar { it.uppercase() }} · seen ${s.charges} times · next ${friendlyDay(s.dueMs, ui.nowMs, ui.zone)}",
                    onClick = { picked = s },
                )
            }
        }
    }
    picked?.let { s ->
        ChoiceSheet(s.name, listOf(
            Choice("Add to my subscriptions", "add", "${s.cadence.label.replaceFirstChar { it.uppercase() }}, first due ${friendlyDay(s.dueMs, ui.nowMs, ui.zone)}. Your past entries stay as they are."),
            Choice("Not a subscription", "no", "Never suggested again. You can bring it back."),
        ), { v -> picked = null; if (v == "add") vm.addSuggestion(s) else vm.dismissSuggestion(s) }, { picked = null })
    }
    if (dismissedOpen) ChoiceSheet(
        "Not subscriptions", dismissed.map { (k, n) -> Choice(n, k, "Tap to let it be suggested again.") },
        { k -> vm.restoreSuggestion(k); if (dismissed.size <= 1) dismissedOpen = false }, { dismissedOpen = false }, emptyText = "Nothing set aside.",
    )

    if (showTotals) ConfirmSheet("How this adds up", listOf(
        "Only money going out is counted, and only bills not yet paid or skipped.",
        "Still to pay: ${rupees(totals.total)}.",
        "Repeating bills (${totals.repeating}) come to about ${rupees(totals.perMonth)} a month and ${rupees(totals.perYear)} a year at their current repeat.",
    ), "Close", { showTotals = false }, { showTotals = false }, attention = false)

    acting?.let { t ->
        ChoiceSheet(t.name.ifBlank { "Bill" }, buildList {
            if (Bills.needsAmount(t)) add(Choice("Set the amount", "amount")) else {
                add(Choice("Pay ${rupees(Paise(Math.abs(t.amount)))}", "pay")); add(Choice("Pay a different amount or account", "different"))
            }
            add(Choice("Skip this one", "skip")); add(Choice("Edit", "edit")); add(Choice("Delete", "delete"))
        }, { v ->
            acting = null
            when (v) { "pay" -> vm.pay(t); "different" -> different = t; "amount" -> amountFor = t; "skip" -> vm.skip(t); "edit" -> onSheet("edit-bill:${t.id}"); "delete" -> deleting = t }
        }, { acting = null })
    }
    different?.let { t ->
        AmountAccountSheet("Pay a bill", "Amount paid", ui.accounts, t.accountId, plainAmount(t), "Pay", note = "Only this one changes. The next one keeps the usual amount and account.",
            onSave = { amount, account, problem -> if (vm.pay(t, amount, account, problem)) different = null }, onDismiss = { different = null })
    }
    amountFor?.let { t ->
        TextSheet("Amount", "Amount", "", { if (vm.setAmount(t, it)) amountFor = null }, { amountFor = null }, numeric = true)
    }
    deleting?.let { t ->
        ConfirmSheet("Delete this bill?", listOf("${t.name.ifBlank { "This bill" }} is removed from the list. ${if (Bills.isRepeating(t)) "Later ones are not made." else ""}".trim(), "You can undo this for 10 seconds, and it stays in history."), "Delete", { vm.deleteBill(t); deleting = null }, { deleting = null })
    }
}

private fun plainAmount(t: Transaction) = if (t.amount == 0L) "" else com.mansaketh.chitta.core.money.IndianFormat.rupees(Paise(Math.abs(t.amount)), symbol = "").replace(",", "")

// ---------------------------------------------------------------- Bill calendar (PDF p.22)
@Composable
private fun CalendarTab(vm: PlanViewModel, ui: PlanUi) {
    var epoch by rememberSaveable { mutableStateOf(YearMonth.from(java.time.Instant.ofEpochMilli(ui.nowMs).atZone(ui.zone)).let { it.year * 12 + it.monthValue - 1 }) }
    val month = YearMonth.of(epoch / 12, epoch % 12 + 1)
    val days = remember(ui.entries, month, ui.nowMs) { BillCalendar.month(ui.entries, month, ui.nowMs, ui.zone) }
    var day by remember { mutableStateOf<CalendarDay?>(null) }
    val c = ChittaTheme.colors
    val toPay = Paise(days.sumOf { it.toPay.value })
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ChittaButton("Earlier", { epoch -= 1 }, style = ButtonStyle.Quiet)
            Text("${month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${month.year}", style = ChittaTheme.type.title, color = c.ink, modifier = Modifier.weight(1f).padding(horizontal = Dimens.Gap2))
            ChittaButton("Later", { epoch += 1 }, style = ButtonStyle.Quiet)
        }
        Text(if (toPay.isZero) "Nothing left to pay this month." else "${rupees(toPay)} still to pay this month.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap3))
        Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), style = ChittaTheme.type.label, color = c.inkMuted) } }
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells: List<CalendarDay?> = List(lead) { null } + days
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    Box(Modifier.weight(1f).heightIn(min = 52.dp).then(if (d != null && d.items.isNotEmpty()) Modifier.clickable(role = Role.Button, onClickLabel = "Show bills") { day = d } else Modifier)) {
                        if (d != null) Column {
                            Text(d.date.dayOfMonth.toString(), style = ChittaTheme.type.body, color = c.ink)
                            if (d.items.isNotEmpty()) Text(d.items.take(3).joinToString("") { glyph(it) }, style = ChittaTheme.type.label, color = if (d.items.any { it.status == BillStatus.Overdue }) c.warningText else c.inkMuted)
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(Dimens.Gap3))
        Text("● due   ▲ overdue   ✓ paid   – skipped   ○ comes later", style = ChittaTheme.type.label, color = c.inkMuted)
    }
    day?.let { d ->
        BottomOverlay({ day = null }) {
            Text(friendlyDay(Periods.startOfDay(d.date, ui.zone), ui.nowMs, ui.zone), style = ChittaTheme.type.title, color = c.ink)
            Spacer(Modifier.height(Dimens.Gap2))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                d.items.forEach { item ->
                    val t = ui.entries.firstOrNull { it.id == item.entryId }
                    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.Gap2)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${glyph(item)} ${item.title.ifBlank { "Bill" }}", style = ChittaTheme.type.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                            AmountText(Paise(Math.abs(item.amount.value)))
                        }
                        Text(when { item.projected -> "Comes after the one before it is paid"; else -> item.status.name.lowercase().replaceFirstChar { it.uppercase() } }, style = ChittaTheme.type.label, color = c.inkMuted)
                        if (t != null && !item.projected) Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                            when (item.status) {
                                BillStatus.Upcoming, BillStatus.Overdue -> { ChittaButton("Pay", { vm.pay(t); day = null }, style = ButtonStyle.Quiet); ChittaButton("Skip", { vm.skip(t); day = null }, style = ButtonStyle.Quiet) }
                                BillStatus.Paid -> ChittaButton("Undo payment", { vm.undoPaid(t); day = null }, style = ButtonStyle.Quiet)
                                BillStatus.Skipped -> ChittaButton("Undo skip", { vm.undoSkip(t); day = null }, style = ButtonStyle.Quiet)
                            }
                        }
                    }
                    HairlineRule()
                }
            }
        }
    }
}

private fun glyph(i: CalendarItem) = when {
    i.projected -> "○"
    i.status == BillStatus.Overdue -> "▲"
    i.status == BillStatus.Upcoming -> "●"
    i.status == BillStatus.Paid -> "✓"
    else -> "–"
}
