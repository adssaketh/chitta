package com.mansaketh.chitta.ui.add

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.BudgetFilter
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 9) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, IST).toInstant().toEpochMilli()
private fun r(whole: Long) = Paise.rupees(whole)

private val cats = listOf(Category("food", "food", null, null, null, 0, null, 0, false, null, null))
private val account = Account("a1", "a1", null, null, 0, null, 0, "INR", null, 2, null, null)

/** [rupees] is signed: negative spends. */
private fun entry(id: String, rupees: Long, date: Long) = Transaction(
    id = id, pairedTransactionId = null, name = id, amount = r(rupees).value, currency = "INR", note = "", categoryId = "food", subCategoryId = null, accountId = "a1",
    dateMs = date, modifiedAtMs = null, originalDueMs = null, income = rupees > 0, periodLength = null, repeat = null, endDateMs = null,
    upcomingNotification = null, kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null,
    objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

private fun november(amount: Long = 60_000) = Budget(
    "b1", "Monthly", r(amount).value, null, ms(2026, 11, 1, 0), ms(2026, 12, 1, 0) - 1, null, null, null, false, false, false, 1, Repeat.MONTHLY, 0, null,
    false, 0, "a1", listOf(BudgetFilter.DEFAULT_FILTERS), null, false,
)

/** The PDF p.15 numbers: a 60,000 budget with 32,498 spent leaves 27,502 safe to spend; a 486 expense leaves 27,016. */
class QuickAddFiguresTest {
    private val now = ms(2026, 11, 12, 12)
    private val book = listOf(entry("early", -20_000, ms(2026, 11, 3)), entry("later", -12_498, ms(2026, 11, 10)))

    private fun safeAfter(added: List<Transaction>, budgets: List<Budget> = listOf(november()), cash: Set<String> = emptySet(), preferCash: Boolean = false) =
        QuickAddFigures.safeAfter(book, added, budgets, emptyList(), cats, listOf(account), emptyList(), cash, preferCash, null, now, IST)

    @Test fun beforeTheEntryItIsWhatHomeShows() {
        assertEquals(r(27_502), safeAfter(emptyList()))
    }

    @Test fun anExpenseOf486LeavesTwentySevenThousandAndSixteen() {
        assertEquals(r(27_016), safeAfter(listOf(entry("new", -486, now))))
    }

    @Test fun noBudgetAndNoCashCheckShowsNothing() {
        assertNull(safeAfter(listOf(entry("new", -486, now)), budgets = emptyList()))
    }

    @Test fun whenTheCashCheckLeadsThereIsNoSafeToSpendLine() {
        assertNull(safeAfter(listOf(entry("new", -486, now)), cash = setOf("a1"), preferCash = true))
    }
}
