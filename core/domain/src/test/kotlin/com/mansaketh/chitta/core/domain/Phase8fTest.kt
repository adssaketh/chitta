package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, IST).toInstant().toEpochMilli()
private fun r(whole: Long) = Paise.rupees(whole)
private val NOW = ms(2026, 11, 12) // Thursday 12 Nov 2026

private fun cat(id: String, name: String = id, income: Boolean = false, main: String? = null) =
    Category(id, name, null, null, null, 0, null, 0, income, null, main)
private fun acct(id: String, name: String) = Account(id, name, null, null, 0, null, 0, "INR", null, 2, null, null)

private fun tx(
    id: String, rupees: Long, date: Long = ms(2026, 11, 5), account: String = "a1", category: String = "food", sub: String? = null,
    note: String = "", kind: TransactionKind? = null, paid: Boolean = true, paired: String? = null, paise: Long = 0,
) = Transaction(
    id = id, pairedTransactionId = paired, name = id, amount = rupees * 100 + if (rupees < 0) -paise else paise, currency = "INR", note = note, categoryId = category, subCategoryId = sub,
    accountId = account, dateMs = date, modifiedAtMs = null, originalDueMs = null, income = rupees > 0, periodLength = null, repeat = null, endDateMs = null,
    upcomingNotification = null, kind = kind, paid = paid, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null,
    objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

private val CATS = listOf(
    cat("food", "Food"), cat("snacks", "Snacks", main = "food"), cat("delivery", "Food delivery"), cat("fuel", "Fuel"), cat("eating", "Eating out"),
    cat("shop", "Shopping"), cat("travel", "Travel"), cat("salary", "Salary", income = true),
)
private val ACCS = listOf(acct("hdfc", "HDFC Savings"), acct("axis", "Axis"), acct("cash", "Cash"))

private fun parse(text: String) = SearchSyntax.parse(text, CATS, ACCS, NOW, IST)

class SearchSyntaxTest {
    @Test fun plainTextIsLeftAloneAndNothingIsActive() {
        val p = parse("  lunch   at cafe ")
        assertEquals("lunch at cafe", p.text)
        assertFalse(p.filter.isActive)
        assertTrue(p.problems.isEmpty())
    }

    @Test fun theMasterPromptExample() {
        val p = parse("amt>500 cat:food acc:hdfc last:30d")
        assertEquals("", p.text)
        assertTrue(p.problems.isEmpty(), p.problems.toString())
        assertEquals(Paise(50_001), p.filter.minAmount) // strictly above ₹500.00
        assertNull(p.filter.maxAmount)
        assertEquals(setOf("food", "snacks"), p.filter.categoryIds) // "Food" is exact, so "Food delivery" is not pulled in; its sub-category is
        assertEquals(setOf("hdfc"), p.filter.accountIds)
        // 30 days = today and the 29 before (the same as the Insights "past 30 days")
        assertEquals(LocalDate.of(2026, 10, 14), Periods.date(p.filter.fromMs!!, IST))
        assertEquals(LocalDate.of(2026, 11, 13), Periods.date(p.filter.toMs!!, IST))
    }

    @Test fun amountOperators() {
        assertEquals(Paise(50_000), parse("amt>=500").filter.minAmount)
        assertEquals(Paise(49_999), parse("amt<500").filter.maxAmount)
        assertEquals(Paise(50_000), parse("amt<=500").filter.maxAmount)
        val range = parse("amt:100-500").filter
        assertEquals(Paise(10_000), range.minAmount)
        assertEquals(Paise(50_000), range.maxAmount)
        val exact = parse("amt:75.50").filter
        assertEquals(Paise(7_550), exact.minAmount)
        assertEquals(Paise(7_550), exact.maxAmount)
    }

    @Test fun amountsAllowCommasAndTwoDecimals() {
        assertEquals(Paise(125_051), parse("amt>1,250.50").filter.minAmount)
        assertEquals(Paise(2_500_000), parse("amt>=₹25,000").filter.minAmount)
    }

    @Test fun twoAmountFiltersNarrowEachOther() {
        val f = parse("amt>100 amt<500 amt>200").filter
        assertEquals(Paise(20_001), f.minAmount)
        assertEquals(Paise(49_999), f.maxAmount)
    }

    @Test fun aBadAmountIsReportedAndIgnored() {
        val p = parse("amt>abc lunch")
        assertEquals("lunch", p.text)
        assertFalse(p.filter.isActive)
        assertEquals(1, p.problems.size)
        assertTrue("abc" in p.problems[0])
        assertTrue(parse("amt:500-100").problems.isNotEmpty())
        assertTrue(parse("amt>100 amt<50").problems.single().contains("nothing between"))
    }

    @Test fun anExactCategoryNameWinsOverNamesThatContainIt() {
        assertEquals(setOf("food", "snacks"), parse("cat:food").filter.categoryIds)
        assertEquals(setOf("food", "snacks", "delivery"), parse("cat:fo").filter.categoryIds) // no exact "fo": every name containing it, with sub-categories
        assertEquals(setOf("delivery"), parse("cat:deliv").filter.categoryIds)
    }

    @Test fun aSubCategoryDoesNotBringItsMain() {
        assertEquals(setOf("snacks"), parse("cat:snacks").filter.categoryIds)
    }

    @Test fun commaAndRepeatedFiltersMeanEither() {
        assertEquals(setOf("fuel", "travel"), parse("cat:fuel,travel").filter.categoryIds)
        assertEquals(setOf("fuel", "travel"), parse("cat:fuel cat:travel").filter.categoryIds)
        assertEquals(setOf("hdfc", "axis"), parse("acc:hdfc,axis").filter.accountIds)
    }

    @Test fun quotesLetAValueHoldSpaces() {
        assertEquals(setOf("eating"), parse("cat:\"eating out\"").filter.categoryIds)
    }

    @Test fun aMinusLeavesThingsOut() {
        val f = parse("-cat:food -acc:cash -type:lent -note:rent").filter
        assertEquals(setOf("food", "snacks"), f.excludeCategoryIds)
        assertEquals(setOf("cash"), f.excludeAccountIds)
        assertEquals(setOf(SearchType.Lent), f.excludeTypes)
        assertEquals(listOf("rent"), f.excludeNotes)
        assertNull(f.categoryIds)
    }

    @Test fun anUnknownNameIsReported() {
        val p = parse("cat:nothing")
        assertFalse(p.filter.isActive)
        assertEquals("No category is called \"nothing\".", p.problems.single())
        assertEquals("No account is called \"zzz\".", parse("acc:zzz").problems.single())
    }

    @Test fun oneUnknownNameDoesNotDropTheKnownOnes() {
        val p = parse("cat:fuel,nothing")
        assertEquals(setOf("fuel"), p.filter.categoryIds)
        assertEquals(1, p.problems.size)
    }

    @Test fun anUnknownFilterWordIsReportedNotSearched() {
        val p = parse("foo:bar lunch")
        assertEquals("lunch", p.text)
        assertTrue(p.problems.single().startsWith("\"foo:\" is not a filter"))
    }

    @Test fun quotedWordsAndNonFiltersStayText() {
        assertEquals("re: invoice", parse("\"re: invoice\"").text)
        assertTrue(parse("\"re: invoice\"").problems.isEmpty())
        assertEquals("7:30 train", parse("7:30 train").text) // digits are not a filter key
        assertEquals("a<b", parse("a<b").text) // only "word:" is read as an attempt at a filter
        assertEquals("-5 off", parse("-5 off").text)
    }

    @Test fun filterKeysIgnoreCaseAndHaveLongNames() {
        val f = parse("AMT>10 Category:fuel account:axis").filter
        assertEquals(Paise(1_001), f.minAmount)
        assertEquals(setOf("fuel"), f.categoryIds)
        assertEquals(setOf("axis"), f.accountIds)
    }

    @Test fun typesAcceptSeveralAndRejectUnknown() {
        assertEquals(setOf(SearchType.Lent, SearchType.Borrowed), parse("type:lent,borrowed").filter.types)
        val p = parse("type:wishes")
        assertFalse(p.filter.isActive)
        assertTrue(p.problems.single().contains("spending, income, upcoming"))
    }

    @Test fun aMinusOnlyWorksWithFourFilters() {
        val p = parse("-amt>5 -last:3d")
        assertFalse(p.filter.isActive)
        assertEquals(2, p.problems.size)
    }

    @Test fun otherFiltersNeedAColon() {
        assertTrue(parse("cat>food").problems.single().contains("colon"))
        val p = parse("last>3d")
        assertFalse(p.filter.isActive)
        assertEquals(1, p.problems.size)
    }

    @Test fun lastCountsDaysWeeksMonthsAndYears() {
        fun from(s: String) = Periods.date(parse(s).filter.fromMs!!, IST)
        assertEquals(LocalDate.of(2026, 11, 12), from("last:1d")) // today only
        assertEquals(LocalDate.of(2026, 10, 30), from("last:2w")) // 14 days: 30 Oct to 12 Nov
        assertEquals(LocalDate.of(2026, 8, 13), from("last:3m")) // calendar months ending today
        assertEquals(LocalDate.of(2025, 11, 13), from("last:1y"))
        assertEquals(LocalDate.of(2026, 11, 13), Periods.date(parse("last:3m").filter.toMs!!, IST))
        assertTrue(parse("last:0d").problems.isNotEmpty())
        assertTrue(parse("last:5x").problems.isNotEmpty())
    }

    @Test fun lastAcrossAMonthEndAndLeapDay() {
        val mar1 = SearchSyntax.parse("last:1m", CATS, ACCS, ms(2028, 3, 31), IST) // 2028 is a leap year
        assertEquals(LocalDate.of(2028, 3, 1), Periods.date(mar1.filter.fromMs!!, IST))
        val feb29 = SearchSyntax.parse("last:1y", CATS, ACCS, ms(2028, 2, 29), IST)
        assertEquals(LocalDate.of(2027, 3, 1), Periods.date(feb29.filter.fromMs!!, IST)) // 29 Feb 2028 minus a year is 28 Feb 2027, plus one day
    }

    @Test fun fromAndToIncludeBothDays() {
        val f = parse("from:2026-10-01 to:2026-10-31").filter
        assertEquals(LocalDate.of(2026, 10, 1), Periods.date(f.fromMs!!, IST))
        assertEquals(LocalDate.of(2026, 11, 1), Periods.date(f.toMs!!, IST)) // exclusive end is the day after
        assertTrue(parse("from:01-10-2026").problems.single().contains("2026-10-01"))
        assertTrue(parse("from:2026-11-05 to:2026-11-01").problems.single().contains("nothing between"))
    }

    @Test fun lastAndFromTogetherKeepTheNarrowerWindow() {
        val f = parse("last:30d from:2026-11-01").filter
        assertEquals(LocalDate.of(2026, 11, 1), Periods.date(f.fromMs!!, IST))
        val g = parse("last:5d from:2026-01-01").filter
        assertEquals(LocalDate.of(2026, 11, 8), Periods.date(g.fromMs!!, IST))
    }

    @Test fun noteFilterMatchesAnyWord() {
        assertEquals(listOf("rent", "emi"), parse("note:rent,EMI").filter.notes)
    }
}

class SmartSearchApplyTest {
    private val entries = listOf(
        tx("lunch", -450, account = "hdfc", category = "food", note = "with team"),
        tx("pizza", -1_200, account = "hdfc", category = "delivery", date = ms(2026, 11, 10)),
        tx("chips", -60, account = "axis", category = "snacks", sub = null, date = ms(2026, 11, 11)),
        tx("petrol", -3_000, account = "axis", category = "fuel", date = ms(2026, 10, 20), note = "Rent a car trip"),
        tx("pay", 60_000, account = "hdfc", category = "salary", date = ms(2026, 11, 1)),
        tx("loan", -2_000, account = "cash", category = "shop", kind = TransactionKind.LENT, date = ms(2026, 11, 3)),
        tx("bill", -999, account = "axis", category = "shop", kind = TransactionKind.UPCOMING, paid = false, date = ms(2026, 11, 20)),
        tx("moveOut", -5_000, account = "hdfc", category = QuickAdd.TRANSFER_CATEGORY_ID, paired = "moveIn", date = ms(2026, 11, 4)),
    )

    private fun ids(text: String, tab: LedgerTab = LedgerTab.All): List<String> {
        val p = parse(text)
        val filter = LedgerFilter(tab = tab, text = p.text, smart = p.filter)
        return LedgerSearch.apply(entries, filter, CATS, ACCS).map { it.id }.sorted()
    }

    @Test fun noFiltersFindsEverything() = assertEquals(entries.map { it.id }.sorted(), ids(""))

    @Test fun amountIsStrictAndUsesTheAbsoluteValue() {
        assertEquals(listOf("lunch", "pay", "pizza").sorted(), ids("amt>=450 amt<=60000 cat:food,delivery,salary"))
        assertFalse("lunch" in ids("amt>450")) // exactly ₹450.00 is not above ₹450
        assertTrue("pay" in ids("amt>50000")) // income counts by its size
    }

    @Test fun categoryAndAccountTogether() {
        assertEquals(listOf("pizza"), ids("cat:delivery acc:hdfc"))
        assertEquals(listOf("chips", "lunch", "pizza"), ids("cat:fo,snacks amt<2000"))
    }

    @Test fun aMainCategoryFindsItsSubCategoryEntries() {
        val withSub = entries + tx("fries", -80, category = "food", sub = "snacks")
        val p = parse("cat:snacks")
        val found = LedgerSearch.apply(withSub, LedgerFilter(smart = p.filter), CATS, ACCS).map { it.id }.sorted()
        assertEquals(listOf("chips", "fries"), found)
    }

    @Test fun lastDaysUsesTheClock() {
        assertEquals(listOf("chips", "pizza"), ids("last:3d cat:fo,snacks")) // 10 to 12 Nov; lunch is on the 5th
        assertTrue("petrol" in ids("last:30d")) // 14 Oct to 12 Nov holds 20 Oct
        assertFalse("petrol" in ids("last:10d")) // 3 to 12 Nov does not
    }

    @Test fun dateRangeIncludesBothEnds() {
        assertEquals(listOf("petrol"), ids("from:2026-10-20 to:2026-10-20"))
        assertEquals(listOf("pay"), ids("from:2026-11-01 to:2026-11-01"))
    }

    @Test fun excludingLeavesOtherEntriesIn() {
        val rest = ids("-cat:food,snacks,delivery")
        assertFalse("lunch" in rest || "chips" in rest || "pizza" in rest)
        assertTrue("petrol" in rest && "pay" in rest)
        assertFalse("loan" in ids("-type:lent"))
        assertFalse("petrol" in ids("-acc:axis"))
    }

    @Test fun typeFilters() {
        assertEquals(listOf("loan"), ids("type:lent"))
        assertEquals(listOf("bill"), ids("type:upcoming"))
        assertEquals(listOf("moveOut"), ids("type:transfer"))
        assertEquals(listOf("pay"), ids("type:income"))
        val spending = ids("type:spending")
        assertFalse("pay" in spending || "bill" in spending || "moveOut" in spending)
        assertTrue("lunch" in spending && "petrol" in spending)
    }

    @Test fun noteFilter() {
        assertEquals(listOf("petrol"), ids("note:rent"))
        assertEquals(listOf("lunch"), ids("note:team,nothing"))
        assertFalse("petrol" in ids("-note:rent"))
    }

    @Test fun plainWordsStillSearchTitleNoteCategoryAndAccountWithFilters() {
        assertEquals(listOf("pizza"), ids("pizza acc:hdfc"))
        assertEquals(listOf("lunch", "pizza", "pay", "moveOut").sorted(), ids("hdfc").sorted())
    }

    @Test fun typedFiltersWorkOnTopOfTheTabs() {
        assertEquals(listOf("pay"), ids("acc:hdfc", LedgerTab.Income))
        assertTrue(ids("type:income", LedgerTab.Spending).isEmpty())
    }
}

class MonthReviewTest {
    // October 2026 against September, worked by hand.
    //   September: salary 50,000; food 10,000; shopping 8,000; travel 1,000  -> out 19,000
    //   October:   salary 60,000; food 12,000; shopping 5,000; travel 4,000; fuel 300 -> out 21,300, net 38,700, saved 64.5% -> 65
    //   Changes: travel +3,000 (+300%), food +2,000 (+20%), shopping -3,000 (-37.5% -> -38%), fuel +300 (under ₹500: not listed)
    private val entries = listOf(
        tx("s-pay", 50_000, ms(2026, 9, 1), category = "salary"), tx("s-food", -10_000, ms(2026, 9, 5)), tx("s-shop", -8_000, ms(2026, 9, 9), category = "shop"),
        tx("s-travel", -1_000, ms(2026, 9, 20), category = "travel"),
        tx("o-pay", 60_000, ms(2026, 10, 1), category = "salary"), tx("o-food", -12_000, ms(2026, 10, 5)), tx("o-shop", -5_000, ms(2026, 10, 9), category = "shop"),
        tx("o-travel", -4_000, ms(2026, 10, 20), category = "travel"), tx("o-fuel", -300, ms(2026, 10, 22), category = "fuel"),
        tx("o-move-out", -9_000, ms(2026, 10, 11), category = QuickAdd.TRANSFER_CATEGORY_ID, paired = "o-move-in"), // own transfer: not spending
        tx("nov", -700, ms(2026, 11, 2)),
    )
    private val now = ms(2026, 11, 3)

    private fun budget(id: String, name: String, amount: Long, repeat: Repeat? = Repeat.MONTHLY, start: Long = ms(2026, 9, 1, 0), end: Long = ms(2026, 9, 30, 0), rollover: Boolean = false, income: Boolean = false, categories: List<String>? = null) = Budget(
        id, name, r(amount).value, null, start, end, null, categories, null, income, false, false, 1, repeat, 0, null, false, 0, "a1",
        listOf(BudgetFilter.DEFAULT_FILTERS), null, false, rollover,
    )

    private val review = MonthReviews.review(YearMonth.of(2026, 10), entries, CATS, emptyList(), now, IST)

    @Test fun totalsMatchTheHandCalculation() {
        assertEquals(r(60_000), review.moneyIn)
        assertEquals(r(21_300), review.moneyOut) // the ₹9,000 transfer leg and November's entry are not counted
        assertEquals(r(38_700), review.net)
        assertEquals(65, review.savedPercent)
        assertEquals(Flow(r(50_000), r(19_000)), review.previous)
    }

    @Test fun biggestChangesUpAndDown() {
        assertEquals(listOf("travel", "food"), review.up.map { it.categoryId })
        assertEquals(listOf(r(3_000), r(2_000)), review.up.map { it.current - it.previous })
        assertEquals(listOf(300, 20), review.up.map { it.percent })
        assertEquals(listOf("shop"), review.down.map { it.categoryId })
        assertEquals(-38, review.down.single().percent)
    }

    @Test fun smallChangesAreNotListed() {
        assertFalse(review.up.any { it.categoryId == "fuel" }) // +₹300 is under ₹500
        val exactly = MonthReviews.review(
            YearMonth.of(2026, 10), listOf(tx("p", -1_000, ms(2026, 9, 3), category = "fuel"), tx("o", -1_500, ms(2026, 10, 3), category = "fuel")), CATS, emptyList(), now, IST,
        )
        assertEquals(listOf("fuel"), exactly.up.map { it.categoryId }) // exactly ₹500 is listed
    }

    @Test fun onlyTheTopThreeAreListed() {
        val prev = listOf("food", "fuel", "shop", "travel", "eating").mapIndexed { i, c -> tx("p$i", -1_000, ms(2026, 9, 3), category = c) }
        val cur = listOf("food", "fuel", "shop", "travel", "eating").mapIndexed { i, c -> tx("c$i", -(2_000L + i * 100), ms(2026, 10, 3), category = c) }
        val rv = MonthReviews.review(YearMonth.of(2026, 10), prev + cur, CATS, emptyList(), now, IST)
        assertEquals(3, rv.up.size)
        assertEquals(listOf("eating", "travel", "shop"), rv.up.map { it.categoryId }) // +1,400, +1,300, +1,200
    }

    @Test fun anEmptyPreviousMonthIsNotComparedWith() {
        val rv = MonthReviews.review(YearMonth.of(2026, 9), entries, CATS, emptyList(), now, IST) // August has no entries
        assertNull(rv.previous)
        assertTrue(rv.up.isEmpty() && rv.down.isEmpty())
        assertEquals(r(19_000), rv.moneyOut)
    }

    @Test fun noIncomeGivesNoSavedPercent() {
        val rv = MonthReviews.review(YearMonth.of(2026, 10), listOf(tx("o", -500, ms(2026, 10, 3))), CATS, emptyList(), now, IST)
        assertNull(rv.savedPercent)
        assertEquals(r(-500), rv.net)
    }

    @Test fun aRefundReducesItsCategory() {
        val rv = MonthReviews.review(YearMonth.of(2026, 10), listOf(tx("o", -1_000, ms(2026, 10, 3)), tx("refund", 400, ms(2026, 10, 9))), CATS, emptyList(), now, IST)
        assertEquals(r(600), rv.moneyOut)
    }

    @Test fun monthsListFinishedMonthsWithEntriesNewestFirst() {
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), MonthReviews.months(entries, now, IST))
        assertTrue(MonthReviews.months(listOf(tx("x", -1, ms(2026, 11, 2))), now, IST).isEmpty()) // the month that holds today is not finished
        val gap = listOf(tx("a", -1, ms(2026, 4, 2)), tx("b", -1, ms(2026, 9, 2)))
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 4)), MonthReviews.months(gap, now, IST)) // months with no entries are skipped
    }

    @Test fun homeLinksOnlyInTheFirstDays() {
        assertEquals(YearMonth.of(2026, 10), MonthReviews.homeMonth(entries, ms(2026, 11, 3), IST))
        assertNull(MonthReviews.homeMonth(entries, ms(2026, 11, 4), IST))
        assertNull(MonthReviews.homeMonth(entries, ms(2026, 11, 12), IST))
        assertEquals(YearMonth.of(2026, 12 - 1), MonthReviews.homeMonth(entries + tx("nov2", -5, ms(2026, 11, 8)), ms(2026, 12, 1), IST))
        assertNull(MonthReviews.homeMonth(listOf(tx("old", -1, ms(2026, 8, 2))), ms(2026, 11, 2), IST)) // October is empty
        assertEquals(YearMonth.of(2025, 12), MonthReviews.homeMonth(listOf(tx("dec", -1, ms(2025, 12, 5))), ms(2026, 1, 2), IST)) // year boundary
    }

    @Test fun budgetsAreHitOrMissedWithLeftAndOver() {
        // food budget ₹11,000 for October: spent 12,000 -> missed by 1,000. Shopping ₹6,000: spent 5,000 -> hit with 1,000 left.
        val food = budget("b-food", "Food", 11_000, categories = listOf("food"), start = ms(2026, 9, 1, 0))
        val shop = budget("b-shop", "Shopping", 6_000, categories = listOf("shop"), start = ms(2026, 9, 1, 0))
        val rv = MonthReviews.review(YearMonth.of(2026, 10), entries, CATS, listOf(shop, food), now, IST)
        assertEquals(listOf("Food", "Shopping"), rv.budgets.map { it.budget.name }) // missed first
        val f = rv.budgets[0]; val s = rv.budgets[1]
        assertFalse(f.hit); assertEquals(r(12_000), f.spent); assertEquals(r(1_000), f.over); assertEquals(Paise.ZERO, f.left)
        assertTrue(s.hit); assertEquals(r(5_000), s.spent); assertEquals(r(1_000), s.left); assertEquals(Paise.ZERO, s.over)
        assertEquals(Periods.startOfDay(LocalDate.of(2026, 10, 1), IST), f.period.startMs)
    }

    @Test fun spendingExactlyTheLimitIsHit() {
        val b = budget("b", "Food", 12_000, categories = listOf("food"))
        assertTrue(MonthReviews.review(YearMonth.of(2026, 10), entries, CATS, listOf(b), now, IST).budgets.single().hit)
    }

    @Test fun rolloverMovesTheLimitTheReviewJudgesAgainst() {
        // September limit 10,000, spent 10,000 -> carry 0. Use shopping: September limit 10,000 spent 8,000 -> carry 2,000, October limit 12,000.
        val b = budget("b", "Shopping", 10_000, categories = listOf("shop"), rollover = true)
        val res = MonthReviews.review(YearMonth.of(2026, 10), entries, CATS, listOf(b), now, IST).budgets.single()
        assertEquals(r(12_000), res.limit)
        assertEquals(r(5_000), res.spent)
        assertTrue(res.hit)
    }

    @Test fun aBudgetThatEndsInTheMonthIsJudgedThen() {
        // monthly from the 15th: Sep 15 to Oct 14 ended in October
        val b = budget("b", "Mid", 5_000, start = ms(2026, 9, 15, 0), categories = listOf("shop"))
        val res = MonthReviews.review(YearMonth.of(2026, 10), entries, CATS, listOf(b), now, IST).budgets.single()
        assertEquals(LocalDate.of(2026, 9, 15), Periods.date(res.period.startMs, IST))
        assertEquals(LocalDate.of(2026, 10, 15), Periods.date(res.period.endMs, IST))
        assertEquals(r(5_000), res.spent) // October's shopping: 5,000 on the 9th (the 9,000 transfer leg is not spending)
    }

    @Test fun anUnfinishedPeriodSavingsArchivedAndFutureBudgetsAreLeftOut() {
        val unfinished = budget("u", "Nov", 1_000, start = ms(2026, 11, 1, 0))
        val savings = budget("s", "Save", 1_000, income = true)
        val archived = budget("a", "Old", 1_000).copy(archived = true)
        val future = budget("f", "Dec", 1_000, start = ms(2026, 12, 1, 0))
        val rv = MonthReviews.review(YearMonth.of(2026, 10), entries, CATS, listOf(unfinished, savings, archived, future), now, IST)
        assertTrue(rv.budgets.isEmpty())
    }

    @Test fun aOneTimeBudgetShowsInTheMonthItEnded() {
        val b = budget("o", "Trip", 3_000, repeat = null, start = ms(2026, 10, 10, 0), end = ms(2026, 10, 25, 0), categories = listOf("travel"))
        assertEquals(r(4_000), MonthReviews.review(YearMonth.of(2026, 10), entries, CATS, listOf(b), now, IST).budgets.single().spent)
        assertTrue(MonthReviews.review(YearMonth.of(2026, 9), entries, CATS, listOf(b), now, IST).budgets.isEmpty())
    }
}
