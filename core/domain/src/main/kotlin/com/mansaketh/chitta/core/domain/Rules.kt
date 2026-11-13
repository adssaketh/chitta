package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise

enum class RuleField { Title, Note, Amount, Account }
enum class RuleOp { Contains, StartsWith, Equals, AtLeast, AtMost }

/** One test. Text fields use [text]; Amount uses [amount] (paise, compared as a magnitude); Account uses [accountId]. */
data class RuleCondition(
    val field: RuleField,
    val op: RuleOp,
    val text: String = "",
    val amount: Paise? = null,
    val accountId: String? = null,
)

/** What a matching rule changes. Tags are not here: Paddhu has no tags (phase 0), so there is nothing to set. */
data class RuleActions(
    val categoryId: String? = null,
    val subCategoryId: String? = null,
    val accountId: String? = null,
    /** Budgets the entry is kept out of. Null leaves the entry's own list alone. */
    val excludeFromBudgetIds: List<String>? = null,
)

/** All [conditions] must hold (an empty list never matches). Rules run in [order]; for each field the first rule to set it wins. */
data class Rule(
    val id: String,
    val name: String,
    val conditions: List<RuleCondition>,
    val actions: RuleActions,
    val order: Int,
    val enabled: Boolean = true,
)

/** The facts a rule can see, so the same engine serves manual entry, SMS and CSV rows (master section 8). */
data class RuleInput(
    val title: String,
    val note: String = "",
    val amount: Paise,
    val accountId: String?,
)

data class RuleOutcome(
    val categoryId: String?,
    val subCategoryId: String?,
    val accountId: String?,
    val excludeFromBudgetIds: List<String>?,
    /** The rules that changed something, in the order they ran, so the screen can say why. */
    val matchedRuleIds: List<String>,
) {
    val changesNothing get() = matchedRuleIds.isEmpty()
}

/** One proposed change to an old entry, shown in the preview before anything is written. */
data class RuleChange(
    val entryId: String,
    val ruleIds: List<String>,
    val newCategoryId: String?,
    val newSubCategoryId: String?,
    val newAccountId: String?,
    val newExcludeFromBudgetIds: List<String>?,
)

object Rules {
    fun matches(c: RuleCondition, input: RuleInput): Boolean = when (c.field) {
        RuleField.Title -> text(c, input.title)
        RuleField.Note -> text(c, input.note)
        RuleField.Amount -> {
            val limit = c.amount
            when {
                limit == null -> false
                c.op == RuleOp.AtLeast -> input.amount >= limit
                c.op == RuleOp.AtMost -> input.amount <= limit
                c.op == RuleOp.Equals -> input.amount == limit
                else -> false
            }
        }
        RuleField.Account -> c.accountId != null && c.op == RuleOp.Equals && input.accountId == c.accountId
    }

    private fun text(c: RuleCondition, value: String): Boolean {
        val needle = c.text.trim()
        if (needle.isEmpty()) return false
        return when (c.op) {
            RuleOp.Contains -> value.contains(needle, ignoreCase = true)
            RuleOp.StartsWith -> value.trim().startsWith(needle, ignoreCase = true)
            RuleOp.Equals -> value.trim().equals(needle, ignoreCase = true)
            else -> false
        }
    }

    fun ruleMatches(rule: Rule, input: RuleInput): Boolean =
        rule.enabled && rule.conditions.isNotEmpty() && rule.conditions.all { matches(it, input) }

    /** Runs every enabled rule in order. For each field the first rule that sets it wins. */
    fun apply(rules: List<Rule>, input: RuleInput): RuleOutcome {
        var category: String? = null
        var sub: String? = null
        var account: String? = null
        var exclude: List<String>? = null
        val used = ArrayList<String>()
        for (r in rules.sortedBy { it.order }) {
            if (!ruleMatches(r, input)) continue
            var changed = false
            if (category == null && r.actions.categoryId != null) { category = r.actions.categoryId; sub = r.actions.subCategoryId; changed = true }
            if (account == null && r.actions.accountId != null) { account = r.actions.accountId; changed = true }
            if (exclude == null && r.actions.excludeFromBudgetIds != null) { exclude = r.actions.excludeFromBudgetIds; changed = true }
            if (changed) used += r.id
        }
        return RuleOutcome(category, sub, account, exclude, used)
    }

    /** Applies the outcome to a new or imported entry. Transfers and balance corrections are never touched. */
    fun applyTo(entry: Transaction, outcome: RuleOutcome): Transaction {
        if (outcome.changesNothing || isTransfer(entry)) return entry
        return entry.copy(
            categoryId = outcome.categoryId ?: entry.categoryId,
            subCategoryId = if (outcome.categoryId != null) outcome.subCategoryId else entry.subCategoryId,
            accountId = outcome.accountId ?: entry.accountId,
            excludeFromBudgetIds = outcome.excludeFromBudgetIds ?: entry.excludeFromBudgetIds,
        )
    }

    private fun isTransfer(t: Transaction) = t.categoryId == QuickAdd.TRANSFER_CATEGORY_ID || t.pairedTransactionId != null

    /**
     * The preview for "apply to my history": only entries that would really change are listed. Split lines and transfers
     * are left out. Nothing is written until the person approves the list.
     */
    fun previewHistory(rules: List<Rule>, entries: List<Transaction>): List<RuleChange> =
        entries.mapNotNull { e ->
            if (isTransfer(e) || e.splitGroupId != null) return@mapNotNull null
            val out = apply(rules, RuleInput(e.name, e.note, Paise(kotlin.math.abs(e.amount)), e.accountId))
            if (out.changesNothing) return@mapNotNull null
            val newCat = out.categoryId?.takeIf { it != e.categoryId || out.subCategoryId != e.subCategoryId }
            val newAcc = out.accountId?.takeIf { it != e.accountId }
            val newEx = out.excludeFromBudgetIds?.takeIf { it.toSet() != e.excludeFromBudgetIds.toSet() }
            if (newCat == null && newAcc == null && newEx == null) null
            else RuleChange(e.id, out.matchedRuleIds, newCat, if (newCat != null) out.subCategoryId else null, newAcc, newEx)
        }

    fun applyChange(entry: Transaction, c: RuleChange): Transaction = entry.copy(
        categoryId = c.newCategoryId ?: entry.categoryId,
        subCategoryId = if (c.newCategoryId != null) c.newSubCategoryId else entry.subCategoryId,
        accountId = c.newAccountId ?: entry.accountId,
        excludeFromBudgetIds = c.newExcludeFromBudgetIds ?: entry.excludeFromBudgetIds,
    )
}
