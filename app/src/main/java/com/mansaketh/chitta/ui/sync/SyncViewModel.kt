package com.mansaketh.chitta.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.cloud.CloudPrefs
import com.mansaketh.chitta.cloud.PersonalSync
import com.mansaketh.chitta.cloud.SyncPrefs
import com.mansaketh.chitta.cloud.SyncResult
import com.mansaketh.chitta.core.database.SyncStore
import com.mansaketh.chitta.core.domain.SyncStatus
import com.mansaketh.chitta.di.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUi(
    val signedIn: Boolean = false,
    val line: String = "",
    val lastSyncMs: Long? = null,
    val waiting: Int = 0,
    val error: String? = null,
    val running: Boolean = false,
    val needsChoice: Boolean = false,
    val notice: String? = null,
)

/** The Sync screen: status, Sync now, and the choice a phone must make after being away more than 180 days. */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val sync: PersonalSync,
    private val prefs: SyncPrefs,
    private val cloud: CloudPrefs,
    store: SyncStore,
    private val clock: Clock,
) : ViewModel() {
    private val notice = MutableStateFlow<String?>(null)

    val ui: StateFlow<SyncUi> = combine(
        cloud.email, prefs.lastSyncMs, prefs.lastError, prefs.needsChoice, prefs.running,
    ) { email, last, error, choice, running -> Five(email != null, last, error, choice, running) }
        .combine(store.observePendingCount()) { f, waiting -> f to waiting }
        .combine(notice) { (f, waiting), note ->
            SyncUi(
                signedIn = f.signedIn,
                line = SyncStatus.line(f.signedIn, f.running, waiting, f.last, f.choice, clock.nowMs()),
                lastSyncMs = f.last, waiting = waiting, error = f.error, running = f.running, needsChoice = f.choice, notice = note,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUi())

    private data class Five(val signedIn: Boolean, val last: Long?, val error: String?, val choice: Boolean, val running: Boolean)

    fun syncNow() {
        viewModelScope.launch { say(sync.run("button")) }
    }

    fun resolve(merge: Boolean) {
        viewModelScope.launch { say(sync.resolveStale(merge)) }
    }

    fun dismissNotice() { notice.value = null }

    private fun say(r: SyncResult) {
        notice.value = when (r) {
            is SyncResult.Done -> "Synced. ${r.pulled} came from the cloud, ${r.pushed} went up."
            SyncResult.NotSignedIn -> "Sign in with Google first (Settings, Google Drive backup)."
            SyncResult.NeedsChoice -> null
            SyncResult.Busy -> "A sync is already running."
            is SyncResult.Failed -> null // the error line shows it
        }
    }
}
