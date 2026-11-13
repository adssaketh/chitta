package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.money.Paise
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The keypad's calculator. Text such as "1200+350.5" is evaluated in exact decimal arithmetic and rounded
 * once, at the end, to whole paise (HALF_EVEN). Never Double (master section 2, rule 4).
 */
object AmountExpression {
    const val MAX_DIGITS = 12
    private val OPERATORS = setOf('+', '−', '×', '÷')

    private fun normalise(c: Char): Char = when (c) {
        '-' -> '−'
        '*' -> '×'
        '/' -> '÷'
        else -> c
    }

    /** The value of [raw], or null when it is empty, ends up negative or divides by zero. A trailing operator is ignored. */
    fun evaluate(raw: String): Paise? {
        val text = raw.map(::normalise).joinToString("").trimEnd('+', '−', '×', '÷', '.')
        if (text.isEmpty()) return null
        val numbers = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        val current = StringBuilder()
        for (ch in text) {
            if (ch in OPERATORS) {
                if (current.isEmpty()) return null
                numbers.add(current.toString().toBigDecimalOrNull() ?: return null)
                current.setLength(0)
                ops.add(ch)
            } else current.append(ch)
        }
        numbers.add(current.toString().trimEnd('.').toBigDecimalOrNull() ?: return null)

        // × and ÷ first, left to right.
        val values = ArrayList<BigDecimal>()
        val addOps = ArrayList<Char>()
        var acc = numbers[0]
        for (i in ops.indices) {
            when (ops[i]) {
                '×' -> acc = acc.multiply(numbers[i + 1])
                '÷' -> {
                    if (numbers[i + 1].signum() == 0) return null
                    acc = acc.divide(numbers[i + 1], MathContext.DECIMAL128)
                }
                else -> { values.add(acc); addOps.add(ops[i]); acc = numbers[i + 1] }
            }
        }
        values.add(acc)
        var total = values[0]
        for (i in addOps.indices) total = if (addOps[i] == '+') total.add(values[i + 1]) else total.subtract(values[i + 1])

        if (total.signum() < 0) return null
        val paise = total.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2)
        return if (paise.compareTo(BigDecimal(Long.MAX_VALUE / 1000)) > 0) null else Paise(paise.longValueExact())
    }

    /** True when [raw] still holds an operator, so the Save button should show the result first. */
    fun isCalculation(raw: String): Boolean = raw.any { it in OPERATORS || it == '-' || it == '*' || it == '/' }

    // Editing rules for the keypad. Each returns the new text; an ignored key returns the text unchanged.

    fun digit(raw: String, d: Char): String {
        require(d in '0'..'9')
        val last = lastNumber(raw)
        if (last.length >= MAX_DIGITS) return raw
        val dot = last.indexOf('.')
        if (dot >= 0 && last.length - dot > 2) return raw // two decimals at most
        return if (last == "0") raw.dropLast(1) + d else raw + d
    }

    fun dot(raw: String): String {
        val last = lastNumber(raw)
        if (last.contains('.')) return raw
        return if (last.isEmpty()) raw + "0." else raw + "."
    }

    fun operator(raw: String, op: Char): String {
        val o = normalise(op)
        require(o in OPERATORS)
        if (raw.isEmpty()) return raw
        val last = raw.last()
        val base = if (last in OPERATORS) raw.dropLast(1) else raw.trimEnd('.')
        return if (base.isEmpty()) raw else base + o
    }

    fun backspace(raw: String): String = raw.dropLast(1)

    private fun lastNumber(raw: String): String = raw.takeLastWhile { it !in OPERATORS }
}
