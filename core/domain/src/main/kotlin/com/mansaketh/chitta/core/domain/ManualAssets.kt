package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.AssetKind
import com.mansaketh.chitta.core.model.AssetValue
import com.mansaketh.chitta.core.model.ManualAsset
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId

/** Gold, EPF and PPF counted in Net worth: the sum of the latest values, how many assets that is, and the oldest value date among them. */
data class AssetTotal(val value: Paise, val count: Int, val oldestAsOfMs: Long?)

/** What the latest typed value of one asset is, and how it moved from the one before. [change] is null when there is only one value. */
data class AssetStatus(val latest: AssetValue, val change: Paise?)

/**
 * Rules for assets typed in by hand (Phase 8i, DECISIONS D158 to D164). Plain Kotlin: values are integer paise and nothing is guessed.
 */
object ManualAssets {
    /** The value in force on [asOfMs]: the newest one dated on or before it; of two for the same day, the one typed later. Null before the first value. */
    fun valueAt(values: List<AssetValue>, asOfMs: Long): AssetValue? =
        values.filter { it.asOfMs <= asOfMs }.maxWithOrNull(compareBy<AssetValue>({ it.asOfMs }, { it.recordedAtMs }, { it.id }))

    /** The latest value and the change from the value before it (by the same ordering), or null with no values. */
    fun status(values: List<AssetValue>): AssetStatus? {
        val ordered = values.sortedWith(compareBy<AssetValue>({ it.asOfMs }, { it.recordedAtMs }, { it.id }))
        val last = ordered.lastOrNull() ?: return null
        val before = ordered.getOrNull(ordered.size - 2)
        return AssetStatus(last, before?.let { Paise(last.valuePaise - it.valuePaise) })
    }

    /** A typed value must be zero or more: zero means sold or emptied. Negative is refused. */
    fun validValue(paise: Long): Boolean = paise >= 0

    /** Whole calendar days from the value's day to today, in [zone]. Never negative: a value dated ahead is not "old". */
    fun daysOld(asOfMs: Long, nowMs: Long, zone: ZoneId): Long {
        val from = Periods.date(asOfMs, zone)
        val to = Periods.date(nowMs, zone)
        return maxOf(0L, java.time.temporal.ChronoUnit.DAYS.between(from, to))
    }

    /** STALE once the latest value is older than the kind's limit: 35 days for EPF and PPF, 60 for gold (DECISIONS D161). */
    fun isStale(kind: AssetKind, asOfMs: Long, nowMs: Long, zone: ZoneId): Boolean = daysOld(asOfMs, nowMs, zone) > kind.staleAfterDays

    /**
     * The total of one kind as of [asOfMs]: every asset of that kind counts at the value in force that day. An asset with no value yet that day
     * is left out (so a past month does not count what had not been typed).
     */
    fun total(kind: AssetKind, assets: List<ManualAsset>, values: List<AssetValue>, asOfMs: Long): AssetTotal {
        val byAsset = values.groupBy { it.assetId }
        var sum = Paise.ZERO
        var count = 0
        var oldest: Long? = null
        for (a in assets) {
            if (a.kind != kind) continue
            val v = valueAt(byAsset[a.id].orEmpty(), asOfMs) ?: continue
            sum += Paise(v.valuePaise)
            count++
            oldest = minOf(oldest ?: v.asOfMs, v.asOfMs)
        }
        return AssetTotal(sum, count, oldest)
    }
}
