package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 12, 0, 0, 0, IST).toInstant().toEpochMilli()

private fun fd(
    principal: Long = 10_000_000, rate: String = "7", start: Long = ms(2026, 1, 1), maturity: Long = ms(2027, 1, 1), comp: Int = 4,
    bank: Long? = null, id: String = "a", bankName: String = "X", number: String = "", sms: String = "", closed: Boolean = false,
) = FixedDeposit(id, bankName, number, principal, rate, start, maturity, comp, bank, null, closed, "", false, sms, null)

class FixedDepositsTest {
    @Test fun quarterlyOneYearIsWorkedOutByHand() {
        // 1,00,000 at 7% compounded quarterly for 365 days: 1.0175^4 = 1.07185903..., so 7,185.90 interest
        assertEquals(Paise(10_718_590), FixedDeposits.maturityValue(fd(), IST))
        assertEquals(Paise(718_590), FixedDeposits.interestAtMaturity(fd(), IST))
    }

    @Test fun simpleInterestIsExact() {
        assertEquals(Paise(10_700_000), FixedDeposits.maturityValue(fd(comp = 0), IST))
        // 182 days of simple 7% on 1,00,000 = 3,490.41
        assertEquals(Paise(349_041), FixedDeposits.accruedInterest(fd(comp = 0), ms(2026, 7, 2), IST))
    }

    @Test fun accruedInterestPartWay() {
        // 182 days of 7% quarterly on 1,00,000 = 3,520.78
        assertEquals(Paise(352_078), FixedDeposits.accruedInterest(fd(), ms(2026, 7, 2), IST))
        assertEquals(Paise(10_352_078), FixedDeposits.currentValue(fd(), ms(2026, 7, 2), IST))
    }

    @Test fun nothingBeforeStartAndEverythingAfterMaturity() {
        assertEquals(Paise.ZERO, FixedDeposits.accruedInterest(fd(), ms(2025, 12, 1), IST))
        assertEquals(FixedDeposits.interestAtMaturity(fd(), IST), FixedDeposits.accruedInterest(fd(), ms(2028, 1, 1), IST))
    }

    @Test fun theBanksFigureWins() {
        val d = fd(bank = 10_750_000)
        assertEquals(Paise(10_750_000), FixedDeposits.maturityValue(d, IST))
        assertTrue(FixedDeposits.isStatedByBank(d))
        // half the days, half the stated interest: 365 days total, 182 gone -> 750,000 * 182 / 365 = 373,972.6 -> 373,973
        assertEquals(Paise(373_973), FixedDeposits.accruedInterest(d, ms(2026, 7, 2), IST))
    }

    @Test fun zeroRateGivesNoInterestAndNoCrash() {
        assertEquals(Paise.ZERO, FixedDeposits.interestAtMaturity(fd(rate = "0"), IST))
        assertEquals(Paise.ZERO, FixedDeposits.interestAtMaturity(fd(rate = "abc"), IST))
    }

    @Test fun daysToMaturityAndMatured() {
        assertEquals(30, FixedDeposits.daysToMaturity(fd(), ms(2026, 12, 2), IST))
        assertTrue(FixedDeposits.hasMatured(fd(), ms(2027, 1, 1), IST))
        assertFalse(FixedDeposits.hasMatured(fd(), ms(2026, 12, 31), IST))
    }

    @Test fun leapYearCountsRealDays() {
        val d = fd(start = ms(2027, 12, 1), maturity = ms(2028, 12, 1), comp = 0)
        assertEquals(366, FixedDeposits.days(d.startMs, d.maturityMs, IST))
        // 366/365 of a year at 7%
        assertEquals(Paise(10_701_918), FixedDeposits.maturityValue(d, IST))
    }

    @Test fun totalCountsOpenDepositsOnly() {
        val open = fd(id = "1", comp = 0)
        val closed = fd(id = "2", closed = true, comp = 0)
        assertEquals(Paise(10_700_000), FixedDeposits.totalValue(listOf(open, closed), ms(2027, 6, 1), IST))
    }

    @Test fun sameDepositRules() {
        assertTrue(FixedDeposits.same(fd(sms = "7"), fd(sms = "7", bankName = "Y"), IST))
        assertTrue(FixedDeposits.same(fd(number = "5521"), fd(number = "5521", principal = 1), IST))
        assertTrue(FixedDeposits.same(fd(bankName = "x"), fd(), IST))
        assertFalse(FixedDeposits.same(fd(), fd(bankName = "Y"), IST))
        assertFalse(FixedDeposits.same(fd(number = "1"), fd(number = "2", principal = 5), IST))
    }

    @Test fun maturingSoonSkipsClosedAndFarOnes() {
        val soon = fd(id = "s", maturity = ms(2026, 12, 10))
        val far = fd(id = "f", maturity = ms(2027, 6, 10))
        val closed = fd(id = "c", maturity = ms(2026, 12, 5), closed = true)
        val got = FixedDeposits.maturingSoon(listOf(far, soon, closed), ms(2026, 12, 1), IST, 14)
        assertEquals(listOf("s"), got.map { it.id })
    }
}
