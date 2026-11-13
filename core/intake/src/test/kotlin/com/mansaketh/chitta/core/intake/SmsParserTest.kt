package com.mansaketh.chitta.core.intake

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The same synthetic messages and expected answers the old app checked (they came from the Python reference). */
class SmsParserTest {
    @Test
    fun everyFixtureReadsAsExpected() {
        val cases = json("sms_synthetic.json").list("cases")
        assertTrue(cases.size >= 20)
        for (c in cases) {
            val id = c.str("id")
            val got = SmsParser.parse(c.str("sender"), c.str("text"))
            if (!c.has("expected")) {
                assertNull(got, "case $id should not be a transaction")
                continue
            }
            val e = c.jsonObject["expected"]!!
            assertNotNull(got, "case $id should be a transaction")
            assertEquals(e.paise("amount"), got.amountPaise, "case $id amount")
            assertEquals(e.bool("isExpense"), got.isExpense, "case $id direction")
            assertEquals(e.str("merchant"), got.merchant, "case $id merchant")
            assertEquals(e.bool("cardPayment"), got.cardPayment, "case $id card payment")
            assertEquals(e.str("category"), got.category, "case $id category")
            assertEquals(e.str("title"), got.title, "case $id title")
            assertEquals(e.str("instrumentKey"), got.instrumentKey, "case $id instrument")
        }
    }

    @Test
    fun amountsAreExactPaise() {
        val r = SmsParser.parse("AX-AXISBK-S", "INR 1,200.50 credited to A/c no. XX1234 on 07-11-26 by UPI/000000000002/Test sender. Axis Bank")!!
        assertEquals(120_050L, r.amountPaise)
        assertEquals(false, r.isExpense)
    }

    @Test
    fun otpsAndRequestsAreNotTransactions() {
        assertNull(SmsParser.parse("AX-AXISBK-S", "123456 is your OTP for payment of Rs 500. Do not share."))
        assertNull(SmsParser.parse("AX-AXISBK-S", "Rs 500 will be debited from your account on 12-11-26 for mandate."))
    }

    @Test
    fun senderIdsLookLikeSenderIds() {
        assertTrue(SmsParser.looksLikeSenderId("AX-AXISBK-S"))
        assertTrue(SmsParser.looksLikeSenderId("JAJIOPAY"))
        assertEquals(false, SmsParser.looksLikeSenderId("+919999999999"))
        assertEquals(false, SmsParser.looksLikeSenderId("Mum"))
    }
}
