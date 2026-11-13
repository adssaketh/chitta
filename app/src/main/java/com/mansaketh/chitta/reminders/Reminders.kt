package com.mansaketh.chitta.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mansaketh.chitta.MainActivity
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.domain.ReminderKind
import com.mansaketh.chitta.core.domain.Reminders
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.di.Clock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.concurrent.TimeUnit

const val CHANNEL_BILLS = "bills"
const val CHANNEL_LOG = "log"
const val CHANNEL_AUTO = "auto"
private const val WORK_NAME = "chitta-reminders"

/** Lets a Worker (which Hilt does not build here) reach the repositories. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun ledger(): LedgerRepository
    fun accounts(): com.mansaketh.chitta.core.database.AccountRepository
    fun prefs(): AppPrefs
    fun clock(): Clock
}

object ReminderNotifications {
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Two channels, so the person can silence either from Android settings: due bills, and the daily nudge to log. Nothing promotional. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val m = context.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(NotificationChannel(CHANNEL_BILLS, "Due bills", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A bill due today or tomorrow." })
        m.createNotificationChannel(NotificationChannel(CHANNEL_AUTO, "Added in the background", NotificationManager.IMPORTANCE_LOW).apply { description = "Tells you when another app added an entry, with Undo." })
        m.createNotificationChannel(NotificationChannel(CHANNEL_LOG, "Log reminder", NotificationManager.IMPORTANCE_LOW).apply { description = "A quiet evening nudge to log what you spent." })
    }

    fun post(context: Context, id: Int, channel: String, title: String, text: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        @Suppress("MissingPermission") // checked in canPost
        androidx.core.app.NotificationManagerCompat.from(context).notify(id, n)
    }
}

/** Posts today's reminders, then queues itself for the next one. A chain of one-time jobs, not a periodic job, so the time of day holds. */
class ReminderWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(context, ReminderEntryPoint::class.java)
        val prefs = ep.prefs(); val clock = ep.clock()
        val settings = prefs.reminders
        val zone = clock.zone()
        val now = clock.nowMs()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        try {
            ReminderNotifications.createChannels(context)
            val posted = prefs.postedKeys(today.toString()).toMutableSet()
            if (settings.dueBills) {
                Reminders.forDay(ep.ledger().observeAllOnce(), today, zone).filter { it.key !in posted }.forEach { r ->
                    val amount = IndianFormat.rupees(r.amount)
                    val text = if (r.kind == ReminderKind.DueToday) (if (r.income) "$amount is due to come in today" else "$amount is due today") else (if (r.income) "$amount is due to come in tomorrow" else "$amount is due tomorrow")
                    ReminderNotifications.post(context, r.key.hashCode(), CHANNEL_BILLS, r.title.ifBlank { "A bill" }, text)
                    posted += r.key
                }
            }
            if (settings.dueBills) {
                // Credit-card payments (master section 8): on the reminder day and on the due day, only while something is owed.
                val entries = ep.ledger().observeAllOnce()
                ep.accounts().observeAll().first().forEach { acc ->
                    val setup = com.mansaketh.chitta.core.domain.Cards.setupOf(acc) ?: return@forEach
                    val f = com.mansaketh.chitta.core.domain.Cards.figures(setup, entries, today, zone)
                    val r = com.mansaketh.chitta.core.domain.Cards.reminderFor(setup, f, today) ?: return@forEach
                    if (r.key in posted) return@forEach
                    val owed = IndianFormat.rupees(r.outstanding)
                    val min = r.minimumDue?.let { " Minimum due ${IndianFormat.rupees(it)}." } ?: ""
                    val text = if (r.dueToday) "$owed is due today.$min" else "$owed is due in ${r.daysToDue} ${if (r.daysToDue == 1L) "day" else "days"}.$min"
                    ReminderNotifications.post(context, r.key.hashCode(), CHANNEL_BILLS, "${acc.name} payment", text)
                    posted += r.key
                }
            }
            if (settings.logReminder && Instant.ofEpochMilli(now).atZone(zone).toLocalTime().hour >= settings.logHour && "log|$today" !in posted) {
                ReminderNotifications.post(context, 9_001, CHANNEL_LOG, "Anything to log today?", "Add what you spent while you remember it.")
                posted += "log|$today"
            }
            prefs.markPosted(today.toString(), posted)
        } finally {
            ReminderScheduler.scheduleNext(context, prefs.reminders, now, zone)
        }
        return Result.success()
    }
}

object ReminderScheduler {
    /** Queues the next run at the earlier of the bill time and the log time (whichever is on). Replaces any queued run. */
    fun scheduleNext(context: Context, s: com.mansaketh.chitta.core.domain.ReminderSettings, nowMs: Long, zone: java.time.ZoneId) {
        val wm = WorkManager.getInstance(context)
        val times = buildList {
            if (s.dueBills) add(Reminders.nextFireMs(nowMs, zone, s.billHour, s.billMinute))
            if (s.logReminder) add(Reminders.nextFireMs(nowMs, zone, s.logHour, s.logMinute))
        }
        val at = times.minOrNull() ?: run { wm.cancelUniqueWork(WORK_NAME); return }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay((at - nowMs).coerceAtLeast(1_000), TimeUnit.MILLISECONDS).build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
