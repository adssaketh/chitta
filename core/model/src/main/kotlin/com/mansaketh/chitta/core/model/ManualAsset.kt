package com.mansaketh.chitta.core.model

/** The kinds of asset typed in by hand (Phase 8i). */
enum class AssetKind(val label: String, val staleAfterDays: Long) {
    GOLD("Gold", 60),
    EPF("EPF", 35),
    PPF("PPF", 35);

    companion object {
        fun of(name: String): AssetKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * An asset whose value is typed in, with a history of dated values (gold, EPF, PPF). Nothing moves between accounts: it is a record,
 * counted in Net worth at its latest value. The value itself lives in [AssetValue] rows, newest decides.
 */
data class ManualAsset(
    val id: String,
    val kind: AssetKind,
    val name: String,
    val note: String,
    val createdAtMs: Long,
)

/**
 * One typed value of an asset: what it was worth on [asOfMs] (a day the person chose). [valuePaise] is zero or more (zero means sold or
 * emptied). [recordedAtMs] is when it was typed, used only to pick between two values given for the same day: the later one wins.
 */
data class AssetValue(
    val id: String,
    val assetId: String,
    val asOfMs: Long,
    val valuePaise: Long,
    val recordedAtMs: Long,
)
