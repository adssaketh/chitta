package com.mansaketh.chitta.ui.manage

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.Stamp
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.AmountExpression
import com.mansaketh.chitta.core.domain.FixedDeposits
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.ConfirmSheet
import com.mansaketh.chitta.ui.TextSheet
import com.mansaketh.chitta.ui.ValueRow
import com.mansaketh.chitta.ui.plan.dateText
import com.mansaketh.chitta.ui.plan.pickDate
import java.math.BigDecimal
import java.time.ZoneId

/** An amount as text for a label or a row value. Hide-amounts mode masks it, like AmountText does. */
@Composable
fun maskedRupees(p: Paise): String = if (ChittaTheme.hideAmounts) "₹ ••••" else IndianFormat.rupees(p)

private fun plain(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

private fun money(text: String): Long? = if (text.isBlank()) null else AmountExpression.evaluate(text)?.value

/** A short line for a deposit row: rate, maturity, and how far away it is. */
fun depositLine(d: FixedDeposit, nowMs: Long, zone: ZoneId): String {
    val left = FixedDeposits.daysToMaturity(d, nowMs, zone)
    val rate = d.ratePct.toBigDecimalOrNull()?.let { if (it.signum() == 0) null else "${it.stripTrailingZeros().toPlainString()}% · " } ?: ""
    return when {
        d.closed -> "${rate}closed"
        left > 0 -> "${rate}matures ${dateText(d.maturityMs, zone)} · $left ${if (left == 1L) "day" else "days"}"
        else -> "${rate}matured ${dateText(d.maturityMs, zone)}. Mark it closed once the money has arrived."
    }
}

/**
 * One deposit in full. Today's value is the principal plus interest earned so far; when you typed the bank's maturity amount the figures
 * say so, because a calculated one can differ by a few rupees (master section 12: say what a number is made of).
 */
@Composable
fun DepositDetailSheet(d: FixedDeposit, nowMs: Long, zone: ZoneId, onEdit: () -> Unit, onToggleClosed: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    val bankFigure = FixedDeposits.isStatedByBank(d)
    BottomOverlay(onClose) {
        Text(d.bank + (if (d.fdNumber.isNotEmpty()) " · ${d.fdNumber}" else ""), style = ChittaTheme.type.title, color = c.ink)
        if (d.closed) Stamp("Closed") else if (FixedDeposits.hasMatured(d, nowMs, zone)) Stamp("Matured")
        if (d.fromSms && !d.closed) Text("Read from a bank message. Check the details.", style = ChittaTheme.type.label, color = c.inkMuted)
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Value today", maskedRupees(FixedDeposits.currentValue(d, nowMs, zone)))
        ValueRow("Interest so far", maskedRupees(FixedDeposits.accruedInterest(d, nowMs, zone)))
        ValueRow("Put in", maskedRupees(Paise(d.principal)))
        ValueRow("At maturity", maskedRupees(FixedDeposits.maturityValue(d, zone)) + if (bankFigure) " (bank)" else " (worked out)")
        ValueRow("Rate", (d.ratePct.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: d.ratePct) + "% a year")
        ValueRow("Interest added", FixedDeposits.COMPOUNDING_NAMES[d.compoundingPerYear] ?: "${d.compoundingPerYear} times a year")
        ValueRow("Booked", dateText(d.startMs, zone))
        ValueRow("Matures", dateText(d.maturityMs, zone))
        Text(
            "Worked out on actual days over 365" + (if (bankFigure) ", spread evenly up to the bank's maturity amount." else "; the bank's own figure can differ a little."),
            style = ChittaTheme.type.label, color = c.inkMuted,
        )
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Edit", onEdit)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton(if (d.closed) "Reopen" else "Mark closed", onToggleClosed, style = ButtonStyle.Quiet)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Delete", { confirmDelete = true }, style = ButtonStyle.Quiet)
    }
    if (confirmDelete) ConfirmSheet(
        "Delete this deposit?", listOf("It leaves the list and Net worth. No account balance changes. You can undo for 10 seconds."), "Delete",
        { confirmDelete = false; onDelete() }, { confirmDelete = false },
    )
}

private enum class DepField { Bank, Number, Principal, Rate, Start, Maturity, Compounding, Stated, Note }

/** Add or edit a deposit. Nothing is saved until the person taps Save, and the check says in plain words what is wrong. */
@Composable
fun DepositEditSheet(existing: FixedDeposit?, nowMs: Long, zone: ZoneId, onSave: (FixedDeposit) -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    val context = LocalContext.current
    val today = Periods.startOfDay(Periods.date(nowMs, zone), zone)
    var bank by remember { mutableStateOf(existing?.bank ?: "") }
    var number by remember { mutableStateOf(existing?.fdNumber ?: "") }
    var principal by remember { mutableStateOf(existing?.principal) }
    var rate by remember { mutableStateOf(existing?.ratePct ?: "") }
    var start by remember { mutableStateOf(existing?.startMs ?: today) }
    var maturity by remember { mutableStateOf(existing?.maturityMs ?: Periods.startOfDay(Periods.date(today, zone).plusYears(1), zone)) }
    var comp by remember { mutableStateOf(existing?.compoundingPerYear ?: 4) }
    var stated by remember { mutableStateOf(existing?.maturityAmount) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var editing by remember { mutableStateOf<DepField?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    BottomOverlay(onClose) {
        Text(if (existing == null) "New fixed deposit" else "Edit fixed deposit", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        HairlineRule()
        ValueRow("Bank", bank.ifBlank { "not set" }, onClick = { editing = DepField.Bank })
        ValueRow("Amount put in", principal?.let { maskedRupees(Paise(it)) } ?: "not set", onClick = { editing = DepField.Principal })
        ValueRow("Rate a year", if (rate.isBlank()) "not set" else "$rate%", onClick = { editing = DepField.Rate })
        ValueRow("Booked on", dateText(start, zone), onClick = { pickDate(context, start, zone, { start = Periods.startOfDay(Periods.date(it, zone), zone) }) })
        ValueRow("Matures on", dateText(maturity, zone), onClick = { pickDate(context, maturity, zone, { maturity = Periods.startOfDay(Periods.date(it, zone), zone) }) })
        ValueRow("Interest added", com.mansaketh.chitta.core.domain.FixedDeposits.COMPOUNDING_NAMES[comp] ?: "$comp a year", onClick = { editing = DepField.Compounding })
        ValueRow("Maturity amount from the bank", stated?.let { maskedRupees(Paise(it)) } ?: "not set", onClick = { editing = DepField.Stated })
        ValueRow("FD number", number.ifBlank { "not set" }, onClick = { editing = DepField.Number })
        ValueRow("Note", note.ifBlank { "none" }, onClick = { editing = DepField.Note })
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", {
            val p = principal
            val r = rate.trim().toBigDecimalOrNull()
            problem = when {
                bank.isBlank() -> "Say which bank it is with."
                p == null || p <= 0 -> "Enter the amount put in."
                r == null || r.signum() < 0 || r > BigDecimal(30) -> "Enter the rate as a number from 0 to 30."
                maturity <= start -> "The maturity date must be after the booking date."
                stated != null && stated!! < p -> "The bank's maturity amount is lower than the amount put in."
                else -> null
            }
            if (problem == null) onSave(
                FixedDeposit(
                    existing?.id ?: "", bank.trim(), number.trim(), p!!, r!!.stripTrailingZeros().toPlainString(), start, maturity, comp, stated,
                    existing?.accountId, existing?.closed ?: false, note.trim(), false, existing?.smsId ?: "", null,
                ),
            )
        }, confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Close", onClose, style = ButtonStyle.Quiet)
    }

    when (editing) {
        DepField.Bank -> TextSheet("Bank", "Name of the bank", bank, { bank = it; editing = null }, { editing = null })
        DepField.Number -> TextSheet("FD number", "Last digits are enough (empty to clear)", number, { number = it; editing = null }, { editing = null })
        DepField.Note -> TextSheet("Note", "Anything to remember (empty to clear)", note, { note = it; editing = null }, { editing = null }, singleLine = false)
        DepField.Principal -> TextSheet("Amount put in", "Amount", principal?.let { plain(it) } ?: "", {
            val v = money(it)
            if (v != null && v > 0) { principal = v; problem = null; editing = null } else problem = "The amount must be a number above zero."
        }, { editing = null }, numeric = true)
        DepField.Rate -> TextSheet("Rate a year", "Percent, for example 7.1", rate, {
            if (it.trim().toBigDecimalOrNull() != null) { rate = it.trim(); problem = null; editing = null } else problem = "The rate must be a number."
        }, { editing = null }, numeric = true)
        DepField.Stated -> TextSheet("Maturity amount from the bank", "Amount (empty to work it out)", stated?.let { plain(it) } ?: "", {
            if (it.isBlank()) { stated = null; problem = null; editing = null }
            else money(it)?.takeIf { v -> v > 0 }?.let { v -> stated = v; problem = null; editing = null } ?: run { problem = "That is not an amount." }
        }, { editing = null }, numeric = true)
        DepField.Compounding -> ChoiceSheet(
            "Interest added",
            FixedDeposits.COMPOUNDING_NAMES.map { (k, name) -> Choice(name, k, selected = k == comp) },
            { comp = it; editing = null }, { editing = null },
        )
        DepField.Start, DepField.Maturity, null -> Unit
    }
}
