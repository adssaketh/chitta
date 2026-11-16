package com.mansaketh.chitta.ui.home

import com.mansaketh.chitta.core.domain.Budgets
import com.mansaketh.chitta.core.domain.HomeFigures
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, IST).toInstant().toEpochMilli()
private fun r(whole: Long) = Paise.rupees(whole)

private val CATS = listOf(Category("food", "food", null, null, null, 0, null, 0, false, null, null))

private fun tx(id: String, amount: Long, date: Long, category: String = "food", kind: TransactionKind? = null, paid: Boolean = true, paired: String? = null) = Transaction(
    id = id, pairedTransactionId = paired, name = id, amount = amount, currency = "INR", note = "", categoryId = category, subCategoryId = null, accountId = "a1",
    dateMs = date, modifiedAtMs = null, originalDueMs = null, income = amount > 0, periodLength = null, repeat = null, endDateMs = null,
    upcomingNotification = null, kind = kind, paid = paid, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null,
    objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null, anchorDay = null,
)

private fun budget() = Budget(
    "b1", "November", 6_000_000, null, ms(2026, 11, 1, 0), ms(2026, 12, 1, 0) - 1, null, null, null, false, false, false, 1, Repeat.MONTHLY, 0, null,
    false, 0, "0", listOf(BudgetFilter.DEFAULT_FILTERS), null, false,
)

class SafeToSpendTextTest {
    // The reference figures of master section 12: ₹60,000 budget, ₹26,520 spent, ₹5,978 of bills still to come, 18 days left on 12 Nov.
    private val now = ms(2026, 11, 12)
    private val entries = listOf(
        tx("spent", -2_652_000, ms(2026, 11, 10)),
        tx("bill1", -400_000, ms(2026, 11, 18), kind = TransactionKind.UPCOMING, paid = false),
        tx("bill2", -197_800, ms(2026, 11, 25), kind = TransactionKind.UPCOMING, paid = false),
    )

    @Test fun workedExampleFiguresReadAsOnPage14() {
        val h = HomeFigures.hero(budget(), entries, CATS, now, IST)
        assertEquals("₹27,502", IndianFormat.rupeesRounded(h.safe))
        assertEquals("₹5,978", IndianFormat.rupeesRounded(h.billsDue))
        assertEquals("₹26,520", IndianFormat.rupeesRounded(h.summary.spent))
        assertEquals("₹60,000", IndianFormat.rupeesRounded(h.summary.limit))
        assertEquals("1–30 Nov", SafeToSpendText.periodText(h.summary.period, IST))
        assertEquals("÷ 18 days = ₹1,528 a day", SafeToSpendText.perDayLine(false, h.summary.daysLeft, IndianFormat.rupeesRounded(h.perDay!!)))
    }

    @Test fun billCountMatchesTheTwoBillsBehindTheAmount() {
        val b = budget()
        val period = Budgets.periodAt(b, now, IST)
        assertEquals(2, SafeToSpendText.billCount(b, period, entries, CATS))
        assertEquals(r(5_978), Budgets.billsDue(b, period, entries, CATS))
    }

    @Test fun billCountLeavesOutPaidTransferPairedAndNextMonthBills() {
        val b = budget()
        val period = Budgets.periodAt(b, now, IST)
        val all = entries + listOf(
            tx("paid", -10_000, ms(2026, 11, 20), kind = TransactionKind.UPCOMING, paid = true),
            tx("xfer", -10_000, ms(2026, 11, 20), category = "0", kind = TransactionKind.UPCOMING, paid = false),
            tx("pair", -10_000, ms(2026, 11, 20), kind = TransactionKind.UPCOMING, paid = false, paired = "x"),
            tx("next", -10_000, ms(2026, 12, 2), kind = TransactionKind.UPCOMING, paid = false),
        )
        assertEquals(2, SafeToSpendText.billCount(b, period, all, CATS))
        assertEquals(Budgets.billsDue(b, period, entries, CATS), Budgets.billsDue(b, period, all, CATS))
        assertEquals(0, SafeToSpendText.billCount(b, period, emptyList(), CATS))
    }

    @Test fun periodTextCrossingAMonthNamesBothMonths() {
        assertEquals("28 Oct–27 Nov", SafeToSpendText.periodText(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27)))
        assertEquals("1–28 Feb", SafeToSpendText.periodText(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
    }

    @Test fun billsLineHandlesNoneOneAndMany() {
        assertEquals("No bills in this budget", SafeToSpendText.billsLine(0))
        assertEquals("1 bill in this budget", SafeToSpendText.billsLine(1))
        assertEquals("4 bills in this budget", SafeToSpendText.billsLine(4))
    }

    @Test fun budgetTitleDoesNotRepeatTheWord() {
        assertEquals("November budget", SafeToSpendText.budgetTitle("November"))
        assertEquals("November budget", SafeToSpendText.budgetTitle("November budget"))
    }

    @Test fun joinReadsLikeASentence() {
        assertEquals("", SafeToSpendText.join(emptyList()))
        assertEquals("HDFC", SafeToSpendText.join(listOf("HDFC")))
        assertEquals("HDFC and Cash", SafeToSpendText.join(listOf("HDFC", "Cash")))
        assertEquals("HDFC, Axis and Cash", SafeToSpendText.join(listOf("HDFC", "Axis", "Cash")))
    }

    @Test fun cashSentenceMatchesPage14() {
        assertEquals(
            "After every bill and this month's goal savings, HDFC and Cash still hold ₹1,00,752.",
            SafeToSpendText.cashSentence(listOf("HDFC", "Cash"), false, "₹1,00,752"),
        )
    }

    @Test fun cashSentenceForOneAccountNoAccountAndSinkingFunds() {
        assertEquals("After every bill and this month's goal savings, HDFC still holds ₹500.", SafeToSpendText.cashSentence(listOf("HDFC"), false, "₹500"))
        assertEquals("After every bill and this month's goal savings, your spending accounts still hold ₹500.", SafeToSpendText.cashSentence(emptyList(), false, "₹500"))
        assertEquals(
            "After every bill, this month's goal savings and the sinking funds' set-aside, HDFC and Cash still hold ₹9.",
            SafeToSpendText.cashSentence(listOf("HDFC", "Cash"), true, "₹9"),
        )
    }

    @Test fun perDayLineHandlesOverBudgetLastDayAndNoFigure() {
        assertNull(SafeToSpendText.perDayLine(true, 18, "₹1,528"))
        assertEquals("Last day: the full amount", SafeToSpendText.perDayLine(false, 1, "₹27,502"))
        assertNull(SafeToSpendText.perDayLine(false, 0, "₹1"))
        assertNull(SafeToSpendText.perDayLine(false, 5, null))
    }

    @Test fun asOfIsTwelveHourLowercase() {
        assertEquals("8:42 pm", SafeToSpendText.asOf(ms(2026, 11, 16, 20, 42), IST))
        assertEquals("9:05 am", SafeToSpendText.asOf(ms(2026, 11, 16, 9, 5), IST))
    }
}
