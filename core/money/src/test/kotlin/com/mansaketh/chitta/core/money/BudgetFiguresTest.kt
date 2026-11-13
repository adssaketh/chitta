package com.mansaketh.chitta.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vectors are the worked examples on the money-rules pages of Paddhu-Pusthakam-final-design.pdf. */
class BudgetFiguresTest {
    private fun r(whole: Long) = Paise.rupees(whole)

    private val budget = r(60_000)
    private val spent = r(26_520)
    private val bills = r(5_978)

    @Test fun safeToSpend() {
        // ₹60,000 − ₹26,520 − ₹5,978 = ₹27,502
        assertEquals(r(27_502), BudgetFigures.safeToSpend(budget, spent, bills))
    }

    @Test fun safeToSpendCanGoNegativeWhenOver() {
        assertEquals(r(-1_000), BudgetFigures.safeToSpend(r(10_000), r(8_000), r(3_000)))
    }

    @Test fun perDay() {
        // ₹27,502 ÷ 18 = ₹1,528 (shown rounded); stored to the paisa
        assertEquals(Paise(152_789), BudgetFigures.perDay(r(27_502), 18))
        // last day: show the full amount
        assertEquals(r(27_502), BudgetFigures.perDay(r(27_502), 1))
        assertEquals(r(27_502), BudgetFigures.perDay(r(27_502), 0))
    }

    @Test fun range() {
        // ₹27,502 ÷ ₹2,210 = 12 days; day 12 of 30, so 18 days left and 6 short of month end
        assertEquals(RangeResult.Days(12), BudgetFigures.range(r(27_502), spent, daysElapsed = 12))
        assertEquals(18 - 12, 6)
    }

    @Test fun rangeThinDataFallbacks() {
        assertEquals(RangeResult.NotEnoughData, BudgetFigures.range(r(27_502), r(5_000), daysElapsed = 6))
        assertEquals(RangeResult.NoSpending, BudgetFigures.range(r(27_502), Paise.ZERO, daysElapsed = 12))
        assertEquals(RangeResult.NoSpending, BudgetFigures.range(r(27_502), Paise.ZERO, daysElapsed = 3))
    }

    @Test fun rangeIsNeverNegative() {
        assertEquals(RangeResult.Days(0), BudgetFigures.range(r(-500), spent, daysElapsed = 12))
        assertEquals(RangeResult.Days(0), BudgetFigures.range(Paise.ZERO, spent, daysElapsed = 12))
    }

    @Test fun idealPace() {
        // ₹60,000 × 12 ÷ 30 = ₹24,000
        assertEquals(r(24_000), BudgetFigures.idealSpend(budget, 12, 30))
        assertNull(BudgetFigures.idealSpend(budget, 1, 30)) // day 1: no pace shown
    }

    @Test fun overPaceAndOverLimitAreDifferent() {
        val ideal = BudgetFigures.idealSpend(budget, 12, 30)
        assertEquals(PaceStatus.OverPace, BudgetFigures.paceStatus(spent, ideal, budget)) // ₹26,520 > ₹24,000
        assertEquals(PaceStatus.OnPace, BudgetFigures.paceStatus(r(20_000), ideal, budget))
        assertEquals(PaceStatus.OverLimit, BudgetFigures.paceStatus(r(61_000), ideal, budget))
        assertEquals(PaceStatus.OnPace, BudgetFigures.paceStatus(r(100), null, budget))
    }

    @Test fun forecast() {
        // ₹26,520 ÷ 12 × 30 = ₹66,300
        assertEquals(r(66_300), BudgetFigures.forecast(spent, 12, 30))
        assertNull(BudgetFigures.forecast(spent, 6, 30)) // shown from day 7 only
        assertEquals(r(66_300 / 30 * 30), BudgetFigures.forecast(spent, 12, 30))
    }

    @Test fun limitDayIsWhereTheForecastCrossesTheLimit() {
        // ₹2,210 a day: ₹60,000 is reached on day 27.15, so day 28
        assertEquals(28, BudgetFigures.limitDay(spent, budget, 12, 30))
        // a forecast that stays under the limit has no marker
        assertNull(BudgetFigures.limitDay(r(10_000), budget, 12, 30))
        // before day 7 there is no forecast, so no marker
        assertNull(BudgetFigures.limitDay(spent, budget, 6, 30))
        // already past the limit: that is "over limit", not a forecast marker
        assertNull(BudgetFigures.limitDay(r(61_000), budget, 12, 30))
    }

    @Test fun limitDayOnTheBoundary() {
        // exactly ₹2,000 a day for 30 days = ₹60,000: the limit is reached on day 30
        assertEquals(30, BudgetFigures.limitDay(r(24_000), budget, 12, 30))
    }
}
