package com.mansaketh.chitta.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.ChittaField
import com.mansaketh.chitta.core.design.components.ChittaTopBar
import com.mansaketh.chitta.core.design.components.EmptyState
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.components.LoadingPlaceholder
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.Rule
import com.mansaketh.chitta.core.domain.RuleField
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.ui.BottomOverlay
import com.mansaketh.chitta.ui.Choice
import com.mansaketh.chitta.ui.ChoiceSheet
import com.mansaketh.chitta.ui.ConfirmSheet
import com.mansaketh.chitta.ui.ValueRow
import com.mansaketh.chitta.ui.accountName
import com.mansaketh.chitta.ui.categoryName

private sealed interface RuleSheet {
    data object None : RuleSheet
    data class Actions(val r: Rule) : RuleSheet
    data class Edit(val d: RuleDraft) : RuleSheet
}

/**
 * Rules (master section 8, "Rules engine"; Settings in the design, PDF p.35). Plain sentences, one rule per row.
 * Rules suggest a category while you type a title in Quick Add, and "Apply to my history" shows every change first.
 */
@Composable
fun RulesScreen(vm: RulesViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val preview by vm.preview.collectAsState()
    val c = ChittaTheme.colors
    var sheet by remember { mutableStateOf<RuleSheet>(RuleSheet.None) }

    Column(Modifier.fillMaxSize()) {
        ChittaTopBar("Rules", onBack = onBack)
        LazyColumn(Modifier.padding(horizontal = Dimens.Margin), contentPadding = PaddingValues(bottom = 140.dp)) {
            item {
                Text("If a new entry matches, a rule fills in the category or account. The first rule that sets something wins. Transfers are never changed.", style = ChittaTheme.type.label, color = c.inkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap2), modifier = Modifier.padding(vertical = Dimens.Gap3)) {
                    ChittaButton("New rule", { sheet = RuleSheet.Edit(RuleDraft()) })
                    ChittaButton("Apply to my history", { vm.buildPreview() }, style = ButtonStyle.Quiet, enabled = ui.rules.any { it.enabled })
                }
                HairlineRule()
            }
            if (ui.loading) item { LoadingPlaceholder() }
            if (!ui.loading && ui.rules.isEmpty()) item { EmptyState("No rules yet. Add one.", "New rule", { sheet = RuleSheet.Edit(RuleDraft()) }) }
            items(ui.rules, key = { it.id }) { r ->
                Column(Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable { sheet = RuleSheet.Actions(r) }.padding(vertical = Dimens.Gap2)) {
                    Text(r.name + if (r.enabled) "" else " (off)", style = ChittaTheme.type.title, color = if (r.enabled) c.ink else c.inkMuted)
                    Text(sentence(r, ui), style = ChittaTheme.type.label, color = c.inkMuted)
                }
                HairlineRule()
            }
        }
    }

    when (val s = sheet) {
        is RuleSheet.Actions -> ChoiceSheet(
            s.r.name,
            listOf(Choice("Edit", "edit"), Choice(if (s.r.enabled) "Turn off" else "Turn on", "toggle"), Choice("Delete", "delete", "Undo is offered for 10 seconds")),
            { v ->
                when (v) {
                    "edit" -> sheet = RuleSheet.Edit(draftOf(s.r))
                    "toggle" -> { vm.setEnabled(s.r, !s.r.enabled); sheet = RuleSheet.None }
                    else -> { vm.delete(s.r); sheet = RuleSheet.None }
                }
            },
            { sheet = RuleSheet.None },
        )
        is RuleSheet.Edit -> RuleForm(s.d, ui, vm, { sheet = RuleSheet.None })
        RuleSheet.None -> Unit
    }

    preview?.let { p ->
        if (p.changes.isEmpty()) {
            ConfirmSheet("Nothing to change", listOf("Your rules would not change any entry in your book."), "Close", { vm.cancelPreview() }, { vm.cancelPreview() }, attention = false)
        } else {
            val shown = p.changes.take(5).mapNotNull { ch ->
                p.entries[ch.entryId]?.let { e ->
                    val what = listOfNotNull(
                        ch.newCategoryId?.let { "category ${categoryName(it, ui.categories)}" },
                        ch.newAccountId?.let { "account ${accountName(it, ui.accounts)}" },
                    ).joinToString(", ")
                    "${e.name.ifBlank { "Entry" }} ${IndianFormat.rupees(com.mansaketh.chitta.core.money.Paise(Math.abs(e.amount)))}: $what"
                }
            }
            ConfirmSheet(
                "Change ${p.changes.size} ${if (p.changes.size == 1) "entry" else "entries"}?",
                shown + (if (p.changes.size > shown.size) listOf("and ${p.changes.size - shown.size} more.") else emptyList()) + "Each change goes in the entry's history and can be undone for 10 seconds.",
                "Apply", { vm.applyPreview() }, { vm.cancelPreview() }, attention = false,
            )
        }
    }
}

private fun sentence(r: Rule, ui: RulesUi): String {
    val ifs = r.conditions.joinToString(" and ") {
        when (it.field) {
            RuleField.Title -> "title has \"${it.text}\""
            RuleField.Note -> "note has \"${it.text}\""
            RuleField.Amount -> "amount is ${IndianFormat.rupees(it.amount ?: com.mansaketh.chitta.core.money.Paise.ZERO)} or more"
            RuleField.Account -> "account is ${accountName(it.accountId ?: "", ui.accounts)}"
        }
    }
    val thens = listOfNotNull(
        r.actions.categoryId?.let { "category ${categoryName(it, ui.categories)}" },
        r.actions.accountId?.let { "account ${accountName(it, ui.accounts)}" },
        r.actions.excludeFromBudgetIds?.let { "kept out of ${it.size} budgets" },
    ).joinToString(", ")
    return "If $ifs, set $thens"
}

private fun draftOf(r: Rule): RuleDraft = RuleDraft(
    id = r.id, name = r.name,
    titleContains = r.conditions.firstOrNull { it.field == RuleField.Title }?.text ?: "",
    amountAtLeast = r.conditions.firstOrNull { it.field == RuleField.Amount }?.amount?.let { p -> if (p.value % 100 == 0L) (p.value / 100).toString() else "${p.value / 100}.${(p.value % 100).toString().padStart(2, '0')}" } ?: "",
    accountId = r.conditions.firstOrNull { it.field == RuleField.Account }?.accountId,
    setCategoryId = r.actions.categoryId, setAccountId = r.actions.accountId,
)

private enum class Pick { None, Category, MatchAccount, SetAccount }

@Composable
private fun RuleForm(initial: RuleDraft, ui: RulesUi, vm: RulesViewModel, onClose: () -> Unit) {
    val c = ChittaTheme.colors
    var d by remember { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf(Pick.None) }
    val liveAccounts = ui.accounts.filter { !it.archived }
    BottomOverlay(onClose) {
        Text(if (d.id == null) "New rule" else "Edit rule", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaField(value = d.titleContains, onValueChange = { d = d.copy(titleContains = it) }, label = "Title contains", singleLine = true, placeholder = "swiggy")
        ChittaField(value = d.amountAtLeast, onValueChange = { d = d.copy(amountAtLeast = it) }, label = "Amount at least (optional)", singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        ValueRow("Only in account", d.accountId?.let { accountName(it, ui.accounts) } ?: "any", onClick = { pick = Pick.MatchAccount })
        ValueRow("Set category", d.setCategoryId?.let { categoryName(it, ui.categories) } ?: "no change", onClick = { pick = Pick.Category })
        ValueRow("Move to account", d.setAccountId?.let { accountName(it, ui.accounts) } ?: "no change", onClick = { pick = Pick.SetAccount })
        ChittaField(value = d.name, onValueChange = { d = d.copy(name = it) }, label = "Name (optional)", singleLine = true)
        problem?.let { Text(it, style = ChittaTheme.type.label, color = c.warningText) }
        Spacer(Modifier.height(Dimens.Gap4))
        ChittaButton("Save", { vm.save(d, { problem = it }, onClose) }, confirmHaptic = true)
    }
    when (pick) {
        Pick.Category -> ChoiceSheet("Set category", listOf(Choice("No change", "")) + ui.categories.filter { !it.archived && it.mainCategoryId == null }.map { Choice(it.name, it.id, selected = it.id == d.setCategoryId) },
            { v -> d = d.copy(setCategoryId = v.ifEmpty { null }); pick = Pick.None }, { pick = Pick.None })
        Pick.MatchAccount -> ChoiceSheet("Only in account", listOf(Choice("Any account", "")) + liveAccounts.map { Choice(it.name, it.id, selected = it.id == d.accountId) },
            { v -> d = d.copy(accountId = v.ifEmpty { null }); pick = Pick.None }, { pick = Pick.None })
        Pick.SetAccount -> ChoiceSheet("Move to account", listOf(Choice("No change", "")) + liveAccounts.map { Choice(it.name, it.id, selected = it.id == d.setAccountId) },
            { v -> d = d.copy(setAccountId = v.ifEmpty { null }); pick = Pick.None }, { pick = Pick.None })
        Pick.None -> Unit
    }
}
