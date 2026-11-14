package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Hero
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 9) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, IST).toInstant().toEpochMilli()
private fun r(whole: Long) = Paise.rupees(whole)

private val cats = listOf(Category("food", "food", null, null, null, 0, null, 0, false, null, null))
private val account = Account("a1", "a1", null, null, 0, null, 0, "INR", null, 2, null, null)

/** [rupees] is signed: negative spends, positive refunds. */
private fun entry(id: String, rupees: Long, date: Long, paired: String? = null) = Transaction(
    id = id, pairedTransactionId = paired, name = id, amount = r(rupees).value, currency = "INR", note = "", categoryId = "food", subCategoryId = null, accountId = "a1",
    dateMs = date, modifiedAtMs = null, originalDueMs = null, income = rupees > 0, periodLength = null, repeat = null, endDateMs = null,
    upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null,
    objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

private fun november(amount: Long = 60_000) = Budget(
    "b1", "Monthly", r(amount).value, null, ms(2026, 11, 1, 0), ms(2026, 12, 1, 0) - 1, null, null, null, false, false, false, 1, Repeat.MONTHLY, 0, null,
    false, 0, "a1", listOf(BudgetFilter.DEFAULT_FILTERS), null, false,
)

/** Worked by hand. Today is 14 Nov 2026: day 14 of 30, so 16 days left. */
class WidgetFiguresTest {
    private val now = ms(2026, 11, 14, 12)

    private fun compute(entries: List<Transaction>, budgets: List<Budget> = listOf(november()), cash: Set<String> = emptySet()) =
        WidgetFigures.compute(entries, budgets, emptyList(), cats, listOf(account), emptyList(), cash, false, null, now, IST)

    private val base = listOf(entry("early", -20_000, ms(2026, 11, 3)), entry("today", -4_000, ms(2026, 11, 14)))

    @Test fun safeToSpendAndPerDay() {
        val f = compute(base)
        val lead = assertNotNull(f.lead)
        assertEquals(Hero.SafeToSpend, lead.hero)
        assertEquals(r(36_000), lead.value)          // 60,000 - 24,000 - no bills
        assertEquals(r(2_250), lead.perDay)          // 36,000 / 16
        assertEquals(16, lead.daysLeft)
        assertEquals(r(4_000), f.spentToday)         // only the entry dated today
    }

    @Test fun theWidgetLeadsWithWhatHomeLeadsWith() {
        val top = HomeFigures.top(base, listOf(november()), emptyList(), cats, listOf(account), emptyList(), emptySet(), false, null, now, IST)
        assertEquals(top.choice!!.value, compute(base).lead!!.value)
        assertEquals(top.hero!!.perDay, compute(base).lead!!.perDay)
    }

    @Test fun aTransferTodayIsNotSpendingAndARefundReducesIt() {
        val e = base + entry("leg", -5_000, ms(2026, 11, 14), paired = "other") + entry("refund", 1_000, ms(2026, 11, 14))
        assertEquals(r(3_000), compute(e).spentToday)  // 4,000 - 1,000 refund; the transfer leg is left out
    }

    @Test fun aRefundBiggerThanTodaysSpendingNeverShowsNegative() {
        val e = listOf(entry("refund", 2_500, ms(2026, 11, 14)))
        assertEquals(Paise.ZERO, compute(e).spentToday)
    }

    @Test fun yesterdayAndTomorrowAreNotToday() {
        val e = listOf(entry("y", -700, ms(2026, 11, 13, 23)), entry("t", -900, ms(2026, 11, 15, 1)), entry("now", -300, ms(2026, 11, 14, 0)))
        assertEquals(r(300), compute(e).spentToday)
    }

    @Test fun noBudgetAndNoCashAccountLeavesNoLeadButStillShowsToday() {
        val f = compute(base, budgets = emptyList())
        assertNull(f.lead)
        assertEquals(r(4_000), f.spentToday)
        val lines = WidgetLines.of(f, hidden = false)
        assertEquals("No budget", lines.main)
        assertEquals("Set one in Plan", lines.sub)
        assertEquals("Spent today ₹4,000", lines.today)
    }

    @Test fun noBudgetFallsBackToTheCashCheck() {
        val f = compute(base, budgets = emptyList(), cash = setOf("a1"))
        val lead = assertNotNull(f.lead)
        assertEquals(Hero.CashCheck, lead.hero)
        assertEquals(r(-24_000), lead.value)         // balance of a1 = -20,000 - 4,000; no bills, no goals
        assertNull(lead.perDay)
        assertFalse(lead.estimate)                   // an entry touched a1 today
        assertEquals("Cash check", WidgetLines.of(f, false).title)
    }

    @Test fun anOldCashBalanceIsAnEstimate() {
        val old = listOf(entry("old", -1_000, ms(2026, 11, 2)))
        val lead = assertNotNull(compute(old, budgets = emptyList(), cash = setOf("a1")).lead)
        assertTrue(lead.estimate)                    // 12 days since the last entry on a1, over 7
        assertEquals("Cash check · estimate", WidgetLines.of(compute(old, emptyList(), setOf("a1")), false).title)
    }

    @Test fun words() {
        val lines = WidgetLines.of(compute(base), hidden = false)
        assertEquals("Safe to spend", lines.title)
        assertEquals("₹36,000", lines.main)
        assertEquals("₹2,250 a day · 16 days left", lines.sub)
        assertEquals("Spent today ₹4,000", lines.today)
    }

    @Test fun hiddenMasksEveryFigureAndTheSpokenText() {
        val lines = WidgetLines.of(compute(base), hidden = true)
        assertEquals("₹ ••••", lines.main)
        assertEquals("₹ •••• a day · 16 days left", lines.sub)
        assertEquals("Spent today ₹ ••••", lines.today)
        assertFalse(lines.spoken.any { it.isDigit() && it != '1' && it != '6' }) // only the "16" of "16 days left" has digits
        assertTrue("amount hidden" in lines.spoken)
        assertFalse("36" in lines.spoken || "4,000" in lines.spoken || "4000" in lines.spoken)
    }

    @Test fun oneDayLeftIsSingular() {
        val lead = WidgetFigures.Lead(Hero.SafeToSpend, r(900), r(900), 1, warn = false, estimate = false)
        assertEquals("₹900 a day · 1 day left", WidgetLines.of(WidgetFigures(lead, Paise.ZERO), false).sub)
    }

    @Test fun overBudgetAndNothingLeftAndCashWarning() {
        fun sub(l: WidgetFigures.Lead) = WidgetLines.of(WidgetFigures(l, Paise.ZERO), false).sub
        assertEquals("Over budget", sub(WidgetFigures.Lead(Hero.SafeToSpend, r(-5_000), null, 16, false, false)))
        assertEquals("Nothing left to spend", sub(WidgetFigures.Lead(Hero.SafeToSpend, Paise.ZERO, null, 16, false, false)))
        assertEquals("Lower than your budget says", sub(WidgetFigures.Lead(Hero.CashCheck, r(10_000), null, null, true, false)))
        assertEquals("−₹5,000", WidgetLines.of(WidgetFigures(WidgetFigures.Lead(Hero.SafeToSpend, r(-5_000), null, 16, false, false), Paise.ZERO), false).main)
    }
}
