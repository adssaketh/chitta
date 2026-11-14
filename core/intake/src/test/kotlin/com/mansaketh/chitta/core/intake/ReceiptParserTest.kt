package com.mansaketh.chitta.core.intake

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** All receipts here are invented. Every expected figure is worked by hand from the text. */
class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 14)
    private fun read(t: String) = ReceiptParser.parse(t.trimIndent(), today)

    private val restaurant = """
        TEST CAFE
        Tax Invoice
        GSTIN 36ABCDE1234F1Z5
        Date: 12/11/2026  Time 13:05
        Masala Dosa  2 x 90.00   180.00
        Sub Total   180.00
        CGST 2.5%   4.50
        SGST 2.5%   4.50
        Grand Total  ₹189.00
    """

    @Test fun `restaurant bill reads total date and name`() {
        val r = read(restaurant)
        assertEquals(18_900L, r.amountPaise)               // 180.00 + 4.50 + 4.50 = 189.00
        assertEquals(AmountSource.TOTAL_LINE, r.amountSource)
        assertEquals(LocalDate.of(2026, 11, 12), r.date)   // day first
        assertEquals("Test Cafe", r.merchant)
    }

    @Test fun `sub total tax and discount lines are never the total`() {
        val r = read("""
            Shop
            Sub Total 500.00
            Discount 50.00
            CGST 9% 40.50
            Total Qty 3
        """)
        // no total line at all: the largest money-like amount is used and labelled as a guess
        assertEquals(50_000L, r.amountPaise)
        assertEquals(AmountSource.LARGEST, r.amountSource)
    }

    @Test fun `total on the next line is read`() {
        val r = read("""
            Test Store
            Total
            ₹ 1,250.00
        """)
        assertEquals(125_000L, r.amountPaise)
        assertEquals(AmountSource.TOTAL_LINE, r.amountSource)
    }

    @Test fun `next line that is an item is not read as the total`() {
        val r = read("""
            Test Store
            Total
            Milk 1L 62.00
        """)
        assertEquals(6_200L, r.amountPaise)               // no total amount found; largest money-like amount
        assertEquals(AmountSource.LARGEST, r.amountSource)
    }

    @Test fun `total with brackets and no decimals`() {
        assertEquals(45_000L, read("Test Store\nTotal (incl. GST) 450").amountPaise)
    }

    @Test fun `strongest kind wins and the last one of it`() {
        val r = read("""
            Test Store
            Total 100.00
            Net Amount Payable 95.00
            Total Savings 5.00
        """)
        assertEquals(9_500L, r.amountPaise)               // "Net Amount Payable" beats plain "Total"; "Total Savings" is not a total
    }

    @Test fun `last plain total wins over an earlier one`() {
        assertEquals(30_000L, read("Test Store\nTotal 100.00\nTotal 300.00").amountPaise)
    }

    @Test fun `lakh commas`() {
        assertEquals(12_500_050L, read("Test Store\nGrand Total Rs. 1,25,000.50").amountPaise)
    }

    @Test fun `no total line uses the largest money-like amount and ignores id lines`() {
        val r = read("""
            Test Store
            Phone 9876543210.00
            Tea 40.00
            Samosa 120.50
        """)
        assertEquals(12_050L, r.amountPaise)
        assertEquals(AmountSource.LARGEST, r.amountSource)
    }

    @Test fun `whole numbers without a total label are not money`() {
        assertNull(read("Test Store\nBill 1234\nTable 7").amountPaise)
    }

    @Test fun `an id read as money is refused`() {
        assertNull(read("Test Store\nTotal 9876543210").amountPaise)   // 9,87,65,43,210 rupees is over the cap
    }

    @Test fun `dates in common Indian and written formats`() {
        assertEquals(LocalDate.of(2026, 11, 5), read("Shop\n2026-11-05").date)
        assertEquals(LocalDate.of(2026, 11, 5), read("Shop\n05-Nov-26").date)
        assertEquals(LocalDate.of(2026, 11, 5), read("Shop\nNov 5, 2026").date)
        assertEquals(LocalDate.of(2026, 11, 5), read("Shop\n5 November 2026").date)
        assertEquals(LocalDate.of(2026, 11, 5), read("Shop\n05.11.26").date)
        assertEquals(LocalDate.of(2026, 4, 3), read("Shop\n03/04/2026").date)        // day first: 3 April
        assertEquals(LocalDate.of(2026, 11, 13), read("Shop\n11/13/2026").date)      // 13 cannot be a month: month first
    }

    @Test fun `a date from the future or before 2000 is not used and the next one is`() {
        assertEquals(LocalDate.of(2026, 11, 14), read("Shop\n20/12/2026\n14/11/2026").date)
        assertNull(read("Shop\n01/01/1998").date)
        assertEquals(LocalDate.of(2026, 11, 15), read("Shop\n15/11/2026").date)       // one day ahead is allowed for clock zones
    }

    @Test fun `first date in the text wins`() {
        assertEquals(LocalDate.of(2026, 10, 1), read("Shop\nBill date 01/10/2026\nDue 20/10/2026").date)
    }

    @Test fun `shop name skips invoice words, numbers and welcome`() {
        assertEquals("Reliance Fresh", read("Welcome to Reliance Fresh\nTax Invoice").merchant)
        assertEquals("Sri Sai Stores", read("TAX INVOICE\n1234567\nGSTIN 36ABCDE1234F1Z5\nSRI SAI STORES").merchant)
        assertEquals("Mixed Case Shop", read("Mixed Case Shop").merchant)         // a mixed-case name is kept as printed
        assertNull(read("12345\n67890").merchant)
    }

    @Test fun `empty or unreadable text gives an empty reading`() {
        assertTrue(ReceiptParser.parse("", today).isEmpty)
        assertTrue(read("???\n---").isEmpty)
    }

    @Test fun `merge takes a total line over a largest guess and fills gaps`() {
        val a = ReceiptReading(5_000, AmountSource.LARGEST, null, "Shop A")
        val b = ReceiptReading(4_000, AmountSource.TOTAL_LINE, LocalDate.of(2026, 11, 1), null)
        val m = ReceiptParser.merge(listOf(a, b))
        assertEquals(4_000L, m.amountPaise)
        assertEquals(AmountSource.TOTAL_LINE, m.amountSource)
        assertEquals(LocalDate.of(2026, 11, 1), m.date)
        assertEquals("Shop A", m.merchant)
        assertTrue(ReceiptParser.merge(emptyList()).isEmpty)
    }
}
