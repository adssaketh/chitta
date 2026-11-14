package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.SinkingFund
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Hero
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId

/**
 * The spend widget's numbers (Phase 8j). The lead figure is the one Home leads with ([HomeFigures.top]), so the two always agree;
 * [spentToday] is money out dated today, counted the way the month header counts it (transfers and balance corrections left out, a refund reduces it, never below zero).
 */
data class WidgetFigures(
    val lead: Lead?,
    val spentToday: Paise,
) {
    /** [perDay] and [daysLeft] are set only for Safe to spend with money left. [warn]: cash is lower than the budget says. [estimate]: a cash-check balance is old. */
    data class Lead(val hero: Hero, val value: Paise, val perDay: Paise?, val daysLeft: Int?, val warn: Boolean, val estimate: Boolean)

    companion object {
        fun compute(
            entries: List<Transaction>, budgets: List<Budget>, objectives: List<Objective>, categories: List<Category>, accounts: List<Account>,
            sinkingFunds: List<SinkingFund>, cashAccounts: Set<String>, preferCash: Boolean, heroBudgetId: String?, nowMs: Long, zone: ZoneId,
        ): WidgetFigures {
            val top = HomeFigures.top(entries, budgets, objectives, categories, accounts, sinkingFunds, cashAccounts, preferCash, heroBudgetId, nowMs, zone)
            val choice = top.choice
            val lead = choice?.let { c ->
                val spendHero = c.hero == Hero.SafeToSpend && top.hero != null
                Lead(
                    hero = c.hero, value = c.value,
                    perDay = if (spendHero) top.hero!!.perDay else null,
                    daysLeft = if (spendHero) top.hero!!.summary.daysLeft else null,
                    warn = c.warn, estimate = c.hero == Hero.CashCheck && top.cashStale,
                )
            }
            val today = Periods.date(nowMs, zone)
            val todays = entries.filter { Periods.date(it.dateMs, zone) == today }
            val out = Totals.of(todays, categories).moneyOut
            return WidgetFigures(lead, if (out.value < 0) Paise.ZERO else out)
        }
    }
}

/** The words on the spend widget, already worked out so they can be tested without a phone. Every money figure is masked when [hidden]. [spoken] is what TalkBack reads. */
data class WidgetLines(val title: String, val main: String, val sub: String, val today: String, val spoken: String) {
    companion object {
        const val MASK = "₹ ••••"

        fun of(f: WidgetFigures, hidden: Boolean): WidgetLines {
            val shown = { p: Paise -> if (hidden) MASK else IndianFormat.rupeesRounded(p) }
            val said = { p: Paise -> if (hidden) "amount hidden" else IndianFormat.spoken(p) }
            val lead = f.lead
                ?: return WidgetLines(
                    "Safe to spend", "No budget", "Set one in Plan", "Spent today ${shown(f.spentToday)}",
                    "Safe to spend: no budget. Set one in Plan. Spent today ${said(f.spentToday)}",
                )
            val title = if (lead.hero == Hero.CashCheck) (if (lead.estimate) "Cash check · estimate" else "Cash check") else "Safe to spend"
            fun sub(m: (Paise) -> String): String = when {
                lead.hero == Hero.CashCheck -> if (lead.warn) "Lower than your budget says" else "After bills and goals"
                lead.value.isNegative -> "Over budget"
                lead.perDay != null && lead.daysLeft != null -> "${m(lead.perDay)} a day · ${lead.daysLeft} ${if (lead.daysLeft == 1) "day" else "days"} left"
                else -> "Nothing left to spend"
            }
            return WidgetLines(
                title, shown(lead.value), sub(shown), "Spent today ${shown(f.spentToday)}",
                "$title: ${said(lead.value)}. ${sub(said)}. Spent today ${said(f.spentToday)}",
            )
        }
    }
}
