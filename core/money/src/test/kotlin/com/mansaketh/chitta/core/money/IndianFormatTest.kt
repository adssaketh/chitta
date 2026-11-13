package com.mansaketh.chitta.core.money

import kotlin.test.Test
import kotlin.test.assertEquals

class IndianFormatTest {
    private fun r(whole: Long) = Paise.rupees(whole)

    @Test fun groupsInTheIndianWay() {
        assertEquals("0", IndianFormat.group("0"))
        assertEquals("999", IndianFormat.group("999"))
        assertEquals("1,000", IndianFormat.group("1000"))
        assertEquals("27,502", IndianFormat.group("27502"))
        assertEquals("1,00,000", IndianFormat.group("100000"))
        assertEquals("12,34,56,789", IndianFormat.group("123456789"))
    }

    // Figures below are the worked examples on the money-rules pages of the design PDF.
    @Test fun formatsTheWorkedExamples() {
        assertEquals("₹60,000", IndianFormat.rupees(r(60_000)))
        assertEquals("₹26,520", IndianFormat.rupees(r(26_520)))
        assertEquals("₹5,978", IndianFormat.rupees(r(5_978)))
        assertEquals("₹27,502", IndianFormat.rupees(r(27_502)))
        assertEquals("₹1,44,460", IndianFormat.rupees(r(144_460)))
        assertEquals("₹1,00,752", IndianFormat.rupees(r(100_752)))
        assertEquals("₹4,98,710", IndianFormat.rupees(r(498_710)))
        assertEquals("₹4,83,539", IndianFormat.rupees(r(483_539)))
        assertEquals("₹15,171", IndianFormat.rupees(r(15_171)))
    }

    @Test fun showsPaiseOnlyWhenPresentUnlessAsked() {
        assertEquals("₹613.34", IndianFormat.rupees(Paise.of(613, 34)))
        assertEquals("₹613.05", IndianFormat.rupees(Paise.of(613, 5)))
        assertEquals("₹613", IndianFormat.rupees(r(613)))
        assertEquals("₹613.00", IndianFormat.rupees(r(613), alwaysShowPaise = true))
    }

    @Test fun negativeUsesATrueMinus() {
        assertEquals("−₹1,200", IndianFormat.rupees(Paise(-120_000)))
        assertEquals("−₹0.50", IndianFormat.rupees(Paise(-50)))
        assertEquals("₹0", IndianFormat.rupees(Paise.ZERO))
    }

    @Test fun roundedHeroFigureMatchesPerDayExample() {
        // ₹27,502 ÷ 18 = ₹1,527.89 -> shown as ₹1,528
        val perDay = BudgetFigures.perDay(r(27_502), 18)
        assertEquals("₹1,528", IndianFormat.rupeesRounded(perDay))
        assertEquals("₹1,528", IndianFormat.rupeesRounded(Paise.of(1527, 50)))
        assertEquals("₹1,527", IndianFormat.rupeesRounded(Paise.of(1527, 49)))
    }

    @Test fun keypadEntryGroupsWhileTyping() {
        assertEquals("", IndianFormat.entry(""))
        assertEquals("0", IndianFormat.entry("0"))
        assertEquals("1,234", IndianFormat.entry("1234"))
        assertEquals("1,234.", IndianFormat.entry("1234."))
        assertEquals("1,234.5", IndianFormat.entry("1234.5"))
        assertEquals("12,34,567", IndianFormat.entry("1234567"))
        assertEquals("0.5", IndianFormat.entry(".5"))
        assertEquals("7", IndianFormat.entry("007"))
        assertEquals("1,200+350.5", IndianFormat.entry("1200+350.5"))
        assertEquals("1,00,000÷2", IndianFormat.entry("100000÷2"))
    }

    @Test fun spokenAmountUsesPlainDigits() {
        assertEquals("rupees 1200", IndianFormat.spoken(r(1_200)))
        assertEquals("rupees 613 and 34 paise", IndianFormat.spoken(Paise.of(613, 34)))
        assertEquals("minus rupees 5", IndianFormat.spoken(Paise(-500)))
    }
}
