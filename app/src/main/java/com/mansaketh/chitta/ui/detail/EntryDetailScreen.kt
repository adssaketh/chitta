package com.mansaketh.chitta.ui.detail

import com.mansaketh.chitta.core.design.kit.DateSheet
import com.mansaketh.chitta.ui.LocalFirstDay
import android.app.DatePickerDialog
import com.mansaketh.chitta.core.design.kit.NavRow
import com.mansaketh.chitta.core.design.kit.GlyphTile
import com.mansaketh.chitta.core.design.kit.ValueRow
import com.mansaketh.chitta.core.design.kit.TopBarDetail
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ButtonPair
import com.mansaketh.chitta.core.design.kit.ChittaButton
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
import com.mansaketh.chitta.core.design.kit.Glyph
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import com.mansaketh.chitta.core.design.icons.KitIcons
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
fun EntryDetailScreen(vm: EntryDetailViewModel, entryId: String, onBack: () -> Unit, onHistory: () -> Unit = {}, plan: com.mansaketh.chitta.ui.plan.PlanViewModel? = null) {
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
            TopBarDetail("Entry", onBack = onBack, actions = {
                val ent = ui.entry
                if (ent != null && ui.canEdit && !Totals.isNeutral(ent)) {
                    IconAction(ChittaIcons.Copy, "Duplicate", { vm.duplicate(onBack) })
                    IconAction(KitIcons.Pin, "Pin", vm::pin)
                    if (ent.kind == null) IconAction(KitIcons.Split, "Split", { edit = Edit.Split })
                }
            })
            when {
                ui.loading -> LoadingPlaceholder(Modifier.padding(Dimens.Margin))
                e == null -> Text("This entry is gone. It may have been deleted.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
                else -> {
                    // Another member's entry is read-only here (master section 7): rows open nothing and the actions are hidden.
                    val canEdit = ui.canEdit
                    val neutral = Totals.isNeutral(e)
                    val locked = neutral || !canEdit
                    val transfer = Totals.isTransfer(e)
                    // One split payment is several entries with one group id; the page shows them as the one payment it was (PDF p.20).
                    val isSplit = e.splitGroupId != null && ui.siblings.isNotEmpty()
                    val lineCount = ui.siblings.size + 1
                    val total = Paise(Math.abs(e.amount) + ui.siblings.sumOf { Math.abs(it.amount) })
                    val bank = com.mansaketh.chitta.core.intake.PingExport.refOfNote(e.note) != null || com.mansaketh.chitta.core.intake.hasCheckMark(e.note)
                    val kindWord = when {
                        transfer -> "Transfer"
                        Totals.isBalanceCorrection(e) -> "Correction"
                        e.kind == TransactionKind.LENT -> "Lent"
                        e.kind == TransactionKind.BORROWED -> "Borrowed"
                        e.amount > 0 -> "Income"
                        else -> "Expense"
                    }
                    val caption = if (neutral) kindWord else kindWord + " \u00B7 " + (if (bank) "from a bank message" else "added by you")
                    val inset = Dimens.Margin
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Dimens.FabInset)) {
                        // The top block is the editor (v8 §5, screen 12): tile, title and amount each open their own sheet; nothing is shown twice below.
                        val cat = ui.categories.firstOrNull { it.id == e.categoryId }
                        Row(Modifier.padding(horizontal = inset).padding(top = Dimens.Gap4), verticalAlignment = Alignment.Top) {
                            GlyphTile(
                                if (neutral) ChittaIcons.Transfer else (com.mansaketh.chitta.ui.manage.CategoryIconCatalog.imageFor(cat?.iconName, cat?.name) ?: ChittaIcons.Tag),
                                if (neutral) c.inkMuted else c.categoryTint(e.categoryId),
                            )
                            Spacer(Modifier.width(Dimens.Space4))
                            Column(Modifier.weight(1f)) {
                                Text(caption, style = ChittaTheme.type.mono, color = c.inkMuted)
                                Text(
                                    e.name.ifBlank { if (locked) categoryName(e.categoryId, ui.categories) else "No title" }, style = ChittaTheme.type.title, color = c.ink,
                                    modifier = Modifier.then(if (locked) Modifier else Modifier.pressClickable(role = Role.Button, onClickLabel = "Change title") { edit = Edit.Title }),
                                )
                                Spacer(Modifier.height(Dimens.Gap2))
                                AmountText(
                                    if (isSplit) total else Paise(Math.abs(e.amount)), style = ChittaTheme.type.amountLarge,
                                    tone = if (neutral) AmountTone.Neutral else if (e.amount > 0) AmountTone.Income else AmountTone.Expense,
                                    modifier = Modifier.then(if (locked || isSplit) Modifier else Modifier.pressClickable(role = Role.Button, onClickLabel = "Change amount") { edit = Edit.Amount }),
                                )
                                if (e.originalAmount != null && e.originalCurrency != null) {
                                    Text("Typed as ${e.originalCurrency} ${IndianFormat.rupees(Paise(Math.abs(e.originalAmount!!)), symbol = "")} at ${e.exchangeRate ?: "a stored rate"}", style = ChittaTheme.type.label, color = c.inkMuted)
                                }
                                val hasChips = LedgerSearch.isUpcoming(e) || (e.kind != null && e.paid) || transfer || Totals.isBalanceCorrection(e) || com.mansaketh.chitta.core.intake.hasCheckMark(e.note) || e.splitGroupId != null
                                if (hasChips) Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), modifier = Modifier.padding(top = Dimens.Gap2)) {
                                    if (e.splitGroupId != null) TagChip("Split")
                                    if (LedgerSearch.isUpcoming(e)) Stamp("Upcoming")
                                    else if (e.kind != null && e.paid) Stamp("Paid")
                                    if (transfer) Stamp("Transfer")
                                    if (Totals.isBalanceCorrection(e)) Stamp("Correction")
                                    if (com.mansaketh.chitta.core.intake.hasCheckMark(e.note)) Stamp("Check", tone = StampTone.Attention)
                                }
                            }
                        }
                        if (canEdit && com.mansaketh.chitta.core.intake.hasCheckMark(e.note)) {
                            Column(Modifier.padding(horizontal = inset)) {
                                Text("Added automatically from a bank message on a best guess. Change what is wrong, or confirm it.", style = ChittaTheme.type.label, color = c.inkMuted)
                                ChittaButton("Looks right", vm::confirmCheck, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Spacer(Modifier.height(Dimens.Gap4))
                        HairlineRule()
                        // Rows in the order of the page; the rules run edge to edge, the words keep the margin.
                        val categoryClick: (() -> Unit)? = when {
                            isSplit -> if (canEdit && !neutral) ({ edit = Edit.Split }) else null
                            locked -> null
                            else -> ({ edit = Edit.Category })
                        }
                        ValueRow(
                            "Category",
                            when { isSplit -> "Split \u00B7 $lineCount lines"; neutral -> "Not counted as spending"; else -> categoryName(e.categoryId, ui.categories) },
                            onClick = categoryClick, inset = inset,
                        )
                        ValueRow("Account", accountName(e.accountId, ui.accounts), onClick = if (locked) null else ({ edit = Edit.Account }), inset = inset)
                        ValueRow("Date", dayAndTime(e.dateMs, zone), onClick = if (canEdit) ({ edit = Edit.Date }) else null, inset = inset)
                        // Who sees it (PDF p.20, p.31): always drawn. Only entries of mine in a household can be switched; without sharing it is only me.
                        ValueRow(
                            "Who sees it", if (!ui.sharing) "Only me" else if (e.isPrivate) "Mine only" else "Everyone",
                            onClick = if (ui.sharing && canEdit && e.ownerUid != "") ({ vm.setMineOnly(!e.isPrivate) }) else null, inset = inset,
                        )
                        ValueRow("Note", e.note.ifBlank { if (canEdit) "Add a note" else "No note" }, onClick = if (canEdit) ({ edit = Edit.Note }) else null, inset = inset)
                        if (canEdit) ValueRow("Receipt", when { receipt.busy -> "Reading the photo\u2026"; receipt.has -> "Photo attached"; else -> "Add a photo" }, onClick = { edit = Edit.Receipt }, inset = inset)
                        // Not drawn on the page, kept in the same style below the drawn rows (Screen 12, answer 7).
                        if (transfer) ui.twin?.let { ValueRow("Other half", accountName(it.accountId, ui.accounts), inset = inset) }
                        if (ui.sharing && !canEdit) ValueRow("Added by", accountName(e.accountId, ui.accounts), inset = inset)
                        if (remember(e.note) { com.mansaketh.chitta.intake.PingBridge.viewIntent(context, e.note) != null }) {
                            Column(Modifier.padding(horizontal = inset)) { NavRow("View SMS in Ping", { com.mansaketh.chitta.intake.PingBridge.open(context, e.note) }) }
                        }
                        if (plan != null && canEdit && !neutral && e.kind != TransactionKind.LENT && e.kind != TransactionKind.BORROWED) ValueRow("Repeat", e.repeat?.let { com.mansaketh.chitta.ui.plan.repeatLabel(it, e.periodLength ?: 1) } ?: "Does not repeat", onClick = { edit = Edit.Repeat }, inset = inset)
                        if (ui.siblings.isNotEmpty()) {
                            Text("Lines of this split:", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(horizontal = inset).padding(top = Dimens.Gap2))
                            (listOf(e) + ui.siblings).forEach { s -> ValueRow(categoryName(s.categoryId, ui.categories), IndianFormat.rupees(Paise(Math.abs(s.amount)), alwaysShowPaise = true), inset = inset) }
                        }
                        // History reads as a timeline, newest first (PDF p.20). The words keep the margin, and so does the rule under the heading.
                        if (ui.history.isNotEmpty()) Column(Modifier.padding(horizontal = inset)) {
                            Text("History", style = ChittaTheme.type.bodyMedium, color = c.ink, modifier = Modifier.padding(top = Dimens.Space5, bottom = Dimens.Gap2))
                            HairlineRule()
                            val versions = ui.history.sortedByDescending { it.id }
                            versions.forEachIndexed { i, v ->
                                val older = versions.getOrNull(i + 1)
                                val label = when (v.action) {
                                    HistoryAction.CREATED -> if (isSplit) "Split into $lineCount lines" else if (bank) "Saved from a bank message" else "Added"
                                    HistoryAction.EDITED -> "Edited"
                                    HistoryAction.DELETED -> "Deleted"
                                    HistoryAction.RESTORED -> "Restored"
                                }
                                val restorable = canEdit && (i > 0 || v.action == HistoryAction.DELETED)
                                TimelineItem(
                                    label, dayAndTime(v.atMs, zone), first = i == 0, last = i == versions.lastIndex,
                                    action = if (restorable) "Restore" else null, onAction = if (restorable) ({ vm.restore(v) }) else null,
                                ) {
                                    val changed = if (older != null) com.mansaketh.chitta.core.domain.EditDiff.between(older.snapshot, v.snapshot).joinToString(", ") { it.field } else ""
                                    if (changed.isNotEmpty()) Text("Changed: $changed", style = ChittaTheme.type.label, color = c.inkMuted)
                                }
                            }
                            if (ui.canDelete) ChittaButton("Delete entry", { impact = vm.deleteImpact(); edit = Edit.Delete }, kind = ButtonKind.Danger, modifier = Modifier.padding(top = Dimens.Gap2).offset(x = -Dimens.Space2))
                        } else if (ui.canDelete) {
                            ChittaButton("Delete entry", { impact = vm.deleteImpact(); edit = Edit.Delete }, kind = ButtonKind.Danger, modifier = Modifier.padding(horizontal = inset).offset(x = -Dimens.Space2))
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
            Edit.Account -> ChoiceSheet("Account", ui.accounts.filter { !it.archived && !com.mansaketh.chitta.core.domain.HouseholdSharing.isPlaceholderAccountId(it.id) }.map { Choice(it.name, it.id, selected = it.id == e.accountId) }, { id -> vm.setAccount(id, ui.accounts.firstOrNull { it.id == id }?.currency); edit = Edit.None }, { edit = Edit.None })
            Edit.Date -> BottomOverlay({ edit = Edit.None }, handle = false) {
                val t = Instant.ofEpochMilli(e.dateMs).atZone(zone).toLocalTime()
                DateSheet(
                    initial = Instant.ofEpochMilli(e.dateMs).atZone(zone).toLocalDate(), today = LocalDate.now(zone), firstDayOfWeek = LocalFirstDay.current,
                    onUse = { d -> vm.setDate(d.atTime(t).atZone(zone).toInstant().toEpochMilli()); edit = Edit.None },
                    onClose = { edit = Edit.None },
                )
            }
            Edit.Delete -> impact?.let { im ->
                // PDF p.22: title with the name and amount, a "This also changes" list of before -> after rows, a note, Keep it / Delete.
                val c = ChittaTheme.colors
                val notes = buildList {
                    if (im.toDelete.size > 1) add("This removes ${im.toDelete.size} entries.")
                    if (im.extraTransferLegs > 0) add("It also removes the other half of this transfer, so no balance is left half-moved.")
                    if (im.linkedToGoalOrLoan > 0) add("It is linked to a goal or loan.")
                    add("You can undo for 10 seconds, and it stays in the entry\u2019s history.")
                }
                com.mansaketh.chitta.ui.BottomOverlay({ edit = Edit.None }, handle = false) {
                    com.mansaketh.chitta.core.design.kit.SheetHeader("Delete ${e.name.ifBlank { "this entry" }}, ${IndianFormat.rupees(Paise(Math.abs(e.amount)))}?")
                    if (im.balanceShifts.isNotEmpty()) {
                        Spacer(Modifier.height(Dimens.Gap2))
                        Text("This also changes", style = ChittaTheme.type.body, color = c.inkMuted)
                        Spacer(Modifier.height(Dimens.Gap2))
                        im.balanceShifts.forEach {
                            ValueRow("${it.account.name} balance", "${IndianFormat.rupees(it.before)} \u2192 ${IndianFormat.rupees(it.after)}")
                        }
                    }
                    notes.forEach { FootNote(it) }
                    Spacer(Modifier.height(Dimens.Gap3))
                    ButtonPair(
                        first = { m -> ChittaButton("Keep it", { edit = Edit.None }, m, kind = ButtonKind.Secondary) },
                        second = { m -> ChittaButton("Delete", { vm.confirmDelete(im, onBack); edit = Edit.None }, m) },
                    )
                }
            }
            Edit.Split -> {
                val group = if (ui.siblings.isNotEmpty()) listOf(e) + ui.siblings else emptyList()
                val groupTotal = Paise(group.sumOf { Math.abs(it.amount) })
                SplitSheet(
                    if (group.isEmpty()) Paise(Math.abs(e.amount)) else groupTotal,
                    ui.categories.filter { it.mainCategoryId == null && !it.archived && it.income == e.income }, e.categoryId,
                    { lines -> if (group.isEmpty()) vm.split(lines) { edit = Edit.None; onBack() } else vm.resplit(lines) { edit = Edit.None; onBack() } },
                    { edit = Edit.None },
                    initial = group.map { com.mansaketh.chitta.core.domain.SplitLine(it.categoryId, it.subCategoryId, Paise(Math.abs(it.amount))) },
                )
            }
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
                    reading, e, canEdit = !Totals.isNeutral(e) && ui.canEdit, zone = zone,
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
        TopBarDetail("History", onBack = onBack)
        if (ui.history.isEmpty()) Text("No history yet.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(Dimens.Margin))
        LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = Dimens.FabInset)) {
            val versions = ui.history.sortedByDescending { it.id }
            items(versions.size) { i ->
                val v = versions[i]
                val older = versions.getOrNull(i + 1)
                val label = when (v.action) { HistoryAction.CREATED -> "Created"; HistoryAction.EDITED -> "Edited"; HistoryAction.DELETED -> "Deleted"; HistoryAction.RESTORED -> "Restored" }
                TimelineItem(label, dayAndTime(v.atMs, zone), first = i == 0, last = i == versions.lastIndex) {
                    Text("${v.snapshot.name.ifBlank { "No title" }} · ${IndianFormat.rupees(Paise(Math.abs(v.snapshot.amount)), alwaysShowPaise = true)} · ${categoryName(v.snapshot.categoryId, ui.categories)}", style = ChittaTheme.type.body, color = c.inkMuted)
                    if (older != null) com.mansaketh.chitta.core.domain.EditDiff.between(older.snapshot, v.snapshot).forEach { ch ->
                        Text("${ch.field}: ${pretty(ch.field, ch.before, ui)} to ${pretty(ch.field, ch.after, ui)}", style = ChittaTheme.type.label, color = c.inkMuted)
                    }
                    if (ui.canEdit && (i > 0 || v.action == HistoryAction.DELETED)) {
                        Spacer(Modifier.height(Dimens.InlineGap))
                        ChittaButton("Restore this version", { vm.restore(v) }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                    }
                }
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
