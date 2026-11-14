package com.mansaketh.chitta.core.intake

import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Invented client id; the amounts and layout follow the sample ledger screenshot (Equity sheet, 1 to 8 June 2021). */
private val SAMPLE = listOf(
    listOf<String>(),
    listOf("ZERODHA"),
    listOf("Client ID", "AB1234"),
    listOf("Ledger for Equity from 2021-06-01 to 2021-06-08"),
    listOf("Particulars", "Posting Date", "Cost Center", "Voucher Type", "Debit", "Credit", "Net Balance"),
    listOf("Opening Balance", "", "", "", "", "", "1528.7769"),
    listOf("DP Charges for sale of HDFC on 2021-06-03", "2021-06-03", "NSE-EQ - Z", "Journal Entry", "15.93", "0", "1512.8469"),
    listOf("Net settlement  for  NSE-Equity with settlement number: 2021103", "2021-06-03", "NSE-EQ - Z", "Book Voucher", "0", "2594.3799", "4107.2267"),
    listOf("Closing Balancce", "", "", "", "", "", "4107.2267"),
)

class ZerodhaLedgerTest {
    private fun ready(rows: List<List<String>>) = assertIs<ZerodhaLedger.Result.Ready>(ZerodhaLedger.read(rows)).ledger

    @Test fun readsTheSampleLedger() {
        val l = ready(SAMPLE)
        assertEquals(LocalDate.of(2021, 6, 1), l.from)
        assertEquals(LocalDate.of(2021, 6, 8), l.to)
        assertEquals(152_878L, l.opening)   // 1528.7769 rounded to the paisa
        assertEquals(410_723L, l.closing)   // 4107.2267
        assertEquals(2, l.lines.size)
        assertEquals(1_593L, l.lines[0].debit)
        assertEquals(259_438L, l.lines[1].credit) // 2594.3799
        assertEquals("Journal Entry", l.lines[0].voucher)
    }

    @Test fun theFourthDecimalDoesNotRefuseAGoodFile() {
        // 1512.8469 + 2594.3799 is 4107.2268 but the file says 4107.2267: Zerodha's own rounding, well inside a paisa
        assertIs<ZerodhaLedger.Result.Ready>(ZerodhaLedger.read(SAMPLE))
    }

    @Test fun csvRowsLeadWithTheOpeningBalanceAndUseTwoDecimals() {
        val rows = ready(SAMPLE).toCsvRows()
        assertEquals(listOf("Date", "Particulars", "Voucher Type", "Debit", "Credit"), rows[0])
        assertEquals(listOf("2021-06-01", "Opening balance (from the ledger file)", "Opening", "", "1528.78"), rows[1])
        assertEquals(listOf("2021-06-03", "DP Charges for sale of HDFC on 2021-06-03", "Journal Entry", "15.93", ""), rows[2])
        assertEquals("2594.38", rows[3][4])
        assertEquals(4, rows.size)
    }

    @Test fun aRowThatDoesNotAddUpRefusesTheFile() {
        val bad = SAMPLE.map { if (it.getOrNull(4) == "15.93") it.toMutableList().also { r -> r[6] = "1500.00" } else it }
        val r = assertIs<ZerodhaLedger.Result.Refused>(ZerodhaLedger.read(bad))
        assertTrue("Row 7" in r.reason, r.reason)
        assertTrue(r.looksLikeLedger)
    }

    @Test fun aWrongClosingBalanceRefusesTheFile() {
        val bad = SAMPLE.map { if (it.firstOrNull() == "Closing Balancce") listOf("Closing Balancce", "", "", "", "", "", "9999.00") else it }
        assertIs<ZerodhaLedger.Result.Refused>(ZerodhaLedger.read(bad))
    }

    @Test fun aMissingClosingRowRefusesTheFile() {
        val r = assertIs<ZerodhaLedger.Result.Refused>(ZerodhaLedger.read(SAMPLE.filter { it.firstOrNull() != "Closing Balancce" }))
        assertTrue("closing" in r.reason.lowercase())
    }

    @Test fun anotherTableIsNotALedger() {
        val r = assertIs<ZerodhaLedger.Result.Refused>(ZerodhaLedger.read(listOf(listOf("Date", "Amount"), listOf("2026-01-01", "5"))))
        assertEquals(false, r.looksLikeLedger)
    }

    @Test fun anOpeningBalanceBelowZeroBecomesMoneyOut() {
        val rows = listOf(
            listOf("Particulars", "Posting Date", "Cost Center", "Voucher Type", "Debit", "Credit", "Net Balance"),
            listOf("Opening Balance", "", "", "", "", "", "-100"),
            listOf("Funds added", "2026-02-02", "", "Bank Receipts", "0", "500", "400"),
            listOf("Closing Balance", "", "", "", "", "", "400"),
        )
        val csv = ready(rows).toCsvRows()
        assertEquals("100.00", csv[1][3])
        assertEquals("", csv[1][4])
    }

    @Test fun datesAsDayFirstTextOrExcelSerialNumbers() {
        assertEquals(LocalDate.of(2021, 6, 3), ZerodhaLedger.parseDate("03-06-2021"))
        assertEquals(LocalDate.of(2021, 6, 3), ZerodhaLedger.parseDate("03/06/2021"))
        assertEquals(LocalDate.of(2021, 6, 3), ZerodhaLedger.parseDate("44350"))
        assertNull(ZerodhaLedger.parseDate("soon"))
    }
}

class XlsxReaderTest {
    private fun zip(parts: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> parts.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    private val workbook = """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Equity" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private val rels = """<Relationships><Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/></Relationships>"""
    private val shared = """<sst><si><t>Particulars</t></si><si><t>Tom &amp; Co</t></si><si><r><t>Net </t></r><r><t>Balance</t></r></si></sst>"""
    private val sheet = """<worksheet><sheetData>
        <row r="2"><c r="A2" t="s"><v>0</v></c><c r="C2" t="s"><v>2</v></c></row>
        <row r="4"><c r="B4" t="inlineStr"><is><t>inline</t></is></c><c r="D4"><v>15.93</v></c></row>
        <row r="5"><c r="A5" t="s"><v>1</v></c><c r="B5"/><c r="C5"><v>2594.3799</v></c></row>
        </sheetData></worksheet>"""

    @Test fun readsSharedInlineAndNumberCellsInPlace() {
        val rows = assertNotNull(XlsxReader.firstSheet(zip(mapOf("xl/workbook.xml" to workbook, "xl/_rels/workbook.xml.rels" to rels, "xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheet))))
        assertEquals(5, rows.size)
        assertEquals(listOf<String>(), rows[0])
        assertEquals(listOf("Particulars", "", "Net Balance"), rows[1])
        assertEquals(listOf("", "inline", "", "15.93"), rows[3])
        assertEquals(listOf("Tom & Co", "", "2594.3799"), rows[4])
    }

    @Test fun aLedgerInsideAnXlsxIsRead() {
        fun row(n: Int, vararg v: String) = "<row r=\"$n\">" + v.mapIndexed { i, s -> "<c r=\"${'A' + i}$n\" t=\"inlineStr\"><is><t>$s</t></is></c>" }.joinToString("") + "</row>"
        val sheetXml = "<worksheet><sheetData>" +
            row(4, "Ledger for Equity from 2021-06-01 to 2021-06-08") +
            row(5, "Particulars", "Posting Date", "Cost Center", "Voucher Type", "Debit", "Credit", "Net Balance") +
            row(6, "Opening Balance", "", "", "", "", "", "100") +
            row(7, "Funds added", "2021-06-02", "", "Bank Receipts", "0", "50", "150") +
            row(8, "Closing Balance", "", "", "", "", "", "150") + "</sheetData></worksheet>"
        val rows = assertNotNull(XlsxReader.firstSheet(zip(mapOf("xl/worksheets/sheet1.xml" to sheetXml))))
        val l = assertIs<ZerodhaLedger.Result.Ready>(ZerodhaLedger.read(rows)).ledger
        assertEquals(15_000L, l.closing)
    }

    @Test fun notAZipIsRefused() {
        assertNull(XlsxReader.firstSheet("not a zip".toByteArray()))
        assertEquals(false, XlsxReader.looksLikeXlsx("a,b\n1,2".toByteArray()))
    }
}
