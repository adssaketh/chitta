package com.mansaketh.chitta.core.money

/**
 * Indian digit grouping (1,00,000) for amounts, including the half-typed text in the keypad.
 * Negative amounts use a true minus sign (U+2212), never a hyphen.
 */
object IndianFormat {
    const val RUPEE = "₹"
    const val MINUS = "−"

    /** Groups a string of digits: "12345678" becomes "1,23,45,678". */
    fun group(digits: String): String {
        require(digits.all { it in '0'..'9' }) { "digits only" }
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(rest)
        return parts.asReversed().joinToString(",") + "," + lastThree
    }

    /**
     * Exact amount: whole rupees get no decimals, otherwise two ("₹613.34").
     * Set [alwaysShowPaise] for columns that must line up.
     */
    fun rupees(amount: Paise, symbol: String = RUPEE, alwaysShowPaise: Boolean = false): String {
        require(amount.value != Long.MIN_VALUE) { "out of range" }
        val abs = Math.abs(amount.value)
        val whole = abs / 100
        val fraction = (abs % 100).toInt()
        val text = StringBuilder(group(whole.toString()))
        if (fraction != 0 || alwaysShowPaise) text.append('.').append(fraction.toString().padStart(2, '0'))
        return (if (amount.isNegative) MINUS else "") + symbol + text
    }

    /** Hero and per-day figures: rounded half up to a whole rupee ("₹1,528"). */
    fun rupeesRounded(amount: Paise, symbol: String = RUPEE): String {
        val roundedRupees = mulDivRound(amount.value, 1, 100)
        return rupees(Paise.rupees(roundedRupees), symbol)
    }

    private val numberToken = Regex("""\d+(\.\d*)?|\.\d+""")

    /**
     * Live display of text being typed, including calculator input such as "1200+350.5".
     * Each number is grouped as you type; operators and a trailing "." are kept.
     */
    fun entry(raw: String): String = numberToken.replace(raw) { m ->
        val token = m.value
        val dot = token.indexOf('.')
        val intPart = if (dot >= 0) token.substring(0, dot) else token
        val frac = if (dot >= 0) token.substring(dot) else ""
        group(intPart.trimStart('0').ifEmpty { "0" }) + frac
    }

    /**
     * What TalkBack should read: plain digits so the speech engine says the number itself,
     * for example "rupees 1200" is spoken as "rupees twelve hundred".
     */
    fun spoken(amount: Paise): String {
        require(amount.value != Long.MIN_VALUE) { "out of range" }
        val abs = Math.abs(amount.value)
        val whole = abs / 100
        val fraction = (abs % 100).toInt()
        val sign = if (amount.isNegative) "minus " else ""
        return sign + "rupees $whole" + if (fraction != 0) " and $fraction paise" else ""
    }
}
