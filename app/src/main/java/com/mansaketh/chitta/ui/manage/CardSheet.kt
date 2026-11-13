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
import com.mansaketh.chitta.core.design.components.AmountText
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.GaugeBar
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.AmountExpression
import com.mansaketh.chitta.core.domain.CardFigures
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.Segments
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.TextSheet
import com.mansaketh.chitta.ui.ValueRow
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class CardField { Limit, Statement, Due, Minimum, Remind }

private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * Credit-card cockpit (master section 8; section 12 for the maths). Figures first, then the five fields that drive them.
 * Outstanding is statement + unbilled - payments; the statement balance is never shown as outstanding. With no limit set
 * the utilisation line is not shown at all.
 */
@Composable
fun CardSheet(account: Account, figures: CardFigures?, onSave: (limit: Long?, statementDay: Int?, dueDay: Int?, minimumDue: Long?, remind: Int?) -> Unit, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    var limit by remember { mutableStateOf(account.creditLimit) }
    var statement by remember { mutableStateOf(account.statementDay) }
    var due by remember { mutableStateOf(account.dueDay) }
    var minimum by remember { mutableStateOf(account.minimumDue) }
    var remind by remember { mutableStateOf(account.remindDaysBefore) }
    var editing by remember { mutableStateOf<CardField?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    BottomOverlay(onClose) {
        Text(account.name, style = ChittaTheme.type.title, color = c.ink)
        if (figures == null) {
            Text("Set the statement day and the due day to see what is owed.", style = ChittaTheme.type.label, color = c.inkMuted)
        } else {
            Spacer(Modifier.height(Dimens.Gap2))
            Text("Outstanding", style = ChittaTheme.type.label, color = c.inkMuted)
            AmountText(figures.outstanding, style = ChittaTheme.type.amountHero, alwaysShowPaise = true)
            Text(
                "Statement ${IndianFormat.rupees(figures.statementBalance)} + new spending ${IndianFormat.rupees(figures.unbilled)} - paid since ${figures.statementEnd.format(dayFormat)} ${IndianFormat.rupees(figures.paymentsSinceStatement)}",
                style = ChittaTheme.type.label, color = c.inkMuted,
            )
            if (figures.credit.value > 0) Text("The card is ${IndianFormat.rupees(figures.credit)} in credit.", style = ChittaTheme.type.label, color = c.inkMuted)
            val bp = figures.utilisationBp
            val lim = limit
            val avail = figures.available
            if (bp != null && lim != null && avail != null) {
                Spacer(Modifier.height(Dimens.Gap3))
                GaugeBar(Segments.gauge(figures.outstanding, Paise(lim), 20), "${account.name} limit used")
                Text(
                    (if (figures.overLimit) "Over the limit by ${IndianFormat.rupees(Paise(-avail.value))}" else "${IndianFormat.rupees(avail)} available") + " · ${bp / 100}.${(bp % 100).toString().padStart(2, '0')}% used",
                    style = ChittaTheme.type.label, color = if (figures.overLimit) c.warningText else c.inkMuted,
                )
            }
            Spacer(Modifier.height(Dimens.Gap2))
            val minText = figures.minimumDue?.let { IndianFormat.rupees(it) } ?: "not set"
            Text("Due ${figures.nextDue.format(dayFormat)}, in ${figures.daysToDue} ${if (figures.daysToDue == 1L) "day" else "days"} · minimum due $minText", style = ChittaTheme.type.body, color = c.ink)
        }
        Spacer(Modifier.height(Dimens.Gap3))
        HairlineRule()
        ValueRow("Credit limit", limit?.let { IndianFormat.rupees(Paise(it)) } ?: "not set", onClick = { editing = CardField.Limit })
        ValueRow("Statement day", statement?.let { "day $it of the month" } ?: "not set", onClick = { editing = CardField.Statement })
        ValueRow("Payment due day", due?.let { "day $it of the month" } ?: "not set", onClick = { editing = CardField.Due })
        ValueRow("Minimum due", minimum?.let { IndianFormat.rupees(Paise(it)) } ?: "not set", onClick = { editing = CardField.Minimum })
        ValueRow("Remind me", "${remind ?: 3} days before", onClick = { editing = CardField.Remind })
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", { onSave(limit, statement, due, minimum, remind) }, confirmHaptic = true)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Close", onClose, style = ButtonStyle.Quiet)
    }

    fun money(text: String): Long? = if (text.isBlank()) null else AmountExpression.evaluate(text)?.value
    when (editing) {
        CardField.Limit -> TextSheet("Credit limit", "Amount (empty to clear)", limit?.let { plain(it) } ?: "", {
            if (it.isBlank() || money(it) != null) { limit = money(it); problem = null; editing = null } else problem = "The limit must be a number."
        }, { editing = null }, numeric = true)
        CardField.Minimum -> TextSheet("Minimum due", "Amount from your statement (empty to clear)", minimum?.let { plain(it) } ?: "", {
            if (it.isBlank() || money(it) != null) { minimum = money(it); problem = null; editing = null } else problem = "The minimum due must be a number."
        }, { editing = null }, numeric = true)
        CardField.Statement -> TextSheet("Statement day", "Day of the month, 1 to 31", statement?.toString() ?: "", {
            val d = it.trim().toIntOrNull()
            if (d != null && d in 1..31) { statement = d; problem = null; editing = null } else problem = "Enter a day from 1 to 31."
        }, { editing = null }, numeric = true)
        CardField.Due -> TextSheet("Payment due day", "Day of the month, 1 to 31", due?.toString() ?: "", {
            val d = it.trim().toIntOrNull()
            if (d != null && d in 1..31) { due = d; problem = null; editing = null } else problem = "Enter a day from 1 to 31."
        }, { editing = null }, numeric = true)
        CardField.Remind -> TextSheet("Remind me", "Days before the due date, 0 to 14", (remind ?: 3).toString(), {
            val d = it.trim().toIntOrNull()
            if (d != null && d in 0..14) { remind = d; problem = null; editing = null } else problem = "Enter 0 to 14."
        }, { editing = null }, numeric = true)
        null -> Unit
    }
}

/** Paise as the text a person would type: 123450 is "1234.50", 120000 is "1200". Keeps the paise when editing again. */
private fun plain(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"
