package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hand-worked examples for SIP estimates and the Net worth fund lines. Each expected figure was calculated on paper first. */
class Phase8dTest {
    private val IST = ZoneId.of("Asia/Kolkata")
    private fun d(s: String) = LocalDate.parse(s)

    private fun fund(
        units: String = "100", invested: Long? = 1_000_000, nav: String? = "50", navDate: String? = "2026-11-12",
        sipAmount: Long? = null, sipDay: Int? = null, through: String = "", id: String = "f", groupId: String = "",
    ) = MutualFund(id, "Sample", "", "", units, invested, MutualFund.SOURCE_CAS, 0, nav, navDate, "", null, groupId = groupId,
        sipAmountPaise = sipAmount, sipDay = sipDay, sipThrough = through)

    // ---- SIP dates and units

    @Test fun dueDatesFallOnTheDayAndShortMonthsUseTheLastDay() {
        assertEquals(listOf(d("2026-11-05"), d("2026-12-05")), Sip.dueDates(5, d("2026-10-31"), d("2026-12-20")))
        // 31st: 30 Nov is the last day of November; 31 Dec; and 28 Feb 2027
        assertEquals(listOf(d("2026-11-30"), d("2026-12-31"), d("2027-01-31"), d("2027-02-28")), Sip.dueDates(31, d("2026-10-31"), d("2027-02-28")))
        assertTrue(Sip.dueDates(5, d("2026-11-05"), d("2026-11-30")).isEmpty()) // after is exclusive
        assertEquals(listOf(d("2026-11-05")), Sip.dueDates(5, d("2026-11-04"), d("2026-11-05"))) // up to is inclusive
        assertTrue(Sip.dueDates(0, d("2026-01-01"), d("2026-12-31")).isEmpty())
    }

    @Test fun oneInstalmentBuysAmountOverPriceToThreePlaces() {
        // 5,000.00 rupees at 52.3456: 5000 / 52.3456 = 95.51901... -> 95.519 (3 places)
        assertEquals(BigDecimal("95.519"), Sip.unitsFor(500_000, BigDecimal("52.3456")))
        // 1,000.00 at 3 = 333.333...
        assertEquals(BigDecimal("333.333"), Sip.unitsFor(100_000, BigDecimal("3")))
        assertNull(Sip.unitsFor(100_000, BigDecimal.ZERO))
    }

    @Test fun anInstalmentIsCreditedAtTheFirstPriceDatedOnOrAfterItsDay() {
        // SIP 5,000.00 on the 5th; set through 4 Nov. Price dated 5 Nov at 50.00 -> 100.000 units, one instalment.
        val f = fund(sipAmount = 500_000, sipDay = 5, through = "2026-11-04")
        val c = Sip.credit(f, BigDecimal("50"), d("2026-11-05"))
        assertEquals("100.000", c.sipUnits); assertEquals(500_000L, c.sipInvestedPaise); assertEquals(1, c.sipCount); assertEquals("2026-11-05", c.sipThrough)
        // The same price again changes nothing (nothing is credited twice).
        assertEquals(c, Sip.credit(c, BigDecimal("50"), d("2026-11-05")))
        // A later price with no new instalment only moves the date.
        val later = Sip.credit(c, BigDecimal("60"), d("2026-11-20"))
        assertEquals("100.000", later.sipUnits); assertEquals(1, later.sipCount); assertEquals("2026-11-20", later.sipThrough)
    }

    @Test fun twoMissedInstalmentsAreBothCreditedAtTheNewPrice() {
        // Through 4 Oct, price dated 6 Dec at 40.00: instalments 5 Oct? no - 5 Nov and 5 Dec (5 Oct is after 4 Oct too): 5 Oct, 5 Nov, 5 Dec = 3.
        val f = fund(sipAmount = 400_000, sipDay = 5, through = "2026-10-04")
        val c = Sip.credit(f, BigDecimal("40"), d("2026-12-06"))
        assertEquals(3, c.sipCount)
        assertEquals("300.000", c.sipUnits) // 3 x (4,000.00 / 40 = 100.000)
        assertEquals(1_200_000L, c.sipInvestedPaise)
    }

    @Test fun aSipWithNoThroughDateNeverCreditsBackInTime() {
        val f = fund(sipAmount = 500_000, sipDay = 5, through = "")
        val c = Sip.credit(f, BigDecimal("50"), d("2026-11-12"))
        assertEquals(0, c.sipCount); assertEquals("0", c.sipUnits); assertEquals("2026-11-12", c.sipThrough)
    }

    @Test fun aFundWithoutASipIsUnchanged() {
        val f = fund()
        assertEquals(f, Sip.credit(f, BigDecimal("50"), d("2026-11-12")))
    }

    @Test fun resetDropsTheEstimateAndKeepsTheSip() {
        val f = fund(sipAmount = 500_000, sipDay = 5, through = "2026-11-05").copy(sipUnits = "100.000", sipInvestedPaise = 500_000, sipCount = 1)
        val r = Sip.reset(f, d("2026-11-13"))
        assertEquals("0", r.sipUnits); assertEquals(0L, r.sipInvestedPaise); assertEquals(0, r.sipCount); assertEquals("2026-11-13", r.sipThrough)
        assertEquals(500_000L, r.sipAmountPaise); assertEquals(5, r.sipDay)
    }

    // ---- value and gain with the estimate

    @Test fun estimatedUnitsAndTheirCostAreInValueAndGain() {
        // 100 units + 100.000 estimated = 200 units x 50.00 = 10,000.00 rupees = 1,000,000 paise.
        // Put in: 10,000.00 (imported, 1,000,000 paise) + 5,000.00 (one instalment, 500,000 paise) = 1,500,000 paise. Gain = 1,000,000 - 1,500,000 = -500,000.
        val f = fund(units = "100", invested = 1_000_000).copy(sipUnits = "100.000", sipInvestedPaise = 500_000, sipCount = 1)
        assertEquals(Paise(1_000_000), MutualFunds.value(f))
        assertEquals(Paise(-500_000), MutualFunds.gain(f))
        assertTrue(MutualFunds.hasEstimate(f))
        assertEquals(BigDecimal("100"), MutualFunds.units(f)) // typed units are unchanged
    }

    @Test fun noAveragePriceStillMeansNoGainEvenWithAnEstimate() {
        val f = fund(invested = null).copy(sipUnits = "10.000", sipInvestedPaise = 500_000, sipCount = 1)
        assertNull(MutualFunds.gain(f))
        assertEquals(Paise(550_000), MutualFunds.value(f)) // 110 x 50.00 = 5,500.00
    }

    // ---- Net worth lines

    private val accounts = listOf(Account("a1", "Bank", null, null, 0, null, 0, "INR", null, 2, null, null))

    @Test fun fundsAndNpsAreSeparateLinesInTodaysNetWorth() {
        val now = ZonedDateTime.of(2026, 11, 14, 12, 0, 0, 0, IST).toInstant().toEpochMilli()
        val mf = listOf(fund(units = "100", nav = "50", navDate = "2026-11-12", id = "m1"), fund(units = "10", nav = null, navDate = null, id = "m2"))
        val nps = listOf(fund(units = "20", nav = "30", navDate = "2026-11-10", id = "n1", groupId = "acc"))
        val n = NetWorth.at(now, emptyList(), accounts, emptyList(), NetWorthExtras(funds = mf, npsSchemes = nps), IST)
        assertEquals(Paise(500_000), n.funds) // 100 x 50.00; the fund with no price is left out
        assertEquals(1, n.fundCount); assertEquals("2026-11-12", n.fundsPriceDate)
        assertEquals(Paise(60_000), n.nps); assertEquals(1, n.npsCount); assertEquals("2026-11-10", n.npsPriceDate)
        assertEquals(Paise(560_000), n.total)
    }

    @Test fun eachLineHasItsOwnSwitch() {
        val now = ZonedDateTime.of(2026, 11, 14, 12, 0, 0, 0, IST).toInstant().toEpochMilli()
        val mf = listOf(fund(id = "m1")); val nps = listOf(fund(units = "20", nav = "30", id = "n1", groupId = "acc"))
        val off = NetWorth.at(now, emptyList(), accounts, emptyList(), NetWorthExtras(funds = mf, npsSchemes = nps, includeFunds = false, includeNps = false), IST)
        assertEquals(Paise.ZERO, off.total); assertEquals(0, off.fundCount); assertEquals(0, off.npsCount)
        val onlyNps = NetWorth.at(now, emptyList(), accounts, emptyList(), NetWorthExtras(funds = mf, npsSchemes = nps, includeFunds = false), IST)
        assertEquals(Paise(60_000), onlyNps.total)
    }

    @Test fun earlierMonthsLeaveFundsAndNpsOutBecauseThereIsNoPriceHistory() {
        val now = ZonedDateTime.of(2026, 11, 14, 12, 0, 0, 0, IST).toInstant().toEpochMilli()
        val extras = NetWorthExtras(funds = listOf(fund()), npsSchemes = listOf(fund(units = "20", nav = "30", id = "n1", groupId = "acc")))
        val s = NetWorth.series(emptyList(), accounts, emptyList(), now, IST, 3, extras)
        assertEquals(Paise.ZERO, s[0].figures.funds); assertEquals(Paise.ZERO, s[1].figures.nps)
        assertEquals(Paise(500_000), s[2].figures.funds); assertEquals(Paise(60_000), s[2].figures.nps)
    }
}
