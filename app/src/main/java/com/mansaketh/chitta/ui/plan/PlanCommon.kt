package com.mansaketh.chitta.ui.plan

import com.mansaketh.chitta.core.design.kit.SheetHeader
import com.mansaketh.chitta.ui.DatePick
import com.mansaketh.chitta.core.design.kit.CategoryChip
import com.mansaketh.chitta.core.design.kit.CheckRow
import com.mansaketh.chitta.core.design.kit.ValueRow
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
import com.mansaketh.chitta.core.design.kit.ButtonPair
import com.mansaketh.chitta.ui.sheetMax
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.Period
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.BottomOverlay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment

private val dFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dyFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun dateText(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().format(dyFmt)

/** "1 Nov to 30 Nov" (the end is exclusive in storage, so one day is taken off for display). */
fun periodText(p: Period, zone: ZoneId): String {
    val a = Periods.date(p.startMs, zone)
    val b = Periods.date(p.endMs, zone).minusDays(1)
    return if (a.year == b.year) "${a.format(dFmt)} to ${b.format(dFmt)}" else "${a.format(dyFmt)} to ${b.format(dyFmt)}"
}

fun rupees(p: Paise) = IndianFormat.rupees(p)

/** Opens the app's date sheet (D283). Keeps the time of day of [ms]; [onPick] gets the new moment in milliseconds. */
fun pickDate(ms: Long, zone: ZoneId, onPick: (Long) -> Unit, onCancel: () -> Unit = {}) {
    val t = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
    DatePick.request = DatePick.Request(
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), zone,
        { d -> onPick(d.atTime(t).atZone(zone).toInstant().toEpochMilli()) }, onCancel,
    )
}

/** An amount and an account in one sheet: contributions, repayments, collecting part, paying a bill a different amount. */
@Composable
fun AmountAccountSheet(
    title: String, amountLabel: String, accounts: List<Account>, initialAccountId: String?, initialAmount: String = "", confirmLabel: String,
    note: String? = null, onSave: (amount: String, accountId: String, problem: (String) -> Unit) -> Unit, onDismiss: () -> Unit,
) {
    val active = accounts.filter { !it.archived }
    var amount by remember { mutableStateOf(initialAmount) }
    var account by remember { mutableStateOf(initialAccountId?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id ?: "") }
    var problem by remember { mutableStateOf<String?>(null) }
    BottomOverlay(onDismiss, handle = false) {
        SheetHeader(title, onClose = onDismiss)
        if (note != null) Text(note, style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(top = Dimens.Gap1))
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaField(amount, { amount = it; problem = null }, problem ?: amountLabel, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        Spacer(Modifier.height(Dimens.Gap3))
        Text("Account", style = ChittaTheme.type.labelMedium, color = ChittaTheme.colors.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        LazyColumn(Modifier.heightIn(max = sheetMax(0.2f))) {
            items(active) { a -> CategoryChip(a.name, selected = a.id == account, onClick = { account = a.id }, modifier = Modifier.padding(bottom = Dimens.Gap1)) }
        }
        Spacer(Modifier.height(Dimens.Gap4))
        // Cancel and the confirm button side by side (PDF page 30); only the confirm is filled.
        ButtonPair(
            first = { m -> ChittaButton("Cancel", onDismiss, m, kind = ButtonKind.Secondary) },
            second = { m -> ChittaButton(confirmLabel, { onSave(amount, account) { problem = it } }, m, confirmHaptic = true) },
        )
    }
}

/** A list where several can be ticked (accounts and categories a budget leaves out). [selected] empty means "all". */
@Composable
fun MultiPickSheet(title: String, options: List<Pair<String, String>>, selected: Set<String>, allLabel: String, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(selected) }
    BottomOverlay(onDismiss, handle = false) {
        SheetHeader(title)
        Text(if (picked.isEmpty()) allLabel else "${picked.size} chosen", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        LazyColumn(Modifier.heightIn(max = sheetMax(0.45f))) {
            items(options) { (id, label) ->
                CheckRow(label, id in picked, { picked = if (it) picked + id else picked - id })
            }
        }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Done", { onDone(picked) }, confirmHaptic = true, modifier = Modifier.fillMaxWidth())
    }
}

/** Repeat unit chips plus "every N" and an end date. Shared by the bill sheet and the entry detail's Repeat row. */
@Composable
fun RepeatFields(repeat: Repeat?, onRepeat: (Repeat?) -> Unit, length: String, onLength: (String) -> Unit, endMs: Long?, onEnd: () -> Unit, onClearEnd: () -> Unit, zone: ZoneId) {
    Text("Repeats", style = ChittaTheme.type.labelMedium, color = ChittaTheme.colors.inkMuted)
    Spacer(Modifier.height(Dimens.Gap2))
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
        CategoryChip("Never", repeat == null, { onRepeat(null) })
        CategoryChip("Daily", repeat == Repeat.DAILY, { onRepeat(Repeat.DAILY) })
        CategoryChip("Weekly", repeat == Repeat.WEEKLY, { onRepeat(Repeat.WEEKLY) })
    }
    Spacer(Modifier.height(Dimens.Gap2))
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
        CategoryChip("Monthly", repeat == Repeat.MONTHLY, { onRepeat(Repeat.MONTHLY) })
        CategoryChip("Yearly", repeat == Repeat.YEARLY, { onRepeat(Repeat.YEARLY) })
    }
    if (repeat != null) {
        Spacer(Modifier.height(Dimens.Gap3))
        ChittaField(length, onLength, "Every how many (1 is every ${repeatUnit(repeat)})", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        ValueRow("Ends", endMs?.let { dateText(it, zone) } ?: "Never", onClick = onEnd)
        if (endMs != null) ChittaButton("Remove end date", onClearEnd, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

fun repeatUnit(r: Repeat) = when (r) { Repeat.DAILY -> "day"; Repeat.WEEKLY -> "week"; Repeat.MONTHLY -> "month"; Repeat.YEARLY -> "year"; Repeat.CUSTOM -> "period" }

/**
 * Pay a bill (PDF p.30): Amount as three choices (Full, Minimum when the card has one, Other typed), a Paid from row, a Paid on row and
 * Cancel / Confirm payment. "Paid on" opens the date sheet (today unless changed). A bill due on a credit card ([cardName]) is paid from
 * another account and saved as a transfer, and says so. [onConfirm] gets the typed amount (null = the bill's usual amount), the account
 * (null = the bill's own) and the chosen day (null = today, so the usual paid-date setting applies), and says what is wrong through the callback.
 */
@Composable
fun PaySheet(
    title: String, full: Paise, minimum: Paise?, accounts: List<Account>, initialAccountId: String?, today: java.time.LocalDate, zone: java.time.ZoneId,
    cardName: String?, note: String?,
    onConfirm: (amount: String?, accountId: String?, paidOnMs: Long?, problem: (String) -> Unit) -> Unit, onDismiss: () -> Unit,
) {
    val c = ChittaTheme.colors
    val active = accounts.filter { !it.archived }
    var choice by remember { mutableStateOf(0) } // 0 Full, 1 Minimum, 2 Other
    var other by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(initialAccountId?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id ?: "") }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var paidOn by remember { mutableStateOf(today) }
    val paidOnText = (if (paidOn == today) "Today, " else if (paidOn == today.minusDays(1)) "Yesterday, " else "") + paidOn.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))
    fun plain(p: Paise) = IndianFormat.rupees(p, symbol = "").replace(",", "")
    if (picking) {
        ChoiceSheet("Paid from", active.map { Choice(it.name, it.id, selected = it.id == account) }, { account = it; picking = false }, { picking = false })
        return
    }
    BottomOverlay(onDismiss, handle = false) {
        SheetHeader(title, onClose = onDismiss)
        Spacer(Modifier.height(Dimens.Gap3))
        Text("Amount", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
            val tiles = buildList { add(0 to "Full"); if (minimum != null) add(1 to "Minimum"); add(2 to "Other") }
            tiles.forEach { (i, label) ->
                val on = choice == i
                val shape = com.mansaketh.chitta.core.design.tokens.Shapes.Control
                Column(
                    Modifier.weight(1f).clip(shape).background(if (on) c.ink else androidx.compose.ui.graphics.Color.Transparent)
                        .border(Dimens.Hairline, if (on) c.ink else c.rule, shape)
                        .pressClickable(role = androidx.compose.ui.semantics.Role.RadioButton) { choice = i; problem = null }
                        .padding(vertical = Dimens.Gap3, horizontal = Dimens.Gap2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = ChittaTheme.type.label, color = if (on) c.background else c.inkMuted)
                    when (i) {
                        0 -> AmountText(full, style = ChittaTheme.type.amountRow, color = if (on) c.background else c.ink)
                        1 -> AmountText(minimum!!, style = ChittaTheme.type.amountRow, color = if (on) c.background else c.ink)
                        else -> Text(if (other.isBlank()) "₹ …" else "₹$other", style = ChittaTheme.type.amountRow, color = if (on) c.background else c.ink)
                    }
                }
            }
        }
        if (choice == 2) {
            Spacer(Modifier.height(Dimens.Gap2))
            ChittaField(other, { other = it; problem = null }, problem ?: "Amount paid", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        }
        Spacer(Modifier.height(Dimens.Gap2))
        ValueRow("Paid from", active.firstOrNull { it.id == account }?.name ?: "", onClick = { picking = true })
        // PDF p.30: the date opens the app's own date sheet; the sheet is drawn over this one by DatePickHost.
        ValueRow("Paid on", paidOnText, onClick = { DatePick.request = DatePick.Request(paidOn, zone, { paidOn = it }, {}) })
        if (cardName != null) FootNote("Saved as a transfer from ${active.firstOrNull { it.id == account }?.name ?: "your account"} to $cardName, so it isn\u2019t counted as spending twice.")
        else if (note != null) FootNote(note)
        Spacer(Modifier.height(Dimens.Gap3))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap3)) {
            ChittaButton("Cancel", onDismiss, Modifier.weight(0.8f).fillMaxHeight(), kind = ButtonKind.Secondary)
            ChittaButton("Confirm payment", {
                val amt = when (choice) { 1 -> plain(minimum!!); 2 -> other; else -> null }
                val paidOnMs = if (paidOn == today) null else paidOn.atTime(java.time.LocalTime.now(zone)).atZone(zone).toInstant().toEpochMilli()
                if (choice == 2 && other.isBlank()) problem = "Enter an amount above zero." else onConfirm(amt, account.takeIf { it != initialAccountId }, paidOnMs) { problem = it }
            }, Modifier.weight(1.2f).fillMaxHeight(), confirmHaptic = true)
        }
    }
}
