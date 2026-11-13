package com.mansaketh.chitta.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/** The result of turning one of Paddhu's `REAL` amounts into paise. */
data class DriftConversion(
    val paise: Paise,
    /** True when the stored double was not an exact number of paise (the value moved when rounded). */
    val moved: Boolean,
)

/**
 * Paddhu (Drift) stores money as a double. We read it through its shortest decimal form
 * (`BigDecimal.valueOf`, not the binary expansion), then round to whole paise with HALF_EVEN.
 * Decision: DECISIONS D26 (Phase 3). The importer counts every `moved` value and the dry run reports them.
 * Every account is stored as paise, whatever its decimals setting; only 2 was found on a real backup (D8).
 */
fun paiseFromDriftDouble(amount: Double): DriftConversion {
    require(amount.isFinite()) { "amount must be finite" }
    return paiseFromDecimal(BigDecimal.valueOf(amount))
}

/** Rupees as an exact decimal (for example a JSON number read as text) to whole paise, HALF_EVEN. */
fun paiseFromDecimal(rupees: BigDecimal): DriftConversion {
    val exact = rupees.movePointRight(2)
    val rounded = exact.setScale(0, RoundingMode.HALF_EVEN)
    return DriftConversion(
        paise = Paise(rounded.longValueExact()),
        moved = exact.compareTo(rounded) != 0,
    )
}
