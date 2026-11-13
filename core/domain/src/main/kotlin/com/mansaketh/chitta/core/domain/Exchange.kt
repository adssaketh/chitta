package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency conversion with the rate kept as decimal text (D31: a rate like 83.2150 never goes through a Double).
 * The converted value is stored on the entry when it is saved, with the original amount, currency and rate,
 * so history never moves when today's rate changes (master section 7).
 */
object Exchange {
    /** "83.215" to a positive rate, or null for anything else. */
    fun parseRate(text: String): BigDecimal? =
        text.trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    /** [amount] times [rate], rounded half-even to whole paise. */
    fun convert(amount: Paise, rate: BigDecimal): Paise {
        val converted = BigDecimal(amount.value).multiply(rate).setScale(0, RoundingMode.HALF_EVEN)
        return Paise(converted.longValueExact())
    }

    /** Rate for the opposite direction, to six decimals, as text. */
    fun inverse(rate: BigDecimal): String = BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_EVEN).toPlainString()

    /**
     * Rate to use when converting from [from] to [to] given the rates table "units of [base] per one unit of X"
     * (the shape of the daily rates file). A custom override, when present, wins. Null if either currency is unknown.
     */
    fun rateBetween(from: String, to: String, perBase: Map<String, BigDecimal>, overrides: Map<Pair<String, String>, BigDecimal> = emptyMap()): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        overrides[from to to]?.let { return it }
        val f = perBase[from] ?: return null
        val t = perBase[to] ?: return null
        if (f.signum() == 0) return null
        return t.divide(f, 10, RoundingMode.HALF_EVEN)
    }
}
