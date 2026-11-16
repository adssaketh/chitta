package com.mansaketh.chitta.ui.add

import com.mansaketh.chitta.core.domain.HomeFigures
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.SinkingFund
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Hero
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId

/**
 * The line under the amount in Quick Add: "Safe to spend after this" (PDF p.15).
 *
 * It is the figure Home leads with ([HomeFigures.top]), worked out again with the entry Save would make added to the book, so it honours the
 * budget's filters, excluded entries, bills and sinking funds exactly as Home does and cannot disagree with Home after the save.
 * It is null (the line shows nothing) when Home is not leading with Safe to spend: no budget runs today, the cash check leads, or the cash check is lower.
 */
object QuickAddFigures {
    fun safeAfter(
        entries: List<Transaction>, added: List<Transaction>, budgets: List<Budget>, objectives: List<Objective>, categories: List<Category>,
        accounts: List<Account>, sinkingFunds: List<SinkingFund>, cashAccounts: Set<String>, preferCash: Boolean, heroBudgetId: String?,
        nowMs: Long, zone: ZoneId,
    ): Paise? {
        val choice = HomeFigures.top(entries + added, budgets, objectives, categories, accounts, sinkingFunds, cashAccounts, preferCash, heroBudgetId, nowMs, zone).choice
            ?: return null
        return if (choice.hero == Hero.SafeToSpend) choice.value else null
    }
}
