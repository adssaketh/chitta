package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId

/**
 * Rollover budgets (Phase 8e, master section 8): money left over, or overspent, in one period moves into the next period's limit.
 *
 *  - limit of period k = amount + carry(k), carry(0) = 0, carry(k + 1) = limit(k) - spent(k), worked out from the unclamped limit so an
 *    overspend keeps counting until later periods pay it back;
 *  - only finished periods carry, counted from the budget's first period; entries before the budget starts are ignored;
 *  - the limit shown is never below zero ([effectiveLimit]); the debt itself is kept in the carry;
 *  - only repeating spending budgets roll over. A one-time budget has no next period and a savings budget has no "left to spend".
 *
 * Category goals stay a share of the base amount, not of the rolled-over limit (DECISIONS D124).
 */
object Rollover {
    fun applies(budget: Budget): Boolean = budget.rollover && !budget.income && budget.repeat != null && budget.repeat != Repeat.CUSTOM

    /** What carries into [period]: the sum over every earlier period of (what it could spend - what it spent). Zero when rollover does not apply. */
    fun carryInto(budget: Budget, period: Period, entries: List<Transaction>, categories: List<Category>, zone: ZoneId): Paise {
        if (!applies(budget)) return Paise.ZERO
        val repeat = budget.repeat!!
        if (period.startMs <= budget.startMs) return Paise.ZERO
        val target = Periods.indexAt(budget.startMs, repeat, budget.periodLength, period.startMs, zone)
        if (target <= 0) return Paise.ZERO
        // spent per earlier period, one pass over the entries
        val spent = LongArray(target)
        for (t in entries) {
            if (t.dateMs < budget.startMs || t.dateMs >= period.startMs) continue
            val c = Budgets.contribution(t, budget, categories)
            if (c == 0L) continue
            val k = Periods.indexAt(budget.startMs, repeat, budget.periodLength, t.dateMs, zone)
            if (k < target) spent[k] = Math.addExact(spent[k], c)
        }
        var carry = 0L
        for (k in 0 until target) carry = Math.subtractExact(Math.addExact(budget.amount, carry), spent[k])
        return Paise(carry)
    }

    /** The limit of a period: the base amount plus what carried in, never below zero. */
    fun effectiveLimit(budget: Budget, carry: Paise): Paise = Paise(maxOf(0L, Math.addExact(budget.amount, carry.value)))
}
