package com.mansaketh.chitta.cloud

import android.app.PendingIntent
import android.content.Context
import com.mansaketh.chitta.core.database.BackupFacts
import com.mansaketh.chitta.core.database.ChittaDatabase
import com.mansaketh.chitta.core.database.DatabaseFiles
import com.mansaketh.chitta.core.domain.BackupVerdict
import com.mansaketh.chitta.core.domain.CloudBackups
import com.mansaketh.chitta.core.domain.DriveFile
import com.mansaketh.chitta.di.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>
    /** Google needs the person's yes first. The caller launches [intent] and then runs the same action again. */
    data class NeedsConsent(val intent: PendingIntent) : CloudResult<Nothing>
    /** [retryable] is true for a missing connection or a Drive hiccup, where trying again later makes sense. */
    data class Failed(val message: String, val retryable: Boolean = false) : CloudResult<Nothing>
}

/** A backup file brought down from Drive and read, not yet used. */
data class DownloadedBackup(val file: File, val facts: BackupFacts?)

/**
 * Backs the book up to the app's hidden folder in the person's own Drive, lists what is there, and brings a file down. Only one
 * action runs at a time. The live book is only ever read (a snapshot is copied, checked, then uploaded).
 */
@Singleton
class CloudBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ChittaDatabase,
    private val auth: DriveAuthorizer,
    private val drive: DriveClient,
    private val prefs: CloudPrefs,
    private val clock: Clock,
) {
    private val gate = Mutex()

    /** Makes a backup now. Old ones beyond the newest 20 are removed only after the new one has arrived whole. */
    suspend fun backupNow(): CloudResult<DriveFile> = gate.withLock {
        if (prefs.email.value == null) return@withLock CloudResult.Failed("Sign in with Google first.")
        val hasData = withContext(Dispatchers.IO) { db.accounts().count() > 0 }
        if (!hasData) return@withLock CloudResult.Failed("Your book is empty, so there is nothing to back up.")
        val tmp = File(context.cacheDir, "cloud-upload.db")
        try {
            val facts = withContext(Dispatchers.IO) {
                try { DatabaseFiles.snapshot(db, tmp); DatabaseFiles.inspect(tmp) } catch (e: Exception) { null }
            }
            if (facts == null || facts.verdict !is BackupVerdict.Ok) {
                return@withLock CloudResult.Failed("Could not make a good copy of the book, so nothing was uploaded.")
            }
            val now = clock.nowMs()
            val sent = guarded { token ->
                val uploaded = drive.upload(token, CloudBackups.fileName(now), tmp)
                // Drive reports the size it stored. A different size means the copy did not arrive whole: remove it and say so.
                if (uploaded.sizeBytes != tmp.length()) {
                    runCatching { drive.delete(token, uploaded.id) }
                    throw DriveHttpException(0, "The backup did not arrive whole in Drive, so it was removed. Try again.")
                }
                // Housekeeping after success. A failure here only leaves an extra old copy; the new backup is safe.
                runCatching { CloudBackups.toDelete(drive.list(token)).forEach { drive.delete(token, it.id) } }
                uploaded
            }
            if (sent is CloudResult.Ok) prefs.backupDone(now) else if (sent is CloudResult.Failed) prefs.setError(sent.message)
            sent
        } finally { tmp.delete() }
    }

    /** The automatic backup: only if signed in, switched on and none was made today. Null means nothing was due. */
    suspend fun backupIfDue(): CloudResult<DriveFile>? {
        if (prefs.email.value == null || !prefs.autoBackup.value) return null
        if (!CloudBackups.isAutoDue(prefs.lastBackupMs.value, clock.nowMs(), clock.zone())) return null
        return backupNow()
    }

    suspend fun listAll(): CloudResult<List<DriveFile>> = gate.withLock { guarded { drive.list(it) } }

    /** Brings [id] down into the cache and reads it as a Chitta backup. [facts] is null when the file is not a readable database. */
    suspend fun download(id: String): CloudResult<DownloadedBackup> = gate.withLock {
        val dest = File(context.cacheDir, "cloud-download.db")
        val got = guarded { token -> drive.download(token, id, dest); dest }
        when (got) {
            is CloudResult.Ok -> CloudResult.Ok(DownloadedBackup(dest, withContext(Dispatchers.IO) { DatabaseFiles.inspect(dest) }))
            is CloudResult.NeedsConsent -> got
            is CloudResult.Failed -> got
        }
    }

    suspend fun deleteBackup(id: String): CloudResult<Unit> = gate.withLock { guarded { drive.delete(it, id) } }

    /**
     * Gets a Drive token, runs [block] off the main thread, and turns every way it can fail into plain words. If Drive refuses the
     * token the first time, the token is dropped and one more is tried.
     */
    private suspend fun <T> guarded(block: (String) -> T): CloudResult<T> {
        var token = when (val t = auth.token()) {
            is TokenResult.Token -> t.value
            is TokenResult.NeedsConsent -> return CloudResult.NeedsConsent(t.intent)
            is TokenResult.Failed -> return CloudResult.Failed(t.message)
        }
        repeat(2) { attempt ->
            try {
                return CloudResult.Ok(withContext(Dispatchers.IO) { block(token) })
            } catch (e: DriveHttpException) {
                if (e.code == 401 && attempt == 0) {
                    auth.forget(token)
                    token = (auth.token() as? TokenResult.Token)?.value ?: return CloudResult.Failed(e.message.orEmpty())
                } else {
                    return CloudResult.Failed(e.message.orEmpty(), retryable = e.code == 429 || e.code >= 500)
                }
            } catch (e: IOException) {
                return CloudResult.Failed("Chitta could not reach Google. Check the connection and try again.", retryable = true)
            }
        }
        return CloudResult.Failed("Drive did not answer. Try again.", retryable = true)
    }
}
