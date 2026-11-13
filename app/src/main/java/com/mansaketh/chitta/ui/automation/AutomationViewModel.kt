package com.mansaketh.chitta.ui.automation

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import com.mansaketh.chitta.core.domain.BroadcastAdd
import com.mansaketh.chitta.core.domain.NotificationScan
import com.mansaketh.chitta.data.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class AutomationUi(val apps: Set<String>, val secretSet: Boolean, val accessOn: Boolean)

/** Settings > Automation: which apps' notifications may be read, and the secret for background add. Kept on this phone only, outside the book and its backups (D99). */
@HiltViewModel
class AutomationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPrefs,
) : ViewModel() {
    private fun read() = AutomationUi(
        prefs.scanApps, prefs.broadcastSecret?.let { it.length >= BroadcastAdd.MIN_SECRET } == true,
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
    )

    private val _ui = MutableStateFlow(read())
    val ui: StateFlow<AutomationUi> = _ui

    /** Called when the screen opens and when the person says they have been to Android settings. */
    fun refresh() { _ui.value = read() }

    fun setApp(pkg: String, on: Boolean) {
        prefs.scanApps = if (on) prefs.scanApps + pkg else prefs.scanApps - pkg
        refresh()
    }

    /** False when the text is not a package name like com.bank.app. */
    fun addPackage(text: String): Boolean {
        val t = text.trim()
        if (!NotificationScan.looksLikePackage(t)) return false
        setApp(t, true)
        return true
    }

    /** Makes a new secret (the old one stops working at once) and returns it to be shown once. */
    fun newSecret(): String { val s = BroadcastAdd.newSecret(); prefs.broadcastSecret = s; refresh(); return s }

    fun turnOffReceiver() { prefs.broadcastSecret = null; refresh() }
}
