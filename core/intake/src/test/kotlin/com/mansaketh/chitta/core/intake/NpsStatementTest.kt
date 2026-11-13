package com.mansaketh.chitta.core.intake

import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.model.NpsAccount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Invented figures in the order the real statement's text comes out (name wraps over two lines, the numbers follow). */
class NpsStatementTest {
    // 10.0000 x 50.0000 = 500.00; 20.5000 x 25.0000 = 512.50; 1.2500 x 8.0000 = 10.00. Sum 1,022.50
    private val sample = """
CENTRAL RECORDKEEPING AGENCY
NATIONAL PENSION SYSTEM
Transaction Statement - Tier I
Subscriber Details
PRAN 400000000000
Some Subscriber Name
Mob. : 910000000000
No of Contributions
Total Contribution
(₹)
Total Withdrawal
(₹)
Deductions due to
Charges (₹)
Current Valuation
(₹)
Notional Gain/Loss
(₹)
3 1000.00 0.00 12.50 1022.50 22.50
Investment Details as on 12-11-2026
Scheme Name Total Units Latest NAV Value at NAV XIRR
SAMPLE PENSION FUND SCHEME E - TIER I
POP
10.0000 50.0000 500.00 3.98%
SAMPLE PENSION FUND SCHEME C - TIER I
POP
20.5000 25.0000 512.50
SAMPLE PENSION FUND SCHEME G - TIER I
POP
1.2500 8.0000 10.00
Total 1022.50
Changes made during selected period
Date Tier Type Transaction Type
19/08/2025 Tier-1 Marital Status
Page 1 of 6
Transaction Details
03-May-2024 Contribution Voluntary
SAMPLE PENSION FUND SCHEME E - TIER I POP 22.8937 65.5529 1500.75
""".trimIndent()

    @Test fun readsSchemesPricesAndContributions() {
        val r = assertIs<NpsReading.Ready>(NpsStatement.read(sample))
        assertEquals("Tier I", r.tier)
        assertEquals(LocalDate.of(2026, 11, 12), r.priceDate)
        assertEquals(100_000L, r.contributionsPaise)
        assertEquals(0L, r.withdrawalsPaise)
        assertEquals(100_000L, r.netContributionsPaise)
        assertEquals(102_250L, r.statedValuePaise)
        assertEquals(3, r.schemes.size)
        val e = r.schemes[0]
        assertEquals("SAMPLE PENSION FUND SCHEME E - TIER I POP", e.name)
        assertEquals(BigDecimal("10.0000"), e.units); assertEquals(BigDecimal("50.0000"), e.nav); assertEquals(50_000L, e.valuePaise)
    }

    @Test fun aWithdrawalIsTakenOffTheContributions() {
        val withdrew = sample.replace("3 1000.00 0.00 12.50", "3 1000.00 100.00 12.50")
        val r = NpsStatement.read(withdrew) as NpsReading.Ready
        assertEquals(90_000L, r.netContributionsPaise)
    }

    @Test fun personalDetailsDoNotReachTheResult() {
        val r = NpsStatement.read(sample) as NpsReading.Ready
        val all = r.toString()
        assertFalse("400000000000" in all || "Subscriber Name" in all || "910000000000" in all)
    }

    @Test fun valuesThatDoNotAddUpAreRefused() {
        assertIs<NpsReading.Refused>(NpsStatement.read(sample.replace("Total 1022.50", "Total 1022.51")))
        assertIs<NpsReading.Refused>(NpsStatement.read(sample.replace("3 1000.00 0.00 12.50 1022.50", "3 1000.00 0.00 12.50 1022.60")))
    }

    @Test fun otherTextIsRefusedAndDetected() {
        assertIs<NpsReading.Refused>(NpsStatement.read("A letter about something else."))
        assertTrue(NpsStatement.looksLike(sample))
        assertFalse(NpsStatement.looksLike("Consolidated Account Summary Folio No. NAV"))
    }

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun firstImportCreatesTheAccountAndItsSchemes() {
        val r = NpsStatement.read(sample) as NpsReading.Ready
        var n = 0
        val p = NpsImport.plan(emptyList(), emptyList(), r, 1_000L, zone) { "id${n++}" }
        assertTrue(p.accountIsNew)
        assertEquals("NPS Tier I", p.account.name)
        assertEquals(100_000L, p.account.netContributionsPaise)
        assertEquals(3, p.add.size)
        assertTrue(p.add.all { it.groupId == p.account.id && it.source == NpsImport.SOURCE && it.investedPaise == null })
        assertEquals("2026-11-12", p.add[0].navDate)
        assertEquals("50", p.add[0].navText)
        assertEquals("10", p.add[0].units)
    }

    @Test fun nextImportUpdatesInPlaceKeepsIdsAndNotesAndDropsGoneSchemes() {
        val r = NpsStatement.read(sample) as NpsReading.Ready
        val acc = NpsAccount("acc", "NPS Tier I", 1L, 0L, "mine", null)
        fun s(id: String, name: String, src: String) = MutualFund(id, name, "", "", "1", null, src, 0, "1", "2026-01-01", "keep", null, "", "acc")
        val existing = listOf(
            s("e", "sample pension fund scheme e tier i pop", NpsImport.SOURCE),   // same scheme, other spelling: updated, id and note kept
            s("gone", "SAMPLE PENSION FUND SCHEME A - TIER I", NpsImport.SOURCE),   // not in file any more: removed
            s("hand", "SAMPLE PENSION FUND SCHEME C - TIER I POP", MutualFund.SOURCE_MANUAL), // typed by hand: row left out
            s("other", "Unrelated scheme", NpsImport.SOURCE).copy(groupId = "other-account"),
        )
        var n = 0
        val p = NpsImport.plan(listOf(acc), existing, r, 5_000L, zone) { "new${n++}" }
        assertFalse(p.accountIsNew)
        assertEquals("acc", p.account.id); assertEquals("mine", p.account.note)
        assertEquals(listOf("e"), p.update.map { it.id }); assertEquals("keep", p.update.single().note)
        assertEquals(listOf("gone"), p.remove.map { it.id })
        assertEquals(listOf("SAMPLE PENSION FUND SCHEME C - TIER I POP"), p.leftOutAsTypedByHand.map { it.name })
        assertEquals(1, p.add.size)   // scheme G
    }
}
