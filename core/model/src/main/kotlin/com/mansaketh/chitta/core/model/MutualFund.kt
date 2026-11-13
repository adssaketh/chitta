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
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_CONSOLE = "console"
        const val SOURCE_CAS = "cas"
    }
}
