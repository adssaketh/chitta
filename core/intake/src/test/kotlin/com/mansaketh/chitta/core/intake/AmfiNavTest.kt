package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.MutualFund
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sample file is a trimmed copy of the live AMFI file read on 14 Nov 2026 (public data), plus two invented bad lines (a price of "N.A." and an
 * unreadable date). Keeping a copy of the real layout is the master prompt's rule for this parser.
 */
class AmfiNavTest {
    private val sample = resource("amfi-navall-sample.txt")
    private fun ready(text: String = sample) = assertIs<AmfiReading.Ready>(AmfiNav.read(text, minSchemes = 1))

    private fun fund(name: String, isin: String = "", code: String = "", navDate: String? = null, id: String = name) =
        MutualFund(id, name, isin, code, "10", 100_000, MutualFund.SOURCE_CAS, 0, null, navDate, "", null)

    @Test fun readsTheRealLayout() {
        val r = ready()
        assertEquals(18, r.schemes.size)
        assertEquals(2, r.skipped) // "N.A." price and the bad date
        assertEquals(LocalDate.of(2026, 11, 13), r.newestDate)
        val flexi = r.schemes.first { it.code == "122639" }
        assertEquals("Parag Parikh Flexi Cap Fund", flexi.name)
        assertEquals("Direct Plan", flexi.plan); assertEquals("Growth", flexi.option)
        assertEquals(BigDecimal("94.8149"), flexi.nav) // four decimals kept exactly
        assertEquals(LocalDate.of(2026, 11, 12), flexi.date)
        assertEquals(listOf("INF879O01027"), flexi.isins)
        assertEquals(listOf("INF846K01WS2", "INF846K01WQ6"), r.schemes.first { it.code == "135763" }.isins)
    }

    @Test fun categoryAndFundHouseLinesAreIgnored() {
        assertTrue(ready().schemes.none { it.name.contains("Open Ended") || it.name == "Axis Mutual Fund" })
    }

    @Test fun aHeaderWithoutAPriceColumnIsRefused() {
        val r = AmfiNav.read("Scheme Code;Scheme Name;Date\r\n1;X;13-Nov-2026\r\n", minSchemes = 1)
        assertIs<AmfiReading.Refused>(r)
    }

    @Test fun aFileWithTooFewSchemesOrNoHeaderIsRefused() {
        assertIs<AmfiReading.Refused>(AmfiNav.read(sample)) // 16 schemes is under the real minimum
        assertIs<AmfiReading.Refused>(AmfiNav.read("<html>Document Moved</html>"))
    }

    @Test fun anOlderLayoutWithoutPlanAndOptionColumnsStillReads() {
        val old = "Scheme Code;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date\r\n" +
            "100001;INF000X00001;-;Sample Fund - Direct Plan - Growth;12.3400;13-Nov-2026\r\n"
        val s = ready(old).schemes.single()
        assertEquals("Sample Fund - Direct Plan - Growth", s.name); assertEquals("", s.plan)
    }

    // ---- matching

    @Test fun aStatementNameWithPlanAndOptionMatchesOneScheme() {
        val index = FundMatchIndex(ready().schemes)
        val m = assertIs<FundMatchResult.Sure>(index.match(fund("Parag Parikh Flexi Cap Fund - Direct Plan - Growth Option")))
        assertEquals("122639", m.scheme.code)
        val reg = assertIs<FundMatchResult.Sure>(index.match(fund("Parag Parikh Flexi Cap Fund-Regular Growth")))
        assertEquals("122640", reg.scheme.code)
    }

    @Test fun andAndAmpersandAreTheSameWord() {
        val m = assertIs<FundMatchResult.Sure>(FundMatchIndex(ready().schemes).match(fund("Abakkus Large and Mid Cap Fund - Direct Plan - Growth")))
        assertEquals("154477", m.scheme.code)
    }

    @Test fun twoSchemesWithTheSameNameAreAPickNeverAGuess() {
        // The file lists two Axis Children's Fund Direct Growth lines (codes 135762 and 135764).
        val m = assertIs<FundMatchResult.Pick>(FundMatchIndex(ready().schemes).match(fund("Axis Children's Fund - Direct Plan - Growth Option")))
        assertEquals(setOf("135762", "135764"), m.candidates.map { it.code }.toSet())
    }

    @Test fun aNameWithNoPlanOrOptionIsAPick() {
        val m = assertIs<FundMatchResult.Pick>(FundMatchIndex(ready().schemes).match(fund("Parag Parikh Flexi Cap Fund")))
        assertTrue(m.candidates.size in 2..5)
    }

    @Test fun aNearNameIsOfferedAndAnUnrelatedOneIsNot() {
        val index = FundMatchIndex(ready().schemes)
        val near = assertIs<FundMatchResult.Pick>(index.match(fund("Parag Parikh Flexi Cap Fund Direct Growth Plan Extra")))
        assertTrue(near.candidates.any { it.code == "122639" })
        assertEquals(FundMatchResult.None, index.match(fund("Totally Different Name Fund - Direct Plan - Growth")))
    }

    @Test fun aSavedSchemeCodeOrIsinWinsOverTheName() {
        val index = FundMatchIndex(ready().schemes)
        assertEquals("122640", assertIs<FundMatchResult.Sure>(index.match(fund("Anything at all", code = "122640"))).scheme.code)
        assertEquals("122639", assertIs<FundMatchResult.Sure>(index.match(fund("Anything at all", isin = "inf879o01027"))).scheme.code)
    }

    @Test fun anNpsSchemeIsNeverMatched() {
        val nps = fund("Parag Parikh Flexi Cap Fund - Direct Plan - Growth").copy(groupId = "nps1")
        assertEquals(FundMatchResult.None, FundMatchIndex(ready().schemes).match(nps))
    }

    // ---- the plan

    @Test fun aPriceIsAppliedOnlyWhenItIsNotOlderThanTheOneTheFundHas() {
        val reading = ready()
        val fresh = fund("Parag Parikh Flexi Cap Fund - Direct Plan - Growth", navDate = "2026-11-10")
        val newer = fund("Parag Parikh Flexi Cap Fund - Regular Plan - Growth", navDate = "2026-11-13", id = "n")
        val plan = FundPrices.plan(listOf(fresh, newer), reading)
        assertEquals(2, plan.updates.size)
        assertTrue(plan.updates.first { it.fund.id == fresh.id }.applyPrice)
        assertEquals(false, plan.updates.first { it.fund.id == "n" }.applyPrice) // file dated 12 Nov, fund already at 13 Nov
        assertEquals(LocalDate.of(2026, 11, 13), plan.fileDate)
    }

    @Test fun planSortsFundsIntoMatchedPickAndNone() {
        val funds = listOf(
            fund("Parag Parikh Flexi Cap Fund - Direct Plan - Growth"), fund("Axis Children's Fund - Direct Plan - Growth"),
            fund("Totally Different Name Fund - Direct Plan - Growth"), fund("Some scheme").copy(groupId = "nps1", id = "nps"),
        )
        val p = FundPrices.plan(funds, ready())
        assertEquals(1, p.updates.size); assertEquals(1, p.needPick.size); assertEquals(1, p.noMatch.size)
        assertNull(p.updates.single().fund.groupId.takeIf { it.isNotEmpty() })
    }
}
