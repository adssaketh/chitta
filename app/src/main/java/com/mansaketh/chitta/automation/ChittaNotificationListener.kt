package com.mansaketh.chitta.automation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mansaketh.chitta.core.domain.NotificationScan
import com.mansaketh.chitta.core.intake.PingExport
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.intake.IntakeStore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads the notifications of the apps ticked in Settings > Automation (none until the person ticks one, and only after they give
 * Notification access in Android settings). A notice that mentions rupees is written to the same inbox as Ping's messages; it is read
 * when Chitta opens, and what is not confident waits in the review list. Nothing is saved from here and nothing leaves the phone.
 */
class ChittaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val allowed = AppPrefs(applicationContext).scanApps
            if (allowed.isEmpty() || sbn.packageName !in allowed) return
            val extras = sbn.notification.extras
            val msg = NotificationScan.accept(
                sbn.packageName, extras.getCharSequence("android.title")?.toString(),
                (extras.getCharSequence("android.bigText") ?: extras.getCharSequence("android.text"))?.toString(),
                sbn.postTime, allowed, applicationContext.packageName,
            ) ?: return
            val item = buildJsonObject {
                put("id", msg.id); put("address", msg.sender); put("body", msg.body); put("date", msg.dateMs); put("kind", "transaction")
            }
            IntakeStore.writeToInbox(applicationContext, PingExport.exportOf(listOf(item)))
        } catch (e: Exception) {
            // a notice that cannot be kept is dropped; the listener must never crash the phone's notification flow
        }
    }
}
