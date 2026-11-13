package com.mansaketh.chitta.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriftAmountTest {
    @Test fun wholeRupees() {
        val c = paiseFromDriftDouble(27502.0)
        assertEquals(Paise.rupees(27_502), c.paise)
        assertFalse(c.moved)
    }

    @Test fun twoDecimalValuesThatDoublesCannotHoldExactly() {
        // 613.34 + 613.33 + 613.33 is the split example in section 12; 0.1+0.2 style values must not drift.
        assertEquals(61_334L, paiseFromDriftDouble(613.34).paise.value)
        assertEquals(61_333L, paiseFromDriftDouble(613.33).paise.value)
        assertFalse(paiseFromDriftDouble(613.33).moved)
        assertEquals(30L, paiseFromDriftDouble(0.1 + 0.2).paise.value) // 0.30000000000000004 -> 30
    }

    @Test fun sumOfSplitLinesEqualsTotal() {
        val total = paiseFromDriftDouble(1840.0).paise
        val lines = listOf(613.34, 613.33, 613.33).map { paiseFromDriftDouble(it).paise }
        assertEquals(total, lines.reduce { a, b -> a + b })
    }

    @Test fun negativeAmountsKeepTheirSign() {
        assertEquals(-1_528_00L, paiseFromDriftDouble(-1528.0).paise.value)
    }

    @Test fun halfPaiseRoundsToEvenAndIsFlaggedAsMoved() {
        val up = paiseFromDriftDouble(0.125) // 12.5 paise -> 12 (even)
        assertEquals(12L, up.paise.value)
        assertTrue(up.moved)
        assertEquals(14L, paiseFromDriftDouble(0.135).paise.value) // 13.5 -> 14 (even)
        assertEquals(-12L, paiseFromDriftDouble(-0.125).paise.value)
    }

    @Test fun zeroAndTinyValues() {
        assertEquals(0L, paiseFromDriftDouble(0.0).paise.value)
        assertEquals(1L, paiseFromDriftDouble(0.01).paise.value)
        assertEquals(0L, paiseFromDriftDouble(0.004).paise.value)
        assertTrue(paiseFromDriftDouble(0.004).moved)
    }

    @Test fun largeAmountsStayExact() {
        assertEquals(12_345_678_901_234_560L, paiseFromDriftDouble(123_456_789_012_345.6).paise.value)
    }
}
