package com.mansaketh.chitta.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.database.RuleRepository
import com.mansaketh.chitta.core.domain.AmountExpression
import com.mansaketh.chitta.core.domain.Rule
import com.mansaketh.chitta.core.domain.RuleActions
import com.mansaketh.chitta.core.domain.RuleChange
import com.mansaketh.chitta.core.domain.RuleCondition
import com.mansaketh.chitta.core.domain.RuleField
import com.mansaketh.chitta.core.domain.RuleOp
import com.mansaketh.chitta.core.domain.Rules
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.ui.EntryActions
import com.mansaketh.chitta.ui.UndoCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RulesUi(
    val rules: List<Rule> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val loading: Boolean = true,
)

/** The preview shown before a rule set is applied to old entries. Nothing is written until [RulesViewModel.applyPreview]. */
data class RulePreview(val changes: List<RuleChange>, val entries: Map<String, Transaction>)

/** What the rule form holds. Only the common shapes are offered: title contains, optional amount at least, optional account. */
data class RuleDraft(
    val id: String? = null,
    val name: String = "",
    val titleContains: String = "",
    val amountAtLeast: String = "",
    val accountId: String? = null,
    val setCategoryId: String? = null,
    val setAccountId: String? = null,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repo: RuleRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val ledger: LedgerRepository,
    private val actions: EntryActions,
    private val undo: UndoCenter,
    private val ids: IdSource,
) : ViewModel() {
    val ui: StateFlow<RulesUi> = combine(repo.observeAll(), categories.observeAll(), accounts.observeAll()) { r, c, a ->
        RulesUi(r, c, a, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUi())

    private val _preview = MutableStateFlow<RulePreview?>(null)
    val preview: StateFlow<RulePreview?> = _preview

    /** Turns a form into a rule, or says what is wrong. Rules with no condition are refused: they would match nothing. */
    fun save(d: RuleDraft, onProblem: (String) -> Unit, onDone: () -> Unit) {
        val conditions = buildList {
            if (d.titleContains.isNotBlank()) add(RuleCondition(RuleField.Title, RuleOp.Contains, d.titleContains.trim()))
            if (d.amountAtLeast.isNotBlank()) {
                val a = AmountExpression.evaluate(d.amountAtLeast) ?: return onProblem("The amount must be a number.")
                add(RuleCondition(RuleField.Amount, RuleOp.AtLeast, amount = a))
            }
            if (d.accountId != null) add(RuleCondition(RuleField.Account, RuleOp.Equals, accountId = d.accountId))
        }
        if (conditions.isEmpty()) return onProblem("Add at least one thing to match: a word in the title, an amount or an account.")
        if (d.setCategoryId == null && d.setAccountId == null) return onProblem("Choose what the rule should change.")
        viewModelScope.launch {
            val existing = repo.all()
            val order = d.id?.let { id -> existing.firstOrNull { it.id == id }?.order } ?: ((existing.maxOfOrNull { it.order } ?: 0) + 1)
            repo.save(
                Rule(
                    id = d.id ?: ids.newId(),
                    name = d.name.trim().ifEmpty { d.titleContains.trim().ifEmpty { "Rule" } },
                    conditions = conditions,
                    actions = RuleActions(categoryId = d.setCategoryId, accountId = d.setAccountId),
                    order = order,
                    enabled = existing.firstOrNull { it.id == d.id }?.enabled ?: true,
                ),
            )
            onDone()
        }
    }

    fun setEnabled(rule: Rule, enabled: Boolean) = viewModelScope.launch { repo.save(rule.copy(enabled = enabled)) }

    fun delete(rule: Rule) = viewModelScope.launch {
        repo.delete(rule.id)
        undo.post("Deleted rule · ${rule.name}") { repo.save(rule) }
    }

    /** Works out what the enabled rules would change in the whole book. Writes nothing. */
    fun buildPreview() = viewModelScope.launch {
        val entries = ledger.observeAll().first()
        val changes = Rules.previewHistory(repo.all(), entries)
        _preview.value = RulePreview(changes, entries.associateBy { it.id })
    }

    fun cancelPreview() { _preview.value = null }

    /** Applies the previewed changes as ordinary edits, so each is in the entry's history and one Undo reverses them all. */
    fun applyPreview() {
        val p = _preview.value ?: return
        _preview.value = null
        viewModelScope.launch {
            val changed = p.changes.mapNotNull { c -> p.entries[c.entryId]?.let { Rules.applyChange(it, c) } }
            if (changed.isNotEmpty()) actions.save(changed, "Rules applied to ${changed.size} ${if (changed.size == 1) "entry" else "entries"}")
        }
    }
}
