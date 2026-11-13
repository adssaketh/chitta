package com.mansaketh.chitta.core.intake

import java.math.BigDecimal
import java.math.RoundingMode

/** Reading rupee amounts out of text, always into integer paise (master prompt section 2, rule 4). */
internal object Rupees {
    /** "1,200.00" or "697.43" as paise, or null when it is not a number or does not fit. More than two decimals round half-even. */
    fun toPaise(text: String?): Long? {
        val s = text?.replace(",", "")?.trim() ?: return null
        if (!Regex("^\\d+(\\.\\d+)?\\z").matches(s)) return null
        return try {
            BigDecimal(s).setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }
}
