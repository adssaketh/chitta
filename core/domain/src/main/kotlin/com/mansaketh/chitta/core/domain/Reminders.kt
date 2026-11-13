package com.mansaketh.chitta.core.domain

import com.mansaketh.chitta.core.model.Transaction
import com.mansaketh.chitta.core.money.Paise
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReminderKind { DueToday, DayBefore }

/** A notification to post. [key] is stable, so a reminder that was already posted is not posted twice. */
data class Reminder(val key: String, val entryId: String, val title: String, val amount: Paise, val income: Boolean, val dueMs: Long, val kind: ReminderKind)

/** When reminders are sent. Defaults agreed for Phase 5: bills the day before and on the day, 9:00 am; a nudge to log at 9:00 pm. Digests stay off. */
data class ReminderSettings(
    val dueBills: Boolean = true, val billHour: Int = 9, val billMinute: Int = 0,
    val logReminder: Boolean = true, val logHour: Int = 21, val logMinute: Int = 0,
)

object Reminders {
    /**
     * The bill reminders for [today]: open bills due today, and open bills due tomorrow. An entry whose own "notify" flag is off is left out.
     * Paid and skipped bills never remind. Overdue bills are not repeated: they show in red on Plan.
     */
    fun forDay(entries: List<Transaction>, today: LocalDate, zone: ZoneId): List<Reminder> {
        val out = ArrayList<Reminder>()
        for (t in entries) {
            if (!Bills.isOpen(t) || t.upcomingNotification == false || t.pairedTransactionId != null) continue
            val due = Periods.date(t.dateMs, zone)
            val kind = when (due) { today -> ReminderKind.DueToday; today.plusDays(1) -> ReminderKind.DayBefore; else -> continue }
            out += Reminder("${t.id}|$due|$kind", t.id, t.name, Paise(Math.abs(t.amount)), t.income, t.dateMs, kind)
        }
        return out.sortedWith(compareBy<Reminder> { it.kind }.thenBy { it.dueMs })
    }

    /** The next time at [hour]:[minute] strictly after [nowMs], in the phone's zone (clock changes handled by java.time). */
    fun nextFireMs(nowMs: Long, zone: ZoneId, hour: Int, minute: Int): Long {
        val now = java.time.Instant.ofEpochMilli(nowMs).atZone(zone)
        var at: ZonedDateTime = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zone)
        if (!at.toInstant().isAfter(now.toInstant())) at = now.toLocalDate().plusDays(1).atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).atZone(zone)
        return at.toInstant().toEpochMilli()
    }
}
