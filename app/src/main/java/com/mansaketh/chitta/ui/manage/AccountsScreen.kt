package com.mansaketh.chitta.ui.manage

import com.mansaketh.chitta.core.design.kit.SheetHeader
import androidx.compose.foundation.clickable
import com.mansaketh.chitta.core.design.kit.SwitchRow
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.ui.FeatureIcons
import com.mansaketh.chitta.core.domain.TabKey
import com.mansaketh.chitta.core.design.kit.EmptyState
import com.mansaketh.chitta.core.design.kit.Section
import com.mansaketh.chitta.core.design.kit.ValueRow
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.ButtonPair
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import com.mansaketh.chitta.core.design.kit.EntryRow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.ui.household.sharedDepositSection
import com.mansaketh.chitta.core.domain.CardDues
import com.mansaketh.chitta.core.domain.FixedDeposits
import com.mansaketh.chitta.core.domain.AmountExpression
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.RefreshResult
import com.mansaketh.chitta.ui.*
import com.mansaketh.chitta.ui.plan.dateText

private sealed interface AccSheet {
    data object None : AccSheet
    data object Add : AccSheet
    data class Actions(val a: Account) : AccSheet
    data class Rename(val a: Account) : AccSheet
    data class Correct(val a: Account) : AccSheet
    data object Rates : AccSheet
    data object NetWorthInfo : AccSheet
    data class Card(val a: Account) : AccSheet
    data class Deposit(val d: FixedDeposit) : AccSheet
    data class DepositEdit(val d: FixedDeposit?) : AccSheet
    data class DepositShare(val d: FixedDeposit) : AccSheet
    data class SharedDeposit(val v: com.mansaketh.chitta.ui.household.SharedDepositView) : AccSheet
    data class CardOwed(val card: String) : AccSheet
}

/** Accounts (PDF p.25): total balance, balances per account, add / rename / archive, transfer, correct balance, exchange rates. */
@Composable
fun AccountsScreen(vm: ManageViewModel, onBack: () -> Unit, onTransfer: () -> Unit, onOpenFunds: () -> Unit = {}, onOpenAssets: () -> Unit = {}) {
    val ui by vm.ui.collectAsState()
    val sharedViews = com.mansaketh.chitta.ui.household.rememberSharedViews(vm.shared, ui.zone)
    val c = ChittaTheme.colors
    var sheet by remember { mutableStateOf<AccSheet>(AccSheet.None) }
    var showArchived by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val live = ui.accounts.filter { !it.archived }.sortedBy { it.sortOrder }
    val archived = ui.accounts.filter { it.archived }
    // Different currencies are not added together: the total is shown only for rupee accounts, with a note when others exist.
    val rupee = live.filter { (it.currency ?: "INR").equals("INR", ignoreCase = true) }
    val total = rupee.fold(Paise.ZERO) { acc, a -> acc + (ui.balances[a.id] ?: Paise.ZERO) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val asOf = remember(ui.nowMs, ui.zone) { java.time.Instant.ofEpochMilli(ui.nowMs).atZone(ui.zone).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)).lowercase() }
            TopBarDetail("Accounts", subtitle = "As of $asOf", onBack = onBack, actions = { IconAction(ChittaIcons.Plus, "Add an account", onClick = { sheet = AccSheet.Add }) })
            LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = Dimens.FabInset)) {
                // PDF p.32: Net worth leads, with an info mark that says what it counts.
                item {
                    Row(Modifier.padding(top = Dimens.Gap4), verticalAlignment = Alignment.CenterVertically) {
                        Text("Net worth", style = ChittaTheme.type.label, color = c.inkMuted)
                        androidx.compose.material3.Icon(ChittaIcons.Info, contentDescription = "What Net worth counts", tint = c.inkMuted, modifier = Modifier.padding(start = Dimens.Gap2).size(Dimens.IconSmall).pressClickable(role = androidx.compose.ui.semantics.Role.Button) { sheet = AccSheet.NetWorthInfo })
                    }
                    AmountText(ui.net?.total ?: total, style = ChittaTheme.type.amountLarge, roundToRupee = true)
                    if (live.size != rupee.size) FootNote("Accounts in other currencies are listed below and not added in.")
                    notice?.let { FootNote(it) }
                }
                item { Section("Accounts", note = IndianFormat.rupees(total)) }
                if (ui.loading) item { LoadingPlaceholder() }
                if (!ui.loading && live.isEmpty()) item { EmptyState(FeatureIcons.tab(TabKey.Accounts), null, "No accounts yet. Add one to start.", "Add an account", { sheet = AccSheet.Add }) }
                items(live.filter { ui.cards[it.id] == null }, key = { it.id }) { a -> AccountRow(a, ui.balances[a.id] ?: Paise.ZERO, ui, null) { sheet = AccSheet.Actions(a) } }
                // Cards (PDF p.32): a bordered panel per card with the statement, what is outstanding and the limit used.
                if (ui.cards.isNotEmpty()) {
                    item { Section("Cards") }
                    items(live.filter { ui.cards[it.id] != null }, key = { "cc-" + it.id }) { a -> CardPanel(a, ui.cards[a.id]!!) { sheet = AccSheet.Actions(a) } }
                }
                if (archived.isNotEmpty()) item {
                    Section("Archived", note = if (showArchived) "Hide" else "Show", onNote = { showArchived = !showArchived })
                }
                if (showArchived) items(archived, key = { "x-" + it.id }) { a -> AccountRow(a, ui.balances[a.id] ?: Paise.ZERO, ui, null) { sheet = AccSheet.Actions(a) } }
                // Fixed deposits (Phase 8b). Worth shown is today's value; Net worth counts them only when switched on.
                item { Section("Fixed deposits", note = "Add", onNote = { sheet = AccSheet.DepositEdit(null) }) }
                if (ui.deposits.isEmpty() && !ui.loading) item {
                    FootNote("No fixed deposits. Add one, or a bank message can add it.")
                }
                items(ui.deposits.sortedWith(compareBy({ it.closed }, { it.maturityMs })), key = { "fd-" + it.id }) { d ->
                    DepositRow(d, ui.nowMs, ui.zone) { sheet = AccSheet.Deposit(d) }
                }
                sharedDepositSection(sharedViews.deposits, ui.nowMs, ui.zone) { sheet = AccSheet.SharedDeposit(it) }
                // Investments: mutual funds (Phase 8c) and gold, EPF and PPF typed in by hand (Phase 8i) each have their own screen.
                item {
                    val t = com.mansaketh.chitta.core.domain.MutualFunds.totals(ui.funds)
                    val dep = ui.deposits.filter { !it.closed }.fold(Paise.ZERO) { acc, d -> acc + FixedDeposits.currentValue(d, ui.nowMs, ui.zone) }
                    Section("Investments", note = IndianFormat.rupees(t.value + dep))
                }
                item {
                    val t = com.mansaketh.chitta.core.domain.MutualFunds.totals(ui.funds)
                    ValueRow("Mutual funds", if (ui.funds.isEmpty()) "None yet" else if (t.priced > 0) maskedRupees(t.value) else "${t.count} ${if (t.count == 1) "fund" else "funds"}", onClick = onOpenFunds)
                }
                item { NavRow("Gold, EPF and PPF", onOpenAssets) }
                // The two Net worth switches share one headed group.
                item { Section("Net worth includes") }
                if (ui.deposits.isNotEmpty()) {
                    val open = ui.deposits.count { !it.closed }
                    item {
                        SwitchRow("Count deposits in Net worth", ui.nwDeposits, { vm.setNetWorthDeposits(it) })
                        FootNote(if (ui.nwDeposits) "$open open at today's value, as of ${dateText(ui.nowMs, ui.zone)}." else "Off, so Net worth leaves them out.")
                    }
                }
                item { SwitchRow("Count instalment loans in Net worth", ui.nwInstalments, { vm.setNetWorthInstalments(it) }) }
                // Cards read from bank messages (Phase 8b).
                val owed = CardDues.owedByCard(ui.dues)
                if (owed.isNotEmpty()) {
                    item { Section("Cards from bank messages") }
                    items(owed.entries.toList(), key = { "cd-" + it.key }) { (card, v) ->
                        val matched = live.any { CardDues.forAccount(it, ui.dues.filter { d -> d.card == card }).isNotEmpty() }
                        ValueRow(card, (if (v < 0) "Credit " else "Owes ") + maskedRupees(Paise(kotlin.math.abs(v))), onClick = { sheet = AccSheet.CardOwed(card) })
                        if (matched) FootNote("Counted in that account's card details.")
                    }
                    item { FootNote("Spend, payment and refund messages change what is owed. Payment messages are not saved as entries.") }
                }
                item { Section("Money moves") }
                item {
                    ButtonPair(
                        first = { m -> ChittaButton("Move money", onTransfer, m, kind = ButtonKind.Secondary, enabled = live.size >= 2) },
                        second = { m -> ChittaButton("Exchange rates", { sheet = AccSheet.Rates }, m, kind = ButtonKind.Secondary) },
                        modifier = Modifier.padding(vertical = Dimens.Gap3),
                    )
                }
            }
        }

        when (val s = sheet) {
            AccSheet.Add -> TextSheet("New account", "Name", "", { vm.addAccount(it); sheet = AccSheet.None }, { sheet = AccSheet.None })
            is AccSheet.Rename -> TextSheet("Rename", "Name", s.a.name, { vm.renameAccount(s.a, it); sheet = AccSheet.None }, { sheet = AccSheet.None })
            is AccSheet.Correct -> {
                var problem by remember { mutableStateOf<String?>(null) }
                TextSheet("Correct balance", problem ?: "What the balance should be", "", { vm.correctBalance(s.a, it) { p -> problem = p }; if (problem == null) sheet = AccSheet.None }, { sheet = AccSheet.None }, numeric = true, saveLabel = "Correct")
            }
            is AccSheet.Actions -> ChoiceSheet(
                s.a.name,
                buildList {
                    add(Choice("Rename", "rename")); add(Choice("Correct balance", "correct", "Adds an entry that is not spending or income"))
                    add(Choice("Credit card details", "card", "Limit, statement and due days, minimum due"))
                    add(Choice(if (s.a.archived) "Bring back" else "Archive", "archive", if (s.a.archived) null else "Hidden from pickers. Entries stay."))
                },
                { v ->
                    when (v) {
                        "rename" -> sheet = AccSheet.Rename(s.a)
                        "correct" -> sheet = AccSheet.Correct(s.a)
                        "card" -> sheet = AccSheet.Card(s.a)
                        else -> { vm.archiveAccount(s.a, !s.a.archived); sheet = AccSheet.None }
                    }
                },
                { sheet = AccSheet.None },
            )
            is AccSheet.Card -> CardSheet(s.a, ui.cards[s.a.id], { l, sd, dd, m, r -> vm.saveCard(s.a, l, sd, dd, m, r); sheet = AccSheet.None }, { sheet = AccSheet.None })
            is AccSheet.Deposit -> {
                val cur = ui.deposits.firstOrNull { it.id == s.d.id } ?: s.d
                DepositDetailSheet(
                    cur, ui.nowMs, ui.zone, onEdit = { sheet = AccSheet.DepositEdit(cur) },
                    onToggleClosed = { vm.saveDeposit(cur.copy(closed = !cur.closed)); sheet = AccSheet.None },
                    onDelete = { vm.deleteDeposit(cur.id); sheet = AccSheet.None }, onClose = { sheet = AccSheet.None },
                    sharing = com.mansaketh.chitta.ui.household.sharingLine(vm.shared, com.mansaketh.chitta.core.domain.SharedItems.KIND_FD, cur.id), onShare = { sheet = AccSheet.DepositShare(cur) },
                    editedBy = com.mansaketh.chitta.ui.household.editedByLine(vm.shared, com.mansaketh.chitta.core.domain.SharedItems.KIND_FD, cur.id),
                )
            }
            is AccSheet.DepositShare -> com.mansaketh.chitta.ui.household.ShareSheet(vm.shared, com.mansaketh.chitta.core.domain.SharedItems.KIND_FD, s.d.id, s.d.bank.ifBlank { "this deposit" }) { sheet = AccSheet.Deposit(s.d) }
            is AccSheet.SharedDeposit -> {
                // Looked up again each time, so an edit shows in the sheet as soon as it is saved.
                val live = sharedViews.deposits.firstOrNull { it.docId == s.v.docId } ?: s.v
                com.mansaketh.chitta.ui.household.SharedDepositSheet(live, vm.shared, ui.nowMs, ui.zone) { sheet = AccSheet.None }
            }
            is AccSheet.DepositEdit -> DepositEditSheet(s.d, ui.nowMs, ui.zone, { vm.saveDeposit(it); sheet = AccSheet.None }, { sheet = AccSheet.None })
            is AccSheet.CardOwed -> {
                var problem by remember { mutableStateOf<String?>(null) }
                val now = CardDues.owedByCard(ui.dues)[s.card] ?: 0L
                TextSheet(
                    s.card, problem ?: "What is owed now, from your statement. Messages so far make ${IndianFormat.rupees(Paise(now))}.", "", { text ->
                        val v = AmountExpression.evaluate(text)?.value
                        if (v == null) problem = "Enter what is owed as a number." else { vm.setCardOwed(s.card, v); sheet = AccSheet.None }
                    }, { sheet = AccSheet.None }, numeric = true, saveLabel = "Set",
                )
            }
            AccSheet.NetWorthInfo -> ConfirmSheet("What Net worth counts", listOf(
                "Your rupee accounts, plus what people owe you, minus what you owe.",
                "Mutual funds, gold, EPF and PPF are added when their switches below are on. Fixed deposits and instalment loans follow their own switches.",
                "Cards count through the accounts they are set up on.",
            ), "Close", { sheet = AccSheet.None }, { sheet = AccSheet.None }, attention = false)
            AccSheet.Rates -> RatesSheet(vm, ui, { notice = it }, { sheet = AccSheet.None })
            AccSheet.None -> Unit
        }
    }
}

/** Account row (PDF p.32): a grey glyph circle, the name, "For spending · today" or "Entered 2 Oct", an optional STALE stamp and the balance. */
@Composable
private fun AccountRow(a: Account, balance: Paise, ui: ManageUi, card: com.mansaketh.chitta.core.domain.CardFigures?, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val last = ui.lastEntryMs[a.id]
    val day = last?.let { friendlyDay(it, ui.nowMs, ui.zone).replaceFirstChar { ch -> ch.lowercase() } }
    val forSpending = a.id in ui.spending
    val stale = !forSpending && last != null && ui.nowMs - last > 35L * 86_400_000L
    val foreign = !(a.currency ?: "INR").equals("INR", ignoreCase = true)
    val meta = when {
        foreign -> (a.currency ?: "").uppercase()
        card != null -> "Owes ${IndianFormat.rupees(card.outstanding)} · due in ${card.daysToDue} ${if (card.daysToDue == 1L) "day" else "days"}"
        forSpending -> "For spending" + (day?.let { " · $it" } ?: "")
        day != null -> "Entered $day"
        else -> "No entries yet"
    }
    EntryRow(
        title = a.name, meta = meta,
        glyph = if (a.name.contains("cash", ignoreCase = true)) ChittaIcons.Tag else ChittaIcons.Bank, glyphColor = c.inkMuted,
        amount = balance, tone = if (balance.value < 0) AmountTone.Expense else AmountTone.Neutral,
        stamp = if (stale) "Stale" else null, onClick = onClick,
    )
}

/** Card panel (PDF p.32): glyph and name with "Due 18 Nov", Statement and Outstanding side by side, "incl. unbilled", the limit gauge and its caption. */
@Composable
private fun CardPanel(a: Account, f: com.mansaketh.chitta.core.domain.CardFigures, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    val shape = com.mansaketh.chitta.core.design.tokens.Shapes.Control
    Column(
        Modifier.fillMaxWidth().padding(vertical = Dimens.Gap2).clip(shape).background(c.surface).border(Dimens.Hairline, c.rule, shape)
            .pressClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(Dimens.Gap4),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.mansaketh.chitta.core.design.kit.Glyph(ChittaIcons.CreditCard, c.sindoor)
            Text(a.name, style = ChittaTheme.type.title, color = c.ink, modifier = Modifier.weight(1f).padding(start = Dimens.Gap3))
            Text("Due ${f.nextDue.dayOfMonth} ${f.nextDue.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)}", style = ChittaTheme.type.label, color = c.inkMuted)
        }
        Spacer(Modifier.height(Dimens.Gap3))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Statement", style = ChittaTheme.type.label, color = c.inkMuted)
                AmountText(f.statementBalance, style = ChittaTheme.type.amountTile)
            }
            Column(Modifier.weight(1f)) {
                Text("Outstanding", style = ChittaTheme.type.label, color = c.inkMuted)
                AmountText(f.outstanding, style = ChittaTheme.type.amountTile)
                if (f.unbilled.value > 0) Text("incl. ${IndianFormat.rupees(f.unbilled)} unbilled", style = ChittaTheme.type.label, color = c.inkMuted)
            }
        }
        val limit = a.creditLimit
        if (limit != null && limit > 0) {
            Spacer(Modifier.height(Dimens.Gap3))
            com.mansaketh.chitta.core.design.kit.GaugeBar(com.mansaketh.chitta.core.money.Segments.gauge(f.outstanding, Paise(limit), 20), "${a.name} limit used")
            Text("${((f.utilisationBp ?: 0) / 100)}% of ${IndianFormat.rupees(Paise(limit))} limit used", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap1))
        }
    }
}

@Composable
private fun DepositRow(d: FixedDeposit, nowMs: Long, zone: java.time.ZoneId, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).pressClickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.bank + (if (d.fdNumber.isNotEmpty()) " \u00B7 ${d.fdNumber.takeLast(4)}" else ""), style = ChittaTheme.type.title, color = if (d.closed) c.inkMuted else c.ink)
            Text(depositLine(d, nowMs, zone), style = ChittaTheme.type.label, color = c.inkMuted)
            if (d.fromSms && !d.closed) Text("From a bank message. Check the details.", style = ChittaTheme.type.label, color = c.inkMuted)
        }
        AmountText(FixedDeposits.currentValue(d, nowMs, zone), alwaysShowPaise = true, color = if (d.closed) c.inkMuted else c.ink)
    }
    HairlineRule()
}

@Composable
private fun RatesSheet(vm: ManageViewModel, ui: ManageUi, say: (String) -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    var editing by remember { mutableStateOf<String?>(null) }
    var bad by remember { mutableStateOf(false) }
    val wanted = (ui.accounts.mapNotNull { it.currency } + listOf("USD", "EUR", "GBP", "AED", "SGD")).filter { it != "INR" }.distinct()
    BottomOverlay(onClose, handle = false) {
        SheetHeader("Exchange rates")
        val asOf = ui.rates.firstOrNull()?.asOfDate
        Text(if (asOf == null) "No rates yet. Refresh needs internet." else "Daily rates as of $asOf. A rate you type wins over the daily one.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        wanted.forEach { code ->
            val override = ui.overrides.firstOrNull { it.fromCurrency == code && it.toCurrency == "INR" }
            val daily = ui.rates.firstOrNull { it.currency == code }?.perBase?.toBigDecimalOrNull()?.let { if (it.signum() == 0) null else java.math.BigDecimal.ONE.divide(it, 4, java.math.RoundingMode.HALF_EVEN).toPlainString() }
            ValueRow("1 $code", (override?.rate?.let { "₹$it (yours)" } ?: daily?.let { "₹$it" } ?: "no rate"), onClick = { editing = code })
        }
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Refresh rates", { vm.refreshRates { r -> say(if (r is RefreshResult.Updated) "Rates updated (${r.asOf})." else (r as RefreshResult.Failed).reason + " Old rates kept.") }; onClose() }, modifier = Modifier.fillMaxWidth())
    }
    editing?.let { code ->
        val has = ui.overrides.any { it.fromCurrency == code && it.toCurrency == "INR" }
        TextSheet("1 $code in rupees", if (bad) "That is not a number" else "Rate (leave empty to use the daily rate)", ui.overrides.firstOrNull { it.fromCurrency == code && it.toCurrency == "INR" }?.rate ?: "",
            { t -> if (t.isBlank()) { if (has) vm.clearOverride(code, "INR"); editing = null } else if (vm.setOverride(code, "INR", t)) editing = null else bad = true },
            { editing = null }, numeric = true)
    }
}
