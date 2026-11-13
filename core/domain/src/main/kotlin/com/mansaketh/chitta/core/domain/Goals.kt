package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Objective
import com.mansaketh.chitta.core.model.ObjectiveKind
import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.core.money.Segments
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class GoalProgress(
    val saved: Paise,
    val target: Paise,
    val remaining: Paise,
    val reached: Boolean,
    val gauge: Segments.Gauge,
    /** Whole months until the end date, rounded up, at least 1. Null when there is no end date or it has passed. */
    val monthsLeft: Int?,
    /** What to put in each month to arrive on the end date. Null when nothing is left, there is no end date, or it has passed. */
    val perMonthNeeded: Paise?,
    val pastEnd: Boolean,
)

/**
 * Goals as Paddhu counts them (`watchTotalTowardsObjective` and `objectivesListPage.dart`): the paid entries linked to the goal are added up,
 * and for an expense goal the sum is flipped, so money going out counts as progress. No confetti (DECISIONS D7).
 */
object Goals {
    fun isGoal(o: Objective) = o.kind == ObjectiveKind.GOAL

    fun saved(o: Objective, entries: List<Transaction>): Paise {
        var sum = 0L
        for (t in entries) if (t.objectiveId == o.id && t.paid) sum = Math.addExact(sum, t.amount)
        return Paise(if (o.income) sum else -sum)
    }

    fun progress(o: Objective, entries: List<Transaction>, nowMs: Long, zone: ZoneId): GoalProgress {
        val saved = saved(o, entries)
        val target = Paise(o.amount)
        val remaining = Paise((target.value - saved.value).coerceAtLeast(0))
        val reached = target.value > 0 && saved >= target
        val today = Periods.date(nowMs, zone)
        val end = o.endDateMs?.let { Periods.date(it, zone) }
        val pastEnd = end != null && end < today && !reached
        val months = if (end == null || end < today) null else {
            val whole = ChronoUnit.MONTHS.between(today, end)
            (if (today.plusMonths(whole) < end) whole + 1 else whole).toInt().coerceAtLeast(1)
        }
        val perMonth = if (months == null || remaining.isZero) null else Paise((remaining.value + months - 1) / months)
        return GoalProgress(saved, target, remaining, reached, Segments.gauge(Paise(saved.value.coerceAtLeast(0)), target, 20), months, perMonth, pastEnd)
    }

    /** An entry that adds to a goal: money in for a savings goal, money out for an expense goal, linked to it. */
    fun contribution(
        o: Objective, amountPaise: Long, accountId: String, categoryId: String, title: String, nowMs: Long, ownerUid: String?, newId: () -> String,
    ): Transaction = Transaction(
        id = newId(), pairedTransactionId = null, name = title, amount = if (o.income) amountPaise else -amountPaise, currency = null, note = "",
        categoryId = categoryId, subCategoryId = null, accountId = accountId, dateMs = nowMs, modifiedAtMs = null, originalDueMs = null,
        income = o.income, periodLength = null, repeat = null, endDateMs = null, upcomingNotification = null, kind = null, paid = true,
        createdAnotherFuture = null, skipPaid = false, methodAdded = null, objectiveId = o.id, objectiveLoanId = null,
        excludeFromBudgetIds = emptyList(), ownerUid = ownerUid, isPrivate = false, householdSyncedAtMs = null,
    )
}
