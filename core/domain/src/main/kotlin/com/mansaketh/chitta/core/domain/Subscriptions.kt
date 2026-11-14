package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId

/** How often a detected charge repeats. [repeat] and [length] are what a saved subscription uses (quarterly is every 3 months). */
enum class Cadence(val repeat: Repeat, val length: Int, val minDays: Int, val maxDays: Int, val graceDays: Int, val label: String) {
    Weekly(Repeat.WEEKLY, 1, 6, 8, 7, "weekly"),
    Monthly(Repeat.MONTHLY, 1, 28, 32, 15, "monthly"),
    Quarterly(Repeat.MONTHLY, 3, 88, 93, 30, "every 3 months"),
    Yearly(Repeat.YEARLY, 1, 360, 370, 60, "yearly"),
}

/**
 * A repeating charge found in the Ledger. [key] names the payee (letters only, lower case) and is what a dismissal remembers.
 * [amount] is the latest charge; [dueMs] is the next expected date, never before today.
 */
data class SubscriptionSuggestion(
    val key: String, val name: String, val amount: Paise, val cadence: Cadence, val charges: Int,
    val lastMs: Long, val dueMs: Long, val anchorDay: Int, val accountId: String, val categoryId: String, val subCategoryId: String?,
) {
    /** About what it costs a year, at the latest amount. Uses the same rounding as the Bills totals. */
    val perYear: Paise get() = Paise(Scheduled.perYear(amount.value, cadence.repeat, cadence.length))
}

/**
 * Subscription detection (master section 8, Should). Looks only at ordinary, paid money-out entries and never changes anything:
 * the person adds a suggestion with one tap, or says it is not a subscription.
 *
 * A suggestion needs [MIN_CHARGES] charges in a row to one payee where
 *  - every gap fits one [Cadence] (weekly 6 to 8 days, monthly 28 to 32, every 3 months 88 to 93, yearly 360 to 370),
 *  - the amounts stay within 10% (largest minus smallest is at most a tenth of the largest, worked in paise), and
 *  - the next expected charge is not long overdue ([Cadence.graceDays]), so a cancelled subscription is not suggested.
 * The run is the latest one: older charges that no longer fit are ignored. Payees already set up as a repeating bill, or
 * dismissed, are left out.
 */
object Subscriptions {
    const val MIN_CHARGES = 3
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Letters only, so "NETFLIX 1234" and "Netflix" are one payee. Under 3 letters is too little to tell payees apart. */
    fun payeeKey(name: String): String? =
        name.lowercase().filter { it.isLetter() }.takeIf { it.length >= 3 }

    private fun eligible(t: Transaction) =
        t.kind == null && t.paid && !t.skipPaid && !t.income && t.amount < 0 &&
            t.pairedTransactionId == null && t.splitGroupId == null && t.categoryId != QuickAdd.TRANSFER_CATEGORY_ID

    /** Payees that already have a live repeating bill, so they are not suggested again. */
    fun trackedKeys(entries: List<Transaction>, nowMs: Long): Set<String> =
        entries.filter {
            (it.kind == TransactionKind.SUBSCRIPTION || it.kind == TransactionKind.REPETITIVE) &&
                it.repeat != null && it.repeat != Repeat.CUSTOM && !it.income && (it.endDateMs?.let { end -> end >= nowMs } ?: true)
        }.mapNotNull { payeeKey(it.name) }.toSet()

    private fun cadenceOf(days: Long): Cadence? = Cadence.entries.firstOrNull { days in it.minDays..it.maxDays }

    private fun withinTenPercent(amounts: List<Long>): Boolean {
        val big = amounts.max(); val small = amounts.min()
        return (big - small) * 10 <= big
    }

    fun detect(entries: List<Transaction>, dismissed: Set<String>, nowMs: Long, zone: ZoneId): List<SubscriptionSuggestion> {
        val skip = dismissed + trackedKeys(entries, nowMs)
        val out = ArrayList<SubscriptionSuggestion>()
        val groups = entries.filter(::eligible).filter { it.dateMs <= nowMs }.groupBy { payeeKey(it.name) }
        for ((key, all) in groups) {
            if (key == null || key in skip) continue
            val rows = all.sortedBy { it.dateMs }
            latestRun(rows, zone)?.let { r ->
                val last = r.rows.last()
                // The day a monthly or yearly charge belongs on: the latest day seen, so one on the 31st stays on the 31st.
                val anchor = r.rows.maxOf { Periods.date(it.dateMs, zone).dayOfMonth }
                fun due(steps: Int) = Periods.advance(last.dateMs, r.cadence.repeat, r.cadence.length, steps, zone, anchor)
                if (nowMs > due(1) + r.cadence.graceDays * DAY_MS) return@let
                val today = Periods.startOfDay(Periods.date(nowMs, zone), zone)
                var steps = 1
                while (due(steps) < today && steps < 400) steps++
                out += SubscriptionSuggestion(
                    key, last.name.trim(), Paise(-last.amount), r.cadence, r.rows.size, last.dateMs, due(steps), anchor,
                    last.accountId, last.categoryId, last.subCategoryId,
                )
            }
        }
        return out.sortedWith(compareBy({ it.dueMs }, { it.name.lowercase() }))
    }

    private class Run(val rows: List<Transaction>, val cadence: Cadence)

    /** The latest run: walk back from the newest charge while each gap keeps the cadence set by the last gap and the amounts stay close. */
    private fun latestRun(sorted: List<Transaction>, zone: ZoneId): Run? {
        if (sorted.size < MIN_CHARGES) return null
        fun gap(a: Transaction, b: Transaction) =
            java.time.temporal.ChronoUnit.DAYS.between(Periods.date(a.dateMs, zone), Periods.date(b.dateMs, zone))
        val cadence = cadenceOf(gap(sorted[sorted.size - 2], sorted.last())) ?: return null
        val picked = ArrayDeque<Transaction>().apply { addFirst(sorted.last()) }
        var i = sorted.size - 2
        while (i >= 0) {
            val g = gap(sorted[i], picked.first())
            if (cadenceOf(g) != cadence) break
            if (!withinTenPercent((picked.map { -it.amount } + (-sorted[i].amount)))) break
            picked.addFirst(sorted[i]); i--
        }
        return if (picked.size >= MIN_CHARGES) Run(picked.toList(), cadence) else null
    }

    /** The subscription to save when the person adds a suggestion. The entries it came from stay as they are. */
    fun toBill(s: SubscriptionSuggestion, newId: String): Transaction = Transaction(
        id = newId, pairedTransactionId = null, name = s.name, amount = -s.amount.value, currency = null, note = "",
        categoryId = s.categoryId, subCategoryId = s.subCategoryId, accountId = s.accountId, dateMs = s.dueMs, modifiedAtMs = null,
        originalDueMs = null, income = false, periodLength = s.cadence.length, repeat = s.cadence.repeat, endDateMs = null,
        upcomingNotification = true, kind = TransactionKind.SUBSCRIPTION, paid = false, createdAnotherFuture = false, skipPaid = false,
        methodAdded = null, objectiveId = null, objectiveLoanId = null, excludeFromBudgetIds = emptyList(), ownerUid = null,
        isPrivate = false, householdSyncedAtMs = null,
        anchorDay = if (s.cadence.repeat == Repeat.MONTHLY || s.cadence.repeat == Repeat.YEARLY) s.anchorDay else null,
    )
}
