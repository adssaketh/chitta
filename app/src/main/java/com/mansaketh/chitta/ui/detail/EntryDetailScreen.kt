package com.mansaketh.chitta.ui.detail

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.LedgerSearch
import com.mansaketh.chitta.core.domain.Totals
import com.mansaketh.chitta.core.model.HistoryAction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class Edit { None, Title, Amount, Category, Account, Note, Date, Delete, Repeat, Split, Receipt, ReceiptView }

/** Entry detail (PDF p.15): stamps, editable rows, History, and Duplicate / Pin / Delete. */
@Composable
fun EntryDetailScreen(vm: EntryDetailViewModel, entryId: String, onBack: () -> Unit, onHistory: () -> Unit, plan: com.mansaketh.chitta.ui.plan.PlanViewModel? = null) {
    LaunchedEffect(entryId) { vm.load(entryId) }
    val ui by vm.ui.collectAsState()
    val e = ui.entry
    val c = ChittaTheme.colors
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    var edit by remember { mutableStateOf(Edit.None) }
    var impact by remember { mutableStateOf<com.mansaketh.chitta.core.domain.DeleteImpact?>(null) }
    val receipt by vm.receipt.collectAsState()
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.attachCaptured() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.attachReceipt(uri) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChittaTopBar("Entry", onBack = onBack)
            when {
                ui.loading -> LoadingPlaceholder(Modifier.padding(Dimens.Margin))
                e == null -> Text("This entry is gone. It may have been deleted.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
                else -> {
                    val neutral = Totals.isNeutral(e)
                    val transfer = Totals.isTransfer(e)
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Dimens.Margin).padding(bottom = 120.dp)) {
                        Text(e.name.ifBlank { categoryName(e.categoryId, ui.categories) }, style = ChittaTheme.type.headline, color = c.ink)
                        Spacer(Modifier.height(Dimens.Gap2))
                        AmountText(Paise(Math.abs(e.amount)), style = ChittaTheme.type.amountHero, tone = if (neutral) AmountTone.Neutral else if (e.amount > 0) AmountTone.Income else AmountTone.Expense, alwaysShowPaise = true)
                        if (e.originalAmount != null && e.originalCurrency != null) {
                            Text("Typed as ${e.originalCurrency} ${IndianFormat.rupees(Paise(Math.abs(e.originalAmount!!)), symbol = "")} at ${e.exchangeRate ?: "a stored rate"}", style = ChittaTheme.type.label, color = c.inkMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), modifier = Modifier.padding(vertical = Dimens.Gap3)) {
                            if (LedgerSearch.isUpcoming(e)) Stamp("Upcoming")
                            else if (e.kind != null && e.paid) Stamp("Paid")
                            if (transfer) Stamp("Transfer")
                            if (Totals.isBalanceCorrection(e)) Stamp("Correction")
                        }
                        HairlineRule()
                        ValueRow("Title", e.name.ifBlank { "No title" }, onClick = if (neutral) null else ({ edit = Edit.Title }))
                        ValueRow("Amount", IndianFormat.rupees(Paise(Math.abs(e.amount)), alwaysShowPaise = true), onClick = if (neutral) null else ({ edit = Edit.Amount }))
                        ValueRow("Category", if (neutral) "Not counted as spending" else categoryName(e.categoryId, ui.categories), onClick = if (neutral) null else ({ edit = Edit.Category }))
                        ValueRow("Account", accountName(e.accountId, ui.accounts), onClick = if (neutral) null else ({ edit = Edit.Account }))
                        if (transfer) ui.twin?.let { ValueRow("Other half", accountName(it.accountId, ui.accounts)) }
                        ValueRow("Date", dayAndTime(e.dateMs, zone), onClick = { edit = Edit.Date })
                        ValueRow("Note", e.note.ifBlank { "No note" }, onClick = { edit = Edit.Note })
                        if (remember(e.note) { com.mansaketh.chitta.intake.PingBridge.viewIntent(context, e.note) != null }) ValueRow("View SMS in Ping", "", onClick = { com.mansaketh.chitta.intake.PingBridge.open(context, e.note) })
                        if (plan != null && !neutral && e.kind != TransactionKind.LENT && e.kind != TransactionKind.BORROWED) ValueRow("Repeat", e.repeat?.let { com.mansaketh.chitta.ui.plan.repeatLabel(it, e.periodLength ?: 1) } ?: "Does not repeat", onClick = { edit = Edit.Repeat })
                        if (ui.siblings.isNotEmpty()) {
                            Text("Part of a split of ${IndianFormat.rupees(Paise(Math.abs(e.amount) + ui.siblings.sumOf { Math.abs(it.amount) }), alwaysShowPaise = true)}. Other lines:", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
                            ui.siblings.forEach { s -> ValueRow(categoryName(s.categoryId, ui.categories), IndianFormat.rupees(Paise(Math.abs(s.amount)), alwaysShowPaise = true)) }
                        }
                        ValueRow("Receipt", when { receipt.busy -> "Reading the photo…"; receipt.has -> "Photo attached"; else -> "None" }, onClick = { edit = Edit.Receipt })
                        ValueRow("History", "${ui.history.size} ${if (ui.history.size == 1) "version" else "versions"}", onClick = onHistory)
                        Spacer(Modifier.height(Dimens.Gap4))
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                            if (!neutral) ChittaButton("Duplicate", { vm.duplicate(onBack) }, style = ButtonStyle.Quiet)
                            if (!neutral) ChittaButton("Pin", vm::pin, style = ButtonStyle.Quiet)
                            if (!neutral && e.kind == null && e.splitGroupId == null) ChittaButton("Split", { edit = Edit.Split }, style = ButtonStyle.Quiet)
                            ChittaButton("Delete", { impact = vm.deleteImpact(); edit = Edit.Delete }, style = ButtonStyle.Quiet)
                        }
                    }
                }
            }
        }

        if (e != null) when (edit) {
            Edit.Title -> TextSheet("Title", "Title", e.name, { vm.setTitle(it); edit = Edit.None }, { edit = Edit.None })
            Edit.Amount -> {
                var bad by remember { mutableStateOf(false) }
                TextSheet("Amount", if (bad) "Enter an amount above zero" else "Amount", IndianFormat.rupees(Paise(Math.abs(e.amount)), symbol = "").replace(",", ""), { if (vm.setAmount(it)) edit = Edit.None else bad = true }, { edit = Edit.None }, numeric = true)
            }
            Edit.Note -> TextSheet("Note", "Note", e.note, { vm.setNote(it); edit = Edit.None }, { edit = Edit.None }, singleLine = false)
            Edit.Category -> ChoiceSheet("Category", ui.categories.filter { it.mainCategoryId == null && !it.archived && it.income == e.income }.map { Choice(it.name, it.id, selected = it.id == e.categoryId) }, { vm.setCategory(it); edit = Edit.None }, { edit = Edit.None })
            Edit.Account -> ChoiceSheet("Account", ui.accounts.filter { !it.archived }.map { Choice(it.name, it.id, selected = it.id == e.accountId) }, { id -> vm.setAccount(id, ui.accounts.firstOrNull { it.id == id }?.currency); edit = Edit.None }, { edit = Edit.None })
            Edit.Date -> LaunchedEffect(Unit) {
                val day = Instant.ofEpochMilli(e.dateMs).atZone(zone).toLocalDate()
                DatePickerDialog(context, { _, y, m, d ->
                    val t = Instant.ofEpochMilli(e.dateMs).atZone(zone).toLocalTime()
                    vm.setDate(LocalDate.of(y, m + 1, d).atTime(t).atZone(zone).toInstant().toEpochMilli()); edit = Edit.None
                }, day.year, day.monthValue - 1, day.dayOfMonth).apply { setOnCancelListener { edit = Edit.None } }.show()
            }
            Edit.Delete -> impact?.let { im ->
                val lines = buildList {
                    add("This removes ${im.toDelete.size} ${if (im.toDelete.size == 1) "entry" else "entries"}.")
                    if (im.extraTransferLegs > 0) add("It also removes the other half of this transfer, so no balance is left half-moved.")
                    im.balanceShifts.forEach { add("${it.account.name}: ${IndianFormat.rupees(it.before)} becomes ${IndianFormat.rupees(it.after)}.") }
                    if (im.linkedToGoalOrLoan > 0) add("It is linked to a goal or loan.")
                    add("You can undo this for 10 seconds, and it stays in history.")
                }
                ConfirmSheet("Delete?", lines, "Delete", { vm.confirmDelete(im, onBack); edit = Edit.None }, { edit = Edit.None })
            }
            Edit.Split -> SplitSheet(
                Paise(Math.abs(e.amount)), ui.categories.filter { it.mainCategoryId == null && !it.archived && it.income == e.income }, e.categoryId,
                { lines -> vm.split(lines) { edit = Edit.None; onBack() } }, { edit = Edit.None },
            )
            Edit.Repeat -> if (plan != null) com.mansaketh.chitta.ui.plan.RepeatSheet(e, zone, { r, len, end -> plan.repeatEntry(e, r, len, end); edit = Edit.None }, { edit = Edit.None })
            Edit.Receipt -> ReceiptSheet(
                has = receipt.has,
                onCamera = {
                    edit = Edit.None
                    try { camera.launch(vm.newCaptureUri()) } catch (ex: Exception) { vm.setReceiptNotice("No camera app was found on this phone. You can choose a photo from the gallery instead.") }
                },
                onGallery = {
                    edit = Edit.None
                    try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (ex: Exception) { vm.setReceiptNotice("No gallery app was found on this phone.") }
                },
                onView = { edit = Edit.ReceiptView },
                onReadAgain = { edit = Edit.None; vm.readAgain() },
                onRemove = { edit = Edit.None; vm.removeReceipt() },
                onClose = { edit = Edit.None },
            )
            Edit.ReceiptView -> ReceiptViewer(load = { vm.receiptImage() }, onClose = { edit = Edit.Receipt })
            Edit.None -> Unit
        }
        // Suggestions and messages from the receipt come up by themselves when the reading finishes.
        if (e != null && edit == Edit.None) {
            val reading = receipt.reading
            val notice = receipt.notice
            when {
                reading != null -> ReceiptReadingSheet(
                    reading, e, canEdit = !Totals.isNeutral(e), zone = zone,
                    onUseAmount = vm::useReceiptAmount, onUseDate = vm::useReceiptDate, onUseTitle = vm::setTitle, onClose = vm::closeReading,
                )
                notice != null -> ReceiptNoticeSheet(notice, vm::clearReceiptNotice)
            }
        }
    }
}

/** Every version of an entry, newest first, each with what changed and a Restore button. */
@Composable
fun EntryHistoryScreen(vm: EntryDetailViewModel, entryId: String, onBack: () -> Unit) {
    LaunchedEffect(entryId) { vm.load(entryId) }
    val ui by vm.ui.collectAsState()
    val c = ChittaTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("History", onBack = onBack)
        if (ui.history.isEmpty()) Text("No history yet.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
        LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 120.dp)) {
            val versions = ui.history.sortedByDescending { it.id }
            items(versions.size) { i ->
                val v = versions[i]
                val older = versions.getOrNull(i + 1)
                val label = when (v.action) { HistoryAction.CREATED -> "Created"; HistoryAction.EDITED -> "Edited"; HistoryAction.DELETED -> "Deleted"; HistoryAction.RESTORED -> "Restored" }
                Column(Modifier.fillMaxWidth().padding(vertical = Dimens.Gap3)) {
                    Text("$label · ${dayAndTime(v.atMs, zone)}", style = ChittaTheme.type.title, color = c.ink)
                    Text("${v.snapshot.name.ifBlank { "No title" }} · ${IndianFormat.rupees(Paise(Math.abs(v.snapshot.amount)), alwaysShowPaise = true)} · ${categoryName(v.snapshot.categoryId, ui.categories)}", style = ChittaTheme.type.body, color = c.inkMuted)
                    if (older != null) com.mansaketh.chitta.core.domain.EditDiff.between(older.snapshot, v.snapshot).forEach { ch ->
                        Text("${ch.field}: ${pretty(ch.field, ch.before, ui)} to ${pretty(ch.field, ch.after, ui)}", style = ChittaTheme.type.label, color = c.inkMuted)
                    }
                    if (i > 0 || v.action == HistoryAction.DELETED) ChittaButton("Restore this version", { vm.restore(v) }, style = ButtonStyle.Quiet)
                }
                HairlineRule()
            }
        }
    }
}

private fun pretty(field: String, raw: String, ui: DetailUi): String = when (field) {
    "Amount" -> raw.toLongOrNull()?.let { IndianFormat.rupees(Paise(Math.abs(it)), alwaysShowPaise = true) } ?: raw
    "Category" -> categoryName(raw, ui.categories)
    "Account" -> accountName(raw, ui.accounts)
    "Date" -> raw.toLongOrNull()?.let { dayAndTime(it, ZoneId.systemDefault()) } ?: raw
    else -> raw.ifBlank { "empty" }
}
