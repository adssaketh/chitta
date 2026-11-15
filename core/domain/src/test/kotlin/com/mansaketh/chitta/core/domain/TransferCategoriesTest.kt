package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
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
private val NOVEMBER = Period(ms(2026, 11, 1, 0), ms(2026, 12, 1, 0))
private val NOW = ms(2026, 11, 12)

private fun cat(id: String, income: Boolean = false, main: String? = null, transfer: Boolean = false) =
    Category(id, id, null, null, null, 0, null, 0, income, null, main, countsAsTransfer = transfer)

private fun tx(id: String, amount: Long, category: String, date: Long = ms(2026, 11, 12)) = Transaction(
    id = id, pairedTransactionId = null, name = id, amount = amount, currency = "INR", note = "", categoryId = category,
    subCategoryId = null, accountId = "a1", dateMs = date, modifiedAtMs = null, originalDueMs = null, income = amount > 0,
    periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null, kind = null, paid = true,
    createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null, objectiveLoanId = null,
    excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

private fun account() = Account("a1", "a1", null, null, 0, null, 0, "INR", null, 2, null, null)

/**
 * D238 (B2): the old book kept transfers between your own accounts as ordinary categories named "Transfer from ..." and "Transfer to ...".
 * Worked by hand for November 2026: Swiggy −₹500, Salary +₹20,000, "Transfer from Mansa" +₹1,000, "Transfer to Saketh" −₹1,000.
 */
class TransferCategoriesTest {
    private val entries = listOf(
        tx("swiggy", -50_000, "food"),
        tx("salary", 2_000_000, "salary"),
        tx("from", 100_000, "tfFrom"),
        tx("to", -100_000, "tfTo"),
    )
    private fun cats(flag: Boolean) = listOf(
        cat("food"), cat("salary", income = true), cat("tfFrom", income = true, transfer = flag), cat("tfTo", transfer = flag),
    )

    @Test fun withoutTheSwitchTheTransfersCountAsMoneyInAndOut() {
        val flow = Totals.of(entries, cats(false))
        assertEquals(Paise(2_100_000), flow.moneyIn)   // 20,000 + 1,000
        assertEquals(Paise(150_000), flow.moneyOut)    // 500 + 1,000
        assertEquals(Paise(1_950_000), flow.net)
    }

    @Test fun withTheSwitchTheyAreLeftOutAndNetStaysTheSame() {
        val flow = Totals.of(entries, cats(true))
        assertEquals(Paise(2_000_000), flow.moneyIn)   // 20,000
        assertEquals(Paise(50_000), flow.moneyOut)     // 500
        assertEquals(Paise(1_950_000), flow.net)       // 19,500, as before: the two transfers cancel
    }

    @Test fun balancesStillIncludeThem() {
        assertEquals(Paise(1_950_000), Balances.balance(entries, "a1"))
    }

    @Test fun subcategoriesOfAMarkedCategoryAreLeftOutToo() {
        val categories = listOf(cat("food"), cat("tfTo", transfer = true), cat("tfSub", main = "tfTo"))
        assertEquals(setOf("tfTo", "tfSub"), Totals.transferCategoryIds(categories))
        val flow = Totals.of(listOf(tx("a", -50_000, "food"), tx("b", -100_000, "tfSub")), categories)
        assertEquals(Paise(50_000), flow.moneyOut)
    }

    @Test fun noMarkedCategoryMeansNothingChanges() {
        assertTrue(Totals.transferCategoryIds(cats(false)).isEmpty())
    }

    @Test fun dayTotalOnTheRuleSkipsThem() {
        assertEquals(Paise(-50_000 + 2_000_000), Totals.dayNet(entries, cats(true)))
        assertEquals(Paise(1_950_000), Totals.dayNet(entries, cats(false)))
    }

    @Test fun categoryBarsAndSummaryLeaveThemOut() {
        val bars = Insights.categoryTotals(entries, cats(true), NOVEMBER, income = false)
        assertEquals(listOf(CategoryTotal("food", Paise(50_000))), bars)
        val income = Insights.categoryTotals(entries, cats(true), NOVEMBER, income = true)
        assertEquals(listOf(CategoryTotal("salary", Paise(2_000_000))), income)
        val s = Insights.summary(entries, cats(true), NOVEMBER, NOW, IST)
        assertEquals(Paise(50_000), s.expense)
        assertEquals(Paise(2_000_000), s.income)
        val before = Insights.categoryTotals(entries, cats(false), NOVEMBER, income = false)
        assertEquals(setOf("food", "tfTo"), before.map { it.categoryId }.toSet())
    }

    @Test fun spendingAndIncomeTabsAndTransferTypeFollowTheSwitch() {
        val a = listOf(account())
        fun ids(tab: LedgerTab, flag: Boolean) = LedgerSearch.apply(entries, LedgerFilter(tab = tab), cats(flag), a).map { it.id }.toSet()
        assertEquals(setOf("swiggy"), ids(LedgerTab.Spending, true))
        assertEquals(setOf("swiggy", "to"), ids(LedgerTab.Spending, false))
        assertEquals(setOf("salary"), ids(LedgerTab.Income, true))
        assertEquals(entries.map { it.id }.toSet(), ids(LedgerTab.All, true)) // nothing is hidden from the All tab
    }

    @Test fun aBudgetDoesNotCountThemUnlessItAsksForBalanceCorrections() {
        val food = listOf(cat("food"), cat("tfTo", transfer = true))
        val e = tx("to", -100_000, "tfTo")
        val plain = Budget(
            "b1", "Monthly", 6_000_000, null, ms(2026, 11, 1, 0), ms(2026, 12, 1, 0) - 1, null, null, null, false, false, false, 1, Repeat.MONTHLY, 0, null, false, 0,
            "0", listOf(BudgetFilter.DEFAULT_FILTERS), null, false,
        )
        assertEquals(0L, Budgets.contribution(e, plain, food))
        assertEquals(100_000L, Budgets.contribution(e, plain, listOf(cat("food"), cat("tfTo"))))
        assertFalse(Totals.leavesTotals(e, emptySet()))
    }

    /** B1 (Net total minus sign): the figure the Insights row draws is `Flow.net` through `IndianFormat.rupees`; a loss must read with the minus. */
    @Test fun aNegativeNetTotalIsSignedAndFormattedWithTheMinus() {
        val spendMore = listOf(tx("s", -3_000_000, "food"), tx("i", 1_000_000, "salary"))
        val flow = Totals.of(spendMore, cats(false))
        assertEquals(Paise(-2_000_000), flow.net)
        assertEquals("−₹20,000", com.mansaketh.chitta.core.money.IndianFormat.rupees(flow.net))
        val s = Insights.summary(spendMore, cats(false), NOVEMBER, NOW, IST)
        assertEquals(Paise(-2_000_000), s.net)
        assertEquals(-200, s.savedPercent) // saved −200%: spent three times what came in
    }
}
