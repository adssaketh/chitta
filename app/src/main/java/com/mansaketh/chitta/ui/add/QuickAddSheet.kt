package com.mansaketh.chitta.ui.add

import com.mansaketh.chitta.core.design.kit.DateSheet
import com.mansaketh.chitta.ui.LocalFirstDay
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.icons.KitIcons
import com.mansaketh.chitta.core.design.kit.Segmented
import com.mansaketh.chitta.core.design.kit.SheetHandle
import com.mansaketh.chitta.core.design.kit.CategoryChip
import com.mansaketh.chitta.core.design.kit.ValueRow
import com.mansaketh.chitta.core.design.kit.ChittaButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.theme.rupeeAware
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

/**
 * Quick Add (PDF p.15, screen 07): handle only (no title, no close button), the type switch with Pin and Split at the top, the amount with
 * "Safe to spend after this", the title, the category chips, one summary line (account, date, who sees it) with "More options", the keypad and
 * Save with the amount in it. Everything above the keypad scrolls when text is large or the keyboard is up; the keypad and Save stay put.
 */
@Composable
fun QuickAddSheet(vm: QuickAddViewModel, onClose: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val safeAfter by vm.safeAfter.collectAsState()
    val sharing by vm.sharing.collectAsState()
    val mineOnly by vm.mineOnly.collectAsState()
    val d = ui.draft
    val c = ChittaTheme.colors
    var pick by remember { mutableStateOf(Pick.None) }
    var useKeyboard by remember { mutableStateOf(false) }
    val amountFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var rateNotice by remember { mutableStateOf<String?>(null) }
    var splitTotal by remember { mutableStateOf<com.mansaketh.chitta.core.money.Paise?>(null) }
    val zone = remember { ZoneId.systemDefault() }
    val types = EntryType.entries
    var moreOptions by remember { mutableStateOf(false) }
    val formScroll = rememberScrollState()
    // The amount's caret shows only while it is being typed: after a tap on the amount or a key press, and for 2 s after the last one.
    // A field taking focus (Title) hides it at once.
    var typingTick by remember { mutableStateOf(0) }
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(typingTick) {
        typing = typingTick > 0
        if (typingTick > 0) { kotlinx.coroutines.delay(2000); typing = false }
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // The keypad and Save are fixed, so the rows "More options" opens land below the visible form: scroll them into view.
    LaunchedEffect(moreOptions) {
        if (moreOptions) {
            withFrameNanos { }
            withFrameNanos { }
            formScroll.animateScrollTo(formScroll.maxValue)
        }
    }

    val transfer = d.type == EntryType.Transfer
    val acct = ui.accounts.firstOrNull { it.id == d.accountId }?.currency ?: "INR"
    val otherCurrency = d.originalCurrency != null && d.originalCurrency != acct
    // "More options" opens Note and Currency in place. They also show on their own when they matter: a note is already there, or the currency is not the account's (PDF p.15).
    val expanded = moreOptions || otherCurrency || d.note.isNotBlank()

    BottomOverlay(onClose, handle = false) {
        Column(Modifier.heightIn(max = sheetMax(Dimens.SheetMaxFraction)).semantics { paneTitle = "Add a transaction" }) {
            SheetHandle(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(Dimens.SheetHandleGap))
            Column(
                Modifier.weight(1f, fill = false).bleed(Dimens.Margin).verticalScroll(formScroll).padding(horizontal = Dimens.Margin),
            ) {
                QuickAddTopRow(
                    type = { pad -> Segmented(listOf("Expense", "Income", "Transfer"), types.indexOf(d.type), { vm.setType(types[it]) }, partPadding = pad) },
                    actions = {
                        IconAction(KitIcons.Pin, "Pin", vm::pinCurrent, enabled = !transfer && d.categoryId != null)
                        IconAction(KitIcons.Split, "Split", { splitTotal = vm.splitTotal() }, enabled = ui.ready && !transfer && ui.categories.isNotEmpty())
                    },
                )
                Spacer(Modifier.height(Dimens.Space2))
                AmountEntryText(
                    d.amountRaw, showCaret = typing,
                    modifier = Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                        focusManager.clearFocus(); typingTick++
                    },
                )
                Box(Modifier.heightIn(min = Dimens.Space4)) {
                    val after = safeAfter
                    if (d.type == EntryType.Expense && after != null) {
                        Text("Safe to spend after this: ${IndianFormat.rupeesRounded(after)}".rupeeAware(), style = ChittaTheme.type.label, color = c.inkMuted)
                    }
                }
                Spacer(Modifier.height(Dimens.Space4))

                if (!transfer) {
                    ChittaField(
                        d.title, vm::setTitle, label = null, placeholder = "Title", minHeight = Dimens.TouchTarget, description = "Title",
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) typingTick = 0 },
                    )
                    if (ui.suggestions.isNotEmpty() && d.title.isNotBlank()) {
                        LazyRow(
                            Modifier.bleed(Dimens.Margin).padding(top = Dimens.Gap2), contentPadding = PaddingValues(horizontal = Dimens.Margin),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2),
                        ) {
                            items(ui.suggestions) { s -> CategoryChip(s.title, selected = false, onClick = { vm.pickSuggestion(s) }) }
                        }
                    }
                    if (d.title.isBlank() && ui.pinned.isNotEmpty()) {
                        LazyRow(
                            Modifier.bleed(Dimens.Margin).padding(top = Dimens.Gap2), contentPadding = PaddingValues(horizontal = Dimens.Margin),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2),
                        ) {
                            items(ui.pinned) { p ->
                                CategoryChip(
                                    p.title + (p.amount?.let { " " + IndianFormat.rupees(Paise(it)) } ?: ""), selected = false, onClick = { vm.reset(pin = p) },
                                    icon = KitIcons.Pin, color = c.inkMuted,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Dimens.Space2))
                    LazyRow(Modifier.bleed(Dimens.Margin), contentPadding = PaddingValues(horizontal = Dimens.Margin), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                        items(ui.categories) { cc ->
                            CategoryChip(
                                cc.category.name, selected = d.categoryId == cc.category.id, onClick = { vm.setCategory(cc.category.id) },
                                icon = com.mansaketh.chitta.ui.manage.CategoryIconCatalog.imageFor(cc.category.iconName, cc.category.name) ?: ChittaIcons.Tag,
                                color = c.categoryTint(cc.category.id),
                            )
                        }
                    }
                }

                val summary = buildList {
                    add(SummaryPart(accountName(d.accountId ?: "", ui.accounts), if (transfer) "From account" else "Account", { pick = Pick.Account }))
                    if (transfer) add(SummaryPart(d.toAccountId?.let { accountName(it, ui.accounts) } ?: "Pick an account", "To account", { pick = Pick.ToAccount }, separator = " → "))
                    add(SummaryPart(friendlyDay(d.dateMs, System.currentTimeMillis(), zone), "Date", { pick = Pick.Date }))
                    if (sharing) add(SummaryPart(if (mineOnly) "Mine only" else "Everyone", "Who sees it. Tap to change", { vm.setMineOnly(!mineOnly) }))
                }
                SummaryLine(summary, onMore = if (expanded) null else ({ moreOptions = true }), modifier = Modifier.tightVertical(Dimens.Space4))

                if (expanded) {
                    if (otherCurrency || moreOptions) {
                        ValueRow("Currency", (d.originalCurrency ?: acct).uppercase() + if (otherCurrency) " · rate ${ui.rateText ?: "unknown"}" else "", onClick = { pick = Pick.Currency })
                    }
                    ui.preview?.let { Text("About ${IndianFormat.rupees(it)} at ${ui.rateText}".rupeeAware(), style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap2)) }
                    ValueRow("Note", d.note.ifBlank { "Add a note" }, onClick = { pick = Pick.Note })
                }
                rateNotice?.let { Text(it, style = ChittaTheme.type.label, color = c.inkMuted) }
                vm.linkNotes.collectAsState().value.forEach { Text(it, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap1)) }
                ui.problem?.let { if (d.amountRaw.isNotEmpty()) Text(it, style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2)) }
            }

            HairlineRule(Modifier.bleed(Dimens.Margin))
            Spacer(Modifier.height(Dimens.Space2))
            if (useKeyboard) {
                // Phone keyboard: type the amount, or a sum such as 120+45 (use - * / for the other signs).
                ChittaField(
                    value = d.amountRaw, onValueChange = vm::setAmountRaw, label = "Amount",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),
                    focusRequester = amountFocus,
                )
                LaunchedEffect(Unit) { amountFocus.requestFocus() }
            } else {
                Keypad({ typingTick++; vm.key(it) }, modifier = Modifier.bleed(Dimens.KeypadBleed), showEquals = com.mansaketh.chitta.core.domain.AmountExpression.isCalculation(d.amountRaw))
            }
            // The one thing p.15 does not draw: the master prompt keeps the way to the phone keyboard, so it sits as a small link between the keypad and Save.
            Row(Modifier.fillMaxWidth().tightVertical(Dimens.Space2), horizontalArrangement = Arrangement.End) {
                Text(
                    if (useKeyboard) "Use the keypad" else "Use the phone keyboard",
                    style = ChittaTheme.type.link, color = c.ink,
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget)
                        .pressClickable(role = androidx.compose.ui.semantics.Role.Button) { useKeyboard = !useKeyboard }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            Spacer(Modifier.height(Dimens.Space1))
            val saving = ui.saveAmount
            ChittaButton(
                if (ui.ready && saving != null) "Save ${IndianFormat.rupees(saving)}" else "Save",
                { vm.save(onClose) }, modifier = Modifier.fillMaxWidth(), enabled = ui.ready, confirmHaptic = true,
            )
        }
    }

    splitTotal?.let { total ->
        com.mansaketh.chitta.ui.detail.SplitSheet(
            total, ui.categories.map { it.category }.filter { it.mainCategoryId == null && !it.archived }, d.categoryId ?: ui.categories.first().category.id,
            { lines -> vm.saveSplit(lines) { splitTotal = null; onClose() } }, { splitTotal = null },
        )
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
        Pick.Date -> BottomOverlay({ pick = Pick.None }, handle = false) {
            val time = Instant.ofEpochMilli(d.dateMs).atZone(zone).toLocalTime()
            DateSheet(
                initial = Instant.ofEpochMilli(d.dateMs).atZone(zone).toLocalDate(), today = LocalDate.now(zone), firstDayOfWeek = LocalFirstDay.current,
                onUse = { day -> vm.setDate(day.atTime(time).atZone(zone).toInstant().toEpochMilli()); pick = Pick.None },
                onClose = { pick = Pick.None },
            )
        }
        Pick.None -> Unit
    }
}
