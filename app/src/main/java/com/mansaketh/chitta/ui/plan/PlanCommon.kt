package com.mansaketh.chitta.ui.plan

import android.app.DatePickerDialog
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
import com.mansaketh.chitta.ui.ChoiceChip
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

/** The system date picker. Keeps the time of day of [ms]. */
fun pickDate(context: android.content.Context, ms: Long, zone: ZoneId, onPick: (Long) -> Unit, onCancel: () -> Unit = {}) {
    val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    DatePickerDialog(context, { _, y, m, day ->
        val t = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
        onPick(LocalDate.of(y, m + 1, day).atTime(t).atZone(zone).toInstant().toEpochMilli())
    }, d.year, d.monthValue - 1, d.dayOfMonth).apply { setOnCancelListener { onCancel() } }.show()
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
    BottomOverlay(onDismiss) {
        Text(title, style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        if (note != null) Text(note, style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(top = Dimens.Gap1))
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaField(amount, { amount = it; problem = null }, problem ?: amountLabel, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        Spacer(Modifier.height(Dimens.Gap3))
        Text("Account", style = ChittaTheme.type.labelMedium, color = ChittaTheme.colors.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        LazyColumn(Modifier.heightIn(max = 160.dp)) {
            items(active) { a -> ChoiceChip(a.name, selected = a.id == account, onClick = { account = a.id }, modifier = Modifier.padding(bottom = Dimens.Gap1)) }
        }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton(confirmLabel, { onSave(amount, account) { problem = it } }, confirmHaptic = true)
    }
}

/** A list where several can be ticked (accounts and categories a budget leaves out). [selected] empty means "all". */
@Composable
fun MultiPickSheet(title: String, options: List<Pair<String, String>>, selected: Set<String>, allLabel: String, onDone: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(selected) }
    BottomOverlay(onDismiss) {
        Text(title, style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Text(if (picked.isEmpty()) allLabel else "${picked.size} chosen", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(options) { (id, label) ->
                com.mansaketh.chitta.ui.ValueRow(label, if (id in picked) "Chosen" else "", onClick = { picked = if (id in picked) picked - id else picked + id })
            }
        }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Done", { onDone(picked) }, confirmHaptic = true)
    }
}

/** Repeat unit chips plus "every N" and an end date. Shared by the bill sheet and the entry detail's Repeat row. */
@Composable
fun RepeatFields(repeat: Repeat?, onRepeat: (Repeat?) -> Unit, length: String, onLength: (String) -> Unit, endMs: Long?, onEnd: () -> Unit, onClearEnd: () -> Unit, zone: ZoneId) {
    Text("Repeats", style = ChittaTheme.type.labelMedium, color = ChittaTheme.colors.inkMuted)
    Spacer(Modifier.height(Dimens.Gap2))
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
        ChoiceChip("Never", repeat == null, { onRepeat(null) })
        ChoiceChip("Daily", repeat == Repeat.DAILY, { onRepeat(Repeat.DAILY) })
        ChoiceChip("Weekly", repeat == Repeat.WEEKLY, { onRepeat(Repeat.WEEKLY) })
    }
    Spacer(Modifier.height(Dimens.Gap2))
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
        ChoiceChip("Monthly", repeat == Repeat.MONTHLY, { onRepeat(Repeat.MONTHLY) })
        ChoiceChip("Yearly", repeat == Repeat.YEARLY, { onRepeat(Repeat.YEARLY) })
    }
    if (repeat != null) {
        Spacer(Modifier.height(Dimens.Gap3))
        ChittaField(length, onLength, "Every how many (1 is every ${repeatUnit(repeat)})", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        com.mansaketh.chitta.ui.ValueRow("Ends", endMs?.let { dateText(it, zone) } ?: "Never", onClick = onEnd)
        if (endMs != null) ChittaButton("Remove end date", onClearEnd, style = ButtonStyle.Quiet)
    }
}

fun repeatUnit(r: Repeat) = when (r) { Repeat.DAILY -> "day"; Repeat.WEEKLY -> "week"; Repeat.MONTHLY -> "month"; Repeat.YEARLY -> "year"; Repeat.CUSTOM -> "period" }
