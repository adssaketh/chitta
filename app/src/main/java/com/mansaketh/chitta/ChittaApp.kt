package com.mansaketh.chitta

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannels(this)
        ReminderScheduler.scheduleNext(this, prefs.reminders, clock.nowMs(), clock.zone())
    }
}
