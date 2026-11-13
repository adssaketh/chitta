package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Repeat
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.model.TransactionKind
import com.mansaketh.chitta.core.money.Paise
import java.time.ZoneId

enum class BillStatus { Upcoming, Overdue, Paid, Skipped }

/** Which date a paid bill keeps. Paddhu's setting `markAsPaidOnOriginalDay`. */
enum class PaidDateRule { Today, DueDate }

/** The result of paying or skipping: the entry as it should be saved, and the next occurrence if one is due. */
data class BillChange(val updated: Transaction, val next: Transaction?)

/**
 * Upcoming, repeating and subscription entries. Behaviour follows Paddhu's `upcomingTransactionsFunctions.dart` (read, not copied):
 *  - paying sets paid, moves the date to today unless the setting keeps the due date, and remembers the old date as the due date;
 *  - skipping sets skipped and leaves paid off (so it counts nowhere) and moves the date to today;
 *  - a repeating entry makes its next occurrence when it is paid or skipped, counted from the due date, not the paid date;
 *  - nothing is made after the end date, or when the linked goal is already reached.
 * One deliberate change: a monthly bill on the 31st uses the last day of a short month (Paddhu's date maths overflows into the next month).
 */
object Bills {
    fun isScheduled(t: Transaction) = t.kind == TransactionKind.UPCOMING || t.kind == TransactionKind.SUBSCRIPTION || t.kind == TransactionKind.REPETITIVE
    fun isRepeating(t: Transaction) = (t.kind == TransactionKind.SUBSCRIPTION || t.kind == TransactionKind.REPETITIVE) &&
        t.repeat != null && t.repeat != Repeat.CUSTOM

    /** The date a bill is really due: the original due date once it has been paid on another day. */
    fun dueMs(t: Transaction) = t.originalDueMs ?: t.dateMs

    fun status(t: Transaction, nowMs: Long, zone: ZoneId): BillStatus = when {
        t.skipPaid -> BillStatus.Skipped
        t.paid -> BillStatus.Paid
        Periods.date(t.dateMs, zone) < Periods.date(nowMs, zone) -> BillStatus.Overdue
        else -> BillStatus.Upcoming
    }

    /** True when this entry is a bill that still needs paying or skipping. */
    fun isOpen(t: Transaction) = isScheduled(t) && !t.paid && !t.skipPaid

    /** A zero amount needs confirming before it is paid (Paddhu asks for the amount). */
    fun needsAmount(t: Transaction) = t.amount == 0L

    /** The next due date of a repeating entry, or null when it does not repeat or would pass its end date. */
    fun nextDueMs(t: Transaction, zone: ZoneId): Long? {
        if (!isRepeating(t)) return null
        val anchor = t.anchorDay ?: Periods.date(dueMs(t), zone).dayOfMonth
        val next = Periods.advance(dueMs(t), t.repeat!!, t.periodLength ?: 1, 1, zone, anchor)
        val end = t.endDateMs
        return if (end != null && end < next) null else next
    }

    /**
     * Pays [t]. [amountPaise] (a magnitude) and [accountId] change only this occurrence, for a bill that came to a different amount or
     * was paid from another account; the next occurrence keeps the series' amount and account.
     * [goalReached] stops a repeating entry linked to a goal from making another occurrence once the goal is met.
     */
    fun pay(
        t: Transaction, nowMs: Long, zone: ZoneId, rule: PaidDateRule, newId: () -> String,
        amountPaise: Long? = null, accountId: String? = null, goalReached: Boolean = false,
    ): BillChange {
        require(isOpen(t)) { "Only an open bill can be paid." }
        val sign = if (t.income) 1L else -1L
        val next = makeNext(t, zone, newId, goalReached)
        val paid = t.copy(
            paid = true, skipPaid = false,
            dateMs = if (rule == PaidDateRule.Today) nowMs else t.dateMs,
            originalDueMs = dueMs(t),
            amount = if (amountPaise != null) sign * amountPaise else t.amount,
            accountId = accountId ?: t.accountId,
            createdAnotherFuture = if (isRepeating(t)) true else t.createdAnotherFuture,
            modifiedAtMs = nowMs,
        )
        return BillChange(paid, next)
    }

    fun skip(t: Transaction, nowMs: Long, zone: ZoneId, newId: () -> String, goalReached: Boolean = false): BillChange {
        require(isOpen(t)) { "Only an open bill can be skipped." }
        val next = makeNext(t, zone, newId, goalReached)
        val skipped = t.copy(
            skipPaid = true, dateMs = nowMs, originalDueMs = dueMs(t),
            createdAnotherFuture = if (isRepeating(t)) true else t.createdAnotherFuture, modifiedAtMs = nowMs,
        )
        return BillChange(skipped, next)
    }

    /** Takes back a skip: the entry is open again. Any occurrence already made after it stays (the person can delete it). */
    fun removeSkip(t: Transaction, nowMs: Long): Transaction = t.copy(skipPaid = false, dateMs = dueMs(t), modifiedAtMs = nowMs)

    /** Takes back a payment: the entry is open again on its due date. */
    fun removePayment(t: Transaction, nowMs: Long): Transaction = t.copy(paid = false, dateMs = dueMs(t), modifiedAtMs = nowMs)

    private fun makeNext(t: Transaction, zone: ZoneId, newId: () -> String, goalReached: Boolean): Transaction? {
        if (t.createdAnotherFuture == true || goalReached) return null
        val due = nextDueMs(t, zone) ?: return null
        val anchor = t.anchorDay ?: Periods.date(dueMs(t), zone).dayOfMonth
        return t.copy(
            id = newId(), pairedTransactionId = null, dateMs = due, originalDueMs = null, paid = false, skipPaid = false,
            createdAnotherFuture = false, modifiedAtMs = null, householdSyncedAtMs = null, anchorDay = anchor,
        )
    }

    /**
     * Bills the "pay overdue bills automatically" setting would pay now: repeating and subscription bills whose due date has passed.
     * Each one chains: a monthly bill three months behind gives three payments and then a future occurrence. Never touches a bill that
     * was already paid or skipped. Each is paid on its due date. [maxSteps] only guards against a bad end date.
     */
    fun autoPay(
        entries: List<Transaction>, nowMs: Long, zone: ZoneId, newId: () -> String, maxSteps: Int = 400,
    ): List<BillChange> {
        val out = mutableListOf<BillChange>()
        val queue = ArrayDeque(entries.filter { isOpen(it) && it.createdAnotherFuture != true && it.dateMs <= nowMs + 60_000 }.sortedBy { it.dateMs })
        while (queue.isNotEmpty() && out.size < maxSteps) {
            val t = queue.removeFirst()
            val change = pay(t, nowMs, zone, PaidDateRule.DueDate, newId) // paid automatically, on its due date
            out += change
            val next = change.next
            if (next != null && next.dateMs <= nowMs + 60_000) queue.addFirst(next)
        }
        return out
    }
}

/** Totals for the scheduled-entries page: what is still to pay, and what it comes to a month and a year at the current repeats. Money out only. */
data class ScheduledTotals(val total: Paise, val perMonth: Paise, val perYear: Paise, val repeating: Int)

object Scheduled {
    /** Open bills that are money out, excluding transfers. Each repeating series is counted once, at its repeat rate. */
    fun totals(entries: List<Transaction>): ScheduledTotals {
        val open = entries.filter { Bills.isOpen(it) && !it.income }
        var total = 0L
        var year = 0L
        var repeating = 0
        for (t in open) {
            val amount = -t.amount
            total += amount
            if (Bills.isRepeating(t)) {
                repeating++
                year += perYear(amount, t.repeat!!, t.periodLength ?: 1)
            }
        }
        return ScheduledTotals(Paise(total), Paise(mulDivRound(year, 1, 12)), Paise(year), repeating)
    }

    /** A year of a repeat: daily × 365, weekly × 365 ÷ 7, monthly × 12, yearly × 1, all ÷ the period length, rounded once. */
    fun perYear(amount: Long, repeat: Repeat, length: Int): Long {
        val len = length.coerceAtLeast(1).toLong()
        return when (repeat) {
            Repeat.DAILY -> mulDivRound(amount, 365, len)
            Repeat.WEEKLY -> mulDivRound(amount, 365, 7 * len)
            Repeat.MONTHLY -> mulDivRound(amount, 12, len)
            Repeat.YEARLY -> mulDivRound(amount, 1, len)
            Repeat.CUSTOM -> 0
        }
    }
}
