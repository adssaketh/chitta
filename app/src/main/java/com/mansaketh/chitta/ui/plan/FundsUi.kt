package com.mansaketh.chitta.ui.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mansaketh.chitta.ui.*

private val NumKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private fun plainRupee(p: Paise) = IndianFormat.rupees(p, symbol = "").replace(",", "")

/** Plan > Sinking funds (Phase 8e). A fund spreads one yearly cost over the months before it falls due. Nothing moves between accounts. */
@Composable
fun FundsTab(ui: PlanUi, onSheet: (String) -> Unit) {
    val funds = ui.sinking.filter { !it.archived }
    val statuses = remember(funds, ui.entries, ui.categories, ui.nowMs) { funds.map { SinkingFunds.status(it, ui.entries, ui.categories, ui.nowMs, ui.zone) } }
    val c = ChittaTheme.colors
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 120.dp)) {
        if (funds.isEmpty()) item {
            EmptyState("A sinking fund spreads a yearly cost, like insurance, over the months before it is due, so that month is not a shock.", "Add a sinking fund", { onSheet("new-fund") })
        } else item {
            Spacer(Modifier.height(Dimens.Gap3))
            Text("Set aside this month", style = ChittaTheme.type.label, color = c.inkMuted)
            AmountText(statuses.fold(Paise.ZERO) { a, s -> a + s.thisMonth }, style = ChittaTheme.type.amount)
            Text("Nothing moves between your accounts. Home's Safe to spend is lowered by this amount.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap1, bottom = Dimens.Gap2))
            HairlineRule()
        }
        items(statuses, key = { it.fund.id }) { s ->
            val f = s.fund
            val sub = when {
                !s.started -> "Starts ${dateText(f.startMs, ui.zone)} · ${rupees(Paise(f.targetPaise))} due ${dateText(f.targetDateMs, ui.zone)}"
                else -> "${rupees(s.setAside)} set aside of ${rupees(Paise(f.targetPaise))} · due ${dateText(f.targetDateMs, ui.zone)}" +
                    (f.categoryId?.let { " · paid from ${categoryName(it, ui.categories)}" } ?: "")
            }
            PlanRow(
                f.name, s.balance, sub,
                stamp = when { s.paid -> "Paid"; s.past -> "Past due"; !s.started -> "Not started"; else -> null },
                attention = s.past && !s.paid, onClick = { onSheet("edit-fund:${f.id}") },
            )
        }
        if (funds.isNotEmpty()) item { Spacer(Modifier.height(Dimens.Gap4)); ChittaButton("Add a sinking fund", { onSheet("new-fund") }, style = ButtonStyle.Quiet) }
    }
}

private enum class FStep { Form, Category }

@Composable
fun FundSheet(
    existing: SinkingFund?, ui: PlanUi,
    onSave: (name: String, target: Paise, startMs: Long, dueMs: Long, categoryId: String?) -> Unit,
    onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ui.zone
    val today = remember { Periods.date(ui.nowMs, zone) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.let { plainRupee(Paise(it.targetPaise)) } ?: "") }
    var startMs by remember { mutableStateOf(existing?.startMs ?: Periods.startOfDay(today.withDayOfMonth(1), zone)) }
    var dueMs by remember { mutableStateOf(existing?.targetDateMs ?: Periods.startOfDay(today.plusYears(1), zone)) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var step by remember { mutableStateOf(FStep.Form) }
    var deleting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val c = ChittaTheme.colors

    when (step) {
        FStep.Category -> BottomOverlay({ step = FStep.Form }) {
            Text("Which category pays this bill?", style = ChittaTheme.type.title, color = c.ink)
            Text("Entries in it take money out of the fund automatically.", style = ChittaTheme.type.label, color = c.inkMuted)
            Spacer(Modifier.height(Dimens.Gap2))
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                ValueRow("None", if (categoryId == null) "Chosen" else "", onClick = { categoryId = null; step = FStep.Form })
                ui.categories.filter { it.mainCategoryId == null && !it.archived && !it.income }.sortedBy { it.sortOrder }.forEach { cat ->
                    ValueRow(cat.name, if (categoryId == cat.id) "Chosen" else "", onClick = { categoryId = cat.id; step = FStep.Form })
                }
            }
        }
        FStep.Form -> BottomOverlay(onDismiss) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(if (existing == null) "New sinking fund" else "Edit sinking fund", style = ChittaTheme.type.title, color = c.ink)
                Spacer(Modifier.height(Dimens.Gap4))
                ChittaField(name, { name = it; problem = null }, "Name, for example Insurance")
                Spacer(Modifier.height(Dimens.Gap3))
                ChittaField(target, { target = it; problem = null }, problem ?: "Total cost", keyboardOptions = NumKeys)
                ValueRow("Set aside from", dateText(startMs, zone), onClick = { pickDate(context, startMs, zone, { startMs = Periods.startOfDay(Periods.date(it, zone), zone) }) })
                ValueRow("Due on", dateText(dueMs, zone), onClick = { pickDate(context, dueMs, zone, { dueMs = Periods.startOfDay(Periods.date(it, zone), zone) }) })
                ValueRow("Paid from category", categoryId?.let { categoryName(it, ui.categories) } ?: "None", onClick = { step = FStep.Category })
                val total = AmountExpression.evaluate(target)
                if (total != null && !total.isZero && dueMs >= startMs) {
                    val n = SinkingFunds.months(SinkingFund("", "", total.value, startMs, dueMs, null, false, 0, 0, null), zone)
                    Text("About ${rupees(Paise(total.value / n))} a month for $n ${if (n == 1) "month" else "months"}, counting the start and due months.", style = ChittaTheme.type.label, color = c.inkMuted, modifier = Modifier.padding(top = Dimens.Gap2))
                }
                Spacer(Modifier.height(Dimens.Gap4))
                ChittaButton("Save", {
                    when {
                        name.isBlank() -> problem = "Give it a name"
                        total == null || total.isZero || total.isNegative -> problem = "Enter a total above zero"
                        dueMs < startMs -> problem = "The due date must not be before the start"
                        else -> onSave(name, total, startMs, dueMs, categoryId)
                    }
                }, confirmHaptic = true)
                if (existing != null) {
                    Spacer(Modifier.height(Dimens.Gap2))
                    ChittaButton("Delete this fund", { deleting = true }, style = ButtonStyle.Quiet)
                }
            }
        }
    }
    if (deleting && existing != null) ConfirmSheet("Delete ${existing.name}?", listOf("Your entries are not touched. Safe to spend stops setting money aside for it.", "You can undo this for 10 seconds."), "Delete", { deleting = false; onDelete() }, { deleting = false })
}
