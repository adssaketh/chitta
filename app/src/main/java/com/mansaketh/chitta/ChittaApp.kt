package com.mansaketh.chitta

import android.app.Application
import android.content.Context
import com.mansaketh.chitta.core.database.DatabaseFiles
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.reminders.ReminderNotifications
import com.mansaketh.chitta.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChittaApp : Application() {
    @Inject lateinit var prefs: AppPrefs
    @Inject lateinit var clock: Clock
    @Inject lateinit var uiPrefs: com.mansaketh.chitta.data.UiPrefs
    @Inject lateinit var receipts: com.mansaketh.chitta.receipts.ReceiptStore
    @Inject lateinit var ledger: com.mansaketh.chitta.core.database.LedgerRepository
    @Inject lateinit var security: com.mansaketh.chitta.data.SecurityPrefs
    @Inject lateinit var cloudPrefs: com.mansaketh.chitta.cloud.CloudPrefs
    @Inject lateinit var cloudBackup: com.mansaketh.chitta.cloud.CloudBackupService

    /** A restore staged by Settings is swapped in here, before anything can open the book (DECISIONS D69). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        DatabaseFiles.applyPending(base)
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannels(this)
        ReminderScheduler.scheduleNext(this, prefs.reminders, clock.nowMs(), clock.zone())
        com.mansaketh.chitta.data.FundPriceScheduler.sync(this, uiPrefs.fundPricesAuto.value)
        com.mansaketh.chitta.widgets.Shortcuts.publish(this)
        // Daily Drive backup (Phase 9a): queued only while signed in with the switch on. Also tried when the app opens, if none was made today.
        com.mansaketh.chitta.cloud.CloudBackupScheduler.sync(this, cloudPrefs.email.value != null && cloudPrefs.autoBackup.value)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { cloudBackup.backupIfDue() }
        }
        // The spend widget is redrawn whenever the book changes, or hide-amounts or the lock is switched, while the app is running.
        // Between runs it refreshes every 30 minutes (the shortest period Android allows), so the day's total can lag after midnight.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
            kotlinx.coroutines.flow.merge(
                ledger.observeAll().map { }.debounce(1_500),
                security.hideAmounts.map { },
                security.lockEnabled.map { },
            ).collect { com.mansaketh.chitta.widgets.WidgetRefresh.spend(this@ChittaApp) }
        }
        // Receipt photos whose entry is gone for over 30 days are removed (DECISIONS D155). A failure here is not worth showing.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { receipts.cleanOrphans(ledger.observeAllOnce().map { it.id }.toSet(), clock.nowMs()) }
        }
    }
}
