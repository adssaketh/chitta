package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.SinkingFund
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, IST).toInstant().toEpochMilli()
private fun r(whole: Long) = Paise.rupees(whole)

private fun cat(id: String, main: String? = null) = Category(id, id, null, null, null, 0, null, 0, false, null, main)
private val cats = listOf(cat("food"), cat("insurance"), cat("health", main = null))

private fun spend(id: String, rupees: Long, date: Long, category: String = "food") = Transaction(
    id = id, pairedTransactionId = null, name = id, amount = -r(rupees).value, currency = "INR", note = "", categoryId = category, subCategoryId = null, accountId = "a1",
    dateMs = date, modifiedAtMs = null, originalDueMs = null, income = false, periodLength = null, repeat = null, endDateMs = null,
    upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null,
    objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

private fun budget(rollover: Boolean, amount: Long = r(60_000).value, start: Long = ms(2026, 8, 1, 0), repeat: Repeat? = Repeat.MONTHLY, income: Boolean = false) = Budget(
    "b1", "Monthly", amount, null, start, ms(2026, 9, 1, 0) - 1, null, null, null, income, false, false, 1, repeat, 0, null, false, 0, "a1",
    listOf(BudgetFilter.DEFAULT_FILTERS), null, false, rollover,
)

private fun summary(b: Budget, entries: List<Transaction>, now: Long) =
    Budgets.summarize(b, Budgets.periodAt(b, now, IST), entries, cats, now, IST)

class RolloverTest {
    // August: 60,000 limit, 50,000 spent -> 10,000 carries. September: 70,000 limit, 75,000 spent -> 5,000 overspent. October: 55,000.
    private val entries = listOf(spend("a", 50_000, ms(2026, 8, 10)), spend("b", 75_000, ms(2026, 9, 12)))

    @Test fun unspentMoneyCarriesIn() {
        val s = summary(budget(true), entries, ms(2026, 9, 20))
        assertEquals(r(10_000), s.carry)
        assertEquals(r(70_000), s.limit)
        assertEquals(r(-5_000), s.left)
    }

    @Test fun overspendCarriesInToo() {
        val s = summary(budget(true), entries, ms(2026, 10, 5))
        assertEquals(r(-5_000), s.carry)
        assertEquals(r(55_000), s.limit)
        assertEquals(r(55_000), s.left)
    }

    @Test fun offMeansPlainLimit() {
        val s = summary(budget(false), entries, ms(2026, 10, 5))
        assertEquals(Paise.ZERO, s.carry)
        assertEquals(r(60_000), s.limit)
    }

    @Test fun firstPeriodCarriesNothing() {
        assertEquals(Paise.ZERO, summary(budget(true), entries, ms(2026, 8, 20)).carry)
    }

    @Test fun deepOverspendKeepsTheDebtButTheLimitStopsAtZero() {
        val e = listOf(spend("a", 130_000, ms(2026, 8, 10)))
        val sep = summary(budget(true), e, ms(2026, 9, 10))
        assertEquals(r(-70_000), sep.carry)
        assertEquals(Paise.ZERO, sep.limit)
        // September spent nothing: its unclamped limit was -10,000, so October carries -10,000 and gets 50,000
        val oct = summary(budget(true), e, ms(2026, 10, 10))
        assertEquals(r(-10_000), oct.carry)
        assertEquals(r(50_000), oct.limit)
    }

    @Test fun entriesBeforeTheStartAreIgnored() {
        val e = listOf(spend("old", 99_000, ms(2026, 7, 20)), spend("a", 50_000, ms(2026, 8, 10)))
        assertEquals(r(10_000), summary(budget(true), e, ms(2026, 9, 20)).carry)
    }

    @Test fun oneTimeAndSavingsBudgetsDoNotRollOver() {
        assertFalse(Rollover.applies(budget(true, repeat = null)))
        assertFalse(Rollover.applies(budget(true, income = true)))
        assertTrue(Rollover.applies(budget(true)))
    }

    @Test fun historyShowsEachPeriodsOwnLimit() {
        val b = budget(true)
        val h = Budgets.history(b, entries, cats, ms(2026, 10, 5), IST)
        assertEquals(2, h.size)
        assertEquals(r(70_000), h[0].third) // September
        assertEquals(r(60_000), h[1].third) // August
    }
}

class SinkingFundsTest {
    // Insurance of 12,000 due in March 2027, set aside from April 2026: 12 months of 1,000.
    private fun fund(target: Long = r(12_000).value, start: Long = ms(2026, 4, 1), due: Long = ms(2027, 3, 31), category: String? = "insurance") =
        SinkingFund("f1", "Insurance", target, start, due, category, false, 0, 0, null)

    private fun month(y: Int, m: Int) = Period(ms(y, m, 1, 0), ms(if (m == 12) y + 1 else y, if (m == 12) 1 else m + 1, 1, 0))

    @Test fun twelveMonthsOfOneThousand() {
        val f = fund()
        assertEquals(12, SinkingFunds.months(f, IST))
        assertEquals(r(1_000).value, SinkingFunds.monthShare(f, 0, 12))
        assertEquals(r(12_000).value, SinkingFunds.cumulative(f, 11, 12))
    }

    @Test fun sharesAddUpToTheTargetToThePaisa() {
        // 10,000 rupees over 3 months: 3,333.33 + 3,333.33 + 3,333.34
        val f = fund(target = r(10_000).value, start = ms(2026, 1, 1), due = ms(2026, 3, 20))
        val shares = (0..2).map { SinkingFunds.monthShare(f, it, 3) }
        assertEquals(listOf(333_333L, 333_333L, 333_334L), shares)
        assertEquals(f.targetPaise, shares.sum())
    }

    @Test fun aCalendarMonthReservesItsExactShare() {
        val e = SinkingFunds.effect(listOf(fund()), month(2026, 11), emptyList(), cats, IST)
        assertEquals(r(1_000), e.reserved)
        assertEquals(Paise.ZERO, e.covered)
        assertEquals(r(-1_000), e.net)
    }

    @Test fun aPeriodAcrossTwoMonthsIsWeightedByDays() {
        // 15 Nov to 14 Dec: 1,000 x 16/30 = 533.33 and 1,000 x 14/31 = 451.61 (each rounded to the paisa)
        val e = SinkingFunds.effect(listOf(fund()), Period(ms(2026, 11, 15, 0), ms(2026, 12, 15, 0)), emptyList(), cats, IST)
        assertEquals(Paise(53_333 + 45_161), e.reserved)
    }

    @Test fun nothingIsReservedBeforeTheStartOrAfterTheTarget() {
        assertEquals(Paise.ZERO, SinkingFunds.effect(listOf(fund()), month(2026, 3), emptyList(), cats, IST).reserved)
        assertEquals(Paise.ZERO, SinkingFunds.effect(listOf(fund()), month(2027, 4), emptyList(), cats, IST).reserved)
    }

    @Test fun archivedFundsDoNothing() {
        assertEquals(SinkingEffect.NONE, SinkingFunds.effect(listOf(fund().copy(archived = true)), month(2026, 11), emptyList(), cats, IST))
    }

    @Test fun payingTheBillOutOfTheFundCostsOnlyThatMonthsShare() {
        // March 2027: the 12,000 premium is paid. Spent counts 12,000, the fund held 11,000 from April to February, so 11,000 is added back.
        val f = fund()
        val e = listOf(spend("premium", 12_000, ms(2027, 3, 10), "insurance"))
        val fx = SinkingFunds.effect(listOf(f), month(2027, 3), e, cats, IST)
        assertEquals(Paise.ZERO, fx.reserved) // paid in full, so it reserves nothing more
        assertEquals(r(11_000), fx.covered)
        // Safe to spend of a 60,000 budget: 60,000 - 12,000 + 11,000 = 59,000, which is 1,000 below a month with no premium.
        val b = Budget("b1", "M", r(60_000).value, null, ms(2026, 4, 1, 0), ms(2027, 4, 1, 0) - 1, null, null, null, false, false, false, 1, Repeat.MONTHLY, 0, null, false, 0, "a1", listOf(BudgetFilter.DEFAULT_FILTERS), null, false)
        val hero = HomeFigures.hero(b, e, cats, ms(2027, 3, 20), IST, listOf(f))
        assertEquals(r(59_000), hero.safe)
    }

    @Test fun spendingOutsideTheBudgetIsNotAddedBack() {
        val f = fund()
        // a budget that only counts "food": the premium is not in its spent figure, so nothing is added back
        val b = Budget("b1", "Food", r(60_000).value, null, ms(2026, 4, 1, 0), ms(2027, 4, 1, 0) - 1, null, listOf("food"), null, false, false, false, 1, Repeat.MONTHLY, 0, null, false, 0, "a1", listOf(BudgetFilter.DEFAULT_FILTERS), null, false)
        val e = listOf(spend("premium", 12_000, ms(2027, 3, 10), "insurance"))
        assertEquals(r(60_000), HomeFigures.hero(b, e, cats, ms(2027, 3, 20), IST, listOf(f)).safe)
    }

    @Test fun anOrdinaryMonthSubtractsOneShare() {
        val f = fund()
        val b = Budget("b1", "M", r(60_000).value, null, ms(2026, 4, 1, 0), ms(2027, 4, 1, 0) - 1, null, null, null, false, false, false, 1, Repeat.MONTHLY, 0, null, false, 0, "a1", listOf(BudgetFilter.DEFAULT_FILTERS), null, false)
        val e = listOf(spend("food", 10_000, ms(2026, 11, 5)))
        assertEquals(r(49_000), HomeFigures.hero(b, e, cats, ms(2026, 11, 20), IST, listOf(f)).safe)
        assertEquals(r(50_000), HomeFigures.hero(b, e, cats, ms(2026, 11, 20), IST, emptyList()).safe)
    }

    @Test fun payingMoreThanTheFundHeldCountsTheRestAsSpending() {
        // Paid early in June 2026: the fund held 2,000 (April and May) plus June's 1,000 share only.
        val f = fund()
        val e = listOf(spend("premium", 12_000, ms(2026, 6, 10), "insurance"))
        val fx = SinkingFunds.effect(listOf(f), month(2026, 6), e, cats, IST)
        assertEquals(r(2_000), fx.covered)
    }

    @Test fun statusReportsBalanceAndMonthsLeft() {
        val f = fund()
        val e = listOf(spend("x", 3_000, ms(2026, 9, 1), "insurance"))
        val s = SinkingFunds.status(f, e, cats, ms(2026, 11, 14), IST)
        assertEquals(r(8_000), s.setAside) // April to November, 8 months
        assertEquals(r(3_000), s.drawn)
        assertEquals(r(5_000), s.balance)
        assertEquals(5, s.monthsLeft)
        assertFalse(s.paid)
        assertTrue(s.started)
    }

    @Test fun aFundWithoutACategoryIsNeverDrawn() {
        val f = fund(category = null)
        val e = listOf(spend("premium", 12_000, ms(2027, 3, 10), "insurance"))
        assertEquals(Paise.ZERO, SinkingFunds.status(f, e, cats, ms(2027, 3, 20), IST).drawn)
    }

    @Test fun aRefundReducesWhatWasDrawn() {
        val f = fund()
        val refund = spend("back", 0, ms(2026, 9, 2), "insurance").copy(amount = r(500).value, income = true)
        val e = listOf(spend("a", 3_000, ms(2026, 9, 1), "insurance"), refund)
        assertEquals(r(2_500), SinkingFunds.status(f, e, cats, ms(2026, 11, 14), IST).drawn)
    }
}
