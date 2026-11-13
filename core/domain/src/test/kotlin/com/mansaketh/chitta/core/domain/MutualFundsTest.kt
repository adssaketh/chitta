package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hand-worked examples. Every expected figure below was calculated on paper first. */
class MutualFundsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(d: String) = LocalDate.parse(d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun fund(
        units: String = "100.500", invested: Long? = 4_000_000, nav: String? = "52.3456", navDate: String? = "2026-11-12", asOf: String = "2026-11-01", id: String = "f",
    ) = MutualFund(id, "Sample fund", "INF000A00000", "", units, invested, MutualFund.SOURCE_MANUAL, ms(asOf), nav, navDate, "", null)

    @Test fun valueIsUnitsTimesPriceRoundedToThePaisa() {
        // 100.500 x 52.3456 = 5,260.7328 rupees = 526,073.28 paise -> 526,073
        assertEquals(Paise(526_073), MutualFunds.value(fund()))
    }

    @Test fun halfPaiseRoundsToEven() {
        // 10 units x 0.12345 = 1.2345 rupees = 123.45 paise -> 123. 10 x 0.12355 = 123.55 -> 124 (not a tie). A true tie: 1 x 0.0125 = 1.25 paise -> 1; 1 x 0.0135 = 1.35 -> 1; 1 x 0.0175 = 1.75 -> 2.
        assertEquals(Paise(123), MutualFunds.value(fund(units = "10", nav = "0.12345")))
        assertEquals(Paise(124), MutualFunds.value(fund(units = "10", nav = "0.12355")))
        assertEquals(Paise(2), MutualFunds.value(fund(units = "1", nav = "0.0150")))
        assertEquals(Paise(2), MutualFunds.value(fund(units = "1", nav = "0.0250")))
    }

    @Test fun gainIsValueMinusInvested() {
        // value 5,260.73 rupees; invested 4,000,000 paise = 40,000.00 rupees; gain = -34,739.27 rupees
        assertEquals(Paise(526_073 - 4_000_000), MutualFunds.gain(fund()))
        // 200 units x 60.00 = 12,000.00 rupees; invested 10,000.00 -> gain 2,000.00
        val f = fund(units = "200", nav = "60", invested = 1_000_000)
        assertEquals(Paise(1_200_000), MutualFunds.value(f))
        assertEquals(Paise(200_000), MutualFunds.gain(f))
    }

    @Test fun noPriceMeansNoValueAndNoGain() {
        val f = fund(nav = null, navDate = null)
        assertNull(MutualFunds.value(f)); assertNull(MutualFunds.gain(f))
        assertNull(MutualFunds.value(fund(nav = "0"))); assertNull(MutualFunds.value(fund(nav = "abc")))
    }

    @Test fun noAverageMeansValueButNoGain() {
        val f = fund(invested = null)
        assertEquals(Paise(526_073), MutualFunds.value(f))
        assertNull(MutualFunds.gain(f))
    }

    @Test fun badUnitsGiveNoValue() {
        assertNull(MutualFunds.value(fund(units = "")))
        assertNull(MutualFunds.value(fund(units = "-5")))
        assertNull(MutualFunds.value(fund(units = "0")))
    }

    @Test fun investedFromUnitsAndAveragePrice() {
        // 150.250 units at 40.00 = 6,010.00 rupees = 601,000 paise
        assertEquals(601_000L, MutualFunds.investedFrom(BigDecimal("150.250"), BigDecimal("40.00")))
        // 33.333 x 30.1234 = 1,004.1032922 rupees = 100,410.33 paise -> 100,410
        assertEquals(100_410L, MutualFunds.investedFrom(BigDecimal("33.333"), BigDecimal("30.1234")))
    }

    @Test fun staleAfterThirtyFiveDays() {
        val f = fund(asOf = "2026-10-01")
        assertEquals(35L, MutualFunds.daysSinceUnits(f, ms("2026-11-05"), zone))
        assertFalse(MutualFunds.isStale(f, ms("2026-11-05"), zone))
        assertTrue(MutualFunds.isStale(f, ms("2026-11-06"), zone))
    }

    @Test fun totalsLeaveOutFundsWithoutAnAverageAndSayHowManyTheGainCovers() {
        val a = fund(id = "a", units = "200", nav = "60", invested = 1_000_000)          // value 1,200,000 gain +200,000
        val b = fund(id = "b", units = "50", nav = "10", invested = null)                // value 50,000 no average
        val c = fund(id = "c", nav = null, navDate = null)                              // no price
        val t = MutualFunds.totals(listOf(a, b, c))
        assertEquals(3, t.count); assertEquals(2, t.priced); assertEquals(1, t.unpriced)
        assertEquals(Paise(1_250_000), t.value)
        assertEquals(Paise(200_000), t.gain)
        assertEquals(1, t.gainCovers)
    }

    @Test fun priceDateIsTheOldestOne() {
        val a = fund(id = "a", navDate = "2026-11-12")
        val b = fund(id = "b", navDate = "2026-11-10")
        assertEquals("2026-11-10", MutualFunds.totals(listOf(a, b)).priceDate)
    }

    @Test fun emptyListGivesZerosAndNoGain() {
        val t = MutualFunds.totals(emptyList())
        assertEquals(0, t.count); assertEquals(Paise.ZERO, t.value); assertNull(t.gain); assertNull(t.priceDate)
    }

    @Test fun isinShape() {
        assertTrue(MutualFunds.isIsinShape("INF846K01DP8"))
        assertTrue(MutualFunds.isIsinShape(" inf846k01dp8 "))
        assertFalse(MutualFunds.isIsinShape("INF846K01DP"))
        assertFalse(MutualFunds.isIsinShape("12F846K01DP8"))
    }
}
