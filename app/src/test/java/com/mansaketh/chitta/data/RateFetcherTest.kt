package com.mansaketh.chitta.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RateFetcherTest {
    private val file = """{"date":"2026-11-12","inr":{"usd":0.011234,"eur":0.0097,"aed":0.0412,"bad":"x","zero":0}}"""

    @Test fun keepsRatesAsPlainDecimalTextUpperCased() {
        val rows = RateFetcher.parse(file, 1L)
        assertEquals("0.011234", rows.first { it.currency == "USD" }.perBase)
        assertEquals("INR", rows.first().base)
        assertEquals("2026-11-12", rows.first().asOfDate)
    }

    @Test fun skipsNonNumbersAndZero() {
        val codes = RateFetcher.parse(file, 1L).map { it.currency }
        assertTrue("BAD" !in codes && "ZERO" !in codes)
    }

    @Test fun scientificNotationBecomesPlainText() {
        val rows = RateFetcher.parse("""{"date":"d","inr":{"xyz":1.5E-5}}""", 1L)
        assertEquals("0.000015", rows.single().perBase)
    }

    @Test fun missingTableGivesNothing() {
        assertTrue(RateFetcher.parse("""{"date":"d"}""", 1L).isEmpty())
    }
}
