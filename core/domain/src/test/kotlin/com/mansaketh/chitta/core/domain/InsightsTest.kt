package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, IST).toInstant().toEpochMilli()
private fun r(whole: Long) = Paise.rupees(whole)
private val NOW = ms(2026, 11, 12) // Thursday 12 Nov 2026
private val NOVEMBER = Period(ms(2026, 11, 1, 0), ms(2026, 12, 1, 0))

private fun cat(id: String, income: Boolean = false, main: String? = null) = Category(id, id, null, null, null, 0, null, 0, income, null, main)
private fun acct(id: String, currency: String = "INR") = Account(id, id, null, null, 0, null, 0, currency, null, 2, null, null)

/** Amounts here are rupees, negative for money out. */
private fun tx(
    id: String, rupees: Long, date: Long = ms(2026, 11, 5), account: String = "a1", category: String = "food", kind: TransactionKind? = null,
    paid: Boolean = true, sub: String? = null, paired: String? = null, loan: String? = null, goal: String? = null,
) = Transaction(
    id = id, pairedTransactionId = paired, name = id, amount = rupees * 100, currency = "INR", note = "", categoryId = category, subCategoryId = sub, accountId = account,
    dateMs = date, modifiedAtMs = null, originalDueMs = null, income = rupees > 0, periodLength = null, repeat = null, endDateMs = null,
    upcomingNotification = null, kind = kind, paid = paid, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = goal,
    objectiveLoanId = loan, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

private val CATS = listOf(cat("food"), cat("snacks", main = "food"), cat("shop"), cat("salary", income = true))

class RangesTest {
    @Test fun pastDaysCountsTodayAndTheDaysBefore() {
        val p = Ranges.resolve(SpendingRange.PastDays(30), emptyList(), NOW, IST)
        assertEquals(LocalDate.of(2026, 10, 14), Periods.date(p.startMs, IST))
        assertEquals(LocalDate.of(2026, 11, 13), Periods.date(p.endMs, IST)) // end is exclusive: today is the last day
        assertEquals(30, Periods.daysIn(p, IST))
    }

    @Test fun openEndedDateRangeRunsToToday() {
        val p = Ranges.resolve(SpendingRange.Dates(LocalDate.of(2026, 11, 1), null), emptyList(), NOW, IST)
        assertEquals(12, Periods.daysIn(p, IST))
        assertTrue(NOW in p)
    }

    @Test fun backwardsDateRangeIsOneDay() {
        val p = Ranges.resolve(SpendingRange.Dates(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 3)), emptyList(), NOW, IST)
        assertEquals(1, Periods.daysIn(p, IST))
    }

    @Test fun cycleIsTheMonthThatHoldsToday() {
        val p = Ranges.resolve(SpendingRange.Cycle(ms(2026, 1, 1, 0), Repeat.MONTHLY), emptyList(), NOW, IST)
        assertEquals(NOVEMBER, p)
    }

    @Test fun allTimeStartsAtTheFirstEntry() {
        val p = Ranges.resolve(SpendingRange.AllTime, listOf(tx("a", -1, date = ms(2024, 2, 29)), tx("b", -1, date = ms(2026, 11, 2))), NOW, IST)
        assertEquals(LocalDate.of(2024, 2, 29), Periods.date(p.startMs, IST))
        assertTrue(NOW in p)
    }

    @Test fun cycleIsComparedToDateWithTheSameDaysOfTheLastOne() {
        val cmp = Ranges.comparison(SpendingRange.Cycle(ms(2026, 1, 1, 0), Repeat.MONTHLY), NOW, IST)!!
        assertEquals(12, Periods.daysIn(cmp.current, IST))
        assertEquals(LocalDate.of(2026, 11, 1), Periods.date(cmp.current.startMs, IST))
        assertEquals(LocalDate.of(2026, 10, 1), Periods.date(cmp.previous.startMs, IST))
        assertEquals(12, Periods.daysIn(cmp.previous, IST)) // 1 to 12 Oct
    }

    @Test fun firstCycleHasNothingToCompareWith() {
        assertNull(Ranges.comparison(SpendingRange.Cycle(ms(2026, 11, 1, 0), Repeat.MONTHLY), NOW, IST))
    }

    @Test fun shortPreviousMonthIsNotOverrun() {
        // 31 Mar compared with February: the earlier month has only 28 days
        val cmp = Ranges.comparison(SpendingRange.Cycle(ms(2026, 1, 1, 0), Repeat.MONTHLY), ms(2026, 3, 31), IST)!!
        assertEquals(31, Periods.daysIn(cmp.current, IST))
        assertEquals(28, Periods.daysIn(cmp.previous, IST))
    }

    @Test fun pastDaysIsComparedWithTheStretchBefore() {
        val cmp = Ranges.comparison(SpendingRange.PastDays(7), NOW, IST)!!
        assertEquals(LocalDate.of(2026, 11, 6), Periods.date(cmp.current.startMs, IST))
        assertEquals(LocalDate.of(2026, 10, 30), Periods.date(cmp.previous.startMs, IST))
        assertEquals(cmp.current.startMs, cmp.previous.endMs)
    }

    @Test fun allTimeHasNoComparison() = assertNull(Ranges.comparison(SpendingRange.AllTime, NOW, IST))
}

class SummaryTest {
    private val entries = listOf(
        tx("e1", -1_000, ms(2026, 11, 5)),
        tx("e2", -2_000, ms(2026, 11, 6), category = "shop"),
        tx("e3", 649, ms(2026, 11, 7), category = "shop"),                      // a refund: reduces shop
        tx("e4", 10_000, ms(2026, 11, 1), category = "salary"),
        tx("e5", -250, ms(2026, 11, 8), sub = "snacks"),
        tx("t1", -5_000, ms(2026, 11, 9), category = "0", paired = "t2"),       // transfer legs: neither spending nor income
        tx("t2", 5_000, ms(2026, 11, 9), account = "a2", category = "0", paired = "t1"),
        tx("c1", 300, ms(2026, 11, 9), category = "0"),                         // balance correction
        tx("l1", -700, ms(2026, 11, 9), kind = TransactionKind.LENT),
        tx("b1", 400, ms(2026, 11, 9), kind = TransactionKind.BORROWED),
        tx("d1", -1_200, ms(2026, 11, 10), kind = TransactionKind.UPCOMING, paid = false),  // overdue
        tx("u1", -3_000, ms(2026, 11, 18), kind = TransactionKind.UPCOMING, paid = false),  // upcoming
        tx("u2", 500, ms(2026, 11, 20), kind = TransactionKind.UPCOMING, paid = false, category = "salary"),
    )

    private val s = Insights.summary(entries, CATS, NOVEMBER, NOW, IST)

    @Test fun totalsLeaveOutTransfersCorrectionsAndDebts() {
        assertEquals(r(2_601), s.expense)   // 1,000 + 250 + 2,000 - 649
        assertEquals(r(10_000), s.income)
        assertEquals(r(7_399), s.net)
    }

    @Test fun savedPercentIsNetOverIncome() = assertEquals(74, s.savedPercent) // 73.99 rounds to 74

    @Test fun savedPercentIsAbsentWhenNothingCameIn() {
        val none = Insights.summary(listOf(tx("x", -100)), CATS, NOVEMBER, NOW, IST)
        assertNull(none.savedPercent)
    }

    @Test fun otherTypesHaveTheirOwnTotals() {
        assertEquals(r(300), s.balanceCorrection)
        assertEquals(r(700), s.lent)
        assertEquals(r(400), s.borrowed)
        assertEquals(r(1_200), s.overdue)
        assertEquals(r(2_500), s.upcoming) // 3,000 to pay less 500 to receive
    }

    @Test fun accountFilterNarrowsEverything() {
        val onlyA2 = Insights.summary(entries, CATS, NOVEMBER, NOW, IST, SummaryFilter(setOf("a2")))
        assertEquals(Paise.ZERO, onlyA2.expense)
        assertEquals(Paise.ZERO, onlyA2.income)
        assertEquals(1, onlyA2.entryCount)
    }

    @Test fun entriesOutsideThePeriodAreIgnored() {
        val oct = Insights.summary(entries + tx("o", -9_999, ms(2026, 10, 31)), CATS, NOVEMBER, NOW, IST)
        assertEquals(r(2_601), oct.expense)
    }

    @Test fun categoryTotalsAreBiggestFirstAndRefundsReduce() {
        val out = Insights.categoryTotals(entries, CATS, NOVEMBER, income = false)
        assertEquals(listOf("shop", "food"), out.map { it.categoryId })
        assertEquals(r(1_351), out[0].amount)
        assertEquals(r(1_250), out[1].amount) // the snack counts under Food
        assertEquals(listOf("salary"), Insights.categoryTotals(entries, CATS, NOVEMBER, income = true).map { it.categoryId })
    }

    @Test fun categoryBarsAddUpToTheExpenseTotal() {
        val sum = Insights.categoryTotals(entries, CATS, NOVEMBER, income = false).sumOf { it.amount.value }
        assertEquals(s.expense.value, sum)
    }

    @Test fun drillSplitsAMainCategoryBySubCategory() {
        val d = Insights.drill(entries, CATS, NOVEMBER, "food", income = false)
        assertEquals(listOf("food", "snacks"), d.map { it.categoryId })
        assertEquals(r(1_000), d[0].amount)
        assertEquals(r(250), d[1].amount)
        assertEquals(listOf("e5", "e1"), Insights.entriesOf(entries, CATS, NOVEMBER, "food", income = false).map { it.id }) // newest first
        assertEquals(listOf("e5"), Insights.entriesOf(entries, CATS, NOVEMBER, "snacks", income = false).map { it.id })
    }

    @Test fun biggestChangesAgainstLastMonth() {
        val before = entries + tx("o1", -800, ms(2026, 10, 5))
        val cmp = Comparison(Period(ms(2026, 11, 1, 0), ms(2026, 11, 13, 0)), Period(ms(2026, 10, 1, 0), ms(2026, 10, 13, 0)))
        val ch = Insights.changes(before, CATS, cmp)
        assertEquals(listOf("shop", "food"), ch.map { it.categoryId })
        assertNull(ch[0].percent)               // shop had nothing last month
        assertEquals(r(450), ch[1].current - ch[1].previous)
        assertEquals(56, ch[1].percent)         // 450 / 800 = 56.25%
    }

    @Test fun aCategoryThatFellShowsANegativePercent() {
        val e = listOf(tx("a", -1_000, ms(2026, 10, 5)), tx("b", -750, ms(2026, 11, 5)))
        val cmp = Comparison(Period(ms(2026, 11, 1, 0), ms(2026, 11, 13, 0)), Period(ms(2026, 10, 1, 0), ms(2026, 10, 13, 0)))
        assertEquals(-25, Insights.changes(e, CATS, cmp).single().percent)
    }
}

class HistoryTest {
    private val e = listOf(
        tx("s", -4_000, ms(2026, 9, 10)), tx("s2", 20_000, ms(2026, 9, 1), category = "salary"),
        tx("o", -6_000, ms(2026, 10, 10)), tx("o2", 20_000, ms(2026, 10, 1), category = "salary"),
        tx("n", -12_000, ms(2026, 11, 10)),
    )

    @Test fun monthlySeriesEndsWithTheCurrentMonth() {
        val p = History.series(e, CATS, Bucket.Month, 3, NOW, IST)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), p.map { YearMonth.from(it.start) })
        assertEquals(listOf(r(4_000), r(6_000), r(12_000)), p.map { it.moneyOut })
        assertEquals(listOf(false, false, true), p.map { it.isCurrent })
    }

    @Test fun cumulativeNetRunsFromTheFirstBucket() {
        val p = History.series(e, CATS, Bucket.Month, 3, NOW, IST)
        assertEquals(listOf(r(16_000), r(30_000), r(18_000)), p.map { it.cumulativeNet })
    }

    @Test fun currentMonthIsProjectedAtTodaysPace() {
        val cur = History.series(e, CATS, Bucket.Month, 3, NOW, IST).last()
        assertEquals(r(30_000), cur.projectedOut) // 12,000 over 12 days, 30 days in November
        assertNull(History.series(e, CATS, Bucket.Month, 3, NOW, IST).first().projectedOut)
    }

    @Test fun noProjectionBeforeDaySeven() {
        assertNull(History.series(e, CATS, Bucket.Month, 3, ms(2026, 11, 3), IST).last().projectedOut)
    }

    @Test fun averageLeavesOutTheMonthStillFilling() {
        val avg = History.average(History.series(e, CATS, Bucket.Month, 3, NOW, IST))!!
        assertEquals(r(5_000), avg.moneyOut) // (4,000 + 6,000) / 2
        assertEquals(r(20_000), avg.moneyIn)
        assertNull(History.average(History.series(e, CATS, Bucket.Month, 1, NOW, IST)))
    }

    @Test fun weeksStartOnTheChosenDay() {
        val sun = History.series(e, CATS, Bucket.Week, 1, NOW, IST, firstDay = DayOfWeek.SUNDAY).single()
        val mon = History.series(e, CATS, Bucket.Week, 1, NOW, IST, firstDay = DayOfWeek.MONDAY).single()
        assertEquals(LocalDate.of(2026, 11, 8), sun.start)
        assertEquals(LocalDate.of(2026, 11, 9), mon.start)
    }

    @Test fun leapDayFallsInFebruary() {
        val p = History.series(listOf(tx("l", -500, ms(2028, 2, 29))), CATS, Bucket.Month, 2, ms(2028, 3, 1), IST)
        assertEquals(r(500), p.first().moneyOut)
        assertEquals(Paise.ZERO, p.last().moneyOut)
    }

    @Test fun yearBoundaryBuckets() {
        val p = History.series(listOf(tx("y", -500, ms(2026, 12, 31, 23, 59)), tx("z", -700, ms(2027, 1, 1, 0, 1))), CATS, Bucket.Month, 2, ms(2027, 1, 15), IST)
        assertEquals(listOf(r(500), r(700)), p.map { it.moneyOut })
    }

    @Test fun bucketsSuitTheLengthOfTheRange() {
        assertEquals(Bucket.Day, History.bucketFor(NOVEMBER, IST))
        assertEquals(Bucket.Week, History.bucketFor(Period(ms(2026, 1, 1, 0), ms(2026, 11, 1, 0)), IST))
        assertEquals(Bucket.Month, History.bucketFor(Period(ms(2024, 1, 1, 0), ms(2026, 11, 1, 0)), IST))
    }
}

class HeatmapTest {
    private val nov = YearMonth.of(2026, 11) // 1 Nov 2026 is a Sunday
    private val e = listOf(
        tx("a", -1_000, ms(2026, 11, 5)), tx("b", -4_000, ms(2026, 11, 6)), tx("c", -3_000, ms(2026, 11, 7)),
        tx("d", -2_000, ms(2026, 11, 8)), tx("t", -9_000, ms(2026, 11, 9), category = "0", paired = "x"), // a transfer is not spending
    )

    @Test fun weeksStartOnTheChosenDay() {
        val mon = Heatmap.month(e, CATS, nov, DayOfWeek.MONDAY, IST)
        assertEquals(6, mon.size)
        assertEquals(6, mon[0].count { it.date == null })
        assertEquals(LocalDate.of(2026, 11, 1), mon[0][6].date)
        val sun = Heatmap.month(e, CATS, nov, DayOfWeek.SUNDAY, IST)
        assertEquals(5, sun.size)
        assertEquals(LocalDate.of(2026, 11, 1), sun[0][0].date)
        assertTrue(sun.all { it.size == 7 })
    }

    @Test fun levelsAreSharesOfTheHeaviestDay() {
        val cells = Heatmap.month(e, CATS, nov, DayOfWeek.SUNDAY, IST).flatten().filter { it.date != null }.associateBy { it.date!!.dayOfMonth }
        assertEquals(1, cells[5]!!.level)  // 1,000 of 4,000
        assertEquals(4, cells[6]!!.level)
        assertEquals(3, cells[7]!!.level)
        assertEquals(2, cells[8]!!.level)
        assertEquals(0, cells[9]!!.level)  // only a transfer
        assertEquals(0, cells[1]!!.level)
    }

    @Test fun aMonthWithNoSpendingIsAllLevelZero() {
        assertTrue(Heatmap.month(emptyList(), CATS, nov, DayOfWeek.MONDAY, IST).flatten().all { it.level == 0 })
    }

    @Test fun februaryOfALeapYearHasTwentyNineDays() {
        val cells = Heatmap.month(emptyList(), CATS, YearMonth.of(2028, 2), DayOfWeek.MONDAY, IST).flatten().filter { it.date != null }
        assertEquals(29, cells.size)
    }
}

class NetWorthTest {
    private val accounts = listOf(acct("a1"), acct("a2", "USD"))
    private val base = listOf(tx("in", 50_000, ms(2026, 10, 20), category = "salary"), tx("out", -10_000, ms(2026, 11, 3)))

    @Test fun accountsAddRupeeAccountsOnly() {
        val n = NetWorth.at(NOW, base + tx("usd", 9_999, ms(2026, 11, 4), account = "a2", category = "salary"), accounts, emptyList())
        assertEquals(r(40_000), n.accounts)
        assertEquals(r(40_000), n.total)
        assertEquals(1, n.otherCurrencyAccounts)
    }

    @Test fun lendingAndBorrowingDoNotChangeNetWorth() {
        val e = base + tx("lent", -2_000, ms(2026, 11, 5), kind = TransactionKind.LENT) + tx("borrow", 1_500, ms(2026, 11, 6), kind = TransactionKind.BORROWED)
        val n = NetWorth.at(NOW, e, accounts, emptyList())
        assertEquals(r(39_500), n.accounts)
        assertEquals(r(2_000), n.owedToYou)
        assertEquals(r(1_500), n.youOwe)
        assertEquals(r(40_000), n.total)
    }

    @Test fun collectedLoansAreNoLongerOwed() {
        val e = base + tx("lent", -2_000, ms(2026, 11, 5), kind = TransactionKind.LENT, paid = false) // collected in full: unpaid, as in Paddhu
        val n = NetWorth.at(NOW, e, accounts, emptyList())
        assertEquals(Paise.ZERO, n.owedToYou)
    }

    @Test fun longTermLoanOutstandingIsOwedToYou() {
        val loan = com.mansaketh.chitta.core.model.Objective("L1", com.mansaketh.chitta.core.model.ObjectiveKind.LOAN, "Ravi", 0, 0, null, 0, null, null, null, null, true, false, false, "a1")
        val e = base + tx("first", -5_000, ms(2026, 11, 4), loan = "L1") + tx("back", 1_000, ms(2026, 11, 8), loan = "L1")
        val n = NetWorth.at(NOW, e, accounts, listOf(loan))
        assertEquals(r(36_000), n.accounts)
        assertEquals(r(4_000), n.owedToYou)
        assertEquals(r(40_000), n.total)
    }

    @Test fun pastMonthsShowWhatTheBookSaidThen() {
        val s = NetWorth.series(base, accounts, emptyList(), NOW, IST, months = 2)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), s.map { it.month })
        assertEquals(r(50_000), s[0].figures.total) // the ₹10,000 spent on 3 Nov is not there yet
        assertEquals(r(40_000), s[1].figures.total)
        assertEquals(NOW, s[1].asOfMs)
    }

    @Test fun negativeBalanceIsKept() {
        assertEquals(r(-300), NetWorth.at(NOW, listOf(tx("x", -300)), accounts, emptyList()).total)
    }

    @Test fun anEmptyBookIsZero() = assertEquals(Paise.ZERO, NetWorth.at(NOW, emptyList(), emptyList(), emptyList()).total)
}

class HomeFiguresTest {
    private fun budget(id: String = "b1", amount: Long = 6_000_000, income: Boolean = false, pinned: Boolean = false, order: Int = 0, archived: Boolean = false, repeat: Repeat? = Repeat.MONTHLY, start: Long = ms(2026, 11, 1, 0), end: Long = ms(2026, 12, 1, 0) - 1) =
        com.mansaketh.chitta.core.model.Budget(id, id, amount, null, start, end, null, null, null, income, archived, false, 1, repeat, 0, null, pinned, order, "0", listOf(com.mansaketh.chitta.core.model.BudgetFilter.DEFAULT_FILTERS), null, false)

    // The reference figures of master section 12 and the design (page 9): ₹60,000 budget, ₹26,520 spent by day 12, ₹5,978 of bills still to come.
    private val entries = listOf(
        tx("spent", -26_520, ms(2026, 11, 10)),
        tx("bill1", -4_000, ms(2026, 11, 18), kind = TransactionKind.UPCOMING, paid = false),
        tx("bill2", -1_978, ms(2026, 11, 25), kind = TransactionKind.UPCOMING, paid = false),
    )

    @Test fun safeToSpendMatchesTheWorkedExample() {
        val h = HomeFigures.hero(budget(), entries, CATS, NOW, IST)
        assertEquals(r(26_520), h.summary.spent)
        assertEquals(r(5_978), h.billsDue)
        assertEquals(r(27_502), h.safe)
    }

    @Test fun perDayAndRangeMatchTheWorkedExample() {
        val h = HomeFigures.hero(budget(), entries, CATS, NOW, IST)
        assertEquals(18, h.summary.daysLeft)
        assertEquals(Paise(152_789), h.perDay)   // ₹27,502 ÷ 18 = ₹1,527.89, shown as ₹1,528
        assertEquals(com.mansaketh.chitta.core.money.RangeResult.Days(12), h.range)
        assertEquals(6, h.rangeShortDays)
    }

    @Test fun ringHasFiftyTicksAndTheSameSafePercentAsTheDesign() {
        val ring = HomeFigures.hero(budget(), entries, CATS, NOW, IST).ring!!
        assertEquals(50, ring.spent + ring.bills + ring.safe)
        assertEquals(46, ring.safePercent) // "46% safe" on page 9
    }

    @Test fun overBudgetHasNoPerDayAndAFullRing() {
        val h = HomeFigures.hero(budget(amount = 2_000_000), entries, CATS, NOW, IST)
        assertTrue(h.safe.isNegative)
        assertNull(h.perDay)
        assertTrue(h.ring!!.over)
    }

    @Test fun cashCheckMatchesTheWorkedExample() {
        val e = listOf(
            tx("bank", 144_460, ms(2026, 11, 1), category = "salary"),
            tx("card", -12_480, ms(2026, 11, 18), kind = TransactionKind.UPCOMING, paid = false),
            tx("other", -5_978, ms(2026, 11, 25), kind = TransactionKind.UPCOMING, paid = false),
            tx("transfer", -9_000, ms(2026, 11, 20), kind = TransactionKind.UPCOMING, paid = false, category = "0", paired = "x"), // not a bill
        )
        val cash = HomeFigures.cashCheck(e, listOf(acct("a1")), setOf("a1"), NOVEMBER, r(25_250))!!
        assertEquals(r(144_460), cash.balances)
        assertEquals(r(18_458), cash.bills)
        assertEquals(r(100_752), cash.value)
    }

    @Test fun cashCheckNeedsAMarkedAccount() {
        assertNull(HomeFigures.cashCheck(entries, listOf(acct("a1")), emptySet(), NOVEMBER, Paise.ZERO))
        assertNull(HomeFigures.cashCheck(entries, listOf(acct("a2", "USD")), setOf("a2"), NOVEMBER, Paise.ZERO)) // rupee accounts only
    }

    @Test fun goalSavingsIsWhatSavingsGoalsStillNeedEachMonth() {
        fun goal(id: String, income: Boolean, end: Long?) = com.mansaketh.chitta.core.model.Objective(id, com.mansaketh.chitta.core.model.ObjectiveKind.GOAL, id, 1_200_000, 0, null, 0, end, null, null, null, income, false, false, "a1")
        val goals = listOf(goal("trip", true, ms(2027, 1, 12)), goal("open", true, null), goal("spend", false, ms(2027, 1, 12)))
        assertEquals(r(6_000), HomeFigures.goalSavings(goals, emptyList(), NOW, IST)) // ₹12,000 over 2 months
    }

    @Test fun quietAccountsAreEstimates() {
        val e = listOf(tx("x", -1, ms(2026, 11, 10), account = "a1"), tx("y", -1, ms(2026, 11, 1), account = "a2"))
        val stale = HomeFigures.staleSpendingAccounts(e, listOf(acct("a1"), acct("a2"), acct("a3"), acct("a4")), setOf("a1", "a2", "a3"), NOW, IST)
        assertEquals(listOf("a2", "a3"), stale.map { it.id }) // a1 is fresh, a4 is not marked
    }

    @Test fun pickingTheHeroBudget() {
        val list = listOf(budget("old", order = 0), budget("pin", pinned = true, order = 5), budget("save", income = true), budget("gone", archived = true), budget("later", start = ms(2027, 1, 1, 0), end = ms(2027, 1, 31, 0), repeat = null))
        assertEquals("pin", HomeFigures.currentBudget(list, NOW, IST)!!.id)
        assertEquals("old", HomeFigures.currentBudget(list, NOW, IST, preferredId = "old")!!.id)
        assertEquals("pin", HomeFigures.currentBudget(list, NOW, IST, preferredId = "save")!!.id) // a savings budget cannot lead
        assertNull(HomeFigures.currentBudget(emptyList(), NOW, IST))
        assertEquals(listOf("pin", "old"), HomeFigures.selectableBudgets(list, NOW, IST).map { it.id })
    }

    @Test fun nextBillIsTheEarliestOpenOneAndOverdueComesFirst() {
        val e = entries + tx("late", -500, ms(2026, 11, 9), kind = TransactionKind.UPCOMING, paid = false) + tx("done", -100, ms(2026, 11, 2), kind = TransactionKind.UPCOMING)
        assertEquals("late", HomeFigures.nextBill(e)!!.id)
        assertNull(HomeFigures.nextBill(listOf(tx("done", -100, kind = TransactionKind.UPCOMING))))
    }
}

class LayoutsTest {
    @Test fun defaultHomeIsCalm() {
        assertEquals(listOf(HomeSection.Budgets, HomeSection.Recent), HomeLayout.DEFAULT.shown.map { it.section })
        assertEquals(HomeSection.entries.toList(), HomeLayout.DEFAULT.sections.map { it.section })
    }

    @Test fun homeLayoutRoundTripsIncludingAwkwardIds() {
        val l = HomeLayout.DEFAULT.toggle(HomeSection.Spending).move(HomeSection.Heatmap, -3)
            .withAccounts(HomeSection.Spending, setOf("a|1", "b;2", "c,3", "100%")).withRange(HomeSection.Spending, 90)
        assertEquals(l, HomeLayout.decode(l.encode()))
    }

    @Test fun readingIsForgiving() {
        val l = HomeLayout.decode("nonsense;recent|1||;goals|0|;recent|0||;budgets")
        assertEquals(HomeSection.entries.size, l.sections.size)
        assertEquals(HomeSection.Recent, l.sections[0].section) // the saved order is kept
        assertTrue(l.sections[0].visible)                         // a repeated name is ignored
        assertEquals(HomeLayout.DEFAULT, HomeLayout.decode(null))
        assertEquals(HomeLayout.DEFAULT, HomeLayout.decode(""))
    }

    @Test fun moveStopsAtTheEnds() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.move(HomeSection.Budgets, -1))
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.move(HomeSection.Heatmap, 1))
        assertEquals(HomeSection.Recent, HomeLayout.DEFAULT.move(HomeSection.Recent, -1).sections[1].section)
    }

    @Test fun rangeIsKeptOnlyWhereASectionHasOne() {
        val l = HomeLayout.decode("budgets|1||45;spending|1||45")
        assertNull(l.sections.first { it.section == HomeSection.Budgets }.rangeDays)
        assertEquals(45, l.sections.first { it.section == HomeSection.Spending }.rangeDays)
    }

    @Test fun aTabNeverAppearsTwice() {
        val t = TabLayout.DEFAULT.set(0, TabKey.Plan) // Plan was in slot 2: the two swap
        assertEquals(listOf(TabKey.Plan, TabKey.Ledger, TabKey.Home, TabKey.Insights), t.slots)
        val swapped = TabLayout.DEFAULT.set(3, TabKey.Accounts)
        assertEquals(listOf(TabKey.Home, TabKey.Ledger, TabKey.Plan, TabKey.Accounts), swapped.slots)
    }

    @Test fun theFirstTabFollowsWhenItsTabIsReplaced() {
        val t = TabLayout.DEFAULT.withFirst(TabKey.Insights).set(3, TabKey.Bills)
        assertEquals(TabKey.Home, t.first)
        assertNotNull(TabLayout.DEFAULT.withFirst(TabKey.Accounts)) // not a tab: ignored
        assertEquals(TabKey.Home, TabLayout.DEFAULT.withFirst(TabKey.Accounts).first)
    }

    @Test fun tabLayoutRoundTripsAndRejectsDamage() {
        val t = TabLayout.DEFAULT.set(1, TabKey.Goals).withFirst(TabKey.Goals)
        assertEquals(t, TabLayout.decode(t.encode()))
        assertEquals(TabLayout.DEFAULT, TabLayout.decode("home,home,plan,insights|home"))
        assertEquals(TabLayout.DEFAULT, TabLayout.decode("home,ledger|home"))
        assertEquals(TabKey.Home, TabLayout.decode("home,ledger,plan,insights|accounts").first)
    }

    @Test fun moreShortcutsKeepTheirOrderAndGainNewOnes() {
        val moved = MoreOrder.move(MoreOrder.DEFAULT, MoreItem.Import, -3)
        assertEquals(MoreItem.Import, moved[0])
        assertEquals(MoreItem.Accounts, moved[3]) // a move swaps two neighbours-by-distance, it does not shift the rest
        assertEquals(moved, MoreOrder.decode(MoreOrder.encode(moved)))
        assertEquals(MoreOrder.DEFAULT, MoreOrder.decode(""))
        assertEquals(listOf(MoreItem.Categories) + MoreItem.entries.filter { it != MoreItem.Categories }, MoreOrder.decode("categories,gone"))
        assertEquals(MoreOrder.DEFAULT, MoreOrder.move(MoreOrder.DEFAULT, MoreItem.Accounts, -1))
    }
}
