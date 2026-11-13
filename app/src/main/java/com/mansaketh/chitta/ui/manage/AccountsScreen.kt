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
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.RefreshResult
import com.mansaketh.chitta.ui.*

private sealed interface AccSheet {
    data object None : AccSheet
    data object Add : AccSheet
    data class Actions(val a: Account) : AccSheet
    data class Rename(val a: Account) : AccSheet
    data class Correct(val a: Account) : AccSheet
    data object Rates : AccSheet
}

/** Accounts (PDF p.25): total balance, balances per account, add / rename / archive, transfer, correct balance, exchange rates. */
@Composable
fun AccountsScreen(vm: ManageViewModel, onBack: () -> Unit, onTransfer: () -> Unit) {
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
                items(live, key = { it.id }) { a -> AccountRow(a, ui.balances[a.id] ?: Paise.ZERO) { sheet = AccSheet.Actions(a) } }
                if (archived.isNotEmpty()) item {
                    SectionHeader("Archived", action = if (showArchived) "Hide" else "Show", onAction = { showArchived = !showArchived })
                }
                if (showArchived) items(archived, key = { "x-" + it.id }) { a -> AccountRow(a, ui.balances[a.id] ?: Paise.ZERO) { sheet = AccSheet.Actions(a) } }
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
                    add(Choice(if (s.a.archived) "Bring back" else "Archive", "archive", if (s.a.archived) null else "Hidden from pickers. Entries stay."))
                },
                { v ->
                    when (v) {
                        "rename" -> sheet = AccSheet.Rename(s.a)
                        "correct" -> sheet = AccSheet.Correct(s.a)
                        else -> { vm.archiveAccount(s.a, !s.a.archived); sheet = AccSheet.None }
                    }
                },
                { sheet = AccSheet.None },
            )
            AccSheet.Rates -> RatesSheet(vm, ui, { notice = it }, { sheet = AccSheet.None })
            AccSheet.None -> Unit
        }
    }
}

@Composable
private fun AccountRow(a: Account, balance: Paise, onClick: () -> Unit) {
    val c = ChittaTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(onClick = onClick).padding(vertical = Dimens.Gap2), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(a.name, style = ChittaTheme.type.title, color = c.ink)
            if ((a.currency ?: "INR") != "INR") Text(a.currency ?: "", style = ChittaTheme.type.label, color = c.inkMuted)
        }
        AmountText(balance, alwaysShowPaise = true, tone = if (balance.value < 0) AmountTone.Expense else AmountTone.Neutral)
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
