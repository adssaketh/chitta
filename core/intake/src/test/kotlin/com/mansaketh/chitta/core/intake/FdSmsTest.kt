package com.mansaketh.chitta.core.intake

import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val IST = ZoneId.of("Asia/Kolkata")
private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(IST).toInstant().toEpochMilli()
private fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(IST).toLocalDate()

/** The first two messages and the negative cases are the ones the old app's own test used (fixed_deposit_test.dart). */
class FdSmsTest {
    @Test fun readsAFullBookingMessage() {
        val body = "Dear Customer, your Fixed Deposit of Rs. 1,50,000.00 has been booked on 03-Nov-2026 for 12 months at 7.10% p.a. Maturity date: 03-Nov-2027. Maturity amount Rs. 1,60,900. FD No. XXXX5521. -Axis Bank"
        val d = assertNotNull(FdSms.parse(body, "AX-AXISBK", ms(2026, 11, 3), "", IST))
        assertEquals(15_000_000, d.principal)
        assertEquals("7.1", d.ratePct)
        assertEquals(LocalDate.of(2026, 11, 3), day(d.startMs))
        assertEquals(LocalDate.of(2027, 11, 3), day(d.maturityMs))
        assertEquals(16_090_000, d.maturityAmount)
        assertEquals("5521", d.fdNumber)
        assertEquals("Axis Bank", d.bank)
        assertTrue(d.fromSms)
    }

    @Test fun tenureStandsInForAMissingMaturityDate() {
        val body = "Your FD of INR 50000 is created for a tenure of 6 months. ROI 6.5%. A/c 90012345. HDFC Bank"
        val d = assertNotNull(FdSms.parse(body, "VM-HDFCBK", ms(2026, 11, 3), "", IST))
        assertEquals(LocalDate.of(2027, 5, 3), day(d.maturityMs))
        assertEquals("6.5", d.ratePct)
        assertEquals("HDFC Bank", d.bank)
    }

    @Test fun readsTheContractSample() {
        val item = json("contract/ping-paddhu/samples/auto-send-fixed-deposit.json").list("sms")[0]
        val d = assertNotNull(FdSms.parse(item.str("body"), item.str("address"), item.jsonObject["date"]!!.toString().toLong(), item.str("id"), IST))
        assertEquals(3_000_000, d.principal)
        assertEquals("7.1", d.ratePct)
        assertEquals("0001", d.fdNumber)
        assertEquals("sms-n1793003600000-33c82e5b", d.id)
    }

    @Test fun notABookingOrNotReadableGivesNull() {
        assertNull(FdSms.parse("Your FD of Rs 50000 has matured and proceeds credited", "AX-AXISBK", 0, "", IST))
        assertNull(FdSms.parse("Rs 500 debited for UPI txn", "AX-AXISBK", 0, "", IST))
        assertNull(FdSms.parse("Your FD has been booked", "AX-AXISBK", 0, "", IST)) // no amount: not guessed
        assertNull(FdSms.parse("Your FD of Rs 50000 has been booked at 7%", "AX-AXISBK", 0, "", IST)) // no maturity: not guessed
        assertNull(FdSms.parse("Your FD of Rs 50000 booked on 03-Nov-2026. Maturity date: 31-02-2027.", "AX-AXISBK", ms(2026, 11, 3), "", IST))
    }

    @Test fun maturityBeforeStartIsRejected() {
        assertNull(FdSms.parse("Your FD of Rs 50000 booked on 03-Nov-2026. Maturity date: 03-Nov-2025.", "AX-AXISBK", ms(2026, 11, 3), "", IST))
    }

    @Test fun monthEndTenureClampsToTheLastDay() {
        val d = assertNotNull(FdSms.parse("Your FD of Rs 10000 is booked on 31-Aug-2026 for 6 months at 7%.", "AX-AXISBK", ms(2026, 8, 31), "x", IST))
        assertEquals(LocalDate.of(2027, 2, 28), day(d.maturityMs))
    }
}
