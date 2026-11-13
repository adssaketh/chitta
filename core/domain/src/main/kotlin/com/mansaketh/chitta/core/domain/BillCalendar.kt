package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One line on the bill calendar. [projected] lines are repeats that have not been made yet; they cannot be paid until they are. */
data class CalendarItem(
    val entryId: String, val title: String, val amount: Paise, val status: BillStatus, val projected: Boolean, val dueMs: Long,
    val accountId: String, val categoryId: String,
)

data class CalendarDay(val date: LocalDate, val items: List<CalendarItem>) {
    val moneyOut: Paise get() = Paise(items.filter { it.status != BillStatus.Skipped && it.amount.value < 0 }.sumOf { -it.amount.value })
    val moneyIn: Paise get() = Paise(items.filter { it.status != BillStatus.Skipped && it.amount.value > 0 }.sumOf { it.amount.value })
    /** Money still to pay that day (open bills only, money out). */
    val toPay: Paise get() = Paise(items.filter { (it.status == BillStatus.Upcoming || it.status == BillStatus.Overdue) && it.amount.value < 0 }.sumOf { -it.amount.value })
}

object BillCalendar {
    /** Every day of [month], with the bills due that day: real entries by their due date, and repeats that will fall in the month. */
    fun month(entries: List<Transaction>, month: YearMonth, nowMs: Long, zone: ZoneId, maxProjected: Int = 1_200): List<CalendarDay> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val byDay = HashMap<LocalDate, MutableList<CalendarItem>>()
        fun add(date: LocalDate, item: CalendarItem) { byDay.getOrPut(date) { mutableListOf() } += item }

        for (t in entries) {
            if (!Bills.isScheduled(t)) continue
            val due = Periods.date(Bills.dueMs(t), zone)
            if (due in first..last) {
                add(due, CalendarItem(t.id, t.name, Paise(t.amount), Bills.status(t, nowMs, zone), false, Bills.dueMs(t), t.accountId, t.categoryId))
            }
            if (Bills.isOpen(t) && Bills.isRepeating(t) && t.createdAnotherFuture != true) {
                val anchor = t.anchorDay ?: Periods.date(Bills.dueMs(t), zone).dayOfMonth
                var k = 1
                while (k <= maxProjected) {
                    val ms = Periods.advance(Bills.dueMs(t), t.repeat!!, t.periodLength ?: 1, k, zone, anchor)
                    val end = t.endDateMs
                    if (end != null && ms > end) break
                    val d = Periods.date(ms, zone)
                    if (d > last) break
                    // the first repeat is made when this entry is paid, so it is shown as projected too
                    if (d >= first) add(d, CalendarItem(t.id, t.name, Paise(t.amount), BillStatus.Upcoming, true, ms, t.accountId, t.categoryId))
                    k++
                }
            }
        }
        return generateSequence(first) { d -> d.plusDays(1).takeIf { it <= last } }
            .map { d -> CalendarDay(d, byDay[d].orEmpty().sortedWith(compareBy<CalendarItem> { it.amount.value }.thenBy { it.title })) }
            .toList()
    }
}
