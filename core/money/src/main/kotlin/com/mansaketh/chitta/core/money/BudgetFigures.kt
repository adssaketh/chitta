package com.mansaketh.chitta.core.money

/** Result of the Range rule (master section 12). Never negative. */
sealed interface RangeResult {
    /** Fewer than 7 days of the period have gone: show "Not enough data yet". */
    data object NotEnoughData : RangeResult

    /** Nothing spent this period: show "No spending yet". */
    data object NoSpending : RangeResult

    /** Whole days the safe-to-spend amount lasts at the average daily spend. */
    data class Days(val days: Int) : RangeResult
}

enum class PaceStatus { OnPace, OverPace, OverLimit }

/**
 * Budget numbers from master prompt section 12. Every function here is pure and is tested
 * with the worked examples from that section (see BudgetFiguresTest).
 */
object BudgetFigures {
    /** The forecast and Range need this many days of the period to have gone. */
    const val MIN_DAYS_FOR_FORECAST = 7

    /** Safe to spend = budget left − unpaid bills due this period. May be negative (over). */
    fun safeToSpend(budget: Paise, spent: Paise, billsDue: Paise): Paise = budget - spent - billsDue

    /** Safe to spend ÷ days left. On the last day (or later) the full amount is shown. */
    fun perDay(safe: Paise, daysLeft: Int): Paise =
        if (daysLeft <= 1) safe else Paise(mulDivRound(safe.value, 1, daysLeft.toLong()))

    /**
     * Range = safe to spend ÷ average daily spending this period (this-period window, decision D-range).
     * Whole days, rounded down: ₹27,502 ÷ ₹2,210 = 12.4, shown as 12 days.
     */
    fun range(safe: Paise, spent: Paise, daysElapsed: Int): RangeResult {
        if (spent.value <= 0) return RangeResult.NoSpending
        if (daysElapsed < MIN_DAYS_FOR_FORECAST) return RangeResult.NotEnoughData
        if (safe.value <= 0) return RangeResult.Days(0)
        // days = safe / (spent / elapsed) = safe * elapsed / spent, floored, in exact arithmetic
        val days = Math.multiplyExact(safe.value, daysElapsed.toLong()) / spent.value
        return RangeResult.Days(days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    /** Ideal spend so far = limit × days elapsed ÷ days in period. Null on day 1 (no pace shown). */
    fun idealSpend(limit: Paise, daysElapsed: Int, daysInPeriod: Int): Paise? =
        if (daysElapsed <= 1) null else Paise(mulDivRound(limit.value, daysElapsed.toLong(), daysInPeriod.toLong()))

    /** Over pace and over limit are different states (master section 12). */
    fun paceStatus(spent: Paise, ideal: Paise?, limit: Paise): PaceStatus = when {
        spent > limit -> PaceStatus.OverLimit
        ideal != null && spent > ideal -> PaceStatus.OverPace
        else -> PaceStatus.OnPace
    }

    /** Forecast = spent ÷ days elapsed × days in period. Shown from day 7 only, otherwise null. */
    fun forecast(spent: Paise, daysElapsed: Int, daysInPeriod: Int): Paise? =
        if (daysElapsed < MIN_DAYS_FOR_FORECAST) null
        else Paise(mulDivRound(spent.value, daysInPeriod.toLong(), daysElapsed.toLong()))

    /**
     * Day of the period (1-based) on which the forecast line first reaches the limit, or null when
     * there is no forecast yet, nothing is spent, the limit is already passed, or the forecast
     * stays under the limit for the whole period.
     */
    fun limitDay(spent: Paise, limit: Paise, daysElapsed: Int, daysInPeriod: Int): Int? {
        if (daysElapsed < MIN_DAYS_FOR_FORECAST || spent.value <= 0 || limit.value <= 0) return null
        if (spent >= limit) return null
        // smallest d with spent/elapsed * d >= limit  <=>  d >= limit * elapsed / spent
        val num = Math.multiplyExact(limit.value, daysElapsed.toLong())
        val d = (num + spent.value - 1) / spent.value
        return if (d <= daysInPeriod) d.toInt() else null
    }
}

/** Which number leads on Home. A setting can force the cash check; [Hero.CashCheck] is also used when no budget is set (master section 12). */
enum class Hero { SafeToSpend, CashCheck }

/** The cash check (master section 12): what the "for spending" accounts hold, less every bill due this period and this month's goal savings. */
data class CashCheck(val balances: Paise, val bills: Paise, val goalSavings: Paise) {
    val value: Paise get() = balances - bills - goalSavings
}

object HeroFigures {
    /**
     * The figure Home leads with. With a budget the hero is Safe to spend, unless [preferCash] is on or there is no budget (then the cash check leads).
     * If the cash check is lower than Safe to spend, it wins and [warn] is true: the budget says there is room but the bank balances do not.
     */
    data class Choice(val hero: Hero, val value: Paise, val warn: Boolean)

    fun choose(safe: Paise?, cash: CashCheck?, preferCash: Boolean): Choice? {
        if (safe == null && cash == null) return null
        if (safe == null) return Choice(Hero.CashCheck, cash!!.value, warn = false)
        if (cash == null) return Choice(Hero.SafeToSpend, safe, warn = false)
        if (preferCash) return Choice(Hero.CashCheck, cash.value, warn = false)
        return if (cash.value < safe) Choice(Hero.CashCheck, cash.value, warn = true) else Choice(Hero.SafeToSpend, safe, warn = false)
    }
}
