package com.mansaketh.chitta.ui.home

import com.mansaketh.chitta.core.domain.Bills
import com.mansaketh.chitta.core.domain.Budgets
import com.mansaketh.chitta.core.domain.Period
import com.mansaketh.chitta.core.domain.Periods
import com.mansaketh.chitta.core.domain.QuickAdd
import com.mansaketh.chitta.core.model.Budget
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.core.model.Transaction
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The words and the one count on the "How Safe to spend is worked out" sheet (design page 14). Plain functions with no Compose in them,
 * so the sentences and the bill count are unit-tested. No money is worked out here: every figure comes from `HomeFigures`.
 */
object SafeToSpendText {
    /** "November budget". A budget already named "... budget" is not given the word twice (same rule as the Home budget line). */
    fun budgetTitle(name: String): String = if (name.endsWith("budget", ignoreCase = true)) name else "$name budget"

    /** "1–30 Nov", or "28 Oct–27 Nov" when the period crosses a month. [endInclusive] is the last day of the period. */
    fun periodText(start: LocalDate, endInclusive: LocalDate): String {
        fun month(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return if (start.month == endInclusive.month && start.year == endInclusive.year) "${start.dayOfMonth}–${endInclusive.dayOfMonth} ${month(endInclusive)}"
        else "${start.dayOfMonth} ${month(start)}–${endInclusive.dayOfMonth} ${month(endInclusive)}"
    }

    fun periodText(period: Period, zone: ZoneId): String =
        periodText(Periods.date(period.startMs, zone), Periods.date(period.endMs - 1, zone))

    fun billsLine(count: Int): String = when (count) {
        0 -> "No bills in this budget"
        1 -> "1 bill in this budget"
        else -> "$count bills in this budget"
    }

    /** "A", "A and B", "A, B and C". */
    fun join(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    /** "8:42 pm". */
    fun asOf(nowMs: Long, zone: ZoneId): String =
        java.time.Instant.ofEpochMilli(nowMs).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase(Locale.ENGLISH)

    /** The line under the total: "÷ 18 days = ₹1,528 a day". Null when there is no per-day figure (over budget, nothing safe, period over). */
    fun perDayLine(safeIsNegative: Boolean, daysLeft: Int, perDay: String?): String? = when {
        safeIsNegative || daysLeft <= 0 -> null
        daysLeft == 1 -> "Last day: the full amount"
        perDay != null -> "÷ $daysLeft days = $perDay a day"
        else -> null
    }

    /**
     * "After every bill and this month's goal savings, HDFC and Cash still hold ₹1,00,752." [amount] is already formatted (and masked when
     * amounts are hidden). With no spending account named the subject is "your spending accounts".
     */
    fun cashSentence(names: List<String>, hasSinking: Boolean, amount: String): String {
        val after = join(listOf("every bill", "this month's goal savings") + if (hasSinking) listOf("the sinking funds' set-aside") else emptyList())
        val who = if (names.isEmpty()) "your spending accounts still hold" else join(names) + if (names.size == 1) " still holds" else " still hold"
        return "After $after, $who $amount."
    }

    /**
     * How many bills make up "Bills still to come" for [budget]: open bills due inside [period] that would count toward it if paid, skipping
     * transfers and paired entries. It is the same test `Budgets.billsDue` adds up, so the count and the amount cannot disagree.
     */
    fun billCount(budget: Budget, period: Period, entries: List<Transaction>, categories: List<Category>): Int =
        entries.count { t ->
            Bills.isOpen(t) && t.dateMs in period && t.pairedTransactionId == null && t.categoryId != QuickAdd.TRANSFER_CATEGORY_ID &&
                Budgets.contribution(t.copy(paid = true), budget, categories) > 0L
        }
}
