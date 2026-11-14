package com.mansaketh.chitta.core.model

/**
 * One mutual fund holding (Phase 8c). Units and prices are decimal text, never Double: units carry up to 3 places and an AMFI price up to
 * 4, so paise would lose them. Money stays in paise.
 *
 * [source] says where the units came from ([SOURCE_MANUAL], [SOURCE_CONSOLE], [SOURCE_CAS]). One source never overwrites another (master
 * section 8). [investedPaise] is null when there is no average price: the fund is then left out of the gain, never guessed.
 * [navText] and [navDate] (yyyy-MM-dd) are null until a price has been fetched; the screen then says "No price yet".
 */
data class MutualFund(
    val id: String,
    val name: String,
    val isin: String,
    val schemeCode: String,
    val units: String,
    val investedPaise: Long?,
    val source: String,
    val unitsAsOfMs: Long,
    val navText: String?,
    val navDate: String?,
    val note: String,
    val updatedAtMs: Long?,
    /** The folio number from a statement; empty when typed by hand. With the name it tells one statement row from another. */
    val folio: String = "",
    /** Empty for a mutual fund. For an NPS scheme, the id of its [NpsAccount]. NPS is shown on its own tab and never added to mutual funds. */
    val groupId: String = "",
    /** Optional monthly SIP (Phase 8d): the amount in paise and the day of the month it is taken. Both set, or both null. */
    val sipAmountPaise: Long? = null,
    val sipDay: Int? = null,
    /** Estimated units added by the SIP since the units were last typed or imported (decimal text). Labelled "estimated"; an import or an edit of the units resets it to zero. */
    val sipUnits: String = "0",
    /** What those estimated instalments put in, in paise, so the gain does not count estimated units as free. */
    val sipInvestedPaise: Long = 0,
    /** How many instalments the estimate covers. */
    val sipCount: Int = 0,
    /** yyyy-MM-dd: instalments dated up to and including this day are already in the units or in the estimate. Empty until a SIP is set. */
    val sipThrough: String = "",
) {
    val isNps: Boolean get() = groupId.isNotEmpty()

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_CONSOLE = "console"
        const val SOURCE_CAS = "cas"
    }
}

/**
 * An NPS account (Tier I or II). Its schemes are [MutualFund] rows whose groupId is [id] (units and a price from the statement, no price file
 * exists for NPS here). [netContributionsPaise] is what was paid in minus what was withdrawn, as the statement states it: the statement gives
 * no cost per scheme, so the gain is worked out for the whole account only. The PRAN is never stored.
 */
data class NpsAccount(
    val id: String,
    val name: String,
    val netContributionsPaise: Long?,
    val asOfMs: Long,
    val note: String,
    val updatedAtMs: Long?,
)
