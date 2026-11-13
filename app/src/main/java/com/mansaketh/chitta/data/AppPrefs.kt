package com.mansaketh.chitta.data

import android.content.Context
import com.mansaketh.chitta.core.domain.ReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Small switches that are not money: first run done, the import marker that allows Undo for 30 days, and reminder choices. */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val p = context.getSharedPreferences("chitta", Context.MODE_PRIVATE)
    var firstRunDone: Boolean
        get() = p.getBoolean("firstRunDone", false)
        set(v) { p.edit().putBoolean("firstRunDone", v).apply() }
    var importedAtMs: Long
        get() = p.getLong("importedAtMs", 0L)
        set(v) { p.edit().putLong("importedAtMs", v).apply() }
    var importBackupPath: String?
        get() = p.getString("importBackupPath", null)
        set(v) { p.edit().putString("importBackupPath", v).apply() }

    fun clearImportMarker() { p.edit().remove("importedAtMs").remove("importBackupPath").apply() }

    // ---- Phase 5 reminders (master section 5.7: due bills and the log reminder on, digests off)
    var reminders: ReminderSettings
        get() = ReminderSettings(
            dueBills = p.getBoolean("rem.bills", true), billHour = p.getInt("rem.billHour", 9), billMinute = p.getInt("rem.billMinute", 0),
            logReminder = p.getBoolean("rem.log", true), logHour = p.getInt("rem.logHour", 21), logMinute = p.getInt("rem.logMinute", 0),
        )
        set(v) {
            p.edit().putBoolean("rem.bills", v.dueBills).putInt("rem.billHour", v.billHour).putInt("rem.billMinute", v.billMinute)
                .putBoolean("rem.log", v.logReminder).putInt("rem.logHour", v.logHour).putInt("rem.logMinute", v.logMinute).apply()
        }

    /** Whether we have already asked for the notification permission, so a refusal is not nagged about. */
    var askedNotificationPermission: Boolean
        get() = p.getBoolean("askedNotif", false)
        set(v) { p.edit().putBoolean("askedNotif", v).apply() }

    /** Reminder keys already posted today (so a re-run of the worker does not post twice); cleared each day. */
    fun postedKeys(day: String): Set<String> = if (p.getString("posted.day", "") == day) p.getStringSet("posted.keys", emptySet()) ?: emptySet() else emptySet()
    fun markPosted(day: String, keys: Set<String>) { p.edit().putString("posted.day", day).putStringSet("posted.keys", keys).apply() }

    /** Whether Auto-pay overdue is on, and which date a paid bill gets (Phase 5). */
    var autoPayOverdue: Boolean
        get() = p.getBoolean("autoPay", false)
        set(v) { p.edit().putBoolean("autoPay", v).apply() }
    var paidOnDueDate: Boolean
        get() = p.getBoolean("paidOnDue", false)
        set(v) { p.edit().putBoolean("paidOnDue", v).apply() }

    /** Saved CSV column setups (Phase 7a), each encoded by `CsvTemplate.encode`. */
    var csvTemplates: Set<String>
        get() = p.getStringSet("csvTemplates", emptySet())?.toSet() ?: emptySet()
        set(v) { p.edit().putStringSet("csvTemplates", v.toSet()).apply() }

    /** The secret another app must send to add an entry in the background. Null = the receiver is off. Device-only. */
    var broadcastSecret: String?
        get() = p.getString("broadcastSecret", null)
        set(v) { if (v == null) p.edit().remove("broadcastSecret").apply() else p.edit().putString("broadcastSecret", v).apply() }

    companion object { const val UNDO_IMPORT_DAYS = 30L }
}
