package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A stretch of time, start included and end excluded, in epoch milliseconds. */
data class Period(val startMs: Long, val endMs: Long) {
    init { require(endMs > startMs) { "A period must end after it starts." } }
    operator fun contains(ms: Long) = ms >= startMs && ms < endMs
}

/**
 * Calendar maths for budgets and repeating bills. Pure JDK time, so month ends, leap days and clock changes are tested
 * on the JVM. Months and years are always counted from the first date, never from the previous result, so a bill on the
 * 31st does not drift to the 28th after February.
 */
object Periods {
    fun date(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun startOfDay(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Whole calendar days inside [p], counting both the first and the last day. */
    fun daysIn(p: Period, zone: ZoneId): Int =
        (ChronoUnit.DAYS.between(date(p.startMs, zone), date(p.endMs - 1, zone)) + 1).toInt()

    /** Day of the period [nowMs] falls on, 1-based and counting today: day 12 of 30 means 18 days left. 0 before it starts, all days after it ends. */
    fun dayOfPeriod(p: Period, nowMs: Long, zone: ZoneId): Int {
        val total = daysIn(p, zone)
        if (nowMs < p.startMs) return 0
        if (nowMs >= p.endMs) return total
        return (ChronoUnit.DAYS.between(date(p.startMs, zone), date(nowMs, zone)) + 1).toInt().coerceIn(1, total)
    }

    /**
     * [steps] repeats after [fromMs]. [anchorDay] is the day of the month a monthly or yearly series belongs on; a month that is
     * too short uses its last day. The time of day is kept. Not for [Repeat.CUSTOM].
     */
    fun advance(fromMs: Long, repeat: Repeat, length: Int, steps: Int, zone: ZoneId, anchorDay: Int? = null): Long {
        require(repeat != Repeat.CUSTOM) { "A custom period does not repeat." }
        val n = length.coerceAtLeast(1).toLong() * steps
        val from = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDateTime()
        val moved: LocalDateTime = when (repeat) {
            Repeat.DAILY -> from.plusDays(n)
            Repeat.WEEKLY -> from.plusWeeks(n)
            Repeat.MONTHLY, Repeat.YEARLY -> {
                val ym = YearMonth.from(from).let { if (repeat == Repeat.MONTHLY) it.plusMonths(n) else it.plusYears(n) }
                val day = minOf(anchorDay ?: from.dayOfMonth, ym.lengthOfMonth())
                LocalDateTime.of(ym.year, ym.month, day, from.hour, from.minute, from.second, from.nano)
            }
            Repeat.CUSTOM -> error("unreachable")
        }
        return moved.atZone(zone).toInstant().toEpochMilli()
    }

    /** Index (0 = the first period) of the repeating period that [nowMs] falls in. Before the start, 0. */
    fun indexAt(startMs: Long, repeat: Repeat, length: Int, nowMs: Long, zone: ZoneId): Int {
        val len = length.coerceAtLeast(1)
        val s = date(startMs, zone)
        val n = date(nowMs, zone)
        var k = when (repeat) {
            Repeat.DAILY -> ChronoUnit.DAYS.between(s, n) / len
            Repeat.WEEKLY -> ChronoUnit.DAYS.between(s, n) / (7L * len)
            Repeat.MONTHLY -> ChronoUnit.MONTHS.between(s, n) / len
            Repeat.YEARLY -> ChronoUnit.YEARS.between(s, n) / len
            Repeat.CUSTOM -> 0L
        }.toInt().coerceAtLeast(0)
        while (k > 0 && advance(startMs, repeat, len, k, zone) > nowMs) k--
        while (advance(startMs, repeat, len, k + 1, zone) <= nowMs) k++
        return k
    }

    fun periodAtIndex(startMs: Long, repeat: Repeat, length: Int, k: Int, zone: ZoneId) =
        Period(advance(startMs, repeat, length, k, zone), advance(startMs, repeat, length, k + 1, zone))

    /**
     * The period of a budget that holds [nowMs]. A one-time budget (no repeat, or a custom period) is just [startMs] to [endMs].
     */
    fun budgetPeriod(startMs: Long, endMs: Long, repeat: Repeat?, length: Int, nowMs: Long, zone: ZoneId): Period {
        if (repeat == null || repeat == Repeat.CUSTOM) return Period(startMs, maxOf(endMs, startMs + 1))
        return periodAtIndex(startMs, repeat, length, indexAt(startMs, repeat, length, nowMs, zone), zone)
    }

    /** Earlier periods of a repeating budget, newest first, not counting the current one. Empty for a one-time budget. */
    fun earlierPeriods(startMs: Long, repeat: Repeat?, length: Int, nowMs: Long, zone: ZoneId, count: Int): List<Period> {
        if (repeat == null || repeat == Repeat.CUSTOM) return emptyList()
        val k = indexAt(startMs, repeat, length, nowMs, zone)
        return (k - 1 downTo maxOf(0, k - count)).map { periodAtIndex(startMs, repeat, length, it, zone) }
    }
}

/** value × numerator ÷ denominator in exact arithmetic, rounded half away from zero. */
internal fun mulDivRound(value: Long, numerator: Long, denominator: Long): Long {
    require(denominator > 0) { "denominator must be positive" }
    val product = Math.multiplyExact(value, numerator)
    val half = denominator / 2
    return if (product >= 0) (product + half) / denominator else -((-product + half) / denominator)
}
