package com.mansaketh.chitta.ui.automation

import androidx.lifecycle.ViewModel
import com.mansaketh.chitta.core.domain.BroadcastAdd
import com.mansaketh.chitta.data.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class AutomationUi(val secretSet: Boolean)

/** Settings > Automation: the secret for background add. Kept on this phone only, outside the book and its backups (D99). */
@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val prefs: AppPrefs,
) : ViewModel() {
    private fun read() = AutomationUi(prefs.broadcastSecret?.let { it.length >= BroadcastAdd.MIN_SECRET } == true)

    private val _ui = MutableStateFlow(read())
    val ui: StateFlow<AutomationUi> = _ui

    /** Called when the screen opens. */
    fun refresh() { _ui.value = read() }

    /** Makes a new secret (the old one stops working at once) and returns it to be shown once. */
    fun newSecret(): String { val s = BroadcastAdd.newSecret(); prefs.broadcastSecret = s; refresh(); return s }

    fun turnOffReceiver() { prefs.broadcastSecret = null; refresh() }
}
