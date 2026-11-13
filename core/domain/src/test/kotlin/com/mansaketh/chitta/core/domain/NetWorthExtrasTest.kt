package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.model.LoanTerms
import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 12, 0, 0, 0, IST).toInstant().toEpochMilli()

class NetWorthExtrasTest {
    private val accounts = listOf(Account("a1", "Bank", null, null, 0, null, 0, "INR", null, 2, null, null))
    private fun loan(income: Boolean = false) = Objective("L9", ObjectiveKind.LOAN, "Card loan", 0, 0, null, 0, null, null, null, null, income, false, false, "a1")
    private val terms = LoanTerms("L9", 10_000_000, "15", 12, 902_500, ms(2026, 12, 5), 100_000, "18", false, listOf(1, 2, 3), "a1", "", null)
    private fun deposit(closed: Boolean = false, start: Long = ms(2026, 1, 1)) =
        FixedDeposit("d", "X", "", 10_000_000, "7", start, ms(2027, 1, 1), 0, null, null, closed, "", false, "", null)

    @Test fun anInstalmentLoanOwesWhatIsStillToPayByTheSchedule() {
        val asOf = ms(2027, 1, 15) // instalments 1 (5 Dec) and 2 (5 Jan) are due; 3 (5 Feb) is ticked but not due yet
        val rows = Instalments.schedule(terms, IST)
        val s = Instalments.summarize(terms.copy(paidInstalments = emptyList()), IST)
        val expected = s.totalPayable - rows[0].totalDue - rows[1].totalDue
        val n = NetWorth.at(asOf, emptyList(), accounts, listOf(loan()), NetWorthExtras(terms = listOf(terms)), IST)
        assertEquals(expected, n.youOwe)
        assertEquals(Paise.ZERO, n.owedToYou)
        assertEquals(Paise.ZERO - expected, n.total)
    }

    @Test fun aLentInstalmentLoanIsOwedToYou() {
        val n = NetWorth.at(ms(2027, 1, 15), emptyList(), accounts, listOf(loan(income = true)), NetWorthExtras(terms = listOf(terms)), IST)
        assertTrue(n.owedToYou.value > 0)
        assertEquals(Paise.ZERO, n.youOwe)
    }

    @Test fun switchedOffFallsBackToThePlainLoanFigure() {
        val n = NetWorth.at(ms(2027, 1, 15), emptyList(), accounts, listOf(loan()), NetWorthExtras(terms = listOf(terms), includeInstalments = false), IST)
        assertEquals(Paise.ZERO, n.youOwe) // no linked entries and no offset: the plain figure is zero
    }

    @Test fun beforeTheLoanExistedItIsNotOwed() {
        val n = NetWorth.at(ms(2026, 6, 1), emptyList(), accounts, listOf(loan()), NetWorthExtras(terms = listOf(terms)), IST)
        assertEquals(Paise.ZERO, n.youOwe)
    }

    @Test fun depositsCountOnlyWhenSwitchedOnAndOnlyOpenOnes() {
        val asOf = ms(2026, 7, 2)
        val off = NetWorth.at(asOf, emptyList(), accounts, emptyList(), NetWorthExtras(deposits = listOf(deposit())), IST)
        assertEquals(Paise.ZERO, off.deposits)
        val on = NetWorth.at(asOf, emptyList(), accounts, emptyList(), NetWorthExtras(deposits = listOf(deposit(), deposit(closed = true)), includeDeposits = true), IST)
        assertEquals(1, on.depositCount)
        assertEquals(FixedDeposits.currentValue(deposit(), asOf, IST), on.deposits)
        assertEquals(on.deposits, on.total)
    }

    @Test fun aDepositNotYetBookedIsNotCountedInAnEarlierMonth() {
        val on = NetWorth.at(ms(2025, 12, 31), emptyList(), accounts, emptyList(), NetWorthExtras(deposits = listOf(deposit()), includeDeposits = true), IST)
        assertEquals(0, on.depositCount)
    }
}
