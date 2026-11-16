package com.mansaketh.chitta.ui.plan

import com.mansaketh.chitta.core.design.kit.SheetHeader
import com.mansaketh.chitta.ui.DatePick
import android.app.DatePickerDialog
import com.mansaketh.chitta.core.design.kit.CategoryChip
import com.mansaketh.chitta.core.design.kit.CheckRow
import com.mansaketh.chitta.core.design.kit.ValueRow
import com.mansaketh.chitta.core.design.kit.ButtonKind
import com.mansaketh.chitta.core.design.kit.ChittaButton
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
        SheetHeader(title)
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
        ChittaButton(confirmLabel, { onSave(amount, account) { problem = it } }, confirmHaptic = true, modifier = Modifier.fillMaxWidth())
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
