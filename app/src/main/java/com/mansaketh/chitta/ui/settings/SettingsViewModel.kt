package com.mansaketh.chitta.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mansaketh.chitta.core.database.AccountRepository
import com.mansaketh.chitta.core.database.BackupFacts
import com.mansaketh.chitta.core.database.CategoryRepository
import com.mansaketh.chitta.core.database.ChittaDatabase
import com.mansaketh.chitta.core.database.DatabaseFiles
import com.mansaketh.chitta.core.database.LedgerRepository
import com.mansaketh.chitta.core.domain.BackupVerdict
import com.mansaketh.chitta.core.domain.Csv
import com.mansaketh.chitta.core.domain.CsvExport
import com.mansaketh.chitta.core.domain.ExportRange
import com.mansaketh.chitta.core.domain.LockTimeout
import com.mansaketh.chitta.core.domain.RestoreKind
import com.mansaketh.chitta.core.domain.RestorePoint
import com.mansaketh.chitta.core.model.Account
import com.mansaketh.chitta.data.AppPrefs
import com.mansaketh.chitta.data.RestoreManager
import com.mansaketh.chitta.data.SecurityPrefs
import com.mansaketh.chitta.di.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** A backup file the person picked, read but not used yet. [facts] says whether it is a usable Chitta backup. */
data class BackupCandidate(val file: File, val facts: BackupFacts)

data class SettingsUi(
    val lockEnabled: Boolean = false,
    val timeout: LockTimeout = LockTimeout.OneMinute,
    val hideInRecents: Boolean = false,
    val points: List<RestorePoint> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val busy: Boolean = false,
    val notice: String? = null,
    val candidate: BackupCandidate? = null,
)

/** Security, restore points, backup files, CSV export and delete-everything. Every change that replaces or removes data makes a restore point first. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ChittaDatabase,
    private val security: SecurityPrefs,
    private val prefs: AppPrefs,
    private val restore: RestoreManager,
    private val clock: Clock,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val ledger: LedgerRepository,
) : ViewModel() {
    private val points = MutableStateFlow(restore.list())
    private val busy = MutableStateFlow(false)
    private val notice = MutableStateFlow<String?>(null)
    private val candidate = MutableStateFlow<BackupCandidate?>(null)

    private data class Local(val points: List<RestorePoint>, val busy: Boolean, val notice: String?, val candidate: BackupCandidate?)
    private val local = combine(points, busy, notice, candidate) { p, b, n, c -> Local(p, b, n, c) }
    private val secure = combine(security.lockEnabled, security.timeout, security.hideInRecents) { l, t, h -> Triple(l, t, h) }

    val ui: StateFlow<SettingsUi> = combine(local, secure, accountRepo.observeAll()) { l, (lock, timeout, hide), accounts ->
        SettingsUi(lock, timeout, hide, l.points, accounts, l.busy, l.notice, l.candidate)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUi())

    fun dismissNotice() { notice.value = null }
    fun tell(message: String) { notice.value = message }
    fun refreshPoints() { points.value = restore.list() }

    // ---- security
    fun setLockEnabled(v: Boolean) = security.setLockEnabled(v)
    fun setTimeout(v: LockTimeout) = security.setTimeout(v)
    fun setHideInRecents(v: Boolean) = security.setHideInRecents(v)

    // ---- restore points
    fun makePoint() = work {
        notice.value = if (restore.create(RestoreKind.Manual) != null) "Restore point made." else "Could not make a restore point. Nothing was changed."
        refreshPoints()
    }

    fun deletePoint(p: RestorePoint) { restore.delete(p); refreshPoints() }

    /** Checks the point, then keeps it to replace the book at the next start. [onStaged] closes the app. */
    fun restorePoint(p: RestorePoint, onStaged: () -> Unit) = work {
        val facts = restore.inspect(p)
        val verdict = facts?.verdict
        when {
            facts == null -> notice.value = "That restore point could not be read. Nothing was changed."
            verdict is BackupVerdict.Refused -> notice.value = verdict.reason
            restore.stageRestore(restore.fileOf(p)) -> onStaged()
            else -> notice.value = "Could not get ready to restore. Nothing was changed."
        }
        refreshPoints()
    }

    // ---- backup file (a full copy of the book)
    fun exportBackup(uri: Uri) = work {
        val ok = withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "backup-export.db")
            try {
                DatabaseFiles.snapshot(db, tmp)
                context.contentResolver.openOutputStream(uri, "wt")?.use { out -> tmp.inputStream().use { it.copyTo(out) } } ?: return@withContext false
                true
            } catch (e: Exception) { false } finally { tmp.delete() }
        }
        notice.value = if (ok) "Backup saved." else "The backup could not be saved. Nothing was changed."
    }

    /** Copies the picked file into the cache and reads it. Nothing is used until [confirmCandidate]. */
    fun pickBackup(uri: Uri) = work {
        val result = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "incoming-backup.db").also { if (it.exists()) it.delete() }
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return@withContext null
                DatabaseFiles.inspect(file)?.let { BackupCandidate(file, it) }
            } catch (e: Exception) { null }
        }
        when {
            result == null -> notice.value = "That file could not be read. Nothing was changed."
            result.facts.verdict is BackupVerdict.Refused -> { notice.value = (result.facts.verdict as BackupVerdict.Refused).reason; result.file.delete() }
            else -> candidate.value = result
        }
    }

    fun clearCandidate() { candidate.value?.file?.delete(); candidate.value = null }

    fun confirmCandidate(onStaged: () -> Unit) = work {
        val c = candidate.value ?: return@work
        if (restore.stageRestore(c.file)) { candidate.value = null; onStaged() } else notice.value = "Could not get ready to restore. Nothing was changed."
        refreshPoints()
    }

    // ---- CSV export
    fun exportCsv(uri: Uri, accountId: String?, range: ExportRange) = work {
        val text = withContext(Dispatchers.IO) {
            try {
                val accounts = accountRepo.observeAll().first()
                val categories = categoryRepo.observeAll().first()
                val entries = ledger.observeAllOnce()
                val (from, to) = range.bounds(clock.nowMs(), clock.zone())
                val rows = CsvExport.rows(entries, accounts, categories, clock.zone(), accountId?.let { setOf(it) }, from, to)
                // A byte-order mark so Excel reads the file as UTF-8 (the app's own reader strips it).
                val body = "﻿" + Csv.write(listOf(CsvExport.HEADER) + rows)
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(body.toByteArray(Charsets.UTF_8)) } ?: return@withContext null
                rows.size
            } catch (e: Exception) { null }
        }
        notice.value = if (text != null) "Saved $text entries." else "The file could not be saved."
    }

    // ---- delete everything
    /** Empties the book. A restore point is made first and must succeed; it is how this is undone. */
    fun deleteAll(onDone: () -> Unit) = work {
        val hasData = withContext(Dispatchers.IO) { db.accounts().count() > 0 }
        if (hasData && restore.create(RestoreKind.BeforeDelete) == null) {
            notice.value = "Could not make a restore point first, so nothing was deleted."
            return@work
        }
        withContext(Dispatchers.IO) { db.clearAllTables() }
        prefs.clearImportMarker(); prefs.firstRunDone = false
        refreshPoints()
        onDone()
    }

    private fun work(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch { try { block() } finally { busy.value = false } }
    }
}
