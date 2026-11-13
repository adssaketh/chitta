package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.MutualFund
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Invented funds and figures in the order the real report's text comes out (name, cost and value, gain, percent, units and date, folio and price). */
class CasSummaryTest {
    // 10 units x 12.50 = 125.00; 200.500 x 20.2000 = 4,050.10; sum 4,175.10
    private val sample = """
Consolidated Account Summary
( As on Date: 13-Nov-2026 )
Some Investor Name
PAN :ABCDE1234F
MFCentralCASSummary_v2.1_0000000000_ALL-13/11/2026 3:21:42pm Page 1 of 3
SoA Holdings Demat Holdings
Consolidated Account Summary
( As on Date: 13-Nov-2026 )
MFCentralCASSummary_v2.1_0000000000_ALL-13/11/2026 3:21:42pm Page 2 of 3
SoA Holdings Demat Holdings
Invested Value
(INR)
Market Value
(INR)
Gain/Loss
(Absolute)
Balance
Units
NAV Date
Scheme Details
Folio No. NAV
Sample Alpha Equity Fund - Direct Plan -Growth
Option
100.00 125.00
25.00
(+25.00%)
10.000 12-Nov-2026
111111 12.50
Sample Beta Bond Fund - Direct Plan 5,000.00 4,050.10
(949.90)
(-19.00%)
200.500 12-Nov-2026
222222 20.2000
Sample Gamma Index Fund - Direct Growth 0.00 0.00
0.00
(+0.00%)
0.000 12-Nov-2026
333333 1,088.6440
MFCentralCASSummary_v2.1_0000000000_ALL-13/11/2026 3:21:42pm Page 3 of 3
Total 4,175.10
""".trimIndent()

    @Test fun readsHeldFundsAndSkipsExitedOnes() {
        val r = assertIs<CasReading.Ready>(CasSummary.read(sample))
        assertEquals(LocalDate.of(2026, 11, 13), r.asOf)
        assertEquals(1, r.exitedRows)
        assertEquals(2, r.holdings.size)
        val a = r.holdings[0]
        assertEquals("Sample Alpha Equity Fund - Direct Plan -Growth Option", a.name)
        assertEquals("111111", a.folio)
        // 100.00 rupees = 10,000 paise
        assertEquals(10_000L, a.investedPaise)
        assertEquals(12_500L, a.valuePaise)
        assertEquals(BigDecimal("10.000"), a.units)
        assertEquals(BigDecimal("12.50"), a.nav)
        assertEquals(LocalDate.of(2026, 11, 12), a.navDate)
    }

    @Test fun aLossAndAFourPlaceNavAreRead() {
        val b = (CasSummary.read(sample) as CasReading.Ready).holdings[1]
        assertEquals("Sample Beta Bond Fund - Direct Plan", b.name)
        assertEquals(500_000L, b.investedPaise)
        assertEquals(405_010L, b.valuePaise)
        assertEquals(BigDecimal("200.500"), b.units)
        assertEquals(BigDecimal("20.2000"), b.nav)
    }

    @Test fun noInvestorNameOrPanReachesTheResult() {
        val r = CasSummary.read(sample) as CasReading.Ready
        val everything = r.holdings.joinToString { it.toString() }
        assertTrue("ABCDE1234F" !in everything && "Investor" !in everything && "PAN" !in everything)
    }

    @Test fun aWrongTotalIsRefused() {
        val bad = sample.replace("Total 4,175.10", "Total 4,175.11")
        assertIs<CasReading.Refused>(CasSummary.read(bad))
    }

    @Test fun textWithoutATableIsRefused() {
        assertIs<CasReading.Refused>(CasSummary.read("Just a letter about something else."))
        assertIs<CasReading.Refused>(CasSummary.read(""))
    }

    @Test fun planAddsUpdatesAndRemovesOnlyStatementFunds() {
        val r = CasSummary.read(sample) as CasReading.Ready
        fun f(id: String, name: String, folio: String, src: String) =
            MutualFund(id, name, "", "", "1", null, src, 0, null, null, "keep", null, folio, "")
        val existing = listOf(
            f("a", "Sample Alpha Equity Fund - Direct Plan -Growth Option", "111111", MutualFund.SOURCE_CAS), // updated, keeps id and note
            f("old", "Sample Delta Fund", "999", MutualFund.SOURCE_CAS),                                      // gone from the file: removed
            f("hand", "sample beta bond fund direct plan", "", MutualFund.SOURCE_MANUAL),                      // same name by hand: row left out
            f("console", "Some Console Fund", "", MutualFund.SOURCE_CONSOLE),                                  // other source: untouched
        )
        var n = 0
        val p = CasImport.plan(existing, r.holdings, 1_000L) { "new${n++}" }
        assertEquals(listOf("a"), p.update.map { it.id })
        assertEquals("keep", p.update.single().note)
        assertEquals("10", p.update.single().units)
        assertEquals(listOf("old"), p.remove.map { it.id })
        assertEquals(emptyList(), p.add)
        assertEquals(listOf("Sample Beta Bond Fund - Direct Plan"), p.leftOutAsTypedByHand.map { it.name })
        // The statement sets the price and cost, so a gain can be shown.
        assertEquals(10_000L, p.update.single().investedPaise)
        assertEquals("12.5", p.update.single().navText)
        assertEquals("2026-11-12", p.update.single().navDate)
    }

    @Test fun anNpsSchemeIsNeverTouchedByAStatementImport() {
        val r = CasSummary.read(sample) as CasReading.Ready
        val nps = MutualFund("n", "Some NPS Scheme E", "", "", "5", null, MutualFund.SOURCE_CAS, 0, null, null, "", null, "", "acc1")
        val p = CasImport.plan(listOf(nps), r.holdings, 1L) { "x" }
        assertEquals(emptyList(), p.remove)
        assertEquals(2, p.add.size)
    }
}
