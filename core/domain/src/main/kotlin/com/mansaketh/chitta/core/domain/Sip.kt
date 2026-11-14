package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.MutualFund
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Estimated units from a monthly SIP (master section 8: "add estimated units, labelled estimated, until the next import replaces them").
 *
 * The only price source is the daily AMFI file, which has the latest price and no history. So an instalment is credited the first time a
 * fetched price is dated on or after the instalment day, at that price. That matches how a real SIP works (the next business day's price
 * when the day is a holiday), and is off by the price move between the instalment day and the day the app next fetched. The result is
 * always shown as "estimated". An import or an edit of the units resets it ([reset]), because the statement already has the real units.
 */
object Sip {
    /** SIP days run 1 to 31; a day the month does not have means that month's last day (a SIP on the 31st is taken on 30 Apr). */
    fun isValidDay(day: Int): Boolean = day in 1..31

    /** The instalment dates after [afterExclusive] up to and including [upToInclusive], oldest first. */
    fun dueDates(day: Int, afterExclusive: LocalDate, upToInclusive: LocalDate): List<LocalDate> {
        if (!isValidDay(day) || !upToInclusive.isAfter(afterExclusive)) return emptyList()
        val out = ArrayList<LocalDate>()
        var ym = YearMonth.from(afterExclusive)
        val last = YearMonth.from(upToInclusive)
        while (!ym.isAfter(last)) {
            val d = ym.atDay(minOf(day, ym.lengthOfMonth()))
            if (d.isAfter(afterExclusive) && !d.isAfter(upToInclusive)) out += d
            ym = ym.plusMonths(1)
        }
        return out
    }

    /** Units one instalment buys at [nav]: amount in rupees divided by the price, 3 places, half-even. Null for a missing or non-positive price. */
    fun unitsFor(amountPaise: Long, nav: BigDecimal): BigDecimal? {
        if (nav.signum() <= 0 || amountPaise <= 0) return null
        return BigDecimal(amountPaise).movePointLeft(2).divide(nav, 3, RoundingMode.HALF_EVEN)
    }

    /**
     * Credits every instalment dated after [MutualFund.sipThrough] and up to [navDate] at [nav], and moves sipThrough to [navDate].
     * A fund with no SIP, a price date that is not newer, or a SIP that has never been "through" a date (blank) is returned with only
     * sipThrough set, so nothing is ever credited back in time. The price date never moves sipThrough backwards.
     */
    fun credit(f: MutualFund, nav: BigDecimal, navDate: LocalDate): MutualFund {
        val amount = f.sipAmountPaise; val day = f.sipDay
        if (amount == null || day == null || !isValidDay(day)) return f
        val through = runCatching { LocalDate.parse(f.sipThrough) }.getOrNull()
        if (through == null) return f.copy(sipThrough = navDate.toString())
        if (!navDate.isAfter(through)) return f
        val dates = dueDates(day, through, navDate)
        val perInstalment = unitsFor(amount, nav) ?: return f
        if (dates.isEmpty()) return f.copy(sipThrough = navDate.toString())
        val units = MutualFunds.sipUnits(f).add(perInstalment.multiply(BigDecimal(dates.size)))
        return f.copy(
            sipUnits = units.toPlainString(), sipInvestedPaise = f.sipInvestedPaise + amount * dates.size,
            sipCount = f.sipCount + dates.size, sipThrough = navDate.toString(),
        )
    }

    /** Drops the estimate: the units now come from a statement or were typed. Instalments up to [through] are in those units. */
    fun reset(f: MutualFund, through: LocalDate): MutualFund = f.copy(sipUnits = "0", sipInvestedPaise = 0, sipCount = 0, sipThrough = through.toString())
}
