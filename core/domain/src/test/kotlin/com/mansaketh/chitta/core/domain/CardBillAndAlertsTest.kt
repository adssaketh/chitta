package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val Z = ZoneId.of("Asia/Kolkata")
private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, Z).toInstant().toEpochMilli()
private fun rs(whole: Long) = Paise.rupees(whole)

private val hdfc = Account("hdfc", "HDFC savings", null, null, 0, null, 0, "INR", null, 2, null, null)
private val icici = Account("icici", "ICICI card", null, null, 0, null, 0, "INR", null, 2, null, null).copy(statementDay = 15, dueDay = 5)

private fun bill(id: String, rupees: Long, account: String, due: Long, repeat: Repeat? = null, name: String = "ICICI card bill") = Transaction(
    id = id, pairedTransactionId = null, name = name, amount = -rs(rupees).value, currency = "INR", note = "", categoryId = "bills", subCategoryId = null,
    accountId = account, dateMs = due, modifiedAtMs = null, originalDueMs = null, income = false, periodLength = if (repeat != null) 1 else null, repeat = repeat,
    endDateMs = null, upcomingNotification = null, kind = if (repeat != null) TransactionKind.SUBSCRIPTION else TransactionKind.UPCOMING, paid = false,
    createdAnotherFuture = false, skipPaid = false, methodAdded = null, objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(),
    ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

class CardBillPaymentTest {
    private var n = 0
    private fun id() = "new${++n}"
    private val due = at(2026, 11, 18)

    @Test fun onlyABillOnACardAccountIsACardBill() {
        val accounts = listOf(hdfc, icici)
        assertEquals(icici, Bills.cardOf(bill("b", 12_480, "icici", due), accounts))
        // A bill titled with the card's name is a card bill wherever it sits.
        assertEquals(icici, Bills.cardOf(bill("b", 12_480, "hdfc", due), accounts))
        // Ordinary bills, and a purchase that merely sits on the card, are not.
        assertNull(Bills.cardOf(bill("b", 1_240, "hdfc", due, name = "Electricity"), accounts))
        assertNull(Bills.cardOf(bill("b", 649, "icici", due, Repeat.MONTHLY, name = "Netflix"), accounts))
        assertNull(Bills.cardOf(bill("b", 12_480, "icici", due), listOf(hdfc, icici.copy(statementDay = null))))
        assertNull(Bills.cardOf(bill("b", 12_480, "icici", due).copy(income = true), accounts))
    }

    // PDF p.30: ₹12,480 paid from HDFC savings to the ICICI card, saved as a transfer so it is not counted as spending twice.
    @Test fun fullPaymentIsATransferFromThePayerToTheCard() {
        val b = bill("b", 12_480, "icici", due)
        val c = Bills.payCardBill(b, icici, hdfc, at(2026, 11, 12), Z, PaidDateRule.Today, ::id)
        val out = c.updated; val inn = c.extra.single()
        assertEquals(-rs(12_480).value, out.amount); assertEquals("hdfc", out.accountId)
        assertEquals(rs(12_480).value, inn.amount); assertEquals("icici", inn.accountId)
        assertEquals(inn.id, out.pairedTransactionId); assertEquals(out.id, inn.pairedTransactionId)
        assertTrue(Totals.isTransfer(out)); assertTrue(Totals.isTransfer(inn))
        assertEquals(QuickAdd.TRANSFER_CATEGORY_ID, out.categoryId); assertEquals(QuickAdd.TRANSFER_CATEGORY_ID, inn.categoryId)
        assertTrue(out.paid); assertFalse(out.skipPaid); assertTrue(inn.paid)
        assertEquals(at(2026, 11, 12), out.dateMs); assertEquals(due, out.originalDueMs)
        assertEquals(0L, out.amount + inn.amount) // nothing is created or lost
    }

    @Test fun partPaymentUsesTheTypedAmountAndAChosenDate() {
        val b = bill("b", 12_480, "icici", due)
        val c = Bills.payCardBill(b, icici, hdfc, at(2026, 11, 12), Z, PaidDateRule.Today, ::id, amountPaise = rs(625).value, paidOnMs = at(2026, 11, 10))
        assertEquals(-rs(625).value, c.updated.amount); assertEquals(rs(625).value, c.extra.single().amount)
        assertEquals(at(2026, 11, 10), c.updated.dateMs); assertEquals(at(2026, 11, 10), c.extra.single().dateMs)
    }

    @Test fun aCardCannotPayItself() {
        assertFailsWith<IllegalArgumentException> { Bills.payCardBill(bill("b", 100, "icici", due), icici, icici, due, Z, PaidDateRule.Today, ::id) }
    }

    @Test fun aRepeatingCardBillMakesItsNextOneOnTheCardWithNoPairing() {
        val b = bill("b", 12_480, "icici", due, Repeat.MONTHLY)
        val c = Bills.payCardBill(b, icici, hdfc, at(2026, 11, 12), Z, PaidDateRule.Today, ::id)
        val next = assertNotNull(c.next)
        assertEquals("icici", next.accountId); assertNull(next.pairedTransactionId); assertFalse(next.paid)
        assertEquals(at(2026, 12, 18), next.dateMs)
    }

    @Test fun anOrdinaryPaymentCanStillChooseItsDate() {
        val b = bill("b", 1_240, "hdfc", due)
        val c = Bills.pay(b, at(2026, 11, 12), Z, PaidDateRule.Today, ::id, paidOnMs = at(2026, 11, 9))
        assertEquals(at(2026, 11, 9), c.updated.dateMs); assertEquals(due, c.updated.originalDueMs); assertTrue(c.extra.isEmpty())
    }
}

private fun cat(id: String) = Category(id, id, null, null, null, 0, null, 0, false, null, null)
private fun spendEntry(id: String, paise: Long, date: Long) = Transaction(
    id = id, pairedTransactionId = null, name = id, amount = -paise, currency = "INR", note = "", categoryId = "food", subCategoryId = null, accountId = "a1",
    dateMs = date, modifiedAtMs = null, originalDueMs = null, income = false, periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null,
    kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null, objectiveLoanId = null,
    excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)
private fun monthly(alert: Int?, income: Boolean = false) = Budget(
    "b1", "November budget", rs(60_000).value, null, at(2026, 11, 1, 0), at(2026, 12, 1, 0) - 1, null, null, null, income, false, false, 1, Repeat.MONTHLY, 0, null, false, 0, "a1",
    listOf(BudgetFilter.DEFAULT_FILTERS), null, false, false, alert,
)
private fun summaryAt(b: Budget, entries: List<Transaction>, now: Long) =
    Budgets.summarize(b, Budgets.periodAt(b, now, Z), entries, listOf(cat("food")), now, Z)

class BudgetAlertsTest {
    private val now = at(2026, 11, 12)

    // Limit ₹60,000, alert at 90% = ₹54,000. One paisa under does not alert; exactly ₹54,000 does.
    @Test fun reachesExactlyAtThePercentOfTheLimit() {
        val b = monthly(90)
        assertFalse(BudgetAlerts.reached(b, summaryAt(b, listOf(spendEntry("e", rs(54_000).value - 1, at(2026, 11, 5))), now)))
        assertTrue(BudgetAlerts.reached(b, summaryAt(b, listOf(spendEntry("e", rs(54_000).value, at(2026, 11, 5))), now)))
        assertTrue(BudgetAlerts.reached(b, summaryAt(b, listOf(spendEntry("e", rs(61_000).value, at(2026, 11, 5))), now)))
    }

    @Test fun noAlertWhenOffForIncomeOrForAnotherPeriod() {
        val spent = listOf(spendEntry("e", rs(59_000).value, at(2026, 11, 5)))
        assertFalse(BudgetAlerts.reached(monthly(null), summaryAt(monthly(null), spent, now)))
        assertFalse(BudgetAlerts.reached(monthly(50, income = true), summaryAt(monthly(50, income = true), spent, now)))
        // October's spending does not count in November.
        val october = listOf(spendEntry("e", rs(59_000).value, at(2026, 10, 20)))
        assertFalse(BudgetAlerts.reached(monthly(90), summaryAt(monthly(90), october, now)))
    }

    @Test fun theKeyChangesWithThePeriodAndThePercent() {
        val s = summaryAt(monthly(90), emptyList(), now)
        val dec = summaryAt(monthly(90), emptyList(), at(2026, 12, 12))
        assertTrue(BudgetAlerts.key(monthly(90), s) != BudgetAlerts.key(monthly(90), dec))
        assertTrue(BudgetAlerts.key(monthly(90), s) != BudgetAlerts.key(monthly(75), s))
        assertEquals("Off", BudgetAlerts.label(null)); assertEquals("At 90%", BudgetAlerts.label(90))
    }
}

class BudgetAlertsDueTest {
    private val now = at(2026, 11, 12)
    private val over = listOf(spendEntry("e", rs(55_000).value, at(2026, 11, 5)))

    @Test fun anAlertIsSentOnceThenNotAgainInThePeriod() {
        val b = monthly(90)
        val first = BudgetAlerts.due(listOf(b), over, listOf(cat("food")), now, Z, emptySet())
        assertEquals(1, first.size); assertEquals(90, first.single().percent); assertEquals(rs(55_000), first.single().spent); assertEquals(rs(60_000), first.single().limit)
        assertTrue(BudgetAlerts.due(listOf(b), over, listOf(cat("food")), now, Z, setOf(first.single().key)).isEmpty())
    }

    @Test fun aNewPercentOrANewPeriodCanAlertAgain() {
        val sent = BudgetAlerts.due(listOf(monthly(90)), over, listOf(cat("food")), now, Z, emptySet()).map { it.key }.toSet()
        assertEquals(1, BudgetAlerts.due(listOf(monthly(75)), over, listOf(cat("food")), now, Z, sent).size)
        val december = listOf(spendEntry("d", rs(55_000).value, at(2026, 12, 3)))
        assertEquals(1, BudgetAlerts.due(listOf(monthly(90)), december, listOf(cat("food")), at(2026, 12, 12), Z, sent).size)
    }

    @Test fun budgetsWithoutAnAlertAreIgnored() {
        assertTrue(BudgetAlerts.due(listOf(monthly(null)), over, listOf(cat("food")), now, Z, emptySet()).isEmpty())
    }
}
