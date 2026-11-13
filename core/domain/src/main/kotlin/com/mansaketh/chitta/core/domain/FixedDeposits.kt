package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.FixedDeposit
import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit
import java.time.ZoneId

/**
 * Fixed deposits: what they are worth today and at maturity. Behaviour of Paddhu's `fixedDeposit.dart`, rebuilt on paise.
 *
 * Interest runs on actual days over 365. [FixedDeposit.compoundingPerYear] is 4 for quarterly (most Indian banks), 12, 2, 1, or 0 for
 * simple interest paid at maturity. Compound interest needs a fractional power, which exact decimals cannot do, so the growth factor is
 * worked out as a Double and then applied to the principal in exact decimals and rounded to the paisa (half even). Error is far below a
 * paisa for any realistic deposit. When the bank's own maturity amount is entered it wins over every calculated figure (master section 12:
 * never show a made-up number when a stated one exists).
 */
object FixedDeposits {
    val COMPOUNDING_NAMES: Map<Int, String> = linkedMapOf(
        4 to "Quarterly", 12 to "Monthly", 2 to "Half-yearly", 1 to "Yearly", 0 to "Simple interest, paid at maturity",
    )

    private fun rate(fd: FixedDeposit): BigDecimal = fd.ratePct.trim().toBigDecimalOrNull()?.coerceAtLeast(BigDecimal.ZERO) ?: BigDecimal.ZERO

    fun days(fromMs: Long, toMs: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(Periods.date(fromMs, zone), Periods.date(toMs, zone))

    /** Interest on the principal after [days] days, in paise. */
    fun interestAfter(fd: FixedDeposit, days: Long): Paise {
        if (days <= 0 || fd.principal <= 0) return Paise.ZERO
        val r = rate(fd)
        if (r.signum() == 0) return Paise.ZERO
        if (fd.compoundingPerYear <= 0) {
            val v = BigDecimal(fd.principal).multiply(r).multiply(BigDecimal(days)).divide(BigDecimal(36_500), 0, RoundingMode.HALF_EVEN)
            return Paise(v.longValueExact())
        }
        val n = fd.compoundingPerYear.toDouble()
        val factor = Math.pow(1.0 + r.toDouble() / 100.0 / n, n * days / 365.0)
        val interest = BigDecimal(fd.principal).multiply(BigDecimal(factor).subtract(BigDecimal.ONE)).setScale(0, RoundingMode.HALF_EVEN)
        return Paise(interest.longValueExact())
    }

    private fun bankFigure(fd: FixedDeposit): Long? = fd.maturityAmount?.takeIf { it > 0 }

    /** True when [maturityValue] is the bank's figure, not ours: the screen says so. */
    fun isStatedByBank(fd: FixedDeposit): Boolean = bankFigure(fd) != null

    fun maturityValue(fd: FixedDeposit, zone: ZoneId): Paise =
        bankFigure(fd)?.let { Paise(it) } ?: Paise(fd.principal) + interestAfter(fd, days(fd.startMs, fd.maturityMs, zone))

    fun interestAtMaturity(fd: FixedDeposit, zone: ZoneId): Paise = maturityValue(fd, zone) - Paise(fd.principal)

    /** Interest earned up to [todayMs]; all of it once matured. With the bank's figure the interest is spread evenly over the days. */
    fun accruedInterest(fd: FixedDeposit, todayMs: Long, zone: ZoneId): Paise {
        val total = days(fd.startMs, fd.maturityMs, zone)
        val gone = days(fd.startMs, todayMs, zone)
        if (gone <= 0 || total <= 0) return Paise.ZERO
        if (gone >= total) return interestAtMaturity(fd, zone)
        if (bankFigure(fd) != null) {
            val v = BigDecimal(interestAtMaturity(fd, zone).value).multiply(BigDecimal(gone)).divide(BigDecimal(total), 0, RoundingMode.HALF_EVEN)
            return Paise(v.longValueExact())
        }
        return interestAfter(fd, gone)
    }

    fun currentValue(fd: FixedDeposit, todayMs: Long, zone: ZoneId): Paise = Paise(fd.principal) + accruedInterest(fd, todayMs, zone)

    /** Days until maturity; negative once past. */
    fun daysToMaturity(fd: FixedDeposit, todayMs: Long, zone: ZoneId): Long = days(todayMs, fd.maturityMs, zone)

    fun hasMatured(fd: FixedDeposit, todayMs: Long, zone: ZoneId): Boolean = daysToMaturity(fd, todayMs, zone) <= 0

    /** The deposits that count in Net worth: open ones. A matured deposit still counts until you mark it closed (the money is not in an account yet). */
    fun openOnes(all: List<FixedDeposit>): List<FixedDeposit> = all.filter { !it.closed }

    /** Total current value of [deposits] (open ones only) and how many were added. */
    fun totalValue(deposits: List<FixedDeposit>, todayMs: Long, zone: ZoneId): Paise =
        openOnes(deposits).fold(Paise.ZERO) { acc, d -> acc + currentValue(d, todayMs, zone) }

    /** The same deposit: same message, same FD number at the same bank, or same amount and dates at the same bank. */
    fun same(a: FixedDeposit, b: FixedDeposit, zone: ZoneId): Boolean {
        if (a.smsId.isNotEmpty() && a.smsId == b.smsId) return true
        if (!a.bank.equals(b.bank, ignoreCase = true)) return false
        if (a.fdNumber.isNotEmpty() && a.fdNumber == b.fdNumber) return true
        return a.principal == b.principal && Periods.date(a.startMs, zone) == Periods.date(b.startMs, zone) &&
            Periods.date(a.maturityMs, zone) == Periods.date(b.maturityMs, zone)
    }

    /** Deposits maturing within [withinDays] days (or already matured and still open), soonest first. Used for the maturity reminder. */
    fun maturingSoon(all: List<FixedDeposit>, todayMs: Long, zone: ZoneId, withinDays: Int): List<FixedDeposit> =
        openOnes(all).filter { daysToMaturity(it, todayMs, zone) in 0..withinDays.toLong() }.sortedBy { it.maturityMs }
}
