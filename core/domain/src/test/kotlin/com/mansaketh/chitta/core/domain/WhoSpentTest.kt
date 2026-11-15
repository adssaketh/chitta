package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val zone = ZoneId.of("Asia/Kolkata")
private val nov = WhoSpent.monthPeriod(YearMonth.of(2026, 11), zone)
private fun day(d: Int) = YearMonth.of(2026, 11).atDay(d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

private fun cat(id: String, income: Boolean = false) = Category(
    id = id, name = id, colour = null, iconName = null, emojiIconName = null, createdAtMs = 0, modifiedAtMs = null, sortOrder = 0,
    income = income, methodAdded = null, mainCategoryId = null,
)

private val categories = listOf(cat("food"), cat("rent"), cat("salary", income = true), cat("0"))

private fun spend(
    id: String, owner: String?, rupees: Long, date: Int = 10, category: String = "food", private: Boolean = false,
    kind: TransactionKind? = null, paired: String? = null, paid: Boolean = true,
) = Transaction(
    id = id, pairedTransactionId = paired, name = id, amount = -rupees * 100, currency = "INR", note = "", categoryId = category, subCategoryId = null,
    accountId = "acc", dateMs = day(date), modifiedAtMs = null, originalDueMs = null, income = false, periodLength = null, repeat = null,
    endDateMs = null, upcomingNotification = null, kind = kind, paid = paid, createdAnotherFuture = null, skipPaid = false, methodAdded = null,
    objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = owner, isPrivate = private, householdSyncedAtMs = null,
)

private val me = HouseholdMember("me", "Mansa", HouseholdRole.Owner, false)
private val sam = HouseholdMember("sam", "Saketh", HouseholdRole.Member, false)

class WhoSpentTest {
    // PDF p.4 worked example: ₹12,440 paid by one person and ₹9,300 by the other, nothing else shared.
    @Test fun pdfExampleTotalsAndPercent() {
        val entries = listOf(spend("a1", "me", 12_000), spend("a2", "me", 440), spend("b1", "sam", 9_300))
        val r = WhoSpent.compute(entries, categories, nov, listOf(me, sam), "me")
        assertEquals(listOf("You", "Saketh"), r.members.map { it.name })
        assertEquals(Paise(1_244_000), r.members[0].spent)
        assertEquals(Paise(930_000), r.members[1].spent)
        assertEquals(Paise(2_174_000), r.total)
        assertEquals(57, r.members[0].percent) // 57.2%
        assertEquals(43, r.members[1].percent) // 42.8%
        assertEquals(listOf(true, false), r.members.map { it.isMe })
    }

    @Test fun privateAndPersonalEntriesAreNeverCounted() {
        val entries = listOf(
            spend("a", "me", 500),
            spend("p", "me", 9_000, private = true),   // marked private
            spend("q", "", 8_000),                     // personal, stays on the phone
            spend("n", null, 7_000),                   // not claimed yet
            spend("s", "sam", 100, private = true),
        )
        val r = WhoSpent.compute(entries, categories, nov, listOf(me, sam), "me")
        assertEquals(Paise(50_000), r.total)
        assertEquals(Paise(0), r.members.first { it.uid == "sam" }.spent)
    }

    @Test fun onlyTheMonthCounts() {
        val oct = YearMonth.of(2026, 10).atDay(31).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        val dec = YearMonth.of(2026, 12).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val entries = listOf(spend("in", "me", 100), spend("early", "me", 900).copy(dateMs = oct), spend("late", "me", 900).copy(dateMs = dec))
        assertEquals(Paise(10_000), WhoSpent.compute(entries, categories, nov, listOf(me), "me").total)
    }

    @Test fun transfersCorrectionsLentAndBorrowedAreLeftOut() {
        val entries = listOf(
            spend("real", "me", 100),
            spend("leg", "me", 5_000, paired = "other"),
            spend("fix", "me", 6_000, category = "0"),
            spend("lent", "me", 7_000, kind = TransactionKind.LENT),
            spend("owed", "me", 8_000, kind = TransactionKind.BORROWED),
        )
        val r = WhoSpent.compute(entries, categories, nov, listOf(me), "me")
        assertEquals(Paise(10_000), r.total)
        assertEquals(1, r.members.single().entryCount)
    }

    @Test fun aRefundReducesWhatTheMemberSpent() {
        val refund = spend("refund", "sam", 0, category = "food").copy(amount = 30_000L) // +₹300 in an expense category
        val entries = listOf(spend("buy", "sam", 1_000), refund)
        val r = WhoSpent.compute(entries, categories, nov, listOf(me, sam), "me")
        assertEquals(Paise(70_000), r.members.first { it.uid == "sam" }.spent)
    }

    @Test fun incomeIsNotSpending() {
        val pay = spend("pay", "me", 0, category = "salary").copy(amount = 5_000_000L)
        assertEquals(Paise(0), WhoSpent.compute(listOf(pay), categories, nov, listOf(me), "me").total)
    }

    @Test fun anUnpaidUpcomingBillDoesNotCountYet() {
        val bill = spend("bill", "me", 2_000, kind = TransactionKind.UPCOMING, paid = false)
        assertEquals(Paise(0), WhoSpent.compute(listOf(bill), categories, nov, listOf(me), "me").total)
    }

    @Test fun everyCurrentMemberIsListedAndBiggestComesFirst() {
        val entries = listOf(spend("b", "sam", 700), spend("a", "me", 200))
        val r = WhoSpent.compute(entries, categories, nov, listOf(me, sam), "me")
        assertEquals(listOf("Saketh", "You"), r.members.map { it.name })
        val none = WhoSpent.compute(emptyList(), categories, nov, listOf(me, sam), "me")
        assertEquals(2, none.members.size)
        assertEquals(Paise(0), none.total)
        assertTrue(none.members.all { it.percent == null })
    }

    @Test fun aMemberWhoLeftIsStillCountedUnderAFormerName() {
        val entries = listOf(spend("gone", "ex", 400), spend("mine", "me", 600))
        val r = WhoSpent.compute(entries, categories, nov, listOf(me), "me")
        assertEquals(listOf("You", WhoSpent.FORMER_MEMBER), r.members.map { it.name })
        assertEquals(Paise(100_000), r.total)
    }

    @Test fun aBlankNicknameShowsAsMember() {
        val blank = HouseholdMember("sam", "  ", HouseholdRole.Member, false)
        val r = WhoSpent.compute(emptyList(), categories, nov, listOf(me, blank), "me")
        assertTrue(r.members.any { it.name == Households.UNKNOWN_NAME })
    }

    @Test fun percentIsNullWhenNothingWasSpent() {
        val refundOnly = spend("r", "me", 0).copy(amount = 10_000L)
        val r = WhoSpent.compute(listOf(refundOnly), categories, nov, listOf(me), "me")
        assertNull(r.members.single().percent)
    }

    @Test fun monthPeriodHasNoGapAtTheYearEnd() {
        val dec = WhoSpent.monthPeriod(YearMonth.of(2026, 12), zone)
        val jan = WhoSpent.monthPeriod(YearMonth.of(2027, 1), zone)
        assertEquals(dec.endMs, jan.startMs)
    }
}

class HouseholdDiagnosticsTest {
    private fun phone(shared: Int = 5, priv: Int = 2, waiting: Int = 0, members: Int = 3, fromMembers: Int = 7) =
        PhoneHolds(sharedSent = shared, privateSent = priv, waiting = waiting, fromMembers = fromMembers, members = members)

    @Test fun matchingCountsAreTheSame() {
        val rows = HouseholdDiagnostics.rows(phone(), ServerHolds(shared = 12, myPrivate = 2, members = 3))
        assertTrue(HouseholdDiagnostics.allSame(rows))
        assertEquals(12, rows[0].phone) // 7 from members + 5 sent
    }

    @Test fun aPhoneThatIsBehindSaysBy() {
        val rows = HouseholdDiagnostics.rows(phone(), ServerHolds(shared = 15, myPrivate = 2, members = 3))
        assertEquals(DiagnosticState.PhoneBehind, rows[0].state)
        assertEquals(3, rows[0].gap)
        assertEquals("The phone is missing 3", HouseholdDiagnostics.describe(rows[0]))
    }

    @Test fun aServerThatIsBehindSaysBy() {
        val rows = HouseholdDiagnostics.rows(phone(priv = 4), ServerHolds(shared = 12, myPrivate = 2, members = 3))
        assertEquals(DiagnosticState.ServerBehind, rows[1].state)
        assertEquals("The server is missing 2", HouseholdDiagnostics.describe(rows[1]))
    }

    @Test fun waitingNoteIsOnlyShownWhenSomethingWaits() {
        assertNull(HouseholdDiagnostics.waitingNote(0))
        assertEquals("1 entry is waiting to go up. Tap Send now.", HouseholdDiagnostics.waitingNote(1))
        assertEquals("4 entries are waiting to go up. Tap Send now.", HouseholdDiagnostics.waitingNote(4))
    }
}
