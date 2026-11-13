package com.mansaketh.chitta.automation

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mansaketh.chitta.MainActivity
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.domain.BroadcastAdd
import com.mansaketh.chitta.core.domain.QuickAdd
import com.mansaketh.chitta.core.domain.QuickAddResult
import com.mansaketh.chitta.core.money.IndianFormat
import com.mansaketh.chitta.core.money.Paise
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.di.IdSource
import com.mansaketh.chitta.reminders.CHANNEL_AUTO
import com.mansaketh.chitta.reminders.ReminderNotifications
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutomationEntryPoint {
    fun ledger(): LedgerRepository
    fun accounts(): AccountRepository
    fun categories(): CategoryRepository
    fun prefs(): AppPrefs
    fun clock(): Clock
    fun ids(): IdSource
}

/**
 * Background add (master section 7). Send action `com.mansaketh.chitta.action.ADD` with string extras `secret`, `amount`, `title`,
 * `category`, `account` (and optionally `type` expense or income, `note`, `date`). Without the secret set in Settings > Automation
 * nothing is accepted, and a wrong secret is dropped silently. A good request is saved and a notice offers Undo for 10 seconds;
 * an incomplete one is refused with a notice saying why. Never guesses an account or category (D99).
 */
class AddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD) return
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, AutomationEntryPoint::class.java)
        val params = paramsOf(intent.extras)
        if (!BroadcastAdd.secretOk(ep.prefs().broadcastSecret, params["secret"])) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { handle(context.applicationContext, ep, params) } catch (e: Exception) {
                notify(context.applicationContext, "Could not add the entry", "Nothing was saved.", null)
            } finally { pending.finish() }
        }
    }

    private suspend fun handle(context: Context, ep: AutomationEntryPoint, params: Map<String, String>) {
        val accounts = ep.accounts().observeAll().first().filter { !it.archived }
        val categories = ep.categories().observeAll().first().filter { !it.archived }
        when (val r = BroadcastAdd.parse(params, accounts, categories, ep.clock().nowMs(), ep.clock().zone())) {
            is BroadcastAdd.Result.Rejected -> notify(context, "Entry not added", r.reason, null)
            is BroadcastAdd.Result.Ready -> {
                val built = QuickAdd.build(r.draft, accounts, ep.ids()::newId) as? QuickAddResult.Ready
                if (built == null) { notify(context, "Entry not added", "The entry could not be built.", null); return }
                ep.ledger().add(built.entries)
                val first = built.entries.first()
                val undo = PendingIntent.getBroadcast(
                    context, first.id.hashCode(), Intent(context, UndoAddReceiver::class.java).putExtra(UndoAddReceiver.EXTRA_IDS, built.entries.map { it.id }.toTypedArray()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                notify(context, "Added ${first.name.ifBlank { "entry" }}", IndianFormat.rupees(Paise(Math.abs(first.amount)), alwaysShowPaise = true), undo)
            }
        }
    }

    private fun notify(context: Context, title: String, text: String, undo: PendingIntent?) {
        if (!ReminderNotifications.canPost(context)) return
        ReminderNotifications.createChannels(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(context, CHANNEL_AUTO).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true)
            .setTimeoutAfter(if (undo != null) 10_000 else 60_000)
        if (undo != null) b.addAction(0, "Undo", undo)
        @Suppress("MissingPermission") // checked in canPost
        NotificationManagerCompat.from(context).notify(NOTICE_ID, b.build())
    }

    private fun paramsOf(extras: Bundle?): Map<String, String> {
        if (extras == null) return emptyMap()
        val out = HashMap<String, String>()
        for (k in listOf("secret", "amount", "title", "category", "account", "type", "note", "date")) {
            @Suppress("DEPRECATION") extras.get(k)?.toString()?.let { if (it.length <= 200) out[k] = it }
        }
        return out
    }

    companion object {
        const val ACTION_ADD = "com.mansaketh.chitta.action.ADD"
        private const val NOTICE_ID = 7001
    }
}

/** Takes back an entry added in the background. Not exported: only the notice's own Undo button reaches it. */
class UndoAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ids = intent.getStringArrayExtra(EXTRA_IDS)?.toList().orEmpty()
        if (ids.isEmpty()) return
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, AutomationEntryPoint::class.java)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { ep.ledger().delete(ids) } finally {
                NotificationManagerCompat.from(context).cancel(7001)
                pending.finish()
            }
        }
    }
    companion object { const val EXTRA_IDS = "ids" }
}
