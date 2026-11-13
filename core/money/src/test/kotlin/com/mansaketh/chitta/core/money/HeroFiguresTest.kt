package com.mansaketh.chitta.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Master section 12 worked examples for the cash check and the hero choice. */
class HeroFiguresTest {
    private val r = Paise.Companion::rupees

    @Test fun cashCheckExample() {
        // ₹1,44,460 − ₹18,458 − ₹25,250 = ₹1,00,752
        assertEquals(r(100_752), CashCheck(r(144_460), r(18_458), r(25_250)).value)
    }

    @Test fun safeToSpendExampleStillHolds() {
        // ₹60,000 − ₹26,520 − ₹5,978 = ₹27,502
        assertEquals(r(27_502), BudgetFigures.safeToSpend(r(60_000), r(26_520), r(5_978)))
    }

    @Test fun safeLeadsWhenCashIsHigher() {
        val c = HeroFigures.choose(r(27_502), CashCheck(r(144_460), r(18_458), r(25_250)), preferCash = false)!!
        assertEquals(Hero.SafeToSpend, c.hero)
        assertEquals(r(27_502), c.value)
        assertEquals(false, c.warn)
    }

    @Test fun lowerCashCheckWinsAndWarns() {
        val c = HeroFigures.choose(r(27_502), CashCheck(r(30_000), r(10_000), r(0)), preferCash = false)!!
        assertEquals(Hero.CashCheck, c.hero)
        assertEquals(r(20_000), c.value)
        assertTrue(c.warn)
    }

    @Test fun settingForcesCashCheckWithoutWarning() {
        val c = HeroFigures.choose(r(27_502), CashCheck(r(144_460), r(18_458), r(25_250)), preferCash = true)!!
        assertEquals(Hero.CashCheck, c.hero)
        assertEquals(false, c.warn)
    }

    @Test fun noBudgetShowsTheCashCheckAsHero() {
        val c = HeroFigures.choose(null, CashCheck(r(1_000), r(200), r(0)), preferCash = false)!!
        assertEquals(Hero.CashCheck, c.hero)
        assertEquals(r(800), c.value)
    }

    @Test fun nothingToShow() {
        assertNull(HeroFigures.choose(null, null, preferCash = false))
    }

    @Test fun negativeCashCheckIsKept() {
        // negative balances are real; the figure is not clamped
        assertEquals(r(-500), CashCheck(r(100), r(600), r(0)).value)
    }
}
