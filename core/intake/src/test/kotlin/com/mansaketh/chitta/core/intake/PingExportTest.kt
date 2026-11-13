package com.mansaketh.chitta.core.intake

import kotlinx.serialization.json.jsonObject
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PingExportTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val base = "contract/ping-paddhu"

    @Test fun contentKeysMatchPingsGoldenVectors() {
        val keys = json("$base/golden/ids-and-keys.json").list("contentKeys")
        assertTrue(keys.size >= 5)
        for (k in keys) assertEquals(k.str("key"), PingExport.contentKey(k.str("body"), k.jsonObject["dateMs"]!!.toString().toLong(), zone), k.str("body"))
    }

    @Test fun singleMessageSampleReadsAsOneExpenseOf50Rupees() {
        val r = PingExport.parse(resource("$base/samples/auto-send-single.json"), KnownAccounts.EMPTY, zone)
        assertEquals(1, r.rows.size)
        val row = r.rows[0]
        assertEquals(5000L, row.amountPaise)
        assertTrue(row.isExpense)
        assertEquals("axisbank", row.brandKey)
        assertEquals(false, row.knownAccount)
    }

    @Test fun legacyFileWithoutOptionalFieldsStillReads() {
        val r = PingExport.parse(resource("$base/samples/legacy-minimal.json"), KnownAccounts.EMPTY, zone)
        assertEquals(1, r.rows.size)
        assertEquals("", r.rows[0].brandKey)
    }

    @Test fun fixedDepositIsParkedAndNotImportedAsAnEntry() {
        val r = PingExport.parse(resource("$base/samples/auto-send-fixed-deposit.json"), KnownAccounts.EMPTY, zone)
        assertEquals(listOf("n1793003600000-33c82e5b"), r.fixedDepositIds)
    }

    @Test fun dateRangeSampleKeepsRangeAndNewestFirst() {
        val r = PingExport.parse(resource("$base/samples/date-range.json"), KnownAccounts.EMPTY, zone)
        assertTrue(r.rows.zipWithNext().all { (a, b) -> a.dateMs >= b.dateMs })
        assertTrue(r.fromMs != null && r.toMs != null)
    }

    @Test fun notAPingFileIsRefusedInPlainWords() {
        val e = assertFailsWith<IntakeFormatException> { PingExport.parse("{\"hello\": 1}", KnownAccounts.EMPTY, zone) }
        assertEquals("This file is not a Ping export.", e.message)
        assertFailsWith<IntakeFormatException> { PingExport.parse("not json", KnownAccounts.EMPTY, zone) }
    }

    @Test fun badBrandKeyIsDropped() {
        assertEquals("", PingExport.validBrandKey("../etc"))
        assertEquals("", PingExport.validBrandKey("Axis"))
        assertEquals("axisbank", PingExport.validBrandKey("axisbank"))
        assertEquals("", PingExport.validBrandKey(null))
    }

    @Test fun duplicateIdsInOneFileCountOnce() {
        val one = json("$base/samples/auto-send-single.json").list("sms")[0]
        val text = PingExport.exportOf(listOf(one.jsonObject, one.jsonObject))
        assertEquals(1, PingExport.parse(text, KnownAccounts.EMPTY, zone).rows.size)
    }

    @Test fun noteAndRefRoundTrip() {
        val row = PingExport.parse(resource("$base/samples/auto-send-single.json"), KnownAccounts.EMPTY, zone).rows[0]
        assertTrue(row.note.contains("Ping SMS #n1793000000000-c8c2791f"))
        assertTrue(row.note.contains("Ping SMS key 20261026-57643486"))
        val ref = PingExport.refOfNote(row.note)!!
        assertEquals("AX-AXISBK-S", ref.address)
        assertEquals(1793000000000L, ref.dateMs)
        assertNull(ref.smsId)
        assertEquals("12345", PingExport.refOfNote("Ping SMS #12345 Ping SMS key 20261026-1 Ping SMS from AX-HDFCBK-S")!!.smsId)
        assertNull(PingExport.refOfNote("Ping SMS #pp-abc"))
        assertNull(PingExport.refOfNote("nothing here"))
    }

    @Test fun knownAccountsMatchByEndingDigitsAndRefuseAmbiguity() {
        val k = KnownAccounts.parse("""{"version":1,"accounts":[
            {"key":"axis:account:1234","name":"Axis 1234","wallet":"Axis"},
            {"key":"hdfc:account:5678","name":"HDFC 5678","wallet":"HDFC"},
            {"key":"icici:account:5678","name":"ICICI 5678","wallet":"ICICI"}]}""")
        assertEquals("Axis 1234", k.lookup("axis:account:1234")?.name)
        assertEquals("Axis", k.byDigits("1234")?.wallet)
        assertNull(k.byDigits("5678")) // two different accounts: nothing is guessed
        assertNull(k.byDigits("12"))
        assertFailsWith<IntakeFormatException> { KnownAccounts.parse("{\"accounts\":[{\"key\":\"bad\",\"name\":\"x\"}]}") }
    }
}
