package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.AssetKind
import com.mansaketh.chitta.core.model.AssetValue
import com.mansaketh.chitta.core.model.ManualAsset
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gold, EPF and PPF typed in by hand (Phase 8i). Every figure is worked by hand; amounts are paise (₹1,00,000 = 10,000,000). */
class Phase8iTest {
    private val zone = ZoneId.of("UTC")
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun v(id: String, asset: String, y: Int, m: Int, d: Int, rupees: Long, typed: Long = 0) = AssetValue(id, asset, day(y, m, d), rupees * 100, typed)
    private fun asset(id: String, kind: AssetKind) = ManualAsset(id, kind, id, "", 0)

    private val chain = listOf(v("v1", "g1", 2026, 10, 1, 100_000), v("v2", "g1", 2026, 11, 10, 110_000))

    @Test fun `value in force is the newest on or before the day`() {
        assertNull(ManualAssets.valueAt(chain, day(2026, 9, 30)))                       // before the first value
        assertEquals("v1", ManualAssets.valueAt(chain, day(2026, 10, 1))!!.id)          // on the day itself
        assertEquals("v1", ManualAssets.valueAt(chain, day(2026, 11, 9))!!.id)
        assertEquals("v2", ManualAssets.valueAt(chain, day(2026, 11, 10))!!.id)
        assertEquals("v2", ManualAssets.valueAt(chain, day(2027, 1, 1))!!.id)
        assertNull(ManualAssets.valueAt(emptyList(), day(2026, 11, 14)))
    }

    @Test fun `two values for the same day, the one typed later wins`() {
        val same = listOf(v("a", "g1", 2026, 11, 1, 50_000, typed = 1), v("b", "g1", 2026, 11, 1, 52_000, typed = 2))
        assertEquals("b", ManualAssets.valueAt(same, day(2026, 11, 14))!!.id)
        assertEquals("b", ManualAssets.valueAt(same.reversed(), day(2026, 11, 14))!!.id)  // the order of the list does not matter
    }

    @Test fun `status gives the latest and the change from the one before`() {
        val s = ManualAssets.status(chain)!!
        assertEquals("v2", s.latest.id)
        assertEquals(Paise(1_000_000), s.change)                                          // 1,10,000 - 1,00,000 = 10,000 rupees
        val down = ManualAssets.status(listOf(chain[1], v("v3", "g1", 2026, 11, 12, 105_000)))!!
        assertEquals(Paise(-500_000), down.change)                                        // 1,05,000 - 1,10,000 = -5,000
        assertNull(ManualAssets.status(listOf(chain[0]))!!.change)                        // one value: nothing to compare
        assertNull(ManualAssets.status(emptyList()))
    }

    @Test fun `a typed value is zero or more`() {
        assertTrue(ManualAssets.validValue(0)); assertTrue(ManualAssets.validValue(1)); assertFalse(ManualAssets.validValue(-1))
    }

    @Test fun `stale after 60 days for gold`() {
        val typed = day(2026, 9, 15)                                                       // 15 Sep to 14 Nov = 15 + 31 + 14 = 60 days
        assertEquals(60, ManualAssets.daysOld(typed, day(2026, 11, 14), zone))
        assertFalse(ManualAssets.isStale(AssetKind.GOLD, typed, day(2026, 11, 14), zone))  // 60 is not over 60
        assertTrue(ManualAssets.isStale(AssetKind.GOLD, typed, day(2026, 11, 15), zone))   // 61 is
    }

    @Test fun `stale after 35 days for EPF and PPF`() {
        val typed = day(2026, 10, 10)                                                      // 10 Oct to 14 Nov = 21 + 14 = 35 days
        assertEquals(35, ManualAssets.daysOld(typed, day(2026, 11, 14), zone))
        assertFalse(ManualAssets.isStale(AssetKind.EPF, typed, day(2026, 11, 14), zone))
        assertTrue(ManualAssets.isStale(AssetKind.EPF, typed, day(2026, 11, 15), zone))
        assertTrue(ManualAssets.isStale(AssetKind.PPF, typed, day(2026, 11, 15), zone))
        assertFalse(ManualAssets.isStale(AssetKind.GOLD, typed, day(2026, 11, 15), zone))  // gold allows 60
    }

    @Test fun `a value dated ahead is not old`() {
        assertEquals(0, ManualAssets.daysOld(day(2026, 12, 1), day(2026, 11, 14), zone))
        assertFalse(ManualAssets.isStale(AssetKind.EPF, day(2026, 12, 1), day(2026, 11, 14), zone))
    }

    @Test fun `totals add the value in force of every asset of that kind`() {
        val assets = listOf(asset("g1", AssetKind.GOLD), asset("g2", AssetKind.GOLD), asset("e1", AssetKind.EPF), asset("g3", AssetKind.GOLD))
        val values = chain + listOf(v("v3", "g2", 2026, 8, 1, 25_000), v("v4", "e1", 2026, 11, 1, 300_000))
        // g3 has no value at all and is left out.
        val gold = ManualAssets.total(AssetKind.GOLD, assets, values, day(2026, 11, 14))
        assertEquals(Paise(13_500_000), gold.value)                                       // 1,10,000 + 25,000 = 1,35,000 rupees
        assertEquals(2, gold.count)
        assertEquals(day(2026, 8, 1), gold.oldestAsOfMs)                                  // the 1 Aug value is the oldest in force
        val epf = ManualAssets.total(AssetKind.EPF, assets, values, day(2026, 11, 14))
        assertEquals(Paise(30_000_000), epf.value)
        // An earlier day counts only what had been typed by then: on 15 Oct g1 was 1,00,000 and g2 25,000.
        assertEquals(Paise(12_500_000), ManualAssets.total(AssetKind.GOLD, assets, values, day(2026, 10, 15)).value)
        val none = ManualAssets.total(AssetKind.PPF, assets, values, day(2026, 11, 14))
        assertEquals(Paise.ZERO, none.value); assertEquals(0, none.count); assertNull(none.oldestAsOfMs)
    }

    private val extras = NetWorthExtras(
        assets = listOf(asset("g1", AssetKind.GOLD), asset("e1", AssetKind.EPF), asset("p1", AssetKind.PPF)),
        assetValues = chain + listOf(v("v4", "e1", 2026, 11, 1, 300_000), v("v5", "p1", 2026, 11, 2, 200_000)),
    )

    @Test fun `net worth counts gold, EPF and PPF as their own lines`() {
        val f = NetWorth.at(day(2026, 11, 14), emptyList(), emptyList(), emptyList(), extras, zone)
        assertEquals(Paise(11_000_000), f.gold); assertEquals(1, f.goldCount)
        assertEquals(Paise(30_000_000), f.epf)
        assertEquals(Paise(20_000_000), f.ppf)
        assertEquals(Paise(61_000_000), f.total)                                           // 1,10,000 + 3,00,000 + 2,00,000 = 6,10,000 rupees
    }

    @Test fun `each switch leaves its own line out`() {
        val f = NetWorth.at(day(2026, 11, 14), emptyList(), emptyList(), emptyList(), extras.copy(includeEpf = false), zone)
        assertEquals(Paise.ZERO, f.epf); assertEquals(0, f.epfCount)
        assertEquals(Paise(31_000_000), f.total)                                           // gold 1,10,000 + PPF 2,00,000
        val off = NetWorth.at(day(2026, 11, 14), emptyList(), emptyList(), emptyList(), extras.copy(includeGold = false, includeEpf = false, includePpf = false), zone)
        assertEquals(Paise.ZERO, off.total)
    }

    @Test fun `the history chart counts assets in earlier months`() {
        val series = NetWorth.series(emptyList(), emptyList(), emptyList(), day(2026, 11, 14), zone, 3, extras)
        // Sep end: nothing typed yet. Oct end: gold 1,00,000. Today: gold 1,10,000 + EPF 3,00,000 + PPF 2,00,000.
        assertEquals(listOf(0L, 10_000_000L, 61_000_000L), series.map { it.figures.total.value })
    }
}
