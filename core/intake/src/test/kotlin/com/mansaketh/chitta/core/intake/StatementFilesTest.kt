package com.mansaketh.chitta.core.intake

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shapes the real files came in (numbers and names invented): glued date and folio, a printed NPS page, an Excel holdings report. */
class StatementFilesTest {
    // PDFBox gives the date and the folio with no space between them, and wraps long names onto a second line.
    private val casText = """
Consolidated Account Summary
( As on Date: 13-Nov-2026?)
Invested Value
(INR)
Market Value
(INR)
Gain/Loss
(Absolute)
Balance
Units
NAV DateScheme DetailsFolio No. NAV
Alpha Retirement Fund - Equity Plan - Direct Plan -Growth
Option
50,000.00 60,000.50
10,000.50
(+20.00%)
1,000.500 12-Nov-202622939772 59.9750
Beta ELSS Tax Saver Fund - Direct Plan 30,000.00 29,000.25
(999.75)
(-3.33%)
900.250 12-Nov-202616810005 32.2121
Gamma Flexi Cap Fund - Direct Plan Growth 0.00 0.00
0.00
(+0.00%)
0.000 12-Nov-202616810005 94.8149
Total 89,000.75
Page 2 of 5
""".trimIndent()

    @Test fun gluedDateAndFolioAreRead() {
        val r = CasSummary.read(casText) as CasReading.Ready
        assertEquals(2, r.holdings.size)
        assertEquals(1, r.exitedRows)
        assertEquals(listOf("22939772", "16810005"), r.holdings.map { it.folio })
        assertEquals("Alpha Retirement Fund - Equity Plan - Direct Plan -Growth Option", r.holdings[0].name)
        assertEquals(6_000_050L, r.holdings[0].valuePaise)
        assertEquals(LocalDate.of(2026, 11, 13), r.asOf)
    }

    @Test fun folioWithSpaceStillWorks() {
        val r = CasSummary.read(casText.replace("12-Nov-202622939772", "12-Nov-2026 22939772")) as CasReading.Ready
        assertEquals("22939772", r.holdings[0].folio)
    }

    // A printed (screenshot) NPS page: the heading comes first, then the figures, then the table; the recogniser may add junk before a name and misread the tier's I.
    private val npsPrinted = """
NATIONAL PENSION SYSTEM
Transaction Statement - Tier I
Investment Details as on 31-10-2026
No of Contributions Total Contribution Total Withdrawal Deductions due to Current Valuation Notional Gain/Loss
11 13677.00 0.00 287.95 14560.77 883.77
Scheme Name Total Units Latest NAV Value at NAV IRR
Hees PENSION FUND SCHEME E - TIER | POP 19.1467 70.1151 1342.47 3.03%
ICICI PENSION FUND SCHEME C - TIER 1 177.8750 46.0491 8190.98
ICICI PENSION FUND SCHEME G - TIER I 132.0648 38.0671 5027.32
Total 14560.77
Changes made during selected period
""".trimIndent()

    @Test fun printedNpsPageIsReadAndNamesAreTidied() {
        assertTrue(NpsStatement.looksLike(npsPrinted))
        val r = NpsStatement.read(npsPrinted) as NpsReading.Ready
        assertEquals(LocalDate.of(2026, 10, 31), r.priceDate)
        assertEquals(1_367_700L, r.contributionsPaise)
        assertEquals(1_456_077L, r.statedValuePaise)
        assertEquals(listOf("19.1467", "177.8750", "132.0648"), r.schemes.map { it.units.toPlainString() })
        assertEquals("ICICI PENSION FUND SCHEME E - TIER I POP", r.schemes[0].name)
        assertEquals("ICICI PENSION FUND SCHEME C - TIER I", r.schemes[1].name)
    }

    @Test fun npsMisreadAmountIsRefused() {
        val bad = npsPrinted.replace("8190.98", "8190.93")
        assertTrue(NpsStatement.read(bad) is NpsReading.Refused)
    }

    private val sheet = listOf(
        listOf("Holdings report as on 16-11-2026"),
        emptyList(),
        listOf("Name", "SOMEONE"),
        listOf("PAN", "ABCDE1234F"),
        emptyList(),
        listOf("Summary"),
        listOf("Total Invested value", "30000.00"),
        listOf("Total Current value", "33000.50"),
        emptyList(),
        listOf("Fund Name", "ISIN", "Total Units", "Invested Value", "Current Value", "Folio Number"),
        listOf("Alpha Fund Direct Growth", "INF000A01001", "100.5", "10000.00", "11000.25", "111"),
        listOf("Beta Fund Direct Growth", "INF000B01002", "200", "20000", "22000.25", "222"),
    )

    @Test fun excelHoldingsReportIsReadWithIsinAndChecked() {
        assertTrue(HoldingsReport.looksLike(sheet))
        val r = HoldingsReport.read(sheet) as CasReading.Ready
        assertEquals(LocalDate.of(2026, 11, 16), r.asOf)
        assertEquals(listOf("INF000A01001", "INF000B01002"), r.holdings.map { it.isin })
        assertEquals(1_100_025L, r.holdings[0].valuePaise)
        assertEquals("109.4552", r.holdings[0].nav.toPlainString()) // 11000.25 / 100.5, four decimals
        assertEquals(3_300_050L, r.holdings.sumOf { it.valuePaise })
    }

    @Test fun excelTotalsThatDoNotMatchRefuseTheFile() {
        val bad = sheet.map { r -> if (r.firstOrNull() == "Total Current value") listOf("Total Current value", "33000.00") else r }
        assertTrue(HoldingsReport.read(bad) is CasReading.Refused)
        assertTrue(HoldingsReport.read(sheet.map { r -> if (r.firstOrNull() == "Alpha Fund Direct Growth") r.toMutableList().also { it[2] = "abc" } else r }) is CasReading.Refused)
    }

    @Test fun otherSheetsAreNotHoldingsReports() {
        assertEquals(false, HoldingsReport.looksLike(listOf(listOf("Date", "Amount"), listOf("1", "2"))))
    }

    @Test fun wrappedTableCellsJoinTheirNumbersInOneRow() {
        // "name" wraps onto a second line "POP"; the numbers sit between the two lines, to the right.
        val lines = listOf(
            OcrLine("ICICI PENSION FUND SCHEME E - TIER I", 10, 100, 130), OcrLine("POP", 10, 132, 160),
            OcrLine("19.1467 70.1151 1342.47", 700, 115, 145),
            OcrLine("ICICI PENSION FUND SCHEME C - TIER I", 10, 240, 270), OcrLine("POP", 10, 272, 300), OcrLine("177.8750 46.0491 8190.98", 700, 255, 285),
        )
        val rows = OcrRows.join(lines).lines()
        assertEquals(2, rows.size)
        assertEquals("ICICI PENSION FUND SCHEME E - TIER I POP 19.1467 70.1151 1342.47", rows[0])
    }
}
