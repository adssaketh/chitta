package com.mansaketh.chitta.ui.add

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.EntryType
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.RefreshResult
import com.mansaketh.chitta.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class Pick { None, Account, ToAccount, Currency, Rate, Note, Date }

/** Quick Add (PDF p.11): type tabs, amount, title with suggestions, category chips, pinned strip, account/date/more, keypad, Save. */
@Composable
fun QuickAddSheet(vm: QuickAddViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val d = ui.draft
    val c = ChittaTheme.colors
    val context = LocalContext.current
    var pick by remember { mutableStateOf(Pick.None) }
    var rateNotice by remember { mutableStateOf<String?>(null) }
    val zone = remember { ZoneId.systemDefault() }
    val types = EntryType.entries

    BottomOverlay(onClose) {
        Column(Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
            TextTabs(listOf("Expense", "Income", "Transfer"), types.indexOf(d.type), { vm.setType(types[it]) })
            Spacer(Modifier.height(Dimens.Gap3))
            AmountEntryText(d.amountRaw)
            ui.preview?.let { Text("About ${IndianFormat.rupees(it)} at ${ui.rateText}", style = ChittaTheme.type.label, color = c.inkMuted) }
            Spacer(Modifier.height(Dimens.Gap3))

            if (d.type != EntryType.Transfer) {
                ChittaField(d.title, vm::setTitle, "Title")
                if (ui.suggestions.isNotEmpty() && d.title.isNotBlank()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), modifier = Modifier.padding(top = Dimens.Gap2)) {
                        items(ui.suggestions) { s -> ChoiceChip(s.title, selected = false, onClick = { vm.pickSuggestion(s) }) }
                    }
                }
                if (d.title.isBlank() && ui.pinned.isNotEmpty()) {
                    Text("Pinned", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap3))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                        items(ui.pinned) { p -> ChoiceChip(p.title + (p.amount?.let { " " + IndianFormat.rupees(Paise(it)) } ?: ""), selected = false, onClick = { vm.reset(pin = p) }) }
                    }
                }
                Text("Category", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap3))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                    items(ui.categories) { cc -> ChoiceChip(cc.category.name, selected = d.categoryId == cc.category.id, onClick = { vm.setCategory(cc.category.id) }, tint = c.categoryTint(cc.category.id)) }
                }
            }

            ValueRow(if (d.type == EntryType.Transfer) "From" else "Account", accountName(d.accountId ?: "", ui.accounts), onClick = { pick = Pick.Account })
            if (d.type == EntryType.Transfer) ValueRow("To", d.toAccountId?.let { accountName(it, ui.accounts) } ?: "Pick an account", onClick = { pick = Pick.ToAccount })
            ValueRow("Date", friendlyDay(d.dateMs, System.currentTimeMillis(), zone), onClick = { pick = Pick.Date })
            if (d.type != EntryType.Transfer || true) {
                val acct = ui.accounts.firstOrNull { it.id == d.accountId }?.currency ?: "INR"
                ValueRow("Currency", (d.originalCurrency ?: acct) + if (d.originalCurrency != null && d.originalCurrency != acct) " · rate ${ui.rateText ?: "unknown"}" else "", onClick = { pick = Pick.Currency })
            }
            ValueRow("Note", d.note.ifBlank { "Add a note" }, onClick = { pick = Pick.Note })
            rateNotice?.let { Text(it, style = ChittaTheme.type.label, color = c.inkMuted) }
            ui.problem?.let { if (d.amountRaw.isNotEmpty()) Text(it, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2)) }

            Spacer(Modifier.height(Dimens.Gap3))
            Keypad(vm::key)
            Spacer(Modifier.height(Dimens.Gap3))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), modifier = Modifier.fillMaxWidth()) {
                if (d.type != EntryType.Transfer && d.categoryId != null) ChittaButton("Pin", vm::pinCurrent, style = ButtonStyle.Quiet)
                ChittaButton("Save", { vm.save(onClose) }, modifier = Modifier.weight(1f), enabled = ui.ready, confirmHaptic = true)
            }
        }
    }

    when (pick) {
        Pick.Account -> ChoiceSheet("Account", ui.accounts.map { Choice(it.name, it.id, selected = it.id == d.accountId) }, { vm.setAccount(it); pick = Pick.None }, { pick = Pick.None })
        Pick.ToAccount -> ChoiceSheet("Goes to", ui.accounts.filter { it.id != d.accountId }.map { Choice(it.name, it.id, selected = it.id == d.toAccountId) }, { vm.setToAccount(it); pick = Pick.None }, { pick = Pick.None })
        Pick.Currency -> ChoiceSheet(
            "Amount is in", ui.knownCurrencies.map { Choice(it, it) } + Choice("Refresh rates", "·refresh", "Needs internet") + Choice("Type the rate myself", "·rate"),
            { v ->
                when (v) {
                    "·refresh" -> { vm.refreshRates { r -> rateNotice = if (r is RefreshResult.Updated) "Rates updated (${r.asOf})." else (r as RefreshResult.Failed).reason + " Old rates kept." }; pick = Pick.None }
                    "·rate" -> pick = Pick.Rate
                    else -> { vm.setCurrency(v); pick = Pick.None }
                }
            },
            { pick = Pick.None },
        )
        Pick.Rate -> TextSheet("Exchange rate", "Rate (account currency per 1 unit typed)", ui.rateText ?: "", { vm.setRate(it); pick = Pick.None }, { pick = Pick.None }, numeric = true)
        Pick.Note -> TextSheet("Note", "Note", d.note, { vm.setNote(it); pick = Pick.None }, { pick = Pick.None }, singleLine = false)
        Pick.Date -> {
            LaunchedEffect(Unit) {
                val day = Instant.ofEpochMilli(d.dateMs).atZone(zone).toLocalDate()
                DatePickerDialog(context, { _, y, m, dd ->
                    val time = Instant.ofEpochMilli(d.dateMs).atZone(zone).toLocalTime()
                    vm.setDate(LocalDate.of(y, m + 1, dd).atTime(time).atZone(zone).toInstant().toEpochMilli())
                    pick = Pick.None
                }, day.year, day.monthValue - 1, day.dayOfMonth).apply { setOnCancelListener { pick = Pick.None } }.show()
            }
        }
        Pick.None -> Unit
    }
}
