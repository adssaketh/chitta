package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Transaction

/** Why two entries look like the same payment. The reason is shown to the person (master section 13). */
enum class DuplicateReason { SameAmountAndTitle, SameAmountCloseTime }

data class DuplicatePair(val entryId: String, val otherId: String, val reason: DuplicateReason)

/**
 * Likely duplicates in the Ledger (master section 8: same amount, close time, similar merchant).
 * Two real payments of one amount in a day stay two (master section 13): a pair is only flagged when the titles are
 * similar, or when the gap is short and one title is empty. Flagging never changes or deletes anything.
 */
object Duplicates {
    const val CLOSE_MS = 10L * 60 * 1000          // same amount within 10 minutes, any title
    const val SAME_DAY_MS = 24L * 60 * 60 * 1000  // same amount and similar title within a day

    fun normalise(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }

    /** Similar when equal after cleaning, or one contains the other (at least 4 letters), e.g. "Swiggy" and "SWIGGY ORDER". */
    fun similarTitles(a: String, b: String): Boolean {
        val x = normalise(a); val y = normalise(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return short.length >= 4 && long.contains(short)
    }

    private fun eligible(t: Transaction) =
        t.kind == null && t.paid && t.pairedTransactionId == null && t.categoryId != QuickAdd.TRANSFER_CATEGORY_ID

    fun reasonFor(a: Transaction, b: Transaction): DuplicateReason? {
        if (a.id == b.id || !eligible(a) || !eligible(b)) return null
        if (a.accountId != b.accountId || a.amount != b.amount) return null
        // Two lines of one split share an amount only by chance; they are never duplicates of each other.
        if (a.splitGroupId != null && a.splitGroupId == b.splitGroupId) return null
        val gap = kotlin.math.abs(a.dateMs - b.dateMs)
        return when {
            gap <= SAME_DAY_MS && similarTitles(a.name, b.name) -> DuplicateReason.SameAmountAndTitle
            gap <= CLOSE_MS -> DuplicateReason.SameAmountCloseTime
            else -> null
        }
    }

    /** Every flagged pair once, earlier entry first. Sorted by date so the scan stays near-linear. */
    fun scan(entries: List<Transaction>): List<DuplicatePair> {
        val sorted = entries.filter(::eligible).sortedBy { it.dateMs }
        val out = ArrayList<DuplicatePair>()
        for (i in sorted.indices) {
            var j = i + 1
            while (j < sorted.size && sorted[j].dateMs - sorted[i].dateMs <= SAME_DAY_MS) {
                reasonFor(sorted[i], sorted[j])?.let { out += DuplicatePair(sorted[i].id, sorted[j].id, it) }
                j++
            }
        }
        return out
    }

    /** Ids to show a "possible duplicate" mark on in the Ledger: the later entry of each pair. */
    fun flaggedIds(entries: List<Transaction>): Set<String> = scan(entries).map { it.otherId }.toSet()
}
