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
