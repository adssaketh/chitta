package com.mansaketh.chitta.ui.plan

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.*
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.*
import com.mansaketh.chitta.core.model.*
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.reminders.ReminderNotifications
import com.mansaketh.chitta.ui.*
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private fun plain(p: Paise) = IndianFormat.rupees(p, symbol = "").replace(",", "")
private val Num = KeyboardOptions(keyboardType = KeyboardType.Decimal)

// ---------------------------------------------------------------- budget (Budget detail, PDF p.19)
private enum class BStep { Form, Accounts, Categories }

@Composable
fun BudgetSheet(existing: Budget?, ui: PlanUi, onSave: (BudgetForm) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val zone = ui.zone
    val today = remember { Periods.date(ui.nowMs, zone) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.let { plain(Paise(it.amount)) } ?: "") }
    var repeat by remember { mutableStateOf(if (existing == null) Repeat.MONTHLY else existing.repeat?.takeIf { it != Repeat.CUSTOM }) }
    var startMs by remember { mutableStateOf(existing?.startMs ?: Periods.startOfDay(today.withDayOfMonth(1), zone)) }
    var endMs by remember { mutableStateOf(existing?.endMs ?: Periods.startOfDay(today.withDayOfMonth(1).plusMonths(1), zone)) }
    var savings by remember { mutableStateOf(existing?.income ?: false) }
    var debt by remember { mutableStateOf(existing?.let { Budgets.filterOn(it.filters, BudgetFilter.INCLUDE_DEBT_AND_CREDIT) } ?: false) }
    var absolute by remember { mutableStateOf(existing?.absoluteSpendingLimit ?: false) }
    var rollover by remember { mutableStateOf(existing?.rollover ?: false) }
    var accounts by remember { mutableStateOf(existing?.accountIds?.toSet() ?: emptySet()) }
    var excluded by remember { mutableStateOf(existing?.excludedCategoryIds?.toSet() ?: emptySet()) }
    var step by remember { mutableStateOf(BStep.Form) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun startFor(r: Repeat?): Long = when (r) {
        Repeat.WEEKLY -> Periods.startOfDay(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), zone)
        Repeat.YEARLY -> Periods.startOfDay(today.withDayOfYear(1), zone)
        else -> Periods.startOfDay(today.withDayOfMonth(1), zone)
    }

    when (step) {
        BStep.Accounts -> MultiPickSheet("Accounts", ui.accounts.filter { !it.archived }.map { it.id to it.name }, accounts, "All accounts", { accounts = it; step = BStep.Form }, { step = BStep.Form })
        BStep.Categories -> MultiPickSheet("Categories it leaves out", ui.categories.filter { it.mainCategoryId == null && !it.archived && it.income == savings }.map { it.id to it.name }, excluded, "None left out", { excluded = it; step = BStep.Form }, { step = BStep.Form })
        BStep.Form -> BottomOverlay(onDismiss) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(if (existing == null) "New budget" else "Edit budget", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
                Spacer(Modifier.height(Dimens.Gap4))
                ChittaField(name, { name = it; problem = null }, "Name")
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(amount, { amount = it; problem = null }, problem ?: "Amount for each period", keyboardOptions = Num)
                Spacer(Modifier.height(Dimens.Gap3))
                Text("Period", style = ChittaTheme.type.labelMedium, color = ChittaTheme.colors.inkMuted)
                Spacer(Modifier.height(Dimens.Gap2))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                    listOf(Repeat.MONTHLY to "Monthly", Repeat.WEEKLY to "Weekly", Repeat.YEARLY to "Yearly").forEach { (r, l) ->
                        ChoiceChip(l, repeat == r, { repeat = r; startMs = startFor(r) })
                    }
                    ChoiceChip("One time", repeat == null, { repeat = null })
                }
                if (repeat == null) {
                    ValueRow("Starts", dateText(startMs, zone), onClick = { pickDate(context, startMs, zone, { startMs = Periods.startOfDay(Periods.date(it, zone), zone) }) })
                    ValueRow("Ends", dateText(endMs - 1, zone), onClick = { pickDate(context, endMs - 1, zone, { endMs = Periods.startOfDay(Periods.date(it, zone).plusDays(1), zone) }) })
                }
                Spacer(Modifier.height(Dimens.Gap3))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                    ChoiceChip("Spending", !savings, { savings = false; excluded = emptySet() })
                    ChoiceChip("Savings", savings, { savings = true; excluded = emptySet() })
                }
                Text("More options", style = ChittaTheme.type.labelMedium, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(top = Dimens.Gap4))
                ValueRow("Accounts", if (accounts.isEmpty()) "All" else "${accounts.size} chosen", onClick = { step = BStep.Accounts })
                ValueRow("Leaves out", if (excluded.isEmpty()) "Nothing" else "${excluded.size} categories", onClick = { step = BStep.Categories })
                ValueRow("Count lent and borrowed", if (debt) "On" else "Off", onClick = { debt = !debt })
                ValueRow("Category goals are", if (absolute) "Amounts" else "Percentages", onClick = { absolute = !absolute })
                if (!savings && repeat != null) {
                    ValueRow("Carry over what is left", if (rollover) "On" else "Off", onClick = { rollover = !rollover })
                    Text("On: money left unspent is added to the next period, and an overspend is taken off it. Starts from this budget's first period.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(top = Dimens.Gap1))
                }
                if (existing?.addedTransactionsOnly == true) Text("This was an \"added only\" budget in Paddhu Pusthakam. Chitta cannot tell which entries were added to it, so it counts every matching entry.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
                Spacer(Modifier.height(Dimens.Gap4))
                ChittaButton("Save", {
                    val p = AmountExpression.evaluate(amount)
                    when {
                        name.isBlank() -> problem = "Give it a name"
                        p == null || p.isZero -> problem = "Enter an amount above zero"
                        repeat == null && endMs <= startMs -> problem = "The end must be after the start"
                        else -> onSave(BudgetForm(name, p, repeat, startMs, endMs, savings, debt, accounts.toList().ifEmpty { null }, excluded.toList().ifEmpty { null }, absolute, rollover && !savings && repeat != null))
                    }
                }, confirmHaptic = true)
            }
        }
    }
}

// ---------------------------------------------------------------- goal (PDF p.20)
@Composable
fun GoalSheet(existing: Objective?, zone: ZoneId, nowMs: Long, onSave: (name: String, target: Paise, income: Boolean, endMs: Long?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.let { plain(Paise(it.amount)) } ?: "") }
    var saving by remember { mutableStateOf(existing?.income ?: true) }
    var endMs by remember { mutableStateOf(existing?.endDateMs) }
    var problem by remember { mutableStateOf<String?>(null) }
    BottomOverlay(onDismiss) {
        Text(if (existing == null) "New goal" else "Edit goal", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaField(name, { name = it; problem = null }, "Name")
        Spacer(Modifier.height(Dimens.Gap3))
        ChittaField(target, { target = it; problem = null }, problem ?: "Target amount", keyboardOptions = Num)
        if (existing == null) {
            Spacer(Modifier.height(Dimens.Gap3))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                ChoiceChip("Saving up", saving, { saving = true }); ChoiceChip("Spending on", !saving, { saving = false })
            }
        }
        ValueRow("Finish by", endMs?.let { dateText(it, zone) } ?: "No date", onClick = { pickDate(context, endMs ?: nowMs, zone, { endMs = it }) })
        if (endMs != null) ChittaButton("Remove the date", { endMs = null }, style = ButtonStyle.Quiet)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", {
            val p = AmountExpression.evaluate(target)
            when {
                name.isBlank() -> problem = "Give it a name"
                p == null || p.isZero -> problem = "Enter a target above zero"
                else -> onSave(name, p, saving, endMs)
            }
        }, confirmHaptic = true)
    }
}

// ---------------------------------------------------------------- loan (PDF p.21)
@Composable
fun LoanSheet(existing: Objective?, ui: PlanUi, onAdd: (name: String, lent: Boolean, initial: Paise, offset: Paise, accountId: String, problem: (String) -> Unit) -> Boolean, onRename: (name: String, offset: Paise) -> Unit, onInstalment: (name: String, lent: Boolean, terms: (String) -> LoanTerms) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val zone = ui.zone
    val accounts = ui.accounts.filter { !it.archived }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var lent by remember { mutableStateOf(existing?.income ?: false) }
    var amount by remember { mutableStateOf("") }
    var offset by remember { mutableStateOf(existing?.let { if (it.amount > 0) plain(Paise(it.amount)) else "" } ?: "") }
    var account by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var instalments by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    var emi by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var gst by remember { mutableStateOf("18") }
    var firstMs by remember { mutableStateOf(ui.nowMs) }
    var problem by remember { mutableStateOf<String?>(null) }
    BottomOverlay(onDismiss) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Text(if (existing == null) "New loan" else "Edit loan", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaField(name, { name = it; problem = null }, problem ?: "Name")
            if (existing == null) {
                Spacer(Modifier.height(Dimens.Gap3))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                    ChoiceChip("I borrowed", !lent, { lent = false }); ChoiceChip("I lent", lent, { lent = true })
                }
                Spacer(Modifier.height(Dimens.Gap2))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                    ChoiceChip("Pay back any time", !instalments, { instalments = false }); ChoiceChip("Fixed instalments", instalments, { instalments = true })
                }
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(amount, { amount = it; problem = null }, if (instalments) "Amount borrowed (principal)" else "Amount ${if (lent) "lent" else "borrowed"}", keyboardOptions = Num)
            }
            if (!instalments) {
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(offset, { offset = it }, "Interest or fees on top (optional)", keyboardOptions = Num)
            } else {
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(rate, { rate = it }, "Interest rate a year (%)", keyboardOptions = Num)
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(months, { months = it }, "Number of months", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                val suggestion = remember(amount, rate, months) {
                    val p = AmountExpression.evaluate(amount); val m = months.toIntOrNull()
                    if (p != null && m != null && m > 0) runCatching { Instalments.suggestedEmi(p, rate.ifBlank { "0" }, m) }.getOrNull() else null
                }
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(emi, { emi = it }, if (suggestion != null) "Instalment (the formula gives ${IndianFormat.rupees(suggestion)})" else "Instalment each month", keyboardOptions = Num)
                ValueRow("First instalment", dateText(firstMs, zone), onClick = { pickDate(context, firstMs, zone, { firstMs = it }) })
                Spacer(Modifier.height(Dimens.Gap2))
                ChittaField(fee, { fee = it }, "Processing fee (optional)", keyboardOptions = Num)
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(gst, { gst = it }, "GST on interest and fee (%)", keyboardOptions = Num)
            }
            if (existing == null && !instalments) {
                Spacer(Modifier.height(Dimens.Gap3))
                Text("Account", style = ChittaTheme.type.labelMedium, color = ChittaTheme.colors.inkMuted)
                Spacer(Modifier.height(Dimens.Gap2))
                Column { accounts.forEach { a -> ChoiceChip(a.name, a.id == account, { account = a.id }, modifier = Modifier.padding(bottom = Dimens.Gap1)) } }
            }
            Spacer(Modifier.height(Dimens.Gap4))
            ChittaButton("Save", {
                val off = if (offset.isBlank()) Paise.ZERO else AmountExpression.evaluate(offset)
                when {
                    name.isBlank() -> problem = "Give the loan a name"
                    existing != null -> if (off == null) problem = "Enter the extra amount as a number" else onRename(name, off)
                    instalments -> {
                        val principal = AmountExpression.evaluate(amount); val m = months.toIntOrNull(); val e = AmountExpression.evaluate(emi)
                        val feeP = if (fee.isBlank()) Paise.ZERO else AmountExpression.evaluate(fee)
                        when {
                            principal == null || principal.isZero -> problem = "Enter the amount borrowed"
                            m == null || m <= 0 -> problem = "Enter the number of months"
                            e == null || e.isZero -> problem = "Enter the monthly instalment"
                            feeP == null -> problem = "Enter the fee as a number"
                            else -> onInstalment(name, lent) { id -> LoanTerms(id, principal.value, rate.ifBlank { "0" }, m, e.value, firstMs, feeP.value, gst.ifBlank { "18" }, false, emptyList(), accounts.firstOrNull()?.id, "", null) }
                        }
                    }
                    else -> {
                        val initial = AmountExpression.evaluate(amount)
                        if (initial == null || off == null) problem = "Enter the amounts as numbers" else onAdd(name, lent, initial, off, account) { problem = it }
                    }
                }
            }, confirmHaptic = true)
        }
    }
}

// ---------------------------------------------------------------- bill (PDF p.23, Pay a bill, and the Upcoming list)
private enum class PStep { Form, Category, Account }

@Composable
fun BillSheet(existing: Transaction?, ui: PlanUi, onSave: (title: String, amount: Paise, income: Boolean, accountId: String, categoryId: String, dueMs: Long, repeat: Repeat?, length: Int, endMs: Long?, notify: Boolean, subscription: Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val zone = ui.zone
    var title by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.takeIf { it.amount != 0L }?.let { plain(Paise(Math.abs(it.amount))) } ?: "") }
    var income by remember { mutableStateOf(existing?.income ?: false) }
    var accountId by remember { mutableStateOf(existing?.accountId ?: ui.accounts.firstOrNull { !it.archived }?.id ?: "") }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: "") }
    var dueMs by remember { mutableStateOf(existing?.let { Bills.dueMs(it) } ?: ui.nowMs) }
    var repeat by remember { mutableStateOf(existing?.repeat?.takeIf { it != Repeat.CUSTOM }) }
    var length by remember { mutableStateOf((existing?.periodLength ?: 1).toString()) }
    var endMs by remember { mutableStateOf(existing?.endDateMs) }
    var notify by remember { mutableStateOf(existing?.upcomingNotification != false) }
    var subscription by remember { mutableStateOf(existing?.kind == TransactionKind.SUBSCRIPTION) }
    var step by remember { mutableStateOf(PStep.Form) }
    var problem by remember { mutableStateOf<String?>(null) }
    val categories = ui.categories.filter { it.mainCategoryId == null && !it.archived && it.income == income }

    when (step) {
        PStep.Category -> ChoiceSheet("Category", categories.map { Choice(it.name, it.id, selected = it.id == categoryId) }, { categoryId = it; step = PStep.Form }, { step = PStep.Form })
        PStep.Account -> ChoiceSheet("Account", ui.accounts.filter { !it.archived }.map { Choice(it.name, it.id, selected = it.id == accountId) }, { accountId = it; step = PStep.Form }, { step = PStep.Form })
        PStep.Form -> BottomOverlay(onDismiss) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(if (existing == null) "New bill" else "Edit bill", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
                Spacer(Modifier.height(Dimens.Gap4))
                ChittaField(title, { title = it; problem = null }, problem ?: "What is it")
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(amount, { amount = it }, "Amount (leave empty if it changes each time)", keyboardOptions = Num)
                Spacer(Modifier.height(Dimens.Gap3))
                if (existing == null) Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2)) {
                    ChoiceChip("To pay", !income, { income = false; categoryId = "" }); ChoiceChip("To receive", income, { income = true; categoryId = "" })
                }
                ValueRow("Category", categoryName(categoryId, ui.categories).takeIf { categoryId.isNotEmpty() } ?: "Choose", onClick = { step = PStep.Category })
                ValueRow("Account", accountName(accountId, ui.accounts), onClick = { step = PStep.Account })
                ValueRow("Due", dateText(dueMs, zone), onClick = { pickDate(context, dueMs, zone, { dueMs = it }) })
                Spacer(Modifier.height(Dimens.Gap3))
                RepeatFields(repeat, { repeat = it }, length, { length = it }, endMs, { pickDate(context, endMs ?: dueMs, zone, { endMs = it }) }, { endMs = null }, zone)
                if (repeat != null) ValueRow("Is a subscription", if (subscription) "Yes" else "No", onClick = { subscription = !subscription })
                ValueRow("Remind me", if (notify) "On" else "Off", onClick = { notify = !notify })
                Spacer(Modifier.height(Dimens.Gap4))
                ChittaButton("Save", {
                    val p = if (amount.isBlank()) Paise.ZERO else AmountExpression.evaluate(amount)
                    when {
                        title.isBlank() -> problem = "Say what it is"
                        p == null -> problem = "Enter the amount as a number"
                        categoryId.isEmpty() -> problem = "Choose a category"
                        accountId.isEmpty() -> problem = "Add an account first"
                        else -> onSave(title, p, income, accountId, categoryId, dueMs, repeat, length.toIntOrNull() ?: 1, endMs, notify, subscription)
                    }
                }, confirmHaptic = true)
            }
        }
    }
}

/** The "Repeat" row on entry detail: makes an ordinary entry repeat, or stops it. */
@Composable
fun RepeatSheet(entry: Transaction, zone: ZoneId, onSave: (Repeat?, Int, Long?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var repeat by remember { mutableStateOf(entry.repeat?.takeIf { it != Repeat.CUSTOM }) }
    var length by remember { mutableStateOf((entry.periodLength ?: 1).toString()) }
    var endMs by remember { mutableStateOf(entry.endDateMs) }
    BottomOverlay(onDismiss) {
        Text("Repeat", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
        Text("The next one is made when this one is paid, or when you skip it.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted)
        Spacer(Modifier.height(Dimens.Gap4))
        RepeatFields(repeat, { repeat = it }, length, { length = it }, endMs, { pickDate(context, endMs ?: entry.dateMs, zone, { endMs = it }) }, { endMs = null }, zone)
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", { onSave(repeat, length.toIntOrNull() ?: 1, endMs) }, confirmHaptic = true)
    }
}

// ---------------------------------------------------------------- reminders (master section 5.7)
@Composable
fun ReminderSheet(s: ReminderSettings, autoPay: Boolean, paidOnDue: Boolean, askedBefore: Boolean, onAsked: () -> Unit, onReminders: (ReminderSettings) -> Unit, onAutoPay: (Boolean) -> Unit, onPaidOnDue: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ReminderNotifications.canPost(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> allowed = ok; onAsked() }
    fun time(h: Int, m: Int, set: (Int, Int) -> Unit) { TimePickerDialog(context, { _, hh, mm -> set(hh, mm) }, h, m, false).show() }
    fun clock(h: Int, m: Int) = (if (h % 12 == 0) 12 else h % 12).toString() + ":" + m.toString().padStart(2, '0') + if (h < 12) " am" else " pm"
    fun wantsOn() { if (!allowed && android.os.Build.VERSION.SDK_INT >= 33 && !askedBefore) ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    BottomOverlay(onDismiss) {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Text("Bills and reminders", style = ChittaTheme.type.title, color = ChittaTheme.colors.ink)
            Spacer(Modifier.height(Dimens.Gap3))
            if (!allowed && (s.dueBills || s.logReminder)) {
                Text("Chitta is not allowed to show notifications, so these reminders will stay silent. You can allow them in Android settings.", style = ChittaTheme.type.body, color = ChittaTheme.colors.warningText)
                Spacer(Modifier.height(Dimens.Gap2))
                ChittaButton("Open notification settings", { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, style = ButtonStyle.Quiet)
                Spacer(Modifier.height(Dimens.Gap2))
            }
            ValueRow("Remind me about due bills", if (s.dueBills) "On" else "Off", onClick = { onReminders(s.copy(dueBills = !s.dueBills)); if (!s.dueBills) wantsOn() })
            if (s.dueBills) ValueRow("At", clock(s.billHour, s.billMinute), onClick = { time(s.billHour, s.billMinute) { h, m -> onReminders(s.copy(billHour = h, billMinute = m)) } })
            Text("You hear about a bill the day before and on the day it is due.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(bottom = Dimens.Gap3))
            ValueRow("Remind me to log", if (s.logReminder) "On" else "Off", onClick = { onReminders(s.copy(logReminder = !s.logReminder)); if (!s.logReminder) wantsOn() })
            if (s.logReminder) ValueRow("At", clock(s.logHour, s.logMinute), onClick = { time(s.logHour, s.logMinute) { h, m -> onReminders(s.copy(logHour = h, logMinute = m)) } })
            Spacer(Modifier.height(Dimens.Gap4))
            ValueRow("Pay overdue repeating bills by themselves", if (autoPay) "On" else "Off", onClick = { onAutoPay(!autoPay) })
            ValueRow("A paid bill is dated", if (paidOnDue) "Its due date" else "The day you pay", onClick = { onPaidOnDue(!paidOnDue) })
            Text("Digest notifications are off. Card due reminders come with the card screen in a later phase.", style = ChittaTheme.type.label, color = ChittaTheme.colors.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
        }
    }
}
