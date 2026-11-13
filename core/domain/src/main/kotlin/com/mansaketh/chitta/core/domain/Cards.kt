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
object Cards {
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

    fun figures(setup: CardSetup, entries: List<Transaction>, today: LocalDate, zone: ZoneId): CardFigures {
        val mine = entries.filter { it.accountId == setup.accountId && it.paid && !it.skipPaid && it.kind != com.mansaketh.chitta.core.model.TransactionKind.UPCOMING }
        val stmtEnd = lastStatementEnd(setup, today)
        fun day(t: Transaction) = java.time.Instant.ofEpochMilli(t.dateMs).atZone(zone).toLocalDate()

        val upToStatement = mine.filter { !day(it).isAfter(stmtEnd) }.sumOf { it.amount }
        val statement = Paise(maxOf(0L, -upToStatement))

        val after = mine.filter { day(it).isAfter(stmtEnd) && !day(it).isAfter(today) }
        val unbilled = Paise(after.filter { it.amount < 0 }.sumOf { -it.amount })
        val payments = Paise(after.filter { it.amount > 0 }.sumOf { it.amount })

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
