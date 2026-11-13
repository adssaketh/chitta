package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.MutualFund
import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** What the Mutual funds screen adds up. [gainCovers] of [priced] funds are in [gain]; the rest have no average price. */
data class FundTotals(
    val count: Int,
    val priced: Int,
    val unpriced: Int,
    val value: Paise,
    val gain: Paise?,
    val gainCovers: Int,
    val priceDate: String?,
)

/**
 * Mutual fund figures (master section 8). value = units x latest price; gain = value - invested.
 * A fund with no price has no value; a fund with no average price has no gain. Neither is ever guessed.
 */
object MutualFunds {
    /** Units go stale after this many days without an import (master section 8). */
    const val STALE_AFTER_DAYS = 35L

    /** Units as a decimal, or null when the text is not a positive number. */
    fun units(f: MutualFund): BigDecimal? = f.units.trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    /** The price per unit, or null when none has been fetched or it is not a positive number. */
    fun nav(f: MutualFund): BigDecimal? = f.navText?.trim()?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    /** Units x price, rounded half-even to the paisa. Null with no price. */
    fun value(f: MutualFund): Paise? {
        val u = units(f) ?: return null
        val n = nav(f) ?: return null
        return Paise(u.multiply(n).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_EVEN).longValueExact())
    }

    /** Value minus what was put in. Null when there is no price or no average price. */
    fun gain(f: MutualFund): Paise? {
        val v = value(f) ?: return null
        val inv = f.investedPaise ?: return null
        return v - Paise(inv)
    }

    /** What was put in, from units and an average price per unit, rounded half-even to the paisa. */
    fun investedFrom(units: BigDecimal, averagePrice: BigDecimal): Long =
        units.multiply(averagePrice).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_EVEN).longValueExact()

    /** Whole days since the units were last imported or typed. */
    fun daysSinceUnits(f: MutualFund, nowMs: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(Periods.date(f.unitsAsOfMs, zone), Periods.date(nowMs, zone))

    fun isStale(f: MutualFund, nowMs: Long, zone: ZoneId): Boolean = daysSinceUnits(f, nowMs, zone) > STALE_AFTER_DAYS

    /** A valid ISIN has 12 characters: 2 letters, 9 letters or digits, 1 check digit. Only the shape is checked here. */
    fun isIsinShape(text: String): Boolean = Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]").matches(text.trim().uppercase())

    /**
     * Totals for the header. The price date is the oldest price date among priced funds, so the line never claims fresher prices than the
     * stalest one. The gain covers only funds that have both a price and an average price, and says how many that is.
     */
    fun totals(funds: List<MutualFund>): FundTotals {
        val priced = funds.filter { value(it) != null }
        val withGain = priced.filter { gain(it) != null }
        return FundTotals(
            count = funds.size,
            priced = priced.size,
            unpriced = funds.size - priced.size,
            value = priced.fold(Paise.ZERO) { a, f -> a + value(f)!! },
            gain = if (withGain.isEmpty()) null else withGain.fold(Paise.ZERO) { a, f -> a + gain(f)!! },
            gainCovers = withGain.size,
            priceDate = priced.mapNotNull { it.navDate }.minOrNull(),
        )
    }
}

/** What the NPS tab adds up for one account. [gain] is for the whole account (the statement gives no cost per scheme). */
data class NpsTotals(
    val schemes: Int,
    val priced: Int,
    val value: Paise,
    val netContributions: Paise?,
    val gain: Paise?,
    val priceDate: String?,
)

/**
 * NPS figures. Each scheme is units x its statement price, like a fund. The gain is value minus net contributions (paid in minus withdrawn,
 * as the statement states them), and only when every scheme has a price and the contributions are known: a partial value would make a
 * false gain. NPS is never added into the mutual fund totals.
 */
object Nps {
    fun totals(account: com.mansaketh.chitta.core.model.NpsAccount, schemes: List<MutualFund>): NpsTotals {
        val priced = schemes.filter { MutualFunds.value(it) != null }
        val value = priced.fold(Paise.ZERO) { a, f -> a + MutualFunds.value(f)!! }
        val net = account.netContributionsPaise?.let { Paise(it) }
        val allPriced = schemes.isNotEmpty() && priced.size == schemes.size
        return NpsTotals(
            schemes = schemes.size, priced = priced.size, value = value, netContributions = net,
            gain = if (allPriced && net != null) value - net else null,
            priceDate = priced.mapNotNull { it.navDate }.minOrNull(),
        )
    }
}
