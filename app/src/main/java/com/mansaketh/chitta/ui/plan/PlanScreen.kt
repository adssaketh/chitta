package com.mansaketh.chitta.ui.plan

import com.mansaketh.chitta.core.model.Repeat

import com.mansaketh.chitta.core.design.kit.SheetHeader
import androidx.compose.foundation.ExperimentalFoundationApi
import com.mansaketh.chitta.core.design.kit.MonthStepper
import com.mansaketh.chitta.ui.FeatureIcons
import com.mansaketh.chitta.core.design.kit.GaugeBar
import com.mansaketh.chitta.core.design.kit.EmptyState
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.TopBarMain
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ButtonPair
import com.mansaketh.chitta.core.design.kit.EntryRow
import com.mansaketh.chitta.ui.ledger.LedgerUi
import com.mansaketh.chitta.ui.ledger.toRow
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.mansaketh.chitta.core.design.kit.ChittaButton
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
import androidx.compose.ui.text.style.TextOverflow
import com.mansaketh.chitta.ui.ownsSideways
import java.time.YearMonth

val PLAN_TABS = listOf("Budgets", "Goals", "Loans", "Bills", "Calendar", "Sinking funds")

/**
 * One row in a Plan list (v8, PDF pages 24 and 26). With a gauge: the name and stamp, the gauge, then the figure with its small print on its own line
 * under it. Without one: the name and small print on the left and the amount on the right.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlanRow(
    title: String, amount: Paise?, subtitle: String? = null, gauge: Segments.Gauge? = null, gaugeLabel: String = "", stamp: String? = null,
    attention: Boolean = false, onClick: () -> Unit = {}, onLongClick: (() -> Unit)? = null,
    /** PDF p.24 and p.26: muted "of ₹60,000" beside the figure, a right-hand "₹33,480 left" (warning colour when [trailingAttention]), plain red "Behind" beside the title. */
    ofText: String? = null, trailing: String? = null, trailingAttention: Boolean = false, titleNote: String? = null,
) {
    val c = ChittaTheme.colors
    Column(Modifier.fillMaxWidth().heightIn(min = Dimens.RowTall).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick).padding(vertical = Dimens.RowPadV)) {
        if (gauge != null && amount != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = ChittaTheme.type.rowTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (titleNote != null) { Spacer(Modifier.width(Dimens.InlineGap)); Text(titleNote, style = ChittaTheme.type.rowMeta, color = c.warningText) }
                if (stamp != null) { Spacer(Modifier.width(Dimens.InlineGap)); Stamp(stamp, tone = if (attention) StampTone.Attention else StampTone.Neutral) }
            }
            Spacer(Modifier.height(Dimens.Gap2))
            GaugeBar(gauge, gaugeLabel)
            Spacer(Modifier.height(Dimens.Gap2))
            Row(verticalAlignment = Alignment.Bottom) {
                AmountText(amount, style = ChittaTheme.type.amountRow, fallbackStyles = listOf(ChittaTheme.type.amountSmall))
                if (ofText != null) Text(ofText, style = ChittaTheme.type.rowMeta, color = c.inkMuted, modifier = Modifier.padding(start = Dimens.InlineGap, bottom = Dimens.Space1))
                if (trailing != null) {
                    Spacer(Modifier.weight(1f))
                    Text(trailing, style = ChittaTheme.type.rowMeta, color = if (trailingAttention) c.warningText else c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Space1))
                }
            }
            if (subtitle != null) Text(subtitle, style = ChittaTheme.type.rowMeta, color = c.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = ChittaTheme.type.rowTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = ChittaTheme.type.rowMeta, color = c.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (stamp != null) { Spacer(Modifier.width(Dimens.InlineGap)); Stamp(stamp, tone = if (attention) StampTone.Attention else StampTone.Neutral) }
                if (amount != null) { Spacer(Modifier.width(Dimens.TitleAmountGap)); AmountText(amount, fallbackStyles = listOf(ChittaTheme.type.amountSmall)) }
            }
            if (gauge != null) { Spacer(Modifier.height(Dimens.Gap2)); GaugeBar(gauge, gaugeLabel) }
        }
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
        // PDF p.24 to p.29: the title alone. Reminders are reached from the link under the Bills list (p.28).
        TopBarMain("Plan", com.mansaketh.chitta.core.design.icons.ChittaIcons.Plan)
        TextTabs(PLAN_TABS, tab, onTab, Modifier.ownsSideways().horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin))
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = Dimens.FabInset)) {
        if (budgets.isEmpty()) item {
            EmptyState(FeatureIcons.tab(TabKey.Budgets), null, "A budget says how much you mean to spend on something, and shows if you are on pace.", "Add a budget", { onSheet("new-budget") })
        }
        items(budgets, key = { it.id }) { b ->
            val s = remember(b, ui.entries, ui.categories, ui.nowMs) { Budgets.summarize(b, Budgets.periodAt(b, ui.nowMs, ui.zone), ui.entries, ui.categories, ui.nowMs, ui.zone) }
            val every = when (b.repeat) { Repeat.DAILY -> " · daily"; Repeat.WEEKLY -> " · weekly"; Repeat.YEARLY -> " · yearly"; else -> "" }
            val over = s.spent.value > s.limit.value
            PlanRow(
                b.name + every, s.spent, null,
                Segments.gauge(s.spent, s.limit), "${b.name} budget",
                stamp = when (s.pace) { PaceStatus.OverLimit -> "Over limit"; PaceStatus.OverPace -> "Over pace"; else -> null }.takeIf { !b.income },
                attention = true, onClick = { onOpen("budget:${b.id}") }, onLongClick = { onSheet("edit-budget:${b.id}") },
                ofText = "of ${rupees(s.limit)}",
                trailing = if (b.income) "${if (over) "over by" else "to go"} ${rupees(Paise(Math.abs(s.limit.value - s.spent.value)))}" else if (over) "${rupees(Paise(s.spent.value - s.limit.value))} over" else "${rupees(s.left)} left",
                trailingAttention = over && !b.income,
            )
        }
    }
}

// ---------------------------------------------------------------- Goals
@Composable
private fun GoalsTab(ui: PlanUi, onOpen: (String) -> Unit, onSheet: (String) -> Unit) {
    val goals = ui.objectives.filter { Goals.isGoal(it) && !it.archived }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = Dimens.FabInset)) {
        if (goals.isEmpty()) item { EmptyState(FeatureIcons.tab(TabKey.Goals), null, "A goal is money you are setting aside for something, with a target and an optional date.", "Add a goal", { onSheet("new-goal") }) }
        items(goals, key = { it.id }) { o ->
            val p = remember(o, ui.entries, ui.nowMs) { Goals.progress(o, ui.entries, ui.nowMs, ui.zone) }
            // PDF p.26: "₹13,000 a month · done by Oct 2027", or "Needs ₹8,000 a month to reach it by Mar" when it is behind. The year is left out for this year.
            val by = o.endDateMs?.let { java.time.Instant.ofEpochMilli(it).atZone(ui.zone).toLocalDate() }?.let { d ->
                d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + if (d.year != java.time.Instant.ofEpochMilli(ui.nowMs).atZone(ui.zone).year) " ${d.year}" else ""
            }
            val sub = when {
                p.reached -> "Reached"
                p.perMonthNeeded != null && by != null -> if (p.pastEnd) "Needs ${rupees(p.perMonthNeeded!!)} a month to reach it by $by" else "${rupees(p.perMonthNeeded!!)} a month · done by $by"
                p.perMonthNeeded != null -> "${rupees(p.perMonthNeeded!!)} a month for ${p.monthsLeft} ${if (p.monthsLeft == 1) "month" else "months"}"
                else -> "${rupees(p.remaining)} to go"
            }
            PlanRow(o.name, p.saved, sub, p.gauge, "${o.name} goal", titleNote = if (p.pastEnd) "Behind" else null, onClick = { onOpen("goal:${o.id}") }, onLongClick = { onSheet("edit-goal:${o.id}") }, ofText = "of ${rupees(p.target)}")
        }
    }
}

// ---------------------------------------------------------------- Loans
/** A loan on the Loans tab (PDF page 27): an [EntryRow] with the loan glyph, and the repayment gauge under it where the loan has one. */
@Composable
private fun LoanLine(
    title: String, meta: String, amount: Paise, gauge: Segments.Gauge? = null, gaugeLabel: String = "", stamp: String? = null,
    onClick: () -> Unit = {}, onLongClick: (() -> Unit)? = null, tone: AmountTone = AmountTone.Neutral,
) {
    Column(Modifier.fillMaxWidth()) {
        EntryRow(
            title = title, meta = meta, glyph = FeatureIcons.tab(TabKey.Loans), glyphColor = ChittaTheme.colors.inkMuted, amount = amount, tone = tone,
            stamp = stamp, onClick = onClick, onLongClick = onLongClick, rule = gauge == null,
        )
        if (gauge != null) {
            GaugeBar(gauge, gaugeLabel, Modifier.padding(start = Dimens.GlyphCircle + Dimens.Space3))
            Spacer(Modifier.height(Dimens.Gap2))
            HairlineRule()
        }
    }
}

/** One of the two figures at the top of Loans (PDF p.27): ink rule, muted label, the figure (green when it is money coming to you), and who it is. */
@Composable
private fun LoanTotal(label: String, amount: Paise, people: Int, modifier: Modifier, income: Boolean) {
    val c = ChittaTheme.colors
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(Dimens.Hairline).background(c.ink))
        Spacer(Modifier.height(Dimens.Space3))
        Text(label, style = ChittaTheme.type.rowMeta14, color = c.inkMuted)
        AmountText(amount, style = ChittaTheme.type.amountTile, tone = if (income) AmountTone.Income else AmountTone.Neutral)
        Text("$people ${if (people == 1) "person" else "people"}", style = ChittaTheme.type.rowMeta, color = c.inkMuted)
    }
}

@Composable
private fun LoansTab(vm: PlanViewModel, ui: PlanUi, onOpen: (String) -> Unit, onSheet: (String) -> Unit) {
    val loans = ui.objectives.filter { Loans.isLoan(it) && !it.archived }
    val termsOf = ui.terms.associateBy { it.objectiveId }
    val oneTime = ui.entries.filter { Loans.isOneTime(it) }
    val open = oneTime.filter { it.paid }
    val done = oneTime.filter { !it.paid }.sortedByDescending { it.modifiedAtMs ?: it.dateMs }.take(5)
    var acting by remember { mutableStateOf<Transaction?>(null) }
    var partOf by remember { mutableStateOf<Transaction?>(null) }
    val sharedViews = com.mansaketh.chitta.ui.household.rememberSharedViews(vm.shared, ui.zone)
    var viewingShared by remember { mutableStateOf<com.mansaketh.chitta.ui.household.SharedLoanView?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = Dimens.FabInset)) {
        if (loans.isEmpty() && oneTime.isEmpty() && sharedViews.loans.isEmpty()) item { EmptyState(FeatureIcons.tab(TabKey.Loans), null, "Money you lent or borrowed, kept apart from your spending.", "Add a loan", { onSheet("new-loan") }) }
        // PDF p.27: what you are owed and what you owe, then one "Lent and borrowed" list (loans and one-off lends together).
        val owedLoans = loans.map { o -> val t = termsOf[o.id]; val outstanding = if (t != null) Instalments.progress(t, ui.entries.filter { it.objectiveLoanId == o.id }.sumOf { it.amount }, ui.zone).outstanding.value else Loans.progress(o, ui.entries).outstanding.value; o.income to outstanding }
        val owedOnce = open.map { (it.kind == TransactionKind.LENT) to Math.abs(it.amount) }
        val allOwed = owedLoans + owedOnce
        if (allOwed.isNotEmpty()) item {
            val toYou = allOwed.filter { it.first }; val you = allOwed.filter { !it.first }
            Row(Modifier.fillMaxWidth().padding(top = Dimens.HeadingGap), horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)) {
                LoanTotal("Owed to you", Paise(toYou.sumOf { it.second }), toYou.size, Modifier.weight(1f), income = true)
                LoanTotal("You owe", Paise(you.sumOf { it.second }), you.size, Modifier.weight(1f), income = false)
            }
        }
        if (loans.isNotEmpty() || open.isNotEmpty()) item { Section("Lent and borrowed") }
        items(loans, key = { it.id }) { o ->
            val t = termsOf[o.id]
            val linked = ui.entries.filter { it.objectiveLoanId == o.id }
            if (t != null) {
                val p = Instalments.progress(t, linked.sumOf { it.amount }, ui.zone)
                val next = Instalments.next(t, ui.zone)
                LoanLine(o.name, (if (o.income) "Lent" else "Borrowed") + (next?.let { " · next ${rupees(it.totalDue)} on ${dateText(it.dueMs, ui.zone)}" } ?: " · all instalments ticked"), p.outstanding, p.gauge, "${o.name} repaid", tone = if (o.income) AmountTone.Income else AmountTone.Neutral, onClick = { onOpen("loan:${o.id}") }, onLongClick = { onSheet("edit-loan:${o.id}") })
            } else {
                val p = Loans.progress(o, ui.entries)
                LoanLine(o.name, (if (o.income) "Lent" else "Borrowed") + " · repaid ${rupees(p.repaid)} of ${rupees(p.total)}", p.outstanding, p.gauge, "${o.name} repaid", tone = if (o.income) AmountTone.Income else AmountTone.Neutral, onClick = { onOpen("loan:${o.id}") }, onLongClick = { onSheet("edit-loan:${o.id}") })
            }
        }
        if (sharedViews.loans.isNotEmpty()) item { Section("Shared with you") }
        items(sharedViews.loans, key = { "sl-" + it.docId }) { v ->
            val f = v.figures(ui.zone)
            val meta = "Shared by ${v.owner} · " + (if (v.loan.lent) "Lent" else "Borrowed") + (f?.let { " · paid ${rupees(it.repaid)} of ${rupees(it.total)}" } ?: "")
            if (f != null) LoanLine(v.loan.name, meta, f.left, f.gauge, "${v.loan.name} repaid", onClick = { viewingShared = v })
            else PlanRow(v.loan.name, null, meta, onClick = { viewingShared = v })
        }
        if (sharedViews.loans.isNotEmpty()) item { Text("These are not in your Net worth or loan totals. Tap one to open it.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted) }
        items(open, key = { it.id }) { t ->
            val lent = t.kind == TransactionKind.LENT
            LoanLine(t.name.ifBlank { if (lent) "Lent" else "Borrowed" }, (if (lent) "Lent " else "Borrowed ") + friendlyDay(t.dateMs, ui.nowMs, ui.zone), Paise(Math.abs(t.amount)), tone = if (lent) AmountTone.Income else AmountTone.Neutral, onClick = { acting = t })
        }
        if (done.isNotEmpty()) item { Section("Collected recently") }
        items(done, key = { it.id }) { t ->
            LoanLine(t.name.ifBlank { "Entry" }, "Collected", Paise(Math.abs(t.amount)), stamp = "Collected", onClick = { acting = t })
        }
    }
    viewingShared?.let { v ->
        // Looked up again each time, so an edit shows in the sheet as soon as it is saved.
        val live = sharedViews.loans.firstOrNull { it.docId == v.docId } ?: v
        com.mansaketh.chitta.ui.household.SharedLoanSheet(live, vm.shared, ui.zone) { viewingShared = null }
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

    val ledgerUi = remember(ui.categories, ui.accounts) { LedgerUi(categories = ui.categories, accounts = ui.accounts) }
    val tint = ChittaTheme.colors

    // A bill is an entry that is still to come, so it reads like one (PDF page 28): glyph, name, when it is due, the amount.
    @Composable fun Bill(t: Transaction) {
        val st = Bills.status(t, ui.nowMs, ui.zone)
        val repeating = Bills.isRepeating(t)
        val row = t.toRow(ledgerUi, tint.inkMuted) { id -> tint.categoryTint(id) }
        EntryRow(
            title = t.name.ifBlank { categoryName(t.categoryId, ui.categories) },
            meta = (if (Bills.needsAmount(t)) "Amount not set · " else "") + friendlyDay(Bills.dueMs(t), ui.nowMs, ui.zone) + (if (repeating) " · repeats ${repeatLabel(t.repeat!!, t.periodLength ?: 1)}" else if (t.kind == TransactionKind.SUBSCRIPTION) " · subscription" else "") + (if (t.income) " · to receive" else ""),
            glyph = row.glyph, glyphColor = row.categoryColor, amount = row.amount, tone = row.tone,
            stamp = if (st == BillStatus.Overdue) "Overdue" else null,
            stampTone = if (st == BillStatus.Overdue) StampTone.Attention else StampTone.Neutral,
            onClick = { acting = t },
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = Dimens.FabInset)) {
        if (scheduled.isEmpty()) item { EmptyState(FeatureIcons.tab(TabKey.Bills), null, "Bills and subscriptions you know are coming, so nothing surprises you.", "Add a bill", { onSheet("new-bill") }) }

        if (suggestions.isNotEmpty()) item {
            val names = suggestions.take(2).joinToString(", ") { it.name } + if (suggestions.size > 2) " and ${suggestions.size - 2} more" else ""
            PlanRow(
                if (suggestions.size == 1) "1 possible subscription" else "${suggestions.size} possible subscriptions",
                null, "$names. Found in your ledger. Nothing is added until you say so.", onClick = { suggestionsOpen = true },
            )
        }
        if (overdue.isNotEmpty()) item { Section("Overdue") }
        items(overdue, key = { it.id }) { Bill(it) }
        if (future.isNotEmpty()) item { Section("Coming up", first = overdue.isEmpty() && suggestions.isEmpty(), note = "${rupees(totals.total)} due", onNote = { showTotals = true }) }
        items(shown, key = { it.id }) { Bill(it) }
        if (future.size >= 3 && !expanded) item { ChittaButton("Show ${future.size - 2} more", { expanded = true }, kind = ButtonKind.Link) }
        // One "Add a bill" at a time (your request, 16 Nov): with no bills it is the empty state's button, so this block starts with the link;
        // with bills it is the one here. "Reminders and auto-pay" sits directly under whichever is showing.
        item {
            Column(Modifier.padding(top = if (scheduled.isEmpty()) Dimens.Gap2 else Dimens.SectionGap), verticalArrangement = Arrangement.spacedBy(Dimens.InlineGap)) {
                if (scheduled.isNotEmpty()) ChittaButton("Add a bill", { onSheet("new-bill") }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ChittaButton("Reminders", onSettings, kind = ButtonKind.Link) }
                if (dismissed.isNotEmpty()) ChittaButton("Not subscriptions (${dismissed.size})", { dismissedOpen = true }, kind = ButtonKind.Link)
            }
        }
    }

    LaunchedEffect(suggestions.isEmpty()) { if (suggestions.isEmpty()) suggestionsOpen = false }
    if (suggestionsOpen && suggestions.isNotEmpty()) BottomOverlay({ suggestionsOpen = false }, handle = false) {
        SheetHeader("Possible subscriptions")
        Text("Charges that came back on a steady rhythm. Tap one to add it or set it aside.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        LazyColumn(Modifier.heightIn(max = sheetMax(0.5f))) {
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

// ---------------------------------------------------------------- Bill calendar (PDF p.29)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarTab(vm: PlanViewModel, ui: PlanUi) {
    var epoch by rememberSaveable { mutableStateOf(YearMonth.from(java.time.Instant.ofEpochMilli(ui.nowMs).atZone(ui.zone)).let { it.year * 12 + it.monthValue - 1 }) }
    val month = YearMonth.of(epoch / 12, epoch % 12 + 1)
    val days = remember(ui.entries, month, ui.nowMs) { BillCalendar.month(ui.entries, month, ui.nowMs, ui.zone) }
    var day by remember { mutableStateOf<CalendarDay?>(null) }
    val c = ChittaTheme.colors
    val toPay = Paise(days.sumOf { it.toPay.value })
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin)) {
        MonthStepper("${month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)} ${month.year}", { epoch -= 1 }, { epoch += 1 })
        Spacer(Modifier.height(Dimens.Gap3))
        Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), style = ChittaTheme.type.mono, color = c.inkMuted, textAlign = TextAlign.Center) } }
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells: List<CalendarDay?> = List(lead) { null } + days
        val today = remember(ui.nowMs, ui.zone) { Periods.date(ui.nowMs, ui.zone) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    // Equal-height cells (PDF page 29). Today has a ring, the chosen day an ink fill; bills are small drawn marks under the number.
                    Box(
                        Modifier.weight(1f).height(Dimens.CalendarCell).then(if (d != null && d.items.isNotEmpty()) Modifier.pressClickable(role = Role.Button, onClickLabel = "Show bills") { day = d } else Modifier),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (d != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val chosen = day?.date == d.date
                            Box(
                                Modifier.size(Dimens.CalendarDay).clip(CircleShape).background(if (chosen) c.ink else Color.Transparent)
                                    .then(if (d.date == today && !chosen) Modifier.border(Dimens.ChipBorderSelected, c.ink, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { Text(d.date.dayOfMonth.toString(), style = ChittaTheme.type.body, color = if (chosen) c.background else c.ink) }
                            if (d.items.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
                                d.items.take(3).forEach { StatusMark(markOf(it.status, it.projected), if (it.status == BillStatus.Overdue && !it.projected) c.warningText else c.ink) }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(Dimens.Gap3))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap3), verticalArrangement = Arrangement.spacedBy(Dimens.Gap1)) {
            listOf<Pair<Mark?, String>>(Mark.Due to "Due", Mark.Overdue to "Overdue", Mark.Paid to "Paid", Mark.Skipped to "Skipped", null to "Today").forEach { (m, w) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
                    if (m != null) StatusMark(m, c.inkMuted) else Box(Modifier.size(Dimens.IconSmall).border(Dimens.Hairline, c.inkMuted, CircleShape))
                    Text(w, style = ChittaTheme.type.label, color = c.inkMuted)
                }
            }
        }
    }
    day?.let { d ->
        BottomOverlay({ day = null }, handle = false) {
            SheetHeader(friendlyDay(Periods.startOfDay(d.date, ui.zone), ui.nowMs, ui.zone))
            Spacer(Modifier.height(Dimens.Gap2))
            Column(Modifier.heightIn(max = sheetMax(0.5f)).verticalScroll(rememberScrollState())) {
                d.items.forEach { item ->
                    val t = ui.entries.firstOrNull { it.id == item.entryId }
                    Column(Modifier.fillMaxWidth().padding(vertical = Dimens.Gap2)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusMark(markOf(item.status, item.projected), c.ink)
                            Spacer(Modifier.width(Dimens.Gap2))
                            Text(item.title.ifBlank { "Bill" }, style = ChittaTheme.type.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                            AmountText(Paise(Math.abs(item.amount.value)))
                        }
                        Text(when { item.projected -> "Comes after the one before it is paid"; else -> item.status.name.lowercase().replaceFirstChar { it.uppercase() } }, style = ChittaTheme.type.label, color = c.inkMuted)
                        if (t != null && !item.projected) {
                            Spacer(Modifier.height(Dimens.Gap2))
                            when (item.status) {
                                BillStatus.Upcoming, BillStatus.Overdue -> ButtonPair(
                                    first = { m -> ChittaButton("Pay", { vm.pay(t); day = null }, m, confirmHaptic = true) },
                                    second = { m -> ChittaButton("Skip", { vm.skip(t); day = null }, m, kind = ButtonKind.Secondary) },
                                )
                                BillStatus.Paid -> ChittaButton("Undo payment", { vm.undoPaid(t); day = null }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                                BillStatus.Skipped -> ChittaButton("Undo skip", { vm.undoSkip(t); day = null }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    HairlineRule()
                }
            }
        }
    }
}
