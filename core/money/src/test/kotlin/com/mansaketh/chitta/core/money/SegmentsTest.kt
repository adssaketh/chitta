package com.mansaketh.chitta.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SegmentsTest {
    private fun r(whole: Long) = Paise.rupees(whole)

    @Test fun ringFromTheHomeExample() {
        // ₹60,000 budget: spent ₹26,520 (22.1 ticks), bills ₹5,978 (4.98), safe ₹27,502 (22.92)
        val ring = Segments.ring(r(60_000), r(26_520), r(5_978))!!
        assertEquals(22, ring.spent)
        assertEquals(5, ring.bills)
        assertEquals(23, ring.safe)
        assertEquals(RingTicks.TOTAL, ring.spent + ring.bills + ring.safe)
        assertEquals(46, ring.safePercent) // 27,502 / 60,000 = 45.84%
        assertFalse(ring.over)
    }

    @Test fun ringAlwaysAddsToFifty() {
        for (spent in listOf(0L, 1L, 333L, 12_345L, 59_999L)) {
            for (bills in listOf(0L, 7L, 1_111L)) {
                val ring = Segments.ring(r(60_000), r(spent), r(bills))!!
                assertEquals(50, ring.spent + ring.bills + ring.safe, "spent=$spent bills=$bills")
            }
        }
    }

    @Test fun ringIsHiddenWithoutABudget() {
        assertNull(Segments.ring(Paise.ZERO, r(100), r(0)))
    }

    @Test fun ringIsFullAndOverWhenSpendPlusBillsExceedBudget() {
        val ring = Segments.ring(r(10_000), r(8_000), r(3_000))!!
        assertTrue(ring.over)
        assertEquals(0, ring.safe)
        assertEquals(50, ring.spent + ring.bills)
        assertEquals(0, ring.safePercent)
    }

    @Test fun ringExactlyAtBudgetIsNotOver() {
        val ring = Segments.ring(r(10_000), r(8_000), r(2_000))!!
        assertFalse(ring.over)
        assertEquals(0, ring.safe)
    }

    @Test fun gaugeForTheHomeBudget() {
        // ₹26,520 of ₹60,000 = 44.2%
        assertEquals(4, Segments.gauge(r(26_520), r(60_000), 10).filled)
        assertEquals(8, Segments.gauge(r(26_520), r(60_000), 20).filled)
    }

    @Test fun gaugeEdges() {
        assertEquals(0, Segments.gauge(Paise.ZERO, r(60_000)).filled)
        assertEquals(1, Segments.gauge(r(1), r(60_000)).filled) // any spend lights one segment
        val full = Segments.gauge(r(60_000), r(60_000))
        assertEquals(10, full.filled)
        assertFalse(full.over) // at the limit is not over it
        val over = Segments.gauge(r(66_300), r(60_000))
        assertEquals(10, over.filled)
        assertTrue(over.over)
    }
}
