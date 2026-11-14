package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, IST).toInstant().toEpochMilli()
private val NOW = ms(2026, 11, 12) // Thursday 12 Nov 2026

private fun spend(
    id: String, name: String, rupees: Long, date: Long, kind: TransactionKind? = null, paid: Boolean = true, income: Boolean = false,
    paired: String? = null, split: String? = null, category: String = "bills", repeat: Repeat? = null, end: Long? = null,
) = Transaction(
    id = id, pairedTransactionId = paired, name = name, amount = if (income) rupees * 100 else -rupees * 100, currency = "INR", note = "", categoryId = category,
    subCategoryId = null, accountId = "a1", dateMs = date, modifiedAtMs = null, originalDueMs = null, income = income, periodLength = if (repeat != null) 1 else null,
    repeat = repeat, endDateMs = end, upcomingNotification = null, kind = kind, paid = paid, createdAnotherFuture = false, skipPaid = false, methodAdded = null,
    objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null, isPrivate = false, householdSyncedAtMs = null,
    splitGroupId = split,
)

private fun detect(entries: List<Transaction>, dismissed: Set<String> = emptySet()) = Subscriptions.detect(entries, dismissed, NOW, IST)

class Phase8gTest {
    private val netflix = listOf(
        spend("n1", "Netflix", 649, ms(2026, 8, 5)), spend("n2", "Netflix", 649, ms(2026, 9, 5)), spend("n3", "Netflix", 649, ms(2026, 10, 5)),
    )

    @Test fun threeMonthlyChargesAreSuggestedWithTheNextDateAfterToday() {
        // Gaps 31 and 30 days: monthly. 5 Nov has passed (today is 12 Nov), so the next date is 5 Dec 2026.
        val s = detect(netflix).single()
        assertEquals("netflix", s.key)
        assertEquals(Cadence.Monthly, s.cadence)
        assertEquals(3, s.charges)
        assertEquals(Paise(64_900), s.amount)
        assertEquals(ms(2026, 12, 5), s.dueMs)
        assertEquals(Paise(778_800), s.perYear) // 649 x 12 = 7,788
    }

    @Test fun twoChargesAreNotEnough() {
        assertTrue(detect(netflix.take(2)).isEmpty())
    }

    @Test fun payeeNamesDifferingInDigitsAreOnePayee() {
        val rows = listOf(spend("a", "NETFLIX 1234", 649, ms(2026, 8, 5)), spend("b", "Netflix", 649, ms(2026, 9, 5)), spend("c", "netflix 77", 649, ms(2026, 10, 5)))
        assertEquals(3, detect(rows).single().charges)
    }

    @Test fun amountsWithinTenPercentCountAndLatestIsSuggested() {
        // 1000, 1050, 1100: largest minus smallest = 100; 100 x 10 = 1000 <= 1100, so it fits.
        val rows = listOf(spend("a", "Electricity", 1000, ms(2026, 8, 3)), spend("b", "Electricity", 1050, ms(2026, 9, 3)), spend("c", "Electricity", 1100, ms(2026, 10, 3)))
        val s = detect(rows).single()
        assertEquals(Paise(110_000), s.amount)
    }

    @Test fun amountsFurtherApartAreNotARun() {
        // 1000, 1050, 1200: 1200 vs 1050 is 150; 150 x 10 = 1500 > 1200, so only one charge is in the run.
        val rows = listOf(spend("a", "Electricity", 1000, ms(2026, 8, 3)), spend("b", "Electricity", 1050, ms(2026, 9, 3)), spend("c", "Electricity", 1200, ms(2026, 10, 3)))
        assertTrue(detect(rows).isEmpty())
    }

    @Test fun anOlderOddChargeIsIgnoredWhenTheLatestThreeFit() {
        val rows = listOf(spend("x", "Gym", 5000, ms(2026, 7, 5))) + listOf(
            spend("a", "Gym", 800, ms(2026, 8, 5)), spend("b", "Gym", 800, ms(2026, 9, 5)), spend("c", "Gym", 800, ms(2026, 10, 5)),
        )
        assertEquals(3, detect(rows).single().charges)
    }

    @Test fun aCancelledSubscriptionIsNotSuggested() {
        // Last charge 5 Mar 2026; next would be 5 Apr; with 15 days of slack it is long past by 12 Nov.
        val rows = listOf(spend("a", "Hotstar", 299, ms(2026, 1, 5)), spend("b", "Hotstar", 299, ms(2026, 2, 5)), spend("c", "Hotstar", 299, ms(2026, 3, 5)))
        assertTrue(detect(rows).isEmpty())
    }

    @Test fun aChargeJustLateIsStillSuggested() {
        // Last 20 Oct; expected 20 Nov, not yet due. Last 20 Sep -> expected 20 Oct, 23 days late: past the 15 day slack.
        val ok = listOf(spend("a", "Jio", 399, ms(2026, 8, 20)), spend("b", "Jio", 399, ms(2026, 9, 20)), spend("c", "Jio", 399, ms(2026, 10, 20)))
        assertNotNull(detect(ok).singleOrNull())
        val late = listOf(spend("a", "Jio", 399, ms(2026, 7, 20)), spend("b", "Jio", 399, ms(2026, 8, 20)), spend("c", "Jio", 399, ms(2026, 9, 20)))
        assertTrue(detect(late).isEmpty())
    }

    @Test fun quarterlyIsEveryThreeMonths() {
        // 15 Jan, 15 Apr, 15 Jul 2026: gaps 90 and 91 days. Next 15 Oct is 28 days ago but inside the 30 day slack; rolls to 15 Jan 2027.
        val rows = listOf(spend("a", "Insurance", 3000, ms(2026, 1, 15)), spend("b", "Insurance", 3000, ms(2026, 4, 15)), spend("c", "Insurance", 3000, ms(2026, 7, 15)))
        val s = detect(rows).single()
        assertEquals(Cadence.Quarterly, s.cadence)
        assertEquals(ms(2027, 1, 15), s.dueMs)
        assertEquals(Paise(1_200_000), s.perYear) // 3,000 x 12 / 3 = 12,000
    }

    @Test fun yearlyChargesNeedThreeYears() {
        // 10 Dec 2023, 2024, 2025: gaps 366 and 365 days. Next is 10 Dec 2026.
        val rows = listOf(spend("a", "Domain", 1200, ms(2023, 12, 10)), spend("b", "Domain", 1200, ms(2024, 12, 10)), spend("c", "Domain", 1200, ms(2025, 12, 10)))
        val s = detect(rows).single()
        assertEquals(Cadence.Yearly, s.cadence)
        assertEquals(ms(2026, 12, 10), s.dueMs)
        assertEquals(Paise(120_000), s.perYear)
        assertTrue(detect(rows.take(2)).isEmpty())
    }

    @Test fun weeklyChargesAreFound() {
        val rows = (0..4).map { spend("w$it", "Milk", 120, ms(2026, 10, 8) + it * 7L * 86_400_000) } // 8, 15, 22, 29 Oct, 5 Nov
        val s = detect(rows).single()
        assertEquals(Cadence.Weekly, s.cadence)
        assertEquals(5, s.charges)
        assertEquals(ms(2026, 11, 12), s.dueMs) // 12 Nov is today: not before today, so it stays
    }

    @Test fun aMonthlyChargeOnThe31stStaysOnTheEndOfTheMonth() {
        // 31 Aug, 30 Sep, 31 Oct: gaps 30 and 31. The anchor is 31, so the next is 30 Nov (not 28 or the 1st of December).
        val rows = listOf(spend("a", "Rent", 9000, ms(2026, 8, 31)), spend("b", "Rent", 9000, ms(2026, 9, 30)), spend("c", "Rent", 9000, ms(2026, 10, 31)))
        val s = detect(rows).single()
        assertEquals(31, s.anchorDay)
        assertEquals(ms(2026, 11, 30), s.dueMs)
    }

    @Test fun twoChargesTheSameDayBreakTheRun() {
        val rows = netflix + spend("n4", "Netflix", 649, ms(2026, 10, 5, 18))
        assertTrue(detect(rows).isEmpty()) // last gap is 0 days: not a cadence
    }

    @Test fun onlyOrdinaryPaidMoneyOutIsLookedAt() {
        val base = netflix
        assertTrue(detect(base.map { it.copy(income = true, amount = 64_900) }).isEmpty())
        assertTrue(detect(base.map { it.copy(paid = false) }).isEmpty())
        assertTrue(detect(base.map { it.copy(pairedTransactionId = "t") }).isEmpty())
        assertTrue(detect(base.map { it.copy(splitGroupId = "s") }).isEmpty())
        assertTrue(detect(base.map { it.copy(categoryId = QuickAdd.TRANSFER_CATEGORY_ID) }).isEmpty())
        assertTrue(detect(base.map { it.copy(name = "") }).isEmpty())
        assertTrue(detect(base.map { it.copy(name = "TV") }).isEmpty()) // under 3 letters
    }

    @Test fun payeeAlreadyRepeatingIsLeftOut() {
        val tracked = spend("sub", "Netflix", 649, ms(2026, 12, 5), kind = TransactionKind.SUBSCRIPTION, paid = false, repeat = Repeat.MONTHLY)
        assertTrue(detect(netflix + tracked).isEmpty())
        // A repeating bill that has ended no longer hides it.
        val ended = tracked.copy(endDateMs = ms(2026, 6, 1))
        assertEquals(1, detect(netflix + ended).size)
        // A one-time upcoming bill does not hide it either.
        val once = tracked.copy(kind = TransactionKind.UPCOMING, repeat = null)
        assertEquals(1, detect(netflix + once).size)
    }

    @Test fun dismissedPayeeIsLeftOutAndComesBackWhenRemoved() {
        assertTrue(detect(netflix, dismissed = setOf("netflix")).isEmpty())
        assertEquals(1, detect(netflix, dismissed = emptySet()).size)
    }

    @Test fun suggestionsAreOrderedByNextDate() {
        val other = listOf(spend("g1", "Gym", 800, ms(2026, 8, 20)), spend("g2", "Gym", 800, ms(2026, 9, 20)), spend("g3", "Gym", 800, ms(2026, 10, 20)))
        assertEquals(listOf("gym", "netflix"), detect(netflix + other).map { it.key }) // gym due 20 Nov, netflix 5 Dec
    }

    @Test fun addingBuildsAnUnpaidSubscriptionAtTheSeriesRate() {
        val s = detect(netflix).single()
        val bill = Subscriptions.toBill(s, "new1")
        assertEquals(TransactionKind.SUBSCRIPTION, bill.kind)
        assertEquals(Repeat.MONTHLY, bill.repeat)
        assertEquals(1, bill.periodLength)
        assertEquals(-64_900L, bill.amount)
        assertEquals(false, bill.paid)
        assertEquals(ms(2026, 12, 5), bill.dateMs)
        assertEquals(5, bill.anchorDay)
        assertNull(bill.endDateMs)
        // Once saved, the payee counts as tracked, so it is not suggested again.
        assertTrue(detect(netflix + bill).isEmpty())
        // Quarterly is a monthly repeat of length 3.
        val q = Subscriptions.toBill(detect(listOf(spend("a", "Insurance", 3000, ms(2026, 1, 15)), spend("b", "Insurance", 3000, ms(2026, 4, 15)), spend("c", "Insurance", 3000, ms(2026, 7, 15)))).single(), "q1")
        assertEquals(3, q.periodLength); assertEquals(Repeat.MONTHLY, q.repeat)
    }
}
