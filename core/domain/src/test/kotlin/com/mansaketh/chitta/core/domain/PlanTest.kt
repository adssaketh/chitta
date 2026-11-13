package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.CategoryLimit
import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.PaceStatus
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private val NY = ZoneId.of("America/New_York")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, zone: ZoneId = IST) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()
private fun r(whole: Long) = Paise.rupees(whole)

private fun cat(id: String, income: Boolean = false, main: String? = null) = Category(id, id, null, null, null, 0, null, 0, income, null, main)

private fun tx(
    id: String, amount: Long, date: Long = ms(2026, 11, 12), account: String = "a1", category: String = "food", kind: TransactionKind? = null,
    paid: Boolean = true, skip: Boolean = false, repeat: Repeat? = null, length: Int? = null, end: Long? = null, anchor: Int? = null,
    originalDue: Long? = null, createdAnother: Boolean? = null, goal: String? = null, loan: String? = null, exclude: List<String> = emptyList(),
    sub: String? = null, name: String = id, notify: Boolean? = null, paired: String? = null,
) = Transaction(
    id = id, pairedTransactionId = paired, name = name, amount = amount, currency = "INR", note = "", categoryId = category, subCategoryId = sub, accountId = account,
    dateMs = date, modifiedAtMs = null, originalDueMs = originalDue, income = amount > 0, periodLength = length, repeat = repeat, endDateMs = end,
    upcomingNotification = notify, kind = kind, paid = paid, createdAnotherFuture = createdAnother, skipPaid = skip, methodAdded = null, objectiveId = goal,
    objectiveLoanId = loan, excludeFromBudgetIds = exclude, ownerUid = null, isPrivate = false, householdSyncedAtMs = null, anchorDay = anchor,
)

private fun budget(
    amount: Long = 6_000_000, start: Long = ms(2026, 11, 1, 0), end: Long = ms(2026, 12, 1, 0) - 1, repeat: Repeat? = Repeat.MONTHLY, accounts: List<String>? = null,
    categories: List<String>? = null, excluded: List<String>? = null, income: Boolean = false, filters: List<BudgetFilter>? = listOf(BudgetFilter.DEFAULT_FILTERS),
    absolute: Boolean = false, id: String = "b1",
) = Budget(
    id, "Monthly", amount, null, start, end, accounts, categories, excluded, income, false, false, 1, repeat, 0, null, false, 0, "0", filters, null, absolute,
)

class PeriodsTest {
    @Test fun monthlyBillOnThe31stKeepsItsDay() {
        val jan31 = ms(2026, 1, 31)
        val feb = Periods.advance(jan31, Repeat.MONTHLY, 1, 1, IST, 31)
        assertEquals(LocalDate.of(2026, 2, 28), Periods.date(feb, IST))
        // two steps from the same start, not one from February: March is the 31st again
        assertEquals(LocalDate.of(2026, 3, 31), Periods.date(Periods.advance(jan31, Repeat.MONTHLY, 1, 2, IST, 31), IST))
        assertEquals(LocalDate.of(2026, 4, 30), Periods.date(Periods.advance(jan31, Repeat.MONTHLY, 1, 3, IST, 31), IST))
    }

    @Test fun leapDayYearly() {
        val leap = ms(2024, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), Periods.date(Periods.advance(leap, Repeat.YEARLY, 1, 1, IST, 29), IST))
        assertEquals(LocalDate.of(2028, 2, 29), Periods.date(Periods.advance(leap, Repeat.YEARLY, 1, 4, IST, 29), IST))
    }

    @Test fun yearBoundaryAndEveryTwoWeeks() {
        assertEquals(LocalDate.of(2027, 1, 15), Periods.date(Periods.advance(ms(2026, 12, 15), Repeat.MONTHLY, 1, 1, IST), IST))
        assertEquals(LocalDate.of(2026, 12, 12), Periods.date(Periods.advance(ms(2026, 11, 28), Repeat.WEEKLY, 2, 1, IST), IST))
        assertEquals(LocalDate.of(2027, 1, 1), Periods.date(Periods.advance(ms(2026, 12, 30), Repeat.DAILY, 1, 2, IST), IST))
    }

    @Test fun clockChangeKeepsTheWallClockTime() {
        // US clocks go forward on 8 Mar 2026; a 9:00 daily bill stays at 9:00 local time
        val before = ms(2026, 3, 7, 9, zone = NY)
        val after = Periods.advance(before, Repeat.DAILY, 1, 1, NY)
        val z = java.time.Instant.ofEpochMilli(after).atZone(NY)
        assertEquals(9, z.hour)
        assertEquals(23, (after - before) / 3_600_000) // that day is only 23 hours long
    }

    @Test fun customDoesNotRepeat() {
        assertFailsWith<IllegalArgumentException> { Periods.advance(ms(2026, 1, 1), Repeat.CUSTOM, 1, 1, IST) }
    }

    @Test fun budgetPeriodFindsTheMonthThatHoldsNow() {
        val start = ms(2026, 1, 1, 0)
        val p = Periods.budgetPeriod(start, start + 1, Repeat.MONTHLY, 1, ms(2026, 11, 13), IST)
        assertEquals(ms(2026, 11, 1, 0), p.startMs)
        assertEquals(ms(2026, 12, 1, 0), p.endMs)
        assertEquals(30, Periods.daysIn(p, IST))
        assertEquals(13, Periods.dayOfPeriod(p, ms(2026, 11, 13), IST))
    }

    @Test fun periodBoundaryBelongsToTheNewPeriod() {
        val start = ms(2026, 1, 1, 0)
        val p = Periods.budgetPeriod(start, start + 1, Repeat.MONTHLY, 1, ms(2026, 12, 1, 0), IST)
        assertEquals(ms(2026, 12, 1, 0), p.startMs)
        val prev = Periods.budgetPeriod(start, start + 1, Repeat.MONTHLY, 1, ms(2026, 12, 1, 0) - 1, IST)
        assertEquals(ms(2026, 11, 1, 0), prev.startMs)
    }

    @Test fun weeklyAndBeforeStartAndOneTime() {
        val start = ms(2026, 11, 2, 0) // a Monday
        val p = Periods.budgetPeriod(start, start + 1, Repeat.WEEKLY, 1, ms(2026, 11, 12), IST)
        assertEquals(ms(2026, 11, 9, 0), p.startMs)
        assertEquals(7, Periods.daysIn(p, IST))
        // before the budget starts, the first period is shown and day-of-period is 0
        val first = Periods.budgetPeriod(start, start + 1, Repeat.WEEKLY, 1, ms(2026, 10, 1), IST)
        assertEquals(start, first.startMs)
        assertEquals(0, Periods.dayOfPeriod(first, ms(2026, 10, 1), IST))
        // a custom (one-time) budget is its own start and end
        val once = Periods.budgetPeriod(ms(2026, 12, 1, 0), ms(2026, 12, 11, 0) - 1, null, 1, ms(2026, 12, 5), IST)
        assertEquals(10, Periods.daysIn(once, IST))
    }

    @Test fun earlierPeriodsAreNewestFirst() {
        val start = ms(2026, 8, 1, 0)
        val list = Periods.earlierPeriods(start, Repeat.MONTHLY, 1, ms(2026, 11, 13), IST, 12)
        assertEquals(listOf(ms(2026, 10, 1, 0), ms(2026, 9, 1, 0), ms(2026, 8, 1, 0)), list.map { it.startMs })
        assertTrue(Periods.earlierPeriods(start, null, 1, ms(2026, 11, 13), IST, 12).isEmpty())
    }
}

class BillsTest {
    private val ids = generateSequence(1) { it + 1 }.map { "n$it" }.iterator()
    private fun newId() = ids.next()
    private val now = ms(2026, 11, 13, 10)

    private fun monthly(due: Long, id: String = "rent", amount: Long = -1_500_000, anchor: Int? = null, end: Long? = null, kind: TransactionKind = TransactionKind.SUBSCRIPTION) =
        tx(id, amount, due, category = "rent", kind = kind, paid = false, repeat = Repeat.MONTHLY, length = 1, end = end, anchor = anchor, createdAnother = false)

    @Test fun payingMovesTheDateAndMakesTheNextBillFromTheDueDate() {
        val t = monthly(ms(2026, 11, 10))
        val c = Bills.pay(t, now, IST, PaidDateRule.Today, ::newId)
        assertTrue(c.updated.paid)
        assertEquals(now, c.updated.dateMs)
        assertEquals(ms(2026, 11, 10), c.updated.originalDueMs)
        assertEquals(true, c.updated.createdAnotherFuture)
        val next = assertNotNull(c.next)
        assertEquals(LocalDate.of(2026, 12, 10), Periods.date(next.dateMs, IST))
        assertFalse(next.paid)
        assertEquals(false, next.createdAnotherFuture)
        assertNull(next.originalDueMs)
    }

    @Test fun paidOnTheDueDateRuleKeepsTheDate() {
        val t = monthly(ms(2026, 11, 10))
        assertEquals(ms(2026, 11, 10), Bills.pay(t, now, IST, PaidDateRule.DueDate, ::newId).updated.dateMs)
    }

    @Test fun billOnThe31stGoesThroughShortMonths() {
        var t = monthly(ms(2026, 1, 31))
        val seen = mutableListOf<LocalDate>()
        repeat(4) {
            val c = Bills.pay(t, now, IST, PaidDateRule.DueDate, ::newId)
            t = c.next!!
            seen += Periods.date(t.dateMs, IST)
        }
        assertEquals(listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31)), seen)
    }

    @Test fun paidLateDoesNotPushTheSchedule() {
        val t = monthly(ms(2026, 10, 5))
        val c = Bills.pay(t, ms(2026, 10, 20), IST, PaidDateRule.Today, ::newId)
        assertEquals(LocalDate.of(2026, 11, 5), Periods.date(c.next!!.dateMs, IST))
    }

    @Test fun changedDueDateMovesTheNextOne() {
        val t = monthly(ms(2026, 11, 10)).copy(dateMs = ms(2026, 11, 14))
        assertEquals(LocalDate.of(2026, 12, 14), Periods.date(Bills.pay(t, now, IST, PaidDateRule.DueDate, ::newId).next!!.dateMs, IST))
    }

    @Test fun paidFromAnotherAccountOrForAnotherAmountChangesOnlyThatBill() {
        val t = monthly(ms(2026, 11, 10)).copy(accountId = "hdfc")
        val c = Bills.pay(t, now, IST, PaidDateRule.Today, ::newId, amountPaise = 1_620_000, accountId = "cash")
        assertEquals(-1_620_000, c.updated.amount)
        assertEquals("cash", c.updated.accountId)
        assertEquals(-1_500_000, c.next!!.amount)
        assertEquals("hdfc", c.next!!.accountId)
        // an income bill keeps its sign
        val salary = tx("pay", 5_000_000, ms(2026, 11, 10), category = "pay", kind = TransactionKind.REPETITIVE, paid = false, repeat = Repeat.MONTHLY, length = 1, createdAnother = false)
        assertEquals(5_100_000, Bills.pay(salary, now, IST, PaidDateRule.Today, ::newId, amountPaise = 5_100_000).updated.amount)
    }

    @Test fun nothingIsMadeAfterTheEndDate() {
        val t = monthly(ms(2026, 11, 10), end = ms(2026, 12, 5))
        assertNull(Bills.pay(t, now, IST, PaidDateRule.Today, ::newId).next)
        val ok = monthly(ms(2026, 11, 10), end = ms(2026, 12, 10, 12))
        assertNotNull(Bills.pay(ok, now, IST, PaidDateRule.Today, ::newId).next)
    }

    @Test fun reachedGoalStopsTheRepeat() {
        assertNull(Bills.pay(monthly(ms(2026, 11, 10)), now, IST, PaidDateRule.Today, ::newId, goalReached = true).next)
    }

    @Test fun oneTimeUpcomingMakesNoNextBill() {
        val t = tx("u", -149_900, ms(2026, 11, 20), kind = TransactionKind.UPCOMING, paid = false)
        assertNull(Bills.pay(t, now, IST, PaidDateRule.Today, ::newId).next)
    }

    @Test fun skippingLeavesItUnpaidSoItCountsNowhereAndMakesTheNext() {
        val t = monthly(ms(2026, 11, 10))
        val c = Bills.skip(t, now, IST, ::newId)
        assertTrue(c.updated.skipPaid)
        assertFalse(c.updated.paid)
        assertEquals(Paise.ZERO, Balances.balance(listOf(c.updated), "a1"))
        assertEquals(LocalDate.of(2026, 12, 10), Periods.date(c.next!!.dateMs, IST))
        assertEquals(BillStatus.Skipped, Bills.status(c.updated, now, IST))
    }

    @Test fun anEntryThatAlreadyMadeItsNextDoesNotMakeAnotherOnSecondPay() {
        val t = monthly(ms(2026, 11, 10)).copy(createdAnotherFuture = true)
        assertNull(Bills.pay(t, now, IST, PaidDateRule.Today, ::newId).next)
    }

    @Test fun payingTwiceOrPayingAPaidBillIsRefused() {
        val paid = monthly(ms(2026, 11, 10)).copy(paid = true)
        assertFailsWith<IllegalArgumentException> { Bills.pay(paid, now, IST, PaidDateRule.Today, ::newId) }
        assertFailsWith<IllegalArgumentException> { Bills.skip(paid, now, IST, ::newId) }
    }

    @Test fun statusIsOverdueOnlyAfterTheDueDay() {
        val today = tx("a", -100, ms(2026, 11, 13, 8), kind = TransactionKind.UPCOMING, paid = false)
        val yesterday = tx("b", -100, ms(2026, 11, 12, 23), kind = TransactionKind.UPCOMING, paid = false)
        val tomorrow = tx("c", -100, ms(2026, 11, 14, 0), kind = TransactionKind.UPCOMING, paid = false)
        assertEquals(BillStatus.Upcoming, Bills.status(today, now, IST))
        assertEquals(BillStatus.Overdue, Bills.status(yesterday, now, IST))
        assertEquals(BillStatus.Upcoming, Bills.status(tomorrow, now, IST))
        assertEquals(BillStatus.Paid, Bills.status(today.copy(paid = true), now, IST))
    }

    @Test fun removingAPaymentOrSkipPutsItBackOnItsDueDate() {
        val t = monthly(ms(2026, 11, 10))
        val paid = Bills.pay(t, now, IST, PaidDateRule.Today, ::newId).updated
        val back = Bills.removePayment(paid, now)
        assertFalse(back.paid)
        assertEquals(ms(2026, 11, 10), back.dateMs)
        val skipped = Bills.skip(t, now, IST, ::newId).updated
        val open = Bills.removeSkip(skipped, now)
        assertFalse(open.skipPaid)
        assertEquals(ms(2026, 11, 10), open.dateMs)
    }

    @Test fun zeroAmountNeedsConfirming() {
        assertTrue(Bills.needsAmount(monthly(ms(2026, 11, 10), amount = 0)))
        assertFalse(Bills.needsAmount(monthly(ms(2026, 11, 10))))
    }

    @Test fun autoPayChainsThroughMissedMonthsAndStopsInTheFuture() {
        val t = monthly(ms(2026, 8, 10))
        val changes = Bills.autoPay(listOf(t), ms(2026, 11, 13), IST, ::newId)
        assertEquals(4, changes.size) // Aug, Sep, Oct, Nov all paid
        assertTrue(changes.all { it.updated.paid && it.updated.dateMs == it.updated.originalDueMs })
        assertEquals(LocalDate.of(2026, 12, 10), Periods.date(changes.last().next!!.dateMs, IST))
    }

    @Test fun autoPayLeavesPaidSkippedAndFutureBillsAlone() {
        val items = listOf(
            monthly(ms(2026, 11, 20), id = "future"),
            monthly(ms(2026, 11, 1), id = "paid").copy(paid = true),
            monthly(ms(2026, 11, 2), id = "skipped").copy(skipPaid = true),
            tx("plain", -100, ms(2026, 11, 1)),
        )
        assertTrue(Bills.autoPay(items, ms(2026, 11, 13), IST, ::newId).isEmpty())
    }

    @Test fun scheduledTotalsAndAverages() {
        val items = listOf(
            monthly(ms(2026, 11, 10), id = "rent", amount = -1_000_000),                                    // ₹10,000 monthly
            tx("gym", -50_000, ms(2026, 11, 15), kind = TransactionKind.SUBSCRIPTION, paid = false, repeat = Repeat.WEEKLY, length = 1), // ₹500 weekly
            tx("ins", -1_200_000, ms(2026, 12, 1), kind = TransactionKind.REPETITIVE, paid = false, repeat = Repeat.YEARLY, length = 1),  // ₹12,000 yearly
            tx("once", -300_000, ms(2026, 11, 25), kind = TransactionKind.UPCOMING, paid = false),         // one-time ₹3,000
            tx("salary", 5_000_000, ms(2026, 11, 30), kind = TransactionKind.REPETITIVE, paid = false, repeat = Repeat.MONTHLY, length = 1), // money in: not counted
            tx("done", -999_900, ms(2026, 11, 1), kind = TransactionKind.SUBSCRIPTION, paid = true, repeat = Repeat.MONTHLY, length = 1),  // paid: not open
        )
        val t = Scheduled.totals(items)
        assertEquals(r(10_000) + r(500) + r(12_000) + r(3_000), t.total)
        assertEquals(3, t.repeating)
        // year: 120,000 + 500 × 365 ÷ 7 = 26,071.43 + 12,000
        assertEquals(Paise(12_000_000 + 2_607_143 + 1_200_000), t.perYear)
        assertEquals(Paise(mulDivRound(12_000_000L + 2_607_143 + 1_200_000, 1, 12)), t.perMonth)
    }
}

class BudgetsTest {
    private val cats = listOf(cat("food"), cat("fuel"), cat("pizza", main = "food"), cat("pay", income = true))
    private val now = ms(2026, 11, 13, 12)
    private val b = budget()

    @Test fun spentAddsMoneyOutInThePeriodOnly() {
        val all = listOf(
            tx("a", -184_000), tx("b", -50_000, category = "fuel"), tx("refund", 20_000),
            tx("old", -999_999, date = ms(2026, 10, 31)), tx("pay", 6_000_000, category = "pay"),
        )
        val p = Budgets.periodAt(b, now, IST)
        assertEquals(Paise(184_000 + 50_000 - 20_000), Budgets.spent(b, p, all, cats))
    }

    @Test fun unpaidAndSkippedBillsDoNotCountUntilPaid() {
        val all = listOf(
            tx("u", -100_000, kind = TransactionKind.UPCOMING, paid = false),
            tx("s", -100_000, kind = TransactionKind.SUBSCRIPTION, skip = true, paid = false),
            tx("p", -100_000, kind = TransactionKind.UPCOMING, paid = true),
        )
        assertEquals(Paise(100_000), Budgets.spent(b, Budgets.periodAt(b, now, IST), all, cats))
    }

    @Test fun defaultFiltersLeaveOutIncomeDebtAndTransfersButKeepGoalSavings() {
        val all = listOf(
            tx("lent", -500_000, kind = TransactionKind.LENT), tx("loanpay", -100_000, loan = "L1"),
            tx("xfer", -200_000, category = "0", paired = "x2"), tx("fix", -300_000, category = "0"), tx("pay", 6_000_000, category = "pay"),
            tx("goal", -400_000, goal = "g1"), // Paddhu keeps goal contributions in a default budget
            tx("food", -10_000),
        )
        assertEquals(Paise(410_000), Budgets.spent(b, Budgets.periodAt(b, now, IST), all, cats))
    }

    @Test fun leavingGoalSavingsOutAndSwitchingTheOthersOn() {
        val all = listOf(tx("lent", -500_000, kind = TransactionKind.LENT), tx("goal", -400_000, goal = "g1"), tx("fix", -300_000, category = "0"), tx("pay", 6_000_000, category = "pay"))
        val noGoals = budget(filters = listOf(BudgetFilter.INCLUDE_DEBT_AND_CREDIT))
        assertEquals(Paise(500_000), Budgets.spent(noGoals, Budgets.periodAt(noGoals, now, IST), all, cats))
        val on = budget(filters = listOf(BudgetFilter.INCLUDE_DEBT_AND_CREDIT, BudgetFilter.ADDED_TO_OBJECTIVE, BudgetFilter.INCLUDE_BALANCE_CORRECTION, BudgetFilter.INCLUDE_INCOME))
        assertEquals(Paise(500_000 + 400_000 + 300_000 - 6_000_000), Budgets.spent(on, Budgets.periodAt(on, now, IST), all, cats))
    }

    @Test fun aBudgetWithNoFilterListCountsEverything() {
        assertTrue(Budgets.filterOn(null, BudgetFilter.INCLUDE_INCOME))
        assertFalse(Budgets.filterOn(listOf(BudgetFilter.DEFAULT_FILTERS), BudgetFilter.INCLUDE_INCOME))
        assertTrue(Budgets.filterOn(listOf(BudgetFilter.DEFAULT_FILTERS), BudgetFilter.ADDED_TO_OBJECTIVE))
        assertFalse(Budgets.filterOn(listOf(BudgetFilter.INCLUDE_INCOME), BudgetFilter.ADDED_TO_OBJECTIVE))
    }

    @Test fun incomeBudgetCountsMoneyIn() {
        val inc = budget(income = true, filters = listOf(BudgetFilter.DEFAULT_FILTERS))
        val all = listOf(tx("pay", 6_000_000, category = "pay"), tx("food", -10_000))
        assertEquals(Paise(6_000_000), Budgets.spent(inc, Budgets.periodAt(inc, now, IST), all, cats))
    }

    @Test fun accountsCategoriesSubcategoriesAndExclusions() {
        val all = listOf(
            tx("a", -100, account = "a1"), tx("b", -200, account = "a2"), tx("pizza", -400, category = "pizza"), tx("fuel", -800, category = "fuel"),
            tx("ex", -1_600, exclude = listOf("b1")),
        )
        val onlyA1 = budget(accounts = listOf("a1"))
        assertEquals(Paise(100 + 400 + 800), Budgets.spent(onlyA1, Budgets.periodAt(onlyA1, now, IST), all, cats))
        val food = budget(categories = listOf("food"))
        assertEquals(Paise(100 + 200 + 400), Budgets.spent(food, Budgets.periodAt(food, now, IST), all, cats)) // pizza rolls up under food; excluded entry left out
        val noFuel = budget(excluded = listOf("fuel"))
        assertEquals(Paise(100 + 200 + 400), Budgets.spent(noFuel, Budgets.periodAt(noFuel, now, IST), all, cats))
    }

    @Test fun summaryMatchesTheWorkedExample() {
        // ₹60,000 limit, ₹26,520 spent by day 12 of a 30-day period (section 12)
        val start = ms(2026, 11, 1, 0)
        val period = Period(start, ms(2026, 12, 1, 0))
        val entries = (1..12).map { d -> tx("e$d", -221_000, date = ms(2026, 11, d, 10)) } // 12 × ₹2,210 = ₹26,520
        val s = Budgets.summarize(b, period, entries, cats, ms(2026, 11, 12, 20), IST)
        assertEquals(r(26_520), s.spent)
        assertEquals(12, s.dayOfPeriod)
        assertEquals(18, s.daysLeft)
        assertEquals(r(24_000), s.ideal)
        assertEquals(PaceStatus.OverPace, s.pace)
        assertEquals(r(66_300), s.forecast)
        assertEquals(28, s.limitDay)
        assertEquals(12, s.cumulative.size)
        assertEquals(r(2_210), s.cumulative.first())
        assertEquals(r(26_520), s.cumulative.last())
        assertEquals(r(33_480), s.left)
    }

    @Test fun firstDaysShowNoPaceOrForecast() {
        val period = Budgets.periodAt(b, ms(2026, 11, 1, 9), IST)
        val s = Budgets.summarize(b, period, listOf(tx("a", -500_000, date = ms(2026, 11, 1, 9))), cats, ms(2026, 11, 1, 9), IST)
        assertNull(s.ideal)
        assertNull(s.forecast)
        assertNull(s.limitDay)
        assertEquals(PaceStatus.OnPace, s.pace)
    }

    @Test fun overLimitIsADifferentStateFromOverPace() {
        val period = Budgets.periodAt(b, now, IST)
        val s = Budgets.summarize(b, period, listOf(tx("a", -6_500_000, date = ms(2026, 11, 3))), cats, now, IST)
        assertEquals(PaceStatus.OverLimit, s.pace)
        assertEquals(r(-5_000), s.left)
    }

    @Test fun aFinishedPeriodIsShownAsItsLastDay() {
        val old = Period(ms(2026, 10, 1, 0), ms(2026, 11, 1, 0))
        val s = Budgets.summarize(b, old, listOf(tx("a", -1_000_000, date = ms(2026, 10, 10))), cats, now, IST)
        assertTrue(s.ended)
        assertEquals(31, s.dayOfPeriod)
        assertEquals(0, s.daysLeft)
        assertEquals(s.spent, s.forecast)
    }

    @Test fun notStartedBudgetHasNoDaysYet() {
        val future = budget(start = ms(2027, 1, 1, 0), end = ms(2027, 2, 1, 0) - 1, repeat = null)
        val s = Budgets.summarize(future, Budgets.periodAt(future, now, IST), emptyList(), cats, now, IST)
        assertFalse(s.started)
        assertEquals(0, s.dayOfPeriod)
        assertTrue(s.cumulative.isEmpty())
    }

    @Test fun changingTheBudgetPeriodRecomputesFromTheNewStart() {
        val weekly = budget(start = ms(2026, 11, 9, 0), repeat = Repeat.WEEKLY)
        val all = listOf(tx("a", -1_000, date = ms(2026, 11, 8)), tx("b", -2_000, date = ms(2026, 11, 10)))
        assertEquals(Paise(2_000), Budgets.spent(weekly, Budgets.periodAt(weekly, now, IST), all, cats))
    }

    @Test fun categoryLimitsAsPercentAndAsAmount() {
        val pct = CategoryLimit("l1", "food", "b1", 2_500, null, "0") // 25%
        assertEquals(r(15_000), Budgets.limitFor(b, pct))
        val abs = budget(absolute = true)
        assertEquals(Paise(2_500), Budgets.limitFor(abs, pct))
    }

    @Test fun breakdownRollsSubcategoriesUpAndListsLimitsWithNoSpending() {
        val all = listOf(tx("a", -400_000), tx("pizza", -200_000, category = "pizza"), tx("fuel", -100_000, category = "fuel"))
        val limits = listOf(CategoryLimit("l1", "food", "b1", 5_000, null, "0"), CategoryLimit("l2", "pay", "b1", 1_000, null, "0"))
        val lines = Budgets.breakdown(b, Budgets.periodAt(b, now, IST), all, cats, limits)
        assertEquals(listOf("food", "fuel", "pay"), lines.map { it.categoryId })
        assertEquals(Paise(600_000), lines[0].spent)
        assertEquals(r(30_000), lines[0].limit) // 50% of ₹60,000
        assertEquals(Paise.ZERO, lines[2].spent)
        assertNull(lines[1].limit)
    }

    @Test fun historyListsEarlierPeriodsWithTheirSpend() {
        val bud = budget(start = ms(2026, 9, 1, 0))
        val all = listOf(tx("sep", -111_100, date = ms(2026, 9, 5)), tx("oct", -222_200, date = ms(2026, 10, 5)), tx("nov", -333_300, date = ms(2026, 11, 5)))
        val h = Budgets.history(bud, all, cats, now, IST)
        assertEquals(listOf(Paise(222_200), Paise(111_100)), h.map { it.second })
    }

    @Test fun billsDueCountsOnlyOpenBillsThatWouldCountWhenPaid() {
        val all = listOf(
            tx("rent", -2_000_000, date = ms(2026, 11, 20), kind = TransactionKind.SUBSCRIPTION, paid = false),
            tx("xfer", -500_000, date = ms(2026, 11, 20), category = "0", kind = TransactionKind.UPCOMING, paid = false),
            tx("done", -700_000, date = ms(2026, 11, 20), kind = TransactionKind.UPCOMING, paid = true),
            tx("next", -300_000, date = ms(2026, 12, 2), kind = TransactionKind.UPCOMING, paid = false),
        )
        assertEquals(Paise(2_000_000), Budgets.billsDue(b, Budgets.periodAt(b, now, IST), all, cats))
    }
}

class GoalsTest {
    private val goal = Objective("g1", ObjectiveKind.GOAL, "Trip", 10_000_000, 0, null, 0, ms(2027, 3, 31), null, null, null, true, false, false, "a1")

    @Test fun savingsGoalAddsPaidLinkedEntries() {
        val all = listOf(tx("a", 2_500_000, goal = "g1"), tx("b", 1_000_000, goal = "g1"), tx("unpaid", 9_000_000, goal = "g1", paid = false), tx("other", 5_000_000, goal = "g2"))
        assertEquals(Paise(3_500_000), Goals.saved(goal, all))
    }

    @Test fun expenseGoalFlipsTheSign() {
        val spend = goal.copy(income = false)
        assertEquals(Paise(700_000), Goals.saved(spend, listOf(tx("a", -400_000, goal = "g1"), tx("b", -300_000, goal = "g1"))))
    }

    @Test fun progressMonthsAndPerMonthNeeded() {
        val p = Goals.progress(goal, listOf(tx("a", 3_500_000, goal = "g1")), ms(2026, 11, 13), IST)
        assertEquals(Paise(6_500_000), p.remaining)
        assertEquals(5, p.monthsLeft) // 13 Nov to 31 Mar: 4 months and 18 days, rounded up
        assertEquals(Paise(1_300_000), p.perMonthNeeded)
        assertFalse(p.reached)
        assertEquals(7, p.gauge.filled) // 35% of 20 segments
    }

    @Test fun reachedAndPastEnd() {
        val done = Goals.progress(goal, listOf(tx("a", 10_000_000, goal = "g1")), ms(2026, 11, 13), IST)
        assertTrue(done.reached)
        assertNull(done.perMonthNeeded)
        val late = Goals.progress(goal, emptyList(), ms(2027, 5, 1), IST)
        assertTrue(late.pastEnd)
        assertNull(late.monthsLeft)
        assertNull(late.perMonthNeeded)
    }

    @Test fun noEndDateMeansNoMonthlyFigure() {
        val open = goal.copy(endDateMs = null)
        assertNull(Goals.progress(open, emptyList(), ms(2026, 11, 13), IST).perMonthNeeded)
    }

    @Test fun contributionHasTheGoalsDirection() {
        val c = Goals.contribution(goal, 500_000, "a1", "food", "Trip fund", ms(2026, 11, 13), null) { "new" }
        assertEquals(500_000, c.amount)
        assertEquals("g1", c.objectiveId)
        assertTrue(c.paid)
        assertEquals(-500_000, Goals.contribution(goal.copy(income = false), 500_000, "a1", "food", "x", 0, null) { "n" }.amount)
    }
}

class LoansTest {
    // you lent ₹10,000: loan income = true; first record money out, repayments money in
    private val lent = Objective("L1", ObjectiveKind.LOAN, "To Ravi", 0, 0, null, 0, null, null, null, null, true, false, false, "a1")
    private val borrowed = lent.copy(id = "L2", income = false)

    @Test fun lentLoanPartlyRepaid() {
        val all = listOf(tx("init", -1_000_000, loan = "L1"), tx("p1", 300_000, loan = "L1"))
        val p = Loans.progress(lent, all)
        assertEquals(r(10_000), p.total)
        assertEquals(r(3_000), p.repaid)
        assertEquals(r(7_000), p.outstanding)
        assertEquals(Paise.ZERO, p.overpaid)
        assertEquals(6, p.gauge.filled) // 30% of 20 segments
    }

    @Test fun borrowedLoanAndTheOffsetForInterest() {
        val all = listOf(tx("init", 1_000_000, loan = "L2"), tx("p1", -400_000, loan = "L2"))
        val p = Loans.progress(borrowed.copy(amount = 50_000), all) // ₹500 interest on top
        assertEquals(r(10_500), p.total)
        assertEquals(r(4_000), p.repaid)
        assertEquals(r(6_500), p.outstanding)
    }

    @Test fun overpaymentIsShownNotHidden() {
        val all = listOf(tx("init", -1_000_000, loan = "L1"), tx("p1", 1_200_000, loan = "L1"))
        val p = Loans.progress(lent, all)
        assertEquals(Paise.ZERO, p.outstanding)
        assertEquals(r(2_000), p.overpaid)
        assertFalse(p.gauge.over.not() && false)
    }

    @Test fun unpaidLinkedEntriesAreIgnored() {
        val all = listOf(tx("init", -1_000_000, loan = "L1"), tx("p1", 300_000, loan = "L1", paid = false))
        assertEquals(Paise.ZERO, Loans.progress(lent, all).repaid)
    }

    @Test fun repaymentAndLendMoreEntriesGoTheRightWay() {
        assertEquals(200_000, Loans.entry(lent, 200_000, lendMore = false, "a1", "c", "Back", 0, null) { "n" }.amount)
        assertEquals(-200_000, Loans.entry(lent, 200_000, lendMore = true, "a1", "c", "More", 0, null) { "n" }.amount)
        assertEquals(-200_000, Loans.entry(borrowed, 200_000, lendMore = false, "a1", "c", "Back", 0, null) { "n" }.amount)
        assertEquals("L1", Loans.entry(lent, 1, false, "a1", "c", "x", 0, null) { "n" }.objectiveLoanId)
    }

    @Test fun collectAllSetsItUnpaidSoItsNetIsZero() {
        val lentEntry = tx("e", -500_000, kind = TransactionKind.LENT)
        assertEquals(Paise(-500_000), Balances.balance(listOf(lentEntry), "a1"))
        val collected = Loans.collectAll(lentEntry, 1)
        assertEquals(Paise.ZERO, Balances.balance(listOf(collected), "a1"))
        assertEquals(Paise(-500_000), Balances.balance(listOf(Loans.uncollect(collected, 2)), "a1"))
    }

    @Test fun partialCollectMakesALongTermLoan() {
        val lentEntry = tx("e", -1_000_000, kind = TransactionKind.LENT, name = "Ravi")
        val ids = listOf("entry2", "loan1").iterator()
        val res = Loans.collectPart(lentEntry, 300_000, "a2", ms(2026, 11, 13), 4, { ids.next() }, { ids.next() })
        val ready = assertIs<CollectResult.Ready>(res)
        val loan = assertNotNull(ready.objective)
        assertEquals(ObjectiveKind.LOAN, loan.kind)
        assertTrue(loan.income) // opposite polarity of the entry: a lent entry gives a lent loan
        assertEquals(0, loan.amount)
        val initial = ready.updated.single()
        assertNull(initial.kind)
        assertEquals("Initial record", initial.name)
        assertEquals(loan.id, initial.objectiveLoanId)
        val repayment = ready.created.single()
        assertEquals(300_000, repayment.amount)
        assertEquals("a2", repayment.accountId)
        val p = Loans.progress(loan, listOf(initial, repayment))
        assertEquals(r(10_000), p.total)
        assertEquals(r(3_000), p.repaid)
        // money moved: -₹10,000 lent, +₹3,000 back
        assertEquals(Paise(-700_000), Paise(initial.amount + repayment.amount))
    }

    @Test fun partialCollectOfABorrowedEntry() {
        val b = tx("e", 1_000_000, kind = TransactionKind.BORROWED)
        val ready = assertIs<CollectResult.Ready>(Loans.collectPart(b, 250_000, "a1", 0, 0, { "x" }, { "y" }))
        assertFalse(ready.objective!!.income)
        assertEquals(-250_000, ready.created.single().amount)
    }

    @Test fun partialCollectRefusals() {
        val e = tx("e", -1_000_000, kind = TransactionKind.LENT)
        assertIs<CollectResult.Invalid>(Loans.collectPart(e, 1_000_000, "a1", 0, 0, { "x" }, { "y" })) // the whole amount
        assertIs<CollectResult.Invalid>(Loans.collectPart(e, 0, "a1", 0, 0, { "x" }, { "y" }))
        assertIs<CollectResult.Invalid>(Loans.collectPart(e.copy(paid = false), 5, "a1", 0, 0, { "x" }, { "y" }))
        assertIs<CollectResult.Invalid>(Loans.collectPart(tx("n", -100), 5, "a1", 0, 0, { "x" }, { "y" }))
        assertIs<CollectResult.Invalid>(Loans.collectPart(e.copy(objectiveLoanId = "L1"), 5, "a1", 0, 0, { "x" }, { "y" }))
    }
}

class InstalmentsTest {
    // ₹1,00,000 at 15% for 12 months, EMI ₹9,025 (letter), first EMI 5 Dec 2026, fee ₹1,000, GST 18%
    private val terms = LoanTerms("L9", 10_000_000, "15", 12, 902_500, ms(2026, 12, 5), 100_000, "18", false, emptyList(), "a1", "", null)

    @Test fun firstInstalment() {
        val first = Instalments.schedule(terms, IST).first()
        assertEquals(1, first.number)
        assertEquals(r(100_000), first.opening)
        assertEquals(Paise(125_000), first.interest)       // 100000 × 15% ÷ 12 = ₹1,250
        assertEquals(Paise(902_500 - 125_000), first.principalPart)
        assertEquals(Paise(22_500), first.gstOnInterest)   // 18% of ₹1,250
        assertEquals(Paise(902_500 + 22_500), first.totalDue)
        assertEquals(LocalDate.of(2026, 12, 5), Periods.date(first.dueMs, IST))
    }

    @Test fun balanceEndsAtZeroWithAnAdjustedLastInstalment() {
        val rows = Instalments.schedule(terms, IST)
        assertEquals(12, rows.size)
        assertEquals(terms.principal, rows.sumOf { it.principalPart.value })
        assertEquals(rows.last().opening, rows.last().principalPart)
        assertEquals(LocalDate.of(2027, 11, 5), Periods.date(rows.last().dueMs, IST))
    }

    @Test fun totalPayableAddsInterestGstAndFee() {
        val s = Instalments.summarize(terms, IST)
        val rows = Instalments.schedule(terms, IST)
        assertEquals(rows.sumOf { it.interest.value }, s.totalInterest.value)
        assertEquals(Paise(18_000), s.gstOnFee)
        assertEquals(s.principal.value + s.totalInterest.value + s.gstOnInterest.value + 100_000 + 18_000, s.totalPayable.value)
        assertEquals(Paise.ZERO, s.paidFromTicks)
    }

    @Test fun ticksAndTheFeeCountAsPaid() {
        val t = Instalments.withTick(terms.copy(feePaid = true), 1, true, 0).let { Instalments.withTick(it, 2, true, 0) }
        val s = Instalments.summarize(t, IST)
        val rows = Instalments.schedule(t, IST)
        assertEquals(rows[0].totalDue.value + rows[1].totalDue.value + 100_000 + 18_000, s.paidFromTicks.value)
        assertEquals(listOf(1, 2), t.paidInstalments)
        assertEquals(listOf(2), Instalments.withTick(t, 1, false, 0).paidInstalments)
        assertEquals(3, Instalments.next(t, IST)!!.number)
    }

    @Test fun paymentsLinkedToTheLoanAddToWhatIsRepaid() {
        val withPayment = Instalments.progress(terms, -902_500, IST)
        assertEquals(Paise(902_500), withPayment.repaid)
        // money coming in (a refund) is not a repayment
        assertEquals(Paise.ZERO, Instalments.progress(terms, 50_000, IST).repaid)
        val s = Instalments.summarize(terms, IST)
        assertEquals(s.totalPayable - Paise(902_500), withPayment.outstanding)
    }

    @Test fun instalmentsDueByTodayAreTickedOnSetup() {
        assertEquals(listOf(1, 2), Instalments.dueBy(terms, ms(2027, 1, 20), IST))
        assertTrue(Instalments.dueBy(terms, ms(2026, 11, 13), IST).isEmpty())
    }

    @Test fun suggestedEmiMatchesTheStandardFormula() {
        // ₹1,00,000 at 15% over 12 months: ₹9,025.83
        assertEquals(Paise(902_583), Instalments.suggestedEmi(r(100_000), "15", 12))
        assertEquals(Paise(833_333), Instalments.suggestedEmi(r(100_000), "0", 12))
    }

    @Test fun instalmentOnThe31stKeepsItsDay() {
        val t = terms.copy(firstEmiMs = ms(2026, 1, 31))
        val dates = Instalments.schedule(t, IST).take(3).map { Periods.date(it.dueMs, IST) }
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)), dates)
    }
}

class CalendarAndReminderTest {
    private val now = ms(2026, 11, 13, 10)

    @Test fun calendarShowsRealBillsByDueDateAndProjectsRepeats() {
        val rent = tx("rent", -1_500_000, ms(2026, 11, 28), kind = TransactionKind.SUBSCRIPTION, paid = false, repeat = Repeat.MONTHLY, length = 1, createdAnother = false, name = "Rent")
        val once = tx("once", -149_900, ms(2026, 11, 20), kind = TransactionKind.UPCOMING, paid = false, name = "Phone")
        val paid = tx("p", -50_000, ms(2026, 11, 14), kind = TransactionKind.UPCOMING, paid = true, originalDue = ms(2026, 11, 2), name = "Done")
        val days = BillCalendar.month(listOf(rent, once, paid), YearMonth.of(2026, 11), now, IST)
        assertEquals(30, days.size)
        assertEquals(listOf("Rent"), days[27].items.map { it.title })
        assertFalse(days[27].items.single().projected)
        assertEquals(Paise(1_500_000), days[27].toPay)
        assertEquals(BillStatus.Paid, days[1].items.single().status) // placed on the day it was due, 2 Nov
        // December shows the repeat that does not exist yet
        val dec = BillCalendar.month(listOf(rent), YearMonth.of(2026, 12), now, IST)
        val item = dec[27].items.single()
        assertTrue(item.projected)
        assertEquals(LocalDate.of(2026, 12, 28), dec[27].date)
    }

    @Test fun calendarRepeatsOnThe31stAndStopsAtTheEndDate() {
        val bill = tx("b", -100, ms(2026, 1, 31), kind = TransactionKind.REPETITIVE, paid = false, repeat = Repeat.MONTHLY, length = 1, createdAnother = false, end = ms(2026, 3, 15))
        val feb = BillCalendar.month(listOf(bill), YearMonth.of(2026, 2), now, IST)
        assertEquals(1, feb[27].items.size) // 28 Feb
        val mar = BillCalendar.month(listOf(bill), YearMonth.of(2026, 3), now, IST)
        assertTrue(mar.all { it.items.isEmpty() }) // the 31st is after the end date
    }

    @Test fun remindersForADay() {
        val today = LocalDate.of(2026, 11, 13)
        val items = listOf(
            tx("due", -100, ms(2026, 11, 13, 18), kind = TransactionKind.UPCOMING, paid = false, name = "Today bill"),
            tx("tom", -200, ms(2026, 11, 14, 8), kind = TransactionKind.SUBSCRIPTION, paid = false, name = "Tomorrow bill"),
            tx("later", -300, ms(2026, 11, 20), kind = TransactionKind.UPCOMING, paid = false),
            tx("paid", -400, ms(2026, 11, 13), kind = TransactionKind.UPCOMING, paid = true),
            tx("skip", -500, ms(2026, 11, 13), kind = TransactionKind.UPCOMING, paid = false, skip = true),
            tx("off", -600, ms(2026, 11, 13), kind = TransactionKind.UPCOMING, paid = false, notify = false),
            tx("plain", -700, ms(2026, 11, 13)),
            tx("late", -800, ms(2026, 11, 10), kind = TransactionKind.UPCOMING, paid = false),
        )
        val out = Reminders.forDay(items, today, IST)
        assertEquals(listOf("Today bill", "Tomorrow bill"), out.map { it.title })
        assertEquals(listOf(ReminderKind.DueToday, ReminderKind.DayBefore), out.map { it.kind })
        assertEquals("due|2026-11-13|DueToday", out[0].key)
        assertEquals(Paise(100), out[0].amount)
    }

    @Test fun nextFireIsTheNextNineAm() {
        assertEquals(ms(2026, 11, 13, 9), Reminders.nextFireMs(ms(2026, 11, 13, 7), IST, 9, 0))
        assertEquals(ms(2026, 11, 14, 9), Reminders.nextFireMs(ms(2026, 11, 13, 9), IST, 9, 0)) // exactly 9:00 now: tomorrow
        assertEquals(ms(2026, 11, 14, 9), Reminders.nextFireMs(ms(2026, 11, 13, 23), IST, 9, 0))
        // the day US clocks go forward, 9:00 is still 9:00 on the wall clock
        val fire = Reminders.nextFireMs(ms(2026, 3, 8, 1, zone = NY), NY, 9, 0)
        assertEquals(9, java.time.Instant.ofEpochMilli(fire).atZone(NY).hour)
    }
}
