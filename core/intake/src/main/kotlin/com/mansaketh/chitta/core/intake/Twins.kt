package com.mansaketh.chitta.core.intake

import java.time.ZoneId

/**
 * The same payment can arrive twice: as the bank's SMS and as the PhonePe / UPI page. They are twins when direction, amount and day
 * match. Each row is claimed by at most one twin, so two real payments of the same amount on one day are not merged into one.
 * The bank SMS row is kept (it names the account); it takes the merchant and note of the UPI row, which knows who was paid.
 */
object Twins {
    fun merge(rows: List<IntakeRow>, zone: ZoneId): List<IntakeRow> {
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val sms = rows.filter { !it.isUpi }
        val upi = rows.filter { it.isUpi }
        val claimed = HashSet<String>()
        val enriched = HashMap<String, IntakeRow>()
        for (u in upi) {
            val twin = sms.firstOrNull { s ->
                s.sourceId !in claimed && s.isExpense == u.isExpense && s.amountPaise == u.amountPaise && day(s.dateMs) == day(u.dateMs)
            } ?: continue
            claimed.add(twin.sourceId)
            enriched[twin.sourceId] = twin.copy(
                title = u.title.ifBlank { twin.title }, merchant = u.merchant.ifBlank { twin.merchant },
                categoryLabel = twin.categoryLabel.ifBlank { u.categoryLabel },
                extraNote = listOf(twin.extraNote, u.extraNote, "UPI ${u.sourceId}").filter { it.isNotBlank() }.joinToString("\n"),
            )
            // the UPI row is dropped: remember its id so it is not offered again
            enriched["drop:${u.sourceId}"] = u
        }
        val dropped = enriched.keys.filter { it.startsWith("drop:") }.map { it.removePrefix("drop:") }.toSet()
        return rows.filter { it.sourceId !in dropped }.map { enriched[it.sourceId] ?: it }
    }
}
