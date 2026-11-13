package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

/** What a person enters once on a card account (schema 4). Everything but the two days may be empty. */
data class CardSetup(
    val accountId: String,
    val limit: Paise?,
    /** Day of month the statement is cut, 1..31 (a short month uses its last day). */
    val statementDay: Int,
    /** Day of month the payment is due, 1..31. */
    val dueDay: Int,
    /** Typed from the statement; never guessed. */
    val minimumDue: Paise?,
    val remindDaysBefore: Int = 3,
)

data class CardFigures(
    val statementEnd: LocalDate,
    val nextDue: LocalDate,
    val daysToDue: Long,
    val statementBalance: Paise,
    val unbilled: Paise,
    val paymentsSinceStatement: Paise,
    /** statement + unbilled - payments, never below zero (master section 12). */
    val outstanding: Paise,
    /** Money the card owes the holder (refunds above what is owed); zero normally. */
    val credit: Paise,
    val available: Paise?,
    /** Outstanding / limit in basis points (1% = 100); null when no limit is set. */
    val utilisationBp: Int?,
    val overLimit: Boolean,
    val minimumDue: Paise?,
    val reminderDate: LocalDate,
)

/**
 * Credit-card cockpit (master section 8) with the section 12 rules. Entries on the card account are paid entries:
 * spending is negative, payments and refunds are positive. A bill payment from the bank account is the paired
 * transfer, which shows up here as a positive entry on the card.
 */
/** A card payment notification to post. [key] is stable so it is posted once. */
data class CardReminder(val key: String, val accountId: String, val dueToday: Boolean, val daysToDue: Long, val outstanding: Paise, val minimumDue: Paise?)

object Cards {
    /** The setup of an account, or null until both the statement day and the due day are set. */
    fun setupOf(a: com.mansaketh.chitta.core.model.Account): CardSetup? {
        val s = a.statementDay ?: return null
        val d = a.dueDay ?: return null
        return CardSetup(a.id, a.creditLimit?.let { Paise(it) }, s, d, a.minimumDue?.let { Paise(it) }, a.remindDaysBefore ?: 3)
    }

    /**
     * The notification for [today], or null. One on the reminder day and one on the due day, only while something is owed.
     * With a reminder of 0 days the two coincide and only the due-day one is sent.
     */
    fun reminderFor(setup: CardSetup, f: CardFigures, today: LocalDate): CardReminder? {
        if (f.outstanding.value <= 0) return null
        val onDue = today == f.nextDue
        val onReminder = today == f.reminderDate
        if (!onDue && !onReminder) return null
        return CardReminder("card|${setup.accountId}|${f.nextDue}|${if (onDue) "due" else "ahead"}", setup.accountId, onDue, f.daysToDue, f.outstanding, f.minimumDue)
    }

    private fun clampDay(ym: YearMonth, day: Int): LocalDate = ym.atDay(minOf(day.coerceIn(1, 31), ym.lengthOfMonth()))

    /** The latest statement date that is on or before [today]. */
    fun lastStatementEnd(setup: CardSetup, today: LocalDate): LocalDate {
        val thisMonth = clampDay(YearMonth.from(today), setup.statementDay)
        return if (!thisMonth.isAfter(today)) thisMonth else clampDay(YearMonth.from(today).minusMonths(1), setup.statementDay)
    }

    /** The first due date on or after [today]. */
    fun nextDue(setup: CardSetup, today: LocalDate): LocalDate {
        val thisMonth = clampDay(YearMonth.from(today), setup.dueDay)
        return if (!thisMonth.isBefore(today)) thisMonth else clampDay(YearMonth.from(today).plusMonths(1), setup.dueDay)
    }

    /**
     * [dues] are the card's messages read from SMS (already matched to this account, see [CardDues.flowsFor]); they count like entries.
     * An empty list gives exactly the figures from entries alone.
     */
    fun figures(setup: CardSetup, entries: List<Transaction>, today: LocalDate, zone: ZoneId, dues: List<CardDues.Flow> = emptyList()): CardFigures {
        val mine = entries.filter { it.accountId == setup.accountId && it.paid && !it.skipPaid && it.kind != com.mansaketh.chitta.core.model.TransactionKind.UPCOMING }
        val stmtEnd = lastStatementEnd(setup, today)
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val items = mine.map { day(it.dateMs) to it.amount } + dues.map { day(it.dateMs) to it.signed }

        val upToStatement = items.filter { !it.first.isAfter(stmtEnd) }.sumOf { it.second }
        val statement = Paise(maxOf(0L, -upToStatement))

        val after = items.filter { it.first.isAfter(stmtEnd) && !it.first.isAfter(today) }
        val unbilled = Paise(after.filter { it.second < 0 }.sumOf { -it.second })
        val payments = Paise(after.filter { it.second > 0 }.sumOf { it.second })

        val raw = statement.value + unbilled.value - payments.value
        val outstanding = Paise(maxOf(0L, raw))
        val credit = Paise(maxOf(0L, -raw))

        val limit = setup.limit?.takeIf { it.value > 0 }
        val available = limit?.let { Paise(it.value - outstanding.value) }
        val utilBp = limit?.let { Math.toIntExact((outstanding.value * 10_000L + it.value / 2) / it.value) }
        val due = nextDue(setup, today)
        return CardFigures(
            statementEnd = stmtEnd, nextDue = due,
            daysToDue = java.time.temporal.ChronoUnit.DAYS.between(today, due),
            statementBalance = statement, unbilled = unbilled, paymentsSinceStatement = payments,
            outstanding = outstanding, credit = credit,
            available = available, utilisationBp = utilBp,
            overLimit = limit != null && outstanding.value > limit.value,
            minimumDue = setup.minimumDue,
            reminderDate = due.minusDays(setup.remindDaysBefore.toLong().coerceAtLeast(0)),
        )
    }
}
