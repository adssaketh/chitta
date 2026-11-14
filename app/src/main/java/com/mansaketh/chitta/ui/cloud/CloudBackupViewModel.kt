package com.mansaketh.chitta.ui.cloud

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.cloud.CloudBackupScheduler
import com.mansaketh.chitta.cloud.CloudBackupService
import com.mansaketh.chitta.cloud.CloudPrefs
import com.mansaketh.chitta.cloud.CloudResult
import com.mansaketh.chitta.cloud.DriveAuthorizer
import com.mansaketh.chitta.cloud.GoogleAccount
import com.mansaketh.chitta.cloud.SignInResult
import com.mansaketh.chitta.cloud.TokenResult
import com.mansaketh.chitta.core.domain.BackupVerdict
import com.mansaketh.chitta.core.domain.CloudBackup
import com.mansaketh.chitta.core.domain.CloudBackups
import com.mansaketh.chitta.core.domain.DriveFile
import com.mansaketh.chitta.data.RestoreManager
import com.mansaketh.chitta.ui.settings.BackupCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** What Drive holds for this app: Chitta's own backups, and any other files in the same hidden folder. */
data class DriveListing(val backups: List<CloudBackup>, val others: List<DriveFile>)

data class CloudUi(
    val email: String? = null,
    val auto: Boolean = true,
    val lastBackupMs: Long? = null,
    val lastError: String? = null,
    val busy: Boolean = false,
    val notice: String? = null,
    val listing: DriveListing? = null,
    val consent: PendingIntent? = null,
    val candidate: BackupCandidate? = null,
)

/** Google sign-in, backing up to Drive, and bringing a backup back. Every restore goes through the same confirm step as a backup file. */
@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val account: GoogleAccount,
    private val auth: DriveAuthorizer,
    private val service: CloudBackupService,
    private val prefs: CloudPrefs,
    private val restore: RestoreManager,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val notice = MutableStateFlow<String?>(null)
    private val listing = MutableStateFlow<DriveListing?>(null)
    private val consent = MutableStateFlow<PendingIntent?>(null)
    private val candidate = MutableStateFlow<BackupCandidate?>(null)
    /** What to run again once the person has said yes on Google's screen. */
    private var resume: (suspend () -> Unit)? = null

    private data class Local(val busy: Boolean, val notice: String?, val listing: DriveListing?, val consent: PendingIntent?, val candidate: BackupCandidate?)
    private val local = combine(busy, notice, listing, consent, candidate) { b, n, l, c, k -> Local(b, n, l, c, k) }
    private data class Saved(val email: String?, val auto: Boolean, val last: Long?, val error: String?)
    private val saved = combine(prefs.email, prefs.autoBackup, prefs.lastBackupMs, prefs.lastError) { e, a, l, x -> Saved(e, a, l, x) }

    val ui: StateFlow<CloudUi> = combine(local, saved) { l, s ->
        CloudUi(s.email, s.auto, s.last, s.error, l.busy, l.notice, l.listing, l.consent, l.candidate)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CloudUi())

    fun dismissNotice() { notice.value = null }

    fun signIn(activity: Activity) = work {
        when (val r = account.signIn(activity)) {
            is SignInResult.Ok -> {
                CloudBackupScheduler.sync(context, prefs.autoBackup.value)
                notice.value = "Signed in as ${r.email}."
                loadListing()
            }
            SignInResult.Cancelled -> Unit
            is SignInResult.Failed -> notice.value = r.message
        }
    }

    fun signOut() = work {
        account.signOut()
        CloudBackupScheduler.sync(context, false)
        listing.value = null
        notice.value = "Signed out on this phone. Your backups stay in your Drive."
    }

    fun setAuto(on: Boolean) {
        prefs.setAuto(on)
        CloudBackupScheduler.sync(context, on && prefs.email.value != null)
    }

    fun backupNow() = work {
        when (val r = service.backupNow()) {
            is CloudResult.Ok -> { notice.value = "Backed up to Drive."; loadListing() }
            is CloudResult.NeedsConsent -> askConsent(r.intent) { runBackup() }
            is CloudResult.Failed -> notice.value = r.message
        }
    }

    private suspend fun runBackup() {
        when (val r = service.backupNow()) {
            is CloudResult.Ok -> { notice.value = "Backed up to Drive."; loadListing() }
            is CloudResult.NeedsConsent -> notice.value = "Drive access was not allowed, so nothing was backed up."
            is CloudResult.Failed -> notice.value = r.message
        }
    }

    fun refreshListing() = work { loadListing() }

    private suspend fun loadListing() {
        when (val r = service.listAll()) {
            is CloudResult.Ok -> listing.value = DriveListing(CloudBackups.backups(r.value), CloudBackups.others(r.value))
            is CloudResult.NeedsConsent -> askConsent(r.intent) { loadListing() }
            is CloudResult.Failed -> notice.value = r.message
        }
    }

    /** Downloads a Chitta backup and, if it is good, asks for the usual "Replace your book?" confirmation. */
    fun pickBackup(backup: CloudBackup) = work {
        when (val r = service.download(backup.file.id)) {
            is CloudResult.Ok -> {
                val facts = r.value.facts
                when {
                    facts == null -> { notice.value = "That backup could not be read. Nothing was changed."; r.value.file.delete() }
                    facts.verdict is BackupVerdict.Refused -> { notice.value = (facts.verdict as BackupVerdict.Refused).reason; r.value.file.delete() }
                    else -> candidate.value = BackupCandidate(r.value.file, facts)
                }
            }
            is CloudResult.NeedsConsent -> askConsent(r.intent) { /* the person taps the backup again */ }
            is CloudResult.Failed -> notice.value = r.message
        }
    }

    /** Downloads some other file from the folder (maybe a Paddhu backup) and hands it to the Paddhu import. */
    fun pickOther(file: DriveFile, onReady: (File) -> Unit) = work {
        when (val r = service.download(file.id)) {
            is CloudResult.Ok -> onReady(r.value.file)
            is CloudResult.NeedsConsent -> askConsent(r.intent) { }
            is CloudResult.Failed -> notice.value = r.message
        }
    }

    fun deleteBackup(backup: CloudBackup) = work {
        when (val r = service.deleteBackup(backup.file.id)) {
            is CloudResult.Ok -> { notice.value = "Backup removed from Drive."; loadListing() }
            is CloudResult.NeedsConsent -> askConsent(r.intent) { }
            is CloudResult.Failed -> notice.value = r.message
        }
    }

    fun clearCandidate() { candidate.value?.file?.delete(); candidate.value = null }

    fun confirmCandidate(onStaged: () -> Unit) = work {
        val c = candidate.value ?: return@work
        if (restore.stageRestore(c.file)) { candidate.value = null; onStaged() } else notice.value = "Could not get ready to restore. Nothing was changed."
    }

    // ---- Google's consent screen
    private fun askConsent(intent: PendingIntent, then: suspend () -> Unit) { resume = then; consent.value = intent }

    /** The screen has started Google's consent screen. */
    fun consentLaunched() { consent.value = null }

    fun consentResult(ok: Boolean, data: Intent?) {
        val next = resume; resume = null
        if (!ok || auth.fromIntent(data) is TokenResult.Failed) { notice.value = "Drive access was not allowed. Backup needs it."; return }
        if (next != null) work { next() }
    }

    private fun work(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch { try { block() } finally { busy.value = false } }
    }
}
