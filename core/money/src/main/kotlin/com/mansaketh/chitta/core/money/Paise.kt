package com.mansaketh.chitta.core.money

/**
 * An amount of money in integer paise (1/100 rupee). Never a Float or Double
 * (master prompt section 2, rule 4). Currency is carried separately in later phases.
 */
@JvmInline
value class Paise(val value: Long) : Comparable<Paise> {
    operator fun plus(other: Paise) = Paise(Math.addExact(value, other.value))
    operator fun minus(other: Paise) = Paise(Math.subtractExact(value, other.value))
    operator fun unaryMinus() = Paise(Math.negateExact(value))
    override fun compareTo(other: Paise) = value.compareTo(other.value)

    val isZero get() = value == 0L
    val isNegative get() = value < 0L

    companion object {
        val ZERO = Paise(0)

        /** Whole rupees, for tests and sample data: rupees(60_000) is ₹60,000. */
        fun rupees(whole: Long) = Paise(Math.multiplyExact(whole, 100L))

        fun of(rupees: Long, paise: Int): Paise {
            require(paise in 0..99) { "paise must be 0..99" }
            return Paise(Math.addExact(Math.multiplyExact(rupees, 100L), paise.toLong()))
        }
    }
}

/** value * numerator / denominator, rounded half away from zero, in exact Long arithmetic. */
internal fun mulDivRound(value: Long, numerator: Long, denominator: Long): Long {
    require(denominator > 0) { "denominator must be positive" }
    val product = Math.multiplyExact(value, numerator)
    val half = denominator / 2
    return if (product >= 0) (product + half) / denominator else -((-product + half) / denominator)
}
