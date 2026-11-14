package com.mansaketh.chitta.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
    data class Card(val a: Account) : AccSheet
    data class Deposit(val d: FixedDeposit) : AccSheet
    data class DepositEdit(val d: FixedDeposit?) : AccSheet
    data class CardOwed(val card: String) : AccSheet
}

/** Accounts (PDF p.25): total balance, balances per account, add / rename / archive, transfer, correct balance, exchange rates. */
@Composable
fun AccountsScreen(vm: ManageViewModel, onBack: () -> Unit, onTransfer: () -> Unit, onOpenFunds: () -> Unit = {}, onOpenAssets: () -> Unit = {}) {
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    var sheet by remember { mutableStateOf<AccSheet>(AccSheet.None) }
    var showArchived by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val live = ui.accounts.filter { !it.archived }.sortedBy { it.sortOrder }
    val archived = ui.accounts.filter { it.archived }
    // Different currencies are not added together: the total is shown only for rupee accounts, with a note when others exist.
    val rupee = live.filter { (it.currency ?: "INR") == "INR" }
    val total = rupee.fold(Paise.ZERO) { acc, a -> acc + (ui.balances[a.id] ?: Paise.ZERO) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChittaTopBar("Accounts", onBack = onBack, actions = { IconAction(ChittaIcons.Plus, "Add an account", onClick = { sheet = AccSheet.Add }) })
            LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
                item {
                    Text("Total balance", style = ChittaTheme.type.label, color = c.inkMuted)
                    AmountText(total, style = ChittaTheme.type.amountHero, alwaysShowPaise = true)
                    if (live.size != rupee.size) Text("Accounts in other currencies are listed below and not added in.", style = ChittaTheme.type.label, color = c.inkMuted)
                    notice?.let { Text(it, style = ChittaTheme.type.label, color = c.inkMuted) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), modifier = Modifier.padding(vertical = Dimens.Gap3)) {
                        ChittaButton("Move money", onTransfer, style = ButtonStyle.Quiet, enabled = live.size >= 2)
                        ChittaButton("Exchange rates", { sheet = AccSheet.Rates }, style = ButtonStyle.Quiet)
                    }
                    HairlineRule()
                }
                if (ui.loading) item { LoadingPlaceholder() }
                if (!ui.loading && live.isEmpty()) item { EmptyState("No accounts yet. Add one to start.", "Add an account", { sheet = AccSheet.Add }) }
                items(live, key = { it.id }) { a -> AccountRow(a, ui.balances[a.id] ?: Paise.ZERO, ui.cards[a.id]) { sheet = AccSheet.Actions(a) } }
                if (archived.isNotEmpty()) item {
                    SectionHeader("Archived", action = if (showArchived) "Hide" else "Show", onAction = { showArchived = !showArchived })
                }
                if (showArchived) items(archived, key = { "x-" + it.id }) { a -> AccountRow(a, ui.balances[a.id] ?: Paise.ZERO, null) { sheet = AccSheet.Actions(a) } }
                item { Spacer(Modifier.height(Dimens.SectionGap)) }
                // Fixed deposits (Phase 8b). Worth shown is today's value; Net worth counts them only when switched on.
                item { SectionHeader("Fixed deposits", action = "Add", onAction = { sheet = AccSheet.DepositEdit(null) }) }
                if (ui.deposits.isEmpty() && !ui.loading) item {
                    Text("No fixed deposits. Add one, or a bank message can add it.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2))
                }
                items(ui.deposits.sortedWith(compareBy({ it.closed }, { it.maturityMs })), key = { "fd-" + it.id }) { d ->
                    DepositRow(d, ui.nowMs, ui.zone) { sheet = AccSheet.Deposit(d) }
                }
                if (ui.deposits.isNotEmpty()) {
                    val open = ui.deposits.count { !it.closed }
                    item {
                        ValueRow("Count deposits in Net worth", if (ui.nwDeposits) "On" else "Off", onClick = { vm.setNetWorthDeposits(!ui.nwDeposits) })
                        Text(
                            if (ui.nwDeposits) "$open open at today's value, as of ${dateText(ui.nowMs, ui.zone)}." else "Off, so Net worth leaves them out.",
                            style = ChittaTheme.type.label, color = c.inkMuted,
                        )
                    }
                }
                // Mutual funds (Phase 8c) have their own screen.
                item {
                    val t = com.mansaketh.chitta.core.domain.MutualFunds.totals(ui.funds)
                    ValueRow("Mutual funds", if (ui.funds.isEmpty()) "None yet" else if (t.priced > 0) maskedRupees(t.value) else "${t.count} ${if (t.count == 1) "fund" else "funds"}", onClick = onOpenFunds)
                }
                // Gold, EPF and PPF typed in by hand (Phase 8i) have their own screen.
                item { ValueRow("Gold, EPF and PPF", "", onClick = onOpenAssets) }
                item { ValueRow("Count instalment loans in Net worth", if (ui.nwInstalments) "On" else "Off", onClick = { vm.setNetWorthInstalments(!ui.nwInstalments) }) }
                // Cards read from bank messages (Phase 8b).
                val owed = CardDues.owedByCard(ui.dues)
                if (owed.isNotEmpty()) {
                    item { Spacer(Modifier.height(Dimens.SectionGap)); SectionHeader("Cards from bank messages") }
                    items(owed.entries.toList(), key = { "cd-" + it.key }) { (card, v) ->
                        val matched = live.any { CardDues.forAccount(it, ui.dues.filter { d -> d.card == card }).isNotEmpty() }
                        ValueRow(card, (if (v < 0) "Credit " else "Owes ") + maskedRupees(Paise(kotlin.math.abs(v))), onClick = { sheet = AccSheet.CardOwed(card) })
                        if (matched) Text("Counted in that account's card details.", style = ChittaTheme.type.label, color = c.inkMuted)
                    }
                    item { Text("Spend, payment and refund messages change what is owed. Payment messages are not saved as entries.", style = ChittaTheme.type.label, color = c.inkMuted) }
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
                )
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
            AccSheet.Rates -> RatesSheet(vm, ui, { notice = it }, { sheet = AccSheet.None })
            AccSheet.None -> Unit
        }
    }
}

@Composable
private fun AccountRow(a: Account, balance: Paise, card: com.mansaketh.chitta.core.domain.CardFigures?, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(onClick = onClick).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(a.name, style = ChittaTheme.type.title, color = c.ink)
            if ((a.currency ?: "INR") != "INR") Text(a.currency ?: "", style = ChittaTheme.type.label, color = c.inkMuted)
            if (card != null) Text("Owes ${IndianFormat.rupees(card.outstanding)} · due in ${card.daysToDue} ${if (card.daysToDue == 1L) "day" else "days"}", style = ChittaTheme.type.label, color = c.inkMuted)
        }
        AmountText(balance, alwaysShowPaise = true, tone = if (balance.value < 0) AmountTone.Expense else AmountTone.Neutral)
    }
    HairlineRule()
}

@Composable
private fun DepositRow(d: FixedDeposit, nowMs: Long, zone: java.time.ZoneId, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(onClick = onClick).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
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
    BottomOverlay(onClose) {
        Text("Exchange rates", style = ChittaTheme.type.title, color = c.ink)
        val asOf = ui.rates.firstOrNull()?.asOfDate
        Text(if (asOf == null) "No rates yet. Refresh needs internet." else "Daily rates as of $asOf. A rate you type wins over the daily one.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        wanted.forEach { code ->
            val override = ui.overrides.firstOrNull { it.fromCurrency == code && it.toCurrency == "INR" }
            val daily = ui.rates.firstOrNull { it.currency == code }?.perBase?.toBigDecimalOrNull()?.let { if (it.signum() == 0) null else java.math.BigDecimal.ONE.divide(it, 4, java.math.RoundingMode.HALF_EVEN).toPlainString() }
            ValueRow("1 $code", (override?.rate?.let { "₹$it (yours)" } ?: daily?.let { "₹$it" } ?: "no rate"), onClick = { editing = code })
        }
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Refresh rates", { vm.refreshRates { r -> say(if (r is RefreshResult.Updated) "Rates updated (${r.asOf})." else (r as RefreshResult.Failed).reason + " Old rates kept.") }; onClose() })
    }
    editing?.let { code ->
        val has = ui.overrides.any { it.fromCurrency == code && it.toCurrency == "INR" }
        TextSheet("1 $code in rupees", if (bad) "That is not a number" else "Rate (leave empty to use the daily rate)", ui.overrides.firstOrNull { it.fromCurrency == code && it.toCurrency == "INR" }?.rate ?: "",
            { t -> if (t.isBlank()) { if (has) vm.clearOverride(code, "INR"); editing = null } else if (vm.setOverride(code, "INR", t)) editing = null else bad = true },
            { editing = null }, numeric = true)
    }
}
