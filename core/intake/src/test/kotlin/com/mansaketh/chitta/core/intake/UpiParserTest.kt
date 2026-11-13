package com.mansaketh.chitta.core.intake

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** All text here is invented. No real names, ids or account digits. */
class UpiParserTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private val phonePe = """
        Transaction Successful
        ₹697.43
        Paid to
        TEST CAFE
        testcafe@okbank
        Transfer Details
        Message
        lunch
        PhonePe Transaction ID
        T2611071234567890123
        Debited from
        XX1234
        ₹697.43
        UTR: 600000000001
        3:45 pm on 07 Nov 2026
    """.trimIndent()

    @Test fun phonePeScreenshotReadsExactPaiseAndFields() {
        val e = UpiParser.parsePhonePeScreenshot(phonePe)!!
        assertEquals(69743L, e.amountPaise)
        assertTrue(e.isExpense)
        assertFalse(e.amountUnconfirmed)
        assertEquals("Test Cafe", e.party)
        assertEquals(LocalDateTime.of(2026, 11, 7, 15, 45), e.date)
        assertEquals("600000000001", e.utr)
        assertEquals("lunch", e.message)
        assertEquals("1234", e.accountDigits.takeLast(4))
        assertEquals("T2611071234567890123", e.txnId)
    }

    @Test fun receivedPaymentIsIncome() {
        val e = UpiParser.parsePhonePeScreenshot("Transaction Successful\n₹1,200.00\nReceived from\nTEST SENDER\n₹1,200.00\n9:05 am on 08 Nov 2026")!!
        assertFalse(e.isExpense)
        assertEquals(120000L, e.amountPaise)
    }

    @Test fun failedOrDatelessPagesAreRefused() {
        assertNull(UpiParser.parsePhonePeScreenshot("Transaction Failed\n₹10\nPaid to\nX\n1:00 pm on 07 Nov 2026"))
        assertNull(UpiParser.parsePhonePeScreenshot("Transaction Successful\n₹10\nPaid to\nTEST"))
    }

    @Test fun rupeeSignReadAsSevenIsDroppedAndMarkedUnconfirmed() {
        // "₹286" came out as "7286" on both lines; no rupee sign anywhere
        val e = UpiParser.parsePhonePeScreenshot("Transaction Successful\n7286\nPaid to\nTEST SHOP\n7286\n1:00 pm on 07 Nov 2026")!!
        assertEquals(28600L, e.amountPaise)
        assertTrue(e.amountUnconfirmed)
        // a real thousand has a comma, so its 7 is kept
        val big = UpiParser.parsePhonePeScreenshot("Transaction Successful\n7,286\nPaid to\nTEST SHOP\n7,286\n1:00 pm on 07 Nov 2026")!!
        assertEquals(728600L, big.amountPaise)
    }

    @Test fun disagreeingSignedAmountsNeedACheck() {
        val e = UpiParser.parsePhonePeScreenshot("Transaction Successful\n₹100\nPaid to\nTEST SHOP\n₹900\n1:00 pm on 07 Nov 2026")!!
        assertTrue(e.amountUnconfirmed)
    }

    @Test fun anIdIsNeverTakenAsTheAmount() {
        // 600000000001 is above the plausible limit, so only ₹50 can be the amount
        val e = UpiParser.parsePhonePeScreenshot("Transaction Successful\n₹50\nPaid to\nTEST SHOP\nUTR ₹600000000001\n₹50\n1:00 pm on 07 Nov 2026")!!
        assertEquals(5000L, e.amountPaise)
    }

    @Test fun googlePayStylePageIsReadByTheOtherAppsReader() {
        val text = "Completed\n₹250\nTo TEST STORE\nUPI transaction ID\n600000000002\n7 Nov 2026, 4:10 pm\nGoogle Pay\nXXXX 4321"
        val e = UpiParser.parseScreenshot(text)!!
        assertEquals("Google Pay", e.app)
        assertEquals(25000L, e.amountPaise)
        assertEquals("Test Store", e.party)
        assertEquals("600000000002", e.utr)
        assertEquals(LocalDateTime.of(2026, 11, 7, 16, 10), e.date)
    }

    @Test fun statementTextGivesEachPaymentItsOwnBlock() {
        val text = """
            Nov 07, 2026
            03:45 PM
            Paid to TEST CAFE DEBIT ₹120.50
            Transaction ID T1234567890
            UTR No: 600000000003
            Nov 06, 2026
            Received from TEST SENDER CREDIT ₹1,000
            Nov 05, 2026
            Paid to FAILED SHOP DEBIT ₹5 FAILED
        """.trimIndent()
        val list = UpiParser.parseStatementText(text)
        assertEquals(2, list.size)
        assertEquals(12050L, list[0].amountPaise)
        assertTrue(list[0].isExpense)
        assertEquals("Test Cafe", list[0].party)
        assertFalse(list[1].isExpense)
        assertEquals(100000L, list[1].amountPaise)
    }

    @Test fun csvIsReadByColumnNamesWithDebitAndCreditColumns() {
        val csv = "Date,Time,Details,Debit,Credit,Status\n07/11/2026,03:45 PM,Paid to TEST CAFE,120.50,,SUCCESS\n06-11-2026,10:00,Received from TEST SENDER,,\"1,000.00\",SUCCESS\n05/11/2026,09:00,Paid to X,5,,FAILED\n"
        val list = UpiParser.parseCsv(csv)
        assertEquals(2, list.size)
        assertEquals(12050L, list[0].amountPaise)
        assertTrue(list[0].isExpense)
        assertEquals(LocalDateTime.of(2026, 11, 7, 15, 45), list[0].date)
        assertEquals(100000L, list[1].amountPaise)
        assertFalse(list[1].isExpense)
        assertEquals(emptyList(), UpiParser.parseCsv("hello,world\n1,2"))
    }

    @Test fun moreThanTwoDecimalsRoundHalfEven() {
        val csv = "Date,Amount,Type\n07/11/2026,10.005,DEBIT\n07/11/2026,10.015,DEBIT\n"
        assertEquals(listOf(1000L, 1002L), UpiParser.parseCsv(csv).map { it.amountPaise })
    }

    @Test fun bestReadingPrefersSignedThenMostVotesThenEarlier() {
        val a = UpiParser.parsePhonePeScreenshot(phonePe)!!
        val unsigned = a.copy(amountPaise = 99900, amountUnconfirmed = true)
        val other = a.copy(amountPaise = 12300)
        assertEquals(69743L, UpiParser.chooseBestReading(listOf(unsigned, unsigned, a))!!.amountPaise)
        assertEquals(69743L, UpiParser.chooseBestReading(listOf(a, other))!!.amountPaise) // tie: the earlier
        assertEquals(69743L, UpiParser.chooseBestReading(listOf(null, other, a, a))!!.amountPaise)
        assertEquals(99900L, UpiParser.chooseBestReading(listOf(unsigned))!!.amountPaise)
        assertNull(UpiParser.chooseBestReading(listOf(null, null)))
    }

    @Test fun rowsHaveStableIdsAndTheSamePaymentCountsOnce() {
        val e = UpiParser.parsePhonePeScreenshot(phonePe)!!
        val known = KnownAccounts.parse("""{"accounts":[{"key":"axis:account:1234","name":"Axis 1234","wallet":"Axis"}]}""")
        val r = UpiParser.toRow(e, known, zone)
        assertEquals("pp-T2611071234567890123", r.sourceId)
        assertEquals("Axis", r.walletName)
        assertTrue(r.knownAccount)
        assertTrue(r.isUpi)
        assertEquals(1, UpiParser.toIntake(listOf(e, e), known, zone).rows.size)
        assertEquals(1, UpiParser.toIntake(listOf(e, e), known, zone).skipped)
        val unknown = UpiParser.toRow(e, KnownAccounts.EMPTY, zone)
        assertEquals("Account ••1234", unknown.accountName)
        assertNotNull(unknown.note)
    }
}
