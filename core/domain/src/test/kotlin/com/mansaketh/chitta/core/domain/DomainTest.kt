package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.TitleMemory
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, IST).toInstant().toEpochMilli()

private fun acct(id: String, name: String = id, currency: String = "INR") =
    Account(id, name, null, null, 0, null, 0, currency, null, 2, null, null)

private fun cat(id: String, income: Boolean = false, main: String? = null, order: Int = 0, name: String = id) =
    Category(id, name, null, null, null, 0, null, order, income, null, main)

private fun tx(
    id: String, amount: Long, account: String = "a1", category: String = "food", date: Long = ms(2026, 11, 12),
    kind: TransactionKind? = null, paid: Boolean = true, skip: Boolean = false, paired: String? = null,
    name: String = id, note: String = "",
) = Transaction(
    id = id, pairedTransactionId = paired, name = name, amount = amount, currency = "INR", note = note, categoryId = category,
    subCategoryId = null, accountId = account, dateMs = date, modifiedAtMs = null, originalDueMs = null, income = amount > 0,
    periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null, kind = kind, paid = paid,
    createdAnotherFuture = null, skipPaid = skip, methodAdded = null, objectiveId = null, objectiveLoanId = null,
    excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

class AmountExpressionTest {
    private fun ev(s: String) = AmountExpression.evaluate(s)?.value

    @Test fun plainNumbersAndDecimals() {
        assertEquals(48_600L, ev("486"))
        assertEquals(61_334L, ev("613.34"))
        assertEquals(61_330L, ev("613.3"))
        assertEquals(61_300L, ev("613."))
    }

    @Test fun additionIsExact() {
        // 0.1 + 0.2 must be exactly 0.30 (a Double gives 0.30000000000000004).
        assertEquals(30L, ev("0.1+0.2"))
        assertEquals(155_050L, ev("1200+350.5"))
    }

    @Test fun precedenceAndOperators() {
        assertEquals(1_400L, ev("2+3×4")) // 14
        assertEquals(500L, ev("10−5")) // 5
        assertEquals(500L, ev("10-5"))
        assertEquals(333L, ev("10÷3")) // 3.333... rounds to 3.33
    }

    @Test fun halfEvenRounding() {
        assertEquals(12L, ev("0.125")) // would need 3 decimals; digit() blocks it, evaluate still rounds half-even: 0.12
        assertEquals(14L, ev("0.135"))
    }

    @Test fun incompleteOrInvalidIsNull() {
        assertNull(ev(""))
        assertNull(ev("+5"))
        assertNull(ev("5÷0"))
        assertNull(ev("5−10")) // negative
    }

    @Test fun trailingOperatorIsIgnored() {
        assertEquals(48_600L, ev("486+"))
    }

    @Test fun keypadEditing() {
        var s = ""
        s = AmountExpression.digit(s, '4'); s = AmountExpression.digit(s, '8'); s = AmountExpression.digit(s, '6')
        assertEquals("486", s)
        s = AmountExpression.operator(s, '+'); s = AmountExpression.operator(s, '−')
        assertEquals("486−", s) // second operator replaces the first
        s = AmountExpression.dot(s)
        assertEquals("486−0.", s)
        s = AmountExpression.digit(s, '5'); s = AmountExpression.digit(s, '5'); s = AmountExpression.digit(s, '5')
        assertEquals("486−0.55", s) // two decimals at most
        assertEquals("486−0.5", AmountExpression.backspace(s))
        assertEquals("5", AmountExpression.digit(AmountExpression.digit("", '0'), '5')) // no leading zero
        assertEquals("", AmountExpression.operator("", '+'))
    }
}

class BalancesTest {
    @Test fun normalAndLentAndBorrowedCount() {
        val all = listOf(
            tx("t1", 6_000_000, category = "pay"),
            tx("t2", -61_334),
            tx("t3", -200_000, kind = TransactionKind.LENT),
        )
        assertEquals(Paise(6_000_000 - 61_334 - 200_000), Balances.balance(all, "a1"))
    }

    @Test fun upcomingCountsOnlyOncePaidAndNotSkipped() {
        val all = listOf(
            tx("u1", -149_900, kind = TransactionKind.UPCOMING, paid = false),
            tx("u2", -149_900, kind = TransactionKind.UPCOMING, paid = true),
            tx("u3", -149_900, kind = TransactionKind.SUBSCRIPTION, paid = true, skip = true),
        )
        assertEquals(Paise(-149_900), Balances.balance(all, "a1"))
    }

    @Test fun balancesPerAccount() {
        val all = listOf(tx("t1", 100, account = "a1"), tx("t2", -40, account = "a2"), tx("t3", -10, account = "a1"))
        assertEquals(mapOf("a1" to Paise(90), "a2" to Paise(-40)), Balances.balances(all))
    }
}

class TotalsTest {
    private val cats = listOf(cat("food"), cat("pay", income = true))

    @Test fun refundReducesItsCategoryAndTransfersAreLeftOut() {
        val all = listOf(
            tx("t1", -184_000, category = "food"),
            tx("t2", 20_000, category = "food"),            // refund
            tx("t3", 6_000_000, category = "pay"),
            tx("t4", -500_000, category = "0", paired = "t5"),
            tx("t5", 500_000, account = "a2", category = "0", paired = "t4"),
            tx("t6", -200_000, kind = TransactionKind.LENT),
        )
        val flow = Totals.of(all, cats)
        assertEquals(Paise(6_000_000), flow.moneyIn)
        assertEquals(Paise(164_000), flow.moneyOut)
        assertEquals(Paise(6_000_000 - 164_000), flow.net)
    }

    @Test fun dayNetMatchesTheDesignExample() {
        // PDF p.12: Thu 12 Nov, 486 + 1,840 + 212 = −2,538.
        val day = listOf(tx("s", -48_600), tx("b", -184_000), tx("u", -21_200))
        assertEquals(Paise(-253_800), Totals.dayNet(day))
    }
}

class TransferTest {
    private val hdfc = acct("hdfc", "HDFC Savings")
    private val cash = acct("cash", "Cash")
    private val usd = acct("usd", "US Dollar", currency = "USD")

    @Test fun pairedLegsNetToZeroWithinTheSameCurrency() {
        var n = 0
        val legs = Transfers.create(hdfc, cash, Paise.rupees(5_000), Paise.rupees(5_000), ms(2026, 11, 12), "", null) { "id${++n}" }
        assertEquals(2, legs.size)
        assertEquals(legs[1].id, legs[0].pairedTransactionId)
        assertEquals(legs[0].id, legs[1].pairedTransactionId)
        assertEquals(-500_000L, legs[0].amount)
        assertEquals(500_000L, legs[1].amount)
        assertEquals("hdfc", legs[0].accountId)
        assertEquals("cash", legs[1].accountId)
        assertEquals(Paise.ZERO, Totals.of(legs, emptyList()).let { it.moneyIn + it.moneyOut })
    }

    @Test fun crossCurrencyKeepsTheOriginalOnTheReceivingLeg() {
        var n = 0
        val legs = Transfers.create(hdfc, usd, Paise.rupees(8_300), Paise(10_000), 0, "", null) { "id${++n}" }
        assertEquals(830_000L, -legs[0].amount)
        assertEquals(10_000L, legs[1].amount)
        assertEquals(830_000L, legs[1].originalAmount)
        assertEquals("INR", legs[1].originalCurrency)
    }

    @Test fun deletingOneLegShowsTheOtherToo() {
        var n = 0
        val legs = Transfers.create(hdfc, cash, Paise.rupees(100), Paise.rupees(100), 0, "", null) { "id${++n}" }
        assertEquals(2, Transfers.legs(legs[0], legs).size)
        val impact = Deletion.impact(listOf(legs[0]), legs, listOf(hdfc, cash))
        assertEquals(2, impact.toDelete.size)
        assertEquals(1, impact.extraTransferLegs)
        assertEquals(2, impact.balanceShifts.size)
    }
}

class QuickAddTest {
    private val accounts = listOf(acct("hdfc", "HDFC"), acct("cash", "Cash"), acct("usd", "USD", currency = "USD"))
    private var n = 0
    private fun id() = "id${++n}"
    private val now = ms(2026, 11, 13)

    @Test fun anExpenseIsStoredNegative() {
        val r = QuickAdd.build(QuickAddDraft(amountRaw = "486", title = " Swiggy ", categoryId = "food", accountId = "hdfc", dateMs = now), accounts, ::id)
        val e = assertIs<QuickAddResult.Ready>(r).entries.single()
        assertEquals(-48_600L, e.amount)
        assertEquals("Swiggy", e.name)
        assertTrue(!e.income && e.paid && e.kind == null)
    }

    @Test fun anIncomeIsStoredPositive() {
        val r = QuickAdd.build(QuickAddDraft(type = EntryType.Income, amountRaw = "60000", categoryId = "pay", accountId = "hdfc", dateMs = now), accounts, ::id)
        assertEquals(6_000_000L, assertIs<QuickAddResult.Ready>(r).entries.single().amount)
    }

    @Test fun calculatorTextIsEvaluatedBeforeSaving() {
        val r = QuickAdd.build(QuickAddDraft(amountRaw = "1200+350.5", categoryId = "food", accountId = "hdfc", dateMs = now), accounts, ::id)
        assertEquals(-155_050L, assertIs<QuickAddResult.Ready>(r).entries.single().amount)
    }

    @Test fun otherCurrencyKeepsOriginalAndRate() {
        val r = QuickAdd.build(
            QuickAddDraft(amountRaw = "12.50", categoryId = "food", accountId = "hdfc", dateMs = now, rate = "83.215", originalCurrency = "USD"),
            accounts, ::id,
        )
        val e = assertIs<QuickAddResult.Ready>(r).entries.single()
        assertEquals(-104_019L, e.amount) // 12.50 × 83.215 = 1040.1875 → 1040.19 → 104,019 paise
        assertEquals(-1_250L, e.originalAmount)
        assertEquals("USD", e.originalCurrency)
        assertEquals("83.215", e.exchangeRate)
    }

    @Test fun invalidDraftsAreRefusedWithAPlainReason() {
        fun bad(d: QuickAddDraft) = assertIs<QuickAddResult.Invalid>(QuickAdd.build(d, accounts, ::id)).reason
        assertEquals("Enter an amount.", bad(QuickAddDraft(categoryId = "food", accountId = "hdfc", dateMs = now)))
        assertEquals("Enter an amount above zero.", bad(QuickAddDraft(amountRaw = "0", categoryId = "food", accountId = "hdfc", dateMs = now)))
        assertEquals("Pick a category.", bad(QuickAddDraft(amountRaw = "5", accountId = "hdfc", dateMs = now)))
        assertEquals("Pick an account.", bad(QuickAddDraft(amountRaw = "5", categoryId = "food", dateMs = now)))
        assertEquals("Pick two different accounts.", bad(QuickAddDraft(type = EntryType.Transfer, amountRaw = "5", accountId = "hdfc", toAccountId = "hdfc", dateMs = now)))
    }

    @Test fun aTransferNeedsNoCategoryAndMakesTwoEntries() {
        val r = QuickAdd.build(QuickAddDraft(type = EntryType.Transfer, amountRaw = "5000", accountId = "hdfc", toAccountId = "cash", dateMs = now), accounts, ::id)
        val legs = assertIs<QuickAddResult.Ready>(r).entries
        assertEquals(2, legs.size)
        assertEquals(listOf(-500_000L, 500_000L), legs.map { it.amount })
    }

    @Test fun titleSuggestionsAndCategoryMemory() {
        val mem = listOf(
            TitleMemory("1", "food", "Swiggy", 0, null, 0, false),
            TitleMemory("2", "food", "Swiggy Instamart", 0, null, 1, false),
            TitleMemory("3", "bills", "Rent", 0, null, 2, true),
        )
        assertEquals(listOf("Swiggy", "Swiggy Instamart"), TitleSuggest.suggest("swig", mem).map { it.title })
        assertEquals(emptyList(), TitleSuggest.suggest("Re", mem)) // exact-match entry needs the whole title
        assertEquals(listOf("Rent"), TitleSuggest.suggest("rent", mem).map { it.title })
        assertEquals("food", TitleSuggest.categoryFor("swiggy", mem))
    }

    @Test fun recentCategoriesComeFirst() {
        val cats = listOf(cat("a", order = 0), cat("b", order = 1), cat("c", order = 2), cat("sub", main = "a"), cat("pay", income = true))
        val ordered = CategoryOrder.recentFirst(cats, listOf(tx("1", -1, category = "c", date = 10), tx("2", -1, category = "b", date = 20)), income = false)
        assertEquals(listOf("b", "c", "a"), ordered.map { it.id })
    }
}

class ExchangeTest {
    @Test fun convertRoundsHalfEven() {
        assertEquals(Paise(104_019), Exchange.convert(Paise(1_250), BigDecimal("83.215")))
        assertEquals(Paise(2), Exchange.convert(Paise(1), BigDecimal("2.5")))   // 2.5 → 2 (even)
        assertEquals(Paise(4), Exchange.convert(Paise(1), BigDecimal("3.5")))   // 3.5 → 4 (even)
    }

    @Test fun rateBetweenUsesBaseTableOrOverride() {
        val perBase = mapOf("INR" to BigDecimal("83.0"), "USD" to BigDecimal("1.0"), "EUR" to BigDecimal("0.92"))
        assertEquals(0, BigDecimal("83").compareTo(Exchange.rateBetween("USD", "INR", perBase)!!))
        assertEquals(BigDecimal.ONE, Exchange.rateBetween("INR", "INR", perBase))
        assertNull(Exchange.rateBetween("USD", "XYZ", perBase))
        val override = mapOf(("USD" to "INR") to BigDecimal("84.5"))
        assertEquals(BigDecimal("84.5"), Exchange.rateBetween("USD", "INR", perBase, override))
    }

    @Test fun badRatesAreRejected() {
        assertNull(Exchange.parseRate("abc"))
        assertNull(Exchange.parseRate("0"))
        assertNull(Exchange.parseRate("-3"))
    }
}

class LedgerViewTest {
    private val cats = listOf(cat("food", name = "Food & dining"), cat("groc", name = "Groceries"), cat("pay", income = true, name = "Salary"))
    private val accounts = listOf(acct("a1", "HDFC"), acct("a2", "ICICI Card"))

    private val entries = listOf(
        tx("swiggy", -48_600, category = "food", date = ms(2026, 11, 12, 20), name = "Swiggy"),
        tx("bigbasket", -184_000, account = "a2", category = "groc", date = ms(2026, 11, 12, 18), name = "BigBasket"),
        tx("uber", -21_200, category = "food", date = ms(2026, 11, 12, 9), name = "Uber"),
        tx("airtel", -59_900, category = "food", date = ms(2026, 11, 11, 10), name = "Airtel postpaid"),
        tx("salary", 6_000_000, category = "pay", date = ms(2026, 10, 31, 10), name = "Salary", note = "October"),
        tx("sub", -29_900, kind = TransactionKind.SUBSCRIPTION, paid = false, date = ms(2026, 11, 20), name = "Netflix"),
    )

    @Test fun groupsByMonthAndDayNewestFirst() {
        val months = LedgerGrouping.group(entries, cats, IST)
        assertEquals(listOf(java.time.YearMonth.of(2026, 11), java.time.YearMonth.of(2026, 10)), months.map { it.month })
        val nov = months[0]
        assertEquals(listOf(20, 12, 11), nov.days.map { it.date.dayOfMonth })
        assertEquals(Paise(-253_800), nov.days.first { it.date.dayOfMonth == 12 }.net)
        // The unpaid subscription is not counted in November's money out.
        assertEquals(Paise(48_600 + 184_000 + 21_200 + 59_900), nov.flow.moneyOut)
        assertEquals(Paise(6_000_000), months[1].flow.moneyIn)
    }

    @Test fun midnightBoundaryUsesTheLocalDay() {
        val late = tx("late", -100, date = ms(2026, 11, 12, 23, 59))
        val early = tx("early", -100, date = ms(2026, 11, 13, 0, 0))
        val days = LedgerGrouping.group(listOf(late, early), cats, IST).single().days
        assertEquals(listOf(13, 12), days.map { it.date.dayOfMonth })
    }

    @Test fun tabsSplitSpendingIncomeUpcoming() {
        fun ids(tab: LedgerTab) = LedgerSearch.apply(entries, LedgerFilter(tab = tab), cats, accounts).map { it.id }.toSet()
        assertEquals(setOf("sub"), ids(LedgerTab.Upcoming))
        assertEquals(setOf("salary"), ids(LedgerTab.Income))
        assertEquals(setOf("swiggy", "bigbasket", "uber", "airtel"), ids(LedgerTab.Spending))
        assertEquals(6, ids(LedgerTab.All).size)
    }

    @Test fun searchMatchesTitleNoteCategoryAndAccount() {
        fun ids(text: String) = LedgerSearch.apply(entries, LedgerFilter(text = text), cats, accounts).map { it.id }.toSet()
        assertEquals(setOf("swiggy"), ids("SWIG"))
        assertEquals(setOf("salary"), ids("october"))
        assertEquals(setOf("bigbasket"), ids("groceries"))
        assertEquals(setOf("bigbasket"), ids("icici"))
    }

    @Test fun filtersByAmountDateAccountAndCategory() {
        fun ids(f: LedgerFilter) = LedgerSearch.apply(entries, f, cats, accounts).map { it.id }.toSet()
        assertEquals(setOf("bigbasket"), ids(LedgerFilter(minAmount = Paise.rupees(1_000), maxAmount = Paise.rupees(2_000))))
        assertEquals(setOf("airtel"), ids(LedgerFilter(fromMs = ms(2026, 11, 11, 0), toMs = ms(2026, 11, 12, 0))))
        assertEquals(setOf("bigbasket"), ids(LedgerFilter(accountIds = setOf("a2"))))
        assertEquals(setOf("swiggy", "uber", "airtel"), ids(LedgerFilter(tab = LedgerTab.Spending, categoryIds = setOf("food"))))
    }
}

class BulkAndDeleteTest {
    private val hdfc = acct("a1", "HDFC")
    private val cash = acct("a2", "Cash")

    @Test fun bulkChangesCategoryButNeverBreaksATransferPair() {
        var n = 0
        val legs = Transfers.create(hdfc, cash, Paise(100), Paise(100), 0, "", null) { "id${++n}" }
        val plain = tx("p", -50, category = "food")
        val all = legs + plain
        val result = BulkEdit.apply(listOf(plain, legs[0]), all, BulkChange(categoryId = "groc"), nowMs = 99)
        assertEquals(listOf("p"), result.changed.map { it.id })
        assertEquals("groc", result.changed.single().categoryId)
        assertEquals(listOf(legs[0].id), result.skipped.map { it.id })
    }

    @Test fun bulkDateChangeMovesBothTransferLegs() {
        var n = 0
        val legs = Transfers.create(hdfc, cash, Paise(100), Paise(100), 0, "", null) { "id${++n}" }
        val result = BulkEdit.apply(listOf(legs[0]), legs, BulkChange(dateMs = 5_000), nowMs = 99)
        assertEquals(2, result.changed.size)
        assertTrue(result.changed.all { it.dateMs == 5_000L && it.modifiedAtMs == 99L })
    }

    @Test fun deleteImpactListsBalanceShifts() {
        val all = listOf(tx("t1", 100_000, account = "a1"), tx("t2", -30_000, account = "a1"), tx("t3", -5_000, account = "a2"))
        val impact = Deletion.impact(listOf(all[1]), all, listOf(hdfc, cash))
        assertEquals(1, impact.balanceShifts.size)
        assertEquals(Paise(70_000), impact.balanceShifts[0].before)
        assertEquals(Paise(100_000), impact.balanceShifts[0].after)
        assertEquals(0, impact.extraTransferLegs)
    }

    @Test fun editDiffListsOnlyWhatChanged() {
        val before = tx("t1", -48_600, name = "Swiggy")
        val after = before.copy(name = "Swiggy Instamart", amount = -52_000)
        assertEquals(listOf("Title", "Amount"), EditDiff.between(before, after).map { it.field })
        assertEquals(emptyList(), EditDiff.between(before, before))
    }
}
