package com.mansaketh.chitta

import android.app.Application
import android.content.Context
import com.mansaketh.chitta.core.database.DatabaseFiles
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.di.Clock
import com.mansaketh.chitta.reminders.ReminderNotifications
import com.mansaketh.chitta.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ChittaApp : Application() {
    @Inject lateinit var prefs: AppPrefs
    @Inject lateinit var clock: Clock
    @Inject lateinit var uiPrefs: com.mansaketh.chitta.data.UiPrefs

    /** A restore staged by Settings is swapped in here, before anything can open the book (DECISIONS D69). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        DatabaseFiles.applyPending(base)
    }

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannels(this)
        ReminderScheduler.scheduleNext(this, prefs.reminders, clock.nowMs(), clock.zone())
        com.mansaketh.chitta.data.FundPriceScheduler.sync(this, uiPrefs.fundPricesAuto.value)
    }
}
