package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.CardDue
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 12, 0, 0, 0, IST).toInstant().toEpochMilli()
private fun r(w: Long) = Paise.rupees(w)

private fun due(id: String, kind: String, rupees: Long, date: Long, card: String = "Saketh - ICICI Amazon Pay Credit Card") =
    CardDue(id, card, kind, rupees * 100, date, "", null)

private fun account(name: String = "Saketh - ICICI Amazon Pay Credit Card", id: String = "card") =
    Account(id, name, null, null, 0, null, 0, "INR", null, 2, null, null)

private fun entry(id: String, amount: Long, date: Long, note: String = "", account: String = "card") = Transaction(
    id = id, pairedTransactionId = null, name = id, amount = amount, currency = "INR", note = note, categoryId = "c", subCategoryId = null, accountId = account,
    dateMs = date, modifiedAtMs = null, originalDueMs = null, income = amount > 0, periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null,
    kind = null, paid = true, createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = null, objectiveLoanId = null,
    excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
)

class CardDuesTest {
    private val dues = listOf(
        due("cd-1", "spend", 5_000, ms(2026, 11, 1)),
        due("cd-2", "payment", 2_000, ms(2026, 11, 5)),
        due("cd-3", "refund", 300, ms(2026, 11, 6)),
        due("cd-4", "opening", 100, ms(2026, 11, 7)),
    )

    @Test fun owedIsSpendMinusPaymentsAndRefundsPlusOpening() {
        assertEquals(mapOf("Saketh - ICICI Amazon Pay Credit Card" to 280_000L), CardDues.owedByCard(dues))
    }

    @Test fun overpayingShowsAsCredit() {
        assertEquals(-50_000L, CardDues.owedByCard(listOf(due("a", "spend", 1_000, 0), due("b", "payment", 1_500, 1))).values.single())
    }

    @Test fun correctionMakesTheTargetShowAndIsNullWhenNothingToChange() {
        val c = CardDues.correctionFor("X", 280_000, 100_000, ms(2026, 11, 8)) { "n" }!!
        assertEquals("opening", c.kind)
        assertEquals(-180_000L, c.amount)
        assertEquals(100_000L, CardDues.owedByCard(dues.map { it.copy(card = "X") } + c.copy(card = "X")).values.single())
        assertNull(CardDues.correctionFor("X", 5, 5, 0) { "n" })
    }

    @Test fun matchesAnAccountByNameIgnoringCaseAndPunctuation() {
        val a = account("saketh – icici amazon pay credit card")
        assertEquals(4, CardDues.forAccount(a, dues).size)
        assertEquals(0, CardDues.forAccount(account("HDFC"), dues).size)
        assertEquals(listOf("Saketh - ICICI Amazon Pay Credit Card"), CardDues.unmatchedCards(dues, listOf(account("HDFC"))))
        assertEquals(emptyList(), CardDues.unmatchedCards(dues, listOf(account())))
    }

    @Test fun anEntryFromTheSameMessageIsNotCountedTwice() {
        val spendEntry = entry("e1", -500_000, ms(2026, 11, 1), "Ping SMS #7 Ping SMS key 20261101-abc")
        val d = due("cd-20261101-abc", "spend", 5_000, ms(2026, 11, 1))
        assertEquals(emptyList(), CardDues.flowsFor(account(), listOf(d), listOf(spendEntry)))
        assertEquals(1, CardDues.flowsFor(account(), listOf(d), emptyList()).size)
    }

    @Test fun aPaymentAlreadyRecordedAsATransferIsNotCountedTwice() {
        val transfer = entry("t1", 200_000, ms(2026, 11, 6))
        val pay = due("cd-2", "payment", 2_000, ms(2026, 11, 5))
        assertEquals(emptyList(), CardDues.flowsFor(account(), listOf(pay), listOf(transfer)))
        // a transfer of another amount, or too far away, does not cover it
        assertEquals(1, CardDues.flowsFor(account(), listOf(pay), listOf(entry("t2", 200_100, ms(2026, 11, 6)))).size)
        assertEquals(1, CardDues.flowsFor(account(), listOf(pay), listOf(entry("t3", 200_000, ms(2026, 11, 20)))).size)
    }

    @Test fun twoPaymentsOfTheSameAmountNeedTwoTransfers() {
        val p1 = due("cd-a", "payment", 2_000, ms(2026, 11, 5))
        val p2 = due("cd-b", "payment", 2_000, ms(2026, 11, 5))
        val flows = CardDues.flowsFor(account(), listOf(p1, p2), listOf(entry("t1", 200_000, ms(2026, 11, 5))))
        assertEquals(1, flows.size)
    }

    @Test fun cockpitCountsMessagesLikeEntries() {
        val setup = CardSetup("card", r(100_000), statementDay = 15, dueDay = 5, minimumDue = null)
        val today = LocalDate.of(2026, 11, 13)
        // Statement cut 15 Oct. A 12,000 spend before it (entry), a 4,000 spend and a 1,000 payment after it (messages only).
        val book = listOf(entry("s1", -1_200_000, ms(2026, 10, 3)))
        val messages = listOf(due("cd-u", "spend", 4_000, ms(2026, 10, 20)), due("cd-p", "payment", 1_000, ms(2026, 11, 4)))
        val flows = CardDues.flowsFor(account(), messages, book)
        val f = Cards.figures(setup, book, today, IST, flows)
        assertEquals(r(12_000), f.statementBalance)
        assertEquals(r(4_000), f.unbilled)
        assertEquals(r(1_000), f.paymentsSinceStatement)
        assertEquals(r(15_000), f.outstanding)
        // and with no messages the figures are the entries' own
        assertEquals(r(12_000), Cards.figures(setup, book, today, IST).outstanding)
    }
}
